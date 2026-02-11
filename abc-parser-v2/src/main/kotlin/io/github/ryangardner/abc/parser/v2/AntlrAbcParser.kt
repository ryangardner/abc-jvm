package io.github.ryangardner.abc.parser.v2

import io.github.ryangardner.abc.antlr.ABCLexer
import io.github.ryangardner.abc.antlr.ABCParser
import io.github.ryangardner.abc.antlr.ABCParserBaseVisitor
import io.github.ryangardner.abc.core.model.*
import io.github.ryangardner.abc.theory.util.KeyParserUtil
import org.antlr.v4.runtime.*
import org.antlr.v4.runtime.misc.ParseCancellationException
import org.antlr.v4.runtime.tree.TerminalNode
import org.slf4j.Logger
import org.slf4j.LoggerFactory

private val logger: Logger = LoggerFactory.getLogger(AntlrAbcParser::class.java)

public class AntlrAbcParser {

    public fun parse(input: String): AbcTune {
        return parseBook(input).firstOrNull() ?: throw IllegalArgumentException("No tunes found in input")
    }

    public fun parseBook(input: String): List<AbcTune> {
        val lexer = ABCLexer(CharStreams.fromString(input))
        val tokens = CommonTokenStream(lexer)
        val parser = ABCParser(tokens)
        
        val errorListener = object : BaseErrorListener() {
            override fun syntaxError(recognizer: Recognizer<*, *>?, offendingSymbol: Any?, line: Int, charPositionInLine: Int, msg: String?, e: RecognitionException?) {
                throw ParseCancellationException("line $line:$charPositionInLine $msg")
            }
        }
        lexer.removeErrorListeners()
        lexer.addErrorListener(errorListener)
        parser.removeErrorListeners()
        parser.addErrorListener(errorListener)

        val tunebookContext = parser.tunebook()
        val visitor = AbcTunebookVisitor()
        return visitor.visitTunebook(tunebookContext)
    }
}

private class AbcTunebookVisitor : ABCParserBaseVisitor<List<AbcTune>>() {
    override fun visitTunebook(ctx: ABCParser.TunebookContext): List<AbcTune> {
        return ctx.tune().map { buildTune(it) }
    }

    override fun visitTune(ctx: ABCParser.TuneContext): List<AbcTune> {
        return listOf(buildTune(ctx))
    }

    private fun buildTune(ctx: ABCParser.TuneContext): AbcTune {
        val header = buildTuneHeader(ctx.tune_header())
        
        val bodyVisitor = AbcTuneBodyVisitor(header)
        ctx.tune_body().accept(bodyVisitor)
        
        return AbcTune(
            header = header,
            body = TuneBody(bodyVisitor.elements),
            metadata = TuneMetadata()
        )
    }

