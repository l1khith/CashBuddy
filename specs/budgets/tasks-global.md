# Global Budget — Tasks

## T1 — Constant and query
- [x] Add `BudgetCategories.GLOBAL` constant in `domain/model/`
- [x] Add `sumAll` query to `Transaction.sq`
- [x] Regenerate SQLDelight bindings
- [x] Add `sumAll` to `TransactionRepository` interface and implementation
- [x] Test: `sumAll` returns correct totals

## T2 — Engine branch
- [x] Update `BudgetEngine.statusFor` with the global branch
- [x] Test: global status computes sum of all DEBIT transactions
- [x] Test: category status still computes only its category

## T3 — ViewModel
- [x] Expose `globalBudget: StateFlow<BudgetStatus?>`
- [x] Expose `categoryBudgets: StateFlow<List<BudgetStatus>>` (rename from existing if needed)
- [x] Add `unbudgetedSpent: StateFlow<Double>`
- [x] Test: unbudgeted computes correctly with global + categories
- [x] Test: unbudgeted is 0.0 when no global budget

## T4 — UI restructure
- [x] Update `BudgetListScreen` to render global, then categories, then unbudgeted
- [x] Add `isGlobal` parameter to `BudgetCard`
- [x] Add `UnbudgetedCard` composable
- [x] Add `SectionHeader` composable if not present
- [x] Test: global card renders above category cards
- [x] Test: unbudgeted row hidden when no global
- [x] Test: global card has no drill-down tap

## T5 — Edit sheet
- [x] Add "Global (all spending)" as the first option in the category dropdown
- [x] Add validation: one global per period
- [x] Test: creating a global budget succeeds
- [x] Test: creating a second global budget for the same period fails

## T6 — Alerts
- [ ] Update `BudgetAlertScheduler` to check category alerts before global
- [ ] Ensure only one notification fires per transition cycle
- [ ] Test: category at 90% and global at 85% fires only the category alert
- [ ] Test: global at 85% with no category alerts fires the global alert

## T7 — Polish
- [ ] Global card label uses the period name ("Monthly Budget", "Weekly Budget", "Yearly Budget")
- [ ] Colors: same as category cards (green < 80%, amber 80–99%, red >= 100%)
- [ ] Accessibility labels on global card and unbudgeted row

## T8 — Documentation
- [ ] Update `README.md` with global budget feature
- [ ] Add `spec-global.md` and `plan-global.md` to the specs index
- [ ] Update CHANGELOG
