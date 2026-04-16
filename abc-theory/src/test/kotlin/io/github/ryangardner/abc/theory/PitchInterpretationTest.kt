package io.github.ryangardner.abc.theory
import io.github.ryangardner.abc.core.model.Accidental
import io.github.ryangardner.abc.core.model.DurationMultiplier
import io.github.ryangardner.abc.core.model.KeyMode
import io.github.ryangardner.abc.core.model.KeyRoot
import io.github.ryangardner.abc.core.model.KeySignature
import io.github.ryangardner.abc.core.model.NoteDuration
import io.github.ryangardner.abc.core.model.NoteElement
import io.github.ryangardner.abc.core.model.NoteStep
import io.github.ryangardner.abc.core.model.Pitch
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

public class PitchInterpretationTest {
    @Test
    public fun `test G Major F is interpreted as F sharp`() {
        val key = KeySignature(KeyRoot(NoteStep.G, Accidental.NATURAL), KeyMode.MAJOR)
        val note = NoteElement(Pitch(NoteStep.F, 4, null), DurationMultiplier(1, 8))

        val interpreted = PitchInterpreter.PitchResolver.interpretBasePitch(note, key, emptyMap())
        assertEquals(Accidental.SHARP, interpreted.accidental)
    }

    @Test
    public fun `test G Major F with natural is interpreted as F natural`() {
        val key = KeySignature(KeyRoot(NoteStep.G, Accidental.NATURAL), KeyMode.MAJOR)
        val note = NoteElement(Pitch(NoteStep.F, 4, Accidental.NATURAL), DurationMultiplier(1, 8))

        val interpreted = PitchInterpreter.PitchResolver.interpretBasePitch(note, key, emptyMap())
        assertEquals(Accidental.NATURAL, interpreted.accidental)
    }

    @Test
    public fun `test F Major B is interpreted as B flat`() {
        val key = KeySignature(KeyRoot(NoteStep.F, Accidental.NATURAL), KeyMode.MAJOR)
        val note = NoteElement(Pitch(NoteStep.B, 4, null), DurationMultiplier(1, 8))

        val interpreted = PitchInterpreter.PitchResolver.interpretBasePitch(note, key, emptyMap())
        assertEquals(Accidental.FLAT, interpreted.accidental)
    }

    @Test
    public fun `test D Minor F is natural`() {
        val key = KeySignature(KeyRoot(NoteStep.D, Accidental.NATURAL), KeyMode.MINOR)
        val note = NoteElement(Pitch(NoteStep.F, 4, null), DurationMultiplier(1, 8))

        val interpreted = PitchInterpreter.PitchResolver.interpretBasePitch(note, key, emptyMap())
        assertEquals(null, interpreted.accidental) // D Minor has B flat, but F is natural
    }

    @Test
    public fun `test D Minor B is interpreted as B flat`() {
        val key = KeySignature(KeyRoot(NoteStep.D, Accidental.NATURAL), KeyMode.MINOR)
        val note = NoteElement(Pitch(NoteStep.B, 4, null), DurationMultiplier(1, 8))

        val interpreted = PitchInterpreter.PitchResolver.interpretBasePitch(note, key, emptyMap())
        assertEquals(Accidental.FLAT, interpreted.accidental)
    }
}
