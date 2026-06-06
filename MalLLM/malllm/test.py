from collections import defaultdict
import concurrent.futures
import json
import torch
from pathlib import Path
from model.model_config import ModelConfig
from model.model import AIModel
from pre_process.data_loader import Dataloader
from eval.eval_model import *
from utils.utils import *
import random
random.seed(42)

NUM_WORKERS = 1

def set_up_model(config_file: Path):
    config = ModelConfig.from_json_file(config_file)
    model = AIModel(config)
    print(f"[INFO] Loaded model: {config.model_name}")
    return model

def inference(sample, model):
    output_root = Path("../output")
    out_pkg_dir = output_root / sample.label / sample.package_name
    out_pkg_dir.mkdir(parents=True, exist_ok=True)
    
    try:
        inputs = model.tokenize(Path(sample.package_path))
        result = model.generate(inputs)

        if result and result.replace(" ", "") != "":
            json_str = extract_json_string(result)
            json_output_path = out_pkg_dir / f"{sample.name[:-3]}.json"
            json_output_path.write_text(json_str, encoding="utf-8")
            
            # TRẢ VỀ DICT ĐỂ ĐÁNH GIÁ NGAY LẬP TỨC
            try:
                return json.loads(json_str)
            except json.JSONDecodeError:
                print(f"[WARN] Invalid JSON format for {sample.name}")
                return None
        else:
            print(f"[WARN] Empty result for {sample.name} in {sample.package_name}")
            return None

    except Exception as e:
        print(f"[ERROR] Failed to process {sample.name} in {sample.package_name}: {str(e)}")
        return None

def process_package(package_name: str, package_samples: list, model, output_root: Path):
    """Xử lý toàn bộ các slice của một package. Dừng sớm nếu phát hiện mã độc."""
    print(f"[INFO] Bắt đầu quét gói: {package_name} ({len(package_samples)} slices)")
    
    max_conf, max_obf, max_mal, max_sec = 0.0, 0.0, 0.0, 0.0
    label = package_samples[0].label # Nhãn thực tế của gói
    out_pkg_dir = output_root / label / package_name
    
    for i, s in enumerate(package_samples):
        # Tạo tên file JSON tương ứng (Dùng .stem để tự động bỏ đuôi .txt)
        json_filename = f"{Path(s.name).stem}.json"
        json_output_path = out_pkg_dir / json_filename
        
        item = None

        # 1. KIỂM TRA FILE JSON ĐÃ TỒN TẠI CHƯA
        if json_output_path.exists():
            try:
                with open(json_output_path, "r", encoding="utf-8") as f:
                    item = json.load(f)
            except Exception as e:
                print(f"[WARN] File {json_filename} bị lỗi đọc ({e}), tiến hành inference lại.")
                item = None # Nếu lỗi thì coi như chưa có, tí nữa sẽ inference lại
                
        # 2. NẾU CHƯA CÓ HOẶC FILE LỖI -> THỰC HIỆN INFERENCE
        if item is None:
            # print(f"[INFER] Tiến hành inference slice: {s.name}")
            item = inference(s, model)
            
            # Giải phóng VRAM chỉ khi nào có chạy model
            torch.cuda.empty_cache()
            
        # Nếu vẫn không có kết quả (lỗi inference) thì bỏ qua slice này
        if not item: 
            continue
        
        # 2. Xử lý logic giống calculate_result cũ (xử lý list hoặc dict)
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
                
        # 3. LOGIC EARLY STOPPING TẠI ĐÂY
        if max_mal >= 0.8:
            print(f"[ALERT] Phát hiện Malware >= 0.8 tại slice {i+1}/{len(package_samples)} của {package_name}. DỪNG SỚM!")
            break # Bỏ qua các slice còn lại của gói này
            
    # 4. Đóng gói kết quả của Package này
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

if __name__ == '__main__':
    print("[INFO] Loading samples...")
    samples = Dataloader().load_data()
    
    # BƯỚC MỚI: Nhóm các slice thành từng package
    packages_dict = defaultdict(list)
    for s in samples:
        packages_dict[s.package_name].append(s)
        
    print(f"[INFO] Loaded {len(samples)} slices across {len(packages_dict)} packages.")
    
    model = set_up_model(Path('../config/deepseek-coder-6.7b.json'))

    # Khai báo đường dẫn Output
    OUTPUT_ROOT = Path("../output")
    OUTPUT_ROOT.mkdir(parents=True, exist_ok=True)

    print("[INFO] Starting Early-Stopping Threaded Inference...")

    results_list = [] # Lưu trữ kết quả cuối cùng để đưa vào evaluate()
    NUM_WORKERS = 2 # Chú ý VRAM khi set số này
    
    with concurrent.futures.ThreadPoolExecutor(max_workers=NUM_WORKERS) as executor:
        # Đẩy từng PACKAGE (chứa danh sách các slice) vào executor
        futures = {
            executor.submit(process_package, pkg_name, pkg_samples, model, OUTPUT_ROOT): pkg_name
            for pkg_name, pkg_samples in packages_dict.items()
        }
        
        # Thu thập kết quả ngay khi một package hoàn thành
        for future in concurrent.futures.as_completed(futures):
            pkg_name = futures[future]
            try:
                pkg_result = future.result()
                if pkg_result:
                    results_list.append(pkg_result)
            except Exception as e:
                print(f"[ERROR] Package {pkg_name} failed completely: {e}")

    # ĐÁNH GIÁ NGAY LẬP TỨC MÀ KHÔNG CẦN LOAD LẠI TỪ FILE
    print(f"[INFO] Inference finished. Evaluating {len(results_list)} packages...")
    
    if results_list:
        metrics = evaluate(results_list)
        print_report(metrics) # Giả sử bạn có hàm này
    else:
        print("[ERROR] No results to evaluate.")