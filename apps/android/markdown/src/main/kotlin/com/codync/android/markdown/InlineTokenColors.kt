package com.codync.android.markdown

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import com.codync.android.design.Palette

@Immutable
data class InlineTokenPalette(
    val fileBackground: Color,
    val fileBorder: Color,
    val fileText: Color,
    val fileGlyph: Color,
    val commandBackground: Color,
    val commandBorder: Color,
    val commandText: Color,
    val commandGlyph: Color,
    val commandUserBackground: Color,
    val commandUserText: Color,
    val commandUserGlyph: Color,
)

/** File and command chips in Codync's greys: a soft tint of the text colour, a hairline, the text itself. */
val inlineTokenPalette: InlineTokenPalette
    @Composable get() {
        val ink = Palette.text
        return InlineTokenPalette(
            fileBackground = ink.copy(alpha = 0.06f),
            fileBorder = Palette.border,
            fileText = ink,
            fileGlyph = Palette.secondary,
            commandBackground = ink.copy(alpha = 0.06f),
            commandBorder = Palette.border,
            commandText = ink,
            commandGlyph = Palette.secondary,
            commandUserBackground = ink.copy(alpha = 0.10f),
            commandUserText = ink,
            commandUserGlyph = Palette.secondary,
        )
    }
