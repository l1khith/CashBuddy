# CashBuddy (PaisaPal) — Concrete Improvement Plan (Per-File Change Lists)

**Date:** 2026-10-28 · **Status:** PLAN ONLY — no source code has been modified.
**Basis:** `docs/AUDIT_REPORT.md` (Parts 1–9). Every item cites the audit ID (C*, D*, 2A–2F, 3A–3E) it fixes.
**No-source-written rule honored:** this document contains partial signatures/SQL sketches for review, not applied edits.

**Project non-negotiables that constrain every change:** no SMS permission · no network permission · 100% Kotlin Multiplatform core · SQLCipher (AES-256 GCM) encryption · human-in-the-loop for confidence < 0.85 or amount ≥ ₹10,000 · 100% content-first gating, zero hardcoded sender allowlists (enforced by `ArchitectureSanityTest.kt`).

---

## Phase Order & Dependency Graph

```
Phase 0  Migration infrastructure (user_version chain)      ← PREREQUISITE for everything
Phase 1  Transactional write paths (C1, C2, C3)
Phase 2  Money as integer paise (D1 root fix)                ← needs Phase 0 migration slots
Phase 3  Critical correctness bugs (C4, C7, C8, C9, C10)
Phase 4  iOS platform honesty (C5, 2C fake-success)
Phase 5  Math-engine upgrades (calibrator, evidence groups, real median, pager race)
Phase 6  Dead-code removal
Phase 7  Compose polish (remember/keys)
Phase 8  Handoff-verification checklist
```

Phases 1–7 are otherwise independent; do not interleave 2 with 1 (both touch `TransactionRepositoryImpl`).

Effort keys: **S** = ≤1 session, **M** = one focused day, **L** = multi-day/multi-file.

---

## Phase 0 — Migration Infrastructure (PREREQUISITE) · Effort M

Replaces hand-baked silent idempotent ALTERs (`DatabaseDriverFactory.kt:23-29, 221-229, 360-455`) with an auditable version chain, and reconciles the diverged `merge_log` DDL.

| # | File | Lines | Change | Fixes |
|---|---|---|---|---|
| 0.1 | `shared/src/commonMain/kotlin/com/cashbuddy/data/local/DatabaseDriverFactory.kt` | 23-29 | Delete `executeQuietly` wrapper from the migration path (keep for schema *observability* logging only — log-and-rethrow, never swallow) | C6 |
| 0.2 | same file | 221-229 | Remove `merge_log` hand DDL duplication; rely on SQLDelight `.sq` source-of-truth | C6 |
| 0.3 | same file | 360-455 | Convert `runMigrations` to a `when (oldVersion)` chain keyed on `PRAGMA user_version`; every legacy silent ALTER becomes an explicit numbered migration (1→2, 2→3, …); record actual current version constant `DB_VERSION` | C6 |
| 0.4 | same file | 23-29 | After opening, run a one-time integrity probe (`PRAGMA foreign_key_check` + `PRAGMA integrity_check`); on failure show Settings "Repair data" suggestion rather than proceeding silently | C6 |
| 0.5 | `shared/src/commonMain/sqldelight/com/cashbuddy/db/transactions.sq` | 38-39 | Keep `ON DELETE CASCADE` for `merge_log` (SQLDelight is source of truth; the runtime copy is deleted in 0.2) | C6 |
| 0.6 | **NEW** `shared/src/commonTest/kotlin/com/cashbuddy/db/MigrationChainTest.kt` | — | Test: construct DB at version N−1 fixture, run driver migration, assert version log and FK integrity | C6 |

**Acceptance:** installing the same DB at version 1, 2, … upgrades with logged, numbered steps; a corrupted DDL fails loudly with a visible error instead of silently skipping.

---

## Phase 1 — Transactional Write Paths (C1, C2, C3) · Effort L

One pattern across all three paths: `db.transact { … }` + write-time deltas. SQLDelight `transact` is synchronous-blocking inside its lambda by design and is safe here because every caller is already on a background dispatcher (see 2F note: keep wrapped in existing `withContext(dispatcher)`).

### 1A. `MessagePipeline.ingest()`

| # | File | Lines | Change | Fixes |
|---|---|---|---|---|
| 1.1 | `shared/src/commonMain/kotlin/com/cashbuddy/core/prob/MessagePipeline.kt` | 76-338 | Hoist the entire sequence (raw insert → tx insert → updateResultingTx → anomaly updates → budget revisions → recurrence upsert) into a single `db.transact { }`. Have `Database` injected or added to the 18 constructor deps via one new `db: SqlDelightAppDatabase` (net constructor deps stay ~18; consider also the Phase-5 split) | C1 |
| 1.2 | `shared/src/commonMain/kotlin/com/cashbuddy/core/prob/MessagePipeline.kt` | 266-277 | Recurrence `upsertAll` moves inside the same transaction (currently a side branch scanning `getAll()`) | C1, D8 |
| 1.3 | `shared/src/commonMain/kotlin/com/cashbuddy/core/prob/MessagePipeline.kt` | 145-159 | Dedup candidate query becomes a bounded index query instead of window-filtered `getAll()` scan (require `idx_transactions_amount_time`-style index; SQL in `.sq` change 2.4) | D9 |
| 1.4 | `shared/src/androidMain/kotlin/com/cashbuddy/notified/…`/`TransactionNotificationListener.kt` | ~170 | Only after 1.1: the raw-id fallback becomes unnecessary; still fix the `1000 + txId % 1000` review-alert id (see Phase 3, 3.9) | C9 (part) |
| 1.5 | `shared/src/commonTest/kotlin/com/cashbuddy/core/prob/MessagePipelineTxTest.kt` (NEW) | — | Test: inject a driver that throws mid-sequence; assert zero rows for raw_message/tx/budget changes (all-or-nothing) | C1 |

