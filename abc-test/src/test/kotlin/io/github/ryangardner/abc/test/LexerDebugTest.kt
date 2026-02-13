package io.github.ryangardner.abc.test

import io.github.ryangardner.abc.antlr.ABCLexer
import org.antlr.v4.runtime.CharStreams
import org.junit.jupiter.api.Test

class LexerDebugTest {
    
    @Test
    fun `debug lexer tokens`() {
        val abc = """
            X:1
            T:Test
            K:C
            !slide!"6:3"[G]
        """.trimIndent()
        
        val lexer = ABCLexer(CharStreams.fromString(abc))
        val tokens = lexer.allTokens
        
        println("=== Tokens ===")
        tokens.forEach { token ->
            println("${lexer.vocabulary.getSymbolicName(token.type)}: '${token.text}'")
        }
    }
}
