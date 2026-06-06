# Mặc định khi gõ 'make' sẽ chạy target 'help'
.PHONY: help clean run

help:
	@echo "================================================================="
	@echo "                        MAKEFILE USAGE                           "
	@echo "================================================================="
	@echo " Lệnh khả dụng:"
	@echo "   make run          - Chạy toàn bộ pipeline (Dataset -> JS -> LLM)"
	@echo "   make clean        - Xóa các file cấu hình/log (.log)"
	@echo ""
	@echo " Tùy chỉnh tham số:"
	@echo "   make run DATASET=/path/to/dataset  - Chỉ định thư mục dataset khác"
	@echo "================================================================="


DATASET_DIR = ./dataset
JS_CODE_SLICING_DIR = ./js_code_slicing
LLM_DIR = ./MalLLM
SCRIPT_DIR = ./scripts
OUTPUT_DIR = ./output
CODE_SLICING_OUTPUT_DIR = $(OUTPUT_DIR)/js_code_slicing_output
LLM_OUTPUT_DIR = $(OUTPUT_DIR)/malllm_output

run:
	@echo "Running the full pipeline with dataset: $(DATASET_DIR)"
	@echo "Step 1: Running JS Code Slicing..."
	@bash $(SCRIPT_DIR)/run_js_code_slicing.sh $(DATASET_DIR) $(CODE_SLICING_OUTPUT_DIR) 4
	@echo "Clean all cpg.bin files after JS code slicing..."
	@find $(CODE_SLICING_OUTPUT_DIR) -type f -name "cpg.bin" -delete
	@echo "Step 2: Running LLM with MalLLM..."
	@bash $(SCRIPT_DIR)/run_mal_llm.sh $(CODE_SLICING_OUTPUT_DIR) $(LLM_OUTPUT_DIR) 4
	@echo "Pipeline execution completed. Check the output directories for results."

clean:
	@echo "Cleaning up log files..."
	@find . -type f -name "*.log" -delete
	@echo "Cleanup completed."
	@find . -type d -name "output" -exec rm -rf {} +
	@find . -type d -name ".metals" -exec rm -rf {} +
	@echo "Output directories cleaned."