### 1B. Balance deltas instead of read-modify-write

| # | File | Lines | Change | Fixes |
|---|---|---|---|---|
| 1.6 | `shared/src/commonMain/sqldelight/com/cashbuddy/db/accounts.sq` | — | Add: `applyBalanceDelta(id, delta); UPDATE accounts SET balance = balance + :delta WHERE id = :id` | C2, C3 (root) |
| 1.7 | `shared/src/commonMain/kotlin/com/cashbuddy/data/repository/AccountRepositoryImpl.kt` | — | Expose `applyBalanceDelta(id, delta)`; deprecate direct `balance` setter | C2, C3 |
| 1.8 | `shared/src/commonMain/kotlin/com/cashbuddy/data/repository/TransactionRepositoryImpl.kt` | 107-149 | `update()` recomputes `delta = newAmount − oldAmount` (and flips sign on type change); apply via `applyBalanceDelta` inside `db.transact { }` with the tx UPDATE. Trigger coexistence stays: triggers keep firing only on status transitions (documented) | C3 |
| 1.9 | `shared/src/commonMain/kotlin/com/cashbuddy/core/DuplicateReconciler.kt` | 184-223 | `mergeDuplicateGroups`: survivor promotion + loser delete + `insertMergeLog` + balance consolidation into one `db.transact { }`; replace per-duplicate `getById().firstOrNull()` read-modify-write with computed delta + `applyBalanceDelta` | C2 |
| 1.10 | `shared/src/commonTest/kotlin/com/cashbuddy/core/DuplicateReconcilerTxTest.kt` (NEW) | — | Test: two inter-leaved merges produce correct final balance (delta math, no lost update) | C2 |

### 1C. Common plumbing (prep for Phase 2)

| # | File | Lines | Change | Fixes |
|---|---|---|---|---|
| 1.11 | `shared/src/commonMain/kotlin/com/cashbuddy/data/repository/` — all 14 reps | — | Inject `ioDispatcher: CoroutineDispatcher` (constructor param, default `Dispatchers.Default` when null) and replace hardcoded `Dispatchers.DEFAULT` constants; keeps `main.async`-style leaks out and honors platform `ioDispatcher` (from `AndroidPlatform.kt:23` / `IosPlatform.kt:10`) — also fixes 2F wholesale | 2F |
| 1.12 | `shared/src/commonMain/kotlin/com/cashbuddy/di/AppModule.kt` | all 14 provider sites | Pass `platform.ioDispatcher` from the platform interface into each repo provider | 2F |
| 1.13 | `shared/src/commonMain/kotlin/com/cashbuddy/di/AppModule.kt` | 70 | Explicitly provide `FraudDetector` (remove default-instantiation inside ctor param) | SRP/DIP (5) |

**Acceptance:** kill the process (kill -9 equivalent) mid-ingest → database contains either a complete message record or nothing; two concurrent merges of the same groups yield a balance that equals the sum of parts (property test).

---

## Phase 2 — Money as Integer Paise (D1 root fix) · Effort L

Value-class `Paise(Long)` end-to-end. Strategy: **parallel columns during migration** (`amount REAL` stays readable for one release from old installs), all engines become integer-exact.

