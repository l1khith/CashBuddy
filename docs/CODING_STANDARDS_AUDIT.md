# Coding-Standards Audit — CashBuddy (2026-11)

Six parallel agents audited the codebase against `rules.md` and project conventions.
**Audit only — no files modified.** Scope: `shared/src`, `androidApp/src` (main source sets).

**Totals: 0 Critical · ~20 Major · ~35 Minor · ~15 Nit** (per-dimension detail below)

---

## Executive Priority List (deduped across agents, ranked)

| # | Issue | Location | Severity | Status | Recommended Fix |
|---|---|---|---|---|---|
| 1 | Raw OCR text (account numbers, VPAs, balances) logged to logcat in production — rules.md §1 rule 8 violation | `ScreenshotParser.kt:50,62` | Major | **Verified** | Remove raw string interpolation. Log character count only or gate strictly behind `DebugLogger`. |
| 2 | Screenshot fallback gate hardcodes 0.85/₹10,000 + `accountId = 1L`, bypassing `PolicyEngine` and user settings — second divergent gating site on a money path | `ScreenshotHandler.kt:171,178` | Major | **Verified** | Inject and invoke `PolicyEngine.evaluate(...)` so screenshot intake respects user threshold, default accounts, and anomaly checks. |
| 3 | CSV import silently coerces unparsable type to `DEBIT` — corrupts balance direction on a money-ingest path | `DataManagementUseCases.kt:104,111` | Major | **Verified** | Fail fast: record unparsable types as import errors (`failedCount++`) rather than silently defaulting to `DEBIT`. |
| 4 | Learning behavior diverges by screen: List edit records nothing (`TransactionListViewModel.kt:389`), Detail learns "Unknown Merchant" rules inconsistently (`TransactionDetailViewModel.kt:97` vs `:140`), AddTransaction double-learns (`AddTransactionViewModel.kt:279` + use case) and instantiates a fresh `CategoryEngine` fallback instead of the DI singleton | 3 ViewModels | Major | **Verified** | Route all category learning through a single unified use case (`ModifyTransactionUseCase` / `BatchCategorizeUseCase`), removing ad-hoc ViewModel rule insertions. |
| 5 | Confirmed→confirmed edit path fires NO balance trigger; protected only by the single compensation block in `TransactionRepositoryImpl.update` — any future direct caller silently drifts balances | `transactions.sq:63-108`, `TransactionRepositoryImpl.kt:146-164` | Major | **Verified** | Add trigger: `AFTER UPDATE OF amount, type, account_id ON transactions WHEN old.status = 'CONFIRMED' AND new.status = 'CONFIRMED'` to update account balance in SQL. |
| 6 | Test fakes: 10 near-identical `TransactionRepository` copies (+13 across 5 other interfaces); 2 copies stub `getByDateRange` to empty (BudgetViewModelTest:116, BudgetDrilldownScreenTest:49), `transactionUpdates` stubbed empty by ALL 10 | `commonTest/**` | Major | **Verified** | Consolidate into a single shared, reusable `FakeTransactionRepository` in a test-fixtures package (`commonTest/.../testing/FakeTransactionRepository.kt`). |
| 7 | Date-pinned test fixtures vs real clock: `BudgetViewModelTest` pins Oct-15-2026 while `BudgetViewModel` uses `currentTimeMillis()` — tests expire as the calendar rolls (already red-risk); `BudgetAlertSchedulerTest` shows the correct injectable-clock precedent | `BudgetViewModelTest.kt:240-354` | Major | **Verified** | Inject `clock: () -> Long = { currentTimeMillis() }` into `BudgetViewModel` constructor so tests can pin time deterministically. |
| 8 | JVM-only APIs (`JdbcSqliteDriver`, `System.*`, perf thresholds) inside `commonTest` — anchors the whole common test set to JVM | `LearnedTokenSafeguardsTest.kt` | Major | **Verified** | Move `LearnedTokenSafeguardsTest.kt` from `shared/src/commonTest/` to `shared/src/androidHostTest/` where JVM SQLite is supported. |
| 9 | DI: 12-arg positional `SettingsViewModel(get(), …)` + 4 more positional registrations; 12 `getOrNull()` chains (`anomalyNotifier` bound only in an ad-hoc app module — silently null elsewhere) | `AppModule.kt:88-114,176-197` | Major | **Verified** | Refactor to modern Koin constructor reflection: `viewModelOf(::SettingsViewModel)`, `viewModelOf(::PersonalizationViewModel)`. |
| 10 | Dispatcher defaults: 15 commonMain classes + 9 androidApp/androidMain sites hardcode `Dispatchers.*`; production DI currently masks the commonMain ones — latent footguns | `data/repository/*.kt`, `MainActivity.kt:118-363` etc. | Major | **Verified** | Inject `CoroutineDispatcher` everywhere via DI (`ioDispatcher` from `Platform.kt`) and remove hardcoded default parameters. |
| 11 | Money summed in raw `Double` while `Paise` type exists unused for sums; raw-Double ₹10,000 gate in `DedupEngine.kt:84`; 4 different paise-conversion idioms | repo-wide | Major | **Verified** | Standardize money calculations to use the `Paise` value class internally for sums/roundings, converting to `Double` rupees only at the UI boundary. |
| 12 | iOS database is plaintext SQLite (rule 4: "always encrypted") — deferred product decision (memory.md) | `IosDatabaseDriverFactory.kt:9-13` | Major | **Verified** | Integrate SQLCipher via CocoaPods/cinterop for iOS target or keep documented in `memory.md` until iOS release track is prioritized. |
| 13 | Stats/Detail/Home/duplicates orchestration in ViewModels; HLD-mandated `GenerateCategoryBreakdownUseCase`/`GetMonthlySummaryUseCase` don't exist | `StatsViewModel.kt:125-260` et al. | Major | **Verified** | Extract repository aggregations into domain use cases (`GetCategoryBreakdownUseCase`, `GetMonthlySummaryUseCase`). |
| 14 | Settings (~50 strings), PersonalizationDashboard (~27), DataView (~20) have zero/near-zero `stringResource`; ~15 hardcoded `"₹"` bypassing `CurrencyRegistry` | presentation/settings+personalization | Major | **Verified** | Extract all hardcoded strings into `strings.xml` and route currency formatting through `formatCurrency(...)`. |
| 15 | Composition size: 6 files >500 LOC; `SettingsScreen` = one ~1110-LOC composable; only 4 of 12 screens use the stateless `XxxContent` hoisting pattern; zero `@Preview`s | `SettingsScreen.kt:128-1239` | Major | **Verified** | Split `SettingsScreen.kt` into modular section composables (`AppCustomizationSection`, `AutomationRulesSection`, etc.) and adopt `XxxContent` hoist pattern. |

