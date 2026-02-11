package io.github.ryangardner.abc.theory.util

import io.github.ryangardner.abc.core.model.*

internal object InterpretationUtils {
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
        val transposeRegex = "transpose=([-]?\d+)".toRegex()
        transposeRegex.find(text)?.let {
            totalShift += it.groupValues[1].toIntOrNull() ?: 0
            foundAny = true
        }

        // 3. Check for octave=N
        val octaveRegex = "octave=([-]?\d+)".toRegex()
        octaveRegex.find(text)?.let {
            totalShift += (it.groupValues[1].toIntOrNull() ?: 0) * 12
            foundAny = true
        }

        return if (foundAny) totalShift else null
    }

    fun parseMeter(text: String): TimeSignature {
        return when (text) {
            "C" -> TimeSignature(4, 4, "C")
            "C|" -> TimeSignature(2, 2, "C|")
            "none" -> TimeSignature(4, 4) // Default
            else -> {
                val parts = text.split("/")
                if (parts.size == 2) {
                    TimeSignature(parts[0].trim().toIntOrNull() ?: 4, parts[1].trim().toIntOrNull() ?: 4)
                } else TimeSignature(4, 4)
            }
        }
    }
}

internal fun addDurations(d1: NoteDuration, d2: NoteDuration): NoteDuration {
    val commonDenom = d1.denominator.toLong() * d2.denominator.toLong()
    val newNum = d1.numerator.toLong() * d2.denominator + d2.numerator.toLong() * d1.denominator
    return NoteDuration.simplify(newNum.toInt(), commonDenom.toInt())
}

internal fun NoteDuration.multiply(p: Int, q: Int): NoteDuration {
    return NoteDuration.simplify(this.numerator * p, this.denominator * q)
}

internal fun NoteDuration.multiply(multiplier: Double): NoteDuration {
    // Fallback for non-simple multipliers
    val newNumerator = (this.numerator * multiplier * 1000).toInt()
    val newDenominator = this.denominator * 1000
    return NoteDuration.simplify(newNumerator, newDenominator)
}
