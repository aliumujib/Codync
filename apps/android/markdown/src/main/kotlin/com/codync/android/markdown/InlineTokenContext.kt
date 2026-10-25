package com.codync.android.markdown

/**
 * Where an [InlineTokenPill] is being rendered — drives both its colour/shape variant and its
 * padding. A pill flowing inside wrapping prose must stay vertically compact so it doesn't inflate
 * the line and collide with pills on the adjacent wrapped line; a standalone pill (a lone token-only
 * bubble) can be roomier.
 */
enum class InlineTokenContext {
    /** Inside assistant markdown text — compact padding, assistant colours. */
    AssistantText,

    /** Inline among prose in a user bubble — compact padding, user-bubble colours. */
    UserBubbleInline,

    /** A lone pill that is the whole user bubble — roomy padding, user-bubble colours + tail shape. */
    UserBubbleStandalone,
}

/** True when the pill sits inside a user bubble (drives the lightened command colours + tail shape). */
internal val InlineTokenContext.isUserBubble: Boolean
    get() = this == InlineTokenContext.UserBubbleInline || this == InlineTokenContext.UserBubbleStandalone
