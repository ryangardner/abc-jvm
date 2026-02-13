package io.github.ryangardner.abc.examples

import io.github.ryangardner.abc.theory.dsl.abcTune
import io.github.ryangardner.abc.core.model.NoteDuration
import io.github.ryangardner.abc.theory.PitchInterpreter
import io.github.ryangardner.abc.theory.RepairEngine
import io.github.ryangardner.abc.theory.MeasureQuantizer

/**
 * Example showcasing the creation of a tune using the DSL,
 * converting it to a timeline, and performing rhythmic repair.
 */
fun main() {
    // 1. Create a tune using the DSL
    val tune = abcTune {
        header {
            title = "DSL and Repair Example"
            key = "G"
            meter = "4/4"
        }
        body {
            note("G", NoteDuration(1, 4))
            note("A", NoteDuration(1, 4))
            bar() // Measure 1 is short (2 beats)
            note("B", NoteDuration(1, 2))
            note("C", NoteDuration(1, 2))
            bar() // Measure 2 is correct (4 beats)
        }
    }

    println("Original Tune Measures:")
    MeasureQuantizer.quantize(tune).forEach { m ->
        println("Measure ${m.index}: ${m.duration.toDouble()} beats")
    }

    // 2. Repair the tune
    val repairedTune = RepairEngine.repairRhythm(tune)
    
    println("\nRepaired Tune Measures:")
    MeasureQuantizer.quantize(repairedTune).forEach { m ->
        println("Measure ${m.index}: ${m.duration.toDouble()} beats")
    }

    // 3. Use the Timeline API
    val timeline = PitchInterpreter.toTimeline(repairedTune)
    println("\nTimeline Analysis:")
    println("Total duration: ${timeline.totalBeats()} beats")
    
    val eventsAtStart = timeline.getEventsAt(0.0)
    println("Events at start: ${eventsAtStart.size}")
}
