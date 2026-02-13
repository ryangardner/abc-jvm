package io.github.ryangardner.abc.test

import io.github.ryangardner.abc.core.model.*
import io.github.ryangardner.abc.parser.AbcParser
import io.github.ryangardner.abc.parser.AbcSerializer
import io.github.ryangardner.abc.theory.PitchInterpreter
import org.junit.jupiter.api.Test
import java.io.File

class DebugFidelityTest {
    @Test
    fun debugPartsTest() {
        val filename = "parts_test.abc"
        val paths = listOf(
            "src/test/resources/regression-samples/$filename",
            "abc-test/src/test/resources/regression-samples/$filename"
        )
        var file: File? = null
        for (path in paths) {
            val f = File(path)
            if (f.exists()) {
                file = f
                break
            }
        }
        
        if (file == null) {
            println("File not found: $filename")
            return
        }
        val content = file.readText()
        val parser = AbcParser()
        val originalTunes = parser.parseBook(content)
        val originalTune = originalTunes[0]
        
        println("--- DEBUGGING HEADER OF $filename ---")
        println("Original Header titles: ${originalTune.header.title}")
        println("Playing Order: ${originalTune.header.playingOrder}")
        println("Unknown Headers: ${originalTune.header.unknownHeaders}")
        
        val serializer = AbcSerializer()
        val serialized = serializer.serialize(originalTune)
        println("Serialized Output:\n$serialized")
    }
}
