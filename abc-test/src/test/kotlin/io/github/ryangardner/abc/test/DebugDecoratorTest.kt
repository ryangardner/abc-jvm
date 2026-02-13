package io.github.ryangardner.abc.test

import io.github.ryangardner.abc.parser.AbcParser
import org.junit.jupiter.api.Test

class DebugDecoratorTest {
    
    @Test
    fun `debug decorator and annotation parsing`() {
        val abc = """
            X:1
            T:Debug
            K:C
            !slide!"6:3"[G]
        """.trimIndent()
        
        val parser = AbcParser()
        val tune = parser.parse(abc)
        
        println("=== Parsed Elements ===")
        tune.body.elements.forEach { element ->
            println("${element::class.simpleName}: $element")
        }
    }
}
