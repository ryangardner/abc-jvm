# LibABC-Kotlin (abc-jvm)

A JVM-native library for symbolic music processing, specifically focused on ABC notation. It provides a robust, strictly typed, and immutable model for musical scores, with a focus on interoperability between ABC (including `abcjs` extensions), MusicXML, and TuxGuitar.

## Project Overview

- **Core Purpose:** Parse, manipulate, and generate ABC notation with high fidelity to the `abcjs` dialect.
- **Technologies:** Kotlin 1.9.22, Java 8 compatibility, Maven, ANTLR4.
- **Architecture:** Multi-module Maven project.
    - `abc-core`: Immutable Abstract Syntax Tree (AST) data models.
    - `abc-parser`: Primary parser engine for ABC notation.
    - `abc-theory`: Music theory algorithms (transposition, key analysis, durations).
    - `abc-interop`: Integration layer for MusicXML and TuxGuitar.
    - `abc-parser-antlr`: Experimental ANTLR4-based parser for the ABC 2.1 standard.
    - `abc-test`: Integration tests, regression suites, and ground truth datasets.
    - `abc-parser-v2` & `abc-theory`: Evolving implementations of the parser and theory logic.

## Building and Running

### Prerequisites
- JDK 8 or higher.
- Maven 3.8+.

### Key Commands
- **Build the entire project:**
  ```bash
  mvn clean install
  ```
- **Run all tests:**
  ```bash
  mvn test
  ```
- **Run tests for a specific module:**
  ```bash
  mvn test -pl abc-parser
  ```
- **Run Linting (Detekt/Ktlint):**
  ```bash
  mvn verify
  ```
- **Generate Coverage Report (Kover):**
  ```bash
  mvn kover:report
  ```

## Development Conventions

### Coding Style
- **Language:** Kotlin with strict explicit API mode (`-Xexplicit-api=strict`).
- **Immutability:** All AST nodes in `abc-core` (e.g., `NoteElement`, `AbcTune`) must be immutable `data class`es.
- **Java Interoperability:** Use `@JvmOverloads`, `@JvmStatic`, and `@JvmField` to ensure the library feels idiomatic for Java consumers.
- **Formatting:** Enforced via `ktlint` and `detekt`. See `config/detekt/detekt.yml`.

### Testing Practices
- **Unit Testing:** Standard JUnit 5 tests.
- **Integration Testing:** Located in `abc-test`, using large datasets (e.g., Zenodo 10k samples) for round-trip fidelity checks.
- **Regression Testing:** Large-scale semantic validation is documented in [REGRESSION.md](file:///Users/ryan.gardner/gitother/abc-jvm/abc-test/REGRESSION.md).
- **Regression Log:** `abc-test/REGRESSION_LOG.md` (legacy) tracks known historical issues.

### Architecture Insights
- **Verticalization Engine:** The library handles the transition from linear ABC streams to measure-oriented formats (TuxGuitar/MusicXML) via a "Verticalization Engine" in `abc-interop`.
- **abcjs Support:** Specific directives like `%%visualTranspose` and `%%MIDI` are treated as first-class citizens in the AST.

## Documentation
- `docs/architecture.md`: Detailed architectural vision and module breakdown.
- `docs/parser.md`: Formal specification for the ANTLR4 parser implementation.
- `docs/parser-gotchas.md`: Known edge cases and complexities in parsing ABC notation.
