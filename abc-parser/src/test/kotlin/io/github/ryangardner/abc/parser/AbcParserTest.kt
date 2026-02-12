package io.github.ryangardner.abc.parser

import io.github.ryangardner.abc.core.model.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

public class AbcParserTest {

    @Test
    public fun `test full tune parsing`(): Unit {
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
        val noteElements = elements.filterIsInstance<NoteElement>()
        val barLines = elements.filterIsInstance<BarLineElement>()

        assertEquals(8, noteElements.size)
        assertEquals(2, barLines.size)

        assertEquals(NoteStep.C, noteElements[0].pitch.step)
        assertEquals(NoteStep.G, noteElements[4].pitch.step)
        
        assertEquals(BarLineType.SINGLE, barLines[0].type)
        assertEquals(BarLineType.SINGLE, barLines[1].type)
    }
}
