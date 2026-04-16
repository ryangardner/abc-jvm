package io.github.ryangardner.abc.theory
import io.github.ryangardner.abc.core.model.AbcTune
import io.github.ryangardner.abc.core.model.Accidental
import io.github.ryangardner.abc.core.model.BarLineElement
import io.github.ryangardner.abc.core.model.BodyHeaderElement
import io.github.ryangardner.abc.core.model.ChordElement
import io.github.ryangardner.abc.core.model.Decoration
import io.github.ryangardner.abc.core.model.DirectiveElement
import io.github.ryangardner.abc.core.model.GraceNoteElement
import io.github.ryangardner.abc.core.model.HeaderType
import io.github.ryangardner.abc.core.model.InlineFieldElement
import io.github.ryangardner.abc.core.model.KeySignature
import io.github.ryangardner.abc.core.model.MusicElement
import io.github.ryangardner.abc.core.model.NoteDuration
import io.github.ryangardner.abc.core.model.NoteElement
import io.github.ryangardner.abc.core.model.NoteStep
import io.github.ryangardner.abc.core.model.Pitch
import io.github.ryangardner.abc.core.model.RestElement
import io.github.ryangardner.abc.core.model.SpacerElement
import io.github.ryangardner.abc.core.model.TieType
import io.github.ryangardner.abc.core.model.TimeSignature
import io.github.ryangardner.abc.core.model.TupletElement
import io.github.ryangardner.abc.theory.util.InterpretationUtils
import io.github.ryangardner.abc.theory.util.KeyParserUtil
import io.github.ryangardner.abc.theory.util.addDurations
import io.github.ryangardner.abc.theory.util.multiply
import org.slf4j.Logger
import org.slf4j.LoggerFactory

public data class InterpretedNote(
    public val pitches: List<Pitch>,
    /**
     * Absolute MIDI pitches (after transpositions)
     */
    public val midiPitches: List<Int>,
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
     * Absolute MIDI pitches that are continuations of a tie from a previous note.
     */
    public val continuedMidiPitches: List<Int> = emptyList(),
    /**
     * A list of optional annotations (e.g., chord symbols like "Am") attached to the note.
     */
    public val annotations: List<String> = emptyList(),
    /**
     * A list of musical decorations or articulations (e.g., staccato, roll) attached to the note.
     */
    public val decorations: List<Decoration> = emptyList(),
)

public data class InterpretedTune(
    public val voices: Map<String, List<InterpretedNote>>,
    public val validationErrors: List<String> = emptyList(),
)

internal data class TupletState(
    val q: Int,
    val p: Int,
    var remainingNotes: Int,
)

@Suppress("LongParameterList")
internal class VoiceState(
    var currentKey: KeySignature,
    var currentMeter: TimeSignature,
    var currentDefaultLength: NoteDuration,
    val activeAccidentals: MutableMap<Pair<NoteStep, Int>, Accidental> = mutableMapOf(),
    var midiTranspose: Int = 0,
    var activeTuplet: TupletState? = null,
    val pendingGraceNotes: MutableList<InterpretedNote> = mutableListOf(),
    var measureDuration: NoteDuration = NoteDuration(0, 1),
    var measureCount: Int = 0,
)

internal class InterpretationSession(
    val tune: AbcTune,
) {
    val voices = mutableMapOf<String, MutableList<InterpretedNote>>()
    val validationErrors = mutableListOf<String>()
    val voiceStates = mutableMapOf<String, VoiceState>()
    val openTies = mutableMapOf<Pair<String, List<Int>>, Pair<String, Int>>() // Tie Key -> (VoiceId, NoteIndex)

    var currentVoiceId = "1"
    var globalMidiTranspose = 0

    fun currentVoiceState(): VoiceState = getVoiceState(currentVoiceId)

    fun getVoiceState(id: String): VoiceState =
        voiceStates.getOrPut(id) {
            VoiceState(
                currentKey = tune.header.key,
                currentMeter = tune.header.meter,
                currentDefaultLength = tune.header.length,
                midiTranspose = globalMidiTranspose,
            )
        }

    fun appendNote(note: InterpretedNote) {
        voices.getOrPut(currentVoiceId) { mutableListOf() }.add(note)
    }

    fun getCurrentVoiceList(): MutableList<InterpretedNote> = voices.getOrPut(currentVoiceId) { mutableListOf() }

    fun calculateDuration(multiplier: io.github.ryangardner.abc.core.model.DurationMultiplier): NoteDuration {
        val defaultLen = currentVoiceState().currentDefaultLength
        return NoteDuration.simplify(
            multiplier.numerator.toLong() * defaultLen.numerator,
            multiplier.denominator.toLong() * defaultLen.denominator,
        )
    }
}

