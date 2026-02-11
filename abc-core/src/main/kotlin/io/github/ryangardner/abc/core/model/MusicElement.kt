package io.github.ryangardner.abc.core.model

public sealed interface MusicElement {
    public val duration: NoteDuration
}

public data class NoteElement @JvmOverloads constructor(
    public val pitch: Pitch,
    public val length: NoteDuration,
    public val ties: TieType = TieType.NONE,
    public val decorations: List<Decoration> = emptyList(),
    public val accidental: Accidental? = null,
    public val annotation: String? = null
) : MusicElement {
    override val duration: NoteDuration get() = length
}

public data class ChordElement @JvmOverloads constructor(
    public val notes: List<NoteElement>,
    override val duration: NoteDuration,
    public val annotation: String? = null,
    public val decorations: List<Decoration> = emptyList()
) : MusicElement

public data class BarLineElement @JvmOverloads constructor(
    public val type: BarLineType,
    public val repeatCount: Int = 0
) : MusicElement {
    override val duration: NoteDuration = NoteDuration(0, 1)
}

public data class InlineFieldElement(
    public val fieldType: HeaderType,
    public val value: String
) : MusicElement {
    override val duration: NoteDuration = NoteDuration(0, 1)
}

public data class RestElement @JvmOverloads constructor(
    override val duration: NoteDuration,
    public val isInvisible: Boolean = false, // x or X
    public val decorations: List<Decoration> = emptyList(),
    public val annotation: String? = null
) : MusicElement

public data class DirectiveElement(
    public val content: String
) : MusicElement {
    override val duration: NoteDuration = NoteDuration(0, 1)
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
public data class SymbolOther(val text: String) : SymbolItem // For robustness

public data class SymbolLineElement(
    public val items: List<SymbolItem>
) : MusicElement {
    override val duration: NoteDuration = NoteDuration(0, 1)
}

public data class TextBlockElement(
    public val content: List<String>
) : MusicElement {
    override val duration: NoteDuration = NoteDuration(0, 1)
}

public data class BodyHeaderElement(
    public val key: String,
    public val value: String
) : MusicElement {
    override val duration: NoteDuration = NoteDuration(0, 1)
}

public data class SlurElement(
    public val isStart: Boolean
) : MusicElement {
    override val duration: NoteDuration = NoteDuration(0, 1)
}

public data class TupletElement(
    public val p: Int, // number of notes to be played
    public val q: Int? = null, // in the time of q notes
    public val r: Int? = null  // for the next r notes
) : MusicElement {
    override val duration: NoteDuration = NoteDuration(0, 1)
}

public data class GraceNoteElement @JvmOverloads constructor(
    public val notes: List<NoteElement>,
    public val isAcciaccatura: Boolean = false // {/g}
) : MusicElement {
    override val duration: NoteDuration = NoteDuration(0, 1)
}

public data class SpacerElement(
    public val text: String
) : MusicElement {
    override val duration: NoteDuration = NoteDuration(0, 1)
}

public object OverlayElement : MusicElement {
    override val duration: NoteDuration = NoteDuration(0, 1)
}
public data class LyricElement(
    public val content: String
) : MusicElement {
    override val duration: NoteDuration = NoteDuration(0, 1)
}

public data class VariantElement(
    public val variants: List<Int>
) : MusicElement {
    override val duration: NoteDuration = NoteDuration(0, 1)
}

public data class PartElement(
    public val name: String
) : MusicElement {
    override val duration: NoteDuration = NoteDuration(0, 1)
}
