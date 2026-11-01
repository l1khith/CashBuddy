# CashBuddy (PaisaPal) — Complete Consolidated Audit (All Sources, One File)

**Date:** 2026-10-28 · **Scope:** read-only · **No code was modified.**
**Sources merged:** (1) Core audit — engine/parser/DB/repositories/ViewModels (main agent); (2) Platform-layer audit — androidMain/iosMain/workers/widget (subagent B); (3) Presentation-layer audit — Compose UI (subagent A).
Citations use exact file paths + line numbers. Unverifiable-from-source items are marked **UNVERIFIED**.

---

# PART 1 — CORE FINDINGS

## 1.1 Executive Summary

- **The data layer is not crash-safe.** The three highest-value write paths — single-message ingest (`MessagePipeline.kt:76-338`), duplicate merge (`DuplicateReconciler.kt:184-223`), and transaction edit (`TransactionRepositoryImpl.kt:107-149`) — all perform multi-table read-modify-write sequences with **no SQLDelight transaction wrapper**. A crash mid-sequence leaves the encrypted DB permanently inconsistent, and the manual account-balance bookkeeping races (TOCTOU) with concurrent writes.
- **Encryption is promised but only half delivered, and the schema is hand-duplicated.** SQLCipher+Keystore is correctly wired on Android (`AndroidDatabaseDriverFactory.kt:21-25`) but **iOS uses an unencrypted plain driver** (`IosDatabaseDriverFactory.kt:9-12`), violating the project's non-negotiable AES-256 rule. `DatabaseDriverFactory.kt:23-455` re-implements the whole schema as hand-baked `executeQuietly` DDL/ALTERs that **swallow failures and have diverged** (merge_log FK cascade present in `transactions.sq:38-39`, absent in runtime DDL `DatabaseDriverFactory.kt:221-229`).
- **Dead/vacuous subsystems inflate cost and risk.** The `goals` table, `AccountEngine`, the classifier's observe/calibration loop (`signal_observations` — `observe()` never called), and the **entire iOS export/import feature (fake-success no-ops)** run as dead or silently-broken code. Combined with a 324-line `MessagePipeline` god-class (18 constructor deps), the codebase is materially harder to reason about than its feature set requires.

## 1.2 Critical Bugs

### C1. No transactional atomicity in `MessagePipeline.ingest()`
`shared/src/commonMain/kotlin/com/cashbuddy/core/prob/MessagePipeline.kt:76-338`
Sequence executes as independent suspend calls; a `Mutex` serializes but is **not atomic**:
1. raw_message insert → tx insert → `rawMessageRepo.updateResultingTx` (audit link)
2. anomaly `updateNotes` / `updateStatus`
3. budget alerts
4. recurrence `upsertAll` (L266-277)

Crash between steps → raw_message pointing at half-written tx, tx without accounting updates, budgets/patterns stale. Irreparable except via the duplicates dialog.

### C2. Non-atomic duplicate merge + balance TOCTOU
`shared/src/commonMain/kotlin/com/cashbuddy/core/DuplicateReconciler.kt:184-223`
`markMerged` + `insertMergeLog` + **read-modify-write of account balance** via `accountRepository.getById().firstOrNull()` per duplicate group — no DB transaction. Concurrent merge (or merge racing an edit) loses balance deltas; balances are `REAL` maintained in app code + SQL triggers → lost delta is silently permanent.

### C3. Non-atomic transaction edit with manual balance bookkeeping
`shared/src/commonMain/kotlin/com/cashbuddy/data/repository/TransactionRepositoryImpl.kt:107-149`
Read old tx → UPDATE → manual account-balance delta, not atomic. Correctness depends on *coexistence of hand-written SQL triggers* (`transactions.sq`; fire only on status transitions) and this manual path (covers CONFIRMED→CONFIRMED edits). Any future trigger/field edit can silently double- or zero-count balance effects.

### C4. Budget alert loss — first-wins bug
`shared/src/commonMain/kotlin/com/cashbuddy/core/budget/BudgetAlertScheduler.kt:98-117`
`markAlerted` runs for **all** alerting budgets, but the notification branch is gated `if (firedEvents.isEmpty())` (L107) — only the **first** budget is notified; all others get `lastAlertState` set permanently and **never** alert. Silent permanent alert loss on a core safety feature.

### C5. iOS database NOT encrypted (non-negotiable violation)
`shared/src/iosMain/kotlin/com/cashbuddy/data/local/IosDatabaseDriverFactory.kt:9-12`
Plain `NativeSqliteDriver(...)` — no SQLCipher, no passphrase. Android chain is correct (`AndroidDatabaseDriverFactory.kt:21-25`, `SupportOpenHelperFactory` + Keystore passphrase `AndroidKeystoreManager.kt:74-81,129-143`, passphrase zeroized at :35), but `System.loadLibrary("sqlcipher")` failure is **silently swallowed** (`AndroidDatabaseDriverFactory.kt:16-19` try/catch swallow) → missing native lib degrades at first query instead of failing fast.

### C6. Hand-baked schema diverges from SQLDelight schema; failures swallowed
`shared/src/commonMain/kotlin/com/cashbuddy/data/local/DatabaseDriverFactory.kt:23-29, 221-229, 360-455`
- `ensureSchema` duplicates the entire DDL with `executeQuietly` try/catch-swallow (L23-29) — DDL failures invisible.
- Verified divergence: `merge_log` FK `ON DELETE CASCADE` in `transactions.sq:38-39` but omitted in the runtime hand DDL (L221-229) — two sources of truth.
- `runMigrations` (L360-455) = silent idempotent ALTERs; **no `user_version` migration chain**.

### C7. CSV import wrong category + no dedup
`shared/src/commonMain/kotlin/com/cashbuddy/domain/usecase/DataManagementUseCases.kt:120`
`categoryId = 1L` hardcoded comment "Default General", but **category 1 in seed data is `Food & Dining`**. No transaction wrapper; **no dedup** → re-import doubles every row.

### C8. Vacuous prediction in manual add + hardcoded `1L` fallbacks
`shared/src/commonMain/kotlin/com/cashbuddy/presentation/addtransaction/AddTransactionViewModel.kt:187-188`
Creates a **brand-new empty `com.cashbuddy.core.CategoryEngine()`** (no rules loaded) then queries it — prediction can never match; bypasses the DI singleton. `AddTransactionScreen.kt:455-456` hardcodes `?: 1L` category/account fallbacks → silently "Food & Dining".

### C9. Notification raw-ID PK collisions drop messages
`shared/src/androidMain/kotlin/com/cashbuddy/service/TransactionNotificationListener.kt:170`
raw id `"notif_${postTime}_${(1000..9999).random()}"` — same-millisecond collision → insert throws → outer catch **drops the whole message**. Review-alert notification id `1000 + txId % 1000` (L277) collides across >1000 txs.

### C10. `clearAllData` clears only one table
`shared/src/commonMain/kotlin/com/cashbuddy/presentation/settings/SettingsViewModel.kt:219-225`
Deletes `transactions` only; account balances (app-maintained), budgets, raw_messages, learned tokens, merchant rules left stale/inconsistent.

## 1.3 DDIA & Database Risks

| # | Finding | Citation |
|---|---|---|
| D1 | **Money as REAL** — floating-point currency; correct `amount_paise` INTEGER exists only for `recurring_pattern` | `transactions.sq:3` |
| D2 | Non-sargable `(c.name = :category OR CAST(t.category_id AS TEXT) = :category)` in `sumByCategory` / `getByCategoryAndPeriod` | `transactions.sq:272,285` |
| D3 | Keyset pagination correct (`idx_transactions_keyset` L31) BUT cursor guard `(:cursorTimestamp IS NULL OR :cursorId IS NULL OR …)` silently disables pagination if only one param passed; `LIKE '%…%'` on raw_text unindexable | `transactions.sq:316-332` |
| D4 | `findByUtr`: UTR not a column → 60-day window scan + `Regexes.UTR` regex per row; no index possible | `TransactionRepositoryImpl.kt:302-314` |
| D5 | `mapTransaction` runs UTR regex **twice per row on every Flow emission** | `TransactionRepositoryImpl.kt:316-366` |
| D6 | Aggregates without LIMIT (`getAll`, `getPending`, `getByCategory`, `getHistorySummary`); `getMonthlySummary` strftime GROUP BY over full table — OOM/CPU at 50k+ txs | `transactions.sq` |
| D7 | Unbounded startup scan: `HomeViewModel.init` calls `getAll()` when patterns >24 h stale | `HomeViewModel.kt:37-56` |
| D8 | Recurrence re-detection triggers `getAll()` full scans (every 10th confirmed tx + HomeViewModel) | `MessagePipeline.kt:266-277` + HomeViewModel |
| D9 | In-memory dedup loads 24h/5min window transactions **per incoming message**, regex per candidate row | `MessagePipeline.kt:145-159` |
| D10 | `transactions.category_id` FK has no ON DELETE action → deleting referenced category throws; repos expose no cascade/reassign/delete path — user-blocking state | `transactions.sq` |
| D11 | Budget/status columns maintained by hand-written triggers **and** app code — dual source of truth, derived-data drift risk | §C3 |
| D12 | `getSignificant` (`>=5 observations`) query exists but unused; `getAllSignificantSync` loads the **entire** learned_token table → contract violation (interface L16-18); single-observation tokens get extreme smoothed LR (1.0) — degrades classification | `learned_token.sq:18-20`, `LearnedTokenRepositoryImpl.kt:49-61` |
| D13 | `LikelihoodCalibrator.observe()` never called in production → `signal_observations` always empty; `calibratedLR` still does ~18 suspend DB round-trips per classified message (dead-in-effect N+1) | `LikelihoodCalibrator.kt` |
| D14 | BudgetEngine N+1: per budget → sumAll/sumByCategory + getByDateRange/getByCategoryAndPeriod full period list, then O(patterns×txs) matching loop; `BudgetViewModel.loadBudgets` calls `statusFor` per budget | `BudgetEngine.kt:58-77`, `BudgetViewModel.kt:61-63` |

## 1.4 Edge-case logic (verified)

- UTR regex accepts 8-35 chars (`Regexes.UTR`); `ScreenshotParserEngine` strict 12-digit only in Priority 1, falls back — inconsistent but not unsafe.
- Tokenizer on empty string → `emptySet` (`Tokenizer.kt:40`) ✅.
- Amount 0.00 → pipeline ignores (`MessagePipeline.kt:138-139`); manual save disabled (`AddTransactionScreen.kt:451-468`) ✅ — but see C8 `1L` fallbacks.
- Timezones: budget/date paths use `kotlinx-datetime` + `TimeZone.currentSystemDefault()` ✅; "n ago" strings (`TransactionItemCard.kt:228-238`) and day-bucket fingerprints use raw epoch (day shift with timezone travel).
- `RecentStateRepository` in-memory `ConcurrentHashMap` (iOS actual: AtomicReference COW map) → duplicate-state dedup **lost on process death**.
- `BatchCategorizeUseCase.kt:35` — `1.0f >= minConfidence` constant-true dead condition; then `update()` + `updateStatus()` double-write; `getPending().firstOrNull()` loop unbounded (no batch cap).
- `SourceDetector.detect` runs twice per notification (`MessagePipeline.kt:84` + listener `L182`).

