# ABC v2.1 ANTLR Parser: Implementation Notes & Gotchas

This document summarizes the key technical hurdles and "gotchas" encountered while achieving 100% round-trip fidelity for the ABC v2.1 ANTLR parser. These notes serve as a guide for future maintainers and anyone implementing a similar state-sensitive musical parser.

## 1. Stateful Duration Logic (`M:` and `L:`)
**The Challenge**: In ABC 2.1, the meter (`M:`) field implicitly sets the default note length (`L:`) if no `L:` is provided.
- **Gotcha**: If you reset the meter mid-tune, you **must** reset the unit note length according to the formula: `ratio < 0.75 ? 1/16 : 1/8`.
- **Fidelity Impact**: Failure to synchronize this between the **Parser** and the **Serializer** leads to factor-of-2 or factor-of-4 duration mismatches during re-parsing.

## 2. Greedy Rule Prevention
**The Challenge**: ABC notation is a mix of structured fields (e.g., `K:D`) and free-form text/music.
- **Gotcha**: Using `.*` or `.+` for catch-all rules like `HEADER_TEXT` or `MUSIC_TEXT` is extremely dangerous. A greedy `HEADER_TEXT` rule can "swallow" the following `K:` field, causing the parser to fail the tune transition.
- **Solution**: 
  - Use single-character catch-alls (`HEADER_TEXT : . ;`) and prioritize specific tokens (like `KEY_FIELD`) in the lexer.
  - Never allow a text rule to consume `\r` or `\n` if those characters drive structural transitions (like `X:` at the start of a line).

## 3. Lexer Mode Management
**The Challenge**: Decorations (`!...!`), Chords (`"..."`), and Inline Fields (`[...]`) share similar delimiters.
- **Gotcha**: The "Bang Decoration" (`!`) is particularly problematic because it often goes unclosed in legacy files.
- **Persistence Error**: If a lexer mode does not pop on `NEWLINE` for line-based elements, one missing `!` will cause the entire rest of the tune (or file) to be consumed as a "decoration," leading to silent failure or massive element loss.
- **Solution**: Always add a fallback pop for `NEWLINE` in modes like `BANG_DECO_MODE` and `CHORD_MODE`.

## 4. Greedy Loop Consumption (Chord Merging)
**The Challenge**: A chord is defined as `BRACKET_START element* BRACKET_END`.
- **Gotcha**: If `BRACKET_END` (`]`) is itself a valid `element` (e.g., as a Spacer or unknown text), the `*` loop will greedily consume the terminating bracket of the *first* chord and continue into the *next* chord.
- **Fidelity Impact**: `[chord1][chord2]` becomes a single malformed `[chord1 chord2]`.
- **Solution**: Create a restricted `chord_element` rule that explicitly excludes `BRACKET_END`.

## 5. Blank Line Sensitivity
**The Challenge**: ABC 2.1 uses a blank line to terminate a tune body.
- **Gotcha**: The re-parser is hyper-sensitive to "aggregate" whitespace. If the `AbcSerializer` emits a newline for a `SpacerElement` and then adds its own structural newline (e.g., for `V:1`), a blank line is created.
- **Side Effect**: The re-parser sees this blank line as the end of the tune, truncating the element count and causing 0.1 fidelity failures on heavy datasets.

## 6. Microtonal Accidental Ordering
**The Challenge**: Supporting `^^/`, `^/`, `_/`, and `__`.
- **Lexer Trap**: Standard accidental tokens like `^^` and `^` are prefixes of microtonal variants.
- **Ordering**: In the lexer, the longest matches (e.g., `^^/`) must appear **above** shorter matches (`^^`) to avoid premature tokenization.

## 7. Inline Field Context (`[P:A]`)
**The Challenge**: Inline fields share the `[` delimiter with chords.
- **Gotcha**: The lexer often consumes the field ID and colon (e.g., `P:`) as part of the `INLINE_FIELD_START` token.
- **Visitor Trap**: `visitInline_field` cannot simply look for `INLINE_FIELD_CONTENT`. It must use `ctx.text` and manually strip the brackets/colons to distinguish between `[P:A]` and other bracketed content.
