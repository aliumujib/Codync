package com.codync.android

import com.codync.android.core.*
import kotlinx.serialization.json.*
import org.junit.Assert.assertEquals
import org.junit.Test

class ChatFilesTest {
    private fun shared(name: String) = SharedFile(name, name, 10, "0".repeat(64))

    @Test fun filesGroupByKindInAFixedOrderNewestFirst() {
        val files = listOf("a.mp4", "notes.md", "b.png", "c.mp4", "x.zip").map { ChatFile.Shared("e", shared(it)) }
        val groups = FileGroup.groups(files).map { (g, m) -> g to m.map { it.name } }
        assertEquals(listOf(FileGroup.Videos to listOf("c.mp4", "a.mp4"), FileGroup.Images to listOf("b.png"),
            FileGroup.Documents to listOf("notes.md"), FileGroup.Other to listOf("x.zip")), groups)
    }

    @Test fun aChatsFilesAreItsSharedAndSentFilesOldestFirst() {
        val sent = Entry("u1", seq = 1, botId = "b", rev = 1, kind = "user", data = buildJsonObject {
            put("attachments", buildJsonArray { add(buildJsonObject { put("id", "s1"); put("name", "brief.pdf"); put("size", 5) }) })
        })
        val reply = Entry("a1", seq = 2, botId = "b", rev = 2, kind = "agent", data = buildJsonObject {
            put("files", WireJson.encodeToJsonElement(listOf(shared("cut.mp4"))))
        })
        val other = Entry("o1", seq = 3, botId = "other", rev = 3, kind = "agent", data = buildJsonObject {
            put("files", WireJson.encodeToJsonElement(listOf(shared("elsewhere.png"))))
        })
        val state = AppState(Computer("c", "Studio", "c"), entries = listOf(reply, other, sent))
        assertEquals(listOf("brief.pdf", "cut.mp4"), state.chatFiles("b").map { it.name })
    }
}
