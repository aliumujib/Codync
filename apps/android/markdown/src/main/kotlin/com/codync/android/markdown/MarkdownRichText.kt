package com.codync.android.markdown

import com.codync.android.markdown.model.*

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
internal fun MarkdownRichText(
    nodes: List<MarkdownInline>,
    theme: MarkdownTheme,
    modifier: Modifier = Modifier,
    style: TextStyle = theme.bodyStyle,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    onLinkClicked: (String) -> Unit,
    onFilePathClicked: (MarkdownInline.FilePath) -> Unit = {},
    onCommandClicked: (String) -> Unit = {},
    tokenContext: InlineTokenContext = InlineTokenContext.AssistantText,
    reveal: MarkdownReveal = MarkdownReveal.Full,
) {
    val richContent = remember(nodes, theme) {
        buildMarkdownRichContent(nodes, theme)
    }
    val displayedText = when (reveal) {
        MarkdownReveal.Full -> richContent.annotated
        is MarkdownReveal.UpToLastBlock -> richContent.annotated.revealedPrefix(reveal.chars)
    }
    val pillInsets = inlineTokenInsetsFor(tokenContext)
    val pillContent: Map<String, InlineTextContent> = richContent.tokenSlots.mapValues { (_, token) ->
        val label = inlineTokenDisplayLabel(token, tokenContext)
        val glyph = if (token is InlineToken.Command) {
            InlineTokenPillTokens.commandGlyphSize
        } else {
            InlineTokenPillTokens.fileGlyphSize
        }
        val gap = when {
            token is InlineToken.Command && tokenContext.isUserBubble -> InlineTokenPillTokens.commandUserGap
            token is InlineToken.Command -> InlineTokenPillTokens.commandGap
            else -> InlineTokenPillTokens.fileGap
        }
        rememberPillInlineContent(labelWidthText = label, glyphSize = glyph, gap = gap, insets = pillInsets) {
            InlineTokenPill(
                token = token,
                context = tokenContext,
                onTap = {
                    when (token) {
                        is InlineToken.Command -> onCommandClicked(token.name)
                        is InlineToken.File -> onFilePathClicked(
                            MarkdownInline.FilePath(
                                value = token.path +
                                    (token.line?.let { ":$it" } ?: "") +
                                    (token.column?.let { ":$it" } ?: ""),
                                path = token.path,
                                line = token.line,
                                column = token.column,
                                isDirectory = false,
                            ),
                        )
                        is InlineToken.Folder -> onFilePathClicked(
                            MarkdownInline.FilePath(
                                value = token.path,
                                path = token.path,
                                line = null,
                                column = null,
                                isDirectory = true,
                            ),
                        )
                        else -> {}
                    }
                },
            )
        }
    }
    val mergedInline = richContent.inlineContent + pillContent
    var layoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
    val inlineCodeBackground = theme.inlineCodeBackground

    val codeBackgroundModifier = if (richContent.inlineCodeRanges.isEmpty()) {
        Modifier
    } else {
        Modifier.drawBehind {
            val layout = layoutResult ?: return@drawBehind
            richContent.inlineCodeRanges.forEach { range ->
                drawInlineCodeBackground(layout, range, inlineCodeBackground)
            }
        }
    }

    BasicText(
        text = displayedText,
        modifier = modifier
            .then(codeBackgroundModifier)
            .pointerInput(richContent.annotated, onLinkClicked, onFilePathClicked) {
            detectTapGestures(
                onTap = { position ->
                    val textLayout = layoutResult ?: return@detectTapGestures
                    val offset = textLayout.getOffsetForPosition(position)
                    val filePath = richContent.annotated
                        .getStringAnnotations(tag = FilePathAnnotationTag, start = offset, end = offset)
                        .firstOrNull()
                        ?.let { MarkdownInlineParser.parseFilePath(it.item) }
                    if (filePath != null) {
                        onFilePathClicked(filePath)
                        return@detectTapGestures
                    }

                    richContent.annotated
                        .getStringAnnotations(tag = UrlAnnotationTag, start = offset, end = offset)
                        .firstOrNull()
                        ?.let { onLinkClicked(it.item) }
                },
            )
        },
        style = style.withMarkdownColor(theme),
        maxLines = maxLines,
        overflow = overflow,
        inlineContent = mergedInline,
        onTextLayout = { layoutResult = it },
    )
}

private fun AnnotatedString.revealedPrefix(chars: Int): AnnotatedString {
    val end = chars.coerceIn(0, length)
    return if (end == 0) AnnotatedString("") else subSequence(0, end)
}

private val InlineCodeBackgroundRadius = 4.dp
private val InlineCodeBackgroundHInset = 6.dp
private val InlineCodeBackgroundVInset = 1.dp

private fun DrawScope.drawInlineCodeBackground(
    layout: TextLayoutResult,
    range: IntRange,
    color: Color,
) {
    val start = range.first
    val endExclusive = range.last + 1
    if (start >= endExclusive || endExclusive > layout.layoutInput.text.length) return

    val firstLine = layout.getLineForOffset(start)
    val lastLine = layout.getLineForOffset(endExclusive - 1)
    val radius = CornerRadius(InlineCodeBackgroundRadius.toPx())
    val hInset = InlineCodeBackgroundHInset.toPx()
    val vInset = InlineCodeBackgroundVInset.toPx()

    for (line in firstLine..lastLine) {
        val segStart = maxOf(start, layout.getLineStart(line))
        val segEnd = minOf(endExclusive, layout.getLineEnd(line, visibleEnd = true))
        if (segStart >= segEnd) continue

        val left = layout.getHorizontalPosition(segStart, usePrimaryDirection = true)
        val right = layout.getHorizontalPosition(segEnd, usePrimaryDirection = true)
        val top = layout.getLineTop(line)
        val bottom = layout.getLineBottom(line)
        val box = Rect(
            left = minOf(left, right) - hInset,
            top = top - vInset,
            right = maxOf(left, right) + hInset,
            bottom = bottom + vInset,
        )
        drawRoundRect(
            color = color,
            topLeft = Offset(box.left, box.top),
            size = Size(box.width, box.height),
            cornerRadius = radius,
        )
    }
}
