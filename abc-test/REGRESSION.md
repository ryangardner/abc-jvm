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

Baselines provide a ground truth for semantic parity. We generate both `abcjs` and `music21` baselines.

### abcjs Baselines
Uses `abcjs` to capture the MIDI sequence and notation structure.
```bash
# In the project root
export PATH=$PATH:/opt/homebrew/bin
node tools/abcjs-exporter/export-batch.js target/abc-dataset/abc_notation_batch_001
```

### music21 Baselines
Uses the `music21` Python library for a "second opinion" on pitch and duration expansion.
```bash
# In the project root
mkdir -p target/abc-dataset/abc_notation_batch_001/music21_json
python3 tools/music21-exporter/m21_validator.py \
  target/abc-dataset/abc_notation_batch_001/abc_files \
  target/abc-dataset/abc_notation_batch_001/music21_json
```

## 4. Running Regression Tests

### Semantic Parity with abcjs
Run the `AbcjsSemanticParityTest` to compare the JVM parser output against the baselines.
```bash
# Run a specific batch
mvn test -pl abc-test -Dtest=AbcjsSemanticParityTest \
  -Dabc.test.batchDir="target/abc-dataset/abc_notation_batch_001"
```

### Filtering Tests
You can filter to a specific tune for debugging:
```bash
mvn test -pl abc-test -Dtest=AbcjsSemanticParityTest \
  -Dabc.test.batchDir="target/abc-dataset/abc_notation_batch_001" \
  -Dabc.test.filter=tune_000004
```

## 5. Cross-Validation

The test suite automatically uses `music21_json` baselines as a fallback when `abcjs` mismatches.
To manually inspect a single file:
```bash
python3 tools/music21-exporter/m21_validator.py \
  target/abc-dataset/abc_notation_batch_001/abc_files/tune_000004.abc
```

## 6. Interpreting Results

### Success-Failures (Expansion)
Due to structural expansion (Repeats, Variants, Parts), the note count in our parser's output may be significantly higher than the `abcjs` notation ground truth. These are often **successes**.
- **Bit-perfect parity**: Usually achieved for tunes without repeats.
- **Structural agreement**: Verified when our count matches `music21`'s expanded count, even if it differs from `abcjs`.

### Known Divergences
Refer to `semantic_divergence_report.md` in the artifacts directory for documented edge cases where we intentionally diverge from `abcjs` or where further work is needed.
