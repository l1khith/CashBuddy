# Training Docs

Documentation for training and shipping the CashBuddy transaction gate.

| Document | Covers |
|---|---|
| [TRAINING_PIPELINE.md](TRAINING_PIPELINE.md) | Where training runs, feature contract, model, calibration, evaluation, exports, commit policy |
| [RUNTIME_INTEGRATION.md](RUNTIME_INTEGRATION.md) | Artifact formats, loading without pauses, latency and APK size budgets, versioning, tests |
| [TRADEOFFS.md](TRADEOFFS.md) | Pros, cons, and decision log for every major choice |

Related:

- Dataset card: [`../README.md`](../README.md)
- Existing gate code: `shared/src/commonMain/kotlin/com/cashbuddy/core/prob/`
  (`ProbabilisticClassifier.kt`, `LikelihoodRatios.kt`, `Evidence.kt`)
- Dataset generator and validator: [`../generate_dataset.py`](../generate_dataset.py),
  [`../validate_dataset.py`](../validate_dataset.py)

## Status

| Item | State |
|---|---|
| Synthetic dataset (20k / 20k) | Done, 37/37 validation checks pass |
| `train_gate.py` | Planned |
| `model/gate_weights.json` + `model/eval_report.json` | Planned |
| Kotlin artifact loader | Planned |
| Amount-role selector | Planned (phase 2) |

The synthetic dataset bootstraps the gate. Real exported corrections remain the
final authority for thresholds and release decisions.
