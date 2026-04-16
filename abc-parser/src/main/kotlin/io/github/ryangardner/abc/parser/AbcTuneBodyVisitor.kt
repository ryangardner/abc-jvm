package io.github.ryangardner.abc.parser

import io.github.ryangardner.abc.antlr.ABCLexer
import io.github.ryangardner.abc.antlr.ABCParser
import io.github.ryangardner.abc.antlr.ABCParserBaseVisitor
import io.github.ryangardner.abc.core.model.Accidental
import io.github.ryangardner.abc.core.model.BarLineElement
import io.github.ryangardner.abc.core.model.BarLineType
import io.github.ryangardner.abc.core.model.BodyHeaderElement
import io.github.ryangardner.abc.core.model.BrokenRhythmMarkerElement
import io.github.ryangardner.abc.core.model.ChordElement
import io.github.ryangardner.abc.core.model.Decoration
import io.github.ryangardner.abc.core.model.DirectiveElement
import io.github.ryangardner.abc.core.model.DurationMultiplier
import io.github.ryangardner.abc.core.model.GraceNoteElement
import io.github.ryangardner.abc.core.model.HeaderType
import io.github.ryangardner.abc.core.model.InlineFieldElement
import io.github.ryangardner.abc.core.model.LyricElement
import io.github.ryangardner.abc.core.model.MusicElement
import io.github.ryangardner.abc.core.model.NoteDuration
import io.github.ryangardner.abc.core.model.NoteElement
import io.github.ryangardner.abc.core.model.NoteStep
import io.github.ryangardner.abc.core.model.OverlayElement
import io.github.ryangardner.abc.core.model.PartElement
import io.github.ryangardner.abc.core.model.Pitch
import io.github.ryangardner.abc.core.model.RestElement
import io.github.ryangardner.abc.core.model.SlurElement
import io.github.ryangardner.abc.core.model.SpacerElement
import io.github.ryangardner.abc.core.model.SymbolBar
import io.github.ryangardner.abc.core.model.SymbolChord
import io.github.ryangardner.abc.core.model.SymbolDecoration
import io.github.ryangardner.abc.core.model.SymbolItem
import io.github.ryangardner.abc.core.model.SymbolLineElement
import io.github.ryangardner.abc.core.model.SymbolSkip
import io.github.ryangardner.abc.core.model.TextBlockElement
import io.github.ryangardner.abc.core.model.TieType
import io.github.ryangardner.abc.core.model.TimeSignature
import io.github.ryangardner.abc.core.model.TuneHeader
import io.github.ryangardner.abc.core.model.TupletElement
import io.github.ryangardner.abc.core.model.VariantElement
import org.antlr.v4.runtime.tree.TerminalNode

