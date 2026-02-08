# **Architectural Design Specification for LibABC-Kotlin: A JVM-Native Library for Symbolic Music Processing**

## **1\. Executive Summary and Architectural Vision**

The digital representation of music has evolved into a fragmented landscape of competing standards, each optimized for specific use cases—MusicXML for interchange, MIDI for playback, and ABC notation for concise, human-readable transcription. The proposed library, LibABC-Kotlin, aims to unify these disparate domains within the Java Virtual Machine (JVM) ecosystem. This document outlines a rigorous architectural specification for a Kotlin-based library designed to parse, manipulate, and generate ABC notation with full fidelity to the abcjs dialect.

The primary objective is to engineer a high-performance, immutable, and strictly typed library that serves as a bridge between the textual flexibility of ABC notation and the structural rigidity of object-oriented models like TuxGuitar and MusicXML. This library is not merely a parser; it is a semantic engine capable of understanding musical context, performing complex music theory operations such as semantic transposition, and ensuring seamless interoperability with legacy Java codebases through idiomatic API design.

The architectural vision prioritizes "correctness over permissiveness" in its internal model while maintaining "permissiveness over correctness" in its parsing layer. This duality allows the library to ingest the often-messy, "loose" ABC files found in the wild—specifically those tailored for the web-based abcjs renderer—while normalizing them into a coherent internal structure suitable for conversion to strict formats like Guitar Pro (via TuxGuitar) or MusicXML.

### **1.1 The Domain Problem: Symbolic Music Impedance Mismatch**

A core challenge addressed by this design is the "impedance mismatch" between stream-oriented and measure-oriented formats. ABC notation 1 is fundamentally a stream of events: notes, chords, and bar lines appear sequentially, often without strict enforcement of measure duration or voice synchronization. Conversely, libraries like TuxGuitar 3 and formats like MusicXML 5 are hierarchical and measure-centric; they require notes to be strictly contained within measure boundaries, often demanding precise vertical alignment of multiple voices.

LibABC-Kotlin solves this by implementing a "Verticalization Engine" as a core component of its architecture. This engine transforms the linear AST (Abstract Syntax Tree) derived from ABC text into a time-sliced, vertically aligned representation required for export to TuxGuitar and MusicXML. This approach ensures that the library acts not just as a translator, but as a structural validator, capable of detecting and reconciling rhythmic inconsistencies inherent in hand-typed ABC files.

### **1.2 The abcjs Compatibility Mandate**

While standard ABC 2.1 6 forms the baseline, the web ecosystem has coalesced around the abcjs library, which introduces a superset of directives for visual rendering and audio synthesis.8 Standard parsers often discard these directives as comments. To fulfill the requirement of "reading and writing to the abcjs format," LibABC-Kotlin treats abcjs directives—such as %%visualTranspose, %%MIDI program, and formatting parameters like %%staffwidth—as first-class citizens within the AST. This ensures that a file parsed, manipulated, and re-serialized by the library preserves the specific rendering instructions required for web presentation, facilitating a seamless round-trip workflow between the JVM backend and a browser frontend.

## **2\. High-Level System Architecture**

The library is structured as a multi-module Maven project. This modularity ensures separation of concerns, allowing users to import only the core data models without pulling in heavy dependencies related to TuxGuitar or complex analysis algorithms.

### **2.1 Module Decomposition**

| Module Name | Artifact ID | Description | Dependencies |
| :---- | :---- | :---- | :---- |
| **Core Model** | abc-core | Defines the immutable data classes, enums, and interfaces representing the ABC AST. Contains zero logic beyond data holding. | kotlin-stdlib |
| **Parser Engine** | abc-parser | Contains the Lexer, Parser, and Validator. Responsible for converting raw text strings into the Core Model. Handles abcjs directive parsing. | abc-core |
| **Music Theory** | abc-theory | Implements algorithmic manipulations: Transposition (chromatic/diatonic), key analysis, and duration calculations. | abc-core |
| **Integration** | abc-interop | Adapters for converting the Core Model to/from TuxGuitar and MusicXML formats. | abc-core, abc-theory, tuxguitar-lib |
| **Test Suite** | abc-test | Contains the "Ground Truth" datasets and integration tests. | All modules, JUnit 5 |

