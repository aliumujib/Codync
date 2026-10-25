package com.codync.android.markdown

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class InlineTokenPillTest {
    @Test fun shortNameUntouched() = assertEquals("App.kt", middleTruncate("App.kt"))

    @Test fun longNameKeepsExtensionAndEllipsizesMiddle() {
        val r = middleTruncate("VeryLongComponentFileNameThatOverflows.kt", max = 20)
        assertTrue(r.endsWith(".kt"))
        assertTrue(r.contains("…"))
        assertTrue(r.length <= 20)
    }
}
