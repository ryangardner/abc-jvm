package io.github.ryangardner.abc.parser.util

import io.github.ryangardner.abc.core.model.*

internal object KeyParserUtil {

    fun parse(keyText: String): KeySignature {
        val parts = keyText.trim().split("\\s+".toRegex())
        var tonicPart = parts.getOrElse(0) { "C" }
        var modePart = if (parts.size > 1) parts[1] else null

        // Handle shorthand like "Dm"
        if (modePart == null && tonicPart.length > 1 && tonicPart.endsWith("m") && !tonicPart.endsWith("bm")) {
             modePart = "minor"
             tonicPart = tonicPart.substring(0, tonicPart.length - 1)
        }

        val step = when (tonicPart[0].uppercaseChar()) {
            'C' -> NoteStep.C
            'D' -> NoteStep.D
            'E' -> NoteStep.E
            'F' -> NoteStep.F
            'G' -> NoteStep.G
            'A' -> NoteStep.A
            'B' -> NoteStep.B
            else -> NoteStep.C
        }

        val accidental = if (tonicPart.length > 1) {
            when (tonicPart.substring(1)) {
                "#" -> Accidental.SHARP
                "b" -> Accidental.FLAT
                "##" -> Accidental.DOUBLE_SHARP
                "bb" -> Accidental.DOUBLE_FLAT
                else -> Accidental.NATURAL
            }
        } else Accidental.NATURAL

        val mode = KeyMode.fromString(modePart)

        return KeySignature(KeyRoot(step, accidental), mode)
    }
}
