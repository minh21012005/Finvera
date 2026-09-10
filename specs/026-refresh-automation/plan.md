# Plan: Feature 026 — Refresh that finishes by itself

FR-021 thay thế kế hoạch tự chọn phiên đã hủy: thêm tham số string `-EndDate`
và hàm Resolve-HistoryEndDate vào `refresh-data.ps1`. ParseExact invariant
yyyy-MM-dd và kiểm tra >= historyStartDate; mặc định ngày Việt Nam như cũ.
Hai exporter và Get-RunKey đã dùng historyEndDate nên dùng nguyên wiring.
Kiểm thử AST/hàm độc lập, không crawl/DB. Không thêm dependency, lịch hoặc
schema; Constitution về scope/resilience/provenance không đổi. Cùng ngày đã
done vẫn skip; -FullRefresh ép tải lại khi cần. Không biến import thành
snapshot lịch sử hoặc xóa dữ liệu mới hơn đã tồn tại.

FR-016: tính mã còn lại bằng tập symbol trong heap và running ở heartbeat;
không đọc lại package hay đổi checkpoint/retry. Tổng tính theo scope truyền
vào run_queue (hỗ trợ max-symbols). Terminal dùng cùng định dạng tiến độ.

## Sửa sau rà soát — ưu tiên hiện hành

Phân loại HTTP status SDK tách khỏi lỗi OSError filesystem trong
`export_all_symbols.py` và `provider_retry.py`. Không sửa SDK hay HTTP hook.
`profile-fetch-cache.json` là sidecar local, không khớp glob import equity-profile:
toolVersion và từng symbol gồm sharesOutstanding/qualityReason/fetchedAt.
Chỉ reuse nếu giá trị khớp package, ngày hợp lệ và chưa quá hạn. Sidecar cũ
không có thì fetch lại; ghi atomic sau package, không ảnh hưởng hợp đồng DB.
`collect_available` thêm workers với futures hữu hạn, coordinator sở hữu heap;
giữ retry 120 giây/3 lượt. Profile main cài SDK pacing theo cờ tốc độ (mặc định
30/min), launcher nhường cài policy cho main; bỏ sleep riêng. Kiểm thử ngoại
tuyến; không đổi process đang chạy, không tối ưu request BCTC trong đợt này.
Constitution: provenance rõ, không đổi API/schema tài chính, không dependency
mới; test lỗi/giới hạn đồng thời. Không cần ngoại lệ kiến trúc.

## Bổ sung hiện hành: ngân sách hữu hạn

Điều chỉnh mới nhất: adapter dùng `stop_after_attempt(1)` và `wait_none()`;
queue giữ 3 lượt. Lời gọi lỗi liên tục chỉ thực hiện tối đa 3 lần qua adapter.
Giữ pacing/quota; kiểm thử adapter thật với provider giả và queue kết hợp.
Quyết định này thay thế cấu hình 2 attempts SDK trong lịch sử bên dưới.

Chủ sở hữu yêu cầu tối ưu thời gian: dùng MAX_DATASET_ATTEMPTS=3 trong
provider_retry và queue. Sau lần thứ ba, bỏ lịch next_retry_at, giữ
failed:NetworkError/RateLimitExceeded và số lượt để báo PARTIAL. Resume
RUNNING/WAITING cùng cửa sổ giữ ngân sách; đợt sau COMPLETE/PARTIAL reset
ngân sách transient. Bootstrap hết ngân sách ném ProviderCallFailed;
profile từng mã trả None để dùng trạng thái thiếu hiện có. Không thêm service,
flag hoặc cơ chế mạng; timeout SDK không đổi. Test fake clock/always-down.

## Thiết kế thu gọn hiện hành — 2026-09-10

Chủ sở hữu duyệt bỏ circuit và hook HTTP. Xóa `provider_runtime.py`; gom
cấu hình retry vào `provider_retry.py`: đặt tenacity của adapter tối đa hai
attempts, chỉ retry lỗi transient, giữ decorator quota vnai và timeout SDK.
Callback `before` pace attempts chung giữa worker; không sửa requests hay
site-packages. Giữ launcher để cấu hình trước khi tạo worker và bật bootstrap
tự chờ. Queue cấp dataset giữ checkpoint/resume, chờ cố định 120 giây thay
backoff dài. Không tải lại phần thành công. Các phần dưới là lịch sử nếu
mâu thuẫn với thiết kế này. Constitution VII: từng lượt hữu hạn, chờ hủy được;
không đổi dữ liệu/quyền/DB. Test tập trung SDK budget, schema không retry,
không hook HTTP, recovery và resume; không benchmark live.

## Thiết kế bổ sung 2026-09-10 (thay thế retry/concurrency cũ khi mâu thuẫn)

- `provider_runtime.py`: wrapper requests.Session.request chỉ cho đường đọc
  VCI, pacing HTTP không burst, timeout connect/read 5/30 s, tối đa 2 attempts
  với 2 s giữa hai lần; 429 tôn trọng Retry-After. Circuit theo host, mở sau
  3 request đã cạn retry, cooldown 2/5/15/30 phút; một probe khi half-open.
  Vô hiệu retry tenacity tại adapter trong bộ nhớ trước khi tạo worker, giữ
  decorator vnai. HTTP 4xx không phải 408/429 là lỗi cần xử lý, không retry.
- `refresh_export.py`: entrypoint dùng runtime cho các exporter bootstrap;
  `provider_retry.call` chờ có heartbeat khi runtime bật; lỗi schema vẫn thoát.
