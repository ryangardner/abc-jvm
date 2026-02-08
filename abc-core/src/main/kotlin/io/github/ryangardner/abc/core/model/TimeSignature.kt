package io.github.ryangardner.abc.core.model

/**
 * Represents a time signature (meter).
 *
 * @property numerator The number of beats per measure.
 * @property denominator The note value that represents one beat.
 * @property symbol Optional symbol representation (e.g., "C", "C|").
 */
data class TimeSignature @JvmOverloads constructor(
    val numerator: Int,
    val denominator: Int,
    val symbol: String? = null
)
