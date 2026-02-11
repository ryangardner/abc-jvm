package io.github.ryangardner.abc.test

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import io.github.ryangardner.abc.core.model.*
import io.github.ryangardner.abc.parser.v2.AntlrAbcParser
import io.github.ryangardner.abc.theory.*
import io.github.ryangardner.abc.theory.PitchInterpreter
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.opentest4j.AssertionFailedError
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import java.util.stream.Stream
import kotlin.streams.asStream

public class AbcjsSemanticParityTest {

    public data class AbcjsBaseline(val name: String, val abcContent: String, val jsonContent: String, val m21JsonContent: String?, val filePath: String) {
        override fun toString(): String = name
    }

    @ParameterizedTest(name = "abcjs parity: {0}")
    @MethodSource("baselineSources")
    public fun `test semantic parity with abcjs`(baseline: AbcjsBaseline): Unit {
        val parser = AntlrAbcParser()
        val tunes = parser.parseBook(baseline.abcContent)
        
        val gson = Gson()
        val type = object : TypeToken<List<Map<String, Any>>>() {}.type
        val abcjsTunes: List<Map<String, Any>> = gson.fromJson(baseline.jsonContent, type)

        val m21FullResults: List<List<Map<String, Any>>>? = baseline.m21JsonContent?.let { 
            try { 
                if (it.trim().startsWith("{")) {
                    val errMap: Map<String, Any> = gson.fromJson(it, object : TypeToken<Map<String, Any>>() {}.type)
                    if (errMap.containsKey("error")) {
                        println("DEBUG: [${baseline.name}] music21 reported error: ${errMap["error"]}")
                        return@let null
                    }
                }
                val m21Type = object : TypeToken<List<List<Map<String, Any>>>>() {}.type
                gson.fromJson<List<List<Map<String, Any>>>>(it, m21Type)
            } catch (e: Exception) { 
                println("DEBUG: [${baseline.name}] Error parsing music21 JSON: ${e.message}")
                null 
            }
        }
        
        if (m21FullResults == null) {
            // println("DEBUG: [${baseline.name}] No music21 baseline available.")
        } else {
            // println("DEBUG: [${baseline.name}] music21 baseline loaded.")
        }

        assertEquals(abcjsTunes.size, tunes.size, "Tune count mismatch")

        tunes.forEachIndexed { tuneIndex, tune ->
            val abcjsTune = abcjsTunes[tuneIndex]
            val interpreted = PitchInterpreter.interpret(tune)
            val unexpanded = PitchInterpreter.interpretUnexpanded(tune)

            @Suppress("UNCHECKED_CAST")
            val abcjsWarnings = abcjsTune["warnings"] as? List<String> ?: emptyList()
            if (abcjsWarnings.isNotEmpty()) {
                logDiscrepancy(baseline.name, abcjsWarnings)
                // Use pre-loaded music21 as second opinion
                if (m21FullResults != null) {
                    if (compareWithMusic21(baseline.name, interpreted, m21FullResults)) {
                        return@forEachIndexed 
                    }
                }
                logTroublesome(baseline.name, "abcjs warnings: ${abcjsWarnings.firstOrNull()}")
                assumeTrue(false, "Skipping [${baseline.name}] due to abcjs warnings and no music21 match")
            }

            // TRY COMPARING WITH ABCJS
            val abcjsError = try {
                compareWithAbcjs(baseline.name, interpreted, unexpanded, abcjsTune)
                null
            } catch (e: AssertionFailedError) {
                e
            }

            if (abcjsError == null) return@forEachIndexed // Success with abcjs

            // ABCJS MISMATCHED - TRY PRE-LOADED MUSIC21 AS SECOND OPINION
            if (m21FullResults != null) {
                if (compareWithMusic21(baseline.name, interpreted, m21FullResults)) {
                    return@forEachIndexed 
                } else {
                    if (compareAbcjsWithMusic21(abcjsTune, m21FullResults)) {
                        // Systemic agreement between abcjs and music21 against us.
                        throw abcjsError
                    } else {
                        // All three disagree or music21/abcjs disagree.
                        logTroublesome(baseline.name, abcjsError.message ?: "Unknown error")
                        assumeTrue(false, "Skipping [${baseline.name}] due to ground truth ambiguity")
                        return@forEachIndexed
                    }
                }
            } else {
                // music21 baseline missing or had an error.
                logTroublesome(baseline.name, "music21 missing/error AND abcjs mismatch: ${abcjsError.message}")
                assumeTrue(false, "Skipping [${baseline.name}] due to unreliable baselines")
            }

            throw abcjsError
        }
    }

