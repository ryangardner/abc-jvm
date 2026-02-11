package io.github.ryangardner.abc.theory

import io.github.ryangardner.abc.core.model.*
import io.github.ryangardner.abc.theory.util.KeyParserUtil
import org.slf4j.Logger
import org.slf4j.LoggerFactory

public data class InterpretedNote(
    public val pitches: List<Pitch>,
    public val midiPitches: List<Int>, // Absolute MIDI pitches (after transpositions)
    public val duration: NoteDuration, // Notational duration (matches abcjs "duration" field)
    public val playedDuration: NoteDuration, // Semantic duration (matches abcjs "midiPitches[0].duration" field)
    public val isRest: Boolean = false,
    public val graceNotes: List<InterpretedNote> = emptyList()
)

public data class InterpretedTune(
    public val voices: Map<String, List<InterpretedNote>>
)

public object PitchInterpreter {
    private val logger: Logger = LoggerFactory.getLogger(PitchInterpreter::class.java)

    public fun interpret(tune: AbcTune): InterpretedTune {
        // abcjs MIDI output expands repeats, so we must expand them to achieve bit-perfect parity.
        val expandedElements = RepeatExpander.expand(tune)
        return interpretElements(tune, expandedElements)
    }

    public fun interpretUnexpanded(tune: AbcTune): InterpretedTune {
        return interpretElements(tune, tune.body.elements)
    }

