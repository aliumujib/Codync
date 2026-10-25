package com.codync.android.markdown

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class InlineTokenDetectorTest {
    @Test fun detectsCommandFileFolderInOneLine() {
        val tokens = InlineTokenDetector.detect("run /code-review on src/main/App.kt in ./docs/")
        assertTrue(tokens.any { it is InlineToken.Command && it.name == "/code-review" })
        assertTrue(tokens.any { it is InlineToken.File && it.path == "src/main/App.kt" })
        assertTrue(tokens.any { it is InlineToken.Folder && it.path == "./docs" })
    }

    @Test fun fileWithLineColumnCarriesThem() {
        val f = InlineTokenDetector.detect("see App.kt:42:3").filterIsInstance<InlineToken.File>().single()
        assertEquals(42, f.line)
        assertEquals(3, f.column)
    }

    @Test fun pathWithSpaceIsUnresolved() {
        assertTrue(InlineTokenDetector.detect("/Users/me/My Folder/x").any { it is InlineToken.UnresolvedPath })
    }

    @Test fun plainTextHasNoTokens() {
        assertEquals(listOf(InlineToken.Text("just a sentence")), InlineTokenDetector.detect("just a sentence"))
    }

    @Test fun preservesSurroundingText() {
        val t = InlineTokenDetector.detect("open App.kt now")
        assertEquals(InlineToken.Text("open "), t.first())
        assertTrue(t.last() is InlineToken.Text && (t.last() as InlineToken.Text).text == " now")
    }

    @Test fun dollarCommandAtLineStartIsCommand() {
        assertTrue(
            InlineTokenDetector.detect("\$clear").any { it is InlineToken.Command && it.name == "\$clear" },
        )
    }

    @Test fun dollarCommandAtStartOfALaterLineIsCommand() {
        assertTrue(
            InlineTokenDetector.detect("do this\n\$hooks").any { it is InlineToken.Command && it.name == "\$hooks" },
        )
    }

    @Test fun inlineDollarVariableStaysPlainText() {
        val tokens = InlineTokenDetector.detect("echo \$PATH now")
        assertTrue(tokens.none { it is InlineToken.Command })
    }
}
