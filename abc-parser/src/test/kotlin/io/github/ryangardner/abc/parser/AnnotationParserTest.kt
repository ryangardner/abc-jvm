package io.github.ryangardner.abc.parser

import io.github.ryangardner.abc.core.model.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class AnnotationParserTest {

    @Test
    fun `test multiple annotations on a note`() {
        val input = """
            X: 1
            T: Multiple Annotations Note
            K: C
            "Anno1" "Anno2" C
        """.trimIndent()

        val parser = AbcParser()
        val tune = parser.parse(input)
        val note = tune.body.elements.filterIsInstance<NoteElement>().first()

        assertEquals(listOf("Anno1", "Anno2"), note.annotations)
    }

    @Test
    fun `test multiple annotations on a chord`() {
        val input = """
            X: 1
            T: Multiple Annotations Chord
            K: C
            "Cmaj7" "5:3,4:2,3:0,2:0"[CEGB]
        """.trimIndent()

        val parser = AbcParser()
        val tune = parser.parse(input)
        val chord = tune.body.elements.filterIsInstance<ChordElement>().first()

        assertEquals(listOf("Cmaj7", "5:3,4:2,3:0,2:0"), chord.annotations)
    }

    @Test
    fun `test multiple annotations on a rest`() {
        val input = """
            X: 1
            T: Multiple Annotations Rest
            K: C
            "Break" "Tacet" z
        """.trimIndent()

        val parser = AbcParser()
        val tune = parser.parse(input)
        val rest = tune.body.elements.filterIsInstance<RestElement>().first()

        assertEquals(listOf("Break", "Tacet"), rest.annotations)
    }
}
