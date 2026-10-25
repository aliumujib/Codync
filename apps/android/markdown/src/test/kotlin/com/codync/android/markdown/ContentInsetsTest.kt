package com.codync.android.markdown

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

/** Locks the three [ContentInsets] construction styles and their RTL-aware edge storage. */
class ContentInsetsTest {
    @Test fun uniformSetsAllEdges() {
        val insets = ContentInsets(all = 8.dp)
        assertEquals(8.dp, insets.start)
        assertEquals(8.dp, insets.top)
        assertEquals(8.dp, insets.end)
        assertEquals(8.dp, insets.bottom)
    }

    @Test fun symmetricMapsAxesToEdges() {
        val insets = ContentInsets(horizontal = 12.dp, vertical = 6.dp)
        assertEquals(12.dp, insets.start)
        assertEquals(12.dp, insets.end)
        assertEquals(6.dp, insets.top)
        assertEquals(6.dp, insets.bottom)
        assertEquals(12.dp, insets.horizontal)
        assertEquals(6.dp, insets.vertical)
    }

    @Test fun perEdgeKeepsEachEdge() {
        val insets = ContentInsets(start = 6.dp, top = 2.dp, end = 8.dp, bottom = 3.dp)
        assertEquals(6.dp, insets.start)
        assertEquals(2.dp, insets.top)
        assertEquals(8.dp, insets.end)
        assertEquals(3.dp, insets.bottom)
    }
}
