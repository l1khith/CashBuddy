# Global Budget — Tasks

## T1 — Constant and query
- [ ] Add `BudgetCategories.GLOBAL` constant in `domain/model/`
- [ ] Add `sumAll` query to `Transaction.sq`
- [ ] Regenerate SQLDelight bindings
- [ ] Add `sumAll` to `TransactionRepository` interface and implementation
- [ ] Test: `sumAll` returns correct totals

## T2 — Engine branch
- [ ] Update `BudgetEngine.statusFor` with the global branch
- [ ] Test: global status computes sum of all DEBIT transactions
- [ ] Test: category status still computes only its category

## T3 — ViewModel
- [ ] Expose `globalBudget: StateFlow<BudgetStatus?>`
- [ ] Expose `categoryBudgets: StateFlow<List<BudgetStatus>>` (rename from existing if needed)
- [ ] Add `unbudgetedSpent: StateFlow<Double>`
- [ ] Test: unbudgeted computes correctly with global + categories
- [ ] Test: unbudgeted is 0.0 when no global budget

## T4 — UI restructure
- [ ] Update `BudgetListScreen` to render global, then categories, then unbudgeted
- [ ] Add `isGlobal` parameter to `BudgetCard`
- [ ] Add `UnbudgetedCard` composable
- [ ] Add `SectionHeader` composable if not present
- [ ] Test: global card renders above category cards
- [ ] Test: unbudgeted row hidden when no global
- [ ] Test: global card has no drill-down tap

## T5 — Edit sheet
- [ ] Add "Global (all spending)" as the first option in the category dropdown
- [ ] Add validation: one global per period
- [ ] Test: creating a global budget succeeds
- [ ] Test: creating a second global budget for the same period fails

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
