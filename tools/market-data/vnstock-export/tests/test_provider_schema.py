"""Feature 011 FR-007: pin the provider's item-id schema (observed 2026-08-30, owner probe) so a
renamed or vanished id fails here instead of silently dropping data (the bank-EPS lesson)."""
import importlib.util
import json
from pathlib import Path

MODULE_PATH = Path(__file__).parents[1] / "export_fundamentals.py"
SPEC = importlib.util.spec_from_file_location("export_fundamentals", MODULE_PATH)
ef = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
SPEC.loader.exec_module(ef)

FIXTURE = json.loads((Path(__file__).parent / "provider_schema_fixture.json").read_text(encoding="utf-8"))
TYPES = FIXTURE["companyTypes"]


def observed(stmt):
    return {i for ct in TYPES.values() for i in ct[stmt]}


def test_every_mapped_item_id_was_observed_at_the_provider():
    missing = {i for i in ef.INCOME_STATEMENT_MAP if i not in observed("income_statement")}
    missing |= {i for i in list(ef.RATIO_MAP) + list(ef.RATIO_GROWTH_MAP) if i not in observed("ratio")}
    missing |= {i for i in ef.FCF_OCF_IDS + ef.FCF_CAPEX_IDS if i not in observed("cash_flow")}
    assert not missing, f"mapped ids no longer observed at the provider: {sorted(missing)}"


# Which concepts each company type must be able to deliver (research R-006 exclusions documented).
CONCEPTS = {
    "NET_PROFIT": {"except": set()},
    "REVENUE": {"except": {"bank"}},
    "EPS": {"except": {"insurance"}},          # BVH: EPS only via ratio trailing_eps
    "OPERATING_PROFIT": {"except": {"bank"}},
}


def test_every_company_type_maps_each_concept_unless_documented():
    inverse = {}
    for item_id, code in ef.INCOME_STATEMENT_MAP.items():
        inverse.setdefault(code, set()).add(item_id)
    for code, rule in CONCEPTS.items():
        for kind, ct in TYPES.items():
            if kind in rule["except"]:
                continue
            assert inverse[code] & set(ct["income_statement"]), f"{kind}: no statement id maps to {code}"


def test_fcf_inputs_exist_for_every_non_bank_type():
    for kind, ct in TYPES.items():
        if kind == "bank":
            continue
        ids = set(ct["cash_flow"])
        assert ids & set(ef.FCF_OCF_IDS), f"{kind}: no OCF id"
        assert ids & set(ef.FCF_CAPEX_IDS), f"{kind}: no capex id"
