package com.codync.android.markdown

import com.codync.android.markdown.model.*

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.contextmenu.builder.item
import androidx.compose.foundation.text.contextmenu.modifier.appendTextContextMenuComponents
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.codync.android.design.Type
import androidx.compose.ui.graphics.Color

@Composable
fun CodyncMarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    style: TextStyle = Type.body,
    color: Color = Color.Unspecified,
    linkColor: Color = Color.Unspecified,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    blockHorizontalPadding: Dp = 0.dp,
    onLinkClicked: (String) -> Unit = {},
    onFilePathClicked: (MarkdownInline.FilePath) -> Unit = {},
    onCommandClicked: (String) -> Unit = {},
    tokenContext: InlineTokenContext = InlineTokenContext.AssistantText,

    selectable: Boolean = true,

    onReplyToBlock: ((String) -> Unit)? = null,
    replyMenuLabel: String = "Reply",

    onReplyMenuBuilt: (() -> Unit)? = null,
    infoMenuLabel: String? = null,
    onInfoClicked: (() -> Unit)? = null,
) {
    val document = rememberMarkdownDocument(markdown)
    CodyncMarkdownDocument(
        document = document,
        plainText = markdown,
        modifier = modifier,
        style = style,
        color = color,
        linkColor = linkColor,
        maxLines = maxLines,
        overflow = overflow,
        blockHorizontalPadding = blockHorizontalPadding,
        onLinkClicked = onLinkClicked,
        onFilePathClicked = onFilePathClicked,
        onCommandClicked = onCommandClicked,
        tokenContext = tokenContext,
        selectable = selectable,
        onReplyToBlock = onReplyToBlock,
        replyMenuLabel = replyMenuLabel,
        onReplyMenuBuilt = onReplyMenuBuilt,
        infoMenuLabel = infoMenuLabel,
        onInfoClicked = onInfoClicked,
        reveal = MarkdownReveal.Full,
    )
}

@Composable
fun CodyncMarkdownText(
    document: MarkdownDocument,
    sourceText: String = "",
    modifier: Modifier = Modifier,
    style: TextStyle = Type.body,
    color: Color = Color.Unspecified,
    linkColor: Color = Color.Unspecified,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    blockHorizontalPadding: Dp = 0.dp,
    onLinkClicked: (String) -> Unit = {},
    onFilePathClicked: (MarkdownInline.FilePath) -> Unit = {},
    onCommandClicked: (String) -> Unit = {},
    tokenContext: InlineTokenContext = InlineTokenContext.AssistantText,
    selectable: Boolean = true,
    onReplyToBlock: ((String) -> Unit)? = null,
    replyMenuLabel: String = "Reply",
    onReplyMenuBuilt: (() -> Unit)? = null,
    infoMenuLabel: String? = null,
    onInfoClicked: (() -> Unit)? = null,
    reveal: MarkdownReveal = MarkdownReveal.Full,
) {
    CodyncMarkdownDocument(
        document = document,
        plainText = sourceText,
        modifier = modifier,
        style = style,
        color = color,
        linkColor = linkColor,
        maxLines = maxLines,
        overflow = overflow,
        blockHorizontalPadding = blockHorizontalPadding,
        onLinkClicked = onLinkClicked,
        onFilePathClicked = onFilePathClicked,
        onCommandClicked = onCommandClicked,
        tokenContext = tokenContext,
        selectable = selectable,
        onReplyToBlock = onReplyToBlock,
        replyMenuLabel = replyMenuLabel,
        onReplyMenuBuilt = onReplyMenuBuilt,
        infoMenuLabel = infoMenuLabel,
        onInfoClicked = onInfoClicked,
        reveal = reveal,
    )
}

@Composable
private fun CodyncMarkdownDocument(
    document: MarkdownDocument,
    plainText: String,
    modifier: Modifier,
    style: TextStyle,
    color: Color,
    linkColor: Color,
    maxLines: Int,
    overflow: TextOverflow,
    blockHorizontalPadding: Dp,
    onLinkClicked: (String) -> Unit,
    onFilePathClicked: (MarkdownInline.FilePath) -> Unit,
    onCommandClicked: (String) -> Unit,
    tokenContext: InlineTokenContext,
    selectable: Boolean,
    onReplyToBlock: ((String) -> Unit)?,
    replyMenuLabel: String,
    onReplyMenuBuilt: (() -> Unit)?,
    infoMenuLabel: String?,
    onInfoClicked: (() -> Unit)?,
    reveal: MarkdownReveal,
) {
    val theme = rememberMarkdownTheme(style = style, color = color, linkColor = linkColor)

    var selectionEpoch by remember { mutableIntStateOf(0) }

    val registrarHolder = remember { arrayOfNulls<Any?>(1) }

    val replyModifier = if (onReplyToBlock != null || infoMenuLabel != null) {
        Modifier.appendTextContextMenuComponents {
            if (onReplyToBlock != null) {
                onReplyMenuBuilt?.invoke()
                item(key = "control-reply-md", label = replyMenuLabel) {
                    val span = SelectionSpanReflect.extractSelectedText(registrarHolder[0])
                    val text = (span ?: plainText).trim()
                    if (text.isNotBlank()) onReplyToBlock(text)
                    close()
                    selectionEpoch++
                }
            }
            if (infoMenuLabel != null) {
                item(key = "control-info-md", label = infoMenuLabel) { onInfoClicked?.invoke(); close() }
            }
        }
    } else {
        Modifier
    }

    val blocks: @Composable () -> Unit = {

        if (onReplyToBlock != null) {
            val captured = SelectionSpanReflect.currentSelectionRegistrar(
                androidx.compose.runtime.currentComposer,
            )
            SideEffect { registrarHolder[0] = captured }
        }
        Column(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(theme.blockSpacing),
        ) {
            document.blocks.forEachIndexed { index, block ->
                MarkdownBlockView(
                    block = block,
                    theme = theme,
                    maxLines = if (document.blocks.size == 1) maxLines else Int.MAX_VALUE,
                    overflow = overflow,
                    blockHorizontalPadding = blockHorizontalPadding,
                    onLinkClicked = onLinkClicked,
                    onFilePathClicked = onFilePathClicked,
                    onCommandClicked = onCommandClicked,
                    tokenContext = tokenContext,
                    reveal = if (index == document.blocks.lastIndex) reveal else MarkdownReveal.Full,
                )
            }
        }
    }

    if (selectable) {

        key(selectionEpoch) {
            SelectionContainer(modifier = replyModifier, content = blocks)
        }
    } else {
        blocks()
    }
}

