#!/bin/bash
echo "========================================"
echo "Running MalLLM on the dataset"
echo "========================================"

SCRIPT_DIR="MalLLM"
if [ ! -d "$SCRIPT_DIR" ]; then
    echo "Error: MalLLM directory not found at $SCRIPT_DIR"
    exit 1
fi

cd "$SCRIPT_DIR" || exit
INPUT_FOLDER=".$1"
OUTPUT_FOLDER=".$2"
PARALLELISM="$3"


## ACTIVATE VIRTUAL ENVIRONMENT
if [ -f "venv/bin/activate" ]; then
    source venv/bin/activate
    echo "Virtual environment activated."
else
    echo "Warning: Virtual environment not found."
    python3 -m venv venv
    source venv/bin/activate
    echo "Virtual environment created and activated."
    pip install -r requirements.txt
fi


if [ ! -d "$INPUT_FOLDER" ]; then
    echo "Warning: Malicious input folder does not exist: $INPUT_FOLDER"
else
    echo "Input folder: $INPUT_FOLDER"
    echo "Output folder: $OUTPUT_FOLDER"
    echo ""

    python3 malllm/main.py --input_dir "$INPUT_FOLDER" --output_dir "$OUTPUT_FOLDER" --num_workers "$PARALLELISM" --config_file  "config/deepseek-coder-6.7b.json"

    echo ""
    echo "MalLLM processing completed!"
    echo ""
fi