package io.github.ryangardner.abc.parser

import io.github.ryangardner.abc.core.model.AbcTune
import io.github.ryangardner.abc.core.model.Accidental
import io.github.ryangardner.abc.core.model.BarLineElement
import io.github.ryangardner.abc.core.model.BarLineType
import io.github.ryangardner.abc.core.model.BodyHeaderElement
import io.github.ryangardner.abc.core.model.BrokenRhythmMarkerElement
import io.github.ryangardner.abc.core.model.ChordElement
import io.github.ryangardner.abc.core.model.DirectiveElement
import io.github.ryangardner.abc.core.model.GraceNoteElement
import io.github.ryangardner.abc.core.model.HeaderType
import io.github.ryangardner.abc.core.model.InlineFieldElement
import io.github.ryangardner.abc.core.model.KeyMode
import io.github.ryangardner.abc.core.model.LyricElement
import io.github.ryangardner.abc.core.model.MusicElement
import io.github.ryangardner.abc.core.model.DurationMultiplier
import io.github.ryangardner.abc.core.model.NoteDuration
import io.github.ryangardner.abc.core.model.NoteElement
import io.github.ryangardner.abc.core.model.OverlayElement
import io.github.ryangardner.abc.core.model.PartElement
import io.github.ryangardner.abc.core.model.Pitch
import io.github.ryangardner.abc.core.model.RestElement
import io.github.ryangardner.abc.core.model.SlurElement
import io.github.ryangardner.abc.core.model.SpacerElement
import io.github.ryangardner.abc.core.model.SymbolBar
import io.github.ryangardner.abc.core.model.SymbolChord
import io.github.ryangardner.abc.core.model.SymbolDecoration
import io.github.ryangardner.abc.core.model.SymbolLineElement
import io.github.ryangardner.abc.core.model.SymbolOther
import io.github.ryangardner.abc.core.model.SymbolSkip
import io.github.ryangardner.abc.core.model.TextBlockElement
import io.github.ryangardner.abc.core.model.TieType
import io.github.ryangardner.abc.core.model.TuneBody
import io.github.ryangardner.abc.core.model.TuneHeader
import io.github.ryangardner.abc.core.model.TuneMetadata
import io.github.ryangardner.abc.core.model.TupletElement
import io.github.ryangardner.abc.core.model.VariantElement

@Suppress("TooManyFunctions", "MaxLineLength", "MagicNumber", "CyclomaticComplexMethod")
public class AbcSerializer {
    private var currentDefaultLength: NoteDuration = NoteDuration(1, 8)

    public fun serialize(tune: AbcTune): String {
        currentDefaultLength = tune.header.length
        val serialized =
            buildString {
                // First, serialize the preamble if this is the first tune in a book
                // or if the preamble contains global headers/comments.
                tune.preamble.forEach { append(serializeElement(it)) }

                append(serializeHeader(tune.header))
                append(serializeMetadata(tune.metadata))
                append(serializeBody(tune.body))
            }
        return if (serialized.endsWith("\n")) serialized else "$serialized\n"
    }

    private fun serializeHeader(header: TuneHeader): String =
        buildString {
            append("X: ${header.reference}\n")
            header.headers.forEach { (k, v) ->
                if (k != "X" && k != "K" && k != "%%") {
                    append("$k: $v\n")
                } else if (k == "%%") {
                    append("%%$v\n")
                }
            }
            val modeStr =
                when (header.key.mode) {
                    KeyMode.IONIAN, KeyMode.MAJOR -> ""
                    else -> " ${header.key.mode.name.lowercase()}"
                }
            append("K: ${header.key.tonicName}$modeStr\n")
        }

    private fun serializeMetadata(metadata: TuneMetadata): String =
        buildString {
            metadata.visualTranspose?.let {
                append("%%visualTranspose $it\n")
            }
        }

