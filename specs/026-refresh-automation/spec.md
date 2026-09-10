# Feature 026: Refresh that finishes by itself

## Ngày kết thúc do người chạy chọn — đã duyệt

FR-021: `refresh-data.ps1 -EndDate YYYY-MM-DD` nhận ngày kết thúc để truyền
cùng giá trị xuống `--end` của giá cổ phiếu và chỉ số. Không truyền cờ thì
giữ mặc định hôm nay theo giờ Việt Nam. Ngày sai định dạng/không tồn tại hoặc
trước 2019-01-01 bị từ chối trước crawl. Resume dùng ngày hiệu lực này.
Không tự đổi cuối tuần/ngày nghỉ hay kiểm tra dữ liệu final của provider.
Các đề xuất FR-017–020/DATA-002/NFR-006 bị hủy theo yêu cầu chủ sở hữu;
không triển khai calendar, grace, publication pending hoặc đối soát tự động.
Nghiệm thu: ngày rõ ràng đến đúng hai exporter; bỏ cờ giữ mặc định; ngày
khác làm runKey khác; cùng ngày giữ skip/resume hiện tại.

FR-016: heartbeat crawl hiển thị số mã đã xử lý/tổng mã trong phạm vi đợt,
số mã còn lại và tiến độ dataset. Mã còn dataset chạy/chờ retry chưa tính
xử lý xong; mã hết ngân sách được tính xong nhưng giữ thống kê thiếu/lỗi.

## Sửa sau rà soát được duyệt — 2026-09-10

FR-009/FR-014: HTTP 4xx không retry (trừ 408/429) phải được ghi nhận riêng
cho dataset, không làm dừng queue; lỗi filesystem vẫn phải báo thất bại.
FR-015: tuổi cache shares tính từ lúc fetch thành công từng mã, không từ lúc
đóng gói lại; thiếu provenance thì fetch lại một lần. Không đổi hợp đồng import.
NFR-005: hồ sơ mặc định 5 worker, giới hạn theo --workers; dùng pacing SDK, không sleep
riêng từng mã. Mỗi mã tối đa 3 lượt; không tạo task trùng cho mã trùng universe.
Nghiệm thu: 403/404 không dừng mã khác; PermissionError vẫn thoát; đóng gói
lại không gia hạn cache; kiểm tra overlap worker và không vượt số worker.

## Giới hạn thời gian chờ được duyệt — 2026-09-10

Thay thế yêu cầu chờ tới khi phục hồi ở FR-009/FR-014: mỗi dataset tối đa
3 lượt trong một đợt (lượt đầu + 2 lượt quay lại, nghỉ 120 giây). Theo yêu cầu
mới nhất, SDK chỉ gọi 1 lần mỗi lượt, không retry lồng thành 6 lần.
Hết ngân sách ghi lỗi transient còn thiếu, kết thúc
PARTIAL và chuyển import phần có sẵn; không biến lỗi mạng thành lỗi vĩnh viễn.
Đợt refresh kế tiếp tự mở ngân sách mới, không cần --retry-failed. Resume
đợt bị gián đoạn giữ số lượt đã dùng. Bootstrap bắt buộc cũng tối đa 3 lượt;
không lấy được prerequisite thì báo thất bại rõ ràng, không import giả thành công.
Hồ sơ công ty từng mã hết ngân sách được đánh dấu thiếu và tiếp tục mã khác.
Nghiệm thu: outage vĩnh viễn kết thúc, đúng 3 lượt, giữ file cũ, lần refresh
sau tự thử lại; bootstrap và profile không chờ vô hạn.

## Thu gọn được chủ sở hữu duyệt — 2026-09-10

Thay thế phần thiết kế phức tạp bên dưới: giữ queue/checkpoint cấp dataset,
skip phần thành công, ngày cố định và bootstrap tự chờ. Mỗi lời gọi adapter
SDK chỉ tối đa hai attempts, không retry nhanh thêm ở exporter. Phần lỗi tự
thử lại sau 2 phút; không backoff 30 phút, circuit hay request thăm dò.
**FR-011 được rút khỏi phạm vi**. **FR-010** dùng lịch cố định 2 phút.
**NFR-004 được sửa** thành pacing attempts tại adapter SDK và giữ quota vnai;
không tuyên bố giới hạn chính xác số HTTP request (constructor SDK có gọi phụ).
Không thay requests.Session, timeout, redirects hoặc xử lý response của SDK.

## Bổ sung được chủ sở hữu duyệt — 2026-09-10

Các yêu cầu dưới đây thay thế hành vi dừng sau lỗi mạng tại FR-002,
SC-1/SC-5 và kịch bản 1–2 cũ. Không thay đổi hợp đồng dữ liệu.

