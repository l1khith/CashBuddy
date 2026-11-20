"""Positive templates: real bank / UPI / card / ATM transaction messages."""

from __future__ import annotations

from vocab import EXPENSE_CATEGORIES, INCOME_CATEGORIES

TEMPLATE_VERSION = "pos-v1"

POSITIVE_TEMPLATES: list[dict] = []


def P(tid: str, family: str, source: str, fmt: str, *, txn: str = "DEBIT",
      fixed: str | None = None, cats: tuple[str, ...] | None = None,
      style: str = "upper", method: str = "UPI",
      difficulty: str | None = None,
      merchant_kind: str = "merchant",
      bounds: tuple[float, float] | None = None) -> None:
    if fixed:
        cats = (fixed,)
    elif cats is None:
        cats = INCOME_CATEGORIES if txn == "CREDIT" else EXPENSE_CATEGORIES
    if txn == "CREDIT":
        assert all(c in INCOME_CATEGORIES for c in cats), (tid, cats)
    else:
        assert all(c not in INCOME_CATEGORIES for c in cats), (tid, cats)
    if difficulty is None:
        difficulty = "easy" if source in ("bank_sms", "upi", "bill") else "medium"
    POSITIVE_TEMPLATES.append(dict(
        tid=tid, family=family, source=source, fmt=fmt, txn=txn,
        cats=tuple(cats), style=style, method=method,
        difficulty=difficulty, merchant_kind=merchant_kind,
        bounds=bounds,
    ))


# --- Bank SMS: generic debits -------------------------------------------------
P("bsms_dr_01", "bank_debit", "bank_sms",
  "{bank}: Rs.{amt} debited from A/c XX{acct} on {date} at {time} to {merchant}. "
  "Avl Bal Rs.{bal}. Ref {utr}.", method="UPI")
P("bsms_dr_02", "bank_debit", "bank_sms",
  "{bank}: INR {amt} has been debited from your account XX{acct} on {date}. "
  "Beneficiary: {merchant}. Available Balance: INR {bal}. UPI Ref: {utr}.")
P("bsms_dr_03", "bank_debit", "bank_sms",
  "Your {bank} A/c XX{acct} is debited by Rs.{amt} on {date} {time} towards "
  "{merchant}. Bal Rs.{bal}. RRN {utr}.")
P("bsms_dr_04", "bank_debit", "bank_sms",
  "Dr Rs.{amt} from {bank} A/c *{acct} to {merchant} on {date}. UPI:{utr}. "
  "Bal Rs.{bal}.", method="UPI")
P("bsms_dr_05", "bank_debit", "bank_sms",
  "{bank} Alert: Rs.{amt} debited from A/c XX{acct} at {merchant} on {date}. "
  "Avl Bal Rs.{bal}. Call 1800{phone6} for help.", method="CARD")
P("bsms_dr_06", "bank_debit", "bank_sms",
  "Payment of Rs.{amt} made from A/c XX{acct} to {merchant} on {date}. "
  "Ref {utr}. Bal Rs.{bal}.", method="NETBANKING")
P("bsms_dr_07", "bank_debit", "bank_sms",
  "{bank}: A/c XX{acct} debited Rs.{amt} on {date} to {merchant} "
  "(UPI Ref {utr}). Avl Bal Rs.{bal}.")
P("bsms_dr_08", "bank_debit", "bank_sms",
  "INR {amt} debited from your {bank} account ending {acct} on {date} at {time}. "
  "Merchant: {merchant}. Ref: {utr}.", method="IMPS")
P("bsms_dr_09", "bank_debit", "bank_sms",
  "{bank}: Rs.{amt} paid to {merchant} from A/c XX{acct} via UPI on {date}. "
  "Transaction ID {utr}. Balance Rs.{bal}.")
P("bsms_dr_10", "bank_debit", "bank_sms",
  "Txn of Rs.{amt} successful. A/c XX{acct} debited to {merchant}. "
  "{bank} Ref {utr}. Avl Bal Rs.{bal}.")

# --- Bank SMS: generic credits ------------------------------------------------
P("bsms_cr_01", "bank_credit", "bank_sms",
  "{bank}: Rs.{amt} credited to A/c XX{acct} on {date} by {merchant}. "
  "Avl Bal Rs.{bal}. Ref {utr}.", txn="CREDIT", method="NEFT")
