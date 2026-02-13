package io.github.ryangardner.abc.test.integration

import io.github.ryangardner.abc.core.model.*
import io.github.ryangardner.abc.parser.AbcParser
import io.github.ryangardner.abc.theory.MeasureQuantizer
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class MeasureQuantizerTest {

    @Test
    fun `test quantize groups notes into measures correctly`() {
        val input = """
            X:1
            K:C
            L:1/4
            C D | E F | G4 |
        """.trimIndent()

        val tune = AbcParser().parse(input)
        println("DEBUG: Tune elements: ${tune.body.elements.size}")
        tune.body.elements.forEachIndexed { i, e ->
            println("DEBUG: [$i] ${e.javaClass.simpleName} duration=${e.duration.toDouble()} ($e)")
        }
        val measures = MeasureQuantizer.quantize(tune)
        println("DEBUG: Measures: ${measures.size}")

        assertEquals(3, measures.size)
        
        // Measure 1: C D
        assertEquals(1, measures[0].index)
        assertEquals(2, measures[0].elements.filterIsInstance<NoteElement>().size)
        assertEquals(0.5, measures[0].duration.toDouble())

        // Measure 2: E F
        assertEquals(2, measures[1].index)
        assertEquals(2, measures[1].elements.filterIsInstance<NoteElement>().size)

        // Measure 3: G4
        assertEquals(3, measures[2].index)
        assertEquals(1, measures[2].elements.filterIsInstance<NoteElement>().size)
        assertEquals(1.0, measures[2].duration.toDouble())
    }

    @Test
    fun `test quantize handles bar lines as boundaries`() {
        val input = "X:1\nK:C\nL:1/4\nC D | E F |"
        val tune = AbcParser().parse(input)
        val measures = MeasureQuantizer.quantize(tune)

        assertEquals(2, measures.size)
    }
}
