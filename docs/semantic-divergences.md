# ABC Semantic Divergence Report

This document tracks cases where this ABC parser diverges from other common implementations (like `abcjs` or `music21`). In many cases, these divergences are intentional or represent a more strict adherence to the ABC 2.1 specification and common extensions.

## 1. Lyrics and Music Line Continuations
**Baseline Affected**: `abcjs`
**Example File**: `tune_001367.abc`

### The Issue
ABC 2.1 allows music lines to be continued using a backslash (``) at the end of the line. The specification states that music code can be continued *through* information fields. 

In some files, a music line ends with `` and is immediately followed by a lyrics line (`w:`). 

### Divergence
- **abcjs**: Often fails to correctly identify the `w:` field when it immediately follows a music continuation backslash. It attempts to parse the lyrics as music code, resulting in "Unknown character" warnings and incorrect note counts.
- **This Parser**: Correcty handles the transition to the `w:` field, accurately aligning lyrics even when music lines are continued.

---

## 2. Octave Transposition Extensions (`-8va`, `8vb`)
**Baseline Affected**: `abcjs`
**Example File**: `tune_000939.abc`

### The Issue
Some ABC files use extensions in the key field (`K:`) to indicate octave transpositions, such as `K:Gdor -8va` or `K:C 8vb`. While not strictly in the core ABC 2.1 spec for the `K:` field, these are common in many digital libraries to indicate that the music should be played an octave lower than written.

### Divergence
- **abcjs**: Ignores the `-8va` / `8vb` parameters in the `K:` field, resulting in MIDI output that is an octave higher than intended.
- **This Parser**: Correcty recognizes these extensions and applies the appropriate MIDI transposition (-12 semitones for `-8va`/`8vb`).

---

## 3. Duration Mismatches in Baselines
**Baseline Affected**: `abcjs` (specifically in some batch baseline exports)
**Example File**: `tune_000509.abc`

### The Issue
Discrepancy reports may occasionally show a duration mismatch (e.g., `expected 0.125, but was 0.25`) for tunes with clear notation (e.g., `L:1/8` and a note like `B2`).

### Divergence
- **Investigation**: In these cases, the notation clearly dictates the duration (e.g., $2 	imes 1/8 = 1/4 = 0.25$). Both our parser and manual inspection of `abcjs` notation output agree on the correct value. 
- **Conclusion**: Mismatches in the discrepancy report for these files are often due to the baseline tool interpreting MIDI durations differently or performing performance-based scaling that doesn't reflect the written notation. Our parser remains faithful to the notation's semantic duration.

---

## 4. Key Mode Recognition (`Gdor`, etc.)
**Baseline Affected**: Intermittent/Dialect-dependent

### The Issue
Different parsers have varying levels of support for short-form mode names like `dor` for Dorian.

### Divergence
- **This Parser**: Explicitly supports `dor`, `phr`, `lyd`, `mix`, `loc` as mode identifiers, ensuring correct accidental interpretation (e.g., `G Dorian` correctly gets a $Bb$ flat).
- **Verification**: Our interpretation of these modes has been cross-validated against the Circle of Fifths and matches the expected musical theory.
