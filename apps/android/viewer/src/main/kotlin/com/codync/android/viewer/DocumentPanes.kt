package com.codync.android.viewer

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.codync.android.design.GlassStage
import com.codync.android.design.Hairline
import com.codync.android.design.Palette
import com.codync.android.design.Spinner
import com.codync.android.design.Symbol
import com.codync.android.design.Type
import com.codync.android.design.glass
import com.codync.android.design.semibold
import com.kyant.shapes.Capsule
import com.kyant.shapes.RoundedRectangle
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import me.saket.telephoto.zoomable.ZoomSpec
import me.saket.telephoto.zoomable.ZoomableContentLocation
import me.saket.telephoto.zoomable.coil3.ZoomableAsyncImage
import me.saket.telephoto.zoomable.rememberZoomableImageState
import me.saket.telephoto.zoomable.rememberZoomableState
import me.saket.telephoto.zoomable.zoomable

/** A picture with pinch-zoom, pan and double-tap; a tap shows or hides the bars. A spinner until it's decoded. */
@Composable internal fun BoxScope.ImagePane(file: File, name: String, toggleChrome: () -> Unit) {
    val state = rememberZoomableImageState()
    ZoomableAsyncImage(model = file, contentDescription = name, modifier = Modifier.fillMaxSize(), state = state, onClick = { toggleChrome() })
    if (!state.isImageDisplayed) Spinner(22.dp, Modifier.align(Alignment.Center), color = Palette.text)
}

/** The result of loading something off the main thread for a pane. */
private sealed interface Loaded<out T> {
    data object Waiting : Loaded<Nothing>
    data class Done<T>(val value: T) : Loaded<T>
    data class Failed(val message: String) : Loaded<Nothing>
}

@Composable private fun <T> rememberLoaded(key: Any, load: suspend () -> T) = produceState<Loaded<T>>(Loaded.Waiting, key) {
    value = try { Loaded.Done(withContext(Dispatchers.IO) { load() }) } catch (error: Exception) {
        Loaded.Failed(error.message ?: "Couldn't open this file.")
    }
}

/** A PDF open for drawing pages; Android's renderer handles one page at a time, so they take turns. */
private class PdfDocument(file: File) : AutoCloseable {
    private val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    private val renderer = try { PdfRenderer(descriptor) } catch (error: Exception) { descriptor.close(); throw error }
    private val lock = Mutex()
    val pages: List<Size> = (0 until renderer.pageCount).map { i -> renderer.openPage(i).use { Size(it.width.toFloat(), it.height.toFloat()) } }