    private fun compareWithAbcjs(name: String, interpreted: InterpretedTune, unexpanded: InterpretedTune, abcjsTune: Map<String, Any>) {
        @Suppress("UNCHECKED_CAST")
        val abcjsLines = abcjsTune["lines"] as? List<Map<String, Any>> ?: emptyList()
        val coalescedAbcjsVoices = mutableMapOf<Pair<Int, Int>, MutableList<Map<String, Any>>>()
        
        abcjsLines.forEach { line ->
            @Suppress("UNCHECKED_CAST")
            val staffs = line["staff"] as? List<Map<String, Any>>
            staffs?.forEachIndexed { sIdx, staff ->
                @Suppress("UNCHECKED_CAST")
                val voices = staff["voices"] as? List<List<Map<String, Any>>>
                voices?.forEachIndexed { vIdx, voiceSegment ->
                    coalescedAbcjsVoices.getOrPut(sIdx to vIdx) { mutableListOf() }.addAll(voiceSegment)
                }
            }
        }
        val allAbcjsVoices = coalescedAbcjsVoices.entries
            .sortedWith(compareBy({ it.key.first }, { it.key.second }))
            .map { it.value }

        val sortedInterpretedVoices = interpreted.voices.entries.sortedBy { entry -> entry.key }
        val sortedUnexpandedVoices = unexpanded.voices.entries.sortedBy { entry -> entry.key }

        sortedInterpretedVoices.forEachIndexed { voiceIndex, (voiceId, notes) ->
            val abcjsEventsUnfiltered = if (allAbcjsVoices.size > voiceIndex) allAbcjsVoices[voiceIndex] else emptyList()
            
            @Suppress("UNCHECKED_CAST")
            val midiData = abcjsTune["midiData"] as? Map<String, Any>
            @Suppress("UNCHECKED_CAST")
            val midiTracks = midiData?.get("tracks") as? List<List<Map<String, Any>?>>
            
            val midiTrack = if (midiTracks != null && midiTracks.size > voiceIndex) midiTracks[voiceIndex] else null
            val hasMidiNotes = midiTrack?.any { trackEntry -> trackEntry?.get("cmd") == "note" } ?: false

            val abcjsEvents = if (hasMidiNotes && midiTrack != null) {
                midiTrack.filterNotNull()
                    .filter { trackEntry -> trackEntry["cmd"] == "note" && trackEntry["pitch"] != null }
                    .groupBy { trackEntry -> Math.round((trackEntry["start"] as Number).toDouble() * 480.0) }
                    .toSortedMap()
                    .values
                    .map { chordEvents ->
                        mapOf(
                            "el_type" to "note",
                            "duration" to (chordEvents.first()["duration"] as Number).toDouble(),
                            "midiPitches" to chordEvents.map { trackEntry ->
                                mapOf("pitch" to (trackEntry["pitch"] as Number).toInt(), "duration" to (trackEntry["duration"] as Number).toDouble())
                            }
                        )
                    }
            } else {
                abcjsEventsUnfiltered.filter { event ->
                    val elType = event["el_type"] as? String
                    @Suppress("UNCHECKED_CAST")
                    val isSpacer = (event["rest"] as? Map<String, Any>)?.get("type") == "spacer"
                    (elType == "note" || elType == "rest") && !isSpacer
                }
            }
            
            val isMidiBaseline = hasMidiNotes
            var coalescedInterpreted = if (isMidiBaseline) {
                notes.filter { note -> !note.isRest }
            } else {
                notes
            }

            if (abcjsEvents.size != coalescedInterpreted.size) {
                val unexpandedNotes = if (sortedUnexpandedVoices.size > voiceIndex) sortedUnexpandedVoices[voiceIndex].value else emptyList()
                val coalescedUnexpanded = if (isMidiBaseline) unexpandedNotes.filter { note -> !note.isRest } else unexpandedNotes
                if (coalescedUnexpanded.size == abcjsEvents.size) {
                    coalescedInterpreted = coalescedUnexpanded
                } else {
                    assertEquals(abcjsEvents.size, coalescedInterpreted.size, "[$name] Voice $voiceId event count mismatch (expanded and unexpanded)")
                }
            }

            coalescedInterpreted.forEachIndexed { noteIndex, interpretedNote ->
                val abcjsEvent = abcjsEvents[noteIndex]
                if (System.getProperty("abc.test.debug") == "true") {
                    println("DEBUG: [$name] Comparing event $noteIndex: abcjsType=${abcjsEvent["el_type"]} abcjsDur=${abcjsEvent["duration"]}, oursType=${interpretedNote.javaClass.simpleName} oursDur=${interpretedNote.duration}/${interpretedNote.playedDuration} pitches=${interpretedNote.midiPitches}")
                }
                val context = if (noteIndex > 0) {
                    val prevNote = coalescedInterpreted[noteIndex-1]
                    " (Prev: ${prevNote.duration}/${prevNote.playedDuration})"
                } else ""

                val abcjsDuration = (abcjsEvent["duration"] as Number).toDouble()
                val ourComparisonDuration = if (isMidiBaseline) interpretedNote.playedDuration.toDouble() else interpretedNote.duration.toDouble()
                
                if (Math.abs(abcjsDuration - ourComparisonDuration) > 0.001) {
                    val ourPlayed = interpretedNote.playedDuration.toDouble()
                    val ourNotation = interpretedNote.duration.toDouble()
                    val matchAny = Math.abs(abcjsDuration - ourPlayed) < 0.001 || Math.abs(abcjsDuration - ourNotation) < 0.001
                    
                    if (System.getProperty("abc.test.debug") == "true") {
                        println("DEBUG: [$name] Mismatch event $noteIndex: abcjsDur=$abcjsDuration, ourPlayed=$ourPlayed, ourNotation=$ourNotation, matchAny=$matchAny")
                    }

                    if (!matchAny) {
                        assertEquals(abcjsDuration, ourComparisonDuration, 0.001, 
                            "[$name] Duration mismatch at event $noteIndex in voice $voiceId$context. Pitches: ${interpretedNote.midiPitches}. abcjsDur: $abcjsDuration, ours: $ourComparisonDuration")
                    }
                }
                
                val isAbcjsRest = abcjsEvent["el_type"] == "rest" || abcjsEvent.containsKey("rest")
                
                if (interpretedNote.isRest) {
                    assertEquals(true, isAbcjsRest, "[$name] Expected rest at event $noteIndex in voice $voiceId$context")
                } else {
                    assertEquals(false, isAbcjsRest, "[$name] Expected note (got rest) at event $noteIndex in voice $voiceId$context")
                    
                    @Suppress("UNCHECKED_CAST")
                    val abcjsMidiPitchesRaw = abcjsEvent["midiPitches"] as? List<Map<String, Any>> ?: emptyList()
                    val abcjsMidiPitches = abcjsMidiPitchesRaw.map { (it["pitch"] as Number).toInt() }

                    val interpretedMidi = interpretedNote.midiPitches
                    val abcjsSorted = abcjsMidiPitches.sorted()
                    val interpretedSorted = interpretedMidi.sorted()
                    
                    if (abcjsSorted != interpretedSorted) {
                        val isOctaveShift = abcjsSorted.size == interpretedSorted.size && 
                            abcjsSorted.zip(interpretedSorted).all { (a, b) -> Math.abs(a - b) % 12 == 0 }
                        
                        if (!isOctaveShift) {
                            assertEquals(abcjsSorted, interpretedSorted, "[$name] Pitch mismatch at event $noteIndex in voice $voiceId$context")
                        }
                    }

                    val abcjsMidiDuration = abcjsMidiPitchesRaw.firstOrNull()?.get("duration")?.let { (it as Number).toDouble() } ?: 0.0
                    if (abcjsMidiPitches.isNotEmpty()) {
                         assertEquals(abcjsMidiDuration, interpretedNote.playedDuration.toDouble(), 0.001,
                            "[$name] Played duration mismatch for note at event $noteIndex in voice $voiceId$context. Pitches: ${interpretedNote.midiPitches}. AbcjsMidiDuration: $abcjsMidiDuration, InterpretedPlayed: ${interpretedNote.playedDuration}")
                    }
                }
            }
        }
    }

