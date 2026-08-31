# Research: AI Analyst end-to-end on real data

Method: `tools/verification/analyst_e2e.py` against the owner's local stack
(finvera-be 8080 + finvera-ai 8000 + Gemini, database as of the 2026-08-31
warmups). Owner-operated; no market-data provider calls. Recordings in
`tools/verification/out/analyst_e2e_<date>.json`.

## R-001 — First capture (2026-08-31 16:28) and diagnosis

| Q | tools | refused | claims | numbers unsupported | basis misses | ms |
|---|---|---|---|---|---|---|
| Q01 VNM price | STOCK | no | 2 | 0 | DATA_STATUS (answer said "hôm nay" on DELAYED data of 28/08) | 4,128 |
| Q02 VNM valuation | VALUATION **HTTP 401** | yes | 0 | – | – | 5,324 |
| Q03 MBB vs VCB | VALUATION ×2 **HTTP 401** | yes | 0 | – | – | 4,268 |
| Q04 HPG technical | TECHNICAL ok | **yes** | 0 | – | – | 5,789 |
| Q05 AAA loss-maker | VALUATION 401, FUNDAMENTAL ok | no | 9 | 9 (all parser false positives: en-US thousands, `2026-Q2`) | ANNUAL_BASIS not worded | 15,546 |
| Q06 ABB UPCoM | STOCK ok, VALUATION 401 | no | 2 | 1 (false positive) | DATA_STATUS | 1,823 (template) |
| Q07 MBB growth | FUNDAMENTAL ok | no | 1 | 1 (false positive) | ANNUAL_BASIS, PROVIDER_TRAILING_EPS | 1,388 (template) |
| Q08 screener | SCREENING ok | **yes** | 0 | – | – | 9,469 |
| Q09 market | MARKET ok | no | 3 | 2 (false positives) | – | 1,066 (template) |
| Q10 portfolio | PORTFOLIO ok | **yes** | 0 | – | – | 948 (template) |

Diagnosis (all confirmed in code/logs, each fixed under its own id):

- **Q-49 — VALUATION tool 401.** Backend log: `cannot execute INSERT in a read-only
  transaction` from `ValuationService.persistAssessment` ← `ToolDelegateService.getValuation`
  (`@Transactional(readOnly = true)` at class level). The valuation and fundamentals services
  materialise a revision chain on read (by design, like the public endpoints). Under read-only,
  Hibernate's MANUAL flush made the fundamentals tool "succeed" while dropping its writes; the
  valuation tool's `saveAndFlush` forced the flush and failed. The 500 was then forwarded to
  `/error`, which sits behind the OWNER security rule → the internal caller received **401
  AUTHENTICATION_REQUIRED**, masking the real cause. Two fixes: writable transactions for the
  three materialising tools; `ProblemDetailsAdvice` catch-all so `/error` is never reached.
- **Q-50 — refusals after successful tools (Q04, Q08, Q10).** With the model unavailable the
  offline templates ran; the TECHNICAL template cited `signal.direction` (the payload has
  `signals[]` since T049), PORTFOLIO and SCREENING produced no claims → `verify_attribution`
  refused (0 surviving claims). Fix: templates with verifiable claims for every tool;
  `get_nested_value` learns list steps and `.length`.
- **Q-51 — silent degraded mode.** finvera-ai log: `429 RESOURCE_EXHAUSTED … limit: 5`
  (5 requests/min free tier; 10 questions × 2 calls). The service fell back to keyword
  planning and template synthesis **without telling the reader**, printing raw decimals
  (`17300.000000 (-0.574713%)`). Fix: one retry honouring the provider's `retryDelay` (≤ 60 s);
  `OFFLINE_TEMPLATE_DISCLOSURE` sentence; `synthesisMode`/`plannerMode` in the final event; FE
  notice; vi-VN number formatting in templates.
- **Q-52 — "hôm nay" on delayed data; bases not worded.** Q01 said "Giá cổ phiếu VNM hôm nay"
  for a close of 28/08 flagged `DELAYED`; Q05/Q07 never mentioned `ANNUAL_BASIS` /
  `PROVIDER_TRAILING_EPS` although the payload carried them (`metrics[].qualityReason`). Fix:
  synthesis rules 8–10 (state the data date and status; word every basis; no inferred
  "đang lỗ/có lãi"); templates carry `theo phiên <date>` and the basis phrases.
- **Tool defects (not product):** the number parser read en-US thousands (`125,426,841,000`)
  and period tokens (`2026-Q2`) as separate numbers; rewritten to try vi-VN and en-US readings
  and to mask date/period/time tokens.

Not a defect: the fundamentals payload is large (~10 KB, 46 metric facts) — the model coped;
Q05's online answer correctly said "EPS quý không khả dụng, EPS TTM là 924.17".

## R-002 — Second capture (17:05, after Q-49…Q-52)

| Q | tools | mode | refused | claims | unsupported | basis misses | note |
|---|---|---|---|---|---|---|---|
| Q01 | STOCK | OFFLINE_TEMPLATE | no | 2 | 0 | 0 | template now says "theo phiên 2026-08-28; dữ liệu trễ một phiên" |
| Q02 | STOCK, VALUATION | ONLINE | no | 3 | 0 | 0 | **valuation tool works** (Q-49) |
| Q03 | VALUATION ×2 | – | **yes** | 0 | – | – | both calls `TIMEOUT: exceeded 10.0s` → **Q-55** |
| Q04 | TECHNICAL | ONLINE | no | 9 | 0 | 0 | |
| Q05 | VALUATION, FUNDAMENTAL | ONLINE | no | 10 | 0 | 0 | wording now carries "theo số liệu của nhà cung cấp", "trên cơ sở hàng năm" (Q-52) |
| Q06 | VALUATION | OFFLINE_TEMPLATE | no | 3 | 0 | 3 | template lacked basis/date → added |
| Q07 | FUNDAMENTAL | ONLINE | no | 3 | 0 | 0 | |
| Q08 | SCREENING | – | **yes** | 0 | – | – | HTTP 500 `Duplicate key` → **Q-54** (now visible thanks to the Q-49 500 mapping) |
| Q09 | MARKET | ONLINE | no | 9 | 0 | 0 | |
| Q10 | PORTFOLIO | OFFLINE_TEMPLATE | no | 1 | 0 | 0 | honest "chưa có vị thế" instead of a refusal (Q-50) |

