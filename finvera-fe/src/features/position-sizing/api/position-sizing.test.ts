import { afterEach, describe, expect, it, vi } from "vitest";
import { calculatePositionSize, parseSizingResult, PositionSizingApiError } from "./position-sizing";

vi.mock("../../auth/api/owner-access", () => ({ getCsrf: vi.fn().mockResolvedValue({ headerName: "X-CSRF-TOKEN", token: "t" }) }));

const result = {
  status: "CALCULATED", symbol: "FPT", mode: "MANUAL", quantity: 1000, rawPermittedQuantity: 1099,
  lotSize: 100, roundingRemainder: 99, capitalBaseVnd: "100000000", availableCashVnd: "100000000",
  resolvedEntryPriceVnd: "50000", resolvedStopPriceVnd: "47000", effectiveEntryPriceVnd: "50000",
  effectiveStopPriceVnd: "47000", costsExcluded: true, riskBudgetVnd: "5000000", acquisitionUnitCostVnd: "50000",
  stopNetProceedsPerShareVnd: "47000", lossPerShareVnd: "3000", requiredCapitalVnd: "50000000",
  estimatedLossAtStopVnd: "3000000", remainingCashVnd: "50000000", projectedSymbolMarketValueVnd: null,
  projectedSymbolExposureRate: null, projectedDeploymentRate: null,
  constraints: [{ code: "RISK_BUDGET", applicability: "APPLIED", rawQuantity: 1666, binding: false }],
  inputEvidence: [{ field: "entryPriceVnd", value: "50000", source: "OWNER_ENTERED", unit: "VND_PER_SHARE", asOf: null, coherenceKey: null }],
  reasonCodes: [], warnings: ["COSTS_EXCLUDED"], sizingRuleVersion: "position-sizing-v1",
  marketRuleVersion: "market-lot-v1", calculatedAt: "2026-09-12T08:00:00Z",
};

describe("position sizing API", () => {
  afterEach(() => vi.unstubAllGlobals());
  it("decodes every financial field and rejects unknown contract fields", () => {
    expect(parseSizingResult(result).quantity).toBe(1000);
    expect(() => parseSizingResult({ ...result, invented: "x" })).toThrow(/unknown field/);
    expect(() => parseSizingResult({ ...result, requiredCapitalVnd: 50000000 })).toThrow(/decimal string/);
  });
  it("rejects unknown enums, malformed timestamps, and contradictory result states", () => {
    expect(() => parseSizingResult({ ...result, constraints: [{ ...result.constraints[0], code: "INVENTED" }] }))
      .toThrow(/constraint code/);
    expect(() => parseSizingResult({ ...result, inputEvidence: [{ ...result.inputEvidence[0], unit: "PERCENT" }] }))
      .toThrow(/evidence unit/);
    expect(() => parseSizingResult({ ...result, warnings: ["UNKNOWN_WARNING"] })).toThrow(/warning/);
    expect(() => parseSizingResult({ ...result, calculatedAt: "yesterday" })).toThrow(/UTC instant/);
    expect(() => parseSizingResult({ ...result, status: "WITHHELD", quantity: null, reasonCodes: [] }))
      .toThrow(/withheld result/);
    expect(() => parseSizingResult({ ...result, status: "CALCULATED", reasonCodes: ["BELOW_STANDARD_LOT"] }))
      .toThrow(/calculated result/);
  });
  it("sends CSRF and maps problem details", async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({ reasonCode: "INVALID_RISK_BUDGET" }),
      { status: 422, headers: { "content-type": "application/problem+json" } }));
    vi.stubGlobal("fetch", fetchMock);
    await expect(calculatePositionSize({} as never)).rejects.toEqual(expect.objectContaining<Partial<PositionSizingApiError>>({ status: 422, reasonCode: "INVALID_RISK_BUDGET" }));
    expect(fetchMock.mock.calls[0][1].headers["X-CSRF-TOKEN"]).toBe("t");
  });
});
