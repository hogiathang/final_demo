import json
from pathlib import Path
from utils.utils import *

def load_predictions(base_dir="output"):
    base = Path(base_dir)

    results_list = []

    for label in ["benign","malicious"]:
        for package in get_packages(base / label):
            package_path = base / label / Path(package)
            results = []
            for output_file_path in package_path.glob("*.json"):
                with open(output_file_path, mode="r") as f:
                    try: 
                        results.append(json.load(f))
                    except: 
                        continue
            final_result = calculate_result(results)
            final_result['package_name'] = package
            final_result['label'] = label
            results_list.append(final_result)
            # print(final_result)
    print(f"[INFO] Load total samples: {len(results_list)}")
    return results_list


# ---------------------------------------------------
# Classification rule:
# malicious if malware > 0.8 else benign
# (obfuscated NOT used for prediction)
# ---------------------------------------------------
def calculate_result(results):
    # Khởi tạo giá trị mặc định
    max_conf = 0.0
    max_obf = 0.0
    max_mal = 0.0
    max_sec = 0.0

    # Duyệt qua từng kết quả (mỗi kết quả từ một slice)
    for item in results:
        # Chuyển đổi mọi thứ về list để xử lý chung một logic
        # Nếu là dict thì bọc vào list, nếu là list thì giữ nguyên
        sub_items = item if isinstance(item, list) else [item]

        for sub_item in sub_items:
            # Chỉ xử lý nếu sub_item là dictionary và không rỗng
            if not isinstance(sub_item, dict):
                continue

            # Ép kiểu về float để tránh lỗi so sánh nếu model trả về chuỗi "0.9"
            try:
                max_conf = max(float(sub_item.get("confidence", 0.0)), max_conf)
                max_obf = max(float(sub_item.get("obfuscated", 0.0)), max_obf)
                max_mal = max(float(sub_item.get("malware", 0.0)), max_mal)
                max_sec = max(float(sub_item.get("securityRisk", 0.0)), max_sec)
            except (ValueError, TypeError):
                continue

    # --- Tính toán các chỉ số phái sinh ---
    
    # Một package được coi là mã độc nếu có ít nhất 1 slice đạt ngưỡng 0.8
    is_malware = 1 if max_mal >= 0.8 else 0
    is_obfuscated = 1 if max_obf > 0.5 else 0
    
    # Công thức Threat Score (Trọng số có thể điều chỉnh)
    threat_score = 0.6 * max_mal + 0.3 * max_obf + 0.1 * max_sec
    
    # Đặc trưng tương tác giữa mã độc và làm rối
    interaction_mal_obs = 1 if max_mal >= 0.8 and max_obf >= 0.5 else 0

    return {
        "confidence": max_conf,
        "obfuscated_score": max_obf,
        "malware_score": max_mal,
        "securityRisk": max_sec,
        "is_obfuscated": is_obfuscated,
        "is_malware": is_malware,
        "threat_score": threat_score,
        "interaction_mal_obs": interaction_mal_obs
    }


def evaluate(results_list):
    tp = 0
    tn = 0
    fp = 0
    fn = 0
    interaction_mal_obs = 0
    for item in results_list:
        label = item['label']
        interaction_mal_obs += item.get("is_obfuscated",0)
        if label == 'malicious':
            if(item['is_malware'] == 1): tp+=1
            else: fn+=1
        else:
            if(item['is_malware'] == 1): fp+=1
            else: tn+=1
    acc = (tn + tp) / ( tp + tn + fp + fn)
    precision = (tp / (tp + fp )) if (tp + fp) > 0 else 0
    recall = tp / (tp + fn ) if (tp + fn) > 0 else 0
    f1 = 2 * precision * recall / (precision + recall ) if (precision + recall) > 0 else 0
    return {
        "accuracy": acc,
        "precision": precision,
        "recall": recall,
        "f1_score": f1,
        "false_positive": fp,
        "obfuscated_rate": interaction_mal_obs / tp if tp > 0 else 0,
        "tp": tp,
        "tn": tn,
        "fp": fp,
        "fn": fn
    }


def print_report(metrics):
    print("==== Model Evaluation Report ====")
    print(f"Accuracy                   : {metrics['accuracy']:.4f}")
    print(f"Precision                   : {metrics['precision']:.4f}")
    print(f"Recall                      : {metrics['recall']:.4f}")
    print(f"F1 Score                   : {metrics['f1_score']:.4f}")
    print(f"False Positives            : {metrics['false_positive']}")
    print(f"Obfuscated Rate            : {metrics['obfuscated_rate']}")
    print("---- Confusion Matrix ----")
    print(f"TP: {metrics['tp']}  FP: {metrics['fp']}")
    print(f"FN: {metrics['fn']}  TN: {metrics['tn']}")
    print("===============================")


if __name__ == "__main__":
    samples = load_predictions("output")
    metrics = evaluate(samples)
    print_report(metrics)