    private fun interpretElements(tune: AbcTune, elements: List<MusicElement>): InterpretedTune {
        val voices = mutableMapOf<String, MutableList<InterpretedNote>>()
        var currentVoiceId = "1"
        
        data class TupletState(val q: Int, val p: Int, var remainingNotes: Int)

        class VoiceState(
            var currentKey: KeySignature,
            var currentMeter: TimeSignature,
            val activeAccidentals: MutableMap<Pair<NoteStep, Int>, Accidental> = mutableMapOf(),
            var midiTranspose: Int = 0,
            var activeTuplet: TupletState? = null,
            val pendingGraceNotes: MutableList<InterpretedNote> = mutableListOf()
        )

        val voiceStates = mutableMapOf<String, VoiceState>()
        
        var globalMidiTranspose = 0

        fun getVoiceState(id: String): VoiceState {
            return voiceStates.getOrPut(id) {
                VoiceState(
                    currentKey = tune.header.key,
                    currentMeter = tune.header.meter,
                    midiTranspose = globalMidiTranspose
                )
            }
        }
        
        // Initialize from header voices and other fields
        tune.header.headers.forEach { (id, value) ->
            when (id) {
                "V" -> {
                    val voiceId = value.split(" ", "\t").first()
                    val vState = getVoiceState(voiceId)
                    parseCombinedTransposition(value)?.let { vState.midiTranspose = it }
                }
                "K" -> {
                    // Default voice (1) should pick up transposition from primary K: if not already set by V:
                    val vState = getVoiceState("1")
                    parseCombinedTransposition(value)?.let { vState.midiTranspose = it }
                }
                "%%" -> {
                    if (value.startsWith("MIDI transpose", ignoreCase = true)) {
                        val transpose = value.split("\\s+".toRegex()).last().toIntOrNull() ?: 0
                        globalMidiTranspose = transpose
                        voiceStates.values.forEach { it.midiTranspose = transpose }
                    }
                }
            }
        }

        // Initialize first voice
        getVoiceState(currentVoiceId)

        // Tie tracking: (voice, List<MIDI pitches>) to (voice, noteIndex)
        val openTies = mutableMapOf<Pair<String, List<Int>>, Pair<String, Int>>()

        elements.forEach { element ->
            val vState = getVoiceState(currentVoiceId)
            
            when (element) {
                is BodyHeaderElement -> {
                    when (element.key) {
                        "V" -> {
                            currentVoiceId = element.value.split(" ", "\t").first()
                            val newState = getVoiceState(currentVoiceId)
                            parseCombinedTransposition(element.value)?.let { newState.midiTranspose = it }
                        }
                        "K" -> {
                            vState.currentKey = KeyParserUtil.parse(element.value)
                            parseCombinedTransposition(element.value)?.let { vState.midiTranspose = it }
                        }
                        "M" -> {
                            vState.currentMeter = parseMeter(element.value)
                        }
                    }
                }
                is InlineFieldElement -> {
                    when (element.fieldType) {
                        HeaderType.KEY -> {
                            vState.currentKey = KeyParserUtil.parse(element.value)
                            parseCombinedTransposition(element.value)?.let { vState.midiTranspose = it }
                        }
                        HeaderType.VOICE -> {
                            currentVoiceId = element.value.split(" ", "\t").first()
                            val newState = getVoiceState(currentVoiceId)
                            parseCombinedTransposition(element.value)?.let { newState.midiTranspose = it }
                        }
                        HeaderType.METER -> {
                            vState.currentMeter = parseMeter(element.value)
                        }
                        else -> {}
                    }
                }
                is DirectiveElement -> {
                    if (element.content.startsWith("MIDI transpose", ignoreCase = true)) {
                        vState.midiTranspose = element.content.split("\\s+".toRegex()).last().toIntOrNull() ?: 0
                    }
                }
                is TupletElement -> {
                    val p = element.p
                    val isCompound = (vState.currentMeter.numerator % 3 == 0 && vState.currentMeter.numerator > 3)
                    
                    val q = element.q ?: when (p) {
                        2 -> 3
                        3 -> 2
                        4 -> 3
                        5 -> if (isCompound) 3 else 2
                        6 -> 2
                        7 -> if (isCompound) 3 else 2
                        8 -> 3
                        9 -> 2
                        else -> 2
                    }
                    val r = element.r ?: p
                    vState.activeTuplet = TupletState(q, p, r)
                }
                is GraceNoteElement -> {
                    element.notes.forEach { note ->
                        val interpretedPitch = interpret(note, vState.currentKey, vState.activeAccidentals)
                        val midiPitch = interpretedPitch.midiNoteNumber + vState.midiTranspose
                        vState.pendingGraceNotes.add(InterpretedNote(
                            pitches = listOf(interpretedPitch), 
                            midiPitches = listOf(midiPitch),
                            duration = NoteDuration(0, 1), 
                            playedDuration = note.length
                        ))
                        
                        val explicitAccidental = note.pitch.accidental ?: note.accidental
                        if (explicitAccidental != null) {
                            vState.activeAccidentals[note.pitch.step to note.pitch.octave] = explicitAccidental
                        }
                    }
                }
                is NoteElement -> {
                    if (logger.isDebugEnabled) {
                        logger.debug("Interpreting pitch for note ${element.pitch.step.name} octave ${element.pitch.octave}")
                    }
                    val interpretedPitch = interpret(element, vState.currentKey, vState.activeAccidentals)
                    // Apply combined transposition (chromatic + octave + clef)
                    val midiPitch = interpretedPitch.midiNoteNumber + vState.midiTranspose
                    
                    val explicitAccidental = element.pitch.accidental ?: element.accidental
                    if (explicitAccidental != null) {
                        vState.activeAccidentals[element.pitch.step to element.pitch.octave] = explicitAccidental
                    }
                    
                    val tuplet = vState.activeTuplet
                    val duration = element.length
                    var playedDuration = duration
                    if (tuplet != null && tuplet.remainingNotes > 0) {
                        playedDuration = playedDuration.multiply(tuplet.q, tuplet.p)
                        tuplet.remainingNotes--
                        if (tuplet.remainingNotes == 0) vState.activeTuplet = null
                    }

                    // Grace note duration stealing
                    val rawGraceNotes = vState.pendingGraceNotes
                    val coalescedGraceNotes = if (rawGraceNotes.isNotEmpty()) {
                        val stolenTotal = playedDuration.multiply(1, 2)
                        val perGraceNodeStolen = stolenTotal.multiply(1, rawGraceNotes.size)
                        val scaledGraceNotes = rawGraceNotes.map { it.copy(playedDuration = perGraceNodeStolen) }
                        playedDuration = addDurations(playedDuration, stolenTotal.multiply(-1, 1))
                        rawGraceNotes.clear()
                        scaledGraceNotes
                    } else emptyList()

                    val voiceList = voices.getOrPut(currentVoiceId) { mutableListOf() }
                    
                    val midiPitches = listOf(midiPitch)
                    val tieKey = currentVoiceId to midiPitches
                    
                    // Fuzzy tie matching: if no explicit accidental, allow matching a tie with different accidental but same base pitch
                    var tiedFrom = openTies[tieKey]
                    var adjustedMidiPitch = midiPitch
                    
                    if (tiedFrom == null && element.pitch.accidental == null && element.accidental == null) {
                        // Check for open ties in this voice with the same (Step, Octave)
                        val potentialTie = openTies.entries.find { (key, _) -> 
                            key.first == currentVoiceId && key.second.size == 1 && (key.second[0] % 12 == midiPitch % 12) && Math.abs(key.second[0] - midiPitch) < 12
                        }
                        
                        val heuristicMatch = openTies.entries.find { (key, _) ->
                            key.first == currentVoiceId && key.second.size == 1 && Math.abs(key.second[0] - midiPitch) <= 2
                        }
                        if (heuristicMatch != null) {
                            tiedFrom = heuristicMatch.value
                            adjustedMidiPitch = heuristicMatch.key.second[0]
                        }
                    }

                    if (tiedFrom != null) {
                        val originalList = voices[tiedFrom.first]!!
                        val originalNote = originalList[tiedFrom.second]
                        originalList[tiedFrom.second] = originalNote.copy(playedDuration = addDurations(originalNote.playedDuration, playedDuration))
                        voiceList.add(InterpretedNote(pitches = emptyList(), midiPitches = emptyList(), duration = duration, playedDuration = playedDuration, graceNotes = coalescedGraceNotes))
                        
                        // Use the tieKey associated with the ACTUAL pitch we tied to
                        val actualTieKey = currentVoiceId to listOf(adjustedMidiPitch)
                        if (element.ties == TieType.START || element.ties == TieType.BOTH) {
                            openTies[actualTieKey] = tiedFrom
                        } else {
                            openTies.remove(actualTieKey)
                        }
                    } else {
                        val newNoteIndex = voiceList.size
                        voiceList.add(InterpretedNote(pitches = listOf(interpretedPitch), midiPitches = midiPitches, duration = duration, playedDuration = playedDuration, graceNotes = coalescedGraceNotes))
                        if (element.ties == TieType.START || element.ties == TieType.BOTH) {
                            openTies[tieKey] = currentVoiceId to newNoteIndex
                        }
                    }
                }
                is ChordElement -> {
                    val interpretedPitches = element.notes.map { note ->
                        val interpretedPitch = interpret(note, vState.currentKey, vState.activeAccidentals)
                        val explicitAccidental = note.pitch.accidental ?: note.accidental
                        if (explicitAccidental != null) {
                            vState.activeAccidentals[note.pitch.step to note.pitch.octave] = explicitAccidental
                        }
                        interpretedPitch
                    }
                    // Apply combined transposition
                    val midiPitches = interpretedPitches.map { it.midiNoteNumber + vState.midiTranspose }

                    val tuplet = vState.activeTuplet
                    var duration = element.duration
                    var playedDuration = duration
                    if (tuplet != null && tuplet.remainingNotes > 0) {
                        playedDuration = playedDuration.multiply(tuplet.q, tuplet.p)
                        duration = duration.multiply(tuplet.q, tuplet.p)
                        tuplet.remainingNotes--
                        if (tuplet.remainingNotes == 0) vState.activeTuplet = null
                    }

                    // Grace note duration stealing
                    val rawGraceNotes = vState.pendingGraceNotes
                    val coalescedGraceNotes = if (rawGraceNotes.isNotEmpty()) {
                        val stolenTotal = playedDuration.multiply(1, 2)
                        val perGraceNodeStolen = stolenTotal.multiply(1, rawGraceNotes.size)
                        val scaledGraceNotes = rawGraceNotes.map { it.copy(playedDuration = perGraceNodeStolen) }
                        playedDuration = addDurations(playedDuration, stolenTotal.multiply(-1, 1))
                        rawGraceNotes.clear()
                        scaledGraceNotes
                    } else emptyList()

                    val voiceList = voices.getOrPut(currentVoiceId) { mutableListOf() }

                    val sortedMidi = midiPitches.sorted()
                    val tieKey = currentVoiceId to sortedMidi
                    val tiedFrom = openTies[tieKey]
                    
                    if (tiedFrom != null) {
                        val originalList = voices[tiedFrom.first]!!
                        val originalNote = originalList[tiedFrom.second]
                        originalList[tiedFrom.second] = originalNote.copy(playedDuration = addDurations(originalNote.playedDuration, playedDuration))
                        voiceList.add(InterpretedNote(pitches = emptyList(), midiPitches = emptyList(), duration = duration, playedDuration = playedDuration, graceNotes = coalescedGraceNotes))
                        
                        val hasTieOut = element.notes.firstOrNull()?.ties?.let { it == TieType.START || it == TieType.BOTH } ?: false
                        if (hasTieOut) {
                            openTies[tieKey] = tiedFrom
                        } else {
                            openTies.remove(tieKey)
                        }
                    } else {
                        val newNoteIndex = voiceList.size
                        voiceList.add(InterpretedNote(pitches = interpretedPitches, midiPitches = midiPitches, duration = duration, playedDuration = playedDuration, graceNotes = coalescedGraceNotes))
                        val hasTieOut = element.notes.firstOrNull()?.ties?.let { it == TieType.START || it == TieType.BOTH } ?: false
                        if (hasTieOut) {
                            openTies[tieKey] = currentVoiceId to newNoteIndex
                        }
                    }
                }
                is RestElement -> {
                    val tuplet = vState.activeTuplet
                    var duration = element.duration
                    var playedDuration = duration
                    if (tuplet != null && tuplet.remainingNotes > 0) {
                        playedDuration = playedDuration.multiply(tuplet.q, tuplet.p)
                        duration = duration.multiply(tuplet.q, tuplet.p)
                        tuplet.remainingNotes--
                        if (tuplet.remainingNotes == 0) vState.activeTuplet = null
                    }

                    voices.getOrPut(currentVoiceId) { mutableListOf() }.add(
                        InterpretedNote(pitches = emptyList(), midiPitches = emptyList(), duration = duration, playedDuration = playedDuration, isRest = true)
                    )
                }
                is BarLineElement -> {
                    vState.activeAccidentals.clear()
                }
                is VariantElement -> {
                    // Variants are handled by the RepeatExpander, no state change needed here.
                }
                else -> {}
            }
        }
        
        return InterpretedTune(voices)
    }

