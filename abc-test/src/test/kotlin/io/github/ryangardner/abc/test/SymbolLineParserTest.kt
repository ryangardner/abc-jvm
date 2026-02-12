package io.github.ryangardner.abc.test

import io.github.ryangardner.abc.core.model.*
import io.github.ryangardner.abc.parser.AbcParser
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

public class SymbolLineParserTest {
    @Test
    public fun `parses symbol line correctly`() {
        val abc = """
            X:1
            T:Symbol Line Test
            K:C
            C D E F |
            s: "Cm" * !trill! * |
        """.trimIndent()
        
        val parser = AbcParser()
        val tune = parser.parse(abc)
        
        val elements = tune.body.elements
        val symbolLine = elements.filterIsInstance<SymbolLineElement>().firstOrNull()
        
        assertNotNull(symbolLine, "Should find a SymbolLineElement")
        
        val items = symbolLine!!.items
        assertEquals(5, items.size, "Should match item count") 
        
        val item0 = items[0]
        assertTrue(item0 is SymbolChord, "Item 0 should be SymbolChord")
        assertEquals("Cm", (item0 as SymbolChord).name)
        
        assertTrue(items[1] is SymbolSkip, "Item 1 should be SymbolSkip")
        
        val item2 = items[2]
        assertTrue(item2 is SymbolDecoration, "Item 2 should be SymbolDecoration")
        assertEquals("trill", (item2 as SymbolDecoration).name)
        
        assertTrue(items[3] is SymbolSkip, "Item 3 should be SymbolSkip")
        
        assertTrue(items[4] is SymbolBar, "Item 4 should be SymbolBar")
    }
}
