package io.github.ryangardner.abc.parser

import io.github.ryangardner.abc.antlr.ABCLexer
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.Token
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource

class AbcLexerTest {

    private fun lex(input: String): List<String> {
        val charStream = CharStreams.fromString(input)
        val lexer = ABCLexer(charStream)

        val tokens = mutableListOf<String>()
        var token = lexer.nextToken()
        while (token.type != Token.EOF) {
            val vocabulary = lexer.vocabulary
            val symbolicName = vocabulary.getSymbolicName(token.type)
            tokens.add(symbolicName ?: "UNKNOWN_TOKEN_${token.type}")
            token = lexer.nextToken()
        }
        return tokens
    }

    private fun lexMusic(input: String): List<String> {
        // Prepend header to switch to MUSIC_MODE
        val fullInput = "X:1\nK:C\n$input"
        val allTokens = lex(fullInput)
        // Skip header tokens: X_REF_START, FIELD_CONTENT, NEWLINE, KEY_FIELD, FIELD_CONTENT, NEWLINE
        // Total 6 tokens.
        return allTokens.drop(6)
    }

    @Test
    fun `test simple header tokenization`() {
        val input = "X:1\nT:Test Tune\nK:C\n"
        val tokens = lex(input)

        val expected = listOf(
            "X_REF_START", "FIELD_CONTENT", "NEWLINE",
            "FIELD_ID", "FIELD_CONTENT", "NEWLINE",
            "KEY_FIELD", "FIELD_CONTENT", "NEWLINE"
        )

        assertEquals(expected, tokens)
    }

    @Test
    fun `test header with comments`() {
        val input = """
            X:1
            % This is a comment
            T:Tune Title
            M:4/4
            K:D
        """.trimIndent() + "\n"

        val tokens = lex(input)

        val expected = listOf(
            "X_REF_START", "FIELD_CONTENT", "NEWLINE",
            "NEWLINE", // Newline after comment
            "FIELD_ID", "FIELD_CONTENT", "NEWLINE",
            "FIELD_ID", "FIELD_CONTENT", "NEWLINE",
            "KEY_FIELD", "FIELD_CONTENT", "NEWLINE"
        )

        assertEquals(expected, tokens)
    }

    @Test
    fun `test header stylesheet directives`() {
        val input = """
            X:1
            %%landscape
            T:Tune
            K:C
        """.trimIndent() + "\n"

        val tokens = lex(input)

        val expected = listOf(
            "X_REF_START", "FIELD_CONTENT", "NEWLINE",
            "STYLESHEET",
            "FIELD_ID", "FIELD_CONTENT", "NEWLINE",
            "KEY_FIELD", "FIELD_CONTENT", "NEWLINE"
        )

        assertEquals(expected, tokens)
    }

    @ParameterizedTest
    @ValueSource(strings = [
        "M:4/4",
        "L:1/8",
        "C:Composer Name",
        "R:Reel",
        "Z:Transcriber"
    ])
    fun `test various header fields`(fieldLine: String) {
        val input = "X:1\n$fieldLine\nK:C\n"
        val tokens = lex(input)
        val middleTokens = tokens.subList(3, 6)
        assertEquals(listOf("FIELD_ID", "FIELD_CONTENT", "NEWLINE"), middleTokens)
    }

    @Test
    fun `test key field transitions to music mode`() {
        val input = "X:1\nK:C\nCDEFG"
        val tokens = lex(input)

        val expected = listOf(
            "X_REF_START", "FIELD_CONTENT", "NEWLINE",
            "KEY_FIELD", "FIELD_CONTENT", "NEWLINE",
            "NOTE_PITCH", "NOTE_PITCH", "NOTE_PITCH", "NOTE_PITCH", "NOTE_PITCH"
        )
        assertEquals(expected, tokens)
    }

