package io.github.ryangardner.abc.test.integration

import io.github.ryangardner.abc.core.model.*
import io.github.ryangardner.abc.parser.AbcParser
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class AnnotationTest {

    @Test
    fun `test note annotations are preserved`() {
        val input = """
            X:1
            K:C
            "Am"C "G"D | [CEG]2 |
        """.trimIndent()

        val parser = AbcParser()
        val tune = parser.parse(input)
        val elements = tune.body.elements

        // "Am"C
        val note1 = elements.filterIsInstance<NoteElement>()[0]
        assertEquals("Am", note1.annotation)

        // "G"D
        val note2 = elements.filterIsInstance<NoteElement>()[1]
        assertEquals("G", note2.annotation)
        
        // [CEG]2
        // Wait, the chord should also have its annotation if it had one.
        // Let's add an annotation to a chord too.
    }

    @Test
    fun `test chord annotations are preserved`() {
        val input = """
            X:1
            K:C
            "F"[FAC]2 |
        """.trimIndent()

        val parser = AbcParser()
        val tune = parser.parse(input)
        val elements = tune.body.elements

        val chord = elements.filterIsInstance<ChordElement>()[0]
        assertEquals("F", chord.annotation)
    }
}