P("bsms_cr_02", "bank_credit", "bank_sms",
  "{bank}: INR {amt} received in your account XX{acct} from {merchant} on {date}. "
  "UTR {utr}. Available Balance INR {bal}.", txn="CREDIT")
P("bsms_cr_03", "bank_credit", "bank_sms",
  "Your A/c XX{acct} is credited with Rs.{amt} on {date} from {merchant}. "
  "Bal Rs.{bal}. Ref {utr}.", txn="CREDIT", method="IMPS")
P("bsms_cr_04", "bank_credit", "bank_sms",
  "{bank} Alert: Credit of Rs.{amt} in A/c XX{acct} from {merchant}. "
  "Date {date}. Ref {utr}. Bal Rs.{bal}.", txn="CREDIT")
P("bsms_cr_05", "bank_credit", "bank_sms",
  "Cr Rs.{amt} to {bank} A/c *{acct} from {merchant} on {date}. UPI:{utr}. "
  "Bal Rs.{bal}.", txn="CREDIT")

# --- Bank app notifications ---------------------------------------------------
P("bapp_01", "bank_app", "bank_app",
  "{bank}: Rs.{amt} debited from A/c XX{acct} to {merchant}. Avl Bal Rs.{bal}. "
  "Ref {utr}.")
P("bapp_02", "bank_app", "bank_app",
  "Transaction successful: Rs.{amt} paid to {merchant} from A/c XX{acct}. "
  "Ref {utr}.")
P("bapp_03", "bank_app", "bank_app",
  "You have received Rs.{amt} from {merchant} in A/c XX{acct}. Ref {utr}.",
  txn="CREDIT")
P("bapp_04", "bank_app", "bank_app",
  "{bank}: Rs.{amt} transferred from A/c XX{acct} to {merchant}. Ref {utr}.",
  method="IMPS")
P("bapp_05", "bank_app", "bank_app",
  "Alert: Rs.{amt} spent at {merchant} using {bank} Card XX{acct}. "
  "Avl limit Rs.{limit}.", method="CARD", difficulty="medium")
P("bapp_06", "bank_app", "bank_app",
  "Salary of Rs.{amt} credited to A/c XX{acct} by {merchant}. Ref {utr}. "
  "Avl Bal Rs.{bal}.", txn="CREDIT", fixed="Salary", method="NEFT")

# --- UPI merchant payments ----------------------------------------------------
P("upi_p2m_01", "upi_p2m", "upi",
  "{app}: Paid ₹{amt} to {merchant}. UPI transaction ID {utr}. "
  "From {bank} {acct}.", style="title")
P("upi_p2m_02", "upi_p2m", "upi",
  "{app}: You paid ₹{amt} to {merchant} on {date}. UPI Ref {utr}.", style="title")
P("upi_p2m_03", "upi_p2m", "upi",
  "Paid ₹{amt} to {merchant} via {app}. Txn ID {utr}. Completed.", style="title")
P("upi_p2m_04", "upi_p2m", "upi",
  "{app}: ₹{amt} sent to {merchant} ({vpa}). UPI Ref {utr}. "
  "{bank} A/c {acct}.", style="title")
P("upi_p2m_05", "upi_p2m", "upi",
  "{app} Payment Successful. ₹{amt} paid to {merchant}. From {bank} "
  "A/c XX{acct}. UPI Ref {utr}.", style="title")
P("upi_p2m_06", "upi_p2m", "upi",
  "₹{amt} paid to {merchant} on {date} at {time}. {app} UPI Ref {utr}.",
  style="title")
P("upi_p2m_07", "upi_p2m", "upi",
  "{app}: Payment of ₹{amt} to {merchant} is successful. UPI ID {vpa}. "
  "Ref {utr}.", style="title")
P("upi_p2m_08", "upi_p2m", "upi",
  "To: {merchant}\n₹{amt}\n{app} UPI transaction ID {utr}\n"
  "From {bank} {acct}\nCompleted", style="upper")

# --- UPI person-to-person -----------------------------------------------------
P("upi_p2p_01", "upi_p2p", "upi",
  "{app}: Sent ₹{amt} to {person} ({vpa}). UPI transaction ID {utr}.",
  style="title", method="UPI", fixed="Uncategorized", merchant_kind="person")