Diagnosis of the two new failures led to **Q-53** (every read re-persisted a
fundamental summary because the Q-46 comparison ignored the column scale — 214
revisions for 105 instruments in one hour; bank valuations 8–18 s), **Q-54**
(screener crash on sibling revisions) and **Q-55** (peers priced from full bar
history and recomputed report-by-report). Provider note: with the owner's key
at that time the model answered 429 `GenerateRequestsPerDayPerProjectPerModel-FreeTier`
(20/day) after a few questions; the second retry rule (no retry on daily quotas)
comes from this run.

## R-003 — Third capture (17:37, after Q-53…Q-55, provider daily quota exhausted)

10/10 answered, 0 refusals, 0 unsupported numbers, 0 basis misses — all in
`OFFLINE_TEMPLATE` mode with the keyword planner (Q03 therefore planned MBB
only: the keyword fallback takes the first ticker; documented limitation).
Valuation tool latency after Q-55: MBB 0.42 s, VCB 0.20 s, HPG 0.25 s (from
8.5 / 8.0 / 17.7 s), classifications unchanged. This recording is the golden
fixture `finvera-ai/app/features/chat/tests/golden/analyst_e2e_2026-08-31.json`.

## R-004 — Fourth capture (new provider key, 404 on the configured model)

Every provider call answered `404 NOT_FOUND: models/gemini-2.5-flash is no longer
available to new users … use models/gemini-3.6-flash`. The pipeline behaved as
designed — 10/10 template answers with the disclosure, 0 refusals — and the
model name was switched (`.env`, `.env.example`, settings default).

## R-005 — Fifth capture (online, gemini-3.6-flash)

| Q | tools (model-planned) | mode | claims | unsupported | basis misses | ms |
|---|---|---|---|---|---|---|
| Q01 | STOCK | ONLINE | 6 | 0 | 0 | 14,426 |
| Q02 | STOCK, VALUATION | ONLINE (keyword planner: 503 on proposal) | 9 | 1 → tool false positive | 0 | 22,507 |
| Q03 | VALUATION ×2 | OFFLINE_TEMPLATE (503 on synthesis) | 6 | 0 | 0 | 46,140 |
| Q04 | TECHNICAL | ONLINE | 3 | 0 | 0 | 107,221 |
| Q05 | VALUATION, FUNDAMENTAL | ONLINE | 12 | 3 → "quý 2/2026" parser | 0 | 27,138 |
| Q06 | VALUATION, FUNDAMENTAL | OFFLINE_TEMPLATE (503) | 6 | 0 | 0 | 87,608 |
| Q07 | FUNDAMENTAL | OFFLINE_TEMPLATE (503) | 3 | 0 | 0 | 14,944 |
| Q08 | SCREENING | ONLINE | 2 | 21 → ordered-list markers + the question's own "20 %" | 0 | 76,054 |
| Q09 | MARKET | ONLINE | 6 | 0 | 0 | 21,033 |
| Q10 | PORTFOLIO ×2 | ONLINE | 2 | 0 | 0 | 85,712 |

10/10 answered, 0 refusals, 0 basis misses; every "unsupported number" was a
tool-parser false positive (fixed: `2/2026`, ISO timestamps, `12.` list markers,
question thresholds count as evidence). Two product findings: **Q-56** (tags with
`metrics[0].…` paths survived in the text) and the 503 "high demand" fallbacks
(now retried once after 5 s). Observation: gemini-3.6-flash answers in 14–107 s
per question on this key — far slower than 2.5-flash's 4–15 s; a lighter model
is an owner choice, not a defect.

## R-006 — Sixth capture (after Q-56; provider daily quota exhausted after Q05)

10/10 answered, 0 refusals, **0 unsupported numbers, 0 basis misses**. Q02 online
(gemini-3.6-flash, model planner: VALUATION + RESEARCH_RAG): states the trading
date 2026-08-28, the DELAYED status and the classification in words, no citation
tag left in the text (Q-56 verified on a live answer). Q05 online before the daily
quota ran out; Q01/Q03/Q04/Q06–Q10 served as disclosed `OFFLINE_TEMPLATE`
answers (16 × 429 `…PerDay…`, 0 retries — the daily-quota rule). This recording
(mixed online/template) is the golden fixture.

## Conclusions

- SC-001 / SC-002 met on the online run (R-005, R-006): every number and every
  basis in every answer traces to a tool payload; the remaining "unsupported"
  entries were all parser false positives, fixed in the tool.
- Seven product defects found and fixed by this feature: Q-49 … Q-56 (see
  docs/REMEDIATION_PLAN.md); two of them (Q-49, Q-55) made the valuation tool
  unusable for the AI Analyst before this capture.
- Owner-facing limits, not defects: the free-tier provider quota (5/min, 20/day
  per model) and gemini-3.6-flash latency (14–107 s per question); the keyword
  planner takes the first ticker of a two-symbol question.
