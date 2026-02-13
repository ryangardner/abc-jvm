package io.github.ryangardner.abc.theory.dsl

import io.github.ryangardner.abc.core.model.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class AbcDSLTest {

    @Test
    fun `test abcTune DSL produces correct structure`() {
        val tune = abcTune {
            header {
                reference = 42
                title = "DSL Test Tune"
                key = "Dm"
                meter = "3/4"
                length = "1/4"
            }
            body {
                note("D", NoteDuration(1, 1))
                bar()
                chord("F") {
                    note("F")
                    note("A")
                    note("C")
                }
            }
        }

        // Verify Header
        assertEquals(42, tune.header.reference)
        assertEquals("DSL Test Tune", tune.header.title[0])
        assertEquals(NoteStep.D, tune.header.key.root.step)
        assertEquals(KeyMode.AEOLIAN, tune.header.key.mode)
        assertEquals(3, tune.header.meter.numerator)
        assertEquals(4, tune.header.meter.denominator)
        assertEquals(1, tune.header.length.numerator)
        assertEquals(4, tune.header.length.denominator)

        // Verify Body
        val elements = tune.body.elements
        assertEquals(3, elements.size)
        
        val note = elements[0] as NoteElement
        assertEquals(NoteStep.D, note.pitch.step)

        val bar = elements[1] as BarLineElement
        assertEquals(BarLineType.SINGLE, bar.type)

        val chord = elements[2] as ChordElement
        assertEquals("F", chord.annotation)
        assertEquals(3, chord.notes.size)
    }
}