P("upi_p2p_02", "upi_p2p", "upi",
  "₹{amt} sent to {person} on {app}. UPI Ref {utr}. From {bank} A/c {acct}.",
  style="title", fixed="Uncategorized", merchant_kind="person")
P("upi_p2p_03", "upi_p2p", "upi",
  "{app}: ₹{amt} paid to {person}. UPI Ref {utr}. Completed on {date}.",
  style="title", fixed="Uncategorized", merchant_kind="person")
P("upi_p2p_04", "upi_p2p", "upi",
  "Paid ₹{amt} to {person} ({vpa}) via {app}. Txn ID {utr}.", style="title",
  fixed="Uncategorized", merchant_kind="person")

# --- UPI app notifications (notification-tray shape) ---------------------------
# These are the messages the app actually listens to: the UPI app's own banner, with NO
# bank account number and NO UTR. They exist because the earlier dataset represented almost
# every positive as a bank SMS with A/c + Ref, which taught the gate that a real payment
# must carry a reference id. Real Google Pay / PhonePe / Paytm banners usually do not.
P("upi_notif_01", "upi_notif", "upi",
  "Paid ₹{amt} to {merchant}", style="title", difficulty="medium")
P("upi_notif_02", "upi_notif", "upi",
  "You paid ₹{amt} to {merchant}", style="title", difficulty="medium")
P("upi_notif_03", "upi_notif", "upi",
  "Payment of ₹{amt} to {merchant} is successful", style="title",
  difficulty="medium")
P("upi_notif_04", "upi_notif", "upi",
  "Payment successful! ₹{amt} sent to {merchant}", style="title",
  difficulty="medium")
P("upi_notif_05", "upi_notif", "upi",
  "₹{amt} paid to {merchant}. Completed", style="title", difficulty="medium")
P("upi_notif_06", "upi_notif", "upi",
  "₹{amt} to {merchant} · {date}", style="title", difficulty="medium")
P("upi_notif_07", "upi_notif", "upi",
  "Paid ₹{amt} to {merchant} ({vpa})", style="title", difficulty="medium")
P("upi_notif_08", "upi_notif", "upi",
  "{merchant} ko ₹{amt} bheja", style="title", difficulty="medium")
P("upi_notif_09", "upi_notif", "upi",
  "{merchant} ko ₹{amt} ka payment ho gaya", style="title", difficulty="medium")
P("upi_notif_10", "upi_notif", "upi",
  "Sent ₹{amt} to {person}", style="title", fixed="Uncategorized",
  merchant_kind="person", difficulty="medium")
P("upi_notif_11", "upi_notif", "upi",
  "You received ₹{amt} from {person}", style="title", txn="CREDIT",
  fixed="Gift", merchant_kind="person", difficulty="medium")
P("upi_notif_12", "upi_notif", "upi",
  "Refund of ₹{amt} from {merchant} processed", style="title", txn="CREDIT",
  fixed="Refund", difficulty="medium")

# --- Merchant app payment receipts ---------------------------------------------
# Checkout receipts pushed by the merchant's own app. Also reference-free; the merchant is
# derived from the package name at runtime. Only payment wording is used — "order placed"
# is deliberately NOT a positive, because it can precede payment (COD) and would otherwise
# teach the gate to log unbilled orders.
P("mapp_01", "merchant_app", "merchant_app",
  "Payment successful. ₹{amt} paid", difficulty="medium", merchant_kind="none")
P("mapp_02", "merchant_app", "merchant_app",
  "Your payment of ₹{amt} was successful", difficulty="medium",
  merchant_kind="none")
P("mapp_03", "merchant_app", "merchant_app",
  "Payment of ₹{amt} confirmed. Thank you!", difficulty="medium",
  merchant_kind="none")
P("mapp_04", "merchant_app", "merchant_app",
  "₹{amt} paid · Payment successful", difficulty="medium", merchant_kind="none")
P("mapp_05", "merchant_app", "merchant_app",
  "Order delivered · ₹{amt} paid", difficulty="medium", merchant_kind="none")
P("mapp_06", "merchant_app", "merchant_app",
  "Payment done: ₹{amt}", difficulty="medium", merchant_kind="none")
P("mapp_07", "merchant_app", "merchant_app",
  "Payment successful — ₹{amt}", difficulty="medium", merchant_kind="none")
