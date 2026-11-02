package com.codync.android.viewer

import android.app.Activity
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.codync.android.design.CodyncTheme
import com.codync.android.design.Edge
import com.codync.android.design.ScrollEdge
import com.codync.android.design.GlassStage
import com.codync.android.design.IconButton
import com.codync.android.design.LocalDark
import com.codync.android.design.Palette
import com.codync.android.design.PrimaryButton
import com.codync.android.design.SecondaryButton
import com.codync.android.design.Spinner
import com.codync.android.design.Symbol
import com.codync.android.design.Type
import com.codync.android.design.glass
import com.codync.android.design.semibold
import com.kyant.shapes.Capsule
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * A chat's files, full screen, as a slideshow: swipe between [files], starting at [start]. Video and audio play
 * while they're read from the computer (only the file on screen); everything else opens once its checked copy has
 * downloaded. A ZIP's files open on top of it, and back returns to the list. Share and Open with always hand out the
 * checked copy.
 *
 * @param workDir where files taken out of a ZIP go; it must be somewhere [share] and [openWith] can hand out.
 * @param markdown the app's own Markdown renderer, so `.md` files read like chat messages. It insets text by
 *   `sidePadding` and lets tables run edge to edge.
 */
@Composable fun FileViewer(
    files: List<ViewerFile>,
    start: Int,
    workDir: File,
    close: () -> Unit,
    share: (File, String) -> Unit,
    openWith: (File, String) -> Unit,
    markdown: @Composable (text: String, sidePadding: Dp) -> Unit,
) {
    if (files.isEmpty()) { LaunchedEffect(Unit) { close() }; return }
    val scope = rememberCoroutineScope()
    // Files taken out of a ZIP, shown over the slideshow until back.
    var nested by remember { mutableStateOf(listOf<ViewerFile>()) }
    // Kept for the whole viewer, so swiping back or returning to a ZIP's list doesn't download it again.
    val loaders = remember { HashMap<String, Downloader>() }
    val loader = { file: ViewerFile -> loaders.getOrPut(file.id) { Downloader(file, scope) } }
    val pager = rememberPagerState(start.coerceIn(0, files.lastIndex)) { files.size }
    val back = { if (nested.isNotEmpty()) nested = nested.dropLast(1) else close() }
    BackHandler(onBack = back)
    Box(Modifier.fillMaxSize()) {
        HorizontalPager(pager, Modifier.fillMaxSize(), key = { files[it].id }, userScrollEnabled = nested.isEmpty()) { page ->
            val file = files[page]
            FilePage(file, loader(file), nested = false, back, workDir, share, openWith, markdown,
                active = page == pager.settledPage && nested.isEmpty(),
                position = if (files.size > 1) "${page + 1} of ${files.size}" else null) { nested = nested + it }
        }
        nested.lastOrNull()?.let { top ->
            key(top.id) { FilePage(top, loader(top), nested = true, back, workDir, share, openWith, markdown, active = true, position = null) { nested = nested + it } }
        }
    }
}

/** One file, full screen (see the slideshow above). */
@Composable fun FileViewer(
    file: ViewerFile,
    workDir: File,
    close: () -> Unit,
    share: (File, String) -> Unit,
    openWith: (File, String) -> Unit,
    markdown: @Composable (text: String, sidePadding: Dp) -> Unit,
) = FileViewer(listOf(file), 0, workDir, close, share, openWith, markdown)

/** Where a file's checked copy is. */
internal sealed interface Load {
    data class Fetching(val received: Long) : Load
    data class Ready(val file: File) : Load
    data class Failed(val message: String) : Load
}

/** Downloads a file once, on demand, and runs whatever was waiting for it. */
internal class Downloader(private val file: ViewerFile, private val scope: CoroutineScope) {
    var state by mutableStateOf<Load?>(null)
        private set
    private var job: Job? = null
    private val waiting = mutableListOf<(File) -> Unit>()