    private fun serializeBody(body: TuneBody): String =
        buildString {
            body.elements.forEachIndexed { index, element ->
                val serializedElement = serializeElement(element)

                // Special handling for line-based elements to ensure they start on a new line
                val isLineBased =
                    element is BodyHeaderElement ||
                        element is DirectiveElement ||
                        element is LyricElement ||
                        element is SymbolLineElement ||
                        element is PartElement

                if (isLineBased) {
                    if (isNotEmpty() && !endsWith("\n")) {
                        append("\n")
                    }
                }

                append(serializedElement)

                // Check for line-break triggers
                val isManualLineBreak = element is SpacerElement && element.text == "!"

                if (isLineBased || isManualLineBreak) {
                    // Peek at NEXT element. If it is a Spacer starting with \n, we skip our own \n
                    // ALSO check if the serialized element itself already ended with a newline
                    if (!endsWith("\n")) {
                        val nextElement = body.elements.getOrNull(index + 1)
                        val nextIsNewlineSpacer = nextElement is SpacerElement && nextElement.text.startsWith("\n")
                        if (!nextIsNewlineSpacer) {
                            append("\n")
                        }
                    }
                }
            }
        }

    private fun serializeElement(element: MusicElement): String =
        when (element) {
            is NoteElement -> {
                serializeNote(element)
            }

            is ChordElement -> {
                serializeChord(element)
            }

            is BarLineElement -> {
                serializeBarLine(element)
            }

            is RestElement -> {
                serializeRest(element)
            }

            is SpacerElement -> {
                if (element.text == "!") "!" else element.text
            }

            is InlineFieldElement -> {
                serializeInlineField(element)
            }

            is DirectiveElement -> {
                "%%${element.content}\n"
            }

            is BodyHeaderElement -> {
                serializeBodyHeader(element)
            }

            is SlurElement -> {
                if (element.isStart) "(" else ")"
            }

            is TupletElement -> {
                "(${element.p}${if (element.q != null) ":${element.q}" else ""}${if (element.r != null) ":${element.r}" else ""}"
            }

            is GraceNoteElement -> {
                buildString {
                    append("{")
                    if (element.isAcciaccatura) append("/")
                    element.notes.forEach { append(serializeNote(it)) }
                    append("}")
                }
            }

            is PartElement -> {
                "P:${element.name}"
            }

            is SymbolLineElement -> {
                serializeSymbolLine(element)
            }

            is TextBlockElement -> {
                "%%begintext\n${element.content.joinToString("\n")}\n%%endtext\n"
            }

            is OverlayElement -> {
                "&"
            }

            is LyricElement -> {
                "w:${element.content}"
            }

            is VariantElement -> {
                "${element.prefix}${element.variants.joinToString(",")}"
            }

            is BrokenRhythmMarkerElement -> {
                element.type
            }

            else -> {
                ""
            }
        }

    private fun serializeInlineField(element: InlineFieldElement): String {
        if (element.fieldType == HeaderType.LENGTH) {
            val parts = element.value.split("/")
            if (parts.size == 2) {
                currentDefaultLength = NoteDuration(parts[0].toIntOrNull() ?: 1, parts[1].toIntOrNull() ?: 8)
            }
        }
        return "[${element.fieldType.key}:${element.value}]"
    }

    private fun serializeBodyHeader(element: BodyHeaderElement): String {
        when (element.key) {
            "L" -> {
                val parts = element.value.split("/")
                if (parts.size == 2) {
                    currentDefaultLength = NoteDuration(parts[0].toIntOrNull() ?: 1, parts[1].toIntOrNull() ?: 8)
                }
            }

            "M" -> {
                val parts = element.value.split("/")
                val ratio =
                    if (parts.size == 2) {
                        (parts[0].toDoubleOrNull() ?: 4.0) / (parts[1].toDoubleOrNull() ?: 4.0)
                    } else if (element.value == "C") {
                        1.0
                    } else if (element.value == "C|") {
                        1.0
                    } else {
                        1.0
                    }

                currentDefaultLength = if (ratio < 0.75) NoteDuration(1, 16) else NoteDuration(1, 8)
            }
        }
        return "${element.key}:${element.value}"
    }

    private fun serializeNote(note: NoteElement): String =
        buildString {
            note.decorations.forEach { deco ->
                if (deco.value != "line-break") {
                    when (deco.value) {
                        "~", "u", "v", ".", "H", "I", "J", "K", "L", "M", "N", "O", "P", "Q", "R", "S", "T", "U", "V", "W" -> {
                            append(
                                deco.value,
                            )
                        }

                        else -> {
                            append("!${deco.value}!")
                        }
                    }
                }
            }
            note.annotations.forEach { annotation ->
                append("\"$annotation\"")
            }
            append(serializePitch(note.pitch))
            append(serializeDurationMultiplier(note.durationMultiplier))
            if (note.ties == TieType.START) append("-")
        }

