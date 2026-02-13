# **Formal Specification and Implementation Strategy for an ANTLR4 Grammar Targeting the ABC Music Notation 2.1 Standard**

## **1\. Introduction**

The domain of computational musicology and digital score engraving has long grappled with the tension between human readability and machine parsability. While complex XML-based formats like MusicXML provide exhaustive descriptive capabilities, they often lack the conciseness required for rapid transcription or folk tradition preservation. Conversely, the **ABC Music Notation** system has established itself as the de facto standard for traditional music, owing to its ASCII-based compactness and intuitive syntax. Since its inception by Chris Walshaw and its subsequent evolution through versions 1.6, 2.0, and finally the **2.1 Standard (December 2011\)**, ABC has matured from a simple shorthand into a robust language capable of handling polyphony, complex lyrics alignment, and macro-based metaprogramming.1

However, the organic evolution of the ABC standard has resulted in a formal grammar that is notoriously difficult to specify for modern parser generators. Unlike languages designed with Look-Ahead Left-to-Right (LALR) or LL(\*) parsers in mind, ABC 2.1 relies heavily on context-sensitive interpretation, mode-switching syntax, and significant whitespace.3 A definitive, open-source **ANTLR4 (Another Tool for Language Recognition, Version 4\)** grammar for ABC 2.1 remains absent from major repositories.4 Existing solutions often rely on ad-hoc regex engines or legacy BNF definitions that fail to capture the "Strict" vs. "Loose" interpretation modes introduced in the latest standard.6

This report presents a comprehensive research analysis and architectural blueprint for constructing a fully compliant ANTLR4 specification for ABC 2.1. It dissects the standard's syntactical ambiguities, proposes a modal lexing strategy to handle the bipartite nature of tune headers and music bodies, and details the semantic analysis required to resolve context-dependent constructs like inline fields and transposing macros.

### **1.1 Historical Context and Standardization**

The trajectory of ABC notation parallels the rise of the internet as a medium for sharing folk music. Initially designed to encode single-voice Western European melodies, early versions were permissible and loosely defined. The release of ABC 2.1 marked a pivotal shift toward formalization, introducing strict rules for multi-voice synchronization and metadata handling to better compete with or complement formats like LilyPond and MusicXML.3

Understanding this history is crucial for the parser architect because ABC 2.1 requires backward compatibility. The standard explicitly defines two interpretation modes:

1. **Strict Interpretation:** Triggered by the presence of a version field %abc-2.1. In this mode, the parser must enforce rigour, rejecting malformed chords or undefined macros.  
2. **Loose Interpretation:** The default for files lacking a version identifier or labeled 2.0 and below. Here, the parser must be permissive, accepting legacy syntax such as the deprecated \+ decoration delimiters.1

This duality implies that an ANTLR4 grammar cannot be a rigid, static structure; it must be capable of runtime reconfiguration or possess a permissive superstructure that allows a post-parse semantic analyzer to enforce version-specific constraints.

### **1.2 The Parser Gap**

Current tooling for ABC notation is fragmented. Many popular tools, such as abcm2ps or abc2midi, utilize custom-written C parsers that have grown organically over decades.2 While performant, these are difficult to maintain or port to other environments (e.g., Java, Python, JavaScript). A formal ANTLR4 specification offers significant advantages:

* **Target Neutrality:** ANTLR4 can generate parsers for Java, C\#, Python, JavaScript, Go, and C++ from a single grammar file.  
* **Visual Debugging:** The ability to visualize the Parse Tree aids in resolving ambiguities in complex polyphonic scores.  
* **Error Recovery:** ANTLR’s sophisticated error recovery mechanisms allow for "best-effort" parsing of malformed files, a common occurrence in user-generated tunebooks.

The objective of this report is to bridge the gap between the informal text of the ABC 2.1 standard and the formal requirements of an ANTLR4 grammar, providing a complete roadmap for implementation.

## **2\. Architectural Analysis of the ABC 2.1 Stream**

Before defining individual tokens, one must understand the high-level architecture of an ABC stream. An ABC file is not a singular entity but a container—a "tunebook"—comprising a sequence of independent musical works, potentially sharing a common global context.

