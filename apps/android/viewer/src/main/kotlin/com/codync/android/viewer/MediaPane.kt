package com.codync.android.viewer

import android.net.Uri
import androidx.annotation.OptIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.compose.ContentFrame
import androidx.media3.ui.compose.SURFACE_TYPE_TEXTURE_VIEW
import androidx.media3.ui.compose.state.rememberPlayPauseButtonState
import androidx.media3.ui.compose.state.rememberProgressStateWithTickInterval
import com.codync.android.design.IconButton
import com.codync.android.design.Palette
import com.codync.android.design.SecondaryButton
import com.codync.android.design.Spinner
import com.codync.android.design.Symbol
import com.codync.android.design.Type
import com.codync.android.design.glass
import com.kyant.shapes.Capsule

/**
 * A player that reads the file from the computer as it plays (see [ChunkSource]). Paused when the app
 * goes to the background; released with the viewer.
 */
@OptIn(UnstableApi::class)
@Composable internal fun rememberStreamingPlayer(file: ViewerFile): ExoPlayer {
    val context = LocalContext.current
    val (player, source) = remember(file.id) {
        val source = ChunkSource(file.size) { offset -> file.readChunk(offset) }
        val uri = Uri.Builder().scheme("codync-file").authority("computer").appendPath(file.id).appendPath(file.name).build()
        val player = ExoPlayer.Builder(context)
            .setMediaSourceFactory(ProgressiveMediaSource.Factory(ChunkDataSource.Factory(source, uri)))
            .setAudioAttributes(AudioAttributes.DEFAULT, /* handleAudioFocus = */ true)
            .setHandleAudioBecomingNoisy(true)
            .build()
        player.setMediaItem(MediaItem.fromUri(uri))
        player.prepare()
        player.playWhenReady = true
        player to source
    }
    DisposableEffect(player) { onDispose { player.release(); source.close() } }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, player) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) player.pause() }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    return player
}

/** What the player is doing that the screen shows: waiting for data, or stopped by an error. */
internal class Playback {
    var buffering by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
}

@Composable internal fun rememberPlayback(player: Player): Playback {
    val playback = remember(player) { Playback() }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                playback.buffering = playbackState == Player.STATE_BUFFERING
                if (playbackState == Player.STATE_READY) playback.error = null
            }
            override fun onPlayerError(error: PlaybackException) {
                playback.error = playbackMessage(error)
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }
    return playback
}

private fun playbackMessage(error: PlaybackException): String {
    var cause: Throwable? = error.cause
    while (cause?.cause != null && cause.message.isNullOrBlank()) cause = cause.cause
    return when (error.errorCode) {
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED, PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED ->
            "This phone can't play this file's format."
        else -> cause?.message?.takeIf { it.isNotBlank() } ?: "Couldn't play this file."
    }
}

/** Video edge to edge (tap to hide the bars), or for audio a waveform and the name. */
@OptIn(UnstableApi::class)
@Composable internal fun BoxScope.MediaPane(file: ViewerFile, kind: FileKind, player: Player, playback: Playback, toggleChrome: () -> Unit) {
    val error = playback.error
    if (error != null) {
        Message(kind.symbol, error) { SecondaryButton("Try again") { playback.error = null; player.prepare(); player.play() } }
        return
    }
    if (kind == FileKind.Video) {
        Box(Modifier.fillMaxSize().clickable(remember { MutableInteractionSource() }, null, onClick = toggleChrome), contentAlignment = Alignment.Center) {
            ContentFrame(player, Modifier.fillMaxSize(), surfaceType = SURFACE_TYPE_TEXTURE_VIEW, contentScale = ContentScale.Fit, keepContentOnReset = true)
        }
    } else {
        Column(Modifier.align(Alignment.Center).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Symbol("waveform", size = 72.dp, tint = Palette.secondary)
            Text(file.name, style = Type.headline, color = Palette.text, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.MiddleEllipsis)
            Text(formatBytes(file.size), style = Type.footnote, color = Palette.secondary)
        }
    }
    if (playback.buffering) Spinner(28.dp, Modifier.align(Alignment.Center), color = Palette.text)
}

