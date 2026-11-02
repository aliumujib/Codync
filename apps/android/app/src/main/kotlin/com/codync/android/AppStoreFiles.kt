package com.codync.android

import com.codync.android.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.decodeFromJsonElement

// Files a bot shares in a chat (iOS `FileDownloads`): one download at a time, checked against its
// SHA-256, then handed to the system share sheet.

/** Where a shared file's download is, as iOS `FileDownloads.State`. */
sealed interface FileDownloadState {
    data class Downloading(val received: Long) : FileDownloadState
    data object Saved : FileDownloadState
    data class Failed(val message: String) : FileDownloadState
}

private fun downloadKey(fileId: String) = "file:$fileId"

/** Downloads `file` (shared in message `entryId` of chat `botId`) and opens the share sheet, if that chat is still on screen by then. */
fun AppStore.saveFile(entryId: String, botId: String, file: SharedFile) {
    val running = state.value.busy.filter { it.startsWith("file:") }
    if (running.isNotEmpty()) {
        if (downloadKey(file.id) !in running) setDownload(file.id, FileDownloadState.Failed("Finish the current download first."))
        return
    }
    action(downloadKey(file.id)) {
        try {
            setDownload(file.id, FileDownloadState.Downloading(0))
            val folder = withContext(Dispatchers.IO) { attachments.shareFolder() }
            val saved = HostFiles(ready()).downloadShared(entryId, file, folder) { setDownload(file.id, FileDownloadState.Downloading(it)) }
            setDownload(file.id, FileDownloadState.Saved)
            if (showing(botId)) attachments.open(saved, file.name)
        } catch (cancelled: CancellationException) {
            setDownload(file.id, null)
            throw cancelled
        } catch (error: Exception) {
            setDownload(file.id, FileDownloadState.Failed(error.message ?: "Couldn't download the file."))
        }
    }
}

/** A file in a chat: one a bot shared in a message, or one the user sent. */
sealed interface ChatFile {
    val id: String
    val name: String
    val size: Long

    data class Shared(val entryId: String, val file: SharedFile) : ChatFile {
        override val id get() = file.id
        override val name get() = file.name
        override val size get() = file.size
    }

    data class Sent(val botId: String, val file: Attachment) : ChatFile {
        override val id get() = file.id
        override val name get() = file.name
        override val size get() = file.size
    }
}

/** The viewer's slideshow: a chat's files, oldest first, opened at [start]. */
data class Gallery(val botId: String, val files: List<ChatFile>, val start: Int)

/** Files the user attached to a message. */
internal val Entry.sentFiles: List<Attachment>
    get() = (data["attachments"] as? kotlinx.serialization.json.JsonArray)
        ?.let { runCatching { WireJson.decodeFromJsonElement<List<Attachment>>(it) }.getOrNull() }.orEmpty()

/** Every file in [botId]'s chat as far as this phone has it, oldest first. */
internal fun AppState.chatFiles(botId: String): List<ChatFile> = entries.filter { it.botId == botId }.sortedBy { it.seq }
    .flatMap { entry -> entry.files.map { ChatFile.Shared(entry.id, it) } + entry.sentFiles.map { ChatFile.Sent(botId, it) } }

/** Opens [file] (shared in [entry]) as part of its chat's slideshow. */
fun AppStore.viewFile(entry: Entry, file: SharedFile) {
    val files = state.value.chatFiles(entry.botId).ifEmpty { listOf(ChatFile.Shared(entry.id, file)) }
    viewGallery(entry.botId, files, files.indexOfFirst { it.id == file.id }.coerceAtLeast(0))
}

fun AppStore.viewGallery(botId: String, files: List<ChatFile>, start: Int) =
    mutableState.update { it.copy(viewing = Gallery(botId, files, start)) }

fun AppStore.closeViewer() = mutableState.update { it.copy(viewing = null) }

internal fun ChatFile.viewerFile(store: AppStore): com.codync.android.viewer.ViewerFile = when (this) {
    is ChatFile.Shared -> SharedViewerFile(store, entryId, file)
    is ChatFile.Sent -> SentViewerFile(store, botId, file)
}

/** Checked copies by file ID, in the share folder so Share and Open with can hand them out. The folder clears them after a day. */
private val viewerCopies = java.util.concurrent.ConcurrentHashMap<String, java.io.File>()

/**
 * The viewer's side of a shared file: chunks read straight from the computer for playing it, and the
 * same checked download the download button makes for everything else, kept while the app runs.
 */
internal class SharedViewerFile(private val store: AppStore, private val entryId: String, private val file: SharedFile) : com.codync.android.viewer.ViewerFile {
    override val id: String = file.id
    override val name: String = file.name
    override val size: Long = file.size

    override suspend fun readChunk(offset: Long): ByteArray =
        HostFiles(store.ready()).readSharedChunk(entryId, file, offset)

    override suspend fun download(progress: (Long) -> Unit): java.io.File {
        viewerCopies[id]?.takeIf { it.isFile }?.let { progress(size); return it }
        val folder = withContext(Dispatchers.IO) { store.attachments.shareFolder() }
        return HostFiles(store.ready()).downloadShared(entryId, file, folder, progress).also { viewerCopies[id] = it }
    }
}

/** A file the user sent: fetched once into the attachment cache (often already there), then read from the copy. */
internal class SentViewerFile(private val store: AppStore, private val botId: String, private val file: Attachment) : com.codync.android.viewer.ViewerFile {
    override val id: String = file.id
    override val name: String = file.name
    override val size: Long = file.size

    override suspend fun readChunk(offset: Long): ByteArray = withContext(Dispatchers.IO) {
        java.io.RandomAccessFile(download {}, "r").use { raf ->
            val length = minOf(com.codync.android.viewer.ViewerFile.CHUNK_BYTES.toLong(), size - offset).toInt().coerceAtLeast(0)
            ByteArray(length).also { raf.seek(offset); raf.readFully(it) }
        }
    }

    override suspend fun download(progress: (Long) -> Unit): java.io.File {
        viewerCopies[id]?.takeIf { it.isFile }?.let { progress(size); return it }
        val cached = store.attachment(botId, file)
        return withContext(Dispatchers.IO) {
            val folder = store.attachments.shareFolder().apply { mkdirs() }
            cached.copyTo(java.io.File(folder, AttachmentFiles.cleanName(file.name)), overwrite = true)
        }.also { progress(size); viewerCopies[id] = it }
    }
}

fun AppStore.cancelFile(fileId: String) {
    actions[downloadKey(fileId)]?.cancel()
    setDownload(fileId, null)
}

private fun AppStore.setDownload(fileId: String, download: FileDownloadState?) = mutableState.update {
    it.copy(fileDownloads = if (download == null) it.fileDownloads - fileId else it.fileDownloads + (fileId to download))
}

/** The chat is the one open. */
private fun AppStore.showing(botId: String): Boolean = foreground && state.value.selectedBot == botId