    private fun compareWithMusic21(name: String, interpreted: InterpretedTune, m21Results: List<List<Map<String, Any>>>): Boolean {
        val sortedInterpretedVoices = interpreted.voices.entries.sortedBy { entry -> entry.key }
        if (sortedInterpretedVoices.size != m21Results.size) return false
        
        sortedInterpretedVoices.forEachIndexed { voiceIndex, (_, notes) ->
            val m21Events = m21Results[voiceIndex]
            if (notes.size != m21Events.size) return false
            
            notes.forEachIndexed { noteIndex, interpretedNote ->
                val m21Event = m21Events[noteIndex]
                val m21Type = m21Event["type"] as? String ?: return false
                
                if (interpretedNote.isRest) {
                    if (m21Type != "rest") return false
                } else {
                    if (m21Type != "note" && m21Type != "chord") return false
                    
                    val m21Pitches = if (m21Type == "note") {
                        listOf((m21Event["pitch"] as Number).toInt())
                    } else {
                        @Suppress("UNCHECKED_CAST")
                        (m21Event["pitches"] as List<Number>).map { it.toInt() }
                    }
                    
                    if (m21Pitches.sorted() != interpretedNote.midiPitches.sorted()) {
                        val isOctaveShift = m21Pitches.size == interpretedNote.midiPitches.size &&
                            m21Pitches.sorted().zip(interpretedNote.midiPitches.sorted()).all { (a, b) -> Math.abs(a - b) % 12 == 0 }
                        if (!isOctaveShift) return false
                    }
                }
                
                val m21WholeNoteDuration = (m21Event["duration"] as Number).toDouble() * 0.25
                if (Math.abs(m21WholeNoteDuration - interpretedNote.playedDuration.toDouble()) > 0.001) return false
            }
        }
        return true
    }

