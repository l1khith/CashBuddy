# CashBuddy Gate — Training Pipeline

How the transaction-vs-noise gate is trained, evaluated, and exported.

## 1. Decision record

Full pros and cons for each choice: [TRADEOFFS.md](TRADEOFFS.md).

| Decision | Choice | Why |
|---|---|---|
| Training hardware | Developer laptop | Model is a weight table; GPU adds nothing |
| Colab | Not used | No speed benefit; breaks the offline habit needed once real corrections arrive |
| On-phone training | Not used for the base model | Shipping 100 MB of JSONL to fit a few dozen weights is wasteful; no eval harness on device |
| Runtime adaptation | Keep existing online token learner | `TokenEvidence` + `LikelihoodCalibrator` already learn per user with significance gates |
| Runtime dependency | None beyond Kotlin | No Python, ONNX, TFLite, JNI, or network |
| Deployment prior | 0.20 (matches `Priors.P_TRANSACTION`) | Dataset is 50/50; production is far more negative |

Training is a **build-time** step. The app ships only fitted weights.

```
trainingdata/splits/train.jsonl ─┐
trainingdata/splits/val.jsonl   ─┼─> train_gate.py ─> model/gate_weights.json
trainingdata/splits/test.jsonl  ─┘                  ─> model/gate_ngrams.bin (optional)
                                                    ─> model/eval_report.json
                                                              │
                                                              v
                                              Kotlin loader / generated constants
```

## 2. Inputs

| Split | Rows | Positives | Negatives | Use |
|---|---:|---:|---:|---|
| `train.jsonl` | 28,000 | 14,000 | 14,000 | Fit weights |
| `val.jsonl` | 6,000 | 3,000 | 3,000 | Prior correction, calibration, threshold |
| `test.jsonl` | 6,000 | 3,000 | 3,000 | One-shot evaluation, never tuned on |

Fields consumed by training:

- `text`, `title`, `source_type`, `package_name` — model input
- `label` / `is_transaction` — target
- `negative_type`, `difficulty`, `category`, `source_type` — slice reporting only
- `amount_text` — amount-role labels (phase 2)

Do not train on `merchant_canonical`, `amount`, or `utr` as inputs. Real messages
have messy merchant strings; using the clean synthetic labels would inflate
results and teach nothing.

## 3. Feature contract

The Python extractor must mirror the existing Kotlin signals exactly. Any drift is
train/serve skew and silently degrades production.

| Feature | Kotlin source | Python derivation |
|---|---|---|
| `hasAmount` | `Evidence` | Number with currency marker or amount context |
| `hasAccount` | `Evidence` | `A/c`, `account`, `card` + 3–4 digit tail |
| `hasUtr` | `Evidence` | 10–16 digit ref after `Ref`/`UTR`/`RRN`/`Txn ID` |
| `hasOtp` | `Evidence` | OTP/verification code + 4–8 digits |
| `hasPromo` | `Evidence` | `cashback`, `offer`, `coupon`, `promo`, `win`, `scratch` |
| `hasOffer` | `Evidence` | `off`, `discount`, `save`, `deal`, `T&C` |
| `hasUpiHandle` | `Evidence` | `@okaxis`, `@ybl`, `@paytm`, generic `@handle` |
| `hasBalanceMention` | `Evidence` | `Avl Bal`, `available balance`, `balance is` |
| `hasSuccessWord` | `Evidence` | `success`, `successful`, `completed`, `credited`, `debited` |
| `senderLooksBank` | `Evidence` | Sender ID/package in the bank vocabulary |
| `fromMerchantPackage` | `Evidence` | UPI/wallet/card package vocabulary |
| `amountInTransactionContext` | `Evidence` | Amount within the same sentence as a payment verb |
| `amountAndDebitSameSentence` | `Evidence` | Amount + debit verb in one sentence |
| `amountAndCreditSameSentence` | `Evidence` | Amount + credit verb in one sentence |
| `amountHasForbiddenShape` | `Evidence` | Amount looks like OTP/account/date/phone/limit |
| `recentSimilarAmount` | `Evidence` | Set `false` in batch training (no history) |
| `recentSameMerchant` | `Evidence` | Set `false` in batch training |
| `burstDetected` | `Evidence` | Set `false` in batch training |
| `tokenLogOdds` | `TokenEvidence` | Not trained here; stays online-only |

The three time-based features depend on `RecentStateRepository`. Batch training has
no equivalent state, so they are zeroed. This is intentional: the shipped base
weights must not learn a signal that is absent during offline fitting. They remain
active at runtime with their existing LRs.

`source_type` enters only as a weak prior feature, never as a hard allowlist.
The dataset deliberately reuses banks and senders across both classes.

## 4. Training procedure

### 4.1 Normalization and n-grams

One normalization function, implemented twice (Python and Kotlin) and parity-tested:

1. Unicode lowercase.
2. Replace every digit run with `<num>` (prevents memorizing amounts, UTRs, OTPs).
3. Replace `₹`, `Rs.`, `INR` with `<cur>`; keep `@` for VPA shape.
4. Replace any remaining non-alphanumeric character with a space, collapse spaces.
5. Tokens: word unigrams, word bigrams, char 3–5 grams with `^`/`$` boundary markers.
6. Hash each token with 32-bit FNV-1a, bucket = `hash and (2^18 - 1)`.

Hashing avoids a vocabulary file and keeps unknown tokens (new merchants, OCR
noise, Hinglish variants) usable at runtime.

### 4.2 Model

- L2-regularized logistic regression over the binary features + hashed buckets.
- Fit on `train.jsonl` unweighted (the file is 50/50).
- Sweep `C` on `val` only; freeze before touching `test`.
- Optional L1 or magnitude pruning: keep only nonzero buckets after fitting.
  Target 10k–20k nonzero weights.

