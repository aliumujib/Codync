package com.codync.android.viewer

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSourceException
import androidx.media3.datasource.DataSpec

/** Media3's view of a [ChunkSource]: ExoPlayer reads the file at whatever position it needs, straight from the computer. */
@UnstableApi
internal class ChunkDataSource(private val source: ChunkSource, private val uri: Uri) : BaseDataSource(/* isNetwork = */ true) {
    private var position = 0L
    private var remaining = 0L
    private var opened = false

    override fun open(dataSpec: DataSpec): Long {
        transferInitializing(dataSpec)
        if (dataSpec.position > source.size) throw DataSourceException(PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE)
        position = dataSpec.position
        val left = source.size - position
        remaining = if (dataSpec.length == C.LENGTH_UNSET.toLong()) left else minOf(dataSpec.length, left)
        opened = true
        transferStarted(dataSpec)
        return remaining
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (remaining == 0L) return C.RESULT_END_OF_INPUT
        val count = source.read(position, buffer, offset, minOf(length.toLong(), remaining).toInt())
        if (count < 0) return C.RESULT_END_OF_INPUT
        position += count
        remaining -= count
        bytesTransferred(count)
        return count
    }

    override fun getUri(): Uri? = if (opened) uri else null

    override fun close() {
        if (opened) {
            opened = false
            transferEnded()
        }
    }

    class Factory(private val source: ChunkSource, private val uri: Uri) : DataSource.Factory {
        override fun createDataSource(): DataSource = ChunkDataSource(source, uri)
    }
}
