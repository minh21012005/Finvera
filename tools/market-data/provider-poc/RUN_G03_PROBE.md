# Hướng dẫn chạy probe G-03 (giá real-time từng mã cổ phiếu — TCBS)

Đây là bước duy nhất còn lại mà chỉ bạn (chủ sở hữu tài khoản TCBS) mới làm được —
Claude không có tài khoản TCBS và không thể tự nhập OTP thay bạn. File này hướng
dẫn từng bước để bạn chạy đúng 1 lần (hoặc vài lần nếu lỗi) và xong.

## Vì sao cần bước này

Feature 002 (chi tiết mã cổ phiếu) cần lấy **giá real-time của từng mã riêng lẻ**
từ TCBS. Endpoint dùng lại đúng API đã được duyệt cho Feature 001
(`tickerCommons`), chỉ khác tham số (`tickers=` thay vì `index=`) — theo tài
liệu chính thức của TCBS. Nhưng theo quy trình của dự án (xem
`specs/002-stock-detail-analysis/research.md` mục R-012, gate G-03), trước khi
viết code chạy live, cần có **bằng chứng thật** rằng endpoint này trả về đúng
dữ liệu (giá khớp lệnh, giá tham chiếu, khối lượng) cho một mã cổ phiếu cụ thể,
với chi phí request phù hợp — không được đoán mò dữ liệu tài chính thật.

## Điều kiện cần trước khi chạy

- Có tài khoản TCBS đã đăng ký **TCBS OpenAPI / iFlash** (không phải tài khoản
  chứng khoán thông thường — cần API key riêng).
- App TCInvest trên điện thoại (để lấy mã OTP/TOTP hiện tại), **hoặc** tài
  khoản đã đăng ký nhận OTP qua email/SMS.
- Máy đã cài PowerShell và [`uv`](https://docs.astral.sh/uv/) (nếu chưa có
  `uv`, cài theo hướng dẫn tại link trên).

## Cách chạy (khuyến nghị — dùng script tự động)

Mở PowerShell tại thư mục gốc dự án, chạy:

```powershell
cd tools/market-data/provider-poc
.\run-g03-probe.ps1
```

Script sẽ:
1. Tự kiểm tra/cài môi trường Python (`uv sync`) nếu cần.
2. Chạy probe với 3 mã mặc định (`VNM,TCB,HPG`) — đủ để chứng minh endpoint
   hoạt động đúng, không cần quét toàn bộ thị trường.
3. Hỏi bạn nhập **API key TCBS** (ẩn khi gõ) rồi **mã OTP hiện tại** (ẩn khi gõ).
4. Kiểm tra kết quả; nếu thành công sẽ báo rõ và dừng. Nếu chưa thành công sẽ
   hỏi bạn có muốn thử lại (nhập OTP mới) hay không — tối đa 3 lần.

Muốn đổi mã cổ phiếu hoặc kiểu OTP:

```powershell
.\run-g03-probe.ps1 -QuoteSymbols "VNM,VCB" -OtpMethod email-sms
```

### Nếu bạn muốn chạy trực tiếp (không dùng script)

```powershell
cd tools/market-data/provider-poc
uv run python poc_tcbs.py --quote-symbols VNM,TCB,HPG
```

Nếu tài khoản của bạn dùng OTP qua email/SMS thay vì app TCInvest:

```powershell
uv run python poc_tcbs.py --otp-method email-sms --quote-symbols VNM,TCB,HPG
```

## Lưu ý quan trọng

- **Không có cách nào bỏ qua bước nhập OTP.** Đây là thiết kế bảo mật cố ý của
  chính công cụ này (đọc `README.md` cùng thư mục) — không phải giới hạn của
  Claude.
- **Chạy trong giờ giao dịch** (9:00–15:00 các ngày thường, giờ Việt Nam) sẽ
  cho kết quả đáng tin cậy nhất về giá khớp lệnh đang biến động. Ngoài giờ vẫn
  có thể chạy được (giá tham chiếu/đóng cửa gần nhất), nhưng phần WebSocket
  (không liên quan đến G-03) có thể báo lỗi timeout — **không sao cả**, script
  đã tự bỏ qua phần đó khi đánh giá thành công.
- **Không bao giờ dán API key hoặc mã OTP vào chat với Claude** — không cần
  thiết. File kết quả (`poc-output/tcbs-capability-summary.json`) được thiết
  kế sẵn để **không chứa** key/token/OTP/giá thị trường thật, chỉ chứa tên
  trường dữ liệu, kiểu dữ liệu, và trạng thái PASS/FAIL.

## Sau khi chạy xong

Kiểm tra nhanh: mở `poc-output/tcbs-capability-summary.json`, tìm mục
`rest.ticker_commons_quote_symbols.status` — nếu là `"PASS"` là xong.

Bạn **không cần** copy nội dung file này vào chat. Chỉ cần báo Claude:

> "Đã chạy xong probe G-03, thành công."

Claude sẽ tự đọc file kết quả (đã có sẵn quyền truy cập file hệ thống), ghi
nhận bằng chứng vào `research.md` R-012 G-03, và viết tiếp
`TcbsStockQuoteProvider` (adapter giá real-time từng mã) dựa trên bằng chứng
đó.

Nếu chạy không thành công sau vài lần thử, cứ báo Claude kèm mô tả lỗi hiển thị
trên màn hình (không cần file/OTP/key) — Claude sẽ giúp chẩn đoán.