    @Test
    fun `test field continuation`() {
        val inputWithNewline = "X:1\nT:Long Title \\\n Continued\nK:C\n"
        val tokensWithNewline = lex(inputWithNewline)

         assertEquals(listOf(
            "X_REF_START", "FIELD_CONTENT", "NEWLINE",
            "FIELD_ID", "FIELD_CONTENT", "FIELD_CONTENT", "NEWLINE",
            "KEY_FIELD", "FIELD_CONTENT", "NEWLINE"
        ), tokensWithNewline)
    }

    // --- MUSIC MODE TESTS ---

    @Test
    fun `test basic notes and octaves`() {
        val input = "C c C, c' C,, c''"
        val tokens = lexMusic(input)
        // Space is WS_MUSIC -> SPACE
        val expected = listOf(
            "NOTE_PITCH", "SPACE",
            "NOTE_PITCH", "SPACE",
            "NOTE_PITCH", "OCTAVE_DOWN", "SPACE",
            "NOTE_PITCH", "OCTAVE_UP", "SPACE",
            "NOTE_PITCH", "OCTAVE_DOWN", "OCTAVE_DOWN", "SPACE",
            "NOTE_PITCH", "OCTAVE_UP", "OCTAVE_UP"
        )
        assertEquals(expected, tokens)
    }

    @Test
    fun `test accidentals`() {
        val input = "^C _D =E ^^F __G ^/A _/B"
        val tokens = lexMusic(input)

        val expected = listOf(
            "ACC_SHARP", "NOTE_PITCH", "SPACE",
            "ACC_FLAT", "NOTE_PITCH", "SPACE",
            "ACC_NATURAL", "NOTE_PITCH", "SPACE",
            "ACC_SHARP_DBL", "NOTE_PITCH", "SPACE",
            "ACC_FLAT_DBL", "NOTE_PITCH", "SPACE",
            "ACC_SHARP_HALF", "NOTE_PITCH", "SPACE",
            "ACC_FLAT_HALF", "NOTE_PITCH"
        )
        assertEquals(expected, tokens)
    }

    @Test
    fun `test rests`() {
        val input = "z z4 x X"
        val tokens = lexMusic(input)

        val expected = listOf(
            "REST", "SPACE",
            "REST", "DIGIT", "SPACE", // z4
            "REST", "SPACE",
            "REST"
        )
        assertEquals(expected, tokens)
    }

    @ParameterizedTest
    @CsvSource(
        "'|', BAR_SINGLE",
        "'||', BAR_THIN_DOUBLE",
        "'|]', BAR_THIN_THICK",
        "'[|', BAR_THICK_THIN",
        "'|:', BAR_REP_START",
        "':|', BAR_REP_END",
        "'::', BAR_REP_DBL"
    )
    fun `test bar lines`(barText: String, tokenName: String) {
        val tokens = lexMusic(barText)
        assertEquals(listOf(tokenName), tokens)
    }

    @Test
    fun `test tuplets`() {
        val input = "(3ABC (3:2:4DEF"
        val tokens = lexMusic(input)

        val expected = listOf(
            "TUPLET_START", "NOTE_PITCH", "NOTE_PITCH", "NOTE_PITCH", "SPACE",
            "TUPLET_START", "NOTE_PITCH", "NOTE_PITCH", "NOTE_PITCH"
        )
        assertEquals(expected, tokens)
    }

    @Test
    fun `test broken rhythm`() {
        val input = "A>B A<B A>>B A<<B"
        val tokens = lexMusic(input)

        val expected = listOf(
            "NOTE_PITCH", "BROKEN_RHYTHM_RIGHT", "NOTE_PITCH", "SPACE",
            "NOTE_PITCH", "BROKEN_RHYTHM_LEFT", "NOTE_PITCH", "SPACE",
            "NOTE_PITCH", "BROKEN_RHYTHM_RIGHT", "NOTE_PITCH", "SPACE",
            "NOTE_PITCH", "BROKEN_RHYTHM_LEFT", "NOTE_PITCH"
        )
        assertEquals(expected, tokens)
    }

    // --- TRANSITION TESTS ---

