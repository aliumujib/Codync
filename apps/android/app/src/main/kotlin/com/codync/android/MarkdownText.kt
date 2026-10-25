package com.codync.android

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import com.codync.android.design.*
import com.codync.android.markdown.CodyncMarkdownText
import com.kyant.shapes.RoundedRectangle

val LocalNetworkImages = staticCompositionLocalOf<ImageLoader> { error("Missing image loader") }

/**
 * A bot's reply as Markdown, drawn by the `:markdown` renderer (from Control): headings, lists and task
 * lists, tables, fenced code with a copy button, quotes, footnotes, and file paths and links as chips.
 * Links open only for web and mail addresses; images show as their text, so a reply never makes the
 * phone fetch an address.
 */
@Composable fun MarkdownText(source: String, selectable: Boolean = true, color: Color = Palette.text) {
    val uris = LocalUriHandler.current
    CodyncMarkdownText(source, color = color, selectable = selectable, onLinkClicked = { link ->
        if (link.startsWith("https://") || link.startsWith("http://") || link.startsWith("mailto:")) runCatching { uris.openUri(link) }
    })
}

/** A monospaced box for commands and output (iOS `CodeBox`). */
@Composable fun CodeBox(text: String, style: TextStyle = Type.caption.monospaced) {
    Box(Modifier.fillMaxWidth().heightIn(max = 240.dp).clip(RoundedRectangle(8.dp)).background(Palette.codeBackground)
        .horizontalScroll(rememberScrollState())) {
        SelectionContainer { Text(text, Modifier.padding(8.dp), style = style, color = Palette.text, softWrap = false) }
    }
}
