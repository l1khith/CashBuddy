# CashBuddy Constitution

## Principles

### P1 — On-Device Only
No network calls in the production artifact. No `INTERNET` permission.
Every file that touches persistence, parsing, or storage carries a
`// NO-NETWORK` header.

### P2 — Structural Over Lexical
Prefer regex and shape-based detection over keyword allowlists.
No hardcoded bank sender IDs. No hardcoded English phrase lists.
Merchant names may appear in `MerchantMap`; nothing else may.

### P3 — Probabilistic, Not Deterministic
Never accept or reject a message on a single signal. Combine evidence
into a calibrated probability. All thresholds are likelihood ratios,
not boolean gates.

### P4 — Single Source of Truth
Derived values are computed, never stored. Balances, totals, and
percentages come from queries over the base tables, not from cache
columns.

### P5 — Reversible Operations
Every destructive action has an undo path. Soft-delete over hard-delete.
Merge over overwrite. Audit trail preserved in dedicated tables.

### P6 — No Speculative Features
Do not implement a feature for a user who does not exist. Do not add
USD support for an INR-only app. Do not build a graph for a single-user
tracker. Ship what is measured.

### P7 — Measurable Improvements Only
Every behavioral change must be verifiable against the labelled dataset.
No change is "obviously better." Numbers or it did not happen.

## Code Style

- Kotlin, `commonMain` first. Platform code only in `androidMain` / `iosMain`.
- `suspend` for all I/O. No `runBlocking` in UI paths.
- ViewModels never touch the database directly. Repositories do.
- All public types in `core/` or `domain/` are `data class` or `sealed interface`.
- Comments explain *why*, not *what*. No commented-out code.

## Testing Rules

- Every new `Signal` has a test that asserts its likelihood ratio fires correctly.
- Every new repository method has a test against an in-memory SQLite instance.
- Every new UI screen has one Compose UI test for the happy path.
- The `AccuracyHarness` runs on every PR that touches classification.

## Architecture

- Layers: UI → ViewModel → Repository → Database. No skipping layers.
- Cross-cutting: `MessagePipeline` orchestrates ingestion. Nothing else does.
- Data: SQLDelight + SQLCipher. No ORM. No reflection.
- DI: manual via `AppContainer`. No Hilt, no Koin, no Service Locator.
