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
}
