# Hướng dẫn chạy Finvera ở chế độ Live (không phải Demo)

Tài liệu này hướng dẫn điền đầy đủ biến môi trường và khởi động cả 3 service
(`finvera-be`, `finvera-ai`, `finvera-fe`) để chạy với dữ liệu và LLM thật, thay
vì fixture/demo. Đọc kèm:

- `docs/runbooks/private-market-overview.md` — chi tiết kích hoạt live TCBS cho
  Feature 001 (gia hạn OTP, kiểm tra sau kích hoạt).
- `tools/market-data/provider-poc/RUN_G03_PROBE.md` — nếu chưa đóng gate G-03.
- `finvera-project-status.md` — tổng quan trạng thái hiện tại của dự án.

---

## 0. Tổng quan kiến trúc và cổng mặc định

```
finvera-fe  (Vite dev, :5173) --proxy /api--> finvera-be (:8080) --http--> finvera-ai (:8000)
                                                    |                           |
                                                    v                           v
                                               PostgreSQL (:5432)          Qdrant (:6333)
```

`finvera-fe` không có file `.env` nào — nó chỉ proxy `/api` sang
`http://127.0.0.1:8080` (xem `finvera-fe/vite.config.ts`), không cần cấu hình
gì thêm.

## 1. Checklist trước khi bắt đầu

- [ ] PostgreSQL đang chạy, đã tạo database `finvera`.
- [ ] Qdrant đang chạy (xem bước 2).
- [ ] Tài khoản **TCBS OpenAPI/iFlash** đã đăng ký (khác tài khoản chứng khoán
      thường) — cần cho giá thị trường live.
