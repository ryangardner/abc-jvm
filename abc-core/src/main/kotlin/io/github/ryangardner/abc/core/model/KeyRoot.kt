package io.github.ryangardner.abc.core.model

/**
 * Represents the pitch class of a key's root (tonic).
 */
data class KeyRoot @JvmOverloads constructor(
    val step: NoteStep,
    val accidental: Accidental = Accidental.NATURAL
)
