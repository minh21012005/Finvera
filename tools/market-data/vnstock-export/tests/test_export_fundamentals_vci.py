"""Feature 018 — VCI fundamentals exporter against the 2026-08-31 fixtures (contract vci-fundamentals-v1).
Every assertion below is an anchor: an audited figure, an arithmetic identity of the data itself, or a
value independently reproduced by another provider's correctly-labelled frame."""
import json
from decimal import Decimal
from pathlib import Path

import pytest

import export_fundamentals_vci as vci

FIXTURES = Path(__file__).parent / "fixtures" / "vci"


def load(symbol: str, period: str):
    data = json.loads((FIXTURES / f"{symbol}.json").read_text(encoding="utf-8"))
    return vci.frames_from_fixture(data, period)


def records(symbol: str, period: str):
    frames = load(symbol, period)
    return vci.build_metric_records(symbol, frames, period), vci.detect_company_type(frames[vci.IS])


def by_code(recs):
    out = {}
    for r in recs:
        out[(r["metricCode"], r["fiscalYear"], r["fiscalQuarter"])] = r
    return out


def test_company_types_are_detected_from_the_income_statement():
    assert records("VNM", "year")[1] == "NON_FINANCIAL"
    assert records("MBB", "year")[1] == "BANK"
    assert records("BVH", "year")[1] == "INSURER"
    assert records("SSI", "year")[1] == "BROKER"


def test_vnm_fiscal_years_carry_the_audited_figures_not_the_kbs_mirror():
    idx = by_code(records("VNM", "year")[0])
    assert idx[("REVENUE", 2022, None)]["value"] == "59956247197418.000000"   # audited FY2022 net revenue
    assert idx[("REVENUE", 2024, None)]["value"] == "61782609528445.000000"   # audited FY2024 (KBS put this under 2023)
    assert idx[("REVENUE", 2025, None)]["value"] == "63645886756227.000000"
    assert idx[("NET_PROFIT", 2025, None)]["value"] == "9413589732469.000000"
    assert idx[("EPS", 2022, None)]["value"] == "3632.000000"
    assert idx[("EPS", 2025, None)]["value"] == "4028.000000"


def test_fiscal_year_equals_the_sum_of_its_four_quarters_to_the_vnd():
    for symbol in ("VNM", "MBB"):
        year = by_code(records(symbol, "year")[0])
        quarter = by_code(records(symbol, "quarter")[0])
        for code in ("REVENUE", "NET_PROFIT"):
            fy = Decimal(year[(code, 2025, None)]["value"])
            q = sum(Decimal(quarter[(code, 2025, n)]["value"]) for n in (1, 2, 3, 4))
            assert fy == q, (symbol, code, fy, q)


def test_shares_come_from_paid_in_capital_at_par_and_match_the_profile():
    frames = load("VNM", "year")
    shares = vci.shares_by_period(frames, "NON_FINANCIAL")
    assert shares["2025"] == Decimal("2089955445")          # equity_profile.shares_outstanding
    bvh = vci.shares_by_period(load("BVH", "year"), "INSURER")
    assert bvh["2025"] == Decimal("742322764")
    mbb = vci.shares_by_period(load("MBB", "year"), "BANK")
    assert abs(mbb["2025"] - Decimal("8054999909")) <= 100   # VCI rounds bank statements to thousands: 80,549,999,000,000 / 10,000 vs profile 8,054,999,909
    ssi = vci.shares_by_period(load("SSI", "quarter"), "BROKER")
    assert abs(ssi["2026-Q2"] - Decimal("2501180000")) < Decimal("5000")   # paid-in 25,030.9 bn less treasury 19.1 bn


def test_trailing_eps_reproduces_the_providers_own_yearly_trailing_eps():
    # KBS's (correctly labelled) yearly ratio frame reports trailing_eps 4,502.58 (2025) / 4,494.02 (2024) /
    # 4,074.74 (2022) for VNM — the derivation reproduces them from VCI statements alone.
    idx = by_code(records("VNM", "year")[0])
    assert idx[("TRAILING_EPS", 2025, None)]["value"].startswith("4502.58")
    assert idx[("TRAILING_EPS", 2024, None)]["value"].startswith("4494.02")
    assert idx[("TRAILING_EPS", 2022, None)]["value"].startswith("4074.73")
    assert idx[("TRAILING_EPS", 2025, None)]["derivation"] == vci.RULE_TRAILING_EPS
    # banks report EPS only annually: the derivation matches the statement EPS where both exist
    mbb = by_code(records("MBB", "year")[0])
    assert mbb[("TRAILING_EPS", 2022, None)]["value"].startswith("3855.9") and mbb[("EPS", 2022, None)]["value"] == "3856.000000"
    bvh = by_code(records("BVH", "year")[0])
    assert bvh[("TRAILING_EPS", 2025, None)]["value"].startswith("3821.2") and bvh[("EPS", 2025, None)]["value"] == "3821.000000"


