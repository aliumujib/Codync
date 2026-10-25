package com.codync.android.markdown

import com.codync.android.design.Symbol

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal fun middleTruncate(name: String, max: Int = 34): String {
    if (name.length <= max) return name
    val dot = name.lastIndexOf('.')
    val ext = if (dot in 1 until name.length) name.substring(dot) else ""
    val keep = max - ext.length - 1
    if (keep < 4) return name.take(max - 1) + "…"
    val head = (keep + 1) / 2
    val tail = keep / 2
    return name.take(head) + "…" + name.substring(name.length - tail) + ext
}

internal fun inlineTokenDisplayLabel(token: InlineToken, context: InlineTokenContext): String = when (token) {
    is InlineToken.Command -> token.name
    is InlineToken.File -> pathLabel(token.path, context)
    is InlineToken.Folder -> pathLabel(token.path, context)
    is InlineToken.Text, is InlineToken.UnresolvedPath -> ""
}

private fun pathLabel(path: String, context: InlineTokenContext): String =
    if (context.isUserBubble) {
        middleTruncate(path, USER_BUBBLE_PATH_MAX)
    } else {
        middleTruncate(path.substringAfterLast('/').ifEmpty { path })
    }

private const val USER_BUBBLE_PATH_MAX = 48

internal object InlineTokenPillTokens {
    val chipShape = RoundedCornerShape(percent = MarkdownRadiusPercent.bubble)

    val bubbleTailShape = RoundedCornerShape(
        topStartPercent = MarkdownRadiusPercent.bubble,
        topEndPercent = MarkdownRadiusPercent.bubbleTail,
        bottomEndPercent = MarkdownRadiusPercent.bubble,
        bottomStartPercent = MarkdownRadiusPercent.bubble,
    )

    val roomyInsets = ContentInsets(horizontal = 8.dp, vertical = 5.dp)
    val compactInsets = ContentInsets(start = 8.dp, top = 5.dp, end = 8.dp, bottom = 5.dp)

    val horizontalBreathing = 3.dp
    val fileGlyphSize = 12.dp
    val commandGlyphSize = 11.dp
    val fileGap = 4.dp
    val commandGap = 4.dp
    val commandUserGap = 5.dp
    val pressedLightening = 0.08f
    val textStyle = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.W700,
        fontSize = 13.sp,
    )
}

private data class PillStyle(
    val display: String,
    val background: Color,
    val text: Color,
    val glyph: Glyph.Icon,
    val border: BorderStyle,
    val shape: Shape,
    val gap: Dp,
    val insets: ContentInsets,
)

private data class PillInput(val token: InlineToken, val context: InlineTokenContext)

internal fun inlineTokenInsetsFor(context: InlineTokenContext): ContentInsets =
    if (context == InlineTokenContext.UserBubbleStandalone) {
        InlineTokenPillTokens.roomyInsets
    } else {
        InlineTokenPillTokens.compactInsets
    }

private object InlineTokenPillStyleResolver : StyleResolver<PillInput, PillStyle> {
    @Composable
    override fun resolve(input: PillInput): PillStyle {
        val userBubble = input.context.isUserBubble
        val palette = inlineTokenPalette
        return when (val token = input.token) {
            is InlineToken.Command -> PillStyle(
                display = token.name,
                background = if (userBubble) {
                    palette.commandUserBackground
                } else {
                    palette.commandBackground
                },
                text = if (userBubble) palette.commandUserText else palette.commandText,
                glyph = Glyph.Icon(
                    symbol = "terminal",
                    tint = if (userBubble) palette.commandUserGlyph else palette.commandGlyph,
                    size = InlineTokenPillTokens.commandGlyphSize,
                ),
                border = if (userBubble) BorderStyle.None else BorderStyle.Solid(palette.commandBorder),

                shape = if (userBubble) InlineTokenPillTokens.bubbleTailShape else InlineTokenPillTokens.chipShape,
                gap = if (userBubble) InlineTokenPillTokens.commandUserGap else InlineTokenPillTokens.commandGap,
                insets = inlineTokenInsetsFor(input.context),
            )

            is InlineToken.File -> filePillStyle(
                display = inlineTokenDisplayLabel(token, input.context),
                path = token.path,
                isDir = false,
                context = input.context,
                palette = palette,
            )

            is InlineToken.Folder -> filePillStyle(
                display = inlineTokenDisplayLabel(token, input.context),
                path = token.path,
                isDir = true,
                context = input.context,
                palette = palette,
            )

            is InlineToken.Text, is InlineToken.UnresolvedPath ->
                filePillStyle("", "", false, input.context, palette)
        }
    }

    private fun filePillStyle(
        display: String,
        path: String,
        isDir: Boolean,
        context: InlineTokenContext,
        palette: InlineTokenPalette,
    ) = PillStyle(
        display = display,
        background = palette.fileBackground,
        text = palette.fileText,
        glyph = Glyph.Icon(
            symbol = FileKind.iconFor(path, isDir),
            tint = palette.fileGlyph,
            size = InlineTokenPillTokens.fileGlyphSize,
        ),
        border = BorderStyle.Solid(palette.fileBorder),
        shape = InlineTokenPillTokens.chipShape,
        gap = InlineTokenPillTokens.fileGap,
        insets = inlineTokenInsetsFor(context),
    )
}

@Composable
fun InlineTokenPill(
    token: InlineToken,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
    context: InlineTokenContext = InlineTokenContext.AssistantText,
) {
    val style = InlineTokenPillStyleResolver.resolve(PillInput(token, context))
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    val fill = if (pressed) {
        lerp(style.background, Color.White, InlineTokenPillTokens.pressedLightening)
    } else {
        style.background
    }
    val surface = SurfaceStyle(
        background = SolidColor(fill),
        shape = style.shape,
        border = style.border,
    )

    Row(
        modifier = modifier
            .clickable(interactionSource = interaction, indication = null, onClick = onTap)
            .then(surface.asModifier())
            .padding(style.insets.toPadding()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Symbol(style.glyph.symbol, size = style.glyph.size, tint = style.glyph.tint)
        Spacer(Modifier.width(style.gap))
        Text(
            text = style.display,
            color = style.text,
            maxLines = 1,
            softWrap = false,
            style = InlineTokenPillTokens.textStyle,
        )
    }
}
