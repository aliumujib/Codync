package com.codync.android

import com.codync.android.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

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

/** A shared file open in the viewer, and the message it came in. */
data class ViewedFile(val entry: Entry, val file: SharedFile)

fun AppStore.viewFile(entry: Entry, file: SharedFile) = mutableState.update { it.copy(viewing = ViewedFile(entry, file)) }

fun AppStore.closeViewer() = mutableState.update { it.copy(viewing = null) }

/**
 * The viewer's side of a shared file: chunks read straight from the computer for playing it, and the
 * same checked download the download button makes for everything else, kept while the app runs.
 */
internal class SharedViewerFile(private val store: AppStore, private val viewed: ViewedFile) : com.codync.android.viewer.ViewerFile {
    override val id: String = viewed.file.id
    override val name: String = viewed.file.name
    override val size: Long = viewed.file.size

    override suspend fun readChunk(offset: Long): ByteArray =
        HostFiles(store.ready()).readSharedChunk(viewed.entry.id, viewed.file, offset)

    override suspend fun download(progress: (Long) -> Unit): java.io.File {
        viewerCopies[id]?.takeIf { it.isFile }?.let { progress(size); return it }
        val folder = withContext(Dispatchers.IO) { store.attachments.shareFolder() }
        return HostFiles(store.ready()).downloadShared(viewed.entry.id, viewed.file, folder, progress).also { viewerCopies[id] = it }
    }

    private companion object {
        /** Checked copies by file ID. The share folder clears them after a day; a missing one downloads again. */
        val viewerCopies = java.util.concurrent.ConcurrentHashMap<String, java.io.File>()
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
