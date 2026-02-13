package io.github.ryangardner.abc.theory

import io.github.ryangardner.abc.core.model.*
import io.github.ryangardner.abc.theory.util.KeyParserUtil
import io.github.ryangardner.abc.theory.util.InterpretationUtils
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

public class DorianKeyTest {

    @Test
    public fun `test G Dorian B is interpreted as B flat`() {
        val key = KeyParserUtil.parse("Gdor")
        assertEquals(KeyMode.DORIAN, key.mode)
        assertEquals(NoteStep.G, key.root.step)
        
        val note = NoteElement(Pitch(NoteStep.B, 4, null), NoteDuration(1, 8))
        val interpreted = PitchInterpreter.PitchResolver.interpretBasePitch(note, key, emptyMap())
        assertEquals(Accidental.FLAT, interpreted.accidental)
    }

    @Test
    public fun `test G Dorian with -8va transposition`() {
        val transposition = InterpretationUtils.parseCombinedTransposition("Gdor -8va")
        assertEquals(-12, transposition)
    }
}
