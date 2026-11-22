#!/usr/bin/env python3
"""Evaluate current model and pipeline on real device exports (debug logs & training JSONL).

Loads real-world exports from CashBuddy (debug logs CSV and training JSONL) and replays
every message through the deployed statistical text gate and policy engine.
Produces a side-by-side diagnostic report and summary metrics.
"""

from __future__ import annotations

import argparse
import csv
import json
import math
import struct
import sys
from pathlib import Path

# Add trainingdata directory to path for tokenizer parity
sys.path.insert(0, str(Path(__file__).parent))
import train_gate as tg


def load_gate_model(weights_path: Path, ngrams_path: Path):
    with weights_path.open("r", encoding="utf-8") as f:
        weights = json.load(f)

    base_logit = weights["intercept"] + weights["bias_adjust"]
    scale = weights["ngrams"]["scale"]
    buckets = weights["ngrams"]["buckets"]
    mask = buckets - 1

    table: dict[int, float] = {}
    if ngrams_path.exists():
        data = ngrams_path.read_bytes()
        assert data[:4] == b"CBG1", f"Invalid magic header in {ngrams_path}"
        count = struct.unpack("<I", data[4:8])[0]
        offset = 8
        for _ in range(count):
            bucket, fixed = struct.unpack("<Ih", data[offset : offset + 6])
            table[bucket] = fixed / scale
            offset += 6

    def score_text(text: str) -> tuple[float, float, float]:
        if not text:
            p = 1.0 / (1.0 + math.exp(-base_logit))
            return p, base_logit, 0.0
        b_set = tg.ngram_buckets(text, mask)
        ngram_score = sum(table.get(b, 0.0) for b in b_set)
        logit = base_logit + ngram_score
        # Guard exp overflow
        if logit < -50:
            p = 0.0
        elif logit > 50:
            p = 1.0
        else:
            p = 1.0 / (1.0 + math.exp(-logit))
        return p, logit, ngram_score

    return weights, score_text


def find_default_file(pattern: str) -> Path | None:
    search_dirs = [
        Path.home() / "Downloads",
        Path("."),
        Path("trainingdata"),
    ]
    for d in search_dirs:
        if d.exists():
            matches = list(d.glob(pattern))
            if matches:
                # Return newest
                matches.sort(key=lambda p: p.stat().st_mtime, reverse=True)
                return matches[0]
    return None


