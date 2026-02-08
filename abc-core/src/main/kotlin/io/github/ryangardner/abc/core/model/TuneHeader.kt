package io.github.ryangardner.abc.core.model

/**
 * Represents the header of an ABC tune.
 *
 * @property reference Unique index of the tune (X:).
 * @property title List of titles (T:).
 * @property key Key signature (K:).
 * @property meter Time signature (M:).
 * @property length Default note length (L:).
 * @property tempo Tempo (Q:).
 */
data class TuneHeader @JvmOverloads constructor(
    val reference: Int,
    val title: List<String>,
    val key: KeySignature,
    val meter: TimeSignature,
    val length: NoteDuration,
    val tempo: Tempo? = null
)