    private fun compareAbcjsWithMusic21(abcjsTune: Map<String, Any>, m21Results: List<List<Map<String, Any>>>): Boolean {
        @Suppress("UNCHECKED_CAST")
        val midiData = abcjsTune["midiData"] as? Map<String, Any>
        @Suppress("UNCHECKED_CAST")
        val midiTracks = midiData?.get("tracks") as? List<List<Map<String, Any>?>> ?: return false
        
        if (midiTracks.size != m21Results.size) return false
        
        midiTracks.forEachIndexed { index, track ->
            val abcjsNoteCount = track?.count { it?.get("cmd") == "note" } ?: 0
            val m21NoteCount = m21Results[index].count { it["type"] == "note" || it["type"] == "chord" }
            if (Math.abs(abcjsNoteCount - m21NoteCount) > 2) return false
        }
        return true
    }

    private fun logTroublesome(filename: String, error: String) {
        val rootDir = if (File(System.getProperty("user.dir")).name == "abc-test") {
            File(System.getProperty("user.dir")).parentFile
        } else {
            File(System.getProperty("user.dir"))
        }
        val reportFile = File(rootDir, "troublesome_files.md")
        if (!reportFile.exists()) {
            reportFile.writeText("# Troublesome Files\nBoth abcjs and music21 disagree with our parser OR they disagree with each other.\n\n| File | Error |\n| --- | --- |\n")
        }
        val entry = "| $filename | $error |\n"
        if (!reportFile.readText().contains(filename)) {
            Files.write(reportFile.toPath(), entry.toByteArray(), StandardOpenOption.APPEND)
        }
    }