A shallow model is deliberate: it is explainable, fits in kilobytes, and runs in
microseconds. Do not jump to a transformer; the pure-Kotlin constraint makes it a
deployment liability, and 40k synthetic rows do not justify it.

### 4.3 Prior correction

Dataset prior is 0.5, deployment prior is 0.2:

```
offset = ln(0.20 / 0.80) - ln(0.50 / 0.50)
       = ln(0.25)
       = -1.3863
```

Apply the offset to the intercept before calibration. Do not also reweight samples;
pick one mechanism, or the gate will be double-corrected and under-call
transactions.

At runtime, `ProbabilisticClassifier` starts from `Priors.P_TRANSACTION = 0.20`,
so the exported weights must be consistent with that prior.

### 4.4 Calibration

- Fit a Platt sigmoid (or isotonic if it visibly helps) on `val` after the prior
  offset.
- Keep the existing per-signal `LikelihoodCalibrator` for online adaptation; the
  exported base LRs are its starting point.
- Report ECE and reliability bins in `eval_report.json`.

### 4.5 Threshold

Choose the operating threshold on `val` at the target false-positive rate
(default: 1%). Freeze it, then evaluate once on `test`. Store it in
`gate_weights.json` as `threshold`.

Review-inbox routing keeps the existing rules: confidence < 0.85 or amount
≥ ₹10,000 goes to PENDING.

## 5. Evaluation

Report in `model/eval_report.json`:

| Metric | Purpose |
|---|---|
| PR-AUC, ROC-AUC | Ranking quality |
| Recall @ FPR 1% and 5% | Operating point |
| Precision at the frozen threshold | Review-inbox load |
| ECE, Brier | Calibration |
| Per-slice recall/precision | Find weak formats |

Slices:

- `source_type`: bank SMS, bank app, UPI, card, ATM, screenshot, forwarded, chat, social, email
- `negative_type`: all 16, especially OTP, PROMO, FAILED_TXN, CHAT_SUMMARY
- `difficulty`: easy, medium, hard, ocr
- `txn_type`: DEBIT, CREDIT
- amount buckets: `<100`, `100–999`, `1k–9.9k`, `10k–99.9k`, `100k+`

Provisional acceptance gates on the synthetic test split:

- Recall ≥ 0.98 at FPR ≤ 1%
- No slice below 0.90 recall at the operating threshold
- ECE ≤ 0.03
- Positive amount span selectable in ≥ 0.98 of positives

These are smoke gates. Synthetic data is easier than production. The release
decision must use a real holdout; do not lower the bar to make synthetic numbers
look good.

### Leakage rules

- `test.jsonl` is read once, at the end.
- No template or merchant crosses train/test by construction (validated).
- Never merge splits before final evaluation.
- Duplicate companions share `event_group_id`; keep them inside one split
  (already guaranteed by the generator).

## 6. Exports

| Artifact | Content | Committed |
|---|---|---|
| `model/gate_weights.json` | 18 base LRs, intercept, prior, threshold, token n-gram config, version | Yes |
| `model/gate_ngrams.bin` | Pruned hashed weights, sorted (optional) | Yes |
| `model/eval_report.json` | Metrics, slices, confusion matrix at threshold | Yes |
| `model/amount_role.json` | Amount-role rules/weights (phase 2) | Yes |
| `combined.jsonl`, class files, `splits/` | Training input | No (gitignored) |

The JSON is the audit artifact; the binary is the runtime artifact. Both are
generated from the same fit and must carry the same `model_version`.

## 7. Amount-role selector (phase 2)

The gate answers "is this a transaction?". A second, tiny model answers "which
number is the amount?".

Candidate spans: every numeric span in the text, excluding:

- account/card tails (`XX1234`, `A/c ...1234`)
- UTR/RRN/reference IDs
- OTP codes
- dates and times
- phone numbers

Features per candidate: currency marker adjacency, distance to a payment verb,
`Avl Bal`/`limit`/`min due` context, `Ref`/`UTR` adjacency, comma grouping,
decimal presence, digit length, position in sentence.

Train on positive rows only: the span matching `amount_text` is positive, every
other candidate is negative. A decision list is enough to start; promote to
logistic regression only if error analysis demands it.

Reject or send to PENDING when the chosen span has confidence < 0.85, matching the
human-in-the-loop rule.

## 8. Reproduce

```powershell
# 1. Rebuild the dataset (deterministic, ~7 s)
python trainingdata/generate_dataset.py --positives 20000 --negatives 20000 --seed 42

# 2. Validate before training
python trainingdata/validate_dataset.py --data trainingdata

# 3. Fit, calibrate, evaluate, export (script planned)
python trainingdata/train_gate.py --data trainingdata --out trainingdata/model
```

Environment: Python 3.11, `numpy`, `scikit-learn`. No GPU. No network access
required after install.

## 9. Non-goals

- Training a production category model. 15 synthetic categories and 19 real
  corrections are not enough; category quality comes from real corrections.
- Merchant canonicalization. Synthetic merchants are clean; real strings are not.
- Replacing the online token learner. It adapts per user; this pipeline only sets
  the base weights.
- Shipping any dataset file in the APK.

## 10. Risks

| Risk | Mitigation |
|---|---|
| Train/serve feature drift | Shared fixture + parity tests (see integration doc) |
| Synthetic overconfidence | Real holdout before release; report slices |
| Prior mismatch | Single documented offset, verified on reweighted val |
| Tokenizer drift | One spec, two implementations, golden parity set |
| Silent asset corruption | Version + checksum in the weights JSON |
