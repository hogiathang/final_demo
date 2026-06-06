# Pipeline Suy Luận (Inference) Đánh Giá Package 

Tài liệu này mô tả chi tiết luồng thực thi (execution logic) của hệ thống xử lý và đánh giá các package phần mềm (phân loại `malicious` hoặc `benign`) sử dụng mô hình ngôn ngữ lớn (LLM - cấu hình mặc định là `deepseek-coder-6.7b`).

Hệ thống được thiết kế để đọc các slices từ các package, đưa qua LLM để phân tích, và xuất kết quả dưới dạng các file JSON độc lập.

## Tổng Quan Luồng Thực Thi (Execution Flow)

Quá trình từ lúc đưa 1 package vào cho đến khi xuất ra file JSON trải qua 5 giai đoạn chính:

### 1. Nạp và Tiền xử lý Dữ liệu (`load_data`)
Quá trình này quét thư mục gốc (source) và chuẩn bị danh sách các mẫu dữ liệu (samples) để đưa vào mô hình:
* Dữ liệu được chia thành hai nhãn rõ ràng là `malicious` và `benign`.
* Nếu một package bị trùng tên với package đã xử lý trước đó, nó sẽ bị bỏ qua. Các package rỗng (không chứa bất kỳ slice nào) cũng sẽ bị loại bỏ.
* Mỗi package có thể chứa nhiều slice. Mỗi slice được xem là một `DataSamples` độc lập chứa thông tin về đường dẫn, tên package, nhãn, và kích thước.
* Sau khi thu thập toàn bộ các slice từ các package được chọn ngẫu nhiên, danh sách các mẫu sẽ được trộn đều để đảm bảo mô hình không bị thiên lệch (bias) do phải liên tục xử lý một loại nhãn trong thời gian dài.

### 2. Tiền xử lý & Tokenization (`tokenize`)
Một sample (1 slice) cần được tokenize trước khi được đưa qua LLM để suy luận. Quá trình tokenize gồm các bước sau:
* **Tạo Prompt:** Kết hợp nội dung từ file `system-prompt.txt` (đóng vai trò system role) và nội dung của slice hiện tại (đóng vai trò user role).
* **Mã hóa:** Sử dụng template chat của Tokenizer, tự động cắt bớt (truncation) nếu vượt quá `max_length` của mô hình, và chuyển đổi thành dạng tensor (`pt`).
* **Chuyển lên GPU:** Các input tensors được tự động đẩy lên thiết bị tính toán của mô hình (VD: `cuda`).

### 3. Sinh kết quả - LLM Generation (`generate`)
Một sample (1 slice) sau khi tokenize sẽ được dùng làm đầu vào cho quá trình inference của LLM. Quá trình inference gồm các bước sau:
* **Tối ưu tính toán**: Toàn bộ quá trình sinh văn bản được đặt trong block `torch.no_grad()` để vô hiệu hóa việc tính toán đạo hàm (gradient). Điều này giúp tiết kiệm tối đa dung lượng VRAM và tăng tốc độ suy luận (forward pass).
* **Kiểm soát sinh văn bản (Text Generation)**: Các tensor đầu vào được truyền cho mô hình kèm theo các siêu tham số điều khiển từ file cấu hình (config) nhằm quyết định hành vi của LLM:
    * `max_new_tokens`: Giới hạn số lượng token tối đa mà mô hình được phép sinh ra.
    * `do_sample`, `temperature`, `top_k`, `top_p`: Các tham số tinh chỉnh độ ngẫu nhiên, giúp câu trả lời mượt mà hoặc bám sát logic hơn tùy theo độ "sáng tạo" được yêu cầu.
    * `pad_token_id` & `eos_token_id`: Xử lý các token đệm và nhận diện điểm dừng an toàn khi mô hình hoàn tất câu trả lời.
* **Tách lọc kết quả (Slicing Output)**: Theo mặc định, đầu ra của mô hình chứa cả phần prompt gốc và phần câu trả lời mới. Hệ thống sẽ loại bỏ hoàn toàn phần prompt đầu vào, chỉ giữ lại những token mới thực sự do LLM sinh ra.
* **Giải mã (Decoding)**: Các token IDs mới sinh ra sẽ được dịch ngược trở lại thành văn bản thuần (plain text). Ở bước này, các token đặc biệt của hệ thống cũng được lược bỏ để lấy được chuỗi JSON sạch nhất có thể.

### 4. Xử lý Output & Trích xuất JSON (`inference`)
* **Trích xuất:** Dữ liệu text do mô hình sinh ra sẽ được xử lý để đảm bảo đầu ra là một định dạng JSON hợp lệ.
* **Lưu trữ:** * Hệ thống tự động tạo các thư mục đầu ra theo cấu trúc: `OUTPUT_DIR / {label} / {package_name}`.
    * Kết quả của mỗi file `.txt` được lưu thành một file `.json` tương ứng (ví dụ: `slice_1.txt` -> `slice_1.json`).

### 5. Quản lý Bộ nhớ & Đánh giá (`__main__`)
* **Sequential Processing:** Hệ thống chạy vòng lặp tuần tự để đảm bảo tính ổn định của GPU.
* **Giải phóng VRAM:** Ngay sau khi xử lý xong mỗi slice, câu lệnh `torch.cuda.empty_cache()` được gọi để dọn dẹp các bộ nhớ đệm (cache) không còn sử dụng của PyTorch, giúp ngăn chặn triệt để lỗi Out Of Memory (OOM) trong quá trình inference.
* **Đánh giá (Evaluation):** Sau khi toàn bộ các samples đã được chạy qua mô hình và xuất file JSON, hệ thống sẽ gọi hàm `load_predictions` để thu thập lại các file output, tính toán metrics (`evaluate`), và in báo cáo cuối cùng (`print_report`).

---

## 📂 Cấu Trúc Dữ Liệu Minh Họa

**Đầu vào (Input Dataset):**
```text
dataset_source/
├── benign/
│   ├── package_A/
│   │   ├── slice1.txt
│   │   └── slice2.txt
├── malicious/
│   ├── package_B/
│   │   └── slice1.txt
```

**Đầu ra (Output Dataset):**
```text
OUTPUT_DIR/
├── benign/
│   ├── package_A/
│   │   ├── slice1.json
│   │   └── slice2.json
├── malicious/
│   ├── package_B/
│   │   └── slice1.json
```