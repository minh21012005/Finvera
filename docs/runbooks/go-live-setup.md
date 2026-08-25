# Hướng dẫn chạy Finvera ở chế độ Live (không phải Demo)

Tài liệu này hướng dẫn điền đầy đủ biến môi trường và khởi động cả 3 service
(`finvera-be`, `finvera-ai`, `finvera-fe`) để chạy với dữ liệu và LLM thật, thay
vì fixture/demo. Đọc kèm:

- `docs/runbooks/private-market-overview.md` — chi tiết kích hoạt TCBS Thesis
  WebSocket làm lớp dữ liệu live cho Feature 001 (gia hạn TOTP, kiểm tra sau kích hoạt).
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

### 3.4 Feature 001 — TCBS Thesis live (chỉ mục và giá cổ phiếu)

| Biến | Giá trị live |
|---|---|
| `FINVERA_MARKET_PROVIDER_MODE` | Giữ `fixture` khi dev, hoặc `vnstock-package-private` khi nạp gói Vnstock. TCBS là lớp live overlay độc lập, không còn mode `live` |
| `FINVERA_MARKET_FIXTURE_BOOTSTRAP_ENABLED` | `false` sau khi database đã có dữ liệu thật; có thể giữ `true` lúc dev ban đầu |
| `FINVERA_TCBS_LIVE_ENABLED` | `true` |
| `FINVERA_TCBS_BASE_URL` | `https://openapi.tcbs.com.vn` (không đổi) |
| `FINVERA_TCBS_WEBSOCKET_URL` | `wss://openapi.tcbs.com.vn/ws/thesis/v1/stream/normal` (không đổi) |
| `FINVERA_TCBS_API_KEY` | API key TCBS OpenAPI thật của bạn |
| `FINVERA_TCBS_HEARTBEAT_INTERVAL` | `2s` |
| `FINVERA_TCBS_RECONNECT_MAX_DELAY` | `30s` |
| `FINVERA_TCBS_MAX_DYNAMIC_SYMBOLS` | Số mã live tối đa được giữ theo nhu cầu, mặc định `100`; mã được tự đăng ký khi mở trang chi tiết và mã ít dùng nhất được hủy khi đầy |
| `FINVERA_STOCK_QUOTE_LIVE_ENABLED` | `true` nếu muốn trang chi tiết cổ phiếu dùng giá live từ cùng stream |

TCBS Thesis là WebSocket push, vì vậy không còn biến polling
`FINVERA_MARKET_TCBS_POLL_INTERVAL_MS` và cũng không còn
`FINVERA_MARKET_PROVIDER_LIVE_ENABLED`. Sau khi cấu hình và khởi động
`finvera-be`, vẫn cần gia hạn phiên bằng TOTP một lần (xem mục 6.2); nếu không,
backend sẽ báo `PROVIDER_AUTH_REQUIRED`.

### 3.5 Feature 001 — nạp lịch sử Vnstock (bootstrap một lần, không phải live liên tục)

| Biến | Giá trị |
|---|---|
| `FINVERA_MARKET_IMPORT_ENABLED` | `false` bình thường; chỉ bật `true` khi đang nạp một gói lịch sử |
| `FINVERA_MARKET_IMPORT_PACKAGE_PATH` | đường dẫn file JSON xuất từ `export_history.py`, để trống nếu không nạp |
| `FINVERA_MARKET_EOD_RECONCILIATION_ENABLED` | `false` bình thường; `refresh-data.ps1` bật tạm ở bước cuối để dựng lại độ rộng thị trường và regime từ dữ liệu PostgreSQL đã import |

### 3.6 Feature 002 — Stock Detail, các cờ live

| Biến | Giá trị live | Ghi chú |
|---|---|---|
| `FINVERA_STOCK_QUOTE_LIVE_ENABLED` | `true` | bật giá live từng mã từ TCBS Thesis; cần đồng thời bật `FINVERA_TCBS_LIVE_ENABLED=true`; mã active được tự đăng ký khi mở trang chi tiết |
| `FINVERA_STOCK_SECTOR_BASIS_ENABLED` | `true` sau khi đã import sector reference (mục 3.7) | khuyến nghị bật thử ở non-production trước để kiểm tra độ trễ, theo đúng ghi chú trong `tasks.md` T064 |
| `FINVERA_STOCK_CHART_MAX_WINDOW` | `2Y` (mặc định) | |

Báo cáo tài chính (fundamentals) không có cờ bật/tắt riêng — chỉ cần đã import
(mục 3.7) là hiển thị, không cần biến nào khác. Corporate actions không có
adapter live nào cả (owner đã chọn RAW-only vĩnh viễn ở gate G-02) nên cũng
không có cờ tương ứng — đã dọn khỏi `application.yaml` để tránh nhầm là còn
tác dụng gì đó.

