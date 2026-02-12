package io.github.ryangardner.abc.test

import io.github.ryangardner.abc.parser.AbcParser
import org.junit.jupiter.api.Test
import java.io.File

class AbcParserDebug {
    @Test
    fun debug011215() {
        val abcFile = File("../target/abc-dataset/abc_notation_batch_007/abc_files/tune_011215.abc")
        if (!abcFile.exists()) {
            println("File not found: ${abcFile.absolutePath}")
            return
        }
        val abcContent = abcFile.readText()
        val tune = AbcParser().parse(abcContent)
        
        println("--- Music Elements for tune_011215 (Original) ---")
        tune.body.elements.forEachIndexed { index, element ->
            println("[$index] ${element.javaClass.simpleName}: $element")
        }

        val expanded = io.github.ryangardner.abc.theory.RepeatExpander.expand(tune)
        println("--- Music Elements for tune_011215 (Expanded) ---")
        expanded.forEachIndexed { index, element ->
            println("[$index] ${element.javaClass.simpleName}: $element")
        }
    }
}
