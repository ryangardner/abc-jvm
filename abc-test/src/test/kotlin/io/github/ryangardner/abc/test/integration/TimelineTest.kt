package io.github.ryangardner.abc.test.integration

import io.github.ryangardner.abc.core.model.*
import io.github.ryangardner.abc.parser.AbcParser
import io.github.ryangardner.abc.theory.PitchInterpreter
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class TimelineTest {

    @Test
    fun `test toTimeline creates events at correct positions`() {
        val input = """
            X:1
            K:C
            L:1/4
            C D | E2 |
        """.trimIndent()

        val tune = AbcParser().parse(input)
        val timeline = PitchInterpreter.toTimeline(tune)

        assertEquals(1.0, timeline.totalBeats())
        
        val events = timeline.events
        assertEquals(3, events.size)
        
        // C at beat 0
        assertEquals(0.0, events[0].beat)
        assertEquals(NoteStep.C, events[0].note.pitches[0].step)

        // D at beat 0.25
        assertEquals(0.25, events[1].beat)
        assertEquals(NoteStep.D, events[1].note.pitches[0].step)

        // E at beat 0.5
        assertEquals(0.5, events[2].beat)
        assertEquals(NoteStep.E, events[2].note.pitches[0].step)
    }

    @Test
    fun `test getChordsAt identifies chords and annotated notes`() {
        val input = """
            X:1
            K:C
            L:1/4
            "Am"C [CEG] |
        """.trimIndent()

        val tune = AbcParser().parse(input)
        val timeline = PitchInterpreter.toTimeline(tune)

        // Beat 0 has "Am"C which is a chordal event due to annotation
        val chordsAt0 = timeline.getChordsAt(0.0)
        assertEquals(1, chordsAt0.size)
        assertEquals("Am", chordsAt0[0].note.annotation)

        // Beat 0.25 has [CEG] which is a chordal event due to multiple pitches
        val chordsAt1 = timeline.getChordsAt(0.25)
        assertEquals(1, chordsAt1.size)
        assertEquals(3, chordsAt1[0].note.pitches.size)
    }
}
