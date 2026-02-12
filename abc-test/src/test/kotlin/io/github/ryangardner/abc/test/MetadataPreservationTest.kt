package io.github.ryangardner.abc.test

import io.github.ryangardner.abc.parser.AbcParser
import io.github.ryangardner.abc.theory.PitchInterpreter
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MetadataPreservationTest {

    @Test
    fun `should preserve chord annotations on single notes`() {
        val abc = """
            X:1
            T:Annotation Test
            K:C
            "Am" C4
        """.trimIndent()

        val tune = AbcParser().parse(abc)
        val interpreted = PitchInterpreter.interpret(tune)
        
        val note = interpreted.voices["1"]!![0]
        assertEquals("Am", note.annotation, "Annotation 'Am' should be preserved on the note")
    }

    @Test
    fun `should preserve chord annotations on rests`() {
        val abc = """
            X:1
            T:Rest Annotation Test
            K:C
            "G7" z4
        """.trimIndent()

        val tune = AbcParser().parse(abc)
        val interpreted = PitchInterpreter.interpret(tune)
        
        val rest = interpreted.voices["1"]!![0]
        assertTrue(rest.isRest)
        assertEquals("G7", rest.annotation, "Annotation 'G7' should be preserved on the rest")
    }

    @Test
    fun `should preserve musical decorations`() {
        val abc = """
            X:1
            T:Decoration Test
            K:C
            !staccato! C2 !fermata! D2
        """.trimIndent()

        val tune = AbcParser().parse(abc)
        val interpreted = PitchInterpreter.interpret(tune)
        
        val voice = interpreted.voices["1"]!!
        
        val cNote = voice[0]
        assertTrue(cNote.decorations.any { it.value == "staccato" }, "Staccato decoration should be present on C note")
        
        val dNote = voice[1]
        assertTrue(dNote.decorations.any { it.value == "fermata" }, "Fermata decoration should be present on D note")
    }
    
    @Test
    fun `should preserve chord symbols on chords`() {
        val abc = """
            X:1
            T:Chord Symbol Test
            K:C
            "Dm7" [F A c e]4
        """.trimIndent()

        val tune = AbcParser().parse(abc)
        val interpreted = PitchInterpreter.interpret(tune)
        
        val chord = interpreted.voices["1"]!![0]
        assertEquals("Dm7", chord.annotation, "Annotation 'Dm7' should be preserved on the chord")
    }
}
