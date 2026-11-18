# Gate — Trade-offs

Pros and cons behind the choices in
[TRAINING_PIPELINE.md](TRAINING_PIPELINE.md) and
[RUNTIME_INTEGRATION.md](RUNTIME_INTEGRATION.md).

Each section ends with the decision actually taken, so this doubles as a decision
log. Revisit a decision only when its stated cons become the bottleneck.

---

## 1. Where training runs

| Option | Pros | Cons |
|---|---|---|
| **Laptop (chosen)** | Private by default; no upload; fits in seconds; reproducible script; no quota; same machine as the repo | Manual Python env; no GPU for experiments; results exist only where committed |
| Colab | Zero setup; free GPU; easy sharing; good for transformer experiments | Data leaves the machine (fine for synthetic, not for real corrections); session limits; notebook drift; overkill for a linear model; builds a bad habit |
| On-phone | Ultimate privacy; can personalize from real corrections; no release needed for updates | Dataset must reach the device (100 MB or slow collection); battery/thermal cost; no eval harness; hard to debug; training code widens the audit surface; non-reproducible user reports; risk of corrupting the shipped model |

**Decision:** train on the laptop. Keep the existing on-device token learner for
per-user adaptation; do not batch-train the base model on the phone.

---

## 2. Model family

| Option | Pros | Cons |
|---|---|---|
| **Linear LR + hashed n-grams (chosen)** | Kilobytes; microseconds; calibrated; per-signal contributions already surfaced; pure Kotlin; easy parity tests; robust with 40k rows | No token order or long context; limited feature interactions; manual feature engineering; hash collisions; can underfit unusual phrasing |
| Gradient-boosted trees | Learns interactions; strong on tabular features | More code to run in Kotlin; larger artifact; harder calibration; overkill for 18 signals |
| Small transformer | Less feature engineering; strong text accuracy | 20–60 MB; 10–50 ms inference; needs ONNX/TFLite/JNI, breaking the pure-Kotlin rule; battery and cold-start cost; hard to explain; 40k synthetic rows do not justify it |
| Rules only | Transparent; no training; trivial to patch | Brittle and combinatorial; no evidence weighting; no calibrated probability; maintenance grows with every new format |

**Decision:** linear model first. Rules stay for hard guards and the amount-role
selector. A transformer is reconsidered only if real data shows the linear model
is the bottleneck *and* the no-JNI constraint changes.

---

## 3. 18 signals vs adding n-grams

| Option | Pros | Cons |
|---|---|---|
| 18 signals only | < 1 KB; < 10 µs; every contribution explainable; no tokenizer parity risk; simplest fallback | Limited vocabulary coverage; weaker on unseen merchant and promo wording; depends on handcrafted cues |
| **+ pruned hashed n-grams (conditional)** | Learns vocabulary and verbs; generalizes to unseen templates; still 60–120 KB and sub-millisecond | Hash collisions; two tokenizers to keep in parity; can overfit synthetic phrasing; less explainable; one more file that can fail to load |

**Decision:** ship the 18-signal model first and measure. Add n-grams only if the
test split (and later a real holdout) shows a gain worth the parity burden.

---

## 4. Artifact format

| Option | Pros | Cons |
|---|---|---|
| **JSON for the 18 signals (chosen)** | Diffable; auditable; easy versioning; human review | Parse cost (negligible here); larger than binary for many weights; needs schema validation |
| Binary for n-grams (chosen) | Compact; memory-mappable; zero-copy; fast | Opaque; endianness and alignment pitfalls; harder code review; needs checksum and versioning; `noCompress` required |
| Kotlin codegen | No I/O, no parse; compile-time constants; R8-friendly | Rebuild to change weights; huge source files at 20k weights; merge conflicts; slower builds |

**Decision:** JSON for the small weight set, binary for the pruned n-gram table.
Codegen only if the JSON loader proves annoying for 18 values.

---

## 5. Dense vs pruned n-grams

| Option | Pros | Cons |
|---|---|---|
| Dense 2^18 table | Simple indexing; no sorted search | 250 KB–1 MB; most weights are near zero; larger download for no accuracy gain |
| **Pruned sparse (chosen)** | 60–120 KB; better cache behavior; same accuracy | Needs sorted keys or a map; slightly more loader code |

