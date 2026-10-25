package com.codync.android.markdown.model

object MarkdownBlockParser {
    private val headingRegex = Regex("^(#{1,6})\\s+(.+)$")
    private val horizontalRuleRegex = Regex("^(?:-{3,}|\\*{3,}|_{3,})$")
    private val listMarkerRegex = Regex("^(\\s*)([-*+]|\\d+[.)])\\s+(.*)$")
    private val taskMarkerRegex = Regex("^\\[([ xX]?)]\\s*(.*)$")
    private val fenceOpenRegex = Regex("^(\\s*)(`{3,})\\s*(\\S*)\\s*$")
    private val tableSeparatorRegex = Regex("^\\|[\\s\\-:|]+\\|$")
    private val urlOnlyRegex = Regex("^https?://\\S+$")
    private val linkDefinitionRegex = Regex("^\\s*\\[([^\\]^][^\\]]*)]:\\s*(\\S+)(?:\\s+\"[^\"]*\")?\\s*$")
    private val footnoteDefinitionRegex = Regex("^\\s*\\[\\^([^\\]\\s]+)]:\\s*(.*)$")
    private const val INDENTED_CODE_SPACES = 4
    private const val ESCAPED_PIPE_SENTINEL = ''

    fun parse(markdown: String): MarkdownDocument {
        val lines = markdown.replace("\r\n", "\n").split('\n')

        val linkReferences = mutableMapOf<String, String>()
        val footnoteDefinitions = linkedMapOf<String, String>()
        val bodyLines = collectDefinitions(lines, linkReferences, footnoteDefinitions)
        val context = MarkdownInlineContext(
            linkReferences = linkReferences,
            footnoteIndices = footnoteDefinitions.keys.withIndex().associate { (index, id) -> id to index + 1 },
        )

        val blocks = parseBlocks(bodyLines, context).toMutableList()
        if (footnoteDefinitions.isNotEmpty()) {
            blocks += MarkdownFootnoteSection(
                footnotes = footnoteDefinitions.entries.mapIndexed { index, (id, text) ->
                    MarkdownFootnote(id = id, index = index + 1, children = MarkdownInlineParser.parse(text, context))
                },
            )
        }
        return MarkdownDocument(blocks)
    }

    private fun collectDefinitions(
        lines: List<String>,
        linkReferences: MutableMap<String, String>,
        footnoteDefinitions: MutableMap<String, String>,
    ): List<String> {
        val body = mutableListOf<String>()
        var fenceDelimiter: String? = null
        lines.forEach { line ->
            val trimmed = line.trim()
            val openMatch = fenceOpenRegex.matchEntire(line)
            if (fenceDelimiter == null && openMatch != null) {
                fenceDelimiter = openMatch.groupValues[2]
                body += line
                return@forEach
            }
            if (fenceDelimiter != null) {
                if (trimmed.all { it == '`' } && trimmed.length >= fenceDelimiter!!.length) fenceDelimiter = null
                body += line
                return@forEach
            }
            val footnote = footnoteDefinitionRegex.matchEntire(line)
            if (footnote != null) {
                footnoteDefinitions[footnote.groupValues[1]] = footnote.groupValues[2]
                return@forEach
            }
            val linkDef = linkDefinitionRegex.matchEntire(line)
            if (linkDef != null) {
                linkReferences[linkDef.groupValues[1].lowercase()] = linkDef.groupValues[2]
                return@forEach
            }
            body += line
        }
        return body
    }

    internal fun parseBlocks(lines: List<String>, context: MarkdownInlineContext): List<MarkdownBlock> {
        val blocks = mutableListOf<MarkdownBlock>()
        var index = 0

        while (index < lines.size) {
            val line = lines[index]
            val trimmed = line.trim()

            if (trimmed.isEmpty()) {
                index++
                continue
            }

            if (fenceOpenRegex.matchEntire(line) != null) {
                val (block, nextIndex) = parseCodeBlock(lines, index)
                blocks += block
                index = nextIndex
                continue
            }

            val headingMatch = headingRegex.matchEntire(trimmed)
            if (headingMatch != null) {
                blocks += MarkdownHeading(
                    level = headingMatch.groupValues[1].length,
                    children = MarkdownInlineParser.parse(headingMatch.groupValues[2], context),
                )
                index++
                continue
            }

            if (horizontalRuleRegex.matches(trimmed)) {
                blocks += MarkdownHorizontalRule
                index++
                continue
            }

            if (trimmed.startsWith(">")) {
                val (block, nextIndex) = parseBlockquote(lines, index, context)
                blocks += block
                index = nextIndex
                continue
            }

            val listMatch = listMarkerRegex.matchEntire(line)
            if (listMatch != null) {
                val (block, nextIndex) = parseList(lines, index, context)
                blocks += block
                index = nextIndex
                continue
            }

            if (line.leadingSpaces() >= INDENTED_CODE_SPACES) {
                val (block, nextIndex) = parseIndentedCodeBlock(lines, index)
                blocks += block
                index = nextIndex
                continue
            }

            if (isTableStart(lines, index)) {
                val (block, nextIndex) = parseTable(lines, index, context)
                blocks += block
                index = nextIndex
                continue
            }

            if (urlOnlyRegex.matches(trimmed)) {
                blocks += MarkdownUrlBlock(trimmed)
                index++
                continue
            }

            val (block, nextIndex) = parseParagraph(lines, index, context)
            blocks += block
            index = nextIndex
        }

        return blocks
    }