### **2.1 File Structure and Scope Hierarchy**

The ABC 2.1 standard defines a hierarchical scope that the grammar must reflect. The parsing logic must strictly observe the boundaries defined by specific delimiters, creating a scope stack that influences how tokens are interpreted.

| Scope Level | Delimiter / Initiator | Termination Condition | Content |
| :---- | :---- | :---- | :---- |
| **File Scope** | Start of File / %abc-2.1 | End of File (EOF) | Global Header (Optional), Tune Blocks, Free Text |
| **Tune Scope** | X: (Reference Number) | Empty Line or Next X: | Tune Header, Tune Body |
| **Body Scope** | K: (Key) field | Empty Line or Next X: | Music Code, Inline Fields |
| **Overlay Scope** | & (Voice Overlay) | & or Barline | Parallel Music Code |

**Insight:** The standard notes that a file header (a block of information fields before the first X: field) sets default values for all subsequent tunes.1 However, extracting a tune from a file strips it of this context. Therefore, a robust parser must produce an Abstract Syntax Tree (AST) that "denormalizes" these defaults, injecting the file-level properties into each tune object during the semantic analysis phase.

### **2.2 Character Sets and Encoding**

Early ABC was strictly ASCII. ABC 2.1 formalizes support for strictly defined character sets, primarily utf-8 and the ISO-8859 family.8 This has profound implications for the Lexer.

*
### 7. User Defined and Reserved Symbols (Section 4.16)
*   **Current State**:
    *   Tokens like `~`, `u`, `v`, `H`, `L`, `M`, `O`, `P`, `S`, `T` are currently matched by the catch-all `MUSIC_TEXT` rule in `MUSIC_MODE`.
    *   `NOTE_PITCH` only covers `[A-Ga-g]`.
*   **Gap**:
    *   These characters have semantic meaning (e.g., `~` for roll, `u`/`v` for bows, capitals for user-defined decorations).
    *   Parsing them as generic text loses their identity as potential decorations or symbols.
    *   **Proposed**: Add specific tokens for `~`, `u`, `v`, and a `USER_DEFINED_SYMBOL` range (`[H-W]`, `[h-w]`).

### 8. Symbol Lines (`s:`) (Section 4.15)
*   **Current State**:
    *   Parsed as a standard field (`FIELD_ID`). Usage in the body is `field_in_body`.
    *   Content is consumed as a monolithic `FIELD_CONTENT` string.
*   **Gap**:
    *   `s:` lines function like lyrics (`w:`), requiring alignment with notes.
    *   They consist of distinct items (`"..."` chords, `!...!` decorations, `*` skips).
    *   **Proposed**: `s:` should trigger a mode similar to `LYRICS_MODE` (e.g., `SYMBOL_LINE_MODE`) to tokenize its content structure.

### 9. Voice Field Structure (`V:`) (Section 7.1)
*   **Current State**:
    *   Parsed as `FIELD_ID` + `FIELD_CONTENT`.
*   **Gap**:
    *   `V:` fields contain structured key-value pairs (e.g., `V:1 name="Violin" clef=treble`).
    *   Current grammar leaves parsing of these properties to post-processing.
    *   **Proposed**: While acceptable for a broad parser, a finer-grained grammar could parse `KEY=VALUE` pairs within voice definitions.

### 10. Macros (`m:`) (Section 9)
*   **Current State**: Parsed as generic field.
*   **Note**: The spec implies macros are expanded *before* parsing music. However, the presence of macro definitions (`m:`) needs to be captured. Currently handled by generic field parsing, which is likely sufficient, but the parser must be robust enough to handle unexpanded macro calls (e.g., `~G3`) if pre-processing is skipped. This reinforces needs in #7 (handling `~` and `[H-W]` tokens).
This instruction tells the parser how to interpret bytes. While ANTLR4 operates on Unicode streams, the grammar must define TEXT tokens broadly enough to accept accented characters in titles (T:Frère Jacques) and lyrics, while restricting musical symbols to the standard ASCII set (A-G, a-g, z, x).  
* **MIME Types:** The standard defines the MIME type as text/vnd.abc. While irrelevant to the grammar itself, this confirms the text-based nature of the input, validating the use of a character-stream based lexer.9

