# Tasks: Feature 026 — Refresh that finishes by itself

## Bổ sung 2026-09-10

### Ngày kết thúc do người chạy chọn

T026–T030 bị hủy theo yêu cầu chủ sở hữu, thay bằng FR-021; không còn lịch
tự động, resolver phiên, đối soát hoặc migration lịch trong phạm vi này.

- [x] T031 FR-021: `refresh-data.ps1` nhận -EndDate; kiểm tra ngày, truyền
  giá trị qua historyEndDate đến hai exporter và runKey. Cập nhật README,
  kiểm chứng `tools/refresh-tests/test-crawl-contract.ps1` ngoại tuyến: PASS
  ngày chỉ định/mặc định/ngày sai, cả hai exporter và runKey; diff --check đạt.

### Các sửa đổi đã thực hiện

- [x] T025 FR-016: `export_all_symbols.py` thêm tiến độ mã/dataset vào log;
  `tests/test_refresh_queue.py` xác nhận mã chờ retry chưa được tính xong.
  Kiểm thử queue: **19 passed**, provider giả; git diff --check đạt.

- [x] T022 FR-009/FR-014: `provider_retry.py`, `export_all_symbols.py` phân biệt
  HTTP/filesystem; test trong `tests/test_refresh_queue.py`.
- [x] T023 FR-015: `export_equity_profile.py` sidecar tuổi dữ liệu thực;
  test trong `tests/test_export_equity_profile.py` cache hết hạn/thiếu/mismatch.
- [x] T024 NFR-005, phụ thuộc T022/T023: `provider_retry.py` queue hồ sơ mặc định
  5 worker; `export_equity_profile.py`, `refresh_export.py` dùng chung pacing;
  tests queue/concurrency và CLI, cập nhật README; **118 passed** toàn suite
  exporter qua uv offline; harness crawl contract PASS, git diff --check đạt.

- [x] T021 FR-009/FR-014, phụ thuộc T020: `provider_retry.py` bỏ retry SDK;
  `tests/test_sdk_retry.py` kiểm tra 1 attempt và queue cộng SDK tối đa 3 lần.
  Cập nhật README/PowerShell; suite exporter **111 passed**, harness crawl
  contract **PASS**. Provider giả xác nhận đúng 3 lần ở giây 0/120/240.

- [x] T020 FR-009/FR-014 sửa đổi, phụ thuộc T019: giới hạn 3 lượt/dataset
  tại `export_all_symbols.py` và `provider_retry.py`, kể cả bootstrap/profile.
  Giữ transient và file cũ, PARTIAL khi hết ngân sách; reset ở đợt kế tiếp,
  giữ ngân sách khi resume. Bỏ cache profile thiếu trong `export_equity_profile.py`.
  `tests/test_refresh_queue.py` kiểm thử outage vĩnh viễn, resume ngân sách,
  lần chạy kế tiếp và file cũ. Suite hiện hành **110 passed** qua uv offline;
  các kết quả bên dưới là lịch sử, yêu cầu retry vô hạn đã bị thay thế.

- [x] T019 FR-009/FR-010/FR-012–014, NFR-004 sửa đổi; phụ thuộc T018:
  thu gọn theo chủ sở hữu duyệt. Xóa `provider_runtime.py` và test circuit;
  gom retry SDK vào `provider_retry.py`, sửa exporter/launcher và tài liệu.
  `tests/test_sdk_retry.py` kiểm chứng 2 attempts qua adapter thật, schema/403
  không retry và không thay HTTP; giữ fault/resume/CLI tests.
  Kết quả hiện hành: **106 passed** qua uv (HTTP giả). Các kết quả 112 test
  bên dưới là lịch sử của bản có circuit; T015 đã được T019 thay thế.

Kết quả: `uv run --offline --frozen --project ../provider-poc python -m pytest
tests -q -p no:cacheprovider` từ thư mục exporter: **112 passed**.
`tools/refresh-tests/test-resume-state.ps1`, `test-stage-runner.ps1` và
`test-crawl-contract.ps1`: đều PASS (backend/HTTP giả, không tác động DB).
`git diff --check`: PASS. Sandbox Windows chặn thư mục tạm pytest; suite
đã chạy thành công ngoài sandbox theo cơ chế approval. Không thay lockfile.
SC-4 về tốc độ full crawl live vẫn chưa được đo lại; không suy diễn thời gian
hoàn tất từ các fault test. P1 tái sử dụng response BCTC/metadata để giảm request
không nằm trong đợt sửa P0 này.

- [x] T014 FR-009–FR-014/NFR-004: thiết kế và bằng chứng tại `spec.md`,
  `research.md`, `plan.md` (trước T015).
- [x] T015 FR-010/FR-011/NFR-004: `tools/market-data/vnstock-export/provider_runtime.py`
  và `tests/test_provider_runtime.py`: HTTP contract, 2 attempts, circuit,
  quota thật, không retry 4xx; phụ thuộc T014.
- [x] T016 FR-009/FR-012/FR-013/FR-014: `export_all_symbols.py` và
  `tests/test_refresh_queue.py`: queue, resume, thiếu dữ liệu, full refresh,
  recovery tự hoàn tất; phụ thuộc T015.
- [x] T017 FR-009/FR-013: `provider_retry.py`, `refresh_export.py`,
  `refresh-data.ps1`, `tools/refresh-tests/test-crawl-contract.ps1`:
  bootstrap tự chờ, end cố định, import chỉ sau queue; phụ thuộc T016.