public object PitchInterpreter {
    private val logger: Logger = LoggerFactory.getLogger(PitchInterpreter::class.java)
    private val WHITESPACE_REGEX = "\\s+".toRegex()

    /**
     * Converts an AbcTune to a Timeline, providing a high-level view of musical events.
     */
    @JvmStatic
    public fun toTimeline(tune: AbcTune): Timeline {
        val interpreted = interpret(tune)
        val events = mutableListOf<TimeEvent>()

        interpreted.voices.forEach { (voiceId, notes) ->
            var currentBeat = 0.0
            notes.forEach { note ->
                events.add(TimeEvent(currentBeat, note, voiceId))
                currentBeat += note.semanticDuration.toDouble()
            }
        }

        return Timeline(events.sortedBy { it.beat })
    }

    private object HeaderProcessor {
        fun processGlobalHeaders(
            session: InterpretationSession,
            tune: AbcTune,
        ) {
            tune.header.headers.forEach { (id, value) ->
                when (id) {
                    "V" -> processVoiceHeader(session, value)
                    "K" -> processKeyHeader(session, value)
                    "%%" -> processDirectiveHeader(session, value)
                }
            }
        }

        private fun processVoiceHeader(
            session: InterpretationSession,
            value: String,
        ) {
            val voiceId = value.split(" ", "\t").first()
            val vState = session.getVoiceState(voiceId)
            InterpretationUtils.parseCombinedTransposition(value)?.let { vState.midiTranspose = it }
        }

        private fun processKeyHeader(
            session: InterpretationSession,
            value: String,
        ) {
            val vState = session.getVoiceState("1")
            InterpretationUtils.parseCombinedTransposition(value)?.let { vState.midiTranspose = it }
        }

        private fun processDirectiveHeader(
            session: InterpretationSession,
            value: String,
        ) {
            if (value.startsWith("MIDI transpose", ignoreCase = true)) {
                val transpose = value.split(WHITESPACE_REGEX).last().toIntOrNull() ?: 0
                session.globalMidiTranspose = transpose
                session.voiceStates.values.forEach { it.midiTranspose = transpose }
            }
        }

        fun handleBodyHeader(
            session: InterpretationSession,
            element: BodyHeaderElement,
        ) {
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

                "L" -> {
                    val parts = element.value.split("/")
                    if (parts.size == 2) {
                        vState.currentDefaultLength = NoteDuration(parts[0].toIntOrNull() ?: 1, parts[1].toIntOrNull() ?: 8)
                    }
                }
            }
        }

