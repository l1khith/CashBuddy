# Budgets — Tasks

## T1 — Schema
- [x] Add `budgets` table to `Budget.sq`
- [x] Add `sumByCategory` query to `Transaction.sq`
- [x] Add SQLDelight migration `1.sqm`
- [x] Regenerate Kotlin bindings
- [x] Verify build passes

## T2 — Domain models
- [x] Create `Budget` data class in `domain/model/`
- [x] Create `BudgetPeriod` enum (MONTHLY, WEEKLY, YEARLY)
- [x] Create `AlertState` enum (ON_TRACK, WARNING, EXCEEDED)
- [x] Create `BudgetStatus` data class

## T3 — Repository
- [x] Create `BudgetRepository` interface
- [x] Implement `SqlDelightBudgetRepository`
- [x] Add tests covering all methods with in-memory DB

## T4 — Engine
- [x] Create `BudgetEngine` with `statusFor(budget, now)`
- [x] Implement `periodRange(period, now)`
- [x] Add tests:
  - budget with zero transactions
  - budget at exactly 100%
  - budget crossing period boundary
  - budget with merged transactions excluded

## T5 — ViewModel
- [x] Create `BudgetViewModel`
- [x] Load active budgets on init
- [x] Expose `StateFlow<BudgetUiState>`
- [x] Handle create/update/delete actions
- [x] Add tests for each action

## T6 — UI: List
- [x] Create `BudgetListScreen`
- [x] Create `BudgetCard` composable
- [x] Empty state
- [x] FAB wiring
- [x] Compose UI test for both states

## T7 — UI: Edit Sheet
- [x] Create `BudgetEditSheet`
- [x] Category dropdown, amount field, period segmented control
- [x] Validation
- [x] Save / Delete actions
- [x] Compose UI test for create flow

## T8 — UI: Drill-down
- [x] Create `BudgetDrilldownScreen`
- [x] Reuse `TransactionItemCard`
- [x] Filter by category and period
- [x] Compose UI test

## T9 — Alerts
- [x] Create `BudgetAlertScheduler`
- [x] Hook into `MessagePipeline` after transaction commit
- [x] Implement at-most-once-per-transition logic
- [x] Reset on period rollover
- [x] Unit tests with fake clock

## T10 — Navigation
- [ ] Add Budgets to bottom nav or home screen entry
- [ ] Wire drill-down navigation
- [ ] Back stack behavior test

## T11 — Polish
- [ ] Colors: green < 80, amber 80–99, red >= 100
- [ ] Progress bar animation
- [ ] Currency formatting uses existing `CurrencyRegistry.format`
- [ ] Accessibility labels

## T12 — Documentation
- [ ] Update `README.md` with budget feature
- [ ] Add `specs/budgets/` to repo docs index
- [ ] Note in CHANGELOG
