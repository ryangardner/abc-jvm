package io.github.ryangardner.abc.theory

import io.github.ryangardner.abc.core.model.*
import io.github.ryangardner.abc.theory.util.InterpretationUtils
import io.github.ryangardner.abc.theory.util.KeyParserUtil
import io.github.ryangardner.abc.theory.util.addDurations
import io.github.ryangardner.abc.theory.util.multiply
import org.slf4j.Logger
import org.slf4j.LoggerFactory

public data class InterpretedNote(
    public val pitches: List<Pitch>,
    public val midiPitches: List<Int>, // Absolute MIDI pitches (after transpositions)
    /**
     * The written duration as indicated by the score's notation (e.g., a quarter note).
     * This value corresponds to the visual length of the note symbol on the staff.
     * In ABC, this is the note length multiplied by the default unit length (L:).
     * (Matches abcjs's "duration" property in notation JSON).
     */
    public val duration: NoteDuration,
    /**
     * The nominal musical duration of the note within the rhythmic grid.
     * This accounts for tuplets (e.g. 3 notes in the time of 2) to maintain proper 
     * measure alignment, but provides the "clean" theoretical value before any 
     * performative interpretation (like grace notes stealing time) is applied.
     * (Matches music21's scaled length).
     */
    public val semanticDuration: NoteDuration,
    /**
     * The performative duration of the note as it would be heard.
     * This includes both tuplet scaling and "duration stealing" from embellishments 
     * like grace notes. Different playback engines may have different opinions 
     * on how much time an embellishment "steals" from its neighbor.
     * (Matches the duration found in abcjs MIDI tracks).
     */
    public val playedDuration: NoteDuration,
    /**
     * Indicates the event is a rest (silence) rather than a pitched note.
     */
    public val isRest: Boolean = false,
    /**
     * Indicates the note is a grace note (embellishment) which has no nominal 
     * duration in the rhythmic grid, but may "steal" performative time (playedDuration) 
     * from the following main note.
     */
    public val isGrace: Boolean = false,
    /**
     * Indicates that this note is a continuation of a tie from a previous note.
     * Tied notes represent a single sustained sound across rhythmic boundaries 
     * rather than separate articulations.
     */
    public val isTieContinued: Boolean = false,
    /**
     * An optional annotation (e.g., a chord symbol like "Am") attached to the note.
     */
    public val annotation: String? = null,
    /**
     * A list of musical decorations or articulations (e.g., staccato, roll) attached to the note.
     */
    public val decorations: List<Decoration> = emptyList()
)

public data class InterpretedTune(
    public val voices: Map<String, List<InterpretedNote>>,
    public val validationErrors: List<String> = emptyList()
)

private data class TupletState(val q: Int, val p: Int, var remainingNotes: Int)

private class VoiceState(
    var currentKey: KeySignature,
    var currentMeter: TimeSignature,
    val activeAccidentals: MutableMap<Pair<NoteStep, Int>, Accidental> = mutableMapOf(),
    var midiTranspose: Int = 0,
    var activeTuplet: TupletState? = null,
    val pendingGraceNotes: MutableList<InterpretedNote> = mutableListOf(),
    var measureDuration: NoteDuration = NoteDuration(0, 1),
    var measureCount: Int = 0
)

private class InterpretationSession(val tune: AbcTune) {
    val voices = mutableMapOf<String, MutableList<InterpretedNote>>()
    val validationErrors = mutableListOf<String>()
    val voiceStates = mutableMapOf<String, VoiceState>()
    val openTies = mutableMapOf<Pair<String, List<Int>>, Pair<String, Int>>() // Tie Key -> (VoiceId, NoteIndex)

    var currentVoiceId = "1"
    var globalMidiTranspose = 0

    fun currentVoiceState(): VoiceState = getVoiceState(currentVoiceId)

    fun getVoiceState(id: String): VoiceState {
        return voiceStates.getOrPut(id) {
            VoiceState(
                currentKey = tune.header.key,
                currentMeter = tune.header.meter,
                midiTranspose = globalMidiTranspose
            )
        }
    }

    fun appendNote(note: InterpretedNote) {
        voices.getOrPut(currentVoiceId) { mutableListOf() }.add(note)
    }

    fun getCurrentVoiceList(): MutableList<InterpretedNote> = voices.getOrPut(currentVoiceId) { mutableListOf() }
}

public object PitchInterpreter {
    private val logger: Logger = LoggerFactory.getLogger(PitchInterpreter::class.java)

