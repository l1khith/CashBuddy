# CashBuddy Synthetic Gate Dataset

Binary training data for the **transaction-vs-noise gate**:

- **Positive** = a real bank / UPI / card / ATM message that should create a transaction.
- **Negative** = a false flag or an ordinary notification that must not create a transaction.

This is a **gate dataset**, not a category model and not a substitute for real labels. It exists to teach format coverage, amount-role selection, and hard-negative separation (same bank, different content).

Generated deterministically from `generate_dataset.py` with `seed=42`. No real phone numbers, account numbers, UTRs, names, or messages are used.

> Training and shipping are specified in
> [`docs/TRAINING_PIPELINE.md`](docs/TRAINING_PIPELINE.md) and
> [`docs/RUNTIME_INTEGRATION.md`](docs/RUNTIME_INTEGRATION.md).
> This file is the dataset card; those documents cover the model.

---

## Files

| File | Rows | Purpose |
|---|---:|---|
| `positive_bank_transactions.jsonl` | 20,000 | Real transaction messages, all sources |
| `negative_false_flags.jsonl` | 20,000 | OTPs, promos, chat noise, failed txns, etc. |
| `combined.jsonl` | 40,000 | Both classes with `label` / `is_transaction` |
| `splits/train.jsonl` | 28,000 | 14,000 positive + 14,000 negative |
| `splits/val.jsonl` | 6,000 | 3,000 positive + 3,000 negative |
| `splits/test.jsonl` | 6,000 | 3,000 positive + 3,000 negative |
| `manifest.json` | — | Seed, counts, hashes, template versions |
| `validate_report.json` | — | Result of the last validation run |

All splits contain **all 15 app categories** and **all 16 negative types**. There are **40,000 unique texts**; no text is shared between splits.

Split is by **template and merchant**, not by row:

- 82 positive templates and 108 negative templates, assigned to one split each.
- 7 merchants per category in train, 2 in val, 3 in test.
- People used for P2P transfers are split the same way.
- Train and test share **no merchant ID** and **no template ID**.
- About 7% of positives have a duplicate companion (same amount/merchant/time, different source) linked by `event_group_id` / `duplicate_of`.

This prevents the usual fake-accuracy result from duplicate templates leaking across train and test.

---

## Label dictionary

### Positive rows

| Field | Value |
|---|---|
| `label` | `TRANSACTION` |
| `is_transaction` | `true` |
| `is_financial_event` | `true` |
| `amount` | numeric INR amount |
| `amount_text` | exact amount string as it appears in `text` |
| `txn_type` | `DEBIT` or `CREDIT` |
| `status` | `SUCCESS` |
| `category` | one of the 15 app categories |
| `negative_type` | `null` |

Income categories (`Salary`, `Refund`, `Gift`, `Investment Returns`) are always `CREDIT`. All other categories are `DEBIT`.

### Negative rows

| Field | Value |
|---|---|
| `label` | `NOT_TRANSACTION` |
| `is_transaction` | `false` |
| `amount` | `null` |
| `amount_text` | `null` |
| `distractor_amount` | amount present in the text, if any |
| `category` | `null` |
| `negative_type` | see table below |
| `status` | `FAILED`, `PENDING`, `REVERSED`, `REQUESTED`, `MANDATE`, or `null` |

`FAILED_TXN`, `PENDING_TXN`, `REVERSED`, `COLLECT_REQUEST`, and `MANDATE` have `is_financial_event=true` because they describe money-related events, but `is_transaction=false` because no spend should be recorded. OTPs and promos may contain an amount but are never transactions.

### Negative types

| Type | Count | Examples |
|---|---:|---|
| `PROMO` | 3,000 | cashback, discount, “up to ₹500” |
| `BANK_PROMO` | 1,200 | FD/loan offers, KYC reminders, UPI Lite |
| `OTP` | 2,000 | OTP with a transaction amount in the same message |
| `BALANCE_ONLY` | 1,400 | “Avl Bal ₹18,420”, credit-card limit |
| `STATEMENT` | 600 | statement ready, total due, minimum due |
| `CREDIT_SCORE` | 400 | score alerts from bureaus |
| `CHAT_SUMMARY` | 2,800 | “4 messages from 3 chats bigbasket: ₹20 added” |
| `SOCIAL` | 2,300 | Instagram/Reddit/YouTube/X notifications |
| `NEWS_JOB` | 1,200 | Gmail digests, LinkedIn/Naukri job alerts |
| `GENERIC` | 1,400 | music, alarm, game, update notifications |
| `FAILED_TXN` | 1,400 | failed payment with amount and reference |
| `PENDING_TXN` | 700 | processing / on-hold payment |
| `REVERSED` | 400 | reversal / refund-to-source messages |
| `COLLECT_REQUEST` | 500 | “X requested ₹500, approve to pay” |
| `MANDATE` | 400 | autopay/e-mandate registration |
| `REWARD` | 300 | points/coins, no money movement |

The negative class deliberately reuses the **same banks, UPI apps, and senders** as the positive class. The model cannot learn “HDFC = transaction” or “WhatsApp = noise”. WhatsApp-forwarded bank alerts are included as positives. Chat summaries that contain financial-looking text are included as negatives.

---

## Schema

Every row is one JSON object per line:

