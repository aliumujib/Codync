package com.codync.android.markdown

import com.codync.android.design.Symbol

import com.codync.android.markdown.model.*

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp

@Composable
internal fun MarkdownBulletListView(
    block: MarkdownBulletList,
    theme: MarkdownTheme,
    modifier: Modifier = Modifier,
    onLinkClicked: (String) -> Unit,
    onFilePathClicked: (MarkdownInline.FilePath) -> Unit,
    onCommandClicked: (String) -> Unit,
    tokenContext: InlineTokenContext,
    depth: Int = 0,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(ListItemSpacing),
    ) {
        block.items.forEach { item ->
            MarkdownListRow(
                item = item,
                marker = { modifierArg ->
                    MarkdownListMarker(
                        marker = item.marker,
                        plainMarker = bulletGlyphForDepth(depth),
                        plainStyle = theme.bulletMarkerStyle,
                        theme = theme,
                        modifier = modifierArg,
                    )
                },
                markerWidth = BulletMarkerWidth,
                theme = theme,
                onLinkClicked = onLinkClicked,
                onFilePathClicked = onFilePathClicked,
                onCommandClicked = onCommandClicked,
                tokenContext = tokenContext,
                depth = depth,
            )
        }
    }
}

@Composable
internal fun MarkdownOrderedListView(
    block: MarkdownOrderedList,
    theme: MarkdownTheme,
    modifier: Modifier = Modifier,
    onLinkClicked: (String) -> Unit,
    onFilePathClicked: (MarkdownInline.FilePath) -> Unit,
    onCommandClicked: (String) -> Unit,
    tokenContext: InlineTokenContext,
    depth: Int = 0,
) {
    val markerWidth = orderedMarkerWidth(block, theme)
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(ListItemSpacing),
    ) {
        block.items.forEachIndexed { index, item ->
            MarkdownListRow(
                item = item,
                marker = { modifierArg ->
                    MarkdownListMarker(
                        marker = item.marker,
                        plainMarker = "${block.displayNumber(index)}.",
                        plainStyle = theme.orderedMarkerStyle,
                        theme = theme,
                        modifier = modifierArg,
                    )
                },
                markerWidth = markerWidth,
                theme = theme,
                onLinkClicked = onLinkClicked,
                onFilePathClicked = onFilePathClicked,
                onCommandClicked = onCommandClicked,
                tokenContext = tokenContext,
                depth = depth,
            )
        }
    }
}

private fun MarkdownOrderedList.displayNumber(index: Int): Int =
    items[index].number ?: (startNumber + index)

@Composable
private fun orderedMarkerWidth(block: MarkdownOrderedList, theme: MarkdownTheme): Dp {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val widestMarker = block.items.indices
        .map { index -> block.displayNumber(index) }
        .maxByOrNull { it.toString().length }
        ?: block.startNumber
    return remember(widestMarker, theme.orderedMarkerStyle, theme.listIndent, density) {
        val markerPx = measurer.measure(
            text = "$widestMarker.",
            style = theme.orderedMarkerStyle,
            softWrap = false,
            maxLines = 1,
        ).size.width
        maxOf(theme.listIndent, with(density) { markerPx.toDp() } + OrderedMarkerGap)
    }
}

@Composable
private fun MarkdownListRow(
    item: MarkdownListItem,
    marker: @Composable (Modifier) -> Unit,
    markerWidth: Dp,
    theme: MarkdownTheme,
    onLinkClicked: (String) -> Unit,
    onFilePathClicked: (MarkdownInline.FilePath) -> Unit,
    onCommandClicked: (String) -> Unit,
    tokenContext: InlineTokenContext,
    depth: Int,
) {
    Column(verticalArrangement = Arrangement.spacedBy(NestedGap)) {
        Row(verticalAlignment = Alignment.Top) {
            marker(Modifier.width(markerWidth))
            MarkdownRichText(
                nodes = item.children,
                theme = theme,
                modifier = Modifier.weight(1f),
                style = theme.listItemStyle(item.marker),
                onLinkClicked = onLinkClicked,
                onFilePathClicked = onFilePathClicked,
                onCommandClicked = onCommandClicked,
                tokenContext = tokenContext,
            )
        }
        if (item.nestedBlocks.isNotEmpty()) {
            MarkdownNestedListChildren(
                blocks = item.nestedBlocks,
                theme = theme,
                onLinkClicked = onLinkClicked,
                onFilePathClicked = onFilePathClicked,
                onCommandClicked = onCommandClicked,
                tokenContext = tokenContext,
                depth = depth,
                indent = markerWidth,
            )
        }
    }
}

