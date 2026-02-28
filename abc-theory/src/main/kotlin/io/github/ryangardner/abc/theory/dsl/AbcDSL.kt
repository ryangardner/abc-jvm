package io.github.ryangardner.abc.theory.dsl
import io.github.ryangardner.abc.core.model.AbcTune
import io.github.ryangardner.abc.core.model.Accidental
import io.github.ryangardner.abc.core.model.BarLineElement
import io.github.ryangardner.abc.core.model.BarLineType
import io.github.ryangardner.abc.core.model.ChordElement
import io.github.ryangardner.abc.core.model.MusicElement
import io.github.ryangardner.abc.core.model.DurationMultiplier
import io.github.ryangardner.abc.core.model.NoteDuration
import io.github.ryangardner.abc.core.model.NoteElement
import io.github.ryangardner.abc.core.model.NoteStep
import io.github.ryangardner.abc.core.model.Pitch
import io.github.ryangardner.abc.core.model.TimeSignature
import io.github.ryangardner.abc.core.model.TuneBody
import io.github.ryangardner.abc.core.model.TuneHeader
import io.github.ryangardner.abc.core.model.TuneMetadata
import io.github.ryangardner.abc.theory.util.KeyParserUtil

/**
 * DSL for building [AbcTune] objects in a type-safe and readable way.
 *
 * Usage example:
 * ```kotlin
 * val tune = abcTune {
 *     header {
 *         title = "My Tune"
 *         key = "G"
 *     }
 *     body {
 *         note("G", DurationMultiplier(1, 4))
 *         bar()
 *     }
 * }
 * ```
 */
@DslMarker
public annotation class AbcDsl

/**
 * Root builder for an ABC tune.
 */
@AbcDsl
public class AbcTuneBuilder {
    private var reference: Int = 1
    private var title: String = "Untitled"
    private var key: String = "C"
    private var meter: String = "4/4"
    private var length: String = "1/8"
    private val elements: MutableList<MusicElement> = mutableListOf<MusicElement>()

    public fun header(block: HeaderBuilder.() -> Unit) {
        val builder = HeaderBuilder().apply(block)
        reference = builder.reference
        title = builder.title
        key = builder.key
        meter = builder.meter
        length = builder.length
    }

    public fun body(block: BodyBuilder.() -> Unit) {
        val builder = BodyBuilder().apply(block)
        elements.addAll(builder.elements)
    }

    public fun build(): AbcTune {
        val meterSig = parseMeter(meter)
        val lengthDur = parseLength(length)
        val header =
            TuneHeader(
                reference = reference,
                title = listOf(title),
                key = KeyParserUtil.parse(key),
                meter = meterSig,
                length = lengthDur,
            )
        return AbcTune(header, TuneBody(elements), TuneMetadata())
    }

    private fun parseMeter(text: String): TimeSignature {
        val parts = text.split("/")
        return if (parts.size == 2) {
            TimeSignature(parts[0].toIntOrNull() ?: 4, parts[1].toIntOrNull() ?: 4)
        } else {
            TimeSignature(4, 4)
        }
    }

    private fun parseLength(text: String): NoteDuration {
        val parts = text.split("/")
        return if (parts.size == 2) {
            NoteDuration(parts[0].toIntOrNull() ?: 1, parts[1].toIntOrNull() ?: 8)
        } else {
            NoteDuration(1, 8)
        }
    }
}

/**
 * Builder for the tune header.
 */
@AbcDsl
public class HeaderBuilder {
    /** The reference number (X:). */
    public var reference: Int = 1

    /** The title (T:). */
    public var title: String = "Untitled"

    /** The key signature (K:). */
    public var key: String = "C"

    /** The time signature (M:). */
    public var meter: String = "4/4"

    /** The default note length (L:). */
    public var length: String = "1/8"
}

/**
 * Builder for the tune body.
 */
@AbcDsl
public class BodyBuilder {
    public val elements: MutableList<MusicElement> = mutableListOf<MusicElement>()

    /**
     * Adds a note to the body.
     *
     * @param step The note step (e.g., "C", "D", "E").
     * @param duration The note duration.
     * @param octave The octave (default is 4).
     * @param accidental The accidental (optional).
     */
    public fun note(
        step: String,
        durationMultiplier: DurationMultiplier = DurationMultiplier(1, 1),
        octave: Int = 4,
        accidental: Accidental? = null,
    ) {
        val noteStep = NoteStep.valueOf(step.uppercase())
        elements.add(NoteElement(Pitch(noteStep, octave, accidental), durationMultiplier))
    }

    /**
     * Adds a bar line.
     *
     * @param type The type of bar line.
     */
    public fun bar(type: BarLineType = BarLineType.SINGLE) {
        elements.add(BarLineElement(type))
    }

    /**
     * Adds a sounding chord.
     *
     * @param annotation An optional chord symbol annotation (e.g., "Am7").
     * @param block The builder block for the chord's notes.
     */
    public fun chord(
        vararg annotations: String,
        block: ChordBuilder.() -> Unit,
    ) {
        val builder = ChordBuilder().apply(block)
        elements.add(ChordElement(builder.notes, DurationMultiplier(1, 1), annotations.toList()))
    }
}

/**
 * Builder for a sounding chord.
 */
@AbcDsl
public class ChordBuilder {
    public val notes: MutableList<NoteElement> = mutableListOf<NoteElement>()

    /**
     * Adds a note to the chord.
     */
    public fun note(
        step: String,
        octave: Int = 4,
        accidental: Accidental? = null,
    ) {
        val noteStep = NoteStep.valueOf(step.uppercase())
        notes.add(NoteElement(Pitch(noteStep, octave, accidental), DurationMultiplier(1, 1)))
    }
}

/**
 * Entry point for the ABC DSL.
 *
 * @param block The builder block.
 * @return A fully constructed [AbcTune].
 */
public fun abcTune(block: AbcTuneBuilder.() -> Unit): AbcTune = AbcTuneBuilder().apply(block).build()