    private object HeaderProcessor {
        fun processGlobalHeaders(session: InterpretationSession, tune: AbcTune) {
            tune.header.headers.forEach { (id, value) ->
                when (id) {
                    "V" -> {
                        val voiceId = value.split(" ", "\t").first()
                        val vState = session.getVoiceState(voiceId)
                        InterpretationUtils.parseCombinedTransposition(value)?.let { vState.midiTranspose = it }
                    }
                    "K" -> {
                        val vState = session.getVoiceState("1")
                        InterpretationUtils.parseCombinedTransposition(value)?.let { vState.midiTranspose = it }
                    }
                    "%%" -> {
                        if (value.startsWith("MIDI transpose", ignoreCase = true)) {
                            val transpose = value.split("\\s+".toRegex()).last().toIntOrNull() ?: 0
                            session.globalMidiTranspose = transpose
                            session.voiceStates.values.forEach { it.midiTranspose = transpose }
                        }
                    }
                }
            }
        }

        fun handleBodyHeader(session: InterpretationSession, element: BodyHeaderElement) {
            val vState = session.currentVoiceState()
            when (element.key) {
                "V" -> {
                    session.currentVoiceId = element.value.split(" ", "\t").first()
                    val newState = session.getVoiceState(session.currentVoiceId)
                    InterpretationUtils.parseCombinedTransposition(element.value)?.let { newState.midiTranspose = it }
                }
                "K" -> {
                    vState.currentKey = KeyParserUtil.parse(element.value)
                    InterpretationUtils.parseCombinedTransposition(element.value)?.let { vState.midiTranspose = it }
                }
                "M" -> {
                    vState.currentMeter = InterpretationUtils.parseMeter(element.value)
                }
            }
        }

        fun handleInlineField(session: InterpretationSession, element: InlineFieldElement) {
            val vState = session.currentVoiceState()
            when (element.fieldType) {
                HeaderType.KEY -> {
                    vState.currentKey = KeyParserUtil.parse(element.value)
                    InterpretationUtils.parseCombinedTransposition(element.value)?.let { vState.midiTranspose = it }
                }
                HeaderType.VOICE -> {
                    session.currentVoiceId = element.value.split(" ", "\t").first()
                    val newState = session.getVoiceState(session.currentVoiceId)
                    InterpretationUtils.parseCombinedTransposition(element.value)?.let { newState.midiTranspose = it }
                }
                HeaderType.METER -> {
                    vState.currentMeter = InterpretationUtils.parseMeter(element.value)
                }
                else -> {}
            }
        }

        fun handleDirective(session: InterpretationSession, element: DirectiveElement) {
            if (element.content.startsWith("MIDI transpose", ignoreCase = true)) {
                session.currentVoiceState().midiTranspose = element.content.split("\\s+".toRegex()).last().toIntOrNull() ?: 0
            }
        }
    }

    private object TimeCalculator {
        data class DurationResult(
            val semantic: NoteDuration,
            val played: NoteDuration
        )

        fun calculate(
            baseDuration: NoteDuration,
            tuplet: TupletState?
        ): DurationResult {
            var played = baseDuration
            if (tuplet != null && tuplet.remainingNotes > 0) {
                played = played.multiply(tuplet.q, tuplet.p)
                tuplet.remainingNotes--
            }
            return DurationResult(played, played) // semantic is same as played (before grace stealing)
        }

        fun handleGraceStealing(
            session: InterpretationSession,
            playedDuration: NoteDuration
        ): NoteDuration {
            var adjustedPlayed = playedDuration
            val vState = session.currentVoiceState()
            val rawGraceNotes = vState.pendingGraceNotes
            if (rawGraceNotes.isNotEmpty()) {
                val stolenTotal = playedDuration.multiply(1, 2)
                val perGraceNodeStolen = stolenTotal.multiply(1, rawGraceNotes.size)
                val scaledGraceNotes = rawGraceNotes.map { it.copy(playedDuration = perGraceNodeStolen) }
                adjustedPlayed = addDurations(playedDuration, stolenTotal.multiply(-1, 1))

                session.getCurrentVoiceList().addAll(scaledGraceNotes)
                rawGraceNotes.clear()
            }
            return adjustedPlayed
        }
    }

    private object PitchResolver {
        fun resolve(
            note: NoteElement,
            session: InterpretationSession
        ): Pitch {
            val vState = session.currentVoiceState()
            val interpretedPitch = interpretBasePitch(note, vState.currentKey, vState.activeAccidentals)

            val explicitAccidental = note.pitch.accidental ?: note.accidental
            if (explicitAccidental != null) {
                vState.activeAccidentals[note.pitch.step to note.pitch.octave] = explicitAccidental
            }
            return interpretedPitch
        }