    /** Starts the download if it isn't running or done; [then] runs with the copy once it's here. */
    fun fetch(then: ((File) -> Unit)? = null) {
        (state as? Load.Ready)?.let { ready -> then?.invoke(ready.file); return }
        if (then != null) waiting += then
        if (job?.isActive == true) return
        state = Load.Fetching(0)
        job = scope.launch {
            try {
                val copy = file.download { received -> state = Load.Fetching(received) }
                state = Load.Ready(copy)
                val run = waiting.toList()
                waiting.clear()
                run.forEach { it(copy) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                waiting.clear()
                state = Load.Failed(error.message ?: "Couldn't download the file.")
            }
        }
    }
}

@Composable private fun FilePage(
    file: ViewerFile,
    loader: Downloader,
    nested: Boolean,
    back: () -> Unit,
    workDir: File,
    share: (File, String) -> Unit,
    openWith: (File, String) -> Unit,
    markdown: @Composable (text: String, sidePadding: Dp) -> Unit,
    active: Boolean,
    position: String?,
    open: (ViewerFile) -> Unit,
) {
    val named = remember(file.id) { FileKind.of(file.name) }
    val load = loader.state
    val copy = (load as? Load.Ready)?.file
    // A name that says nothing is sniffed once the copy is here.
    val kind = remember(file.id, copy) { if (named == FileKind.Other && copy != null) sniff(copy) else named }
    // Only the file on screen downloads or plays; its neighbours in the slideshow wait until they're swiped to.
    LaunchedEffect(file.id, active) { if (active && !named.streams) loader.fetch() }
    var chrome by remember(file.id) { mutableStateOf(true) }
    val media = kind == FileKind.Video || kind == FileKind.Image
    val player = if (kind.streams && active) rememberStreamingPlayer(file) else null
    val playback = player?.let { rememberPlayback(it) }
    if (media && active) LightSystemBarIcons()

    CodyncTheme(dark = media || LocalDark.current) {
        val insets = viewerInsets()
        GlassStage(Modifier.fillMaxSize().background(if (media) Color.Black else Palette.background), chrome = {
            // Scrolling pages blur and fade under the bars, as on iOS, so the header stays readable.
            if (!media && !kind.streams) {
                ScrollEdge(Edge.Top, insets.calculateTopPadding(), Modifier.align(Alignment.TopCenter))
                ScrollEdge(Edge.Bottom, insets.calculateBottomPadding(), Modifier.align(Alignment.BottomCenter))
            }
            AnimatedVisibility(chrome || !media, Modifier.align(Alignment.TopCenter), enter = fadeIn(), exit = fadeOut()) {
                Header(file.name, listOfNotNull(position, detail(file, kind, load)).joinToString(" · "), nested, back,
                    share = { loader.fetch { share(it, file.name) } }, openWith = { loader.fetch { openWith(it, file.name) } })
            }
            if (player != null && playback?.error == null) {
                AnimatedVisibility(chrome || kind == FileKind.Audio, Modifier.align(Alignment.BottomCenter), enter = fadeIn(), exit = fadeOut()) {
                    MediaControls(player)
                }
            }
        }) {
            when {
                player != null && playback != null -> MediaPane(file, kind, player, playback, toggleChrome = { chrome = !chrome })
                kind.streams -> Symbol(kind.symbol, Modifier.align(Alignment.Center), size = 48.dp, tint = Palette.secondary)
                load is Load.Failed -> Message(kind.symbol, load.message) { SecondaryButton("Try again") { loader.fetch() } }
                copy == null && !active -> Symbol(kind.symbol, Modifier.align(Alignment.Center), size = 48.dp, tint = Palette.secondary)
                copy == null -> Downloading(file, (load as? Load.Fetching)?.received ?: 0)
                else -> when (kind) {
                    FileKind.Image -> ImagePane(copy, file.name, toggleChrome = { chrome = !chrome })
                    FileKind.Pdf -> PdfPane(copy, insets)
                    FileKind.Markdown -> MarkdownPane(copy, insets, markdown)
                    FileKind.Text -> TextPane(copy, insets)
                    FileKind.Zip -> ZipPane(copy, workDir, insets, open)
                    else -> Message(kind.symbol, "No preview for this file", file.name) {
                        PrimaryButton("Open with") { openWith(copy, file.name) }
                        SecondaryButton("Share") { share(copy, file.name) }
                    }
                }
            }
        }
    }
}

/** Glass top bar: close (or back from a ZIP's file), the name and what's happening, Share and Open with. */
@Composable private fun Header(title: String, detail: String, nested: Boolean, back: () -> Unit, share: () -> Unit, openWith: () -> Unit) {
    Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.glass(Capsule()).padding(4.dp)) {
            if (nested) IconButton("Back", "chevron.left", action = back) else IconButton("Close", "xmark", action = back)
        }
        Column(Modifier.weight(1f).glass(Capsule()).padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, style = Type.subheadline.semibold, color = Palette.text, maxLines = 1, overflow = TextOverflow.MiddleEllipsis)
            Text(detail, style = Type.caption2, color = Palette.secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Row(Modifier.glass(Capsule()).padding(4.dp)) {
            IconButton("Share", "square.and.arrow.up", action = share)
            IconButton("Open in another app", "arrow.up.right", action = openWith)
        }
    }
}