### **2.2 Technology Stack and Compatibility Profile**

To satisfy the requirement of easy Java integration 10, the library leverages Kotlin's static compilation features while targeting a baseline that ensures broad compatibility.

* **Language:** Kotlin 1.9+ (compiled with strict explicit API mode).  
* **JVM Target:** Java 8 (1.8). This ensures the library can be "dropped in" to legacy enterprise Java applications or older Android environments without version conflicts.11  
* **Build System:** Maven 3.8+. While Gradle is popular in Kotlin, Maven provides a rigid, declarative structure preferred for library publication and legacy integration.  
* **Interop Strategy:** The API surface utilizes @JvmOverloads, @JvmStatic, and @JvmField annotations to mask Kotlin-specific constructs (like companion objects or default arguments) from Java consumers, presenting a natural, idiomatic Java API.13

## **3\. The Abstract Syntax Tree (AST) Data Model**

The AST is the source of truth for the musical content. It is designed to be **immutable**, **type-safe**, and **exhaustive**. Unlike string-based manipulation, utilizing an AST prevents the generation of syntactically invalid ABC code.

### **3.1 The Root Structure: AbcTune**

The root object represents a single tune within an ABC file (or a file containing multiple tunes).

Kotlin

/\*\*  
 \* Represents a fully parsed ABC tune.  
 \* Immutable and thread-safe.  
 \*/  
data class AbcTune(  
    val header: TuneHeader,  
    val body: TuneBody,  
    val metadata: TuneMetadata  
)

### **3.2 The Header Model**

ABC headers define the global state. LibABC-Kotlin parses these into strongly typed objects rather than generic strings to facilitate algorithmic manipulation (e.g., transposition requires understanding KeySignature as an object, not just the string "K:Dm").

