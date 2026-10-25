package com.codync.android.viewer

import java.io.IOException
import java.io.InterruptedIOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking

/**
 * Random-access reads over a file the computer serves in fixed chunks (`readFile`): the chunk holding a
 * position is fetched once and kept in a small LRU, and the next one is fetched while the current one is
 * read, so playback doesn't wait a round trip per chunk and seeking back or looping doesn't refetch.
 */
class ChunkSource(
    val size: Long,
    private val chunkBytes: Int = ViewerFile.CHUNK_BYTES,
    private val cacheChunks: Int = DEFAULT_CACHE_CHUNKS,
    private val fetch: suspend (offset: Long) -> ByteArray,
) : AutoCloseable {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val chunks = object : LinkedHashMap<Long, Deferred<ByteArray>>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, Deferred<ByteArray>>) = size > cacheChunks
    }

    private val chunkCount: Long get() = (size + chunkBytes - 1) / chunkBytes

    /**
     * Copies up to [length] bytes at [position] into [target] from [offset]; returns how many, or -1 at the
     * end of the file. Blocks the calling thread (Media3's loader thread) until the chunk has arrived.
     */
    fun read(position: Long, target: ByteArray, offset: Int, length: Int): Int {
        if (position >= size) return -1
        if (length == 0) return 0
        val index = position / chunkBytes
        val chunk = await(index)
        if (index + 1 < chunkCount) chunk(index + 1) // read-ahead
        val within = (position - index * chunkBytes).toInt()
        val count = minOf(length, chunk.size - within)
        System.arraycopy(chunk, within, target, offset, count)
        return count
    }

    private fun await(index: Long): ByteArray {
        val pending = chunk(index)
        return try {
            runBlocking { pending.await() }
        } catch (interrupted: InterruptedException) {
            throw InterruptedIOException("Reading the file was interrupted.").apply { initCause(interrupted) }
        } catch (error: CancellationException) {
            forget(index, pending)
            throw InterruptedIOException("Reading the file was cancelled.").apply { initCause(error) }
        } catch (error: IOException) {
            forget(index, pending)
            throw error
        } catch (error: Exception) {
            forget(index, pending)
            throw IOException(error.message ?: "Couldn't read the file from the computer.", error)
        }
    }

    /** The chunk's fetch, started if it isn't cached; a failed one is dropped so the next read retries. */
    private fun chunk(index: Long): Deferred<ByteArray> = synchronized(chunks) {
        chunks.getOrPut(index) {
            scope.async {
                val start = index * chunkBytes
                val bytes = fetch(start)
                val expected = minOf(chunkBytes.toLong(), size - start).toInt()
                if (bytes.size != expected) throw IOException("The computer sent a damaged piece of the file.")
                bytes
            }
        }
    }

    private fun forget(index: Long, pending: Deferred<ByteArray>) = synchronized(chunks) {
        if (chunks[index] === pending) chunks.remove(index)
    }

    override fun close() = scope.cancel()

    companion object {
        /** About 24 MiB of 384 KiB chunks: a short video plays, seeks and loops without refetching. */
        const val DEFAULT_CACHE_CHUNKS = 64
    }
}
