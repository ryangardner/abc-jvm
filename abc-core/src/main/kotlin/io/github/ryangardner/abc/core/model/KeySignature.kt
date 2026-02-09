package io.github.ryangardner.abc.core.model

/**
 * Represents a musical key signature.
 */
data class KeySignature @JvmOverloads constructor(
    val root: KeyRoot,
    val mode: KeyMode = KeyMode.IONIAN,
    val extraAccidentals: List<Pitch> = emptyList()
) {
    /**
     * String representation of the tonic, e.g., "C", "F#", "Bb".
     */
    val tonicName: String get() = buildString {
        append(root.step.name)
        when (root.accidental) {
            Accidental.SHARP -> append("#")
            Accidental.FLAT -> append("b")
            Accidental.DOUBLE_SHARP -> append("##")
            Accidental.DOUBLE_FLAT -> append("bb")
            else -> {}
        }
    }
}