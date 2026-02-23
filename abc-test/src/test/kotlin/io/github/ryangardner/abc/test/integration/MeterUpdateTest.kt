package io.github.ryangardner.abc.test.integration

import io.github.ryangardner.abc.core.model.*
import io.github.ryangardner.abc.parser.AbcParser
import io.github.ryangardner.abc.theory.MeasureQuantizer
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class MeterUpdateTest {

    @Test
    fun `test quantize handles meter changes in body via InlineField`() {
        val input = """
            X:1
            K:C
            M:4/4
            L:1/4
            C D E F | [M:2/4] G A | B C |
        """.trimIndent()

        val tune = AbcParser().parse(input)
        val measures = MeasureQuantizer.quantize(tune)

        assertEquals(3, measures.size)
        assertEquals(TimeSignature(4, 4), measures[0].timeSignature)
        assertEquals(TimeSignature(2, 4), measures[1].timeSignature)
        assertEquals(TimeSignature(2, 4), measures[2].timeSignature)
    }

    @Test
    fun `test quantize handles meter changes in body via BodyHeader`() {
        val input = """
            X:1
            K:C
            M:4/4
            L:1/4
            C D E F
            M:2/4
            G A | B C |
        """.trimIndent()

        val tune = AbcParser().parse(input)
        val measures = MeasureQuantizer.quantize(tune)

        // Measure 1: C D E F
        // Measure 2: G A |
        // Measure 3: B C |
        assertEquals(3, measures.size)
        assertEquals(TimeSignature(4, 4), measures[0].timeSignature)
        assertEquals(TimeSignature(2, 4), measures[1].timeSignature)
        assertEquals(TimeSignature(2, 4), measures[2].timeSignature)
    }
}