| Field | ABC Code | Type | Description |
| :---- | :---- | :---- | :---- |
| Reference | X: | Int | Unique index of the tune. |
| Title | T: | List\<String\> | Supports multiple titles (primary, secondary). |
| Key | K: | KeySignature | Encapsulates Tonic, Mode (Major, Minor, Dorian, etc.), and Accidental list. |
| Meter | M: | TimeSignature | Represents meter as numerator/denominator or symbols (C, \`C |
| Length | L: | NoteDuration | The default note length (e.g., 1/8). |
| Tempo | Q: | Tempo | Beats per minute mapping. |

### **3.3 The Musical Body: A Polymorphic Stream**

The body of an ABC tune is a sequence of musical elements. To represent this in Kotlin, we utilize a sealed class hierarchy. This allows for exhaustive when expressions in the compiler, ensuring that any visitor processing the music handles every possible element type.

Kotlin

sealed class MusicElement {  
    // Represents a playable note or rest  
    abstract val duration: Duration  
}

data class NoteElement(  
    val pitch: Pitch,  
    val length: Duration,  
    val ties: TieType \= TieType.NONE,  
    val decorations: List\<Decoration\> \= emptyList(), // e.g.,\!trill\!  
    val accidental: Accidental? \= null // Explicit accidental handling  
) : MusicElement()

data class ChordElement(  
    val notes: List\<NoteElement\>,  
    val duration: Duration,  
    val annotation: String? \= null // Text annotations like "Am7"  
) : MusicElement()

data class BarLineElement(  
    val type: BarLineType, // |, ||, |\], :|, |:  
    val repeatCount: Int \= 0  
) : MusicElement()

data class InlineFieldElement(  
    val fieldType: HeaderType, // e.g., K, L, M  
    val value: String  
) : MusicElement()

**Insight:** Standard ABC parsers often fail to distinguish between a "decorating chord" (text above staff) and a "sounding chord" (notes played together). LibABC-Kotlin disambiguates these by treating text annotations (e.g., "Am") as properties of the MusicElement, while sounding chords (\[CEG\]) are distinct ChordElement objects.

### **3.4 Handling abcjs Directives**

abcjs directives usually appear as comment-like lines starting with %%. To support reading and writing abcjs format, these are not discarded but parsed into a specialized FormattingModel.

* **Visual Transpose:** %%visualTranspose n shifts the rendering without changing the audio or logic. The library parses this into an integer field in TuneMetadata.8  
* **MIDI Directives:** %%MIDI program controls instrument selection. This is parsed into a MidiConfiguration object, critical for the TuxGuitar export layer to assign correct instrument tracks.

## **4\. Parser Implementation Strategy**

The parsing layer is critical for robustness. It must handle the variability of human-generated ABC files.

### **4.1 Lexical Analysis (Tokenization)**

The lexer transforms the raw string into a stream of AbcTokens.

* **State Machine:** The lexer operates in two primary states: HEADER\_SCAN and BODY\_SCAN.  
  * In HEADER\_SCAN, it looks for lines starting with Letter: and newline terminators.  
  * In BODY\_SCAN, it recognizes notes (C, ^C, C,,), durations (2, /2), and structural tokens (|, \` to determine the target key.  
  * *Example:* Transposing G Major (1 sharp) up 2 semitones \-\> A Major (3 sharps).  
  * *Enharmonic Resolution:* If the target key would have excessive accidentals (e.g., G\# Major with 8 sharps), the algorithm automatically snaps to the enharmonic equivalent (Ab Major with 4 flats).  
2. **Interval Calculation:**  
   * Determine the diatonic interval (number of staff lines to move) and the chromatic interval (semitones).  
   * *Example:* Up 2 semitones (Major 2nd) involves moving the diatonic step by \+1 (e.g., G \-\> A).  
3. **Note-Level Transposition:**  
   * For every NoteElement:  
     * Shift the step by the diatonic interval.  
     * Calculate the expected pitch of the new step in the *target key*.  
     * Adjust the accidental to match the target chromatic pitch.  
   * *Handling abcjs:* If %%visualTranspose is present, it must be cleared or adjusted, as the physical notes are now changed.

### **5.3 Handling abcjs Visual Transposition**

The library distinguishes between **Semantic Transposition** (changing the data) and **Visual Transposition** (changing the view).

* AbcTune.transpose(semitones: Int): Returns a new AbcTune with altered notes and key signature.  
* AbcTune.setVisualTranspose(semitones: Int): Returns a new AbcTune with the original notes but an updated %%visualTranspose directive. This mimics the client-side behavior of abcjs.8

## **6\. Integration: TuxGuitar and MusicXML**

This section addresses the complexity of bridging the linear ABC format with the vertical/hierarchical formats of MusicXML and TuxGuitar.

### **6.1 The Verticalization Problem**

ABC does not strictly enforce measure lengths. A voice can have 5 beats in a 4/4 measure. TuxGuitar's data model (TGMeasure) is rigid.

* **Solution: The Measure Quantizer.**  
  The abc-interop module implements a MeasureQuantizer. It iterates through the linear stream of ABC notes and "bins" them into measures based on the time signature.  
  * *Accumulator Logic:* The quantizer maintains a beat accumulator. When currentBeats \>= timeSignatureBeats, a new TGMeasure is instantiated.  
  * *Tie Handling:* If an ABC note overruns a measure boundary (which is valid in loose ABC), the quantizer splits the note into two TGNote objects tied together across the measure line.

### **6.2 Mapping to TuxGuitar (TG)**

Based on TuxGuitar's internal model 3:

* **AbcVoice \-\> TGTrack:** Each voice in the ABC file becomes a separate track.  
* **%%MIDI program \-\> TGChannel:** The library parses the MIDI directive to assign the correct instrument (e.g., Distortion Guitar vs. Piano) to the TGTrack.  
* **Decorations \-\> TGNoteEffect:** ABC decorations like . (staccato) or \~ (roll) are mapped to TGNoteEffect or TGEffect flags.

### **6.3 Mapping to MusicXML**

Since the user's software reads MusicXML, providing a robust export is crucial.

* **Part-wise Export:** The library exports to \<score-partwise\>.18  
* **Directives:** ABC headers (T:, C:) map to MusicXML metadata (\<work-title\>, \<creator type="composer"\>).  
* **Dynamics:** ABC dynamics (\!ff\!, \!p\!) are mapped to \<direction\>\<dynamics\>\<ff/\>\</dynamics\>\</direction\> elements in MusicXML.19

## **7\. Quality Assurance: Exhaustive Unit Testing**

To ensure the library is production-ready, we employ a "Ground Truth" testing strategy using large-scale datasets.

### **7.1 Ground Truth Datasets**

We utilize the **ABC Notation Dataset (10k samples)** from Zenodo.20 This dataset provides verified ABC files and their rendered images.

* **Test Setup:** The build system will download a subset of this dataset (e.g., the "Nottingham" subset for clean data, and a random subset for "wild" data) into src/test/resources.

### **7.2 Test Strategies**

1. **Round-Trip Fidelity (The Golden Test):**  
   * Parse File A \-\> AST \-\> Serialize to File B.  
   * Assert File A (normalized) \== File B.  
   * *Normalization:* The test ignores whitespace differences but strictly enforces that all headers, notes, and abcjs directives are preserved.  
2. **Transposition Verification:**  
   * Parse a tune in C Major.  
   * Transpose programmatically to D Major (+2 semitones).  
   * Compare the result against a manually verified "Gold Standard" file of the same tune in D Major.  
   * *Check:* Ensure accidentals are correctly spelled (F\# in D Major, not Gb).  
3. **TuxGuitar Integration Test:**  
   * Parse an ABC file.  
   * Convert to TGSong.  
   * Serialize TGSong to MusicXML using the internal converter.  
   * Validate the output XML against the official MusicXML 4.0 XSD schema.5

### **7.3 Code Coverage and Quality**

* **Kover:** Configured to enforce 90% branch coverage on the abc-parser and abc-theory modules.21  
* **Ktlint & Detekt:** Enforce style guides. Detekt is configured to flag complex methods (cyclomatic complexity \> 10), which often indicates fragile parsing logic.23

## **8\. Development Roadmap and Agent Tasks**

This section breaks down the implementation into discrete, verifiable tasks for an autonomous coding agent.

### **Phase 1: Core Foundation**

* **Task 1.1:** Initialize Maven project with modules: abc-core, abc-parser, abc-theory, abc-interop. Configure pom.xml with Kotlin 1.9, Java 1.8 target, and ktlint plugin.  
* **Task 1.2:** Implement AbcTune, TuneHeader, and MusicElement sealed classes in abc-core. Use @JvmOverloads on all constructors.  
* **Task 1.3:** Create Token enum and implement the AbcLexer in abc-parser. Write tests to verify tokenization of headers vs. body content.

### **Phase 2: Parser & abcjs Support**

* **Task 2.1:** Implement HeaderParser. Support all standard headers. Add a Map\<String, String\> for unknown headers.  
* **Task 2.2:** Implement BodyParser. Handle notes, rests, chords, and bar lines.  
* **Task 2.3:** Implement DirectiveParser specifically for abcjs. Parse %%visualTranspose, %%staffwidth, and %%MIDI into strongly typed configuration objects within the AST.

### **Phase 3: Theory & Transposition**

* **Task 3.1:** Implement Pitch class with semitone values. Implement KeySignature with a Circle of Fifths lookup table.  
* **Task 3.2:** Implement Transposer class. Logic: targetKey \= sourceKey \+ interval. targetNote \= sourceNote \+ interval. Ensure enharmonic spelling logic is robust (using key signature context).  
* **Task 3.3:** Add AbcTune.transpose(semitones: Int) method.

### **Phase 4: Integration**

* **Task 4.1:** Implement MeasureQuantizer in abc-interop. Convert linear MusicElement lists into fixed-duration chunks.  
* **Task 4.2:** Implement AbcToTuxGuitarConverter. Map AbcTune to TGSong and AbcVoice to TGTrack.  
* **Task 4.3:** Implement MusicXMLWriter. Serialize AbcTune to compliant MusicXML.

### **Phase 5: Verification**

* **Task 5.1:** Set up abc-test module. Write a script to download the Zenodo dataset.  
* **Task 5.2:** Implement parameterized JUnit 5 tests to run the Round-Trip check on 1,000 files.  
* **Task 5.3:** Run mvn kover:report and refine tests to hit coverage targets.

# ---

**Detailed Implementation Guide**

## **9\. Maven Build Specification**

To ensure compatibility with Java users ("drop it in easily") and modern Kotlin development, the Maven build is configured carefully.

### **9.1 Parent POM (pom.xml)**

XML

\<project xmlns\="http://maven.apache.org/POM/4.0.0"...\>  
    \<modelVersion\>4.0.0\</modelVersion\>  
    \<groupId\>com.example.music\</groupId\>  
    \<artifactId\>libabc-kotlin-parent\</artifactId\>  
    \<version\>1.0.0\</version\>  
    \<packaging\>pom\</packaging\>

    \<modules\>  
        \<module\>abc-core\</module\>  
        \<module\>abc-parser\</module\>  
        \<module\>abc-theory\</module\>  
        \<module\>abc-interop\</module\>  
    \</modules\>

    \<properties\>  
        \<kotlin.version\>1.9.22\</kotlin.version\>  
        \<java.version\>1.8\</java.version\>  
        \<tuxguitar.version\>1.5.4\</tuxguitar.version\>  
    \</properties\>l

    \<dependencyManagement\>  
        \<dependencies\>  
            \<dependency\>  
                \<groupId\>org.jetbrains.kotlin\</groupId\>  
                \<artifactId\>kotlin-stdlib\</artifactId\>
                \<version\>${kotlin.version}\</version\>  
            \</dependency\>  
            \<dependency\>  
                \<groupId\>org.jetbrains.kotlin\</groupId\>  
                \<artifactId\>kotlin-test-junit5\</artifactId\>  
                \<version\>${kotlin.version}\</version\>  
                \<scope\>test\</scope\>  
            \</dependency\>  
        \</dependencies\>  
    \</dependencyManagement\>

    \<build\>  
        \<plugins\>  
            \<plugin\>  
                \<groupId\>org.jetbrains.kotlin\</groupId\>  
                \<artifactId\>kotlin-maven-plugin\</artifactId\>  
                \<version\>${kotlin.version}\</version\>  
                \<configuration\>  
                    \<jvmTarget\>${java.version}\</jvmTarget\>  
                    \<args\>  
                        \<arg\>\-Xjvm-default=all\</arg\> \</args\>  
                \</configuration\>  
                \<executions\>  
                    \<execution\>  
                        \<id\>compile\</id\>  
                        \<goals\>\<goal\>compile\</goal\>\</goals\>  
                    \</execution\>  
                    \<execution\>  
                        \<id\>test-compile\</id\>  
                        \<goals\>\<goal\>test-compile\</goal\>\</goals\>  
                    \</execution\>  
                \</executions\>  
            \</plugin\>  
            \<plugin\>  
                \<groupId\>org.jetbrains.kotlinx\</groupId\>  
                \<artifactId\>kover-maven-plugin\</artifactId\>  
                \<version\>0.7.5\</version\>  
                \<executions\>  
                    \<execution\>  
                        \<goals\>\<goal\>report\</goal\>\</goals\>  
                    \</execution\>  
                \</executions\>  
            \</plugin\>  
        \</plugins\>  
    \</build\>  
\</project\>

### **9.2 Java Interoperability Strategy**

To make the library feel native to Java users:

* **@JvmOverloads:** Applied to the AbcParser.parse() method. This allows Java users to call parse(file) without supplying default configuration objects.  
* **@JvmStatic:** Used on companion object factory methods. Java code calls AbcFactory.create() instead of AbcFactory.Companion.create().  
* **@JvmField:** Used for public constants in AbcConstants to avoid getter overhead.

## **10\. Code Standards and Instructions**

### **10.1 Style Guide**

* **Kotlin:** Follow the official JetBrains style guide. Enforced via ktlint.  
* **Immutability:** All AST nodes (NoteElement, Header) must be data class with val properties. No var in the core model.  
* **Nullability:** Use strict null checks. Java interop boundaries must ensure null is not returned unless explicitly typed as Optional or documented @Nullable.

### **10.2 Documentation**

* KDoc is mandatory for all public members.  
* Must include @sample tags pointing to test cases for usage examples.

## **11\. Conclusion**

LibABC-Kotlin represents a robust, theoretically sound approach to bringing ABC notation into the professional Java/Kotlin ecosystem. By respecting the nuances of abcjs, implementing a rigorous semantic transposition engine, and solving the linear-to-vertical mapping problem for TuxGuitar integration, this library fills a critical gap in music software development tools. The detailed architectural plan and test-driven development roadmap provided herein ensure that an automated agent can execute this vision with high precision and reliability.

---

**Citations:** 1 \- ABC Standard & Notation. 1 \- abcjs directives and features. 3 \- TuxGuitar data models. 5 \- MusicXML structure. 15 \- Music theory and Transposition algorithms. 20 \- Ground Truth Datasets. 10 \- Java/Kotlin Interoperability. 11 \- Build & Quality tools.

#### **Works cited**

1. ABC Quick Reference Card \- Michael Eskin, accessed February 8, 2026, [https://michaeleskin.com/documents/ABCquickRefv0\_6.pdf](https://michaeleskin.com/documents/ABCquickRefv0_6.pdf)  
2. ABC notation \- Grokipedia, accessed February 8, 2026, [https://grokipedia.com/page/ABC\_notation](https://grokipedia.com/page/ABC_notation)  
3. Which file formats does TuxGuitar support?, accessed February 8, 2026, [https://tuxguitar.org/which-file-formats-does-tuxguitar-support/](https://tuxguitar.org/which-file-formats-does-tuxguitar-support/)  
4. Measure and beat \- TuxGuitar Help, accessed February 8, 2026, [https://www.tuxguitar.app/files/devel/desktop/help/detail\_measure\_beat.html](https://www.tuxguitar.app/files/devel/desktop/help/detail_measure_beat.html)  
5. MusicXML 3.0 Tutorial, accessed February 8, 2026, [https://www.musicxml.com/wp-content/uploads/2012/12/musicxml-tutorial.pdf](https://www.musicxml.com/wp-content/uploads/2012/12/musicxml-tutorial.pdf)  
6. ABC (musical notation) \- Just Solve the File Format Problem, accessed February 8, 2026, [http://justsolve.archiveteam.org/wiki/ABC\_(musical\_notation)](http://justsolve.archiveteam.org/wiki/ABC_\(musical_notation\))  
7. abc:standard:v2.1 \[abc wiki\] \- ABC Notation, accessed February 8, 2026, [https://abcnotation.com/wiki/abc:standard:v2.1](https://abcnotation.com/wiki/abc:standard:v2.1)  
8. abcjs/RELEASE.md at main · paulrosen/abcjs \- GitHub, accessed February 8, 2026, [https://github.com/paulrosen/abcjs/blob/main/RELEASE.md](https://github.com/paulrosen/abcjs/blob/main/RELEASE.md)  
9. abcjs \- NPM, accessed February 8, 2026, [https://www.npmjs.com/package/abcjs](https://www.npmjs.com/package/abcjs)  
10. Calling Kotlin from Java, accessed February 8, 2026, [https://kotlinlang.org/docs/java-to-kotlin-interop.html](https://kotlinlang.org/docs/java-to-kotlin-interop.html)  
11. Maven | Kotlin Documentation, accessed February 8, 2026, [https://kotlinlang.org/docs/maven.html](https://kotlinlang.org/docs/maven.html)  
12. Create a Java and Kotlin Project with Maven \- Baeldung, accessed February 8, 2026, [https://www.baeldung.com/kotlin/maven-java-project](https://www.baeldung.com/kotlin/maven-java-project)  
13. Using @JvmOverloads and @JvmStatic for Kotlin interoperability, accessed February 8, 2026, [https://dev.to/ike\_\_jr/using-jvmoverloads-and-jvmstatic-for-kotlin-interoperability-with-java-3ag7](https://dev.to/ike__jr/using-jvmoverloads-and-jvmstatic-for-kotlin-interoperability-with-java-3ag7)  
14. Understanding @JvmStatic, @JvmField, and @JvmOverloads in Kotlin, accessed February 8, 2026, [https://medium.com/@manishkumar\_75473/understanding-jvmstatic-jvmfield-and-jvmoverloads-11dbe4226c5c](https://medium.com/@manishkumar_75473/understanding-jvmstatic-jvmfield-and-jvmoverloads-11dbe4226c5c)  
15. Transpose Music Easily: Use the Circle of Fifths \- CircleOfFifths.io, accessed February 8, 2026, [https://circleoffifths.io/blog/transpose-music-easily-use-the-circle-of-fifths](https://circleoffifths.io/blog/transpose-music-easily-use-the-circle-of-fifths)  
16. Learn to use the circle of fifths, a powerful musical tool, accessed February 8, 2026, [https://musicteachers.co.uk/music/circle-of-fifths](https://musicteachers.co.uk/music/circle-of-fifths)  
17. Transposition | \- abcjs, accessed February 8, 2026, [https://docs.abcjs.net/transposing/transposing](https://docs.abcjs.net/transposing/transposing)  
18. The Structure of MusicXML Files, accessed February 8, 2026, [https://www.w3.org/2021/06/musicxml40/tutorial/structure-of-musicxml-files/](https://www.w3.org/2021/06/musicxml40/tutorial/structure-of-musicxml-files/)  
19. Notation Basics in MusicXML, accessed February 8, 2026, [https://www.w3.org/2021/06/musicxml40/tutorial/notation-basics/](https://www.w3.org/2021/06/musicxml40/tutorial/notation-basics/)  
20. ABC Notation Dataset (10k samples) \- Zenodo, accessed February 8, 2026, [https://zenodo.org/records/17694747](https://zenodo.org/records/17694747)  
21. Kover Maven Plugin | kotlinx-kover \- GitHub Pages, accessed February 8, 2026, [https://kotlin.github.io/kotlinx-kover/maven-plugin/](https://kotlin.github.io/kotlinx-kover/maven-plugin/)  
22. A Guide to Kotlinx-Kover: a Kotlin Code Coverage Toolset \- Baeldung, accessed February 8, 2026, [https://www.baeldung.com/kotlin/kover](https://www.baeldung.com/kotlin/kover)  
23. Enforcing Code Quality in Android with Detekt and Ktlint \- Medium, accessed February 8, 2026, [https://medium.com/@mohamad.alemicode/enforcing-code-quality-in-android-with-detekt-and-ktlint-a-practical-guide-907b57d047ec](https://medium.com/@mohamad.alemicode/enforcing-code-quality-in-android-with-detekt-and-ktlint-a-practical-guide-907b57d047ec)  
24. ABC Music Notation | \- abcjs, accessed February 8, 2026, [https://docs.abcjs.net/overview/abc-notation](https://docs.abcjs.net/overview/abc-notation)  
25. RenderAbc options |, accessed February 8, 2026, [https://docs.abcjs.net/visual/render-abc-options.html](https://docs.abcjs.net/visual/render-abc-options.html)  
26. The quirks of programming music theory | by Nick Rose \- Medium, accessed February 8, 2026, [https://medium.com/@nicholas.rose/the-quirks-of-programming-music-theory-fcffd42d9e50](https://medium.com/@nicholas.rose/the-quirks-of-programming-music-theory-fcffd42d9e50)