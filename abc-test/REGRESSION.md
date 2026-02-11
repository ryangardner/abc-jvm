# ABC Parser Batch Regression Testing

This document describes how to perform large-scale semantic validation of the ABC parser using the ABC notation dataset and baseline tools.

## 1. Prerequisites

- **Node.js** (v18 or higher)
- **Python 3** with `music21` installed: `pip3 install music21`
- **Maven**

## 2. Dataset Setup

The test suite uses a dataset of ~13,000 ABC tunes hosted on Zenodo.

### Download and Extract
Use the `DatasetDownloader` utility to fetch specific batches of 1,000 tunes each.

```bash
# In the abc-test directory
mvn exec:java -Dexec.mainClass="io.github.ryangardner.abc.test.DatasetDownloader" -Dexec.args="1 2 3"
```
This will extract files to `target/abc-dataset/abc_notation_batch_XXX/`.

## 3. Generating Baselines

Baselines are generated using `abcjs` to provide a ground truth for semantic parity.

```bash
cd tools/abcjs-exporter
npm install
node export-batch.js ../../target/abc-dataset/abc_notation_batch_001
```

This produces JSON files in the `midi_json` subdirectory of each batch, containing both the notation structure and the linear MIDI sequence.

## 4. Running Regression Tests

### Semantic Parity with abcjs
Run the `AbcjsSemanticParityTest` to compare the JVM parser output against the `abcjs` MIDI baselines.

```bash
# Run a specific batch
mvn test -Dtest=AbcjsSemanticParityTest \
  -DargLine="-Dtest.batchDir=$(pwd)/target/abc-dataset/abc_notation_batch_001"
```

### Filtering Tests
You can filter to a specific tune for debugging:
```bash
mvn test -Dtest=AbcjsSemanticParityTest \
  -DargLine="-Dtest.batchDir=$(pwd)/target/abc-dataset/abc_notation_batch_001 -Dabc.test.filter=tune_000004"
```

## 5. Cross-Validation

If a tune shows a mismatch between our parser and `abcjs`, use the `m21_validator.py` script to get a "second opinion" from the `music21` library.

```bash
# In the project root
python3 m21_validator.py abc-test/target/abc-dataset/abc_notation_batch_001/abc_files/tune_000004.abc
```

## 6. Interpreting Results

### Success-Failures (Expansion)
Due to structural expansion (Repeats, Variants, Parts), the note count in our parser's output may be significantly higher than the `abcjs` notation ground truth. These are often **successes**.
- **Bit-perfect parity**: Usually achieved for tunes without repeats.
- **Structural agreement**: Verified when our count matches `music21`'s expanded count, even if it differs from `abcjs`.

### Known Divergences
Refer to `semantic_divergence_report.md` in the artifacts directory for documented edge cases where we intentionally diverge from `abcjs` or where further work is needed.