        /**
         * Interprets a literal note based on the current key signature and any active accidentals in the measure.
         *
         * @param note The literal note parsed from ABC (may have explicit accidental or not)
         * @param key The current key signature
         * @param activeAccidentals A map of step to accidental active in the current measure
         * @return The absolute pitch
         */
        fun interpretBasePitch(note: NoteElement, key: KeySignature, activeAccidentals: Map<Pair<NoteStep, Int>, Accidental>): Pitch {
            val step = note.pitch.step
            val octave = note.pitch.octave
            val explicitAccidental = note.pitch.accidental ?: note.accidental

            val interpretedAccidental = if (explicitAccidental != null) {
                explicitAccidental
            } else if (activeAccidentals.containsKey(step to octave)) {
                activeAccidentals[step to octave]
            } else {
                val fromKey = getAccidentalFromKey(step, key)
                interpretedAccidentalFromKey(step, octave, fromKey)
                fromKey
            }

            interpretedAccidentalDebug(step, octave, interpretedAccidental)

            return note.pitch.copy(accidental = interpretedAccidental)
        }

        private fun interpretedAccidentalFromKey(step: NoteStep, octave: Int, fromKey: Accidental?) {
            if (logger.isDebugEnabled) {
                logger.debug("Pitch $step at octave $octave using accidental from key: $fromKey")
            }
        }

        private fun interpretedAccidentalDebug(step: NoteStep, octave: Int, interpretedAccidental: Accidental?) {
            if (logger.isDebugEnabled) {
                logger.debug("Pitch $step at octave $octave interpreted accidental: $interpretedAccidental")
            }
        }

        private fun getAccidentalFromKey(step: NoteStep, key: KeySignature): Accidental? {
            val candidate = CircleOfFifths.getBestKey(key)
            val k = candidate.accidentalsCount

            val accidentalValue = CircleOfFifths.getAccidentalForStep(step, k)
            return CircleOfFifths.semitonesToAccidental(accidentalValue)
        }
    }

    private object TieResolver {
        data class TieResult(
            val tiedFrom: Pair<String, Int>?,
            val adjustedMidiPitches: List<Int>
        )

        fun resolve(
            session: InterpretationSession,
            midiPitches: List<Int>,
            hasExplicitAccidental: Boolean,
            isChord: Boolean = false
        ): TieResult {
            val vState = session.currentVoiceState()
            val voiceId = session.currentVoiceId
            val openTies = session.openTies

            val sortedMidi = midiPitches.sorted()
            val tieKey = voiceId to sortedMidi
            var tiedFrom = openTies[tieKey]
            var adjustedMidi = midiPitches

            if (!isChord && tiedFrom == null && !hasExplicitAccidental) {
                // Fuzzy matching for single notes
                val midiPitch = midiPitches[0]
                val heuristicMatch = openTies.entries.find { (key, _) ->
                    key.first == voiceId && key.second.size == 1 && Math.abs(key.second[0] - midiPitch) <= 2
                }
                if (heuristicMatch != null) {
                    tiedFrom = heuristicMatch.value
                    adjustedMidi = listOf(heuristicMatch.key.second[0])
                }
            }
            return TieResult(tiedFrom, adjustedMidi)
        }
    }

    public fun interpret(tune: AbcTune): InterpretedTune {
        // abcjs MIDI output expands repeats, so we must expand them to achieve bit-perfect parity.
        val expandedElements = RepeatExpander.expand(tune)
        return interpretElements(tune, expandedElements)
    }

    public fun interpretUnexpanded(tune: AbcTune): InterpretedTune {
        return interpretElements(tune, tune.body.elements)
    }

