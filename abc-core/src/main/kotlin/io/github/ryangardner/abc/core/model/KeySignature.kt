package io.github.ryangardner.abc.core.model

/**
 * Represents a musical key signature.
 *
 * @property tonic The tonic note of the key.
 * @property mode The mode of the key (e.g., Major, Minor).
 * @property accidentals List of explicit accidentals.
 */
data class KeySignature @JvmOverloads constructor(
    val tonic: String, // Placeholder, will be Pitch later
    val mode: String = "Major", // Placeholder
    val accidentals: List<String> = emptyList() // Placeholder
)
