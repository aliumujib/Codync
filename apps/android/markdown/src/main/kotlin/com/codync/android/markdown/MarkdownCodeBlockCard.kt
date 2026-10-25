package com.codync.android.markdown

import com.codync.android.design.Type

import com.codync.android.design.Symbol

import com.codync.android.markdown.model.*

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

@Composable
internal fun MarkdownCodeBlockCard(
    block: MarkdownCodeBlock,
    theme: MarkdownTheme,
    modifier: Modifier = Modifier,
) {
    val clipboardManager = LocalClipboardManager.current
    var copied by remember(block.code) { mutableStateOf(false) }
    val shape = RoundedCornerShape(8.dp)

    if (copied) {
        LaunchedEffect(block.code) {
            delay(2_000)
            copied = false
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .border(0.5.dp, theme.borderColor, shape)
            .background(theme.codeBackground),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(theme.codeHeaderBackground)
                .padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val language = block.language?.takeIf { it.isNotBlank() }
            if (language != null) {
                Text(
                    text = language,
                    color = theme.mutedTextColor,
                    style = theme.codeCaptionStyle.copy(
                        fontFamily = FontFamily.Monospace,
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(theme.inlineCodeBackground.copy(alpha = 0.5f))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            } else {
                Box(modifier = Modifier.width(1.dp))
            }

            CodeCopyButton(
                copied = copied,
                theme = theme,
                onClick = {
                    clipboardManager.setText(AnnotatedString(block.code))
                    copied = true
                },
            )
        }

        if (block.code.length > VirtualCodeBlockThresholdChars) {
            VirtualizedCodeBlock(
                code = block.code,
                theme = theme,
            )
        } else {
            val highlighted = remember(block.code, theme) {
                CodeSyntaxHighlighter.highlight(block.code, theme)
            }
            Box(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(12.dp),
            ) {
                Text(
                    text = highlighted,
                    style = theme.codeStyle,
                )
            }
        }
    }
}

@Composable
private fun VirtualizedCodeBlock(
    code: String,
    theme: MarkdownTheme,
) {
    val lines = remember(code) { code.split('\n') }
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 560.dp)
            .padding(vertical = 8.dp),
    ) {
        itemsIndexed(
            items = lines,
            key = { index, _ -> index },
        ) { _, line ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 1.dp),
            ) {
                Text(
                    text = remember(line, theme) {
                        CodeSyntaxHighlighter.highlight(line.ifEmpty { " " }, theme)
                    },
                    style = theme.codeStyle,
                )
            }
        }
    }
}

@Composable
private fun CodeCopyButton(
    copied: Boolean,
    theme: MarkdownTheme,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .height(COPY_BUTTON_HEIGHT)
            .markdownPressFeedback(interactionSource)
            .background(
                theme.textColor.copy(alpha = COPY_BUTTON_CONTAINER_ALPHA),
                RoundedCornerShape(MarkdownRadius.pill),
            )
            .clip(RoundedCornerShape(MarkdownRadius.pill))
            .clickable(
                role = Role.Button,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = COPY_BUTTON_PADDING_H),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Symbol(if (copied) "checkmark" else "doc.on.doc", size = MarkdownIconSize.capsule, tint = theme.mutedTextColor)
        Text(
            text = if (copied) "Copied" else "Copy",
            color = theme.mutedTextColor,
            style = Type.caption2,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

private const val VirtualCodeBlockThresholdChars = 12_000
private val COPY_BUTTON_HEIGHT = 30.dp
private val COPY_BUTTON_PADDING_H = 12.dp
private const val COPY_BUTTON_CONTAINER_ALPHA = 0.07f
