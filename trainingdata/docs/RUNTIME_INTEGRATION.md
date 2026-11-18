# Gate — Runtime Integration

How the trained gate reaches the app without pauses, network access, or a large
download.

## 1. What ships

| Artifact | Ships in APK | Size |
|---|---|---|
| `gate_weights.json` (18 LRs + bias + threshold) | Yes | < 1 KB |
| `gate_ngrams.bin` (optional, pruned) | Yes | 60–120 KB |
| `amount_role.json` (phase 2) | Yes | < 1 KB |
| Dataset JSONL files | **Never** | 0 |
| Python training code | No | 0 |

The runtime stays pure Kotlin. No ONNX, TFLite, JNI, or background service is
introduced. The notification path remains fully offline.

## 2. Weights JSON

```json
{
  "model_version": "gate-1.0.0",
  "schema_version": 1,
  "trained_at_utc": "2026-11-18T00:00:00Z",
  "dataset_manifest_sha256": "…",
  "prior": 0.20,
  "intercept": -2.812,
  "bias_adjust": -1.426,
  "threshold": 0.62,
  "calibration": { "kind": "platt", "a": 1.041, "b": -0.083 },
  "features": {
    "amount_in_context": 6.21,
    "amount_debit_sent": 7.04,
    "amount_credit_sent": 7.11,
    "amount_forbidden_shape": 0.03,
    "account": 6.88,
    "utr": 2.41,
    "bank_sender": 1.92,
    "upi_handle": 2.08,
    "balance": 1.33,
    "merchant_pkg": 1.22,
    "success": 1.57,
    "otp": 0.02,
    "promo": 0.06,
    "offer": 0.09
  },
  "ngrams": {
    "enabled": true,
    "buckets": 262144,
    "hash": "fnv1a32",
    "scale": 4096,
    "file": "gate_ngrams.bin",
    "sha256": "…"
  }
}
```

Notes:

- Values above are illustrative; the trainer overwrites them.
- `features` holds **likelihood ratios**, not log-weights. `ln(lr)` is the logit
  contribution. Clamp to `[0.01, 100]` as `LikelihoodCalibrator` already does.
- `bias_adjust = intercept - ln(prior / (1 - prior))` compensates for any
  intercept difference left by regularization. With `prior = 0.20`, the prior
  logit is `-1.3863`; the example's `-1.426` is that offset plus a small fitted
  residual.
- `threshold` is chosen on validation and frozen before test evaluation.

## 3. Mapping into `ProbabilisticClassifier`

The existing pipeline sums prior logit plus per-signal `ln(lr)`. The trained model
must slot into that shape:

```
logit = ln(p/(1-p)) + Σ ln(lr_i) · x_i + bias_adjust + ngram_score + token_log_odds
```

- `lr_i` come from `gate_weights.json` instead of `LikelihoodRatios` constants.
- `bias_adjust` is a new additive constant (one line in the classifier).
- `ngram_score` is a new `NgramEvidence` component (Section 4).
- `token_log_odds` remains the existing per-user `TokenEvidence`; it is **not**
  part of offline training and keeps its online behavior.

Do not fold static n-gram weights into `tokenLogOdds`. They have different
lifetimes: n-grams are frozen at release, user tokens adapt on device.

## 4. Binary n-gram format

Only nonzero buckets are exported.

```
offset  size            field
0       4               magic  "CBG1" (0x43 0x42 0x47 0x31)
4       4               count  uint32 little-endian
8       6 × count       entries, sorted by bucket ascending:
                          bucket  uint32 little-endian
                          weight  int16  little-endian, fixed-point
```

- `weight_fixed = round(w * 4096)`, clamped to int16 range.
- `score = Σ weight_fixed / 4096.0` over matched buckets (each bucket counted once
  per message, or per occurrence if training used counts — keep it consistent).
- 20,000 entries → 120,008 bytes. 10,000 → 60,008 bytes.
- Little-endian matches Android and JVM on all supported ABIs.

### 4.1 Compression and memory mapping

`AssetManager` compresses unknown extensions by default, which disables
`MappedByteBuffer`. Add `bin` to `noCompress`:

```kotlin
androidResources {
    noCompress += "bin"
}
```

Then read via `AssetFileDescriptor` → `FileInputStream.channel.map(...)`. If the
asset cannot be mapped, fall back to a byte read plus `ByteBuffer.wrap`. Either
way, load once off the main thread.

## 5. Tokenization parity

The Python trainer and Kotlin runtime must produce identical tokens. One spec:

1. Lowercase (Unicode).
2. Every digit run → `<num>`.
3. `₹`, `Rs.`, `INR` → `<cur>`; keep `@` for VPA shape.
4. Other non-alphanumerics → space; collapse whitespace.
5. Emit word unigrams, word bigrams, char 3–5 grams with `^` and `$` boundaries.
6. Hash with 32-bit FNV-1a; bucket = `hash and (buckets - 1)`.

