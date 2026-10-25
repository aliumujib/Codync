package com.codync.android.markdown

import com.codync.android.markdown.model.*

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun MarkdownFootnoteSectionView(
    section: MarkdownFootnoteSection,
    theme: MarkdownTheme,
    modifier: Modifier = Modifier,
    onLinkClicked: (String) -> Unit,
    onFilePathClicked: (MarkdownInline.FilePath) -> Unit,
    onCommandClicked: (String) -> Unit,
    tokenContext: InlineTokenContext,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(FootnoteSpacing),
    ) {
        HorizontalDivider(color = theme.dividerColor)
        Text(
            text = "FOOTNOTES",
            color = theme.mutedTextColor,
            style = theme.codeCaptionStyle.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
        )
        section.footnotes.forEach { footnote ->
            Row(verticalAlignment = Alignment.Top) {
                FootnoteBadge(index = footnote.index, theme = theme)
                MarkdownRichText(
                    nodes = footnote.children,
                    theme = theme,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 10.dp, top = 1.dp),
                    style = theme.bodyStyle.copy(
                        fontSize = theme.tableCellStyle.fontSize,
                        color = theme.mutedTextColor,
                    ),
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
private fun FootnoteBadge(index: Int, theme: MarkdownTheme) {
    Box(
        modifier = Modifier
            .size(BadgeSize)
            .clip(CircleShape)
            .background(theme.linkColor.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = index.toString(),
            color = theme.linkColor,
            style = theme.codeCaptionStyle.copy(fontWeight = FontWeight.Bold),
            textAlign = TextAlign.Center,
        )
    }
}

private val FootnoteSpacing = 8.dp
private val BadgeSize = 20.dp
