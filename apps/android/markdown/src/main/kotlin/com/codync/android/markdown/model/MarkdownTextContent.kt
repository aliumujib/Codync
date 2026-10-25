package com.codync.android.markdown.model

fun List<MarkdownInline>.plainText(): String = buildString {
    appendPlainText(this@plainText)
}

fun StringBuilder.appendPlainText(nodes: List<MarkdownInline>) {
    nodes.forEach { node ->
        when (node) {
            is MarkdownInline.FilePath -> append(node.value)
            is MarkdownInline.Link -> appendPlainText(node.label)
            is MarkdownInline.Text -> append(node.value)
            is MarkdownInline.Command -> append(node.name)
            is MarkdownInline.Image -> append(node.alt)
            is MarkdownInline.FootnoteRef -> append(node.index.toString())
        }
    }
}

fun MarkdownTableCell.plainText(): String = children.plainText()
