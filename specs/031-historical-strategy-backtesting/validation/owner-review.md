# Owner comprehension review

**Status**: Ready for owner execution; automated gates pass, T057 remains open

Automated tests cannot establish SC-007. For each of ten representative runs,
the owner records the next-session entry time, binding sizing cap, exit reason,
total costs, maximum drawdown, and the main data limitation without assistance.
All six answers must be correct in all ten rows before T057 and SC-007 pass.

| Scenario | Strategy / condition | Entry | Cap | Exit | Costs | Drawdown | Limitation | Pass |
|---|---|---|---|---|---|---|---|---|
| 1 | Next-open entry | | | | | | | |
| 2 | Gap through stop | | | | | | | |
| 3 | Stop and target touched | | | | | | | |
| 4 | Terminal close | | | | | | | |
| 5 | Costs excluded | | | | | | | |
| 6 | Cash cap binds | | | | | | | |
| 7 | Aggregate risk binds | | | | | | | |
| 8 | Pyramiding rejected | | | | | | | |
| 9 | Provider-adjusted basis | | | | | | | |
| 10 | Corporate-action withholding | | | | | | | |