P("mapp_08", "merchant_app", "merchant_app",
  "₹{amt} payment completed", difficulty="medium", merchant_kind="none")

# --- Card spends --------------------------------------------------------------
P("card_01", "card", "card",
  "{bank}: Rs.{amt} spent on Credit Card ending {acct} at {merchant} on {date}. "
  "Avl limit Rs.{limit}.", method="CARD")
P("card_02", "card", "card",
  "{bank} Card Alert: INR {amt} spent at {merchant} on {date}. Card XX{acct}. "
  "Available limit INR {limit}.", method="CARD")
P("card_03", "card", "card",
  "Your {bank} Credit Card XX{acct} was used for Rs.{amt} at {merchant}. "
  "Avl limit Rs.{limit}.", method="CARD")
P("card_04", "card", "card",
  "{bank}: Rs.{amt} spent at {merchant} using Card XX{acct} on {date}. "
  "Ref {utr}.", method="CARD")
P("card_05", "card", "card",
  "Card txn of Rs.{amt} at {merchant} approved. {bank} Card XX{acct}. "
  "Avl limit Rs.{limit}. Ref {utr}.", method="CARD")

# --- ATM withdrawals ----------------------------------------------------------
P("atm_01", "atm", "atm",
  "{bank}: Rs.{amt} withdrawn from A/c XX{acct} at ATM {city} on {date}. "
  "Avl Bal Rs.{bal}.", fixed="Uncategorized", method="ATM",
  merchant_kind="atm", difficulty="medium", bounds=(100, 20000))
P("atm_02", "atm", "atm",
  "ATM cash withdrawal of Rs.{amt} from A/c XX{acct} at {atm}, {city}. "
  "Bal Rs.{bal}. Ref {utr}.", fixed="Uncategorized", method="ATM",
  merchant_kind="atm", difficulty="medium", bounds=(100, 20000))
P("atm_03", "atm", "atm",
  "{bank}: INR {amt} withdrawn using Card XX{acct} at {city} ATM on {date}. "
  "Available Balance INR {bal}.", fixed="Uncategorized", method="ATM",
  merchant_kind="atm", difficulty="medium", bounds=(100, 20000))

# --- NEFT / IMPS / RTGS -------------------------------------------------------
P("trf_01", "transfer", "bank_sms",
  "{bank}: NEFT of Rs.{amt} to {merchant} successful. Ref {utr}. "
  "A/c XX{acct} debited. Bal Rs.{bal}.", method="NEFT", difficulty="medium")
P("trf_02", "transfer", "bank_sms",
  "{bank}: IMPS transfer of Rs.{amt} from A/c XX{acct} to {merchant} successful "
  "on {date}. Ref {utr}.", method="IMPS", difficulty="medium")
P("trf_03", "transfer", "bank_sms",
  "RTGS transfer of Rs.{amt} from {bank} A/c XX{acct} to {merchant} completed. "
  "UTR {utr}.", method="RTGS", difficulty="medium")
P("trf_04", "transfer", "bank_sms",
  "{bank}: Rs.{amt} transferred to {merchant} via IMPS. Ref {utr}. "
  "Avl Bal Rs.{bal}.", method="IMPS", difficulty="medium")

# --- Bills and recharges ------------------------------------------------------
P("bill_01", "bill", "bank_sms",
  "{bank}: Bill payment of Rs.{amt} to {merchant} successful. A/c XX{acct}. "
  "Ref {utr}.", cats=("Bills & Utilities",), method="NETBANKING")
P("bill_02", "bill", "upi",
  "{app}: ₹{amt} paid to {merchant} for your bill. UPI Ref {utr}.",
  cats=("Bills & Utilities",), style="title")
P("bill_03", "bill", "bank_sms",
  "{bank}: Rs.{amt} paid towards {merchant} bill from A/c XX{acct} on {date}. "
  "Ref {utr}.", cats=("Bills & Utilities",))
P("bill_04", "bill", "bank_sms",
  "Recharge of Rs.{amt} for {merchant} successful. {bank} A/c XX{acct}. "
  "Ref {utr}.", cats=("Bills & Utilities",))

