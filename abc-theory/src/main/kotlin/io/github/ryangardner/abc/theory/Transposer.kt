package io.github.ryangardner.abc.theory

import io.github.ryangardner.abc.core.model.*

object Transposer {
    /**
     * Transposes the given tune by the specified number of semitones.
     * Uses the Circle of Fifths to determine the new key signature.
     * Java usage: Transposer.transpose(tune, 2)
     */
    @JvmStatic
    fun transpose(tune: AbcTune, semitones: Int): AbcTune {
        if (semitones == 0) return tune

        val newHeader = transposeHeader(tune.header, semitones)
        
        // Find the step difference between the old tonic and the new tonic
        val oldKey = tune.header.key
        val newKey = newHeader.key
        val stepDiff = (newKey.root.step.ordinal - oldKey.root.step.ordinal + 7) % 7

        // Get the new key's accidentals count (sharps/flats) to help with context-aware note transposition
        val bestKey = CircleOfFifths.getBestKey(newKey.tonicSemitones, newKey.mode)
        
        val newBody = transposeBody(tune.body, semitones, stepDiff, bestKey.accidentalsCount)
        
        // As per architecture: If physical notes changed, clear visualTranspose
        val newMetadata = tune.metadata.copy(visualTranspose = null)
        
        return tune.copy(header = newHeader, body = newBody, metadata = newMetadata)
    }

    private fun transposeHeader(header: TuneHeader, semitones: Int): TuneHeader {
        return header.copy(key = transposeKey(header.key, semitones))
    }

    private fun transposeKey(key: KeySignature, semitones: Int): KeySignature {
        val newTonicSemitones = ((key.tonicSemitones + semitones) % 12 + 12) % 12
        val bestKey = CircleOfFifths.getBestKey(newTonicSemitones, key.mode)
        
        return key.copy(
            root = KeyRoot(
                bestKey.tonicStep,
                CircleOfFifths.semitonesToAccidental(bestKey.tonicAccidental) ?: Accidental.NATURAL
            )
        )
    }

    private fun transposeBody(body: TuneBody, semitones: Int, stepDiff: Int, newKeyAccidentals: Int): TuneBody {
        return body.copy(elements = body.elements.map { transposeElement(it, semitones, stepDiff, newKeyAccidentals) })
    }

    private fun transposeElement(element: MusicElement, semitones: Int, stepDiff: Int, newKeyAccidentals: Int): MusicElement {
        return when (element) {
            is NoteElement -> transposeNote(element, semitones, stepDiff, newKeyAccidentals)
            is ChordElement -> element.copy(notes = element.notes.map { transposeNote(it, semitones, stepDiff, newKeyAccidentals) })
            is BarLineElement, is InlineFieldElement, is RestElement, is DirectiveElement -> element
            else -> element
        }
    }

    private fun transposeNote(note: NoteElement, semitones: Int, stepDiff: Int, newKeyAccidentals: Int): NoteElement {
        val newPitch = transposePitch(note.pitch, semitones, stepDiff, newKeyAccidentals)
        return note.copy(pitch = newPitch, accidental = newPitch.accidental)
    }

    private fun transposePitch(pitch: Pitch, semitones: Int, stepDiff: Int, newKeyAccidentals: Int): Pitch {
        val oldTotalSemitones = pitch.totalSemitones
        val newTotalSemitones = oldTotalSemitones + semitones
        
        // 1. Calculate New Step: First, shift the diatonic step
        val newStepOrdinal = (pitch.step.ordinal + stepDiff) % 7
        val newStep = NoteStep.values()[newStepOrdinal]
        
        // 2. Calculate New Alteration: Then, calculate the necessary accidental to match the target semitone pitch.
        val baseSemitones = CircleOfFifths.stepToSemitones(newStep)
        val diff = newTotalSemitones - baseSemitones
        var newOctave = Math.floorDiv(diff, 12)
        var accidentalSemitones = Math.floorMod(diff, 12)
        if (accidentalSemitones > 6) {
            accidentalSemitones -= 12
            newOctave += 1
        }

        // 3. Context Check: compare against the New Key Signature.
        val keyAccidental = CircleOfFifths.getAccidentalForStep(newStep, newKeyAccidentals)
        
        val finalAccidental = if (accidentalSemitones == keyAccidental) {
            null // The key already provides this accidental
        } else if (accidentalSemitones == 0) {
            Accidental.NATURAL
        } else {
            CircleOfFifths.semitonesToAccidental(accidentalSemitones)
        }
        
        return Pitch(newStep, newOctave, finalAccidental)
    }
}