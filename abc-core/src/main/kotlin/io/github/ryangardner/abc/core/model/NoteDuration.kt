package io.github.ryangardner.abc.core.model

/**
 * Represents a note duration.
 *
 * @property value The duration value (e.g., 1/8).
 */
data class NoteDuration(
    val numerator: Int,
    val denominator: Int
)
