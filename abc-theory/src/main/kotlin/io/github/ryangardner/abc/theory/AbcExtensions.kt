package io.github.ryangardner.abc.theory

import io.github.ryangardner.abc.core.model.AbcTune

/**
 * Idiomatic Kotlin extension. 
 * Performs semantic transposition (changes the underlying note data and key signature).
 */
public fun AbcTune.transpose(semitones: Int): AbcTune = Transposer.transpose(this, semitones)

/**
 * Idiomatic Kotlin extension.
 * Performs visual transposition (changes only the rendering hint, leaves notes untouched).
 * Mimics abcjs client-side behavior.
 */
public fun AbcTune.setVisualTranspose(semitones: Int): AbcTune {
    return this.copy(metadata = this.metadata.copy(visualTranspose = semitones))
}
