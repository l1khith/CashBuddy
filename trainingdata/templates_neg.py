"""Negative templates: false flags and ordinary notifications."""

from __future__ import annotations

from vocab import EXPENSE_CATEGORIES

TEMPLATE_VERSION = "neg-v1"

NEGATIVE_TEMPLATES: list[dict] = []


def N(tid: str, source: str, fmt: str, *, negative_type: str,
      status: str | None = None, fin: bool = False,
      cats: tuple[str, ...] = EXPENSE_CATEGORIES,
      merchant_kind: str = "merchant", difficulty: str = "hard",
      txn: str | None = None, promo: bool = False,
      bounds: tuple[float, float] | None = None) -> None:
    NEGATIVE_TEMPLATES.append(dict(
        tid=tid, source=source, fmt=fmt, negative_type=negative_type,
        status=status, is_financial_event=fin, cats=tuple(cats),
        merchant_kind=merchant_kind, difficulty=difficulty, txn=txn,
        is_promo=promo, bounds=bounds,
    ))


# --- OTP (contains an amount, must never create a transaction) ----------------
N("otp_01", "bank_sms",
  "{bank}: {otp} is your OTP for txn of Rs.{amt} at {merchant}. "
  "Do not share with anyone. Valid for {mins} mins.", negative_type="OTP",
  txn="DEBIT", difficulty="hard")
N("otp_02", "bank_sms",
  "{otp} is your {bank} OTP for login. Valid for {mins} minutes. Do not share.",
  negative_type="OTP", difficulty="medium")
N("otp_03", "bank_sms",
  "{bank}: Use OTP {otp} to complete payment of Rs.{amt} to {merchant}. "
  "Valid {mins} mins.", negative_type="OTP", txn="DEBIT")
N("otp_04", "bank_sms",
  "Your OTP for {bank} Credit Card XX{acct} txn of INR {amt} at {merchant} "
  "is {otp}. Expires in {mins} mins.", negative_type="OTP", txn="DEBIT")
N("otp_05", "bank_sms",
  "{bank}: OTP {otp} for adding beneficiary. Amount limit Rs.{amt}. "
  "Never share OTP.", negative_type="OTP", txn="DEBIT")
N("otp_06", "bank_sms",
  "Do not share OTP {otp}. {bank} will never ask for it. Txn of Rs.{amt} at "
  "{merchant} is pending verification.", negative_type="OTP", txn="DEBIT")
N("otp_07", "bank_sms",
  "{bank}: {otp} is your verification code for UPI registration. "
  "Valid {mins} mins.", negative_type="OTP", difficulty="medium")
N("otp_08", "bank_sms",
  "OTP {otp} for your {bank} A/c XX{acct} txn of Rs.{amt}. "
  "Valid for {mins} minutes.", negative_type="OTP", txn="DEBIT")
N("otp_09", "upi",
  "{app}: {otp} is your OTP for payment of ₹{amt} to {merchant}. "
  "Do not share.", negative_type="OTP", txn="DEBIT")
N("otp_10", "bank_sms",
  "{bank}: {otp} OTP for resetting your net banking password. Valid {mins} "
  "mins. Not for any transaction.", negative_type="OTP", difficulty="medium")

# --- Merchant promos / offers -------------------------------------------------
N("promo_01", "bank_sms",
  "{bank}: Get {pct}% cashback up to Rs.{amt} on {merchant}. "
  "Offer valid till {date}. T&C apply.", negative_type="PROMO", promo=True)
N("promo_02", "bank_sms",
  "{bank}: Flat Rs.{amt} off on your next {merchant} order. "
  "Use code SAVE{pct}. Valid till {date}.", negative_type="PROMO", promo=True)
N("promo_03", "upi",
  "{app}: Win cashback up to ₹{amt} on payments to {merchant}. "
  "Offer ends {date}. T&C.", negative_type="PROMO", promo=True)
N("promo_04", "bank_sms",
  "{bank} Credit Card: Earn {pct}X reward points on {merchant}. "
  "Limited period offer. T&C.", negative_type="PROMO", promo=True)
N("promo_05", "bank_sms",
  "Shop for Rs.{amt} or more at {merchant} and get {pct}% cashback. "
  "Offer valid till {date}.", negative_type="PROMO", promo=True)
N("promo_06", "bank_sms",
  "{bank}: Pre-approved personal loan up to Rs.{amt} at {pct}% interest. "
  "Apply now. T&C.", negative_type="PROMO", promo=True)
N("promo_07", "bank_sms",
  "{bank}: Upgrade your credit card and get Rs.{amt} worth vouchers. "
  "Offer till {date}.", negative_type="PROMO", promo=True)
