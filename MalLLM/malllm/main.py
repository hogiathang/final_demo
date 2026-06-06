import argparse
import concurrent
import json
from concurrent.futures import ThreadPoolExecutor, as_completed
from pathlib import Path
import random
from collections import defaultdict

import torch

# Assuming these are custom modules in your project
from pre_process.data_loader import Dataloader
from model.model_config import ModelConfig
from model.model import AIModel
from eval.eval_model import *
from utils.utils import *

random.seed(42)

def parse_args():
    parser = argparse.ArgumentParser(description="MalLLM Inference Script")
    parser.add_argument("--input_dir", type=str, default="../data/slices/", help="Directory containing input slices")
    parser.add_argument("--config_file", type=str, default="../config/deepseek-coder-6.7b.json", help="Path to model config file")
    parser.add_argument("--output_dir", type=str, default="../output/result/", help="Directory to save output JSON files")
    parser.add_argument("--num_workers", type=int, default=2, help="Number of worker threads for concurrent processing")
    return parser.parse_args()

def set_up_model(config_file: Path):
    config = ModelConfig.from_json_file(config_file)
    model = AIModel(config)
    print(f"[INFO] Loaded model: {config.model_name}")
    return model

def inference(sample, model, output_root: Path):
    out_pkg_dir = output_root / sample.label / sample.package_name
    json_output_path = out_pkg_dir / f"{sample.name[:-4]}.json"

    try:
        # Tokenize → generate prediction
        inputs = model.tokenize(Path(sample.package_path))
        
        # Optional: Skip logic if tokens > 8000
        # input_length = inputs["input_ids"].shape[1]
        # if input_length > 8000:
        #     print(f"[WARN] Skipping {sample.name}: File too long ({input_length} tokens).")
        #     return None
        
        result = model.generate(inputs)

        if result is not None and result.strip() != "":
            json_str = extract_json_string(result)
            out_pkg_dir.mkdir(parents=True, exist_ok=True)
            json_output_path.write_text(json_str, encoding="utf-8")

            try:
                print(f"[INFO] Processed slice: {sample.package_name}/{sample.name}")
                return json.loads(json_str)
            except json.JSONDecodeError:
                print(f"[WARN] Invalid JSON format for {sample.name}")
                return None
        else:
            print(f"[WARN] Empty result for {sample.name} in {sample.package_name}")
            return None

    except Exception as e:
        print(f"[ERROR] Failed to process {sample.name} in {sample.package_name}: {str(e)}")

def process_package(package_name: str, package_samples: list, model, output_root: Path):
    """
    Process a single package: Read existing files or infer new ones.
    Early stopping applies if malware is confidently detected.
    """
    print(f"[INFO] Processing package: {package_name} with {len(package_samples)} slices")
    
    max_conf, max_obf, max_mal, max_sec = 0.0, 0.0, 0.0, 0.0
    label = package_samples[0].label # Ground truth label
    out_pkg_dir = output_root / label / package_name
    
    for s in package_samples:
        json_filename = f"{s.name[:-4]}.json"
        json_output_path = out_pkg_dir / json_filename
        
        item = None
        
        # 1. CHECK IF JSON RESULT ALREADY EXISTS
        if json_output_path.exists():
            try:
                with open(json_output_path, "r", encoding="utf-8") as f:
                    item = json.load(f)
                    print(f"[INFO] Loaded existing prediction for slice: {s.name} in {package_name}")
            except Exception as e:
                print(f"[WARN] Slice {json_filename} in {package_name} has invalid JSON.")
                continue
                
        # 2. IF NOT EXISTS OR INVALID -> RUN INFERENCE
        if item is None:
            item = inference(s, model, output_root)
            
            # Clear VRAM only when model is actually used
            torch.cuda.empty_cache()
            
        # Skip slice if inference fails
        if not item: 
            continue
            
        # 3. SCORE EVALUATION LOGIC
        sub_items = item if isinstance(item, list) else [item]
        
        for sub_item in sub_items:
            if not isinstance(sub_item, dict): continue
            try:
                max_conf = max(float(sub_item.get("confidence", 0.0)), max_conf)
                max_obf = max(float(sub_item.get("obfuscated", 0.0)), max_obf)
                max_mal = max(float(sub_item.get("malware", 0.0)), max_mal)
                max_sec = max(float(sub_item.get("securityRisk", 0.0)), max_sec)
            except (ValueError, TypeError):
                continue
                
        # 4. EARLY STOPPING
        if max_mal >= 0.8:
            print(f"[ALERT] Detected malware in package {package_name} at slice {s.name}. Stop processing of this package.")
            break 
            
    # 5. Pack results for evaluation
    is_malware = 1 if max_mal >= 0.8 else 0
    is_obfuscated = 1 if max_obf > 0.5 else 0
    threat_score = 0.6 * max_mal + 0.3 * max_obf + 0.1 * max_sec
    interaction_mal_obs = 1 if max_mal >= 0.8 and max_obf >= 0.5 else 0
    
    return {
        "package_name": package_name,
        "label": label,
        "confidence": max_conf,
        "obfuscated_score": max_obf,
        "malware_score": max_mal,
        "securityRisk": max_sec,
        "is_obfuscated": is_obfuscated,
        "is_malware": is_malware,
        "threat_score": threat_score,
        "interaction_mal_obs": interaction_mal_obs
    }

def read_input(input_dir: Path):
    dataloader = Dataloader(input_dir)
    samples = dataloader.load_samples()
    print(f"[INFO] Loaded {len(samples)} samples from {input_dir}")
    return samples

if __name__ == '__main__':
    args = parse_args()

    output_dir = Path(args.output_dir)
    output_dir.mkdir(parents=True, exist_ok=True)

    print("[INFO] Loading model...")
    model = set_up_model(Path(args.config_file))

    print("[INFO] Loading samples...")
    samples = read_input(Path(args.input_dir))

    if not samples:
        print("[ERROR] No samples found.")
        exit(1)

    # Group samples by package
    packages = defaultdict(list)

    for sample in samples:
        packages[sample.package_name].append(sample)

    print(f"[INFO] Found {len(packages)} packages")

    results_list = []

    with ThreadPoolExecutor(max_workers=args.num_workers) as executor:
        futures = {
            executor.submit(
                process_package,
                package_name,
                package_samples,
                model,
                output_dir
            ): package_name
            for package_name, package_samples in packages.items()
        }

        for future in as_completed(futures):
            package_name = futures[future]

            try:
                result = future.result()

                if result:
                    results_list.append(result)

                print(f"[INFO] Finished package: {package_name}")

            except Exception as e:
                print(f"[ERROR] Failed package {package_name}: {e}")

    summary_file = output_dir / "summary.json"

    with open(summary_file, "w", encoding="utf-8") as f:
        json.dump(results_list, f, indent=2, ensure_ascii=False)

    print(f"[INFO] Saved summary to {summary_file}")
    print(f"[INFO] Processed {len(results_list)} packages")