    suspend fun render(index: Int, widthPx: Int): Bitmap = lock.withLock {
        withContext(Dispatchers.IO) {
            renderer.openPage(index).use { page ->
                val width = widthPx.coerceIn(200, 2400)
                val height = (width * page.height.toFloat() / page.width).toInt().coerceAtLeast(1)
                Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also {
                    it.eraseColor(AndroidColor.WHITE)
                    page.render(it, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                }
            }
        }
    }

    override fun close() { renderer.close(); descriptor.close() }
}

private fun pdfError(error: Exception): String = when (error) {
    is SecurityException -> "This PDF is password-protected."
    else -> "Couldn't open this PDF."
}

/** Pages one under another; tap a page to zoom into it. A glass chip shows where you are. */
@Composable internal fun BoxScope.PdfPane(file: File, insets: PaddingValues) {
    val document by produceState<Loaded<PdfDocument>>(Loaded.Waiting, file) {
        val opened = try { Loaded.Done(withContext(Dispatchers.IO) { PdfDocument(file) }) } catch (error: Exception) { Loaded.Failed(pdfError(error)) }
        value = opened
        awaitDispose { (opened as? Loaded.Done)?.value?.close() }
    }
    when (val loaded = document) {
        Loaded.Waiting -> Spinner(22.dp, Modifier.align(Alignment.Center))
        is Loaded.Failed -> Message("doc.richtext", loaded.message)
        is Loaded.Done -> PdfPages(loaded.value, insets)
    }
}

@Composable private fun PdfPages(document: PdfDocument, insets: PaddingValues) {
    val list = rememberLazyListState()
    var zoomed by remember { mutableStateOf<Int?>(null) }
    GlassStage(Modifier.fillMaxSize(), chrome = {
        if (document.pages.size > 1) {
            Text("${list.firstVisibleItemIndex + 1} of ${document.pages.size}", Modifier.align(Alignment.BottomCenter)
                .navigationBarsPadding().padding(bottom = 16.dp).glass(Capsule()).padding(horizontal = 14.dp, vertical = 7.dp),
                style = Type.footnote.semibold, color = Palette.text)
        }
    }) {
        // A faint grey ground so white pages stand out in light mode too.
        BoxWithConstraints(Modifier.fillMaxSize().background(Palette.surface)) {
            val widthPx = with(LocalDensity.current) { maxWidth.roundToPx() }
            LazyColumn(Modifier.fillMaxSize(), state = list, contentPadding = PaddingValues(start = 12.dp, end = 12.dp,
                top = insets.calculateTopPadding(), bottom = insets.calculateBottomPadding() + 48.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                itemsIndexed(document.pages) { index, page ->
                    PdfPageView(document, index, page, widthPx * 2, Modifier.clickable { zoomed = index })
                }
            }
        }
    }
    zoomed?.let { index -> PdfPageZoom(document, index, widthPx = 2400) { zoomed = null } }
}

@Composable private fun PdfPageView(document: PdfDocument, index: Int, page: Size, widthPx: Int, modifier: Modifier) {
    val bitmap by produceState<ImageBitmap?>(null, document, index) { value = document.render(index, widthPx).asImageBitmap() }
    Box(modifier.fillMaxWidth().aspectRatio(page.width / page.height).clip(RoundedRectangle(4.dp)).background(Color.White)
        .border(1.dp, Palette.border, RoundedRectangle(4.dp)).semantics { contentDescription = "Page ${index + 1}" }, contentAlignment = Alignment.Center) {
        bitmap?.let { Image(it, null, Modifier.fillMaxSize()) } ?: Spinner(18.dp, color = Color.Gray)
    }
}

/** One page, full screen, sharp enough to zoom into; back or a tap outside the page returns to the list. */
@Composable private fun PdfPageZoom(document: PdfDocument, index: Int, widthPx: Int, close: () -> Unit) {
    BackHandler(onBack = close)
    val bitmap by produceState<ImageBitmap?>(null, document, index) { value = document.render(index, widthPx).asImageBitmap() }
    val state = rememberZoomableState(ZoomSpec(maxZoomFactor = 5f))
    LaunchedEffect(bitmap) { bitmap?.let { state.setContentLocation(ZoomableContentLocation.scaledInsideAndCenterAligned(Size(it.width.toFloat(), it.height.toFloat()))) } }
    Box(Modifier.fillMaxSize().background(Palette.surface).zoomable(state, onClick = { close() }), contentAlignment = Alignment.Center) {
        bitmap?.let { Image(it, "Page ${index + 1}", Modifier.fillMaxSize()) } ?: Spinner(22.dp)
    }
}

/** A Markdown file, read like a chat message. */
@Composable internal fun BoxScope.MarkdownPane(file: File, insets: PaddingValues, markdown: @Composable (String, Dp) -> Unit) {
    val text by rememberLoaded(file) { TextPreview.load(file) }
    when (val loaded = text) {
        Loaded.Waiting -> Spinner(22.dp, Modifier.align(Alignment.Center))
        is Loaded.Failed -> Message("doc.text", loaded.message)
        is Loaded.Done -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(insets)) {
            markdown(loaded.value.lines.joinToString("\n"), 18.dp)
            if (loaded.value.truncated) Box(Modifier.padding(horizontal = 18.dp)) { Truncated() }
        }
    }
}

/** Plain text and code: monospace lines, selectable. */
@Composable internal fun BoxScope.TextPane(file: File, insets: PaddingValues) {
    val text by rememberLoaded(file) { TextPreview.load(file) }
    when (val loaded = text) {
        Loaded.Waiting -> Spinner(22.dp, Modifier.align(Alignment.Center))
        is Loaded.Failed -> Message("doc.text", loaded.message)
        is Loaded.Done -> SelectionContainer {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp,
                top = insets.calculateTopPadding(), bottom = insets.calculateBottomPadding())) {
                items(loaded.value.lines.size) { i ->
                    Text(loaded.value.lines[i].ifEmpty { " " }, style = Type.footnote.copy(fontFamily = FontFamily.Monospace), color = Palette.text)
                }
                if (loaded.value.truncated) item { Truncated() }
            }
        }
    }
}

