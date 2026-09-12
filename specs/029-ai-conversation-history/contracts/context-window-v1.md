# Context Window Contract: `context-window-v1`

## Purpose

This contract defines the only conversation history that Spring Boot may send
to `finvera-ai` for a new conversation-backed Analyst question. It bounds data
exposure and prompt size while keeping recent referential context useful.

## Inputs

- authenticated `ownerId`;
- owned `conversationId`;
- current exchange sequence number;
- current question, sent separately and never counted as a prior exchange;
- prior owned exchanges with `status = COMPLETED` and lower sequence number.

Failed, cancelled, processing, non-owned, and current exchanges are ineligible.

## Default limits

| Limit | Value |
|---|---:|
| Database candidates | 10 newest completed exchanges |
| Included exchanges | 5 maximum |
| Aggregate history size | 12,000 Unicode characters maximum |

`question.length + answer.length` is the size of one exchange after the same
normalization used for stored text. Separator/prompt-template text is outside
this business limit and remains bounded by the AI adapter.

## Selection algorithm

1. Fetch no more than ten eligible exchanges newest first.
2. Initialize included count and size to zero.
3. For each candidate from newest to oldest:
   - stop after five included exchanges;
   - include the candidate only when its complete question and answer keep the
     aggregate size at or below 12,000 characters;
   - otherwise stop and omit that candidate plus every older candidate.
4. Reverse included exchanges so the internal request is chronological.
5. Send the selected pairs and current question. Never send any other exchange.

The selector never truncates, summarizes, merges, or rewrites an exchange. The
current question is always sent even when zero history pairs fit.

## Internal request invariants

- `priorTurns` contains 0-5 items and is chronological.
- Every item contains one complete stored question and completed answer.
- Aggregate prior-turn content is at most 12,000 characters.
- The request carries no conversation title, deleted content, raw provider
  response, raw tool result, or evidence payload.
- `finvera-ai` validates these bounds before prompt construction.
- All prompt locations that use history label it as untrusted historical
  conversation. It cannot authorize tools, override system instructions, or
  serve as evidence for current financial/document claims.

## Observable metadata

The first SSE `accepted` event exposes:

```json
{
  "ruleVersion": "context-window-v1",
  "includedExchanges": 3,
  "omittedExchanges": 4
}
```

Only counts and the rule version are logged. Selected text is never logged.

## Boundary examples

| Prior state | Expected selection |
|---|---|
| No completed exchange | Empty `priorTurns` |
| Five small completed exchanges | All five in chronological order |
| Six small completed exchanges | Newest five |
| Ten exchanges totaling exactly 12,000 characters | Newest candidates that fit, maximum five |
| Newest exchange alone exceeds 12,000 characters | Empty `priorTurns`; never truncate it or skip backward to older context |
| Newest is failed, next two completed | Exclude failed; include the two completed pairs |
| Eleven small completed exchanges | Query considers newest ten, then includes newest five |

## Versioning

Changing candidate count, included count, size calculation, selection order,
eligible statuses, or truncation/summary behavior creates a new rule version and
requires regression/evaluation updates. Configuration may lower emergency
limits without changing the algorithm, but increasing beyond this contract is a
contract change.
