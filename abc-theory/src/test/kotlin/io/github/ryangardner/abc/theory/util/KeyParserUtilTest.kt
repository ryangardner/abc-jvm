package io.github.ryangardner.abc.theory.util

import io.github.ryangardner.abc.core.model.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

class KeyParserUtilTest {

    @ParameterizedTest(name = "Parsing \"{0}\" should result in {1} {2} {3}")
    @MethodSource("keyTestCases")
    fun `test parse various key strings`(input: String, expectedRootStep: NoteStep, expectedAccidental: Accidental, expectedMode: KeyMode) {
        val result = KeyParserUtil.parse(input)
        assertEquals(expectedRootStep, result.root.step, "Wrong root step for \"$input\"")
        assertEquals(expectedAccidental, result.root.accidental, "Wrong accidental for \"$input\"")
        assertEquals(expectedMode, result.mode, "Wrong mode for \"$input\"")
    }

    companion object {
        @JvmStatic
        fun keyTestCases(): Stream<Arguments> = Stream.of(
            // Basic cases
            Arguments.of("", NoteStep.C, Accidental.NATURAL, KeyMode.IONIAN),
            Arguments.of(" ", NoteStep.C, Accidental.NATURAL, KeyMode.IONIAN),
            Arguments.of("% comment", NoteStep.C, Accidental.NATURAL, KeyMode.IONIAN),

            // Reserved clef names
            Arguments.of("treble", NoteStep.C, Accidental.NATURAL, KeyMode.IONIAN),
            Arguments.of("bass", NoteStep.C, Accidental.NATURAL, KeyMode.IONIAN),
            Arguments.of("alto", NoteStep.C, Accidental.NATURAL, KeyMode.IONIAN),
            Arguments.of("tenor", NoteStep.C, Accidental.NATURAL, KeyMode.IONIAN),

            // Highland Bagpipe
            Arguments.of("HP", NoteStep.D, Accidental.NATURAL, KeyMode.IONIAN),
            Arguments.of("Hp", NoteStep.D, Accidental.NATURAL, KeyMode.IONIAN),

            // Tonics and accidentals
            Arguments.of("C", NoteStep.C, Accidental.NATURAL, KeyMode.IONIAN),
            Arguments.of("D", NoteStep.D, Accidental.NATURAL, KeyMode.IONIAN),
            Arguments.of("E", NoteStep.E, Accidental.NATURAL, KeyMode.IONIAN),
            Arguments.of("F", NoteStep.F, Accidental.NATURAL, KeyMode.IONIAN),
            Arguments.of("G", NoteStep.G, Accidental.NATURAL, KeyMode.IONIAN),
            Arguments.of("A", NoteStep.A, Accidental.NATURAL, KeyMode.IONIAN),
            Arguments.of("B", NoteStep.B, Accidental.NATURAL, KeyMode.IONIAN),
            Arguments.of("C#", NoteStep.C, Accidental.SHARP, KeyMode.IONIAN),
            Arguments.of("C##", NoteStep.C, Accidental.DOUBLE_SHARP, KeyMode.IONIAN),
            Arguments.of("Cb", NoteStep.C, Accidental.FLAT, KeyMode.IONIAN),
            Arguments.of("Cbb", NoteStep.C, Accidental.DOUBLE_FLAT, KeyMode.IONIAN),
            Arguments.of("F#", NoteStep.F, Accidental.SHARP, KeyMode.IONIAN),
            Arguments.of("Bb", NoteStep.B, Accidental.FLAT, KeyMode.IONIAN),

            // Modes concatenated
            Arguments.of("Cm", NoteStep.C, Accidental.NATURAL, KeyMode.AEOLIAN),
            Arguments.of("Cmin", NoteStep.C, Accidental.NATURAL, KeyMode.AEOLIAN),
            Arguments.of("Cdor", NoteStep.C, Accidental.NATURAL, KeyMode.DORIAN),
            Arguments.of("Cphr", NoteStep.C, Accidental.NATURAL, KeyMode.PHRYGIAN),
            Arguments.of("Clyd", NoteStep.C, Accidental.NATURAL, KeyMode.LYDIAN),
            Arguments.of("Cmix", NoteStep.C, Accidental.NATURAL, KeyMode.MIXOLYDIAN),
            Arguments.of("Cloc", NoteStep.C, Accidental.NATURAL, KeyMode.LOCRIAN),
            Arguments.of("Cmaj", NoteStep.C, Accidental.NATURAL, KeyMode.IONIAN),
            Arguments.of("Cion", NoteStep.C, Accidental.NATURAL, KeyMode.IONIAN),

            // Modes separate
            Arguments.of("C minor", NoteStep.C, Accidental.NATURAL, KeyMode.AEOLIAN),
            Arguments.of("G mixolydian", NoteStep.G, Accidental.NATURAL, KeyMode.MIXOLYDIAN),
            Arguments.of("F# dorian", NoteStep.F, Accidental.SHARP, KeyMode.DORIAN),
            Arguments.of("Eb major", NoteStep.E, Accidental.FLAT, KeyMode.IONIAN),

            // Case sensitivity
            Arguments.of("am", NoteStep.A, Accidental.NATURAL, KeyMode.AEOLIAN),
            Arguments.of("G MIX", NoteStep.G, Accidental.NATURAL, KeyMode.MIXOLYDIAN),

            // Edge cases and extra info
            Arguments.of("G major clef=bass", NoteStep.G, Accidental.NATURAL, KeyMode.IONIAN),
            Arguments.of("C % with comment", NoteStep.C, Accidental.NATURAL, KeyMode.IONIAN),
            Arguments.of("X", NoteStep.C, Accidental.NATURAL, KeyMode.IONIAN), // Invalid defaults to C

            // Complex accidentals and modes
            Arguments.of("C#m", NoteStep.C, Accidental.SHARP, KeyMode.AEOLIAN),
            Arguments.of("Abmin", NoteStep.A, Accidental.FLAT, KeyMode.AEOLIAN),

            // Verify behavior for unrecognized strings (defaults to IONIAN)
            Arguments.of("Cmaj7", NoteStep.C, Accidental.NATURAL, KeyMode.IONIAN),
            Arguments.of("Cm7", NoteStep.C, Accidental.NATURAL, KeyMode.IONIAN)
        )
    }
}