@Suppress(
    "TooManyFunctions",
    "NestedBlockDepth",
    "MagicNumber",
    "SwallowedException",
    "MaxLineLength",
    "ComplexCondition",
    "CyclomaticComplexMethod",
)
internal class AbcTuneBodyVisitor(
    val header: TuneHeader,
) : ABCParserBaseVisitor<Unit>() {
    val elements = mutableListOf<MusicElement>()
    private var currentMeter = header.meter

    private val isStrict: Boolean =
        try {
            val vNum = header.version.toDouble()
            vNum >= 2.1
        } catch (e: Exception) {
            false
        }
    private var lastNoteStep: NoteStep? = null
    private var lastNoteOctave: Int? = null
    private val pendingAnnotations = mutableListOf<String>()
    private val pendingDecorations = mutableListOf<Decoration>()

    override fun visitMeasureWithBar(ctx: ABCParser.MeasureWithBarContext) {
        ctx.children?.forEach { visit(it) }
    }

    override fun visitMeasureNoBar(ctx: ABCParser.MeasureNoBarContext) {
        ctx.children?.forEach { visit(it) }
    }

    override fun visitMusicLineContent(ctx: ABCParser.MusicLineContentContext) {
        ctx.children?.forEach { visit(it) }
    }

    override fun visitMusicLineEmpty(ctx: ABCParser.MusicLineEmptyContext) {
        elements.add(SpacerElement("\n"))
    }

    override fun visitNote(ctx: ABCParser.NoteContext) {
        var note = buildNote(ctx.note_element())
        if (pendingAnnotations.isNotEmpty()) {
            note = note.copy(annotations = note.annotations + pendingAnnotations)
            pendingAnnotations.clear()
        }
        if (pendingDecorations.isNotEmpty()) {
            note = note.copy(decorations = note.decorations + pendingDecorations)
            pendingDecorations.clear()
        }
        elements.add(note)
    }

    override fun visitRest(ctx: ABCParser.RestContext) {
        var rest = buildRest(ctx.rest_element())
        if (pendingAnnotations.isNotEmpty()) {
            rest = rest.copy(annotations = rest.annotations + pendingAnnotations)
            pendingAnnotations.clear()
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

        val tupletElement = ctx.tuplet_element()
        val p = tupletElement.p?.text?.toIntOrNull() ?: 3
        val q = tupletElement.q?.text?.toIntOrNull()
        val r = tupletElement.r?.text?.toIntOrNull()

        elements.add(TupletElement(p, q, r, line = line, column = col))
    }

    override fun visitChord(ctx: ABCParser.ChordContext) {
        val chordCtx = ctx.chord_alt()
        val line = ctx.start.line
        val col = ctx.start.charPositionInLine
        val explicitLengthCtx = chordCtx.note_length()
        var explicitChordMultiplier: DurationMultiplier? = null
        if (explicitLengthCtx != null) {
            explicitChordMultiplier = ParserUtils.parseDurationMultiplier(explicitLengthCtx)
        }

        val notes = mutableListOf<NoteElement>()

        // Collect decorations and annotations from the grammar rule (before the '[')
        val grammarDecorations = mutableListOf<Decoration>()
        val grammarAnnotations = mutableListOf<String>()

        chordCtx.children?.forEach { child ->
            when (child) {
                is ABCParser.Decoration_altContext -> {
                    ParserUtils.parseDecoration(child)?.let { grammarDecorations.add(it) }
                }

                is ABCParser.Annotation_altContext -> {
                    child.CHORD_CONTENT()?.text?.let { grammarAnnotations.add(it) }
                }
            }
        }

        // Also capture any pending decorations/annotations from before the chord (standalone elements)
        val allDecorations = (pendingDecorations.toList() + grammarDecorations).toMutableList()
        val allChordAnnotations = (pendingAnnotations.toList() + grammarAnnotations).toMutableList()
        pendingAnnotations.clear()
        pendingDecorations.clear()

        chordCtx.chord_element()?.forEach { elementCtx ->
            val chordItemVisitor =
                object : ABCParserBaseVisitor<Unit>() {
                    override fun visitChordNote(ctx: ABCParser.ChordNoteContext) {
                        var note = buildNote(ctx.note_element())
                        if (explicitChordMultiplier != null) {
                            // If the note has no multiplier, use the chord's
                            if (note.durationMultiplier == DurationMultiplier.DEFAULT) {
                                note = note.copy(durationMultiplier = explicitChordMultiplier!!)
                            } else {
                                // Multiply note's multiplier by chord's explicit multiplier
                                val newNum = note.durationMultiplier.numerator * explicitChordMultiplier!!.numerator
                                val newDen = note.durationMultiplier.denominator * explicitChordMultiplier!!.denominator
                                note = note.copy(durationMultiplier = DurationMultiplier(newNum, newDen))
                            }
                        }
                        // Apply any note-level annotation found inside the chord
                        if (pendingAnnotations.isNotEmpty()) {
                            note = note.copy(annotations = note.annotations + pendingAnnotations)
                            pendingAnnotations.clear()
                        }
                        // Apply any note-level decoration found inside the chord
                        if (pendingDecorations.isNotEmpty()) {
                            note = note.copy(decorations = note.decorations + pendingDecorations)
                            pendingDecorations.clear()
                        }
                        notes.add(note)
                    }

                    override fun visitChordDecoration(ctx: ABCParser.ChordDecorationContext) {
                        ParserUtils.parseDecoration(ctx.decoration_alt())?.let { pendingDecorations.add(it) }
                    }

                    override fun visitChordAnnotation(ctx: ABCParser.ChordAnnotationContext) {
                        ctx.annotation_alt()?.CHORD_CONTENT()?.text?.let { anno ->
                            if (notes.isEmpty() && allChordAnnotations.isEmpty()) {
                                // If it's at the start of the chord and we don't have any yet, use it for the chord
                                allChordAnnotations.add(anno)
                            } else {
                                // Otherwise, it belongs to the NEXT note in the chord
                                pendingAnnotations.add(anno)
                            }
                        }
                    }
                }
            elementCtx.accept(chordItemVisitor)
        }

        val durationMultiplier = notes.firstOrNull()?.durationMultiplier ?: DurationMultiplier.DEFAULT

        // Combine all decorations: from pending + from grammar + from inside chord
        allDecorations.addAll(pendingDecorations)

        var finalNotes = notes.toList()
        if (chordCtx.tie() != null) {
            finalNotes = finalNotes.map { note ->
                val newTie = if (note.ties == TieType.END) TieType.BOTH else TieType.START
                note.copy(ties = newTie)
            }
        }

        elements.add(
            ChordElement(
                finalNotes,
                durationMultiplier,
                annotations = allChordAnnotations,
                decorations = allDecorations,
                line = line,
                column = col,
            ),
        )
        pendingAnnotations.clear()
        pendingDecorations.clear()
    }

    override fun visitAnnotation(ctx: ABCParser.AnnotationContext) {
        ctx.annotation_alt()?.CHORD_CONTENT()?.text?.let {
            pendingAnnotations.add(it)
        }
    }

    override fun visitDecoration(ctx: ABCParser.DecorationContext) {
        ParserUtils.parseDecoration(ctx.decoration_alt())?.let { pendingDecorations.add(it) }
    }

    override fun visitInlineField(ctx: ABCParser.InlineFieldContext) {
        val line = ctx.start.line
        val col = ctx.start.charPositionInLine
        val fullText =
            ctx
                .inline_field_alt()
                ?.text
                ?.removePrefix("[")
                ?.removeSuffix("]") ?: ""
        val colonIdx = fullText.indexOf(':')
        if (colonIdx != -1) {
            val key = fullText.substring(0, colonIdx).trim()
            val value = fullText.substring(colonIdx + 1).trim()
            val type = HeaderType.entries.find { it.key == key } ?: HeaderType.UNKNOWN

            if (type == HeaderType.LENGTH) {
                // We no longer track L: implicitly in the parser state.
                // It's processed downstream by MeasureQuantizer.
            }

            elements.add(InlineFieldElement(type, value, line = line, column = col))
        }
    }

    override fun visitStylesheet(ctx: ABCParser.StylesheetContext) {
        elements.add(
            DirectiveElement(ctx.stylesheet_directive_alt()?.text?.removePrefix("%%") ?: "", ctx.start.line, ctx.start.charPositionInLine),
        )
    }

    override fun visitOverlay(ctx: ABCParser.OverlayContext) {
        elements.add(OverlayElement(ctx.start.line, ctx.start.charPositionInLine))
    }

    override fun visitGraceGroup(ctx: ABCParser.GraceGroupContext) {
        val line = ctx.start.line
        val col = ctx.start.charPositionInLine
        val graceCtx = ctx.grace_group_alt()
        val notes = mutableListOf<NoteElement>()
        val graceNoteVisitor =
            object : ABCParserBaseVisitor<Unit>() {
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
        elements.add(BrokenRhythmMarkerElement(text, ctx.start.line, ctx.start.charPositionInLine))
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
        if (text == "!") {
            elements.add(SpacerElement("!", line, col))
            return
        }
        if (text == "-" && !isStrict) {
            val targetIndex = elements.indices.reversed().firstOrNull { elements[it] !is SpacerElement }
            if (targetIndex != null) {
                val el = elements[targetIndex]
                if (el is NoteElement) {
                    if (el.ties == TieType.NONE) {
                        elements[targetIndex] = el.copy(ties = TieType.START)
                    }
                    return
                }
            }
        }
        elements.add(SpacerElement(text, line, col))
    }

    override fun visitChordMisc(ctx: ABCParser.ChordMiscContext) {
        val text = ctx.text ?: ""
        if (text == "!") {
            // Standalone ! inside a chord is weird but we should preserve it
            // However, the AST for ChordElement doesn't have a list of all elements,
            // just notes, duration, annotation, decorations.
            // If we want to preserve formatting markers INSIDE chords, we need to extend ChordElement.
            // For now, let's just ignore it or attach it as a decoration if it's !
        }
    }

    private fun emitBarLine(
        type: BarLineType,
        ctx: org.antlr.v4.runtime.ParserRuleContext,
    ) {
        elements.add(
            BarLineElement(
                type = type,
                line = ctx.start.line,
                column = ctx.start.charPositionInLine,
            ),
        )
    }

    override fun visitBarSingle(ctx: ABCParser.BarSingleContext) = emitBarLine(BarLineType.SINGLE, ctx)

    override fun visitBarThinDouble(ctx: ABCParser.BarThinDoubleContext) = emitBarLine(BarLineType.DOUBLE, ctx)

    override fun visitBarFinal(ctx: ABCParser.BarFinalContext) = emitBarLine(BarLineType.FINAL, ctx)

    override fun visitBarDouble(ctx: ABCParser.BarDoubleContext) = emitBarLine(BarLineType.DOUBLE, ctx)

    override fun visitBarThickDouble(ctx: ABCParser.BarThickDoubleContext) = emitBarLine(BarLineType.DOUBLE, ctx)

    override fun visitBarRepStart(ctx: ABCParser.BarRepStartContext) = emitBarLine(BarLineType.REPEAT_START, ctx)

    override fun visitBarRepEnd(ctx: ABCParser.BarRepEndContext) = emitBarLine(BarLineType.REPEAT_END, ctx)

    override fun visitBarRepEndAlt(ctx: ABCParser.BarRepEndAltContext) = emitBarLine(BarLineType.REPEAT_END, ctx)

    override fun visitBarRepEndTune(ctx: ABCParser.BarRepEndTuneContext) = emitBarLine(BarLineType.REPEAT_END, ctx)

    override fun visitBarRepDbl(ctx: ABCParser.BarRepDblContext) = emitBarLine(BarLineType.REPEAT_BOTH, ctx)

    override fun visitBarRepDblAlt(ctx: ABCParser.BarRepDblAltContext) = emitBarLine(BarLineType.REPEAT_BOTH, ctx)

    override fun visitBarRepDblTune(ctx: ABCParser.BarRepDblTuneContext) = emitBarLine(BarLineType.REPEAT_BOTH, ctx)

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
            val value =
                ctx.children
                    ?.filter {
                        it is TerminalNode &&
                            (it.symbol.type == ABCLexer.FIELD_CONTENT || it.symbol.type == ABCLexer.FIELD_BACKSLASH)
                    }?.joinToString("") { it.text }
                    ?.trim() ?: ""

            when (id) {
                "L" -> {
                    // Do nothing in parser
                }

                "M" -> {
                    currentMeter = ParserUtils.parseMeter(value)
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

    // Default Length logic removed; shifted to MeasureQuantizer/Timeline

    private fun buildNote(ctx: ABCParser.Note_elementContext): NoteElement {
        val line = ctx.start.line
        val col = ctx.start.charPositionInLine
        val pitchText = ctx.note_pitch()?.text ?: ""

        val step = extractNoteStep(pitchText)
        val octave = extractNoteOctave(pitchText, ctx)

        if (pitchText.isNotEmpty()) {
            lastNoteStep = step
            lastNoteOctave = octave
        }

        val accidental = extractAccidental(ctx)
        val noteLength = ctx.note_length()
        val durationMultiplier = ParserUtils.parseDurationMultiplier(noteLength)
        val tie = if (ctx.tie() != null) TieType.START else TieType.NONE
        val decorations = ctx.decoration_alt()?.mapNotNull { ParserUtils.parseDecoration(it) } ?: emptyList()

        return NoteElement(
            Pitch(step, octave, accidental),
            durationMultiplier,
            tie,
            decorations = decorations,
            accidental = accidental,
            line = line,
            column = col,
        )
    }

    private fun extractNoteStep(pitchText: String): NoteStep {
        if (pitchText.isEmpty()) return lastNoteStep ?: NoteStep.C
        return when (pitchText[0].uppercaseChar()) {
            'C' -> NoteStep.C
            'D' -> NoteStep.D
            'E' -> NoteStep.E
            'F' -> NoteStep.F
            'G' -> NoteStep.G
            'A' -> NoteStep.A
            'B' -> NoteStep.B
            else -> NoteStep.C
        }
    }

    private fun extractNoteOctave(
        pitchText: String,
        ctx: ABCParser.Note_elementContext,
    ): Int {
        var octave =
            if (pitchText.isNotEmpty()) {
                if (pitchText[0].isLowerCase()) 5 else 4
            } else {
                lastNoteOctave ?: 4
            }

        ctx.octave_modifier()?.children?.forEach { child ->
            if (child is TerminalNode) {
                when (child.symbol.type) {
                    ABCLexer.OCTAVE_UP -> octave++
                    ABCLexer.OCTAVE_DOWN -> octave--
                }
            }
        }
        return octave
    }

    private fun extractAccidental(ctx: ABCParser.Note_elementContext): Accidental? =
        ctx.accidental()?.let {
            val child = it.getChild(0)
            if (child is TerminalNode) ParserUtils.parseAccidental(child.symbol.type) else null
        }

    private fun buildRest(ctx: ABCParser.Rest_elementContext): RestElement {
        val line = ctx.start.line
        val col = ctx.start.charPositionInLine
        val restChar = ctx.REST().text
        val durationMultiplier =
            if (restChar.equals("Z", ignoreCase = false)) {
                // Multi-measure rests (Z) are preserved structurally.
                // The fraction acts as a measure count (e.g. Z4 implies 4 measures).
                ctx.note_length()?.let {
                    ParserUtils.parseDurationMultiplier(it)
                } ?: DurationMultiplier.DEFAULT
            } else {
                ParserUtils.parseDurationMultiplier(ctx.note_length())
            }
        val isHidden = restChar.equals("x", ignoreCase = true)
        val decorations = ctx.decoration_alt()?.mapNotNull { ParserUtils.parseDecoration(it) } ?: emptyList()
        return RestElement(durationMultiplier, isHidden, decorations, line = line, column = col)
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
}
