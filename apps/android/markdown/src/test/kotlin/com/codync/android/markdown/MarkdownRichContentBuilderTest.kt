package com.codync.android.markdown

import com.codync.android.markdown.model.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MarkdownRichContentBuilderTest {
    @Test
    fun commandNodeProducesACommandTokenSlot() {
        val content = buildMarkdownRichContent(
            nodes = listOf(MarkdownInline.Command("/help")),
            theme = testMarkdownTheme(),
        )

        assertTrue(content.tokenSlots.values.any { it is InlineToken.Command && it.name == "/help" })
    }

    @Test
    fun filePathNodeProducesAFileTokenSlot() {
        val content = buildMarkdownRichContent(
            nodes = listOf(MarkdownInlineParser.parseFilePath("src/App.kt")),
            theme = testMarkdownTheme(),
        )

        assertTrue(content.tokenSlots.values.any { it is InlineToken.File && it.path == "src/App.kt" })
    }

    @Test
    fun skipsAutomaticLinkIconWhenLabelAlreadyStartsWithIcon() {
        val content = buildMarkdownRichContent(
            nodes = listOf(
                MarkdownInline.Link(
                    label = listOf(MarkdownInline.Text("🐙 https://github.com/sagaya/Control/pull/49")),
                    destination = "https://github.com/sagaya/Control/pull/49",
                ),
            ),
            theme = testMarkdownTheme(),
        )

        assertEquals(0, content.inlineContent.size)
    }

    @Test
    fun keepsAutomaticLinkIconForPlainGithubUrl() {
        val content = buildMarkdownRichContent(
            nodes = MarkdownInlineParser.parse("https://github.com/sagaya/Control/pull/49"),
            theme = testMarkdownTheme(),
        )

        assertEquals(1, content.inlineContent.size)
    }

    @Test
    fun inlineCodeRunIsRecordedAsABackgroundRangeOverItsChars() {
        val content = buildMarkdownRichContent(
            nodes = listOf(
                MarkdownInline.Text("say "),
                MarkdownInline.Text("code", marks = setOf(MarkdownTextMark.InlineCode)),
                MarkdownInline.Text(" now"),
            ),
            theme = testMarkdownTheme(),
        )

        val text = content.annotated.text
        assertEquals(listOf(text.indexOf("code") until (text.indexOf("code") + "code".length)), content.inlineCodeRanges)
    }

    @Test
    fun plainProseHasNoInlineCodeBackgroundRanges() {
        val content = buildMarkdownRichContent(
            nodes = listOf(MarkdownInline.Text("just prose")),
            theme = testMarkdownTheme(),
        )

        assertTrue(content.inlineCodeRanges.isEmpty())
    }
}

private fun testMarkdownTheme(): MarkdownTheme {
    val body = TextStyle(fontSize = 17.sp, lineHeight = 24.sp, color = Color.Black)
    return MarkdownTheme(
        bodyStyle = body,
        codeStyle = body,
        inlineCodeStyle = body,
        tableCellStyle = body,
        codeCaptionStyle = body,
        bulletMarkerStyle = body,
        orderedMarkerStyle = body,
        headingStyles = List(6) { body },
        textColor = Color.Black,
        linkColor = Color.Blue,
        filePathColor = Color.Magenta,
        mutedTextColor = Color.Gray,
        borderColor = Color.LightGray,
        dividerColor = Color.LightGray,
        inlineCodeBackground = Color.Transparent,
        codeBackground = Color.Transparent,
        codeHeaderBackground = Color.Transparent,
        tableHeaderBackground = Color.Transparent,
        tableAlternateRowBackground = Color.Transparent,
        blockquoteBackground = Color.Transparent,
        blockquoteAccent = Color.Blue,
        syntaxKeywordColor = Color.Blue,
        syntaxStringColor = Color.Green,
        syntaxNumberColor = Color.Cyan,
        syntaxCommentColor = Color.Gray,
        syntaxPunctuationColor = Color.Gray,
        blockSpacing = 16.dp,
        nestedBlockSpacing = 8.dp,
        paragraphLineSpacing = 7.dp,
        listIndent = 28.dp,
        cardShape = RoundedCornerShape(12.dp),
    )
}