    private fun parseCombinedTransposition(text: String): Int? {
        val lower = text.lowercase()
        var totalShift = 0
        var foundAny = false
        
        // 1. Check for clef-based octave modifiers
        val clefShift = when {
            lower.contains("treble-8") || lower.contains("treble8vb") -> -12
            lower.contains("bass+8") || lower.contains("bass8va") -> 12
            lower.contains("-8va") || lower.contains("8vb") || lower.contains("-8") || lower.contains("8-") -> -12
            lower.contains("+8va") || lower.contains("8va") || lower.contains("+8") || lower.contains(" treble8") -> 12
            lower.contains("clef=bass") -> -24 // Standard bass clef shift from treble baseline
            lower.contains("clef=alto") -> -12 // Standard alto clef shift
            else -> null
        }
        if (clefShift != null) {
            totalShift += clefShift
            foundAny = true
        }
        
        // 2. Check for transpose=N
        val transposeRegex = "transpose=([-]?\\d+)".toRegex()
        transposeRegex.find(text)?.let {
            totalShift += it.groupValues[1].toIntOrNull() ?: 0
            foundAny = true
        }
        
        // 3. Check for octave=N
        val octaveRegex = "octave=([-]?\\d+)".toRegex()
        octaveRegex.find(text)?.let {
            totalShift += (it.groupValues[1].toIntOrNull() ?: 0) * 12
            foundAny = true
        }
        
        return if (foundAny) totalShift else null
    }

