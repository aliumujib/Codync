package com.codync.android.markdown

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.codync.android.design.pressScale

// The few style primitives the renderer was written against (from Control's design system), expressed
// with Codync's own pieces: its press feedback, its hairline width and its symbol names for icons.

/** A resolved border. [None] instead of a nullable stroke keeps call sites branch-free. */
sealed interface BorderStyle {
    data object None : BorderStyle
    data class Solid(val color: Color, val width: Dp = 1.dp) : BorderStyle
}

/** A resolved surface: clip, then the border when there is one, then the fill. */
data class SurfaceStyle(val background: Brush, val shape: Shape, val border: BorderStyle = BorderStyle.None) {
    fun asModifier(): Modifier {
        val clipped = Modifier.clip(shape)
        val bordered = when (val b = border) {
            BorderStyle.None -> clipped
            is BorderStyle.Solid -> clipped.border(BorderStroke(b.width, b.color), shape)
        }
        return bordered.background(brush = background)
    }
}

/** Inner spacing as four edges (start and end mirror in right-to-left text). */
data class ContentInsets(val start: Dp, val top: Dp, val end: Dp, val bottom: Dp) {
    val horizontal: Dp get() = start
    val vertical: Dp get() = top

    fun toPadding(): PaddingValues = PaddingValues(start = start, top = top, end = end, bottom = bottom)

    companion object {
        operator fun invoke(all: Dp): ContentInsets = ContentInsets(all, all, all, all)
        operator fun invoke(horizontal: Dp, vertical: Dp): ContentInsets =
            ContentInsets(start = horizontal, top = vertical, end = horizontal, bottom = vertical)
    }
}

/** A resolved icon: a Codync `Symbol` name, its tint and size. */
sealed interface Glyph {
    data object None : Glyph
    data class Icon(val symbol: String, val tint: Color, val size: Dp) : Glyph
}

/** Maps facts about an item to a ready-to-apply style bundle, so composables don't branch. */
fun interface StyleResolver<in In, out Out> {
    @Composable fun resolve(input: In): Out
}

internal object MarkdownRadius {
    val small = 4.dp
    val compact = 8.dp
    val medium = 12.dp
    val pill = 999.dp
}

internal object MarkdownIconSize {
    val chip = 12.dp
    val capsule = 14.dp
    val inline = 16.dp
    val block = 20.dp
}

internal object MarkdownRadiusPercent {
    const val chip = 30
    const val chipMarker = 20
    const val block = 25
    const val bubble = 34
    const val bubbleTight = 40
    const val bubbleTail = 20
}

/** Codync's press: a slight shrink while held. */
@Composable internal fun Modifier.markdownPressFeedback(interactionSource: InteractionSource, enabled: Boolean = true): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    return pressScale(pressed && enabled)
}