def test_quarterly_trailing_eps_needs_four_consecutive_quarters_and_equals_the_fy_at_q4():
    q = by_code(records("VNM", "quarter")[0])
    assert q[("TRAILING_EPS", 2025, 4)]["value"] == by_code(records("VNM", "year")[0])[("TRAILING_EPS", 2025, None)]["value"]
    assert ("TRAILING_EPS", 2025, 1) not in q      # only 2024-Q3, 2024-Q4 precede it in the 8-quarter window
    assert ("TRAILING_EPS", 2026, 2) in q


def test_banks_get_no_quarterly_statement_eps_but_a_derived_one():
    q = by_code(records("MBB", "quarter")[0])
    assert ("EPS", 2026, 2) not in q                # VCI writes 0.0 -> dropped (not reported), never stored as zero
    assert q[("TRAILING_EPS", 2026, 2)]["derivation"] == vci.RULE_TRAILING_EPS
    assert q[("BVPS", 2026, 2)]["derivation"] == vci.RULE_BVPS
    assert q[("ROE_TTM", 2026, 2)]["derivation"] == vci.RULE_ROE
    assert Decimal(q[("ROE_TTM", 2026, 2)]["value"]) > 15   # MBB ROE ~ 20 %+


def test_bvps_is_parent_equity_over_shares():
    y = by_code(records("VNM", "year")[0])
    equity = Decimal(y[("EQUITY_ATTRIBUTABLE_TO_PARENT", 2025, None)]["value"])
    assert equity == Decimal("34483015286107") - Decimal("3797632379221")    # owners' equity less minority interests
    assert Decimal(y[("BVPS", 2025, None)]["value"]).quantize(Decimal("0.01")) == (equity / Decimal("2089955445")).quantize(Decimal("0.01"))


def test_margins_leverage_fcf_and_ebitda_follow_the_published_formulas():
    y = by_code(records("VNM", "year")[0])
    rev = Decimal(y[("REVENUE", 2025, None)]["value"])
    gp = Decimal(y[("GROSS_PROFIT", 2025, None)]["value"])
    assert Decimal(y[("GROSS_MARGIN", 2025, None)]["value"]).quantize(Decimal("0.0001")) == (gp / rev * 100).quantize(Decimal("0.0001"))
    debt = Decimal("9393736731992") + Decimal("62907826150")
    assert Decimal(y[("TOTAL_DEBT", 2025, None)]["value"]) == debt
    assert y[("DEBT_TO_EQUITY", 2025, None)]["derivation"] == vci.RULE_DEBT_TO_EQUITY
    assert y[("FREE_CASH_FLOW", 2025, None)]["derivation"] == vci.RULE_FCF
    assert y[("EBITDA", 2025, None)]["derivation"] == vci.RULE_EBITDA
    assert ("EBITDA", 2025, None) not in by_code(records("MBB", "year")[0])   # non-financials only


def test_no_kbs_style_period_shift_survives_in_quarters():
    # VCI 2025-Q1 net revenue 12,934.5 bn (the figure KBS filed under "2025-Q4") sits under 2025-Q1.
    q = by_code(records("VNM", "quarter")[0])
    assert q[("REVENUE", 2025, 1)]["value"] == "12934505332856.000000"
    assert q[("NET_PROFIT", 2025, 1)]["value"] == "1587273268054.000000"
    assert q[("REVENUE", 2026, 2)]["value"] == "18847347464988.000000"


def test_first_occurrence_wins_and_package_carries_source_and_type():
    recs, company_type = records("SSI", "quarter")
    pkg = vci.build_package(recs, "SSI", company_type)
    assert pkg["upstreamSource"] == "VNSTOCK_VCI" and pkg["companyType"] == "BROKER"
    assert pkg["contractVersion"] == "vnstock-fundamentals-v1" and pkg["unitScale"] == 1
    assert all(r["canonicalRecord"] and r["symbol"] == "SSI" for r in pkg["records"])
    with pytest.raises(vci.NoStatementsAvailable):
        vci.build_package([], "SSI", "BROKER")   # e.g. A32: annual statements only -> named, settled failure


def test_fixture_is_unchanged_since_the_probe():
    # The anchor set only means something if the fixture is the 2026-08-31 capture.
    data = json.loads((FIXTURES / "VNM.json").read_text(encoding="utf-8"))
    assert data["capturedAt"] == "2026-08-31" and "4.0.7" in data["source"]
