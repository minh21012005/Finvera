# Source Facts Contract v1

Alerts consumes only published, read-only application services. Implementations remain in the owning module and return immutable records.

## Stock

`StockAlertDataService.resolve(instrumentId)` returns a bounded snapshot with up to 22 accepted completed daily observations and the required technical components. Every observation includes source row ID, trading date, accepted timestamp, source, adjustment basis, OHLCV, and requested indicator components. Results preserve repository recency ordering and evaluators explicitly select current and prior observations without mixing adjustment bases.

The signal collection in `StockAlertDataService.resolve(instrumentId)` returns current accepted triggered signal facts with signal ID, direction, trading date, calculated time, and strategy/rule version. The stock module does not currently publish a durable negative strategy-evaluation fact, so an absent signal is withheld as `STRATEGY_SIGNAL_NOT_PUBLISHED`; every distinct published signal ID is evaluated as an event and may notify once.

## Market

`MarketAlertDataService.findLatestRegimePair("EOD")` returns previous/current distinct accepted observations. Each contains assessment ID, trading date, accepted time, score, deterministic `RegimeLabel`, data status, source, and regime rule version. The market module owns score-to-label mapping.

## Portfolio

`PortfolioAlertDataService.resolveConcentration(ownerId, portfolioId)` validates the owner and returns percentage concentration, largest symbol, total invested market value, largest-position value, source bar IDs/dates/accepted times, status, reasons, coherence key, unit `PERCENT`, rule version, and observed time. Unknown and foreign portfolios both raise the same not-found result.

## Research

`ResearchAlertDataService.findAfter(ownerId, instrumentId, documentType?, acceptedAfter, idAfterAtSameTime?, limit)` returns at most `limit` `READY` documents ordered by `(processedAt,id)`. The pair `(acceptedAt, documentId)` is an exclusive cursor, so documents that share a processed timestamp are not lost. A fact contains document ID/event key, title, instrument, category, source, publication date, and processed/accepted time. It excludes original bytes, extracted text, and chunks.

## Failure and temporal rules

- Empty means no eligible fact only when the service can distinguish that from unavailable input; otherwise return a typed status/reason.
- Facts are immutable references. Corrections use a different source row/version or newer accepted time.
- Services perform no alert persistence and never receive an alert condition as executable text.
- Calls are in-process and bounded. Any unexpected exception is isolated to the claimed alert and receives at most one retry.
