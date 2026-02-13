package io.github.ryangardner.abc.parser

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

private val logger: Logger = LoggerFactory.getLogger(AbcParser::class.java)

public class AbcParser {

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
        val preambleVisitor = AbcPreambleVisitor()
        val globalPreamble = ctx.tune_preamble()?.accept(preambleVisitor) ?: emptyList()
        
        return ctx.tune()?.mapIndexed { index, tuneCtx -> 
            val headerCtx = tuneCtx.tune_header()
            val header = buildTuneHeader(headerCtx)
            
            val bodyVisitor = AbcTuneBodyVisitor(header)
            tuneCtx.tune_body()?.accept(bodyVisitor)
            
            AbcTune(
                header = header,
                body = TuneBody(bodyVisitor.elements),
                metadata = TuneMetadata(),
                preamble = if (index == 0) globalPreamble else emptyList()
            )
        } ?: emptyList()
    }

    private fun buildTuneHeader(ctx: ABCParser.Tune_headerContext): TuneHeader {
        val xRef = ctx.x_ref()?.children?.filter { it is TerminalNode && (it.symbol.type == ABCLexer.FIELD_CONTENT || it.symbol.type == ABCLexer.FIELD_BACKSLASH) }
            ?.joinToString("") { it.text }?.trim()?.toIntOrNull() ?: 1
        val titles = mutableListOf<String>()
        var key: KeySignature? = null
        var meter: TimeSignature? = null
        var length: NoteDuration? = null
        var playingOrder: String? = null
        val unknownHeaders = mutableMapOf<String, String>()
        val allHeaders = mutableListOf<Pair<String, String>>()
        var version = "2.0"
        ctx.children?.forEach { child ->
            when (child) {
                is ABCParser.FieldContext -> {
                    if (child.FIELD_ID() != null) {
                        val id = child.FIELD_ID().text.removeSuffix(":")
                        val value = child.children
                            ?.filter { it is TerminalNode && (it.symbol.type == ABCLexer.FIELD_CONTENT || it.symbol.type == ABCLexer.FIELD_BACKSLASH) }
                            ?.joinToString("") { it.text }?.trim() ?: ""
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

        val actualMeter = meter ?: TimeSignature.NONE
        val actualLength = length ?: run {
            if (actualMeter.numerator * 4 < actualMeter.denominator * 3) NoteDuration(1, 16) else NoteDuration(1, 8)
        }

        val keyValue = ctx.key_field()?.children
            ?.filter { it is TerminalNode && (it.symbol.type == ABCLexer.FIELD_CONTENT || it.symbol.type == ABCLexer.FIELD_BACKSLASH) }
            ?.joinToString("") { it.text }?.trim() ?: "C"
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

private class AbcPreambleVisitor : ABCParserBaseVisitor<List<MusicElement>>() {
    private val elements = mutableListOf<MusicElement>()

    override fun visitTunebook(ctx: ABCParser.TunebookContext): List<MusicElement> {
        return ctx.tune_preamble()?.accept(this) ?: emptyList()
    }

    override fun visitTune_preamble(ctx: ABCParser.Tune_preambleContext): List<MusicElement> {
        ctx.children?.forEach { child ->
            when (child) {
                is ABCParser.FieldContext -> {
                    val id = child.FIELD_ID().text.removeSuffix(":")
                    val value = child.children
                        ?.filter { it is TerminalNode && (it.symbol.type == ABCLexer.FIELD_CONTENT || it.symbol.type == ABCLexer.FIELD_BACKSLASH) }
                        ?.joinToString("") { it.text }?.trim() ?: ""
                    elements.add(BodyHeaderElement(id, value, child.start.line, child.start.charPositionInLine))
                }
                is ABCParser.Text_block_defaultContext -> {
                    val lines = child.children?.filter { it is TerminalNode && it.symbol.type == ABCLexer.TEXT_BLOCK_CONTENT }?.map { it.text } ?: emptyList()
                    elements.add(TextBlockElement(lines, child.start.line, child.start.charPositionInLine))
                }
                is TerminalNode -> {
                    when (child.symbol.type) {
                        ABCLexer.STYLESHEET -> elements.add(DirectiveElement(child.text.removePrefix("%%"), child.symbol.line, child.symbol.charPositionInLine))
                        ABCLexer.NEWLINE, ABCLexer.WS_DEFAULT, ABCLexer.FREE_TEXT, ABCLexer.UNRECOGNIZED, ABCLexer.DEFAULT_COMMENT -> 
                            elements.add(SpacerElement(child.text, child.symbol.line, child.symbol.charPositionInLine))
                    }
                }
            }
        }
        return elements
    }
}

private fun parseMeter(text: String): TimeSignature {
    val cleanText = text.substringBefore("%").trim()
    return when (cleanText) {
        "C" -> TimeSignature(4, 4, "C")
        "C|" -> TimeSignature(2, 2, "C|")
        "none" -> TimeSignature.NONE
        else -> {
            val parts = cleanText.split("/")
            if (parts.size >= 2) {
                TimeSignature(parts[0].trim().toIntOrNull() ?: 4, parts[1].trim().toIntOrNull() ?: 4)
            } else TimeSignature.NONE
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
    private var hasExplicitLength = header.headers.any { it.first == "L" }
    
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

    override fun visitMeasureWithBar(ctx: ABCParser.MeasureWithBarContext) {
        ctx.children?.forEach { visit(it) }
    }

    override fun visitMeasureNoBar(ctx: ABCParser.MeasureNoBarContext) {
        ctx.children?.forEach { visit(it) }
    }

    override fun visitMusicLineContent(ctx: ABCParser.MusicLineContentContext) {
        val initialSize = elements.size
        ctx.children?.forEach { visit(it) }
        if (elements.size > initialSize) {
            val last = elements.lastOrNull()
            if (last !is SpacerElement || last.text != "\n") {
                elements.add(SpacerElement("\n"))
            }
        }
    }

    override fun visitMusicLineEmpty(ctx: ABCParser.MusicLineEmptyContext) {
        elements.add(SpacerElement("\n"))
    }

    override fun visitNote(ctx: ABCParser.NoteContext) {
        var note = buildNote(ctx.note_element())
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

    override fun visitRest(ctx: ABCParser.RestContext) {
        var rest = buildRest(ctx.rest_element())
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

    override fun visitTuplet(ctx: ABCParser.TupletContext) {
        val line = ctx.start.line
        val col = ctx.start.charPositionInLine
        val text = ctx.tuplet_element().TUPLET_START().text.substring(1) // remove (
        val parts = text.split(":")
        val p = parts.getOrNull(0)?.toIntOrNull() ?: 3
        val q = parts.getOrNull(1)?.toIntOrNull()
        val r = parts.getOrNull(2)?.toIntOrNull()
        elements.add(TupletElement(p, q, r, line = line, column = col))
    }

    override fun visitChord(ctx: ABCParser.ChordContext) {
        val chordCtx = ctx.chord_alt()
        val line = ctx.start.line
        val col = ctx.start.charPositionInLine
        val explicitLengthCtx = chordCtx.note_length()
        var explicitChordMultiplier: NoteDuration? = null
        if (explicitLengthCtx != null) {
            explicitChordMultiplier = calculateDuration(explicitLengthCtx.text, NoteDuration(1, 1))
        }

        val notes = mutableListOf<NoteElement>()
        chordCtx.chord_element()?.forEach { elementCtx ->
            val chordItemVisitor = object : ABCParserBaseVisitor<Unit>() {
                override fun visitChordNote(ctx: ABCParser.ChordNoteContext) {
                    var note = buildNote(ctx.note_element())
                    if (explicitChordMultiplier != null) {
                        note = note.copy(length = note.length * explicitChordMultiplier!!)
                    }
                    if (pendingAnnotation != null) {
                        note = note.copy(annotation = pendingAnnotation)
                        pendingAnnotation = null
                    }
                    if (pendingDecorations.isNotEmpty()) {
                        note = note.copy(decorations = note.decorations + pendingDecorations)
                        pendingDecorations.clear()
                    }
                    notes.add(note)
                }

                override fun visitChordDecoration(ctx: ABCParser.ChordDecorationContext) {
                    parseDecoration(ctx.decoration_alt())?.let { pendingDecorations.add(it) }
                }

                override fun visitChordAnnotation(ctx: ABCParser.ChordAnnotationContext) {
                    pendingAnnotation = ctx.annotation_alt().CHORD_CONTENT()?.text ?: ""
                }
            }
            elementCtx.accept(chordItemVisitor)
        }
        
        var duration = notes.firstOrNull()?.length ?: currentDefaultLength
        
        val decorations = chordCtx.decoration_alt()?.mapNotNull { parseDecoration(it) } ?: emptyList()
        
        if (pendingDecorations.isNotEmpty()) {
            val combinedDecos = decorations + pendingDecorations
            elements.add(ChordElement(notes, duration, annotation = pendingAnnotation, decorations = combinedDecos, line = line, column = col))
            pendingDecorations.clear()
        } else {
            elements.add(ChordElement(notes, duration, annotation = pendingAnnotation, decorations = decorations, line = line, column = col))
        }
        pendingAnnotation = null
    }

    override fun visitAnnotation(ctx: ABCParser.AnnotationContext) {
        pendingAnnotation = ctx.annotation_alt()?.CHORD_CONTENT()?.text ?: ""
    }

    override fun visitDecoration(ctx: ABCParser.DecorationContext) {
        parseDecoration(ctx.decoration_alt())?.let { pendingDecorations.add(it) }
    }

    override fun visitInlineField(ctx: ABCParser.InlineFieldContext) {
        val line = ctx.start.line
        val col = ctx.start.charPositionInLine
        val fullText = ctx.inline_field_alt()?.text?.removePrefix("[")?.removeSuffix("]") ?: ""
        val colonIdx = fullText.indexOf(':')
        if (colonIdx != -1) {
            val key = fullText.substring(0, colonIdx).trim()
            val value = fullText.substring(colonIdx + 1).trim()
            val type = HeaderType.entries.find { it.key == key } ?: HeaderType.UNKNOWN
            
            if (type == HeaderType.LENGTH) {
                currentDefaultLength = parseLength(value)
                hasExplicitLength = true
            }
            
            elements.add(InlineFieldElement(type, value, line = line, column = col))
        }
    }

    override fun visitStylesheet(ctx: ABCParser.StylesheetContext) {
        elements.add(DirectiveElement(ctx.stylesheet_directive_alt()?.text?.removePrefix("%%") ?: "", ctx.start.line, ctx.start.charPositionInLine))
    }

    override fun visitOverlay(ctx: ABCParser.OverlayContext) {
        elements.add(OverlayElement(ctx.start.line, ctx.start.charPositionInLine))
    }

    override fun visitGraceGroup(ctx: ABCParser.GraceGroupContext) {
        val line = ctx.start.line
        val col = ctx.start.charPositionInLine
        val graceCtx = ctx.grace_group_alt()
        val notes = mutableListOf<NoteElement>()
        val graceNoteVisitor = object : ABCParserBaseVisitor<Unit>() {
            override fun visitNote_element(ctx: ABCParser.Note_elementContext) {
                notes.add(buildNote(ctx))
            }
        }
        graceCtx.children?.forEach { it.accept(graceNoteVisitor) }
        val isAcciaccatura = graceCtx.SLASH() != null
        elements.add(GraceNoteElement(notes, isAcciaccatura, line = line, column = col))
    }

    override fun visitSlurStart(ctx: ABCParser.SlurStartContext) {
        elements.add(SlurElement(true, ctx.start.line, ctx.start.charPositionInLine))
    }

    override fun visitSlurEnd(ctx: ABCParser.SlurEndContext) {
        elements.add(SlurElement(false, ctx.start.line, ctx.start.charPositionInLine))
    }

    override fun visitBrokenRhythm(ctx: ABCParser.BrokenRhythmContext) {
        val text = ctx.broken_rhythm_alt()?.text ?: ""
        
        // Attach to the last rhythmic element instead of adding a spacer
        for (i in elements.indices.reversed()) {
            val el = elements[i]
            when (el) {
                is NoteElement -> {
                    elements[i] = el.copy(brokenRhythm = text)
                    return
                }
                is RestElement -> {
                    elements[i] = el.copy(brokenRhythm = text)
                    return
                }
                is ChordElement -> {
                    elements[i] = el.copy(brokenRhythm = text)
                    return
                }
                is SpacerElement, is BarLineElement, is SlurElement -> continue
                else -> break 
            }
        }
        // Fallback if no preceding note
        elements.add(SpacerElement(text, ctx.start.line, ctx.start.charPositionInLine))
    }

    override fun visitSpace(ctx: ABCParser.SpaceContext) {
        ctx.spacer_alt()?.let { visitSpacer_alt(it) }
    }

    override fun visitSpacer_alt(ctx: ABCParser.Spacer_altContext) {
        elements.add(SpacerElement(ctx.text, ctx.start.line, ctx.start.charPositionInLine))
    }

    override fun visitMiscellaneous(ctx: ABCParser.MiscellaneousContext) {
        val line = ctx.start.line
        val col = ctx.start.charPositionInLine
        val text = ctx.text ?: ""
        if (text == "-") {
            if (!isStrict) {
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
        }
        elements.add(SpacerElement(text, line, col))
    }

    override fun visitBar(ctx: ABCParser.BarContext) {
        val firstChild = ctx.getChild(0)
        if (firstChild is TerminalNode) {
            val tokenType = firstChild.symbol.type
            val type = parseBarLineType(tokenType)
            elements.add(BarLineElement(type, line = ctx.start.line, column = ctx.start.charPositionInLine))
        }
    }

    override fun visitVariantBar(ctx: ABCParser.VariantBarContext) {
        ctx.variant()?.let { visitVariant(it) }
    }

    override fun visitVariant(ctx: ABCParser.VariantContext) {
        val line = ctx.start.line
        val col = ctx.start.charPositionInLine
        val prefix = ctx.getChild(0)?.text ?: "["
        val variants = ctx.DIGIT()?.mapNotNull { it.text.toIntOrNull() } ?: emptyList()
        elements.add(VariantElement(variants, prefix, line = line, column = col))
    }

    override fun visitField_in_body(ctx: ABCParser.Field_in_bodyContext) {
        if (ctx.FIELD_ID() != null || ctx.KEY_FIELD() != null) {
            val id = if (ctx.FIELD_ID() != null) ctx.FIELD_ID().text.removeSuffix(":") else "K"
            val value = ctx.children
                ?.filter { it is TerminalNode && (it.symbol.type == ABCLexer.FIELD_CONTENT || it.symbol.type == ABCLexer.FIELD_BACKSLASH) }
                ?.joinToString("") { it.text }?.trim() ?: ""
            
            when (id) {
                "L" -> {
                    currentDefaultLength = parseLength(value)
                    hasExplicitLength = true
                }
                "M" -> {
                    currentMeter = parseMeter(value)
                    // Reset Default Length logic when Meter changes in body, 
                    // ONLY if we don't have an explicit L: in this tune.
                    if (!hasExplicitLength) {
                        if (currentMeter.numerator * 4 < currentMeter.denominator * 3) {
                            currentDefaultLength = NoteDuration(1, 16)
                        } else {
                            currentDefaultLength = NoteDuration(1, 8)
                        }
                    }
                }
                "P" -> {
                    elements.add(PartElement(value, line = ctx.start.line, column = ctx.start.charPositionInLine))
                    return
                }
            }
            elements.add(BodyHeaderElement(id, value, line = ctx.start.line, column = ctx.start.charPositionInLine))
        }
    }

    override fun visitLyrics_line(ctx: ABCParser.Lyrics_lineContext) {
        val line = ctx.start.line
        val col = ctx.start.charPositionInLine
        val content = if (ctx.LYRIC_CONTENT() != null) ctx.LYRIC_CONTENT().text else ""
        elements.add(LyricElement(content.trim(), line = line, column = col))
    }

    override fun visitSymbol_line(ctx: ABCParser.Symbol_lineContext) {
        val line = ctx.start.line
        val col = ctx.start.charPositionInLine
        val items = mutableListOf<SymbolItem>()
        for (i in 0 until ctx.childCount) {
            val child = ctx.getChild(i)
            if (child is TerminalNode) {
                when (child.symbol.type) {
                    ABCLexer.SYMBOL_CHORD -> items.add(SymbolChord(child.text.removeSurrounding("\"")))
                    ABCLexer.SYMBOL_DECO -> items.add(SymbolDecoration(child.text.removeSurrounding("!")))
                    ABCLexer.SYMBOL_DECO_PLUS -> items.add(SymbolDecoration(child.text.removeSurrounding("+")))
                    ABCLexer.SYMBOL_SKIP -> items.add(SymbolSkip)
                    ABCLexer.SYMBOL_BAR -> items.add(SymbolBar)
                    ABCLexer.SYMBOL_TEXT -> items.add(SymbolDecoration(child.text))
                }
            }
        }
        elements.add(SymbolLineElement(items, line = line, column = col))
    }

    override fun visitText_block_music(ctx: ABCParser.Text_block_musicContext) {
        val children = (0 until ctx.childCount).map { ctx.getChild(it) }
        elements.add(extractTextBlock(children))
    }

    private fun buildNote(ctx: ABCParser.Note_elementContext): NoteElement {
        val line = ctx.start.line
        val col = ctx.start.charPositionInLine
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
            if (child is TerminalNode) parseAccidental(child.symbol.type) else null
        }
        val noteLength = ctx.note_length()
        val duration = noteLength?.let { calculateDuration(it.text, currentDefaultLength) } ?: currentDefaultLength
        val tie = if (ctx.tie() != null) TieType.START else TieType.NONE
        val decorations = ctx.decoration_alt()?.mapNotNull { parseDecoration(it) } ?: emptyList()

        return NoteElement(Pitch(step, octave, accidental), duration, tie, decorations = decorations, accidental = accidental, line = line, column = col)
    }

    private fun buildRest(ctx: ABCParser.Rest_elementContext): RestElement {
        val line = ctx.start.line
        val col = ctx.start.charPositionInLine
        val restChar = ctx.REST().text
        val duration = if (restChar.equals("Z", ignoreCase = false)) {
            val measureDuration = NoteDuration.simplify(currentMeter.numerator.toLong(), currentMeter.denominator.toLong())
            ctx.note_length()?.let { 
                val multiplier = calculateDuration(it.text, NoteDuration(1, 1))
                measureDuration * multiplier
            } ?: measureDuration
        } else {
            ctx.note_length()?.let { calculateDuration(it.text, currentDefaultLength) } ?: currentDefaultLength
        }
        val isHidden = restChar.equals("x", ignoreCase = true)
        val decorations = ctx.decoration_alt()?.mapNotNull { parseDecoration(it) } ?: emptyList()
        return RestElement(duration, isHidden, decorations, line = line, column = col)
    }

    private fun extractTextBlock(children: List<org.antlr.v4.runtime.tree.ParseTree>): TextBlockElement {
        val startNode = children.firstOrNull { it is TerminalNode } as? TerminalNode
        val line = startNode?.symbol?.line ?: -1
        val col = startNode?.symbol?.charPositionInLine ?: -1
        val sb = StringBuilder()
        children.forEach { child ->
            if (child is TerminalNode) {
                val text = child.text
                if (!text.startsWith("%%begintext") && !text.startsWith("%%endtext")) {
                     sb.append(text)
                }
            }
        }
        return TextBlockElement(sb.toString().lines(), line = line, column = col)
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
        val num: Int
        val den: Int
        val slashCount = text.count { it == '/' }
        if (slashCount > 0) {
            val parts = text.split("/")
            num = if (parts[0].isEmpty()) 1 else parts[0].toIntOrNull() ?: 1
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
        return NoteDuration.simplify(num.toLong() * defaultLength.numerator, den.toLong() * defaultLength.denominator)
    }

    private fun parseDecoration(ctx: ABCParser.Decoration_altContext): Decoration? {
        val firstChild = ctx.getChild(0)
        if (firstChild is TerminalNode) {
            val deco = when (firstChild.symbol.type) {
                ABCLexer.ROLL -> Decoration("~")
                ABCLexer.PLUS -> Decoration("+")
                ABCLexer.UPBOW -> Decoration("u")
                ABCLexer.DOWNBOW -> Decoration("v")
                ABCLexer.USER_DEF_SYMBOL -> Decoration(firstChild.text)
                ABCLexer.STACCATO -> Decoration(".")
                else -> null
            }
            if (deco != null) return deco
        }
        val content = ctx.children
            ?.filter { it is TerminalNode && (it.symbol.type == ABCLexer.BANG_DECO_CONTENT || it.symbol.type == ABCLexer.SPACE || it.symbol.type == ABCLexer.BROKEN_RHYTHM_LEFT || it.symbol.type == ABCLexer.BROKEN_RHYTHM_RIGHT) }
            ?.joinToString("") { it.text }?.trim() ?: ""
        return if (content.isNotEmpty()) Decoration(content) else null
    }
}
