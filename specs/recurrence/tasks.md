# Tasks: Recurring Transaction Detection

- [ ] T1 — Database table and queries (`recurring_pattern.sq`)
- [ ] T2 — Autocorrelation computation (`Autocorrelation.kt` + tests)
- [ ] T3 — RecurrenceDetector (`RecurrenceDetector.kt` + tests)
- [ ] T4 — Repository (`RecurringPatternRepository` + `RecurringPatternRepositoryImpl`)
- [ ] T5 — Detection trigger (every 10 confirmed txns, app open 24h, settings manual trigger)
- [ ] T6 — MessagePipeline integration (pattern match -> max(p, 0.95), auto-confirm, keep anomaly check)
- [ ] T7 — MAD == 0 fix in `FraudDetector.kt` + unit test
- [ ] T8 — Budget integration (`BudgetEngine.kt` committed vs discretionary)
- [ ] T9 — Home screen Recurring section composable
- [ ] T10 — Transaction list 🔁 badge on `TransactionItemCard.kt`
- [ ] T11 — Budget card committed/discretionary display
- [ ] T12 — Settings screen Recurring patterns rows & list
- [ ] T13 — Transaction detail recurring block
- [ ] T14 — Documentation & verification
