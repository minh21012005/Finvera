import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { LiteMarkdown } from "./format/lite-markdown";
import { stripCitationTags } from "./format/citation-tags";

describe("LiteMarkdown", () => {
  it("renders bold, bullet lists and paragraphs from the Analyst's markdown", () => {
    const text = [
      "Dưới đây là phân tích cho VIC:",
      "",
      "**1. Phân tích kỹ thuật**",
      "* **Các đường trung bình động:**",
      "  * MA20 là 213.380,0 VND.",
      "  * MA50 là 216.958,0 VND.",
      "* RSI14 đạt 68,74 điểm.",
      "",
      "**2. Định giá**",
      "1. P/E là 101,05.",
      "2. P/B là 5,66.",
    ].join("\n");
    const { container } = render(<LiteMarkdown text={text} />);
    expect(container.querySelectorAll("ul")).toHaveLength(1);
    expect(container.querySelectorAll("ul li")).toHaveLength(4);
    expect(container.querySelectorAll("ol li")).toHaveLength(2);
    expect(container.querySelectorAll("strong").length).toBeGreaterThanOrEqual(3);
    expect(screen.getByText("MA20 là 213.380,0 VND.")).toBeTruthy();
    expect(container.innerHTML).not.toContain("**");
  });

  it("renders Markdown tables with headers and styled rows", () => {
    const text = [
      "**Bảng so sánh đối đầu:**",
      "",
      "| Chỉ số / Tiêu chí | SSI | VND |",
      "| --- | --- | --- |",
      "| Thị giá (VND) | 32.500 | 18.200 |",
      "| P/E | 18,5 | 14,2 |",
      "| Định giá Finvera | Phù hợp | Hấp dẫn |",
      "",
      "*(Lưu ý: Bảng so sánh mang tính chất hỗ trợ quyết định)*.",
    ].join("\n");
    const { container } = render(<LiteMarkdown text={text} />);
    expect(container.querySelectorAll("table")).toHaveLength(1);
    expect(container.querySelectorAll("th")).toHaveLength(3);
    expect(screen.getByText("Chỉ số / Tiêu chí")).toBeTruthy();
    expect(screen.getByText("SSI")).toBeTruthy();
    expect(screen.getByText("VND")).toBeTruthy();
    expect(container.querySelectorAll("tbody tr")).toHaveLength(3);
    expect(screen.getByText("32.500")).toBeTruthy();
    expect(screen.getByText("18,5")).toBeTruthy();
    expect(container.querySelector("em")).toBeTruthy();
    expect(screen.getByText(/Lưu ý: Bảng so sánh mang tính chất hỗ trợ quyết định/)).toBeTruthy();
  });

  it("never injects model text as HTML", () => {
    const { container } = render(<LiteMarkdown text={"<img src=x onerror=alert(1)> **x**"} />);
    expect(container.querySelector("img")).toBeNull();
    expect(container.textContent).toContain("<img src=x onerror=alert(1)>");
  });
});

describe("stripCitationTags", () => {
  it("removes inline citation tags and the space they leave before punctuation", () => {
    expect(stripCitationTags("MA20 là 213.380,0 VND [T1:MA20.value=213380] .")).toBe("MA20 là 213.380,0 VND.");
    expect(stripCitationTags("Theo tài liệu [Block 2], doanh thu tăng.")).toBe("Theo tài liệu, doanh thu tăng.");
  });
});
