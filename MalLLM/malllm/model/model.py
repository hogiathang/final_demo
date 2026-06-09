from pathlib import Path
from transformers import AutoTokenizer, AutoModelForCausalLM, BitsAndBytesConfig
from .model_config import ModelConfig
from .prompt import Prompt
import torch
torch.cuda.empty_cache()
from peft import PeftModel

base_model_id = "deepseek-ai/deepseek-coder-6.7b-instruct"

bnb_config = BitsAndBytesConfig(
    load_in_4bit=True,
    bnb_4bit_quant_type="nf4",
    bnb_4bit_compute_dtype=torch.float32,
)

# Load model & tokenizer
class AIModel:
    def __init__(self, config : ModelConfig):
        self.config = config

        self.tokenizer = AutoTokenizer.from_pretrained(
            base_model_id,
            trust_remote_code=True,
            # use_fast=False
        )

        self.model = AutoModelForCausalLM.from_pretrained(
            base_model_id,
            # quantization_config=bnb_config,
            dtype=torch.float16, 
            device_map="auto",
            trust_remote_code=True
        )

        # adapter_model_id = config.model_name

        # self.tokenizer = AutoTokenizer.from_pretrained(
        #     base_model_id, 
        #     trust_remote_code=True, 
        #     use_fast=False
        # )

        # base_model = AutoModelForCausalLM.from_pretrained(
        #     base_model_id,
        #     # quantization_config=bnb_config,
        #     device_map="auto",
        #     trust_remote_code=True
        # )

        # self.model = PeftModel.from_pretrained(base_model, adapter_model_id)
    
    def read_file(self, file_path : Path):
        # Nếu file_path là relative, resolve dựa trên folder model.py
        if not file_path.exists() and not file_path.is_absolute():
            file_path = Path(__file__).parent / file_path
            if not file_path.exists():
                raise FileNotFoundError(f"Prompt file not found: {file_path}")
        with open(file_path, "r", encoding="utf-8") as f:
            return f.read().strip()

    def tokenize(self, input_file_path : Path): 
        # Read prompts from given file
        prompt = [
            {"role": "system", "content": self.read_file(Path("system-prompt.txt"))},
            {"role": "user", "content": self.read_file(input_file_path)}
        ]
        inputs = self.tokenizer.apply_chat_template(
            prompt,
            add_generation_prompt=True,
            return_tensors="pt",
            truncation=True,
            max_length=self.tokenizer.model_max_length - self.config.max_new_tokens,
        )
        inputs = inputs.to(self.model.device)
        if self.tokenizer.pad_token_id is None:
            self.tokenizer.pad_token_id = self.tokenizer.eos_token_id

        return inputs

    # def build_instruction_prompt(self, instruction: str):
    #     return f'''
    # You are a professional static security code auditor.  

    # Your task: analyze a single JavaScript (or Node.js) code fragment (a "code slice") and output EXACTLY one JSON object only.  

    # STRICT RULES:
    # 1. Do NOT execute the code. Perform only static analysis.
    # 2. ALWAYS return exactly one JSON object. 
    # 3. DO NOT add explanations, comments, or extra text.  
    # 4. DO NOT wrap JSON in markdown or quotes.  
    # 5. If unsure, use 0.0 for numeric fields.
    # 6. Follow the exact key order below:

    # {{
    # "confidence": float,
    # "obfuscated": float,
    # "malware": float,
    # "securityRisk": float
    # }}

    # ### Instruction:
    # {instruction.strip()}

    # ### Response:
    # '''.lstrip()
    # def tokenize(self, input_file_path: Path):
    # # Read JS / code slice (instruction)
    #     instruction = self.read_file(input_file_path)

    #     # Build prompt EXACTLY like SFT training
    #     prompt_text = self.build_instruction_prompt(instruction)

    #     # Tokenize (NO chat template)
    #     inputs = self.tokenizer(
    #         prompt_text,
    #         return_tensors="pt",
    #         truncation=True,
    #         max_length=self.tokenizer.model_max_length,
    #     )

    #     # Ensure PAD token consistency
    #     if self.tokenizer.pad_token_id is None:
    #         self.tokenizer.pad_token_id = self.tokenizer.eos_token_id

    #     # Move tensors to model device
    #     inputs = inputs.to(self.model.device)

    #     return inputs
    
    def generate(self, inputs) -> str:
        # Generate output
        outputs = self.model.generate(
            **inputs,
            max_new_tokens=self.config.max_new_tokens,
            do_sample=self.config.do_sample,
            top_k=self.config.top_k,
            top_p=self.config.top_p,
            temperature=self.config.temperature,
            num_return_sequences=self.config.num_return_sequences,
            pad_token_id=self.tokenizer.pad_token_id,
            eos_token_id=self.tokenizer.eos_token_id
        )

        # Decode only generated tokens
        if hasattr(inputs, "input_ids"):
            input_length = inputs["input_ids"].shape[1]
        elif isinstance(inputs, dict):
            input_length = inputs["input_ids"].shape[1]
        else:
            input_length = inputs.shape[1]
        return self.tokenizer.decode(
            outputs[0][input_length:],
            skip_special_tokens=True
        )