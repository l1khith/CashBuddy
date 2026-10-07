# Budgets — Specification

## Problem

CashBuddy tracks categorized transactions. Users can see *what* they spent.
They cannot see *how much they have left to spend*. Without a limit, the
tracker is a diary. With a limit, it becomes a decision tool.

## Goals

1. Let users set a spending limit per category per period (monthly, weekly, yearly).
2. Show current-period spending against each limit.
3. Warn the user the first time a limit crosses 80% and 100%.
4. Allow editing and deleting budgets at any time.

## Non-Goals

- Rollover of unspent budget between periods.
- Sub-category budgets (e.g. "Restaurants" within "Food").
- Shared budgets across users.
- Budget templates or AI-generated suggestions.
- Multi-currency budgets. The app is INR-only.

## User Stories

**US-1** — As a user, I want to set a monthly limit for Food & Dining so I
can control my eating-out spending.

**US-2** — As a user, I want to see at a glance how much I have spent and
how much is left in each category this month.

**US-3** — As a user, I want a notification when I am approaching or have
exceeded a budget, so I can adjust before the period ends.

**US-4** — As a user, I want to edit or delete a budget when my income or
priorities change.

**US-5** — As a user, I want to tap a budget and see the transactions that
counted against it, so I can verify the number is correct.

## Acceptance Criteria

### AC-1: Creating a budget
- From the Budgets screen, tapping "+ Add" opens a sheet.
- The sheet requires: category, amount, period.
- Saving creates the budget and it appears at the top of the list.
- Validation: amount must be > 0. Category must not already have an active
  budget for the same period.

### AC-2: Viewing budgets
- The Budgets screen shows all active budgets for the current period.
- Each card shows: category name and icon, spent amount, limit,
  percent used, remaining amount, and a progress bar.
- Progress bar color: green below 80%, amber 80–99%, red 100%+.
- Empty state: "No budgets yet. Tap + to set one."

### AC-3: Spending calculation
- Spent is computed from transactions where:
  - `category = budget.category`
  - `type = DEBIT`
  - `timestamp` is within the current period
  - `is_merged = 0`
- Spent is recalculated on every screen visit. Never cached.

### AC-4: Warnings
- When a budget crosses 80% for the first time in a period, one notification
  fires: "Food budget is at 80%".
- When it crosses 100%, one notification fires: "Food budget exceeded".
- The same state does not fire again within the same period.
- State resets when the period rolls over.

### AC-5: Editing and deleting
- Long-press a budget card opens an edit sheet with the same fields.
- Edit sheet has a "Delete budget" action.
- Deleting removes the budget. Existing transactions are unchanged.

### AC-6: Drill-down
- Tapping a budget card opens a filtered transaction list for that category
  and period.
- The list shows the same transactions that contribute to "spent".

### AC-7: Persistence
- Budgets survive app restart.
- Deleting a budget does not delete any transaction.
- If a category is deleted from the app, its budget is also deleted.

## Constraints

- No new permissions.
- No network calls.
- INR only. No currency selection.
- The budget engine reads from the transaction table. It does not write.
- The notification logic fires at most once per state transition per period.

## Success Metrics

- A user can set a budget in under 15 seconds.
- The "spent" value matches a manual sum of transactions 100% of the time.
- No duplicate notifications within a period.