```json
{
  "id": "te-000123",
  "schema_version": "1.0.0",
  "dataset_version": "1.0.0",
  "split": "test",
  "label": "TRANSACTION",
  "is_transaction": true,
  "is_financial_event": true,
  "negative_type": null,
  "source_type": "BANK_SMS",
  "bank": "HDFC Bank",
  "package_name": "com.snapwork.hdfc",
  "sender_id": "HDFCBK",
  "title": null,
  "text": "HDFC Bank: Rs.1,250 debited from A/c XX4821 on 18-10-26 at 14:32 to SWIGGY. Avl Bal Rs.18,420. Ref 668812345678.",
  "amount": 1250.0,
  "amount_text": "1,250",
  "distractor_amount": null,
  "currency": "INR",
  "txn_type": "DEBIT",
  "status": "SUCCESS",
  "merchant_raw": "SWIGGY",
  "merchant_canonical": "Swiggy",
  "merchant_id": "m_swiggy",
  "merchant_type": "MERCHANT",
  "category": "Food & Dining",
  "account_last4": "4821",
  "balance": 18420.0,
  "available_limit": null,
  "utr": "668812345678",
  "payment_method": "UPI",
  "is_promo": false,
  "difficulty": "easy",
  "template_id": "bsms_dr_01",
  "event_group_id": null,
  "duplicate_of": null,
  "timestamp_ms": 1792400000000
}
```

Negative example:

```json
{
  "id": "tr-000456",
  "label": "NOT_TRANSACTION",
  "is_transaction": false,
  "is_financial_event": false,
  "negative_type": "CHAT_SUMMARY",
  "source_type": "MESSAGING",
  "package_name": "com.whatsapp",
  "bank": null,
  "text": "4 messages from 3 chats Apartment Association: Your payment is pending",
  "amount": null,
  "amount_text": null,
  "distractor_amount": null,
  "txn_type": null,
  "status": null,
  "merchant_raw": null,
  "category": null,
  "template_id": "chat_01"
}
```

Amounts are always stored in rupees (not paise). `amount_text` is the exact string to find in `text`; this lets an extractor learn candidate spans rather than regress a number.

---

## Regenerate

```powershell
python trainingdata/generate_dataset.py --positives 20000 --negatives 20000 --seed 42
python trainingdata/validate_dataset.py --data trainingdata
```

The generator is deterministic for a given seed. Scale up without changing the code:

```powershell
python trainingdata/generate_dataset.py --positives 50000 --negatives 50000 --seed 7 --out trainingdata
```

The validator exits non-zero on failure and writes `validate_report.json`.

### What validation covers

- exact counts and file hashes
- positive amount is present verbatim and parses to the numeric label
- negative rows have no transaction amount or category
- every positive is `SUCCESS`; income categories are `CREDIT`
- all categories and negative types are present in every split
- no text, template, or merchant leaks between train and test
- duplicate links point to a real main row with the same group/merchant
- amount buckets cover `<₹100` through `≥₹1 lakh`
- source types are from the allowed set

---

## How to train with this

1. **Gate only.** Train `is_transaction` with `text` (+ `title`, `source_type`, `package_name`) as input.
2. **Amount-role extraction.** Use `amount_text` and the distractor amounts (`balance`, `available_limit`, `min`, `utr`) as a candidate-selection task. Do not train a single regression to output the amount.
3. **Type and merchant.** `txn_type`, `merchant_canonical`, and `category` are auxiliary labels. They are useful for a second-stage model, but this dataset is not large enough to train a production category model on its own.
4. **Class balance.** 50/50 by design. If you want realistic deployment priors, reweight at training time, not by deleting negatives.
5. **Evaluation.** Report PR-AUC, recall at a fixed false-positive rate, ECE, and per-slice results:
   - bank SMS / bank app / UPI / card / ATM / screenshot / forwarded
   - chat / social / email / generic
   - OTP / promo / failed / pending / collect / mandate
   - English vs Hinglish vs OCR
6. **Do not evaluate on synthetic only.** Fine-tune on the real corrections/ledger you export, and keep a real holdout. The number that matters is real-message precision at the review-inbox threshold.

### Recommended model shape

A pure-Kotlin logistic regression over structural features plus hashed token/char n-grams is enough for a first calibrated gate:

- group-summary detector (`N messages from M chats`)
- chat-preview detector (`Sent a sticker`, `Sent you a reel`, `~Name:`)
- amount-in-sentence-with-payment-verb feature
- OTP/promo/balance/statement cues
- UTR/account/phone/date shape features
- merchant validity features
- `source_type` only as a weak prior, never as a hard allowlist

Keep the existing Naive Bayes log-odds model and fit its weights on this data first. That is cheaper and more explainable than a neural model.

---

## Important limitations

- **Synthetic.** Templates are realistic but not a substitute for real bank traffic. Do not ship a model trained only on this.
- **No negative ground truth from production.** These negatives are plausible, not observed. Add real “this was not a transaction” labels from the review inbox.
- **Templates repeat structure.** Uniqueness is enforced per row, but the underlying patterns are finite. Split by template prevents leakage, but it does not create new formats.
- **Merchant/category labels are clean by construction.** Real merchant strings are messier (`8879635472`, `Upiapp`, truncated fragments). This dataset will not teach merchant canonicalization; that needs real data.
- **Hinglish/OCR are simulated.** They cover common patterns, not every script or OCR failure.
- **No PII.** All account numbers, UTRs, phone numbers, and people are fabricated.

Use this to bootstrap the gate and the amount-role extractor, then replace/augment with consented real exports.
