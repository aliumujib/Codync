package com.codync.android

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.codync.android.core.*
import com.codync.android.design.*
import com.codync.android.viewer.FileKind
import com.kyant.shapes.RoundedRectangle

/** How the Files sheet groups a chat's files, in the order the groups show. */
internal enum class FileGroup(val title: String) {
    Videos("Videos"), Images("Images"), Documents("Documents"), Audio("Audio"), Other("Other");

    companion object {
        fun of(name: String): FileGroup = when (FileKind.of(name)) {
            FileKind.Video -> Videos
            FileKind.Image -> Images
            FileKind.Pdf, FileKind.Markdown, FileKind.Text -> Documents
            FileKind.Audio -> Audio
            FileKind.Zip, FileKind.Other -> Other
        }

        /** [files] grouped, each group newest first; empty groups left out. */
        fun groups(files: List<ChatFile>): List<Pair<FileGroup, List<ChatFile>>> =
            files.groupBy { of(it.name) }.toList().sortedBy { it.first.ordinal }.map { (group, members) -> group to members.reversed() }
    }
}

/**
 * Every file in [bot]'s chat, grouped by kind, each group a row that scrolls sideways; the chat's artifacts as their
 * own row. A file opens the viewer as a slideshow of all of the chat's files, so the rest are a swipe away.
 */
@Composable internal fun ChatFilesSheet(bot: Bot, state: AppState, store: AppStore, openArtifact: (OpenArtifact) -> Unit, close: () -> Unit) {
    val online = state.actualConnection is LinkState.Ready
    LaunchedEffect(bot.id, online) { if (online) store.loadLibrary(bot.id) }
    val files = state.chatFiles(bot.id)
    val artifacts = state.artifacts.library(bot.id, state.library[bot.id].orEmpty()).filterIsInstance<LibraryItem.ArtifactItem>()
    Column(Modifier.fillMaxWidth()) {
        ModalHeader("Files")
        if (files.isEmpty() && artifacts.isEmpty()) {
            Text("No files in this chat yet. Files you send and files ${bot.name} shares show here.",
                Modifier.padding(horizontal = 20.dp, vertical = 24.dp), style = Type.subheadline, color = Palette.secondary)
            return@Column
        }
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            for ((group, members) in FileGroup.groups(files)) Shelf(group.title, members.size) {
                items(members, key = { it.id }) { file ->
                    FileTile(file, state, store) { close(); store.viewGallery(bot.id, files, files.indexOf(file)) }
                }
            }
            if (artifacts.isNotEmpty()) Shelf("Artifacts", artifacts.size) {
                items(artifacts, key = { it.artifact.id }) { item ->
                    Tile(item.artifact.title, "Artifact", Modifier.plainButton {
                        close(); openArtifact(OpenArtifact(bot.id, item.artifact.id, item.artifact.title, item.artifact.entryId))
                    }) { Symbol("doc.richtext", size = 34.dp, tint = Palette.secondary) }
                }
            }
        }
    }
}

/** A group's heading and its row of tiles. */
@Composable private fun Shelf(title: String, count: Int, tiles: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = Type.footnote.semibold, color = Palette.secondary)
            Text("$count", style = Type.footnote, color = Palette.tertiary)
        }
        LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), content = tiles)
    }
}

/** A file's tile: the picture itself for an image, else its kind's symbol; then its name and size. */
@Composable private fun FileTile(file: ChatFile, state: AppState, store: AppStore, open: () -> Unit) {
    val picture = Modifier.fillMaxSize()
    Tile(file.name, formatBytes(file.size), Modifier.plainButton(action = open).semantics { contentDescription = "Open " + file.name }) {
        when {
            file is ChatFile.Shared && file.file.showsAsImage -> SharedImage(file.file, file.entryId, 360, state, store, picture)
            file is ChatFile.Sent && file.file.isImage -> AttachmentImage(file.file, file.botId, 360, state, store, picture)
            else -> Symbol(FileKind.of(file.name).symbol, size = 34.dp, tint = Palette.secondary)
        }
    }
}

@Composable private fun Tile(title: String, detail: String, modifier: Modifier, preview: @Composable () -> Unit) {
    Column(modifier.width(148.dp).clip(RoundedRectangle(14.dp)).background(Palette.bubbleAgent)) {
        Box(Modifier.fillMaxWidth().height(112.dp).background(Palette.text.copy(alpha = 0.04f)), contentAlignment = Alignment.Center) { preview() }
        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = Type.caption.semibold, color = Palette.text, maxLines = 2, overflow = TextOverflow.MiddleEllipsis)
            Text(detail, style = Type.caption2, color = Palette.secondary, maxLines = 1)
        }
    }
}
