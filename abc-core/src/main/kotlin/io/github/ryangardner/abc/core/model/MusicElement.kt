package io.github.ryangardner.abc.core.model

public sealed interface MusicElement {
    public val duration: NoteDuration
    public val line: Int
    public val column: Int
    public val brokenRhythm: String? get() = null

    public fun withoutLocation(): MusicElement
}

public data class NoteElement @JvmOverloads constructor(
    public val pitch: Pitch,
    public val length: NoteDuration,
    public val ties: TieType = TieType.NONE,
    public val decorations: List<Decoration> = emptyList(),
    public val accidental: Accidental? = null,
    public val annotations: List<String> = emptyList(),
    override val brokenRhythm: String? = null,
    override val line: Int = -1,
    override val column: Int = -1
) : MusicElement {
    override val duration: NoteDuration get() = length
    override fun withoutLocation(): NoteElement = copy(line = -1, column = -1)
}

public data class ChordElement @JvmOverloads constructor(
    public val notes: List<NoteElement>,
    override val duration: NoteDuration,
    public val annotations: List<String> = emptyList(),
    public val decorations: List<Decoration> = emptyList(),
    override val brokenRhythm: String? = null,
    override val line: Int = -1,
    override val column: Int = -1
) : MusicElement {
    override fun withoutLocation(): ChordElement = copy(line = -1, column = -1, notes = notes.map { it.withoutLocation() })
}

public data class BarLineElement @JvmOverloads constructor(
    public val type: BarLineType,
    public val repeatCount: Int = 0,
    override val line: Int = -1,
    override val column: Int = -1
) : MusicElement {
    override val duration: NoteDuration = NoteDuration(0, 1)
    override fun withoutLocation(): BarLineElement = copy(line = -1, column = -1)
}

public data class InlineFieldElement @JvmOverloads constructor(
    public val fieldType: HeaderType,
    public val value: String,
    override val line: Int = -1,
    override val column: Int = -1
) : MusicElement {
    override val duration: NoteDuration = NoteDuration(0, 1)
    override fun withoutLocation(): InlineFieldElement = copy(line = -1, column = -1)
}

public data class RestElement @JvmOverloads constructor(
    override val duration: NoteDuration,
    public val isInvisible: Boolean = false, // x or X
    public val decorations: List<Decoration> = emptyList(),
    public val annotations: List<String> = emptyList(),
    override val brokenRhythm: String? = null,
    override val line: Int = -1,
    override val column: Int = -1
) : MusicElement {
    override fun withoutLocation(): RestElement = copy(line = -1, column = -1)
}

public data class DirectiveElement @JvmOverloads constructor(
    public val content: String,
    override val line: Int = -1,
    override val column: Int = -1
) : MusicElement {
    override val duration: NoteDuration = NoteDuration(0, 1)
    override fun withoutLocation(): DirectiveElement = copy(line = -1, column = -1)
}

public sealed interface SymbolItem
public data class SymbolChord(val name: String) : SymbolItem
public data class SymbolDecoration(val name: String) : SymbolItem
public object SymbolSkip : SymbolItem {
    override fun toString(): String = "*"
}
public object SymbolBar : SymbolItem {
    override fun toString(): String = "|"
}
public data class SymbolOther(val text: String) : SymbolItem 

public data class SymbolLineElement @JvmOverloads constructor(
    public val items: List<SymbolItem>,
    override val line: Int = -1,
    override val column: Int = -1
) : MusicElement {
    override val duration: NoteDuration = NoteDuration(0, 1)
    override fun withoutLocation(): SymbolLineElement = copy(line = -1, column = -1)
}

public data class TextBlockElement @JvmOverloads constructor(
    public val content: List<String>,
    override val line: Int = -1,
    override val column: Int = -1
) : MusicElement {
    override val duration: NoteDuration = NoteDuration(0, 1)
    override fun withoutLocation(): TextBlockElement = copy(line = -1, column = -1)
}

public data class BodyHeaderElement @JvmOverloads constructor(
    public val key: String,
    public val value: String,
    override val line: Int = -1,
    override val column: Int = -1
) : MusicElement {
    override val duration: NoteDuration = NoteDuration(0, 1)
    override fun withoutLocation(): BodyHeaderElement = copy(line = -1, column = -1)
}

public data class SlurElement @JvmOverloads constructor(
    public val isStart: Boolean,
    override val line: Int = -1,
    override val column: Int = -1
) : MusicElement {
    override val duration: NoteDuration = NoteDuration(0, 1)
    override fun withoutLocation(): SlurElement = copy(line = -1, column = -1)
}

public data class TupletElement @JvmOverloads constructor(
    public val p: Int, // number of notes to be played
    public val q: Int? = null, // in the time of q notes
    public val r: Int? = null,  // for the next r notes
    override val line: Int = -1,
    override val column: Int = -1
) : MusicElement {
    override val duration: NoteDuration = NoteDuration(0, 1)
    override fun withoutLocation(): TupletElement = copy(line = -1, column = -1)
}

public data class GraceNoteElement @JvmOverloads constructor(
    public val notes: List<NoteElement>,
    public val isAcciaccatura: Boolean = false, // {/g}
    override val line: Int = -1,
    override val column: Int = -1
) : MusicElement {
    override val duration: NoteDuration = NoteDuration(0, 1)
    override fun withoutLocation(): GraceNoteElement = copy(line = -1, column = -1, notes = notes.map { it.withoutLocation() })
}

public data class SpacerElement @JvmOverloads constructor(
    public val text: String,
    override val line: Int = -1,
    override val column: Int = -1
) : MusicElement {
    override val duration: NoteDuration = NoteDuration(0, 1)
    override fun withoutLocation(): SpacerElement = copy(line = -1, column = -1)
}

public data class OverlayElement @JvmOverloads constructor(
    override val line: Int = -1,
    override val column: Int = -1
) : MusicElement {
    override val duration: NoteDuration = NoteDuration(0, 1)
    override fun withoutLocation(): OverlayElement = copy(line = -1, column = -1)
}

public data class LyricElement @JvmOverloads constructor(
    public val content: String,
    override val line: Int = -1,
    override val column: Int = -1
) : MusicElement {
    override val duration: NoteDuration = NoteDuration(0, 1)
    override fun withoutLocation(): LyricElement = copy(line = -1, column = -1)
}

public data class VariantElement @JvmOverloads constructor(
    public val variants: List<Int>,
    public val prefix: String = "[",
    override val line: Int = -1,
    override val column: Int = -1
) : MusicElement {
    override val duration: NoteDuration = NoteDuration(0, 1)
    override fun withoutLocation(): VariantElement = copy(line = -1, column = -1)
}

public data class PartElement @JvmOverloads constructor(
    public val name: String,
    override val line: Int = -1,
    override val column: Int = -1
) : MusicElement {
    override val duration: NoteDuration = NoteDuration(0, 1)
    override fun withoutLocation(): PartElement = copy(line = -1, column = -1)
}