- [ ] API key **Gemini** (miễn phí tại https://aistudio.google.com/) — cần cho
      Feature 006/007 (RAG, AI Analyst).
- [ ] Java 21, Node.js, `uv` (Python) đã cài.

---

## 2. Hạ tầng nền

### PostgreSQL

Chỉ cần một database rỗng tên `finvera` — Flyway (`spring.flyway.enabled: true`)
tự tạo toàn bộ schema khi `finvera-be` khởi động lần đầu, không cần chạy SQL
tay:

```sql
CREATE DATABASE finvera;
```

### Qdrant (vector DB cho Feature 006/007)

Chưa chạy thì Feature 006/007 sẽ lỗi khi gọi retrieval. Cách nhanh nhất (cần
Docker):

```powershell
docker run -d --name finvera-qdrant -p 6333:6333 -p 6334:6334 -v qdrant_storage:/qdrant/storage qdrant/qdrant
```

Kiểm tra đã chạy: `curl http://localhost:6333/collections` phải trả JSON (không
lỗi kết nối). `finvera-ai` tự tạo collection `research_chunks_v1` khi khởi động
lần đầu — không cần tạo tay.

---

## 3. `finvera-be/.env` — điền từng biến

File thật, gitignore, copy từ `finvera-be/.env.example`. Các nhóm biến dưới
đây theo đúng thứ tự trong file.

### 3.1 Database

| Biến | Giá trị |
|---|---|
| `FINVERA_DATABASE_URL` | `jdbc:postgresql://127.0.0.1:5432/finvera` (đổi host/port nếu Postgres không chạy local) |
| `FINVERA_DATABASE_USERNAME` | user Postgres của bạn |
| `FINVERA_DATABASE_PASSWORD` | mật khẩu Postgres của bạn |

### 3.2 Owner (tài khoản chủ sở hữu duy nhất)

| Biến | Cách lấy |
|---|---|
| `FINVERA_OWNER_ID` | UUID bất kỳ — PowerShell: `[guid]::NewGuid().ToString()` |
| `FINVERA_OWNER_USERNAME` | tên đăng nhập bạn chọn |
| `FINVERA_OWNER_PASSWORD_HASH` | **hash BCrypt** của mật khẩu thật bạn chọn — xem cách tạo bên dưới, **không** gõ mật khẩu thật vào chat với AI hay bất kỳ tool online nào |

**Tạo BCrypt hash an toàn, chạy hoàn toàn trên máy bạn** (dùng đúng thư viện
project đang dùng, không cần cài thêm gì — JDK 21 đã có `jshell`):

```powershell
$jar = (Get-ChildItem "$env:USERPROFILE\.m2\repository\org\springframework\security\spring-security-crypto" -Recurse -Filter "spring-security-crypto-*.jar" |
  Where-Object { $_.Name -notlike "*sources*" } | Sort-Object Name -Descending | Select-Object -First 1).FullName
jshell --class-path $jar
```

Trong jshell (mật khẩu chỉ hiện trên màn hình của bạn, không gửi đi đâu cả):

```java
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
new BCryptPasswordEncoder(12).encode("mật-khẩu-thật-của-bạn-ở-đây")
```

Copy chuỗi `$2a$12$...` in ra vào `FINVERA_OWNER_PASSWORD_HASH`. Gõ `/exit` để
thoát jshell.

### 3.3 Độ trễ dữ liệu hợp đồng (freshness)

| Biến | Giá trị đề xuất |
|---|---|
| `FINVERA_MARKET_INDEX_CONTRACTED_DELAY` | `PT0S` (không trễ) hoặc theo hợp đồng dữ liệu TCBS thật của bạn |
| `FINVERA_STOCK_QUOTE_CONTRACTED_DELAY` | `PT15M` (mặc định hợp lý cho dữ liệu miễn phí/độ trễ 15 phút) |

### 3.4 Feature 001 — TCBS live (chỉ mục thị trường)

| Biến | Giá trị live |
|---|---|
| `FINVERA_MARKET_PROVIDER_MODE` | **`live`** (đúng chữ này, không phải tên khác) |
| `FINVERA_MARKET_PROVIDER_LIVE_ENABLED` | `true` |
| `FINVERA_MARKET_FIXTURE_BOOTSTRAP_ENABLED` | `false` |
| `FINVERA_TCBS_BASE_URL` | `https://openapi.tcbs.com.vn` (không đổi) |
| `FINVERA_TCBS_API_KEY` | API key TCBS OpenAPI thật của bạn |
| `FINVERA_MARKET_TCBS_POLL_INTERVAL_MS` | `60000` (60 giây/lần, đã kiểm chứng an toàn với rate limit TCBS) |

Sau khi set các biến này và khởi động `finvera-be`, còn **một bước thủ công
bắt buộc**: gọi API gia hạn OTP một lần (xem mục 6 bên dưới) — nếu không, phiên
TCBS sẽ luôn báo `PROVIDER_AUTH_REQUIRED`.

### 3.5 Feature 001 — nạp lịch sử Vnstock (bootstrap một lần, không phải live liên tục)

| Biến | Giá trị |
|---|---|
| `FINVERA_MARKET_IMPORT_ENABLED` | `false` bình thường; chỉ bật `true` khi đang nạp một gói lịch sử |
| `FINVERA_MARKET_IMPORT_PACKAGE_PATH` | đường dẫn file JSON xuất từ `export_history.py`, để trống nếu không nạp |

### 3.6 Feature 002 — Stock Detail, các cờ live

| Biến | Giá trị live | Ghi chú |
|---|---|---|
| `FINVERA_STOCK_QUOTE_LIVE_ENABLED` | `true` | bật giá real-time từng mã (`TcbsStockQuoteProvider`) — **yêu cầu `FINVERA_MARKET_PROVIDER_MODE=live` ở trên đã bật**, vì dùng chung phiên TCBS |
| `FINVERA_STOCK_SECTOR_BASIS_ENABLED` | `true` sau khi đã import sector reference (mục 3.7) | khuyến nghị bật thử ở non-production trước để kiểm tra độ trễ, theo đúng ghi chú trong `tasks.md` T064 |
| `FINVERA_STOCK_CHART_MAX_WINDOW` | `2Y` (mặc định) | |

Báo cáo tài chính (fundamentals) không có cờ bật/tắt riêng — chỉ cần đã import
(mục 3.7) là hiển thị, không cần biến nào khác. Corporate actions không có
adapter live nào cả (owner đã chọn RAW-only vĩnh viễn ở gate G-02) nên cũng
không có cờ tương ứng — đã dọn khỏi `application.yaml` để tránh nhầm là còn
tác dụng gì đó.

### 3.7 Feature 002 — nạp dữ liệu Vnstock (bootstrap một lần mỗi loại)

Ba cặp biến này dùng lần lượt, **mỗi lần chỉ bật một cặp**, chạy app một lần để
nạp rồi tắt lại — không phải cấu hình chạy thường trực. `_PACKAGE_PATH` nhận
**một file JSON, hoặc một thư mục** (tự quét toàn bộ file đúng loại trong đó —
dùng khi nạp nhiều mã cùng lúc từ `export_all_symbols.py`, mục 6.3):

| Biến | Mục đích |
|---|---|
| `FINVERA_STOCK_IMPORT_DAILY_BAR_ENABLED` / `_PACKAGE_PATH` | nạp lịch sử giá đầy đủ OHLCV (file `daily-bars-*.json`) |
| `FINVERA_STOCK_IMPORT_FUNDAMENTALS_ENABLED` / `_PACKAGE_PATH` | nạp báo cáo tài chính (file `fundamentals-*.json`) |
| `FINVERA_STOCK_IMPORT_SECTOR_REFERENCE_ENABLED` / `_PACKAGE_PATH` | nạp phân loại ngành (file `sector-reference-*.json`) |

Nạp cả thư mục vẫn an toàn nếu một vài mã lỗi — importer bỏ qua file lỗi, ghi
log, và tiếp tục các file còn lại thay vì dừng cả batch. Xem lệnh export chi
tiết ở mục 6.3.

### 3.8 Kết nối sang `finvera-ai` (Feature 006/007)

| Biến | Giá trị |
|---|---|
| `FINVERA_RESEARCH_INTERNAL_API_KEY` | một chuỗi bí mật ngẫu nhiên bạn tự chọn — **phải giống hệt** `INTERNAL_API_KEY` trong `finvera-ai/.env` |
| `FINVERA_RESEARCH_AI_SERVICE_URL` | `http://127.0.0.1:8000/internal/v1` |
| `FINVERA_ANALYST_INTERNAL_API_KEY` | thường dùng chung giá trị với `FINVERA_RESEARCH_INTERNAL_API_KEY` |
| `FINVERA_ANALYST_AI_SERVICE_URL` | `http://127.0.0.1:8000` |

Tạo chuỗi bí mật nhanh: PowerShell `[guid]::NewGuid().ToString("N")`.

---

## 4. `finvera-ai/.env` — điền từng biến

File thật, gitignore, copy từ `finvera-ai/.env.example`.

| Biến | Giá trị live |
|---|---|
| `ENVIRONMENT` | `development` (hoặc `production` nếu bạn phân biệt cấu hình theo môi trường) |
| `INTERNAL_API_KEY` | **giống hệt** `FINVERA_RESEARCH_INTERNAL_API_KEY`/`FINVERA_ANALYST_INTERNAL_API_KEY` ở trên |
| `GEMINI_API_KEY` | key thật lấy tại https://aistudio.google.com/ — hiện đang là placeholder `your-g...`, **bắt buộc phải đổi** để Feature 006/007 chạy được |
| `GEMINI_GENERATION_MODEL` | `gemini-2.5-flash` (mặc định, không cần đổi) |
| `GEMINI_EMBEDDING_MODEL` | `text-embedding-004` (mặc định) |
| `EMBEDDING_DIMENSION` | `768` (khớp với model trên — không tự đổi một mình) |
| `EMBEDDING_VERSION` | `gemini-embedding-v1` (mặc định) |
| `QDRANT_HOST` | `localhost` |
| `QDRANT_PORT` | `6333` |
| `QDRANT_COLLECTION_NAME` | `research_chunks_v1` (mặc định) |
| `BACKEND_INTERNAL_API_URL` | `http://127.0.0.1:8080/internal/v1` |
| `ANALYST_MAX_TOOL_CALLS` | `10` (mặc định, chỉnh nếu muốn AI Analyst gọi nhiều/ít tool hơn) |
| `ANALYST_TOOL_CALL_TIMEOUT_SECONDS` | `10.0` |
| `ANALYST_ASK_TIMEOUT_SECONDS` | `30.0` |

---

## 5. `finvera-fe`

Không có file `.env`. Chỉ cần:

```powershell
cd finvera-fe
npm install
```

---

## 6. Thứ tự khởi động và kích hoạt live

### 6.1 Khởi động 3 service (mỗi lệnh một cửa sổ terminal riêng)

```powershell
# 1) Backend — tự chạy Flyway migration khi khởi động
cd finvera-be
.\mvnw.cmd spring-boot:run

# 2) AI service
cd finvera-ai
uv sync
uv run uvicorn app.main:app --reload --port 8000

# 3) Frontend
cd finvera-fe
npm run dev
```

Mở `http://localhost:5173`, đăng nhập bằng `FINVERA_OWNER_USERNAME` +
mật khẩu thật bạn đã hash ở mục 3.2.

### 6.2 Kích hoạt live TCBS (Feature 001, bắt buộc để có giá thật)

Sau khi backend đã chạy với `FINVERA_MARKET_PROVIDER_MODE=live` và
`FINVERA_TCBS_API_KEY` đã điền, gọi endpoint gia hạn **một lần** với OTP thật
(hướng dẫn đầy đủ, gồm cả curl mẫu, ở
`docs/runbooks/private-market-overview.md` → "Activate live TCBS ingestion").
TCBS giới hạn token tối đa 8 giờ, nên bạn sẽ cần lặp lại bước này mỗi phiên làm
việc dài.

### 6.3 Nạp dữ liệu lịch sử Vnstock (Feature 002)

Thư mục `tools/market-data/vnstock-export/` dùng chung môi trường Python với
`provider-poc` (không có `pyproject.toml`/venv riêng) — luôn chạy với
`--project ../provider-poc`.

**Cách nhanh nhất — lấy toàn bộ thị trường bằng một lệnh**, tự dừng khi xong,
tự tiếp tục nếu bạn Ctrl+C giữa chừng rồi chạy lại đúng lệnh đó (checkpoint
trong `output/full-universe-checkpoint.json`):

```powershell
cd tools/market-data/vnstock-export

# Chạy thử với vài mã trước khi để chạy hàng giờ không giám sát:
uv run --project ../provider-poc python export_all_symbols.py --start 2024-01-01 --max-symbols 5

# Chạy toàn bộ ~1525 mã (HOSE+HNX+UPCOM, mất vài giờ tùy tốc độ mạng — chạy nền được):
uv run --project ../provider-poc python export_all_symbols.py --start 2024-01-01
```

Không cần truyền `--end` — mặc định lấy đến **hôm nay**. Muốn cập nhật thêm
phiên giao dịch mới sau này, chạy lại **đúng lệnh cũ** (không đổi gì) —
script tự nhận ra ngày cuối đã đổi và fetch lại phần dữ liệu giá bị thiếu,
không bị kẹt mãi ở lần chạy đầu tiên.

Kết quả: nhiều file `daily-bars-<mã>-*.json` và `fundamentals-<mã>-*.json`
trong `output/`. Sau đó chỉ cần **trỏ `*_PACKAGE_PATH` vào cả thư mục
`output/`** (không phải từng file) — xem mục 3.7, importer tự quét đúng loại
file, bỏ qua mã nào lỗi mà không dừng cả batch.

**Nếu chỉ cần vài mã cụ thể** (không cần toàn thị trường), chạy riêng từng
script:

```powershell
uv run --project ../provider-poc python export_history.py --symbol VNM --venue HOSE --start 2024-01-01 --end 2026-08-01
uv run --project ../provider-poc python export_daily_bars.py --symbol VNM --start 2025-01-01 --end 2026-08-01
uv run --project ../provider-poc python export_fundamentals.py --symbol VNM --period quarter
```

**Phân loại ngành** (một lần cho toàn thị trường, không lặp theo mã):

```powershell
uv run --project ../provider-poc python export_sector_reference.py --scheme-version 4.0.6   # xem version thật: uv pip show vnstock
```

Với mỗi loại dữ liệu: mở `finvera-be/.env`, điền `*_PACKAGE_PATH` (file hoặc
thư mục), bật `*_ENABLED=true` tương ứng, khởi động lại backend một lần để
nạp, rồi tắt `*_ENABLED` về `false` lại (tránh nạp trùng lặp ở lần chạy sau).

---

## 7. Kiểm tra đã live thật, không phải demo

| Kiểm tra | Cách xác nhận |
|---|---|
| Backend đọc đúng file `.env` | Log khởi động không báo lỗi bind property; `GET http://localhost:8080/actuator/health` trả `UP` |
| TCBS live thật | Sau khi gia hạn OTP, `GET /api/v1/market/overview` trả số liệu khớp với giá thị trường thật, không phải các số tròn kiểu fixture |
| Giá real-time từng mã | Mở trang chi tiết một mã bất kỳ, giá phải khớp bảng giá thật (chỉ khi `FINVERA_STOCK_QUOTE_LIVE_ENABLED=true`) |
| Dữ liệu lịch sử/báo cáo tài chính | Sau khi import, biểu đồ/báo cáo hiển thị đúng số liệu thật của mã đó, không phải "no data" |
| AI Analyst / RAG hoạt động | Hỏi AI Analyst một câu — nếu `GEMINI_API_KEY` còn là placeholder sẽ báo lỗi rõ ràng (401/invalid key) thay vì trả lời |
| Qdrant có dữ liệu | `curl http://localhost:6333/collections/research_chunks_v1` trả `points_count` > 0 sau khi ingest ít nhất 1 tài liệu |

---

## 8. Xử lý sự cố nhanh

| Triệu chứng | Nguyên nhân thường gặp |
|---|---|
| `/api/v1/market/providers/tcbs/token-renewal` luôn trả `PROVIDER_AUTH_REQUIRED` | `FINVERA_MARKET_PROVIDER_MODE` không đúng chữ `live`, hoặc `FINVERA_TCBS_API_KEY` để trống |
| Giá real-time từng mã không lên | `FINVERA_STOCK_QUOTE_LIVE_ENABLED=false`, hoặc Feature 001 chưa live (xem trên) |
| AI Analyst báo lỗi 401/invalid key | `GEMINI_API_KEY` vẫn là placeholder `your-g...` |
| RAG không tìm thấy tài liệu nào | Qdrant chưa chạy, hoặc chưa ingest tài liệu nào qua Feature 006 |
| Backend không gọi được `finvera-ai` | `INTERNAL_API_KEY` hai bên không khớp nhau, hoặc `finvera-ai` chưa chạy ở port 8000 |
| Import Vnstock không thấy dữ liệu mới | Quên bật `*_ENABLED=true` + trỏ đúng `*_PACKAGE_PATH`, hoặc quên khởi động lại backend sau khi đổi `.env` |