## 1.5 Hardcoded English / vocab constants (OCP fail-sites)

- `BudgetAlertScheduler.kt:70-96` (alert strings)
- `TransactionItemCard.kt:200-218` (icon keyword lists)
- `CategoryEngine` "Unknown"/generic token sets
- `ScreenshotParserEngine.kt:296` (`isSystemLabel`/"UPI Merchant" sentinels), `typealias bool` hack at `:332/356`
- `DebugLogger.kt:117-124` (`isLikelyFinancial`)
- Manual-entry sentinels in `AddTransactionScreen.kt`
- `PersonalizationViewModel.kt:91-96` — fabricated accuracy: `coerceIn(75,100)`, default 96 (not measured)
- **Rule tension:** `ScreenshotParserEngine`'s "UPI Merchant" publisher sentinel skirts the "100% content-first, zero hardcoded sender allowlists" rule (label heuristic, not bank sender — flag for review; boundary itself enforced by `ArchitectureSanityTest.kt` which bans bank sender-ID strings).

---

# PART 2 — PLATFORM-LAYER AUDIT (Subagent B, full)

## 2A. AndroidMain / iosMain CoroutineScope leaks

1. **`androidApp/src/main/kotlin/com/cashbuddy/widget/CashBuddyWidgetReceiver.kt:18`** — `CoroutineScope(Dispatchers.IO).launch { glanceAppWidget.updateAll(context) }` inside `onReceive` (:15). Anonymous, unretained, uncancellable; not tied to `goAsync()` → boot-time widget update can be killed with the process; scope object abandoned per receive. `super.onReceive` (:16) already manages Glance updates → **duplicated work**.
2. **`androidApp/src/main/kotlin/com/cashbuddy/notification/AndroidAnomalyNotifier.kt:26`** — `private val scope = CoroutineScope(Dispatchers.IO)` in a **Koin singleton** (`CashBuddyApplication.kt:27-31`). Never cancelled, **no `SupervisorJob`** → one uncaught failure cancels the scope for all future anomaly alerts; body try/catch (:91) mitigates but fragile.
3. **`androidApp/src/main/kotlin/com/cashbuddy/notification/AnomalyActionReceiver.kt:33`** — per-`onReceive` scope; acceptable because `goAsync()`'s `pendingResult.finish()` is in `finally` (:42); abandoned without cancel (transient per-receive leak).
4. **`androidApp/src/main/kotlin/com/cashbuddy/CashBuddyApplication.kt:22`** — `applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)` never cancelled; infinite `collect` at :43-58 and registered BroadcastReceiver (:61-78) live for the whole process, never unregistered. Technically acceptable for Application; flagged.
5. **✅ Clean:** `shared/src/androidMain/kotlin/com/cashbuddy/service/TransactionNotificationListener.kt:31` — scope created and **cancelled in `onDestroy` at :87**. Workers create no scopes (`BudgetAlertWorker.kt:33`, `WeeklyDigestWorker.kt:28`). `QuickGlanceNotification.kt:27` suspend-only. `CashBuddyWidget.kt:41` standard Glance.

## 2B. Context-in-singleton issues

All hold typed `Context` vals resolving to the **Application** context (`CashBuddyApplication.kt:35`) — no Activity observed anywhere (**EXACT Koin resolution instance UNVERIFIED**, no Activity usage found):

| Class | Field | Singleton wiring |
|---|---|---|
| `shared/src/androidMain/kotlin/com/cashbuddy/security/AndroidKeystoreManager.kt:16` | context | `AndroidModule.kt:11` |
| `shared/src/androidMain/kotlin/com/cashbuddy/platform/AndroidFileExporter.kt:13` | context | `AndroidModule.kt:12` |
| `shared/src/androidMain/kotlin/com/cashbuddy/data/local/AndroidDatabaseDriverFactory.kt:10` | context | `AndroidModule.kt:13` |
| `androidApp/src/play/kotlin/com/cashbuddy/ocr/MlKitOcrEngine.kt:15-17` | **dead-weight field** `@Suppress("UNUSED_PARAMETER")` | — |
| `androidApp/src/fdroid/kotlin/com/cashbuddy/ocr/TesseractOcrEngine.kt:21-23` | context | `fdroid/.../di/PlatformModule.kt:9` |
| `androidApp/src/main/kotlin/com/cashbuddy/notification/AndroidAnomalyNotifier.kt:22` | context | `CashBuddyApplication.kt:28-30` |
| `shared/src/androidMain/kotlin/com/cashbuddy/platform/AndroidPlatform.kt:8` | **global static `appContext: Context?`** set `CashBuddyApplication.kt:82`; stores applicationContext only (:10-12) — no Activity leak, process-lifetime static | — |

✅ Clean: `ClipboardMonitor.kt:8` (object, context param only, not stored); `ScreenshotHandler.kt:20` constructed per share intent (`MainActivity.kt:327`), never stored.

## 2C. expect/actual contract verification

Expects at `shared/src/commonMain/kotlin/com/cashbuddy/platform/Platform.kt:3-9` (4 functions) — all actuals exist, but:

- **`shared/src/iosMain/kotlin/com/cashbuddy/platform/IosFileExporter.kt:4-15` — fake success:** `exportCsvFile` returns `Result.success("Exported: $fileName")` **without writing anything** (:4-6); `exportFileStream` calls `writer {}` (:13) and **silently discards all content**. Users believe exports succeeded.
- **`FilePicker.ios.kt:10`** — `rememberFilePicker` returns `{}` → import feature dead no-op on iOS.
- **`IosPlatform.kt:8`** — `getAppVersionDisplay()` hardcoded `"CashBuddy iOS"` (Android actual `AndroidPlatform.kt:21` uses real values).
- **`IosPlatform.kt:12-14`** — `notifyTransactionsUpdated()` no-op on iOS. Correctly wired on Android: called from common VMs — `TransactionDetailViewModel.kt:83,125,158`; `AddTransactionViewModel.kt:281`; `ReviewViewModel.kt:101,110,131,146,177` — consumed at `CashBuddyApplication.kt:61-78`.
- **`BackHandler.ios.kt:6-8`** — documented deliberate no-op (acceptable).
- `DatabaseDriverFactory`: both actuals exist (`AndroidDatabaseDriverFactory.kt:10`, `IosDatabaseDriverFactory.kt:7`). FileExporter/FilePicker/BackHandler correctly expect/interface per `FileExporter.kt:3`, `FilePicker.kt:6-9`, `BackHandler.kt:6`.
- **Common-code trap: `FileExporter.kt:19`** — default `exportFileStream` delegates to `exportCsvFile(fileName, "")` → **empty file** if not overridden.

## 2D. Workers / widget blocking I/O

- **🔴 High — main-thread blocking I/O + sleeps (ANR risk):** `androidApp/src/main/kotlin/com/cashbuddy/MainActivity.kt:147,227` call `handleIncomingShareIntent` (main) → `:327-348` constructs `ScreenshotHandler(this)` and calls `copyUriToCache(...)` synchronously. Inside `androidApp/src/main/kotlin/com/cashbuddy/screenshot/ScreenshotHandler.kt:38-72`: blocking `contentResolver.openInputStream` + `copyTo(FileOutputStream)` (:49-56) and `Thread.sleep` retry loop (:45 initial `150L`, :67 sleep, :69 doubling, 5 attempts) — **up to ~4.65 s of UI-thread stalls** → jank/ANR on OEM paths. Only `processScreenshots` is guarded (`:78 withContext(Dispatchers.IO)`).
- **Medium:** `FilePicker.android.kt:24,30-32` — `contentResolver.query` + `openInputStream…readText()` inside the ActivityResult callback on the **main** thread.
- **✅ Clean:** `BudgetAlertWorker.doWork` (`BudgetAlertWorker.kt:47-131`) and `WeeklyDigestWorker` (`WeeklyDigestWorker.kt:40-81`) are suspend `CoroutineWorker`s, no `.get()`/`runBlocking`; `QuickGlanceNotification.update` (`QuickGlanceNotification.kt:36-121`) suspend on applicationScope; `CashBuddyWidget.provideGlance` wraps DB reads in `withContext(ioDispatcher)` (`CashBuddyWidget.kt:48`). Grep: no `runBlocking`/`.get()` in production (tests only).

## 2E. SQLCipher verification

- **Android — VERIFIED.** `AndroidDatabaseDriverFactory.kt:8` imports `net.zetetic.database.sqlcipher.SupportOpenHelperFactory`; `:21-25` builds `AndroidSqliteDriver(schema, context, "cashbuddy.db", SupportOpenHelperFactory(passphrase.copyOf()))`; passphrase from hardware Keystore (`:12-13`, GCM cycles `AndroidKeystoreManager.kt:74-81,129-143`); zeroized `:35`. **Concern:** `:16-19` — `System.loadLibrary("sqlcipher")` in `try/catch(_: Throwable)` **swallows load failure**; missing native lib degrades at first query instead of failing fast (prod-build reachability UNVERIFIED).
- **iOS — NOT ENCRYPTED.** `IosDatabaseDriverFactory.kt:9-12` — plain `NativeSqliteDriver(...)`. The AES-256 GCM guarantee **holds only on Android**.

## 2F. Dispatchers cross-cutting

All 14 SQLDelight repo impls hardcode `Dispatchers.Default` (e.g. `SqlDelightBudgetRepository.kt:18`, `TransactionRepositoryImpl.kt:24`, `SettingsRepositoryImpl.kt:17`, `AccountRepositoryImpl.kt:20`) wrapping DM work in `withContext(dispatcher)` (e.g. `SqlDelightBudgetRepository.kt:47,80,92`). Result: DB work from the listener (IO scope), workers, QuickGlance actually executes on **Default**, not the platform `ioDispatcher` (`AndroidPlatform.kt:23` = IO, `IosPlatform.kt:10` = Default) — inconsistent with the codebase's own platform abstraction; `main.async {}` used nowhere.

**Platform priority ranking:** 1. Main-thread blocking I/O+sleep (2D) · 2. Unencrypted iOS DB (2E) + fake-success iOS export/picker (2C) · 3. Silent sqlcipher load + Dispatchers.Default-vs-ioDispatcher (2F) + unsupervised never-cancelled anomaly scope (2A.2) · 4. Unretained widget-receiver scope (2A.1) + hardcoded iOS version (2C).

---

# PART 3 — PRESENTATION-LAYER AUDIT (Subagent A, full)

