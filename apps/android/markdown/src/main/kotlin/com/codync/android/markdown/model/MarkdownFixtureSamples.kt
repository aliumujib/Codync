package com.codync.android.markdown.model

object MarkdownFixtureSamples {
    val featureMatrixValid = """
        # Markdown Renderer Check

        This paragraph checks **bold**, *italic*, ***bold italic***, ~~strikethrough~~, `inline code`, inline math `${'$'}x = y + 1${'$'}`, a normal link to [OpenAI](https://openai.com), and inline file paths like Package.swift, src/main/kotlin/com/aliumujib/control/App.kt:42, ./feature/conversation/MarkdownText.kt:18:3, and the directory /Users/aliumujib/Desktop.

        > Blockquote check: this should render with a leading bar, muted styling, and preserve inline items like **strong text**, `code`, app/src/main/AndroidManifest.xml:12, and ../Control/controlAndroid/.

        ## Lists

        - First bullet item
        - Second bullet item wraps onto another line so we can inspect multiline alignment against the bullet marker
        - Third bullet references controlAndroid/core/markdown-renderer/src/main/java/com/aliumujib/control/core/markdown/MarkdownInlineParser.kt:77

        1. First numbered item
        2. Second numbered item wraps onto another line so the number alignment is easy to inspect
        3. Third numbered item references controliOS/controliOS/Views/Components/MarkdownText.swift:1209

        ## Task list

        - [x] Completed task rendered with a filled box and struck-through muted text
        - [ ] Pending task with an empty box, which wraps onto a second line so the hanging indent is easy to inspect
        - [] Pending task written without the inner space, as the CLI sometimes emits it
        - Plain bullet mixed into the same list keeps its bullet marker
    """.trimIndent()

    val tableMatrixValid = """
        ## Table

        | Surface | Expected | File |
        | :--- | :---: | ---: |
        | Inline file path | Underlined, tinted, tappable | controlAndroid/feature/conversation/MarkdownText.kt:34 |
        | Directory path | Folder icon and no fake extension | /Users/aliumujib/Desktop |
        | Raw link | Link styling | https://github.com/openai/openai-openapi |
        | Inline code | Monospace chip-like styling | `src/Ignored.kt` |
        | Right aligned cell | Should hug the right side | 42 |
    """.trimIndent()

    val inlineIconMatrixValid = """
        ## Inline Icon Matrix

        A paragraph with /Users/aliumujib/Desktop, ../Control/controlAndroid/, src/main/kotlin/com/aliumujib/control/App.kt:42, README.md, docs/setup.md:9, build.gradle.kts:1, Package.swift, config/app.json, script/deploy.sh, image/logo.svg, and `src/Ignored.kt:99`.

        Website mentions should also look distinct: https://github.com/aliumujib/control, https://openai.com, and https://example.com/docs.
    """.trimIndent()

    val codeBlocksValid = """
        ## Code Block A

        ```bash
        adb shell input tap 500 800
        adb shell input swipe 100 500 100 100 300
        ./gradlew :controlAndroid:feature:conversation:compileDebugKotlinAndroid
        ```

        Paragraph after bash block. This should not be inside the code card.

        ## Code Block B

        ```kotlin
        fun renderMarkdown(sample: String) {
            val file = "controlAndroid/core/markdown-renderer/src/test/kotlin/com/aliumujib/control/core/markdown/MarkdownBlockParserTest.kt:128"
            println(sample)
            println(file)
        }
        ```

        Paragraph after Kotlin block with another file reference: controlAndroid/core/markdown-renderer/src/main/java/com/aliumujib/control/core/markdown/CodyncMarkdownText.kt:22
    """.trimIndent()

    val urlOnlyValid = """
        ## URL-only Block

        https://github.com/aliumujib/control
    """.trimIndent()

    val malformedTableFallback = """
        This should stay paragraph text, not a real table:

        | broken | table |
        | still | plain text |
    """.trimIndent()

    val unclosedFenceTail = """
        ## Unclosed Fence Tail

        This next block is intentionally unclosed so we can inspect the failure mode:

        ```yaml
        name: control
        platform: android
        file: controlAndroid/feature/conversation/MarkdownText.kt:34

        If parsing is resilient, everything from the fence onward should be treated consistently as the unterminated code tail.
    """.trimIndent()

    val complexButValidConversationMessage = """
        # Markdown Torture Test

        Intro paragraph with **bold**, *italic*, ***bold italic***, ~~strikethrough~~, `inline code`, `${'$'}E = mc^2${'$'}`, [OpenAI](https://openai.com), [GitHub](https://github.com), src/main/kotlin/com/aliumujib/control/App.kt:42, ./feature/conversation/MarkdownText.kt:18:3, ../Control/controlAndroid/gradle/libs.versions.toml:166, README.md, docs/setup.md:9, Package.swift, build.gradle.kts:1, and `src/Ignored.kt:99` which should stay inline-code styled rather than file-path styled.

        A second paragraph checks long wrapping behavior for file references like controlAndroid/core/markdown-renderer/src/main/java/com/aliumujib/control/core/markdown/MarkdownInlineParser.kt:77, controliOS/controliOS/Views/Components/MarkdownText.swift:1209, /Users/aliumujib/Desktop, and ../controlAndroid/core/markdown-renderer/src/main/java/com/aliumujib/control/core/markdown/ while also mixing a raw URL https://github.com/aliumujib/control and another one in brackets [Control Repo](https://github.com/aliumujib/control).

        > Quote level one with **strong text**, `inline code`, src/test/resources/sample.json:12, and a normal URL https://example.com/docs.

        ## Lists

        - Short bullet
        - Wrapped bullet item with a very long explanation so we can inspect hanging indent alignment against the bullet marker and make sure the continuation line does not jump under the bullet itself
        - Bullet with file references: controlAndroid/core/markdown-renderer/src/test/kotlin/com/aliumujib/control/core/markdown/MarkdownBlockParserTest.kt:128 and app/build.gradle.kts:1

        1. First numbered item
        2. Second numbered item with a long continuation line that should align under the text instead of under the numeral itself while still keeping the spacing readable
        3. Third numbered item with src/main/AndroidManifest.xml:5 and docs/setup.md:9

        | Surface | Expected Rendering | Example |
        | :--- | :--- | ---: |
        | Plain text | Normal paragraph text | hello world |
        | Inline code | Monospace treatment | `adb devices` |
        | File path | Underlined tinted tappable path | controlAndroid/feature/conversation/MarkdownText.kt:34 |
        | URL | Link styling | https://github.com/aliumujib/control |
        | Right-aligned data | Should hug right edge | 42 |

        ```bash
        adb shell input tap 500 800
        adb shell input swipe 100 500 100 100 300
        adb shell input text "hello_world"
        ```

        Paragraph after bash block. This should not be inside the code card.

        ```kotlin
        data class MarkdownSample(
            val title: String,
            val filePath: String,
        )
        ```

        https://github.com/aliumujib/control
    """.trimIndent()

    val adjacentBlockBoundariesValid = """
        | One | Two |
        | --- | --- |
        | A | B |

        Paragraph between table and code block.

        ```swift
        struct MarkdownView: View {
            let path = "controliOS/controliOS/Views/Components/MarkdownText.swift:1209"
        }
        ```

        Paragraph between code block and URL-only block.

        https://example.com/just-a-url
    """.trimIndent()
}
