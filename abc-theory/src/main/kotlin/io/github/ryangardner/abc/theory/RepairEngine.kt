package io.github.ryangardner.abc.theory

import io.github.ryangardner.abc.core.model.*
import kotlin.math.abs

/**
 * RepairEngine provides automated tools for correcting common rhythmic and
 * semantic errors in ABC tunes.
 * 
 * It is particularly useful when dealing with "loose" ABC files that may have
 * inconsistent measure durations.
 */
public object RepairEngine {

    /**
     * Repairs an [AbcTune] by normalizing rhythmic mismatches.
     * 
     * This method uses the [MeasureQuantizer] to analyze the tune's measures.
     * If a measure is shorter than the expected duration of the time signature,
     * it is padded with a rest.
     * 
     * @param tune The tune to repair.
     * @return A new [AbcTune] with corrected rhythmic boundaries.
     */
    public fun repairRhythm(tune: AbcTune): AbcTune {
        val quantizer = MeasureQuantizer
        val measures = quantizer.quantize(tune)
        val repairedElements = mutableListOf<MusicElement>()
        
        measures.forEach { measure ->
            val expectedDuration = measure.timeSignature.toDouble()
            val actualDuration = measure.duration.toDouble()
            
            repairedElements.addAll(measure.elements)
            
            if (abs(actualDuration - expectedDuration) > 0.0001) {
                if (actualDuration < expectedDuration) {
                    // Pad with rest
                    val diff = expectedDuration - actualDuration
                    // This is a simplified approach, ideally we should use NoteDuration better
                    // Find the last element that isn't a barline to insert before
                    val insertIdx = if (repairedElements.lastOrNull() is BarLineElement) repairedElements.size - 1 else repairedElements.size
                    repairedElements.add(insertIdx, RestElement(NoteDuration(1, (1.0 / diff).toInt())))
                }
            }
            
            // Only add a bar line if the measure didn't already have one
            if (measure.elements.none { it is BarLineElement }) {
                repairedElements.add(BarLineElement(BarLineType.SINGLE))
            }
        }

        return tune.copy(body = TuneBody(repairedElements))
    }
}