*Usage searches covered every tracked `*.kt` file in the repo (199 files), incl. app + test sources.*

## 3A. Composables defined but never invoked

- `shared/src/commonMain/kotlin/com/cashbuddy/presentation/components/PaisaPalComponents.kt:44` — **`PaisaPalMetricCard`**: the only occurrence of its name in the entire codebase is its own definition. Dead (not referenced even in tests).
- All 55 other extracted composables (`CashBuddyApp` down to `DetailRow`, `BarNavItem`, `EvidenceChip`, `ConfidenceBar`…) have non-test call sites. **Zero test/preview references to any presentation composable** (`@Preview` count project-wide: 0) — the "used only by tests" category is empty.

## 3B. Lazy lists & `key =` usage (complete per-file matrix)

| File | Lazy call | Line | key? | Verdict |
|---|---|---|---|---|
| TransactionListScreen.kt | `items(...)` | 316–319 | `key = { it.id }`, `contentType = { it.type }` | ✅ GOOD |
| TransactionListScreen.kt | `item(...)` | 330 | `"loading_more_indicator"` | ✅ GOOD |
| HomeScreen.kt | `item {` | 128, 159, 169, 232 (cond.), 296, 306 (cond.) | none | ⚠ Keyless; conditional items at 232/306 shift positions of all following items → state loss / full recomposition possible. Add stable keys |
| HomeScreen.kt | `items(...)` | 316 | `{ it.id }` | ✅ GOOD |
| StatsScreen.kt | `item {` | 88, 99 (cond.), 121, 179, 189 (cond.) | none | ⚠ Positional-shift concern (cond. at 99, 189) |
| StatsScreen.kt | `items(...)` | 198 | `{ it.categoryName }` | ✅ GOOD (names assumed unique) |
| DebugLogScreen.kt | `items(...)` | 305–307 | `{ it.id }` | ✅ GOOD |
| DebugLogScreen.kt | `item(...)` | 317 | `"loading_more"` | ✅ GOOD |
| BudgetListScreen.kt | `item(...)` ×5 | 230, 243, 259, 262 | `"global_${...}"`, `"header_categories"`, `"header_unbudgeted"`, `"unbudgeted_card"` | ✅ GOOD (best exemplar) |
| BudgetListScreen.kt | `items(...)` | 247 | `{ it.budget.id }` | ✅ GOOD |
| BudgetDrilldownScreen.kt | `items(...)` | 200–202 | `{ it.id }` | ✅ GOOD |
| SettingsScreen.kt | `LazyColumn` @120, ~14 static `item {` | 127, 139, 147, 267, 275, 308, 316, 434, 442, 538, 546, 652, 660, 717 | none | ⚠ Keyless static; mostly unconditional → lower risk |
| SettingsScreen.kt | nested `items(...)` (Ignore List) | 882–884 | `{ it }` (package string) | ✅ GOOD |
| PersonalizationDashboardScreen.kt | `item {` | 133, 221, 320, 329 (cond.) | none | ⚠ Keyless |
| PersonalizationDashboardScreen.kt | `items(...)` | 364 | `{ it.id }` | ✅ GOOD |
| AddTransactionScreen.kt | — | — | N/A | `verticalScroll` (:163) |
| ReviewScreen.kt | `items(...)` | 236 | `{ it.id }` | ✅ GOOD |
| ReviewScreen.kt | `item {` | 126, 178, 203 (cond.), 228 (cond.) | none | ⚠ Cond. items at 203/228 shift positions |
| TransactionDetailScreen.kt | — | — | N/A | Column + `verticalScroll` (:110) |
| DataViewScreen.kt | `items(...)` | 185, 202 | `"corr_${it.id}"`, `"raw_${it.id}"` | ✅ GOOD |
| DataViewScreen.kt | `item` | 177, 193 (cond. headers), 209 (`"loading_more"`), 226 (cond.) | 209 keyed; 177/193/226 keyless | ⚠ Cond. headers keyless |
| EditTransactionDialog.kt / DuplicateReconciliationDialog.kt | — | EditDialog N/A; Dialog `itemsIndexed` @156–158 | `key = { _, g -> g.survivor.id }` | ✅ GOOD |

## 3C. Unremembered computation inside composables (per-recomposition allocations)

- **🔴 `BudgetCard.kt:101` — worst offender:** `val now = com.cashbuddy.platform.currentTimeMillis()` inside `if (isGlobal)` composable body, not remembered → `Instant.fromEpochMilliseconds` + `TimeZone.currentSystemDefault()` + `toLocalDateTime` + 1–3 `LocalDate` constructions **per recomposition of every budget card** (rendered at `BudgetListScreen.kt:231, :248`).
- **`TransactionItemCard.kt:150`** — `"$categoryName • ${formatTimestamp(transaction.timestamp)}"`: `formatTimestamp` (:228–229) reads `currentTimeMillis()` **per recomposition of every visible card** (used at `TransactionListScreen.kt:321`, `HomeScreen.kt:317`, `BudgetDrilldownScreen.kt:204`); plus interpolation.
- **`TransactionItemCard.kt:127`** — `painterResource(getCategoryDrawable(...))`: `getCategoryDrawable` (:200–218) = `lowercase()` + ~15 `contains()` per recomposition (painter cached; string churn not).
- **`TransactionItemCard.kt:164`** — `formatCurrency(...)` recomputed per recomposition (minor).
- **`ReviewScreen.kt:114-116` — most material UI finding:** `state.transactions.filter { it.confidence >= state.selectedConfidenceFilter }` runs on **every recomposition of the whole screen**, allocating a fresh list before the LazyColumn (:236) reads it. Fix: `remember(state.transactions, state.selectedConfidenceFilter)` or derive in VM/reducer.
- **`BudgetCard.kt:78–87`** — `when` builds `Triple(color, painterResource(...), formatCurrency-string)` per recomposition; **`:133`** re-concatenates a11y `contentDescription` with 3× `formatCurrency` per recomposition.
- Minor: `StatsScreen.kt:197` (`sumOf {...}.coerceAtLeast(1.0)` O(n) per recomposition); `HomeScreen.kt:309` (`"No activity for ${...displayName.lowercase()}"`); `TransactionListScreen.kt:515` (`currentTimeMillis()` in the dialog body — clock read per recomposition, cheap but uncached).

✅ **Correct remember patterns already in-tree (copy these):** `BalanceHeaderCard.kt:257` — `remember(animatedBalance, isVisible, isNegative) { formattedBalance }`; `TransactionListScreen.kt:97–107` — `rememberLazyListState` + `remember { derivedStateOf {...} }` load-more + `LaunchedEffect(shouldLoadMore)`; `BudgetEditSheet.kt:76–95`; `EditTransactionDialog.kt:73–78`; `CashBuddyApp.kt:60–61, :309`.

**UNVERIFIED:** whether the Compose compiler memoizes item-scope lambdas (e.g. `onClick = { onNavigateToDetail(tx.id) }` at `TransactionListScreen.kt:322–325`) depends on compiled stability metadata — not inspectable from source. The string/alloc issues above occur regardless.

## 3D. SharedFlow / Scaffold-effect consumption

**No `collectAsStateWithLifecycle` is used on any SharedFlow anywhere** — all one-off effects consumed via `LaunchedEffect(Unit) { ... collectLatest }`:

| VM (define) | Effect flow | Screen (collect) |
|---|---|---|
| `HomeViewModel.kt:68–69` | `effects` (extraBufferCapacity=1) | `HomeScreen.kt:74–80` |
| `AddTransactionViewModel.kt:61–62` | `savedEffect` | `AddTransactionScreen.kt:140–144` |
| `ReviewViewModel.kt:45–46` | `effects` | `ReviewScreen.kt:92–98` |
| `TransactionDetailViewModel.kt:44–45` | `navBackEffect` | `TransactionDetailScreen.kt:82–85` |
| `SettingsViewModel.kt:64–65` | `messageEffect` | `SettingsScreen.kt:100–104` |
| `PersonalizationViewModel.kt:60–61` | `messageEffect` | `PersonalizationDashboardScreen.kt:82–84`; `DebugLogScreen.kt:132–134` |

✅ Idiom correct: replay=0, extraBufferCapacity=1, `asSharedFlow()`, collectLatest → no coalescing/combinatorial hazard.
⚠ Structural caveat: `LaunchedEffect(Unit)` starts collecting **after** first composition; replay=0+buffer=1 means any effect emitted before collection starts (e.g., a fast `savedEffect`) is **silently dropped**. Also not tied to a lifecycle window — **no screen uses `repeatOnLifecycle` at all**.

## 3E. Multi-StateFlow exposure / redundant collection

