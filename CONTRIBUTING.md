# PaisaPal (CashBuddy) — Developer & Architecture Quick Start Guide

Welcome! This document helps new contributors, external reviewers, and maintainers understand, develop, and extend the PaisaPal Kotlin Multiplatform (KMP) codebase with confidence.

---

## 🏛️ Core Principles & Architecture in 60 Seconds

1. **100% Offline, Zero-Network**: PaisaPal never requests `android.permission.INTERNET`. Everything runs locally.
2. **Zero SMS Permission**: We exclusively intake notifications via `BIND_NOTIFICATION_LISTENER_SERVICE`.
3. **Hardware-Backed Encryption**: The SQLite database is encrypted with SQLCipher (AES-256) keyed by the Android Keystore.
4. **Clean Architecture & Strict UDF**:
   * **`core/`**: Pure algorithmic models, probabilistic categorization, sentence tokenizers, and deduplication.
   * **`data/`**: SQLDelight database drivers, encrypted key stores, repositories.
   * **`domain/`**: Pure Kotlin models and repository interfaces.
   * **`presentation/`**: Compose Multiplatform UI adhering to Unidirectional Data Flow (StateFlow / MVI).

---

## 🛠️ Frequently Asked Questions (Developer Guide)

### 1. How do I add a new screen?

All screens follow the **Stateful Screen + Stateless Content** pattern:

1. **Define the Route**:
   * Add your destination route in [`ScreenRoute.kt`](file:///shared/src/commonMain/kotlin/com/cashbuddy/presentation/navigation/ScreenRoute.kt).
2. **Create the ViewModel & State**:
   * Create `XViewModel.kt` exposing a single `StateFlow<XUiState>`.
   * Register the ViewModel in Koin: [`PlatformModule.kt`](file:///shared/src/commonMain/kotlin/com/cashbuddy/di/SharedModule.kt).
3. **Build the Composable Screen**:
   * Create `XScreen.kt`:
     ```kotlin
     @Composable
     fun XScreen(
         viewModel: XViewModel = koinViewModel(),
         onNavigateBack: () -> Unit,
         modifier: Modifier = Modifier
     ) {
         val state by viewModel.uiState.collectAsStateWithLifecycle()
         XContent(
             state = state,
             onAction = viewModel::handleAction,
             onNavigateBack = onNavigateBack,
             modifier = modifier
         )
     }

     @Composable
     fun XContent(
         state: XUiState,
         onAction: (XAction) -> Unit,
         onNavigateBack: () -> Unit,
         modifier: Modifier = Modifier
     ) {
         // Pure stateless UI layout — no ViewModel references!
         // Readily previewable and easy to unit test.
     }
     ```
4. **Register in Navigation**:
   * Hook `XScreen` into the NavHost in [`CashBuddyApp.kt`](file:///shared/src/commonMain/kotlin/com/cashbuddy/presentation/CashBuddyApp.kt).

---

### 2. How do I add or modify a database table / column?

PaisaPal uses **SQLDelight 2.x** with dual-layer safety to guarantee zero crashes for users upgrading in production:

1. **Update SQLDelight Definitions**:
   * Add or edit table queries in `shared/src/commonMain/sqldelight/com/cashbuddy/db/<Feature>.sq`.
   * If modifying existing tables, add a new migration file: `shared/src/commonMain/sqldelight/com/cashbuddy/db/<next_version>.sqm`.
2. **Synchronize Fallback Schema**:
   * Open [`DatabaseDriverFactory.kt`](file:///shared/src/commonMain/kotlin/com/cashbuddy/data/local/DatabaseDriverFactory.kt).
   * Ensure your new table or `ALTER TABLE ADD COLUMN` is added to:
     * `ensureSchema()` $\rightarrow$ for fresh installs.
     * `runMigrations()` $\rightarrow$ idempotent safe execution for existing users updating the app.
3. **Rebuild**:
   * Run `./gradlew :shared:generateCommonMainAppDatabaseInterface` to generate type-safe Kotlin query interfaces.

---

### 3. How do I write and run unit tests?

Unit tests run natively on the host JVM using Kotlin Test and Coroutines Test:

* **Location**: `shared/src/commonTest/kotlin/com/cashbuddy/`
* **Test Suite Conventions**:
  * Test pure business logic in isolation.
  * Use Fake Repositories or in-memory SQLite drivers.
* **Running Tests from Terminal**:
  ```powershell
  # Run all core algorithmic & probabilistic engine tests:
  ./gradlew :shared:testAndroidHostTest --tests "com.cashbuddy.core.ProbabilisticEngineTest"

  # Run repository & parsing tests:
  ./gradlew :shared:testAndroidHostTest --tests "com.cashbuddy.core.prob.MerchantFuzzyTest"
  ```

---

### 4. Flavor Differences (`play` vs `fdroid`)

PaisaPal ships two distinct product flavors configured in [`androidApp/build.gradle.kts`](file:///androidApp/build.gradle.kts):

| Dimension | `play` Flavor | `fdroid` Flavor |
| :--- | :--- | :--- |
| **OCR Text Recognition** | Google ML Kit (`play-services-mlkit-text-recognition`) | Tesseract4Android (`cz.adaptech.tesseract4android:4.9.0`) |
| **Bundled Assets** | Lightweight (downloads on-demand or preloaded) | Pre-bundled `eng.traineddata` & `hin.traineddata` (100% offline) |
| **Google Dependencies** | Minimal Play Services | ZERO Google / closed-source dependencies |
| **Build Command** | `./gradlew :androidApp:assemblePlayRelease` | `./gradlew :androidApp:assembleFdroidRelease` |

---

### 5. Categorization & Merchant Resolution Precedence

When a transaction notification arrives, categorization follows a strict hierarchy:

1. **Tier 1 (Confidence 1.0)**: **User Rules** in SQLite `merchant_rules` (manual corrections always win).
2. **Tier 2 (Confidence 0.90 - 0.95)**: **Exact & Token Match** via baseline brand dictionary.
3. **Tier 3 (Confidence 0.85 * Similarity)**: **Jaro-Winkler Fuzzy Match** for handle variations (`SWIGGY_BLR_KORAMANGALA`, `paytm*swiggy`, etc.).
4. **Tier 4 (Confidence 0.70)**: **Generic Keyword Match** (e.g. "canteen", "pharmacy").
5. **Tier 5**: **On-Device Naive Bayes** token likelihood classifier.
