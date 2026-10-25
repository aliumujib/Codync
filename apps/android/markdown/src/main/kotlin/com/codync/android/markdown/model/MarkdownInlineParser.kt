package com.codync.android.markdown.model

data class MarkdownInlineContext(
    val linkReferences: Map<String, String> = emptyMap(),
    val footnoteIndices: Map<String, Int> = emptyMap(),
) {
    companion object {
        val Empty = MarkdownInlineContext()
    }
}

object MarkdownInlineParser {
    private val inlineCodeRegex = Regex("`([^`]+)`")
    private val boldItalicRegex = Regex("\\*\\*\\*(.+?)\\*\\*\\*")
    private val boldRegex = Regex("\\*\\*(.+?)\\*\\*")
    private val italicRegex = Regex("(?<!\\*)\\*([^*]+)\\*(?!\\*)")
    private val underscoreBoldRegex = Regex("(?<![\\w_])__(.+?)__(?![\\w_])")
    private val underscoreItalicRegex = Regex("(?<![\\w_])_([^_\\n]+)_(?![\\w_])")
    private val strikethroughRegex = Regex("~~(.+?)~~")
    private val linkRegex = Regex("\\[([^\\]]+)]\\(\\s*([^)\\s]+)(?:\\s+\"[^\"]*\")?\\s*\\)")
    private val imageRegex = Regex("!\\[([^\\]]*)]\\(\\s*([^)\\s]+)(?:\\s+\"[^\"]*\")?\\s*\\)")
    private val referenceLinkRegex = Regex("\\[([^\\]]+)]\\[([^\\]]*)]")
    private val footnoteRefRegex = Regex("\\[\\^([^\\]\\s]+)]")
    private val urlRegex = Regex("https?://[^\\s)]+")
    private const val explicitDirectoryPrefix = "(?:/|\\./|\\.\\./)"
    private const val compactPathSegment = "[\\w@.+-]+"
    private const val spacedFileBase = "[\\w@.+-]+(?: [\\w@.+-]+)*"

    internal val knownFileExtensions = setOf(
        "kt", "kts", "java", "swift", "ts", "tsx", "js", "jsx", "py", "rb", "go", "rs",
        "c", "cc", "cpp", "h", "hpp", "m", "mm", "cs", "php", "scala",
        "sh", "bash", "zsh", "gradle",
        "json", "yaml", "yml", "toml", "xml", "html", "css", "scss", "md", "markdown", "txt",
        "sql", "proto", "plist", "gitignore", "env", "properties", "cfg", "ini", "lock",
        "png", "jpg", "jpeg", "gif", "svg", "webp", "pdf", "csv", "tsv", "log",
        "mp4", "m4v", "mov", "webm", "3gp", "mkv",
    )
    internal val knownFileNames = setOf(
        "Makefile", "GNUmakefile", "Dockerfile", "Containerfile", "Gemfile", "Podfile", "Fastfile", "Appfile",
        "Brewfile", "Procfile", "Jenkinsfile", "Vagrantfile", "Rakefile", "Justfile", "Cartfile",
        "README", "LICENSE", "CHANGELOG", "CODEOWNERS", "AGENTS", "CLAUDE",
        "gradlew", "mvnw", "BUILD", "WORKSPACE",
    )
    private val knownFileExtensionAlternation = knownFileExtensions.joinToString("|")
    private const val bareExtension = "[A-Za-z0-9][\\w.+-]*"

    val filePathRegex = Regex(
        "(?<![`\\w])((" +
            "(?:$compactPathSegment\\.(?i:$knownFileExtensionAlternation)(?![\\w.+-])(?::\\d+(?::\\d+)?)?)" +
            "|(?:$explicitDirectoryPrefix(?:$compactPathSegment/)*$compactPathSegment\\.$bareExtension(?::\\d+(?::\\d+)?)?)" +
            "|(?:$compactPathSegment/(?:$compactPathSegment/)*$compactPathSegment\\.$bareExtension(?::\\d+(?::\\d+)?)?)" +
            "|(?:$explicitDirectoryPrefix(?:$compactPathSegment/)*$spacedFileBase\\.$bareExtension(?::\\d+(?::\\d+)?)?)" +
            ")|(" +
            "${explicitDirectoryPrefix}(?:${compactPathSegment}/)*${compactPathSegment}/?" +
            "))(?![`\\w])",
    )
    private val inlineMathRegex = Regex("\\$([^$\\n]+?)\\$")
    private val slashCommandRegex = Regex("""(?<![`\w])(/[A-Za-z][\w:-]*)(?![\w:-]*/)""")
    private val dollarCommandRegex = Regex("""(?<=^|\n)[ \t]*(\$[A-Za-z][\w:-]*)""")

