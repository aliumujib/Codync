package com.codync.android.markdown.model

sealed interface MarkdownNode

data class MarkdownDocument(
    val blocks: List<MarkdownBlock>,
) : MarkdownNode

sealed interface MarkdownBlock : MarkdownNode

data class MarkdownParagraph(
    val children: List<MarkdownInline>,
) : MarkdownBlock

data class MarkdownHeading(
    val level: Int,
    val children: List<MarkdownInline>,
) : MarkdownBlock

data class MarkdownCodeBlock(
    val language: String?,
    val code: String,
) : MarkdownBlock

data class MarkdownBulletList(
    val items: List<MarkdownListItem>,
) : MarkdownBlock

data class MarkdownOrderedList(
    val items: List<MarkdownListItem>,
    val startNumber: Int = 1,
) : MarkdownBlock

data class MarkdownListItem(
    val children: List<MarkdownInline>,
    val marker: MarkdownListMarkerKind = MarkdownListMarkerKind.Plain,
    val nestedBlocks: List<MarkdownBlock> = emptyList(),
    val number: Int? = null,
) : MarkdownNode

enum class MarkdownListMarkerKind {
    Plain,
    TaskDone,
    TaskPending,
}

data class MarkdownBlockquote(
    val blocks: List<MarkdownBlock>,
) : MarkdownBlock {
    val children: List<MarkdownInline>
        get() = blocks.flatMap { block ->
            when (block) {
                is MarkdownParagraph -> block.children
                else -> emptyList()
            }
        }
}

data object MarkdownHorizontalRule : MarkdownBlock

data class MarkdownUrlBlock(
    val url: String,
) : MarkdownBlock

data class MarkdownTable(
    val headers: List<MarkdownTableCell>,
    val rows: List<MarkdownTableRow>,
    val alignments: List<MarkdownTableAlignment>,
) : MarkdownBlock

data class MarkdownTableRow(
    val cells: List<MarkdownTableCell>,
) : MarkdownNode

data class MarkdownTableCell(
    val children: List<MarkdownInline>,
) : MarkdownNode

enum class MarkdownTableAlignment {
    Left,
    Center,
    Right,
}

data class MarkdownFootnoteSection(
    val footnotes: List<MarkdownFootnote>,
) : MarkdownBlock

data class MarkdownFootnote(
    val id: String,
    val index: Int,
    val children: List<MarkdownInline>,
) : MarkdownNode

sealed interface MarkdownInline : MarkdownNode {
    data class Text(
        val value: String,
        val marks: Set<MarkdownTextMark> = emptySet(),
    ) : MarkdownInline

    data class Link(
        val label: List<MarkdownInline>,
        val destination: String,
    ) : MarkdownInline

    data class Image(
        val alt: String,
        val destination: String,
    ) : MarkdownInline

    data class FootnoteRef(
        val id: String,
        val index: Int,
    ) : MarkdownInline

    data class FilePath(
        val value: String,
        val path: String,
        val line: Int? = null,
        val column: Int? = null,
        val isDirectory: Boolean = false,
    ) : MarkdownInline

    data class Command(val name: String) : MarkdownInline
}

enum class MarkdownTextMark {
    Bold,
    Italic,
    BoldItalic,
    Strikethrough,
    InlineCode,
    FilePath,
    InlineMath,
}
