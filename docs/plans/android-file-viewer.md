# Android file viewer

Status: built on branch `android-file-viewer` (Plum fork, on top of v2.23.0). Android only; the iPhone app
still hands a bot's file to the share sheet.

## Spec

Tapping a file a bot shared in a chat opens it full screen inside the app instead of going straight to the
share sheet. The viewer lives in one Gradle module, `:viewer`, and looks like the rest of Codync: it is
built only from the `:design` module (Liquid Glass chrome, `IconButton`, `Spinner`, `Palette`, `Type`,
`Symbol`).

| File | How it opens |
|---|---|
| Video (mp4, m4v, mov, webm, mkv, 3gp) | **Streamed**: Media3 ExoPlayer plays while it reads the file from the computer in 384 KiB `readFile` chunks; seeking jumps straight to the chunk it needs. Play/pause, a seek bar, elapsed and total time on glass. |
| Audio (mp3, m4a, aac, wav, ogg, opus, flac) | Streamed the same way, with the same controls under a waveform symbol. |
| Image (png, jpg, gif, webp, heic, avif, bmp) | Downloaded, then pinch-zoom and pan (Telephoto on Coil 3). |
| PDF | Downloaded, then rendered page by page with Android's `PdfRenderer`; pages scroll vertically and zoom; a glass chip shows the page. |
| Markdown | Downloaded, rendered with the app's own `MarkdownText` (passed in by the app). |
| Text, code, logs, JSON, CSV, … | Downloaded, monospace lines, selectable. Files over 4 MiB show the first 4 MiB and say so. |
| ZIP | Downloaded, entries listed with sizes; tapping one extracts it and opens it in the same viewer (back returns to the list). |
| Anything else | "No preview for this file" with Share and Open with. |

Always available in the header: close, the file name and size, Share (system share sheet), and Open with
(another app). Share/Open with use the downloaded, SHA-256-checked copy, so a streamed video is downloaded
in full first.

Not in scope: browsing folders on the computer (needs a host endpoint), Office documents, files the user
sent (their images already show inline).

## Design

- **`:viewer` depends on `:design`** for the look and knows nothing about the host protocol. The app passes
  a `ViewerFile`: the name, the size, `readChunk(offset)` (one `readFile` call) and `download(progress)`
  (the existing verified `HostFiles.downloadShared`).
- **Streaming** (`ChunkSource` + `ChunkDataSource`): Media3 asks for bytes at a position; the source maps it
  to the 384 KiB chunk that holds it, reads chunks through a small in-memory LRU (about 24 MiB, enough to
  hold a TikTok-length video, so a loop or a seek back doesn't refetch) and prefetches the next chunk while
  the current one plays. Streamed bytes aren't hash-checked (the hash covers the whole file); everything
  that leaves the app (Share, Open with) goes through the verified download.
- **Type detection** (`FileKind`): by extension, falling back to "text" when the first 4 KiB decode as
  UTF-8 without control characters.
- **ZIP safety**: entries are listed from the central directory; an opened entry is extracted under a fresh
  folder by its file name only (no paths, so no zip-slip), capped at 200 MiB.
- **App integration** (the only app changes): `AppState.viewing` holds the open file; the file card's body
  opens it (the download button keeps saving to the share sheet); `ChatScreen` draws the viewer as an
  `Overlay` so it covers the whole app and survives the chat scrolling; a small adapter turns
  `HostFiles` into a `ViewerFile`; the downloaded copy lives under the existing FileProvider folder
  (`cache/shared-attachments`) so Share and Open with work.

## Plan

1. Module scaffold: `settings.gradle.kts`, `viewer/build.gradle.kts`, version catalog entries (Media3
   1.11.1, Telephoto 0.19.0).
2. Pure logic with JVM tests: `FileKind`, `ChunkSource` (offset→chunk mapping, LRU, prefetch, end of file),
   `ZipEntries` (listing, safe extraction).
3. `ChunkDataSource` (Media3 `BaseDataSource` over `ChunkSource`).
4. UI: `FileViewer` (chrome, loading, error, nested ZIP entries), `MediaPane`, `ImagePane`, `PdfPane`,
   `TextPane`, `ZipPane`, `NoPreviewPane`.
5. App integration as above.
6. Build the release APK, install on the Pixel, open each file type from a test bot that shares one of
   each, and check streaming, seeking, zoom, ZIP drill-down, Share and Open with.

## Tested (25 Oct 2026, Pixel 9, release build, host 2.23.0 on the NAS over Wi-Fi)

A bot shared one file of each kind; each opened from its card:
- mp4 (3.1 MB): first frame almost at once, streamed; seek by tapping the bar; tap hides/shows the bars;
  back closes only the viewer. Share downloaded the checked copy ("Preparing to share 36%") then opened
  the share sheet.
- mp3: waveform screen, played, audio focus released on close.
- png (1.7 MB): download progress, then a spinner while it decodes, then pinch/double-tap zoom.
- pdf (9 pages): pages render, the chip follows the scroll, tap a page to zoom, back returns to the list.
- md, json: the chat's Markdown renderer; monospace text.
- zip: 4 entries listed; a txt and a png inside open on top with a back arrow; Share on the extracted txt
  works (FileProvider path); back returns to the list without downloading again.
- random .bin: "No preview" with Open with (system chooser) and Share.

Fixed during testing: a spinner while an image decodes (it sat black for 2–3 s); white status and nav
bar icons over the black photo/video screens in light mode; a grey ground and a hairline round PDF pages
so white pages don't vanish into the light theme.

Unit tests: `:viewer` 15 (type detection, chunk reads/seek/LRU/read-ahead/errors, ZIP listing and safe
extraction, text preview), `:core` 146 including `readSharedChunk`, `:app` 25.
