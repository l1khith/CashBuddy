# Implementation Plan: Recurring Transaction Detection

## Architecture & Math

Point process analysis on merchant transaction timestamps $T = \{t_1, t_2, \dots, t_n\}$ with exact integer amounts in paise:
1. $CV_\Delta = \sigma_\Delta / \mu_\Delta \le 0.25$
2. Count $\ge 5$ in last 30 days
3. Autocorrelation peak $R(L) \ge 0.4$ for $L \in \{12, 24, 48, 168\}$ (hourly bins over 720 hours)

## Database

New table `recurring_pattern`:

```sql
CREATE TABLE recurring_pattern (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    merchant_normalized TEXT NOT NULL,
    amount_paise INTEGER NOT NULL,
    occurrence_count INTEGER NOT NULL,
    median_interval_hours REAL NOT NULL,
    cv REAL NOT NULL,
    period_hours INTEGER NOT NULL,
    autocorrelation_peak REAL NOT NULL,
    first_seen INTEGER NOT NULL,
    last_seen INTEGER NOT NULL,
    last_updated INTEGER NOT NULL,
    UNIQUE(merchant_normalized, amount_paise)
);

CREATE INDEX idx_pattern_merchant ON recurring_pattern(merchant_normalized);
CREATE INDEX idx_pattern_amount ON recurring_pattern(amount_paise);
```

Named queries:
`upsert`, `getAll`, `findByMerchantAndAmount`, `deleteAll`, `deleteStale`

## New Files

- `shared/src/commonMain/kotlin/com/cashbuddy/core/analytics/Autocorrelation.kt` — $R(L)$ computation
- `shared/src/commonMain/kotlin/com/cashbuddy/core/analytics/RecurrenceDetector.kt` — the three point process tests
- `shared/src/commonMain/kotlin/com/cashbuddy/domain/model/RecurringPattern.kt` — domain model
- `shared/src/commonMain/kotlin/com/cashbuddy/domain/repository/RecurringPatternRepository.kt` — repository interface
- `shared/src/commonMain/kotlin/com/cashbuddy/data/repository/RecurringPatternRepositoryImpl.kt` — repository implementation

## Modified Files

- `shared/src/commonMain/sqldelight/com/cashbuddy/db/recurring_pattern.sq` — SQLDelight table & queries
- `shared/src/commonMain/kotlin/com/cashbuddy/core/prob/MessagePipeline.kt` — check pattern match before PolicyEngine, auto-confirm if matched, run anomaly check
- `shared/src/commonMain/kotlin/com/cashbuddy/core/FraudDetector.kt` — fix MAD == 0 bug
- `shared/src/commonMain/kotlin/com/cashbuddy/core/budget/BudgetEngine.kt` — compute committed vs discretionary
- `shared/src/commonMain/kotlin/com/cashbuddy/di/AppModule.kt` — register new repository & detector
- UI Composables:
  - `HomeScreen.kt` — add RecurringSection
  - `TransactionItemCard.kt` — 🔁 badge
  - `BudgetCard.kt` — committed vs discretionary
  - `SettingsScreen.kt` — Recurring patterns row & manual recompute
  - `TransactionDetailScreen.kt` — Recurring pattern block
