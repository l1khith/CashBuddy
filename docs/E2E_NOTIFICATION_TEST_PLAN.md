# CashBuddy — End-to-End Notification Pipeline Test Plan (ADB-Driven)

**Status: PLAN ONLY — written 2026-10-29, deliberately NOT executed.** Execute top-to-bottom in one ~45-minute session.
**Purpose:** drive the live pipeline (NotificationListenerService → MessagePipeline → parser/classifier → dedup → budgets/recurrence → UI) with real `adb`-posted notifications and check it on-device, instead of trusting compile/unit-test results.
**Each test row links the audit finding it probes** (`docs/AUDIT_REPORT.md` IDs) so this plan doubles as a regression probe for known-open bugs.
**Also probes:** the R1–R8 fixes just committed (`31797cc`) — tests T13/T14 exercise the L1 cache + ≥5 gate + purge on real data.

---

## 1. Environment & Preconditions

| Item | Value / Command |
|---|---|
| **Device** | **Physical OnePlus device shared from this machine — one of the following is selected by the user: an active OnePlus device connected via ADB USB debugging (`adb devices` verifies) — replaces the previously-listed AVD **Pixel_9a** (`android emulator start Pixel_9a`, optional fallback only) |
| Package (verified in `androidApp/build.gradle.kts`) | `com.cashbuddy` |
| **Installed state** | **App already installed on the device. DO NOT install, re-install, update, clear-data, or uninstall. All user data MUST remain untouched.** Tests probe the *existing* app instance and the *existing* encrypted data. Verify identity before testing: `adb shell dumpsys package com.cashbuddy \| findstr "versionName versionCode"` and record it in the session log |
| Listener service (verified in `AndroidManifest.xml:48-55`) | `com.cashbuddy/com.cashbuddy.service.TransactionNotificationListener` |
| Permissions reality check | Manifest **removes INTERNET/ACCESS_NETWORK_STATE** (`AndroidManifest.xml:7-8`) — app cannot leak test data anywhere; good |
| Logcat tags (verified in source) | `TxNotificationListener`, `CashBuddyApplication`, `CashBuddyWidgetReceiver`, `MlKitOcrEngine`, `ScreenshotParser`, `ScreenshotHandler` |
| DB access | **SQLCipher-encrypted — do NOT attempt to read `databases/*.db` via adb.** Verify through (a) logcat, (b) the app's own Debug screens, (c) CSV export → `adb pull` |

### 1.1 Setup sequence (execute in order)

```powershell
adb devices                                            # confirm the OnePlus serial is listed, authorize the USB-debugging prompt ON-DEVICE
adb shell 'while [ "$(getprop sys.boot_completed)" != "1" ]; do sleep 2; done'
# NO build, NO install, NO uninstall — the app on the device is the test subject as-is.
# Record the exact installed build (release 1.0.4? debug? which commit?):
adb shell dumpsys package com.cashbuddy | findstr "versionName versionCode pkgFlags"
adb shell pm list packages -f com.cashbuddy             # APK path tells you the variant (base.apk = release store build, playDebug path = debug variant)
adb shell cmd notification allow_listener com.cashbuddy/com.cashbuddy.service.TransactionNotificationListener
adb shell settings get secure enabled_notification_listeners   # must contain the component above
adb shell dumpsys deviceidle whitelist +com.cashbuddy           # ColorOS/OnePlus: stop aggressive Doze from dropping the listener
# Open the app ONCE (do not touch any destructive settings) so the listener binds,
# then close to background. NEVER clear app data.
```