---

## Dimension 1 — Non-Negotiables (rules.md §1)

**Clean:** Rules 1 (no SMS), 2 (no network — all 3 manifests verified, force-stripped), 3 (pure-Kotlin commonMain, zero android/java imports), 7 (content-first gating, actively guarded by `ArchitectureSanityTest`), 8-partial (FLAG_SECURE applied, 40 log sites audited clean).

| Finding | Location |
|---|---|
| [Major] Raw OCR text logged (2 sites) | `ScreenshotParser.kt:50,62` |
| [Major] Screenshot fallback gate bypasses PolicyEngine + user settings + anomaly check; hardcoded `accountId = 1L` | `ScreenshotHandler.kt:171,178` |
| [Major] iOS plaintext SQLite driver | `IosDatabaseDriverFactory.kt:9-13` |
| [Minor] Threshold drift: PolicyEngine uses 0.90, rules/docs say 0.85, persisted setting `minConfidenceThreshold` never read by the gate | `PolicyEngine.kt:11-16`, `SettingsRepositoryImpl.kt:27` |
| [Minor] OCR base confidence pinned exactly at the auto-confirm boundary (0.85 + 0.05 bonus) | `ScreenshotParserEngine.kt:66-70` |
| [Minor] `DedupEngine` merge-clear gate hardcodes 0.90/10000 | `DedupEngine.kt:84` |
| [Minor] Biometric lock is opt-in default-off vs rule 6 "require" (flagged as product decision) | `MainActivity.kt:105-113` |
| [Minor] `System.loadLibrary("sqlcipher")` silent swallow — missing native lib fails later, confusingly | `AndroidDatabaseDriverFactory.kt:16-19` |
| [Minor] Ungated `println` in commonMain (schema notices) — funnel through `DebugLogger` | `DatabaseDriverFactory.kt:38,41,54,57,66` |
| [Minor] Add CI merged-manifest check asserting INTERNET absent (guards ML Kit transitive perms) | `libs.versions.toml:76` |

