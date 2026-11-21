# CashBuddy Real-Device Dataset & Evaluation Benchmark

## 1. Overview & Purpose

This global benchmark establishes an automated, repeatable evaluation harness using **real exported device notifications** (`cashbuddy_debug_logs_*.csv` and `cashbuddy_training_*.jsonl`).

Instead of guessing or manually testing single notifications on physical devices, this harness replays **all ~5,000 real notifications** through the statistical text gate and policy engine to measure:
1. **Noise Rejection Rate:** Are non-financial notifications (social media, news, chats, game alerts) correctly dropped?
2. **Transaction Recall:** Are real UPI, bank SMS, and merchant transactions successfully qualified ($p \ge 0.30$)?
3. **Model & Weight Calibration:** Identifying exact token biases (e.g. security disclaimers like *"Never share OTP"* causing false negatives).

---

## 2. Dataset Schema & Structure

### A. Real Device Debug Logs (`cashbuddy_debug_logs_*.csv`)
* **Total Records:** 4,998 real notifications captured on Android device.
* **Columns:**
  * `timestamp` (epoch millis)
  * `source_type` (`NOTIFICATION`, `SMS`, `UPI_RECEIPT`, `SCREENSHOT`)
  * `package_name` (e.g. `com.google.android.apps.nbu.paisa.user`, `in.org.npci.upiapp`, `com.reddit.frontpage`)
  * `policy_action` (Action taken at time of capture: `IGNORE`, `LOG_AND_FLAG`, `AUTO_LOG`)
  * `p_transaction` (Probability score assigned by phone at runtime)
  * `raw_text` (Raw notification title + body)
  * `outcome` (`IGNORED`, `PENDING_REVIEW`, `LOGGED`, `MERGED`)

### B. User Corrections & Ground Truth (`cashbuddy_training_*.jsonl`)
* **Total Records:** 440 confirmed real transactions.
* **Fields:**
  * `raw_text`: Complete transaction alert from Indian banks and payment apps.
  * `merchant`: Confirmed merchant string (e.g. `Chira`, `Blinkit`, `BMRCL SILK INSTITUTE`).
  * `label`: Confirmed category (e.g. `Food & Dining`, `Education`, `Transit`).
  * `source_app`: Origin package.

---

## 3. How to Run the Benchmark

Run the evaluation script from the repository root:

```bash
python trainingdata/eval_real_device_logs.py
```

### Options:
```bash
python trainingdata/eval_real_device_logs.py \
  --csv C:/Users/ailik/Downloads/cashbuddy_debug_logs_1795019968972.csv \
  --jsonl C:/Users/ailik/Downloads/cashbuddy_training_1795020276486.jsonl \
  --weights shared/src/commonMain/composeResources/files/gate_weights.json \
  --ngrams shared/src/commonMain/composeResources/files/gate_ngrams.bin \
  --out trainingdata/model/replay_evaluation_report.csv
```

---

## 4. Benchmark Findings & Insights

### Finding A: Massive Noise Elimination (False Positives Fixed)
In the old app, non-financial promotional alerts were wrongly entering `PENDING_REVIEW` due to currency regexes matching plan prices or discounts:
* `in.org.npci.upiapp` *"Get the Retro Watch at only ₹299"* $\rightarrow$ **Old:** `PENDING_REVIEW` (p=0.31) $\rightarrow$ **New Model:** `IGNORE` (p=0.0000)
* `com.jio.myjio` *"Play 1000+ Games, plans starting at ₹48/-"* $\rightarrow$ **Old:** `PENDING_REVIEW` (p=0.31) $\rightarrow$ **New Model:** `IGNORE` (p=0.0000)
* `in.org.npci.upiapp` *"Pay any bill of ₹100+ on BHIM"* $\rightarrow$ **Old:** `PENDING_REVIEW` (p=0.76) $\rightarrow$ **New Model:** `IGNORE` (p=0.0000)
* `com.reddit.frontpage` *"Bitcoin selloff accelerating. Is $60K next?"* $\rightarrow$ **Old:** `PENDING_REVIEW` $\rightarrow$ **New Model:** `IGNORE` (p=0.0000)

### Finding B: Security Disclaimer Bias (The OTP Token Collision)
* **The Root Cause:** Real Indian bank SMS (Union Bank, SBI, HDFC) append a mandatory security warning at the end of debit and credit alerts:
  > *"Never Share OTP/PIN/CVV - Union Bank of India"*
* In synthetic data, `"otp"` was exclusively present in the negative class. This caused tokens like `w:otp`, `c:otp`, `w:never`, and `w:share` to receive high negative penalties (`-1.60` to `-1.90`), dragging real debit/credit alerts with disclaimers down to $p = 0.0000$.
* **The Fix:** Ensure positive training templates include standard bank security disclaimers (*"Never share OTP/PIN/CVV"*, *"Do not disclose OTP"*) so the model learns that a disclaimer at the end of an alert does not cancel out a debit/credit event.

---

## 5. Continuous Validation Workflow

Before deploying any new `gate_weights.json` or `gate_ngrams.bin` to `shared/src/commonMain/composeResources/files/`:
1. Run `python trainingdata/eval_real_device_logs.py`
2. Verify:
   * Noise rejection on `cashbuddy_debug_logs` $\ge 90\%$.
   * Recall on `cashbuddy_training` confirmed transactions $\ge 95\%$.
   * Zero promotional false positives in `replay_evaluation_report.csv`.
