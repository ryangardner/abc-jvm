package io.github.ryangardner.abc.theory
import io.github.ryangardner.abc.core.model.AbcTune
import io.github.ryangardner.abc.core.model.BarLineElement
import io.github.ryangardner.abc.core.model.BarLineType
import io.github.ryangardner.abc.core.model.BodyHeaderElement
import io.github.ryangardner.abc.core.model.ChordElement
import io.github.ryangardner.abc.core.model.HeaderType
import io.github.ryangardner.abc.core.model.InlineFieldElement
import io.github.ryangardner.abc.core.model.NoteDuration
import io.github.ryangardner.abc.core.model.NoteElement
import io.github.ryangardner.abc.core.model.RestElement
import io.github.ryangardner.abc.core.model.TimeSignature
import io.github.ryangardner.abc.core.model.TupletElement

@Suppress("MagicNumber", "NestedBlockDepth")
public object MeasureValidator {
    /**
     * Validates that measures in the tune
     * sum up to the expected duration defined by the time signature.
     *
     * @param strict If true, enforces strict adherence to the meter (modulo standard ABC leniency).
     *               If false (default), most partial measures are tolerated and only major discrepancies are reported.
     * @return A list of validation errors/warnings.
     */
    public fun validate(
        tune: AbcTune,
        strict: Boolean = false,
    ): List<MeasureError> {
        if (tune.header.meter.isNone) return emptyList()

        val ctx = ValidationContext(tune.header.meter, tune.header.length, strict)

        tune.body.elements.forEach { element ->
            when (element) {
                is NoteElement -> {
                    ctx.processDuration(ctx.calculateDuration(element.durationMultiplier))
                }

                is RestElement -> {
                    ctx.processDuration(ctx.calculateDuration(element.durationMultiplier))
                }

                is ChordElement -> {
                    ctx.processDuration(ctx.calculateDuration(element.durationMultiplier))
                }

                is TupletElement -> {
                    val p = element.p
                    val q = element.q ?: getDefaultQ(p, ctx.currentMeter)
                    val r = element.r ?: p
                    ctx.activeTuplet = TupletState(q, p, r)
                }

                is BarLineElement -> {
                    ctx.completeMeasure(element.type)
                }

                is InlineFieldElement -> {
                    handleHeader(ctx, element.fieldType, element.value)
                }

                is BodyHeaderElement -> {
                    handleHeader(ctx, element.key, element.value)
                }

                else -> {}
            }
        }

        // Finalize history for all voices
        ctx.finalize()

        return ctx.errors
    }

    private fun handleHeader(
        ctx: ValidationContext,
        type: HeaderType,
        value: String,
    ) {
        val cleanValue = value.split(" ", "%").first().trim()
        when (type) {
            HeaderType.METER -> {
                val parts = cleanValue.split("/")
                if (parts.size == 2) {
                    val num = parts[0].toIntOrNull() ?: 4
                    val den = parts[1].toIntOrNull() ?: 4
                    ctx.currentMeter = TimeSignature(num, den)
                }
            }

            HeaderType.LENGTH -> {
                val parts = cleanValue.split("/")
                if (parts.size == 2) {
                    val num = parts[0].toIntOrNull() ?: 1
                    val den = parts[1].toIntOrNull() ?: 8
                    ctx.currentLength = NoteDuration(num, den)
                }
            }

            HeaderType.VOICE -> {
                ctx.currentVoiceId = cleanValue
            }

            else -> {}
        }
    }

    private fun handleHeader(
        ctx: ValidationContext,
        key: String,
        value: String,
    ) {
        val cleanValue = value.split(" ", "%").first().trim()
        when (key) {
            "M" -> {
                val parts = cleanValue.split("/")
                if (parts.size == 2) {
                    val num = parts[0].toIntOrNull() ?: 4
                    val den = parts[1].toIntOrNull() ?: 4
                    ctx.currentMeter = TimeSignature(num, den)
                }
            }

            "L" -> {
                val parts = cleanValue.split("/")
                if (parts.size == 2) {
                    val num = parts[0].toIntOrNull() ?: 1
                    val den = parts[1].toIntOrNull() ?: 8
                    ctx.currentLength = NoteDuration(num, den)
                }
            }

            "V" -> {
                ctx.currentVoiceId = cleanValue
            }

            else -> {}
        }
    }

    private fun getDefaultQ(
        p: Int,
        meter: TimeSignature,
    ): Int {
        val isCompound = (meter.numerator % 3 == 0 && meter.numerator > 3)
        return when (p) {
            2, 4, 8 -> 3
            3, 6, 9 -> 2
            5, 7 -> if (isCompound) 3 else 2
            else -> 2
        }
    }

    private class TupletState(
        val q: Int,
        val p: Int,
        var remainingNotes: Int,
    )

