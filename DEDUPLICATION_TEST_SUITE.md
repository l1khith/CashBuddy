# CashBuddy Deduplication Test Suite v1.0

**File:** `DEDUPLICATION_TEST_SUITE.md`  
**Purpose:** Ensure every real-world payment that arrives via multiple intake channels (notification, screenshot OCR, manual entry) results in exactly **one** ledger row, while legitimate repeat transactions are never merged.  
**Total Test Cases:** 345  
**Base time reference:** `T0 = 2026-10-14 14:00:00 IST`

---

## Deduplication Rule Under Test

Two transactions are duplicates **iff ALL** of the following hold:

| Condition | Match Rule |
|---|---|
| **Amount** | Exact match in paise after normalization |
| **Direction** | Same (DEBIT vs CREDIT) |
| **Timestamp** | Within ±10 minutes (bidirectional) |
| **Merchant** | Levenshtein similarity ≥ 0.70 OR alias map match OR identical RRN |

**Outcomes:** `INSERT_NEW` | `DEDUP_DROP` | `DEDUP_MERGE` | `FLAG_REVIEW` | `IGNORE`

**Source priority on merge:** Screenshot > Notification > Manual (screenshot wins merchant, notification wins amount)

---

## Table of Contents

- [Category A: Notification × Screenshot Core (T001–T030)](#category-a)
- [Category B: Notification × Notification (T031–T050)](#category-b)
- [Category C: Screenshot × Screenshot (T051–T065)](#category-c)
- [Category D: OCR Noise & Misread (T066–T085)](#category-d)
- [Category E: Legitimate Repeats — Must NOT Dedup (T086–T110)](#category-e)
- [Category F: Refunds, Reversals & Failed Payments (T111–T130)](#category-f)
- [Category G: Merchant Name Variations (T131–T150)](#category-g)
- [Category H: Timing Edge Cases (T151–T165)](#category-h)
- [Category I: Amount Parsing & Formats (T166–T180)](#category-i)
- [Category J: Concurrency & Race Conditions (T181–T190)](#category-j)
- [Category K: State Transitions (T191–T200)](#category-k)
- [Category L: Manual Entry Interaction (T201–T210)](#category-l)
- [Category M: Multi-Account / Multi-Bank (T211–T220)](#category-m)
- [Category N: Budget & Analytics Impact (T221–T230)](#category-n)
- [Category O: Data Integrity & Idempotency (T231–T245)](#category-o)
- [Category P: UPI App Wording Variants (T246–T270)](#category-p)
- [Category Q: Non-Transactional — Must Ignore (T271–T285)](#category-q)
- [Category R: Robustness & Fuzz (T286–T305)](#category-r)
- [Category S: Security, PII & Privacy (T306–T315)](#category-s)
- [Category T: Performance & Scale (T316–T320)](#category-t)
- [Category U: Real-World End-to-End (T321–T335)](#category-u)
- [Category V: Category Classification Interaction (T336–T345)](#category-v)

---

<a name="category-a"></a>
## Category A — Notification × Screenshot Core (T001–T030)

| ID | Scenario | Input 1 | Input 2 | Expected |
|----|----------|---------|---------|----------|
| T001 | Zomato ₹50 notif → same screenshot 30s later | NOTIF: ₹50 Zomato @ T0 | SHOT: ₹50 Zomato @ T0+30s | DEDUP_DROP |
| T002 | Screenshot first, notif 30s later | SHOT: ₹50 Zomato @ T0 | NOTIF: ₹50 Zomato @ T0+30s | DEDUP_MERGE |
| T003 | Swiggy ₹250 → screenshot 5 min later | NOTIF: ₹250 Swiggy @ T0 | SHOT: ₹250 Swiggy @ T0+5m | DEDUP_DROP |
| T004 | Same pair at 9m59s apart | NOTIF: ₹50 Zomato @ T0 | SHOT: ₹50 Zomato @ T0+9m59s | DEDUP_DROP |
| T005 | Same pair at 10m01s apart | NOTIF: ₹50 Zomato @ T0 | SHOT: ₹50 Zomato @ T0+10m01s | INSERT_NEW |
| T006 | PhonePe ₹1200 Amazon → screenshot 2 min later | NOTIF: ₹1200 Amazon @ T0 | SHOT: ₹1200 Amazon @ T0+2m | DEDUP_DROP |
| T007 | Two screenshots of same GPay receipt | SHOT: ₹499 GPay @ T0 | SHOT: ₹499 GPay @ T0+5s | DEDUP_DROP |
| T008 | Uber ₹180 + two screenshots | NOTIF: ₹180 Uber @ T0 | SHOT × 2 @ T0+2m, T0+3m | 1 INSERT + 2 DEDUP_DROP |
| T009 | Notification only | NOTIF: ₹300 DMart @ T0 | — | INSERT_NEW |
| T010 | Screenshot only | — | SHOT: ₹300 DMart @ T0 | INSERT_NEW |
| T011 | Notif arrives 12 min after screenshot | SHOT: ₹600 BigBasket @ T0 | NOTIF: ₹600 BigBasket @ T0+12m | 2 INSERT + FLAG_REVIEW |
| T012 | Screenshot has extra whitespace/newlines | NOTIF: ₹50 Zomato @ T0 | SHOT: `₹50  Zomato\n` @ T0+1m | DEDUP_DROP |
| T013 | "Zomato Ltd" vs "Zomato" | NOTIF: ₹50 Zomato Ltd @ T0 | SHOT: ₹50 Zomato @ T0+1m | DEDUP_DROP |
| T014 | Notif has UPI VPA `zomato@ybl` | NOTIF: ₹50 zomato@ybl @ T0 | SHOT: ₹50 Zomato @ T0+1m | DEDUP_DROP |
| T015 | ₹50.00 vs ₹50 | NOTIF: ₹50.00 @ T0 | SHOT: ₹50 @ T0+30s | DEDUP_DROP |
| T016 | "Rs.50" vs "₹50.00" | NOTIF: Rs.50 @ T0 | SHOT: ₹50.00 @ T0+30s | DEDUP_DROP |
| T017 | Screenshot is partial crop, merchant unreadable | NOTIF: ₹150 Swiggy @ T0 | SHOT: `₹150 ???` @ T0+1m | DEDUP_DROP |
| T018 | Notif merchant unreadable, screenshot clear | NOTIF: `₹150 ???` @ T0 | SHOT: `₹150 Swiggy` @ T0+1m | DEDUP_MERGE |
| T019 | Both merchant unreadable, amount+time match | NOTIF: `₹150 ???` @ T0 | SHOT: `₹150 ???` @ T0+1m | DEDUP_DROP |
| T020 | Notification ₹50, screenshot ₹500 | NOTIF: ₹50 Zomato @ T0 | SHOT: ₹500 Zomato @ T0+1m | 2 INSERT_NEW |
| T021 | Zomato ₹50 notif → ₹50 screenshot 1 min later | NOTIF: ₹50 @ T0 | SHOT: ₹50 @ T0+1m | DEDUP_DROP |
| T022 | Blinkit ₹350 → screenshot 3 min later | NOTIF: ₹350 Blinkit @ T0 | SHOT: ₹350 Blinkit @ T0+3m | DEDUP_DROP |
| T023 | Zepto ₹150 → screenshot at edge of window | NOTIF: ₹150 Zepto @ T0 | SHOT: ₹150 Zepto @ T0+9m59s | DEDUP_DROP |
| T024 | Notif from HDFC → screenshot from GPay | NOTIF: ₹500 HDFC @ T0 | SHOT: ₹500 GPay @ T0+1m | DEDUP_DROP |
| T025 | Notif from SBI → screenshot from PhonePe | NOTIF: ₹750 SBI @ T0 | SHOT: ₹750 PhonePe @ T0+45s | DEDUP_DROP |
| T026 | Credit notif → credit screenshot | NOTIF: +₹500 @ T0 | SHOT: +₹500 @ T0+1m | DEDUP_DROP |
| T027 | Debit notif → credit screenshot (mismatch direction) | NOTIF: -₹500 @ T0 | SHOT: +₹500 @ T0+1m | 2 INSERT_NEW |
| T028 | Screenshot cropped to only show amount | NOTIF: ₹50 Zomato @ T0 | SHOT: ₹50 @ T0+2m | DEDUP_DROP |
| T029 | Screenshot with visible UPI ref | NOTIF: ₹50 @ T0 | SHOT: ₹50 ref123 @ T0+1m | DEDUP_DROP |
| T030 | Notif and screenshot with matching RRN | NOTIF: ₹50 RRN123 @ T0 | SHOT: ₹50 RRN123 @ T0+5m | DEDUP_DROP |

---

<a name="category-b"></a>
## Category B — Notification × Notification (T031–T050)

| ID | Scenario | Input 1 | Input 2 | Expected |
|----|----------|---------|---------|----------|
| T031 | HDFC + GPay same ₹500 debit | NOTIF: HDFC ₹500 @ T0 | NOTIF: GPay ₹500 @ T0+5s | DEDUP_DROP |
| T032 | Bank "pending" → "success" 30s later | NOTIF: pending ₹500 @ T0 | NOTIF: success ₹500 @ T0+30s | DEDUP_MERGE |
| T033 | Bank + UPI app same txn | NOTIF: bank ₹250 @ T0 | NOTIF: PhonePe ₹250 @ T0+3s | DEDUP_DROP |
| T034 | Same amount+merchant 15 min apart | NOTIF: ₹100 Starbucks @ T0 | NOTIF: ₹100 Starbucks @ T0+15m | 2 INSERT_NEW |
| T035 | Same amount+merchant 3 min apart | NOTIF: ₹100 Starbucks @ T0 | NOTIF: ₹100 Starbucks @ T0+3m | FLAG_REVIEW |
| T036 | Same bank, two payments same amount, diff merchants | NOTIF: ₹500 @ T0 | NOTIF: ₹500 @ T0+1m | 2 INSERT_NEW |
| T037 | Identical notification retry | NOTIF: same hash @ T0 | NOTIF: same hash @ T0+1s | DEDUP_DROP |
| T038 | Two notifications, no merchant info | NOTIF: ₹500 @ T0 | NOTIF: ₹500 @ T0+30s | FLAG_REVIEW |
| T039 | Auto-debit (Netflix) vs manual Netflix | NOTIF: Netflix auto ₹649 @ T0 | NOTIF: Netflix manual ₹649 @ T0+2m | FLAG_REVIEW |
| T040 | Credit vs debit same amount | NOTIF: -₹500 @ T0 | NOTIF: +₹500 @ T0+10s | 2 INSERT_NEW |
| T041 | HDFC + SBI same amount | NOTIF: HDFC ₹1000 @ T0 | NOTIF: SBI ₹1000 @ T0+20s | FLAG_REVIEW |
| T042 | OTP + transaction notif | NOTIF: OTP 123456 | NOTIF: ₹500 @ T0 | 1 INSERT |
| T043 | Bank A + bank B same merchant, same amount | NOTIF: HDFC ₹100 Zomato @ T0 | NOTIF: ICICI ₹100 Zomato @ T0+5s | FLAG_REVIEW |
| T044 | ICICI sends two notifications for one txn | NOTIF: ICICI ₹750 @ T0 | NOTIF: ICICI ₹750 @ T0+2s | DEDUP_DROP |
| T045 | SMS + push notification same txn | NOTIF: SMS ₹500 @ T0 | NOTIF: push ₹500 @ T0+1s | DEDUP_DROP |
| T046 | Same amount, different day | NOTIF: ₹500 @ T0 | NOTIF: ₹500 @ T0+1d | 2 INSERT_NEW |
| T047 | Axis sends "debited" and "paid" for same txn | NOTIF: debited ₹500 @ T0 | NOTIF: paid ₹500 @ T0+2s | DEDUP_DROP |
| T048 | Kotak + BHIM same txn | NOTIF: Kotak ₹250 @ T0 | NOTIF: BHIM ₹250 @ T0+3s | DEDUP_DROP |
| T049 | Promotional + transaction notif for same merchant | NOTIF: "Get cashback Zomato" @ T0 | NOTIF: ₹50 Zomato @ T0+1s | 1 INSERT (promo ignored) |
| T050 | Two transactions 1 minute apart (legit) | NOTIF: ₹40 auto @ T0 | NOTIF: ₹40 auto @ T0+1m | FLAG_REVIEW |

---

<a name="category-c"></a>
## Category C — Screenshot × Screenshot (T051–T065)

| ID | Scenario | Input 1 | Input 2 | Expected |
|----|----------|---------|---------|----------|
| T051 | Same screenshot shared twice | SHOT: identical @ T0 | SHOT: identical @ T0+2s | DEDUP_DROP |
| T052 | Cropped + full of same receipt | SHOT: cropped ₹250 @ T0 | SHOT: full ₹250 @ T0+5s | DEDUP_DROP |
| T053 | Different txns same amount+merchant | SHOT: ₹50 Zomato @ T0 | SHOT: ₹50 Zomato @ T0+20m | 2 INSERT_NEW |
| T054 | Legit repeat 4 min apart | SHOT: ₹50 chai @ T0 | SHOT: ₹50 chai @ T0+4m | FLAG_REVIEW |
| T055 | Blurry OCR of same | SHOT: ₹50 Zomato @ T0 | SHOT: ₹5O Zomato @ T0+10s | DEDUP_DROP |
| T056 | GPay + PhonePe screenshots same payment | SHOT: GPay ₹500 @ T0 | SHOT: PhonePe ₹500 @ T0+30s | DEDUP_DROP |
| T057 | Same payment from WhatsApp vs Files | SHOT: from WA @ T0 | SHOT: from Files @ T0+1m | DEDUP_DROP |
| T058 | Failed page + success page | SHOT: FAILED ₹500 @ T0 | SHOT: SUCCESS ₹500 @ T0+2m | DEDUP_MERGE |
| T059 | Portrait + landscape of same receipt | SHOT: portrait @ T0 | SHOT: landscape @ T0+3s | DEDUP_DROP |
| T060 | Same screenshot, different crop dimensions | SHOT: crop A @ T0 | SHOT: crop B @ T0+5s | DEDUP_DROP |
| T061 | Two screenshots 30 min apart, same amount | SHOT: ₹50 @ T0 | SHOT: ₹50 @ T0+30m | 2 INSERT_NEW |
| T062 | Different merchants, same amount | SHOT: Zomato ₹50 @ T0 | SHOT: Swiggy ₹50 @ T0+1m | 2 INSERT_NEW |
| T063 | Same payment from Google Drive share | SHOT: gdrive @ T0 | SHOT: direct @ T0+2m | DEDUP_DROP |
| T064 | Same screenshot, one has watermark | SHOT: clean @ T0 | SHOT: watermarked @ T0+1m | DEDUP_DROP |
| T065 | Screenshot of SMS screenshot | SHOT: SMS wrapper ₹500 @ T0 | SHOT: direct SMS ₹500 @ T0+2m | DEDUP_DROP |

---

<a name="category-d"></a>
## Category D — OCR Noise & Misread (T066–T085)

| ID | Scenario | Input 1 | Input 2 | Expected |
|----|----------|---------|---------|----------|
| T066 | OCR reads `5O` for `50` | NOTIF: ₹50 @ T0 | SHOT: `₹5O Zomato` @ T0+1m | DEDUP_DROP |
| T067 | OCR reads `l` for `1` | NOTIF: ₹10 @ T0 | SHOT: `₹l0` @ T0+1m | DEDUP_DROP |
| T068 | Trailing period in amount | NOTIF: ₹50 @ T0 | SHOT: `₹50.` @ T0+30s | DEDUP_DROP |
| T069 | ₹500 misread as ₹590 | NOTIF: ₹500 @ T0 | SHOT: `₹590 Zomato` @ T0+30s | 2 INSERT + FLAG_REVIEW |
| T070 | ₹50 misread as ₹5 (dropped zero) | NOTIF: ₹50 @ T0 | SHOT: `₹5 Zomato` @ T0+30s | FLAG_REVIEW |
| T071 | "Zomato" → "Zomato0" | NOTIF: ₹50 Zomato @ T0 | SHOT: `₹50 Zomato0` @ T0+1m | DEDUP_DROP |
| T072 | "Swiggy" → "Swiqgy" | NOTIF: ₹250 Swiggy @ T0 | SHOT: `₹250 Swiqgy` @ T0+1m | DEDUP_DROP |
| T073 | Comma as decimal `₹1.500` = ₹1500 | NOTIF: ₹1500 @ T0 | SHOT: `₹1.500` @ T0+1m | DEDUP_DROP |
| T074 | Indian format `1,50,000` | NOTIF: ₹150000 @ T0 | SHOT: `₹1,50,000` @ T0+1m | DEDUP_DROP |
| T075 | Rotated 90° screenshot | NOTIF: ₹500 @ T0 | SHOT: rotated 90° ₹500 @ T0+1m | DEDUP_DROP |
| T076 | OCR reads `O` as `0` in merchant | NOTIF: ₹50 `Z0mato` @ T0 | SHOT: `₹50 Zomato` @ T0+1m | DEDUP_DROP |
| T077 | OCR inserts extra space in merchant | NOTIF: ₹50 `Zoma to` @ T0 | SHOT: `₹50 Zomato` @ T0+1m | DEDUP_DROP |
| T078 | OCR reads ₹0.50 as 50 | NOTIF: `₹0.50` @ T0 | SHOT: `₹50` @ T0+1m | 2 INSERT_NEW |
| T079 | OCR missing digit `₹50` → `₹5` | NOTIF: ₹50 @ T0 | SHOT: `₹5` @ T0+1m | FLAG_REVIEW |
| T080 | OCR extra digit `₹50` → `₹500` | NOTIF: ₹50 @ T0 | SHOT: `₹500` @ T0+1m | 2 INSERT_NEW |
| T081 | Merchant read in reverse | NOTIF: `otamoZ` @ T0 | SHOT: `Zomato` @ T0+1m | DEDUP_DROP if reversed-matching enabled, else FLAG_REVIEW |
| T082 | Amount symbols garbled `₹#!50` | NOTIF: ₹50 @ T0 | SHOT: `₹#!50 Zomato` @ T0+1m | DEDUP_DROP |
| T083 | Decimal comma used `₹50,00` | NOTIF: ₹5000 @ T0 | SHOT: `₹50,00` @ T0+1m | DEDUP_DROP |
| T084 | OCR reads `₹50` from blurred image as `₹5O` | NOTIF: ₹50 @ T0 | SHOT: `₹5O Zomato` @ T0+1m | DEDUP_DROP |
| T085 | Amount with ₹ symbol replaced by ₹? | NOTIF: ₹50 @ T0 | SHOT: `?50 Zomato` @ T0+1m | DEDUP_DROP |

---

<a name="category-e"></a>
## Category E — Legitimate Repeats (Must NOT Dedup) (T086–T110)

| ID | Scenario | Input 1 | Input 2 | Expected |
|----|----------|---------|---------|----------|
| T086 | Auto ₹40 twice in a day | NOTIF: ₹40 auto @ T0 | NOTIF: ₹40 auto @ T0+4h | 2 INSERT_NEW |
| T087 | Two ₹50 chai 3 min apart | NOTIF: ₹50 chai @ T0 | NOTIF: ₹50 chai @ T0+3m | FLAG_REVIEW |
| T088 | Netflix ₹649 monthly | NOTIF: Netflix ₹649 @ Oct 1 | NOTIF: Netflix ₹649 @ Nov 1 | 2 INSERT_NEW |
| T089 | Same amount, different merchants | NOTIF: ₹100 Zomato @ T0 | NOTIF: ₹100 Swiggy @ T0+2m | 2 INSERT_NEW |
| T090 | Same merchant, different amounts | NOTIF: ₹50 Zomato @ T0 | NOTIF: ₹100 Zomato @ T0+2m | 2 INSERT_NEW |
| T091 | Split bill: same merchant, same amount, two friends | NOTIF: ₹200 Bar @ T0 | NOTIF: ₹200 Bar @ T0+1m | FLAG_REVIEW |
| T092 | Two metro rides back-to-back | NOTIF: ₹50 metro @ T0 | NOTIF: ₹50 metro @ T0+8m | FLAG_REVIEW |
| T093 | Cashback credit then purchase | NOTIF: +₹50 cashback @ T0 | NOTIF: -₹50 Zomato @ T0+5s | 2 INSERT_NEW |
| T094 | Same amount, different UPI IDs, same store | NOTIF: ₹100 @ T0 | NOTIF: ₹100 @ T0+2m | 2 INSERT_NEW |
| T095 | Recharge ₹239 twice (two SIMs) | NOTIF: Jio ₹239 @ T0 | NOTIF: Airtel ₹239 @ T0+5m | 2 INSERT_NEW |
| T096 | Two Swiggy orders same evening | NOTIF: ₹250 Swiggy @ T0 | NOTIF: ₹250 Swiggy @ T0+3h | 2 INSERT_NEW |
| T097 | Zomato ₹50 morning + ₹50 evening | NOTIF: ₹50 Zomato @ 9am | NOTIF: ₹50 Zomato @ 9pm | 2 INSERT_NEW |
| T098 | Uber ₹180 twice, different routes | NOTIF: ₹180 Uber @ T0 | NOTIF: ₹180 Uber @ T0+45m | 2 INSERT_NEW |
| T099 | Two Netflix accounts, same plan | NOTIF: Netflix ₹649 @ T0 | NOTIF: Netflix ₹649 @ T0+2m | FLAG_REVIEW |
| T100 | Same merchant, amount, 10 min 30 sec apart | NOTIF: ₹100 @ T0 | NOTIF: ₹100 @ T0+10m30s | FLAG_REVIEW |
| T101 | Two DMart visits same amount | NOTIF: ₹500 DMart @ T0 | NOTIF: ₹500 DMart @ T0+6h | 2 INSERT_NEW |
| T102 | Same merchant, same amount, 11 min apart | NOTIF: ₹100 @ T0 | NOTIF: ₹100 @ T0+11m | 2 INSERT_NEW |
| T103 | Two ₹1 transactions, coffee + tip | NOTIF: ₹1 @ T0 | NOTIF: ₹1 @ T0+30s | FLAG_REVIEW |
| T104 | Two same-amount txns, different UPIs | NOTIF: ₹500 @ T0 | NOTIF: ₹500 @ T0+3m | FLAG_REVIEW |
| T105 | Same txn split across two cards | NOTIF: ₹1000 @ T0 | NOTIF: ₹1000 @ T0+10s | FLAG_REVIEW |
| T106 | Two Swiggy orders, same amount, 1 hr apart | NOTIF: ₹250 @ T0 | NOTIF: ₹250 @ T0+1h | 2 INSERT_NEW |
| T107 | Repeat purchase within 5 min, different merchants | NOTIF: ₹100 Zomato @ T0 | NOTIF: ₹100 Swiggy @ T0+5m | 2 INSERT_NEW |
| T108 | Two distinct Ola rides 3 min apart | NOTIF: ₹100 Ola @ T0 | NOTIF: ₹100 Ola @ T0+3m | FLAG_REVIEW |
| T109 | Same merchant two consecutive days | NOTIF: ₹50 Zomato @ T0 | NOTIF: ₹50 Zomato @ T0+1d | 2 INSERT_NEW |
| T110 | Two ₹10 payments 1 min apart | NOTIF: ₹10 @ T0 | NOTIF: ₹10 @ T0+1m | FLAG_REVIEW |

---

<a name="category-f"></a>
## Category F — Refunds, Reversals & Failed Payments (T111–T130)

| ID | Scenario | Input 1 | Input 2 | Expected |
|----|----------|---------|---------|----------|
| T111 | ₹500 debit → ₹500 refund 10 min later | NOTIF: -₹500 Zomato @ T0 | NOTIF: +₹500 refund @ T0+10m | 2 INSERT_NEW |
| T112 | Refund notif arrives before debit | NOTIF: +₹500 refund @ T0 | NOTIF: -₹500 Zomato @ T0+2s | 2 INSERT_NEW |
| T113 | Fail → refund → retry success | NOTIF: fail ₹500 @ T0 | NOTIF: refund ₹500 @ T0+2m | NOTIF: success ₹500 @ T0+3m | fail dropped, success inserted |
| T114 | Reversal with same reference | NOTIF: ₹500 ref R123 @ T0 | NOTIF: reversal ₹500 ref R123 @ T0+1m | DEDUP_DROP |
| T115 | Chargeback arriving days later | NOTIF: -₹500 @ T0 | NOTIF: +₹500 chargeback @ T0+3d | 2 INSERT_NEW |
| T116 | Partial refund | NOTIF: -₹500 @ T0 | NOTIF: +₹100 refund @ T0+5m | 2 INSERT_NEW |
| T117 | Merchant-initiated reversal | NOTIF: -₹100 Swiggy @ T0 | NOTIF: +₹100 Swiggy reversal @ T0+3m | 2 INSERT_NEW |
| T118 | Cashback appears as separate credit | NOTIF: -₹100 @ T0 | NOTIF: +₹5 cashback @ T0+1m | 2 INSERT_NEW |
| T119 | Failed txn then resubmit same | NOTIF: fail ₹500 @ T0 | NOTIF: success ₹500 @ T0+5m | 1 row, status SUCCESS |
| T120 | Failed txn never succeeds | NOTIF: fail ₹500 @ T0 | — | 0 rows (fail dropped) |
| T121 | UPI failure notification variants | NOTIF: `TXN FAILED ₹50` @ T0 | — | 0 rows |
| T122 | Bank reversal next day | NOTIF: -₹500 @ T0 | NOTIF: +₹500 reversal @ T0+1d | 2 INSERT_NEW |
| T123 | Refund screenshot + debit notif | NOTIF: -₹500 Zomato @ T0 | SHOT: +₹500 refund @ T0+5m | 2 INSERT_NEW |
| T124 | Failed screenshot then success notif | SHOT: FAILED ₹500 @ T0 | NOTIF: success ₹500 @ T0+3m | 1 row, status SUCCESS |
| T125 | Two refunds for one payment | NOTIF: -₹1000 @ T0 | NOTIF: +₹500 @ T0+1m | NOTIF: +₹500 @ T0+3m | 3 INSERT_NEW |
| T126 | Debit and refund 15 min apart | NOTIF: -₹500 @ T0 | NOTIF: +₹500 @ T0+15m | 2 INSERT_NEW |
| T127 | Refund for old txn with matching RRN | NOTIF: -₹500 RRN123 @ T0 | NOTIF: +₹500 RRN123 @ T0+2d | 2 INSERT_NEW (direction differs) |
| T128 | Merchant cancellation | NOTIF: -₹500 Zomato @ T0 | NOTIF: +₹500 cancelled @ T0+2m | 2 INSERT_NEW |
| T129 | Timeout failure then success | NOTIF: timeout ₹500 @ T0 | NOTIF: success ₹500 @ T0+10m | 1 row (timeout dropped) |
| T130 | Failed payment screenshot then retry success screenshot | SHOT: FAILED ₹500 @ T0 | SHOT: SUCCESS ₹500 @ T0+5m | 1 row, status SUCCESS |

---

<a name="category-g"></a>
## Category G — Merchant Name Variations (T131–T150)

| ID | Scenario | Input 1 | Input 2 | Expected |
|----|----------|---------|---------|----------|
| T131 | "ZOMATO" vs "zomato" | NOTIF: ₹50 ZOMATO @ T0 | SHOT: ₹50 zomato @ T0+1m | DEDUP_DROP |
| T132 | "Zomato" vs "Zomato Ltd" | NOTIF: ₹50 Zomato @ T0 | SHOT: ₹50 Zomato Ltd @ T0+1m | DEDUP_DROP |
| T133 | "Zomato" vs "ZOMATO MEDIA PVT LTD" | NOTIF: ₹50 Zomato @ T0 | SHOT: ₹50 ZOMATO MEDIA PVT LTD @ T0+1m | DEDUP_DROP |
| T134 | "Amazon" vs "Amazon Pay" | NOTIF: ₹500 Amazon @ T0 | SHOT: ₹500 Amazon Pay @ T0+1m | DEDUP_DROP |
| T135 | "Uber" vs "Uber India Systems" | NOTIF: ₹180 Uber @ T0 | SHOT: ₹180 Uber India Systems @ T0+1m | DEDUP_DROP |
| T136 | "Zomato" vs "Swiggy" (different) | NOTIF: ₹50 Zomato @ T0 | SHOT: ₹50 Swiggy @ T0+1m | 2 INSERT_NEW |
| T137 | UPI handle `zomato@ybl` vs "Zomato" | NOTIF: ₹50 zomato@ybl @ T0 | SHOT: ₹50 Zomato @ T0+1m | DEDUP_DROP |
| T138 | "Swiggy" vs "SWIGGY" vs "Swiggy Instamart" | NOTIF: ₹50 Swiggy @ T0 | SHOT: ₹50 Swiggy Instamart @ T0+1m | DEDUP_DROP |
| T139 | "BigBasket" vs "Big Basket" | NOTIF: ₹500 BigBasket @ T0 | SHOT: ₹500 Big Basket @ T0+1m | DEDUP_DROP |
| T140 | "DMart" vs "D-Mart" vs "DMART" | NOTIF: ₹300 DMart @ T0 | SHOT: ₹300 D-Mart @ T0+1m | DEDUP_DROP |
| T141 | "McDonald's" vs "McDonalds" | NOTIF: ₹200 McDonald's @ T0 | SHOT: ₹200 McDonalds @ T0+1m | DEDUP_DROP |
| T142 | "Airtel" vs "Bharti Airtel" | NOTIF: ₹239 Airtel @ T0 | SHOT: ₹239 Bharti Airtel @ T0+1m | DEDUP_DROP |
| T143 | "PhonePe" vs "PhonePe Pvt Ltd" | NOTIF: ₹50 PhonePe @ T0 | SHOT: ₹50 PhonePe Pvt Ltd @ T0+1m | DEDUP_DROP |
| T144 | "Zepto" vs "Zepto Marketplace" | NOTIF: ₹100 Zepto @ T0 | SHOT: ₹100 Zepto Marketplace @ T0+1m | DEDUP_DROP |
| T145 | "Blinkit" vs "Blink Commerce" | NOTIF: ₹200 Blinkit @ T0 | SHOT: ₹200 Blink Commerce @ T0+1m | DEDUP_DROP |
| T146 | "Flipkart" vs "Flipkart Internet" | NOTIF: ₹500 Flipkart @ T0 | SHOT: ₹500 Flipkart Internet @ T0+1m | DEDUP_DROP |
| T147 | "Myntra" vs "Myntra Designs" | NOTIF: ₹300 Myntra @ T0 | SHOT: ₹300 Myntra Designs @ T0+1m | DEDUP_DROP |
| T148 | "IRCTC" vs "Indian Railways" | NOTIF: ₹500 IRCTC @ T0 | SHOT: ₹500 Indian Railways @ T0+1m | FLAG_REVIEW (weak alias) |
| T149 | Merchant with special chars "Zomato!" vs "Zomato" | NOTIF: ₹50 Zomato! @ T0 | SHOT: ₹50 Zomato @ T0+1m | DEDUP_DROP |
| T150 | Merchant name with location "Zomato Koramangala" | NOTIF: ₹50 Zomato @ T0 | SHOT: ₹50 Zomato Koramangala @ T0+1m | DEDUP_DROP |

---

<a name="category-h"></a>
## Category H — Timing Edge Cases (T151–T165)

| ID | Scenario | Input 1 | Input 2 | Expected |
|----|----------|---------|---------|----------|
| T151 | Screenshot 5 min after payment, notif instant | NOTIF: ₹50 @ T0 | SHOT: ₹50 @ T0+5m | DEDUP_DROP |
| T152 | Bank delayed notif 8 min after screenshot | SHOT: ₹500 @ T0 | NOTIF: ₹500 @ T0+8m | DEDUP_DROP |
| T153 | Exactly 10-min boundary | NOTIF: ₹500 @ T0 | SHOT: ₹500 @ T0+10m00s | DEDUP_DROP |
| T154 | 10 min + 1 s (outside) | NOTIF: ₹500 @ T0 | SHOT: ₹500 @ T0+10m01s | INSERT_NEW |
| T155 | Cross-midnight 23:58 → 00:03 | NOTIF: ₹500 @ 23:58 | SHOT: ₹500 @ 00:03 | DEDUP_DROP |
| T156 | Timezone mismatch UTC vs IST | NOTIF: ₹500 @ 14:00 IST | SHOT: ₹500 @ 14:00 IST | DEDUP_DROP |
| T157 | Device clock changed by user | NOTIF @ T0 | SHOT @ T0-5m | DEDUP_DROP |
| T158 | DST transition (India no DST but sim) | NOTIF @ T0 | SHOT @ T0+10m | DEDUP_DROP |
| T159 | App background 30 min between events | NOTIF @ T0 | SHOT @ T0+30m | 2 INSERT_NEW |
| T160 | App killed, restarted between events | NOTIF @ T0 | SHOT @ T0+2m | DEDUP_DROP |
| T161 | Notification arrives 1 s before screenshot | NOTIF @ T0 | SHOT @ T0+1s | DEDUP_DROP |
| T162 | Two events exactly same millisecond | NOTIF @ T0 | SHOT @ T0 | DEDUP_DROP (mutex) |
| T163 | Leap second during matching | NOTIF @ T0 | SHOT @ T0+1s | DEDUP_DROP |
| T164 | Screenshot timestamp from EXIF differs from notif | NOTIF @ T0 | SHOT @ T0 (EXIF T0-5m) | DEDUP_DROP |
| T165 | Historic screenshot 2 days old | SHOT @ T0-2d | NOTIF @ T0 | 2 INSERT_NEW |

---

<a name="category-i"></a>
## Category I — Amount Parsing & Formats (T166–T180)

| ID | Scenario | Input 1 | Input 2 | Expected |
|----|----------|---------|---------|----------|
| T166 | ₹1000 vs ₹1,000 | NOTIF: ₹1000 @ T0 | SHOT: ₹1,000 @ T0+1m | DEDUP_DROP |
| T167 | ₹1000 vs ₹1 000 (space separator) | NOTIF: ₹1000 @ T0 | SHOT: ₹1 000 @ T0+1m | DEDUP_DROP |
| T168 | ₹0.50 vs 50 paise | NOTIF: 50 paise @ T0 | SHOT: ₹0.50 @ T0+1m | DEDUP_DROP |
| T169 | Scientific notation ₹1e3 | NOTIF: ₹1000 @ T0 | SHOT: ₹1e3 @ T0+1m | DEDUP_DROP |
| T170 | Rounding ₹500.00 vs ₹500.000 | NOTIF: ₹500.00 @ T0 | SHOT: ₹500.000 @ T0+1m | DEDUP_DROP |
| T171 | `.00` vs no decimals | NOTIF: ₹500.00 @ T0 | SHOT: ₹500 @ T0+1m | DEDUP_DROP |
| T172 | Negative sign in one source | NOTIF: -₹500 @ T0 | SHOT: ₹500 @ T0+1m | DEDUP_DROP if screenshot marked debit |
| T173 | Currency symbol mismatch | NOTIF: ₹500 @ T0 | SHOT: Rs.500 @ T0+1m | DEDUP_DROP |
| T174 | Amount with "INR" prefix | NOTIF: ₹500 @ T0 | SHOT: INR 500 @ T0+1m | DEDUP_DROP |
| T175 | Amount with "Rupees" spelled out | NOTIF: ₹500 @ T0 | SHOT: Rupees 500 @ T0+1m | DEDUP_DROP |
| T176 | Amount with currency after | NOTIF: ₹500 @ T0 | SHOT: 500 Rs @ T0+1m | DEDUP_DROP |
| T177 | Very small amount 0.01 | NOTIF: ₹0.01 @ T0 | SHOT: 1 paisa @ T0+1m | DEDUP_DROP |
| T178 | Amount with spaces ₹ 500 | NOTIF: ₹500 @ T0 | SHOT: ₹ 500 @ T0+1m | DEDUP_DROP |
| T179 | Amount in words "five hundred" | NOTIF: ₹500 @ T0 | SHOT: five hundred @ T0+1m | DEDUP_DROP |
| T180 | Amount with commas both places | NOTIF: ₹1,000 @ T0 | SHOT: ₹1,000 @ T0+1m | DEDUP_DROP |

---

<a name="category-j"></a>
## Category J — Concurrency & Race Conditions (T181–T190)

| ID | Scenario | Expected |
|----|----------|----------|
| T181 | Notif & screenshot inserted simultaneously | Exactly 1 row (mutex/lock) |
| T182 | Two screenshots inserted on different threads | 1 INSERT + 1 DEDUP_DROP |
| T183 | Notification retried by OS after crash | DEDUP_DROP |
| T184 | Screenshot parsed while notif mid-transaction | Serializable isolation, 1 row |
| T185 | App killed between INSERT and index update | Rollback or recovery, 1 row |
| T186 | Two notification listeners fire for same txn | DEDUP_DROP |
| T187 | Screenshot handler called twice for same URI | DEDUP_DROP |
| T188 | Race between dedup check and insert | 1 row (atomic check-and-insert) |
| T189 | Concurrent dedup on same pair | Idempotent, 1 row |
| T190 | Koin singleton initialization race | Single guard instance, 1 row |

---

<a name="category-k"></a>
## Category K — State Transitions (T191–T200)

| ID | Scenario | Input 1 | Input 2 | Expected |
|----|----------|---------|---------|----------|
| T191 | PENDING → SUCCESS | NOTIF: pending ₹500 Zomato @ T0 | NOTIF: success ₹500 Zomato @ T0+20s | DEDUP_MERGE |
| T192 | SUCCESS → PENDING (out of order) | NOTIF: success ₹500 @ T0 | NOTIF: pending ₹500 @ T0-1m | DEDUP_DROP |
| T193 | Screenshot pending → success | SHOT: pending ₹500 @ T0 | SHOT: success ₹500 @ T0+30s | DEDUP_MERGE |
| T194 | FAILED → SUCCESS | NOTIF: failed ₹500 @ T0 | NOTIF: success ₹500 @ T0+1m | 1 row, status SUCCESS |
| T195 | PENDING never resolves | NOTIF: pending ₹500 @ T0 | — | 1 row, status PENDING |
| T196 | Multiple PENDING → one SUCCESS | NOTIF: pending ₹500 @ T0, T0+5s, T0+10s | NOTIF: success ₹500 @ T0+15s | 1 row SUCCESS |
| T197 | SUCCESS then FAILED (reversal state) | NOTIF: success ₹500 @ T0 | NOTIF: failed ₹500 @ T0+30s | FLAG_REVIEW |
| T198 | Screenshot shows FAILED, notif shows SUCCESS | SHOT: FAILED ₹500 @ T0 | NOTIF: success ₹500 @ T0+2m | 1 row, status SUCCESS |
| T199 | Notif shows SUCCESS, screenshot shows FAILED | NOTIF: success ₹500 @ T0 | SHOT: FAILED ₹500 @ T0+2m | 1 row, status SUCCESS |
| T200 | Pending notif + confirmed screenshot | NOTIF: pending ₹500 @ T0 | SHOT: success ₹500 @ T0+1m | DEDUP_MERGE |

---

<a name="category-l"></a>
## Category L — Manual Entry Interaction (T201–T210)

| ID | Scenario | Input 1 | Input 2 | Expected |
|----|----------|---------|---------|----------|
| T201 | Manual ₹500 Zomato → notif 5s later | MANUAL: ₹500 Zomato @ T0 | NOTIF: ₹500 Zomato @ T0+5s | FLAG_REVIEW |
| T202 | Manual entry after notif | NOTIF: ₹500 @ T0 | MANUAL: ₹500 @ T0+5m | FLAG_REVIEW |
| T203 | Manual same as auto-captured | NOTIF @ T0, MANUAL @ T0+10m | FLAG_REVIEW |
| T204 | User marks "different transaction" | NOTIF @ T0, MANUAL @ T0+1m | User clicks Keep separate | 2 INSERT_NEW |
| T205 | Manual entry with no merchant | MANUAL: ₹500 @ T0 | NOTIF: ₹500 Zomato @ T0+1m | FLAG_REVIEW |
| T206 | Manual entry with different amount | MANUAL: ₹500 @ T0 | NOTIF: ₹600 @ T0+1m | 2 INSERT_NEW |
| T207 | User edits amount after auto-capture | NOTIF: ₹500 @ T0 (user edits to ₹550) | NOTIF: ₹500 @ T0+30s | FLAG_REVIEW |
| T208 | User deletes auto-capture then manual re-adds | NOTIF: ₹500 @ T0 (deleted) | MANUAL: ₹500 @ T0+1m | 2 INSERT_NEW (notif was deleted) |
| T209 | Manual entry inside notification window | MANUAL: ₹500 Zomato @ T0 | NOTIF: ₹500 Zomato @ T0+2m | FLAG_REVIEW |
| T210 | Manual entry outside window | MANUAL: ₹500 Zomato @ T0 | NOTIF: ₹500 Zomato @ T0+30m | 2 INSERT_NEW |

---

<a name="category-m"></a>
## Category M — Multi-Account / Multi-Bank (T211–T220)

| ID | Scenario | Input 1 | Input 2 | Expected |
|----|----------|---------|---------|----------|
| T211 | Same amount, two different bank accounts | NOTIF: HDFC ₹500 @ T0 | NOTIF: SBI ₹500 @ T0+1m | FLAG_REVIEW |
| T212 | Screenshot shows HDFC card, notif from SBI | SHOT: HDFC ₹500 @ T0 | NOTIF: SBI ₹500 @ T0+1m | FLAG_REVIEW |
| T213 | Same UPI VPA different sending accounts | NOTIF: A/c XX1234 ₹500 @ T0 | NOTIF: A/c XX5678 ₹500 @ T0+1m | 2 INSERT_NEW |
| T214 | Two cards, same merchant, same amount | NOTIF: Card A ₹500 @ T0 | NOTIF: Card B ₹500 @ T0+30s | 2 INSERT_NEW |
| T215 | Joint account, two holders get notif | NOTIF: holder1 ₹500 @ T0 | NOTIF: holder2 ₹500 @ T0+2s | DEDUP_DROP |
| T216 | Same txn, credit card + bank notif | NOTIF: CC ₹500 @ T0 | NOTIF: bank ₹500 @ T0+1s | DEDUP_DROP |
| T217 | UPI + card notif same txn | NOTIF: UPI ₹500 @ T0 | NOTIF: card ₹500 @ T0+2s | DEDUP_DROP |
| T218 | Two accounts, same amount, same merchant | NOTIF: A ₹500 Zomato @ T0 | NOTIF: B ₹500 Zomato @ T0+1m | FLAG_REVIEW |
| T219 | Wallet + bank same txn | NOTIF: Paytm wallet ₹500 @ T0 | NOTIF: HDFC ₹500 @ T0+1s | DEDUP_DROP |
| T220 | Multi-currency account same amount | NOTIF: ₹500 INR @ T0 | NOTIF: $6 USD @ T0+1s | 2 INSERT_NEW |

---

<a name="category-n"></a>
## Category N — Budget & Analytics Impact (T221–T230)

| ID | Scenario | Input | Expected |
|----|----------|-------|----------|
| T221 | Dedup'd txn counts once in budget | 1 dedup'd Zomato ₹500 | Budget impact = ₹500 |
| T222 | Dedup'd txn counts once in category | 1 dedup'd Swiggy ₹250 | Food & Dining += ₹250 |
| T223 | Dedup'd appears once in list | 1 dedup'd | Exactly 1 row visible |
| T224 | Dedup'd merchant frequency +1 only | 1 dedup'd Zomato | Zomato frequency +1 |
| T225 | Budget alert fires once | ₹500 dedup'd at 80% threshold | Alert fired once |
| T226 | Category recategorization applies to merged row | User recategorizes Zomato → Food | Merged row recategorized |
| T227 | Monthly sum correct after dedup | Multiple dedup'd txns | Sum correct |
| T228 | Weekly period rollover correctly counts | Dedup'd during rollover | Correct assignment |
| T229 | Budget recalc triggered once per dedup | Dedup event | Single recalc |
| T230 | Analytics dashboard consistent | Post-dedup | Counts consistent |

---

<a name="category-o"></a>
## Category O — Data Integrity & Idempotency (T231–T245)

| ID | Scenario | Expected |
|----|----------|----------|
| T231 | Running dedup twice is idempotent | No change on second run |
| T232 | Dedup'd rows retain RRN | RRN preserved |
| T233 | Dedup'd row retains best-of-both fields | Merchant from screenshot, amount from notif |
| T234 | Original row's createdAt unchanged | createdAt preserved |
| T235 | Original row's updatedAt bumped on merge | updatedAt updated |
| T236 | Source field reflects winning source | Correct source |
| T237 | Deleting one duplicate doesn't delete the other | Rows independent |
| T238 | Merge preserves user-set category | User category not overwritten |
| T239 | Merge preserves user notes | Notes preserved |
| T240 | Merge preserves budget attachment | Budget link preserved |
| T241 | Soft-deleted row not restored by merge | Deleted stays deleted |
| T242 | Archived row not affected | Archive preserved |
| T243 | Merge is transactional (all or nothing) | Crash mid-merge → rollback |
| T244 | Dedup log entry created | Audit trail present |
| T245 | Hash of dedup'd pair stable | Same hash on repeated runs |

---

<a name="category-p"></a>
## Category P — UPI App Wording Variants (T246–T270)

| ID | Scenario | Input | Expected |
|----|----------|-------|----------|
| T246 | GPay: "You paid ₹50 to Zomato" | Parse OK, DEBIT | INSERT |
| T247 | PhonePe: "Rs.50 debited from A/c XX1234" | Parse OK | INSERT |
| T248 | Paytm: "₹50 paid successfully to Zomato" | Parse OK | INSERT |
| T249 | CRED: "Payment of ₹50 to Zomato is successful" | Parse OK | INSERT |
| T250 | HDFC: "Rs.50.00 debited from A/c XX1234 on 14-10-26" | Parse OK | INSERT |
| T251 | SBI: "Your A/c XX1234 debited by Rs.50 on 14Oct26" | Parse OK | INSERT |
| T252 | ICICI: "INR 50.00 debited from A/c XX1234" | Parse OK | INSERT |
| T253 | Axis: "Txn of INR 50 debited. Info: ZOMATO" | Parse OK | INSERT |
| T254 | Kotak: "Rs 50 spent on your Kotak Card at Zomato" | Parse OK | INSERT |
| T255 | BHIM: "₹50 paid to zomato@ybl" | Parse OK | INSERT |
| T256 | Amazon Pay: "₹50 debited. Paid to Zomato" | Parse OK | INSERT |
| T257 | WhatsApp Pay: "You sent ₹50 to Zomato" | Parse OK | INSERT |
| T258 | Fi Money: "₹50 spent at Zomato" | Parse OK | INSERT |
| T259 | Jupiter: "Rs.50 debited. Merchant: Zomato" | Parse OK | INSERT |
| T260 | Navi: "Your payment of ₹50 to Zomato" | Parse OK | INSERT |
| T261 | Slice: "₹50 spent on your Slice card at Zomato" | Parse OK | INSERT |
| T262 | OneCard: "Transaction ₹50. Merchant Zomato" | Parse OK | INSERT |
| T263 | IDFC: "INR 50 debited from your a/c XX1234" | Parse OK | INSERT |
| T264 | Yes Bank: "Rs 50.00 debited A/c XX1234 to Zomato" | Parse OK | INSERT |
| T265 | Federal Bank: "FedBook: ₹50 debited to Zomato" | Parse OK | INSERT |
| T266 | IndusInd: "Your txn of Rs 50 at Zomato is successful" | Parse OK | INSERT |
| T267 | Bank of Baroda: "BOB: Rs.50 debited from XX1234" | Parse OK | INSERT |
| T268 | Punjab National Bank: "PNB: ₹50 debited A/c XX1234" | Parse OK | INSERT |
| T269 | Canara Bank: "Rs 50.00 debited from your Canara a/c" | Parse OK | INSERT |
| T270 | Union Bank: "Rs.50 debited to Zomato from XX1234" | Parse OK | INSERT |

---

<a name="category-q"></a>
## Category Q — Non-Transactional — Must Ignore (T271–T285)

| ID | Scenario | Input | Expected |
|----|----------|-------|----------|
| T271 | OTP SMS-style | NOTIF: OTP 123456 for txn of ₹50 | IGNORE |
| T272 | Promotional: cashback offer | NOTIF: Get ₹50 cashback on Zomato | IGNORE |
| T273 | Bill reminder | NOTIF: Your Zomato bill of ₹50 is due | IGNORE |
| T274 | Statement ready | NOTIF: Statement for ₹5000 ready | IGNORE |
| T275 | Balance notification | NOTIF: Bal: ₹5000 | IGNORE |
| T276 | Failed txn | NOTIF: Txn of ₹50 failed | IGNORE |
| T277 | Scheduled mandate | NOTIF: Auto-debit scheduled ₹500 | IGNORE |
| T278 | KYC alert | NOTIF: Complete KYC | IGNORE |
| T279 | Security alert | NOTIF: Login from new device | IGNORE |
| T280 | Low balance | NOTIF: Low balance ₹100 | IGNORE |
| T281 | Account statement | NOTIF: Your monthly statement | IGNORE |
| T282 | Loan EMI reminder | NOTIF: EMI due on 5th | IGNORE |
| T283 | Credit card offer | NOTIF: 5% cashback on your card | IGNORE |
| T284 | Referral reward | NOTIF: ₹100 referral bonus credited | INSERT (credit) |
| T285 | Salary credit | NOTIF: Salary ₹50000 credited | INSERT (credit) |

---

<a name="category-r"></a>
## Category R — Robustness & Fuzz (T286–T305)

| ID | Scenario | Expected |
|----|----------|----------|
| T286 | Very long merchant string (500 chars) | Truncate, no crash |
| T287 | Unicode merchant "ज़ोमैटो" vs "Zomato" | No match, both insert |
| T288 | Null merchant, null RRN, only amount+time | Rely on amount+time |
| T289 | Screenshot with no text | IGNORE, log error |
| T290 | Notification with only emoji | IGNORE |
| T291 | Amount = 0 | IGNORE |
| T292 | Amount = negative (malformed) | IGNORE |
| T293 | Extremely large amount ₹99,99,99,999 | FLAG_REVIEW |
| T294 | Amount = 1 paisa | INSERT (valid) |
| T295 | RRN present in both, matches | DEDUP_DROP |
| T296 | RRN present in one only | Fall back to amount+time+merchant |
| T297 | RRN present in both, differs | 2 INSERT_NEW |
| T298 | Same RRN across different days | DEDUP_DROP |
| T299 | Empty notification payload | IGNORE |
| T300 | Malformed JSON in payload | IGNORE, log |
| T301 | SQL injection attempt in merchant | Sanitize, INSERT |
| T302 | Null bytes in merchant string | Strip, INSERT |
| T303 | Emoji-only merchant | IGNORE |
| T304 | Merchant with control characters | Strip, INSERT |
| T305 | Extremely long notification (10MB) | Truncate, no crash |

---

<a name="category-s"></a>
## Category S — Security, PII & Privacy (T306–T315)

| ID | Scenario | Expected |
|----|----------|----------|
| T306 | Dedup log must not include full amount | Log hashed amount only |
| T307 | Dedup log must not include merchant name | Log anonymized |
| T308 | Dedup decisions not sent over network | No network call |
| T309 | Dedup guard respects SQLCipher encryption | Data encrypted at rest |
| T310 | Dedup doesn't leak to Android Logcat | Only in debug builds |
| T311 | Dedup runs in memory only, no temp file | No disk spillover |
| T312 | Dedup respects biometric lock state | No dedup while locked |
| T313 | Dedup decision not tied to device ID | Privacy preserved |
| T314 | Dedup doesn't create analytics events | Zero tracking |
| T315 | Dedup logs are user-deletable | Comply with data rights |

---

<a name="category-t"></a>
## Category T — Performance & Scale (T316–T320)

| ID | Scenario | Expected |
|----|----------|----------|
| T316 | 10,000 txns in repo, dedup a new one | < 100ms |
| T317 | 100 concurrent dedup checks | All complete < 1s |
| T318 | Merchant similarity on 500-char strings | < 50ms |
| T319 | Sustained load: 1000 txns in 1 min | No dropped txns |
| T320 | Memory footprint of dedup cache | < 10 MB |

---

<a name="category-u"></a>
## Category U — Real-World End-to-End (T321–T335)

| ID | Scenario | Steps | Expected |
|----|----------|-------|----------|
| T321 | Morning coffee flow | Open Zomato → pay ₹50 → share screenshot → notif arrives | 1 txn |
| T322 | UPI QR code flow | Scan QR at shop → pay ₹100 → notif → later share screenshot | 1 txn |
| T323 | Missed notif recovery | Pay at store, miss notif, share screenshot next day | 1 txn |
| T324 | Bank delay catch-up | Pay, screenshot immediately, notif 12 min later | 2 txns (outside window) |
| T325 | Two orders one evening | Zomato ₹200 at 7pm, Swiggy ₹200 at 7:05pm | 2 txns |
| T326 | Refund flow | Pay ₹500 → refund ₹500 → re-pay ₹500 | 3 txns |
| T327 | Failed retry flow | Pay fail → retry success → notif both | 1 txn |
| T328 | Auto-debit + manual notif | Netflix auto-debit + manual Netflix charge | FLAG_REVIEW |
| T329 | Split bill at restaurant | Two people pay ₹250 each at same restaurant | 2 txns |
| T330 | Two Swiggy orders via one screenshot | Share screenshot with two orders | 2 txns (parse both) |
| T331 | Subscription cycle | Netflix Oct + Nov + Dec | 3 txns |
| T332 | Trip: many rides same day | Ola ₹100 + Ola ₹100 + Ola ₹150 | 3 txns |
| T333 | Flash sale: multiple ₹99 buys | ₹99 DMart × 3, 30s apart | FLAG_REVIEW ×2 |
| T334 | Reversal + retry + refund | Complex multi-step | Correct final state |
| T335 | User shares wrong screenshot | Screenshot from previous day | 1 txn + FLAG_REVIEW |

---

<a name="category-v"></a>
## Category V — Category Classification Interaction (T336–T345)

| ID | Scenario | Expected |
|----|----------|----------|
| T336 | Dedup'd txn categorized once | Category set once |
| T337 | User category override survives merge | Override preserved |
| T338 | Merchant alias from one source enriches other | Alias merged |
| T339 | Tier 1 user rule applies to merged row | Rule enforced |
| T340 | Tier 2 keyword matches on merge | Keyword applied |
| T341 | Merged merchant name used for future categorization | Alias learned |
| T342 | Category recalc triggered on merge | Recalc fired once |
| T343 | Category stats reflect merged row only | Stats correct |
| T344 | Category bar chart consistent | Chart correct |
| T345 | Category tree not double-counted | Tree consistent |

---

## Implementation Notes

### Fixtures Required

- `TestClock` — controllable `now()`
- In-memory `TransactionRepository` with `source` column
- `DeduplicationGuard` singleton with configurable window (default ±10 min)
- Fake notification payloads as raw strings
- Fake screenshot OCR outputs as strings (decouple OCR from dedup)
- `UserShareScreenshotFixtures` — simulate actual share flows

### Standard Assertions

```kotlin
assertEquals(expectedCount, repo.count())
assertEquals(expectedOutcome, guard.lastOutcome)
assertEquals(expectedSource, repo.last().source)
assertEquals(expectedAmount, repo.last().amountInPaise)
```

### Coverage Gate

All 345 tests must pass on both `play` and `fdroid` flavors before merging dedup changes.

### Implementation Priority

1. **T001–T030** — core notification × screenshot
2. **T031–T050** — notification × notification
3. **T086–T110** — legitimate repeats (false positive protection)
4. **T066–T085** — OCR noise
5. **T111–T130** — refunds & reversals
6. Everything else

### Regression Gate

If any test in **Category E (T086–T110)** fails, treat it as a **critical regression** — these tests protect against incorrectly merging distinct purchases.

### Safety Rule

Wrap dedup in `try/catch`. On any exception, default to `INSERT_NEW`. Never drop a real transaction due to a dedup bug.

---

## Honest Caveats

- **T035, T091, T092, T100, T104, T105, T108, T110** are genuinely ambiguous. `FLAG_REVIEW` is correct — the user decides.
- **T041, T211, T212, T218** require parsing the account/card identifier. If your parser doesn't extract this, tests fail closed (safer).
- **T233 (merge policy)** requires a product decision on field precedence.
- **T286–T305 (fuzz)** must never crash. Default to `INSERT_NEW` on failure.
- **T321–T335** are integration tests — run them against the full app, not just the guard unit.

---

**End of file — 345 test cases across 22 categories.**