**🚫 Data-preservation rules (non-negotiable on the user's personal device):**
1. No `adb install` / `adb uninstall` / Settings→Clear storage. Everything runs against the existing install.
2. `force-stop`/`am kill` are only usable with the user's explicit go-ahead (in-memory RecentStateRepository resets — harmless to disk data — but confirm first); **T15/T20 are SKIP by default** on this device.
3. Budget/balance-affecting tests (T3 ₹15k debits, T8-T9 duplicates) will write real rows into the user's live database. Prefer test-named merchants (e.g. "E2ETEST Pay") so the entries are identifiable and can be reviewed/deleted in-app afterward.
4. Never paste the user's real recent SMS content into the test corpus; use synthetic content (amounts still exercise the same parser branches).

**Grant NotificationListenerService access (required before ANY test):**

```powershell
adb shell cmd notification allow_listener com.cashbuddy/com.cashbuddy.service.TransactionNotificationListener
# verify:
adb shell settings get secure enabled_notification_listeners
# must contain: com.cashbuddy/com.cashbuddy.service.TransactionNotificationListener
```

**Start log capture:**

```powershell
adb logcat -c
adb logcat TxNotificationListener:V CashBuddyApplication:V CashBuddyWidgetReceiver:V *:S > e2e_logcat.txt
```

---

## 2. Posting Primer (Windows-specific pitfalls)

```powershell
adb shell cmd notification post -t "TITLE" tag "body text"
```

- Posted by package **`com.android.shell`** — the app's own SourceDetector will see this package. That is a feature here: it proves the classifier is content-first (zero hardcoded allowlists — if it were allowlist-driven, shell-pkg posts would fail and we'd catch a rule violation).
- ⚠ **PowerShell quoting:** single-quote the whole shell argument, double-quote inner title. Example below. Avoid `<`, `>`, `₹` issues by using `INR`/`Rs` (the amount regexes accept `Rs`; `₹` posts fine in most terminals but is not required).
- 🖥 **Debug vs release (per user note):** `cmd notification post` delivers to **any** installed build — the listener parses releases identically. What changes is **observability**: a release build (store/signed 1.0.4) reports almost nothing to logcat (and R8 may strip logs), so verification on release relies on [U]/[D]/[X] surfaces (Home cards, Debug screens, CSV export). A debuggable build reports the [L] evidence; check which variant is installed via the PKG dump in §1.1 (`pkgFlags=[DEBUGGABLE]` = debug). If the installed release predates the Phase-5B/`31797cc` code, T13/T14/T15 can only validate shipped behavior, not the new safeguards — record installed build explicitly in results.
- Posts go to the current user's notification rail (Project Mainline `cmd`), always "bigtext" by default.
- To test a *silent* background notification (SMS-receipt style, no heads-up): add `-S bigtext`.
- Wait ~2 s between posts; inspect logcat after each (T-steps include checks).

```powershell
adb shell cmd notification post -t 'ICICI: Rs.540.00 debited' tx1 'INR 540.00 debited from A/c XX4321 on 29-10-26. UPI Ref 123456789012'
```

### 2.1 In-app E2E Debug Harness (debug builds only — one-tap alternative to posting)

Coverage commit pairing: harness implemented 2026-10-29 in working tree (uncommitted):

- `androidApp/src/main/kotlin/com/cashbuddy/debug/E2eDebugHarness.kt` — new file
- `androidApp/src/main/kotlin/com/cashbuddy/MainActivity.kt` — `AuthState.AUTHENTICATED` branch wraps `CashBuddyApp` in a `Box` and hosts `E2eDebugHarness()`

