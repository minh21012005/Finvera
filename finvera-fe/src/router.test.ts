import { describe, it, expect } from "vitest";
import { isAlertsPath, isAnalystPath, isResearchPath, isScreenerPath, parseStockSymbolFromPath } from "./router";

describe("router", () => {
  it("recognizes /analyst as the AI Analyst route", () => {
    // Regression test: the AI Analyst page (Feature 7) was fully built, tested in
    // isolation, and never wired into the router — no path in this module ever
    // matched it, so the page was unreachable from the running application.
    expect(isAnalystPath("/analyst")).toBe(true);
    expect(isAnalystPath("/analyst/")).toBe(true);
    expect(isAnalystPath("/research")).toBe(false);
  });

  it("still recognizes existing routes unchanged", () => {
    expect(isResearchPath("/research")).toBe(true);
    expect(isScreenerPath("/screener")).toBe(true);
    expect(parseStockSymbolFromPath("/stocks/HPG")).toBe("HPG");
  });

  it("recognizes the owner alerts route only", () => {
    expect(isAlertsPath("/alerts")).toBe(true);
    expect(isAlertsPath("/alerts/")).toBe(true);
    expect(isAlertsPath("/alerts/other")).toBe(false);
  });
});
