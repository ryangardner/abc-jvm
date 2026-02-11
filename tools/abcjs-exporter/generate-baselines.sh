#!/bin/bash
export PATH=$PATH:/opt/homebrew/bin
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
EXPORTER="$SCRIPT_DIR/export-abc.js"

DIRS=(
    "../../../../src/test/resources/sanity-samples"
    "../../../../src/test/resources/regression-samples"
)

for target_dir in "${DIRS[@]}"; do
    ABS_DIR="$(cd "$SCRIPT_DIR" && cd "$target_dir" && pwd)"
    echo "Processing $ABS_DIR..."
    for f in "$ABS_DIR"/*.abc; do
        if [ -f "$f" ]; then
            echo "  Exporting $(basename "$f")..."
            node "$EXPORTER" "$f" > "${f%.abc}.json"
        fi
    done
done