Behavior:
- **DRY-RUN is the DEFAULT (switch ON):** the sheet opens in examine-mode — pressing **Analyze** (fixed T-rows) or **Analyze** (custom freeform) evaluates the text through a READ-ONLY mirror of `MessagePipeline.ingest` stages 2–6 (`MessagePipeline.kt:83–171`): `SourceDetector.detect` → `EvidenceExtractor.extract` → `classifier.classifySuspending` → recurring-pattern lookup → inline `PolicyEngine.action` replica → read-only dedup window query + `DedupEngine.resolve`. The report shows: resulting Action (IGNORE/ASK_USER/LOG_AND_FLAG/AUTO_LOG), P(transaction), amount/type/account/UTR/merchant, field confidences, top-3 signal contributions + learned-token LR, and the dedup verdict (INSERT / MERGE into tx #id / SKIP). **NOTHING is stored** — no raw_message, no tx, no budget/pattern/recent-state writes. Freeform typing accepts ANY content (bank msg, any app notification, weird formats).
- **Live mode (switch OFF) restores Post behavior:** fixed T-buttons (T1–T3, T5–T7) and the custom freeform Post go through `NotificationManagerCompat` → the app's own listener ingests via the REAL pipeline — this path DOES write real rows (see §1 data-preservation rules); use deliberately for T8 (re-post T1 → tier-1 UTR merge) and confirmation gates.
- **Listener status row** shows bound/not-bound (mirrors plan §1.1 grant gate).
- **Release builds render nothing** (`isDebuggableBuild` gate) — zero overhead/no release-UI change.
- One nav-quirk risk to verify in §1 compile: harness pill is composed AFTER `CashBuddyApp` inside the same `Box`, so it draws above the app UI; confirm no bottom-sheet conflict with the app's own sheets (Budget/Review edit dialogs).

Scope note: T9/T10+ (burst, advanced timing, kill-mid-ingest) still require planned adb §3 rows — the harness covers the deterministic one-tap subset (T1/T2/T3/T5/T6/T7 fully in DRY-RUN read-only, T8 live-only) — adb-only rows remain: T9 onward, T16, T20.


---

## 3. Test Matrix

Legend: **E/F** = Expected result on GREEN/FIXED code. `(probe)` = known-open audit item, current fix status UNVERIFIED → record actual result even if "fail". Verify via: **[L]** logcat grep, **[U] UI in-app**, **[D]** debug screen, **[X]** CSV export/pull, **[W]** widget.

| # | Test | Deterministic adb command | Expected | Verify | Audit links |
|---|---|---|---|---|---|
| **T1** | Listener grant + basic debit parse | post -t 'ICICI: Rs.540.00 debited' t1 'INR 540.00 debited from A/c XX4321 on 29-10-26. UPI Ref 123456789012' | raw_message row created; classified TRANSACTION; amount 540, DEBIT, UTR `123456789012` extracted | [L] `TxNotificationListener` onNotificationPosted/pipeline logs · [U] Home recent list shows ₹540 debit card | C1 (happy path piece) |
| **T2** | Credit parse & type detection | post -t 'HDFC: Rs.1,200.00 credited' t2 'INR 1200 credited to A/c XX9876' | Transaction, CREDIT, amount 1200 | [U] Home card type badge · [D] DebugLog screen shows type confidence 0.95 (`FieldConfidenceEstimator.kt:61`) | Part 9.2 M7 |
| **T3** | Human-in-the-loop ≥ ₹10,000 | post -t 'ICICI: Rs.15,000 debited' t3 'INR 15000 debited from A/c XX4321' | Amount ≥ 10k → **PENDING**, appears in Review screen, NOT auto-confirmed in Home | [U] Review screen badge count +1 · [L] pipeline status=PENDING | Rule 6 (AGENTS.md) |
| **T4** | Low-confidence → Review | post -t 'abc' t4 'random words - electricity bill update news' | pTransaction low → not inserted as tx, or inserted PENDING; NOT in Home confirmed list | [L] contribution list, pTransaction value · [U] | Rule 6 |
| **T5** | OTP → ignored | post -t 'ICICI: OTP' t5 'OTP is 4812 for your transaction' | NOT inserted (OTP_KEYWORD LR 0.02, `LikelihoodRatios.kt:26`) | [L] classified non-tx · [D] DebugLog shows OTP contribution | Part 1.2 LR table |
| **T6** | Promo → ignored | post -t 'ICICI: Offer!' t6 'Get 10% cashback this festive season, limited offer' | not inserted; logged as NON_TRANSACTION (if repo records) | [L] · [D] | Part 1.2 |
| **T7** | Amount ₹0.00 → ignored | post -t 'ICICI: Rs.0.00' t7 'INR 0 debited from A/c XX4321' | dropped before insert (`MessagePipeline.kt:138-139`) | [L] no tx-insert log; [U] nothing appears | Part 1.4 edge list |
| **T8** | Tier-1 dedup: exact UTR repeat | **Re-post T1 body within 5 min** (same UTR 123456789012) | `DedupEngine` tier 1 → Merge, upsert into existing tx row, not a second tx | [L] Merge decision logs · [U] Review "merged" state or unchanged row count | C2 path, DedupEngine.kt:44-51 |
| **T9** | Tier-2 soft dedup: same amount+merchant, no UTR | post twice within 5 min: 'Amazon Pay: Rs.99 debited' … 'Amazon Pay: Rs.99 debited …' (no UTR) | tier-2 merge (ε=0.01, window=300 s) | [L] merge logs; row count unchanged | DedupEngine.kt:53-69 |
| **T10** | Burst detection | post 5 messages with same amount, different UTR, within seconds | burstDetected evidence → contributions logged | [L] 5 ingest batches with burst contribution | LikelihoodRatios.kt:33 |
| **T11** | Raw-id PK collision / review-alert ID | post ~20 rapid distinct small txs | no dropped (notification insert failures in logcat should be zero); review banner IDs deterministic per tx | [L] zero `unique constraint` errors on raw_messages | C9 (fix is Phase-3 item — **probe**: may still collide; record) |
| **T12** | Budget breach alert | Create 2 category budgets BELOW current spend first (via app UI), then post 2 txs tripping them | **BOTH budgets alert, not only the first** (the C4 first-wins bug fix) | [U] 2 notifications/banners · [L] markAlerted per-budget | C4 — **probe**: fix 3.1 not in commit history; likely FAIL → record |
| **T13** | Recurrence write-up | Post 4+ weekend/weekday-pattern recurring-looking txs (same merchant+amount paise-precision) | `RecurrenceDetector` ≥ count, activeDays, CV gates; pattern row written | [U] Home "bills due" section after re-detection · [D] DebugLog | Part 9.2 M4; median fix already in 9da85d6 — verify real median value (not 24/168 constant) in pattern row |
| **T14** | **Learned-token ≥5 gate (validates 5B + R-fixes live)** | Confirm ≥5 transactions containing a distinctive merchant token (e.g. "bottlelab") via Review UI; then post a new message containing it, and also a message containing a 1-observation token (e.g. "zyxqt") | `learned_tokens` contribution non-zero for established token AND **0.0 log-odds for 1-observation token**; no full-table reads (`getForTokens` path only) | [L] contributions JSON · [D] DebugLog screen: contribution name `learned_tokens` = 0.0 → 1-obs ignored per `LearnedTokenRepositoryImpl.kt:102,120` | D12, 5B, R-fixes |
| **T15** | Cache behavior under load (R1/R2 check) | **SKIP on the user's device by default** (requires `am force-stop com.cashbuddy` = process restart; disk data is safe but ask consent). If approved: force-stop, relaunch, immediately post 20 messages while the first parse is in flight | No StrictMode violation / main-thread I/O warnings in logcat; cache stays ≤2048 tokens; lookups served from RAM | [L] no `StrictMode` policy violation + no `SQLite access on main thread` · note: only available if installed build IS debuggable | R1, R2 — **probe** of the R-fix quality |
| **T16** | Screenshot share path (ANR probe) | `adb shell am start -a android.intent.action.SEND -t image/png --eu android.intent.extra.STREAM <file Uri>` with a fake screenshot posted via `adb push` | Copy + sleep loop runs; measure UI stall — **inspect any >1 s gap**: `copyUriToCache` blocks the main thread up to ~4.65 s (ScreenshotHandler.kt sleep loop) | [L] `ScreenshotHandler`/`ScreenshotParser` timings + StrictMode gap · stopwatch | 2D — **probe**, known-open (fix not committed) |
| **T17** | Widget refresh on data change | After a few txs, check glance widget updates without app-foreground | widget shows recent balance/activity; logcat `CashBuddyWidgetReceiver` no failures | [U+W] | 2A.1 scope-dup probe |
| **T18** | CSV export round-trip | In-app → export CSV → `adb pull` the file | Header + amounts correct; on re-import through DataView UI **row count does not double** (c18b182 dedup fix) | [X] pulled file · [U] import twice | C7 |
| **T19** | Offline assertion (hard rule) | During ALL of the above: `adb shell dumpsys netpolicy com.cashbuddy` / inspect app traffic (should be impossible — INTERNET stripped) | zero network attempts; manifest-grep stays `INTERNET` = removed | [L] none; manifest check | Rule 2 |
| **T20** | Kill-mid-ingest crash safety (optional, advanced) | **Requires explicit user consent on the personal device** (no data deletion, but interrupts a live agent). Post message, and at a scripted delay window (`adb shell am kill com.cashbuddy` within ~200-800 ms of ingest), reopen app | Either complete raw+tx+budget state or none — no orphan half-writes | [D] DataView raw↔tx linkage, [U] rows | C1 — **probe**: db.transact fix (Phase 1) not committed yet; currently EXPECT INCONSISTENT → record exactly what breaks |

### 3.1 Verification surface commands

```powershell
# pipeline decisions
adb logcat -d | Select-String "TxNotificationListener"
# latent corrections / merge decisions — cross-reference with the in-app DebugLog screen
adb logcat -d | Select-String "CashBuddy"
# screenshots after each matrix step for the record
adb shell screencap -p /sdcard/e2e_T3.png ; adb pull /sdcard/e2e_T3.png .
# CSV pull after in-app export (DataView or Settings > Export)
adb shell 'ls -t /storage/emulated/0/Download/' ; adb pull <newest-csv> .
```

**Expected on green:** every T-verification that references `[L]` must show decision logs; every `[U]/[D]` step needs matching screenshot evidence in this log.

### 3.2 Known-open items to record as In-Progress (NOT test failures)

Status as of 2026-10-30 (post-sprint fixes now in the working tree, uncommitted):
- C1 (db.transact in MessagePipeline) → **FIXED (F1)** via `TransactionIngestStore.insertAndLink` — T20 row validates.
- C4 (budget alert first-wins) → **FIXED (F5 triage)**: per-budget `markAlerted` retained; global notification suppressed when a category alert already fired — T12 row validates.
- C9 (notification ID strategies) → verified addressed by parallel-session commit `c18b182` — T11 row validates.
- 2D (screenshot main-thread copy+sleep) → verified fixed in tree by parallel session — T16 row validates.

---

## 4. Pass/Fail Criteria (session end)

1. **Hard PASS:** T1-T11, T13-T15, T17-T19 all green; zero SQLcipher errors; zero network attempts (T19).
2. **Fail-fast triggers:** T5/T6/T7 leak non-transaction junk into Home list → classifier defect, stop, dump DebugLog.
3. **Soft-FAIL/record:** T12/T16/T20 fail as predicted → attach logcat excerpt to corresponding audit line so their fixes get an acceptance test when implemented.
4. **Regression gates by code:** dev branch flags, strict-mode, and `ArchitectureSanityTest.kt` green at session start.

## 5. Cleanup

```powershell
adb shell am force-stop com.cashbuddy      # optional; ask consent first
# 🚫 NO adb uninstall — this is the user's personal device with live data.
#    The app stays installed exactly as found; no files added, no installs/uninstalls.
adb logcat -c
```

*Reminder: never store real bank credentials in this harness; Caller-notifications only. `com.android.shell` posts style restrict realism but prove content-first classification, which is the actual rule.*
