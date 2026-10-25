package com.codync.android.viewer

import java.io.File
import java.io.IOException
import java.nio.charset.Charset
import java.util.zip.ZipException
import java.util.zip.ZipFile

/** One file inside a ZIP. */
data class ZipItem(val path: String, val size: Long) {
    /** The last path segment, which is all an extracted copy is ever named after. */
    val name: String get() = path.trimEnd('/').substringAfterLast('/').substringAfterLast('\\')
}

/** Lists a ZIP's files and takes one out, safely: never outside a fresh folder, never past a size cap. */
object ZipEntries {
    const val MAX_ENTRY_BYTES = 200L * 1024 * 1024

    /** Its files (not folders), in path order. Old archives with non-UTF-8 names are read as CP437. */
    fun list(zip: File): List<ZipItem> = open(zip).use { file ->
        file.entries().asSequence().filter { !it.isDirectory }.map { ZipItem(it.name, it.size) }.sortedBy { it.path.lowercase() }.toList()
    }

    /** Extracts [path] into a new folder under [cache] and returns the copy, named by its last segment only. */
    fun extract(zip: File, path: String, cache: File, maxBytes: Long = MAX_ENTRY_BYTES): File = open(zip).use { file ->
        val entry = file.getEntry(path) ?: throw IOException("That file isn't in the archive.")
        if (entry.isDirectory) throw IOException("That's a folder.")
        if (entry.size > maxBytes) throw IOException("That file is too large to open here.")
        val folder = File(cache, "zip-" + System.nanoTime()).apply { if (!mkdirs()) throw IOException("Couldn't prepare the file.") }
        val target = File(folder, safeName(ZipItem(path, entry.size).name))
        file.getInputStream(entry).use { input ->
            target.outputStream().use { output ->
                val buffer = ByteArray(64 * 1024)
                var total = 0L
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    total += read
                    if (total > maxBytes) { output.close(); folder.deleteRecursively(); throw IOException("That file is too large to open here.") }
                    output.write(buffer, 0, read)
                }
            }
        }
        target
    }

    internal fun safeName(name: String): String = name.replace(Regex("[\\u0000-\\u001F/\\\\]"), "_").takeUnless { it.isBlank() || it == "." || it == ".." } ?: "file"

    private fun open(zip: File): ZipFile {
        val utf8 = try { ZipFile(zip) } catch (error: ZipException) { throw IOException("This ZIP is damaged or isn't a ZIP.", error) }
        return try {
            utf8.entries().asSequence().forEach { it.name } // names that aren't UTF-8 fail here
            utf8
        } catch (_: IllegalArgumentException) {
            utf8.close()
            ZipFile(zip, Charset.forName("IBM437"))
        }
    }
}

/** The start of a text file, as lines, and whether the rest was left out. */
data class TextPreview(val lines: List<String>, val truncated: Boolean) {
    companion object {
        const val MAX_BYTES = 4 * 1024 * 1024

        fun load(file: File, maxBytes: Int = MAX_BYTES): TextPreview {
            val length = file.length()
            val bytes = file.inputStream().use { it.readNBytes(minOf(length, maxBytes.toLong()).toInt()) }
            val text = String(bytes, Charsets.UTF_8).removePrefix("﻿")
            val lines = text.split("\r\n", "\n", "\r").let { if (it.size > 1 && it.last().isEmpty()) it.dropLast(1) else it }
            return TextPreview(lines, length > maxBytes)
        }
    }
}
