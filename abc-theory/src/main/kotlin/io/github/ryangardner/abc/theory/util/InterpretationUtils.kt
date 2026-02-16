package io.github.ryangardner.abc.theory.util

import io.github.ryangardner.abc.core.model.*

internal object InterpretationUtils {
    private val TRANSPOSE_REGEX = "transpose=([-]?\\d+)".toRegex()
    private val OCTAVE_REGEX = "octave=([-]?\\d+)".toRegex()

    fun parseCombinedTransposition(text: String): Int? {
        val lower = text.lowercase()
        var totalShift = 0
        var foundAny = false

        // 1. Check for clef-based octave modifiers
        val clefShift = when {
            lower.contains("treble-8") || lower.contains("treble8vb") -> -12
            lower.contains("bass+8") || lower.contains("bass8va") -> 12
            lower.contains("-8va") || lower.contains("8vb") || lower.contains("-8") || lower.contains("8-") -> -12
            lower.contains("+8va") || lower.contains("8va") || lower.contains("+8") || lower.contains(" treble8") -> 12
            lower.contains("clef=bass") -> -24 // Standard bass clef shift from treble baseline
            lower.contains("clef=alto") -> -12 // Standard alto clef shift
            else -> null
        }
        if (clefShift != null) {
            totalShift += clefShift
            foundAny = true
        }

        // 2. Check for transpose=N
        TRANSPOSE_REGEX.find(text)?.let {
            totalShift += it.groupValues[1].toIntOrNull() ?: 0
            foundAny = true
        }

        // 3. Check for octave=N
        OCTAVE_REGEX.find(text)?.let {
            totalShift += (it.groupValues[1].toIntOrNull() ?: 0) * 12
            foundAny = true
        }

        return if (foundAny) totalShift else null
    }

    fun parseMeter(text: String): TimeSignature {
        return when (text) {
            "C" -> TimeSignature(4, 4, "C")
            "C|" -> TimeSignature(2, 2, "C|")
            "none" -> TimeSignature.NONE
            else -> {
                val parts = text.split("/")
                if (parts.size == 2) {
                    TimeSignature(parts[0].trim().toIntOrNull() ?: 4, parts[1].trim().toIntOrNull() ?: 4)
                } else TimeSignature.NONE
            }
        }
    }
}

internal fun addDurations(d1: NoteDuration, d2: NoteDuration): NoteDuration {
    val commonDenom = d1.denominator.toLong() * d2.denominator.toLong()
    val newNum = d1.numerator.toLong() * d2.denominator + d2.numerator.toLong() * d1.denominator
    return NoteDuration.simplify(newNum, commonDenom)
}

internal fun NoteDuration.multiply(p: Int, q: Int): NoteDuration {
    return NoteDuration.simplify(this.numerator.toLong() * p, this.denominator.toLong() * q)
}

internal fun NoteDuration.multiply(multiplier: Double): NoteDuration {
    // Use the exact rational scaling from NoteDuration
    return this.scale(multiplier)
}
