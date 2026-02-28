package io.github.ryangardner.abc.test

import io.github.ryangardner.abc.core.model.DurationMultiplier
import io.github.ryangardner.abc.core.model.NoteDuration
import io.github.ryangardner.abc.parser.AbcParser
import io.github.ryangardner.abc.theory.PitchInterpreter
import io.github.ryangardner.abc.theory.RepairEngine
import java.io.File
import org.junit.jupiter.api.Test

class RepairDebugTest {
    @Test
    fun testRepair() {
        val src = File("../abc-dataset/abc_notation_batch_001/abc_files/tune_000347.abc").readText()
        val parser = AbcParser()
        val tune = parser.parse(src)
        
        println("=== TUNE PIPELINE ===")
        val interpreted = PitchInterpreter.interpretUnexpanded(tune)
        
        println("=== INTERPRETED EVENTS ===")
        interpreted.voices.values.firstOrNull()?.take(5)?.forEachIndexed { idx, ev -> 
            println("Event $idx: ${ev.pitches} duration=${ev.duration.toDouble()}")
        }
    }
}
