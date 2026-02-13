package io.github.ryangardner.abc.core.model

/**
 * Represents a time signature (meter).
 *
 * @property numerator The number of beats per measure.
 * @property denominator The note value that represents one beat.
 * @property symbol Optional symbol representation (e.g., "C", "C|").
 */
public data class TimeSignature @JvmOverloads constructor(
    public val numerator: Int,
    public val denominator: Int,
    public val symbol: String? = null
) {
    public fun toDouble(): Double = numerator.toDouble() / denominator.toDouble()

    public fun toNoteDuration(): NoteDuration = NoteDuration(numerator, denominator)

    public val isNone: Boolean get() = numerator == 0

    public companion object {
        public val NONE: TimeSignature = TimeSignature(0, 1, "none")
    }
}