    @Test
    fun `test lyrics mode`() {
        val tokensInBody = lexMusic("w: This is a lyric line\n")
        val expected = listOf(
            "FIELD_ID", "FIELD_CONTENT", "NEWLINE"
        )
        assertEquals(expected, tokensInBody)
    }

    @Test
    fun `test text block mode`() {
        val input = """
            X:1
            %%begintext
            This is free text.
            It can contain anything.
            %%endtext
            K:C
        """.trimIndent() + "\n"

        val tokens = lex(input)

        val expected = listOf(
            "X_REF_START", "FIELD_CONTENT", "NEWLINE",
            "HEADER_TEXT_BLOCK_START",
            "TEXT_BLOCK_CONTENT",
            "TEXT_BLOCK_END",
            "NEWLINE",
            "KEY_FIELD", "FIELD_CONTENT", "NEWLINE"
        )

        assertEquals(expected, tokens)
    }

    @Test
    fun `test symbol line mode`() {
        val tokensWithNewline = lexMusic("s: !f! \"Am\" * |\n")

        val expected = listOf(
            "MUSIC_SYMBOL_LINE",
            "SYMBOL_DECO",
            "SYMBOL_CHORD",
            "SYMBOL_SKIP",
            "SYMBOL_BAR",
            "NEWLINE"
        )

        assertEquals(expected, tokensWithNewline)
    }

    @Test
    fun `test inline fields`() {
        val input = "[M:6/8] CDEF"
        val tokens = lexMusic(input)

        val expected = listOf(
            "INLINE_FIELD_START",
            "INLINE_FIELD_CONTENT",
            "INLINE_FIELD_END",
            "SPACE",
            "NOTE_PITCH", "NOTE_PITCH", "NOTE_PITCH", "NOTE_PITCH"
        )

        assertEquals(expected, tokens)
    }

    // --- COMPLEX TOKEN TESTS ---

    @Test
    fun `test chords`() {
        val input = "\"Am7\" [CEG]"
        val tokens = lexMusic(input)

        // "Am7" -> CHORD_START, CHORD_CONTENT, CHORD_END
        // [CEG] -> BRACKET_START, NOTE_PITCH, NOTE_PITCH, NOTE_PITCH, BRACKET_END

        val expected = listOf(
            "CHORD_START", "CHORD_CONTENT", "CHORD_END",
            "SPACE",
            "BRACKET_START", "NOTE_PITCH", "NOTE_PITCH", "NOTE_PITCH", "BRACKET_END"
        )

        assertEquals(expected, tokens)
    }

    @Test
    fun `test decorations`() {
        val input = "!trill! +crescendo+ ~ . u v"
        val tokens = lexMusic(input)

        val expected = listOf(
            "DECORATION_START", "BANG_DECO_CONTENT", "DECORATION_END", "SPACE",
            "PLUS_DECORATION", "PLUS_DECO_CONTENT", "PLUS_DECORATION_END", "SPACE",
            "ROLL", "SPACE",
            "STACCATO", "SPACE",
            "UPBOW", "SPACE",
            "DOWNBOW"
        )

        assertEquals(expected, tokens)
    }

    @Test
    fun `test user defined symbols`() {
        val input = "H I J K L M N O P Q R S T U V W"
        val tokens = lexMusic(input)

        // All these letters are valid user defined symbols
        // Except K, L, M, Q might be fields if at start of line?
        // But here they are in middle of line (after H and spaces).

        // H..W are USER_DEF_SYMBOL : [H-Wh-w] ;
        // Wait, NOTE_PITCH : [A-Ga-g] ;
        // So H-W are definitely user defined.

        // K is [H-W] range? Yes. H I J K L M N O P Q R S T U V W.

        val expected = (1..16).flatMap { listOf("USER_DEF_SYMBOL", "SPACE") }.dropLast(1)

        val actual = tokens

        assertEquals(expected.size, actual.size)
        assertEquals("USER_DEF_SYMBOL", actual[0])
    }
}
