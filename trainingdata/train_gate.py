"""Fit the CashBuddy transaction gate and export runtime artifacts.

The feature extraction here mirrors the Kotlin pipeline in
shared/src/commonMain/kotlin/com/cashbuddy/core/prob/EvidenceExtractor.kt and
Regexes.kt. Any change to those files must be reflected here, or the model will
suffer train/serve skew.

Usage:
    python trainingdata/train_gate.py --data trainingdata --out trainingdata/model
    python trainingdata/train_gate.py --data trainingdata --out trainingdata/model --deploy

Artifacts:
    gate_weights.json   likelihood ratios, bias, prior, threshold, feature scaling
    gate_ngrams.bin     pruned hashed n-gram weights (optional)
    eval_report.json    test metrics, slices, confusion matrix

Calibration: the deployed artifact uses NO Platt scaling. Platt fitted on the balanced
validation split multiplied the strongest signals (account ~168x, utr ~3201x), which both
destroyed the meaning of "likelihood ratio" and pushed app-style receipts (no A/c, no UTR)
far below the PolicyEngine bands. The only calibration that ships is the prior shift:
`bias_adjust` is the trained intercept, and the runtime adds `priorLogit` on top.

Feature scaling: the 20 deterministic signals are scaled by `--feature-scale` while fitting.
Because L2 regularisation shrinks rare binary columns towards zero, the `failure` signal had
too little weight to beat the positive n-grams of a failed-transaction message. Fitting on
`scale * x` and exporting `scale * w` (so the runtime still consumes raw booleans) reduces
that shrinkage without touching the n-gram block.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import math
import re
import struct
import sys
from collections import Counter, defaultdict
from datetime import datetime, timezone
from pathlib import Path

import numpy as np
from scipy import sparse
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import (
    average_precision_score,
    brier_score_loss,
    roc_auc_score,
    roc_curve,
)

HERE = Path(__file__).resolve().parent
REPO = HERE.parent

FEATURES = (
    "amount_in_context",
    "amount_debit_sent",
    "amount_credit_sent",
    "amount_forbidden_shape",
    "amount",
    "account",
    "utr",
    "bank_sender",
    "upi_handle",
    "balance",
    "merchant_pkg",
    "success",
    "OTP",
    "promo",
    "offer",
    "recent_similar_amount",
    "recent_same_merchant",
    "burst_detected",
    "failure",
    "failure_in_context",
    "pending_in_context",
)

NGRAM_BUCKETS = 1 << 18
NGRAM_MASK = NGRAM_BUCKETS - 1
NGRAM_SCALE = 4096
DEPLOY_PRIOR = 0.20
FEATURE_LOGIT_MIN = -20.0
FEATURE_LOGIT_MAX = 20.0
NGRAM_LOGIT_MAX = 8.0

# --- Regexes ported from Regexes.kt -----------------------------------------

CURRENCY_PREFIX = r"(?:₹|Rs[.:]?|INR|[$€£¥]|USD|EUR|GBP|CAD|AUD|AED|SGD|JPY|C\$|A\$|S\$)"
CURRENCY_SUFFIX = r"(?:₹|Rs[.:]?|INR|[$€£¥]|USD|EUR|GBP|CAD|AUD|AED|SGD|JPY)"

AMOUNT = re.compile(
    rf"(?i){CURRENCY_PREFIX}\s*(\d+(?:,\d{{2,3}})*(?:\.\d{{1,2}})?|\d+(?:\.\d{{1,2}})?)"
)
AMOUNT_SUFFIX = re.compile(
    rf"(?i)(\d+(?:,\d{{2,3}})*(?:\.\d{{1,2}})?|\d+(?:\.\d{{1,2}})?)\s*{CURRENCY_SUFFIX}"
)
ACCOUNT = re.compile(
    r"(?i)\b(?:a/c|ac|acct|account|card)\s*(?:no\.?)?\s*(?:ending\s+)?"
    r"(?:with\s+)?(?:[*xX.]*)(\d{3,4})\b"
)
UTR = re.compile(
    r"(?i)\b(?:UPI\s+(?:transaction\s+id|ref(?:\s+no|\s+id|\s+number)?)|"
    r"Transaction\s+ID|utr|ref|txn|rrn)\s*(?:no\.?|id)?\s*[:#\s]+\s*([A-Za-z0-9]{8,35})"
)
UPI_HANDLE = re.compile(r"(?i)\b[a-z0-9._-]+@[a-z]+\b")
BALANCE = re.compile(r"(?i)(?:avl\s*bal|available\s+balance|bal)\s*[:₹$€£¥]?\s*\d")
DEBIT = re.compile(
    r"(?i)\b(debited|paid(?!\s+(?:to\s+)?you\b)|spent|sent(?!\s+you\b)|"
    r"transferred(?!\s+to\s+you\b)|withdrawn|charged|purchase at|payment)\b"
)
CREDIT = re.compile(
    r"(?i)\b(credited|received|deposited|added|refunded|cashback|"
    r"paid\s+(?:to\s+)?you|sent\s+you|transferred\s+to\s+you)\b"
)
OTP = re.compile(r"(?i)\b(otp|one time password|verification code|secret code|do not share)\b")
PROMO = re.compile(
    r"(?i)\b(congratulations|pre-approved|win cash|apply now|apply for|apply|earn|"
    r"special offer|voucher)\b"
)
OFFER = re.compile(r"(?i)\b(offer|discount|deal|cashback up to)\b")
SUCCESS = re.compile(r"(?i)\b(successful|completed|success|confirmed|done)\b")
FAILURE = re.compile(
    r"(?i)\b(failed|failure|declined|unsuccessful|not\s+successful|rejected|reversed|"
    r"cancell?ed|could\s+not|couldn'?t|unable\s+to|not\s+processed|not\s+completed|"
    r"no\s+amount|not\s+debited|not\s+charged|insufficient\s+(?:balance|funds))\b"
)
PENDING = re.compile(
    r"(?i)\b(pending|processing|on\s+hold|awaiting\s+confirmation|under\s+process|"
    r"in\s+progress)\b"
)

CURR = r"(?:₹|Rs[.:]?|INR|[$€£¥]|USD|EUR|GBP|CAD|AUD|AED|SGD|JPY)"
FORBIDDEN = (
    re.compile(CURR + r"\s*\d[\d,.]*[kKlL]?\s*off\b", re.I),
    re.compile(r"\b(?:up\s*to|upto)\s*" + CURR + r"\s*\d", re.I),
    re.compile(CURR + r"\s*\d[\d,.]*[kKlL]?\s*%"),
    re.compile(
        CURR + r"\s*\d[\d,.]*[kKlL]?\s*(?:[/]\s*)?"
        r"(?:LPA|CTC|P\.?A\.?|P\.?M\.?|pm|pa|month|annum|year)\b",
        re.I,
    ),
    re.compile(CURR + r"\s*\d[\d,.]*[kKlL]?\s*[-–]\s*(?:" + CURR + r")?\s*\d", re.I),
    re.compile(CURR + r"\s*\d[\d,.]*\s*(?:lakh|lakhs|crore|crores|lac|lacs)\b", re.I),
    re.compile(
        CURR + r"\s*\d[\d,.]*[kKlL]?\s*"
        r"(?:per\s+(?:annum|month|year)|a\s+year|annually|salary|stipend)\b",
        re.I,
    ),
)

DECIMAL_PROTECT = re.compile(r"(\d)\.(\d)")
CURRENCY_ABBREV = re.compile(r"(?i)\b(rs|re|inr)\.")
BANKING_ABBREV = re.compile(r"(?i)\b(a/c|ac|no|ref|vpa|co|ltd|dr|mr|mrs)\.")
SENTENCE_SPLIT = re.compile(r"[.!?\n\r•·|—]+")


def sentences(text: str) -> list[str]:
    s = DECIMAL_PROTECT.sub("\\1\x00\\2", text)
    s = CURRENCY_ABBREV.sub("\\1\x01", s)
    s = BANKING_ABBREV.sub("\\1\x01", s)
    return [
        part.replace("\x00", ".").replace("\x01", ".").strip()
        for part in SENTENCE_SPLIT.split(s)
        if part.strip()
    ]


def amount_present(text: str) -> bool:
    return bool(AMOUNT.search(text) or AMOUNT_SUFFIX.search(text))


def amount_and_debit_same_sentence(text: str) -> bool:
    return any(amount_present(s) and DEBIT.search(s) for s in sentences(text))


def amount_and_credit_same_sentence(text: str) -> bool:
    return any(amount_present(s) and CREDIT.search(s) for s in sentences(text))


def amount_in_transaction_context(text: str) -> bool:
    for s in sentences(text):
        if not amount_present(s):
            continue
        low = s.lower()
        if DEBIT.search(s) or CREDIT.search(s) or "payment" in low or "txn" in low:
            return True
    return False


def amount_has_forbidden_shape(text: str) -> bool:
    return any(pattern.search(text) for pattern in FORBIDDEN)


TRAI_SMS_HEADER = re.compile(r"^[a-z]{2}-[a-z0-9]{3,8}$")


def sender_looks_bank(sender: str | None) -> bool:
    if not sender:
        return False
    clean = sender.lower().strip()
    core = clean.split("-", 1)[1] if "-" in clean else clean
    structured = bool(TRAI_SMS_HEADER.match(clean))
    return structured and (
        core.endswith(("bk", "inb", "cb", "crd", "bank")) or "bank" in core
    )


IGNORED_PACKAGE_SEGMENTS = {"com", "in", "org", "net", "io", "co"}


def brand_from_package(package_name: str) -> str | None:
    segments = [
        s for s in re.split(r"[._-]", package_name.lower())
        if len(s) >= 3 and s not in IGNORED_PACKAGE_SEGMENTS
    ]
    if not segments:
        return None
    for segment in reversed(segments):
        if "app" not in segment:
            return segment
    return segments[-1]


def is_merchant_package_hint(package_name: str | None) -> bool:
    if not package_name:
        return False
    p = package_name.lower()
    if p == "android" or p.startswith("android.") or p.startswith("com.android."):
        return False
    brand = brand_from_package(package_name)
    return bool(brand and len(brand) >= 3)


def full_text(row: dict) -> str:
    title = row.get("title") or ""
    return f"{title} {row['text']}" if title else row["text"]


def extract_features(row: dict) -> dict[str, int]:
    text = full_text(row)
    lower = text.lower()
    in_context = amount_in_transaction_context(text)
    failure = bool(FAILURE.search(lower))
    pending = bool(PENDING.search(lower))
    sender = row.get("sender_id") or row.get("title")
    values = {
        "amount_in_context": int(in_context),
        "amount_debit_sent": int(amount_and_debit_same_sentence(text)),
        "amount_credit_sent": int(amount_and_credit_same_sentence(text)),
        "amount_forbidden_shape": int(amount_has_forbidden_shape(text)),
        "amount": int(amount_present(text) and not in_context),
        "account": int(bool(ACCOUNT.search(text))),
        "utr": int(bool(UTR.search(text))),
        "bank_sender": int(sender_looks_bank(sender)),
        "upi_handle": int(bool(UPI_HANDLE.search(text))),
        "balance": int(bool(BALANCE.search(lower))),
        "merchant_pkg": int(is_merchant_package_hint(row.get("package_name"))),
        "success": int(bool(SUCCESS.search(lower))),
        "OTP": int(bool(OTP.search(lower))),
        "promo": int(bool(PROMO.search(lower))),
        "offer": int(bool(OFFER.search(lower))),
        "recent_similar_amount": 0,
        "recent_same_merchant": 0,
        "burst_detected": 0,
        "failure": int(failure),
        "failure_in_context": int(failure and in_context),
        "pending_in_context": int(pending and in_context),
    }
    return values


# --- Tokenizer: must match the Kotlin implementation exactly ------------------

DIGITS = re.compile(r"[0-9]+")
CURRENCY_WORDS = re.compile(r"₹|rs\.?|inr")
NON_TOKEN = re.compile(r"[^a-z0-9@]+")


def fnv1a32(token: str) -> int:
    h = 2166136261
    for byte in token.encode("utf-8"):
        h ^= byte
        h = (h * 16777619) & 0xFFFFFFFF
    return h


def ngram_tokens(text: str) -> set[str]:
    """Tokens produced by Kotlin `GateTokenizer.buckets` before hashing."""
    normalized = text.lower()
    normalized = DIGITS.sub(" num ", normalized)
    normalized = CURRENCY_WORDS.sub(" cur ", normalized)
    normalized = NON_TOKEN.sub(" ", normalized).strip()
    words = normalized.split()
    tokens: set[str] = set()
    for word in words:
        tokens.add("w:" + word)
    for first, second in zip(words, words[1:]):
        tokens.add("b:" + first + "_" + second)
    for word in words:
        padded = "^" + word + "$"
        for size in (3, 4, 5):
            for start in range(len(padded) - size + 1):
                tokens.add("c:" + padded[start:start + size])
    return tokens


def ngram_buckets(text: str, mask: int = NGRAM_MASK) -> set[int]:
    """Tokenise exactly like Kotlin `GateTokenizer.buckets`.

    `mask` must be `artifact.buckets - 1`; the default keeps the historical 2^18 space
    used by the signal+ngram model.
    """
    return {fnv1a32(token) & mask for token in ngram_tokens(text)}


# --- Data loading ------------------------------------------------------------


def load_jsonl(path: Path) -> list[dict]:
    rows = []
    with path.open(encoding="utf-8") as handle:
        for line in handle:
            line = line.strip()
            if line:
                rows.append(json.loads(line))
    return rows


def build_matrix(rows: list[dict], use_ngrams: bool, feature_scale: float = 1.0):
    feature_rows: list[int] = []
    feature_cols: list[int] = []
    feature_vals: list[float] = []
    expected: list[dict[str, int]] = []
    bucket_sets: list[set[int]] = []

    for index, row in enumerate(rows):
        values = extract_features(row)
        expected.append(values)
        for offset, name in enumerate(FEATURES):
            if values[name]:
                feature_rows.append(index)
                feature_cols.append(offset)
                feature_vals.append(feature_scale)
        if use_ngrams:
            buckets = ngram_buckets(full_text(row))
        else:
            buckets = set()
        bucket_sets.append(buckets)
        for bucket in buckets:
            feature_rows.append(index)
            feature_cols.append(len(FEATURES) + bucket)
            feature_vals.append(1.0)

    width = len(FEATURES) + (NGRAM_BUCKETS if use_ngrams else 0)
    matrix = sparse.csr_matrix(
        (np.asarray(feature_vals, dtype=np.float32),
         (np.asarray(feature_rows), np.asarray(feature_cols))),
        shape=(len(rows), width),
    )
    return matrix, expected, bucket_sets


def labels(rows: list[dict]) -> np.ndarray:
    return np.asarray([1 if row["is_transaction"] else 0 for row in rows], dtype=np.int8)


# --- Training ----------------------------------------------------------------


def fit(train_rows: list[dict], use_ngrams: bool, C: float, feature_scale: float):
    matrix, _, _ = build_matrix(train_rows, use_ngrams, feature_scale)
    y = labels(train_rows)
    model = LogisticRegression(
        C=C,
        solver="liblinear",
        max_iter=500,
        random_state=42,
        fit_intercept=True,
    )
    model.fit(matrix, y)
    return model, matrix


def prune_ngrams(coef: np.ndarray, top_k: int) -> np.ndarray:
    pruned = coef.copy()
    bucket_coefs = pruned[len(FEATURES):]
    nonzero = np.flatnonzero(bucket_coefs)
    if nonzero.size <= top_k:
        return pruned
    order = np.argsort(-np.abs(bucket_coefs[nonzero]))[:top_k]
    keep = np.zeros_like(bucket_coefs)
    keep[nonzero[order]] = 1.0
    pruned[len(FEATURES):] = bucket_coefs * keep
    return pruned


def logits(matrix, coef: np.ndarray, intercept: float) -> np.ndarray:
    return matrix @ coef + intercept


def choose_threshold(y: np.ndarray, scores: np.ndarray, target_fpr: float) -> float:
    fpr, tpr, thresholds = roc_curve(y, scores)
    allowed = fpr <= target_fpr
    if not allowed.any():
        return float(thresholds[0])
    best = np.argmax(np.where(allowed, tpr, -1.0))
    return float(thresholds[best])


def sigmoid(values: np.ndarray) -> np.ndarray:
    return 1.0 / (1.0 + np.exp(-values))


def metrics_at(y: np.ndarray, scores: np.ndarray, threshold: float) -> dict:
    predicted = (scores >= threshold).astype(np.int8)
    tp = int(np.sum((predicted == 1) & (y == 1)))
    tn = int(np.sum((predicted == 0) & (y == 0)))
    fp = int(np.sum((predicted == 1) & (y == 0)))
    fn = int(np.sum((predicted == 0) & (y == 1)))
    recall = tp / max(1, tp + fn)
    fpr = fp / max(1, fp + tn)
    precision = tp / max(1, tp + fp)
    return {
        "tp": tp, "tn": tn, "fp": fp, "fn": fn,
        "recall": round(recall, 6),
        "fpr": round(fpr, 6),
        "precision": round(precision, 6),
        "accuracy": round((tp + tn) / max(1, len(y)), 6),
    }


def runtime_signal_keys() -> list[str] | None:
    """Read GateSignal enum order from Kotlin so the artifact cannot drift from the app."""
    path = (
        REPO / "shared" / "src" / "commonMain" / "kotlin" / "com" / "cashbuddy"
        / "core" / "prob" / "gate" / "GateWeights.kt"
    )
    if not path.exists():
        return None
    source = path.read_text(encoding="utf-8")
    block = source.split("enum class GateSignal", 1)
    if len(block) != 2:
        return None
    return re.findall(r'\(\s*"([a-zA-Z_]+)"\s*,', block[1])


def check_runtime_contract(runtime_keys: list[str] | None) -> None:
    if runtime_keys is None:
        print("! GateWeights.kt not found; skipping trainer/runtime signal check")
        return
    if runtime_keys != list(FEATURES):
        raise SystemExit(
            "trainer/runtime signal mismatch:\n"
            f"  trainer: {list(FEATURES)}\n"
            f"  runtime: {runtime_keys}"
        )
    print(f"runtime contract ok: {len(FEATURES)} signals match GateWeights.kt")


def slice_metrics(rows: list[dict], y: np.ndarray, scores: np.ndarray, threshold: float) -> dict:
    groups: dict[str, dict[str, list[int]]] = defaultdict(lambda: defaultdict(list))
    for index, row in enumerate(rows):
        keys = {
            "source_type": row["source_type"],
            "difficulty": row["difficulty"],
            "txn_type": row["txn_type"] or "NONE",
            "negative_type": row["negative_type"] or "POSITIVE",
        }
        for name, key in keys.items():
            groups[name][key].append(index)

    out: dict[str, dict] = {}
    for name, buckets in groups.items():
        out[name] = {}
        for key, indexes in sorted(buckets.items()):
            idx = np.asarray(indexes)
            sub_y = y[idx]
            sub_scores = scores[idx]
            entry = {"n": int(idx.size), "positives": int(sub_y.sum())}
            entry["negatives"] = int(idx.size - sub_y.sum())
            if entry["positives"]:
                entry["recall"] = round(
                    float(np.mean(sub_scores[sub_y == 1] >= threshold)), 6)
            if entry["negatives"]:
                entry["fpr"] = round(
                    float(np.mean(sub_scores[sub_y == 0] >= threshold)), 6)
            out[name][key] = entry
    return out


def reliability(y: np.ndarray, scores: np.ndarray, bins: int = 10) -> dict:
    edges = np.linspace(0.0, 1.0, bins + 1)
    rows = []
    ece = 0.0
    for low, high in zip(edges[:-1], edges[1:]):
        mask = (scores >= low) & (scores < high)
        count = int(mask.sum())
        if count == 0:
            continue
        confidence = float(scores[mask].mean())
        accuracy = float(y[mask].mean())
        ece += abs(confidence - accuracy) * count / len(y)
        rows.append({
            "bin": f"{low:.1f}-{high:.1f}",
            "count": count,
            "confidence": round(confidence, 4),
            "accuracy": round(accuracy, 4),
        })
    return {"ece": round(ece, 6), "bins": rows}


def write_ngrams(path: Path, coef: np.ndarray) -> dict:
    bucket_coefs = coef[len(FEATURES):]
    entries = []
    for bucket in np.flatnonzero(bucket_coefs):
        weight = float(bucket_coefs[bucket])
        fixed = int(round(weight * NGRAM_SCALE))
        fixed = max(-32768, min(32767, fixed))
        if fixed != 0:
            entries.append((int(bucket), fixed))
    entries.sort()
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("wb") as handle:
        handle.write(b"CBG1")
        handle.write(struct.pack("<I", len(entries)))
        for bucket, fixed in entries:
            handle.write(struct.pack("<Ih", bucket, fixed))
    digest = hashlib.sha256(path.read_bytes()).hexdigest()
    return {
        "entries": len(entries),
        "bytes": path.stat().st_size,
        "sha256": digest,
    }


def main() -> None:
    parser = argparse.ArgumentParser(description="Train the CashBuddy gate")
    parser.add_argument("--data", type=Path, default=HERE)
    parser.add_argument("--out", type=Path, default=HERE / "model")
    parser.add_argument("--C", type=float, default=1.0)
    parser.add_argument("--feature-scale", type=float, default=2.0,
                        help="fit the 20 deterministic signals at this scale (runtime uses raw booleans)")
    parser.add_argument("--target-fpr", type=float, default=0.01)
    parser.add_argument("--ngram-top-k", type=int, default=20000)
    parser.add_argument("--no-ngrams", action="store_true")
    parser.add_argument("--deploy", action="store_true",
                        help="copy artifacts into shared composeResources")
    parser.add_argument("--fixture-rows", type=int, default=200)
    args = parser.parse_args()

    use_ngrams = not args.no_ngrams
    data: Path = args.data
    out: Path = args.out
    out.mkdir(parents=True, exist_ok=True)

    train_rows = load_jsonl(data / "splits" / "train.jsonl")
    val_rows = load_jsonl(data / "splits" / "val.jsonl")
    test_rows = load_jsonl(data / "splits" / "test.jsonl")
    print(f"rows: train={len(train_rows)} val={len(val_rows)} test={len(test_rows)}")

    check_runtime_contract(runtime_signal_keys())

    model, _ = fit(train_rows, use_ngrams, args.C, args.feature_scale)
    coef_fit = model.coef_[0].astype(np.float64)
    intercept_train = float(model.intercept_[0])
    if use_ngrams:
        coef_fit = prune_ngrams(coef_fit, args.ngram_top_k)

    # The fit saw `feature_scale * x`. The runtime sees raw booleans, so the exported
    # signal weights must absorb the scale exactly once.
    coef_runtime = coef_fit.copy()
    coef_runtime[:len(FEATURES)] *= args.feature_scale
    print(f"nonzero weights: {int(np.count_nonzero(coef_runtime))} "
          f"(ngram cap {args.ngram_top_k if use_ngrams else 0})")

    prior_logit = math.log(DEPLOY_PRIOR / (1.0 - DEPLOY_PRIOR))
    intercept_deploy = intercept_train + prior_logit
    bias_adjust = intercept_train

    val_matrix, _, _ = build_matrix(val_rows, use_ngrams, args.feature_scale)
    y_val = labels(val_rows)
    val_scores = sigmoid(logits(val_matrix, coef_fit, intercept_deploy))

    test_matrix, _, _ = build_matrix(test_rows, use_ngrams, args.feature_scale)
    y_test = labels(test_rows)
    test_scores = sigmoid(logits(test_matrix, coef_fit, intercept_deploy))

    threshold = choose_threshold(y_val, val_scores, args.target_fpr)
    threshold = float(np.clip(threshold, 1e-6, 1.0 - 1e-6))

    full_metrics = metrics_at(y_test, test_scores, threshold)
    pr_auc = float(average_precision_score(y_test, test_scores))
    roc_auc = float(roc_auc_score(y_test, test_scores))
    brier = float(brier_score_loss(y_test, test_scores))
    calib = reliability(y_test, test_scores)

    # Baseline: the same fit without n-gram contributions.
    baseline_scores = sigmoid(
        test_matrix[:, :len(FEATURES)] @ coef_fit[:len(FEATURES)] + intercept_deploy)
    baseline_val_scores = sigmoid(
        val_matrix[:, :len(FEATURES)] @ coef_fit[:len(FEATURES)] + intercept_deploy)
    baseline_threshold = choose_threshold(y_val, baseline_val_scores, args.target_fpr)
    baseline_metrics = metrics_at(y_test, baseline_scores, baseline_threshold)
    baseline_pr_auc = float(average_precision_score(y_test, baseline_scores))

    lr_weights = {}
    for index, name in enumerate(FEATURES):
        logit_weight = float(np.clip(coef_runtime[index], FEATURE_LOGIT_MIN, FEATURE_LOGIT_MAX))
        lr_weights[name] = round(math.exp(logit_weight), 8)

    ngram_info = {"enabled": False}
    if use_ngrams:
        ngram_info = {
            "enabled": True,
            "buckets": NGRAM_BUCKETS,
            "hash": "fnv1a32",
            "scale": NGRAM_SCALE,
            "file": "gate_ngrams.bin",
        }
        ngram_info.update(write_ngrams(out / "gate_ngrams.bin", coef_runtime))

    manifest_path = data / "manifest.json"
    manifest_sha = (hashlib.sha256(manifest_path.read_bytes()).hexdigest()
                    if manifest_path.exists() else None)
    model_version = datetime.now(timezone.utc).strftime("gate-%Y.%m.%d")

    weights = {
        "model_version": model_version,
        "schema_version": 1,
        "trained_at_utc": datetime.now(timezone.utc).isoformat(),
        "dataset_manifest_sha256": manifest_sha,
        "prior": DEPLOY_PRIOR,
        "intercept": round(intercept_deploy, 8),
        "bias_adjust": round(bias_adjust, 8),
        "threshold": round(threshold, 8),
        # Documentation only: the runtime applies raw booleans, the trainer absorbed the
        # scale into `features`. No Platt/temperature calibration ships.
        "calibration": {"kind": "none"},
        "feature_scaling": {
            "scale": args.feature_scale,
            "note": "features[] already include this factor; the runtime must not apply it again",
        },
        "features": lr_weights,
        "ngrams": ngram_info,
    }
    (out / "gate_weights.json").write_text(
        json.dumps(weights, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")

    report = {
        "model_version": model_version,
        "dataset_manifest_sha256": manifest_sha,
        "rows": {"train": len(train_rows), "val": len(val_rows), "test": len(test_rows)},
        "training": {
            "C": args.C,
            "feature_scale": args.feature_scale,
            "ngrams": use_ngrams,
            "calibration": "none",
            "nonzero_weights": int(np.count_nonzero(coef_runtime)),
            "bias_adjust": round(bias_adjust, 8),
        },
        "threshold": {
            "value": round(threshold, 8),
            "target_fpr": args.target_fpr,
        },
        "metrics": {
            "pr_auc": round(pr_auc, 6),
            "roc_auc": round(roc_auc, 6),
            "brier": round(brier, 6),
            "ece": calib["ece"],
            **full_metrics,
        },
        "baseline_18_signals": {
            "threshold": round(float(baseline_threshold), 8),
            "pr_auc": round(baseline_pr_auc, 6),
            **baseline_metrics,
        },
        "slices": slice_metrics(test_rows, y_test, test_scores, threshold),
        "reliability": calib["bins"],
        "feature_weights": lr_weights,
        "ngrams": ngram_info,
    }
    (out / "eval_report.json").write_text(
        json.dumps(report, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")

    print(f"threshold {threshold:.4f} | PR-AUC {pr_auc:.4f} | "
          f"recall {full_metrics['recall']:.4f} @ FPR {full_metrics['fpr']:.4f}")
    print(f"18-signal baseline: PR-AUC {baseline_pr_auc:.4f} | "
          f"recall {baseline_metrics['recall']:.4f} @ FPR {baseline_metrics['fpr']:.4f}")

    fixtures = test_rows[:args.fixture_rows]
    fixture_path = data / "fixtures" / "gate_smoke.jsonl"
    fixture_path.parent.mkdir(parents=True, exist_ok=True)
    with fixture_path.open("w", encoding="utf-8", newline="\n") as handle:
        for row in fixtures:
            handle.write(json.dumps(row, ensure_ascii=False, separators=(",", ":")) + "\n")

    if args.deploy:
        target = REPO / "shared" / "src" / "commonMain" / "composeResources" / "files"
        target.mkdir(parents=True, exist_ok=True)
        (target / "gate_weights.json").write_bytes((out / "gate_weights.json").read_bytes())
        if use_ngrams:
            (target / "gate_ngrams.bin").write_bytes((out / "gate_ngrams.bin").read_bytes())
        print(f"deployed artifacts to {target}")

    print(f"wrote {out / 'gate_weights.json'}")
    print(f"wrote {out / 'eval_report.json'}")


if __name__ == "__main__":
    sys.exit(main())