Parity is enforced by a golden fixture (Section 9), not by review. A single
divergence (for example Python's `\d` matching Devanagari digits while Kotlin's
does not) silently shifts the score.

## 6. Loading without pauses

| Step | Where | Budget |
|---|---|---|
| Read + verify JSON (< 1 KB) | App start, `Dispatchers.Default` | < 1 ms |
| Map/read `gate_ngrams.bin` (~120 KB) | App start, `Dispatchers.Default` | 1–3 ms |
| Tokenize + hash + score | Notification path | 50–200 µs |
| Amount-role selection | Notification path, only for positives | < 100 µs |

Rules:

- Load in `Application.onCreate` or a lazy singleton warmed on a background
  dispatcher. Never open assets inside the listener callback.
- Keep the mapped buffer and a `DoubleArray` of feature weights in memory.
- Avoid per-message allocations: reuse a token buffer, avoid building maps.
- Never parse JSON per notification, even though it is small.
- Scoring is a sparse dot product: ~40 hash lookups worst case.

With the 18-signal model only, latency is under 10 µs and asset load is
effectively free.

## 7. APK size impact

Baseline: KMP + Compose + SQLCipher release builds with R8 enabled typically land
at 12–18 MB.

| Configuration | Added size | Share of a 15 MB APK |
|---|---:|---:|
| 18 signals only (JSON/codegen) | < 5 KB | ~0% |
| + 10k n-grams (60 KB binary) | ~60 KB | 0.4% |
| + 20k n-grams (120 KB binary) | ~120 KB | 0.8% |
| Dense 2^18 int8 table (262 KB) | ~250 KB | 1.7% |

Hashed weights are high-entropy, so AAB compression will not shrink them much.
That is fine at this scale. Do not ship a dense table; pruning removes tens of
thousands of near-zero weights with no measurable accuracy loss.

The dataset itself must never enter `assets/`, `res/raw/`, or `commonMain/resources/`.

## 8. Failure behavior

| Failure | Behavior |
|---|---|
| Asset missing | Use handcrafted `LikelihoodRatios`; log once |
| JSON parse error | Same fallback |
| Unknown `schema_version` major | Same fallback |
| `gate_ngrams.bin` corrupt/short | Ignore n-grams, keep 18-signal model |
| Checksum mismatch | Fallback, report in debug log |

The notification listener must never crash or block because a model file is bad.
The handcrafted weights are a complete, working gate; they are the safety net.

## 9. Tests

1. **Tokenizer parity** — same token list for 200 tricky messages (`₹`, `Rs.`,
   Hinglish, OCR `1`/`0` substitutions, emoji, multiline receipts).
2. **Feature parity** — identical `Evidence` vector and logit (±1e-6) between
   Python and Kotlin on the golden fixture.
3. **Golden scores** — freeze expected probabilities for 200 fixture rows; fail on
   drift beyond tolerance.
4. **Loader robustness** — truncated bin, wrong magic, unsorted buckets, zero
   count, unknown major version.
5. **Performance** — benchmark on a low-end device; p99 classification under 1 ms.
6. **End-to-end** — notification → evidence → classifier → stored/previewed
   transaction; verify PENDING routing at confidence < 0.85 or amount ≥ ₹10,000.

Committed fixture: `trainingdata/fixtures/gate_smoke.jsonl`, about 200 rows
sampled from `test.jsonl` with labels. It exercises parsing without shipping 40k
rows or regenerating in CI.

## 10. Versioning and updates

- `model_version` follows `major.minor.patch`; `schema_version` changes only on
  format breaks.
- Weights update only through an app release. There is no model download.
- Keep the previous model artifact tag in git so a bad release can be bisected.
- Record `dataset_manifest_sha256` in the JSON for provenance.

## 11. Privacy

- Weights and hashes come from synthetic template text; no user data is involved.
- Hashes are not reversible and contain no PII.
- No dataset file ships; no network call is added.
- Runtime adaptation stays in the encrypted local DB via the existing token
  learner.

## 12. Integration checklist

- [ ] `train_gate.py` writes weights, eval report, optional bin
- [ ] Validation pass recorded in `model/eval_report.json`
- [ ] `noCompress += "bin"` configured
- [ ] Loader on a background dispatcher, mapped buffer cached
- [ ] `bias_adjust` wired into `ProbabilisticClassifier`
- [ ] `NgramEvidence` additive term added (if enabled)
- [ ] Fallback path exercised by deleting the asset in a debug build
- [ ] Parity and golden-score tests green
- [ ] Release APK size delta measured (< 0.2 MB target)
- [ ] Notification path latency measured (p99 < 1 ms)
