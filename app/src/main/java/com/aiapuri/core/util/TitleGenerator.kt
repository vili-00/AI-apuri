package com.aiapuri.core.util

/**
 * Utility for generating conversation titles from user message text.
 *
 * Uses a simple local truncation rule — no model calls are made.
 * The generated title is taken from the first line of the message,
 * trimmed and truncated to a maximum length with clean ellipsis.
 */
object TitleGenerator {

    /** Maximum length for an auto-generated title. */
    const val MAX_TITLE_LENGTH = 50

    /** Default placeholder title used when a conversation is created without a message. */
    const val DEFAULT_TITLE = "New Chat"

    /**
     * Generate a conversation title from a user message.
     *
     * Rules:
     * - Use the first line of the message (multi-line messages are common).
     * - Trim leading/trailing whitespace.
     * - Strip trailing punctuation that makes titles look messy (period, comma, etc.).
     * - Truncate to [MAX_TITLE_LENGTH] with an ellipsis if needed.
     * - Return [DEFAULT_TITLE] if the result is empty after processing.
     */
    fun generateTitle(messageText: String): String {
        if (messageText.isBlank()) {
            return DEFAULT_TITLE
        }

        // Take the first line only
        val firstLine = messageText.split('\n').firstOrNull()?.trim() ?: messageText.trim()

        if (firstLine.isEmpty()) {
            return DEFAULT_TITLE
        }

        // Strip trailing punctuation for cleaner titles
        val stripped = firstLine.trimEnd('.', ',', '!', '?', ':', ';')

        if (stripped.length <= MAX_TITLE_LENGTH) {
            return stripped
        }

        // Truncate cleanly: cut at the last space before the limit to avoid
        // splitting words mid-way, then append ellipsis.
        val truncated = stripped.take(MAX_TITLE_LENGTH)
        val lastSpace = truncated.lastIndexOf(' ')

        return if (lastSpace > MAX_TITLE_LENGTH / 2) {
            // There's a good break point — use it
            "${truncated.take(lastSpace)}…"
        } else {
            // No good break point found — hard truncate
            "$truncated…"
        }
    }

    /**
     * Check whether a title looks like an auto-generated placeholder that should
     * be replaced by a real title on first message.
     */
    fun isPlaceholderTitle(title: String): Boolean {
        return title == DEFAULT_TITLE
    }
}
