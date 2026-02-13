package io.github.ryangardner.abc.test.integration

import io.github.ryangardner.abc.core.model.*
import io.github.ryangardner.abc.parser.AbcParser
import io.github.ryangardner.abc.theory.MeasureQuantizer
import io.github.ryangardner.abc.theory.RepairEngine
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class RepairEngineTest {

    @Test
    fun `test repairRhythm adds rests to short measures`() {
        val input = """
            X:1
            K:C
            L:1/4
            M:4/4
            C D | E F G |
        """.trimIndent()

        val tune = AbcParser().parse(input)
        
        // Measure 1 is 2 beats (short)
        // Measure 2 is 3 beats (short)
        
        val repaired = RepairEngine.repairRhythm(tune)
        val measures = MeasureQuantizer.quantize(repaired)

        assertEquals(2, measures.size)
        
        // Measure 1 should now have a rest appended to make it 4 beats
        // (Our current simple repair logic might just append one rest, 
        // but let's check it's improved)
        assertTrue(measures[0].elements.any { element -> element is RestElement })
        
        // Measure 2 should also have a rest
        assertTrue(measures[1].elements.any { element -> element is RestElement })
    }
}
