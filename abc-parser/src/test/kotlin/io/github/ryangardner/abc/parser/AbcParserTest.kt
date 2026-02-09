package io.github.ryangardner.abc.parser

import io.github.ryangardner.abc.core.model.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class AbcParserTest {

    @Test
    fun `test full tune parsing`() {
        val input = """
            X: 1
            T: Full Tune
            M: 4/4
            K: C
            C D E F | G A B c |
        """.trimIndent()

        val parser = AbcParser()
        val tune = parser.parse(input)

        assertEquals(1, tune.header.reference)
        assertEquals("Full Tune", tune.header.title.first())
        assertEquals(TimeSignature(4, 4), tune.header.meter)
        assertEquals(KeySignature(KeyRoot(NoteStep.C), KeyMode.IONIAN), tune.header.key)

        val elements = tune.body.elements
        // C D E F | G A B c |
        // 4 notes, 1 barline, 4 notes, 1 barline = 10 elements
        assertEquals(10, elements.size)

        val n1 = elements[0] as NoteElement
        assertEquals(NoteStep.C, n1.pitch.step)

        val bar1 = elements[4] as BarLineElement
        assertEquals(BarLineType.SINGLE, bar1.type)

        val n5 = elements[5] as NoteElement
        assertEquals(NoteStep.G, n5.pitch.step)

        val bar2 = elements[9] as BarLineElement
        assertEquals(BarLineType.SINGLE, bar2.type)
    }
}