    private fun parseCodeBlock(lines: List<String>, startIndex: Int): Pair<MarkdownCodeBlock, Int> {
        val opening = checkNotNull(fenceOpenRegex.matchEntire(lines[startIndex]))
        val indent = opening.groupValues[1].length
        val delimiter = opening.groupValues[2]
        val language = opening.groupValues[3].ifBlank { null }
        val codeLines = mutableListOf<String>()
        var index = startIndex + 1

        while (index < lines.size) {
            val line = lines[index]
            val trimmed = line.trim()

            if (trimmed.isNotEmpty() && trimmed.all { it == '`' } && trimmed.length >= delimiter.length) {
                return MarkdownCodeBlock(language = language, code = codeLines.joinToString("\n")) to index + 1
            }
            codeLines += line.dedent(indent)
            index++
        }

        return MarkdownCodeBlock(language = language, code = codeLines.joinToString("\n")) to index
    }

    private fun parseIndentedCodeBlock(lines: List<String>, startIndex: Int): Pair<MarkdownCodeBlock, Int> {
        val codeLines = mutableListOf<String>()
        var index = startIndex
        while (index < lines.size) {
            val line = lines[index]
            when {
                line.isBlank() -> {
                    val continues = (index + 1 until lines.size)
                        .firstOrNull { lines[it].isNotBlank() }
                        ?.let { lines[it].leadingSpaces() >= INDENTED_CODE_SPACES } == true
                    if (!continues) break
                    codeLines += ""
                }
                line.leadingSpaces() >= INDENTED_CODE_SPACES -> codeLines += line.dedent(INDENTED_CODE_SPACES)
                else -> break
            }
            index++
        }
        return MarkdownCodeBlock(language = null, code = codeLines.joinToString("\n")) to index
    }

    private fun parseBlockquote(
        lines: List<String>,
        startIndex: Int,
        context: MarkdownInlineContext,
    ): Pair<MarkdownBlockquote, Int> {
        val innerLines = mutableListOf<String>()
        var index = startIndex
        while (index < lines.size) {
            val trimmed = lines[index].trim()
            if (!trimmed.startsWith(">")) break

            innerLines += trimmed.removePrefix(">").removePrefix(" ")
            index++
        }

        return MarkdownBlockquote(parseBlocks(innerLines, context)) to index
    }

    private fun parseList(
        lines: List<String>,
        startIndex: Int,
        context: MarkdownInlineContext,
    ): Pair<MarkdownBlock, Int> {
        val firstMatch = checkNotNull(listMarkerRegex.matchEntire(lines[startIndex]))
        val baseIndent = firstMatch.groupValues[1].length
        val ordered = firstMatch.groupValues[2].first().isDigit()
        val startNumber = firstMatch.groupValues[2].trimEnd('.', ')').toIntOrNull() ?: 1
        val items = mutableListOf<MarkdownListItem>()
        var index = startIndex

        while (index < lines.size) {
            val itemMatch = listMarkerRegex.matchEntire(lines[index]) ?: break
            if (itemMatch.groupValues[1].length != baseIndent) break
            if (itemMatch.groupValues[2].first().isDigit() != ordered) break

            val contentIndent = checkNotNull(itemMatch.groups[3]).range.first
            val continuation = mutableListOf<String>()
            index++

            while (index < lines.size) {
                val line = lines[index]
                when {
                    line.isBlank() -> {
                        val next = (index + 1 until lines.size).firstOrNull { lines[it].isNotBlank() }
                        val nextBelongs = next != null && lines[next].leadingSpaces() > baseIndent
                        if (!nextBelongs) break
                        continuation += ""
                    }
                    listMarkerRegex.matchEntire(line)?.groupValues?.get(1)?.length == baseIndent -> break
                    line.leadingSpaces() > baseIndent -> continuation += line.dedent(contentIndent)
                    else -> break
                }
                if (line.isBlank() || line.leadingSpaces() > baseIndent) index++ else break
            }

            items += listItem(
                body = itemMatch.groupValues[3],
                nestedBlocks = parseBlocks(continuation, context),
                context = context,
            ).copy(number = itemMatch.groupValues[2].trimEnd('.', ')').toIntOrNull())
        }

        val block = if (ordered) {
            MarkdownOrderedList(items = items, startNumber = startNumber)
        } else {
            MarkdownBulletList(items)
        }
        return block to index
    }

