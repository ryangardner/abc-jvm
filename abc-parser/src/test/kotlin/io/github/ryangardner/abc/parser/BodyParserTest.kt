package io.github.ryangardner.abc.parser

import io.github.ryangardner.abc.core.model.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

public class BodyParserTest {

    @Test
    public fun `test simple notes`(): Unit {
        val input = "C D E"
        // L defaults to 1/8 from header
        val header = TuneHeader(1, emptyList(), KeySignature(KeyRoot(NoteStep.C), KeyMode.IONIAN), TimeSignature(4, 4), NoteDuration(1, 8))
        val lexer = AbcLexer(input)
        val parser = BodyParser(lexer, header)

        val body = parser.parse()
        assertEquals(3, body.elements.size)

        val n1 = body.elements[0] as NoteElement
        assertEquals(NoteStep.C, n1.pitch.step)
        assertEquals(NoteDuration(1, 8), n1.length) // Default

        val n2 = body.elements[1] as NoteElement
        assertEquals(NoteStep.D, n2.pitch.step)
    }

    @Test
    public fun `test note duration`(): Unit {
        val input = "C2 D/2"
        val header = TuneHeader(1, emptyList(), KeySignature(KeyRoot(NoteStep.C), KeyMode.IONIAN), TimeSignature(4, 4), NoteDuration(1, 8))
        val lexer = AbcLexer(input)
        val parser = BodyParser(lexer, header)

        val body = parser.parse()

        val n1 = body.elements[0] as NoteElement
        // C2 -> 2 * 1/8 = 2/8 = 1/4. Impl simplifies it.
        assertEquals(NoteDuration(1, 4), n1.length)

        val n2 = body.elements[1] as NoteElement
        // D/2 -> (1/2) * (1/8) = 1/16
        assertEquals(NoteDuration(1, 16), n2.length)
    }

    @Test
    public fun `test accidentals and octaves`(): Unit {
        val input = "^C, _D'"
        val header = TuneHeader(1, emptyList(), KeySignature(KeyRoot(NoteStep.C), KeyMode.IONIAN), TimeSignature(4, 4), NoteDuration(1, 8))
        val lexer = AbcLexer(input)
        val parser = BodyParser(lexer, header)

        val body = parser.parse()

        val n1 = body.elements[0] as NoteElement
        assertEquals(Accidental.SHARP, n1.accidental)
        assertEquals(NoteStep.C, n1.pitch.step)
        assertEquals(3, n1.pitch.octave) // C (4) - 1 (comma) = 3

        val n2 = body.elements[1] as NoteElement
        assertEquals(Accidental.FLAT, n2.accidental)
        assertEquals(NoteStep.D, n2.pitch.step)
        assertEquals(5, n2.pitch.octave) // D (4) + 1 (apostrophe) = 5
    }

    @Test
    public fun `test chord`(): Unit {
        val input = "[CEG]2"
        val header = TuneHeader(1, emptyList(), KeySignature(KeyRoot(NoteStep.C), KeyMode.IONIAN), TimeSignature(4, 4), NoteDuration(1, 8))
        val lexer = AbcLexer(input)
        val parser = BodyParser(lexer, header)

        val body = parser.parse()
        val chord = body.elements[0] as ChordElement

        assertEquals(3, chord.notes.size)
        // Chord duration 2 * 1/8 = 2/8 = 1/4
        assertEquals(NoteDuration(1, 4), chord.duration)

        assertEquals(NoteStep.C, chord.notes[0].pitch.step)
        assertEquals(NoteDuration(1, 8), chord.notes[0].length) // Inner note uses default L
    }

    @Test
    public fun `test inline field L`(): Unit {
        val input = "C [L:1/4] C"
        val header = TuneHeader(1, emptyList(), KeySignature(KeyRoot(NoteStep.C), KeyMode.IONIAN), TimeSignature(4, 4), NoteDuration(1, 8))
        val lexer = AbcLexer(input)
        val parser = BodyParser(lexer, header)

        val body = parser.parse()

        val n1 = body.elements[0] as NoteElement
        assertEquals(NoteDuration(1, 8), n1.length) // Initial L=1/8

        // Element 1 is InlineField
        assertTrue(body.elements[1] is InlineFieldElement)

        val n2 = body.elements[2] as NoteElement
        assertEquals(NoteDuration(1, 4), n2.length) // New L=1/4
    }
}
