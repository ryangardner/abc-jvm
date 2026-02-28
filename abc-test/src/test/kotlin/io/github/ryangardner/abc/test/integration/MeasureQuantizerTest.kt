package io.github.ryangardner.abc.test.integration
import io.github.ryangardner.abc.core.model.NoteElement
import io.github.ryangardner.abc.parser.AbcParser
import io.github.ryangardner.abc.theory.MeasureQuantizer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MeasureQuantizerTest {
    @Test
    fun `test quantize groups notes into measures correctly`() {
        val input =
            """
            X:1
            K:C
            L:1/4
            C D | E F | G4 |
            """.trimIndent()

        val tune = AbcParser().parse(input)
        println("DEBUG: Tune elements: ${tune.body.elements.size}")
        tune.body.elements.forEachIndexed { i, e ->
            println("DEBUG: [$i] ${e.javaClass.simpleName} ($e)")
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

    @Test
    fun `test quantize handles voice overlays`() {
        val input = "X:1\nK:C\nL:1/4\nC D & E F | G A |"
        val tune = AbcParser().parse(input)
        val measures = MeasureQuantizer.quantize(tune)

        assertEquals(2, measures.size)
        // Measure 1 should contain 5 elements: C, D, &, E, F
        // However, NoteElements might be adjacent to SpacerElements, let's just assert on NoteElements and Overlays
        val m1Notes = measures[0].elements.filterIsInstance<NoteElement>()
        assertEquals(4, m1Notes.size) // C, D, E, F
        assertEquals(1, measures[0].elements.filterIsInstance<io.github.ryangardner.abc.core.model.OverlayElement>().size)
        // Because of the &, the cumulative duration of the measure mathematically is going to be max(0.5, 0.5) if we were tracking max,
        // but since we only track a single cursor which gets reset, the final 'currentMeasureDuration' will just be the duration
        // of the last layer (which is 0.5 for 'E F'). Wait, 'C D & E F' -> C (0.25) D (0.25) & (resets to 0) E (0.25) F (0.25) => total duration 0.5.
        // Wait, C (1/4) D (1/4) is 0.5 in 4/4 if L=1/4.
    }
}