- **FR-009** Refresh MUST tự chờ và thử lại dataset lỗi mạng/429/5xx cho đến
  khi nguồn phục hồi hoặc người vận hành hủy; không yêu cầu chạy lại lệnh.
- **FR-010** Mỗi request đọc MUST có tối đa hai lần thử thực tế trong một lượt;
  hết lượt thì nhường công việc khác và lên lịch lại 2/5/15/30 phút, có jitter.
- **FR-011** Endpoint lỗi hàng loạt MUST có cooldown và chỉ một request thăm dò
  khi hết cooldown; process vẫn sống và công việc độc lập vẫn tiến triển.
- **FR-012** Một quy tắc cấp dataset MUST quyết định cả skip và hoàn tất: không
  gọi lại phần đã thành công trong đợt, kể cả full refresh, hoặc lỗi chưa đến
  hạn kiểm tra lại. Schema lỗi được ghi nhận riêng, không retry vô hạn.
- **FR-013** Checkpoint MUST lưu lịch thử lại, số lượt, kết quả theo mã/dataset
  và cửa sổ ngày cố định. Chạy qua nửa đêm không đổi ngày kết thúc đợt.
- **FR-014** MUST báo heartbeat, số dataset thành công/chờ/thiếu/lỗi và chỉ
  chuyển sang import khi không còn lỗi mạng tồn đọng trong phạm vi đã chọn.
- **NFR-004** MUST giới hạn HTTP request thực tế, gồm handshake và retry,
  đồng thời giữ nguyên kiểm tra quota của SDK.

Kịch bản nghiệm thu: một mã timeout không chặn mã khác; nguồn lỗi hơn hai
lượt rồi phục hồi tự hoàn tất; một probe duy nhất sau cooldown; restart đọc
đúng lịch retry; full refresh không tải lại phần đã thành công trong retry;
BCTC thiếu còn hạn không bị gọi lại khi giá cũ; ngày kết thúc giữ nguyên qua
nửa đêm; bootstrap endpoint lỗi rồi phục hồi không làm mất process refresh.
Không hứa thời gian hoàn tất nếu provider không phục hồi. Lỗi ổ đĩa, cấu hình,
schema bootstrap hoặc lỗi lập trình vẫn phải được báo thay vì lặp vô hạn.

**Status**: Specified 2026-09-06 · **Amended 2026-09-07** (FR-007/SC-6 + FR-008/SC-7, research R-008/R-009: a failure
class that changes with time was being settled forever — Q-61; and "finished" required a bar dated
`--end`, which a stopped symbol can never have — Q-62)
**Closes**: docs/REMEDIATION_PLAN.md **P2-12**; the owner's requirement that a refresh "must run
through, or pick itself back up, without me restarting it"
**SRS References**: SRS-NFR-07 (operability) · **Measured**: during the owner's 2026-09-06 refresh

## Problem

A full refresh is seven stages over roughly six hours. Two things are wrong with it.

**It can die in the first minute, and then nothing has happened.** `refresh-data.ps1` runs with
`$ErrorActionPreference = "Stop"` and calls `Assert-NativeSuccess` after each exporter, so any
non-zero exit aborts the whole script. Stage 1 makes **at least seven provider calls with no retry
at all**:

| File | Call | Protected |
|---|---|---|
| `export_instrument_reference.py:40` | `Listing(vci).symbols_by_exchange()` | no |
| `export_sector_reference_vci.py:182` | `symbols_by_industries()`, `industries_icb()`, `symbols_by_exchange()` | no |
| `export_equity_profile.py:48,59` | two universe calls | no (its per-symbol loop *is* protected) |
| `export_history.py` | index history for four indexes | no |
| `export_all_symbols.py:75` | `fetch_symbol_universe()` | no |

The irony is exact: `export_all_symbols.py`, the stage that takes hours, is the only one with a
checkpoint, a transient/settled failure classification and a retry ladder. The seven short calls
that gate it have none — and the provider times out several times a minute
(`trading.vietcap.com.vn`, 30 s read timeout). The 2026-09-06 run survived stage 1 by luck.

**Nothing resumes.** Stages 2–7 keep no record of what completed. A failure in stage 7 means
re-running stages 2–7; the imports are idempotent so nothing corrupts, but hours are re-spent. And
the wait is blind: stage 6a's timeout is 21,600 s, so a backend that hangs without dying is waited
on for six hours before the script gives up.

Separately, the run is slower than it needs to be: measured mid-run, **7.3 provider calls per
minute against a 60/min quota — 12 % of the allowance** — because symbols are fetched strictly one
at a time, so every round-trip and every 30 s timeout is paid in series (~18 s per symbol,
~199 symbols/hour).

## Scope

In scope: the resilience and pacing of the refresh pipeline — retry coverage on provider calls,
stage-level resume and retry in the orchestrator, stall detection, and concurrency in the
universe exporter.

