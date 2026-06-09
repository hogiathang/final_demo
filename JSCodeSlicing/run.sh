#!/bin/bash

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
export ASTGEN_BIN="$SCRIPT_DIR/astgen/astgen-linux"
chmod +x "$ASTGEN_BIN"

if [ ! -f "$ASTGEN_BIN" ]; then
    echo "Error: astgen-linux not found at $ASTGEN_BIN"
    exit 1
fi

echo "ASTGEN_BIN: $ASTGEN_BIN"
echo ""

echo "========================================"
echo "Processing MALICIOUS packages"
echo "========================================"

INPUT_FOLDER="${1:-../real_malware/malicious-software-packages-dataset/unzip}"
OUTPUT_FOLDER="${2:-../real_malware/malicious-software-packages-dataset/sliced}"
PARALLELISM="${3:-1}"
CHECKPOINT_FILE="${4:-./src/main/resources/checkpoint.txt}"
IS_PROCESSING_FILE="${5:-./src/main/resources/is_processing.txt}"
ERROR_LOG_FILE="${6:-./src/main/resources/error_log.txt}"
TIMEOUT_LOG_FILE="${7:-./src/main/resources/timeout_log.txt}"

# INPUT_FOLDER="../real_malware/malicious-software-packages-dataset/unzip"
# OUTPUT_FOLDER="../real_malware/malicious-software-packages-dataset/sliced"

if [ ! -d "$INPUT_FOLDER" ]; then
    echo "Warning: Malicious input folder does not exist: $INPUT_FOLDER"
else
    mkdir -p "$OUTPUT_FOLDER"
    echo "Input folder: $INPUT_FOLDER"
    echo "Output folder: $OUTPUT_FOLDER"
    echo ""

    sbt \
    -J-Xms8G \
    -J-Xmx32G \
    -J-XX:+UseG1GC \
    -J-XX:MaxGCPauseMillis=200 \
    -Dastgen.bin="$ASTGEN_BIN" \
    "run $INPUT_FOLDER $OUTPUT_FOLDER $PARALLELISM $CHECKPOINT_FILE $IS_PROCESSING_FILE $ERROR_LOG_FILE $TIMEOUT_LOG_FILE"

    echo ""
    echo "Malicious packages processing completed!"
    echo ""
fi

echo "========================================"
echo "ALL PROCESSING COMPLETED!"
echo "========================================"

echo "========================================"
echo "REMOVE CPG.BIN FILES"
echo "========================================"