    fun parse(text: String, context: MarkdownInlineContext = MarkdownInlineContext.Empty): List<MarkdownInline> {
        if (text.isEmpty()) return emptyList()

        val nodes = mutableListOf<MarkdownInline>()
        var remaining = MarkdownInlinePreprocessor.preprocess(text)

        while (remaining.isNotEmpty()) {
            val match = firstMatch(remaining)
            if (match == null) {
                nodes += MarkdownInline.Text(remaining.decodeSentinels())
                break
            }

            if (match.range.first > 0) {
                nodes += MarkdownInline.Text(remaining.substring(0, match.range.first).decodeSentinels())
            }

            nodes += when (match.kind) {
                InlineKind.Code -> parseInlineCode(match.result.groupValues[1])

                InlineKind.BoldItalic -> markedText(
                    raw = match.result.groupValues[1],
                    marks = setOf(MarkdownTextMark.Bold, MarkdownTextMark.Italic, MarkdownTextMark.BoldItalic),
                    context = context,
                )

                InlineKind.Bold -> markedText(match.result.groupValues[1], setOf(MarkdownTextMark.Bold), context)

                InlineKind.Italic -> markedText(match.result.groupValues[1], setOf(MarkdownTextMark.Italic), context)

                InlineKind.Strikethrough ->
                    markedText(match.result.groupValues[1], setOf(MarkdownTextMark.Strikethrough), context)

                InlineKind.Math -> listOf(
                    MarkdownInline.Text(
                        value = match.result.groupValues[1].decodeSentinels(),
                        marks = setOf(MarkdownTextMark.InlineMath),
                    ),
                )

                InlineKind.Image -> listOf(
                    MarkdownInline.Image(
                        alt = match.result.groupValues[1].decodeSentinels(),
                        destination = match.result.groupValues[2].decodeSentinels(),
                    ),
                )

                InlineKind.FootnoteRef -> {
                    val id = match.result.groupValues[1]
                    val index = context.footnoteIndices[id]
                    if (index != null) {
                        listOf(MarkdownInline.FootnoteRef(id = id, index = index))
                    } else {
                        listOf(MarkdownInline.Text(match.result.value.decodeSentinels()))
                    }
                }

                InlineKind.Link -> listOf(
                    MarkdownInline.Link(
                        label = parse(match.result.groupValues[1], context),
                        destination = match.result.groupValues[2].decodeSentinels(),
                    ),
                )

                InlineKind.ReferenceLink -> {
                    val label = match.result.groupValues[1]
                    val refId = match.result.groupValues[2].ifEmpty { label }.lowercase()
                    val destination = context.linkReferences[refId]
                    if (destination != null) {
                        listOf(MarkdownInline.Link(label = parse(label, context), destination = destination))
                    } else {
                        listOf(MarkdownInline.Text(match.result.value.decodeSentinels()))
                    }
                }

                InlineKind.Url -> {
                    val url = match.result.value
                    listOf(
                        MarkdownInline.Link(
                            label = listOf(MarkdownInline.Text(url)),
                            destination = url,
                        ),
                    )
                }

                InlineKind.FilePath -> listOf(parseFilePath(match.result.value))

                InlineKind.Command -> listOf(MarkdownInline.Command(match.result.groupValues[1]))
            }

            remaining = remaining.substring(match.range.last + 1)
        }

        return nodes.mergeAdjacentText()
    }

    private fun markedText(raw: String, marks: Set<MarkdownTextMark>, context: MarkdownInlineContext): List<MarkdownInline> {
        val inner = parse(raw, context)
        return inner.map { node ->
            if (node is MarkdownInline.Text && MarkdownTextMark.InlineCode !in node.marks) {
                node.copy(marks = node.marks + marks)
            } else {
                node
            }
        }
    }

    private fun parseInlineCode(value: String): List<MarkdownInline> {
        val normalized = value.decodeSentinels().trim()
        return listOf(
            when {
                urlRegex.matchEntire(normalized) != null -> MarkdownInline.Link(
                    label = listOf(MarkdownInline.Text(normalized)),
                    destination = normalized,
                )

                filePathRegex.matchEntire(normalized) != null -> parseFilePath(normalized)

                normalized.substringBefore(':') in knownFileNames -> parseFilePath(normalized)

                else -> MarkdownInline.Text(
                    value = value.decodeSentinels(),
                    marks = setOf(MarkdownTextMark.InlineCode),
                )
            },
        )
    }

