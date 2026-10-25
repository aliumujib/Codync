package com.codync.android.markdown

import com.codync.android.markdown.model.*

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
internal fun MarkdownTableView(
    table: MarkdownTable,
    theme: MarkdownTheme,
    modifier: Modifier = Modifier,
    onLinkClicked: (String) -> Unit,
    onFilePathClicked: (MarkdownInline.FilePath) -> Unit,
    onCommandClicked: (String) -> Unit,
    tokenContext: InlineTokenContext,
) {
    if (table.headers.isEmpty()) return

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val density = LocalDensity.current
        val textMeasurer = rememberTextMeasurer()
        val columnCount = maxOf(
            table.headers.size,
            table.rows.maxOfOrNull { it.cells.size } ?: 0,
        )
        val widths = remember(table, columnCount, maxWidth, density, textMeasurer) {
            estimateColumnWidths(
                table = table,
                columnCount = columnCount,
                maxWidth = maxWidth,
                textMeasurer = textMeasurer,
                density = density,
                theme = theme,
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
        ) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .clip(theme.cardShape)
                    .border(1.dp, theme.borderColor, theme.cardShape),
            ) {
                MarkdownTableRowView(
                    cells = table.headers,
                    widths = widths,
                    alignments = table.alignments,
                    theme = theme,
                    isHeader = true,
                    alternate = false,
                    onLinkClicked = onLinkClicked,
                    onFilePathClicked = onFilePathClicked,
                    onCommandClicked = onCommandClicked,
                    tokenContext = tokenContext,
                )
                table.rows.forEachIndexed { index, row ->
                    MarkdownTableRowView(
                        cells = row.cells,
                        widths = widths,
                        alignments = table.alignments,
                        theme = theme,
                        isHeader = false,
                        alternate = index % 2 == 1,
                        onLinkClicked = onLinkClicked,
                        onFilePathClicked = onFilePathClicked,
                        onCommandClicked = onCommandClicked,
                        tokenContext = tokenContext,
                    )
                }
            }
        }
    }
}

@Composable
private fun MarkdownTableRowView(
    cells: List<MarkdownTableCell>,
    widths: List<Dp>,
    alignments: List<MarkdownTableAlignment>,
    theme: MarkdownTheme,
    isHeader: Boolean,
    alternate: Boolean,
    onLinkClicked: (String) -> Unit,
    onFilePathClicked: (MarkdownInline.FilePath) -> Unit,
    onCommandClicked: (String) -> Unit,
    tokenContext: InlineTokenContext,
) {
    val background = when {
        isHeader -> theme.tableHeaderBackground
        alternate -> theme.tableAlternateRowBackground
        else -> theme.codeBackground.copy(alpha = 0f)
    }
    Row(
        modifier = Modifier
            .height(IntrinsicSize.Min)
            .background(background),
    ) {
        widths.forEachIndexed { index, width ->
            val cell = cells.getOrNull(index) ?: MarkdownTableCell(emptyList())
            val alignment = alignments.getOrNull(index) ?: MarkdownTableAlignment.Left
            Column(
                modifier = Modifier
                    .width(width)
                    .fillMaxHeight()
                    .defaultMinSize(minHeight = 48.dp)
                    .border(0.5.dp, theme.dividerColor)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                if (isHeader) {
                    Text(
                        text = cell.plainText(),
                        style = theme.tableCellStyle.copy(fontWeight = FontWeight.SemiBold),
                        color = theme.textColor,
                        textAlign = alignment.toTextAlign(),
                    )
                } else {
                    MarkdownRichText(
                        nodes = cell.children,
                        theme = theme,
                        style = theme.tableCellStyle.copy(textAlign = alignment.toTextAlign()),
                        onLinkClicked = onLinkClicked,
                        onFilePathClicked = onFilePathClicked,
                        onCommandClicked = onCommandClicked,
                        tokenContext = tokenContext,
                    )
                }
            }
        }
    }
}

private fun estimateColumnWidths(
    table: MarkdownTable,
    columnCount: Int,
    maxWidth: Dp,
    textMeasurer: TextMeasurer,
    density: Density,
    theme: MarkdownTheme,
): List<Dp> {
    val minWidth = 104.dp
    val maxColumnWidth = maxWidth * 0.72f
    return List(columnCount) { index ->
        val samples = buildList {
            add(table.headers.getOrNull(index)?.plainText().orEmpty())
            table.rows.forEach { row -> add(row.cells.getOrNull(index)?.plainText().orEmpty()) }
        }
        val measured = samples.maxOfOrNull { sample ->
            textMeasurer.measure(sample, style = theme.tableCellStyle).size.width
        } ?: 0
        val estimated = with(density) { measured.toDp() } + 36.dp
        estimated.coerceIn(minWidth, maxColumnWidth.coerceAtLeast(minWidth))
    }
}

private fun MarkdownTableAlignment.toTextAlign(): TextAlign = when (this) {
    MarkdownTableAlignment.Left -> TextAlign.Start
    MarkdownTableAlignment.Center -> TextAlign.Center
    MarkdownTableAlignment.Right -> TextAlign.End
}
