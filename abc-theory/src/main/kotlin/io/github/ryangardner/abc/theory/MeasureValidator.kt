package io.github.ryangardner.abc.theory

import io.github.ryangardner.abc.core.model.*
import kotlin.math.abs

public object MeasureValidator {

    /**
     * Validates that all measures in the tune (except possibly the first and last)
     * sum up to the expected duration defined by the time signature.
     * 
     * @return A list of validation errors. If empty, the tune is semantically valid for rhythm.
     */
    public fun validate(tune: AbcTune): List<MeasureError> {
        val errors = mutableListOf<MeasureError>()
        var currentMeter = tune.header.meter
        var targetValue = currentMeter.toDouble()
        var currentDefaultLength = tune.header.length

        // Track state per voice
        val voiceSums = mutableMapOf<String, Double>()
        val voiceMeasureIndices = mutableMapOf<String, Int>()
        
        var currentVoice = "default"
        var inTupletRemainingNotes = 0
        var tupletMultiplier = 1.0

        tune.body.elements.forEach { element ->
            when (element) {
                is NoteElement -> {
                    val duration = element.length.toDouble()
                    val scaled = if (inTupletRemainingNotes > 0) {
                        inTupletRemainingNotes--
                        duration * tupletMultiplier
                    } else duration
                    voiceSums[currentVoice] = (voiceSums[currentVoice] ?: 0.0) + scaled
                }
                is RestElement -> {
                    val duration = element.duration.toDouble()
                    val scaled = if (inTupletRemainingNotes > 0) {
                        inTupletRemainingNotes--
                        duration * tupletMultiplier
                    } else duration
                    voiceSums[currentVoice] = (voiceSums[currentVoice] ?: 0.0) + scaled
                }
                is ChordElement -> {
                    val duration = element.duration.toDouble()
                    val scaled = if (inTupletRemainingNotes > 0) {
                        inTupletRemainingNotes--
                        duration * tupletMultiplier
                    } else duration
                    voiceSums[currentVoice] = (voiceSums[currentVoice] ?: 0.0) + scaled
                }
                is TupletElement -> {
                    val r = element.r ?: element.p
                    val q = element.q ?: calculateDefaultQ(element.p)
                    tupletMultiplier = q.toDouble() / element.p.toDouble()
                    inTupletRemainingNotes = r
                }
                is InlineFieldElement -> {
                    val cleanValue = element.value.split(" ", "%").first().trim()
                    when (element.fieldType) {
                        HeaderType.METER -> {
                            val parts = cleanValue.split("/")
                            if (parts.size == 2) {
                                currentMeter = TimeSignature(parts[0].toIntOrNull() ?: 4, parts[1].toIntOrNull() ?: 4)
                                targetValue = currentMeter.toDouble()
                            }
                        }
                        HeaderType.VOICE -> {
                            currentVoice = cleanValue
                        }
                        HeaderType.LENGTH -> {
                            val parts = cleanValue.split("/")
                            if (parts.size == 2) {
                                currentDefaultLength = NoteDuration(parts[0].toIntOrNull() ?: 1, parts[1].toIntOrNull() ?: 8)
                            }
                        }
                        else -> {}
                    }
                }
                is BodyHeaderElement -> {
                    val cleanValue = element.value.split(" ", "%").first().trim()
                    if (element.key == "V") {
                        currentVoice = cleanValue
                    } else if (element.key == "M") {
                        val parts = cleanValue.split("/")
                        if (parts.size == 2) {
                            currentMeter = TimeSignature(parts[0].toIntOrNull() ?: 4, parts[1].toIntOrNull() ?: 4)
                            targetValue = currentMeter.toDouble()
                        }
                    } else if (element.key == "L") {
                        val parts = cleanValue.split("/")
                        if (parts.size == 2) {
                            currentDefaultLength = NoteDuration(parts[0].toIntOrNull() ?: 1, parts[1].toIntOrNull() ?: 8)
                        }
                    }
                }
                is BarLineElement -> {
                    val currentMeasureIdx = voiceMeasureIndices[currentVoice] ?: 0
                    val sum = voiceSums[currentVoice] ?: 0.0
                    
                    if (currentMeasureIdx > 0 && sum > 0.0) {
                        if (abs(sum - targetValue) > 0.0001) {
                            errors.add(MeasureError(currentMeasureIdx, sum, targetValue, currentVoice))
                        }
                    }
                    
                    voiceSums[currentVoice] = 0.0
                    voiceMeasureIndices[currentVoice] = currentMeasureIdx + 1
                }
                else -> {}
            }
        }

        return errors
    }

    private fun calculateDefaultQ(p: Int): Int {
        return when (p) {
            2 -> 3
            3 -> 2
            4 -> 3
            5 -> 2
            6 -> 2
            7 -> 3
            8 -> 3
            9 -> 2
            else -> 2
        }
    }
}

public data class MeasureError(
    val measureIndex: Int,
    val actualDuration: Double,
    val expectedDuration: Double,
    val voice: String
)
