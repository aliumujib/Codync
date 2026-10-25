package com.codync.android.markdown.model

sealed interface MarkdownReveal {
    data object Full : MarkdownReveal

    data class UpToLastBlock(val chars: Int) : MarkdownReveal
}