    private fun buildTuneHeader(ctx: ABCParser.Tune_headerContext): TuneHeader {
        val xRef = ctx.x_ref().children
            .filter { it is TerminalNode && (it.symbol.type == ABCLexer.FIELD_CONTENT || it.symbol.type == ABCLexer.FIELD_BACKSLASH) }
            .joinToString("") { it.text }.trim().toIntOrNull() ?: 1
        val titles = mutableListOf<String>()
        var key: KeySignature? = null
        var meter: TimeSignature? = null
        var length: NoteDuration? = null
        var playingOrder: String? = null
        val unknownHeaders = mutableMapOf<String, String>()
        val allHeaders = mutableListOf<Pair<String, String>>()
        var version = "2.0"
        ctx.children.forEach { child ->
            when (child) {
                is ABCParser.FieldContext -> {
                    if (child.FIELD_ID() != null) {
                        val id = child.FIELD_ID().text.removeSuffix(":")
                        val value = child.children
                            .filter { it is TerminalNode && (it.symbol.type == ABCLexer.FIELD_CONTENT || it.symbol.type == ABCLexer.FIELD_BACKSLASH) }
                            .joinToString("") { it.text }.trim()
                        allHeaders.add(id to value)
                        when (id) {
                            "T" -> titles.add(value)
                            "M" -> meter = parseMeter(value)
                            "L" -> length = parseLength(value)
                            "P" -> playingOrder = value
                            else -> unknownHeaders[id] = value
                        }
                    }
                }
                is TerminalNode -> {
                    if (child.symbol.type == ABCLexer.STYLESHEET) {
                        val content = child.text.trim().removePrefix("%%")
                        allHeaders.add("%%" to content)
                        if (content.startsWith("abc-version", ignoreCase = true)) {
                            version = content.substringAfter("abc-version").trim()
                        }
                    }
                }
            }
        }

        val actualMeter = meter ?: TimeSignature(4, 4)
        val actualLength = length ?: run {
            val ratio = actualMeter.numerator.toDouble() / actualMeter.denominator.toDouble()
            if (ratio < 0.75) NoteDuration(1, 16) else NoteDuration(1, 8)
        }

        val keyValue = ctx.key_field().children
            .filter { it is TerminalNode && (it.symbol.type == ABCLexer.FIELD_CONTENT || it.symbol.type == ABCLexer.FIELD_BACKSLASH) }
            .joinToString("") { it.text }.trim()
        allHeaders.add("K" to keyValue)
        key = KeyParserUtil.parse(keyValue)

        return TuneHeader(
            reference = xRef,
            title = titles.ifEmpty { listOf("Unknown") },
            key = key,
            meter = actualMeter,
            length = actualLength,
            headers = allHeaders,
            unknownHeaders = unknownHeaders,
            version = version,
            playingOrder = playingOrder
        )
    }
}

private fun parseMeter(text: String): TimeSignature {
    val cleanText = text.substringBefore("%").trim()
    return when (cleanText) {
        "C" -> TimeSignature(4, 4, "C")
        "C|" -> TimeSignature(2, 2, "C|")
        "none" -> TimeSignature(4, 4) // Common in some ABCs
        else -> {
            val parts = cleanText.split("/")
            if (parts.size >= 2) {
                TimeSignature(parts[0].trim().toIntOrNull() ?: 4, parts[1].trim().toIntOrNull() ?: 4)
            } else TimeSignature(4, 4)
        }
    }
}

