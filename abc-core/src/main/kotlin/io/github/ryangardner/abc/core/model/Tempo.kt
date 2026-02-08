package io.github.ryangardner.abc.core.model

/**
 * Represents the tempo of the tune.
 *
 * @property bpm Beats per minute.
 * @property beatUnit The note value that the beat refers to (optional).
 */
data class Tempo @JvmOverloads constructor(
    val bpm: Int,
    val beatUnit: NoteDuration? = null
)
