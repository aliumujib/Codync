package com.codync.android.viewer

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction

/** How the viewer shows a file. */
enum class FileKind(val symbol: String) {
    Video("film"), Audio("waveform"), Image("photo"), Pdf("doc.richtext"), Markdown("doc.text"), Text("doc.text"),
    Zip("doc.zipper"), Other("doc");

    /** Played while it's read from the computer instead of after a full download. */
    val streams: Boolean get() = this == Video || this == Audio

    companion object {
        private val byExtension: Map<String, FileKind> = buildMap {
            for (e in listOf("mp4", "m4v", "mov", "webm", "mkv", "3gp")) put(e, Video)
            for (e in listOf("mp3", "m4a", "aac", "wav", "ogg", "oga", "opus", "flac")) put(e, Audio)
            for (e in listOf("png", "jpg", "jpeg", "gif", "webp", "heic", "heif", "avif", "bmp")) put(e, Image)
            put("pdf", Pdf)
            for (e in listOf("md", "markdown")) put(e, Markdown)
            for (e in listOf("txt", "log", "json", "jsonl", "ndjson", "csv", "tsv", "xml", "yaml", "yml", "toml", "ini",
                "conf", "cfg", "env", "properties", "html", "htm", "css", "js", "mjs", "ts", "tsx", "jsx", "py", "kt", "kts",
                "java", "swift", "rs", "go", "rb", "php", "c", "h", "cpp", "hpp", "cs", "sh", "bash", "zsh", "sql", "srt",
                "vtt", "diff", "patch", "gradle", "lock", "gitignore", "dockerfile")) put(e, Text)
            put("zip", Zip)
        }

        /** By the file name's extension; [Other] when it has none we know. */
        fun of(name: String): FileKind {
            val lower = name.lowercase()
            val extension = if ('.' in lower) lower.substringAfterLast('.') else lower
            return byExtension[extension] ?: Other
        }

        /** For a file whose name says nothing: [Text] when its start decodes as UTF-8 without control bytes. */
        fun sniff(head: ByteArray): FileKind {
            if (head.isEmpty()) return Text
            // A multi-byte character cut off at the end of the sample isn't a sign of binary.
            val sample = head.copyOf(trimPartialUtf8(head))
            val decoded = try {
                Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(sample)).toString()
            } catch (_: CharacterCodingException) { return Other }
            val control = decoded.count { it.code < 32 && it != '\n' && it != '\r' && it != '\t' && it != '\u000C' }
            return if (control == 0) Text else Other
        }

        private fun trimPartialUtf8(bytes: ByteArray): Int {
            var end = bytes.size
            var i = end - 1
            var continuation = 0
            while (i >= 0 && continuation < 3 && (bytes[i].toInt() and 0xC0) == 0x80) { i--; continuation++ }
            if (i < 0) return end
            val lead = bytes[i].toInt() and 0xFF
            val needed = when { lead >= 0xF0 -> 3; lead >= 0xE0 -> 2; lead >= 0xC0 -> 1; else -> 0 }
            if (needed > continuation) end = i
            return end
        }
    }
}
