package io.github.ryangardner.abc.theory

import io.github.ryangardner.abc.core.model.NoteDuration

/**
 * A TimeEvent represents a musical occurrence at a specific rhythmic position.
 * 
 * @property beat The absolute beat position (0-indexed) where the event occurs.
 * @property note The interpreted musical information (pitch, duration, etc.).
 * @property voiceId The identifier of the voice this event belongs to.
 */
public data class TimeEvent(
    val beat: Double,
    val note: InterpretedNote,
    val voiceId: String
)

/**
 * A Timeline provides a high-level, time-ordered view of an ABC tune's musical content.
 * 
 * Unlike the raw [AbcTune] which is a linear stream of symbols, the [Timeline]
 * allows for vertical querying of what notes or chords are active at any given beat
 * across all voices.
 * 
 * @property events The complete list of musical events in the tune, sorted by their beat position.
 */
public class Timeline(
    public val events: List<TimeEvent>
) {
    /**
     * Returns all events that occur exactly at the specified [beat].
     * 
     * @param beat The target beat position.
     * @return A list of [TimeEvent]s occurring at that beat.
     */
    public fun getEventsAt(beat: Double): List<TimeEvent> {
        return events.filter { it.beat == beat }
    }

    /**
     * Returns all harmonic events (chords or annotated notes) occurring at the specified [beat].
     * 
     * This is useful for harmonic analysis, as it filters for events that either contain
     * multiple pitches or carry an explicit chord annotation (e.g., "Am7").
     * 
     * @param beat The target beat position.
     * @return A list of harmonic [TimeEvent]s.
     */
    public fun getChordsAt(beat: Double): List<TimeEvent> {
        return events.filter { it.beat == beat && (it.note.annotation != null || it.note.pitches.size > 1) }
    }

    /**
     * Returns all events that fall within the specified range [startBeat] to [endBeat].
     * 
     * @param startBeat The inclusive start of the range.
     * @param endBeat The inclusive end of the range.
     * @return A list of [TimeEvent]s within the range.
     */
    public fun getEventsInRange(startBeat: Double, endBeat: Double): List<TimeEvent> {
        return events.filter { it.beat in startBeat..endBeat }
    }

    /**
     * Calcuates the total duration of the timeline in beats.
     * 
     * This is determined by the end position of the last occurring musical event.
     * 
     * @return The total length of the tune in beats.
     */
    public fun totalBeats(): Double {
        return events.maxByOrNull { it.beat + it.note.semanticDuration.toDouble() }
            ?.let { it.beat + it.note.semanticDuration.toDouble() } ?: 0.0
    }
}
