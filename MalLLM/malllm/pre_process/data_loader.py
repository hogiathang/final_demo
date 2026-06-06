from dataclasses import dataclass
from pathlib import Path
from typing import List, Optional
import random
from collections import defaultdict

@dataclass
class DataSamples:
    name: str
    package_name: str
    package_path: Optional[Path] = None
    package_length: Optional[int] = None
    label: Optional[str] = None

@dataclass
class Dataloader:
    # source_dir: str = "~/workspace/Fo/train/"

    def __init__(self, source_dir: str = "~/workspace/MalLLM/data/MAL_NPMNDB/"):
         self.source_dir = source_dir

    def load_samples(self) -> List[DataSamples]:
        source_path = Path(self.source_dir).expanduser()
        if not source_path.is_dir():
            print(f"Source directory {self.source_dir} does not exist.")
            return []

        samples: List[DataSamples] = []

        for package_path in source_path.iterdir():
            if not package_path.is_dir():
                continue
            
            package_name = package_path.name
            label = "none"
            
            for file_path in package_path.glob("**/*.txt"):
                print(file_path)
                samples.append(DataSamples(
                    name=file_path.name,
                    package_name=package_name,
                    package_path=file_path,
                    label=label,
                    package_length=file_path.stat().st_size
                ))

        return samples

    source_dir: str = "~/workspace/MalLLM/data/MAL_NPMNDB/"

    # def load_data(self) -> List[DataSamples]:
    #     '''
    #     Load data from the source directory
    #     '''

    #     source_path = Path(self.source_dir).expanduser()

    #     if not source_path.is_dir():
    #         print(f"Source directory {self.source_dir} does not exist.")
    #         return []

    #     malicious_path = source_path / "mal"
    #     benign_path = source_path / "ben"

    #     samples: List[DataSamples] = []
        
    #     # Tạo dictionary để theo dõi số lượng gói và file cho từng label
    #     label_stats = {
    #         "malicious": {"packages": set(), "files": 0},
    #         "benign": {"packages": set(), "files": 0}
    #     }

    #     for category_path, label in [(malicious_path, "malicious"), (benign_path, "benign")]:
    #         if not category_path.exists():
    #             continue
            
    #         for file_path in category_path.glob("**/*.txt"):
    #             package_name = file_path.parts[-2]
                
    #             samples.append(DataSamples(
    #                 name=file_path.name,
    #                 package_name=package_name,
    #                 package_path=file_path,
    #                 label=label,
    #                 package_length=file_path.stat().st_size
    #             ))
                
    #             # Cập nhật logic kiểm tra
    #             label_stats[label]["files"] += 1
    #             label_stats[label]["packages"].add(package_name)

    #     # In ra màn hình số lượng gói và file đã thu thập được
    #     print("=== Thống kê dữ liệu đã tải ===")
    #     for lbl in ["malicious", "benign"]:
    #         num_packages = len(label_stats[lbl]["packages"])
    #         num_files = label_stats[lbl]["files"]
    #         print(f"- {lbl.capitalize()}: {num_packages} gói (packages) từ {num_files} file.")
    #     print("===============================")

    #     return samples

    def load_data(self) -> List[DataSamples]:
        '''
        Load data from the source directory, sampling 200 packages per category.
        '''
        source_path = Path(self.source_dir).expanduser()
        if not source_path.is_dir():
            print(f"Source directory {self.source_dir} does not exist.")
            return []

        # malicious_path = source_path / "malicious"
        # benign_path = source_path / "benign"

        malicious_path = source_path / "malware"
        benign_path = source_path / "benign"

        # Dictionary cấu trúc: { label: { package_name: [List of DataSamples] } }
        grouped_data = {
            "malicious": defaultdict(list),
            "benign": defaultdict(list)
        }

        # Tập hợp (set) dùng để lưu các package_name đã gặp -> Xử lý yêu cầu 2
        seen_packages = set()

        # Bước 1: Thu thập toàn bộ dữ liệu và gom nhóm theo package_name
        for category_path, label in [(malicious_path, "malicious"), (benign_path, "benign")]:
            if not category_path.exists():
                continue
                
            # Duyệt qua từng thư mục con (chính là thư mục chứa gói)
            for pkg_dir in category_path.iterdir():
                if not pkg_dir.is_dir():
                    continue
                
                pkg_name = pkg_dir.name
                
                # Yêu cầu 2: Nếu gói có tên trùng với tên gói trước đó => bỏ qua
                if pkg_name in seen_packages:
                    continue
                
                # Tìm tất cả file .txt trong gói này
                txt_files = list(pkg_dir.glob("**/*.txt"))
                
                # Yêu cầu 1: Nếu gói không có file .txt nào => bỏ qua
                if not txt_files:
                    continue
                
                # Đánh dấu gói này đã được xử lý để tránh trùng lặp cho các lần sau
                seen_packages.add(pkg_name)
                
                # Nếu thỏa mãn mọi điều kiện, tiến hành lưu các file .txt
                for file_path in txt_files:
                    sample = DataSamples(
                        name=file_path.name,
                        package_name=pkg_name,
                        package_path=file_path,
                        label=label,
                        package_length=file_path.stat().st_size
                    )
                    grouped_data[label][pkg_name].append(sample)

        final_samples: List[DataSamples] = []

        # Bước 2: Chọn ngẫu nhiên 200 gói cho mỗi label
        for label in ["malicious", "benign"]:
            all_packages = list(grouped_data[label].keys())
            
            # Đảm bảo không lấy quá số lượng gói đang có
            # num_to_sample = min(len(all_packages), 200)
            num_to_sample = len(all_packages)
            
            selected_packages = random.sample(all_packages, num_to_sample)
            print(f"[INFO] Selected {num_to_sample} random packages for category: {label}")

            # Thêm toàn bộ các slices (file .txt) của các gói đã chọn vào danh sách cuối
            for pkg in selected_packages:
                final_samples.extend(grouped_data[label][pkg])

        # Trộn ngẫu nhiên danh sách cuối để quá trình inference không bị tập trung một loại
        random.shuffle(final_samples)
        
        return final_samples