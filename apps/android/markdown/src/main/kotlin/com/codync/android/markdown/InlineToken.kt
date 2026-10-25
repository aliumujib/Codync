package com.codync.android.markdown

sealed interface InlineToken {
    data class Text(val text: String) : InlineToken
    data class File(val path: String, val line: Int? = null, val column: Int? = null) : InlineToken
    data class Folder(val path: String) : InlineToken
    data class Command(val name: String) : InlineToken

    /** Path-shaped but not a plain file/folder we render as a pill (has spaces, or a wrap-prone length). */
    data class UnresolvedPath(val raw: String) : InlineToken
}