@Composable private fun Truncated() {
    Text("Showing the first ${formatBytes(TextPreview.MAX_BYTES.toLong())}. Share it or open it in another app to see the rest.",
        Modifier.padding(vertical = 16.dp), style = Type.footnote, color = Palette.secondary)
}

/** A ZIP's files; tapping one takes it out and opens it on top. */
@Composable internal fun BoxScope.ZipPane(file: File, workDir: File, insets: PaddingValues, open: (ViewerFile) -> Unit) {
    val entries by rememberLoaded(file) { ZipEntries.list(file) }
    val scope = rememberCoroutineScope()
    var opening by remember { mutableStateOf<String?>(null) }
    var problem by remember { mutableStateOf<String?>(null) }
    when (val loaded = entries) {
        Loaded.Waiting -> Spinner(22.dp, Modifier.align(Alignment.Center))
        is Loaded.Failed -> Message("doc.zipper", loaded.message)
        is Loaded.Done -> LazyColumn(Modifier.fillMaxSize(), contentPadding = insets) {
            item {
                Text(if (loaded.value.size == 1) "1 file" else "${loaded.value.size} files", Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    style = Type.footnote.semibold, color = Palette.secondary)
                problem?.let { Text(it, Modifier.padding(horizontal = 20.dp, vertical = 4.dp), style = Type.footnote, color = Palette.text) }
            }
            itemsIndexed(loaded.value) { index, item ->
                if (index > 0) Hairline(Modifier.padding(start = 56.dp))
                ZipRow(item, busy = opening == item.path) {
                    if (opening != null) return@ZipRow
                    opening = item.path
                    problem = null
                    scope.launch {
                        try {
                            val out = withContext(Dispatchers.IO) { ZipEntries.extract(file, item.path, File(workDir, "zip")) }
                            open(LocalViewerFile(out, item.name))
                        } catch (error: IOException) {
                            problem = error.message ?: "Couldn't open that file."
                        } finally { opening = null }
                    }
                }
            }
        }
    }
}

@Composable private fun ZipRow(item: ZipItem, busy: Boolean, tap: () -> Unit) {
    val folder = item.path.trimEnd('/').substringBeforeLast('/', "")
    Row(Modifier.fillMaxWidth().clickable(onClick = tap).padding(horizontal = 20.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) {
            if (busy) Spinner(16.dp) else Symbol(FileKind.of(item.name).symbol, size = 22.dp, tint = Palette.secondary)
        }
        Column(Modifier.weight(1f)) {
            Text(item.name, style = Type.subheadline, color = Palette.text, maxLines = 1, overflow = TextOverflow.MiddleEllipsis)
            if (folder.isNotEmpty()) Text(folder, style = Type.caption2, color = Palette.tertiary, maxLines = 1, overflow = TextOverflow.MiddleEllipsis)
        }
        Text(formatBytes(item.size.coerceAtLeast(0)), style = Type.caption2, color = Palette.secondary)
    }
}
