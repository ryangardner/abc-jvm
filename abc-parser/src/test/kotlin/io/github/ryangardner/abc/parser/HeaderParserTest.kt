package io.github.ryangardner.abc.parser

import io.github.ryangardner.abc.core.model.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class HeaderParserTest {

    @Test
    fun `test standard headers`() {
        val input = """
            X: 1
            T: Test Tune
            M: 4/4
            L: 1/8
            Q: 1/4=120
            K: C
        """.trimIndent()

        val lexer = AbcLexer(input)
        val parser = HeaderParser(lexer)
        val result = parser.parse()

        assertNotNull(result)
        val header = result!!.header

        assertEquals(1, header.reference)
        assertEquals(listOf("Test Tune"), header.title)
        assertEquals(TimeSignature(4, 4), header.meter)
        assertEquals(NoteDuration(1, 8), header.length)
        assertEquals(Tempo(120, NoteDuration(1, 4)), header.tempo)
        assertEquals(KeySignature("C", "Major"), header.key)
        assertEquals(emptyMap<String, String>(), header.unknownHeaders)
    }

    @Test
    fun `test multiple titles`() {
        val input = """
            X: 2
            T: Title 1
            T: Title 2
            K: G
        """.trimIndent()

        val lexer = AbcLexer(input)
        val parser = HeaderParser(lexer)
        val result = parser.parse()

        assertNotNull(result)
        val header = result!!.header

        assertEquals(listOf("Title 1", "Title 2"), header.title)
        assertEquals(KeySignature("G", "Major"), header.key)
    }

    @Test
    fun `test unknown headers`() {
        val input = """
            X: 3
            C: Composer Name
            Z: Transcriber
            K: Dm
        """.trimIndent()

        val lexer = AbcLexer(input)
        val parser = HeaderParser(lexer)
        val result = parser.parse()

        assertNotNull(result)
        val header = result!!.header

        assertEquals("Composer Name", header.unknownHeaders["C"])
        assertEquals("Transcriber", header.unknownHeaders["Z"])
        assertEquals(KeySignature("D", "minor"), header.key) // Assuming simple parsing logic in HeaderParser
        // But HeaderParser uses split by space. "Dm" -> "Dm" "Major" if split fails?
        // Let's check parseKey implementation.
        // val parts = text.split("\\s+".toRegex())
        // val tonic = parts[0]
        // val mode = parts[1] else "Major"
        // If text is "Dm", split is ["Dm"]. Tonic="Dm", Mode="Major".
        // My test expectation "D", "minor" assumes smarter parsing.
        // I should probably fix parseKey or adjust test expectation.
        // For now, I'll adjust expectation to what implemented logic does: Tonic="Dm", Mode="Major"
        // Wait, "Dm" usually means D minor.
        // But for Task 2.1 I just need basic support. I'll update parseKey later or now?
        // Let's update test to expect "Dm", "Major" for now, as I haven't implemented complex key parsing.
    }

    @Test
    fun `test key parsing simple`() {
        // Just verify what currently implemented
        val input = "X:1\nK:Dm"
        val lexer = AbcLexer(input)
        val parser = HeaderParser(lexer)
        val result = parser.parse()

        assertEquals("D", result!!.header.key.tonic)
        assertEquals("minor", result.header.key.mode)
    }

    @Test
    fun `test visual transpose directive`() {
        val input = """
            X: 4
            %%visualTranspose 2
            K: C
        """.trimIndent()

        val lexer = AbcLexer(input)
        val parser = HeaderParser(lexer)
        val result = parser.parse()

        assertNotNull(result)
        assertEquals(2, result!!.metadata.visualTranspose)
    }

    @Test
    fun `test missing mandatory header`() {
        // Missing K
        val input = """
            X: 5
            T: No Key
        """.trimIndent()

        val lexer = AbcLexer(input)
        val parser = HeaderParser(lexer)
        val result = parser.parse()

        assertNull(result)
    }
}
