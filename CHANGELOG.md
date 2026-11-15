# Changelog

All notable changes to the CashBuddy project will be documented in this file.

## [1.0.6] - 2026-11-13

### Added - Dynamic Custom Category Domains
- **Manage Categories Screen**: Full Material 3 custom category domain management (`ManageCategoriesScreen`, `ManageCategoriesViewModel`) accessible directly from Settings.
- **Custom Domains Customization**: Allows creating, editing, and deleting custom income/expense categories with custom hex colors and curated icons.
- **Safe Reassignment**: Deleting a custom category automatically reassigns all historical transactions to Uncategorized (ID 0) in the same database transaction.
- **System Domain Protection**: Core system categories (`is_system = 1`) are locked and protected from deletion/renaming.

### Added - Mathematical Pipeline & Statistical Integrity
- **Dimensionless Recurrence Detection**: Fixed `RecurrenceDetector` to calculate and store the true dimensionless Coefficient of Variation ($\text{CV} = \frac{\sqrt{\text{Var}}}{\mu} = \frac{\sigma}{\mu}$) derived from Inter-Arrival Times (IAT), eliminating the 720× Fano factor inflation on monthly transactions.
- **Tier-2 Deduplication Merchant Guard**: `DedupEngine` now verifies merchant equality or Jaro-Winkler similarity ($\ge 0.70$) before merging same-amount transactions within $\pm 5$ minutes, preventing distinct merchants with identical amounts from merging.
- **Self-Calibration Feedback Loop**: Enabled active on-device calibration in `LikelihoodCalibrator` with dual ground-truth feedback loops (pipeline auto-outcomes and user confirm/reject actions) blending empirical likelihood ratios once $\ge 50$ observations accumulate.
- **Human-in-the-Loop Threshold Synchronization**: Centrally extracted `HUMAN_IN_THE_LOOP_MAX_AMOUNT = 10000.0` across `PolicyEngine`, `MessagePipeline`, and `DedupEngine`.
- **Trained OCR Pipeline**: Injected DI-configured `CategoryEngine` into `ScreenshotParser` to apply learned merchant rules to OCR receipts.
- **Budget Alert Delivery**: Added and registered `AndroidBudgetNotifier` in `CashBuddyApplication` ensuring real-time pacing alerts are dispatched.

### Changed & Cleaned - Database, Migrations & Dead Code Sweep
- **SQLDelight Migration 4.sqm**: Dropped orphaned `goals` table across existing installs, deleted `goals.sq`, and cleaned raw SQL fallbacks.
- **Repository Surface Pruning**: Removed dead repository methods (`getByCategory`, `getMonthlySummary`, `getAverageAmount`, `sumByCategory`, `updateNotes`, etc.) and synchronized all unit test fakes.
- **Dead Theme & ViewModels**: Removed 13 unused theme colors, unused spacing tokens, dead ViewModel functions (`deleteTransaction`, `clearFilters`, `moveNavItem`, `exportDuplicatePreviewCsv`), and 10 unused XML drawables.
- **Presentation i18n**: Replaced hardcoded strings with `stringResource(...)` across all Settings sections, dialogs, and Duplicate Reconciliation components.

## [Unreleased] - 2026-10-08

### Added - Global Budget Feature (Spec-Driven Development Bundle)
- **Constant & Aggregate Query (`T1`)**:
  - Added `BudgetCategories.GLOBAL = "__GLOBAL__"` sentinel category.
  - Added `sumAll` aggregate debit query to `Transaction.sq` and wired into `TransactionRepository`.
- **Core Budget Engine (`T2`)**:
  - Added global budget branch to `BudgetEngine.statusFor` summing all debit transactions regardless of category.
- **ViewModel Architecture (`T3`)**:
  - Exposed `globalBudget`, `categoryBudgets`, and computed `unbudgetedSpent` in `BudgetViewModel` and `BudgetUiState`.
- **Compose Multiplatform UI Restructure (`T4`)**:
  - Restructured `BudgetListScreen` to display global budget card at top, followed by category budgets and dynamic "Unbudgeted" row.
  - Updated `BudgetCard` with `isGlobal` support (larger typography, no category dot, disabled drilldown tap).
- **Budget Edit Sheet (`T5`)**:
  - Added "Global (all spending)" option in category dropdown with sentinel validation ("A global budget for this period already exists").
- **Alert Precedence Scheduler (`T6`)**:
  - Updated `BudgetAlertScheduler` to process category budgets before global, ensuring at most one notification per transition cycle.
  - Global state updates silently when category alerts fire.
- **Polish & Design System (`T7`)**:
  - Added dynamic period titles ("Monthly Budget", "Weekly Budget", "Yearly Budget") and accessibility semantics on `BudgetCard`, `UnbudgetedCard`, and `SectionHeader`.
- **Documentation (`T8`)**:
  - Updated `README.md`, specs index (`specs/README.md`), and `CHANGELOG.md`.

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