- `export_all_symbols.py`: hàng đợi hữu hạn bộ nhớ gồm (mã,dataset), futures
  tối đa số worker. Lỗi transient được ghi `*_retry_attempts`, `*_next_retry_at`
  (UTC epoch seconds), `*_retry_window`; success xóa lịch. `runWindow` ghi
  start/end/period/full-refresh/lookback/unit-scale; `*_completed_window`
  cho phép resume cùng đợt khi package còn hợp lệ. `status` là
  RUNNING/WAITING/COMPLETE/PARTIAL. Trạng thái cũ
  không có lịch thì thử ngay. Không còn pass retry cuối trả 0 dù còn lỗi mạng.
  Những task thành công trong đợt không được thêm lại, kể cả --full-refresh.
- PowerShell truyền cùng --end cố định theo múi giờ Việt Nam cho toàn đợt;
  các bootstrap chạy qua launcher. Import/backend runner không đổi hợp đồng.
- Test fake clock/HTTP chứng minh giới hạn retry, circuit độc lập, tiếp tục
  sau outage, checkpoint resume, phân loại và terminal status; harness
  PowerShell kiểm tra ngày và launcher. Không benchmark outage live.

Constitution trước/sau thiết kế: I/II không đổi tính toán/provenance; III/IV
không đổi public API/quyền; V yêu cầu đã duyệt; VI contract+fault tests;
VII timeout/retry từng lượt hữu hạn, chờ hủy được và heartbeat; VIII không
thêm service/dependency. Không cần exception. Rollout lần refresh kế tiếp,
không thay code trong process đang chạy; rollback code vẫn đọc checkpoint
cũ vì trường mới additive, các JSON dữ liệu và DB không thay đổi.

## Thiết kế ban đầu 2026-09-06 (lịch sử; bổ sung trên có ưu tiên)

Three changes, in the order that reduces risk fastest: make the fragile calls survivable, then make
the orchestrator resumable, then make the long stage concurrent. Each is independently useful — if
only the first ships, the worst failure mode is already gone.

The retry classification is not re-invented: `export_all_symbols.py` already distinguishes a
transient network failure from a settled one, and that logic moves into a shared module so every
exporter uses the same definition rather than a second, drifting copy.

## Components

| # | Layer | Change |
|---|---|---|
| 1 | `tools/market-data/vnstock-export/provider_retry.py` (new) | `NETWORK_EXCEPTION_NAMES`, `NETWORK_MESSAGE_PATTERN`, `is_network_error`, and `call(label, fn)` which retries with the existing 5 s / 20 s ladder and raises a `ProviderCallFailed` naming the call when the ladder is exhausted. |
| 1 | `export_instrument_reference.py`, `export_sector_reference_vci.py`, `export_equity_profile.py`, `export_history.py`, `export_all_symbols.py` | Every bare provider call goes through `provider_retry.call`. `export_all_symbols.py` imports the classification from the shared module instead of defining it. |
| 2 | `refresh-data.ps1` | Stage state file (`output/refresh-state.json`): stage name, parameters hash, timestamp. Skip completed stages when the state matches and is < 12 h old, printing what is skipped. `Invoke-BackendStage` gains `-Attempts` (default 3) and a stall window (default 15 min of no new output). Log following reads from a byte offset instead of re-reading the file. |
| 3 | `export_all_symbols.py` | A worker pool over symbols (`--workers`, default 5) behind a token bucket (`--max-calls-per-minute`, default 40, below the provider's 60). Checkpoint writes take a lock and use atomic replace; progress printing takes a lock. The end-of-run transient retry pass keeps its current shape. |
| — | Tests | `tests/test_provider_retry.py`; a per-exporter test that a doubled timeout recovers; concurrency tests for the token bucket and for checkpoint atomicity. |
| — | Docs | `refresh-data.ps1` header; `tools/verification/README.md` untouched; REMEDIATION P2-12 closed; changelog. |

## Ordering and why

1 first because it removes the failure that wastes the most owner time for the least work. 2 second
because it turns any remaining failure into a resumable one. 3 last because it is the only change
that can alter *what* gets written if done carelessly, and it is worth the least if the run still
cannot finish unattended.

## Constitution check

- **I. Determinism** — concurrency changes timing, not content: per-symbol work is independent, the
  checkpoint is keyed by symbol, and the end-of-run pass iterates a list. A sampled serial-vs-parallel
  comparison is a success criterion (SC-4), not an assumption.
- **II. Provenance and honesty** — a retried call that finally fails still records a *settled*
  failure scoped to the exporter version; retry never converts a real failure into silence. Resume
  never reuses stale state silently (R-005).
- **IV. Responsible operation** — the rate limit is charged to the owner's account, so the guarantee
  is our own token bucket, not the provider library's unlocked counter (R-003).
- **VI. Risk-based testing** — a unit test per newly protected call, plus concurrency tests for the
  two shared resources.
- **VIII. Modular simplicity** — one small module, no new dependency, no new process model
  (threads, because the work is I/O-bound and the pacing must be shared in-process).
- Complexity tracking: none.

## Risks

- **Provider library thread-safety.** `vnstock`/`vnai` are not documented as thread-safe. Mitigation:
  threads only issue independent HTTP calls; our token bucket, not vnai's counter, enforces pacing;
  the worker count is configurable and defaults low (5). If a run shows library-level breakage,
  `--workers 1` restores exactly today's behaviour.
- **A resumed run hiding a real problem.** Bounded by the 12-hour window, the parameter match, and
  printing every skip.
- **Killing the backend on a retried stage.** `Invoke-BackendStage` already kills its process in
  `finally`; the retry loop must wait for the port to be free before the next attempt, or the next
  attempt fails on the port check.

## Rollout

Exporter and script changes are inert until the owner's next refresh. `--workers 1` and
`-Attempts 1` reproduce today's behaviour exactly, so the change can be backed out by flags without
a code revert.
