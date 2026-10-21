# PaisaPal (CashBuddy) — Project Roadmap & Technical Debt TODO

This document tracks upcoming architectural cleanups, design overhauls, and technical debt items for the PaisaPal KMP codebase.

---

## 📌 High Priority: Database Architecture & Migration Standardization

- [x] **Dual-Layer Database Update Safety (Zero-Crash Guarantee for Closed Testing)**
  - [x] Added `recurring_pattern`, `ignored_packages`, and `learned_token` to both `statements` and `runMigrations` in `DatabaseDriverFactory.kt`.
  - [x] Verified existing user updates will automatically execute `CREATE TABLE IF NOT EXISTS` and `CREATE INDEX IF NOT EXISTS` via `executeQuietly` even if `.sqm` version steps were desynchronized.
- [ ] **Establish SQLDelight as the Single Source of Truth (Final Step for Production v1.0)**
  - [x] Freeze baseline schema across all `.sq` files.
  - [x] Add sequential migrations (`1.sqm`, `2.sqm`, `3.sqm`).
  - [ ] Configure sqlite-jdbc native linking for `./gradlew verifySqlDelightMigration` in Windows environment.
  - [ ] Safely deprecate raw DDL strings in `DatabaseDriverFactory.kt` once schema changes are frozen for public launch.

---

## 🛡️ Production & Closed Testing Readiness

- [x] **Zero Compiler Warnings in Shared & Android Modules**
  - [x] Fixed redundant elvis operator on non-nullable `month` in `TransactionRepositoryImpl.kt`.
  - [x] Resolved `UNCHECKED_CAST` warnings in `TransactionListViewModel.kt`.
  - [x] Handled `LocalClipboardManager` deprecation in `DebugLogScreen.kt`.
  - [x] Replaced deprecated `compose.materialIconsExtended` build accessor with pinned `org.jetbrains.compose.material:material-icons-extended:1.7.3` in version catalog.
- [x] **Android Lint Fatal Errors & Warnings Clean (Play Store Compliance)**
  - [x] Fixed all 4 `MissingPermission` errors for `POST_NOTIFICATIONS` runtime permission checks in `AndroidAnomalyNotifier.kt`, `BudgetAlertWorker.kt`, `QuickGlanceNotification.kt`, and `WeeklyDigestWorker.kt`.
  - [x] Created `data_extraction_rules.xml` and `backup_rules.xml` excluding SQLite database and SharedPreferences from cloud/device backups, satisfying Android 12+ DataExtractionRules requirements.
  - [x] Removed empty `res/drawable-v24` directory and resolved `ScreenshotHandler.kt` stream disposal warning.
  - [x] Configured focused lint options in `androidApp/build.gradle.kts`.
  - [x] Verified `./gradlew :androidApp:lintPlayDebug` reports **"No issues found." (0 errors, 0 warnings)**.
  - [x] Verified `./gradlew :androidApp:assemblePlayRelease` passes with **BUILD SUCCESSFUL** (full R8 minification, resource shrinking, and APK packaging).

---

## 🎨 UI/UX & Visual Design Modernization

- [x] **Unified Material 3 Design System & Tokens**
  - [x] Standard 4dp/8dp grid spacing tokens ([`Spacing.kt`](file:///c:/Users/ailik/AndroidStudioProjects/CashBuddy/shared/src/commonMain/kotlin/com/cashbuddy/presentation/theme/Spacing.kt)) integrated via `LocalSpacing` and `CashBuddyTheme`.
  - [x] Standard component library ([`PaisaPalComponents.kt`](file:///c:/Users/ailik/AndroidStudioProjects/CashBuddy/shared/src/commonMain/kotlin/com/cashbuddy/presentation/components/PaisaPalComponents.kt)):
    - `PaisaPalMetricCard`: High-end financial metric card with icons, accent tinting, and trend markers.
    - `PaisaPalFilterChip`: Animated interactive pill filter chip for dates, categories, and account filters.
    - `PaisaPalSectionHeader`: Uniform title header with clickable action text ("See All").
  - [ ] Implement adaptive Edge-to-Edge and responsive layouts across phones, foldables, and tablets.
  - [ ] Polish empty states, loading skeletons, and interactive micro-animations (e.g. chart reveals, swipe-to-confirm).

- [ ] **Screen-by-Screen UI Refactoring**
  - [x] **Dashboard / Home**: Modernized header, `BalanceHeaderCard` spacing, and integrated `PaisaPalSectionHeader`.
  - [x] **Transactions Screen**: Modernized period and type filter chips with `PaisaPalFilterChip`, animated borders, and clear selection states.
  - [ ] **Budget & Analytics**: Visual progress meters, category breakdown donuts/bar charts, and budget pacing warnings.
  - [ ] **Settings Screen**: Clean categorised sections (Data & Storage, Privacy & Security, Notification Listener, Personalization & Debug).

---

## 🏗️ Codebase Architecture & Code Quality Improvements

- [x] **Clean Architecture & Separation of Concerns**
  - [x] Enforce strict Unidirectional Data Flow (UDF) via StateFlow MVVM and MVI Reducers.
  - [x] Separate UI Composables into pure stateless components (`HomeContent`) and stateful screen wrappers (`HomeScreen`).
  - [x] Decouple domain UseCases / Interactors from UI ViewModels with distinct query pipelines.

- [x] **Repository & Data Layer Hardening**
  - [x] Audited repository implementations for consistent Flow dispatching (`Dispatchers.Default`) across all table mappings and suspending withContext operations.
  - [ ] Ensure all database exceptions are wrapped in typed Domain Results (`Result<T>`) rather than leaking raw SQLite exceptions to the UI.

- [ ] **Continuous Integration & Testing**
  - [ ] Add `./gradlew :shared:testAndroidHostTest` and `./gradlew verifySqlDelightMigration` into pre-commit / CI checks.
  - [ ] Add Compose Multiplatform screenshot/Roborazzi testing for key UI screens to prevent visual regressions during redesigns.
