package com.codync.android.markdown

import androidx.compose.runtime.Composable
import androidx.compose.runtime.currentComposer

data class SelectedSpan(
    val blockText: String,
    val text: String,
    val startOffset: Int,
    val endOffset: Int,
)

object CodeSelectionQuote {

    @Composable
    fun currentRegistrar(): Any? =
        SelectionSpanReflect.currentSelectionRegistrar(currentComposer)

    fun extractSpans(registrar: Any?): List<SelectedSpan>? =
        SelectionSpanReflect.extractSelectedSpans(registrar)
            ?.map {
                SelectedSpan(
                    blockText = it.text,
                    text = it.text.substring(it.startOffset, it.endOffset),
                    startOffset = it.startOffset,
                    endOffset = it.endOffset,
                )
            }
            ?.takeIf { spans -> spans.any { it.text.isNotBlank() } }
}
