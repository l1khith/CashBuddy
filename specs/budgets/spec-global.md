# Global Budget — Specification

## Problem

Users can set per-category budgets but cannot set an overall spending limit.
They cannot answer: "How much did I spend this month, and am I within my
target?" Without a global budget, the tracker answers only part of the question.

## Goals

1. Let the user set one global spending limit per period (monthly, weekly, yearly).
2. Show global spending against that limit at the top of the Budgets screen.
3. Show a computed "Unbudgeted" row when both global and category budgets exist.
4. Fire one alert the first time the global crosses 80% and 100% per period.

## Non-Goals

- Excluding specific categories from the global total.
- Auto-allocating the global budget into category budgets.
- Rollover of unspent global budget.
- Blocking transactions when the global is exceeded.
- Multiple simultaneous global budgets (one per period, user picks the period).
- A separate drill-down screen for the global budget.

## User Stories

**US-1** — As a user, I want to set a monthly global spending limit so I can
see how much of my total budget I have used.

**US-2** — As a user, I want to see the global budget above my category
budgets so I understand the relationship between them.

**US-3** — As a user, I want to see how much I have spent outside my tracked
categories, so I know where the rest of my money went.

**US-4** — As a user, I want a notification when I approach or exceed my
global budget, so I can adjust my spending before the period ends.

## Acceptance Criteria

### AC-1: Creating a global budget
- From the Budgets screen, tapping "+ Add" opens the edit sheet.
- The category dropdown includes "Global (all spending)" as the first option.
- Selecting "Global" and saving creates a budget with `category = '__GLOBAL__'`.
- Only one global budget may exist per period. Attempting to create a second
  shows the validation error "A global budget for this period already exists."

### AC-2: Viewing the global budget
- The global budget appears at the top of the Budgets screen, above all
  category budgets.
- The card shows: category name ("Monthly Budget"), spent / limit,
  percent used, progress bar, remaining amount, days remaining.
- The card is visually distinct from category cards (larger, no category icon).

### AC-3: Global spending calculation
- Spent is computed from all transactions where:
  - `type = DEBIT`
  - `timestamp` is within the current period
  - `is_merged = 0`
- The category filter is NOT applied. All categories count.

### AC-4: Unbudgeted row
- The Unbudgeted row appears only when BOTH conditions are true:
  - A global budget exists for the current period
  - At least one category budget exists for the current period
- The value is `global.spent - sum(category.spent)`.
- If the value is negative (categories exceed global, which is possible if
  a category has spending that also counts toward the global — it does not
  double-count, but the arithmetic can appear negative with edge cases), show
  the value as-is. Do not clamp.

### AC-5: Alerts
- When the global crosses 80% for the first time in a period, one notification
  fires: "You've used 80% of your monthly budget".
- When it crosses 100%, one notification fires: "You've exceeded your monthly budget".
- The same state does not fire again within the same period.
- If a category alert and a global alert would fire at the same moment,
  only the category alert fires. The global state updates silently.

### AC-6: Editing and deleting
- Long-press on the global card opens the edit sheet with the same fields.
- The edit sheet has a "Delete budget" action.
- Deleting removes the global budget. Transactions are unchanged.
- After deletion, the Unbudgeted row disappears.

### AC-7: Persistence
- The global budget survives app restart.
- The global budget survives app upgrade with the existing schema.

## Constraints

- No new table. Reuse `budgets` with the sentinel category `__GLOBAL__`.
- No new permissions. No network calls.
- INR only. No currency selection.
- The engine reads. It does not write.

## Success Metrics

- A user can set a global budget in under 15 seconds.
- The global spent value matches a manual sum of all DEBIT transactions in
  the period, 100% of the time.
- No duplicate notifications within a period.
- The Unbudgeted value is displayed correctly when both global and category
  budgets exist.
