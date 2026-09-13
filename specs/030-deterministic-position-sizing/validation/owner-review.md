# Owner Comprehension Review

**Feature**: `030-deterministic-position-sizing`  
**Status**: PENDING OWNER REVIEW  
**Prepared**: 2026-09-13

This is the required SC-007 release check. For each result, the owner should
name every binding constraint and explain why increasing the quantity by one
standard lot would violate that constraint. Record the answer as understood or
needs correction. Automated tests cannot substitute for this review.

| # | Representative scenario | Expected explanation | Owner result |
|---|---|---|---|
| 1 | Fixed risk is lower than cash capacity | `RISK_BUDGET` binds | Pending |
| 2 | Cash capacity is lower than risk capacity | `AFFORDABILITY` binds | Pending |
| 3 | Risk and cash candidates are exactly equal | Both risk and affordability bind | Pending |
| 4 | Symbol concentration has the lowest headroom | `SYMBOL_CONCENTRATION` binds | Pending |
| 5 | Total deployment has the lowest headroom | `TOTAL_DEPLOYMENT` binds | Pending |
| 6 | Symbol and deployment candidates tie | Both exposure caps bind | Pending |
| 7 | Raw capacity is 199 shares | Result is 100; 99-share remainder is excluded by lot floor | Pending |
| 8 | Raw capacity is 99 shares | `BELOW_STANDARD_LOT`; no positive quantity | Pending |
| 9 | Explicit costs reduce risk and cash candidates | Effective entry/stop and all five cost assumptions explain the lower result | Pending |
| 10 | Selected signal is no longer current | `SIGNAL_NOT_CURRENT`; no fallback to copied prices | Pending |

**Pass condition**: 10/10 explanations are correct. If any explanation is
unclear, improve labels or disclosures, rerun automated checks, then repeat all
ten scenarios before changing this status to PASSED.