@Composable
private fun rememberMarkdownDocument(markdown: String): MarkdownDocument =
    remember(markdown) { MarkdownBlockParser.parse(markdown) }

@Composable
internal fun MarkdownNestedBlocks(
    blocks: List<MarkdownBlock>,
    theme: MarkdownTheme,
    onLinkClicked: (String) -> Unit,
    onFilePathClicked: (MarkdownInline.FilePath) -> Unit,
    onCommandClicked: (String) -> Unit,
    tokenContext: InlineTokenContext,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(theme.nestedBlockSpacing),
    ) {
        blocks.forEach { block ->
            MarkdownBlockView(
                block = block,
                theme = theme,
                maxLines = Int.MAX_VALUE,
                overflow = TextOverflow.Clip,
                blockHorizontalPadding = 0.dp,
                onLinkClicked = onLinkClicked,
                onFilePathClicked = onFilePathClicked,
                onCommandClicked = onCommandClicked,
                tokenContext = tokenContext,
            )
        }
    }
}

@Composable
internal fun MarkdownBlockView(
    block: MarkdownBlock,
    theme: MarkdownTheme,
    maxLines: Int,
    overflow: TextOverflow,
    blockHorizontalPadding: Dp,
    onLinkClicked: (String) -> Unit,
    onFilePathClicked: (MarkdownInline.FilePath) -> Unit,
    onCommandClicked: (String) -> Unit,
    tokenContext: InlineTokenContext,
    reveal: MarkdownReveal = MarkdownReveal.Full,
) {
    val paddedModifier = Modifier.padding(horizontal = blockHorizontalPadding)
    when (block) {
        is MarkdownFootnoteSection -> MarkdownFootnoteSectionView(
            section = block,
            theme = theme,
            modifier = paddedModifier,
            onLinkClicked = onLinkClicked,
            onFilePathClicked = onFilePathClicked,
            onCommandClicked = onCommandClicked,
            tokenContext = tokenContext,
        )

        is MarkdownBlockquote -> MarkdownBlockquoteView(
            block = block,
            theme = theme,
            modifier = paddedModifier,
            onLinkClicked = onLinkClicked,
            onFilePathClicked = onFilePathClicked,
            onCommandClicked = onCommandClicked,
            tokenContext = tokenContext,
        )

        is MarkdownBulletList -> MarkdownBulletListView(
            block = block,
            theme = theme,
            modifier = paddedModifier,
            onLinkClicked = onLinkClicked,
            onFilePathClicked = onFilePathClicked,
            onCommandClicked = onCommandClicked,
            tokenContext = tokenContext,
        )

        is MarkdownCodeBlock -> MarkdownCodeBlockCard(
            block = block,
            theme = theme,
            modifier = paddedModifier,
        )

        is MarkdownHeading -> MarkdownRichText(
            nodes = block.children,
            theme = theme,
            modifier = paddedModifier,
            style = theme.headingStyles.getOrElse(block.level - 1) { theme.bodyStyle },
            onLinkClicked = onLinkClicked,
            onFilePathClicked = onFilePathClicked,
            onCommandClicked = onCommandClicked,
            tokenContext = tokenContext,
            reveal = reveal,
        )

        MarkdownHorizontalRule -> Box(
            modifier = Modifier
                .padding(horizontal = blockHorizontalPadding)
                .fillMaxWidth()
                .height(1.dp)
                .background(theme.dividerColor),
        )

        is MarkdownOrderedList -> MarkdownOrderedListView(
            block = block,
            theme = theme,
            modifier = paddedModifier,
            onLinkClicked = onLinkClicked,
            onFilePathClicked = onFilePathClicked,
            onCommandClicked = onCommandClicked,
            tokenContext = tokenContext,
        )

        is MarkdownParagraph -> MarkdownRichText(
            nodes = block.children,
            theme = theme,
            modifier = paddedModifier,
            maxLines = maxLines,
            overflow = overflow,
            onLinkClicked = onLinkClicked,
            onFilePathClicked = onFilePathClicked,
            onCommandClicked = onCommandClicked,
            tokenContext = tokenContext,
            reveal = reveal,
        )

        is MarkdownTable -> MarkdownTableView(
            table = block,
            theme = theme,
            onLinkClicked = onLinkClicked,
            onFilePathClicked = onFilePathClicked,
            onCommandClicked = onCommandClicked,
            tokenContext = tokenContext,
        )

        is MarkdownUrlBlock -> MarkdownRichText(
            nodes = listOf(
                MarkdownInline.Link(
                    label = listOf(MarkdownInline.Text(block.url)),
                    destination = block.url,
                ),
            ),
            theme = theme,
            modifier = paddedModifier,
            onLinkClicked = onLinkClicked,
            onFilePathClicked = onFilePathClicked,
            onCommandClicked = onCommandClicked,
            tokenContext = tokenContext,
        )
    }
}