| # | File | Lines | Change | Fixes |
|---|---|---|---|---|
| 2.1 | **NEW** `shared/src/commonMain/kotlin/com/cashbuddy/domain/model/Paise.kt` | — | `@JvmInline value class Paise(val value: Long)`; `plus/minus/negate`; `div(nonZero: Long): Double` for shares only; `Paise.fromRupees(halfUp: Double)` + `Paise.ZERO` | D1 |
| 2.2 | `shared/src/commonMain/sqldelight/com/cashbuddy/db/transactions.sq` | 3 | Migration 2→3: add `amount_paise INTEGER NOT NULL` (recomputed on open from REAL for existing rows, then verified: `abs(amount*100 − amount_paise) < 1` discrepancy → log warning); year+1 release: drop REAL. All 60+ aggregate queries (`sumAll`, `getMonthlySummary`, `sumByCategory`, `getByCategoryAndPeriod`, `getHistorySummary`, INSERTs) read/write `amount_paise` | D1, D6 |
| 2.3 | same file | all trip params | Amount-related folded/typed-total tuples switch to Paise from injected double where applicable. | D1 |
| 2.4 | same file | 272, 285 | Replace non-sargable `(c.name = :category OR CAST(t.category_id AS TEXT) = :category)` with two parameterized variants: `sumByCategoryName` and `sumByCategoryId`, both id/index sargable | D2 |
| 2.5 | same file | 316-332 | `getHistoryPage` cursor guard: replace `(:cursorTimestamp IS NULL OR :cursorId IS NULL OR …)` with two explicit overloads (no-cursor variant) so partial cursor params cannot silently disable pagination | D3 |
| 2.6 | `shared/src/commonMain/kotlin/com/cashbuddy/data/repository/TransactionRepositoryImpl.kt` | 302-314, 316-366 | `mapTransaction` returns Paise; regex double-execution removed (single extract, stash in the row's parsed result — see 3.7 for UTR-index work) | D5, D1 |
| 2.7 | `shared/src/commonMain/kotlin/com/cashbuddy/core/budget/BudgetEngine.kt` | 58-88 | All sums/percent math switch to `Paise.safeDiv` on sums only for percent; status thresholds unchanged | D1 |
| 2.8 | `shared/src/commonMain/kotlin/com/cashbuddy/core/budget/BudgetPacer.kt` | 24-98 | Signatures accept `Paise` for spent/budget; double-based overloads kept one release then removed | D1 |
| 2.9 | `shared/src/commonMain/kotlin/com/cashbuddy/core/prob/DedupEngine.kt` | 8, 54-57 | `AMOUNT_EPS` deleted; tier-2 becomes exact paise equality (`it.amountPaise == newTx.amountPaise`) | D1 |
| 2.10 | `shared/src/commonMain/kotlin/com/cashbuddy/core/analytics/RecurrenceDetector.kt` | 142 | `amountPaise` field already paise; `Point.amountPaise` becomes `Paise` type from Transaction (nanos of fp rounding residue eliminated at the source) | D1 |
| 2.11 | `shared/src/commonMain/kotlin/com/cashbuddy/core/FraudDetector.kt` | 29-84 | All median/MAD math on Paise (`median`/`deviations`/`mad` switch to Long); `computeMedian` tail stays parity-correct on Long list | D1 |
| 2.12 | `shared/src/commonMain/kotlin/com/cashbuddy/core/DuplicateReconciler.kt` | — | Runner/read-modify-write now operates on `Paise` deltas (pairs with 1.9) | C2, D1 |
| 2.13 | `shared/src/commonMain/kotlin/com/cashbuddy/data/repository/` — 14 repos | — | Constructors/returns of amounts typed Paise | D1 |
| 2.14 | `shared/src/commonMain/kotlin/com/cashbuddy/presentation/` — ViewModels: `HomeViewModel.kt`, `StatsViewModel.kt`, `BudgetViewModel.kt`, `BudgetDrilldownViewModel.kt`, `TransactionDetailViewModel.kt`, `AddTransactionViewModel.kt`, `ReviewViewModel.kt` | state data classes | UI state carries formatted `String` (derived per phase-0 rule of `AmountFormatter`), not Double; comparisons use Paise | D1 |
| 2.15 | `shared/src/commonMain/kotlin/com/cashbuddy/presentation/addtransaction/AddTransactionScreen.kt` | 455-456 | Remove `?: 1L` fallbacks (see 3.8) | C8 |
| 2.16 | `shared/src/commonMain/kotlin/com/cashbuddy/domain/usecase/DataManagementUseCases.kt` | 120 | CSV parse converts currency strings to Paise (same formatter/view-schema); also feeds 3.7 dedup-key | C7, D1 |
| 2.17 | `shared/src/commonTest/kotlin/com/cashbuddy/domain/PaiseTest.kt` (NEW) | — | Property tests: half-up rounding, overflow guard, div guard (non-zero) | D1 |

**Note:** `DatabaseDriverFactory.kt` schema (Phase 0) is the only true migration blocker; `transactions.sq` schema is otherwise untouched outside money columns.

**Acceptance:** `sum(amt_paise) ≡ sum of digits` invariant property test; no `.contentEquals`-style float comparison remains in Kotlin source for money.

---

## Phase 3 — Critical Correctness Bugs · Effort M

| # | File | Lines | Change | Fixes |
|---|---|---|---|---|
| 3.1 | `shared/src/commonMain/kotlin/com/cashbuddy/core/budget/BudgetAlertScheduler.kt` | 98-117 | Replace `if (firedEvents.isEmpty())` first-wins gate with per-budget notification: build full list of `BudgetAlertSpec` and hand to notifier (one notification per breached budget, or grouped single banner listing each budget); `markAlerted` only for **notified** budgets | C4 |
| 3.2 | `shared/src/commonMain/kotlin/com/cashbuddy/core/budget/BudgetAlertScheduler.kt` | 70-96 | Alert strings move to common resources (`strings_common.xml`/iOS `Localizable`) via `expect/actual Messages` interface — OCP fail-site fix (already planned in 7.x resources work if opened) | 6.5 note |
| 3.3 | `shared/src/commonMain/kotlin/com/cashbuddy/core/anomaly/AnomalyNotifier.kt` interface | — | Widen to accept a list of `AnomalyPayload`s (supports 3.1 grouping) | C4 |
| 3.4 | `androidApp/src/main/kotlin/com/cashbuddy/notification/AndroidAnomalyNotifier.kt` | 26, 84 | 3.1 consumer + add `SupervisorJob() + Dispatchers.IO` (already flagged in 2A.2); keep scope in singleton but add explicit `close()` hook via Koin `Disposable` | 2A.2, C4 |
| 3.5 | `shared/src/commonMain/kotlin/com/cashbuddy/domain/usecase/DataManagementUseCases.kt` | 120 | CSV import: `categoryId` resolved by name match on `CategoryRepository.getAll()`; explicit "General"-named seeded category created if absent (id captured, not hardcoded); **no silent fk confusion** | C7 |
| 3.6 | same file | whole `importCsv` | Wrap in `db.transact { }` (rows append atomically) + per-row dedup key `(source: CSV, amount_paise, timestamp, merchant)` consulted against `getUserLightWindow`-equivalent bounded query. Re-import of identical CSV is a no-op | C7 |
| 3.7 | `shared/src/commonMain/sqldelight/com/cashbuddy/db/transactions.sq` | — | Add tracked operational index: `idx_transactions_dedup` on `(amount, timestamp)` — required by 1.3; and backfill a `utr TEXT` column + `idx_transactions_utr` (populated by ingest for sms/screenshot flows where UTR was statically extractable) so `findByUtr` becomes an index seek | D4, D9 |
| 3.8 | `shared/src/commonMain/kotlin/com/cashbuddy/presentation/addtransaction/AddTransactionViewModel.kt` | 187-188 | Inject the DI `CategoryEngine` singleton instead of `new Create CategoryEngine()`; prediction actually uses loaded rules/tokens. Also the `SavedEffect` (82-89) gains a `didSetFallback` warning when the category was the seeded default (catches the "Food & Dining silent default" visible to user in review) | C8 |
| 3.9 | `androidApp/src/main/kotlin/com/cashbuddy/service/TransactionNotificationListener.kt` | 170, 277 | Raw id: replace `${(1000..9999).random()}` with a monotonically-assigned sequence per-process + timestamp (e.g. `notif_${postTime}_${seq++}`); review-alert notification id derived by safe hash `1000 + (txId % Int.MAX_VALUE) % 100000` — collision probability now the birthday bound over 100k buckets, still deterministic per tx | C9 |
| 3.10 | `shared/src/commonMain/kotlin/com/cashbuddy/presentation/settings/SettingsViewModel.kt` | 219-225 | `clearAllData` wraps full clear in `db.transact { }`: transactions + budgets + raw_messages + learned tokens/training_data + merchant rules + budget-state columns + accounts `balance = 0` (or empty) + recurring_pattern reset; progress callback chains to keep UI responsive; ALSO delete files (DebugLog JSONL, exports) on Dispatchers.IO | C10 |
| 3.11 | `shared/src/commonMain/kotlin/com/cashbuddy/presentation/home/HomeViewModel.kt` | 37-56 | Stale-pattern reload: replace `getAll()` with `getSince(sinceEpoch)` bounded by the pattern's reference horizon (patterns only need last 30-90 days) | D7 |
| 3.12 | `shared/src/commonMain/kotlin/com/cashbuddy/domain/usecase/…/BatchCategorizeUseCase.kt` | 35 | `if (1.0 >= minConfidence)` dead condition removed; threshold honored; cap batch at 50 with continuation; single-writer write (`update` then `updateStatus` folded into one `db.transact` wrapper or a new repository method `updateCategorized`) | Part 1.4 (part) |
| 3.13 | `shared/src/commonMain/kotlin/com/cashbuddy/core/prob/…/SourceDetector.kt` usage | MessagePipeline.kt:84, listener 182 | Detect once in listener, pass `NotificationSource` into pipeline — removes the double call | Part 1.4 (part) |

**Acceptance:** every item pairs with a unit test listed in Phase 8.

---

## Phase 4 — iOS Platform Honesty (Non-Negotiables & Contracts) · Effort L

| # | File | Lines | Change | Fixes |
|---|---|---|---|---|
| 4.1 | **NEW** `shared/src/iosMain/kotlin/com/cashbuddy/data/local/IosDatabaseDriverFactory.kt` | 9-12 | Replace `NativeSqliteDriver` with SQLCipher-equivalent: `net.zetetic` / `co.touchlab.sqliter` SQLCipher actual for iOS (SQLDelight supports SQLCipher via `SQLCipherHelper`/embedded factory on Darwin via `net.zetetic.database.sqlcipher`) + Keychain-stored passphrase (SecItem), mirroring Android's Keystore flow | C5 (non-negotiable) |
| 4.2 | `shared/src/androidMain/kotlin/com/cashbuddy/data/local/AndroidDatabaseDriverFactory.kt` | 16-19 | `System.loadLibrary("sqlcipher")` failure must propagate: throw explicit `SqlCipherUnavailableException`; add a fail-fast startup gate in `CashBuddyApplication.kt` (3rd-party stable) — degrade visibly in Settings (never silently run UNENCRYPTED). Reachability of prod path verified post-change | C5 part |
| 4.3 | `shared/src/iosMain/kotlin/com/cashbuddy/platform/IosFileExporter.kt` | 4-15 | Real export: `UIDocumentPickerViewController` wrapper (`NSURLSession`/`UIKit`-free: use `NSFileCoordinator` + share sheet via `UIKitUtil`); if not implementable yet, **fail honestly**: `return Result.failure(UnsupportedOnIosException)` — never fake success | 2C LSP |
| 4.4 | `shared/src/commonMain/kotlin/com/cashbuddy/platform/FileExporter.kt` | 19 | Delete the empty-file default `exportCsvFile(fileName, "")` fallback; make `exportFileStream` abstract so every actual must implement the honest behavior | 2C trap |
| 4.5 | `shared/src/iosMain/kotlin/com/cashbuddy/platform/FilePicker.ios.kt` | 10 | Implement via `UIDocumentPickerViewController` (security-scoped bookmark reads) or `Result.failure` — no-op neither hides broken feature | 2C |
| 4.6 | `shared/src/iosMain/kotlin/com/cashbuddy/platform/IosPlatform.kt` | 8, 12-14 | `getAppVersionDisplay()` reads real CFBundle version (`NSBundle.mainBundle.infoDictionary`); `notifyTransactionsUpdated()` implemented via `WidgetCenter.reloadAllTimelines` equivalent (Glance widget exists only on Android; on iOS call the actual widget/hello reload or document permanent no-op in KDoc with honest behavior) | 2C |

**Acceptance:** iOS device settings → Files → “CashBuddy export” produces a real file with data; DB file on disk is SQLCipher header (`Salted__` prefix) instead of `SQLite format 3`.

---

## Phase 5 — Math-Engine Upgrades · Effort M

| # | File | Lines | Change | Fixes |
|---|---|---|---|---|
| 5.1 | `shared/src/commonMain/kotlin/com/cashbuddy/core/prob/LikelihoodCalibrator.kt` | all | Wire the feedback loop: `observe(signal, present, confirmed, isDebit)` called from confirmed-transaction write path (inside phase-1.1/1.8 transaction); `calibratedLR` switches to Laplace-blend formula (inline sketch in chat §4): below n<20 sample → trust hand LR; above → geometric blend `baseLR^(1−w) · empirical^w`, `w = n/(n+50)` | D13 root (dead-in-effect N+1 becomes live learning) |
| 5.2 | `shared/src/commonMain/kotlin/com/cashbuddy/core/prob/ProbabilisticClassifier.kt` | 67-71 | Evidence-group dedupe: amount signal group (amount-in-context × amount-with-debit/credit × amount-anywhere) becomes `maxOf` over applicable group members (single `apply` call) — removes correlated double-count; exceptions documented in contributions (`name = "amount_group(max)"`) | Part 9.3.1 |
| 5.3 | `shared/src/commonMain/kotlin/com/cashbuddy/core/analytics/RecurrenceDetector.kt` | 175-176, 182 | Compute real median interval from consecutive deltas (sketch in chat §3); hardcode 24/168 only as a fallback for single-interval cases (`intervals.isEmpty()`) — field name finally matches semantics (HomeViewModel consumers get better alarm predictions) | Part 9.3.2 |
| 5.4 | same file | 85 | Add a named comment/constant `POPULATION_VARIANCE` or switch to sample variance (÷N−1) when `activeCounts.size > 1` — either is defensible; document choice | Part 9.3.3 |
| 5.5 | `shared/src/commonMain/kotlin/com/cashbuddy/presentation/transactions/TransactionListViewModel.kt` + **NEW** `TransactionPager.kt` thread-safety | 15-22 | Pager: `collapse`/`reset` and `loadNextPage` guarded by a single `Mutex` (or convert to a `StateFlow<PageCursor>` reducer — cursor transitions atomically decided); kills the collectLatest-vs-loadNextPage dup/skip race | Part 4 row debounce |
| 5.6 | `shared/src/commonMain/kotlin/com/cashbuddy/core/prob/ProbabilisticClassifier.kt` | 48-121, 123-196 | Delete `classify()` (blocking twin); single `classifySuspending()` — DRY (all call sites are in coroutines) | DR/6.2 |
| 5.7 | **NEW** `shared/src/commonMain/kotlin/com/cashbuddy/domain/model/MoneyFormat.kt` | — | Central currency formatter (locale-consistent ₹, lakh/crore grouping), consumed by all UI (pairs with 2.14): kills the four scattered formatCurrency sites | D1 part |

## Phase 5B — Learned-Token Safeguard Wiring (the 500k-token collapse plan) · Effort S–M

**Context (verified 2026-10-29):** All four safeguards' SQL/queries **already exist and are dead** — zero Kotlin callers (grep-verified across the repo):

| Safeguard | SQL that exists | Reality in production |
|---|---|---|
| 1. `IN (...)` keyed lookup (B-tree on PK) | `learned_token.sq:28-30` `getByTokens` | **Dead.** Live path is `queries.getAll().executeAsList()` — full-table materialization **per message** (`TokenEvidence.kt:39` → `LearnedTokenRepositoryImpl.kt:49-61`, invoked per classification from `EvidenceExtractor.kt:129`) |
| 2. "≥5 observations" significance gate | `learned_token.sq:18-20` `getSignificant`, `:22-26` `getTopSignificant` | **Dead.** Interface KDoc *claims* ≥5 (`LearnedTokenRepository.kt:24`, `TokenEvidence.kt:20-22`); impl loads ALL rows incl. 1-observation noise tokens with extreme smoothed LR (audit D12) |
| 3. In-memory L1 cache | — (Kotlin-side construct) | **Absent.** `LearnedTokenRepositoryImpl.kt` is 66 lines, DB-only, no map (full read verified) |
| 4. Purge GC worker | `learned_token.sq:35-41` `purgeOldestLeastUsed`, `:32-33` `getCount` | **Dead.** No `Purge`/`vacuum`/`cleanup` match in any `.kt`; no Worker exists |

Also note: the doc-fragment's proposed `DELETE FROM learned_token ORDER BY … LIMIT :excess` would **not compile** — SQLite has no `DELETE … ORDER BY … LIMIT`. The `.sq` file's subquery form (`WHERE token IN (SELECT … LIMIT :excess)`, `learned_token.sq:37-40`) is the correct SQLite syntax and should be kept.

The single reader (`TokenEvidenceExtractor.extract/extractSync`, `TokenEvidence.kt:30-41`) and the single writer (`TransactionDetailViewModel.kt:51 record()`) are both live — so the collapse risk is real: every notification, tokenize-first would be O(5-8 keys); today's materialize-first is O(table size) *per message*, on the listener thread, upstream of the same ingest path flagged in audit D9.

| # | File | Lines | Change | Fixes |
|---|---|---|---|---|
| 5B.1 | `shared/src/commonMain/kotlin/com/cashbuddy/domain/repository/LearnedTokenRepository.kt` | 16-24 | Replace the `getAllSignificant()/getAllSignificantSync(): Map<String, TokenStats>` contract with `getForTokens(tokens: Collection<String>): Map<String, TokenStats>` (plus keep `record`, `clear`); delete the misnamed "getAllSignificant" family | D12 |
| 5B.2 | `shared/src/commonMain/kotlin/com/cashbuddy/data/repository/LearnedTokenRepositoryImpl.kt` | 16-66 | Implementation: (a) `private val cache = ConcurrentHashMap<String, TokenStats>()`; (b) `suspend fun warmCache(topN = 2_000)` calling `queries.getCount()` + `queries.getTopSignificant(2000)` once at construction/DI init; (c) `getForTokens` → cache hits first, misses batched through `queries.getByTokens(missing)` (standard SQLDelight collection param), results cached; (d) `record()` additionally updates the cache synchronously next to the existing transaction (`record` DB write stays as-is, already atomic, L26); (e) `clear()` also clears the cache | D12, Safeguards 1+3 |
| 5B.3 | `shared/src/commonMain/kotlin/com/cashbuddy/core/prob/TokenEvidence.kt` | 30-41, 53 | Invert order: `Tokenizer.tokenize(rawText)` FIRST, then one `learnedTokenRepository.getForTokens(tokens)` per message; delete the full-map calls; keep the `.filter { (txn+nonTxn) >= 5 }` defense-in-depth line in `evaluate` in case the cache holds pre-gate rows | Safeguards 1+2 |
| 5B.4 | `shared/src/commonMain/kotlin/com/cashbuddy/core/prob/EvidenceExtractor.kt` | 129 | No structural change — `extractSync` signature unchanged behind the new repository contract; confirm the sync path now runs under the caller's dispatcher only after the cache is warmed; if first-run cold path can't be sync-safe, gate with `runCatching { } fallback 0.0` (already the behavior at L129 `?: 0.0`) | Safeguard 1 |
| 5B.5 | `shared/src/commonMain/sqldelight/com/cashbuddy/db/learned_token.sq` | 28-41 | Keep as-is; optionally add `deleteAllBelow(total: Long)` variant if a manual "reset learning" needs a stronger trim; index `idx_learned_token_counts` (L9-10) already serves the ORDER BY | — |
| 5B.6 | **NEW** `androidApp/src/main/kotlin/com/cashbuddy/work/TokenPurgeWorker.kt` | — | Weekly `CoroutineWorker` (mirror `WeeklyDigestWorker.kt:40-81` pattern): `count = learned_tokenQueries.getCount()`; if `> 20_000`, `db.transact { purgeOldestLeastUsed(count - 20_000) }`; clear cache; schedule via `PeriodicWorkRequest` (7 days, ~Sunday 02:00, `Constraints` = device idle/charging); post-conditions logged | Safeguard 4 |
| 5B.7 | `shared/src/commonMain/kotlin/com/cashbuddy/di/AppModule.kt` | near 71-72 | Wire the warmed repository singleton (`warmCache` on IO dispatcher at init); expose `TokenPurgeScheduler`/equivalent Android-side (Work is Android-only by KMP constraint — iOS would need a `expect/actual` `TokenMaintenance` facade to run the same trim on a timer later) | Safeguards 3+4 |
| 5B.8 | `androidApp/src/main/kotlin/com/cashbuddy/CashBuddyApplication.kt` | 22, 43-58 | Register `TokenPurgeWorker` with the existing `applicationScope`/WorkManager setup next to `BudgetAlertWorker`/`WeeklyDigestWorker` | Safeguard 4 |
| 5B.9 | **NEW** tests `shared/src/commonTest/kotlin/com/cashbuddy/data/…/LearnedTokenCacheTest.kt` | — | (a) 50k synthetic tokens; `getForTokens(8 tokens)` must complete via IN-query path without materializing getAll (assert call count); (b) cache-miss then cache-hit latency assertions; (c) purge worker: seed 22k rows → run → assert 20k remain, rarest-first ordering verified | Verification § |

**Acceptance (mirrors the doc-fragment's spec, made testable):**
1. 50,000 seeded dummy tokens + 5 known tokens message → `evaluate()` path performs exactly one `getByTokens` call, no `getAll` call, wall-time < 5 ms on commonMain test JVM (device assert: no StrictMode violation on listener thread).
2. Purge worker manual run: 52,000 → 20,000 rows, deleted rows are the lowest `(transaction_count + non_transaction_count, last_updated)` — then cache re-warms to ≤2,000 entries.
3. Significance gate provable: a token with 1 observation contributes `0.0` to `tokenLogOdds` even after cache populates.



| # | File | Lines | Change |
|---|---|---|---|
| 6.1 | `shared/src/commonMain/kotlin/com/cashbuddy/core/prob/AccountEngine.kt` | all 74 | Delete file |
| 6.2 | `shared/src/commonMain/sqldelight/com/cashbuddy/db/goals.sq` | all 29 | Delete + `user_version` hop 3 drop-table migration (Phase 0 chain); replace any UI ledger references if any appear (grep says none) |
| 6.3 | `shared/src/commonMain/kotlin/com/cashbuddy/core/prob/HeuristicCategorizer.kt` | all 34 | Delete + its tests |
| 6.4 | `shared/src/commonMain/sqldelight/com/cashbuddy/db/signal_observations.sq` | 16 | Keep table (5.1 revives it), delete unused `getAll`/`reset` unless 5.1 needs them |
| 6.5 | `shared/src/commonMain/sqldelight/com/cashbuddy/db/categories.sq` deleteById/update/insert | partial | Keep queries; die with the eventual category-CRUD; comment-out now to reduce generated surface |
| 6.6 | `shared/src/commonMain/sqldelight/com/cashbuddy/db/learned_token.sq:18-20` | partial | `getSignificant` becomes load-bearing in 5.2 follow-up (repository switches to it, "≥5 observations" contract restored); no deletion — **activate instead** |
| 6.7 | `shared/src/commonMain/kotlin/com/cashbuddy/presentation/components/PaisaPalComponents.kt:44` | 1 | Delete `PaisaPalMetricCard` composable |
| 6.8 | `androidApp/src/play/kotlin/com/cashbuddy/ocr/MlKitOcrEngine.kt:15-17` | partial | Remove unused context field/param |
| 6.9 | `shared/src/commonMain/kotlin/com/cashbuddy/core/prob/MessagePipeline.kt:340-349` | partial | Inline `format2Decimals` into `formatAnomalyNote` (then delete) |
| 6.10 | `shared/src/commonMain/kotlin/com/cashbuddy/presentation/addtransaction/AddTransactionViewModel.kt:41` | partial | Delete `typealias ManualEntryViewModel` (rename usages) |
| 6.11 | `shared/src/commonMain/kotlin/com/cashbuddy/core/ScreenshotParserEngine.kt:332,356` | partial | Delete `typealias bool`; use real `Boolean` (smells like a swift-interop artifact) |
| 6.12 | `shared/src/commonMain/kotlin/com/cashbuddy/presentation/budget/BudgetViewModel.kt:38-45` | partial | Collapse 3 unused public StateFlows into `uiState` |
| 6.13 | `shared/src/commonMain/kotlin/com/cashbuddy/presentation/home/HomeViewModel.kt:58-59` | partial | Remove public `unreviewedCount` StateFlow (already inside uiState) |
| 6.14 | `shared/src/commonMain/kotlin/com/cashbuddy/presentation/CashBuddyApp.kt:66` | partial | Delete the second `GetUnreviewedCountUseCase` collection — bottom bar reads HomeViewModel-derived count |

---

## Phase 7 — Compose Polish (per-recomposition waste) · Effort M

| # | File | Lines | Change |
|---|---|---|---|
| 7.1 | `shared/src/commonMain/kotlin/com/cashbuddy/presentation/budget/BudgetCard.kt` | 101, 78-87, 133 | `now` computed once outside the composable body and passed in (or `val now = remember { currentTimeMillis() }` at Screen granularity); remember the `Triple`/contentDescription |  3C |
| 7.2 | `shared/src/commonMain/kotlin/com/cashbuddy/presentation/components/TransactionItemCard.kt` | 127, 150, 164 | `val fmt = remember(transaction.timestamp) { formatTimestamp(...) }`; hoist `getCategoryDrawable(categoryName, isDebit)` to a `HashMap`-memoized small table (or precompute into the `Transaction` immutable model at VM level) | 3C |
| 7.3 | `shared/src/commonMain/kotlin/com/cashbuddy/presentation/review/ReviewScreen.kt` | 114-116 | `val filtered = remember(state.transactions, state.selectedConfidenceFilter) { … }` (or derive in VM) | 3C |
| 7.4 | `HomeScreen.kt`, `StatsScreen.kt`, `ReviewScreen.kt`, `DataViewScreen.kt`, `PersonalizationDashboardScreen.kt`, `SettingsScreen.kt` conditional `item { }` blocks | 33 sites (:232 Home etc.) | Add `key = "<stable-id>"` to every conditional/static `item { }` (strings from Part 3B), so conditional appearance keeps item identity stable | 3B |
| 7.5 | All effect-consuming screens (`HomeScreen.kt:74-80`, `AddTransactionScreen.kt:140-144`, `ReviewScreen.kt:92-98`, `TransactionDetailScreen.kt:82-85`, `SettingsScreen.kt:100-104`, `PersonalizationDashboardScreen.kt:82-84`, `DebugLogScreen.kt:132-134`) | — | Keep `LaunchedEffect(Unit)` (idiom fine) but ensure the VM emitters *can't* fire before the screen is attached on config restore (e.g., add `.viewmodelScope.launch { delay(1) }`-free design: confirm `replay = 1` for terminal effects or screen re-navigation contract) — small; **or** standardize on `repeatOnLifecycle` (variant per platform) if the team prefers; **pick one house rule and document in `AGENTS.md`** | 3D |
| 7.6 | `shared/src/commonMain/kotlin/com/cashbuddy/presentation/stats/StatsScreen.kt:197`, `HomeScreen.kt:309`, `TransactionListScreen.kt:515` | minor | `remember`-wrap the computed strings/sums | 3C minor |

---

## Phase 8 — Verification Checklist (handed to QA/test task list)

1. Migration chain: fresh install vs installed-at-v1..vN upgrade UI flows.
2. Kill-process test: `adb shell am force-stop` after each of (raw-insert / tx-insert / budget-pass / recurrence-upsert) → DB contains zero-or-complete, verified by SQL dump.
3. Concurrent double-merge and double-edit stress: 50 parallel ops on 10 groups → final balance property passes (Phase 1 tests).
4. Money: golden CSV round-trip import-then-re-import → row count unchanged, categories correct (3.5) — catches C7 + paise migration jointly.
5. Budget alerts: fixture with 3 simultaneous breaches → 3 notifications (or 1 grouped), all subsequent alerts fire next cycle.
6. iOS: export file opens with nonempty content; DB file header `Salted__`; widget reloads after edits.
7. Classifier A/B: capture 200 labeled notification fixtures; P-evaluation before/after 5.2 to confirm expected-P drops from biased-high to interval-tested values.
8. Recurrence: rent-like fixture (1st & 15th) → medianIntervalHours ≈ 360±24, not 24.
9. `ArchitectureSanityTest.kt` stays green (proves no hardcoded sender was smuggled in by any phase).
10. Permission manifest re-audit: `grep -rn "RECEIVE_SMS\|INTERNET" --include="AndroidManifest.xml"` returns zero.

---

## Suggested execution order (one team cadence)

| Sprint | Scope |
|---|---|
| 1 | Phase 0 + 6.1-6.3, 6.7-6.11 (free wins) |
| 2 | Phase 1 (C1,C2,C3) |
| 3 | Phase 3 non-engine items (3.1-3.5, 3.9-3.13) |
| 4-5 | Phase 2 (paise) behind a feature flag; Phase 4 (iOS) |
| 6 | Phase 5 + remaining Phase 7 |

---

## Sprint: Store-Release Readiness (planned 2026-10-30 — from current audit drift, NOT stale Parts)

**State of record, verified 2026-10-30 against working tree + git log:**
- C2/C3 fixed & committed `13e1bcb` · C4/C8/C9 fixed & committed `db6f714` · C7 fixed `c18b182` (this plan's audit Parts pre-dating those commits are stale on those items)
- In-flight (uncommitted, parallel session): 2D screenshot ANR fix (copy moved to `Dispatchers.IO` in `MainActivity.kt` — correct minimal approach), C1 step-1 in `MessagePipeline.kt` (anomaly pre-insert, `notes` at insert), R8 ProGuard keep rules (`proguard-rules.pro`: serialization/Koin/data/nav), C10 partial (`SettingsViewModel.clearAllData`: + training data wipe + account balances zeroed), harness moved to `androidApp/src/debug` + `androidApp/src/release` stub
- **Not yet done anywhere:** harness call site in main's `MainActivity` (currently unreachable in every build), C1 driver-level transaction wrap, remaining `clearAllData` tables (learned_token, budgets, raw_message), release-variant compile+R8 verification, on-device E2E, Play paperwork

**Order (each step independently revertible; verify = compile + existing unit suite green):**
- F0: restore `E2eDebugHarness()` call in main `MainActivity` inside the `AUTHENTICATED` Box (variant source sets now supply debug-impl / release-no-op; internal runtime gate already handles edge cases)
- F1: wrap ONLY `rawMessageRepo.insert` → `transactionRepo.insert` → `rawMessageRepo.updateResultingTx` in one driver-level SQLDelight transaction (idiom: `LearnedTokenRepositoryImpl.record()`); budget/recurrence/merge stay outside — behavior otherwise unchanged
- F2: complete C10 `clearAllData`: wrap in single transaction + clear `learned_token`, budgets, raw_message (mirror `13e1bcb` transact pattern)
- F3: `assemblePlayRelease` + install + listener-bind + parse smoke (SQLCipher load, token JSON serialization through `Contribution`)
- F4: full E2E session (dry-run examine → live T1→T8 dedup) on the real device per `docs/E2E_NOTIFICATION_TEST_PLAN.md`; then commit the approved batch + Play Console declarations (privacy policy, Data Safety, NotificationListenerService disclosure)

---

*All line numbers reference the codebase as-is on 2026-10-28; re-verify lines near merge time since this branch is actively developed. Every change keeps: zero new network/SMS dependencies, human-in-the-loop thresholds (0.85 confidence · ₹10,000) untouched, and pure-KMP core boundaries.*