- [x] T018 FR-009–FR-014/NFR-004: chạy toàn bộ exporter pytest và PowerShell
  harness; cập nhật `README.md`, `docs/REMEDIATION_PLAN.md` và kết quả ở đây;
  phụ thuộc T015–T017.

| ID | Req | Task | Path | Done when |
|---|---|---|---|---|
| T001 | — | Spec, research (measurements), plan | `specs/026-refresh-automation/**` | Written; constitution check passed — **done 2026-09-06** |
| T002 | FR-001, FR-002 | Shared retry module: classification moved out of `export_all_symbols.py`, `call(label, fn)` with the 5 s / 20 s ladder, `ProviderCallFailed` naming the call | `tools/market-data/vnstock-export/provider_retry.py` | **done** — `provider_retry.py`; `tests/test_provider_retry.py` 9/9 incl. the cause-chain walk and the no-retry-on-schema case |
| T003 | FR-001 | Wrap every bare provider call in the four stage-1 exporters and in `fetch_symbol_universe` | `export_instrument_reference.py`, `export_sector_reference_vci.py`, `export_equity_profile.py`, `export_history.py`, `export_all_symbols.py` | **done** — all 7 previously bare calls wrapped; parametrised tests cover the instrument-reference, equity-profile (universe + delisted) and index-history calls |
| T004 | DATA-001 | `export_all_symbols.py` uses the shared classification instead of its own copy | same | **done** — `export_all_symbols.py` now aliases the shared names; suite 92/92 |
| T005 | NFR-001, FR-005 | `Invoke-BackendStage`: follow the log from a byte offset; fail on a stall window instead of the full timeout | `refresh-data.ps1` | **done** — `Invoke-BackendStageAttempt` follows both logs through `StreamReader`s from a byte offset and fails after 15 min of silence |
| T006 | FR-004 | `Invoke-BackendStage` retries a failed stage (`-Attempts`, default 3), waiting for port 8080 to free between attempts | `refresh-data.ps1` | **done** — up to 3 attempts, waits for port 8080 to free between them |
| T007 | FR-003, FR-006 | Stage state file: record completions with parameters + timestamp; skip completed stages when they match and are < 12 h old; print every skip and every discard | `refresh-data.ps1` | **done** — `refresh-state.json`; harness checked resume, parameter change, 13-hour-old state and a corrupt file (all three hazards start clean and print why) |
| T008 | NFR-002, NFR-003 | Worker pool + token bucket + locked, atomically-replaced checkpoint in the universe exporter | `export_all_symbols.py` | **done** — `TokenBucket` (our own ceiling, default 40/min), locked + atomically replaced checkpoint, `--workers` default 5; `tests/test_export_all_symbols_concurrency.py` 7/7 |
| T009 | SC-4, SC-5 | Exporter suite; measured serial-vs-parallel comparison on a sample | `tools/market-data/vnstock-export/tests` | exporter suite **92/92**; every-symbol-exactly-once proven for 1 and 5 workers, and a worker-level bug is re-raised rather than silently shortening the crawl. The end-to-end speed-up is measured on the owner's next refresh (SC-4 stays open until then) |
| T010 | — | Docs: script header, REMEDIATION P2-12 closed, changelog | `refresh-data.ps1`, `docs/REMEDIATION_PLAN.md` | **done** — script header, P2-12 closed, changelog |
| T011 | SC-2, SC-3 | Keep the orchestrator harnesses as repo tests — the stage runner was rewritten and had nothing behind it | `tools/refresh-tests/` | **done 2026-09-06** — `test-resume-state.ps1` (6 checks incl. the three stale-state hazards) and `test-stage-runner.ps1` (9 checks against fake backends: markers, stall, early exit, APPLICATION FAILED TO START, skip, retry-then-throw). Both green. Found and fixed while writing them: the stall path was unreachable in the first draft of the harness, and a failed stage printed "exit ()" |
| T012 | FR-007, DATA-001, SC-6 | A "not enough sessions yet" failure is classified and re-checked instead of settled forever | `export_daily_bars.py`, `export_all_symbols.py`, `tests/test_export_daily_bars.py`, `tests/test_export_all_symbols.py` | **done 2026-09-07** — `InsufficientSessions(ValueError)` names the actual session count; `RECHECK_DAYS_BY_FAILURE` gives it a 7-day window beside `NoStatementsAvailable`'s 35; suite **94/94** (was 92) and a live re-probe of LPS raises the new class. Closes Q-61; Q-62 (the `--end`-dated-bar convergence defect found in the same measurement) stays open by design |
| T013 | FR-008, SC-7 | Coverage is the window that was fetched, not a bar dated `--end`, so a stopped symbol can finish and the run converges | `export_all_symbols.py`, `tests/test_export_daily_bars.py` | **done 2026-09-07** — `daily_bars_current` drops the traded-on-`--end` condition and cross-checks the package's own `rangeEnd`; the test that encoded the old rule was rewritten in both directions (stale `--end` still re-fetches; a package short of the window, or with no sessions, is still not current). Replayed over the real 1,522-symbol checkpoint: `daily_bars_current` 870 → 1,473, `is_finished` 889 → 1,168. Suite 94/94. Closes Q-62 |