- **`BudgetViewModel.kt:38–50`** exposes 4 public StateFlows: `globalBudget` (:38–39), `categoryBudgets` (:41–42), `unbudgetedSpent` (:44–45, derived `combine`), `uiState` (:49–50). **`BudgetListScreen` reads only `state`** (`BudgetListScreen.kt:55`; used :229, :241–242, :247, :258, :263) → the 3 extra flows are dead exposure duplicating `uiState` content; hazard only if future screens mix them.
- **`HomeViewModel.kt:58–59`** exposes separate `unreviewedCount: StateFlow<Int>` while `HomeUiState` also carries it (`HomeViewModel.kt:94`, `HomeContract.kt:12`); `HomeScreen.kt:231, :262` reads `state.unreviewedCount` only → extra flow unused.
- **`CashBuddyApp.kt:66`** collects `GetUnreviewedCountUseCase().collectAsStateWithLifecycle(0)` independently for the bottom bar (:160) — same use case collected **twice** (also inside HomeViewModel's uiState combine) → **double DB observation**.
- ✅ No screen consumes more than one StateFlow from the same ViewModel (grep-verified across all 13 screens: BudgetListScreen.kt:55, BudgetDrilldownScreen.kt:60, TransactionListScreen.kt:93, TransactionDetailScreen.kt:78, HomeScreen.kt:72, ReviewScreen.kt:86, StatsScreen.kt:58, SettingsScreen.kt:91, AddTransactionScreen.kt:79, DataViewScreen.kt:66, PersonalizationDashboardScreen.kt:78, DebugLogScreen.kt:111).

---

# PART 4 — API SEMANTICS AUDIT TABLE

| API | Sites audited | Verdict |
|---|---|---|
| **StateFlow / SharedFlow effects** | 6 VMs, replay=0 + extraBufferCapacity=1 (cites in 3D table) | ✅ Correct idiom. ⚠ Drop-before-collect + no `repeatOnLifecycle` anywhere (3D caveat) |
| **Multi-flow per VM** | `BudgetViewModel.kt:38–50` (3 of 4 flows unused); `HomeViewModel.kt:58–59` (unused); `CashBuddyApp.kt:66` double-collect | ⚠ Redundant/dual collection; collapse into `uiState` |
| **debounce / collectLatest** | `TransactionListViewModel.kt:84` debounce(300), `:111` collectLatest ✅; `TransactionPager.kt:15–22` non-thread-safe — `collectLatest` reset vs `loadNextPage()` race → page dup/skip | ✅/⚠ |
| **Channel** | No Channel usage in effect paths (SharedFlow used throughout) | — |
| **Dispatchers** | 14 repos hardcode `Dispatchers.Default` despite platform `ioDispatcher` (Part 2F); listener/workers DB work actually runs on Default | ⚠ Inconsistent with own abstraction |
| **runBlocking / Workers** | `runBlocking` tests only (grep-verified); Workers proper suspend CoroutineWorkers (2D) | ✅ |
| **`@Stable`/`@Immutable`** | `Transaction.kt:17` `@Immutable` ✅; UI state data classes immutable by shape; lambda memoization depends on compiled stability (**UNVERIFIED**) | ✅/UNVERIFIED |
| **expect/actual** | `Platform.kt:3–9` all actuals exist; iOS violations: fake export (IosFileExporter.kt:4–15), dead picker (FilePicker.ios.kt:10), hardcoded version (IosPlatform.kt:8), no-op notify (IosPlatform.kt:12–14); Android wiring validated (2C); default-`exportFileStream` empty-file trap (FileExporter.kt:19) | 🔴 Contract violations on iOS |
| **SQLDelight generated API** | Dead generated surface: categories insert/update/deleteById unused; learned_token getSignificant unused; signal_observations getAll/reset unused; goals table entirely unused | ⚠ See Part 5 |

---

# PART 5 — SOLID VIOLATIONS & REFACTOR PLAN

| Principle | Violation | Citation | Plan |
|---|---|---|---|
| **SRP** | `MessagePipeline` god-class — ingest orchestration + dedup + classification + anomaly + budget + recurrence + persistence; **18 constructor deps**; `FraudDetector` default-instantiated inside the constructor param | `MessagePipeline.kt` (324 L), `AppModule.kt:~70` (:70 for FraudDetector) | Split: `IngestCommand` (transactional persistence), `ClassificationStage`, `PostIngestSideEffects`; inject FraudDetector explicitly |
| **DRY** | `classify()` vs `classifySuspending()` near-identical | `ProbabilisticClassifier.kt:48–121` vs `:123–196` | Delete blocking variant; single suspend implementation |
| **ISP/LSP** | Repo contract drift: "significant" interface loads entire table; iOS `FileExporter` returns fake success | `LearnedTokenRepositoryImpl.kt:49–61` (iface L16–18); `IosFileExporter.kt:4–15` | Use `getSignificant` query; iOS actual must implement or explicitly throw/flag unsupported |
| **DIP** | Held overall (`AppModule` interfaces); violations: hardcoded `Dispatchers.Default` baked into every repo impl; FraudDetector default-in-ctor; VM instantiates concrete `CategoryEngine()` | `AppModule.kt`, `AddTransactionViewModel.kt:187–188` | Inject `ioDispatcher` like Platform already provides; explicit DI |
| **OCP** | Hardcoded English/vocab knowledge compiled into classes (see 1.5 list) | Part 1.5 | Data-driven vocab/config; re-verify "UPI Merchant" sentinel against the 100%-content-first rule |

**Refactor priority order:**
1. Wrap pipeline ingest / dup-merge / tx-edit in SQLDelight `transaction` blocks (C1–C3) — smallest change, removes the largest risk class.
2. Fix alert first-wins (C4) and CSV category-id import (C7).
3. Wire iOS SQLCipher + honest export/picker (C5, Part 2C).
4. Replace hand-baked `ensureSchema`/migrations with a `user_version` chain; reconcile diverged merge_log DDL (C6).
5. Tokenize `MessagePipeline` (SRP row); delete classifier duplicate; inject dispatcher.
6. Compose: `remember` per-row timestamp/derived lists (3C); key conditional Lazy items (3B); collapse extra StateFlow exposures (3E).

---

# PART 6 — DEAD CODE INVENTORY

| File Path | Line Count | Why dead | Delete/Fix |
|---|---|---|---|
| `shared/src/commonMain/kotlin/com/cashbuddy/core/prob/AccountEngine.kt` | 74 | Zero production or test references (grep-verified); superseded by ProbabilisticClassifier + CategoryEngine | **Delete** |
| `shared/src/commonMain/sqldelight/com/cashbuddy/db/goals.sq` | 29 | Entire table + queries unused (`goalsQueries` never referenced) | **Delete** (schema-destroying; needs migration care for existing installs) |
| `shared/src/commonMain/kotlin/com/cashbuddy/core/prob/HeuristicCategorizer.kt` | 34 | Referenced only by tests; not in DI graph | Delete with its tests |
| `shared/src/commonMain/sqldelight/com/cashbuddy/db/signal_observations.sq` | 16 | `observe()` never called → table always empty; `getAll`/`reset` unused | **Fix or delete**: wire calibrator feedback loop, or remove table + per-signal reads (D13) |
| `shared/src/commonMain/sqldelight/com/cashbuddy/db/categories.sq` — insert/update/deleteById | partial | Repository exposes only getAll/getByType/seedDefaults; queries unreachable | Delete queries (re-enable with category CRUD UI + FK reassignment) |
| `shared/src/commonMain/sqldelight/com/cashbuddy/db/learned_token.sq:18–20` — getSignificant | partial | Written but never called — its absence is why the "≥5 observations" contract is broken | **Fix**: use it in `LearnedTokenRepositoryImpl` (then drop getAll-based path) |
| `shared/src/commonMain/kotlin/com/cashbuddy/presentation/components/PaisaPalComponents.kt:44` — `PaisaPalMetricCard` | 1 of 241 | Name's only occurrence in 199 tracked files is its own definition; 0 `@Preview` in project | Delete composable (keep rest of file) |
| `androidApp/src/play/kotlin/com/cashbuddy/ocr/MlKitOcrEngine.kt:15–17` (context field) | partial of 56 | `@Suppress("UNUSED_PARAMETER")` context retained, unused | Remove field |
| `shared/src/commonMain/kotlin/com/cashbuddy/core/prob/MessagePipeline.kt:340–349` — `format2Decimals` | partial of 324 | Only consumed by `formatAnomalyNote` | Inline / move to formatter util |
| `AddTransactionViewModel.kt:41` — `typealias ManualEntryViewModel`; `ScreenshotParserEngine.kt:332/356` — `typealias bool` | partial | Self-referential alias / platform-interop hack | Rename to real type; delete `typealias bool` |
| `BudgetViewModel.kt:38–45` — 3 of 4 public StateFlows | partial of 105 | Screen reads only `state` | Collapse into `uiState` |
| `HomeViewModel.kt:58–59` — public `unreviewedCount` | partial of 116 | Screen reads `state.unreviewedCount`; double-collected (`CashBuddyApp.kt:66`) | Remove + collapse |
| `shared/src/iosMain/kotlin/com/cashbuddy/platform/IosFileExporter.kt` / `FilePicker.ios.kt` | 14 / ~10 | Not dead — **worse**: fake-success no-ops presented as working features | Implement or fail explicitly (Part 2C) |

**Ghost modules (confirmed absent — nothing to do):** `MerchantMap`, `AppLabelMap`, legacy `NotificationParser`, `ManualEntryScreen`, `TrainingDashboardScreen`, `BackupDatabaseUseCase` — **do not exist** in the tree.

**Reference line counts (for weight):** MessagePipeline.kt=324 · TransactionListScreen.kt=704 · SettingsScreen.kt=975 · DebugLogScreen.kt=811 · ReviewScreen.kt=476 · MainActivity.kt=450 · DatabaseDriverFactory.kt=441 · AddTransactionScreen.kt=493 · TransactionListViewModel.kt=291 · DuplicateReconciler.kt=240 · ScreenshotParserEngine.kt=313 · TransactionNotificationListener.kt=249 · ProbabilisticClassifier.kt=173 · RecurrenceDetector.kt=178 · AndroidKeystoreManager.kt=147 · AppModule.kt=181 · DataManagementUseCases.kt=147 · BudgetEngine.kt=134 · BudgetAlertScheduler.kt=114 · DebugLogger.kt=114 · CashBuddyApplication.kt=104 · BudgetViewModel.kt=105 · AndroidAnomalyNotifier.kt=84 · PaisaPalComponents.kt=241 · BudgetCard.kt=210 · TransactionItemCard.kt=224 · HomeViewModel.kt=116 · PersonalizationViewModel.kt=186 · BatchCategorizeUseCase.kt=51 · ScreenshotHandler.kt=194 · AddTransactionViewModel.kt=274 · LearnsTokenRepositoryImpl note → `LearnedTokenRepositoryImpl.kt`=59 · AndroidDatabaseDriverFactory.kt=33 · MlKitOcrEngine.kt=56 · TesseractOcrEngine.kt=130 · AnomalyActionReceiver.kt=42 · CashBuddyWidgetReceiver.kt=24 · IosDatabaseDriverFactory.kt=12 · IosFileExporter.kt=14 · goals.sq=29 · signal_observations.sq=16

---

# PART 7 — VERIFIED NON-ISSUES (for the record)

- Keyset pagination + keyset index implemented correctly (`transactions.sq:31, 316-332`).
- Lazy dynamic lists are keyed + contentTyped codebase-wide (Part 3B matrix — all `items(...)` GOOD).
- Workers all suspend; no `runBlocking` in production; Glance DB reads IO-dispatched; `provideGlance` pattern correct.
- Android SQLCipher chain (Keystore → passphrase → `SupportOpenHelperFactory`) correctly built, passphrase zeroized.
- `notifyTransactionsUpdated()` expect/actual fully wired on Android (7+ call sites + registered consumer receiver).
- `HomeViewModel` navigation effects use correct SharedFlow idiom (`extraBufferCapacity=1`).
- `TransactionPager` debounce+collectLatest chain on the VM side is correct (`TransactionListViewModel.kt:84,111`) — only the pager object itself races.
- In-tree `remember`/`derivedStateOf` exemplars exist (`BalanceHeaderCard.kt:257`, `TransactionListScreen.kt:97-107`) — fixes are mechanical applications of the house pattern.
- Project hard-rule enforcement verified present: zero SMS permission, zero network permission paths, `ArchitectureSanityTest.kt` bans bank sender-ID strings (100% content-first gating).

---

# PART 8 — CONSOLIDATED PRIORITY MATRIX (everything, one list)

| # | Severity | Finding | Cite |
|---|---|---|---|
| 1 | 🔴 | Unencrypted iOS DB (non-negotiable rule violation) | IosDatabaseDriverFactory.kt:9-12 |
| 2 | 🔴 | Main-thread blocking I/O + ~4.65 s sleeps in screenshot flow (ANR) | MainActivity.kt:327-348 → ScreenshotHandler.kt:38-72 |
| 3 | 🔴 | No DB transaction around ingest/merge/edit writes (3 crash-safety holes) | MessagePipeline.kt:76-338; DuplicateReconciler.kt:184-223; TransactionRepositoryImpl.kt:107-149 |
| 4 | 🔴 | Budget alert first-wins permanent loss | BudgetAlertScheduler.kt:98-117 |
| 5 | 🔴 | Hand-baked schema divergence + swallowed DDL/migration failures | DatabaseDriverFactory.kt:23-29,221-229,360-455 |
| 6 | 🔴 | iOS fake-success export/picker (contract violations) | IosFileExporter.kt:4-15; FilePicker.ios.kt:10 |
| 7 | 🔴 | CSV import wrong-category + duplicate rows | DataManagementUseCases.kt:120 |
| 8 | 🔴 | Notification PK collisions silently drop messages | TransactionNotificationListener.kt:170,277 |
| 9 | 🟠 | Silent `System.loadLibrary("sqlcipher")` swallow | AndroidDatabaseDriverFactory.kt:16-19 |
| 10 | 🟠 | `clearAllData` one-table-only | SettingsViewModel.kt:219-225 |
| 11 | 🟠 | Vacuous empty `CategoryEngine` prediction + hardcoded `1L` fallbacks | AddTransactionViewModel.kt:187-188; AddTransactionScreen.kt:455-456 |
| 12 | 🟠 | learned_token "≥5" contract violation loading full table | LearnedTokenRepositoryImpl.kt:49-61 |
| 13 | 🟠 | Dead-in-effect calibrator N+1 (observe never called) | LikelihoodCalibrator.kt |
| 14 | 🟠 | Startup `getAll()` unbounded scan (stale patterns >24 h) | HomeViewModel.kt:37-56 |
| 15 | 🟠 | ReviewScreen per-recomposition list filter (no remember) | ReviewScreen.kt:114-116 |
| 16 | 🟠 | Per-card clock-read + datetime math in composable body (no remember) | BudgetCard.kt:101; TransactionItemCard.kt:150 |
| 17 | 🟠 | Money REAL (floating-point currency) | transactions.sq:3 |
| 18 | 🟠 | Dispatchers.Default hardcoded in 14 repos (platform abstraction bypassed) | Part 2F |
| 19 | 🟡 | Scope hygiene: widget receiver per-receive scope; anomaly singleton scope no SupervisorJob; abandoned receiver scopes | CashBuddyWidgetReceiver.kt:18; AndroidAnomalyNotifier.kt:26; AnomalyActionReceiver.kt:33 |
| 20 | 🟡 | TransactionPager thread unsafety; BatchCategorize dead condition + unbounded loop; dedup/regex per-message scans; mapTransaction double regex; budget N+1; home 4 luckily-keyed but keyless conditional Lazy items; keyless Label-compatible effects drop-before-collect caveat; unnecessary 4-VM-flow/extra StateFlow exposures + double `GetUnreviewedCountUseCase` collection | MessagePipeline.kt:145-159; TransactionRepositoryImpl.kt:316-366; BudgetEngine.kt:58-77; BudgetViewModel.kt:38-50; HomeViewModel.kt:58-59; CashBuddyApp.kt:66; TransactionPager.kt:15-22; BatchCategorizeUseCase.kt:35; ReviewScreen/Home/Stats/Settings conditional items (Part 3B) |

---

*All findings above are cited from source read in this session. Marked **UNVERIFIED**: exact Koin context resolution instances (Part 2B), Compose compiler lambda memoization (Part 3C), runtime reachability of the sqlcipher load-catch path (Part 2E). AI-generated artifacts and tests were never treated as proof of production behavior.*

---

# PART 9 — THE MATH ENGINE: LAYER MAP + WHAT IS IMPLEMENTED CORRECTLY

## 9.1 Where the maths engine lives (layer map)

| Layer | Package/Files | Responsibility | Style |
|---|---|---|---|
| **Probabilistic (Bayes) math** | `shared/src/commonMain/kotlin/com/cashbuddy/core/prob/ProbabilisticClassifier.kt`, `LikelihoodRatios.kt`, `LikelihoodCalibrator.kt`, `FieldConfidenceEstimator.kt`, `EvidenceExtractor`/`SentenceScopedExtractor` (feature engineering) | Is-this-a-transaction probability → P(transaction) + per-field confidence | Log-odds naive Bayes (100% local, no network) |
| **Robust statistics** | `shared/src/commonMain/kotlin/com/cashbuddy/core/FraudDetector.kt` | Outlier detection on amounts per merchant | Modified Z-Score / MAD (Iglewicz & Hoaglin 1993) |
| **Financial math** | `shared/src/commonMain/kotlin/com/cashbuddy/core/budget/BudgetPacer.kt`, `BudgetEngine.kt`, `BudgetAlertScheduler.kt` | Pacing/projection/over-budget math | Calendar-exact pro-rata |
| **Time-series statistics** | `shared/src/commonMain/kotlin/com/cashbuddy/core/analytics/RecurrenceDetector.kt` | Recurring-pattern detection | Day-of-week histogram + coefficient-of-variation |
| **String similarity** | `shared/src/commonMain/kotlin/com/cashbuddy/core/prob/JaroWinkler.kt` | Merchant-name fuzzy matching | Textbook Jaro–Winkler |
| **Deterministic decisioning** | `shared/src/commonMain/kotlin/com/cashbuddy/core/prob/DedupEngine.kt`, `DuplicateReconciler.kt` | Duplicate classification | Tiered match with epsilon/tolerance bands |

All of it is **pure Kotlin, commonMain, dependency-light** (only `kotlin.math` + `kotlinx-datetime`) — consistent with the pure-KMP-core non-negotiable. Two design wins: everything tunable lives in constants (`LikelihoodRatios.kt:4-34`), and `ProbabilisticClassifier` ships every contribution as an auditable `Contribution(name, delta, lr)` (L13-26) that is serialized into the DB evidence — the math is explainable/forensic, not a black box.

## 9.2 Verified-correct implementations (with citations)

### ✅ M1. Naive Bayes log-odds accumulation — `ProbabilisticClassifier.kt:54-91` (and `:129-166`)
- Prior is converted to logit **once**: `ln(P_TRANSACTION / (1-P_TRANSACTION))` (L54; P=0.20 at `LikelihoodRatios.kt:5` → ln(0.25) ≈ −1.386). Correct.
- Each present signal adds `ln(LR)` to the logit (L59-65) — evidence accumulation in **log-space**, so many small factors can't underflow and the aggregation is exactly the product of odds ratios. Correct.
- Conversion back: sigmoid `1/(1+exp(−logit))` (L91). Correct inverse.
- **Anti-double-count guard:** the generic "amount anywhere" LR is applied only when the contextual amount signal was NOT applied — `apply("amount", evidence.hasAmount && !evidence.amountInTransactionContext, ...)` (L71, repeated L146). Overlapping-evidence hygiene done right.
- Learned tokens are injected as a **pre-computed log-odds block** (L86-89, `logit += evidence.tokenLogOdds`) and contributions report `lr = exp(tokenLogOdds)` — units stay consistent with the hand-set LRs.
- Calibration hook cleanly overrides the base LR per signal (`classifySuspending`, L134-140) without changing the accumulation structure.

### ✅ M2. Modified Z-Score / MAD outlier detection — `FraudDetector.kt:42-95`
- **Textbook Iglewicz–Hoaglin:** `score = 0.6745 * (amount − median) / MAD`, threshold `> 3.5`, minimum sample `n ≥ 10` (L74-75, L43; constants match the KDoc L7-15).
- **Degenerate-distribution fallback done correctly:** when `MAD == 0.0` (e.g., all 10+ historical amounts identical), dividing by MAD is impossible → it switches to a relative-difference rule `|amount − median| / median ≥ 0.5` (L57-63) instead of crashing or always-flagging. This is the right call — MAD=0 means the historical distribution is a point mass, so percent deviation is the correct robust substitute.
- `computeMedian` handles **both parities** of sample size: odd → middle element (L91), even → mean of the two middle elements (L93). Correct.

### ✅ M3. Calendar-exact pacing/projection — `BudgetPacer.kt:59-88`
- Leap-year rule `(y%4==0 && (y%100!=0 || y%400==0))` — correct full Gregorian rule (L64 for month length, L78 for year length 365/366).
- Month lengths correct including February (L65-69); WEEKLY uses ISO day number Mon=1..Sun=7 over a 7-day denominator (L72-75) — weeks are paced by elapsed weekdays, not calendar rollback.
- Every division is zero-guarded: `expectedPercent` guards `totalDays > 0`, `actualPercent` guards `budgetAmount > 0` (returns 100.0 only when money was spent against a zero budget), `dailyAverage` guards `daysElapsed > 0` (L84-87).
- Projection is the honest straight-line method: `dailyAverage × totalDays` (L87) and `projectedOverage` is floored at 0.0 (L88) — never reports negative overage.
- Timezone-aware: instant → local date via `TimeZone` (L59) — day counters follow the user's calendar.

### ✅ M4. Day-of-week histogram + coefficient of variation — `RecurrenceDetector.kt:59-110`
- Histogram is timezone-aware (`toLocalDateTime(timeZone)`, Mon..Sun index 0-6, L63-68); window filter `in windowStart..now` (L59).
- **CV math is correct:** population variance on the active-day counts (L85), `stdDev / mean` with a `mean <= 0` guard (L82-83). Decision rule exactly matches its KDoc: `count ≥ 4 && (activeDays ≥ 3 || weekend pattern) && CV ≤ 0.30` (L107-110).
- Amounts are converted to **integer paise with `roundToLong`** (L142) — correctly rounds away the float residue of REAL-column money before grouping/comparison.

### ✅ M5. Jaro–Winkler — `JaroWinkler.kt:25-77`
- Match window `max(0, maxLen/2 − 1)` (L30); per-position first-unmatched-char scan with `aMatched/bMatched` flags (L37-48) — the standard comparator.
- Transpositions correctly halved (`t = transpositions/2`, L67) and the Jaro formula `(1/3)(m/|a| + m/|b| + (m−t)/m)` (L68) is exact.
- Winkler prefix bonus `jaro + 0.1·p·(1−jaro)` with p capped at 4 and bounded by the shorter string (L70-76) — standard p=0.1, l=4.
- Edge guards: empty string → 0.0, identical → 1.0 short-circuit, zero matches → 0.0 (L26-27, L50).

### ✅ M6. Tiered dedup tolerances — `DedupEngine.kt:42-86`
- Tier 1 = exact UTR (case-insensitive) — identity, not similarity (L44-51).
- Tier 2 = amount epsilon `< AMOUNT_EPS` (0.01 = one paise) AND strict window `< WINDOW_MS` (5 min) (L54-57) — the half-open band means exactly-at-boundary pairs are NOT merged, an intentionally conservative boundary.
- Merge is monotonic in probability: `pTransaction = max(new, existing)` (L75) — a merge can only raise confidence, never lower it; `clearReview` gate `maxP ≥ 0.90 && both amounts < 10000` (L83) is aligned with the human-in-the-loop rule (confidence threshold + ₹10k cap).

### ✅ M7. Field confidences — `FieldConfidenceEstimator.kt:34-83`
- Deterministic, no randomness: amount 0.98/0.80 by explicit currency-symbol presence (15 symbols recognized, ₹ first, L35-53); type 0.95 vs 0.85 by explicit "debited"/"credited" wording (L59-64); merchant tiers 0.95/0.85/0.70 by category-match confidence and UPI-handle regex (L72-79). Sensible monotone tiers, all zeroed on missing fields.

## 9.3 Honest caveats (design choices, not bugs)

1. **Naive-Bayes independence assumption:** overlapping signals can both fire (e.g., `amount_in_context` + `amount_debit_sent`, ProbabilisticClassifier.kt:67-68), double-counting correlated evidence and inflating P. This is inherent to the hand-tuned-LR design — acceptable, but it means P values near the 0.85 review threshold are biased high on verbosity-rich messages.
2. **`medianIntervalHours` is not a median** — it's hardcoded 24 or 168 (`RecurrenceDetector.kt:175,182`). Workspace KDoc honestly declares inter-arrival analysis is out of scope (L19), but the field name promises more than the math delivers.
3. **Population variance (÷N), not sample variance (÷N−1)** in CV (L85) — slightly optimistic σ with small active-day sets; documented as a design choice.
4. **Money inputs are REAL from the DB** — the math layer can't fully escape float residue; it mitigates by rounding to paise at entry (`RecurrenceDetector.kt:142`) and by epsilon comparisons (`DedupEngine.kt:8,55-56`), which is the correct containment strategy short of fixing the column type (Part 1, D1).
5. Verification caveat: per-call hypotheses of `prove`-level exactness were read from source; seconds-precision boundary behavior at `WINDOW_MS` equality is deterministic from code but not runtime-observed.

---

# PART 10 — POST-COMMIT RECHECK: Phase-5B Implementation (commit 59bc3f6, 2026-10-29)

Files verified by direct read: `TokenEvidence.kt` (`shared/src/commonMain/kotlin/com/cashbuddy/core/prob/`), `LearnedTokenRepositoryImpl.kt`, `LearnedTokenRepository.kt`, `TokenPurgeWorker.kt` (`androidApp/src/main/kotlin/com/cashbuddy/worker/`), `AssistantWorkManager.kt`, `learned_token.sq`; greps for callers/tests.

## 10.1 What is genuinely fixed ✅

1. **Lookup inversion real:** `TokenEvidence.kt:36-40` (suspend) and `:51-55` (sync) tokenize the message FIRST, then request **only** those tokens — `getTokenStats`/`getTokenStatsSync` used at `:40`/`:55`. `EvidenceExtractor.kt:129` path unchanged, now cheap.
2. **L1 cache real:** `LearnedTokenRepositoryImpl.kt:23` (`com.cashbuddy.core.prob.ConcurrentHashMap` — the project's KMP-safe expect/actual map, good practice), warm-up `getTopSignificant(2000L)` (`:32`), miss-batch through **indexed** `getByTokens(missingFromCache)` (`:112`), cache populated on hit (`:119`).
3. **≥5 gate enforced at the repository layer:** `:102` (cached hits) and `:120` (DB misses) — the KDoc on `TokenEvidence.kt:20-22` finally matches reality; D12 resolved in spirit (see 8.2 below).
4. **Worker real & wired:** `TokenPurgeWorker.kt:22-28` (Koin `inject()` of the SAME singleton used by the classifier — no cache-instance mismatch); purge → `purgeExcessTokens` → `purgeOldestLeastUsed` subquery (`learned_token.sq:35-41`, correct SQLite `DELETE … IN (SELECT … LIMIT)` syntax) → `cache.clear()+preloadCache()` refresh (`LearnedTokenRepositoryImpl.kt:152-167`). Scheduled weekly Sun 02:00 via `AssistantWorkManager.kt:46-59` + `calculateWeeklySundayDelay` (`:84-98`), invoked from `CashBuddyApplication.kt:48` (and `cancelAll` at `:51`).
5. **Bonus fix beyond spec:** the legacy `getAllSignificantSync` no longer calls `queries.getAll()` — it now uses the significant-filtered `getSignificant` query (`LearnedTokenRepositoryImpl.kt:136-150`). Grep confirms no external caller remains, so this is now a dead-but-honest surface.

## 10.2 New bugs introduced or retained by the commit ❌

| # | Severity | Finding | Cite |
|---|---|---|---|
| R1 | 🔴 high | **`init { preloadCache() }` runs synchronous SQLite I/O on whatever thread first resolves the Koin singleton.** Repos resolve on first `EvidenceExtractor` construction — in practice the first ViewModel init on the **main thread**. First app-open after cold start does a top-2,000-row ORDER-BY query on the UI thread → the exact jank class Phase 5 was supposed to remove. Fix: lazy warm-up scheduled on `dispatcher` from the first `getTokenStats` call (with a cheap synchronous fallback for `extractSync`), not in `init`. | `LearnedTokenRepositoryImpl.kt:26-28, 30-44` |
| R2 | 🔴 high | **Cache grows unbounded between purges, no eviction.** Miss path caches every DB row encountered (`:119`, `:144`), including <5-observation tokens; no size cap/LRU. Steady-state drifts from "top 2,000 in RAM" toward post-purge 20,000 entries (~2-4 MB) and, before the first-ever purge, effectively whole-table. The purge clears it weekly, but between-week growth is unbounded. Fix: cap at `MAX_CACHE = 2_048` with a small LRU (access-order `LinkedHashMap` wrapper or count-gated clear+re-warm). | `LearnedTokenRepositoryImpl.kt:110-127, 132-150` |
| R3 | 🟠 med | **Interface default methods are silent-failure traps:** `getTokenStats(...) = getAllSignificant()` (full significant scan as default), `getTokenStatsSync(...) = getAllSignificantSync()` (whole significant table), and worst: `purgeExcessTokens(...) = 0` — a worker backed by any partial/mock implementation would return `Result.success()` *without purging*. The real impl overrides all three, but defaults convert a forgotten override into a silent no-op. Fix: make all three abstract. | `LearnedTokenRepository.kt:30, 35, 40` |
| R4 | 🟠 med | **`purgeExcessTokens` masks DB failures:** the inner `catch (_: Throwable) { 0 }` (`LearnedTokenRepositoryImpl.kt:164-166`) means the worker's `Result.retry()` (`TokenPurgeWorker.kt:23-28`) can never trigger on a transient DB error — a failed purge silently "succeeds" and waits a week. Fix: rethrow inside the repo let the worker own retry semantics. | `LearnedTokenRepositoryImpl.kt:152-167` |
| R5 | 🟡 low | **`record()` cache path has a read-modify-write race:** `cache[token] = existing.copy(count+1)` (`:51-66`) under two concurrent `record()` calls for the same token can lose an increment in RAM (DB stays exact, cache drifts low until next purge/reload). Fix: `cache.merge(token, newStats, { a, b -> a.copy(transactionCount = a.transactionCount + b.transactionCount, nonTransactionCount = a.nonTransactionCount + b.nonTransactionCount) })`. | `LearnedTokenRepositoryImpl.kt:50-67` |
| R6 | 🟡 low | **Double tokenization per message:** `extract/extractSync` tokenize (`:36`, `:51`) and then `evaluate(rawText, …)` tokenizes **again** (`:69`). Cheap (≈30 words) but pure waste on the hot path. Fix: `evaluate(messageTokens, significant)`. | `TokenEvidence.kt:36→69, 51→69` |
| R7 | 🟡 low | **Dead variable + permanently-swallowed preload failure:** `isCachePreloaded` is set (`:40`) but read nowhere; if the first `preloadCache()` throws (e.g., table not migrated yet — exactly the catch comment's justification), there is **no retry ever** for the whole process lifetime: cache stays empty, every lookup takes the DB path (correct, just slower) and the event is invisible. Fix: delete the flag; retry warm-up once per `getForTokens` cold-miss burst. | `LearnedTokenRepositoryImpl.kt:24, 40-43` |
| R8 | 🟡 low | **Phase-5B.9 acceptance tests not delivered:** grep across `*Test*.kt` for `getTokenStats|purgeExcess|TokenPurge|LearnedTokenCache` → zero matches. The commit's verification is compile-only; the 50k-token latency test, purge ordering test, and 1-observation-token-zero-contribution test from the plan remain open items. | — |

## 10.3 Net verdict

The commit is a genuine fix of the OOM/ANR-class flaw (inverted lookup + cache + gate + GC worker, all wired through the correct singleton). It is safe to keep. Before calling Phase 5B closed: fix R1 (main-thread init preload), R2 (no eviction cap), R3 (purge no-op default), and R4 (masked worker retries), and land R8's three tests — those four are the difference between "compiled" and "verified."

---

# PART 11 — POST-COMMIT RECHECK 2: R1–R8 Fixes (commit 31797cc, 2026-10-29)

Files re-read in full: `LearnedTokenRepositoryImpl.kt` (184 L), `LearnedTokenRepository.kt` (63 L), `TokenEvidence.kt` (97 L), **NEW** `LearnedTokenSafeguardsTest.kt` (82 L). Findings ID'd N-*/R-claim-*, verdict per claim.

## 11.1 Claim-by-claim verdict

| Claim | Verdict | Evidence |
|---|---|---|
| **R1 fixes main-thread cold-start preload** | 🟠 **PARTIAL — improved, residual risk** | `init { preloadCache() }` removed (was :26-28; now none); `ensurePreload()` is called from suspend paths first (:107 within `withContext(dispatcher)`, and :150), so the common warm-up runs on the dispatcher. **Residual:** `extractSync` (underlying EvidenceExtractor.kt:129) still executes `getTokenStatsSync` on the caller's thread — first message on the listener's IO scope is fine, but a sync call that ever reaches main thread yields one full top-2048 main-thread query. Fix claim "called on first background query on Dispatchers.Default" is only literally true for the suspend path. |
| **R2 cache cap 2048** | 🟠 **CAP REAL but claim overstates eviction quality** | `MAX_CACHE_SIZE = 2048` (`Impl:27`) and every dynamic put funnels through `putIntoCacheWithCap` (:47-56): record (:78), miss-path (:132), getAllSignificant (:158). **BUT:** (a) commit message says "LRU cache cap" — the code evicts `cache.keys.firstOrNull()` (:50) = arbitrary/insertion-order eviction, NOT LRU; it can evict hot preload tokens while cache keeps one-observation noise; (b) **preload bypasses the cap**: `ensurePreload()` writes directly `cache[row.token] = …` (:35) with no cap check, so after a purge-and-repreload that coexists with pre-existing `record()` entries the map can transiently hold ≈ 2× 2048; (c) no test covers the cap (§11.2). |
| **R3 defaults abstract** | ✅ **TRUE** | `LearnedTokenRepository.kt:19-46` — all five members abstract, no default bodies; `purgeExcessTokens(...) = 0` no-op gone. |
| **R4 purge failures propagate** | ✅ **TRUE** | `Impl:166-178` has no try/catch — exception bubbles to `TokenPurgeWorker → Result.retry()`. KDoc on the interface also updated (:39). |
| **R5 record cache race fixed** | ❌ **CLAIM FALSE — race untouched** | `Impl:63-78` is the same read-modify-write as before: `existing = cache[token]` → `existing.copy(count+1)` → put. No atomic `merge()`/`addAndGet`-style update; with the project's copy-on-write `com.cashbuddy.core.prob.ConcurrentHashMap` two concurrent `record()` calls can both read snapshot N and write N+1, losing an increment in RAM (DB stays exact; cache drifts low until next purge/reload). The report's "handled via capped insertion" does not add atomicity. |
| **R6 single tokenization** | ✅ **TRUE** | `TokenEvidence.kt:36/:51` tokenize once; `evaluateWithTokens(messageTokens, significant)` (:41/:56, signature :59). Second `rawText`-based tokenization removed. |
| **R7 flag now a real retry guard** | ✅ **TRUE** | `isCachePreloaded` read at `Impl:31` (skip), set :41, reset :172 after purge, catch leaves false → next call retries. Two nits below (N1/N2). |
| **R8 tests delivered** | 🟠 **TRUE, but coverage is narrower than "comprehensive"** | `LearnedTokenSafeguardsTest.kt:29-81` — exactly the 3 claimed tests (1-obs ignored :30-39; 5-obs activated :42-52; purge rarest-first :55-81) using a real in-memory JdbcSqliteDriver + `AppDatabase.Schema.create` (:22-23) — legitimate. **Missing:** cache-cap/eviction test (would have caught N1), post-purge cache-state test, and the miss-path-caches-<5-rows assertion. Also no latencies (fine for unit scope). |

## 11.2 New findings in the fixed code (N1–N4)

| # | Severity | Finding | Cite |
|---|---|---|---|
| N1 | 🟠 | **Preload path bypasses the cache cap** — `ensurePreload` writes raw `cache[row.token] = ...` without `putIntoCacheWithCap`; combined with entries already added by `record()` before first lookup, the map can overshoot to ~2× 2048 after the first purge+re-preload cycle. Cap is only enforced on subsequent adds. | `Impl:34-39` vs `:47-56` |
| N2 | 🟠 | **Eviction is FIFO/arbitrary, not LRU** (contradicting the commit message). `firstOrNull()` on the custom COW map's key order can evict a "top-2000" high-observation token to make room for a one-off UTR/string cached on the miss path — i.e., the hot vocabulary can be silently evicted between purges, degrading classifier evidence until the next purge or preload. | `Impl:50-54` |
| N3 | 🟡 | **Miss path still caches <5-observation tokens** (`putIntoCacheWithCap(row.token, stats)` before the significance check, `Impl:132`), permanently occupying cap slots with noise that is never refreshed (its counts only move on `record`) — combined with N2's FIFO eviction this makes hot-token eviction MORE likely, not less. Fix: only cache when `>= 5` (skip junk), or drop from cache when the gate excludes. | `Impl:126-135` |
| N4 | 🟡 | Minor contract inconsistency: `clear()` does not reset `isCachePreloaded` (`Impl:180-182`) while `purgeExcessTokens` does (`:172`) — functionally harmless (empty table undoes preload value) but a second "reset learning" then leaves a stale flag; non-atomic `Boolean` on the flag (:24) is benign (worst case one duplicate preload). | `Impl:24, 172, 181` |

## 11.3 Net verdict

5.5 of 8 claims verified (R3, R4, R6, R7 clean; R8 delivered but under-covering; R1 partial), **R5's claim is false** (race code unchanged), R2's cap is real but is FIFO not LRU and has a preload bypass. Recommended follow-up (small, all in `LearnedTokenRepositoryImpl.kt`):

1. Route `ensurePreload` writes through `putIntoCacheWithCap` (fixes N1) — one-line change per row.
2. Replace `firstOrNull()` eviction with either (a) don't cache <5s (N3), or (b) an explicit LRU wrapper of the custom map, or (c) track observed-counts and evict min-observed — any of these kills N2+N3 together.
3. Atomize `record()`'s cache update with a merge-style lambda (R5) or simply delete the cache update there and let the next DB read refresh it — cache staleness of ≤1 confirm transaction is negligible vs. risking lost updates.
4. Add three tests: cache-cap overshoot on purge-then-repreload (N1), hot-token-survives-among-noise eviction behavior (N2), miss-path caching of a <5 token then reaching 5 (N3).

Status stays: **keep the commit** (it strictly improves cold-start and cap behavior, and the test skeleton is real) — but Phase 5B should not be marked "done" on `31797cc` alone; the above four are the remaining gap to "verified."

---

# PART 12 — POST-FIX RECHECK 3: N1–N4 + R5 Working-Tree Fixes (UNCOMMITTED, 2026-10-29)

Re-read: `LearnedTokenRepositoryImpl.kt` (200 L, working tree), `core/prob/ConcurrentHashMap.kt` (expect/actual API), `LearnedTokenSafeguardsTest.kt` (112 L). Nothing committed (per user instruction); all verdicts are on working-tree code.

## 12.1 Production-code verdicts — all five real fixes verified ✅

| Item | Verdict | Evidence |
|---|---|---|
| **N1 preload cap bypass** | ✅ **FIXED** | `ensurePreload` now routes every row through `putIntoCacheWithCap` (`Impl:35-42`); no direct `cache[...]` write remains. |
| **N2 FIFO eviction** | ✅ **FIXED (correct and slightly clever)** | Eviction scans for min total observations (`Impl:56-66`) with an early-exit as soon as a 5-observation entry is found (`:63-64`). Preload inserts hottest→coldest (query orders DESC), so during re-preload the eviction systematically displaces coldest entries — the end state is exactly the top-2048 hot vocabulary. The custom COW map supports the iteration used (`ConcurrentHashMap.kt:10`) and both actuals tolerate a weakly-consistent snapshot scan. Dead fallback path `firstOrNull()` (`:69-71`) is now **unreachable** — with the N3 guard every cached entry is ≥5 obs, so `minObs` can never be <5 — cosmetic dead code, harmless. |
| **N3 <5 tokens cached** | ✅ **FIXED** | Guard at `Impl:51-52` rejects sub-threshold tokens from ALL cache writes (preload/miss/record/readback). Noise occupies zero cap slots. |
| **R5 record cache race** | ✅ **FIXED (structurally, not patched)** | `record()` is now DB-first (`Impl:82-98`), then a **DB read-back** of the affected tokens (`:100-110`) refreshes the cache from the source of truth. Lost-update is impossible by construction: every writer's read-back necessarily sees at least its own committed increment, so the last read-back converges to the current DB value. The old `existing.copy(+1)` RMW is gone entirely. |
| **N4 clear() flag** | ✅ **FIXED** | `isCachePreloaded = false` in `clear()` (`Impl:197`). |

## 12.2 Test-suite verdicts — real tests exist, but the two NEW cases are vacuous ⚠

| Test | Verdict | Why |
|---|---|---|
| `testOneObservationToken_IsIgnoredByExtractor` | ✅ discriminating | |
| `testFiveObservationsToken_IsRecognizedAndEvaluated` | ✅ discriminating | |
| `testPurgeExcessTokens_DropsRarestAndLeavesMaxCount` | ✅ discriminating | Seeds via the actual SQL upserts; asserts surviving-token set (`Test:55-81`). |
| `testUnderFiveObservationsTokens_AreNotCached` (:84-91) | ⚠ **vacuous — passes on the OLD code too** | It asserts `getTokenStats(1-obs token) == empty`, but the old pre-N3 code produced the same empty result via the read-side `>=5` filter (`Impl:130-117 old`). Nothing introspects the cache, so "not cached" is not actually proven — this test cannot detect a regression that re-adds the old cache-any-token behavior as long as the read filter survives. To discriminate: expose `cache.size`-introspection for tests (or a fake DB that throws on second access), or record a token 4× and assert the 5th distinct call still hits DB. |
| `testCacheEvictsLowestObservationToken_HotTokenSurvives` (:94-111) | ⚠ **never evicts anything** | It creates 11 cached tokens total — far below `MAX_CACHE_SIZE = 2048` — so **the eviction branch (`Impl:54-73`) is never executed**. "Hot token survives" is trivially true with no pressure. To discriminate: seed >2048 distinct significant tokens (loop seeding is cheap) or make `MAX_CACHE_SIZE` test-injectable; then assert hot token present + a known low-obs token absent + cache size ≤ cap. |

So "5/5 passed" is truthful but two of the five are non-discriminating: the eviction algorithm — the one truly new algorithm in this commit — remains untested (including its early-break and unreachable-fallback branches).

## 12.3 Remaining nits (all minor, none blocking)

1. **Unreachable fallback** `Impl:69-71` — delete it or keep as belt-and-suspenders with a comment; currently it can never run.
2. **`getAllSignificant` got theoretically slower** — it now does a cap+eviction scan per row (O(N×2048) worst case, `Impl:167-175`); still zero production callers, so impact nil, but if this dead surface is ever used at scale it becomes a stone; simplest hygiene: delete the `getAllSignificant`/`getAllSignificantSync` pair (interface `:19-24`, impl `:160-179`) now that nothing reads it (grep-verified callers: none outside tests' fixture `Test:79` uses `getAll` query, not these methods).
3. **First-`extractSync` main-thread top-2048 scan** (R1 residual from Part 11) is unchanged — still only reachable if the sync path ever lands on main; unchanged status, acceptable on the listener's IO scope.

## 12.4 Net verdict

**Production code: Phase 5B is now genuinely clean.** All of N1, N2, N3, R5, N4 are correctly fixed in the working tree, and R8 has grown from 3 to 5 tests. **Test code: 2 of the 5 cases are low-value** — a regression that reintroduces cache-everything or breaks eviction would still pass green. Recommended before the (user-approved) commit:

- Rewrite the two vacuous tests into discriminating ones (cache introspection or >2048-token seeding), OR drop them and accept that the eviction algorithm ships untested.
- Optionally delete the unreachable fallback + the dead `getAllSignificant` surface.
- Then: only commit what the user approves, and re-run the E2E plan (T14/T15 rows in `docs/E2E_NOTIFICATION_TEST_PLAN.md`) against a debuggable build to validate on-device behavior — the unit suite still doesn't cover the R1 main-thread warm-up residual.

Phase 5B scoreline across the three rounds: 59bc3f6 (framework wired, 8 bugs) → 31797cc (8 fixed, 4 new nits) → working tree (all 9 fixed; test discrimination is the last gap).

---

# PART 13 — STORE-RELEASE READINESS AUDIT DRIFT + SPRINT F0–F2 (2026-10-30)

## 13.1 Status corrections — audit Parts above are STALE on these findings

Re-verified against `git log` + working tree on 2026-10-30:

| Finding | Stale status in earlier Parts | Actual status |
|---|---|---|
| **C2** merge/balance TOCTOU | open | **FIXED & COMMITTED** `13e1bcb` ("atomic balance deltas and transact boundary") |
| **C3** non-atomic tx edit | open | **FIXED & COMMITTED** `13e1bcb` (same pattern) |
| **C4** budget alerts first-wins | open | **FIXED & COMMITTED** `db6f714` — `BudgetAlertScheduler.kt` current code has per-budget `shouldAlert` → `markAlerted` + `sendAlert`; NO `firedEvents.isEmpty()` gate anywhere (grep-verified) |
| **C8** manual-add vacuous prediction | open | **FIXED & COMMITTED** `db6f714` |
| **C9** notification ID collisions | open | **FIXED & COMMITTED** `db6f714` — raw id now `"notif_${postTime}_${UUID-8hex}"` (`TransactionNotificationListener.kt:170`); review-alert ID `NOTIFICATION_ID_BASE + ((txId.hashCode() and 0x7FFFFFFF) % 100000)` (`:277`) |
| **C7** CSV fallback | partial | **FIXED & COMMITTED** `c18b182` |

Earlier cite-lines drifted because these commits landed after the Parts were written; treat Parts 1–9 statuses for C2/C3/C4/C7/C8/C9 as superseded by this table.

## 13.2 Working-tree changes landed since 2026-10-29 (parallel session — verified, not authored here)

- **2D screenshot ANR (§1.5 T16 probe): FIXED (uncommitted).** `MainActivity.kt` now collects raw `Uri`s on main thread and runs `copyUriToCache` inside `lifecycleScope.launch(Dispatchers.IO)` — same sequence, off the UI thread. Sleep-loop retained (behavior-preserving).
- **R8/ProGuard keep rules added** (`proguard-rules.pro`): serialization `GeneratedSerializer`/`KSerializer`/`SerialName`, Koin `org.koin.**`, `com.cashbuddy.data.**`, navigation package.
- **C1 step-1 (uncommitted):** `MessagePipeline.kt` computes anomaly BEFORE insert; `notes` written at insert → the post-insert `updateNotes`+`updateStatus` pair was removed (2 writes → 0; single-row state at insert).
- **C10 partial (uncommitted):** `SettingsViewModel.clearAllData()` additionally clears training data and zeroes account balances.

## 13.3 Sprint F0–F2 fixes authored 2026-10-30 (this session, uncommitted)

**F0 — harness call site restored.** `MainActivity.kt` `AuthState.AUTHENTICATED` branch now calls `com.cashbuddy.debug.E2eDebugHarness()`; the harness lives in variant source sets (`androidApp/src/debug/.../E2eDebugHarness.kt` = full DRY-RUN impl, `androidApp/src/release/.../E2eDebugHarness.kt` = no-op stub). Release APKs contain zero harness bytecode beyond the empty stub. Verified: `compilePlayDebugKotlin` + `compilePlayReleaseKotlin` green.

**F1 — C1 atomic ingest cluster.** New `shared/src/commonMain/kotlin/com/cashbuddy/data/repository/TransactionIngestStore.kt`:
- `suspend fun insertAndLink(rawMessageId, transaction): Long` — one driver-level `database.transactionWithResult { … }` wrapping `transactions.insert` + `lastInsertRowId` + `raw_messages.updateResultingTx`. INSERT arg list mirrors `TransactionRepositoryImpl.insert:82-99` (keep in sync /* comment */).
- `MessagePipeline` gains optional `ingestStore: TransactionIngestStore? = null`. When present, the Insert branch commits tx+link atomically; when null (4 unit-test construction sites — `DebugLoggerTest:367`, `AnomalyAlertTest:140,181`, `RecurrencePipelineIntegrationTest:193`), the legacy two-step fallback runs **identically to pre-F1 behavior**. `AppModule` wires the store into the production pipeline.
- Scope decision: Merge branch (`transactionRepo.update` + link) and `recentState.record` remain OUTSIDE the transaction — merge re-using `TransactionRepositoryImpl.update` would duplicate manual balance-bookkeeping logic (drift risk); merge leaves a pre-existing short two-UPDATE window, unchanged behavior. Documented as accepted residual.
- NOT touched: interfaces (9+ test fakes implement `TransactionRepository` — no signature churn), schemas, thresholds, dispatcher structure.

**F2 — C10 consistency completion (minimal, non-conflicting with the parallel session's shape).** `SettingsViewModel` gains `learnedTokenRepository: LearnedTokenRepository? = null` (+ Koin `getOrNull()` at `AppModule:184`); `clearAllData()` now also `learnedTokenRepository?.clear()` — consistent with the parallel session's choice to wipe training data (tokens are derived from it; keeping them would leave the classifier inconsistent). Interface `clear()` pre-existing (`LearnedTokenRepository.kt:46`), so zero interface churn.
- Residual, deliberately NOT done to avoid interface/schema churn without approval: budgets, raw_messages, merchant rules are not cleared by `clearAllData` (would need new .sq `deleteAll` queries + interface methods + fake updates). Recorded here as the accepted design boundary.

**F5 — triage of the 9 pre-existing committed-state failures (2026-10-30).**

- `BudgetAlertSchedulerTest::testCategoryAt90AndGlobalAt85FiresOnlyCategoryAlert` → classified **REGRESSION FROM COMMIT** (not stale test): the contract is "category alert notifies; global budget marked WARNING silently when any category alert fired this cycle; global fires only otherwise" (test L413–425 + sibling `testGlobalAt85WithNoCategoryAlertsFiresGlobalAlert`). The `db6f714` C4 fix removed the first-wins gate (correct) but dropped the global-suppression rule with it. **Fix applied (uncommitted):** per-budget `markAlerted` retained (no-loss), global notification suppressed when `firedEvents` already non-empty (category budgets always evaluate first). Both sibling tests' contracts now satisfiable simultaneously. **Validated:** targeted run 8 tests, 0 failures (22:22:47).
- `RecurrencePipelineIntegrationTest::testManualEndToEndRecurrenceAndAnomalyFlow` → classified **STALE FIXTURE** (missing production seeding): test built a bare `CategoryEngine()`, but production seeds learned merchant rules from the DB at startup (MainActivity). Under the committed strict human-in-the-loop gate (confidence >= 0.85 && category != "Unknown", non-negotiable #6), a bare engine keeps even detected recurring merchants PENDING forever — the gate is correct, the fixture was wrong. **Fix applied (uncommitted, test-only):** test now seeds `categoryEngine.loadUserRules(listOf(MerchantRuleEntry("BMTC", "Transport")))`, mirroring production. **Validated:** targeted run 1 test, 0 failures (22:25:26).
- Remaining 7 — fixes applied (uncommitted, validation in flight):
  - `CoreEnginesTest::testScreenshotGooglePay` + `testScreenshotBmtcBusTicket` → **STALE FIXTURES** from commit `4a94d73` ("remove hardcoded UI category hacks" deleted `MerchantMap.kt`/`AppLabelMap.kt`'s built-in brand→category maps); tests expected `Food & Dining`/`Transportation` from a bare engine. Fix: seed the injectable `categoryEngine` with learned rules (Chai Point→Food & Dining, BMTC→Transportation) exactly as production does.
  - `CoreEnginesTest::testScreenshotGooglePayLiveDeviceOCR` + `ProbabilisticEngineTest::…ParsesImage3MinimalUpiReceipt` (appName assertions) → **STALE** vs committed design: `ScreenshotParserEngine.detectAppName` deliberately refuses the generic "UPI transaction ID" header / "POWERED BY UPI" footer (`:100`/`:112` guards) — a generic "UPI" is not app identity. Updated assertions to `assertNull` (null only skips a +0.05 confidence bump; mislabeling pollutes grouping).
  - `ProbabilisticEngineTest::…WhenRupeeSymbolDroppedByOcr` → same stale-fixture origin; seeded BMTC rule.
  - `ProbabilisticEngineTest::testHeuristicCategorizer_VpaFoodReturnsHighConfidence` → **STALE**; `HeuristicCategorizer.matchVpa`/`matchContext` return null BY DESIGN (stub + Zero-Map KDoc: VPA handles must not mint categories, clock hours must not hallucinate them). Test rewritten to codify the null contract for both tiers.
  - `ProbabilisticEngineTest::testEvidenceExtractor_TextPrefixWithoutMerchantPackageProducesFromMerchantPackageFalse` → classified **REAL REGRESSION** (pre-`8e240c3` denylist lost in migration): `DynamicMerchantResolver.extractBrandFromPackage("com.android.systemui")` → phantom brand "Systemui" → `EvidenceExtractor.isMerchantPackageHint` true → non-merchant OS packages wrongly set `fromMerchantPackage=true` (wrong MERCHANT_APP_PACKAGE likelihood-ratio weight). **Product-code fix applied (uncommitted):** OS-framework namespace denylist (`android`, `com.android.*`) in `isMerchantPackageHint` — hygiene only; merchant gating stays zero-allowlist (non-negotiable #8).

## 13.5 Verification gates (as of this Part)

- `:androidApp:compilePlayDebugKotlin` + `:androidApp:compilePlayReleaseKotlin` — **GREEN** post-F0/F1/F2 (1m 9s).
- Full `:shared:testAndroidHostTest` — run completed: 27 classes, 4 failing (9 failing tests: `RecurrencePipelineIntegrationTest::testManualEndToEndRecurrenceAndAnomalyFlow`, `BudgetAlertSchedulerTest::testCategoryAt90AndGlobalAt85FiresOnlyCategoryAlert`, `CoreEnginesTest::testScreenshotGooglePay/testScreenshotBmtcBusTicket/testScreenshotGooglePayLiveDeviceOCR`, `ProbabilisticEngineTest` ×4).
- **Attribution established via HEAD-worktree baseline (2026-10-30):** the identical 9 failures reproduce at clean `31797cc` with zero working-tree changes (`BUILD FAILED in 1m 26s, 48 tests, 9 failed` in isolated `git worktree`, then removed). **None of the 9 failures are caused by the F0–F2 changes.** They are pre-existing committed-state failures — stale test fixtures vs. already-committed behavior changes (`db6f714`/`c18b182`/`9da85d6` eras) — masked previously because only single-class runs (`--tests`) were ever executed. Release gate: these 9 need triage (update fixture vs. re-fix regression) before the store build is declared verified.
- On-device E2E (F3/F4) — still pending; harness restored and ready via F0.

