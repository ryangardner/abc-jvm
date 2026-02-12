package io.github.ryangardner.abc.test

import io.github.ryangardner.abc.core.model.*
import io.github.ryangardner.abc.parser.AbcParser
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

public class TextBlockParserTest {
    @Test
    public fun `parses text block correctly`() {
        val abc = """
            X:1
            T:Text Block Test
            K:C
            %%begintext
            This is a text block.
            It has multiple lines.
            %%endtext
            C D E F |
        """.trimIndent()
        
        val parser = AbcParser()
        val tune = parser.parse(abc)
        
        val elements = tune.body.elements
        val textBlock = elements.filterIsInstance<TextBlockElement>().firstOrNull()
        
        assertNotNull(textBlock, "Should find a TextBlockElement")
        
        val content = textBlock!!.content
        // Content lines should match lines inside
        assertTrue(content.any { it.contains("This is a text block.") }, "Content should contain line 1")
        assertTrue(content.any { it.contains("It has multiple lines.") }, "Content should contain line 2")
    }
}
