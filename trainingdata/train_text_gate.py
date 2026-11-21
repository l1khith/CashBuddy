"""Regex-free gate trainer: hashed text n-grams only.

Why this exists
---------------
The signal gate (`train_gate.py`) answers "is this money movement?" by running a set of
hand-written regexes (DEBIT, CREDIT, FAILURE, PENDING, PROMO, ...) and fitting a weight per
regex. Every wording the regex author did not anticipate becomes a bug: "bheja" is a send,
"Dr" is a debit, "on hold" is not a payment, "%. off" is not a spend. The regexes are also
the only place where real-world knowledge enters the model.

This trainer replaces that whole layer with one learned quantity per hashed token. The
message is tokenised by the same function the phone runs (`GateTokenizer`): lowercase,
digit runs -> `num`, currency words -> `cur`, non `[a-z0-9@]` -> space, then word unigrams,
word bigrams and character 3/4/5-grams, each hashed with FNV-1a 32 into a fixed bucket
space. Training only assigns a float weight per bucket, and the phone sums the weights of
the buckets present in the message. There is no keyword list anywhere.

Two estimators are available:

  nb  (default) smoothed log-odds P(token|txn) / P(token|noise), i.e. a learned lexicon.
      This is the regex-free analogue of `LikelihoodRatios`: interpretable, and rare but
      decisive words ("declined", "failed") keep the weight the data gives them.
  lr  L2-regularised logistic regression over the same buckets. Better calibrated inside
      the training distribution, but it shrinks rare tokens and loses to `nb` on templates
      it has never seen.

Artifact contract
-----------------
The shipped JSON keeps the 21 runtime signal keys in `GateSignal` order with a neutral
likelihood ratio of 1.0. A ratio of 1.0 contributes `ln(1) = 0` to the logit, so a runtime
that still runs the regexes scores exactly the text model. That means the regex-free model
can be evaluated on the current APK with no Kotlin change; deleting the regexes later only
removes dead work.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import math
import struct
import sys
from collections import Counter, defaultdict
from datetime import datetime, timezone
from pathlib import Path

import numpy as np
from scipy import sparse
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import average_precision_score, brier_score_loss, roc_auc_score

HERE = Path(__file__).resolve().parent
REPO = HERE.parent
sys.path.insert(0, str(HERE))

import train_gate as tg  # noqa: E402  (shared loader/metrics/tokenizer parity)

NGRAM_SCALE = 4096
INT16_MAX = 32767
INT16_MIN = -32768

BAND_AUTO = 0.90    # PolicyEngine: log silently
BAND_FLAG = 0.65    # PolicyEngine: log and flag
BAND_ASK = 0.30     # PolicyEngine: ask the user


# --- data ------------------------------------------------------------------


def load_splits(data: Path) -> tuple[list[dict], list[dict], list[dict]]:
    return (
        tg.load_jsonl(data / "splits" / "train.jsonl"),
        tg.load_jsonl(data / "splits" / "val.jsonl"),
        tg.load_jsonl(data / "splits" / "test.jsonl"),
    )


def family(row: dict) -> tuple[str, str]:
    if row["is_transaction"]:
        return ("pos", row["template_id"].rsplit("_", 1)[0])
    return ("neg", row["negative_type"])


def family_weights(rows: list[dict]) -> np.ndarray:
    """Give every (label, family) the same total weight.

    Without this, 20k balance/promo rows drown the 210 failed-transaction rows, and the
    model never spends weight on the words that separate them.
    """
    groups: dict[tuple[str, str], list[int]] = defaultdict(list)
    for index, row in enumerate(rows):
        groups[family(row)].append(index)
    weights = np.ones(len(rows), dtype=np.float64)
    for indexes in groups.values():
        weights[indexes] = 1.0 / len(indexes)
    return weights * len(rows) / weights.sum()


SYNONYM_GROUPS: tuple[tuple[str, tuple[str, ...]], ...] = (
    (r"\bsuccessful\b", ("successful", "success", "completed", "confirmed", "approved")),
    (r"\bpaid\b", ("paid", "debited", "spent", "charged", "deducted")),
    (r"\bdebited\b", ("debited", "paid", "spent", "charged", "deducted")),
    (r"\bdeducted\b", ("deducted", "debited", "paid")),
    (r"\breceived\b", ("received", "credited", "deposited")),
    (r"\bcredited\b", ("credited", "received", "deposited")),
    (r"\bjust deposited\b", ("just deposited", "deposited", "credited")),
    (r"\bsent\b", ("sent", "transferred")),
    (r"\btransferred\b", ("transferred", "sent")),
    (r"\bsent you\b", ("sent you", "paid you", "transferred to you")),
    (r"\bfailed\b", ("failed", "declined", "rejected", "unsuccessful", "not successful")),
    (r"\bis processing\b", ("is processing", "is pending", "is on hold",
                            "is awaiting confirmation")),
    (r"\bpending\b", ("pending", "processing", "on hold")),
    (r"\breversed\b", ("reversed", "refunded", "returned to your account")),
    (r"\bhas requested\b", ("has requested", "requests", "sent a collect request")),
    (r"\be-mandate\b", ("e-mandate", "autopay", "auto-debit", "standing instruction")),
    (r"\bOTP\b", ("OTP", "one-time password", "verification code")),
)


def augment_rows(rows: list[dict], copies: int, seed: int = 7) -> list[dict]:
    """Paraphrase each training row `copies` times with interchangeable wording.

    A text model can only learn that "confirmed" and "successful" mean the same thing if
    both appear in training under the same label. The regex layer used to encode that
    knowledge by hand; augmentation puts it in the data instead, so the learned weights,
    not a keyword list, decide every message.
    """
    import random
    import re

    rng = random.Random(seed)
    out = list(rows)
    for _ in range(copies):
        for row in rows:
            body = row["text"]
            candidates = [(pattern, options) for pattern, options in SYNONYM_GROUPS
                          if re.search(pattern, body, re.I)]
            if not candidates:
                continue
            pattern, options = rng.choice(candidates)
            replacement = rng.choice(options)
            clone = dict(row)
            clone["text"] = re.sub(pattern, replacement, body, count=1, flags=re.I)
            out.append(clone)
    return out


def bucket_matrix(rows: list[dict], buckets: int) -> sparse.csr_matrix:
    mask = buckets - 1
    indptr = [0]
    indices: list[int] = []
    for row in rows:
        indices.extend(tg.ngram_buckets(tg.full_text(row), mask))
        indptr.append(len(indices))
    data = np.ones(len(indices), dtype=np.float32)
    return sparse.csr_matrix(
        (data, np.asarray(indices, dtype=np.int64), np.asarray(indptr, dtype=np.int64)),
        shape=(len(rows), buckets),
    )


def labels(rows: list[dict]) -> np.ndarray:
    return np.asarray([1 if row["is_transaction"] else 0 for row in rows], dtype=np.int8)


# --- estimators ------------------------------------------------------------


def fit_nb(matrix, y: np.ndarray, weights: np.ndarray, smooth: float):
    """Smoothed log-odds per bucket, then a 1-D temperature fit."""
    pos = np.asarray(matrix[y == 1].T @ weights[y == 1]).ravel()
    neg = np.asarray(matrix[y == 0].T @ weights[y == 0]).ravel()
    pos_total = float(weights[y == 1].sum())
    neg_total = float(weights[y == 0].sum())
    coef = (np.log((pos + smooth) / (pos_total + 2 * smooth))
            - np.log((neg + smooth) / (neg_total + 2 * smooth)))
    # Buckets never seen in training carry no evidence; without this their smoothed value
    # is a constant that leaks into every message and turns message length into a feature.
    coef[(pos + neg) == 0] = 0.0
    raw = np.asarray(matrix @ coef).ravel()
    calib = LogisticRegression(C=1e6, solver="liblinear", max_iter=200)
    calib.fit(raw.reshape(-1, 1), y)
    temperature = float(calib.coef_[0][0])
    return coef * temperature, float(calib.intercept_[0]), temperature


def fit_lr(matrix, y: np.ndarray, weights: np.ndarray, c_value: float):
    model = LogisticRegression(
        C=c_value, solver="liblinear", max_iter=400, random_state=42,
        fit_intercept=True,
    )
    model.fit(matrix, y, sample_weight=weights)
    return model.coef_[0].astype(np.float64), float(model.intercept_[0]), 1.0


# --- reporting -------------------------------------------------------------


def band_metrics(y: np.ndarray, scores: np.ndarray) -> dict:
    positives = scores[y == 1]
    negatives = scores[y == 0]
    return {
        "recall_at_0.65": round(float(np.mean(positives >= BAND_FLAG)), 6),
        "recall_at_0.90": round(float(np.mean(positives >= BAND_AUTO)), 6),
        "negatives_at_0.30": round(float(np.mean(negatives >= BAND_ASK)), 6),
        "negatives_at_0.65": round(float(np.mean(negatives >= BAND_FLAG)), 6),
    }


def template_slices(rows: list[dict], y: np.ndarray, scores: np.ndarray,
                    threshold: float) -> dict:
    groups: dict[str, list[int]] = defaultdict(list)
    for index, row in enumerate(rows):
        groups[row["template_id"].rsplit("_", 1)[0]].append(index)
    out = {}
    for key in sorted(groups):
        idx = np.asarray(groups[key])
        sub_y, sub_s = y[idx], scores[idx]
        entry = {
            "n": int(idx.size),
            "positives": int(sub_y.sum()),
            "p10": round(float(np.quantile(sub_s, 0.10)), 4),
            "p50": round(float(np.quantile(sub_s, 0.50)), 4),
            "above_threshold": round(float(np.mean(sub_s >= threshold)), 4),
        }
        if entry["positives"]:
            entry["recall_at_0.65"] = round(float(np.mean(sub_s[sub_y == 1] >= BAND_FLAG)), 4)
        out[key] = entry
    return out


def top_tokens(coef: np.ndarray, token_to_bucket: dict[str, int], count: int = 25) -> dict:
    bucket_to_token: dict[int, str] = {}
    for token, bucket in token_to_bucket.items():
        bucket_to_token.setdefault(bucket, token)
    order = np.argsort(coef)
    negative = [(bucket_to_token.get(int(b), f"#{int(b)}"), round(float(coef[b]), 4))
                for b in order[:count] if coef[b] < 0]
    positive = [(bucket_to_token.get(int(b), f"#{int(b)}"), round(float(coef[b]), 4))
                for b in order[-count:][::-1] if coef[b] > 0]
    return {"transaction": positive, "noise": negative}


def write_ngrams(path: Path, coef: np.ndarray) -> dict:
    entries = []
    for bucket in np.flatnonzero(coef):
        fixed = int(round(float(coef[bucket]) * NGRAM_SCALE))
        fixed = max(INT16_MIN, min(INT16_MAX, fixed))
        if fixed:
            entries.append((int(bucket), fixed))
    entries.sort()
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("wb") as handle:
        handle.write(b"CBG1")
        handle.write(struct.pack("<I", len(entries)))
        for bucket, fixed in entries:
            handle.write(struct.pack("<Ih", bucket, fixed))
    return {
        "entries": len(entries),
        "bytes": path.stat().st_size,
        "sha256": hashlib.sha256(path.read_bytes()).hexdigest(),
    }


def main() -> None:
    parser = argparse.ArgumentParser(description="Train the regex-free text gate")
    parser.add_argument("--data", type=Path, default=HERE)
    parser.add_argument("--out", type=Path, default=HERE / "model")
    parser.add_argument("--name", default="text",
                        help="artifact basename: gate_weights_<name>.json")
    parser.add_argument("--model", choices=("nb", "lr"), default="nb")
    parser.add_argument("--buckets", type=int, default=1 << 16)
    parser.add_argument("--C", type=float, default=0.1)
    parser.add_argument("--smooth", type=float, default=20.0)
    parser.add_argument("--prior", type=float, default=0.20)
    parser.add_argument("--target-fpr", type=float, default=0.01)
    parser.add_argument("--no-family-weights", action="store_true")
    parser.add_argument("--augment", type=int, default=0,
                        help="paraphrase each training row this many extra times")
    parser.add_argument("--fixture-rows", type=int, default=200)
    args = parser.parse_args()

    if args.buckets <= 0 or args.buckets & (args.buckets - 1):
        raise SystemExit("--buckets must be a power of two (GateNgrams requires it)")

    train_rows, val_rows, test_rows = load_splits(args.data)
    if args.augment:
        before = len(train_rows)
        train_rows = augment_rows(train_rows, args.augment)
        print(f"augmented train {before} -> {len(train_rows)} rows")
    print(f"rows: train={len(train_rows)} val={len(val_rows)} test={len(test_rows)}")
    print(f"model={args.model} buckets={args.buckets} prior={args.prior}")

    mask = args.buckets - 1
    token_to_bucket: dict[str, int] = {}
    for row in train_rows:
        for token in tg.ngram_tokens(tg.full_text(row)):
            token_to_bucket.setdefault(token, tg.fnv1a32(token) & mask)

    train_matrix = bucket_matrix(train_rows, args.buckets)
    val_matrix = bucket_matrix(val_rows, args.buckets)
    test_matrix = bucket_matrix(test_rows, args.buckets)
    y_train, y_val, y_test = labels(train_rows), labels(val_rows), labels(test_rows)
    print(f"nnz train={train_matrix.nnz} dims={train_matrix.shape[1]}")

    weights = (np.ones(len(train_rows), dtype=np.float64)
               if args.no_family_weights else family_weights(train_rows))
    if args.model == "nb":
        coef, intercept_train, temperature = fit_nb(
            train_matrix, y_train, weights, args.smooth)
        print(f"nb temperature {temperature:.4f} intercept {intercept_train:.4f} "
              f"nonzero {int(np.count_nonzero(coef))}")
    else:
        coef, intercept_train, temperature = fit_lr(
            train_matrix, y_train, weights, args.C)
        print(f"lr nonzero {int(np.count_nonzero(coef))}")

    prior_logit = math.log(args.prior / (1.0 - args.prior))
    intercept_deploy = intercept_train + prior_logit

    def score(matrix) -> np.ndarray:
        return 1.0 / (1.0 + np.exp(-(matrix @ coef + intercept_deploy)))

    val_scores, test_scores = score(val_matrix), score(test_matrix)
    threshold = float(np.clip(tg.choose_threshold(y_val, val_scores, args.target_fpr),
                              1e-6, 1.0 - 1e-6))

    metrics = {
        "test": {
            **tg.metrics_at(y_test, test_scores, threshold),
            "pr_auc": round(float(average_precision_score(y_test, test_scores)), 6),
            "roc_auc": round(float(roc_auc_score(y_test, test_scores)), 6),
            "brier": round(float(brier_score_loss(y_test, test_scores)), 6),
            **band_metrics(y_test, test_scores),
        },
        "val": {
            **tg.metrics_at(y_val, val_scores, threshold),
            "pr_auc": round(float(average_precision_score(y_val, val_scores)), 6),
            "roc_auc": round(float(roc_auc_score(y_val, val_scores)), 6),
            **band_metrics(y_val, val_scores),
        },
    }
    print(f"threshold {threshold:.4f}")
    for split in ("val", "test"):
        m = metrics[split]
        print(f"{split.upper():4s} recall {m['recall']:.4f} fpr {m['fpr']:.4f} "
              f"auc {m['roc_auc']:.4f} | recall@0.65 {m['recall_at_0.65']:.4f} "
              f"| neg>=0.30 {m['negatives_at_0.30']:.4f} "
              f"| pos>=0.90 {m['recall_at_0.90']:.4f}")

    base_name = f"_{args.name}" if args.name and args.name not in ("", "default") else ""
    ngram_info = {
        "enabled": True,
        "buckets": args.buckets,
        "hash": "fnv1a32",
        "scale": NGRAM_SCALE,
        "file": f"gate_ngrams{base_name}.bin",
    }
    ngram_path = args.out / f"gate_ngrams{base_name}.bin"
    ngram_info.update(write_ngrams(ngram_path, coef))

    # Neutral signal weights: ln(1.0) == 0, so the runtime's regex work cannot change the
    # score. Keeping the keys means the artifact still satisfies GateModel's contract.
    neutral = {key: 1.0 for key in tg.FEATURES}

    manifest_path = args.data / "manifest.json"
    manifest_sha = (hashlib.sha256(manifest_path.read_bytes()).hexdigest()
                    if manifest_path.exists() else None)
    model_version = datetime.now(timezone.utc).strftime("gate-text-%Y.%m.%d")

    weights_json = {
        "model_version": model_version,
        "schema_version": 1,
        "trained_at_utc": datetime.now(timezone.utc).isoformat(),
        "dataset_manifest_sha256": manifest_sha,
        "prior": args.prior,
        "intercept": round(intercept_deploy, 8),
        "bias_adjust": round(intercept_train, 8),
        "threshold": round(threshold, 8),
        "calibration": {"kind": "none"},
        "features": neutral,
        "ngrams": ngram_info,
    }
    weights_path = args.out / f"gate_weights{base_name}.json"
    weights_path.write_text(
        json.dumps(weights_json, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")

    report = {
        "model_version": model_version,
        "dataset_manifest_sha256": manifest_sha,
        "estimator": {
            "kind": args.model,
            "buckets": args.buckets,
            "C": args.C if args.model == "lr" else None,
            "smooth": args.smooth if args.model == "nb" else None,
            "temperature": round(temperature, 6),
            "family_weights": not args.no_family_weights,
            "prior": args.prior,
            "threshold": round(threshold, 8),
            "target_fpr": args.target_fpr,
            "nonzero_buckets": int(np.count_nonzero(coef)),
            "signal_features": "neutral (all 1.0)",
        },
        "rows": {"train": len(train_rows), "val": len(val_rows), "test": len(test_rows)},
        "metrics": metrics,
        "slices_test": template_slices(test_rows, y_test, test_scores, threshold),
        "ngrams": ngram_info,
        "top_tokens": top_tokens(coef, token_to_bucket),
    }
    report_path = args.out / f"eval_report{base_name}.json"
    report_path.write_text(
        json.dumps(report, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")

    fixture_path = args.data / "fixtures" / f"gate_smoke{base_name}.jsonl"
    fixture_path.parent.mkdir(parents=True, exist_ok=True)
    with fixture_path.open("w", encoding="utf-8", newline="\n") as handle:
        for row in test_rows[:args.fixture_rows]:
            handle.write(json.dumps(row, ensure_ascii=False, separators=(",", ":")) + "\n")

    print(f"wrote {weights_path}")
    print(f"wrote {ngram_path} ({ngram_info['entries']} entries, {ngram_info['bytes']} B)")
    print(f"wrote {report_path}")
    print(f"wrote {fixture_path}")


if __name__ == "__main__":
    sys.exit(main())
