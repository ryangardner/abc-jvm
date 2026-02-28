package io.github.ryangardner.abc.theory
import io.github.ryangardner.abc.core.model.AbcTune
import io.github.ryangardner.abc.core.model.Accidental
import io.github.ryangardner.abc.core.model.BarLineElement
import io.github.ryangardner.abc.core.model.BarLineType
import io.github.ryangardner.abc.core.model.KeyMode
import io.github.ryangardner.abc.core.model.KeyRoot
import io.github.ryangardner.abc.core.model.KeySignature
import io.github.ryangardner.abc.core.model.DurationMultiplier
import io.github.ryangardner.abc.core.model.NoteDuration
import io.github.ryangardner.abc.core.model.NoteElement
import io.github.ryangardner.abc.core.model.NoteStep
import io.github.ryangardner.abc.core.model.PartElement
import io.github.ryangardner.abc.core.model.Pitch
import io.github.ryangardner.abc.core.model.TimeSignature
import io.github.ryangardner.abc.core.model.TuneBody
import io.github.ryangardner.abc.core.model.TuneHeader
import io.github.ryangardner.abc.core.model.TuneMetadata
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RepeatExpanderTest {
    @Test
    fun testSimpleRepeat() {
        // C D E F |: G A B c :| C4 |]
        val elements =
            listOf(
                NoteElement(Pitch(NoteStep.C, 4), DurationMultiplier(1, 4)),
                NoteElement(Pitch(NoteStep.D, 4), DurationMultiplier(1, 4)),
                NoteElement(Pitch(NoteStep.E, 4), DurationMultiplier(1, 4)),
                NoteElement(Pitch(NoteStep.F, 4), DurationMultiplier(1, 4)),
                BarLineElement(BarLineType.REPEAT_START),
                NoteElement(Pitch(NoteStep.G, 4), DurationMultiplier(1, 4)),
                NoteElement(Pitch(NoteStep.A, 4), DurationMultiplier(1, 4)),
                NoteElement(Pitch(NoteStep.B, 4), DurationMultiplier(1, 4)),
                NoteElement(Pitch(NoteStep.C, 5), DurationMultiplier(1, 4)),
                BarLineElement(BarLineType.REPEAT_END),
                NoteElement(Pitch(NoteStep.C, 4), DurationMultiplier(1, 1)),
                BarLineElement(BarLineType.FINAL),
            )

        val tune =
            AbcTune(
                header =
                    TuneHeader(
                        1,
                        listOf("Test"),
                        KeySignature(KeyRoot(NoteStep.C, Accidental.NATURAL), KeyMode.IONIAN),
                        TimeSignature(4, 4),
                        NoteDuration(1, 4),
                        null,
                        emptyList(),
                        emptyMap(),
                        "2.1",
                    ),
                body = TuneBody(elements),
                metadata = TuneMetadata(),
            )

        val expanded = RepeatExpander.expand(tune)

        val noteCount = expanded.filterIsInstance<NoteElement>().size
        // Original: 4 (before repeat) + 4 (inside repeat) + 1 (after repeat) = 9
        // Expanded: 4 + 4 + 4 + 1 = 13 notes
        assertEquals(13, noteCount, "Note count mismatch after expansion")
    }

    @Test
    fun testRepeatFromBeginning() {
        // C D E F | G A B c :|
        val elements =
            listOf(
                NoteElement(Pitch(NoteStep.C, 4), DurationMultiplier(1, 4)),
                NoteElement(Pitch(NoteStep.D, 4), DurationMultiplier(1, 4)),
                NoteElement(Pitch(NoteStep.E, 4), DurationMultiplier(1, 4)),
                NoteElement(Pitch(NoteStep.F, 4), DurationMultiplier(1, 4)),
                BarLineElement(BarLineType.SINGLE),
                NoteElement(Pitch(NoteStep.G, 4), DurationMultiplier(1, 4)),
                NoteElement(Pitch(NoteStep.A, 4), DurationMultiplier(1, 4)),
                NoteElement(Pitch(NoteStep.B, 4), DurationMultiplier(1, 4)),
                NoteElement(Pitch(NoteStep.C, 5), DurationMultiplier(1, 4)),
                BarLineElement(BarLineType.REPEAT_END),
            )

        val tune =
            AbcTune(
                header =
                    TuneHeader(
                        1,
                        listOf("Test"),
                        KeySignature(KeyRoot(NoteStep.C, Accidental.NATURAL), KeyMode.IONIAN),
                        TimeSignature(4, 4),
                        NoteDuration(1, 4),
                        null,
                        emptyList(),
                        emptyMap(),
                        "2.1",
                    ),
                body = TuneBody(elements),
                metadata = TuneMetadata(),
            )

        val expanded = RepeatExpander.expand(tune)

        val noteCount = expanded.filterIsInstance<NoteElement>().size
        // Original: 8. Expanded: 16.
        assertEquals(16, noteCount, "Note count mismatch after expansion from beginning")
    }

    @Test
    fun testPartsExpansion() {
        // P:AAB
        // [P:A] C D E F | [P:B] G A B c |
        val header =
            TuneHeader(
                1,
                listOf("Parts Test"),
                KeySignature(KeyRoot(NoteStep.C, Accidental.NATURAL), KeyMode.IONIAN),
                TimeSignature(4, 4),
                NoteDuration(1, 4),
                null,
                emptyList(),
                emptyMap(),
                "2.1",
                "AAB",
            )
        val partA =
            listOf(
                PartElement("A"),
                NoteElement(Pitch(NoteStep.C, 4), DurationMultiplier(1, 4)),
                NoteElement(Pitch(NoteStep.D, 4), DurationMultiplier(1, 4)),
                NoteElement(Pitch(NoteStep.E, 4), DurationMultiplier(1, 4)),
                NoteElement(Pitch(NoteStep.F, 4), DurationMultiplier(1, 4)),
                BarLineElement(BarLineType.SINGLE),
            )
        val partB =
            listOf(
                PartElement("B"),
                NoteElement(Pitch(NoteStep.G, 4), DurationMultiplier(1, 4)),
                NoteElement(Pitch(NoteStep.A, 4), DurationMultiplier(1, 4)),
                NoteElement(Pitch(NoteStep.B, 4), DurationMultiplier(1, 4)),
                NoteElement(Pitch(NoteStep.C, 5), DurationMultiplier(1, 4)),
                BarLineElement(BarLineType.SINGLE),
            )
        val tune = AbcTune(header, TuneBody(partA + partB), TuneMetadata())

        val expanded = RepeatExpander.expand(tune)

        val notes = expanded.filterIsInstance<NoteElement>()
        assertEquals(12, notes.size, "P:AAB should produce 12 notes (4+4+4)")
        assertEquals(NoteStep.C, notes[0].pitch.step)
        assertEquals(NoteStep.C, notes[4].pitch.step)
        assertEquals(NoteStep.G, notes[8].pitch.step)
    }

    @Test
    fun testNestedPartsExpansion() {
        // P:(AB)2C
        val header =
            TuneHeader(
                1,
                listOf("Nested Parts"),
                KeySignature(KeyRoot(NoteStep.C, Accidental.NATURAL), KeyMode.IONIAN),
                TimeSignature(4, 4),
                NoteDuration(1, 4),
                null,
                emptyList(),
                emptyMap(),
                "2.1",
                "(AB)2C",
            )
        val partA = listOf(PartElement("A"), NoteElement(Pitch(NoteStep.C, 4), DurationMultiplier(1, 4)))
        val partB = listOf(PartElement("B"), NoteElement(Pitch(NoteStep.D, 4), DurationMultiplier(1, 4)))
        val partC = listOf(PartElement("C"), NoteElement(Pitch(NoteStep.E, 4), DurationMultiplier(1, 4)))

        val tune = AbcTune(header, TuneBody(partA + partB + partC), TuneMetadata())
        val expanded = RepeatExpander.expand(tune)

        val notes = expanded.filterIsInstance<NoteElement>()
        // sequence: A B A B C -> 5 notes
        assertEquals(5, notes.size)
        assertEquals(NoteStep.C, notes[0].pitch.step)
        assertEquals(NoteStep.D, notes[1].pitch.step)
        assertEquals(NoteStep.C, notes[2].pitch.step)
        assertEquals(NoteStep.D, notes[3].pitch.step)
        assertEquals(NoteStep.E, notes[4].pitch.step)
    }
}