## Dimension 2 — Architecture, UDF & Layering

**Clean:** hard layer prohibitions hold (0 presentation→data, 0 domain→data/presentation, 0 core→presentation); all 11 ViewModels keep mutable flows private; `ReviewViewModel` is the model UDF citizen; no duplicate DI bindings.

| Finding | Location |
|---|---|
| [Major] Positional `get()` ViewModel registrations (5 sites, worst 12 args — silent mis-wiring risk) | `AppModule.kt:176,178-185,194,195,197` |
| [Major] 12 `getOrNull()` chains hide wiring failures (`anomalyNotifier` bound only in ad-hoc `assistantModule`) | `AppModule.kt:88,89,112-114,183,194,195,197` |
| [Major] Use-case divergence: see Priority #4/#13 | — |
| [Minor] `// NO-NETWORK` header decorative: 66/174 files — normalize or scope-and-document | repo-wide |
| [Minor] BudgetViewModel exposes 3 public StateFlows duplicating `BudgetUiState` (dead, drift-prone) | `BudgetViewModel.kt:47-56` |
| [Minor] SettingsViewModel `_pending*` drafts written from 4 paths, last-write-wins | `SettingsViewModel.kt:85-106` |
| [Minor] DebugLogViewModel SharedFlow lacks `extraBufferCapacity` (siblings all have 1) | `DebugLogViewModel.kt:75` |
| [Minor] PersonalizationViewModel duplicates non-atomic wipe fallback + export code (SettingsViewModel has both too) | `PersonalizationViewModel.kt:169-203` |
| [Nit] Inline FQN core references in use cases; DI import ordering; dead imports | `TransactionActionUseCases.kt:17-23`, `AppModule.kt:24` |

## Dimension 3 — Kotlin Idioms & Concurrency

**Clean:** all 15 SQLDelight transaction bodies free of suspend calls/hops; `runBlocking` only in the documented allowed wrapper; no `GlobalScope`; zero TODO/FIXME in main source.

