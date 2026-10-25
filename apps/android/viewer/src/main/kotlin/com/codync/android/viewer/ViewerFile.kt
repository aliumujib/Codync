package com.codync.android.viewer

import java.io.File
import java.io.RandomAccessFile

/**
 * A file the viewer can open. Video and audio stream through [readChunk]; everything else, and Share and
 * Open with, use the whole [download]ed copy.
 */
interface ViewerFile {
    /** Stable for the same file: keys the viewer's state. */
    val id: String
    val name: String
    val size: Long

    /** Up to [CHUNK_BYTES] bytes starting at [offset]; fewer only at the end of the file. */
    suspend fun readChunk(offset: Long): ByteArray

    /** The whole file on this device, integrity-checked, reporting the bytes received so far. */
    suspend fun download(progress: (Long) -> Unit): File

    companion object {
        /** What one host `readFile` call returns at most. */
        const val CHUNK_BYTES = 384 * 1024
    }
}

/** A file already on this device: an entry taken out of a ZIP. */
class LocalViewerFile(private val file: File, override val name: String = file.name) : ViewerFile {
    override val id: String = file.absolutePath
    override val size: Long = file.length()

    override suspend fun readChunk(offset: Long): ByteArray = RandomAccessFile(file, "r").use { raf ->
        val length = minOf(ViewerFile.CHUNK_BYTES.toLong(), size - offset).toInt().coerceAtLeast(0)
        ByteArray(length).also { raf.seek(offset); raf.readFully(it) }
    }

    override suspend fun download(progress: (Long) -> Unit): File = file.also { progress(size) }
}
