package com.codync.android.markdown

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.codync.android.design.Palette
import com.codync.android.design.Type
import com.codync.android.design.bold
import com.codync.android.design.monospaced
import com.codync.android.design.semibold

@Immutable
data class MarkdownTheme(
    val bodyStyle: TextStyle,
    val codeStyle: TextStyle,
    val inlineCodeStyle: TextStyle,
    val tableCellStyle: TextStyle,
    val codeCaptionStyle: TextStyle,
    val bulletMarkerStyle: TextStyle,
    val orderedMarkerStyle: TextStyle,
    val headingStyles: List<TextStyle>,
    val textColor: Color,
    val linkColor: Color,
    val filePathColor: Color,
    val mutedTextColor: Color,
    val borderColor: Color,
    val dividerColor: Color,
    val inlineCodeBackground: Color,
    val codeBackground: Color,
    val codeHeaderBackground: Color,
    val tableHeaderBackground: Color,
    val tableAlternateRowBackground: Color,
    val blockquoteBackground: Color,
    val blockquoteAccent: Color,
    val syntaxKeywordColor: Color,
    val syntaxStringColor: Color,
    val syntaxNumberColor: Color,
    val syntaxCommentColor: Color,
    val syntaxPunctuationColor: Color,
    val blockSpacing: Dp,
    val nestedBlockSpacing: Dp,
    val paragraphLineSpacing: Dp,
    val listIndent: Dp,
    val cardShape: RoundedCornerShape,
)

/**
 * Codync's look for Markdown: Inter at the iOS text sizes, black and white (links are the text colour,
 * underlined), grey code and table surfaces, and greys instead of colours for syntax highlighting.
 */
@Composable
fun rememberMarkdownTheme(
    style: TextStyle = Type.body,
    color: Color = Color.Unspecified,
    linkColor: Color = Color.Unspecified,
): MarkdownTheme {
    val ink = color.takeOrElse { style.color.takeOrElse { Palette.text } }
    val body = style.copy(color = ink)
    val tint = Palette.text
    return MarkdownTheme(
        bodyStyle = body,
        codeStyle = Type.footnote.monospaced.copy(color = Palette.text),
        inlineCodeStyle = Type.callout.monospaced.copy(color = ink),
        tableCellStyle = Type.subheadline.copy(color = ink),
        codeCaptionStyle = Type.caption2.copy(color = ink),
        bulletMarkerStyle = body.semibold,
        orderedMarkerStyle = body.copy(color = Palette.secondary),
        headingStyles = listOf(
            Type.title3.bold.copy(color = ink),
            Type.headline.copy(color = ink),
            Type.subheadline.bold.copy(color = ink),
            Type.subheadline.bold.copy(color = ink),
            Type.subheadline.semibold.copy(color = ink),
            Type.subheadline.semibold.copy(color = ink),
        ),
        textColor = ink,
        linkColor = linkColor.takeOrElse { ink },
        filePathColor = ink,
        mutedTextColor = Palette.secondary,
        borderColor = Palette.border,
        dividerColor = Palette.border,
        inlineCodeBackground = tint.copy(alpha = 0.08f),
        codeBackground = Palette.codeBackground,
        codeHeaderBackground = tint.copy(alpha = 0.05f),
        tableHeaderBackground = tint.copy(alpha = 0.06f),
        tableAlternateRowBackground = tint.copy(alpha = 0.03f),
        blockquoteBackground = Color.Transparent,
        blockquoteAccent = Palette.border,
        syntaxKeywordColor = Palette.text,
        syntaxStringColor = Palette.secondary,
        syntaxNumberColor = Palette.text,
        syntaxCommentColor = Palette.tertiary,
        syntaxPunctuationColor = Palette.secondary,
        blockSpacing = 10.dp,
        nestedBlockSpacing = 6.dp,
        paragraphLineSpacing = 4.dp,
        listIndent = 22.dp,
        cardShape = RoundedCornerShape(8.dp),
    )
}
