package io.github.ryangardner.abc.test

import io.github.ryangardner.abc.core.model.AbcTune
import io.github.ryangardner.abc.parser.AbcSerializer
import io.github.ryangardner.abc.parser.v2.AntlrAbcParser
import io.github.ryangardner.abc.theory.PitchInterpreter
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.io.File

public class RegressionHeavyTest {

    @ParameterizedTest(name = "Regression trip: {0}")
    @MethodSource("regressionFiles")
    public fun `test regression tunes fidelity`(file: File): Unit {
        val parser = AntlrAbcParser()
        val serializer = AbcSerializer()

        val originalAbc = file.readText()
        val originalTunes = parser.parseBook(originalAbc)
        
        val serializedBook = originalTunes.joinToString("") { serializer.serialize(it) }
        val roundTrippedTunes = parser.parseBook(serializedBook)
        
        assertEquals(originalTunes.size, roundTrippedTunes.size, "[${file.name}] Tune count mismatch")

        originalTunes.forEachIndexed { tuneIndex, originalTune ->
            val roundTrippedTune = roundTrippedTunes[tuneIndex]
            
            val originalInterpreted = PitchInterpreter.interpret(originalTune)
            val roundTrippedInterpreted = PitchInterpreter.interpret(roundTrippedTune)
            
            assertEquals(originalInterpreted.voices.size, roundTrippedInterpreted.voices.size, "[${file.name}] Tune $tuneIndex Voice count mismatch")
            
            originalInterpreted.voices.forEach { (voiceId, originalNotes) ->
                val roundTrippedNotes = roundTrippedInterpreted.voices[voiceId] ?: throw AssertionError("Voice $voiceId missing")
                if (originalNotes.size != roundTrippedNotes.size) {
                    println("--- SERIALIZED BOOK FOR ${file.name} ---")
                    println(serializedBook)
                    println("-----------------------------------------")
                }
                assertEquals(originalNotes.size, roundTrippedNotes.size, "[${file.name}] Tune $tuneIndex Voice $voiceId element count mismatch")
                
                originalNotes.forEachIndexed { noteIndex, originalNote ->
                    val roundTrippedNote = roundTrippedNotes[noteIndex]
                    assertEquals(originalNote.pitches.map { it.midiNoteNumber }.sorted(), roundTrippedNote.pitches.map { it.midiNoteNumber }.sorted(), "[${file.name}] Tune $tuneIndex Voice $voiceId Note $noteIndex pitch mismatch")
                    assertEquals(originalNote.duration.toDouble(), roundTrippedNote.duration.toDouble(), 0.001, "[${file.name}] Tune $tuneIndex Voice $voiceId Note $noteIndex duration mismatch")
                }
            }
        }
    }

    public companion object {
        @JvmStatic
        public fun regressionFiles(): List<File> {
            val paths = listOf(
                "abc-test/src/test/resources/regression-samples",
                "src/test/resources/regression-samples"
            )
            for (path in paths) {
                val dir = File(path)
                if (dir.exists() && dir.isDirectory) {
                    val files = dir.listFiles { f -> f.extension == "abc" }?.toList()
                    if (files != null && files.isNotEmpty()) return files
                }
            }
            return emptyList()
        }
    }
}
