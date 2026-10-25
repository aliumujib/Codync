package com.codync.android.markdown

/** A file or folder's symbol (a Codync `Symbol` name). Shared by the renderer and the inline-token pills. */
object FileKind {
    fun iconFor(path: String, isDirectory: Boolean): String {
        if (isDirectory) return "folder"
        return when (path.substringAfterLast('.', missingDelimiterValue = "").lowercase()) {
            "md", "txt", "rtf" -> "doc.text"
            "png", "jpg", "jpeg", "gif", "webp", "bmp", "avif", "svg" -> "photo"
            "pdf" -> "doc.richtext"
            "yaml", "yml", "toml", "ini", "gradle" -> "gearshape"
            "sh", "zsh", "bash" -> "terminal"
            "zip", "gz", "tar", "7z" -> "doc.zipper"
            "mov", "mp4", "m4v", "webm" -> "film"
            "mp3", "m4a", "wav", "aac" -> "waveform"
            "ts", "tsx", "js", "jsx", "py", "css", "scss", "html", "json", "swift", "kt", "kts", "java", "rb", "go", "rs",
            "cpp", "c", "h", "m", "mm", "php", "vue", "svelte", "sql", "dart", "lua", "xml" -> "doc.text"
            else -> "doc"
        }
    }
}