### 3.7 Feature 002 — nạp dữ liệu Vnstock (bootstrap một lần mỗi loại)

**Bắt buộc chạy bước 0 dưới đây trước tiên**, một lần, trước bất kỳ import
giá/tài chính nào — `StockIngestionService` chỉ nhận dữ liệu cho mã đã tồn tại
trong `market_instrument` (bảng danh mục của Feature 001); mã chưa đăng ký sẽ
bị từ chối thẳng với `UNKNOWN_INSTRUMENT`, không phải lỗi tạm thời.

| Biến | Mục đích |
|---|---|
| `FINVERA_MARKET_IMPORT_INSTRUMENT_REFERENCE_ENABLED` / `_PACKAGE_PATH` (**0 — chạy trước**) | đăng ký toàn bộ mã (symbol + sàn) vào `market_instrument`; `_PACKAGE_PATH` trỏ vào **file** `instrument-reference.json` (không phải thư mục — script này chỉ xuất một file duy nhất) |
| `FINVERA_STOCK_IMPORT_EQUITY_PROFILE_ENABLED` / `_PACKAGE_PATH` (**0.5 — chạy sau bước 0, trước sector-reference**) | tạo hồ sơ công ty (`equity_profile`: tên VI/EN) cho mỗi mã; `_PACKAGE_PATH` trỏ vào **file** `equity-profile.json`. Bắt buộc chạy trước sector-reference — sector-reference chỉ *gắn* ngành vào một hồ sơ đã tồn tại, không tự tạo hồ sơ |
| `FINVERA_STOCK_IMPORT_DAILY_BAR_ENABLED` / `_PACKAGE_PATH` | nạp lịch sử giá đầy đủ OHLCV (file `daily-bars-*.json`) |
| `FINVERA_STOCK_IMPORT_FUNDAMENTALS_ENABLED` / `_PACKAGE_PATH` | nạp báo cáo tài chính (file `fundamentals-*.json`) |
| `FINVERA_STOCK_IMPORT_SECTOR_REFERENCE_ENABLED` / `_PACKAGE_PATH` | nạp phân loại ngành, gắn vào `equity_profile.sector_reference_id` (file `sector-reference-*.json`) — mã nào chưa có `equity_profile` (bước 0.5) sẽ bị bỏ qua, đếm là `NO_EQUITY_PROFILE`, không lỗi |
| `FINVERA_STOCK_TECHNICAL_WARMUP_ENABLED` (**chạy sau khi đã có daily-bar, trước khi kỳ vọng trang Chiến lược có tín hiệu**) | tính trước MA/RSI/MACD/... cho mọi mã `LISTED` và lưu vào `technical_indicator_result` — trang Chiến lược (`StrategyScanService`/`StrategySignalService`) chỉ **đọc** bảng này, không tự tính; thiếu bước này thì mọi mã mới nạp sẽ báo `INSUFFICIENT_HISTORY` dù đã có đủ giá. "Giao cắt đường trung bình"/MACD/RSI cần đúng **2 phiên liên tiếp** để phát hiện điểm cắt — mỗi lần chạy, warmup tự tính bù **mọi phiên còn thiếu kể từ lần chạy trước** (không chỉ 2 phiên gần nhất), nên dù bạn nghỉ vài ngày hay vài tuần mới nạp giá + chạy lại, "hôm qua" vẫn luôn là phiên liền kề thật, không bị nhảy cách quãng |

Bước 0 và 0.5 chỉ tạo những dòng **chưa có sẵn** (mã/hồ sơ đã có bị bỏ qua,
không sửa/không tạo trùng) nên chạy lại bao nhiêu lần cũng an toàn — không bắt
buộc phải tắt lại `_ENABLED` sau đó, nhưng tắt đi cho log lần chạy sau gọn
hơn. Ba cặp biến còn lại dùng lần lượt, **mỗi lần chỉ bật một cặp**, chạy app
một lần để nạp rồi tắt lại — không phải cấu hình chạy thường trực.
`_PACKAGE_PATH` của ba mục cuối nhận **một file JSON, hoặc một thư mục** (tự
quét toàn bộ file đúng loại trong đó — dùng khi nạp nhiều mã cùng lúc từ
`export_all_symbols.py`, mục 6.3).

Nạp cả thư mục vẫn an toàn nếu một vài mã lỗi — importer bỏ qua file lỗi, ghi
log, và tiếp tục các file còn lại thay vì dừng cả batch. Xem lệnh export chi
tiết ở mục 6.3.

`FINVERA_STOCK_TECHNICAL_WARMUP_ENABLED` an toàn để bật lại bất cứ khi nào
(chỉ tạo bản ghi mới nếu kết quả tính ra thực sự khác bản đã lưu) — nên bật
lại mỗi khi vừa nạp thêm giá mới (crawl mã mới, hoặc cập nhật phiên gần đây)
để trang Chiến lược phản ánh đúng dữ liệu mới nhất.

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

