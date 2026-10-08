# Global Budget — Plan

## Design Decision: Sentinel Category

The global budget is stored in the existing `budgets` table with
`category = '__GLOBAL__'`. No new table, no new repository, no new engine.

Rationale: the global budget differs from a category budget in exactly one
way — which transactions count toward it. That is a query concern, not a
schema concern. A separate table would introduce two code paths for
identical operations (create, read, update, delete, alert, period math).

## Data Model

No schema change. Add a constant:

```kotlin
object BudgetCategories {
    const val GLOBAL = "__GLOBAL__"
}
```

## New Query

In `Transaction.sq`:

```sql
sumAll:
SELECT COALESCE(SUM(amount), 0.0) FROM transactions
WHERE type = ?
  AND timestamp >= ?
  AND timestamp <= ?
  AND is_merged = 0;
```

## Engine Change

`BudgetEngine.statusFor` gains one branch:

```kotlin
val spent = if (budget.category == BudgetCategories.GLOBAL) {
    transactionRepository.sumAll(range.first, range.second, TransactionType.DEBIT)
} else {
    transactionRepository.sumByCategory(
        budget.category, range.first, range.second, TransactionType.DEBIT
    )
}
```

No other logic changes.

## ViewModel Change

`BudgetViewModel` exposes:

```kotlin
val globalBudget: StateFlow<BudgetStatus?>
val categoryBudgets: StateFlow<List<BudgetStatus>>
val unbudgetedSpent: StateFlow<Double>
```

`unbudgetedSpent` is computed:

```kotlin
combine(globalBudget, categoryBudgets) { global, cats ->
    if (global == null) 0.0
    else global.spent - cats.sumOf { it.spent }
}
```

## UI Change

`BudgetListScreen` structure:

```
Column {
    globalBudget?.let { BudgetCard(it, isGlobal = true) }
    if (categoryBudgets.isNotEmpty()) {
        SectionHeader("Category Budgets")
        categoryBudgets.forEach { BudgetCard(it) }
    }
    if (globalBudget != null && categoryBudgets.isNotEmpty()) {
        SectionHeader("Unbudgeted Spending")
        UnbudgetedCard(unbudgetedSpent)
    }
}
```

`BudgetCard` gains a `isGlobal: Boolean` parameter. When true:
- Larger font for the limit
- No category icon
- No drill-down navigation on tap
- The label reads "Monthly Budget" / "Weekly Budget" / "Yearly Budget"

## Edit Sheet Change

The category dropdown gains "Global (all spending)" as the first option.
When selected, the sheet:
- Disables category-specific validation for existing category
- Enables the sentinel validation instead ("A global budget for this period already exists")

## Alert Precedence

`BudgetAlertScheduler` fires alerts in this order:

1. Compute all category statuses. Fire category alerts first.
2. Compute global status. Fire only if no category alert fired in this cycle.
3. Update `last_alert_state` on all rows regardless of whether an alert fired.

This ensures the user sees at most one notification per state transition.

## Testing Plan

- `BudgetEngineTest`: global budget sums all DEBIT transactions.
- `BudgetEngineTest`: category budget sums only its category.
- `BudgetViewModelTest`: `unbudgetedSpent` computes correctly when both exist.
- `BudgetAlertSchedulerTest`: global does not fire when category fires.
- `BudgetListScreenTest`: global card appears above category cards.
- `BudgetListScreenTest`: unbudgeted row hidden when no global budget.
- `BudgetEditSheetTest`: selecting Global validates correctly.

## Migration

No migration needed. The `budgets` table already exists. The sentinel
category is a data convention, not a schema change.

## Rollout

Ship as a single feature branch. No flag. Existing users see no change until
they create a global budget.