        fun handleInlineField(
            session: InterpretationSession,
            element: InlineFieldElement,
        ) {
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

                HeaderType.LENGTH -> {
                    val parts = element.value.split("/")
                    if (parts.size == 2) {
                        vState.currentDefaultLength = NoteDuration(parts[0].toIntOrNull() ?: 1, parts[1].toIntOrNull() ?: 8)
                    }
                }

                else -> {}
            }
        }

        fun handleDirective(
            session: InterpretationSession,
            element: DirectiveElement,
        ) {
            if (element.content.startsWith("MIDI transpose", ignoreCase = true)) {
                session.currentVoiceState().midiTranspose = element.content
                    .split(WHITESPACE_REGEX)
                    .last()
                    .toIntOrNull() ?: 0
            }
        }
    }

    private object TimeCalculator {
        data class DurationResult(
            val semantic: NoteDuration,
            val played: NoteDuration,
        )

        fun calculate(
            baseDuration: NoteDuration,
            tuplet: TupletState?,
            session: InterpretationSession,
        ): DurationResult {
            val vState = session.currentVoiceState()
            var scaled = baseDuration
            if (tuplet != null && tuplet.remainingNotes > 0) {
                scaled = scaled.multiply(tuplet.q, tuplet.p)
                tuplet.remainingNotes--
            }

            return DurationResult(scaled, scaled) // semantic is same as played (before grace stealing)
        }

        fun handleGraceStealing(
            session: InterpretationSession,
            playedDuration: NoteDuration,
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

    internal object PitchResolver {
        internal fun resolve(
            note: NoteElement,
            session: InterpretationSession,
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
        internal fun interpretBasePitch(
            note: NoteElement,
            key: KeySignature,
            activeAccidentals: Map<Pair<NoteStep, Int>, Accidental>,
        ): Pitch {
            val step = note.pitch.step
            val octave = note.pitch.octave
            val explicitAccidental = note.pitch.accidental ?: note.accidental

            val interpretedAccidental =
                if (explicitAccidental != null) {
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

        private fun interpretedAccidentalFromKey(
            step: NoteStep,
            octave: Int,
            fromKey: Accidental?,
        ) {
            if (logger.isDebugEnabled) {
                logger.debug("Pitch $step at octave $octave using accidental from key: $fromKey")
            }
        }

        private fun interpretedAccidentalDebug(
            step: NoteStep,
            octave: Int,
            interpretedAccidental: Accidental?,
        ) {
            if (logger.isDebugEnabled) {
                logger.debug("Pitch $step at octave $octave interpreted accidental: $interpretedAccidental")
            }
        }

        private fun getAccidentalFromKey(
            step: NoteStep,
            key: KeySignature,
        ): Accidental? {
            val candidate = CircleOfFifths.getBestKey(key)
            val k = candidate.accidentalsCount

            val accidentalValue = CircleOfFifths.getAccidentalForStep(step, k)
            return CircleOfFifths.semitonesToAccidental(accidentalValue)
        }
    }

    private object TieResolver {
        data class TieResult(
            val resolvedPitches: Map<Int, Pair<String, Int>>,
            val adjustedMidiPitches: List<Int>,
        )

        fun resolve(
            session: InterpretationSession,
            midiPitches: List<Int>,
            hasExplicitAccidental: Boolean,
            isChord: Boolean = false,
        ): TieResult {
            val voiceId = session.currentVoiceId
            val openTies = session.openTies

            val resolved = mutableMapOf<Int, Pair<String, Int>>()
            val adjusted = midiPitches.toMutableList()

            // 1. Try exact match for the whole set (e.g. [CEG] tied to [CEG])
            val sortedMidi = midiPitches.sorted()
            val fullTieKey = voiceId to sortedMidi
            val fullTiedFrom = openTies[fullTieKey]

            if (fullTiedFrom != null) {
                midiPitches.forEach { resolved[it] = fullTiedFrom }
            } else {
                // 2. Try individual pitch matches
                midiPitches.forEachIndexed { index, midiPitch ->
                    val singleTieKey = voiceId to listOf(midiPitch)
                    val tiedFrom = openTies[singleTieKey]
                    if (tiedFrom != null) {
                        resolved[midiPitch] = tiedFrom
                    } else if (!isChord && !hasExplicitAccidental) {
                        // Heuristic fuzzy match for single notes only
                        val heuristicMatch = openTies.entries.find { (key, _) ->
                            key.first == voiceId && key.second.size == 1 && Math.abs(key.second[0] - midiPitch) <= 2
                        }
                        if (heuristicMatch != null) {
                            resolved[midiPitch] = heuristicMatch.value
                            adjusted[index] = heuristicMatch.key.second[0]
                        }
                    }
                }
            }

            return TieResult(resolved, adjusted)
        }
    }

    public fun interpret(tune: AbcTune): InterpretedTune {
        val repairedTune = RepairEngine.resolveBrokenRhythms(tune)
        val expandedElements = RepeatExpander.expand(repairedTune)
        return interpretElements(repairedTune, expandedElements)
    }

    public fun interpretUnexpanded(tune: AbcTune): InterpretedTune {
        val repairedTune = RepairEngine.resolveBrokenRhythms(tune)
        return interpretElements(repairedTune, repairedTune.body.elements)
    }

    private fun interpretElements(
        tune: AbcTune,
        elements: List<MusicElement>,
    ): InterpretedTune {
        val session = InterpretationSession(tune)
        HeaderProcessor.processGlobalHeaders(session, tune)
        session.getVoiceState(session.currentVoiceId)

        elements.forEachIndexed { idx, element ->
            val vState = session.currentVoiceState()
            when (element) {
                is BodyHeaderElement -> {
                    HeaderProcessor.handleBodyHeader(session, element)
                }

                is InlineFieldElement -> {
                    HeaderProcessor.handleInlineField(session, element)
                }

                is DirectiveElement -> {
                    HeaderProcessor.handleDirective(session, element)
                }

                is TupletElement -> {
                    handleTuplet(element, vState)
                }

                is GraceNoteElement -> {
                    handleGraceNote(element, vState, session)
                }

                is NoteElement -> {
                    handleNote(element, vState, session)
                }

                is ChordElement -> {
                    handleChord(element, vState, session)
                }

                is RestElement -> {
                    handleRest(element, vState, session)
                }

                is BarLineElement -> {
                    handleBarLine(vState, session)
                }

                else -> {}
            }
        }
        return InterpretedTune(session.voices, session.validationErrors)
    }

    @Suppress("MagicNumber")
    private fun handleTuplet(
        element: TupletElement,
        vState: VoiceState,
    ) {
        val p = element.p
        val isCompound = (vState.currentMeter.numerator % 3 == 0 && vState.currentMeter.numerator > 3)
        val q =
            element.q ?: when (p) {
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

    private fun handleGraceNote(
        element: GraceNoteElement,
        vState: VoiceState,
        session: InterpretationSession,
    ) {
        element.notes.forEach { note ->
            val interpretedPitch = PitchResolver.resolve(note, session)
            val midiPitch = interpretedPitch.midiNoteNumber + vState.midiTranspose
            val calculatedDur = session.calculateDuration(note.durationMultiplier)
            vState.pendingGraceNotes.add(
                InterpretedNote(
                    pitches = listOf(interpretedPitch),
                    midiPitches = listOf(midiPitch),
                    duration = calculatedDur,
                    semanticDuration = NoteDuration(0, 1),
                    playedDuration = calculatedDur,
                    isGrace = true,
                ),
            )
        }
    }

    private fun handleNote(
        element: NoteElement,
        vState: VoiceState,
        session: InterpretationSession,
    ) {
        val interpretedPitch = PitchResolver.resolve(element, session)
        val midiPitch = interpretedPitch.midiNoteNumber + vState.midiTranspose
        val hasExplicitAccidental = element.pitch.accidental != null || element.accidental != null

        val noteDuration = session.calculateDuration(element.durationMultiplier)

        processMusicEvent(
            session,
            MusicEventContext(
                pitches = listOf(interpretedPitch),
                midiPitches = listOf(midiPitch),
                baseDuration = noteDuration,
                tieType = element.ties,
                pitchTies = listOf(element.ties),
                hasExplicitAccidental = hasExplicitAccidental,
                annotations = element.annotations,
                decorations = element.decorations,
            ),
        )
    }

    private fun handleChord(
        element: ChordElement,
        vState: VoiceState,
        session: InterpretationSession,
    ) {
        val interpretedPitches = element.notes.map { PitchResolver.resolve(it, session) }
        val midiPitches = interpretedPitches.map { it.midiNoteNumber + vState.midiTranspose }
        val pitchTies = element.notes.map { it.ties }
        val hasTieOut = pitchTies.any { it == TieType.START || it == TieType.BOTH }
        val tieType = if (hasTieOut) TieType.START else TieType.NONE

        processMusicEvent(
            session,
            MusicEventContext(
                pitches = interpretedPitches,
                midiPitches = midiPitches,
                baseDuration = session.calculateDuration(element.durationMultiplier),
                tieType = tieType,
                pitchTies = pitchTies,
                hasExplicitAccidental = false,
                annotations = element.annotations,
                decorations = element.decorations,
                isChord = true,
            ),
        )
    }

    private fun handleRest(
        element: RestElement,
        vState: VoiceState,
        session: InterpretationSession,
    ) {
        val restDuration = session.calculateDuration(element.durationMultiplier)
        val timing = TimeCalculator.calculate(restDuration, vState.activeTuplet, session)
        if (vState.activeTuplet?.remainingNotes == 0) vState.activeTuplet = null

        session.appendNote(
            InterpretedNote(
                pitches = emptyList(),
                midiPitches = emptyList(),
                duration = restDuration,
                semanticDuration = timing.semantic,
                playedDuration = timing.played,
                isRest = true,
                annotations = element.annotations,
                decorations = element.decorations,
            ),
        )
    }

    private fun handleBarLine(
        vState: VoiceState,
        session: InterpretationSession,
    ) {
        vState.activeAccidentals.clear()
        val expectedTotal = NoteDuration(vState.currentMeter.numerator, vState.currentMeter.denominator)
        if (vState.measureDuration.numerator != 0 && vState.measureDuration != expectedTotal) {
            if (!(vState.measureCount == 0 && vState.measureDuration.toDouble() < expectedTotal.toDouble())) {
                session.validationErrors.add(
                    "Voice ${session.currentVoiceId} Measure ${vState.measureCount + 1}: Rhythmic mismatch. Expected $expectedTotal, found ${vState.measureDuration}",
                )
            }
        }
        vState.measureCount++
        vState.measureDuration = NoteDuration(0, 1)
    }

    private data class MusicEventContext(
        val pitches: List<Pitch>,
        val midiPitches: List<Int>,
        val baseDuration: NoteDuration,
        val tieType: TieType,
        val pitchTies: List<TieType>,
        val hasExplicitAccidental: Boolean,
        val annotations: List<String>,
        val decorations: List<Decoration>,
        val isChord: Boolean = false,
    )

    private fun processMusicEvent(
        session: InterpretationSession,
        event: MusicEventContext,
    ) {
        val pitches = event.pitches
        val midiPitches = event.midiPitches
        val baseDuration = event.baseDuration
        val tieType = event.tieType
        val hasExplicitAccidental = event.hasExplicitAccidental
        val annotations = event.annotations
        val decorations = event.decorations
        val isChord = event.isChord
        val vState = session.currentVoiceState()
        val timing = TimeCalculator.calculate(baseDuration, vState.activeTuplet, session)
        if (vState.activeTuplet?.remainingNotes == 0) vState.activeTuplet = null
        vState.measureDuration += timing.semantic

        val playedDuration = TimeCalculator.handleGraceStealing(session, timing.played)
        val voiceList = session.getCurrentVoiceList()

        val tieResult = TieResolver.resolve(session, midiPitches, hasExplicitAccidental, event.isChord)
        val resolvedPitches = tieResult.resolvedPitches
        val adjustedMidiPitches = tieResult.adjustedMidiPitches

        val newPitches = mutableListOf<Pitch>()
        val newMidiPitches = mutableListOf<Int>()
        val continuedMidiPitches = mutableListOf<Int>()

        val extendedEvents = mutableSetOf<Pair<String, Int>>()
        adjustedMidiPitches.forEachIndexed { index, midiPitch ->
            val tiedFrom = resolvedPitches[midiPitch]
            if (tiedFrom != null) {
                continuedMidiPitches.add(midiPitch)
                // Extend the original note (only once per source event)
                if (extendedEvents.add(tiedFrom)) {
                    val originalList = session.voices[tiedFrom.first]!!
                    val originalNote = originalList[tiedFrom.second]
                    originalList[tiedFrom.second] = originalNote.copy(
                        playedDuration = addDurations(originalNote.playedDuration, playedDuration),
                        semanticDuration = addDurations(originalNote.semanticDuration, timing.semantic)
                    )
                }
            } else {
                newMidiPitches.add(midiPitch)
                newPitches.add(pitches[index])
            }
        }

        val newNoteIndex = voiceList.size
        val isFullContinuation = newMidiPitches.isEmpty() && continuedMidiPitches.isNotEmpty()

        voiceList.add(
            InterpretedNote(
                pitches = newPitches,
                midiPitches = newMidiPitches,
                continuedMidiPitches = continuedMidiPitches,
                duration = baseDuration,
                semanticDuration = timing.semantic,
                playedDuration = playedDuration,
                isRest = false,
                isGrace = false,
                isTieContinued = isFullContinuation,
                annotations = annotations,
                decorations = decorations,
            )
        )

        // Handle opening ties for the NEXT event
        // We must remove ties that were resolved but NOT re-started
        // And add ties that are START or BOTH
        
        // 1. Remove all resolved ties from openTies
        resolvedPitches.values.toSet().forEach { tiedFrom ->
            session.openTies.entries.removeIf { it.value == tiedFrom }
        }

        // 2. Add new ties
        if (event.isChord) {
            // Add individual note ties
            adjustedMidiPitches.forEachIndexed { index, midiPitch ->
                val pTie = event.pitchTies[index]
                if (pTie == TieType.START || pTie == TieType.BOTH) {
                    session.openTies[session.currentVoiceId to listOf(midiPitch)] = session.currentVoiceId to newNoteIndex
                }
            }
            // If the WHOLE chord is tied (chord-level tie), also add a chord tie key for backward compatibility/fuzzy matching
            if (event.tieType == TieType.START || event.tieType == TieType.BOTH) {
                session.openTies[session.currentVoiceId to adjustedMidiPitches.sorted()] = session.currentVoiceId to newNoteIndex
            }
        } else {
            // Single note
            if (event.tieType == TieType.START || event.tieType == TieType.BOTH) {
                session.openTies[session.currentVoiceId to adjustedMidiPitches.sorted()] = session.currentVoiceId to newNoteIndex
            }
        }
    }
}