    private fun listItem(
        body: String,
        nestedBlocks: List<MarkdownBlock>,
        context: MarkdownInlineContext,
    ): MarkdownListItem {
        val task = taskMarkerRegex.matchEntire(body)
            ?: return MarkdownListItem(
                children = MarkdownInlineParser.parse(body, context),
                nestedBlocks = nestedBlocks,
            )
        return MarkdownListItem(
            children = MarkdownInlineParser.parse(task.groupValues[2], context),
            marker = if (task.groupValues[1].isNotBlank()) {
                MarkdownListMarkerKind.TaskDone
            } else {
                MarkdownListMarkerKind.TaskPending
            },
            nestedBlocks = nestedBlocks,
        )
    }

    private fun parseTable(
        lines: List<String>,
        startIndex: Int,
        context: MarkdownInlineContext,
    ): Pair<MarkdownTable, Int> {
        val headers = parseTableCells(lines[startIndex]).map { MarkdownTableCell(MarkdownInlineParser.parse(it, context)) }
        val alignments = parseTableAlignments(lines[startIndex + 1])
        val rows = mutableListOf<MarkdownTableRow>()
        var index = startIndex + 2

        while (index < lines.size) {
            val trimmed = lines[index].trim()
            if (!isTableRow(trimmed) || isTableSeparator(trimmed)) break
            rows += MarkdownTableRow(
                cells = parseTableCells(trimmed).map { MarkdownTableCell(MarkdownInlineParser.parse(it, context)) },
            )
            index++
        }

        return MarkdownTable(headers = headers, rows = rows, alignments = alignments) to index
    }

    private fun parseParagraph(
        lines: List<String>,
        startIndex: Int,
        context: MarkdownInlineContext,
    ): Pair<MarkdownParagraph, Int> {
        val paragraphLines = mutableListOf<String>()
        var index = startIndex
        while (index < lines.size) {
            val trimmed = lines[index].trim()
            if (trimmed.isEmpty() || startsSpecialBlock(lines, index)) break
            paragraphLines += lines[index]
            index++
        }

        val joined = buildString {
            paragraphLines.forEachIndexed { lineIndex, raw ->
                val isLast = lineIndex == paragraphLines.lastIndex
                val content = raw.trim().removeSuffix("\\").trimEnd()
                append(content)
                if (!isLast) append("\n")
            }
        }
        return MarkdownParagraph(MarkdownInlineParser.parse(joined, context)) to index
    }

    private fun startsSpecialBlock(lines: List<String>, index: Int): Boolean {
        val line = lines[index]
        val trimmed = line.trim()
        return fenceOpenRegex.matchEntire(line) != null ||
            headingRegex.matches(trimmed) ||
            horizontalRuleRegex.matches(trimmed) ||
            trimmed.startsWith(">") ||
            listMarkerRegex.matchEntire(line) != null ||
            isTableStart(lines, index) ||
            urlOnlyRegex.matches(trimmed)
    }

    private fun isTableStart(lines: List<String>, index: Int): Boolean =
        index + 1 < lines.size &&
            isTableRow(lines[index].trim()) &&
            isTableSeparator(lines[index + 1].trim())

    private fun isTableRow(line: String): Boolean =
        line.startsWith("|") && line.endsWith("|") && line.count { it == '|' } >= 2

    private fun isTableSeparator(line: String): Boolean =
        tableSeparatorRegex.matches(line)

    private fun parseTableCells(line: String): List<String> =
        line.trim()
            .replace("\\|", ESCAPED_PIPE_SENTINEL.toString())
            .removePrefix("|")
            .removeSuffix("|")
            .split("|")
            .map { it.trim().replace(ESCAPED_PIPE_SENTINEL.toString(), "|") }

    private fun parseTableAlignments(separatorLine: String): List<MarkdownTableAlignment> =
        parseTableCells(separatorLine).map { cell ->
            val trimmed = cell.trim()
            val left = trimmed.startsWith(":")
            val right = trimmed.endsWith(":")
            when {
                left && right -> MarkdownTableAlignment.Center
                right -> MarkdownTableAlignment.Right
                else -> MarkdownTableAlignment.Left
            }
        }

    private fun String.leadingSpaces(): Int = indexOfFirst { it != ' ' }.let { if (it < 0) length else it }

    private fun String.dedent(spaces: Int): String {
        var removed = 0
        var index = 0
        while (index < length && removed < spaces && this[index] == ' ') {
            removed++
            index++
        }
        return substring(index)
    }
}
