package io.github.ryangardner.abc.test

import io.github.ryangardner.abc.core.model.ChordElement
import io.github.ryangardner.abc.core.model.Decoration
import io.github.ryangardner.abc.core.model.NoteElement
import io.github.ryangardner.abc.parser.AbcParser
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DecoratorAnnotationBugTest {
    
    private val parser = AbcParser()
    
    @Test
    fun `decorator and annotation on chord should both be preserved`() {
        val abc = """
            X:1
            T:Bug Reproduction
            K:C
            !slide!"6:3"[G]
        """.trimIndent()
        
        val tune = parser.parse(abc)
        val elements = tune.body.elements
        
        // Find the chord element
        val chord = elements.filterIsInstance<ChordElement>().firstOrNull()
        assertNotNull(chord, "Should have a chord element")
        
        // Check that the decoration is present
        assertTrue(chord.decorations.any { it.value == "slide" }, 
            "Chord should have !slide! decoration, but has: ${chord.decorations}")
        
        // Check that the annotation is present
        assertEquals("6:3", chord.annotation, 
            "Chord should have annotation '6:3'")
    }
    
    @Test
    fun `decorator without annotation on chord works`() {
        val abc = """
            X:1
            T:Test
            K:C
            !slide![G]
        """.trimIndent()
        
        val tune = parser.parse(abc)
        val chord = tune.body.elements.filterIsInstance<ChordElement>().firstOrNull()
        assertNotNull(chord)
        assertTrue(chord.decorations.any { it.value == "slide" })
    }
    
    @Test
    fun `annotation without decorator on chord works`() {
        val abc = """
            X:1
            T:Test
            K:C
            "6:3"[G]
        """.trimIndent()
        
        val tune = parser.parse(abc)
        val chord = tune.body.elements.filterIsInstance<ChordElement>().firstOrNull()
        assertNotNull(chord)
        assertEquals("6:3", chord.annotation)
    }
    
    @Test
    fun `decorator and annotation on note works correctly`() {
        val abc = """
            X:1
            T:Test
            K:C
            !slide!"6:3"G
        """.trimIndent()
        
        val tune = parser.parse(abc)
        val note = tune.body.elements.filterIsInstance<NoteElement>().firstOrNull()
        assertNotNull(note)
        assertTrue(note.decorations.any { it.value == "slide" })
        assertEquals("6:3", note.annotation)
    }
}