    private fun interpretElements(tune: AbcTune, elements: List<MusicElement>): InterpretedTune {
        val session = InterpretationSession(tune)
        HeaderProcessor.processGlobalHeaders(session, tune)
        session.getVoiceState(session.currentVoiceId)

        elements.forEach { element ->
            val vState = session.currentVoiceState()
            when (element) {
                is BodyHeaderElement -> HeaderProcessor.handleBodyHeader(session, element)
                is InlineFieldElement -> HeaderProcessor.handleInlineField(session, element)
                is DirectiveElement -> HeaderProcessor.handleDirective(session, element)
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
                        val interpretedPitch = PitchResolver.resolve(note, session)
                        val midiPitch = interpretedPitch.midiNoteNumber + vState.midiTranspose
                        vState.pendingGraceNotes.add(InterpretedNote(
                            pitches = listOf(interpretedPitch),
                            midiPitches = listOf(midiPitch),
                            duration = note.length,
                            semanticDuration = NoteDuration(0, 1),
                            playedDuration = note.length,
                            isGrace = true
                        ))
                    }
                }
                is NoteElement -> {
                    val interpretedPitch = PitchResolver.resolve(element, session)
                    val midiPitch = interpretedPitch.midiNoteNumber + vState.midiTranspose
                    val hasExplicitAccidental = element.pitch.accidental != null || element.accidental != null

                    processMusicEvent(
                        session,
                        listOf(interpretedPitch),
                        listOf(midiPitch),
                        element.length,
                        element.ties,
                        hasExplicitAccidental,
                        element.annotation,
                        element.decorations
                    )
                }
                is ChordElement -> {
                    val interpretedPitches = element.notes.map { PitchResolver.resolve(it, session) }
                    val midiPitches = interpretedPitches.map { it.midiNoteNumber + vState.midiTranspose }
                    val hasTieOut = element.notes.firstOrNull()?.ties?.let { it == TieType.START || it == TieType.BOTH } ?: false
                    val tieType = if (hasTieOut) TieType.START else TieType.NONE

                    processMusicEvent(
                        session,
                        interpretedPitches,
                        midiPitches,
                        element.duration,
                        tieType,
                        false, // Chords don't use fuzzy accidental matching in current logic
                        element.annotation,
                        element.decorations,
                        isChord = true
                    )
                }
                is RestElement -> {
                    val timing = TimeCalculator.calculate(element.duration, vState.activeTuplet)
                    if (vState.activeTuplet?.remainingNotes == 0) vState.activeTuplet = null

                    session.appendNote(InterpretedNote(
                        pitches = emptyList(),
                        midiPitches = emptyList(),
                        duration = element.duration,
                        semanticDuration = timing.semantic,
                        playedDuration = timing.played,
                        isRest = true,
                        annotation = element.annotation,
                        decorations = element.decorations
                    ))
                }
                is BarLineElement -> {
                    vState.activeAccidentals.clear()
                    val expectedTotal = NoteDuration(vState.currentMeter.numerator, vState.currentMeter.denominator)
                    if (vState.measureDuration.numerator != 0 && vState.measureDuration != expectedTotal) {
                        if (!(vState.measureCount == 0 && vState.measureDuration.toDouble() < expectedTotal.toDouble())) {
                            session.validationErrors.add("Voice ${session.currentVoiceId} Measure ${vState.measureCount + 1}: Rhythmic mismatch. Expected $expectedTotal, found ${vState.measureDuration}")
                        }
                    }
                    vState.measureCount++
                    vState.measureDuration = NoteDuration(0, 1)
                }
                else -> {}
            }
        }
        return InterpretedTune(session.voices, session.validationErrors)
    }

    private fun processMusicEvent(
        session: InterpretationSession,
        pitches: List<Pitch>,
        midiPitches: List<Int>,
        baseDuration: NoteDuration,
        tieType: TieType,
        hasExplicitAccidental: Boolean,
        annotation: String?,
        decorations: List<Decoration>,
        isChord: Boolean = false
    ) {
        val vState = session.currentVoiceState()
        val timing = TimeCalculator.calculate(baseDuration, vState.activeTuplet)
        if (vState.activeTuplet?.remainingNotes == 0) vState.activeTuplet = null
        vState.measureDuration += timing.semantic

        val playedDuration = TimeCalculator.handleGraceStealing(session, timing.played)
        val voiceList = session.getCurrentVoiceList()

        val tieResult = TieResolver.resolve(session, midiPitches, hasExplicitAccidental, isChord)

        if (tieResult.tiedFrom != null) {
            val originalList = session.voices[tieResult.tiedFrom.first]!!
            val originalNote = originalList[tieResult.tiedFrom.second]
            originalList[tieResult.tiedFrom.second] = originalNote.copy(
                playedDuration = addDurations(originalNote.playedDuration, playedDuration),
                semanticDuration = addDurations(originalNote.semanticDuration, timing.semantic)
            )
            voiceList.add(InterpretedNote(
                pitches = emptyList(),
                midiPitches = emptyList(),
                duration = baseDuration,
                semanticDuration = timing.semantic,
                playedDuration = playedDuration,
                isTieContinued = true
            ))

            val actualTieKey = session.currentVoiceId to tieResult.adjustedMidiPitches.sorted()
            if (tieType == TieType.START || tieType == TieType.BOTH) {
                session.openTies[actualTieKey] = tieResult.tiedFrom
            } else {
                session.openTies.remove(actualTieKey)
            }
        } else {
            val newNoteIndex = voiceList.size
            voiceList.add(InterpretedNote(
                pitches = pitches,
                midiPitches = midiPitches,
                duration = baseDuration,
                semanticDuration = timing.semantic,
                playedDuration = playedDuration,
                annotation = annotation,
                decorations = decorations
            ))
            if (tieType == TieType.START || tieType == TieType.BOTH) {
                val tieKey = session.currentVoiceId to midiPitches.sorted()
                session.openTies[tieKey] = session.currentVoiceId to newNoteIndex
            }
        }
    }
}
