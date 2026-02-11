package io.github.ryangardner.abc.parser

import io.github.ryangardner.abc.core.model.*

public class AbcSerializer {
    private var currentDefaultLength: NoteDuration = NoteDuration(1, 8)

    public fun serialize(tune: AbcTune): String {
        currentDefaultLength = tune.header.length
        return buildString {
            append(serializeHeader(tune.header))
            append(serializeMetadata(tune.metadata))
            append(serializeBody(tune.body))
        }
    }

    private fun serializeHeader(header: TuneHeader): String = buildString {
        appendLine("X: ${header.reference}")
        header.title.forEach { appendLine("T: $it") }
        appendLine("M: ${header.meter.symbol ?: "${header.meter.numerator}/${header.meter.denominator}"}")
        appendLine("L: ${header.length.numerator}/${header.length.denominator}")
        header.tempo?.let { tempo ->
            val beatStr = tempo.beatUnit?.let { "${it.numerator}/${it.denominator}=" } ?: ""
            appendLine("Q: $beatStr${tempo.bpm}")
        }
        header.unknownHeaders.forEach { (k, v) ->
            appendLine("$k: $v")
        }
        val modeStr = when (header.key.mode) {
            KeyMode.IONIAN, KeyMode.MAJOR -> ""
            else -> " ${header.key.mode.name.lowercase()}"
        }
        appendLine("K: ${header.key.tonicName}$modeStr")
    }

    private fun serializeMetadata(metadata: TuneMetadata): String = buildString {
        metadata.visualTranspose?.let {
            appendLine("%%visualTranspose $it")
        }
    }

    private fun serializeBody(body: TuneBody): String = buildString {
        body.elements.forEach { element ->
            val serialized = serializeElement(element)
            if (serialized.isNotEmpty()) {
                // If it's a line-based element, ensure it starts on a new line
                if (element is BodyHeaderElement || element is DirectiveElement || element is LyricElement || element is SymbolLineElement) {
                    if (isNotEmpty() && !endsWith("\n")) {
                        append("\n")
                    }
                }
                
                // Append serialized content, but avoid creating a blank line (double newline)
                for (char in serialized) {
                   if (char == '\n') {
                       if (!endsWith("\n")) {
                           append(char)
                       }
                   } else {
                       append(char)
                   }
                }

                // If it's a line-based element, ensure it ends with a newline
                if (element is BodyHeaderElement || element is LyricElement || element is SymbolLineElement) {
                    // But don't add a double newline if the serialized content already ends with one
                    if (!endsWith("\n")) {
                        append("\n")
                    }
                }
            }
        }
    }

    private fun serializeElement(element: MusicElement): String = when (element) {
        is NoteElement -> serializeNote(element)
        is ChordElement -> serializeChord(element)
        is BarLineElement -> serializeBarLine(element)
        is RestElement -> serializeRest(element)
        is SpacerElement -> element.text
        is InlineFieldElement -> {
            if (element.fieldType == HeaderType.LENGTH) {
                val parts = element.value.split("/")
                if (parts.size == 2) {
                    currentDefaultLength = NoteDuration(parts[0].toIntOrNull() ?: 1, parts[1].toIntOrNull() ?: 8)
                }
            }
            "[${element.fieldType.key}:${element.value}]"
        }
        is DirectiveElement -> "%%${element.content}"
        is BodyHeaderElement -> {
            when (element.key) {
                "L" -> {
                    val parts = element.value.split("/")
                    if (parts.size == 2) {
                        currentDefaultLength = NoteDuration(parts[0].toIntOrNull() ?: 1, parts[1].toIntOrNull() ?: 8)
                    }
                }
                "M" -> {
                    // Meter change resets L if L wasn't explicitly pinned?
                    // According to ABC 2.1, M: changes L: if not specified.
                    // To be safe, we follow the same logic as the parser here.
                    val parts = element.value.split("/")
                    val ratio = if (parts.size == 2) {
                        (parts[0].toDoubleOrNull() ?: 4.0) / (parts[1].toDoubleOrNull() ?: 4.0)
                    } else if (element.value == "C") 1.0 else if (element.value == "C|") 1.0 else 1.0
                    
                    currentDefaultLength = if (ratio < 0.75) NoteDuration(1, 16) else NoteDuration(1, 8)
                }
            }
            "${element.key}:${element.value}"
        }
        is SlurElement -> if (element.isStart) "(" else ")"
        is TupletElement -> "(${element.p}${if (element.q != null) ":${element.q}" else ""}${if (element.r != null) ":${element.r}" else ""}"
        is GraceNoteElement -> buildString {
            append("{")
            if (element.isAcciaccatura) append("/")
            element.notes.forEach { append(serializeNote(it)) }
            append("}")
        }
        is SymbolLineElement -> serializeSymbolLine(element)
        is TextBlockElement -> "%%begintext\n${element.content.joinToString("\n")}\n%%endtext\n"
        is OverlayElement -> "&"
        is LyricElement -> "w:${element.content}"
        else -> ""
    }

    private fun serializeNote(note: NoteElement): String = buildString {
        note.decorations.forEach { append("!${it.value}!") }
        append(serializePitch(note.pitch))
        append(serializeDuration(note.length))
        if (note.ties == TieType.START) append("-")
    }

    private fun serializePitch(pitch: Pitch): String = buildString {
        when (pitch.accidental) {
            Accidental.SHARP -> append("^")
            Accidental.FLAT -> append("_")
            Accidental.NATURAL -> append("=")
            Accidental.DOUBLE_SHARP -> append("^^")
            Accidental.DOUBLE_FLAT -> append("__")
            Accidental.QUARTER_SHARP -> append("^/")
            Accidental.THREE_QUARTER_SHARP -> append("^^/")
            Accidental.QUARTER_FLAT -> append("_/")
            Accidental.THREE_QUARTER_FLAT -> append("__/")
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

    private fun serializeDuration(duration: NoteDuration): String {
        val num = duration.numerator * currentDefaultLength.denominator
        val den = duration.denominator * currentDefaultLength.numerator

        if (num == den) return ""

        val common = gcd(num, den)
        val sNum = num / common
        val sDen = den / common

        return when {
            sDen == 1 -> "$sNum"
            sNum == 1 -> "/$sDen"
            else -> "$sNum/$sDen"
        }
    }

    private fun gcd(a: Int, b: Int): Int {
        var x = a
        var y = b
        while (y != 0) {
            val temp = y
            y = x % y
            x = temp
        }
        return x
    }

    private fun serializeChord(chord: ChordElement): String = buildString {
        append("[")
        chord.notes.forEach { append(serializeNote(it)) }
        append("]")
        append(serializeDuration(chord.duration))
    }

    private fun serializeBarLine(bar: BarLineElement): String = when (bar.type) {
        BarLineType.SINGLE -> "|"
        BarLineType.DOUBLE -> "||"
        BarLineType.FINAL -> "|]"
        BarLineType.REPEAT_START -> "|:"
        BarLineType.REPEAT_END -> ":|"
        BarLineType.REPEAT_BOTH -> "::"
    }

    private fun serializeRest(rest: RestElement): String = buildString {
        append(if (rest.isInvisible) "x" else "z")
        append(serializeDuration(rest.duration))
    }

    private fun serializeSymbolLine(line: SymbolLineElement): String = buildString {
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