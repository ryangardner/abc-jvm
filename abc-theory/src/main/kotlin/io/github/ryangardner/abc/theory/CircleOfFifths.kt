package io.github.ryangardner.abc.theory

import io.github.ryangardner.abc.core.model.*

data class KeyCandidate(
    val tonicStep: NoteStep,
    val tonicAccidental: Int, // -1 for flat, 0 for natural, 1 for sharp, etc.
    val accidentalsCount: Int // Positive for sharps, negative for flats
) {
    val absoluteAccidentals: Int get() = kotlin.math.abs(accidentalsCount)
    
    val tonicName: String get() = buildString {
        append(tonicStep.name)
        when (tonicAccidental) {
            1 -> append("#")
            -1 -> append("b")
            2 -> append("##")
            -2 -> append("bb")
        }
    }
}

object CircleOfFifths {
    // Maps semitones (0-11) to candidate Major keys
    private val majorKeys = mapOf(
        0 to listOf(KeyCandidate(NoteStep.C, 0, 0)),
        1 to listOf(KeyCandidate(NoteStep.D, -1, -5), KeyCandidate(NoteStep.C, 1, 7)),
        2 to listOf(KeyCandidate(NoteStep.D, 0, 2)),
        3 to listOf(KeyCandidate(NoteStep.E, -1, -3), KeyCandidate(NoteStep.D, 1, 9)),
        4 to listOf(KeyCandidate(NoteStep.E, 0, 4)),
        5 to listOf(KeyCandidate(NoteStep.F, 0, -1)),
        6 to listOf(KeyCandidate(NoteStep.F, 1, 6), KeyCandidate(NoteStep.G, -1, -6)),
        7 to listOf(KeyCandidate(NoteStep.G, 0, 1)),
        8 to listOf(KeyCandidate(NoteStep.A, -1, -4), KeyCandidate(NoteStep.G, 1, 8)),
        9 to listOf(KeyCandidate(NoteStep.A, 0, 3)),
        10 to listOf(KeyCandidate(NoteStep.B, -1, -2), KeyCandidate(NoteStep.A, 1, 10)),
        11 to listOf(KeyCandidate(NoteStep.B, 0, 5), KeyCandidate(NoteStep.C, -1, -7))
    )

    fun getBestMajorKey(semitones: Int): KeyCandidate {
        val normalized = ((semitones % 12) + 12) % 12
        return majorKeys[normalized]?.minByOrNull { it.absoluteAccidentals } 
            ?: KeyCandidate(NoteStep.C, 0, 0)
    }

    /**
     * Gets the best key for a given semitone and mode.
     */
    fun getBestKey(tonicSemitones: Int, mode: KeyMode): KeyCandidate {
        val modeOffset = getModeOffsetInSemitones(mode)
        // Find the relative major tonic
        val relativeMajorSemitones = (tonicSemitones + modeOffset) % 12
        val bestMajor = getBestMajorKey(relativeMajorSemitones)
        
        // Now find the mode's tonic step by shifting from the major tonic step
        val stepOffset = getModeStepOffset(mode)
        val modeStepOrdinal = (bestMajor.tonicStep.ordinal + stepOffset) % 7
        val modeStep = NoteStep.values()[modeStepOrdinal]
        
        // Calculate accidental for this step to match tonicSemitones
        val baseSemitones = stepToSemitones(modeStep)
        var diff = (tonicSemitones - baseSemitones) % 12
        if (diff > 6) diff -= 12
        if (diff < -6) diff += 12
        
        return KeyCandidate(modeStep, diff, bestMajor.accidentalsCount)
    }

    private fun getModeOffsetInSemitones(mode: KeyMode): Int = when (mode) {
        KeyMode.MAJOR, KeyMode.IONIAN -> 0
        KeyMode.DORIAN -> 10 // or -2
        KeyMode.PHRYGIAN -> 8 // or -4
        KeyMode.LYDIAN -> 7 // or -5
        KeyMode.MIXOLYDIAN -> 5 // or -7
        KeyMode.MINOR, KeyMode.AEOLIAN -> 3
        KeyMode.LOCRIAN -> 1
    }

    private fun getModeStepOffset(mode: KeyMode): Int = when (mode) {
        KeyMode.MAJOR, KeyMode.IONIAN -> 0
        KeyMode.DORIAN -> 1
        KeyMode.PHRYGIAN -> 2
        KeyMode.LYDIAN -> 3
        KeyMode.MIXOLYDIAN -> 4
        KeyMode.MINOR, KeyMode.AEOLIAN -> 5
        KeyMode.LOCRIAN -> 6
    }

    fun getAccidentalForStep(step: NoteStep, k: Int): Int {
        val sharpOrder = listOf(NoteStep.F, NoteStep.C, NoteStep.G, NoteStep.D, NoteStep.A, NoteStep.E, NoteStep.B)
        val flatOrder = sharpOrder.reversed()
        
        return if (k > 0) {
            if (sharpOrder.take(k).contains(step)) 1 else 0
        } else if (k < 0) {
            if (flatOrder.take(-k).contains(step)) -1 else 0
        } else {
            0
        }
    }

    fun stepToSemitones(step: NoteStep): Int = when (step) {
        NoteStep.C -> 0
        NoteStep.D -> 2
        NoteStep.E -> 4
        NoteStep.F -> 5
        NoteStep.G -> 7
        NoteStep.A -> 9
        NoteStep.B -> 11
    }

    fun accidentalToSemitones(accidental: Accidental?): Int = when (accidental) {
        Accidental.SHARP -> 1
        Accidental.FLAT -> -1
        Accidental.DOUBLE_SHARP -> 2
        Accidental.DOUBLE_FLAT -> -2
        Accidental.NATURAL -> 0
        null -> 0
    }

    fun semitonesToAccidental(semitones: Int): Accidental? = when (semitones) {
        1 -> Accidental.SHARP
        -1 -> Accidental.FLAT
        2 -> Accidental.DOUBLE_SHARP
        -2 -> Accidental.DOUBLE_FLAT
        0 -> null
        else -> null
    }
}

/**
 * Extension property to get the absolute semitone value of a pitch within its octave.
 */
val Pitch.semitones: Int
    get() = CircleOfFifths.stepToSemitones(step) + CircleOfFifths.accidentalToSemitones(accidental)

/**
 * Extension property to get the total semitones including octave.
 */
val Pitch.totalSemitones: Int
    get() = semitones + octave * 12

/**
 * Extension property to get semitones of a KeySignature tonic.
 */
val KeySignature.tonicSemitones: Int
    get() = CircleOfFifths.stepToSemitones(root.step) + CircleOfFifths.accidentalToSemitones(root.accidental)