N("promo_08", "upi",
  "{app}: Refer a friend and earn ₹{amt}. Limited time offer.",
  negative_type="PROMO", promo=True)
N("promo_09", "bank_sms",
  "{bank}: No-cost EMI available on {merchant} purchases above Rs.{amt}. "
  "Know more.", negative_type="PROMO", promo=True)
N("promo_10", "bank_sms",
  "{bank}: Congratulations! You are eligible for a credit limit of Rs.{amt}. "
  "Activate now.", negative_type="PROMO", promo=True)
N("promo_11", "bank_app",
  "{merchant}: Mega sale! Up to {pct}% off. Minimum order Rs.{amt}. "
  "Ends {date}.", negative_type="PROMO", promo=True)
N("promo_12", "bank_sms",
  "{bank}: Pay your {merchant} bill before {date} and get Rs.{amt} cashback.",
  negative_type="PROMO", promo=True, bounds=(49, 2000))
N("promo_13", "bank_sms",
  "{bank}: Insurance cover starting at Rs.{amt}/month. Limited offer. T&C.",
  negative_type="PROMO", promo=True, bounds=(199, 5000))
N("promo_14", "upi",
  "{app}: Scratch card unlocked! You can win up to ₹{amt}. Tap to play.",
  negative_type="PROMO", promo=True)

# --- Bank service promos / KYC / product offers -------------------------------
N("bpromo_01", "bank_sms",
  "{bank}: Open a fixed deposit at {pct}% p.a. and get assured returns. "
  "Invest now. T&C.", negative_type="BANK_PROMO", difficulty="medium",
  promo=True)
N("bpromo_02", "bank_sms",
  "{bank}: Apply for a personal loan up to Rs.{amt} at {pct}% p.a. "
  "Instant approval.", negative_type="BANK_PROMO", difficulty="medium",
  promo=True)
N("bpromo_03", "bank_sms",
  "{bank}: Get {pct}% cashback on your first credit card bill payment. "
  "Offer till {date}.", negative_type="BANK_PROMO", difficulty="medium",
  promo=True, bounds=(49, 2000))
N("bpromo_04", "bank_sms",
  "{bank}: Update your KYC before {date} to avoid service interruption.",
  negative_type="BANK_PROMO", difficulty="medium", promo=True)
N("bpromo_05", "bank_sms",
  "{bank}: Enable UPI Lite for small payments. No PIN required below "
  "Rs.{amt}.", negative_type="BANK_PROMO", difficulty="medium", promo=True,
  bounds=(100, 1000))
N("bpromo_06", "bank_sms",
  "{bank}: Your KYC is due for renewal. Visit branch or app. "
  "Ignore if done.", negative_type="BANK_PROMO", difficulty="medium",
  promo=True)
N("bpromo_07", "bank_sms",
  "{bank}: Get a pre-approved credit card with limit up to Rs.{amt}. "
  "Apply in app.", negative_type="BANK_PROMO", difficulty="medium",
  promo=True)
N("bpromo_08", "bank_sms",
  "{bank}: Invest in mutual funds starting at Rs.{amt}. "
  "No account charges. T&C.", negative_type="BANK_PROMO",
  difficulty="medium", promo=True)

# --- Balance-only alerts ------------------------------------------------------
N("bal_01", "bank_sms",
  "{bank}: Your A/c XX{acct} balance is Rs.{bal} as on {date}. "
  "Ignore if already checked.", negative_type="BALANCE_ONLY",
  difficulty="medium")
N("bal_02", "bank_sms",
  "{bank}: Avl Bal in A/c XX{acct} is INR {bal}. "
  "No transaction in this period.", negative_type="BALANCE_ONLY",
  difficulty="medium")
N("bal_03", "bank_sms",
  "Balance update: {bank} A/c XX{acct} has Rs.{bal}. "
  "Check statement for details.", negative_type="BALANCE_ONLY",
  difficulty="medium")
N("bal_04", "bank_sms",
  "{bank}: Your credit card XX{acct} has an available limit of Rs.{limit}. "
  "Total outstanding Rs.{bal}.", negative_type="BALANCE_ONLY",
  difficulty="medium")
N("bal_05", "bank_sms",
  "{bank}: A/c XX{acct} balance Rs.{bal}. Last credit Rs.{amt}. "
  "For disputes call 1800{phone6}.", negative_type="BALANCE_ONLY",
  difficulty="hard")
N("bal_06", "upi",
  "{app}: Your wallet balance is ₹{bal}. Add money to continue.",
  negative_type="BALANCE_ONLY", difficulty="medium")