| Finding | Location |
|---|---|
| [Major] Dispatcher defaults (see Priority #10); `DebugLogRepositoryImpl` secondary ctors explicitly pass `Dispatchers.Default` | `DebugLogRepositoryImpl.kt:19-20` + list in agent report |
| [Major] CSV import DEBIT coercion (Priority #3) | `DataManagementUseCases.kt:104,111` |
| [Major] Double-as-ledger (Priority #11); `amountPaise` derived per-access from Double | `Transaction.kt:45-46` |
| [Minor] `DebugConfig` creates eager uncancelled scope + collector at construction | `DebugConfig.kt:15-29` |
| [Minor] `getAllSignificantSync()` swallows all Throwables → learned-token evidence silently disabled | `LearnedTokenRepositoryImpl.kt:176` |
| [Nit] 3 unnecessary `!!` after adjacent null checks | `TransactionDetailScreen.kt:118,350`, `ReviewScreen.kt:297` |
| [Nit] Dead commented-out injection line | `TransactionNotificationListener.kt:33` |
| [Nit] Boolean `is`-prefix drift; `UserSettings.biometricEnabled` vs VM `biometricLockEnabled` renames | `SettingsViewModel.kt`, `UserSettings.kt` |

## Dimension 4 — SQLDelight & Data Layer

**Clean:** all 18 INSERT + 13 UPDATE mapping args match column-for-column (named-args protected); timestamps/amounts typed consistently; column naming uniform snake_case; existing FKs + `ON DELETE CASCADE` correct; balance triggers cover insert/confirm/demote/delete paths.

| Finding | Location |
|---|---|
| [Major] Confirmed→confirmed edit path: no trigger fires; single compensation site (Priority #5) | `transactions.sq:63-108` |
| [Major] Schema v4 exists only via Android-only runtime patcher; no `4.sqm`; iOS upgrade path would crash if wired (documented open decision) | `DatabaseDriverFactory.kt:406-519` |
| [Major] FK gaps: `merged_into_id` (mitigated in code), `raw_messages.resulting_tx_id` (TEXT vs INTEGER — FK impossible), `budgets.category` name-based soft ref | `transactions.sq:18`, `raw_messages.sq:10`, `Budget.sq:3` |
| [Minor] No `amount` index for `findDuplicateCandidates` (full scan + sort every dedup pass) | `transactions.sq:248-255` |
| [Minor] Fallback DDL missing `idx_transactions_keyset` — third drifting schema copy | `DatabaseDriverFactory.kt:306-332` |
| [Minor] Boolean adapter inconsistency (`is_merged`/`is_loan_given`/`budgets.is_active` raw INTEGER vs `AS Boolean` elsewhere) | `transactions.sq:17,20`, `Budget.sq:7` |
| [Minor] No FTS backing for leading-wildcard searches (known deferred item) | `transactions.sq:373,392` |

## Dimension 5 — Compose UI Standards

**Clean:** all 12 screens collect state once via `collectAsStateWithLifecycle`; 43 composables thread `modifier` correctly; no nested-weight misuse; decorative `contentDescription = null` uses acceptable; touch targets compliant.

| Finding | Location |
|---|---|
| [Major] Localization split-brain (Priority #14) | settings+personalization cluster |
| [Major] Composition size (Priority #15); near-duplicate `StatsCustomDateRangeDialog` vs `TransactionListScreen` dialog | — |
| [Minor] `Color.White` ×25 instead of `colorScheme.onPrimary`; static palette vals ×35; Spacing tokens used in only 2 files | 9+ files |
| [Minor] Clickable rows/cards lack Role/state semantics (10+ sites); `TransactionItemCard` should adopt `BudgetCard`'s semantics pattern | `TransactionItemCard.kt:105-113` |
| [Nit] Dead `formatTimestamp()` duplicate; `BudgetCard` global-card no-op focus target | `TransactionItemCard.kt:260`, `BudgetCard.kt:129` |

## Dimension 6 — Testing Standards

**Clean:** ~97% `testXxx` naming consistency; no assertion-free tests; no sleeps; stateful singletons audited stateless; no `android.*` imports in commonTest.

| Finding | Location |
|---|---|
| [Major] Fake duplication + stale stubs (Priority #6); perf-threshold assertions flaky (Priority #8) | — |
| [Major] Money-path coverage holes: `TransactionIngestStore`, all DB repo impls except 3, every domain use case, 9 of 11 ViewModels, `androidApp` has zero tests | — |
| [Minor] `getMonthlySummary` empty-stubbed in all 10 fakes — Stats monthly flow untested anywhere | — |
| [Minor] 0 `runTest`/virtual-time usage — no safety net for future timing tests | — |
| [Nit] Tautological "ScreenTest" state tests; dangling `assertTrue(true)`; `UtrRegexTest` naming outlier | `BudgetListScreenTest.kt:16-25`, `SqlDelightMergeStoreTest.kt:163` |

---

## Recommended Fix Order

**Quick wins (same-day):** #1 (2 lines), #8 (move file to androidHostTest), #9 (mechanical DI rewrite to `viewModelOf`), stale-fake stubs (#6 partial), dangling `assertTrue(true)`, dead code lines.

**This sprint (money integrity):** #2 (route fallback through PolicyEngine), #3 (CSV error-out), #5 (trigger pair or repository-only comment), #4 (unify edit learning through use cases), #6 (shared test fixture package `commonTest/.../testing/`), #7 (inject clock into BudgetViewModel).

**Roadmap:** #10 dispatcher defaults, #11 Paise ledger migration, #13 use-case extraction per HLD, #14/#15 UI localization + decomposition, #12 iOS SQLCipher (needs product decision).
