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
public data class TuneHeader @JvmOverloads constructor(
    public val reference: Int,
    public val title: List<String>,
    public val key: KeySignature,
    public val meter: TimeSignature,
    public val length: NoteDuration,
    public val tempo: Tempo? = null,
    public val headers: List<Pair<String, String>> = emptyList(),
    public val unknownHeaders: Map<String, String> = emptyMap(),
    public val version: String = "2.0",
    public val playingOrder: String? = null
)
