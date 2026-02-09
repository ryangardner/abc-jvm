package io.github.ryangardner.abc.parser

import io.github.ryangardner.abc.core.model.*

class BodyParser(
    private val lexer: AbcLexer,
    private val initialHeader: TuneHeader
) {
    private var currentDefaultLength: NoteDuration = initialHeader.length
    private var currentKey: KeySignature = initialHeader.key
    private var currentMeter: TimeSignature = initialHeader.meter

    fun parse(): TuneBody {
        val elements = mutableListOf<MusicElement>()

        var pendingDecorations = mutableListOf<Decoration>()
        var pendingAccidental: Accidental? = null

        while (lexer.hasNext()) {
            // Check for EOF first
            if (lexer.peekToken().type == TokenType.EOF) {
                lexer.next() // consume EOF
                break
            }

            val token = lexer.next()

            when (token.type) {
                TokenType.NOTE -> {
                    val note = parseNote(token, pendingAccidental, pendingDecorations)
                    elements.add(note)
                    pendingAccidental = null
                    pendingDecorations = mutableListOf()
                }
                TokenType.REST -> {
                    val rest = parseRest(token)
                    elements.add(rest)
                    pendingDecorations = mutableListOf()
                    pendingAccidental = null
                }
                TokenType.ACCIDENTAL -> {
                    pendingAccidental = parseAccidental(token.text)
                }
                TokenType.DECORATION -> {
                    pendingDecorations.add(Decoration(token.text))
                }
                TokenType.CHORD_START -> {
                    val chord = parseChord(pendingDecorations)
                    elements.add(chord)
                    pendingDecorations = mutableListOf()
                    pendingAccidental = null
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
                TokenType.TIE -> {
                     // Stray tie?
                }
                TokenType.DURATION -> {
                     // Stray duration?
                }
                else -> {
                    // Ignore whitespace, comments, newlines
                }
            }
        }

        return TuneBody(elements)
    }

    private fun parseNote(token: Token, accidental: Accidental?, decorations: List<Decoration>): NoteElement {
        val text = token.text
        // Text contains step + octaves (e.g. C,, or c')
        val stepChar = text.first { it.isLetter() }
        val step = when (stepChar.uppercase()) {
            "C" -> NoteStep.C
            "D" -> NoteStep.D
            "E" -> NoteStep.E
            "F" -> NoteStep.F
            "G" -> NoteStep.G
            "A" -> NoteStep.A
            "B" -> NoteStep.B
            else -> NoteStep.C
        }

        var octave = if (stepChar.isLowerCase()) 5 else 4
        val commas = text.count { it == ',' }
        val apostrophes = text.count { it == '\'' }
        octave -= commas
        octave += apostrophes

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
            } else {
                // Ignore other things inside chord?
            }
        }

        val duration = parseDuration()

        return ChordElement(notes, duration, null, decorations)
    }

    private fun parseDuration(): NoteDuration {
        if (!lexer.hasNext()) return currentDefaultLength

        val token = lexer.peekToken()
        if (token.type == TokenType.DURATION) {
            lexer.next() // consume
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
        return when (text) {
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

        if (text == "/") {
            num = 1
            den = 2
        } else if (text.startsWith("/")) {
            // /2
            num = 1
            den = text.substring(1).toIntOrNull() ?: 2
        } else if (text.contains("/")) {
            val parts = text.split("/")
            num = parts[0].toIntOrNull() ?: 1
            den = parts.getOrElse(1) { "" }.toIntOrNull() ?: 2
        } else {
            num = text.toIntOrNull() ?: 1
            den = 1
        }

        val finalNum = num * currentDefaultLength.numerator
        val finalDen = den * currentDefaultLength.denominator

        return NoteDuration(finalNum, finalDen)
    }

    private fun parseInlineField(elements: MutableList<MusicElement>) {
         // Start token [ was consumed in loop, but here I modified sig to NOT take startToken.
         // Wait, the loop: TokenType.INLINE_FIELD_START -> parseInlineField(elements)
         // But in loop I check token type.
         // The token IS INLINE_FIELD_START.
         // But parseInlineField logic assumed we are AT start.
         // Loop consumed START token.
         // So parseInlineField should start expecting KEY.

         // In loop: val token = lexer.next() (which is START)

         if (lexer.hasNext()) {
             val keyToken = lexer.next()
             if (keyToken.type == TokenType.INLINE_FIELD_KEY) {
                 var valueText = ""
                 if (lexer.hasNext()) {
                     val valToken = lexer.next()
                     if (valToken.type == TokenType.INLINE_FIELD_VALUE) {
                         valueText = valToken.text
                         if (lexer.hasNext()) lexer.next() // Consume ]
                     } else if (valToken.type == TokenType.INLINE_FIELD_END) {
                         // Empty value?
                     }
                 }

                 // Update state
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
        } else {
            NoteDuration(1, 8)
        }
    }

    private fun parseMeter(text: String): TimeSignature {
        return when (text) {
            "C" -> TimeSignature(4, 4, "C")
            "C|" -> TimeSignature(2, 2, "C|")
            else -> {
                val parts = text.split("/")
                if (parts.size == 2) {
                    TimeSignature(parts[0].toIntOrNull() ?: 4, parts[1].toIntOrNull() ?: 4)
                } else {
                    TimeSignature(4, 4)
                }
            }
        }
    }

    private fun parseKey(text: String): KeySignature {
        return io.github.ryangardner.abc.parser.util.KeyParserUtil.parse(text)
    }
}
