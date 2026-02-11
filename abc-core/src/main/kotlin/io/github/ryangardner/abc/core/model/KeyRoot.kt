package io.github.ryangardner.abc.core.model

/**
 * Represents the pitch class of a key's root (tonic).
 */
public data class KeyRoot @JvmOverloads constructor(
    public val step: NoteStep,
    public val accidental: Accidental = Accidental.NATURAL
)