private fun parseLength(text: String): NoteDuration {
    val cleanText = text.substringBefore("%").trim()
    val parts = cleanText.split("/")
    return if (parts.size == 2) {
        NoteDuration(parts[0].trim().toIntOrNull() ?: 1, parts[1].trim().toIntOrNull() ?: 8)
    } else NoteDuration(1, 8)
}

    private class AbcTuneBodyVisitor(val header: TuneHeader) : ABCParserBaseVisitor<Unit>() {
    val elements = mutableListOf<MusicElement>()
    private var currentDefaultLength = header.length
    private var currentMeter = header.meter
    private val isStrict: Boolean = try {
        val vNum = header.version.toDouble()
        vNum >= 2.1
    } catch (e: Exception) {
        false
    }
    private var lastNoteStep: NoteStep? = null
    private var lastNoteOctave: Int? = null
    private var pendingAnnotation: String? = null
    private val pendingDecorations = mutableListOf<Decoration>()
    private var pendingBrokenRhythmMultiplier: Double? = null

    override fun visitMeasure(ctx: ABCParser.MeasureContext): Unit {
        for (i in 0 until ctx.childCount) {
             val child = ctx.getChild(i)
             if (child is ABCParser.ElementContext) {
                 visitElement(child)
             } else if (child is ABCParser.VariantContext) {
                 visitVariant(child)
             }
        }
        ctx.barline()?.let { visitBarline(it) }
    }

    override fun visitMusic_line(ctx: ABCParser.Music_lineContext): Unit {
        super.visitMusic_line(ctx)
        if (ctx.EOL_MUSIC() != null || ctx.NEWLINE() != null) {
            elements.add(SpacerElement("\n"))
        }
    }

    override fun visitField_in_body(ctx: ABCParser.Field_in_bodyContext): Unit {
        if (ctx.FIELD_ID() != null || ctx.KEY_FIELD() != null) {
            val id = if (ctx.FIELD_ID() != null) ctx.FIELD_ID().text.removeSuffix(":") else "K"
            val value = ctx.children
                .filter { it is TerminalNode && (it.symbol.type == ABCLexer.FIELD_CONTENT || it.symbol.type == ABCLexer.FIELD_BACKSLASH) }
                .joinToString("") { it.text }.trim()
            
            when (id) {
                "L" -> currentDefaultLength = parseLength(value)
                "M" -> {
                    val newMeter = parseMeter(value)
                    currentMeter = newMeter
                    // Per ABC 2.1 spec (4.4), M: only sets the default L: when in the header.
                    // If M: appears in the body, it does NOT change L:.
                    // This aligns with abcjs behavior even for unversioned tunes.
                }
                "P" -> {
                    elements.add(PartElement(value))
                    return // Don't add BodyHeaderElement for P: if we already added PartElement?
                    // Actually, keeping BodyHeaderElement for compatibility might be safer, 
                    // but PartElement is what the RepeatExpander will use.
                }
            }
            elements.add(BodyHeaderElement(id, value))
        }
    }

    override fun visitNote_element(ctx: ABCParser.Note_elementContext): Unit {
        var note = buildNote(ctx)
        if (pendingBrokenRhythmMultiplier != null) {
            note = note.copy(length = note.length.scale(pendingBrokenRhythmMultiplier!!))
            pendingBrokenRhythmMultiplier = null
        }
        if (pendingAnnotation != null) {
            note = note.copy(annotation = pendingAnnotation)
            pendingAnnotation = null
        }
        if (pendingDecorations.isNotEmpty()) {
            note = note.copy(decorations = note.decorations + pendingDecorations)
            pendingDecorations.clear()
        }
        elements.add(note)
    }

    override fun visitElement(ctx: ABCParser.ElementContext): Unit {
        if (ctx.MUSIC_BACKSLASH() != null) {
            elements.add(SpacerElement("\\"))
            return
        }
        if (ctx.SPACER() != null) {
            elements.add(SpacerElement(ctx.SPACER().text))
            return
        }
        if (ctx.BACKTICK() != null) {
            elements.add(SpacerElement("`"))
            return
        }
        if (ctx.DOLLAR() != null) {
            elements.add(SpacerElement("$"))
            return
        }
        if (ctx.PLUS() != null) {
            elements.add(SpacerElement("+"))
            return
        }
        if (ctx.COLON() != null) {
            elements.add(SpacerElement(":"))
            return
        }
        if (ctx.BRACKET_START() != null) {
            elements.add(SpacerElement("["))
            return
        }
        if (ctx.BRACKET_END() != null) {
            elements.add(SpacerElement("]"))
            return
        }
        if (ctx.HYPHEN() != null) {
            // Standalone hyphen might be a tie separated by a space from the note.
            // ABC 2.1 says "A tie is indicated by a hyphen (-) immediately following the note"
            // So if isStrict (2.1+), we should probably NOT search backwards if there was a space.
            // However, even in 2.1, some tools are lenient.
            // We follow the user preference: strict is only enabled when version >= 2.1.
            if (!isStrict) {
                // Search backwards for the last NoteElement to attach the tie to.
                for (i in elements.indices.reversed()) {
                    val el = elements[i]
                    if (el is NoteElement) {
                        if (el.ties == TieType.NONE) {
                            elements[i] = el.copy(ties = TieType.START)
                        }
                        return
                    } else if (el is SpacerElement) {
                        continue
                    } else {
                        break
                    }
                }
            }
            elements.add(SpacerElement("-"))
            return
        }
        if (ctx.DIGIT() != null) {
            elements.add(SpacerElement(ctx.DIGIT().text))
            return
        }
        if (ctx.OCTAVE_UP() != null) {
            elements.add(SpacerElement("'"))
            return
        }
        if (ctx.OCTAVE_DOWN() != null) {
            elements.add(SpacerElement(","))
            return
        }
        if (ctx.MUSIC_TEXT() != null) {
            elements.add(SpacerElement(ctx.MUSIC_TEXT().text))
            return
        }
        super.visitElement(ctx)
    }

    override fun visitDecoration(ctx: ABCParser.DecorationContext): Unit {
        parseDecoration(ctx)?.let { pendingDecorations.add(it) }
    }

    private fun buildNote(ctx: ABCParser.Note_elementContext): NoteElement {
        val pitchText = ctx.note_pitch()?.text ?: ""
        
        val step: NoteStep
        var octave: Int

        if (pitchText.isNotEmpty()) {
            val stepChar = pitchText[0]
            step = when (stepChar.uppercaseChar()) {
                'C' -> NoteStep.C
                'D' -> NoteStep.D
                'E' -> NoteStep.E
                'F' -> NoteStep.F
                'G' -> NoteStep.G
                'A' -> NoteStep.A
                'B' -> NoteStep.B
                else -> NoteStep.C
            }
            octave = if (stepChar.isLowerCase()) 5 else 4
            
            // Handle octave modifiers by iterating tokens
            ctx.octave_modifier()?.children?.forEach { child ->
                if (child is TerminalNode) {
                    when (child.symbol.type) {
                        ABCLexer.OCTAVE_UP -> octave++
                        ABCLexer.OCTAVE_DOWN -> octave--
                    }
                }
            }
            
            lastNoteStep = step
            lastNoteOctave = octave
        } else {
            step = lastNoteStep ?: NoteStep.C
            octave = lastNoteOctave ?: 4
            
            ctx.octave_modifier()?.children?.forEach { child ->
                if (child is TerminalNode) {
                    when (child.symbol.type) {
                        ABCLexer.OCTAVE_UP -> octave++
                        ABCLexer.OCTAVE_DOWN -> octave--
                    }
                }
            }
        }

        val accidental = ctx.accidental()?.let { 
            val child = it.getChild(0)
            if (child is TerminalNode) {
               parseAccidental(child.symbol.type)
            } else null
        }
        val noteLength = ctx.note_length()
        val duration = noteLength?.let { 
            calculateDuration(it.text, currentDefaultLength)
        } ?: currentDefaultLength
        val tie = if (ctx.tie() != null) TieType.START else TieType.NONE
        
        val decorations = ctx.decoration().mapNotNull { parseDecoration(it) }

        return NoteElement(Pitch(step, octave, accidental), duration, tie, decorations = decorations, accidental = accidental)
    }

    override fun visitBroken_rhythm(ctx: ABCParser.Broken_rhythmContext): Unit {
        val lastElement = elements.findLast { it is NoteElement || it is ChordElement || it is RestElement }
        if (lastElement != null) {
            val dots = ctx.text.length
            val multiplier = if (ctx.text.startsWith(">")) {
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

    override fun visitRest_element(ctx: ABCParser.Rest_elementContext): Unit {
        var rest = buildRest(ctx)
        if (pendingBrokenRhythmMultiplier != null) {
            rest = rest.copy(duration = rest.duration.scale(pendingBrokenRhythmMultiplier!!))
            pendingBrokenRhythmMultiplier = null
        }
        if (pendingAnnotation != null) {
            rest = rest.copy(annotation = pendingAnnotation)
            pendingAnnotation = null
        }
        if (pendingDecorations.isNotEmpty()) {
            rest = rest.copy(decorations = rest.decorations + pendingDecorations)
            pendingDecorations.clear()
        }
        elements.add(rest)
    }

    private fun buildRest(ctx: ABCParser.Rest_elementContext): RestElement {
        val restChar = ctx.REST().text
        val duration = if (restChar.equals("Z", ignoreCase = false)) {
            // Z indicates a rest of one measure length.
            val measureDuration = NoteDuration.simplify(currentMeter.numerator, currentMeter.denominator)
            ctx.note_length()?.let { 
                // Multi-measure rest multiplier (e.g. Z2)
                val multiplier = calculateDuration(it.text, NoteDuration(1, 1))
                measureDuration * multiplier
            } ?: measureDuration
        } else {
            ctx.note_length()?.let { calculateDuration(it.text, currentDefaultLength) } ?: currentDefaultLength
        }
        val isHidden = restChar.equals("x", ignoreCase = true)
        val decorations = ctx.decoration().mapNotNull { parseDecoration(it) }
        return RestElement(duration, isHidden, decorations)
    }

    override fun visitChord(ctx: ABCParser.ChordContext): Unit {
        val explicitLengthCtx = ctx.note_length()
        var explicitChordMultiplier: NoteDuration? = null
        if (explicitLengthCtx != null) {
            // Note: calculateDuration returns (num/den) * defaultLength.
            // For a chord multiplier, we just want the multiplier part, or we divide by defaultLength.
            // Actually, calculateDuration(text, 1/1) gives us the multiplier as a NoteDuration.
            explicitChordMultiplier = calculateDuration(explicitLengthCtx.text, NoteDuration(1, 1))
        }

        val notes = mutableListOf<NoteElement>()
        val chordVisitor = object : ABCParserBaseVisitor<Unit>() {
            override fun visitNote_element(noteCtx: ABCParser.Note_elementContext) {
                var note = buildNote(noteCtx)
                if (explicitChordMultiplier != null) {
                    note = note.copy(length = note.length * explicitChordMultiplier!!)
                }
                notes.add(note)
            }
        }
        ctx.chord_element().forEach { it.accept(chordVisitor) }
        
        // ABC 2.1: "If the chord has no duration modifier, its duration is the same as that of the first note in the chord."
        // "If a chord has a duration modifier, the duration of each note in the chord is multiplied by that modifier."
        var duration = if (explicitChordMultiplier != null) {
            // If explicit length is present on the chord, the chord's duration is determined by that.
            // But wait, the notes inside already had durations.
            // [G2B2]3 -> G6 B6. Duration 6.
            // calculateDuration(explicitLengthCtx.text, currentDefaultLength) correctly scales the default length.
            // But if notes inside have multipliers... 
            // The first note's duration (after scaling) should represent the chord's duration.
            notes.firstOrNull()?.length ?: currentDefaultLength
        } else {
            // No explicit length on chord. Use first note.
            notes.firstOrNull()?.length ?: currentDefaultLength
        }
        if (pendingBrokenRhythmMultiplier != null) {
            duration = duration.scale(pendingBrokenRhythmMultiplier!!)
            pendingBrokenRhythmMultiplier = null
        }
        
        val decorations = ctx.decoration().mapNotNull { parseDecoration(it) }
        
        if (pendingDecorations.isNotEmpty()) {
            val combinedDecos = decorations + pendingDecorations
            elements.add(ChordElement(notes, duration, annotation = pendingAnnotation, decorations = combinedDecos))
            pendingDecorations.clear()
        } else {
            elements.add(ChordElement(notes, duration, annotation = pendingAnnotation, decorations = decorations))
        }
        pendingAnnotation = null
    }

    override fun visitAnnotation(ctx: ABCParser.AnnotationContext): Unit {
        pendingAnnotation = ctx.CHORD_CONTENT()?.text ?: ""
    }

    override fun visitGrace_group(ctx: ABCParser.Grace_groupContext): Unit {
        val notes = ctx.note_element().map { buildNote(it) }
        val isAcciaccatura = ctx.SLASH() != null
        elements.add(GraceNoteElement(notes, isAcciaccatura))
    }

    override fun visitSlur_start(ctx: ABCParser.Slur_startContext): Unit {
        elements.add(SlurElement(true))
    }

    override fun visitSlur_end(ctx: ABCParser.Slur_endContext): Unit {
        elements.add(SlurElement(false))
    }

    override fun visitInline_field(ctx: ABCParser.Inline_fieldContext): Unit {
        val fullText = ctx.text.removePrefix("[").removeSuffix("]")
        val colonIdx = fullText.indexOf(':')
        if (colonIdx != -1) {
            val key = fullText.substring(0, colonIdx).trim()
            val value = fullText.substring(colonIdx + 1).trim()
            val type = HeaderType.entries.find { it.key == key } ?: HeaderType.UNKNOWN
            
            if (type == HeaderType.LENGTH) {
                currentDefaultLength = parseLength(value)
            }
            
            elements.add(InlineFieldElement(type, value))
        }
    }

    override fun visitBarline(ctx: ABCParser.BarlineContext): Unit {
        val firstChild = ctx.getChild(0)
        if (firstChild is TerminalNode) {
            val tokenType = firstChild.symbol.type
            val type = parseBarLineType(tokenType)
            elements.add(BarLineElement(type))
        } else if (firstChild is ABCParser.VariantContext) {
            visitVariant(firstChild)
        }
    }

    override fun visitVariant(ctx: ABCParser.VariantContext): Unit {
        val variants = ctx.DIGIT().mapNotNull { it.text.toIntOrNull() }
        elements.add(VariantElement(variants))
    }

    override fun visitOverlay(ctx: ABCParser.OverlayContext): Unit {
        elements.add(OverlayElement)
    }

    override fun visitLyrics_line(ctx: ABCParser.Lyrics_lineContext): Unit {
        val content = if (ctx.LYRIC_CONTENT() != null) ctx.LYRIC_CONTENT().text else ""
        elements.add(LyricElement(content.trim()))
    }

    override fun visitTuplet_element(ctx: ABCParser.Tuplet_elementContext): Unit {
        val text = ctx.TUPLET_START().text.substring(1) // remove (
        val parts = text.split(":")
        val p = parts.getOrNull(0)?.toIntOrNull() ?: 3
        val q = parts.getOrNull(1)?.toIntOrNull()
        val r = parts.getOrNull(2)?.toIntOrNull()
        elements.add(TupletElement(p, q, r))
    }

    override fun visitSpace(ctx: ABCParser.SpaceContext): Unit {
        elements.add(SpacerElement(ctx.text))
    }

    override fun visitStylesheet_directive(ctx: ABCParser.Stylesheet_directiveContext): Unit {
        elements.add(DirectiveElement(ctx.text.removePrefix("%%")))
    }
    
    override fun visitSymbol_line(ctx: ABCParser.Symbol_lineContext): Unit {
        val items = mutableListOf<SymbolItem>()
        for (i in 0 until ctx.childCount) {
            val child = ctx.getChild(i)
            if (child is TerminalNode) {
                when (child.symbol.type) {
                    ABCLexer.SYMBOL_CHORD -> items.add(SymbolChord(child.text.removeSurrounding("\"")))
                    ABCLexer.SYMBOL_DECO -> items.add(SymbolDecoration(child.text.removeSurrounding("!")))
                    ABCLexer.SYMBOL_SKIP -> items.add(SymbolSkip)
                    ABCLexer.SYMBOL_BAR -> items.add(SymbolBar)
                    // Ignore others like MUSIC_SYMBOL_LINE ("s:") and SYMBOL_EOL
                }
            }
        }
        elements.add(SymbolLineElement(items))
    }


    private fun extractTextBlock(children: List<org.antlr.v4.runtime.tree.ParseTree>): TextBlockElement {
        val sb = StringBuilder()
        children.forEach { child ->
            if (child is TerminalNode) {
                val text = child.text
                if (!text.startsWith("%%begintext") && !text.startsWith("%%endtext")) {
                     sb.append(text)
                }
            }
        }
        return TextBlockElement(sb.toString().lines())
    }

    override fun visitText_block_default(ctx: ABCParser.Text_block_defaultContext) {
        // Ignored for now as we only collect body elements
    }
    
    override fun visitText_block_music(ctx: ABCParser.Text_block_musicContext) {
        val children = (0 until ctx.childCount).map { ctx.getChild(it) }
        elements.add(extractTextBlock(children))
    }
    
    override fun visitText_block_header(ctx: ABCParser.Text_block_headerContext) {
        // Ignored for now
    }

    private fun parseAccidental(tokenType: Int): Accidental? = when (tokenType) {
        ABCLexer.ACC_SHARP -> Accidental.SHARP
        ABCLexer.ACC_SHARP_DBL -> Accidental.DOUBLE_SHARP
        ABCLexer.ACC_SHARP_HALF -> Accidental.QUARTER_SHARP
        ABCLexer.ACC_SHARP_DBL_HALF -> Accidental.THREE_QUARTER_SHARP
        ABCLexer.ACC_SHARP_QUART_3 -> Accidental.THREE_QUARTER_SHARP
        ABCLexer.ACC_FLAT -> Accidental.FLAT
        ABCLexer.ACC_FLAT_DBL -> Accidental.DOUBLE_FLAT
        ABCLexer.ACC_FLAT_HALF -> Accidental.QUARTER_FLAT
        ABCLexer.ACC_FLAT_DBL_HALF -> Accidental.THREE_QUARTER_FLAT
        ABCLexer.ACC_FLAT_QUART_3 -> Accidental.THREE_QUARTER_FLAT
        ABCLexer.ACC_NATURAL -> Accidental.NATURAL
        else -> null
    }

    private fun parseBarLineType(tokenType: Int): BarLineType = when (tokenType) {
        ABCLexer.BAR_SINGLE -> BarLineType.SINGLE
        ABCLexer.BAR_THIN_DOUBLE -> BarLineType.DOUBLE
        ABCLexer.BAR_THIN_THICK -> BarLineType.FINAL
        ABCLexer.BAR_THICK_THIN -> BarLineType.DOUBLE
        ABCLexer.BAR_REP_START -> BarLineType.REPEAT_START
        ABCLexer.BAR_REP_END -> BarLineType.REPEAT_END
        ABCLexer.BAR_REP_END_ALT -> BarLineType.REPEAT_END
        ABCLexer.BAR_REP_END_TUNE -> BarLineType.REPEAT_END
        ABCLexer.BAR_REP_DBL_ALT -> BarLineType.REPEAT_BOTH
        ABCLexer.BAR_REP_DBL -> BarLineType.REPEAT_BOTH
        ABCLexer.BAR_REP_DBL_TUNE -> BarLineType.REPEAT_BOTH
        ABCLexer.BAR_THICK_THICK -> BarLineType.DOUBLE
        else -> BarLineType.SINGLE
    }


    private fun calculateDuration(text: String, defaultLength: NoteDuration): NoteDuration {
        if (logger.isDebugEnabled) {
            logger.debug("calculateDuration text='$text' default=$defaultLength")
        }
        val num: Int
        val den: Int
        val slashCount = text.count { it == '/' }
        if (slashCount > 0) {
            val parts = text.split("/")
            num = if (parts[0].isEmpty()) 1 else parts[0].toIntOrNull() ?: 1
            // Handle multiple slashes like d// (slashCount=2, parts=["d", "", ""]) or d/2/ (slashCount=2, parts=["d", "2", ""])
            val explicitDen = if (parts.size > 1 && parts[1].isNotEmpty()) parts[1].toIntOrNull() else null
            
            den = if (explicitDen != null) {
                explicitDen * Math.pow(2.0, (slashCount - 1).toDouble()).toInt()
            } else {
                Math.pow(2.0, slashCount.toDouble()).toInt()
            }
        } else {
            num = text.toIntOrNull() ?: 1
            den = 1
        }
        val result = NoteDuration.simplify(num * defaultLength.numerator, den * defaultLength.denominator)
        if (logger.isDebugEnabled) {
            logger.debug("calculateDuration text='$text' default=$defaultLength -> $result ($num/$den)")
        }
        return result
    }

    private fun parseDecoration(ctx: ABCParser.DecorationContext): Decoration? {
        val child = ctx.getChild(0)
        if (child is TerminalNode) {
            return when (child.symbol.type) {
                ABCLexer.ROLL -> Decoration("~")
                ABCLexer.PLUS -> Decoration("+")
                ABCLexer.UPBOW -> Decoration("u")
                ABCLexer.DOWNBOW -> Decoration("v")
                ABCLexer.USER_DEF_SYMBOL -> Decoration(child.text)
                ABCLexer.STACCATO -> Decoration(".")
                else -> null
            }
        }
        
        val content = ctx.children
            .filter { it is TerminalNode && (it.symbol.type == ABCLexer.BANG_DECO_CONTENT || it.symbol.type == ABCLexer.PLUS_DECO_CONTENT || it.symbol.type == ABCLexer.SPACE) }
            .joinToString("") { it.text }.trim()
        
        if (content.isNotEmpty()) {
            return Decoration(content)
        }
        return null
    }
}