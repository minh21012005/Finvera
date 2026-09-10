"""Feature 015 golden replay (specs/015-analyst-e2e-on-real-data, FR-004 / SC-003).

The fixtures under ./golden/ are real recordings made by tools/verification/analyst_e2e.py on the
owner's stack: tool responses re-fetched from finvera-be, the final answer and the verified claims.
No model and no network here — only the deterministic parts of the pipeline are exercised:

  1. attribution replay  — the recorded verified claims re-verify one-for-one against the recorded
                           tool responses (the verifier is stable on real payloads);
  2. offline synthesis   — the templates produce ≥ 1 verifiable claim for every recorded tool
                           response, so a provider outage never turns a successful tool into a
                           refusal (Q-50);
  3. fabrication guard   — every number ≥ 10 in every recorded answer exists in that question's
                           tool responses (Q-43 tolerance rules from explain.py);
  4. planner fallback    — the keyword planner picks at least the tools the recording used for the
                           symbol questions (KEYWORD_FALLBACK stays useful under a 429/503).
"""
import glob
import json
import os
from typing import Any, Dict, List

import pytest

from app.features.analysis.explain import fabricated_numbers
from app.features.chat.service import ChatOrchestrationService
from app.features.orchestration.allowlist import ToolName
from app.features.orchestration.attribution import RawStructuredClaim, verify_attribution
from app.features.orchestration.dispatch import DispatchedToolCall, OrchestrationDispatcher

GOLDEN_DIR = os.path.join(os.path.dirname(__file__), "golden")
FIXTURES = sorted(glob.glob(os.path.join(GOLDEN_DIR, "analyst_e2e_*.json")))


def _load(path: str) -> Dict[str, Any]:
    with open(path, encoding="utf-8") as fh:
        return json.load(fh)


def _calls(entry: Dict[str, Any]) -> List[DispatchedToolCall]:
    out = []
    for tr in entry["toolResponses"]:
        resp = tr["response"]
        if not isinstance(resp, dict) or "_httpError" in resp or "_unsupportedInCapture" in resp:
            continue
        out.append(DispatchedToolCall(sequence_no=tr["sequenceNo"], tool_name=ToolName(tr["toolName"]),
                                      arguments=tr.get("arguments") or {}, status="SUCCEEDED", response_data=resp))
    return out


def _questions():
    for path in FIXTURES:
        for entry in _load(path)["questions"]:
            yield pytest.param(entry, id=f"{os.path.basename(path)}::{entry['id']}")


QUESTIONS = list(_questions())
pytestmark = pytest.mark.skipif(not FIXTURES, reason="no golden recording checked in")


@pytest.mark.parametrize("entry", QUESTIONS)
def test_recorded_verified_claims_replay_against_recorded_tool_responses(entry):
    final = entry["final"] or {}
    calls = _calls(entry)
    if final.get("refused") or not calls:
        pytest.skip("refused or no successful tool in the recording")
    raw = [RawStructuredClaim(claimText=c["claimText"], sequenceNo=c["sequenceNo"], fieldPath=c["fieldPath"],
                              claimedValue=c["claimedValue"]) for c in final.get("structuredClaims", [])]
    if not raw:
        pytest.skip("answer carried no structured claims")
    replay = verify_attribution(final["answer"], raw, [], calls, False)
    assert replay.refused is False
    assert [(c.sequenceNo, c.fieldPath) for c in replay.structuredClaims] == [(c.sequenceNo, c.fieldPath) for c in raw]


@pytest.mark.parametrize("entry", QUESTIONS)
def test_offline_templates_never_refuse_a_successful_tool(entry):
    calls = _calls(entry)
    if not calls:
        pytest.skip("no successful tool in the recording")
    svc = ChatOrchestrationService(dispatcher=OrchestrationDispatcher(tool_client=None))
    parts, raw_claims, _ = svc._offline_synthesize(calls)
    verified = verify_attribution(" ".join(parts), raw_claims, [], calls, False)
    assert verified.refused is False, parts
    covered = {c.sequenceNo for c in verified.structuredClaims}
    assert covered == {c.sequence_no for c in calls}, f"every tool must contribute a verified claim: {parts}"


@pytest.mark.parametrize("entry", QUESTIONS)
def test_recorded_answer_states_no_number_the_tools_did_not_supply(entry):
    final = entry["final"] or {}
    calls = _calls(entry)
    if final.get("refused") or not calls:
        pytest.skip("refused or no successful tool in the recording")
    evidence = "\n".join(json.dumps(c.response_data, ensure_ascii=False) for c in calls)
    assert fabricated_numbers(final["answer"], evidence) == []


@pytest.mark.parametrize("entry", QUESTIONS)
def test_keyword_planner_covers_the_tools_the_recording_used(entry):
    used = {t["toolName"] for t in entry["toolCalls"]}
    if not used or "RESEARCH_RAG" in used:
        pytest.skip("nothing to compare")
    svc = ChatOrchestrationService(dispatcher=OrchestrationDispatcher(tool_client=None))
    planned = {p["tool_name"] for p in svc.plan_tools(entry["question"], entry.get("symbol"))}
    effective_planned = set(planned)
    if "COMPARE" in effective_planned:
        # COMPARE tool subsumes individual read tools (STOCK, VALUATION, etc.) for multi-symbol queries
        effective_planned.update({"STOCK", "VALUATION", "FUNDAMENTAL", "TECHNICAL"})
    assert used <= effective_planned, f"keyword fallback planned {planned} but the model used {used}"
