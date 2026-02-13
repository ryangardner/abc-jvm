package io.github.ryangardner.abc.test

import io.github.ryangardner.abc.parser.AbcParser
import org.junit.jupiter.api.Test
import java.io.File

class InspectTune {
    @Test
    fun inspect() {
        val file = File("abc-dataset/abc_notation_batch_001/abc_files/tune_001728.abc")
        if (!file.exists()) return
        val parser = AbcParser()
        val tune = parser.parse(file.readText())
        
        println("--- AST ELEMENTS ---")
        tune.body.elements.forEachIndexed { i, e ->
            println("[$i] ${e.javaClass.simpleName} : $e")
        }
    }
}
