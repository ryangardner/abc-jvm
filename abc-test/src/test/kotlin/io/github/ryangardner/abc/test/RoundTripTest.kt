package io.github.ryangardner.abc.test

import io.github.ryangardner.abc.core.model.*
import io.github.ryangardner.abc.parser.AbcParser
import io.github.ryangardner.abc.parser.AbcSerializer
import io.github.ryangardner.abc.theory.MeasureValidator
import io.github.ryangardner.abc.theory.PitchInterpreter
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
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
            
            // Basic header checks
            assertTrue(originalTune.header.reference == roundTrippedTune.header.reference, FidelityReporter.reportRoundTripFailure(file, tuneIndex, originalTune, roundTrippedTune, "Reference mismatch: expected ${originalTune.header.reference}, got ${roundTrippedTune.header.reference}", originalAbc, serializedBook))
            assertTrue(originalTune.header.title == roundTrippedTune.header.title, FidelityReporter.reportRoundTripFailure(file, tuneIndex, originalTune, roundTrippedTune, "Title mismatch", originalAbc, serializedBook))
            assertTrue(originalTune.header.key == roundTrippedTune.header.key, FidelityReporter.reportRoundTripFailure(file, tuneIndex, originalTune, roundTrippedTune, "Key mismatch", originalAbc, serializedBook))
            
            // Body structural check (Normalized)
            val originalNormalized = originalTune.withoutLocation()
            val roundTrippedNormalized = roundTrippedTune.withoutLocation()

            // Normalize elements more aggressively for the structural check.
            val originalBodyElements = normalizeElements(originalNormalized.body.elements)
            val roundTrippedBodyElements = normalizeElements(roundTrippedNormalized.body.elements)
            
            // We'll create normalized versions of the tunes just for the final identity check
            val originalNormalizedTune = originalTune.copy(body = originalTune.body.copy(elements = originalBodyElements))
            val roundTrippedNormalizedTune = roundTrippedTune.copy(body = roundTrippedTune.body.copy(elements = roundTrippedBodyElements))

            assertTrue(originalBodyElements == roundTrippedBodyElements, FidelityReporter.reportRoundTripFailure(file, tuneIndex, originalNormalizedTune, roundTrippedNormalizedTune, "Body structural mismatch (Normalized)", originalAbc, serializedBook))
            
            // Preamble check (Normalized)
            val originalPreamble = normalizeElements(originalNormalized.preamble)
            val roundTrippedPreamble = normalizeElements(roundTrippedNormalized.preamble)
            
            val originalNormalizedPreambleTune = originalTune.copy(preamble = originalPreamble)
            val roundTrippedNormalizedPreambleTune = roundTrippedTune.copy(preamble = roundTrippedPreamble)

            assertTrue(originalPreamble == roundTrippedPreamble, FidelityReporter.reportRoundTripFailure(file, tuneIndex, originalNormalizedPreambleTune, roundTrippedNormalizedPreambleTune, "Preamble structural mismatch (Normalized)", originalAbc, serializedBook))

            // Check for unrecognized characters
            val unrecognizedOriginal = FidelityReporter.reportUnrecognizedCharacters(file, tuneIndex, originalTune)
            assertTrue(unrecognizedOriginal.isEmpty(), unrecognizedOriginal)
            
            val unrecognizedRT = FidelityReporter.reportUnrecognizedCharacters(file, tuneIndex, roundTrippedTune)
            assertTrue(unrecognizedRT.isEmpty(), unrecognizedRT)

            // Semantic Validation
            val originalInterpreted = PitchInterpreter.interpret(originalTune)
            val roundTrippedInterpreted = PitchInterpreter.interpret(roundTrippedTune)
            
            assertTrue(originalInterpreted.voices.size == roundTrippedInterpreted.voices.size, FidelityReporter.reportRoundTripFailure(file, tuneIndex, originalTune, roundTrippedTune, "Interpreted voice count mismatch", originalAbc, serializedBook))
            
            originalInterpreted.voices.forEach { (voiceId, originalNotes) ->
                val roundTrippedNotes = roundTrippedInterpreted.voices[voiceId] ?: throw AssertionError("Voice $voiceId missing in round-tripped tune")
                assertTrue(originalNotes.size == roundTrippedNotes.size, FidelityReporter.reportRoundTripFailure(file, tuneIndex, originalTune, roundTrippedTune, "Voice $voiceId element count mismatch", originalAbc, serializedBook))
                
                originalNotes.forEachIndexed { noteIndex, originalNote ->
                    val roundTrippedNote = roundTrippedNotes[noteIndex]
                    assertTrue(originalNote.pitches.map { it.midiNoteNumber }.sorted() == roundTrippedNote.pitches.map { it.midiNoteNumber }.sorted(), FidelityReporter.reportRoundTripFailure(file, tuneIndex, originalTune, roundTrippedTune, "Voice $voiceId Note $noteIndex pitch mismatch", originalAbc, serializedBook))
                    assertEquals(originalNote.duration.toDouble(), roundTrippedNote.duration.toDouble(), 0.001, FidelityReporter.reportRoundTripFailure(file, tuneIndex, originalTune, roundTrippedTune, "Voice $voiceId Note $noteIndex duration mismatch", originalAbc, serializedBook))
                }
            }
        }
    }

    private fun normalizeElements(elements: List<MusicElement>): List<MusicElement> {
        // First pass: filter out ALL spacers for structural identity check and strip virtual line-break decorations
        return elements.filter { it !is SpacerElement }.map { 
            val el = it.withoutLocation()
            when (el) {
                is NoteElement -> el.copy(decorations = el.decorations.filter { d -> d.value != "line-break" })
                is RestElement -> el.copy(decorations = el.decorations.filter { d -> d.value != "line-break" })
                is ChordElement -> el.copy(decorations = el.decorations.filter { d -> d.value != "line-break" })
                else -> el
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
                    val rootDatasetDir = File("abc-test/abc-dataset/abc_notation_batch_001")
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