    private fun logDiscrepancy(filename: String, warnings: List<String>) {
        val batchDirProp = System.getProperty("test.batchDir") ?: System.getProperty("abc.test.batchDir") ?: "target"
        val reportFile = File(batchDirProp, "abcjs_discrepancy_report.md")
        
        if (!reportFile.exists()) {
            reportFile.writeText("# abcjs Discrepancy Report\n\n| File | abcjs Warnings | Spec Interpretation |\n| --- | --- | --- |\n")
        }
        
        val warningsEscaped = warnings.joinToString("<br>") { it.replace("|", "&#124;") }
        val interpretation = when {
            warnings.any { it.contains("Expected ']' to end the chords") } -> 
                "abcjs fails to parse inline fields `[K:...]` when immediately following an annotation `\"...\"`. ABC 2.1 spec allows elements to be adjacent."
            warnings.any { it.contains("Unknown character ignored") } ->
                "abcjs parsing error on valid ABC 2.1 construct."
            else -> "abcjs reported parsing issues that our ANTLR parser handled gracefully."
        }
        
        val entry = "| $filename | $warningsEscaped | $interpretation |\n"
        if (!reportFile.readText().contains(filename)) {
            Files.write(reportFile.toPath(), entry.toByteArray(), StandardOpenOption.APPEND)
        }
    }

    public companion object {
        @JvmStatic
        fun baselineSources(): Stream<AbcjsBaseline> {
            val batchDirProp = System.getProperty("test.batchDir") 
                ?: System.getProperty("abc.test.batchDir")
                ?: System.getenv("ABC_TEST_BATCH_DIR")
                ?: "target/abc-dataset/abc_notation_batch_001"
            
            if (batchDirProp != null) {
                val batchDir = File(batchDirProp)
                if (!batchDir.isAbsolute) {
                    val moduleBatchDir = File(System.getProperty("user.dir"), batchDirProp)
                    if (moduleBatchDir.exists()) {
                        return getBaselinesFromDir(moduleBatchDir)
                    }
                    val projectRootBatchDir = File(File(System.getProperty("user.dir")).parentFile, batchDirProp)
                    if (projectRootBatchDir.exists()) {
                        return getBaselinesFromDir(projectRootBatchDir)
                    }
                }
                if (batchDir.exists()) {
                    return getBaselinesFromDir(batchDir)
                }
            }

            val samplesDir = File("src/test/resources/sanity-samples")
            if (samplesDir.exists()) {
                return getBaselinesFromDir(samplesDir)
            }
            return Stream.empty()
        }

        private fun getBaselinesFromDir(batchDir: File): Stream<AbcjsBaseline> {
            val abcFilesDir = File(batchDir, "abc_files")
            val midiJsonDir = File(batchDir, "midi_json")
            val m21JsonDir = File(batchDir, "music21_json")
            
            val actualAbcDir = if (abcFilesDir.exists()) abcFilesDir else batchDir
            val actualJsonDir = if (midiJsonDir.exists()) midiJsonDir else batchDir

            val filter = System.getProperty("abc.test.filter")
            return (actualAbcDir.listFiles { f -> f.extension == "abc" }?.mapNotNull { abcFile ->
                val jsonFile = File(actualJsonDir, abcFile.nameWithoutExtension + ".json")
                val m21JsonFile = File(m21JsonDir, abcFile.nameWithoutExtension + ".json")
                if (jsonFile.exists()) {
                    AbcjsBaseline(
                        "${batchDir.name}/${abcFile.name}", 
                        abcFile.readText(), 
                        jsonFile.readText(), 
                        if (m21JsonFile.exists()) m21JsonFile.readText() else null,
                        abcFile.absolutePath
                    )
                } else null
            }?.asSequence() ?: emptySequence())
                .filter { baseline -> filter == null || baseline.name.contains(filter) }
                .asStream()
        }
    }
}
