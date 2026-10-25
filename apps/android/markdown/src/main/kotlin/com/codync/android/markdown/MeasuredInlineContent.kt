package com.codync.android.markdown

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max

/**
 * Builds an [InlineTextContent] sized to hold a variable-width token pill.
 *
 * [InlineTextContent.Placeholder] needs a fixed width/height in text units BEFORE the pill composes,
 * and a single InlineTextContent cannot feed its child's measured size back into its own placeholder.
 * So we measure the pill's label with a [rememberTextMeasurer] at the pill's own text style, then add
 * the pill's fixed structural width (leading glyph + icon/text gap + horizontal insets) to get the
 * total. Height is constant: the taller of the glyph or the label line, plus vertical insets. Both are
 * converted dp -> sp against the current density so the pill scales with font scale like the text it
 * flows in. This mirrors the width-estimation pattern already used in MarkdownTableRenderer.
 *
 * The pill itself middle-truncates long labels, so no width clamp is needed here.
 *
 * @param labelWidthText the pill's display label — the text whose measured width drives the placeholder.
 * @param glyphSize the leading glyph's size (kind-varying: file vs command).
 * @param gap the icon/text gap (kind-varying).
 * @param insets the pill's resolved padding for this context — MUST match what the pill applies, or
 *   the placeholder mis-sizes.
 * @param content the pill composable to place inside the sized placeholder.
 */
@Composable
internal fun rememberPillInlineContent(
    labelWidthText: String,
    glyphSize: Dp,
    gap: Dp,
    insets: ContentInsets,
    content: @Composable () -> Unit,
): InlineTextContent {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val placeholder = remember(labelWidthText, glyphSize, gap, insets, density) {
        val measured = measurer.measure(labelWidthText, style = InlineTokenPillTokens.textStyle).size
        val labelDp = with(density) { measured.width.toDp() }
        // Row: [start inset][glyph][gap][label][end inset]. + a small pad so the border isn't clipped,
        // + breathing on both sides so the pill never butts against the neighbouring word/pill.
        val totalWidthDp = insets.start + glyphSize + gap + labelDp + insets.end +
            WIDTH_SAFETY_PAD + InlineTokenPillTokens.horizontalBreathing * 2
        // Height: taller of the glyph or the label's rendered line box, plus vertical insets.
        val lineDp = with(density) { measured.height.toDp() }
        val totalHeightDp = insets.top + max(glyphSize, lineDp) + insets.bottom
        Placeholder(
            width = with(density) { totalWidthDp.toSp() },
            height = with(density) { totalHeightDp.toSp() },
            placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter,
        )
    }
    // Centre the pill in the (slightly wider) placeholder so the breathing sits on both sides.
    return InlineTextContent(placeholder = placeholder) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
    }
}

/** Trailing pad added to the measured pill width so the border/last glyph pixel isn't clipped. */
private val WIDTH_SAFETY_PAD: Dp = 2.dp