    private fun addDurations(d1: NoteDuration, d2: NoteDuration): NoteDuration {
        val commonDenom = d1.denominator.toLong() * d2.denominator.toLong()
        val newNum = d1.numerator.toLong() * d2.denominator + d2.numerator.toLong() * d1.denominator
        return NoteDuration.simplify(newNum.toInt(), commonDenom.toInt())
    }

    private fun NoteDuration.multiply(p: Int, q: Int): NoteDuration {
        return NoteDuration.simplify(this.numerator * p, this.denominator * q)
    }

    private fun NoteDuration.multiply(multiplier: Double): NoteDuration {
        // Fallback for non-simple multipliers
        val newNumerator = (this.numerator * multiplier * 1000).toInt()
        val newDenominator = this.denominator * 1000
        return NoteDuration.simplify(newNumerator, newDenominator)
    }

    private fun parseMeter(text: String): TimeSignature {
        return when (text) {
            "C" -> TimeSignature(4, 4, "C")
            "C|" -> TimeSignature(2, 2, "C|")
            "none" -> TimeSignature(4, 4) // Default
            else -> {
                val parts = text.split("/")
                if (parts.size == 2) {
                    TimeSignature(parts[0].trim().toIntOrNull() ?: 4, parts[1].trim().toIntOrNull() ?: 4)
                } else TimeSignature(4, 4)
            }
        }
    }

