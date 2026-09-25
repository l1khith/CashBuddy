# CashBuddy — Development & Architectural Rules

## 1. Non-Negotiable Core Constraints

These 8 constraints are strictly enforced across the entire codebase:

1. **NO SMS PERMISSIONS**:
   - Never declare or request `android.permission.READ_SMS` or `android.permission.RECEIVE_SMS`.
   - All passive expense data extraction must originate solely from `android.permission.BIND_NOTIFICATION_LISTENER_SERVICE`.

2. **NO NETWORK PERMISSIONS (100% OFFLINE)**:
   - Never declare or request `android.permission.INTERNET`.
   - No external HTTP clients, telemetry SDKs, or cloud synchronization endpoints.
   - All processing occurs 100% on-device.

3. **PURE KOTLIN MULTIPLATFORM CORE**:
   - **100% Pure Kotlin Multiplatform (`shared/commonMain`)**: All business logic—including the 5-Layer Notification Parser, 3-Tier Category Engine, Screenshot Parser Engine, Deduplication Engine, Fraud Velocity Detector, Use Cases, ViewModels, and Database Repositories—is written in pure Kotlin.
   - **Zero JNI / Zero Native C/Rust Dependencies**: Eliminates `UnsatisfiedLinkError`, JNI lock contention, NDK compilation complexity, and multi-ABI binary bloat.
   - **Thread Safety**: Use lock-free copy-on-write collections with `@Volatile` or coroutine primitives (`Mutex`, `StateFlow`) for thread-safe state.

4. **HARDWARE-BACKED DATABASE ENCRYPTION**:
   - The SQLite database (`cashbuddy.db`) must always be encrypted with SQLCipher (AES-256 GCM).
   - Keys are generated and protected by the hardware-backed **Android Keystore (TEE / StrongBox)** via `AndroidKeystoreManager`.
   - In-memory passphrases must be immediately zeroized via `java.util.Arrays.fill(passphrase, 0.toByte())` after driver creation.

5. **HUMAN-IN-THE-LOOP VERIFICATION**:
   - Any transaction with confidence $< 0.85$ OR amount $\ge ₹10,000$ (or user-configured threshold) must be stored with status `PENDING`.
   - Only transactions meeting both criteria ($\text{confidence} \ge 0.85$ AND $\text{amount} < ₹10,000$) may be automatically marked `CONFIRMED`.

6. **BIOMETRIC APP PROTECTION**:
   - Require `BiometricPrompt` with `BIOMETRIC_STRONG` on application cold launch.
   - Automatically lock the application after 5 minutes of background inactivity (`TIMEOUT_LOCK_MS = 300_000L`).

7. **PROBABILISTIC GATING & ZERO HARDCODED ALLOWLISTS**:
   - Classify all messages using content-first Naive Bayes confidence modeling (`P(transaction | evidence)`).
   - Never gate acceptance or rejection on hardcoded bank sender IDs or package allowlists.

8. **ZERO PRIVATE DATA LEAKAGE**:
   - Never log raw notification text, full account numbers, or OTP contents in production logs.
   - Use `FLAG_SECURE` on sensitive windows to prevent unauthorized screen captures and task manager thumbnails.

---

## 2. Kotlin Multiplatform Coding Guidelines

- **Clean Architecture & UDF**: Maintain clear boundaries between Presentation (`presentation/`), Domain (`domain/`), Data (`data/`), and Core Engines (`core/`). ViewModels expose immutable `StateFlow<UiState>` and `SharedFlow<UiEffect>`.
- **Pure Multiplatform in `commonMain`**: Do not import Android-specific APIs (`android.*`, `java.*`) in `commonMain`. Use `expect/actual` or platform interfaces where necessary.
- **Fail-Safe Processing**: All notification and screenshot parsing must gracefully handle malformed text and edge cases without throwing unhandled exceptions.
