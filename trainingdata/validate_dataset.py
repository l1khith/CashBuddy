"""Validate the generated CashBuddy synthetic dataset."""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import sys
from collections import Counter, defaultdict
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))

from vocab import ALL_CATEGORIES, INCOME_CATEGORIES  # noqa: E402

NEG_TYPES = {
    "PROMO", "BANK_PROMO", "OTP", "BALANCE_ONLY", "STATEMENT", "CREDIT_SCORE",
    "CHAT_SUMMARY", "SOCIAL", "NEWS_JOB", "GENERIC", "FAILED_TXN",
    "PENDING_TXN", "REVERSED", "COLLECT_REQUEST", "MANDATE", "REWARD",
}
SOURCE_TYPES = {
    "BANK_SMS", "BANK_APP", "CARD_ALERT", "ATM", "UPI_APP", "WALLET",
    "SCREENSHOT", "MESSAGING_FORWARD", "MESSAGING", "APP_NOTIFICATION",
    "EMAIL",
}


def load(path: Path) -> list[dict]:
    rows = []
    with path.open(encoding="utf-8") as fh:
        for line in fh:
            line = line.strip()
            if line:
                rows.append(json.loads(line))
    return rows


def number(text: str) -> float:
    return float(text.replace(",", "").replace("/-", "").strip())


