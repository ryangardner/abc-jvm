# LibABC-Kotlin (abc-jvm)

LibABC-Kotlin is a JVM-native library for parsing, manipulating, and generating [ABC Music Notation](https://abcnotation.com/wiki/abc:standard:v2.1). It features a novel, highly performant **ANTLR4-based parser**—believed to be the first of its kind for the ABC 2.1 standard—designed to provide strict type safety and high fidelity for JVM applications.

## Project Motivation

The primary goal of this project is to provide a robust, maintainable, and highly performant ABC parser for the JVM ecosystem. While other tools exist (often based on C or ad-hoc regex parsing), this library leverages **ANTLR4** to define a formal grammar for ABC notation. This approach allows for:

*   **Formal Specification:** A clear, machine-readable definition of the ABC language structure.
*   **Error Recovery:** Robust handling of malformed input, common in user-generated files.
*   **Type Safety:** A strictly typed Abstract Syntax Tree (AST) in Kotlin, ensuring invalid states are unrepresentable.
*   **Interoperability:** Seamless integration with MusicXML and TuxGuitar (via `abc-interop`).

## Architecture

The project is organized as a multi-module Maven project:

*   **`abc-core`**: Defines the immutable AST data models (`AbcTune`, `NoteElement`, `Measure`, etc.). This is the only module required for consumers who just need to work with the data structure.
*   **`abc-antlr-grammar`**: Contains the ANTLR4 `.g4` grammar files (`ABCLexer.g4`, `ABCParser.g4`) that define the language.
*   **`abc-parser`**: The core parsing engine. It wraps the ANTLR-generated parser and transforms the parse tree into the `abc-core` AST.
*   **`abc-theory`**: Provides music theory algorithms, such as key signature analysis, transposition, and duration calculations.
*   **`abc-interop`**: Handles conversion to and from other formats like MusicXML and TuxGuitar.
*   **`abc-test`**: Contains the regression test suite and tools for validating semantic fidelity against large datasets.

## Getting Started

### Prerequisites
*   JDK 8 or higher (Kotlin 1.9.22 is used internally)
*   Maven 3.8+

### Installation

Add the `abc-parser` dependency to your project:

**Maven:**
```xml
<dependency>
    <groupId>io.github.ryangardner</groupId>
    <artifactId>abc-parser</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

**Gradle (Kotlin DSL):**
```kotlin
implementation("io.github.ryangardner:abc-parser:0.1.0-SNAPSHOT")
```

### Basic Usage

```kotlin
import io.github.ryangardner.abc.parser.AbcParser
import io.github.ryangardner.abc.core.model.AbcTune

val abcString = """
X:1
T:Scale
K:C
CDEF GABc|
"""

val parser = AbcParser()
val tune: AbcTune = parser.parse(abcString)

println("Title: ${tune.header.title.first()}")
println("Key: ${tune.header.key}")
```

## Contributing

Contributions are welcome! However, please note that parsing ABC notation is deceptively complex. The standard has evolved over decades, and many "valid" files rely on loose interpretations or legacy syntax.

### Evaluation Criteria
We evaluate Pull Requests based on the following strict criteria:
1.  **Spec Compliance:** Changes must align with the [Official ABC 2.1 Standard](https://abcnotation.com/wiki/abc:standard:v2.1).
2.  **Regression Safety:** Fixing a bug in one area must not break parsing for other valid files. The "loose" nature of ABC means that ambiguity is common; we prioritize stability.
3.  **Utility:** New features should have a clear use case for JVM applications.

### Contributor Guidelines
Before submitting a PR, you **must** run the full regression suite. This includes "high-fidelity" tests that verify the parser can round-trip thousands of real-world tunes without data loss.

To run the full regression suite (including the "heavy" tests against the Zenodo dataset):

```bash
# Run the high-fidelity round-trip tests
mvn test -pl abc-test -Dtest=RoundTripAntlrTest -Dtest.profile=heavy

# Run semantic fidelity validation
mvn test -pl abc-test -Dtest=SemanticFidelityTest -Dtest.profile=heavy
```

*Note: The first run will download a large dataset of ABC tunes.*

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## Acknowledgments & Links

*   [ABC Notation Standard v2.1](https://abcnotation.com/wiki/abc:standard:v2.1)
*   [abcjs](https://paulrosen.github.io/abcjs/) - The reference implementation for web-based rendering.
*   [The Session](https://thesession.org/) - A vast repository of traditional music in ABC format.