### **2.3 The Bipartite Syntax Problem**

The central challenge in parsing ABC is that it is essentially two languages in one container:

1. **Metadata Language:** Line-oriented, key-value pairs (Field: Value).  
2. **Music Notation Language:** Character-oriented, sequence of symbols (AB c/d/).

A naive grammar that attempts to define a single set of tokens for the entire file will encounter immediate collisions. For example, the character C is valid in both modes but has entirely different meanings:

* In a **Header**, C: denotes the "Composer" field.  
* In **Music**, C denotes the pitch "Middle C".  
* In a **Meter Field**, C denotes "Common Time" (4/4).

To resolve this, the ANTLR4 architecture must utilize **Lexical Modes**. The grammar will start in a DEFAULT\_MODE (expecting headers) and transition to a MUSIC\_MODE upon encountering the specific trigger field K: (Key), which strictly terminates the header.1

## **3\. Lexical Analysis: The Tokenization Strategy**

The Lexer is the gatekeeper of the parser. For ABC 2.1, it must be context-aware, handling the state transitions between metadata and music, and managing the unique whitespace rules that govern beaming.

### **3.1 Mode Transition Logic**

The transition logic is dictated by the standard's definition of a tune. "The tune header should start with an X: field... and finish with a K: field. The tune body... follows immediately after".1

#### **3.1.1 The Header State (Default Mode)**

The default mode scans for Information Fields. These are defined as a line starting with an uppercase letter, followed optionally by specific modifiers, and then a colon.

* **Strictness:** In strict mode, only valid field identifiers (X, T, C, M, L, etc.) should be recognized as fields. Lines starting with other characters in the file header or between tunes are treated as "Free Text" or "Typeset Text".1  
* **The Trigger:** The recognition of the K: field is the critical event. The Lexer rule for K: must include a command \-\> pushMode(MUSIC\_MODE).

#### **3.1.2 The Music State (Music Mode)**

Once in MUSIC\_MODE, the Lexer interprets characters as musical primitives (notes, bars, decorations).

