// NO-NETWORK
package com.cashbuddy.core.prob

object Regexes {
    const val CURRENCY_PREFIX = """(?:₹|Rs[.:]?|INR|[$€£¥]|USD|EUR|GBP|CAD|AUD|AED|SGD|JPY|C\$|A\$|S\$)"""
    const val CURRENCY_SUFFIX = """(?:₹|Rs[.:]?|INR|[$€£¥]|USD|EUR|GBP|CAD|AUD|AED|SGD|JPY)"""

    val AMOUNT = Regex("""(?i)$CURRENCY_PREFIX\s*(\d+(?:,\d{2,3})*(?:\.\d{1,2})?|\d+(?:\.\d{1,2})?)""")
    val AMOUNT_SUFFIX = Regex("""(?i)(\d+(?:,\d{2,3})*(?:\.\d{1,2})?|\d+(?:\.\d{1,2})?)\s*$CURRENCY_SUFFIX""")
    val ACCOUNT = Regex("""(?i)\b(?:a/c|ac|acct|account|card)\s*(?:no\.?)?\s*(?:ending\s+)?(?:with\s+)?(?:[*xX.]*)(\d{3,4})\b""")
    val UTR = Regex("""(?i)(?:utr|ref|txn|rrn)\s*(?:no\.?|id)?\s*[:#]?\s*([A-Z0-9]{8,})""")
    val UPI_HANDLE = Regex("""(?i)\b[a-z0-9._-]+@[a-z]+\b""")
    val BALANCE = Regex("""(?i)(?:avl\s*bal|available\s*balance|bal)\s*[:₹$€£¥]?\s*\d""")

    val DEBIT = Regex("""(?i)\b(debited|paid|spent|sent|transferred|withdrawn|charged|purchase at)\b""")
    val CREDIT = Regex("""(?i)\b(credited|received|deposited|added|refunded|cashback)\b""")
    val OTP = Regex("""(?i)\b(otp|one time password|verification code|secret code|do not share)\b""")
    val PROMO = Regex("""(?i)\b(congratulations|pre-approved|win cash|apply now|apply for|apply|earn|special offer|voucher)\b""")
    val OFFER = Regex("""(?i)\b(offer|discount|deal|cashback up to)\b""")
    val TX_VERB = Regex("""(?i)\b(payment|transaction|debited|credited|paid|sent|transferred|spent|received)\b""")
    val SUCCESS = Regex("""(?i)\b(successful|completed|success|confirmed|done)\b""")
}
