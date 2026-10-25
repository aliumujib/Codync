package com.codync.android.markdown

import com.codync.android.design.Symbol

import com.codync.android.design.Palette

import com.codync.android.markdown.model.*

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em

internal const val UrlAnnotationTag = "url"
internal const val FilePathAnnotationTag = "file_path"
internal const val CommandAnnotationTag = "command"

internal const val InlineCodeAnnotationTag = "inline_code"

internal data class MarkdownRichContent(
    val annotated: AnnotatedString,
    val inlineContent: Map<String, InlineTextContent>,
    val tokenSlots: Map<String, InlineToken>,
) {

    val inlineCodeRanges: List<IntRange> by lazy {
        annotated.getStringAnnotations(InlineCodeAnnotationTag, 0, annotated.length)
            .map { it.start until it.end }
    }
}

internal fun buildMarkdownRichContent(
    nodes: List<MarkdownInline>,
    theme: MarkdownTheme,
): MarkdownRichContent {
    val builder = MarkdownRichContentBuilder(theme)
    builder.appendMarkdown(nodes)
    return MarkdownRichContent(
        annotated = builder.buildAnnotatedString(),
        inlineContent = builder.inlineContent(),
        tokenSlots = builder.tokenSlots(),
    )
}

private class MarkdownRichContentBuilder(
    private val theme: MarkdownTheme,
) {
    private val inlineContent = linkedMapOf<String, InlineTextContent>()
    private val tokenSlots = linkedMapOf<String, InlineToken>()
    private var inlineContentCount = 0
    private val builder = AnnotatedString.Builder()

    fun appendMarkdown(nodes: List<MarkdownInline>) {
        builder.appendMarkdown(nodes)
    }

    fun buildAnnotatedString(): AnnotatedString = builder.toAnnotatedString()

    fun inlineContent(): Map<String, InlineTextContent> = inlineContent

    fun tokenSlots(): Map<String, InlineToken> = tokenSlots

    private fun AnnotatedString.Builder.appendMarkdown(nodes: List<MarkdownInline>) {
        nodes.forEach { node ->
            when (node) {
                is MarkdownInline.FilePath -> appendFilePath(node)
                is MarkdownInline.Link -> appendLink(node)
                is MarkdownInline.Text -> appendText(node)
                is MarkdownInline.Command -> appendCommand(node)
                is MarkdownInline.Image -> appendImage(node)
                is MarkdownInline.FootnoteRef -> appendFootnoteRef(node)
            }
        }
    }

    private fun AnnotatedString.Builder.appendImage(image: MarkdownInline.Image) {
        pushStringAnnotation(tag = UrlAnnotationTag, annotation = image.destination)
        appendInlineIcon(
            MarkdownInlineIconSpec(symbol = "photo", tint = theme.mutedTextColor),
        )
        pushStyle(SpanStyle(color = theme.linkColor, textDecoration = TextDecoration.Underline))
        append(image.alt.ifBlank { image.destination.substringAfterLast('/').ifBlank { "image" } })
        pop()
        pop()
    }

    private fun AnnotatedString.Builder.appendFootnoteRef(ref: MarkdownInline.FootnoteRef) {
        pushStyle(
            SpanStyle(
                color = theme.linkColor,
                baselineShift = BaselineShift.Superscript,
                fontSize = theme.bodyStyle.fontSize * 0.72f,
                fontWeight = FontWeight.Bold,
            ),
        )
        append(ref.index.toString())
        pop()
    }

    private fun AnnotatedString.Builder.appendFilePath(filePath: MarkdownInline.FilePath) {

        if (filePath.path.contains(' ')) {
            pushStringAnnotation(tag = FilePathAnnotationTag, annotation = filePath.value)
            appendInlineIcon(filePath.iconSpec())
            pushStyle(
                SpanStyle(
                    color = theme.filePathColor,
                    textDecoration = TextDecoration.Underline,
                    fontFamily = FontFamily.Monospace,
                    fontSize = theme.inlineCodeStyle.fontSize,
                ),
            )
            append(filePath.value)
            pop()
            pop()
            return
        }

        val token: InlineToken = if (filePath.isDirectory) {
            InlineToken.Folder(filePath.path)
        } else {
            InlineToken.File(filePath.path, filePath.line, filePath.column)
        }
        pushStringAnnotation(tag = FilePathAnnotationTag, annotation = filePath.value)
        appendTokenPill(token)
        pop()
    }

    private fun AnnotatedString.Builder.appendCommand(command: MarkdownInline.Command) {
        pushStringAnnotation(tag = CommandAnnotationTag, annotation = command.name)
        appendTokenPill(InlineToken.Command(command.name))
        pop()
    }

    private fun AnnotatedString.Builder.appendLink(link: MarkdownInline.Link) {
        pushStringAnnotation(tag = UrlAnnotationTag, annotation = link.destination)
        if (!link.label.hasLeadingInlineIcon()) {
            appendInlineIcon(link.iconSpec())
        }
        pushStyle(
            SpanStyle(
                color = theme.linkColor,
                textDecoration = TextDecoration.Underline,
            ),
        )
        appendMarkdown(link.label)
        pop()
        pop()
    }

    private fun AnnotatedString.Builder.appendText(text: MarkdownInline.Text) {
        val marks = text.marks
        val style = SpanStyle(
            color = textColorForMarks(marks, theme),
            fontWeight = when {
                MarkdownTextMark.Bold in marks || MarkdownTextMark.BoldItalic in marks -> FontWeight.SemiBold
                else -> null
            },
            fontStyle = when {
                MarkdownTextMark.Italic in marks || MarkdownTextMark.BoldItalic in marks -> FontStyle.Italic
                else -> null
            },
            textDecoration = when {
                MarkdownTextMark.Strikethrough in marks -> TextDecoration.LineThrough
                else -> null
            },
            fontFamily = when {
                MarkdownTextMark.InlineCode in marks || MarkdownTextMark.FilePath in marks || MarkdownTextMark.InlineMath in marks -> FontFamily.Monospace
                else -> null
            },
            fontSize = when {
                MarkdownTextMark.InlineCode in marks || MarkdownTextMark.FilePath in marks -> theme.inlineCodeStyle.fontSize
                MarkdownTextMark.InlineMath in marks -> theme.bodyStyle.fontSize
                else -> TextUnit.Unspecified
            },

        )

        val fenced = MarkdownTextMark.InlineCode in marks || MarkdownTextMark.InlineMath in marks
        if (fenced) pushStringAnnotation(tag = InlineCodeAnnotationTag, annotation = "")
        pushStyle(style)
        append(text.value)
        pop()
        if (fenced) pop()
    }

    private fun AnnotatedString.Builder.appendTokenPill(token: InlineToken) {
        val contentId = "token-pill-${inlineContentCount++}"
        tokenSlots[contentId] = token
        appendInlineContent(contentId, alternateText = " ")
    }

    private fun AnnotatedString.Builder.appendInlineIcon(spec: MarkdownInlineIconSpec) {
        val contentId = "inline-icon-${inlineContentCount++}"
        inlineContent[contentId] = InlineTextContent(
            placeholder = Placeholder(
                width = 1.1.em,
                height = 1.1.em,
                placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter,
            ),
        ) {
            MarkdownInlineIcon(spec = spec)
        }
        appendInlineContent(contentId, alternateText = " ")
        append("\u00A0")
    }
}

