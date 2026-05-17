package com.aiapuri.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [TitleGenerator].
 */
class TitleGeneratorTest {

    @Test
    fun `generateTitle returns short message unchanged`() {
        val title = TitleGenerator.generateTitle("Hello, how are you?")
        // Trailing punctuation is stripped
        assertEquals("Hello, how are you", title)
    }

    @Test
    fun `generateTitle trims whitespace`() {
        val title = TitleGenerator.generateTitle("  Trim this  ")
        assertEquals("Trim this", title)
    }

    @Test
    fun `generateTitle uses first line for multi-line messages`() {
        val title = TitleGenerator.generateTitle("First line title\nSecond line body\nThird line more")
        assertEquals("First line title", title)
    }

    @Test
    fun `generateTitle truncates long messages with ellipsis`() {
        val longText = "A".repeat(100)
        val title = TitleGenerator.generateTitle(longText)
        assertEquals(TitleGenerator.MAX_TITLE_LENGTH + 1, title.length) // MAX + ellipsis char
        assertTrue(title.endsWith("…"))
    }

    @Test
    fun `generateTitle truncates at word boundary when possible`() {
        val text = "This is a test message that is way too long for a title and should be truncated"
        val title = TitleGenerator.generateTitle(text)
        // When truncating at a word boundary, the result may be shorter than MAX + 1
        // because we cut at the last space before the limit.
        assertTrue(title.length <= TitleGenerator.MAX_TITLE_LENGTH + 1)
        assertTrue(title.endsWith("…"))
        // Should not end with a trailing space before ellipsis
        assertFalse(title.dropLast(1).endsWith(" "))
    }

    @Test
    fun `generateTitle returns default for empty input`() {
        assertEquals(TitleGenerator.DEFAULT_TITLE, TitleGenerator.generateTitle(""))
        assertEquals(TitleGenerator.DEFAULT_TITLE, TitleGenerator.generateTitle("   "))
        assertEquals(TitleGenerator.DEFAULT_TITLE, TitleGenerator.generateTitle("\n\n"))
    }

    @Test
    fun `generateTitle strips trailing punctuation`() {
        assertEquals("How to fix this", TitleGenerator.generateTitle("How to fix this."))
        assertEquals("What is the answer", TitleGenerator.generateTitle("What is the answer?"))
        assertEquals("Amazing result", TitleGenerator.generateTitle("Amazing result!"))
        assertEquals("Check this out", TitleGenerator.generateTitle("Check this out:"))
        assertEquals("Note on topic", TitleGenerator.generateTitle("Note on topic;"))
    }

    @Test
    fun `generateTitle preserves internal punctuation`() {
        val title = TitleGenerator.generateTitle("What's the difference between foo, bar, and baz?")
        assertEquals("What's the difference between foo, bar, and baz", title)
    }

    @Test
    fun `generateTitle handles single word`() {
        assertEquals("Hello", TitleGenerator.generateTitle("Hello"))
    }

    @Test
    fun `generateTitle handles exactly max length`() {
        val exact = "A".repeat(TitleGenerator.MAX_TITLE_LENGTH)
        val title = TitleGenerator.generateTitle(exact)
        assertEquals(TitleGenerator.MAX_TITLE_LENGTH, title.length)
        assertFalse(title.endsWith("…"))
    }

    @Test
    fun `isPlaceholderTitle returns true for default title`() {
        assertTrue(TitleGenerator.isPlaceholderTitle("New Chat"))
    }

    @Test
    fun `isPlaceholderTitle returns false for custom title`() {
        assertFalse(TitleGenerator.isPlaceholderTitle("My conversation"))
        assertFalse(TitleGenerator.isPlaceholderTitle("new chat"))
        assertFalse(TitleGenerator.isPlaceholderTitle("NEW CHAT"))
    }
}
