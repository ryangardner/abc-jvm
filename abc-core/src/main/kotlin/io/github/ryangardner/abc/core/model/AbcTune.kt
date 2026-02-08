package io.github.ryangardner.abc.core.model

/**
 * Represents a fully parsed ABC tune.
 * Immutable and thread-safe.
 */
data class AbcTune(
    val header: TuneHeader,
    val body: TuneBody,
    val metadata: TuneMetadata
)
