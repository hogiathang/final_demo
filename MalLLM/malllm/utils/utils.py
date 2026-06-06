import json
import re
import os
from pathlib import Path
from json_repair import repair_json

# def extract_json_string(markdown_text: str) -> str:
#     """
#     Extracts JSON string from markdown code blocks.
#     Returns the JSON string or empty string if not found.
#     """
#     # Remove opening ```json or ``` fences
#     cleaned = re.sub(r"```(?:json)?", "", markdown_text, flags=re.IGNORECASE)
#     # Remove closing ``` fences
#     cleaned = cleaned.replace("```", "").strip()

#     # Optional: check if it starts with '{' and ends with '}' to look like JSON
#     if cleaned.startswith("{") and cleaned.endswith("}"):
#         return cleaned
#     return ""

def extract_json_string(markdown_text: str) -> str:
    if not markdown_text:
        return ""

    # Bước 1: Dùng Regex để tìm vùng có khả năng là JSON cao nhất
    # Tìm từ dấu { đầu tiên đến dấu } cuối cùng (hoặc đến hết văn bản nếu thiếu })
    json_match = re.search(r"(\{.*)", markdown_text, re.DOTALL)
    if not json_match:
        return ""
    
    potential_json = json_match.group(1).strip()

    try:
        # Bước 2: Dùng json_repair để "vá" nội dung
        # repair_json sẽ trả về một chuỗi JSON đã được sửa lỗi
        repaired_json_str = repair_json(potential_json)
        
        # Bước 3: Kiểm tra thử xem có parse được không
        # Nếu parse được nghĩa là đã thành công
        json.loads(repaired_json_str)
        return repaired_json_str
    except Exception:
        return ""


def get_packages(root : Path):
    packages = []

    if not os.path.isdir(root):
        print(f"Directory not found: {root}")
        return []

    # List only directories (packages)
    for name in os.listdir(root):
        path = os.path.join(root, name)
        if os.path.isdir(path):
            packages.append(name)

    return packages

