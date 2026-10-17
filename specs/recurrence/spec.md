# Specification: Recurring Transaction Detection

## Problem

Users have predictable recurring expenses (commute, lunch, subscriptions) that trigger a Review Inbox prompt every time. This is friction. The app should recognize the pattern and auto-confirm.

## Goals

1. Detect recurring patterns per merchant using $CV_\Delta$, count, and autocorrelation.
2. Auto-confirm new transactions that match an established pattern.
3. Show the user what patterns exist and their schedules.
4. Fold recurring patterns into budget views as committed spend.

## Non-Goals

- Monthly or yearly pattern detection (v2).
- Cross-merchant pattern detection (e.g. "every purchase over ₹5000 tends to be on the 1st"). Out of scope.
- Auto-cancelling subscriptions. The app detects, it does not act.
- Pattern detection for income. Only expenses.

## User Stories

- **US-1**: As a user, when I take the BMTC bus every morning at 8:02, the ₹18 charge should not prompt me for confirmation.
- **US-2**: As a user, I want to see which merchants are recurring so I can understand my committed monthly spend.
- **US-3**: As a user, when a recurring merchant charges an unusually large amount (e.g. ₹2,500 at BMTC), I want an anomaly alert even though the small daily charges are recurring.

## Acceptance Criteria

- **AC-1: Detection**
  - A merchant is recurring iff all three tests pass:
    1. $CV_\Delta \le 0.25$
    2. Occurrence count in last 30 days $\ge 5$
    3. Autocorrelation peak $\max_{L \in \{12, 24, 48, 168\}} R(L) \ge 0.4$
  - The detected period is one of $\{12\text{h}, 24\text{h}, 48\text{h}, 168\text{h}\}$.
  - Detection runs after every 10 confirmed transactions, or manually from Settings, or once per day at app open.

- **AC-2: Auto-confirm**
  - When a new transaction arrives whose merchant + amount matches a recurring pattern:
    - Classification $p_{\text{transaction}}$ is set to $\max(p, 0.95)$.
    - The transaction bypasses the Review Inbox.
    - The transaction status is set to `CONFIRMED`.
  - The user can still see it in the transaction list.

- **AC-3: Pattern visibility**
  - The Home screen shows a "Recurring" section listing detected patterns with merchant, amount, period, and next expected date.
  - Tapping a pattern opens a list of past transactions that match the pattern.

- **AC-4: Budget integration**
  - The Budget screen shows committed vs discretionary spend:
    - $\text{Committed} = \sum (\text{pattern.amount} \times \text{occurrences per period})$
    - $\text{Discretionary} = \text{spent} - \text{committed}$
  - Both are shown on each category card.

- **AC-5: Anomaly interaction**
  - A recurring pattern does NOT suppress the anomaly check.
  - A ₹2,500 charge at a merchant with a ₹18 recurring pattern is flagged as anomalous.
  - Fix the $MAD == 0$ condition in `FraudDetector.kt`.

- **AC-6: Persistence**
  - Patterns survive app restart.
  - Patterns are recomputed on detection runs; the table is a cache, not authoritative ($P4$).
  - Deleting all transactions clears the pattern table.
