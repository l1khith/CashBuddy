# Changelog

All notable changes to the CashBuddy project will be documented in this file.

## [Unreleased] - 2026-10-07

### Added - Budgets Feature (Spec-Driven Development Bundle)
- **Database Schema & Migrations (`T1`)**:
  - Created `budgets` table in SQLDelight (`Budget.sq`) with UUID primary key, category foreign reference, amount, period, start date, alert state, and timestamps.
  - Added `sumByCategory` and `getByCategoryAndPeriod` queries in `Transaction.sq`.
  - Added migration script `1.sqm` and automatic table verification in `DatabaseDriverFactory`.
- **Domain Models (`T2`)**:
  - Added `Budget`, `BudgetPeriod` (`MONTHLY`, `WEEKLY`, `YEARLY`), `AlertState` (`ON_TRACK`, `WARNING`, `EXCEEDED`), and `BudgetStatus`.
- **Repository Pattern (`T3`)**:
  - Implemented `SqlDelightBudgetRepository` implementing `BudgetRepository` interface.
  - Comprehensive unit tests against in-memory SQLite driver (`SqlDelightBudgetRepositoryTest`).
- **Core Budget Engine (`T4`)**:
  - Created `BudgetEngine` with pure logic `statusFor(budget, now)` and calendar-aligned `periodRange(period, now)`.
  - Zero I/O, fully testable period rollover and spending calculation (`BudgetEngineTest`).
- **ViewModel Architecture (`T5`)**:
  - Implemented `BudgetViewModel` with reactive `StateFlow<BudgetUiState>`.
  - Full CRUD operations with coroutine scope injection for unit tests (`BudgetViewModelTest`).
- **Compose Multiplatform UI (`T6`, `T7`, `T8`)**:
  - Created `BudgetListScreen` and animated `BudgetCard` with color-coded spending status (<80% green, 80-99% amber, ≥100% red).
  - Created `BudgetEditSheet` bottom sheet with inline validation, category dropdown, period segmented control, and save/delete actions.
  - Created `BudgetDrilldownScreen` filtering debit transactions by category and period with summary banner and `TransactionItemCard` reuse.
- **Alerts & Ingestion Hook (`T9`)**:
  - Created `BudgetAlertScheduler` with at-most-once-per-transition notifications for 80% and 100% crossings and period rollover resets.
  - Hooked into `MessagePipeline` post-commit ingestion loop.
  - Unit tests with simulated clock (`BudgetAlertSchedulerTest`).
- **Navigation & Routing (`T10`)**:
  - Added `ScreenRoute.BudgetDrilldown(category, period)` type-safe route destination.
  - Added Quick Entry Card on Home screen and wired drill-down navigation from Budget cards.
  - Added navigation back stack test (`BudgetNavigationTest`).
- **Polish & Design System (`T11`)**:
  - Standardized currency formatting using `CurrencyRegistry.format`.
  - Accessibility labels and content descriptions across all components.
  - Smooth 500ms tween progress bar animation.