/** Glass bar at the bottom: play/pause, elapsed, a seek bar that shows what has loaded, total. */
@OptIn(UnstableApi::class)
@Composable internal fun MediaControls(player: Player) {
    val playPause = rememberPlayPauseButtonState(player)
    val progress = rememberProgressStateWithTickInterval(player, tickIntervalMs = 250)
    val duration = progress.durationMs.takeIf { it != C.TIME_UNSET && it > 0 } ?: 0L
    Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 12.dp, vertical = 10.dp)
        .glass(Capsule()).padding(start = 6.dp, end = 18.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (playPause.showPlay) IconButton("Play", "play.fill", enabled = playPause.isEnabled) { playPause.onClick() }
        else IconButton("Pause", "pause.fill", enabled = playPause.isEnabled) { playPause.onClick() }
        Clock(progress.currentPositionMs)
        SeekBar(progress.currentPositionMs, progress.bufferedPositionMs, duration, { player.seekTo(it) }, Modifier.weight(1f))
        Clock(duration)
    }
}

@Composable private fun Clock(ms: Long) {
    Text(clock(ms), style = Type.caption.copy(fontFeatureSettings = "tnum"), color = Palette.secondary)
}

internal fun clock(ms: Long): String {
    val total = (ms.coerceAtLeast(0) / 1000)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

/** Drag or tap to seek; the lighter fill is what has already loaded from the computer. */
@Composable private fun SeekBar(position: Long, buffered: Long, duration: Long, seek: (Long) -> Unit, modifier: Modifier) {
    var dragging by remember { mutableStateOf<Float?>(null) }
    val enabled = duration > 0
    val played = dragging ?: if (enabled) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
    val loaded = if (enabled) (buffered.toFloat() / duration).coerceIn(0f, 1f) else 0f
    val ink = Palette.text
    Canvas(modifier.height(30.dp)
        .pointerInput(duration) { if (enabled) detectTapGestures { seek(((it.x / size.width).coerceIn(0f, 1f) * duration).toLong()) } }
        .pointerInput(duration) {
            if (enabled) detectHorizontalDragGestures(
                onDragStart = { dragging = (it.x / size.width).coerceIn(0f, 1f) },
                onDragEnd = { dragging?.let { seek((it * duration).toLong()) }; dragging = null },
                onDragCancel = { dragging = null },
            ) { change, _ -> dragging = (change.position.x / size.width).coerceIn(0f, 1f) }
        }
        .semantics {
            contentDescription = "Playback position"
            progressBarRangeInfo = ProgressBarRangeInfo(played, 0f..1f)
            if (enabled) setProgress { seek((it.coerceIn(0f, 1f) * duration).toLong()); true }
        }) {
        val mid = size.height / 2
        val thick = 4.dp.toPx()
        fun bar(fraction: Float, alpha: Float) = drawRoundRect(ink.copy(alpha = alpha), Offset(0f, mid - thick / 2),
            Size(size.width * fraction, thick), CornerRadius(thick / 2))
        bar(1f, 0.16f)
        bar(loaded, 0.3f)
        bar(played, 1f)
        drawCircle(ink, radius = (if (dragging != null) 8.dp else 6.dp).toPx(), center = Offset(size.width * played, mid))
    }
}

/** A thin, still progress bar in the seek bar's style. */
@Composable internal fun ProgressTrack(fraction: Float, modifier: Modifier = Modifier) {
    val ink = Palette.text
    Canvas(modifier.height(4.dp)) {
        val thick = size.height
        drawRoundRect(ink.copy(alpha = 0.16f), Offset.Zero, Size(size.width, thick), CornerRadius(thick / 2))
        drawRoundRect(ink, Offset.Zero, Size(size.width * fraction.coerceIn(0f, 1f), thick), CornerRadius(thick / 2))
    }
}
