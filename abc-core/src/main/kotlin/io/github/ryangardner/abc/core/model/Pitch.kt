package io.github.ryangardner.abc.core.model

public data class Pitch @JvmOverloads constructor(
    public val step: NoteStep,
    public val octave: Int,
    public val accidental: Accidental? = null
) {
    public val midiNoteNumber: Int
        get() {
            val baseSemitones = when (step) {
                NoteStep.C -> 0
                NoteStep.D -> 2
                NoteStep.E -> 4
                NoteStep.F -> 5
                NoteStep.G -> 7
                NoteStep.A -> 9
                NoteStep.B -> 11
            }
            val accidentalSemitones = accidental?.semitones ?: 0
            return 12 * (octave + 1) + baseSemitones + accidentalSemitones
        }
}
