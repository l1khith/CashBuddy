---
name: compose-multiplatform-ui
description: >-
  Use this skill when developing or styling Compose Multiplatform 1.11+ screens, Material3 themes,
  Navigation 3 type-safe backstack destinations, MVI Reducers for Review/Budget, and MVVM StateFlow for dashboards.
---

# Compose Multiplatform UI Development Procedures

## Overview
PaisaPal UI is built with Compose Multiplatform 1.11+ using Material3 design tokens, adaptive layout support, and type-safe Navigation 3.

## Screen Architectures

1. **Dashboard Screens (MVVM with StateFlow)**:
   - Screen files: `HomeScreen.kt`, `TransactionListScreen.kt`, `StatsScreen.kt`, `AccountsScreen.kt`, `SettingsScreen.kt`.
   - Pattern: ViewModel exposes single immutable `StateFlow<UiState>` and `Channel<UiEffect>` for one-shot events.
   - Screen composable consumes state via `collectAsStateWithLifecycle()`.

2. **Complex Triage Flows (MVI with Reducer)**:
   - Screen files: `ReviewScreen.kt`, `BudgetScreen.kt`.
   - Contract files: `ReviewContract.kt` (defining `ReviewIntent`, `ReviewState`, `ReviewEffect`).
   - Reducer file: `ReviewReducer.kt` — a pure Kotlin class with a deterministic `reduce(state, result): ReviewState` method.
   - Ideal for swipe-to-confirm, confidence filtering, and instant batch actions.

3. **Navigation 3 Type-Safe Routing**:
   - Define destinations using Kotlin `@Serializable` objects.
   - Avoid string-based URL routes.
   - NavHost manages backstack transitions cleanly across shared UI.

4. **Material3 Design Guidelines**:
   - Custom palette with Fintech Trust Blue (`#1E88E5`), Teal Mint (`#26A69A`), Expense Crimson (`#E53935`), Income Emerald (`#43A047`).
   - Confidence badges clearly displayed on transaction cards:
     - High ($\ge 0.85$): Green.
     - Medium ($0.50 - 0.84$): Amber.
     - Low ($< 0.50$): Red.
   - Adaptive Edge-to-Edge window insets handling for status bar and navigation bar.

## Verification Checklist
- [ ] No direct state mutation inside `@Composable` functions.
- [ ] MVI Reducers are pure functions covered by unit tests.
- [ ] Preview composables exist for key visual components.
- [ ] Edge-to-edge system insets are applied without UI clipping.