private fun detail(file: ViewerFile, kind: FileKind, load: Load?): String {
    val size = formatBytes(file.size)
    return when {
        load is Load.Fetching && kind.streams -> "$size · Preparing to share ${percent(load.received, file.size)}"
        load is Load.Fetching -> "Downloading ${formatBytes(load.received)} of $size"
        else -> size
    }
}

private fun percent(part: Long, whole: Long) = if (whole <= 0) "" else "${(part * 100 / whole).coerceIn(0, 100)}%"

/** Room the glass bars take: content scrolls under them but starts and ends clear of them. */
@Composable internal fun viewerInsets(): PaddingValues {
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    return PaddingValues(top = top + 64.dp, bottom = bottom + 24.dp)
}

@Composable private fun BoxScope.Downloading(file: ViewerFile, received: Long) {
    Column(Modifier.align(Alignment.Center).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Spinner(22.dp)
        ProgressTrack(if (file.size > 0) received.toFloat() / file.size else 0f, Modifier.widthIn(max = 220.dp).fillMaxWidth())
        Text("Downloading ${formatBytes(received)} of ${formatBytes(file.size)}", style = Type.footnote, color = Palette.secondary)
    }
}

/** A centred symbol, a line, an optional second line and actions: errors and files with no preview. */
@Composable internal fun BoxScope.Message(symbol: String, title: String, subtitle: String? = null, actions: @Composable () -> Unit = {}) {
    Column(Modifier.align(Alignment.Center).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Symbol(symbol, size = 40.dp, tint = Palette.secondary)
        Text(title, style = Type.headline, color = Palette.text, textAlign = TextAlign.Center)
        if (subtitle != null) Text(subtitle, style = Type.footnote, color = Palette.secondary, textAlign = TextAlign.Center,
            maxLines = 2, overflow = TextOverflow.MiddleEllipsis)
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) { actions() }
    }
}

/** White status and navigation bar icons while a photo or video fills the screen in black, even in light mode. */
@Composable private fun LightSystemBarIcons() {
    val view = LocalView.current
    DisposableEffect(view) {
        var context = view.context
        while (context is ContextWrapper && context !is Activity) context = context.baseContext
        val window = (context as? Activity)?.window ?: return@DisposableEffect onDispose { }
        val bars = WindowCompat.getInsetsController(window, view)
        val status = bars.isAppearanceLightStatusBars
        val navigation = bars.isAppearanceLightNavigationBars
        bars.isAppearanceLightStatusBars = false
        bars.isAppearanceLightNavigationBars = false
        onDispose {
            bars.isAppearanceLightStatusBars = status
            bars.isAppearanceLightNavigationBars = navigation
        }
    }
}

private fun sniff(file: File): FileKind {
    val head = file.inputStream().use { it.readNBytes(4096) }
    return FileKind.sniff(head)
}

internal fun formatBytes(size: Long): String = when {
    size >= 1024L * 1024 * 1024 -> "%.1f GB".format(size / (1024.0 * 1024 * 1024))
    size >= 1024 * 1024 -> "%.1f MB".format(size / (1024.0 * 1024))
    size >= 1024 -> "%.0f KB".format(size / 1024.0)
    else -> "$size bytes"
}