@Composable
private fun MarkdownNestedListChildren(
    blocks: List<MarkdownBlock>,
    theme: MarkdownTheme,
    onLinkClicked: (String) -> Unit,
    onFilePathClicked: (MarkdownInline.FilePath) -> Unit,
    onCommandClicked: (String) -> Unit,
    tokenContext: InlineTokenContext,
    depth: Int,
    indent: Dp,
) {

    val effectiveIndent = if (depth >= MaxIndentedDepth) DeepIndentStep else indent
    Column(
        modifier = Modifier.padding(start = effectiveIndent),
        verticalArrangement = Arrangement.spacedBy(theme.nestedBlockSpacing),
    ) {
        blocks.forEach { block ->
            when (block) {
                is MarkdownBulletList -> MarkdownBulletListView(
                    block = block,
                    theme = theme,
                    onLinkClicked = onLinkClicked,
                    onFilePathClicked = onFilePathClicked,
                    onCommandClicked = onCommandClicked,
                    tokenContext = tokenContext,
                    depth = depth + 1,
                )
                is MarkdownOrderedList -> MarkdownOrderedListView(
                    block = block,
                    theme = theme,
                    onLinkClicked = onLinkClicked,
                    onFilePathClicked = onFilePathClicked,
                    onCommandClicked = onCommandClicked,
                    tokenContext = tokenContext,
                    depth = depth + 1,
                )
                is MarkdownParagraph, is MarkdownHeading, is MarkdownCodeBlock, is MarkdownBlockquote,
                MarkdownHorizontalRule, is MarkdownUrlBlock, is MarkdownTable, is MarkdownFootnoteSection,
                -> MarkdownNestedBlocks(
                    blocks = listOf(block),
                    theme = theme,
                    onLinkClicked = onLinkClicked,
                    onFilePathClicked = onFilePathClicked,
                    onCommandClicked = onCommandClicked,
                    tokenContext = tokenContext,
                )
            }
        }
    }
}

@Composable
private fun MarkdownListMarker(
    marker: MarkdownListMarkerKind,
    plainMarker: String,
    plainStyle: TextStyle,
    theme: MarkdownTheme,
    modifier: Modifier = Modifier,
) {
    if (marker == MarkdownListMarkerKind.Plain) {
        Text(
            text = plainMarker,
            color = theme.mutedTextColor,
            style = plainStyle,
            softWrap = false,
            maxLines = 1,
            modifier = modifier,
        )
        return
    }
    val done = marker == MarkdownListMarkerKind.TaskDone
    Box(modifier = modifier, contentAlignment = Alignment.TopStart) {
        Symbol(if (done) "checkmark.circle.fill" else "circle", size = TaskCheckboxSize,
            tint = if (done) theme.linkColor else theme.mutedTextColor)
    }
}

private fun bulletGlyphForDepth(depth: Int): String = when (depth % 3) {
    0 -> "•"
    1 -> "◦"
    else -> "–"
}

private fun MarkdownTheme.listItemStyle(marker: MarkdownListMarkerKind): TextStyle =
    if (marker == MarkdownListMarkerKind.TaskDone) {
        bodyStyle.copy(color = mutedTextColor, textDecoration = TextDecoration.LineThrough)
    } else {
        bodyStyle
    }

private val TaskCheckboxSize = 18.dp
private val BulletMarkerWidth = 30.dp
private val ListItemSpacing = 10.dp
private val NestedGap = 8.dp
private val OrderedMarkerGap = 6.dp

private const val MaxIndentedDepth = 4
private val DeepIndentStep = 10.dp
