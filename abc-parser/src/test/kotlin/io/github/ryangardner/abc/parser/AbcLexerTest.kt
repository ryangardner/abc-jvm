package io.github.ryangardner.abc.parser

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class AbcLexerTest {
    @Test
    fun `test header parsing`() {
        val input = """
            X: 1
            T: Test Tune
            K: C
        """.trimIndent()

        val lexer = AbcLexer(input)
        val tokens = lexer.asSequence().toList()

        assertEquals(TokenType.HEADER_KEY, tokens[0].type)
        assertEquals("X", tokens[0].text)
        assertEquals(TokenType.HEADER_VALUE, tokens[1].type)
        assertEquals("1", tokens[1].text)
        assertEquals(TokenType.NEWLINE, tokens[2].type)

        assertEquals(TokenType.HEADER_KEY, tokens[3].type)
        assertEquals("T", tokens[3].text)
        assertEquals(TokenType.HEADER_VALUE, tokens[4].type)
        assertEquals("Test Tune", tokens[4].text)
        assertEquals(TokenType.NEWLINE, tokens[5].type)

        assertEquals(TokenType.HEADER_KEY, tokens[6].type)
        assertEquals("K", tokens[6].text)
        assertEquals(TokenType.HEADER_VALUE, tokens[7].type)
        assertEquals("C", tokens[7].text)
        assertEquals(TokenType.EOF, tokens[8].type)
    }

    @Test
    fun `test body transition and parsing`() {
        val input = "K:C\nC D E |"
        val lexer = AbcLexer(input)
        val tokens = lexer.asSequence().toList()

        // Header K:C
        assertEquals(TokenType.HEADER_KEY, tokens[0].type)
        assertEquals("K", tokens[0].text)
        assertEquals(TokenType.HEADER_VALUE, tokens[1].type)
        assertEquals("C", tokens[1].text)
        assertEquals(TokenType.NEWLINE, tokens[2].type) // Transitions to BODY here

        // Body C D E |
        assertEquals(TokenType.NOTE, tokens[3].type)
        assertEquals("C", tokens[3].text)
        assertEquals(TokenType.NOTE, tokens[4].type) // Space skipped
        assertEquals("D", tokens[4].text)
        assertEquals(TokenType.NOTE, tokens[5].type)
        assertEquals("E", tokens[5].text)
        assertEquals(TokenType.BAR_LINE, tokens[6].type)
        assertEquals("|", tokens[6].text)
        assertEquals(TokenType.EOF, tokens[7].type)
    }

    @Test
    fun `test inline fields`() {
        val input = "K:C\n[K:Dm] A B"
        val lexer = AbcLexer(input)
        val tokens = lexer.asSequence().toList()

        // Skip header check, focus on body
        val bodyTokens = tokens.drop(3) // Key, Value, Newline

        assertEquals(TokenType.INLINE_FIELD_START, bodyTokens[0].type)
        assertEquals("[", bodyTokens[0].text)
        assertEquals(TokenType.INLINE_FIELD_KEY, bodyTokens[1].type)
        assertEquals("K", bodyTokens[1].text)
        assertEquals(TokenType.INLINE_FIELD_VALUE, bodyTokens[2].type)
        assertEquals("Dm", bodyTokens[2].text) // Or "Dm" depending on logic
        assertEquals(TokenType.INLINE_FIELD_END, bodyTokens[3].type)
        assertEquals("]", bodyTokens[3].text)

        assertEquals(TokenType.NOTE, bodyTokens[4].type) // A
        assertEquals("A", bodyTokens[4].text)
    }
}