    /**
     * Interprets a literal note based on the current key signature and any active accidentals in the measure.
     * 
     * @param note The literal note parsed from ABC (may have explicit accidental or not)
     * @param key The current key signature
     * @param activeAccidentals A map of step to accidental active in the current measure
     * @return The absolute pitch
     */
    public fun interpret(note: NoteElement, key: KeySignature, activeAccidentals: Map<Pair<NoteStep, Int>, Accidental>): Pitch {
        val step = note.pitch.step
        val octave = note.pitch.octave
        val explicitAccidental = note.pitch.accidental ?: note.accidental

        val interpretedAccidental = if (explicitAccidental != null) {
            explicitAccidental
        } else if (activeAccidentals.containsKey(step to octave)) {
            activeAccidentals[step to octave]
        } else {
            val fromKey = getAccidentalFromKey(step, key)
            if (logger.isDebugEnabled) {
                logger.debug("Pitch $step at octave $octave using accidental from key: $fromKey")
            }
            fromKey
        }
        
        if (logger.isDebugEnabled) {
            logger.debug("Pitch $step at octave $octave interpreted accidental: $interpretedAccidental")
        }

        return note.pitch.copy(accidental = interpretedAccidental)
    }

    private fun getAccidentalFromKey(step: NoteStep, key: KeySignature): Accidental? {
        val candidate = CircleOfFifths.getBestKey(key)
        val k = candidate.accidentalsCount
        
        val accidentalValue = CircleOfFifths.getAccidentalForStep(step, k)
        return CircleOfFifths.semitonesToAccidental(accidentalValue)
    }
}
