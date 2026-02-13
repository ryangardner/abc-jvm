package io.github.ryangardner.abc.theory

import io.github.ryangardner.abc.core.model.*

/**
 * Represents a logical musical measure.
 * 
 * @property index The 1-based index of the measure in the tune.
 * @property elements The list of musical elements contained in this measure.
 * @property timeSignature The time signature active during this measure.
 * @property duration The total duration of the elements in this measure.
 */
public data class Measure(
    val index: Int,
    val elements: List<MusicElement>,
    val timeSignature: TimeSignature,
    val duration: NoteDuration
)

/**
 * MeasureQuantizer is responsible for grouping a linear stream of musical elements
 * into structured [Measure] objects based on the tune's time signature.
 * 
 * This is a critical step for converting stream-oriented ABC music into
 * measure-oriented formats like MusicXML or MIDI.
 */
public object MeasureQuantizer {

    /**
     * Quantizes an [AbcTune] into a list of [Measure]s.
     * 
     * This function iterates through the tune's elements, accumulating duration
     * and breaking at bar lines or when a measure's duration is exceeded.
     * 
     * @param tune The tune to quantize.
     * @return A list of [Measure] objects.
     */
    public fun quantize(tune: AbcTune): List<Measure> {
        val measures = mutableListOf<Measure>()
        val defaultMeter = if (tune.header.meter.isNone) TimeSignature(4, 4) else tune.header.meter
        var currentMeter = defaultMeter
        var targetDuration = NoteDuration(currentMeter.numerator, currentMeter.denominator)
        
        var currentMeasureIndex = 1
        var currentMeasureElements = mutableListOf<MusicElement>()
        var currentMeasureDuration = NoteDuration(0, 1)

        tune.body.elements.forEach { element ->
            when (element) {
                is NoteElement, is RestElement, is ChordElement -> {
                    val duration = element.duration
                    val newDuration = currentMeasureDuration + duration
                    
                    // Use a small epsilon for floating point comparison if needed, 
                    // but rational arithmetic in NoteDuration should be exact.
                    if (newDuration.toDouble() > targetDuration.toDouble() + 0.000001) {
                        measures.add(Measure(currentMeasureIndex++, currentMeasureElements.toList(), currentMeter, currentMeasureDuration))
                        currentMeasureElements = mutableListOf(element)
                        currentMeasureDuration = duration
                    } else {
                        currentMeasureElements.add(element)
                        currentMeasureDuration = newDuration
                    }
                }
                is BarLineElement -> {
                    // Include the bar line in the current measure before closing it
                    currentMeasureElements.add(element)
                    measures.add(Measure(currentMeasureIndex++, currentMeasureElements.toList(), currentMeter, currentMeasureDuration))
                    currentMeasureElements = mutableListOf()
                    currentMeasureDuration = NoteDuration(0, 1)
                }
                is InlineFieldElement -> {
                    if (element.fieldType == HeaderType.METER) {
                        // For quantization, we need to know the meter. 
                        // A more complete implementation would parse the value here.
                    }
                    currentMeasureElements.add(element)
                }
                is BodyHeaderElement -> {
                    if (element.key == "M") {
                        // Update meter
                    }
                    currentMeasureElements.add(element)
                }
                else -> {
                    currentMeasureElements.add(element)
                }
            }
        }

        if (currentMeasureElements.any { it !is SpacerElement } || currentMeasureDuration.toDouble() > 0) {
            measures.add(Measure(currentMeasureIndex, currentMeasureElements.toList(), currentMeter, currentMeasureDuration))
        }

        return measures
    }
}
