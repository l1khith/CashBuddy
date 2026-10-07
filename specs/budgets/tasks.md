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
- [ ] Create `BudgetRepository` interface
- [ ] Implement `SqlDelightBudgetRepository`
- [ ] Add tests covering all methods with in-memory DB

## T4 — Engine
- [ ] Create `BudgetEngine` with `statusFor(budget, now)`
- [ ] Implement `periodRange(period, now)`
- [ ] Add tests:
  - budget with zero transactions
  - budget at exactly 100%
  - budget crossing period boundary
  - budget with merged transactions excluded

## T5 — ViewModel
- [ ] Create `BudgetViewModel`
- [ ] Load active budgets on init
- [ ] Expose `StateFlow<BudgetUiState>`
- [ ] Handle create/update/delete actions
- [ ] Add tests for each action

## T6 — UI: List
- [ ] Create `BudgetListScreen`
- [ ] Create `BudgetCard` composable
- [ ] Empty state
- [ ] FAB wiring
- [ ] Compose UI test for both states

## T7 — UI: Edit Sheet
- [ ] Create `BudgetEditSheet`
- [ ] Category dropdown, amount field, period segmented control
- [ ] Validation
- [ ] Save / Delete actions
- [ ] Compose UI test for create flow

## T8 — UI: Drill-down
- [ ] Create `BudgetDrilldownScreen`
- [ ] Reuse `TransactionItemCard`
- [ ] Filter by category and period
- [ ] Compose UI test

## T9 — Alerts
- [ ] Create `BudgetAlertScheduler`
- [ ] Hook into `MessagePipeline` after transaction commit
- [ ] Implement at-most-once-per-transition logic
- [ ] Reset on period rollover
- [ ] Unit tests with fake clock

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