    fun isPlausibleFilePath(path: String): Boolean {
        val bare = path.substringBefore(':')
        val extension = bare.substringAfterLast('.', missingDelimiterValue = "")
        return bare.contains('/') || bare in knownFileNames || extension.lowercase() in knownFileExtensions
    }

    fun parseFilePath(value: String): MarkdownInline.FilePath {
        val normalized = value.trimEnd().trimTrailingPathPunctuation()
        val split = normalized.split(':', limit = 3)
        val rawPath = split.getOrNull(0).orEmpty()
        val isDirectory = explicitDirectoryPathValueRegex.matches(rawPath)
        return MarkdownInline.FilePath(
            value = normalized,
            path = if (isDirectory) rawPath.trimEnd('/') else rawPath,
            line = split.getOrNull(1)?.toIntOrNull(),
            column = split.getOrNull(2)?.toIntOrNull(),
            isDirectory = isDirectory,
        )
    }

    private fun firstMatch(text: String): InlineMatch? {
        val candidates = listOfNotNull(
            inlineCodeRegex.find(text)?.toInlineMatch(InlineKind.Code),
            inlineMathRegex.find(text)?.toInlineMatch(InlineKind.Math),
            boldItalicRegex.find(text)?.toInlineMatch(InlineKind.BoldItalic),
            boldRegex.find(text)?.toInlineMatch(InlineKind.Bold),
            underscoreBoldRegex.find(text)?.toInlineMatch(InlineKind.Bold),
            italicRegex.find(text)?.toInlineMatch(InlineKind.Italic),
            underscoreItalicRegex.find(text)?.toInlineMatch(InlineKind.Italic),
            strikethroughRegex.find(text)?.toInlineMatch(InlineKind.Strikethrough),
            imageRegex.find(text)?.toInlineMatch(InlineKind.Image),
            footnoteRefRegex.find(text)?.toInlineMatch(InlineKind.FootnoteRef),
            linkRegex.find(text)?.toInlineMatch(InlineKind.Link),
            referenceLinkRegex.find(text)?.toInlineMatch(InlineKind.ReferenceLink),
            urlRegex.find(text)?.toInlineMatch(InlineKind.Url),
            filePathRegex.find(text)?.toInlineMatch(InlineKind.FilePath),
            slashCommandRegex.find(text)?.toGroupInlineMatch(InlineKind.Command, 1),
            dollarCommandRegex.find(text)?.toGroupInlineMatch(InlineKind.Command, 1),
        )

        return candidates.minWithOrNull(
            compareBy<InlineMatch> { it.range.first }.thenBy { it.kind.priority },
        )
    }

    private fun MatchResult.toInlineMatch(kind: InlineKind) = InlineMatch(
        kind = kind,
        result = this,
        range = range,
    )

    private fun MatchResult.toGroupInlineMatch(kind: InlineKind, group: Int) = InlineMatch(
        kind = kind,
        result = this,
        range = groups[group]!!.range,
    )

    private fun List<MarkdownInline>.mergeAdjacentText(): List<MarkdownInline> {
        if (isEmpty()) return this
        val merged = mutableListOf<MarkdownInline>()
        for (node in this) {
            val previous = merged.lastOrNull()
            if (
                previous is MarkdownInline.Text &&
                node is MarkdownInline.Text &&
                previous.marks == node.marks
            ) {
                merged[merged.lastIndex] = previous.copy(value = previous.value + node.value)
            } else {
                merged += node
            }
        }
        return merged
    }

    private fun String.decodeSentinels(): String = MarkdownInlinePreprocessor.decodeSentinels(this)

    private data class InlineMatch(
        val kind: InlineKind,
        val result: MatchResult,
        val range: IntRange,
    )

    private enum class InlineKind(val priority: Int) {
        Code(0),
        Math(1),
        Command(2),
        Image(3),
        FootnoteRef(4),
        BoldItalic(5),
        Bold(6),
        Italic(7),
        Strikethrough(8),
        Link(9),
        ReferenceLink(10),
        Url(11),
        FilePath(12),
    }
}

internal object MarkdownInlinePreprocessor {
    private const val SENTINEL_BASE = '\uE100'
    private const val ESCAPABLE = "\\`*_{}[]()#+-.!|~<>$"

