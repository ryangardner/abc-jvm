package io.github.ryangardner.abc.parser

import io.github.ryangardner.abc.antlr.ABCLexer
import io.github.ryangardner.abc.antlr.ABCParser
import io.github.ryangardner.abc.antlr.ABCParserBaseVisitor
import io.github.ryangardner.abc.core.model.Accidental
import io.github.ryangardner.abc.core.model.BarLineElement
import io.github.ryangardner.abc.core.model.BodyHeaderElement
import io.github.ryangardner.abc.core.model.ChordElement
import io.github.ryangardner.abc.core.model.Decoration
import io.github.ryangardner.abc.core.model.DirectiveElement
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
    private var currentDefaultLength = header.length
    private var currentMeter = header.meter
    private var hasExplicitLength = header.headers.any { it.first == "L" }

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
        val text =
            ctx
                .tuplet_element()
                .TUPLET_START()
                .text
                .substring(1) // remove (
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
            explicitChordMultiplier = ParserUtils.calculateDuration(explicitLengthCtx.text, NoteDuration(1, 1))
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
                            note = note.copy(length = note.length * explicitChordMultiplier!!)
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

        val duration = notes.firstOrNull()?.length ?: currentDefaultLength

        // Combine all decorations: from pending + from grammar + from inside chord
        elements.add(
            ChordElement(
                notes,
                duration,
                annotations = allChordAnnotations,
                decorations = allDecorations + pendingDecorations,
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
                currentDefaultLength = ParserUtils.parseLength(value)
                hasExplicitLength = true
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

        val lastRhythmicIndex =
            elements.indices.reversed().firstOrNull {
                val el = elements[it]
                el !is SpacerElement && el !is BarLineElement && el !is SlurElement
            }

        var applied = false
        if (lastRhythmicIndex != null) {
            val el = elements[lastRhythmicIndex]
            if (el is NoteElement) {
                elements[lastRhythmicIndex] = el.copy(brokenRhythm = text)
                applied = true
            } else if (el is RestElement) {
                elements[lastRhythmicIndex] = el.copy(brokenRhythm = text)
                applied = true
            } else if (el is ChordElement) {
                elements[lastRhythmicIndex] = el.copy(brokenRhythm = text)
                applied = true
            }
        }

        if (!applied) {
            // Fallback if no preceding rhythmic element
            elements.add(SpacerElement(text, ctx.start.line, ctx.start.charPositionInLine))
        }
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

    override fun visitBar(ctx: ABCParser.BarContext) {
        val firstChild = ctx.getChild(0)
        if (firstChild is TerminalNode) {
            val tokenType = firstChild.symbol.type
            val type = ParserUtils.parseBarLineType(tokenType)
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
            val value =
                ctx.children
                    ?.filter {
                        it is TerminalNode &&
                            (it.symbol.type == ABCLexer.FIELD_CONTENT || it.symbol.type == ABCLexer.FIELD_BACKSLASH)
                    }?.joinToString("") { it.text }
                    ?.trim() ?: ""

            when (id) {
                "L" -> {
                    currentDefaultLength = ParserUtils.parseLength(value)
                    hasExplicitLength = true
                }

                "M" -> {
                    currentMeter = ParserUtils.parseMeter(value)
                    // Reset Default Length logic when Meter changes in body,
                    // ONLY if we don't have an explicit L: in this tune.
                    if (!hasExplicitLength) {
                        currentDefaultLength = calculateDefaultLength(currentMeter)
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

    private fun calculateDefaultLength(meter: TimeSignature): NoteDuration {
        if (meter.isNone) return NoteDuration(1, 8)
        return if (meter.toDouble() < 0.75) NoteDuration(1, 16) else NoteDuration(1, 8)
    }

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
        val duration = noteLength?.let { ParserUtils.calculateDuration(it.text, currentDefaultLength) } ?: currentDefaultLength
        val tie = if (ctx.tie() != null) TieType.START else TieType.NONE
        val decorations = ctx.decoration_alt()?.mapNotNull { ParserUtils.parseDecoration(it) } ?: emptyList()

        return NoteElement(
            Pitch(step, octave, accidental),
            duration,
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
        val duration =
            if (restChar.equals("Z", ignoreCase = false)) {
                val measureDuration = NoteDuration.simplify(currentMeter.numerator.toLong(), currentMeter.denominator.toLong())
                ctx.note_length()?.let {
                    val multiplier = ParserUtils.calculateDuration(it.text, NoteDuration(1, 1))
                    measureDuration * multiplier
                } ?: measureDuration
            } else {
                ctx.note_length()?.let { ParserUtils.calculateDuration(it.text, currentDefaultLength) } ?: currentDefaultLength
            }
        val isHidden = restChar.equals("x", ignoreCase = true)
        val decorations = ctx.decoration_alt()?.mapNotNull { ParserUtils.parseDecoration(it) } ?: emptyList()
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
}
