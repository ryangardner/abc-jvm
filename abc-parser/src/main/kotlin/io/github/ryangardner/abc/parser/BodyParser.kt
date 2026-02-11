package io.github.ryangardner.abc.parser

import io.github.ryangardner.abc.core.model.*
import io.github.ryangardner.abc.theory.util.KeyParserUtil

public class BodyParser(
    private val lexer: AbcLexer,
    private val initialHeader: TuneHeader
) {
    private var currentDefaultLength: NoteDuration = initialHeader.length
    private var currentKey: KeySignature = initialHeader.key
    private var currentMeter: TimeSignature = initialHeader.meter

    private var lastNoteStep: NoteStep? = null
    private var lastNoteOctave: Int? = null

    public fun parse(): TuneBody {
        val elements = mutableListOf<MusicElement>()

        var pendingDecorations = mutableListOf<Decoration>()
        var pendingAccidental: Accidental? = null
        var pendingBrokenRhythmMultiplier: Double? = null

        while (lexer.hasNext()) {
            val nextToken = lexer.peekToken()
            if (nextToken.type == TokenType.EOF) break
            if (nextToken.type == TokenType.HEADER_KEY && nextToken.text == "X") break

            val token = lexer.next()

            when (token.type) {
                TokenType.NOTE -> {
                    var note = parseNote(token, pendingAccidental, pendingDecorations)
                    if (pendingBrokenRhythmMultiplier != null) {
                        note = note.copy(length = note.length.scale(pendingBrokenRhythmMultiplier!!))
                        pendingBrokenRhythmMultiplier = null
                    }
                    elements.add(note)
                    pendingAccidental = null
                    pendingDecorations = mutableListOf()
                }
                TokenType.REST -> {
                    var rest = parseRest(token)
                    if (pendingBrokenRhythmMultiplier != null) {
                        rest = rest.copy(duration = rest.duration.scale(pendingBrokenRhythmMultiplier!!))
                        pendingBrokenRhythmMultiplier = null
                    }
                    elements.add(rest)
                    pendingDecorations = mutableListOf()
                    pendingAccidental = null
                }
                TokenType.CHORD_START -> {
                    var chord = parseChord(pendingDecorations)
                    if (pendingBrokenRhythmMultiplier != null) {
                        chord = chord.copy(duration = chord.duration.scale(pendingBrokenRhythmMultiplier!!))
                        pendingBrokenRhythmMultiplier = null
                    }
                    elements.add(chord)
                    pendingDecorations = mutableListOf()
                    pendingAccidental = null
                }
                TokenType.BROKEN_RHYTHM -> {
                    val lastElement = elements.findLast { it is NoteElement || it is ChordElement || it is RestElement }
                    if (lastElement != null) {
                        val dots = token.text.length
                        val multiplier = if (token.text.startsWith(">")) {
                            (Math.pow(2.0, dots.toDouble() + 1) - 1) / Math.pow(2.0, dots.toDouble())
                        } else {
                            1.0 / Math.pow(2.0, dots.toDouble())
                        }
                        
                        val nextMultiplier = 2.0 - multiplier
                        
                        val lastIdx = elements.lastIndexOf(lastElement)
                        elements[lastIdx] = when (lastElement) {
                            is NoteElement -> lastElement.copy(length = lastElement.length.scale(multiplier))
                            is RestElement -> lastElement.copy(duration = lastElement.duration.scale(multiplier))
                            is ChordElement -> lastElement.copy(duration = lastElement.duration.scale(multiplier))
                            else -> lastElement
                        }
                        
                        pendingBrokenRhythmMultiplier = nextMultiplier
                    }
                }
                TokenType.ACCIDENTAL -> {
                    pendingAccidental = parseAccidental(token.text)
                }
                TokenType.DECORATION -> {
                    pendingDecorations.add(Decoration(token.text))
                }
                TokenType.BAR_LINE -> {
                    elements.add(BarLineElement(parseBarLineType(token.text)))
                }
                TokenType.INLINE_FIELD_START -> {
                    parseInlineField(elements)
                }
                TokenType.DIRECTIVE -> {
                    elements.add(DirectiveElement(token.text))
                }
                TokenType.SLUR_START -> {
                    elements.add(SlurElement(true))
                }
                TokenType.SLUR_END -> {
                    elements.add(SlurElement(false))
                }
                TokenType.GRACE_START -> {
                    elements.add(parseGraceNotes())
                }
                TokenType.TUPLET -> {
                    val tupletText = token.text.substring(1) // skip (
                    val parts = tupletText.split(":")
                    val p = parts[0].toIntOrNull() ?: 3
                    val q = parts.getOrNull(1)?.toIntOrNull()
                    val r = parts.getOrNull(2)?.toIntOrNull()
                    elements.add(TupletElement(p, q, r))
                }
                TokenType.HEADER_KEY -> {
                    val key = token.text
                    if (lexer.hasNext()) {
                        val valToken = lexer.next()
                        if (valToken.type == TokenType.HEADER_VALUE) {
                            elements.add(BodyHeaderElement(key, valToken.text.trim()))
                            if (key == "L") {
                                currentDefaultLength = parseLength(valToken.text.trim())
                            }
                        }
                    }
                }
                TokenType.WHITESPACE, TokenType.NEWLINE, TokenType.UNKNOWN -> {
                    elements.add(SpacerElement(token.text))
                }
                else -> {}
            }
        }

        return TuneBody(elements)
    }

    private fun parseNote(token: Token, accidental: Accidental?, decorations: List<Decoration>): NoteElement {
        val text = token.text
        val stepChar = text.firstOrNull { it.isLetter() }
        
        val step: NoteStep
        var octave: Int

        if (stepChar != null) {
            step = when (stepChar.uppercase()) {
                "C" -> NoteStep.C
                "D" -> NoteStep.D
                "E" -> NoteStep.E
                "F" -> NoteStep.F
                "G" -> NoteStep.G
                "A" -> NoteStep.A
                "B" -> NoteStep.B
                else -> NoteStep.C
            }
            octave = if (stepChar.isLowerCase()) 5 else 4
            
            val commas = text.count { it == ',' }
            val apostrophes = text.count { it == '\'' }
            octave -= commas
            octave += apostrophes
            
            lastNoteStep = step
            lastNoteOctave = octave
        } else {
            step = lastNoteStep ?: NoteStep.C
            octave = lastNoteOctave ?: 4
            
            val commas = text.count { it == ',' }
            val apostrophes = text.count { it == '\'' }
            octave -= commas
            octave += apostrophes
            
            lastNoteOctave = octave
        }

        val duration = parseDuration()
        val tie = parseTie()

        return NoteElement(
            pitch = Pitch(step, octave, accidental),
            length = duration,
            ties = tie,
            decorations = decorations,
            accidental = accidental
        )
    }

    private fun parseRest(token: Token): RestElement {
        val duration = parseDuration()
        return RestElement(duration, token.text.equals("x", ignoreCase = true))
    }

    private fun parseGraceNotes(): GraceNoteElement {
        val notes = mutableListOf<NoteElement>()
        var isAcciaccatura = false
        var pendingAccidental: Accidental? = null

        if (lexer.hasNext() && lexer.peekToken().text == "/") {
            isAcciaccatura = true
            lexer.next()
        }

        while (lexer.hasNext()) {
            val token = lexer.peekToken()
            if (token.type == TokenType.GRACE_END) {
                lexer.next() // consume }
                break
            }

            val t = lexer.next()
            when (t.type) {
                TokenType.NOTE -> {
                    notes.add(parseNote(t, pendingAccidental, emptyList()))
                    pendingAccidental = null
                }
                TokenType.ACCIDENTAL -> {
                    pendingAccidental = parseAccidental(t.text)
                }
                else -> { /* ignore other things in grace notes for now */ }
            }
        }

        return GraceNoteElement(notes, isAcciaccatura)
    }

    private fun parseChord(decorations: List<Decoration>): ChordElement {
        val notes = mutableListOf<NoteElement>()
        var pendingAccidental: Accidental? = null

        while (lexer.hasNext()) {
            val token = lexer.peekToken()
            if (token.type == TokenType.CHORD_END) {
                lexer.next() // consume ]
                break
            }

            val t = lexer.next()
            if (t.type == TokenType.NOTE) {
                notes.add(parseNote(t, pendingAccidental, emptyList()))
                pendingAccidental = null
            } else if (t.type == TokenType.ACCIDENTAL) {
                pendingAccidental = parseAccidental(t.text)
            }
        }

        val duration = parseDuration()
        return ChordElement(notes, duration, null, decorations)
    }

    private fun parseDuration(): NoteDuration {
        if (!lexer.hasNext()) return currentDefaultLength
        val token = lexer.peekToken()
        if (token.type == TokenType.DURATION) {
            lexer.next()
            return calculateDuration(token.text)
        }
        return currentDefaultLength
    }

    private fun parseTie(): TieType {
        if (!lexer.hasNext()) return TieType.NONE
        val token = lexer.peekToken()
        if (token.type == TokenType.TIE) {
            lexer.next()
            return TieType.START
        }
        return TieType.NONE
    }

    private fun parseAccidental(text: String): Accidental {
        return when (text.trim()) {
            "^" -> Accidental.SHARP
            "^^" -> Accidental.DOUBLE_SHARP
            "_" -> Accidental.FLAT
            "__" -> Accidental.DOUBLE_FLAT
            "=" -> Accidental.NATURAL
            else -> Accidental.NATURAL
        }
    }

    private fun parseBarLineType(text: String): BarLineType {
        return when (text) {
            "|]" -> BarLineType.FINAL
            "||" -> BarLineType.DOUBLE
            "|:" -> BarLineType.REPEAT_START
            ":|" -> BarLineType.REPEAT_END
            ":|:" -> BarLineType.REPEAT_BOTH
            else -> BarLineType.SINGLE
        }
    }

    private fun calculateDuration(text: String): NoteDuration {
        val num: Int
        val den: Int

        val slashCount = text.count { it == '/' }
        if (slashCount > 0) {
            val parts = text.split("/")
            num = if (parts[0].isEmpty()) 1 else parts[0].toIntOrNull() ?: 1
            val explicitDen = parts.getOrNull(1)?.toIntOrNull()
            den = if (explicitDen != null) {
                explicitDen * Math.pow(2.0, (slashCount - 1).toDouble()).toInt()
            } else {
                Math.pow(2.0, slashCount.toDouble()).toInt()
            }
        } else {
            num = text.toIntOrNull() ?: 1
            den = 1
        }

        // The duration text multiplies the current default length (L:)
        val finalNum = num * currentDefaultLength.numerator
        val finalDen = den * currentDefaultLength.denominator

        return NoteDuration.simplify(finalNum, finalDen)
    }

    private fun parseInlineField(elements: MutableList<MusicElement>) {
        if (lexer.hasNext()) {
            val keyToken = lexer.next()
            if (keyToken.type == TokenType.INLINE_FIELD_KEY) {
                var valueText = ""
                if (lexer.hasNext()) {
                    val valToken = lexer.next()
                    if (valToken.type == TokenType.INLINE_FIELD_VALUE) {
                        valueText = valToken.text
                        if (lexer.hasNext()) lexer.next() // Consume ]
                    }
                }
                when (keyToken.text) {
                    "L" -> currentDefaultLength = parseLength(valueText)
                    "K" -> currentKey = parseKey(valueText)
                    "M" -> currentMeter = parseMeter(valueText)
                }
                val fieldType = when (keyToken.text) {
                    "K" -> HeaderType.KEY
                    "L" -> HeaderType.LENGTH
                    "M" -> HeaderType.METER
                    "Q" -> HeaderType.TEMPO
                    "T" -> HeaderType.TITLE
                    "V" -> HeaderType.VOICE
                    else -> HeaderType.UNKNOWN
                }
                elements.add(InlineFieldElement(fieldType, valueText))
            }
        }
    }

    private fun parseLength(text: String): NoteDuration {
        val parts = text.split("/")
        return if (parts.size == 2) {
            NoteDuration(parts[0].toIntOrNull() ?: 1, parts[1].toIntOrNull() ?: 8)
        } else NoteDuration(1, 8)
    }

    private fun parseMeter(text: String): TimeSignature {
        return when (text) {
            "C" -> TimeSignature(4, 4, "C")
            "C|" -> TimeSignature(2, 2, "C|")
            else -> {
                val parts = text.split("/")
                if (parts.size == 2) {
                    TimeSignature(parts[0].toIntOrNull() ?: 4, parts[1].toIntOrNull() ?: 4)
                } else TimeSignature(4, 4)
            }
        }
    }

    private fun parseKey(text: String): KeySignature {
        return KeyParserUtil.parse(text)
    }
}