* **Exit Strategy:** The music body is terminated by an empty line. The Lexer must define a rule for DOUBLE\_NEWLINE or EMPTY\_LINE that executes \-\> popMode.  
* **Inline Fields:** ABC 2.1 allows information fields to appear *inside* the music body if enclosed in brackets, e.g., \[M:6/8\]. This introduces a recursive need: the Lexer must recognize \[ followed by Letter \+ : as a temporary shift back to a header-like parsing logic, or define specific tokens for inline fields within the music mode.10

### **3.2 Whitespace and Beaming**

One of the most subtle aspects of ABC is the semantic significance of whitespace in the music body.

* **Beaming Rule:** A B C represents three distinct quarter notes (assuming L:1/4). ABC represents three notes beamed together.  
* **Implication:** Most ANTLR grammars skip whitespace. For ABC, this is incorrect in MUSIC\_MODE. The Lexer must emit WS (whitespace) tokens. The Parser then uses these tokens to distinguish between a beam\_group (notes with no intervening WS) and a phrase (groups separated by WS).8

### **3.3 Lexical Ambiguities and Resolution**

#### **3.3.1 The "Symbol" vs. "Field" Ambiguity**

Consider the input A:.

* In the Header, this is the (deprecated) Area field.  
* In the Body, A is a note, and : denotes a repeat bar line.  
* **Resolution:** Mode segregation handles this. In DEFAULT\_MODE, A: is tokenized as FIELD\_ID. In MUSIC\_MODE, A is NOTE and : is BAR\_COLON.

#### **3.3.2 The Quote Ambiguity**

* "Am" represents a Chord Symbol.  
* "Guitar" represents an Annotation (placed above the staff).  
* "Unknown" might be a text string in a header T:Unknown.  
* **Resolution:** In MUSIC\_MODE, quoted strings are generic STRING tokens. The Parser discriminates their function based on position (e.g., preceding a note vs. standalone) or content, although the standard defines specific placement characters (^, \_, \<) for annotations to distinguish them from chords.12

## **4\. Header Syntax: Metadata and Directives**

The Tune Header is an unordered collection of fields, bounded by mandatory start and end fields. While simple in appearance, the internal syntax of specific fields like V: (Voice) and Q: (Tempo) is highly structured.

### **4.1 Field Categorization**

The grammar should categorize fields to facilitate semantic validation.

| Field Type | Identifiers | Grammar Rule Characteristics |
| :---- | :---- | :---- |
| **Reference** | X: | Must be the first field. Value is Integer. |
| **Title** | T: | At least one required. String value. |
| **Musical** | M:, L:, K:, Q: | Structured syntax (fractions, keys, definitions). |
| **Metadata** | C:, R:, Z:, O: | Unstructured String values. |
| **Instruction** | I:, %% | Directives affecting playback/display. |
| **Macro** | m:, U: | definitions for substitution. |
| **Voice** | V: | Complex parameter list. |

### **4.2 The Key (K:) Field and Clefs**

The K: field is deceptive. While it primarily sets the key (e.g., K:G), in ABC 2.1 it serves as a container for global musical state, accepting clef and transposition modifiers.

* **Syntax:** K: \<tonic\> \<mode\> \[modifications\].  
* **Modifications:** The grammar must parse a sequence of unordered parameters: clef=..., octave=..., transpose=....  
* **Explicit Accidentals:** The exp keyword allows explicitly defining the key signature (e.g., K:D exp ^f \_b), requiring a specific sub-rule in the grammar to parse accidentals independent of a standard mode.13

### **4.3 The Voice (V:) Field Structure**

The V: field is the linchpin of polyphonic ABC. It defines properties for a specific voice and can appear in the header or inline.

* **Parameters:** The standard lists extensive parameters including clef, name, subname, stem, gstem, middle, scale, stafflines.12  
* **Grammar Logic:**  
  Code snippet  
  voice\_field : 'V:' voice\_identifier voice\_parameter\* NEWLINE ;  
  voice\_parameter  
      : 'clef=' clef\_def

| 'name=' string

| 'subname=' string

| 'stem=' ('up'|'down'|'auto')

| 'middle=' note\_pitch

//... additional parameters

;

\`\`\`

This granular parsing is superior to capturing the whole line as a string, as it allows the AST to validate values immediately (e.g., ensuring stem is only up/down/auto).

### **4.4 Macros and User-Defined Symbols**

ABC 2.1 supports two types of macros:

1. **Symbol Substitution (U:):** U: T \=\!trill\!. Maps a single character (H-W, h-w, \~) to a decoration.  
2. **Melodic Macros (m:):** m: \~G3 \= G{A}G{F}G. Defines complex expansion rules.

**Architectural Decision:** The ANTLR grammar should parse the *definition* of these macros but should **not** attempt to expand them. Macro expansion is a pre-lexical or stream-rewriting operation. If the parser attempts to expand \~G3 into notes during the parse, it violates the separation of concerns and complicates the grammar infinitely. The parser's job is to recognize m: fields as definitions and store them in the symbol table.15

## **5\. Music Body Syntax: The Core Grammar**

The tune\_body is a sequence of music lines. A music line is a sequence of measures, and a measure is a sequence of "elements" (notes, chords, rests, bars).

### **5.1 The Note Construct**

The representation of a note is the most heavily used rule in the grammar. The standard enforces a strict ordering of elements attached to a note.13

**Strict Ordering Rule:**

1. **Grace Notes:** {...} (enclosed in curly braces).  
2. **Chord Symbols:** "..." (enclosed in double quotes).  
3. **Decorations:** \!...\! or legacy symbols (., u, v).  
4. **Accidentals:** ^, \_, \=, etc.  
5. **Pitch:** The base letter (A-G, a-g).  
6. **Octave:** , or ' modifiers.  
7. **Length:** Number or fraction.

**ANTLR Implementation:**

Code snippet

note\_element  
    : grace\_phrase?         // (1)  
      chord\_symbol\*         // (2)  
      decoration\*           // (3)  
      accidental?           // (4)  
      NOTE\_LETTER           // (5)  
      octave\_modifier?      // (6)  
      note\_length?          // (7)  
      tie?                  // (Post-note tie)  
    ;

Deviating from this order (e.g., placing an accidental before a decoration) is technically invalid in strict mode, though common in loose interpretation. The grammar can enforce strictness by adhering to this sequence, forcing parsing errors on malformed notes.

### **5.2 Rhythms and Fractions**

Rhythm in ABC is relative. A note C2 means "C with duration 2 \* Unit Note Length".

* **Fractions:** 3/2, /2, /, //.  
  * *Parser Rule:* note\_length : digit+ ('/' digit\*)? | '/' digit\* ;  
  * This covers all permutations: explicit numerator/denominator, default numerator (1), and default denominator (2).16  
* **Broken Rhythm:** The operators \> (dotted) and \< (reverse dotted) interact between two notes.  
  * *Structure:* This is a binary operation. element BROKEN\_RHYTHM element.  
  * *Nesting:* \>\> and \>\>\> are distinct tokens representing greater dotting. The Lexer should tokenize \>\>\> as a single BROKEN\_RHYTHM\_3 token to avoid parser ambiguity.1

### **5.3 Tuplets: The (p:q:r Syntax**

Tuplets are one of the most complex parsing challenges due to the varied syntax (p:q:r.

* **The Problem:** The ( character also starts slurs.  
* **Lookahead Strategy:** The Lexer must distinguish ( followed by a digit (Tuplet) from ( followed by a non-digit (Slur).  
  * TUPLET\_START : '(' \[0-9\] ;  
* **Syntax Variations:**  
  * (3 : Simple triplet (p=3, q=default, r=p).  
  * (3:: : Triplet with defaults explicit.  
  * (3:2:3 : Full specification.  
* **Grammar Rule:**  
  Code snippet  
  tuplet\_element  
      : tuplet\_specifier music\_content  
      ;  
  tuplet\_specifier  
      : TUPLET\_START (':' digit\* (':' digit\*)?)?  
      ;

  *Semantic Note:* The grammar can parse the specifier, but it cannot structurally enforce that exactly r notes follow. The music\_content rule will match a sequence of notes. It is the responsibility of the AST walker to count r notes and group them logically into the tuplet.10

### **5.4 Chords and Clusters**

Chords are note clusters enclosed in \[ and \].

* **Internal Syntax:** Inside \`\`, notes are stacked. They can have individual decorations, accidentals, and lengths.  
* **Chord Length:** The chord itself can have a length: \[CEG\]2.  
* **Ambiguity:** As mentioned, \[M:6/8\] is an inline field.  
  * *Predicate Logic:* The parser can use a semantic predicate or a specific Lexer rule to check the content immediately following \[. If it matches \[A-Z\]:, it routes to inline\_field; otherwise, it routes to chord.  
  * *Alternative:* Define INLINE\_FIELD as a specific high-priority token in the Lexer.

### **5.5 Lyrics Alignment**

Lyrics are introduced by w: lines following a music line.

* **Alignment Logic:** Lyrics parsing is strictly token-based. The parser must isolate tokens separated by whitespace, hyphens, or underscores.  
* **Special Tokens:**  
  * \*: Skips a note.  
  * \-: Separates syllables (hyphenation).  
  * \_: Extends a syllable (melisma).  
  * |: Advances to the next bar.18  
* **Lexing Lyrics:** When the Lexer encounters w:, it should ideally switch to a LYRICS\_MODE where tokens like | and \* have specific lyric meanings, distinct from their musical meanings. This prevents | in lyrics from being misinterpreted as a barline that ends a measure.

## **6\. Advanced Contextual Features**

### **6.1 Inline Fields and Context Switching**

ABC 2.1 allows changing the Key, Meter, or Unit Length mid-stream.

* **Impact:** \[L:1/16\] changes the semantic value of all subsequent note lengths.  
* **Grammar Integration:** Inline fields are treated as "elements" within a measure.  
  element : note | chord | inline\_field |... ;  
  This allows them to appear anywhere a note could appear.

### **6.2 Voice Overlay (&)**

The & operator allows two musical phrases to occupy the same timeline within a measure.

* Example: A B C & c d e (Three notes overlaying three notes).  
* **Implementation:** The & acts as a measure-internal separator.  
  Code snippet  
  measure\_body : voice\_phrase ('&' voice\_phrase)\* ;

  This structure allows the AST to identify parallel tracks within a single measure, which is crucial for correct rendering and playback.19

### **6.3 Stylesheet Directives (%%)**

Stylesheet directives allow fine-grained control over printing (e.g., %%pagewidth). These are treated similarly to Information Fields but are identifiable by the double percent prefix.

* *Parsing:* They are generally line-oriented instructions. The grammar must parse them to preserve them in the AST, even if the parser itself doesn't "execute" the typesetting instruction.20

## **7\. ANTLR4 Implementation Strategy**

The following section translates the architectural and syntactic analysis into a concrete strategy for the .g4 grammar file.

### **7.1 Lexer Grammar Structure**

The Lexer should be split into modes to handle the header/body separation cleanly.

Code snippet

lexer grammar ABCLexer;

// Default Mode (Headers)  
X\_REF : 'X:' \-\> pushMode(HEADER\_MODE);  
PERCENT : '%' \-\> channel(HIDDEN); // Comments

mode HEADER\_MODE;  
  K\_KEY : 'K:' \-\> pushMode(MUSIC\_MODE); // The critical transition  
  FIELD\_ID : \[A-Z\] ':';  
  FIELD\_CONTENT : \~\[\\r\\n\]+ ;  
  NEWLINE : \[\\r\\n\]+ ;

mode MUSIC\_MODE;  
  // Inline Fields: Transient shift or self-contained token  
  INLINE\_FIELD : '\[' \[A-Z\] ':'.\*? '\]' ;  
    
  // Musical Tokens  
  NOTE\_PITCH : \[A-Ga-g\] ;  
  NUMBER : \[0-9\]+ ;  
  SLASH : '/' ;  
    
  // Structure  
  BARLINE : '|' | '||' | '\[|' | '|\]' | ':|' | '|:' | '::' ;  
    
  // Special  
  TUPLET\_START : '(' \[0-9\] ;  
  SLUR\_START : '(' ;  
    
  // Whitespace is significant for beaming  
  WS : \[ \\t\]+ ;  
    
  // Exit Strategy  
  EMPTY\_LINE : \[\\r\\n\]\[\\r\\n\] \-\> popMode ;

### **7.2 Parser Grammar Structure**

The Parser consumes the tokens to build the structural hierarchy.

Code snippet

parser grammar ABCParser;  
options { tokenVocab=ABCLexer; }

tunebook : file\_header? tune+ EOF ;

tune : tune\_header tune\_body ;

tune\_header : x\_ref (field | stylesheet\_directive)\* key\_field ;

tune\_body : music\_line+ ;

music\_line : measure+ EOL ;

measure : element\* barline ;

element   
    : note 

| chord   
| tuplet   
| inline\_field   
| broken\_rhythm   
    ;

### **7.3 Semantic Analysis and Validations**

Since ANTLR provides a structural parse, a subsequent **Visitor** pass is required to validate semantics that the grammar cannot enforce context-free.

1. **Key Signature Propagation:** The visitor must track the last seen K: field to assign precise pitches to notes (e.g., converting an F note to F\# if the key is D).  
2. **Rhythm Calculation:** The visitor must calculate the duration of every note based on the global L: and M: fields.  
3. **Macro Expansion:** Before the main analysis, a pass should collect all m: definitions. The post-parse traversal can then logically replace nodes matching the macro pattern, or the input stream can be pre-processed.  
4. **Tuplet Validation:** The visitor checks that the number of notes following a tuplet specifier matches the r value defined (or implied) by the specifier.

## **8\. Comparison with Other Formats**

To justify the complexity of this implementation, it is useful to compare ABC 2.1 with other standards.

| Feature | ABC 2.1 | MusicXML | LilyPond | Implications for Parsing |
| :---- | :---- | :---- | :---- | :---- |
| **Structure** | Character stream (ASCII) | Hierarchical XML DOM | TeX-like commands | ABC requires specific Lexer modes; XML relies on standard DOM parsers. |
| **Polyphony** | Voice ID (V:) and Overlay (&) | \<part\> and \<voice\> tags | \<\<... \>\> parallel blocks | ABC's voice interleaving requires the AST to de-interleave lines for processing. |
| **Lyrics** | Aligned text lines (w:) | Embedded \<lyric\> tags per note | \\addlyrics blocks | ABC lyrics require synchronization logic post-parse; MusicXML is pre-synchronized. |
| **Macros** | Built-in (m:) | Not supported native | Scheme functions | ABC parser needs macro-expansion logic; others do not. |

## **9\. Conclusion**

The construction of an ANTLR4 grammar for ABC 2.1 is a non-trivial undertaking that demands a deep understanding of the standard's history, its dual interpretation modes, and its context-sensitive syntax. By adopting a **Modal Lexing** strategy, the parser can cleanly separate the rigid structure of metadata headers from the fluid, character-based syntax of the music body. Furthermore, by defining granular rules for complex fields like V: and K:, the parser enables robust validation and semantic analysis.

This research establishes that a purely context-free grammar is insufficient for ABC 2.1. The solution requires a hybrid approach: a rigorous ANTLR4 syntactic layer to build a typed Parse Tree, coupled with a rich Semantic Analysis layer to handle the dynamic state changes (keys, meters, macros) that characterize the format. This architecture ensures strict compliance with the 2.1 standard while maintaining the flexibility to handle the legacy files that permeate the digital folk music landscape.

### **9.1 Recommended Next Steps for Implementation**

1. **Prototype the Lexer:** Focus immediately on the Mode transition logic (K: \-\> MUSIC\_MODE).  
2. **Unit Test with "Strict" Files:** Use the corpus of standard ABC 2.1 examples to validate the grammar's strictness.  
3. **Implement the AST Visitor:** Build the state machine that tracks Key and Meter changes to verify that the parser output is musically valid.

This specification provides the necessary theoretical and structural foundation to build a definitive, open-source parsing engine for the ABC 2.1 ecosystem.

#### **Works cited**

1. abc:standard:v2.1 \[abc wiki\] \- ABC Notation, accessed February 8, 2026, [https://abcnotation.com/wiki/abc:standard:v2.1](https://abcnotation.com/wiki/abc:standard:v2.1)  
2. ABC notation \- Wikipedia, accessed February 8, 2026, [https://en.wikipedia.org/wiki/ABC\_notation](https://en.wikipedia.org/wiki/ABC_notation)  
3. What are the limitations of the ABC notation format? \- Music, accessed February 8, 2026, [https://music.stackexchange.com/questions/23841/what-are-the-limitations-of-the-abc-notation-format](https://music.stackexchange.com/questions/23841/what-are-the-limitations-of-the-abc-notation-format)  
4. CodeLogicIncEngineering/grammars-v4-public-fork \- GitHub, accessed February 8, 2026, [https://github.com/CodeLogicIncEngineering/grammars-v4-public-fork](https://github.com/CodeLogicIncEngineering/grammars-v4-public-fork)  
5. antlr/grammars-v4 \- GitHub, accessed February 8, 2026, [https://github.com/antlr/grammars-v4](https://github.com/antlr/grammars-v4)  
6. ABC notation \- Wikiwand, accessed February 8, 2026, [https://www.wikiwand.com/en/articles/abc\_notation](https://www.wikiwand.com/en/articles/abc_notation)  
7. abc:standard:v2.0 \[abc wiki\] \- ABC Notation, accessed February 8, 2026, [https://abcnotation.com/wiki/abc:standard:v2.0](https://abcnotation.com/wiki/abc:standard:v2.0)  
8. ABC (musical notation) \- Just Solve the File Format Problem, accessed February 8, 2026, [http://justsolve.archiveteam.org/wiki/ABC\_(musical\_notation)](http://justsolve.archiveteam.org/wiki/ABC_\(musical_notation\))  
9. abc:standard:v2.2 \[abc wiki\] \- ABC Notation, accessed February 8, 2026, [https://abcnotation.com/wiki/abc:standard:v2.2](https://abcnotation.com/wiki/abc:standard:v2.2)  
10. ABC Music Notation \- MIT, accessed February 8, 2026, [https://trillian.mit.edu/\~jc/music/abc/doc/ABC.html](https://trillian.mit.edu/~jc/music/abc/doc/ABC.html)  
11. Beaming in Groups \- My Music Theory, accessed February 8, 2026, [https://mymusictheory.com/rhythm/beaming-in-groups/](https://mymusictheory.com/rhythm/beaming-in-groups/)  
12. ABC Quick Reference Card \- Michael Eskin, accessed February 8, 2026, [https://michaeleskin.com/documents/ABCquickRefv0\_6.pdf](https://michaeleskin.com/documents/ABCquickRefv0_6.pdf)  
13. abc:standard:v2.1 \[abc wiki\] \- ABC Notation, accessed February 8, 2026, [https://abcnotation.com/wiki/abc:standard:v2.1\#voices\_and\_staves](https://abcnotation.com/wiki/abc:standard:v2.1#voices_and_staves)  
14. abc:standard:v2.1:proposals:clefs\_voice\_parameters:v8, accessed February 8, 2026, [http://abcnotation.com/wiki/abc:standard:v2.1:proposals:clefs\_voice\_parameters:v8](http://abcnotation.com/wiki/abc:standard:v2.1:proposals:clefs_voice_parameters:v8)  
15. abc:standard:v2.1 \[abc wiki\] \- ABC notation, accessed February 8, 2026, [https://abcnotation.com/wiki/abc:standard:v2.1\#macros](https://abcnotation.com/wiki/abc:standard:v2.1#macros)  
16. ABC notation, the basics, accessed February 8, 2026, [https://notabc.app/abc/basics/](https://notabc.app/abc/basics/)  
17. abc:standard:v2.1 \[abc wiki\] \- ABC Notation, accessed February 8, 2026, [https://abcnotation.com/wiki/abc:standard:v2.1\#tuplets](https://abcnotation.com/wiki/abc:standard:v2.1#tuplets)  
18. abc:standard:v2.1 \[abc wiki\] \- ABC Notation, accessed February 8, 2026, [https://abcnotation.com/wiki/abc:standard:v2.1\#lyrics](https://abcnotation.com/wiki/abc:standard:v2.1#lyrics)  
19. The ABC Music Standard 2.1 (Dec 2011\) \- Michael Eskin, accessed February 8, 2026, [https://michaeleskin.com/abctools/abc\_standard\_v2.1.pdf](https://michaeleskin.com/abctools/abc_standard_v2.1.pdf)  
20. abc:standard:v2.1 \[abc wiki\] \- ABC notation, accessed February 8, 2026, [https://abcnotation.com/wiki/abc:standard:v2.1\#information\_fields](https://abcnotation.com/wiki/abc:standard:v2.1#information_fields)
## **10. Implementation Status (Feb 2026)**

The ANTLR4-based parser specified in this document has been fully implemented and is now the primary parsing engine for the project, residing in the `abc-parser` module and utilizing grammar from `abc-antlr-grammar`.

### **10.1 Components**

1.  **Grammar Files** (in `abc-antlr-grammar`):
    *   `src/main/antlr4/.../ABCLexer.g4`: Implements the Modal Lexing strategy (Header vs Music modes).
    *   `src/main/antlr4/.../ABCParser.g4`: Defines the structural hierarchy (Tunebook -> Tune -> Body).

2.  **Wrapper** (in `abc-parser`):
    *   `AbcParser.kt`: The main entry point that uses the ANTLR generated classes to parse strings into the Core AST.

3.  **Testing**:
    *   `TortureTest.kt`: A comprehensive test that scans the `abc-test` module for all `.abc` files and attempts to parse them, reporting success rates and specific failure modes.

### **10.2 Build Instructions**

To build the new module and generate the ANTLR sources:

```bash
mvn clean install -pl abc-parser-antlr
```

To run the torture test:

```bash
mvn test -pl abc-parser-antlr
```