# --- Wallet loads -------------------------------------------------------------
P("wal_01", "wallet", "wallet",
  "₹{amt} added to your {app} wallet from {bank} A/c XX{acct}. Ref {utr}.",
  fixed="Uncategorized", method="WALLET", style="title", difficulty="medium")
P("wal_02", "wallet", "wallet",
  "{app}: Wallet loaded with ₹{amt} from {bank}. Txn ID {utr}.",
  fixed="Uncategorized", method="WALLET", style="title", difficulty="medium")
P("wal_03", "wallet", "wallet",
  "{app}: ₹{amt} added to wallet on {date}. Ref {utr}. From {bank} {acct}.",
  fixed="Uncategorized", method="WALLET", style="title", difficulty="medium")

# --- Bank charges -------------------------------------------------------------
P("chg_01", "charges", "bank_sms",
  "{bank}: Rs.{amt} debited towards charges for A/c XX{acct} on {date}. "
  "Ref {utr}.", fixed="Uncategorized", difficulty="medium", bounds=(2, 200),
  merchant_kind="none")
P("chg_02", "charges", "bank_sms",
  "{bank}: Annual fee of Rs.{amt} charged on Card XX{acct}. Ref {utr}. "
  "Bal Rs.{bal}.", fixed="Uncategorized", method="CARD", difficulty="medium",
  bounds=(499, 2500), merchant_kind="none")
P("chg_03", "charges", "bank_sms",
  "{bank}: Rs.{amt} debited towards card charges on A/c XX{acct}. "
  "Avl Bal Rs.{bal}.", fixed="Uncategorized", difficulty="medium",
  bounds=(10, 500), merchant_kind="none")

# --- Screenshot / OCR receipts ------------------------------------------------
P("ocr_01", "ocr", "screenshot",
  "{app}\nPaid ₹{amt} to {merchant}\nUPI transaction ID\n{utr}\n"
  "To: {merchant}\n{bank} {acct}\nCompleted\n{date2}",
  style="upper", method="UPI", difficulty="ocr")
P("ocr_02", "ocr", "screenshot",
  "{app}\nPayment Successful\n₹{amt}\nTo {merchant}\nUPI Ref {utr}\n"
  "From {bank} A/c {acct}\n{time}", style="upper", method="UPI",
  difficulty="ocr")
P("ocr_03", "ocr", "screenshot",
  "Payment Receipt\nAmount: ₹{amt}\nPaid to: {merchant}\nUPI Ref: {utr}\n"
  "Bank: {bank}\nA/c: XX{acct}\nStatus: Success", style="upper",
  method="UPI", difficulty="ocr")
P("ocr_04", "ocr", "screenshot",
  "{app}\n₹{amt}\nSent to {merchant}\n{date} {time}\n"
  "Transaction ID {utr}\nCompleted", style="upper", method="UPI",
  difficulty="ocr")
P("ocr_05", "ocr", "screenshot",
  "{bank}\nTransaction Alert\n₹{amt} debited\n{merchant}\nA/c XX{acct}\n"
  "Avl Bal ₹{bal}\nRef {utr}", style="upper", method="UPI", difficulty="ocr")
P("ocr_06", "ocr", "screenshot",
  "{app}\nRefund Successful\n₹{amt}\nFrom {merchant}\nUPI Ref {utr}\n"
  "Credited to {bank} A/c XX{acct}", txn="CREDIT", fixed="Refund",
  style="upper", method="UPI", difficulty="ocr")

# --- Hinglish / transliterated messages --------------------------------------
P("hin_01", "hinglish", "bank_sms",
  "{bank}: Aapke A/c XX{acct} se Rs.{amt} debit ho gaye hain on {date} to "
  "{merchant}. Avl Bal Rs.{bal}. Ref {utr}.", difficulty="medium")
P("hin_02", "hinglish", "upi",
  "{app} se ₹{amt} bheja {merchant} ko. UPI Ref {utr}.", style="title",
  difficulty="medium")
P("hin_03", "hinglish", "bank_sms",
  "{bank}: A/c XX{acct} me Rs.{amt} credit hua {merchant} se. Bal Rs.{bal}. "
  "Ref {utr}.", txn="CREDIT", difficulty="medium")
P("hin_04", "hinglish", "upi",
  "{merchant} ko ₹{amt} ka payment ho gaya. {app} UPI Ref {utr}. A/c {acct}.",
  style="title", difficulty="medium")