N("bal_07", "bank_sms",
  "{bank}: Minimum balance requirement met. A/c XX{acct} balance Rs.{bal}.",
  negative_type="BALANCE_ONLY", difficulty="medium")
N("bal_08", "bank_sms",
  "{bank}: Your account balance as of {date} is Rs.{bal}. "
  "Thank you for banking with us.", negative_type="BALANCE_ONLY",
  difficulty="medium")

# --- Statements / bills due ---------------------------------------------------
N("stmt_01", "bank_sms",
  "{bank}: Your credit card statement for XX{acct} is ready. "
  "Total due Rs.{amt}, minimum due Rs.{min}. Due date {date}.",
  negative_type="STATEMENT", difficulty="medium")
N("stmt_02", "bank_sms",
  "{bank}: Statement for A/c XX{acct} for {month} is available. "
  "Total spends Rs.{amt}. View in app.", negative_type="STATEMENT",
  difficulty="medium")
N("stmt_03", "bank_sms",
  "{bank}: Your e-statement is ready. Closing balance Rs.{bal}. "
  "Password is your DOB.", negative_type="STATEMENT", difficulty="medium")
N("stmt_04", "bank_sms",
  "{bank}: Credit card bill of Rs.{amt} due on {date}. "
  "Pay now to avoid late fee.", negative_type="STATEMENT",
  difficulty="medium")
N("stmt_05", "bank_sms",
  "{bank}: Annual account summary for A/c XX{acct}. Total credits Rs.{amt}, "
  "total debits Rs.{bal}.", negative_type="STATEMENT", difficulty="medium")

# --- Credit score alerts ------------------------------------------------------
N("score_01", "email",
  "{bureau}: Your credit score is {score}. Check your report for details. "
  "No impact on score.", negative_type="CREDIT_SCORE", difficulty="easy")
N("score_02", "generic",
  "{bureau}: Your score changed to {score} this month. "
  "View report in app.", negative_type="CREDIT_SCORE", difficulty="easy")
N("score_03", "generic",
  "Your credit score is {score}, better than {pct}% of users. "
  "Check {bureau} report.", negative_type="CREDIT_SCORE", difficulty="easy")
N("score_04", "email",
  "{bureau}: Score alert - your credit score is now {score}. "
  "Log in to view factors.", negative_type="CREDIT_SCORE", difficulty="easy")

# --- Chat summaries and forwarded chat noise ---------------------------------
N("chat_01", "chat",
  "{count} messages from {chats} chats {sender}: {preview}",
  negative_type="CHAT_SUMMARY", difficulty="hard")
N("chat_02", "chat", "{sender}: {preview}",
  negative_type="CHAT_SUMMARY", difficulty="hard")
N("chat_03", "chat", "{count} new messages\n{sender}: {preview}",
  negative_type="CHAT_SUMMARY", difficulty="hard")
N("chat_04", "chat", "WhatsApp: {sender} sent a message: {preview}",
  negative_type="CHAT_SUMMARY", difficulty="hard")
N("chat_05", "chat",
  "{count} messages from {chats} chats\n{sender}: {preview}\n"
  "{sender2}: {preview2}", negative_type="CHAT_SUMMARY", difficulty="hard")
N("chat_06", "chat", "{sender}: {preview} (Forwarded many times)",
  negative_type="CHAT_SUMMARY", difficulty="hard")
N("chat_07", "chat",
  "You have {count} unread messages from {chats} chats. {sender}: {preview}",
  negative_type="CHAT_SUMMARY", difficulty="hard")
N("chat_08", "chat", "{sender}: {preview}\nTap to open chat",
  negative_type="CHAT_SUMMARY", difficulty="hard")

# --- Social app notifications -------------------------------------------------
N("social_01", "social", "Instagram: {username} sent you a reel.",
  negative_type="SOCIAL", difficulty="easy")
N("social_02", "social", "Instagram: {username} liked your post.",
  negative_type="SOCIAL", difficulty="easy")
N("social_03", "social",
  "You have {count} new notifications from {app}.",
  negative_type="SOCIAL", difficulty="easy")
N("social_04", "social",
  "New comment from {username}: {comment}",
  negative_type="SOCIAL", difficulty="easy")
N("social_05", "social", "{username} started following you.",
  negative_type="SOCIAL", difficulty="easy")
N("social_06", "social",
  "Reddit: {count} new posts in r/{subreddit}.",
  negative_type="SOCIAL", difficulty="easy")
N("social_07", "social", "{username} mentioned you in a comment.",
  negative_type="SOCIAL", difficulty="easy")
