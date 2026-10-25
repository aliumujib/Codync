package com.codync.android.viewer

import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.delay
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class FileKindTest {
    @Test fun knownExtensionsMapToTheirKind() {
        assertEquals(FileKind.Video, FileKind.of("final.MP4"))
        assertEquals(FileKind.Audio, FileKind.of("voice.m4a"))
        assertEquals(FileKind.Image, FileKind.of("cover.jpeg"))
        assertEquals(FileKind.Pdf, FileKind.of("plum-week-2026-W44.pdf"))
        assertEquals(FileKind.Markdown, FileKind.of("README.md"))
        assertEquals(FileKind.Text, FileKind.of("posted-log.jsonl"))
        assertEquals(FileKind.Text, FileKind.of("Dockerfile"))
        assertEquals(FileKind.Text, FileKind.of(".env"))
        assertEquals(FileKind.Zip, FileKind.of("frames.zip"))
        assertEquals(FileKind.Other, FileKind.of("model.onnx"))
        assertTrue(FileKind.Video.streams && FileKind.Audio.streams && !FileKind.Pdf.streams)
    }

    @Test fun sniffingTellsTextFromBinary() {
        assertEquals(FileKind.Text, FileKind.sniff("name,role\nJessica,presenter\n".toByteArray()))
        assertEquals(FileKind.Text, FileKind.sniff("café ☕".toByteArray()))
        // A multi-byte character cut off by the sample's end is still text.
        val cut = "abc€".toByteArray().let { it.copyOf(it.size - 1) }
        assertEquals(FileKind.Text, FileKind.sniff(cut))
        assertEquals(FileKind.Other, FileKind.sniff(byteArrayOf(0x00, 0x01, 0x02, 0x7F, 0x10)))
        assertEquals(FileKind.Other, FileKind.sniff(byteArrayOf(0xFF.toByte(), 0xFE.toByte(), 0x41)))
    }
}

class ChunkSourceTest {
    private val data = ByteArray(1000) { (it % 251).toByte() }

    private fun source(fetches: AtomicInteger = AtomicInteger(), cache: Int = 64, body: suspend (Long) -> ByteArray = { offset ->
        fetches.incrementAndGet(); data.copyOfRange(offset.toInt(), minOf(offset.toInt() + 100, data.size))
    }) = ChunkSource(data.size.toLong(), chunkBytes = 100, cacheChunks = cache, fetch = body)

    private fun readAll(source: ChunkSource, from: Long, step: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        var position = from
        val buffer = ByteArray(step)
        while (true) {
            val n = source.read(position, buffer, 0, step)
            if (n < 0) break
            out.write(buffer, 0, n); position += n
        }
        return out.toByteArray()
    }

    @Test fun readsTheWholeFileAcrossChunkBoundaries() = source().use { s ->
        assertArrayEquals(data, readAll(s, 0, 37))
        assertEquals(-1, s.read(1000, ByteArray(10), 0, 10))
    }

    @Test fun seeksIntoTheMiddleOfAChunk() = source().use { s ->
        assertArrayEquals(data.copyOfRange(555, 1000), readAll(s, 555, 64))
    }

    @Test fun cachedChunksAreNotFetchedAgain() {
        val fetches = AtomicInteger()
        source(fetches).use { s ->
            readAll(s, 0, 50)
            val first = fetches.get()
            readAll(s, 0, 50) // a loop, or a seek back
            assertEquals(first, fetches.get())
            assertEquals(10, first)
        }
    }

    @Test fun readsAheadToTheNextChunk() {
        val fetches = AtomicInteger()
        source(fetches).use { s ->
            s.read(0, ByteArray(10), 0, 10)
            val deadline = System.currentTimeMillis() + 2000
            while (fetches.get() < 2 && System.currentTimeMillis() < deadline) Thread.sleep(5)
            assertEquals(2, fetches.get())
        }
    }

    @Test fun aWrongSizedChunkIsAnErrorAndIsRetried() {
        var broken = true
        source(body = { offset -> if (broken) ByteArray(3) else data.copyOfRange(offset.toInt(), offset.toInt() + 100) }).use { s ->
            try { s.read(0, ByteArray(10), 0, 10); fail("expected an IOException") } catch (_: IOException) { }
            broken = false
            assertEquals(10, s.read(0, ByteArray(10), 0, 10))
        }
    }

    @Test fun aFailedFetchSurfacesAsIOException() {
        source(body = { delay(1); throw IllegalStateException("Can't reach the computer.") }).use { s ->
            try { s.read(0, ByteArray(10), 0, 10); fail("expected an IOException") }
            catch (error: IOException) { assertEquals("Can't reach the computer.", error.message) }
        }
    }

    @Test fun theCacheStaysWithinItsLimit() {
        val fetches = AtomicInteger()
        source(fetches, cache = 2).use { s ->
            readAll(s, 0, 100)
            val before = fetches.get()
            s.read(0, ByteArray(10), 0, 10) // chunk 0 was evicted long ago
            assertTrue(fetches.get() > before)
        }
    }
}

class ZipEntriesTest {
    private val dir: File = Files.createTempDirectory("viewer-test").toFile()

    private fun zip(vararg entries: Pair<String, ByteArray>): File = File(dir, "test.zip").also { file ->
        ZipOutputStream(file.outputStream()).use { out ->
            for ((name, bytes) in entries) { out.putNextEntry(ZipEntry(name)); out.write(bytes); out.closeEntry() }
        }
    }

    @Test fun listsFilesNotFoldersInPathOrder() {
        val file = zip("b/notes.txt" to "hi".toByteArray(), "a/" to ByteArray(0), "A.md" to "# x".toByteArray())
        assertEquals(listOf("A.md", "b/notes.txt"), ZipEntries.list(file).map { it.path })
        assertEquals("notes.txt", ZipEntries.list(file)[1].name)
    }

    @Test fun extractsByNameOnlyIntoAFreshFolder() {
        val file = zip("../../evil/payload.sh" to "echo".toByteArray())
        val cache = File(dir, "cache")
        val out = ZipEntries.extract(file, "../../evil/payload.sh", cache)
        assertEquals("payload.sh", out.name)
        assertTrue(out.canonicalPath.startsWith(cache.canonicalPath))
        assertEquals("echo", out.readText())
    }

    @Test fun refusesEntriesOverTheCap() {
        val file = zip("big.bin" to ByteArray(5000))
        try { ZipEntries.extract(file, "big.bin", File(dir, "cache"), maxBytes = 1000); fail("expected an IOException") }
        catch (_: IOException) { }
    }

    @Test fun aDamagedZipIsAnIOException() {
        val file = File(dir, "bad.zip").apply { writeText("not a zip") }
        try { ZipEntries.list(file); fail("expected an IOException") } catch (_: IOException) { }
    }

    @Test fun safeNamesNeverEscape() {
        assertEquals("file", ZipEntries.safeName(".."))
        assertEquals("a_b", ZipEntries.safeName("a/b"))
        assertFalse(ZipEntries.safeName("x\u0000y").contains('\u0000'))
    }
}

class TextPreviewTest {
    @Test fun splitsLinesAndFlagsTruncation() {
        val file = Files.createTempFile("viewer", ".txt").toFile().apply { writeText("﻿one\r\ntwo\nthree\n") }
        assertEquals(TextPreview(listOf("one", "two", "three"), false), TextPreview.load(file))
        val cut = TextPreview.load(file, maxBytes = 8)
        assertTrue(cut.truncated)
    }
}