def validate(data: Path) -> dict:
    checks: list[dict] = []

    def check(name: str, ok: bool, detail: str = "") -> None:
        checks.append({"check": name, "ok": bool(ok), "detail": detail})

    manifest_path = data / "manifest.json"
    check("manifest_exists", manifest_path.exists(), str(manifest_path))
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))

    combined = load(data / "combined.jsonl")
    positives = load(data / "positive_bank_transactions.jsonl")
    negatives = load(data / "negative_false_flags.jsonl")
    splits = {s: load(data / "splits" / f"{s}.jsonl") for s in ("train", "val", "test")}

    check("combined_count", len(combined) == manifest["counts"]["total"],
          f"{len(combined)} vs {manifest['counts']['total']}")
    check("positive_count", len(positives) == manifest["counts"]["positives"],
          f"{len(positives)} vs {manifest['counts']['positives']}")
    check("negative_count", len(negatives) == manifest["counts"]["negatives"],
          f"{len(negatives)} vs {manifest['counts']['negatives']}")
    check("split_count_sum", sum(len(v) for v in splits.values()) == len(combined),
          f"{sum(len(v) for v in splits.values())} vs {len(combined)}")

    all_ids = [r["id"] for r in combined]
    check("ids_unique", len(all_ids) == len(set(all_ids)),
          f"{len(all_ids) - len(set(all_ids))} duplicates")

    pos = [r for r in combined if r["is_transaction"]]
    neg = [r for r in combined if not r["is_transaction"]]
    check("label_matches_flag",
          all((r["label"] == "TRANSACTION") == r["is_transaction"] for r in combined))

    bad_amount = [
        r for r in pos
        if r["amount"] is None or not r["amount_text"]
        or r["amount_text"] not in r["text"]
        or abs(number(r["amount_text"]) - float(r["amount"])) > 0.011
    ]
    check("positive_amount_verbatim", not bad_amount,
          f"{len(bad_amount)} bad rows; e.g. {bad_amount[:2]}")

    check("positive_amount_positive", all(float(r["amount"]) > 0 for r in pos))
    check("positive_status", all(r["status"] == "SUCCESS" for r in pos))
    check("positive_txn_type",
          all(r["txn_type"] in ("DEBIT", "CREDIT") for r in pos))
    check("positive_category_valid",
          all(r["category"] in ALL_CATEGORIES for r in pos),
          str(Counter(r["category"] for r in pos)))
    check("income_category_credit",
          all((r["category"] in INCOME_CATEGORIES) == (r["txn_type"] == "CREDIT")
              for r in pos))

    check("negative_amount_null", all(r["amount"] is None for r in neg))
    check("negative_amount_text_null", all(r["amount_text"] is None for r in neg))
    check("negative_category_null", all(r["category"] is None for r in neg))
    check("negative_type_valid",
          all(r["negative_type"] in NEG_TYPES for r in neg),
          str(Counter(r["negative_type"] for r in neg)))
    check("negative_financial_present",
          all(r["is_financial_event"] for r in neg
              if r["negative_type"] in
              {"FAILED_TXN", "PENDING_TXN", "REVERSED",
               "COLLECT_REQUEST", "MANDATE"}))

    texts = [r["text"] for r in combined]
    check("texts_non_empty", all(t.strip() for t in texts))
    split_texts = {s: {r["text"] for r in rows} for s, rows in splits.items()}
    cross = ((split_texts["train"] & split_texts["val"])
             | (split_texts["train"] & split_texts["test"])
             | (split_texts["val"] & split_texts["test"]))
    check("no_text_leak_across_splits", not cross,
          f"{len(cross)} shared texts")

    train_merchants = {r["merchant_id"] for r in splits["train"] if r["merchant_id"]}
    test_merchants = {r["merchant_id"] for r in splits["test"] if r["merchant_id"]}
    val_merchants = {r["merchant_id"] for r in splits["val"] if r["merchant_id"]}
    check("merchant_train_test_disjoint", not (train_merchants & test_merchants),
          f"{len(train_merchants & test_merchants)} collisions")
    check("merchant_val_test_disjoint", not (val_merchants & test_merchants),
          f"{len(val_merchants & test_merchants)} collisions")

    train_templates = {r["template_id"] for r in splits["train"]}
    test_templates = {r["template_id"] for r in splits["test"]}
    val_templates = {r["template_id"] for r in splits["val"]}
    check("template_train_test_disjoint", not (train_templates & test_templates),
          f"{len(train_templates & test_templates)} collisions")
    check("template_val_test_disjoint", not (val_templates & test_templates),
          f"{len(val_templates & test_templates)} collisions")

    for split, rows in splits.items():
        cats = {r["category"] for r in rows if r["category"]}
        check(f"category_coverage_{split}", cats == set(ALL_CATEGORIES),
              f"missing={sorted(set(ALL_CATEGORIES) - cats)}")
        ntypes = {r["negative_type"] for r in rows if r["negative_type"]}
        check(f"negative_type_coverage_{split}", ntypes == NEG_TYPES,
              f"missing={sorted(NEG_TYPES - ntypes)}")

    check("source_type_valid",
          all(r["source_type"] in SOURCE_TYPES for r in combined),
          str(Counter(r["source_type"] for r in combined)))
    check("currency_inr", all(r["currency"] == "INR" for r in combined))

    id_to_row = {r["id"]: r for r in combined}
    bad_dups = []
    for r in combined:
        if r["duplicate_of"]:
            main = id_to_row.get(r["duplicate_of"])
            if (main is None or main["event_group_id"] != r["event_group_id"]
                    or main["split"] != r["split"]
                    or main["merchant_id"] != r["merchant_id"]):
                bad_dups.append(r["id"])
    check("duplicate_links_valid", not bad_dups, f"{len(bad_dups)} bad links")

    dup_groups = defaultdict(list)
    for r in combined:
        if r["event_group_id"]:
            dup_groups[r["event_group_id"]].append(r)
    check("duplicate_groups_sane",
          all(len(v) >= 2 for v in dup_groups.values()),
          f"{sum(1 for v in dup_groups.values() if len(v) < 2)} singleton groups")

    amounts = [float(r["amount"]) for r in pos]
    buckets = Counter()
    for a in amounts:
        if a < 100:
            buckets["<100"] += 1
        elif a < 1000:
            buckets["100-999"] += 1
        elif a < 10000:
            buckets["1k-9.9k"] += 1
        elif a < 100000:
            buckets["10k-99.9k"] += 1
        else:
            buckets["100k+"] += 1
    check("amount_bucket_coverage", set(buckets) ==
          {"<100", "100-999", "1k-9.9k", "10k-99.9k", "100k+"}, str(buckets))
    check("credit_share", sum(1 for r in pos if r["txn_type"] == "CREDIT") > 0,
          str(Counter(r["txn_type"] for r in pos)))

    file_hashes_ok = True
    hash_detail = []
    for name, info in manifest["files"].items():
        path = data / name
        if not path.exists():
            file_hashes_ok = False
            hash_detail.append(f"missing {name}")
            continue
        digest = hashlib.sha256(path.read_bytes()).hexdigest()
        if digest != info["sha256"]:
            file_hashes_ok = False
            hash_detail.append(f"hash mismatch {name}")
    check("file_hashes", file_hashes_ok, "; ".join(hash_detail))

    failed = [c for c in checks if not c["ok"]]
    report = {
        "passed": not failed,
        "checks": checks,
        "stats": {
            "rows": len(combined),
            "positives": len(pos),
            "negatives": len(neg),
            "unique_texts": len(set(texts)),
            "by_split": {s: len(v) for s, v in splits.items()},
            "amount_buckets": dict(buckets),
            "categories": dict(sorted(Counter(r["category"] for r in pos).items())),
            "negative_types": dict(sorted(Counter(r["negative_type"] for r in neg).items())),
            "sources": dict(sorted(Counter(r["source_type"] for r in combined).items())),
        },
    }
    (data / "validate_report.json").write_text(
        json.dumps(report, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    return report


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--data", type=Path, default=HERE)
    args = parser.parse_args()
    report = validate(args.data)
    for check in report["checks"]:
        mark = "PASS" if check["ok"] else "FAIL"
        line = f"[{mark}] {check['check']}"
        if not check["ok"] and check["detail"]:
            line += f" :: {check['detail']}"
        print(line)
    print(json.dumps(report["stats"], indent=2))
    raise SystemExit(0 if report["passed"] else 1)


if __name__ == "__main__":
    main()
