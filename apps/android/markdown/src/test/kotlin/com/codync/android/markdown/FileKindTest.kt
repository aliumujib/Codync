package com.codync.android.markdown

import kotlin.test.Test
import kotlin.test.assertEquals

class FileKindTest {
    @Test fun directoryUsesFolderSymbol() {
        assertEquals("folder", FileKind.iconFor("src/main", isDirectory = true))
    }
    @Test fun codeUsesTextSymbol() {
        assertEquals("doc.text", FileKind.iconFor("Foo.kt", isDirectory = false))
    }
    @Test fun imageUsesPhotoSymbol() {
        assertEquals("photo", FileKind.iconFor("a/b.png", isDirectory = false))
    }
    @Test fun unknownExtensionFallsBackToDoc() {
        assertEquals("doc", FileKind.iconFor("a.qwerty", isDirectory = false))
    }
}
