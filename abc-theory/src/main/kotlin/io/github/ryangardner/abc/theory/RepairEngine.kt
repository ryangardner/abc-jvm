package io.github.ryangardner.abc.theory
import io.github.ryangardner.abc.core.model.AbcTune
import io.github.ryangardner.abc.core.model.BarLineElement
import io.github.ryangardner.abc.core.model.BarLineType
import io.github.ryangardner.abc.core.model.MusicElement
import io.github.ryangardner.abc.core.model.ChordElement
import io.github.ryangardner.abc.core.model.DurationMultiplier
import io.github.ryangardner.abc.core.model.NoteElement
import io.github.ryangardner.abc.core.model.BrokenRhythmMarkerElement
import io.github.ryangardner.abc.core.model.RestElement
import io.github.ryangardner.abc.core.model.TuneBody
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
                    val insertIdx =
                        if (repairedElements.lastOrNull() is BarLineElement) {
                            repairedElements.size - 1
                        } else {
                            repairedElements.size
                        }
                    repairedElements.add(insertIdx, RestElement(DurationMultiplier(1, (1.0 / diff).toInt())))
                }
            }

            // Only add a bar line if the measure didn't already have one
            if (measure.elements.none { it is BarLineElement }) {
                repairedElements.add(BarLineElement(BarLineType.SINGLE))
            }
        }

        return tune.copy(body = TuneBody(repairedElements))
    }

    /**
     * Resolves BrokenRhythmMarkerElement nodes in the AST by multiplying the duration
     * of the preceding and succeeding notes accordingly.
     *
     * @param tune The tune to repair.
     * @return A new [AbcTune] with resolved broken rhythms.
     */
    public fun resolveBrokenRhythms(tune: AbcTune): AbcTune {
        val oldElements = tune.body.elements
        val newElements = mutableListOf<MusicElement>()

        var i = 0
        var pendingMultiplierForNext: Double? = null

        while (i < oldElements.size) {
            val element = oldElements[i]

            if (element is BrokenRhythmMarkerElement) {
                val prevIdx = newElements.indexOfLast { it is NoteElement || it is RestElement || it is ChordElement }
                if (prevIdx != -1) {
                    val prevElement = newElements[prevIdx]
                    val dots = element.type.length
                    val m1 =
                        if (element.type.startsWith(">")) {
                            (Math.pow(2.0, dots.toDouble() + 1) - 1) / Math.pow(2.0, dots.toDouble())
                        } else {
                            1.0 / Math.pow(2.0, dots.toDouble())
                        }
                    val m2 = 2.0 - m1

                    newElements[prevIdx] = scaleDuration(prevElement, m1)
                    pendingMultiplierForNext = m2
                }
            } else if (element is NoteElement || element is RestElement || element is ChordElement) {
                if (pendingMultiplierForNext != null) {
                    newElements.add(scaleDuration(element, pendingMultiplierForNext))
                    pendingMultiplierForNext = null
                } else {
                    newElements.add(element)
                }
            } else {
                newElements.add(element)
            }
            i++
        }

        return tune.copy(body = TuneBody(newElements))
    }

    private fun scaleDuration(
        element: MusicElement,
        factor: Double,
    ): MusicElement {
        return when (element) {
            is NoteElement ->
                element.copy(
                    durationMultiplier = scaleMultiplier(element.durationMultiplier, factor),
                )
            is RestElement ->
                element.copy(
                    durationMultiplier = scaleMultiplier(element.durationMultiplier, factor),
                )
            is ChordElement ->
                element.copy(
                    durationMultiplier = scaleMultiplier(element.durationMultiplier, factor),
                )
            else -> element
        }
    }

    private fun scaleMultiplier(
        multiplier: DurationMultiplier,
        factor: Double,
    ): DurationMultiplier {
        // Find a rational approximation of factor
        val p = (factor * 256).toInt()
        val num = multiplier.numerator * p
        val den = multiplier.denominator * 256
        return DurationMultiplier(num, den) // A more robust Rational simplification might be better here
    }
}
