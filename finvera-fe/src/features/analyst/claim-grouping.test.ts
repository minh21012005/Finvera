import { describe, expect, it } from "vitest";
import { groupClaimsBySentence } from "./format/claim-grouping";

describe("verified-claim grouping", () => {
  it("shows a sentence once with every verified field instead of once per tag", () => {
    const claims = [
      { claimText: "Tín hiệu MUA (LONG) với chiến lược MOMENTUM.", sequenceNo: 1, toolName: "TECHNICAL", sourceField: "signal.direction", asOf: "2026-08-31T03:47:34Z" },
      { claimText: "Tín hiệu MUA (LONG) với chiến lược MOMENTUM.", sequenceNo: 1, toolName: "TECHNICAL", sourceField: "signal.strategyCode", asOf: "2026-08-31T03:47:34Z" },
      { claimText: "Chỉ số P/E là 101,05.", sequenceNo: 2, toolName: "VALUATION", sourceField: "peRatio", asOf: "2026-08-31T03:47:34Z" },
    ];
    const groups = groupClaimsBySentence(claims);
    expect(groups).toHaveLength(2);
    expect(groups[0].sourceFields).toEqual(["signal.direction", "signal.strategyCode"]);
    expect(groups[1].sourceFields).toEqual(["peRatio"]);
  });

  it("keeps identical sentences from different tools apart", () => {
    const claims = [
      { claimText: "Giá 28500.", sequenceNo: 1, toolName: "STOCK", sourceField: "price", asOf: "" },
      { claimText: "Giá 28500.", sequenceNo: 3, toolName: "TECHNICAL", sourceField: "close", asOf: "" },
    ];
    expect(groupClaimsBySentence(claims)).toHaveLength(2);
  });
});