    private val autolinkUrlRegex = Regex("<(https?://[^>\\s]+)>")
    private val autolinkEmailRegex = Regex("<([\\w.+-]+@[\\w.-]+\\.[A-Za-z]{2,})>")
    private val lineBreakTagRegex = Regex("(?i)<br\\s*/?>")
    private val strongTagRegex = Regex("(?i)</?(?:strong|b)\\s*>")
    private val emphasisTagRegex = Regex("(?i)</?(?:em|i)\\s*>")
    private val codeTagRegex = Regex("(?i)</?code\\s*>")
    private val strikeTagRegex = Regex("(?i)</?(?:del|s|strike)\\s*>")
    private val knownTagRegex = Regex(
        "(?i)</?(?:div|details|summary|p|ul|ol|li|a|span|img|table|thead|tbody|tfoot|tr|td|th|" +
            "sub|sup|mark|u|section|article|header|footer|figure|figcaption|video|audio|source|" +
            "iframe|nav|aside|main|small|center|font|ins|kbd|samp|var|abbr|cite|q|dfn|time|pre|" +
            "blockquote|hr|h[1-6]|input|button|label|form|select|option|textarea|noscript|wbr)" +
            "(?:\\s[^>]*)?/?>",
    )
    private val entityRegex = Regex("&(#x?[0-9a-fA-F]+|[a-zA-Z][a-zA-Z0-9]{1,30});")

    private val namedEntities = mapOf(
        "amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'", "nbsp" to " ",
        "copy" to "©", "reg" to "®", "trade" to "™", "hellip" to "…", "mdash" to "—",
        "ndash" to "–", "laquo" to "«", "raquo" to "»", "frac12" to "½", "frac14" to "¼",
        "frac34" to "¾", "rarr" to "→", "larr" to "←", "rArr" to "⇒", "lArr" to "⇐",
        "there4" to "∴", "infin" to "∞", "deg" to "°", "middot" to "·", "bull" to "•",
        "dagger" to "†", "sect" to "§", "para" to "¶", "plusmn" to "±", "times" to "×",
        "divide" to "÷", "ne" to "≠", "le" to "≤", "ge" to "≥",
    )

    fun preprocess(text: String): String {
        var result = encodeEscapes(text)
        result = autolinkUrlRegex.replace(result, "$1")
        result = autolinkEmailRegex.replace(result, "$1")
        result = lineBreakTagRegex.replace(result, "\n")
        result = strongTagRegex.replace(result, "**")
        result = emphasisTagRegex.replace(result, "*")
        result = codeTagRegex.replace(result, "`")
        result = strikeTagRegex.replace(result, "~~")
        result = knownTagRegex.replace(result, "")
        result = decodeEntities(result)
        return result
    }

    fun decodeSentinels(text: String): String {
        if (text.none { it in SENTINEL_BASE..(SENTINEL_BASE + ESCAPABLE.length) }) return text
        return buildString(text.length) {
            text.forEach { char ->
                val offset = char - SENTINEL_BASE
                if (offset in ESCAPABLE.indices) append(ESCAPABLE[offset]) else append(char)
            }
        }
    }

    private fun encodeEscapes(text: String): String {
        if ('\\' !in text) return text
        return buildString(text.length) {
            var index = 0
            while (index < text.length) {
                val char = text[index]
                val next = text.getOrNull(index + 1)
                if (char == '\\' && next != null && next in ESCAPABLE) {
                    append(SENTINEL_BASE + ESCAPABLE.indexOf(next))
                    index += 2
                } else {
                    append(char)
                    index++
                }
            }
        }
    }

    private fun decodeEntities(text: String): String {
        if ('&' !in text) return text
        return entityRegex.replace(text) { match ->
            val body = match.groupValues[1]
            when {
                body.startsWith("#x") || body.startsWith("#X") ->
                    body.drop(2).toIntOrNull(16)?.toChar()?.toString() ?: match.value
                body.startsWith("#") ->
                    body.drop(1).toIntOrNull()?.takeIf { it in 1..0x10FFFF }
                        ?.let { String(Character.toChars(it)) } ?: match.value
                else -> namedEntities[body] ?: match.value
            }
        }
    }
}

private fun String.trimTrailingPathPunctuation(): String = trimEnd('.', ',', ';', '!', '?')
private val explicitDirectoryPathValueRegex = Regex(
    "^(?:/|\\./|\\.\\./)(?:[\\w@.+-]+(?: [\\w@.+-]+)*/)*[\\w@.+-]+/?$",
)