P("hin_05", "hinglish", "bank_sms",
  "{bank}: Rs.{amt} ka txn {merchant} par hua. A/c XX{acct}. "
  "Bal Rs.{bal}. Ref {utr}.", difficulty="medium")
P("hin_06", "hinglish", "upi",
  "{app}: ₹{amt} bheja {person} ko. UPI Ref {utr}.", fixed="Uncategorized",
  style="title", difficulty="medium", merchant_kind="person")

# --- Forwarded bank alerts inside messaging apps -----------------------------
P("msg_01", "messaging", "messaging",
  "Fwd: {bank}: Rs.{amt} debited from A/c XX{acct} on {date} to {merchant}. "
  "Avl Bal Rs.{bal}. Ref {utr}.", difficulty="hard")
P("msg_02", "messaging", "messaging",
  "Fwd - {bank}: INR {amt} credited to A/c XX{acct} from {merchant} on {date}. "
  "Ref {utr}.", txn="CREDIT", difficulty="hard")
P("msg_03", "messaging", "messaging",
  "Sharing bank alert: Rs.{amt} debited from {bank} A/c XX{acct} at {merchant}. "
  "UPI Ref {utr}.", difficulty="hard")

# --- Salary -------------------------------------------------------------------
P("sal_01", "salary", "bank_sms",
  "{bank}: Salary of Rs.{amt} credited to A/c XX{acct} on {date} by {merchant}. "
  "Avl Bal Rs.{bal}.", txn="CREDIT", fixed="Salary", method="NEFT")
P("sal_02", "salary", "bank_sms",
  "{bank}: INR {amt} credited towards salary from {merchant} to A/c XX{acct} on "
  "{date}. Ref {utr}.", txn="CREDIT", fixed="Salary", method="NEFT")
P("sal_03", "salary", "bank_app",
  "Salary credit: Rs.{amt} from {merchant} in your {bank} A/c XX{acct}. "
  "Avl Bal Rs.{bal}. Ref {utr}.", txn="CREDIT", fixed="Salary", method="NEFT")

# --- Refunds ------------------------------------------------------------------
P("ref_01", "refund", "bank_sms",
  "{bank}: Refund of Rs.{amt} credited to A/c XX{acct} from {merchant} on "
  "{date}. Ref {utr}.", txn="CREDIT", fixed="Refund")
P("ref_02", "refund", "bank_sms",
  "{bank}: Rs.{amt} refunded by {merchant} to your A/c XX{acct}. "
  "UPI Ref {utr}. Avl Bal Rs.{bal}.", txn="CREDIT", fixed="Refund")
P("ref_03", "refund", "bank_sms",
  "Refund received: INR {amt} from {merchant} in A/c XX{acct}. Ref {utr}.",
  txn="CREDIT", fixed="Refund")

# --- Gift credits -------------------------------------------------------------
P("gift_01", "gift", "bank_sms",
  "{bank}: Rs.{amt} credited to A/c XX{acct} from {merchant} as gift. "
  "Ref {utr}. Bal Rs.{bal}.", txn="CREDIT", fixed="Gift")
P("gift_02", "gift", "upi",
  "{app}: You received ₹{amt} from {merchant}. UPI Ref {utr}.",
  txn="CREDIT", fixed="Gift", style="title")
P("gift_03", "gift", "bank_sms",
  "Gift credit of Rs.{amt} from {merchant} in A/c XX{acct}. Ref {utr}.",
  txn="CREDIT", fixed="Gift")

# --- Investment returns -------------------------------------------------------
P("inv_01", "invret", "bank_sms",
  "{bank}: Interest of Rs.{amt} credited to A/c XX{acct} from {merchant}. "
  "Ref {utr}. Bal Rs.{bal}.", txn="CREDIT", fixed="Investment Returns",
  method="INTEREST")
P("inv_02", "invret", "bank_sms",
  "{bank}: Rs.{amt} credited as investment returns from {merchant} to "
  "A/c XX{acct} on {date}. Ref {utr}.", txn="CREDIT",
  fixed="Investment Returns", method="INTEREST")
P("inv_03", "invret", "bank_sms",
  "Dividend of Rs.{amt} credited from {merchant} to A/c XX{acct}. Ref {utr}.",
  txn="CREDIT", fixed="Investment Returns", method="INTEREST")
