package io.github.ryangardner.abc.core.model

/**
 * Represents a musical key signature.
 */
public data class KeySignature @JvmOverloads constructor(
    public val root: KeyRoot,
    public val mode: KeyMode = KeyMode.IONIAN,
    public val extraAccidentals: List<Pitch> = emptyList()
) {
    /**
     * String representation of the tonic, e.g., "C", "F#", "Bb".
     */
    public val tonicName: String get() = buildString {
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
