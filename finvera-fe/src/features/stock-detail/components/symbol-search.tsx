import { useEffect, useState } from "react";
import { searchStocks, type StockSearchResult } from "../api/stock-detail";

export function SymbolSearch({ onSelect }: { onSelect: (symbol: string) => void }) {
  const [query, setQuery] = useState("");
  const [results, setResults] = useState<StockSearchResult[]>([]);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (query.trim().length === 0) {
      return;
    }
    const controller = new AbortController();
    const timeout = window.setTimeout(() => {
      searchStocks(query, controller.signal)
        .then((found) => {
          setResults(found);
          setError(null);
        })
        .catch((cause: unknown) => {
          if (controller.signal.aborted) return;
          setResults([]);
          setError("Không thể tìm kiếm mã cổ phiếu lúc này.");
          void cause;
        });
    }, 200);
    return () => {
      controller.abort();
      window.clearTimeout(timeout);
    };
  }, [query]);

  return (
    <div className="symbol-search" role="search">
      <label htmlFor="symbol-search-input">Tra cứu mã cổ phiếu</label>
      <input
        id="symbol-search-input"
        type="text"
        value={query}
        placeholder="Nhập mã, ví dụ FPT"
        autoComplete="off"
        onChange={(event) => {
          const next = event.target.value.toUpperCase();
          setQuery(next);
          if (next.trim().length === 0) {
            setResults([]);
            setError(null);
          }
        }}
      />
      {error && <p role="alert">{error}</p>}
      {results.length > 0 && (
        <ul className="symbol-search-results" role="listbox" aria-label="Kết quả tra cứu">
          {results.map((result) => (
            <li key={result.symbol}>
              <button type="button" onClick={() => onSelect(result.symbol)}>
                <strong>{result.symbol}</strong> — {result.companyName}
                {result.sector ? ` · ${result.sector}` : ""}
              </button>
            </li>
          ))}
        </ul>
      )}
      {query.trim().length > 0 && results.length === 0 && !error && (
        <p role="status">Không tìm thấy mã cổ phiếu được hỗ trợ.</p>
      )}
    </div>
  );
}
