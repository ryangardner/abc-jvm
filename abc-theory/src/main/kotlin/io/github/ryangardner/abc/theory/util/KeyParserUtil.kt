package io.github.ryangardner.abc.theory.util

import io.github.ryangardner.abc.core.model.*

public object KeyParserUtil {

    private val RECOGNIZED_MODES = setOf(
        "m", "min", "minor", "aeolian",
        "maj", "major", "ion", "ionian",
        "dor", "dorian",
        "phr", "phrygian",
        "lyd", "lydian",
        "mix", "mixolydian",
        "loc", "locrian"
    )

    private val RESERVED_CLEF_NAMES = setOf(
        "treble", "bass", "alto", "tenor", "perc", "none", "mezzosoprano", "soprano", "baritone", "subbass"
    )

    private val WHITESPACE_REGEX = "\\s+".toRegex()

    public fun parse(keyText: String): KeySignature {
        val trimmed = keyText.substringBefore("%").trim()
        if (trimmed.isEmpty()) {
            return KeySignature(KeyRoot(NoteStep.C, Accidental.NATURAL), KeyMode.IONIAN)
        }
        val parts = trimmed.split(WHITESPACE_REGEX)
        var firstWord = parts[0]
        
        // If the first word is a known clef name, the key is implicitly C Major
        val lowerFirst = firstWord.lowercase()
        if (RESERVED_CLEF_NAMES.any { lowerFirst.startsWith(it) }) {
             return KeySignature(KeyRoot(NoteStep.C, Accidental.NATURAL), KeyMode.IONIAN)
        }

        if (firstWord.isEmpty()) {
             return KeySignature(KeyRoot(NoteStep.C, Accidental.NATURAL), KeyMode.IONIAN)
        }

        // 0. Handle Highland Bagpipe special keys
        if (firstWord == "HP" || firstWord == "Hp") {
            // HP/Hp implies a specific set of sharps (F#, C#) and Gn. 
            // In MIDI terms, this is typically represented as D Major (F#, C#) or A Mixolydian.
            // Many implementations use D Major as the baseline for HP.
            return KeySignature(KeyRoot(NoteStep.D, Accidental.NATURAL), KeyMode.IONIAN)
        }

        // 1. Identify tonic step
        val step = when (firstWord[0].uppercaseChar()) {
            'C' -> NoteStep.C
            'D' -> NoteStep.D
            'E' -> NoteStep.E
            'F' -> NoteStep.F
            'G' -> NoteStep.G
            'A' -> NoteStep.A
            'B' -> NoteStep.B
            else -> NoteStep.C
        }

        // 2. Identify tonic accidental
        var accidental = Accidental.NATURAL
        var offset = 1
        if (firstWord.length > 1) {
            if (firstWord[1] == '#') {
                accidental = Accidental.SHARP
                offset = 2
                if (firstWord.length > 2 && firstWord[2] == '#') {
                    accidental = Accidental.DOUBLE_SHARP
                    offset = 3
                }
            } else if (firstWord[1] == 'b') {
                accidental = Accidental.FLAT
                offset = 2
                if (firstWord.length > 2 && firstWord[2] == 'b') {
                    accidental = Accidental.DOUBLE_FLAT
                    offset = 3
                }
            }
        }

        // 3. Identify mode
        val remainder = if (firstWord.length > offset) firstWord.substring(offset) else null
        val secondWord = if (parts.size > 1) parts[1] else null
        
        val modeStr = when {
            remainder != null && isRecognized(remainder) -> remainder
            secondWord != null && isRecognized(secondWord) -> secondWord
            // If neither is "recognized", but remainder exists, it might be the mode (e.g. "m")
            remainder != null -> remainder
            else -> secondWord
        }

        return KeySignature(KeyRoot(step, accidental), KeyMode.fromString(modeStr))
    }

    private fun isRecognized(s: String): Boolean {
        val mode = s.lowercase().takeWhile { it.isLetter() }
        return RECOGNIZED_MODES.contains(mode)
    }
}