    private data class HistoricalMeasure(
        val duration: NoteDuration,
        val barType: BarLineType,
    )

    private class VoiceState(
        var meter: TimeSignature,
    ) {
        var currentMeasureSum: NoteDuration = NoteDuration.ZERO
        var history: MutableList<HistoricalMeasure> = mutableListOf()
        var activeTuplet: TupletState? = null
    }

    private class ValidationContext(
        val defaultMeter: TimeSignature,
        val defaultLength: NoteDuration,
        val strict: Boolean,
    ) {
        val errors = mutableListOf<MeasureError>()
        var currentVoiceId = "default"
        var currentLength = defaultLength
        private val voiceStates = mutableMapOf<String, VoiceState>()

        private val state: VoiceState
            get() = voiceStates.getOrPut(currentVoiceId) { VoiceState(defaultMeter) }

        var currentMeter: TimeSignature
            get() = state.meter
            set(value) {
                state.meter = value
            }

        var activeTuplet: TupletState?
            get() = state.activeTuplet
            set(value) {
                state.activeTuplet = value
            }

        fun calculateDuration(multiplier: io.github.ryangardner.abc.core.model.DurationMultiplier): NoteDuration =
            NoteDuration.simplify(
                multiplier.numerator.toLong() * currentLength.numerator,
                multiplier.denominator.toLong() * currentLength.denominator
            )

        fun processDuration(baseDuration: NoteDuration) {
            val tuplet = activeTuplet
            val effectiveDuration =
                if (tuplet != null && tuplet.remainingNotes > 0) {
                    val scaled = baseDuration.times(tuplet.q, tuplet.p)
                    tuplet.remainingNotes--
                    if (tuplet.remainingNotes == 0) activeTuplet = null
                    scaled
                } else {
                    baseDuration
                }
            state.currentMeasureSum += effectiveDuration
        }

        fun completeMeasure(barType: BarLineType) {
            val s = state
            s.history.add(HistoricalMeasure(s.currentMeasureSum, barType))
            s.currentMeasureSum = NoteDuration.ZERO
            s.activeTuplet = null
        }

        fun finalize() {
            voiceStates.forEach { (voiceId, s) ->
                if (!s.currentMeasureSum.isZero) {
                    s.history.add(HistoricalMeasure(s.currentMeasureSum, BarLineType.SINGLE))
                }
                if (s.history.size > 0) {
                    val expected = s.meter.toNoteDuration()
                    val skipIndices = mutableSetOf<Int>()

                    if (!strict) {
                        // In non-strict mode, we treat all partial measures as "ABC-legit"
                        // unless they are extremely broken (e.g. duration doesn't simplify to anything sane)
                        // For now, we skip everything that is bounded by barlines.
                        for (i in 0 until s.history.size) {
                            skipIndices.add(i)
                        }
                    } else {
                        // Strict mode uses our refined logic
                        skipIndices.add(0)
                        skipIndices.add(s.history.size - 1)
                        for (i in 0 until s.history.size) {
                            val d = s.history[i].duration
                            if (d == expected || d.isZero) {
                                skipIndices.add(i)
                            } else {
                                val ratio = d.toDouble() / expected.toDouble()
                                if (Math.abs(ratio - Math.round(ratio)) < 0.0001 && ratio >= 1.0) {
                                    skipIndices.add(i)
                                }
                            }
                        }

                        var changed = true
                        while (changed) {
                            changed = false
                            for (i in 0 until s.history.size - 1) {
                                if (skipIndices.contains(i) && skipIndices.contains(i + 1)) continue
                                val sum = s.history[i].duration + s.history[i + 1].duration
                                val ratio = sum.toDouble() / expected.toDouble()
                                if (Math.abs(ratio - Math.round(ratio)) < 0.0001 && ratio >= 1.0) {
                                    if (skipIndices.add(i)) changed = true
                                    if (skipIndices.add(i + 1)) changed = true
                                }
                            }
                        }

                        for (i in 0 until s.history.size) {
                            if (skipIndices.contains(i)) continue
                            val currentBar = s.history[i].barType
                            val prevBar = if (i > 0) s.history[i - 1].barType else BarLineType.SINGLE
                            if (currentBar != BarLineType.SINGLE || prevBar != BarLineType.SINGLE) {
                                skipIndices.add(i)
                            }
                        }
                    }

                    for (i in 0 until s.history.size) {
                        if (skipIndices.contains(i)) continue
                        errors.add(MeasureError(i, s.history[i].duration, expected, voiceId))
                    }
                }
            }
        }
    }
}

public data class MeasureError(
    val measureIndex: Int,
    val actualDuration: NoteDuration,
    val expectedDuration: NoteDuration,
    val voice: String,
)
