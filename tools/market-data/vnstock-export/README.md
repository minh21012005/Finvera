# Local Vnstock history exporter

## Refresh tự phục hồi — bản thu gọn

Chạy từ repository root như trước: `.\refresh-data.ps1`. Thay đổi có hiệu lực
ở lần khởi chạy kế tiếp; process đang chạy không tự nạp code mới.

Chủ động chọn ngày kết thúc cho giá cổ phiếu và chỉ số:

```powershell
.\refresh-data.ps1 -EndDate 2026-09-09
```

`-EndDate` nhận `yyyy-MM-dd`, từ 2019-01-01; bỏ cờ thì mặc định hôm nay theo
giờ Việt Nam. Ngày này được truyền đến `--end` của hai exporter và tham gia
runKey để resume đúng phạm vi. Không tự lùi ngày nghỉ hoặc xác nhận provider
đã công bố đủ dữ liệu. Cùng ngày đã xong vẫn skip; cần tải lại dùng
`-FullRefresh -EndDate 2026-09-09`. Đây là ngày kết thúc yêu cầu crawl, không
xóa dữ liệu mới hơn đã có trong DB hay biến BCTC/hồ sơ thành snapshot lịch sử.

- Mỗi lời gọi adapter SDK chỉ **1 attempt**, không retry nhanh bên trong SDK.
  Queue thử lại lỗi mạng/quota; tổng tối đa **3 lần** cho lời gọi lỗi liên tục.
  Timeout và lớp HTTP của vnstock giữ nguyên.
- Dataset lỗi nhường lượt cho phần khác, tự quay lại sau **2 phút**, tối đa
  **3 lượt/đợt** (lượt đầu + 2 lượt quay lại). Hết giới hạn thì ghi nhận thiếu
  và đi tiếp, không chờ vô hạn. Đợt refresh sau tự thử lại với ngân sách mới;
  resume đợt bị gián đoạn giữ số lượt đã dùng.
  Không circuit breaker, request thăm dò hay cooldown tăng tới 30 phút.
- Giữ checkpoint, skip phần thành công và lỗi thiếu dữ liệu chưa đến hạn;
  ngày kết thúc cố định theo Việt Nam. Full-refresh resume cùng đợt kiểm tra
  package trước khi bỏ qua phần đã thành công.
- Mặc định pace 40 **attempts SDK/phút**, vẫn giữ quota vnai. Đây không phải
  số HTTP chính xác vì constructor SDK có request phụ. Cờ
  `--max-calls-per-minute` giữ tên để tương thích; `--requests-per-minute`
  cũ không điều khiển pacing.
- Riêng hồ sơ: mặc định **5 worker**, pacing SDK chung **30/phút** qua
  `--requests-per-minute`; không cộng sleep sau từng mã. `--workers` điều chỉnh
  mức đồng thời. Retry vẫn tối đa 3 lượt/mã.
- Cache hồ sơ dùng `profile-fetch-cache.json` để giữ thời điểm fetch thật của
  từng mã, đối chiếu giá trị với package. Ghi package lại không gia hạn tuổi
  30 ngày. Lần đầu nâng cấp phải fetch lại hồ sơ chưa có provenance này;
  sidecar không được importer đọc như package. Mất/hỏng cache thì fetch lại.
- HTTP 403/404 của dataset được ghi lỗi rồi xử lý mã khác; lỗi ghi đĩa vẫn
  dừng và báo rõ. Không retry HTTP 4xx ngoại trừ 408/429.
- Heartbeat tối đa 30 giây. Chuyển sang import sau khi xử lý xong danh sách,
  kể cả còn dataset lỗi mạng đã hết giới hạn. File giá/BCTC cũ được giữ khi
  fetch thất bại. `COMPLETE` có đủ dữ liệu; `PARTIAL` có phần thiếu/lỗi mạng/schema
  lỗi. Thiếu BCTC/phiên được kiểm tra lại sau 35/7 ngày, không chờ hàng tuần.
  `symbols=x/y symbols_remaining=z` đếm mã đã xử lý/tổng mã/còn lại;
  `datasets=a/b` đếm dataset đã xử lý/tổng. Xử lý xong gồm cả phần thiếu/lỗi
  đã hết lượt, không có nghĩa đủ dữ liệu. Mã chờ retry vẫn thuộc phần còn lại.
- Bootstrap bắt buộc cũng tối đa 3 lượt, hết giới hạn báo thất bại rõ ràng vì
  chưa có prerequisite để tiếp tục an toàn. Hồ sơ từng mã hết giới hạn được
  ghi thiếu và không cache giá trị thiếu cho đợt sau. Ctrl+C hủy
  được. Lỗi ổ đĩa/cấu hình/schema bootstrap vẫn được báo rõ. Không có tiến
  trình tự khởi động lại sau khi máy tắt hoặc người vận hành hủy.
- Không sửa site-packages, không thay requests.Session, không nâng SDK.
  Không chạy hai refresh đồng thời trên cùng output.

Số lượt hữu hạn; thời gian vẫn phụ thuộc số mã và latency SDK. Import/backend
giữ stage retry cũ. Không cần `--retry-failed` cho lỗi mạng.

Kiểm thử từ thư mục exporter:

```powershell
uv run --project ../provider-poc python -m pytest tests -q
```

This is a manual, local-only tool under the owner-approved exception in the
feature contract. It never writes to PostgreSQL. Do not commit its `output/`.

## Market overview package

Generate the four-index market package used by Feature 001:

```powershell
uv run --project ..\provider-poc python .\export_history.py `
  --market-overview `
  --start 2024-01-01 `
  --end 2026-08-24 `
  --output .\output
```

The output contract is `vnstock-market-private-package-v1`. Spring imports it
through `FINVERA_MARKET_IMPORT_PACKAGE_PATH`; the browser never calls Vnstock
directly.

## Single-equity history package

```powershell
uv run --project ..\provider-poc python .\export_history.py --symbol VNM --venue HOSE --start 2025-01-01 --end 2026-08-14
```

The exporter refuses packages with fewer than 271 daily rows. Review the output
locally before enabling the Spring internal import boundary. It is not approved
for public, remote, multi-user, scheduled, or redistributed use.