### 6.2 Kích hoạt TCBS Thesis live overlay (Feature 001)

Sau khi backend chạy với `FINVERA_TCBS_LIVE_ENABLED=true` và
`FINVERA_TCBS_API_KEY` đã điền, đăng nhập tài khoản owner, mở
`/settings/live-data` và nhập TOTP hiện tại để gia hạn phiên. Hướng dẫn đầy đủ
nằm trong `docs/runbooks/private-market-overview.md`. TCBS giới hạn token tối
đa 8 giờ, nên bạn sẽ cần lặp lại bước này khi phiên hết hạn. API key và token
chỉ nằm ở backend; frontend không nhận hai giá trị này.

### 6.3 Nạp dữ liệu lịch sử Vnstock (Feature 002)

Thư mục `tools/market-data/vnstock-export/` dùng chung môi trường Python với
`provider-poc` (không có `pyproject.toml`/venv riêng) — luôn chạy với
`--project ../provider-poc`.

Luồng chuẩn cho local end-of-day refresh là chạy từ root:

```powershell
cd D:\Finvera
.\refresh-data.ps1
```

Script này tự động export và import cả gói market overview ổn định
`market-overview.json`, nên `index_snapshot` của `VN_INDEX`, `VN30`,
`HNX_INDEX`, và `UPCOM_INDEX` được nạp cùng daily bars, fundamentals và
technical warmup. Script cũng nạp `instrument-reference`, `equity-profile` và
`sector-reference` theo đúng thứ tự phụ thuộc, nên sau khi tạo DB local mới,
chỉ cần `refresh-data.ps1` là đủ dữ liệu nền cho toàn bộ flow phân tích. Mặc
định script chạy incremental: market index và nến giá chỉ
tải lại vùng `-LookbackDays` gần nhất cộng phần ngày mới, rồi merge với file cũ.
Chỉ khi truyền `-FullRefresh` nó mới tải lại toàn bộ range từ đầu; nên dùng định
kỳ, ví dụ cuối tháng, để bắt các correction/corporate-action cũ hơn lookback.

```powershell
cd D:\Finvera
.\refresh-data.ps1                 # incremental mặc định, lookback 90 ngày
.\refresh-data.ps1 -LookbackDays 30 # incremental hẹp hơn
.\refresh-data.ps1 -FullRefresh     # tải lại full range cho index + nến giá
.\refresh-data.ps1 -Cleanup         # sau refresh, dọn audit/revision cũ không còn được dùng
.\refresh-data.ps1 -CleanupOnly     # chỉ dọn retention, không crawl/import/warmup
```

`-Cleanup` là tùy chọn riêng cho local/private. Nó chỉ xóa audit cũ,
observations LIVE quá retention, và các revision derived `is_current=false`
không còn được input nào tham chiếu. Nó không xóa daily bar/index/fundamental
lịch sử đang current, nên không làm mất chuỗi dữ liệu cần cho MA/RSI/MACD,
valuation, breadth, regime, chart, hoặc import idempotency.
Nếu vừa refresh xong rồi mới muốn dọn, dùng `-CleanupOnly`.

Sau remediation ngày 2026-08-25 về đơn vị giá cổ phiếu, nếu đã reset local DB
thì chỉ cần tạo lại database `finvera` rồi chạy trực tiếp `.\refresh-data.ps1`.
Script tự khởi động backend ở từng stage, nên Flyway sẽ tạo schema sạch trong
stage đầu tiên; không cần chạy backend bình thường trước. Script nạp cả danh
mục mã, hồ sơ công ty, phân loại ngành, index, daily bars, fundamentals và tính
lại derived data. Daily-bar exporter đã bump `toolVersion` lên
`0.4.0`, nên checkpoint cũ của nến giá sẽ không bị skip: Vnstock/KBS sẽ được
export lại theo đơn vị canonical `VND/share`, import vào DB sạch, sau đó
technical warmup và valuation warmup tính lại từ dữ liệu sạch.

Nếu chạy thủ công từng bước, bắt buộc tạo riêng gói index:

```powershell
cd tools/market-data/vnstock-export
uv run --project ../provider-poc python export_history.py --market-overview --start 2024-01-01 --end 2026-08-24
```

Sau đó bật `FINVERA_MARKET_IMPORT_ENABLED=true`, trỏ
`FINVERA_MARKET_IMPORT_PACKAGE_PATH` vào file `output/market-overview.json`,
rồi khởi động backend một lần để import. Nếu bỏ qua bước này, equity bars vẫn
có thể đã được nạp nhưng `index_snapshot` sẽ rỗng; regime v2 sẽ bị giữ lại với
`TREND_COMPONENT_UNAVAILABLE`.

