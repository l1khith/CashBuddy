# Budgets — Plan

## Stack

- **Language**: Kotlin (commonMain), no platform-specific code needed.
- **Storage**: SQLDelight + SQLCipher (existing).
- **UI**: Compose Multiplatform (existing).
- **DI**: manual via AppContainer (existing).
- **Notifications**: existing Android notification channel; iOS via local
  notifications if the platform is later added.

## Data Model

### New table: `budgets`

| Column            | Type    | Purpose |
|-------------------|---------|---------|
| id                | TEXT PK | UUID    |
| category          | TEXT    | matches `transactions.category` |
| amount            | REAL    | limit in INR |
| period            | TEXT    | MONTHLY / WEEKLY / YEARLY |
| start_date        | INTEGER | epoch millis, anchor for period math |
| is_active         | INTEGER | 1 or 0, default 1 |
| last_alert_state  | TEXT    | ON_TRACK / WARNING / EXCEEDED / null |
| last_alert_at     | INTEGER | epoch millis, null until first alert |
| created_at        | INTEGER | epoch millis |
| updated_at        | INTEGER | epoch millis |

Indexes: `category`, `is_active`.

### New query: `sumByCategory`

```
SELECT COALESCE(SUM(amount), 0.0) FROM transactions
WHERE category = ?
  AND type = 'DEBIT'
  AND timestamp >= ?
  AND timestamp <= ?
  AND is_merged = 0;
```

## Architecture

```
UI (BudgetListScreen, BudgetEditSheet, BudgetDrilldownScreen)
        │
        ▼
ViewModel (BudgetViewModel)
        │
        ▼
BudgetEngine  ←──  BudgetRepository
        │                │
        │                ▼
        │         SQLDelight queries
        │
        ▼
TransactionRepository (existing sumByCategory)
```

`BudgetEngine` is the only component that knows how to compute a budget
status. It is pure logic: takes a `Budget` and a `now` timestamp, returns
a `BudgetStatus`. No I/O. Testable in isolation.

## API Contracts

### `BudgetRepository`

```kotlin
interface BudgetRepository {
    suspend fun create(budget: Budget)
    suspend fun update(budget: Budget)
    suspend fun delete(id: String)
    suspend fun getActive(): List<Budget>
    suspend fun getById(id: String): Budget?
    suspend fun findByCategory(category: String, period: String): Budget?
    suspend fun markAlerted(id: String, state: AlertState, at: Long)
    suspend fun resetAlertState(id: String)
}
```

### `BudgetEngine`

```kotlin
class BudgetEngine(
    private val transactionRepository: TransactionRepository
) {
    suspend fun statusFor(budget: Budget, now: Long): BudgetStatus
    fun periodRange(period: BudgetPeriod, now: Long): LongRange
}
```

### `BudgetStatus`

```kotlin
data class BudgetStatus(
    val budget: Budget,
    val spent: Double,
    val remaining: Double,
    val percentUsed: Float,
    val state: AlertState
)
```

## Notification Flow

- `BudgetAlertScheduler` runs after each transaction insert.
- For each active budget, compute status.
- If `status.state > budget.lastAlertState`, fire notification and update.
- If a period boundary has passed since `lastAlertAt`, reset state to ON_TRACK.

## Period Math

- MONTHLY: from the 1st of `now`'s month to the last day of that month.
- WEEKLY: Monday 00:00 to Sunday 23:59 of `now`'s week.
- YEARLY: Jan 1 to Dec 31 of `now`'s year.
- `start_date` is used only to determine whether the budget should be active
  at `now` (i.e. `now >= start_date`). Period boundaries follow the calendar.

## UI Contracts

### `BudgetListScreen`
- Column of `BudgetCard`s, one per active budget.
- "+ Add" FAB opens `BudgetEditSheet(null)`.
- Long-press a card opens `BudgetEditSheet(budget)`.
- Tap a card navigates to `BudgetDrilldownScreen(category, periodRange)`.

### `BudgetEditSheet`
- Fields: category (dropdown of existing categories), amount (numeric),
  period (segmented control).
- Buttons: Save, Delete (visible only when editing).
- Validation errors inline.

### `BudgetDrilldownScreen`
- Title: category name + period label.
- List of `TransactionItemCard` filtered by the same predicate used in
  `sumByCategory`.

## Migration

Existing installs:
- Add the `budgets` table via SQLDelight migration `1.sqm`.
- No backfill needed. Budgets start empty.

## Testing Plan

- `BudgetEngineTest`: pure unit tests for period math and state transitions.
- `BudgetRepositoryTest`: in-memory SQLite, CRUD coverage.
- `BudgetAlertSchedulerTest`: fake clock, assert at-most-once per transition.
- `BudgetListScreenTest`: Compose UI, empty and populated states.
- No integration test for notifications. Covered by unit test on the scheduler.

## Rollout

Ship as a single feature branch. No flag. No staged rollout.
The feature is additive; existing users see an empty Budgets screen until
they create their first budget.

## Open Questions

None. If any emerge during implementation, they are resolved in favor of
the spec.
