package com.codync.android.markdown

import com.codync.android.markdown.model.*

object InlineTokenDetector {

    private val commandRegex = Regex("""(^|\s)(/[A-Za-z][\w:-]*)""")

    private val dollarCommandRegex = Regex("""(^|\n)[ \t]*(\$[A-Za-z][\w:-]*)""")

    private val pathRegex = MarkdownInlineParser.filePathRegex

    private val spaceFreeComponent = """[\w@.+-]+"""
    private val spacedComponent = """[\w@.+-]+(?: [\w@.+-]+)*"""
    private val spacedPathRegex =
        Regex("""(?<![`\w])((?:/|\./|\.\./)$spaceFreeComponent(?:/$spacedComponent)+/?)(?![`\w])""")

    private const val WRAP_LENGTH = 42

    fun detect(text: String): List<InlineToken> {
        if (text.isEmpty()) return listOf(InlineToken.Text(text))
        val out = mutableListOf<InlineToken>()
        var cursor = 0
        val spaced = spacedPathRegex.findAll(text)
            .filter { it.groupValues[1].contains(' ') }
            .map { it to Kind.PATH }
        val matches = (commandRegex.findAll(text).map { it to Kind.COMMAND } +
            dollarCommandRegex.findAll(text).map { it to Kind.COMMAND } +
            spaced +
            pathRegex.findAll(text).map { it to Kind.PATH })
            .sortedWith(
                compareBy<Pair<MatchResult, Kind>> { candidateGroup(it.first, it.second).range.first }

                    .thenByDescending { candidateGroup(it.first, it.second).range.last },
            )

        var lastEnd = -1
        for ((match, kind) in matches) {
            val tokenGroup = candidateGroup(match, kind)
            val start = tokenGroup.range.first
            val end = tokenGroup.range.last
            if (start <= lastEnd) continue
            if (cursor < start) out += InlineToken.Text(text.substring(cursor, start))
            val raw = text.substring(start, end + 1)
            out += classify(kind, raw)
            cursor = end + 1
            lastEnd = end
        }
        if (cursor < text.length) out += InlineToken.Text(text.substring(cursor))
        return mergeText(out)
    }

    private enum class Kind { COMMAND, PATH }

    private fun candidateGroup(match: MatchResult, kind: Kind): MatchGroup =
        if (kind == Kind.COMMAND) match.groups[2]!! else match.groups[1] ?: match.groups[0]!!

    private fun classify(kind: Kind, raw: String): InlineToken = when (kind) {
        Kind.COMMAND -> InlineToken.Command(raw)
        Kind.PATH -> {
            val fp = MarkdownInlineParser.parseFilePath(raw)
            when {
                fp.path.contains(' ') || raw.length > WRAP_LENGTH -> InlineToken.UnresolvedPath(raw)
                fp.isDirectory -> InlineToken.Folder(fp.path)
                else -> InlineToken.File(fp.path, fp.line, fp.column)
            }
        }
    }

    private fun mergeText(tokens: List<InlineToken>): List<InlineToken> {
        val merged = mutableListOf<InlineToken>()
        for (t in tokens) {
            val prev = merged.lastOrNull()
            if (t is InlineToken.Text && prev is InlineToken.Text) {
                merged[merged.lastIndex] = InlineToken.Text(prev.text + t.text)
            } else if (!(t is InlineToken.Text && t.text.isEmpty())) {
                merged += t
            }
        }
        return merged
    }
}