N("social_08", "social",
  "YouTube: {channel} uploaded: {video}",
  negative_type="SOCIAL", difficulty="easy")
N("social_09", "social", "X: {username} posted: {tweet}",
  negative_type="SOCIAL", difficulty="easy")
N("social_10", "social", "Snapchat: {username} sent you a snap.",
  negative_type="SOCIAL", difficulty="easy")

# --- Email and job alerts -----------------------------------------------------
N("news_01", "email", "Gmail: {sender_name} - {subject}",
  negative_type="NEWS_JOB", difficulty="easy")
N("news_02", "linkedin",
  "LinkedIn: {count} new jobs match your profile. {role} at {company}.",
  negative_type="NEWS_JOB", difficulty="easy")
N("news_03", "linkedin",
  "Indeed: {role} at {company} in {city}. Apply now.",
  negative_type="NEWS_JOB", difficulty="easy")
N("news_04", "generic", "Google News: {headline} | {month}",
  negative_type="NEWS_JOB", difficulty="easy")
N("news_05", "linkedin",
  "Naukri: {count} recruiters from {company} viewed your profile.",
  negative_type="NEWS_JOB", difficulty="easy")
N("news_06", "email", "Gmail: {subject} - {preview}",
  negative_type="NEWS_JOB", difficulty="easy")

# --- Generic app notifications ------------------------------------------------
N("gen_01", "generic", "{app}: Now playing {song} by {artist}.",
  negative_type="GENERIC", difficulty="easy")
N("gen_02", "generic", "{app}: {count} new songs added to your playlist.",
  negative_type="GENERIC", difficulty="easy")
N("gen_03", "generic", "{app}: Your alarm is set for {time}.",
  negative_type="GENERIC", difficulty="easy")
N("gen_04", "generic", "{app}: Battery at {count}%. Charge your phone.",
  negative_type="GENERIC", difficulty="easy")
N("gen_05", "generic", "{app}: {count} new levels available. Play now.",
  negative_type="GENERIC", difficulty="easy")
N("gen_06", "generic", "{app}: Your order #{count} is out for delivery.",
  negative_type="GENERIC", difficulty="easy")
N("gen_07", "generic", "{app}: Update {version} available. Tap to install.",
  negative_type="GENERIC", difficulty="easy")
N("gen_08", "generic", "{app}: {count} new episodes available.",
  negative_type="GENERIC", difficulty="easy")

# --- Failed transactions ------------------------------------------------------
N("fail_01", "bank_sms",
  "{bank}: Txn of Rs.{amt} to {merchant} failed on {date}. "
  "Amount will be reversed within {days} days. Ref {utr}.",
  negative_type="FAILED_TXN", status="FAILED", fin=True, txn="DEBIT")
N("fail_02", "bank_sms",
  "{bank}: Payment of INR {amt} to {merchant} could not be completed. "
  "No amount debited. Ref {utr}.", negative_type="FAILED_TXN",
  status="FAILED", fin=True, txn="DEBIT")
N("fail_03", "bank_sms",
  "{bank}: UPI txn of Rs.{amt} to {merchant} declined due to insufficient "
  "balance. Ref {utr}.", negative_type="FAILED_TXN", status="FAILED",
  fin=True, txn="DEBIT")
N("fail_04", "upi",
  "{app}: ₹{amt} payment to {merchant} failed. Try again. UPI Ref {utr}.",
  negative_type="FAILED_TXN", status="FAILED", fin=True, txn="DEBIT")
N("fail_05", "bank_sms",
  "{bank}: Your txn at {merchant} for Rs.{amt} was unsuccessful. "
  "Card XX{acct}. Ref {utr}.", negative_type="FAILED_TXN", status="FAILED",
  fin=True, txn="DEBIT")
N("fail_06", "bank_sms",
  "{bank}: Failed txn of Rs.{amt} on A/c XX{acct} at {merchant}. "
  "If debited, will be reversed in {days} days.", negative_type="FAILED_TXN",
  status="FAILED", fin=True, txn="DEBIT")

# --- Pending transactions -----------------------------------------------------
N("pend_01", "bank_sms",
  "{bank}: Txn of Rs.{amt} to {merchant} is pending. We will update you once "
  "processed. Ref {utr}.", negative_type="PENDING_TXN", status="PENDING",
  fin=True, txn="DEBIT")
N("pend_02", "upi",
  "{app}: Payment of ₹{amt} to {merchant} is processing. Do not retry. "
  "UPI Ref {utr}.", negative_type="PENDING_TXN", status="PENDING",
  fin=True, txn="DEBIT")
