package com.codync.android.markdown

import com.codync.android.markdown.model.*

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalDensity

@Composable
internal fun MarkdownBlockquoteView(
    block: MarkdownBlockquote,
    theme: MarkdownTheme,
    modifier: Modifier = Modifier,
    onLinkClicked: (String) -> Unit,
    onFilePathClicked: (MarkdownInline.FilePath) -> Unit,
    onCommandClicked: (String) -> Unit,
    tokenContext: InlineTokenContext,
) {
    val railColor = theme.blockquoteAccent
    val railWidthPx: Float
    val railInsetPx: Float
    with(LocalDensity.current) {
        railWidthPx = RailWidth.toPx()
        railInsetPx = RailInset.toPx()
    }
    MarkdownNestedBlocks(
        blocks = block.blocks,
        theme = theme,
        onLinkClicked = onLinkClicked,
        onFilePathClicked = onFilePathClicked,
        onCommandClicked = onCommandClicked,
        tokenContext = tokenContext,
        modifier = modifier
            .clip(RoundedCornerShape(QuoteRadius))
            .background(theme.blockquoteBackground)
            .drawBehind {
                val radius = CornerRadius(railWidthPx / 2f, railWidthPx / 2f)
                drawRoundRect(
                    color = railColor,
                    topLeft = Offset(RailLeftPx, railInsetPx),
                    size = Size(railWidthPx, (size.height - railInsetPx * 2f).coerceAtLeast(0f)),
                    cornerRadius = radius,
                )
            }
            .padding(
                start = InteriorStartPadding,
                end = InteriorPadding,
                top = InteriorPadding,
                bottom = InteriorPadding,
            ),
    )
}

private const val RailLeftPx = 0f
private val QuoteRadius = 10.dp
private val RailWidth = 3.dp
private val RailInset = 6.dp
private val InteriorStartPadding = 14.dp
private val InteriorPadding = 10.dp