    private fun serializePitch(pitch: Pitch): String =
        buildString {
            when (pitch.accidental) {
                Accidental.SHARP -> {
                    append("^")
                }

                Accidental.FLAT -> {
                    append("_")
                }

                Accidental.NATURAL -> {
                    append("=")
                }

                Accidental.DOUBLE_SHARP -> {
                    append("^^")
                }

                Accidental.DOUBLE_FLAT -> {
                    append("__")
                }

                Accidental.QUARTER_SHARP -> {
                    append("^/")
                }

                Accidental.THREE_QUARTER_SHARP -> {
                    append("^^/")
                }

                Accidental.QUARTER_FLAT -> {
                    append("_/")
                }

                Accidental.THREE_QUARTER_FLAT -> {
                    append("__/")
                }

                null -> {}
            }
            val stepChar = pitch.step.name[0]
            if (pitch.octave >= 5) {
                append(stepChar.lowercaseChar())
                repeat(pitch.octave - 5) { append("'") }
            } else {
                append(stepChar.uppercaseChar())
                repeat(4 - pitch.octave) { append(",") }
            }
        }

    private fun serializeDurationMultiplier(multiplier: DurationMultiplier): String {
        return when {
            multiplier.numerator == 1 && multiplier.denominator == 1 -> "" // Default length
            multiplier.numerator == 1 && multiplier.denominator > 1 -> "/${multiplier.denominator}"
            multiplier.denominator == 1 -> "${multiplier.numerator}"
            else -> "${multiplier.numerator}/${multiplier.denominator}"
        }
    }

    private fun gcd(
        a: Int,
        b: Int,
    ): Int {
        var x = Math.abs(a)
        var y = Math.abs(b)
        while (y != 0) {
            val temp = y
            y = x % y
            x = temp
        }
        return x
    }

    private fun serializeChord(chord: ChordElement): String =
        buildString {
            chord.decorations.forEach { deco ->
                if (deco.value != "line-break") {
                    when (deco.value) {
                        "~", "u", "v", ".", "H", "I", "J", "K", "L", "M", "N", "O", "P", "Q", "R", "S", "T", "U", "V", "W" -> {
                            append(
                                deco.value,
                            )
                        }

                        else -> {
                            append("!${deco.value}!")
                        }
                    }
                }
            }
            chord.annotations.forEach { annotation ->
                append("\"$annotation\"")
            }
            append("[")
            chord.notes.forEach { append(serializeNote(it)) }
            append("]")
            if (chord.notes.isEmpty()) {
                append(serializeDurationMultiplier(chord.durationMultiplier))
            }
        }

    private fun serializeBarLine(bar: BarLineElement): String =
        buildString {
            val s =
                when (bar.type) {
                    BarLineType.SINGLE -> "|"
                    BarLineType.DOUBLE -> "||"
                    BarLineType.FINAL -> "|]"
                    BarLineType.REPEAT_START -> "|:"
                    BarLineType.REPEAT_END -> ":|"
                    BarLineType.REPEAT_BOTH -> "::"
                }
            append(s)
        }

    private fun serializeRest(rest: RestElement): String =
        buildString {
            rest.decorations.forEach { deco ->
                if (deco.value != "line-break") {
                    when (deco.value) {
                        "~", "u", "v", ".", "H", "I", "J", "K", "L", "M", "N", "O", "P", "Q", "R", "S", "T", "U", "V", "W" -> {
                            append(
                                deco.value,
                            )
                        }

                        else -> {
                            append("!${deco.value}!")
                        }
                    }
                }
            }
            rest.annotations.forEach { annotation ->
                append("\"$annotation\"")
            }
            append(if (rest.isInvisible) "x" else "z")
            append(serializeDurationMultiplier(rest.durationMultiplier))
        }

    private fun serializeSymbolLine(line: SymbolLineElement): String =
        buildString {
            append("s:")
            line.items.forEach { item ->
                append(" ")
                when (item) {
                    is SymbolChord -> append("\"${item.name}\"")
                    is SymbolDecoration -> append("!${item.name}!")
                    is SymbolSkip -> append("*")
                    is SymbolBar -> append("|")
                    is SymbolOther -> append(item.text)
                }
            }
            append("\n")
        }
}
