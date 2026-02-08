package io.github.ryangardner.abc.core.model

sealed interface MusicElement {
    val duration: NoteDuration
}

data class NoteElement @JvmOverloads constructor(
    val pitch: Pitch,
    val length: NoteDuration,
    val ties: TieType = TieType.NONE,
    val decorations: List<Decoration> = emptyList(),
    val accidental: Accidental? = null
) : MusicElement {
    override val duration: NoteDuration get() = length
}

data class ChordElement @JvmOverloads constructor(
    val notes: List<NoteElement>,
    override val duration: NoteDuration,
    val annotation: String? = null,
    val decorations: List<Decoration> = emptyList()
) : MusicElement

data class BarLineElement @JvmOverloads constructor(
    val type: BarLineType,
    val repeatCount: Int = 0
) : MusicElement {
    override val duration: NoteDuration = NoteDuration(0, 1)
}

data class InlineFieldElement(
    val fieldType: HeaderType,
    val value: String
) : MusicElement {
    override val duration: NoteDuration = NoteDuration(0, 1)
}

data class RestElement @JvmOverloads constructor(
    override val duration: NoteDuration,
    val isInvisible: Boolean = false // x or X
) : MusicElement

data class DirectiveElement(
    val content: String
) : MusicElement {
    override val duration: NoteDuration = NoteDuration(0, 1)
}
