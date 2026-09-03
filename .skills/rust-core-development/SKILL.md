---
name: rust-core-development
description: >-
  Use this skill when authoring, modifying, compiling, or testing the high-performance Rust core library (core-rust/)
  for PaisaPal KMP, including UniFFI FFI bindings, strict zero-unwrap/no-panic conventions, size-optimized release profiles,
  and on-device ONNX Runtime inference.
---

# Rust Core Development & UniFFI Integration

## 1. Core Principles & Philosophy

PaisaPal uses Rust selectively for high-throughput, low-latency, and security-critical modules:
1. **Notification Parser Engine**: Zero GC latency, sub-millisecond regex execution.
2. **On-Device ML Classifier**: DistilBERT ONNX runtime execution with INT8 quantization via `ort`.
3. **Cryptographic Core**: Zero-cost AES-256-GCM encryption, Argon2id KDF, and zero JVM heap key leakage via `zeroize`.
4. **Fraud Guard**: Real-time velocity and duplicate hash detection with bounded memory.

---

## 2. Strict Coding Standards

### 2.1 Zero-Panic Policy
- **NO `unwrap()` or `expect()`** in production code.
- Exceptions: Unit tests (`#[cfg(test)]`) and Mutex lock recovery where poisoning indicates abnormal state.
- Always use `Result<T, CoreError>` with the `?` operator.
- Use `std::sync::OnceLock` for global static regex compilation.

### 2.2 Memory & Allocation Discipline
- Avoid unnecessary `.clone()`. Prefer borrowing with `&str`, `&[u8]`, and slices.
- Sensitive buffers (keys, plaintexts) must implement `zeroize::Zeroize` and wipe memory on `Drop`.
- Minimize heap allocations inside tight parser loops.

### 2.3 Tokio & Async Rules (When Applicable)
- Assume a Tokio runtime already exists if async code is invoked.
- Never create nested runtimes or call `Runtime::block_on()` inside async tasks.
- Never hold locks across `.await` points.
- Use `spawn_blocking` for CPU-intensive work (e.g. model inference or Argon2 KDF).

### 2.4 Editing Hygiene
- Change only what is required.
- Prefer purely additive changes and the smallest valid diff.
- Keep naming and style consistent with existing Rust code.
- Avoid comments unless explaining a non-obvious constraint or algorithmic detail.

---

## 3. UniFFI Interface Definition (`paisapal.udl`)

- All types exported to Kotlin must be declared in `core-rust/src/paisapal.udl`.
- Enums map directly to Kotlin sealed classes or enums.
- Dictionaries map to Kotlin immutable `data class` types.
- Fallible functions must be annotated with `[Throws=CoreError]`.
- Build bindings with `./gradlew :composeApp:build` or `cargo build`.

---

## 4. Size-Optimization Verification

Verify release flags in `core-rust/Cargo.toml`:
```toml
[profile.release]
opt-level = "z"
lto = true
codegen-units = 1
strip = true
panic = "abort"
```
Ensure compiled shared libraries stay under 2 MB per ABI architecture.
