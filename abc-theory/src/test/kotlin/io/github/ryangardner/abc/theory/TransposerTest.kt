package io.github.ryangardner.abc.theory

import io.github.ryangardner.abc.core.model.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

public class TransposerTest {

    private fun createSimpleTune(tonic: NoteStep, accidental: Accidental, mode: KeyMode, notes: List<Pitch>): AbcTune {
        val key = KeySignature(KeyRoot(tonic, accidental), mode)
        val header = TuneHeader(
            reference = 1,
            title = listOf("Test Tune"),
            key = key,
            meter = TimeSignature(4, 4),
            length = NoteDuration(1, 8)
        )
        val body = TuneBody(
            elements = notes.map { NoteElement(it, NoteDuration(1, 8)) }
        )
        return AbcTune(header, body, TuneMetadata())
    }

    @Test
    public fun `test transpose C Major up 2 semitones to D Major`(): Unit {
        val tune = createSimpleTune(NoteStep.C, Accidental.NATURAL, KeyMode.IONIAN, listOf(Pitch(NoteStep.C, 4)))
        val transposed = Transposer.transpose(tune, 2)
        
        assertEquals(NoteStep.D, transposed.header.key.root.step)
        assertEquals(Accidental.NATURAL, transposed.header.key.root.accidental)
        assertEquals(KeyMode.IONIAN, transposed.header.key.mode)
        
        val note = transposed.body.elements[0] as NoteElement
        assertEquals(NoteStep.D, note.pitch.step)
        assertEquals(4, note.pitch.octave)
        assertNull(note.pitch.accidental)
    }

    @Test
    public fun `test transpose G Major up 1 semitone to Ab Major`(): Unit {
        val tune = createSimpleTune(NoteStep.G, Accidental.NATURAL, KeyMode.IONIAN, listOf(Pitch(NoteStep.G, 4)))
        val transposed = Transposer.transpose(tune, 1)
        
        assertEquals(NoteStep.A, transposed.header.key.root.step)
        assertEquals(Accidental.FLAT, transposed.header.key.root.accidental)
        
        val note = transposed.body.elements[0] as NoteElement
        assertEquals(NoteStep.A, note.pitch.step)
        assertNull(note.pitch.accidental) // Ab is in key sig
    }

    @Test
    public fun `test explicit natural in G Major`(): Unit {
        val tune = createSimpleTune(NoteStep.C, Accidental.NATURAL, KeyMode.IONIAN, listOf(Pitch(NoteStep.F, 4, Accidental.SHARP)))
        val transposed = Transposer.transpose(tune, 7)
        
        assertEquals(NoteStep.G, transposed.header.key.root.step)
        val note = transposed.body.elements[0] as NoteElement
        assertEquals(NoteStep.C, note.pitch.step)
        assertEquals(Accidental.SHARP, note.pitch.accidental)
    }

    @Test
    public fun `test natural accidental needed`(): Unit {
        val tune = createSimpleTune(NoteStep.G, Accidental.NATURAL, KeyMode.IONIAN, listOf(Pitch(NoteStep.F, 4, Accidental.NATURAL)))
        val transposed = Transposer.transpose(tune, 2)
        
        assertEquals(NoteStep.A, transposed.header.key.root.step)
        val note = transposed.body.elements[0] as NoteElement
        assertEquals(NoteStep.G, note.pitch.step)
        assertEquals(Accidental.NATURAL, note.pitch.accidental)
    }

    @Test
    public fun `test setVisualTranspose`(): Unit {
        val tune = createSimpleTune(NoteStep.C, Accidental.NATURAL, KeyMode.IONIAN, listOf(Pitch(NoteStep.C, 4)))
        val visual = tune.setVisualTranspose(2)
        
        assertEquals(2, visual.metadata.visualTranspose)
        // Ensure notes are NOT changed
        val note = visual.body.elements[0] as NoteElement
        assertEquals(NoteStep.C, note.pitch.step)
    }
}
