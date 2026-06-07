#!/bin/bash

SCRIPT_DIR="JSCodeSlicing"
if [ ! -d "$SCRIPT_DIR" ]; then
    echo "Error: JSCodeSlicing directory not found at $SCRIPT_DIR"
    exit 1
fi

cd "$SCRIPT_DIR" || exit

INPUT_FOLDER=".$1"
OUTPUT_FOLDER=".$2"
PARALLELISM="$3"
CHECKPOINT_DIR=".$4"

sh run.sh "$INPUT_FOLDER" "$OUTPUT_FOLDER" "$PARALLELISM" "$CHECKPOINT_DIR"