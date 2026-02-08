package io.github.ryangardner.abc.core.model

data class Pitch @JvmOverloads constructor(
    val step: NoteStep,
    val octave: Int,
    val accidental: Accidental? = null
)
