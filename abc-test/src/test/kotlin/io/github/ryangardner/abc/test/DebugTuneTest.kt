package io.github.ryangardner.abc.test

import io.github.ryangardner.abc.parser.AbcParser
import io.github.ryangardner.abc.parser.AbcSerializer
import org.junit.jupiter.api.Test
import java.io.File

public class DebugTuneTest {
    @Test
    public fun debugTune1055() {
        val file = File("src/test/resources/sanity-samples/tune_001055.abc")
        if (!file.exists()) return
        val abc = file.readText()
        val tunes = AbcParser().parseBook(abc)
        val serialized = AbcSerializer().serialize(tunes[0])
        println("--- SERIALIZED TUNE 1055 ---")
        println(serialized)
        println("----------------------------")
    }
}
