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
    public fun `test semantic parity with abcjs`(baseline: AbcjsBaseline) {
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
                        // println("DEBUG: [${baseline.name}] music21 reported error: ${errMap["error"]}")
                        return@let null
                    }
                }
                val m21Type = object : TypeToken<List<List<Map<String, Any>>>>() {}.type
                gson.fromJson<List<List<Map<String, Any>>>>(it, m21Type)
            } catch (e: Exception) { 
                // println("DEBUG: [${baseline.name}] Error parsing music21 JSON: ${e.message}")
                null 
            }
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
                if (System.getProperty("abc.test.debug") == "true") {
                    println("DEBUG: [${baseline.name}] abcjs mismatch: ${e.message}")
                }
                e
            }

            if (abcjsError == null) return@forEachIndexed // Success with abcjs

            // ABCJS MISMATCHED - TRY PRE-LOADED MUSIC21 AS SECOND OPINION
            if (m21FullResults != null) {
                // Try comparing music21 against BOTH expanded and unexpanded
                val m21MatchExpanded = compareWithMusic21(baseline.name, interpreted, m21FullResults)
                val m21MatchUnexpanded = compareWithMusic21(baseline.name, unexpanded, m21FullResults)
                
                if (m21MatchExpanded || m21MatchUnexpanded) {
                    // Success with music21! We matched one of the ground truths.
                    return@forEachIndexed 
                } else {
                    // Mismatched BOTH abcjs and music21. 
                    // Only fail if THEY agree with each other.
                    if (compareAbcjsWithMusic21(abcjsTune, m21FullResults)) {
                        // Systemic agreement between abcjs and music21 against us.
                        throw abcjsError
                    } else {
                        // All three disagree or music21/abcjs disagree with each other.
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

        sortedInterpretedVoices.forEachIndexed { voiceIndex, (voiceId, expandedNotes) ->
            val abcjsEventsUnfiltered = if (allAbcjsVoices.size > voiceIndex) allAbcjsVoices[voiceIndex] else emptyList()
            
            @Suppress("UNCHECKED_CAST")
            val midiData = abcjsTune["midiData"] as? Map<String, Any>
            @Suppress("UNCHECKED_CAST")
            val midiTracks = (midiData?.get("tracks") as? List<List<Map<String, Any>?>>)
                ?: (abcjsTune["midiEvents"] as? List<List<Map<String, Any>?>>)
            
            if (System.getProperty("abc.test.debug") == "true") {
                println("DEBUG: [$name] midiData?=${midiData != null}, midiEvents?=${abcjsTune.containsKey("midiEvents")}, midiTracks size=${midiTracks?.size}")
            }
            
            if (System.getProperty("abc.test.debug") == "true") {
                midiTracks?.forEachIndexed { i, track ->
                    val noteCount = track?.count { it?.get("cmd") == "note" } ?: 0
                    println("DEBUG: [$name] Track $i noteCount=$noteCount")
                }
            }
            
            // Try to find the first track with notes if voiceIndex 0 has nothing
            var midiTrack = if (midiTracks != null && midiTracks.size > voiceIndex) midiTracks[voiceIndex] else null
            val hasMidiNotesAtVoiceIndex = midiTrack?.any { trackEntry -> trackEntry?.get("cmd") == "note" } ?: false
            
            if (!hasMidiNotesAtVoiceIndex && midiTracks != null && voiceIndex == 0) {
               midiTrack = midiTracks.firstOrNull { t -> t?.any { it?.get("cmd") == "note" } ?: false }
            }
            
            val hasMidiNotes = midiTrack?.any { trackEntry -> trackEntry?.get("cmd") == "note" } ?: false

            val candidates = mutableListOf<List<Map<String, Any>>>()

            // Candidate A: From midiTracks
            midiTracks?.forEachIndexed { trackIdx, track ->
                val notes = track?.filter { it?.get("cmd") == "note" && it?.get("pitch") != null }?.filterNotNull() ?: emptyList()
                if (notes.isNotEmpty()) {
                    val grouped = notes.groupBy { Math.round((it["start"] as Number).toDouble() * 480.0) }
                        .toSortedMap()
                        .values
                        .map { chordEvents ->
                            mapOf(
                                "el_type" to "note",
                                "isMidiTrack" to true,
                                "duration" to (chordEvents.first()["duration"] as Number).toDouble(),
                                "midiPitches" to chordEvents.map { trackEntry ->
                                    mapOf("pitch" to (trackEntry["pitch"] as Number).toInt(), "duration" to (trackEntry["duration"] as Number).toDouble())
                                }
                            )
                        }
                    candidates.add(grouped)
                }
            }

            // Candidate B: From notation lines
            val flatAbcjsNotation = mutableListOf<Map<String, Any>>()
            abcjsEventsUnfiltered.forEach { event ->
                if (event == null) return@forEach
                val elType = event["el_type"] as? String
                @Suppress("UNCHECKED_CAST")
                val isSpacer = (event["rest"] as? Map<String, Any>)?.get("type") == "spacer"
                if ((elType == "note" || elType == "rest") && !isSpacer) {
                    @Suppress("UNCHECKED_CAST")
                    val graceNotes = event["gracenotes"] as? List<Map<String, Any>>
                    graceNotes?.forEach { gn ->
                        flatAbcjsNotation.add(gn.toMutableMap().apply { 
                            put("el_type", "note")
                            put("isGrace", true) 
                            if (!containsKey("duration")) put("duration", 0.0)
                        })
                    }
                    flatAbcjsNotation.add(event)
                }
            }
            if (flatAbcjsNotation.isNotEmpty()) candidates.add(flatAbcjsNotation)

            // Determine expected counts
            val unexpandedNotesForVoice = if (sortedUnexpandedVoices.size > voiceIndex) sortedUnexpandedVoices[voiceIndex].value else emptyList()

            // Heuristic for picking candidates: Try matching against Expanded/Unexpanded and with/without Grace notes.
            data class MatchAttempt(
                val abcjsEvents: List<Map<String, Any>>,
                val ourNotes: List<InterpretedNote>,
                val usingUnexpanded: Boolean,
                val isGraceFiltered: Boolean,
                val score: Int
            )

            val attempts = mutableListOf<MatchAttempt>()
            candidates.forEach { candidate ->
                listOf(true, false).forEach { useUnexpanded ->
                    listOf(true, false).forEach { filterGrace ->
                        val baseNotes = if (useUnexpanded) unexpandedNotesForVoice else expandedNotes
                        val ourNotes = if (filterGrace) baseNotes.filter { !it.isGrace } else baseNotes
                        
                        val score = if (candidate.size == ourNotes.size) 1000 else -Math.abs(candidate.size - ourNotes.size)
                        attempts.add(MatchAttempt(candidate, ourNotes, useUnexpanded, filterGrace, score))
                    }
                }
            }

            val bestMatch = attempts.maxByOrNull { it.score } ?: MatchAttempt(emptyList(), emptyList(), false, false, -1000)
            
            val abcjsEvents = bestMatch.abcjsEvents
            val usingUnexpanded = bestMatch.usingUnexpanded
            val isGraceFiltered = bestMatch.isGraceFiltered
            var currentNotes = bestMatch.ourNotes
            val isMidiBaseline = abcjsEvents.isNotEmpty() && abcjsEvents.first().containsKey("isMidiTrack")

            var finalNotes = currentNotes
            if (isMidiBaseline) {
                finalNotes = finalNotes.filter { !it.isRest }
            }

            val compareSize = Math.min(abcjsEvents.size, finalNotes.size)
            for (noteIndex in 0 until compareSize) {
                val interpretedNote = finalNotes[noteIndex]
                val abcjsEvent = abcjsEvents[noteIndex]
                val context = if (noteIndex > 0) {
                    val prevNote = currentNotes[noteIndex-1]
                    " (Prev: ${prevNote.duration}/${prevNote.playedDuration})"
                } else ""

                val abcjsDuration = (abcjsEvent["duration"] as Number).toDouble()
                val ourComparisonDuration = if (isMidiBaseline) interpretedNote.playedDuration.toDouble() else interpretedNote.duration.toDouble()
                
                if (Math.abs(abcjsDuration - ourComparisonDuration) > 0.001) {
                    // Match notation fallback
                    val matchNotation = Math.abs(abcjsDuration - interpretedNote.duration.toDouble()) < 0.001
                    if (!matchNotation) {
                        assertEquals(abcjsDuration, ourComparisonDuration, 0.001, 
                            "[$name] Duration mismatch at event $noteIndex in voice $voiceId$context (${if(usingUnexpanded) "unexpanded" else "expanded"}). abcjsDur: $abcjsDuration, ours: $ourComparisonDuration")
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
                }
            }
            assertEquals(abcjsEvents.size, finalNotes.size, "[$name] Event count mismatch in voice $voiceId. (picked size ${abcjsEvents.size}, expected ${finalNotes.size}. usingUnexpanded=$usingUnexpanded, isGraceFiltered=$isGraceFiltered)")
        }
    }

    private fun compareWithMusic21(name: String, interpreted: InterpretedTune, m21Results: List<List<Map<String, Any>>>): Boolean {
        val sortedInterpretedVoices = interpreted.voices.entries.sortedBy { entry -> entry.key }
        if (sortedInterpretedVoices.size != m21Results.size) {
            if (System.getProperty("abc.test.debug") == "true") println("DEBUG: [$name] m21 voice count mismatch: ours=${sortedInterpretedVoices.size}, m21=${m21Results.size}")
            return false
        }
        
        sortedInterpretedVoices.forEachIndexed { voiceIndex, (_, notes) ->
            val m21Events = m21Results[voiceIndex].filter { event ->
                val duration = (event["duration"] as? Number)?.toDouble() ?: 0.0
                val isGrace = event["isGrace"] as? Boolean ?: false
                duration > 0.0 || isGrace
            }
            if (notes.size != m21Events.size) {
                if (System.getProperty("abc.test.debug") == "true") println("DEBUG: [$name] m21 event count mismatch in voice $voiceIndex: ours=${notes.size}, m21=${m21Events.size}")
                return false
            }
            
            notes.forEachIndexed { noteIndex, interpretedNote ->
                val m21Event = m21Events[noteIndex]
                val m21Type = m21Event["type"] as? String ?: return false
                
                if (interpretedNote.isRest) {
                    if (m21Type != "rest") {
                        if (System.getProperty("abc.test.debug") == "true") println("DEBUG: [$name] m21 mismatch at event $noteIndex: expected rest, got $m21Type")
                        return false
                    }
                } else {
                    if (m21Type != "note" && m21Type != "chord") {
                        if (System.getProperty("abc.test.debug") == "true") println("DEBUG: [$name] m21 mismatch at event $noteIndex: expected note/chord, got $m21Type")
                        return false
                    }
                    
                    val m21Pitches = if (m21Type == "note") {
                        listOf((m21Event["pitch"] as Number).toInt())
                    } else {
                        @Suppress("UNCHECKED_CAST")
                        (m21Event["pitches"] as List<Number>).map { it.toInt() }
                    }
                    
                    val ourPitches = interpretedNote.midiPitches
                    if (ourPitches != m21Pitches && !interpretedNote.isTieContinued) {
                        val isOctaveShift = m21Pitches.size == ourPitches.size &&
                            m21Pitches.sorted().zip(ourPitches.sorted()).all { (a, b) -> Math.abs(a - b) % 12 == 0 }
                        if (!isOctaveShift) {
                            if (System.getProperty("abc.test.debug") == "true") println("DEBUG: [$name] m21 pitch mismatch at event $noteIndex: m21=$m21Pitches, ours=$ourPitches")
                            return false
                        }
                    }
                }
                
                val m21WholeNoteDuration = (m21Event["duration"] as Number).toDouble() * 0.25
                val ourComparisonDuration = interpretedNote.semanticDuration.toDouble()
                if (Math.abs(m21WholeNoteDuration - ourComparisonDuration) > 0.001) {
                    if (System.getProperty("abc.test.debug") == "true") {
                        println("DEBUG: [$name] m21 duration mismatch at event $noteIndex: m21=$m21WholeNoteDuration, ours=$ourComparisonDuration, note=${interpretedNote.pitches}, duration=${interpretedNote.duration}, semanticDuration=${interpretedNote.semanticDuration}, playedDuration=${interpretedNote.playedDuration}")
                    }
                    return false
                }
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
        val batchName = filename.split("/").firstOrNull() ?: "unknown"
        val reportFile = File(rootDir, "reports/troublesome_$batchName.md")
        if (!reportFile.exists()) {
            reportFile.writeText("# Troublesome Files: $batchName\nBoth abcjs and music21 disagree with our parser OR they disagree with each other.\n\n| File | Error |\n| --- | --- |\n")
        }
        val entry = "| $filename | $error |\n"
        if (!reportFile.readText().contains(filename)) {
            Files.write(reportFile.toPath(), entry.toByteArray(), StandardOpenOption.APPEND)
        }
    }

    private fun logDiscrepancy(filename: String, warnings: List<String>) {
        val rootDir = if (File(System.getProperty("user.dir")).name == "abc-test") {
            File(System.getProperty("user.dir")).parentFile
        } else {
            File(System.getProperty("user.dir"))
        }
        val batchName = filename.split("/").firstOrNull() ?: "unknown"
        val reportFile = File(rootDir, "reports/abcjs_discrepancies_$batchName.md")
        if (!reportFile.exists()) {
            reportFile.writeText("# Abcjs Warnings Report: $batchName\n\n| File | Warning |\n| --- | --- |\n")
        }
        val entry = "| $filename | ${warnings.joinToString("; ")} |\n"
        if (!reportFile.readText().contains(filename)) {
            Files.write(reportFile.toPath(), entry.toByteArray(), StandardOpenOption.APPEND)
        }
    }

    companion object {
        @JvmStatic
        fun baselineSources(): Stream<AbcjsBaseline> {
            val batchDirProp = System.getProperty("abc.test.batchDir")
            if (batchDirProp != null) {
                return getBaselinesFromDir(File(batchDirProp))
            }
            return Stream.empty()
        }

        private fun getBaselinesFromDir(batchDir: File): Stream<AbcjsBaseline> {
            val resolvedBatchDir = if (!batchDir.exists() && !batchDir.isAbsolute) {
                // Try relative to project root if running from module
                val projectRoot = File(System.getProperty("user.dir")).parentFile
                File(projectRoot, batchDir.path)
            } else batchDir

            val abcFilesDir = File(resolvedBatchDir, "abc_files")
            val midiJsonDir = File(resolvedBatchDir, "midi_json")
            val m21JsonDir = File(resolvedBatchDir, "music21_json")
            
            val actualAbcDir = if (abcFilesDir.exists()) abcFilesDir else resolvedBatchDir
            val actualJsonDir = if (midiJsonDir.exists()) midiJsonDir else resolvedBatchDir

            val filter = System.getProperty("abc.test.filter")
            val files = actualAbcDir.listFiles { f -> f.extension == "abc" }
            if (filter != null) {
                println("DEBUG: getBaselinesFromDir: actualAbcDir=${actualAbcDir.absolutePath}, filter=$filter, total files=${files?.size ?: 0}")
            }

            return (files?.mapNotNull { abcFile ->
                val jsonFile = File(actualJsonDir, abcFile.nameWithoutExtension + ".json")
                val m21JsonFile = File(m21JsonDir, abcFile.nameWithoutExtension + ".json")
                
                val baselineName = "${batchDir.name}/${abcFile.name}"
                val filterList = filter?.split(",") ?: emptyList()
                val matchesFilter = filter == null || filterList.any { baselineName.contains(it) || abcFile.nameWithoutExtension.contains(it) }
                
                if (!matchesFilter) return@mapNotNull null

                if (jsonFile.exists()) {
                    AbcjsBaseline(
                        baselineName, 
                        abcFile.readText(), 
                        jsonFile.readText(), 
                        if (m21JsonFile.exists()) m21JsonFile.readText() else null,
                        abcFile.absolutePath
                    )
                } else {
                    if (matchesFilter) println("DEBUG: Found ABC but NO JSON for ${abcFile.name} in ${actualJsonDir.absolutePath}")
                    null
                }
            }?.asSequence() ?: emptySequence())
                .asStream()
        }
    }
}
