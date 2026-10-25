package com.codync.android.markdown

import kotlin.test.Test
import kotlin.test.assertNull

class CodeSelectionQuoteTest {
    @Test
    fun `extractSpans is null-safe off-device`() {
        assertNull(CodeSelectionQuote.extractSpans(null))
        assertNull(CodeSelectionQuote.extractSpans(Any()))
    }
}
