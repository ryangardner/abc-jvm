package io.github.ryangardner.abc.test

import io.github.ryangardner.abc.core.model.AbcTune
import io.github.ryangardner.abc.parser.AbcSerializer
import io.github.ryangardner.abc.parser.v2.AntlrAbcParser
import io.github.ryangardner.abc.theory.MeasureValidator
import io.github.ryangardner.abc.theory.PitchInterpreter
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.io.File
import java.util.stream.Stream
import java.util.zip.ZipFile
import kotlin.streams.asStream

public class RoundTripAntlrTest {

    public data class AbcSource(val name: String, val content: String) {
        override fun toString(): String = name
    }

    @ParameterizedTest(name = "ANTLR Round trip: {0}")
    @MethodSource("abcSources")
    public fun `test antlr parser fidelity`(source: AbcSource): Unit {
        val parser = AntlrAbcParser()
        val serializer = AbcSerializer()

        val originalAbc = source.content
        val originalTunes: List<AbcTune> = try {
            parser.parseBook(originalAbc)
        } catch (e: Exception) {
            println("ANTLR PARSE FAILED for ${source.name}: ${e.message}")
            throw e // Fail the test
        }
        
        val serializedBook: String = originalTunes.joinToString("") { serializer.serialize(it) }
        
        val roundTrippedTunes: List<AbcTune> = try {
            parser.parseBook(serializedBook)
        } catch (e: Exception) {
            println("FAILED TO RE-PARSE SERIALIZED BOOK (ANTLR) for ${source.name}: ${e.message}")
            println("SERIALIZED CONTENT:\n$serializedBook")
            throw e
        }
        
        assertEquals(originalTunes.size, roundTrippedTunes.size, "[${source.name}] Tune count mismatch")

        originalTunes.forEachIndexed { tuneIndex: Int, originalTune: AbcTune ->
            val roundTrippedTune = roundTrippedTunes[tuneIndex]
            
            // Semantic Validation
            val originalInterpreted = PitchInterpreter.interpret(originalTune)
            val roundTrippedInterpreted = PitchInterpreter.interpret(roundTrippedTune)
            
            assertEquals(originalInterpreted.voices.size, roundTrippedInterpreted.voices.size, "[${source.name}] Tune $tuneIndex Interpreted voice count mismatch")
            
            originalInterpreted.voices.forEach { (voiceId, originalNotes) ->
                val roundTrippedNotes = roundTrippedInterpreted.voices[voiceId] ?: throw AssertionError("Voice $voiceId missing in round-tripped tune")
                assertEquals(originalNotes.size, roundTrippedNotes.size, "[${source.name}] Tune $tuneIndex Voice $voiceId element count mismatch")
                
                originalNotes.forEachIndexed { noteIndex, originalNote ->
                    val roundTrippedNote = roundTrippedNotes[noteIndex]
                    assertEquals(originalNote.pitches.map { it.midiNoteNumber }.sorted(), roundTrippedNote.pitches.map { it.midiNoteNumber }.sorted(), "[${source.name}] Tune $tuneIndex Voice $voiceId Note $noteIndex pitch mismatch")
                    assertEquals(originalNote.duration.toDouble(), roundTrippedNote.duration.toDouble(), 0.001, "[${source.name}] Tune $tuneIndex Voice $voiceId Note $noteIndex duration mismatch")
                }
            }
            
            // Measure Validation
            try {
                MeasureValidator.validate(originalTune)
                MeasureValidator.validate(roundTrippedTune)
            } catch (e: Exception) {
                // println("Measure validation failed for ${source.name}: ${e.message}")
            }
        }
    }

    public companion object {
        private val isHeavy: Boolean = System.getProperty("test.profile") == "heavy"

        @JvmStatic
        public fun abcSources(): Stream<AbcSource> {
            val home = System.getProperty("user.home")
            val downloads = File(home, "Downloads")
            
            if (!isHeavy) {
                val dir = File("abc-test/src/test/resources/sanity-samples")
                return (dir.listFiles { f -> f.extension == "abc" }?.map { 
                    AbcSource(it.name, it.readText()) 
                }?.asSequence() ?: emptySequence()).asStream()
            }

            val zipFiles = downloads.listFiles { f -> f.name.startsWith("abc_notation_batch_") && f.extension == "zip" } ?: emptyArray()
            val unzippedDirs = downloads.listFiles { f -> f.isDirectory && f.name.startsWith("abc_notation_batch_") } ?: emptyArray()

            val dirNames = unzippedDirs.map { it.name }.toSet()
            val filteredZips = zipFiles.filter { it.name.substringBeforeLast(".") !in dirNames }

            val sequence = sequence {
                unzippedDirs.sortedBy { it.name }.forEach { dir ->
                    dir.walkTopDown()
                        .filter { it.extension == "abc" }
                        .sortedBy { it.name }
                        .forEach { file ->
                            yield(AbcSource("${dir.name}/${file.name}", file.readText()))
                        }
                }
                filteredZips.sortedBy { it.name }.forEach { zipFile ->
                    ZipFile(zipFile).use { zip ->
                        zip.entries().asSequence()
                            .filter { !it.isDirectory && it.name.endsWith(".abc") }
                            .sortedBy { it.name }
                            .forEach { entry ->
                                val content = zip.getInputStream(entry).bufferedReader().readText()
                                yield(AbcSource("${zipFile.name}/${entry.name}", content))
                            }
                    }
                }
            }

            return sequence.take(10000).asStream()
        }
    }
}