N("pend_03", "bank_sms",
  "{bank}: Rs.{amt} debit to {merchant} is on hold. Ref {utr}.",
  negative_type="PENDING_TXN", status="PENDING", fin=True, txn="DEBIT")
N("pend_04", "bank_sms",
  "{bank}: Your UPI txn of INR {amt} to {merchant} is pending confirmation. "
  "Ref {utr}.", negative_type="PENDING_TXN", status="PENDING", fin=True,
  txn="DEBIT")
N("pend_05", "upi",
  "{app}: ₹{amt} to {merchant} is pending at bank. Usually takes {mins} "
  "minutes.", negative_type="PENDING_TXN", status="PENDING", fin=True,
  txn="DEBIT")

# --- Reversals ----------------------------------------------------------------
N("rev_01", "bank_sms",
  "{bank}: Txn of Rs.{amt} to {merchant} was reversed. Rs.{amt} credited back "
  "to A/c XX{acct}. Ref {utr}.", negative_type="REVERSED",
  status="REVERSED", fin=True, txn="DEBIT")
N("rev_02", "bank_sms",
  "{bank}: Reversal of INR {amt} for txn at {merchant} completed. "
  "Avl Bal Rs.{bal}. Ref {utr}.", negative_type="REVERSED",
  status="REVERSED", fin=True, txn="DEBIT")
N("rev_03", "upi",
  "{app}: ₹{amt} refunded to source for failed payment to {merchant}. "
  "UPI Ref {utr}.", negative_type="REVERSED", status="REVERSED", fin=True,
  txn="DEBIT")
N("rev_04", "bank_sms",
  "{bank}: Your disputed txn of Rs.{amt} at {merchant} has been reversed. "
  "Ref {utr}.", negative_type="REVERSED", status="REVERSED", fin=True,
  txn="DEBIT")

# --- Collect requests ---------------------------------------------------------
N("coll_01", "upi",
  "{app}: {merchant} has requested ₹{amt}. Approve to pay. UPI Ref {utr}.",
  negative_type="COLLECT_REQUEST", status="REQUESTED", fin=True, txn="DEBIT")
N("coll_02", "upi",
  "{app}: Payment request of ₹{amt} from {person}. Open app to approve.",
  negative_type="COLLECT_REQUEST", status="REQUESTED", fin=True, txn="DEBIT",
  merchant_kind="person")
N("coll_03", "bank_sms",
  "{bank}: Collect request of Rs.{amt} from {merchant} is pending. "
  "Do not approve if not expected.", negative_type="COLLECT_REQUEST",
  status="REQUESTED", fin=True, txn="DEBIT")
N("coll_04", "upi",
  "{app}: {person} requested ₹{amt}. Request expires in {mins} minutes.",
  negative_type="COLLECT_REQUEST", status="REQUESTED", fin=True,
  txn="DEBIT", merchant_kind="person")

# --- Mandates -----------------------------------------------------------------
N("mand_01", "bank_sms",
  "{bank}: Mandate of Rs.{amt} created for {merchant}. Auto-debit will start "
  "from {date}. Ref {utr}.", negative_type="MANDATE", status="MANDATE",
  fin=True, txn="DEBIT")
N("mand_02", "upi",
  "{app}: Autopay mandate for ₹{amt} to {merchant} is active. Manage in app.",
  negative_type="MANDATE", status="MANDATE", fin=True, txn="DEBIT")
N("mand_03", "bank_sms",
  "{bank}: UPI mandate of Rs.{amt} for {merchant} registered. Ref {utr}.",
  negative_type="MANDATE", status="MANDATE", fin=True, txn="DEBIT")
N("mand_04", "bank_sms",
  "{bank}: e-mandate for {merchant} of INR {amt} is pending authentication. "
  "Ref {utr}.", negative_type="MANDATE", status="MANDATE", fin=True,
  txn="DEBIT")

# --- Reward points ------------------------------------------------------------
N("rew_01", "upi",
  "{app}: You earned {count} reward points on your last payment to {merchant}.",
  negative_type="REWARD", difficulty="easy")
N("rew_02", "bank_sms",
  "{bank}: {count} reward points credited to your card XX{acct}. Redeem now.",
  negative_type="REWARD", difficulty="easy")
N("rew_03", "upi",
  "{app}: Congratulations! You won {count} coins. "
  "Use them on your next order.", negative_type="REWARD", difficulty="easy")
N("rew_04", "bank_sms",
  "{bank}: You have {count} reward points expiring on {date}. Redeem now.",
  negative_type="REWARD", difficulty="easy")