def main():
    parser = argparse.ArgumentParser(description="Replay device debug logs through CashBuddy gate model")
    parser.add_argument("--csv", type=Path, default=None, help="Path to cashbuddy_debug_logs_*.csv")
    parser.add_argument("--jsonl", type=Path, default=None, help="Path to cashbuddy_training_*.jsonl")
    parser.add_argument(
        "--weights",
        type=Path,
        default=Path("shared/src/commonMain/composeResources/files/gate_weights.json"),
        help="Path to gate_weights.json",
    )
    parser.add_argument(
        "--ngrams",
        type=Path,
        default=Path("shared/src/commonMain/composeResources/files/gate_ngrams.bin"),
        help="Path to gate_ngrams.bin",
    )
    parser.add_argument(
        "--out",
        type=Path,
        default=Path("trainingdata/model/replay_evaluation_report.csv"),
        help="Output CSV report path",
    )
    args = parser.parse_args()

    csv_path = args.csv or find_default_file("*cashbuddy_debug_logs*.csv")
    jsonl_path = args.jsonl or find_default_file("*cashbuddy_training*.jsonl")

    if not csv_path or not csv_path.exists():
        print(f"Error: Could not find debug logs CSV. Searched Downloads and current dir.", file=sys.stderr)
        sys.exit(1)

    print(f"Loading weights: {args.weights}")
    print(f"Loading ngrams:  {args.ngrams}")
    weights, score_func = load_gate_model(args.weights, args.ngrams)
    print(f"Gate model loaded: version={weights.get('model_version')}, threshold={weights.get('threshold')}")

    print(f"\nProcessing Debug Logs: {csv_path}")
    total_rows = 0
    redacted_count = 0
    non_redacted_count = 0

    # Metrics
    old_outcomes = {"IGNORED": 0, "PENDING_REVIEW": 0, "LOGGED": 0, "MERGED": 0, "OTHER": 0}
    new_actions = {"IGNORE": 0, "REVIEW": 0, "AUTO_LOG": 0}
    discrepancies = []

    out_rows = []

    with open(csv_path, "r", encoding="utf-8", errors="replace") as f:
        reader = csv.DictReader(f)
        for row in reader:
            total_rows += 1
            raw_text = row.get("raw_text", "")
            pkg = row.get("package_name", "")
            old_outcome = row.get("outcome", "") or "IGNORED"
            old_p_str = row.get("p_transaction", "")
            old_p = float(old_p_str) if old_p_str else 0.0

            is_redacted = "[REDACTED" in raw_text
            if is_redacted:
                redacted_count += 1
            else:
                non_redacted_count += 1

            # Score with current model
            if not is_redacted and raw_text:
                new_p, new_logit, ngram_score = score_func(raw_text)
            else:
                # Text is redacted in the old log
                new_p = old_p
                new_logit = 0.0
                ngram_score = 0.0

            # Determine new policy action
            if new_p < 0.30:
                new_action = "IGNORE"
            elif new_p < 0.85:
                new_action = "REVIEW"
            else:
                new_action = "AUTO_LOG"

            old_outcomes[old_outcome if old_outcome in old_outcomes else "OTHER"] += 1
            new_actions[new_action] += 1

            # Check if this was a false positive fixed or changed
            status = "SAME"
            if not is_redacted:
                old_is_tx = old_outcome in ("PENDING_REVIEW", "LOGGED", "MERGED")
                new_is_tx = new_action in ("REVIEW", "AUTO_LOG")

                if old_is_tx and not new_is_tx:
                    status = "FIXED_FALSE_POSITIVE" if "reddit" in pkg.lower() or "news" in raw_text.lower() or "$" in raw_text else "DROPPED"
                    discrepancies.append((pkg, raw_text, old_outcome, old_p, new_action, new_p, status))
                elif not old_is_tx and new_is_tx:
                    status = "PROMOTED_TO_REVIEW"
                    discrepancies.append((pkg, raw_text, old_outcome, old_p, new_action, new_p, status))

            out_rows.append({
                "timestamp": row.get("timestamp", ""),
                "package_name": pkg,
                "is_redacted": is_redacted,
                "old_outcome": old_outcome,
                "old_p": f"{old_p:.4f}",
                "new_action": new_action,
                "new_p": f"{new_p:.4f}",
                "status": status,
                "raw_text": raw_text[:120].replace("\n", " "),
            })

    # Write output CSV
    args.out.parent.mkdir(parents=True, exist_ok=True)
    with open(args.out, "w", encoding="utf-8", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=list(out_rows[0].keys()))
        writer.writeheader()
        writer.writerows(out_rows)

    print("\n" + "=" * 60)
    print("DEVICE REPLAY EVALUATION REPORT")
    print("=" * 60)
    print(f"Total Rows Processed:       {total_rows}")
    print(f"  - Non-redacted Rows:      {non_redacted_count} ({non_redacted_count/total_rows*100:.1f}%)")
    print(f"  - Redacted Rows (Noise):  {redacted_count} ({redacted_count/total_rows*100:.1f}%)")
    print("-" * 60)
    print("Old App Recorded Outcomes:")
    for k, v in old_outcomes.items():
        print(f"  {k:15s}: {v:5d} ({v/total_rows*100:.2f}%)")
    print("-" * 60)
    print("New Statistical Gate Inverted Actions:")
    for k, v in new_actions.items():
        print(f"  {k:15s}: {v:5d} ({v/total_rows*100:.2f}%)")
    print("-" * 60)
    print(f"Total Non-redacted State Changes: {len(discrepancies)}")

    if discrepancies:
        print("\nSample Notable Changes (Old vs New):")
        for pkg, text, old_out, old_p, new_act, new_p, status in discrepancies[:10]:
            clean_text = text[:80].replace("\n", " ")
            print(f"[{status}] pkg={pkg}")
            print(f"  Text: {clean_text}")
            print(f"  Old: {old_out} (p={old_p:.4f}) -> New: {new_act} (p={new_p:.4f})\n")

    # If JSONL exists, evaluate against labeled ground truth
    if jsonl_path and jsonl_path.exists():
        print("=" * 60)
        print(f"Evaluating Against User Corrections JSONL ({jsonl_path.name}):")
        print("=" * 60)
        user_corrections = []
        with open(jsonl_path, "r", encoding="utf-8") as f:
            for line in f:
                if not line.strip():
                    continue
                item = json.loads(line)
                text = item.get("raw_text") or item.get("text") or ""
                if not text:
                    continue
                if item.get("type") == "user_correction":
                    user_corrections.append((item, text))

        tx_correct = 0
        tx_autolog = 0
        for item, text in user_corrections:
            p, _, _ = score_func(text)
            if p >= 0.30:
                tx_correct += 1
            if p >= 0.65:
                tx_autolog += 1

        tx_total = len(user_corrections)
        print(f"Real Confirmed Transactions (user_correction): {tx_total}")
        if tx_total > 0:
            print(f"  - Successfully Qualified (p >= 0.30): {tx_correct} ({tx_correct/tx_total*100:.1f}%)")
            print(f"  - Strong Confidence (p >= 0.65):      {tx_autolog} ({tx_autolog/tx_total*100:.1f}%)")
            print(f"  - Dropped Below Gate (p < 0.30):      {tx_total - tx_correct} ({(tx_total - tx_correct)/tx_total*100:.1f}%)")

    print("\nReport saved to: " + str(args.out.resolve()))


if __name__ == "__main__":
    main()