Out of scope, deliberately: what any exporter *fetches* or *writes* (no package contract changes),
the import/warmup logic in the backend, and P2-04's import incrementality (still unmeasured).

## Requirements

- **FR-001** Every provider call in the export stage MUST retry transient network failures using
  one shared classification, and MUST NOT retry a genuine data/schema failure.
- **FR-002** A transient failure that survives all retries MUST fail that exporter with a message
  naming the call, so the cause is never guessed from an exit code alone.
- **FR-003** The orchestrator MUST record each completed stage and, on a re-run of the same refresh,
  skip the stages already completed.
- **FR-004** A stage that fails MUST be retried automatically before the run is abandoned, and the
  number of attempts MUST be visible in the output.
- **FR-005** The orchestrator MUST detect a stalled stage — no new log output for a bounded period —
  and fail it then, rather than waiting out the full timeout.
- **FR-006** Resume MUST NOT silently reuse stale state: state older than a bounded age, or from a
  run with different parameters, starts fresh, and the decision is printed.
- **NFR-001** Following a stage's log MUST cost time proportional to new output, not to the whole
  log re-read on every poll.
- **NFR-002** The universe exporter MUST fetch several symbols concurrently while keeping the
  provider call rate under the quota, and MUST NOT rely on the provider library's own counter for
  that guarantee (its usage counters are mutated from several threads without a lock).
- **NFR-003** Concurrency MUST NOT change what is written: the same packages, the same checkpoint
  content, and a checkpoint that is never observed half-written.
- **DATA-001** Retry and concurrency MUST NOT weaken the existing failure classification: a symbol
  whose dataset genuinely fails is still recorded as failed, scoped to the exporter version.
- **FR-007** A failure whose cause is a **state that changes with time** — the provider has no
  statements yet, the symbol has not traded enough sessions yet — MUST be recorded under its own
  name and re-checked after a bounded window sized to that cause, never settled as permanent. A
  failure that no amount of waiting can change MUST still settle.
- **FR-008** A dataset counts as covered for a run when it was **fetched for that run's window**,
  evidenced by the package on disk, not by whether the market happened to trade that symbol on the
  window's end date. A symbol that has stopped trading MUST therefore be able to reach a finished
  state, so that re-running the same command converges instead of repeating work forever.

## Success criteria

- **SC-1** Every provider call listed in the table above is covered by the shared retry, proven by a
  unit test per exporter that makes the provider raise a timeout twice and then succeed.
- **SC-2** Killing a stage mid-run and re-running the script resumes at that stage; the output names
  the skipped stages.
- **SC-3** A stage whose backend produces no output for the stall window fails within that window
  rather than at the full timeout.
- **SC-4** A full crawl of the 1,522-symbol universe completes materially faster than the measured
  199 symbols/hour, with the same package and checkpoint content as a serial run for a sampled
  subset, and a measured call rate under the quota.
- **SC-5** Exporter suite green; the crawl's own resilience tests cover a timeout that recovers and
  one that settles as failed.
- **SC-6** A symbol below the daily-bar minimum-session threshold is recorded under its own failure
  name with the session count it actually had, is not `finished` once its window has passed, and is
  still `finished` inside it — proven by tests at the threshold boundary and at both ends of the
  window.
- **SC-7** A symbol whose newest session predates the run's end date counts as covered once it has
  been fetched for that window, while a package that does not carry the window, or carries no
  sessions, still does not — and moving the end date still makes the entry stale. Proven by tests
  and by replaying `is_finished` over the real checkpoint before and after.

## Acceptance scenarios

1. **Given** the provider times out on `symbols_by_exchange()` twice and then answers, **when**
   stage 1 runs, **then** the exporter waits, retries, succeeds, and the refresh continues.
2. **Given** the provider is down for the whole retry ladder, **when** stage 1 runs, **then** the
   exporter exits non-zero with a message naming the call, and re-running the script later starts
   from stage 1 without having damaged anything.
3. **Given** stage 6a failed and the script was re-run within the resume window, **when** it starts,
   **then** stages 2–5 are reported skipped and the run begins at 6a.
4. **Given** a backend that starts but emits nothing for the stall window, **when** its stage runs,
   **then** the stage fails at the stall window and is retried, not waited on for six hours.
5. **Given** a symbol listed too recently to have the minimum number of sessions, **when** the crawl
   reaches it, **then** its dataset is recorded as "not enough sessions yet" with the count it had,
   the crawl continues, and a run after the re-check window asks the provider again — so the symbol
   joins the product on its own once it has traded enough, without anyone passing `--retry-failed`.
6. **Given** a symbol that stopped trading years ago but whose package was fetched for this run's
   window, **when** the same command is run again, **then** the symbol is reported as finished and
   is not fetched a second time — and when the end date moves to a new day, it is fetched again.
