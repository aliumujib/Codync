package com.codync.android.markdown

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight

internal object CodeSyntaxHighlighter {
    private val keywordRegex = Regex(
        "\\b(val|var|fun|class|object|interface|sealed|data|when|if|else|for|while|return|import|package|private|internal|public|override|suspend|async|await|let|const|function|export|from|type|interface|struct|enum|guard|switch|case|default|do|try|catch|throw|throws|await|def|print|true|false|null|nil)\\b",
    )
    private val numberRegex = Regex("\\b\\d+(?:\\.\\d+)?\\b")
    private val stringRegex = Regex("\"(?:\\\\.|[^\"\\\\])*\"|'(?:\\\\.|[^'\\\\])*'")
    private val commentRegex = Regex("(//.*$|#.*$)", RegexOption.MULTILINE)
    private val punctuationRegex = Regex("[{}()\\[\\],.;:=+\\-*/<>]")

    fun highlight(
        code: String,
        theme: MarkdownTheme,
    ): AnnotatedString {
        val ranges = mutableListOf<HighlightRange>()
        ranges.collectRanges(code, commentRegex, SpanStyle(color = theme.syntaxCommentColor))
        ranges.collectRanges(code, stringRegex, SpanStyle(color = theme.syntaxStringColor))
        ranges.collectRanges(code, keywordRegex, SpanStyle(color = theme.syntaxKeywordColor, fontWeight = FontWeight.SemiBold))
        ranges.collectRanges(code, numberRegex, SpanStyle(color = theme.syntaxNumberColor))
        ranges.collectRanges(code, punctuationRegex, SpanStyle(color = theme.syntaxPunctuationColor))

        return buildAnnotatedString {
            append(code)
            ranges
                .sortedWith(compareBy<HighlightRange> { it.start }.thenByDescending { it.end })
                .fold(mutableListOf<HighlightRange>()) { accepted, range ->
                    if (accepted.none { range.overlaps(it) }) accepted += range
                    accepted
                }
                .forEach { range ->
                    addStyle(range.style, range.start, range.end)
                }
        }
    }

    private fun MutableList<HighlightRange>.collectRanges(
        code: String,
        regex: Regex,
        style: SpanStyle,
    ) {
        regex.findAll(code).forEach { match ->
            add(HighlightRange(match.range.first, match.range.last + 1, style))
        }
    }

    private data class HighlightRange(
        val start: Int,
        val end: Int,
        val style: SpanStyle,
    ) {
        fun overlaps(other: HighlightRange): Boolean =
            start < other.end && other.start < end
    }
}