private fun List<MarkdownInline>.hasLeadingInlineIcon(): Boolean {
    val firstText = firstTextValue() ?: return false
    val firstVisible = firstText.firstOrNull { !it.isWhitespace() } ?: return false
    return firstVisible.category in setOf(
        CharCategory.OTHER_SYMBOL,
        CharCategory.PRIVATE_USE,
        CharCategory.MATH_SYMBOL,
        CharCategory.CURRENCY_SYMBOL,
        CharCategory.MODIFIER_SYMBOL,
        CharCategory.SURROGATE,
    )
}

private fun List<MarkdownInline>.firstTextValue(): String? {
    for (node in this) {
        when (node) {
            is MarkdownInline.FilePath -> return node.value
            is MarkdownInline.Link -> node.label.firstTextValue()?.let { return it }
            is MarkdownInline.Text -> if (node.value.isNotBlank()) return node.value
            is MarkdownInline.Command -> return node.name
            is MarkdownInline.Image -> return node.alt
            is MarkdownInline.FootnoteRef -> return node.index.toString()
        }
    }
    return null
}

@Composable
private fun MarkdownInlineIcon(spec: MarkdownInlineIconSpec) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Symbol(spec.symbol, Modifier.fillMaxSize(0.88f), tint = spec.tint.takeOrElse { Palette.secondary })
    }
}

/** A Codync symbol drawn inline; an unspecified tint takes the secondary text grey. */
private data class MarkdownInlineIconSpec(
    val symbol: String,
    val tint: Color = Color.Unspecified,
)

private fun MarkdownInline.FilePath.iconSpec(): MarkdownInlineIconSpec {
    return MarkdownInlineIconSpec(symbol = FileKind.iconFor(path = path, isDirectory = isDirectory))
}

private fun MarkdownInline.Link.iconSpec(): MarkdownInlineIconSpec {
    return MarkdownInlineIconSpec(symbol = "globe")
}

private fun textColorForMarks(
    marks: Set<MarkdownTextMark>,
    theme: MarkdownTheme,
): Color = when {
    MarkdownTextMark.InlineMath in marks -> theme.syntaxKeywordColor
    MarkdownTextMark.FilePath in marks -> theme.filePathColor
    else -> Color.Unspecified
}

internal fun TextStyle.withMarkdownColor(theme: MarkdownTheme): TextStyle =
    copy(color = color.takeIf { it != Color.Unspecified } ?: theme.textColor)