**Bước 0 — đăng ký danh mục mã (chạy một lần, trước mọi thứ khác):**

```powershell
cd tools/market-data/vnstock-export
uv run --project ../provider-poc python export_instrument_reference.py
```

Ra file `output/instrument-reference.json` (toàn bộ ~1525 mã HOSE+HNX+UPCOM).
Trỏ `FINVERA_MARKET_IMPORT_INSTRUMENT_REFERENCE_PACKAGE_PATH` vào đúng file
này (xem mục 3.7), bật `_ENABLED=true`, khởi động lại backend một lần. Kiểm
tra thành công bằng cách xem số dòng bảng `market_instrument` tăng lên (từ vài
mã demo lên gần 1525). **Chỉ sau bước này** các lệnh nạp giá/tài chính bên
dưới mới không bị từ chối `UNKNOWN_INSTRUMENT`.

**Bước 0.5 — tạo hồ sơ công ty (chạy sau bước 0, trước sector-reference):**

```powershell
uv run --project ../provider-poc python export_equity_profile.py
```

Ra file `output/equity-profile.json` (tên công ty VI/EN cho toàn bộ mã, lấy từ
cùng nguồn niêm yết đã dùng ở bước 0). Trỏ
`FINVERA_STOCK_IMPORT_EQUITY_PROFILE_PACKAGE_PATH` vào file này, bật
`_ENABLED=true`, khởi động lại backend một lần. **Bắt buộc chạy trước** khi
nạp phân loại ngành ở mục 3.7 — nếu chưa có bước này, việc gắn ngành sẽ báo
`NO_EQUITY_PROFILE` cho mọi mã (không lỗi, chỉ là không gắn được gì).

**Sau đó — lấy toàn bộ thị trường bằng một lệnh**, tự dừng khi xong,
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
phiên giao dịch mới sau này, chạy lại **đúng lệnh cũ** (không đổi gì) — script
chỉ tải bổ sung phần còn thiếu (và tự động tải lại 90 ngày gần nhất để dò
trường hợp công ty chia cổ tức/tách cổ phiếu làm giá lịch sử gần đây bị điều
chỉnh hồi tố — xem `--lookback-days`), **không tải lại toàn bộ từ đầu** mỗi
lần chạy. Muốn ép tải lại toàn bộ (ví dụ định kỳ hàng tháng, để dò các đợt
điều chỉnh cũ hơn 90 ngày): thêm `--full-refresh`.

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
| TCBS live thật | Sau khi gia hạn TOTP, log xác nhận WebSocket đã xác thực và nhận frame; `GET /api/v1/market/overview` trả quan sát mới có nguồn TCBS, không phải chỉ còn fixture |
| Giá real-time từng mã | Mở trang chi tiết một mã bất kỳ, giá phải khớp bảng giá thật (chỉ khi `FINVERA_STOCK_QUOTE_LIVE_ENABLED=true`) |
| Dữ liệu lịch sử/báo cáo tài chính | Sau khi import, biểu đồ/báo cáo hiển thị đúng số liệu thật của mã đó, không phải "no data" |
| AI Analyst / RAG hoạt động | Hỏi AI Analyst một câu — nếu `GEMINI_API_KEY` còn là placeholder sẽ báo lỗi rõ ràng (401/invalid key) thay vì trả lời |
| Qdrant có dữ liệu | `curl http://localhost:6333/collections/research_chunks_v1` trả `points_count` > 0 sau khi ingest ít nhất 1 tài liệu |

---

## 8. Xử lý sự cố nhanh

| Triệu chứng | Nguyên nhân thường gặp |
|---|---|
| Gia hạn TCBS luôn trả `PROVIDER_AUTH_REQUIRED` | `FINVERA_TCBS_LIVE_ENABLED=false`, `FINVERA_TCBS_API_KEY` để trống, TOTP hết hạn/sai, hoặc phiên WebSocket chưa xác thực thành công |
| Giá live từng mã không lên | `FINVERA_STOCK_QUOTE_LIVE_ENABLED=false`, `FINVERA_TCBS_LIVE_ENABLED=false`, mã chưa có trong reference data active, hoặc phiên TCBS chưa được gia hạn |
| AI Analyst báo lỗi 401/invalid key | `GEMINI_API_KEY` vẫn là placeholder `your-g...` |
| RAG không tìm thấy tài liệu nào | Qdrant chưa chạy, hoặc chưa ingest tài liệu nào qua Feature 006 |
| Backend không gọi được `finvera-ai` | `INTERNAL_API_KEY` hai bên không khớp nhau, hoặc `finvera-ai` chưa chạy ở port 8000 |
| Import Vnstock không thấy dữ liệu mới | Quên bật `*_ENABLED=true` + trỏ đúng `*_PACKAGE_PATH`, hoặc quên khởi động lại backend sau khi đổi `.env` |
