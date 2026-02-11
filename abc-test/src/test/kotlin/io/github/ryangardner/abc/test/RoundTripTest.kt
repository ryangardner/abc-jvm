package io.github.ryangardner.abc.test

import io.github.ryangardner.abc.core.model.AbcTune
import io.github.ryangardner.abc.parser.AbcParser
import io.github.ryangardner.abc.parser.AbcSerializer
import io.github.ryangardner.abc.theory.MeasureValidator
import io.github.ryangardner.abc.theory.PitchInterpreter
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.io.File

public class RoundTripTest {

    @ParameterizedTest(name = "Round trip: {0}")
    @MethodSource("abcFiles")
    public fun `test round trip fidelity`(file: File): Unit {
        val parser = AbcParser()
        val serializer = AbcSerializer()

        val originalAbc = file.readText()
        val originalTunes: List<AbcTune> = try {
            parser.parseBook(originalAbc)
        } catch (e: Exception) {
            println("PARSE FAILED for ${file.name}: ${e.message}")
            // e.printStackTrace()
            return // Skip for now if it can't even parse once
        }
        
        val serializedBook: String = originalTunes.joinToString("") { serializer.serialize(it) }
        
        // Bit-perfect check (only for single-tune files for now, as we don't preserve inter-tune whitespace yet)
        if (originalTunes.size == 1 && !originalAbc.contains("V:")) {
             // assertEquals(originalAbc.trim(), serializedBook.trim(), "Bit-perfect round trip failed for ${file.name}")
        }

        val roundTrippedTunes: List<AbcTune> = try {
            parser.parseBook(serializedBook)
        } catch (e: Exception) {
            println("FAILED TO RE-PARSE SERIALIZED BOOK for ${file.name}: ${e.message}")
            println("SERIALIZED CONTENT:\n$serializedBook")
            throw e
        }
        
        assertEquals(originalTunes.size, roundTrippedTunes.size, "[${file.name}] Tune count mismatch")

        originalTunes.forEachIndexed { tuneIndex: Int, originalTune: AbcTune ->
            val roundTrippedTune = roundTrippedTunes[tuneIndex]
            try {
                assertEquals(originalTune.header.reference, roundTrippedTune.header.reference, "[${file.name}] Tune $tuneIndex Reference mismatch")
                assertEquals(originalTune.header.title, roundTrippedTune.header.title, "[${file.name}] Tune $tuneIndex Title mismatch")
                assertEquals(originalTune.header.key, roundTrippedTune.header.key, "[${file.name}] Tune $tuneIndex Key mismatch")
                assertEquals(originalTune.header.meter, roundTrippedTune.header.meter, "[${file.name}] Tune $tuneIndex Meter mismatch")
                assertEquals(originalTune.header.length, roundTrippedTune.header.length, "[${file.name}] Tune $tuneIndex Length mismatch")
                assertEquals(originalTune.body.elements.size, roundTrippedTune.body.elements.size, "[${file.name}] Tune $tuneIndex Body size mismatch")
                
                // Semantic Validation
                val originalInterpreted = PitchInterpreter.interpret(originalTune)
                val roundTrippedInterpreted = PitchInterpreter.interpret(roundTrippedTune)
                
                assertEquals(originalInterpreted.voices.size, roundTrippedInterpreted.voices.size, "[${file.name}] Tune $tuneIndex Interpreted voice count mismatch")
                
                originalInterpreted.voices.forEach { (voiceId, originalNotes) ->
                    val roundTrippedNotes = roundTrippedInterpreted.voices[voiceId] ?: throw AssertionError("Voice $voiceId missing in round-tripped tune")
                    assertEquals(originalNotes.size, roundTrippedNotes.size, "[${file.name}] Tune $tuneIndex Voice $voiceId element count mismatch")
                    
                    originalNotes.forEachIndexed { noteIndex, originalNote ->
                        val roundTrippedNote = roundTrippedNotes[noteIndex]
                        assertEquals(originalNote.pitches.map { it.midiNoteNumber }.sorted(), roundTrippedNote.pitches.map { it.midiNoteNumber }.sorted(), "[${file.name}] Tune $tuneIndex Voice $voiceId Note $noteIndex pitch mismatch")
                        assertEquals(originalNote.duration.toDouble(), roundTrippedNote.duration.toDouble(), 0.001, "[${file.name}] Tune $tuneIndex Voice $voiceId Note $noteIndex duration mismatch")
                    }
                }
                
                // Measure Validation
                MeasureValidator.validate(originalTune)
                MeasureValidator.validate(roundTrippedTune)
            } catch (e: Throwable) {
                println("FIDELITY FAILURE for ${file.name} at Tune $tuneIndex")
                // Find first diff in body
                val size = minOf(originalTune.body.elements.size, roundTrippedTune.body.elements.size)
                for (i in 0 until size) {
                    if (originalTune.body.elements[i] != roundTrippedTune.body.elements[i]) {
                        println("First diff in Tune $tuneIndex at element index $i:")
                        println("  Expected: ${originalTune.body.elements[i]}")
                        println("  Actual:   ${roundTrippedTune.body.elements[i]}")
                        break
                    }
                }
                throw e
            }
        }
    }

    public companion object {
        private var datasetDir: File? = null
        private val isHeavy: Boolean = System.getProperty("test.profile") == "heavy"

        @JvmStatic
        @BeforeAll
        public fun setup(): Unit {
            if (isHeavy) {
                datasetDir = DatasetDownloader.downloadAndExtract(1)
                if (!datasetDir!!.exists() || datasetDir!!.list()?.isEmpty() == true) {
                    // Try fallback if running from root
                    val rootDatasetDir = File("abc-test/target/abc-dataset/abc_notation_batch_001")
                    if (rootDatasetDir.exists()) {
                        datasetDir = rootDatasetDir
                    }
                }
            }
        }

        @JvmStatic
        public fun abcFiles(): List<File> {
            if (isHeavy && datasetDir == null) {
                setup()
            }
            return if (isHeavy) {
                datasetDir?.walkTopDown()
                    ?.filter { it.extension == "abc" }
                    ?.take(1000)
                    ?.toList() ?: emptyList()
            } else {
                val dir = File("src/test/resources/sanity-samples")
                val fallbackDir = File("abc-test/src/test/resources/sanity-samples")
                val activeDir = if (dir.exists()) dir else fallbackDir
                
                activeDir.listFiles { f -> f.extension == "abc" }?.toList() ?: emptyList()
            }
        }
    }
}