**Decision:** prune to nonzero buckets, sort by bucket, int16 fixed-point.

---

## 6. Loading strategy

| Option | Pros | Cons |
|---|---|---|
| **Eager, background (chosen)** | No first-notification latency; predictable memory; simple failure handling | 1–3 ms of startup work; small permanent memory hold |
| Lazy on first notification | Zero startup cost | First notification pays I/O; jank risk on the hot path; concurrency complexity |
| Memory-mapped | Zero-copy; low memory; fast random access | Requires an uncompressed asset; platform quirks; buffer lifetime care; harder tests |

**Decision:** load eagerly on `Dispatchers.Default`; memory-map when the asset is
uncompressed, otherwise read into a buffer.

---

## 7. Calibration

| Option | Pros | Cons |
|---|---|---|
| None | Simplest; no extra parameters | Probabilities are not real probabilities; threshold is arbitrary; ECE is poor |
| **Platt (chosen)** | Two parameters; stable on 6k validation rows; monotonic | Assumes a sigmoid shape; can mis-calibrate at the extremes |
| Isotonic | Flexible; better ECE with enough data | Overfits small validation sets; step function; more storage |
| Online blending (existing) | Adapts per user; already implemented | Noisy early; needs the 50-observation gate; can drift if feedback is biased |

**Decision:** Platt on validation after the prior offset; keep the existing online
blending on top with its significance and clamp rules.

---

## 8. Data source

| Option | Pros | Cons |
|---|---|---|
| **Synthetic (chosen for cold start)** | 40k rows in seconds; controlled coverage; no PII; deterministic; deliberate hard negatives | Template bias; clean labels; not the real distribution; optimistic metrics; misses real merchant strings and carrier quirks |
| Real only | True distribution; real failure modes | Only a handful of corrections today; export friction; imbalance; labeling cost |
| Hybrid | Pretrain on synthetic, fine-tune on real | Domain shift; weighting decisions; more complex evaluation |

**Decision:** synthetic bootstraps the base weights; real exported corrections own
the final threshold and release decision.

---

## 9. Split strategy

| Option | Pros | Cons |
|---|---|---|
| **By template (chosen)** | No template leakage; honest generalization; measures format transfer | Test formats are unseen, so scores look lower; small per-template counts in val/test; families still share style across splits |
| Random rows | More data per split; simplest | Duplicate templates leak; metrics inflate; false confidence in production |

**Decision:** split by template and merchant, accept conservative numbers. The
validator proves no template or merchant crosses train and test.

---

## 10. Prior handling

| Option | Pros | Cons |
|---|---|---|
| **50/50 fit + intercept offset (chosen)** | Stable fitting; one explicit deployment prior; easy to retarget | Offset must be applied exactly once; validation must be reweighted; easy to double-correct |
| Reweighted training | Directly targets the deployment distribution | Lower effective sample size; unstable intercept; harder run comparison |

**Decision:** fit unweighted, apply `ln(0.20/0.80) = -1.3863` once, verify on
reweighted validation.

---

## 11. Batch vs online learning

| Option | Pros | Cons |
|---|---|---|
| **Batch base weights (chosen)** | Globally evaluated; stable; reproducible; explainable | Stale until the next release; no personalization |
| Online only | Adapts immediately per user; no release | Noisy; drift risk; no global evaluation; cold start is weak |
| **Both, separated (chosen)** | Stable base plus personal adaptation | Two systems to reason about; feedback quality matters |

**Decision:** frozen base weights plus the existing gated online token learner.
Never let online feedback rewrite the base model.

---

## 12. Cost summary

| Choice | Benefit | Price paid |
|---|---|---|
| Laptop training | Privacy, speed, reproducibility | Manual env; no GPU experiments |
| Linear model | Size, latency, explainability | Feature engineering; weaker on complex text |
| Pruned n-grams | Vocabulary generalization | Tokenizer parity maintenance |
| JSON + binary split | Auditability where it matters | Two formats and two loaders |
| Template split | Honest metrics | Lower headline numbers |
| Synthetic cold start | Fast, safe bootstrap | Optimistic metrics; real holdout still required |

The recurring theme: every accuracy upgrade costs either size, latency, or
maintenance. Spend that budget only when evaluation proves it pays.
