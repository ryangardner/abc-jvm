package io.github.ryangardner.abc.core.model

/**
 * Represents the tempo of the tune.
 *
 * @property bpm Beats per minute.
 * @property beatUnit The note value that the beat refers to (optional).
 */
public data class Tempo @JvmOverloads constructor(
    public val bpm: Int,
    public val beatUnit: NoteDuration? = null
)
