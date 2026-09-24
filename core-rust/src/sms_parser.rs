use crate::category_engine::CategoryEngine;
use crate::TransactionType;
use regex::Regex;
use std::collections::HashSet;
use std::sync::{OnceLock, RwLock};

/// Represents a parsed transaction extracted from a bank SMS.
#[derive(Debug, Clone, PartialEq, serde::Serialize, serde::Deserialize)]
pub struct SmsTransaction {
    pub amount: f64,
    pub transaction_type: TransactionType,
    pub category: String,
    pub merchant: String,
    pub account_last4: Option<String>,
    pub bank: Option<String>,
    pub raw_text: String,
    pub confidence: f32,
    pub timestamp: i64,
}

/// Registry of trusted bank SMS sender headers (TRAI format).
static TRUSTED_SENDERS: OnceLock<RwLock<HashSet<String>>> = OnceLock::new();
static CATEGORY_ENGINE: OnceLock<CategoryEngine> = OnceLock::new();

fn get_category_engine() -> &'static CategoryEngine {
    CATEGORY_ENGINE.get_or_init(CategoryEngine::new)
}

fn get_trusted_senders() -> &'static RwLock<HashSet<String>> {
    TRUSTED_SENDERS.get_or_init(|| {
        let mut set = HashSet::new();
        // Major Indian Scheduled Commercial Banks & Payment Entities
        let defaults = [
            "HDFCBK", "HDFC", "HDFCLT",
            "SBIINB", "SBIPSG", "SBICRD", "SBISMS", "ATMSBI",
            "ICICIB", "ICICIT", "IMOBIL",
            "AXISBK", "AXIS",
            "KOTAKB", "KOTAK",
            "PNBSMS", "PUNBNK",
            "BOBTXN", "BOBSMS",
            "CANBNK",
            "UNIONB", "UBIN",
            "IDFCFB", "IDFC",
            "INDUSB", "INDBNK",
            "YESBNK",
            "RBLBNK",
            "FEDBNK",
            "JUPITR",
            "FIMONEY",
            "ONECRD",
            "PAYTMB",
            "AMAZON",
            "CREDAP",
            "SLICE",
            "AUBANK",
            "CITIBK",
            "SCISMS",
            "BANDHN",
            "BOIMOB",
            "CBISMS",
            "UCOBNK",
            "PSBBK",
            "IOBCHN",
        ];
        for sender in defaults {
            set.insert(sender.to_uppercase());
        }
        RwLock::new(set)
    })
}

/// Dynamic sender learning: registers a sender ID as trusted.
pub fn learn_trusted_sender(sender: String) {
    learn_trusted_sender_str(&sender);
}

pub fn learn_trusted_sender_str(sender: &str) {
    let clean = normalize_sender(sender);
    if clean.is_empty() {
        return;
    }
    if let Ok(mut lock) = get_trusted_senders().write() {
        lock.insert(clean);
    }
}

/// Checks whether a sender ID matches TRAI format or is in the trusted sender registry.
pub fn is_trusted_sender(sender: String) -> bool {
    is_trusted_sender_str(&sender)
}

pub fn is_trusted_sender_str(sender: &str) -> bool {
    let clean = normalize_sender(sender);
    if clean.is_empty() {
        return false;
    }

    if let Ok(lock) = get_trusted_senders().read() {
        if lock.contains(&clean) {
            return true;
        }
        // Extract 6-character header if telecom circle prefix exists (e.g. "AD-HDFCBK" -> "HDFCBK")
        if let Some(pos) = clean.rfind('-') {
            let header = &clean[pos + 1..];
            if lock.contains(header) {
                return true;
            }
        }
    }

    false
}

fn normalize_sender(sender: &str) -> String {
    sender
        .trim()
        .to_uppercase()
        .chars()
        .filter(|c| c.is_ascii_alphanumeric() || *c == '-')
        .collect()
}

fn is_trai_format(clean_sender: &str) -> bool {
    static TRAI_REGEX: OnceLock<Option<Regex>> = OnceLock::new();
    let regex = TRAI_REGEX.get_or_init(|| {
        Regex::new(r"^(?:[A-Z]{2}-)?[A-Z]{6}$").ok()
    });

    if let Some(re) = regex.as_ref() {
        re.is_match(clean_sender)
    } else {
        false
    }
}

/// Identifies the bank name based on sender ID or body text.
fn identify_bank(sender: &str, body: &str) -> Option<String> {
    let s_upper = sender.to_uppercase();
    let b_upper = body.to_uppercase();

    if s_upper.contains("HDFC") || b_upper.contains("HDFC BANK") {
        Some("HDFC Bank".to_string())
    } else if s_upper.contains("SBI") || b_upper.contains("STATE BANK OF INDIA") || b_upper.contains("SBI") {
        Some("State Bank of India".to_string())
    } else if s_upper.contains("ICICI") || b_upper.contains("ICICI BANK") {
        Some("ICICI Bank".to_string())
    } else if s_upper.contains("AXIS") || b_upper.contains("AXIS BANK") {
        Some("Axis Bank".to_string())
    } else if s_upper.contains("KOTAK") || b_upper.contains("KOTAK BANK") {
        Some("Kotak Mahindra Bank".to_string())
    } else if s_upper.contains("PNB") || s_upper.contains("PUNB") || b_upper.contains("PUNJAB NATIONAL BANK") {
        Some("Punjab National Bank".to_string())
    } else if s_upper.contains("BOB") || b_upper.contains("BANK OF BARODA") {
        Some("Bank of Baroda".to_string())
    } else if s_upper.contains("CAN") || b_upper.contains("CANARA BANK") {
        Some("Canara Bank".to_string())
    } else if s_upper.contains("UNION") || s_upper.contains("UBIN") || b_upper.contains("UNION BANK") {
        Some("Union Bank of India".to_string())
    } else if s_upper.contains("IDFC") || b_upper.contains("IDFC FIRST BANK") {
        Some("IDFC FIRST Bank".to_string())
    } else if s_upper.contains("INDUS") || b_upper.contains("INDUSIND BANK") {
        Some("IndusInd Bank".to_string())
    } else if s_upper.contains("YES") || b_upper.contains("YES BANK") {
        Some("YES Bank".to_string())
    } else if s_upper.contains("RBL") || b_upper.contains("RBL BANK") {
        Some("RBL Bank".to_string())
    } else if s_upper.contains("FED") || b_upper.contains("FEDERAL BANK") {
        Some("Federal Bank".to_string())
    } else if s_upper.contains("JUPIT") || b_upper.contains("JUPITER") {
        Some("Jupiter".to_string())
    } else if s_upper.contains("FI") || b_upper.contains("FI MONEY") {
        Some("Fi Money".to_string())
    } else if s_upper.contains("ONEC") || b_upper.contains("ONECARD") {
        Some("OneCard".to_string())
    } else if s_upper.contains("PAYTM") || b_upper.contains("PAYTM") {
        Some("Paytm Payments Bank".to_string())
    } else if s_upper.contains("CRED") || b_upper.contains("CRED") {
        Some("CRED".to_string())
    } else {
        None
    }
}

/// Discards OTP and security verification SMS.
fn is_otp_message(body: &str) -> bool {
    static OTP_REGEX: OnceLock<Option<Regex>> = OnceLock::new();
    let regex = OTP_REGEX.get_or_init(|| {
        Regex::new(r"(?i)\b(?:otp|one\s*time\s*password|verification\s*code|secret\s*code|do\s*not\s*share|valid\s+for\s+\d+\s+min)\b").ok()
    });

    if let Some(re) = regex.as_ref() {
        re.is_match(body)
    } else {
        false
    }
}

/// Discards non-transactional promotional campaigns.
fn is_promotional_message(body: &str) -> bool {
    static PROMO_REGEX: OnceLock<Option<Regex>> = OnceLock::new();
    let regex = PROMO_REGEX.get_or_init(|| {
        Regex::new(r"(?i)\b(?:pre-approved|apply\s+now|claim\s+your|special\s+discount|click\s+here|loan\s+offer|bonus\s+points)\b").ok()
    });

    if let Some(re) = regex.as_ref() {
        re.is_match(body)
    } else {
        false
    }
}

/// Detects Debit vs Credit using Indian banking terminology and math symbols.
fn detect_sms_transaction_type(body: &str) -> Option<TransactionType> {
    static DEBIT_REGEX: OnceLock<Option<Regex>> = OnceLock::new();
    static CREDIT_REGEX: OnceLock<Option<Regex>> = OnceLock::new();

    let debit_re = DEBIT_REGEX.get_or_init(|| {
        Regex::new(r"(?i)\b(?:debited|withdrawn|paid|sent|spent|purchase(?:\s+at)?|charged|-₹|-rs|-inr)\b").ok()
    });
    let credit_re = CREDIT_REGEX.get_or_init(|| {
        Regex::new(r"(?i)\b(?:credited|deposited|received|refund(?:ed)?|cashback|salary|added\s+to|\+₹|\+rs|\+inr)\b").ok()
    });

    let (d_re, c_re) = match (debit_re.as_ref(), credit_re.as_ref()) {
        (Some(d), Some(c)) => (d, c),
        _ => return None,
    };

    let debit_match = d_re.find(body);
    let credit_match = c_re.find(body);

    match (debit_match, credit_match) {
        (Some(d), Some(c)) => {
            // When both appear (e.g. "debited from a/c... Avail bal credited..."),
            // determine which keyword appears earlier or closer to the beginning of the transaction clause.
            if d.start() <= c.start() {
                Some(TransactionType::Debit)
            } else {
                Some(TransactionType::Credit)
            }
        }
        (Some(_), None) => Some(TransactionType::Debit),
        (None, Some(_)) => Some(TransactionType::Credit),
        (None, None) => None,
    }
}

/// Extracts the transaction amount while strictly filtering out balance amounts
/// (e.g. "Avail Bal: Rs. 15,420.50", "Avl Lmt: INR 85,000", "Balance is Rs. 10,000").
fn extract_sms_amount(body: &str) -> Option<f64> {
    static AMOUNT_REGEX: OnceLock<Option<Regex>> = OnceLock::new();
    static BALANCE_INDICATOR_REGEX: OnceLock<Option<Regex>> = OnceLock::new();

    let amount_re = AMOUNT_REGEX.get_or_init(|| {
        Regex::new(r"(?i)(?:(?:₹|Rs\.?|INR)\s*([0-9]{1,3}(?:,[0-9]{3})*(?:\.[0-9]{1,2})?|[0-9]+(?:\.[0-9]{1,2})?))|(?:([0-9]{1,3}(?:,[0-9]{3})*(?:\.[0-9]{1,2})?|[0-9]+(?:\.[0-9]{1,2})?)\s*(?:₹|Rs\.?|INR))").ok()
    });
    let bal_re = BALANCE_INDICATOR_REGEX.get_or_init(|| {
        Regex::new(r"(?i)\b(?:avail(?:able)?\s+bal(?:ance)?|avl\s+bal|bal(?:ance)?(?:\s+is)?|avl\s+lmt|available\s+limit|total\s+bal|clg\s+bal|updated\s+bal)\b").ok()
    });

    let (a_re, b_re) = match (amount_re.as_ref(), bal_re.as_ref()) {
        (Some(a), Some(b)) => (a, b),
        _ => return None,
    };

    // Collect all candidate amounts and their positions
    let mut candidates = Vec::new();

    for cap in a_re.captures_iter(body) {
        let whole_match = cap.get(0)?;
        let start_pos = whole_match.start();
        let end_pos = whole_match.end();

        // Group 1: Prefix currency symbol ("Rs. 500")
        // Group 2: Suffix currency symbol ("500 Rs")
        let val_str = cap.get(1).or_else(|| cap.get(2))?.as_str();
        let cleaned = val_str.replace(',', "");
        let amount = match cleaned.parse::<f64>() {
            Ok(val) if val > 0.0 => val,
            _ => continue,
        };

        // Check if preceding context indicates a balance
        let lookback_start = start_pos.saturating_sub(30);
        let preceding_slice = &body[lookback_start..start_pos];
        let is_balance = b_re.is_match(preceding_slice);

        // Check if trailing context indicates a balance
        let lookforward_end = (end_pos + 20).min(body.len());
        let trailing_slice = &body[end_pos..lookforward_end];
        let trailing_is_balance = b_re.is_match(trailing_slice);

        if !is_balance && !trailing_is_balance {
            candidates.push((amount, start_pos));
        }
    }

    if let Some(&(first_non_bal, _)) = candidates.first() {
        Some(first_non_bal)
    } else {
        None
    }
}

/// Extracts the account or card last 4 digits (e.g. "A/C **1234", "Card ending 9876", "XX4321").
fn extract_sms_account_last4(body: &str) -> Option<String> {
    static ACCOUNT_PATTERNS: OnceLock<Vec<Regex>> = OnceLock::new();
    let patterns = ACCOUNT_PATTERNS.get_or_init(|| {
        vec![
            Regex::new(r"(?i)(?:a/c|acct|account|card|c/c|d/c)\s*(?:no\.?)?\s*(?:ending\s+(?:with|in)\s+|ending|\*{2,}|[xX]{2,}|n\s+)?([0-9]{3,4})\b").ok(),
            Regex::new(r"(?i)(?:\*{3,}|[xX]{3,})([0-9]{4})\b").ok(),
            Regex::new(r"(?i)\b(?:ending\s+with|ending\s+in)\s+([0-9]{4})\b").ok(),
        ]
        .into_iter()
        .flatten()
        .collect()
    });

    for pat in patterns {
        if let Some(captures) = pat.captures(body) {
            if let Some(digits) = captures.get(1) {
                let d = digits.as_str().trim();
                if d.len() == 4 || d.len() == 3 {
                    return Some(d.to_string());
                }
            }
        }
    }

    None
}

/// Extracts the merchant name, beneficiary, or UPI VPA from bank SMS.
fn extract_sms_merchant(body: &str) -> String {
    static MERCHANT_PATTERNS: OnceLock<Vec<Regex>> = OnceLock::new();
    let patterns = MERCHANT_PATTERNS.get_or_init(|| {
        vec![
            // UPI / VPA pattern: e.g. "vpa swiggy@icici" or "to swiggy@upi"
            Regex::new(r"(?i)\b(?:vpa|to\s+vpa)\s+([a-zA-Z0-9.\-_]+@[a-zA-Z0-9]+)").ok(),
            // UPI reference with merchant: e.g. "Info: UPI/SWIGGY/123" or "UPI/ZOMATO/..."
            Regex::new(r"(?i)\bUPI/([A-Za-z0-9&'_\s-]+?)/").ok(),
            // Info: Bil*Amazon or Info: Swiggy
            Regex::new(r"(?i)\binfo:\s*(?:bil\*|pos/|ecom/|upi/)?([A-Za-z0-9&'_\s-]+?)(?:\s+(?:on|via|using|ref|bal|avl)|\.|$|\n)").ok(),
            // Bank transfer indicators: "paid to <Merchant>", "transferred to <Merchant>", "sent to <Merchant>"
            Regex::new(r"(?i)\b(?:paid\s+to|transferred\s+to|sent\s+to)\s+([A-Za-z0-9&'_\s-]+?)(?:\s+(?:on|via|using|ref|a/c|bal|avl)|\.|$|\n)").ok(),
            // "spent at <Merchant>", "purchase at <Merchant>"
            Regex::new(r"(?i)\b(?:spent\s+at|purchase\s+at)\s+([A-Za-z0-9&'_\s-]+?)(?:\s+(?:on|for|via|using|ref|a/c|bal|avl)|\.|$|\n)").ok(),
            // "towards <Merchant>", "for <Merchant>"
            Regex::new(r"(?i)\b(?:towards|for)\s+([A-Za-z0-9&'_\s-]+?)(?:\s+(?:on|via|using|ref|a/c|bal|avl)|\.|$|\n)").ok(),
            // Generic "to <Merchant>" or "at <Merchant>"
            Regex::new(r"(?i)\b(?:to|at)\s+([A-Za-z0-9&'_\s-]+?)(?:\s+(?:on|via|using|ref|a/c|bal|avl)|\.|$|\n)").ok(),
        ]
        .into_iter()
        .flatten()
        .collect()
    });

    for pat in patterns {
        if let Some(captures) = pat.captures(body) {
            if let Some(m) = captures.get(1) {
                let mut name = m.as_str().trim();
                // If it is a UPI VPA, extract the entity name before @
                if let Some(pos) = name.find('@') {
                    name = &name[..pos];
                }
                let clean = clean_merchant_name(name);
                if !clean.is_empty() && clean.len() > 1 {
                    return clean;
                }
            }
        }
    }

    "Unknown Merchant".to_string()
}

fn clean_merchant_name(raw: &str) -> String {
    let stopwords = [
        "on", "via", "using", "ref", "reference", "avl", "bal", "balance",
        "dated", "a/c", "account", "through", "your", "card",
    ];

    let words: Vec<&str> = raw
        .split_whitespace()
        .filter(|w| !stopwords.contains(&w.to_lowercase().as_str()))
        .collect();

    if words.is_empty() {
        return raw.trim().to_string();
    }

    words.join(" ").trim_matches(|c: char| !c.is_alphanumeric()).to_string()
}

/// Primary public SMS parsing function exposed via UniFFI.
/// Takes SMS sender (header) and body, returns an `Option<SmsTransaction>`.
pub fn parse_sms(sender: String, body: String) -> Option<SmsTransaction> {
    parse_sms_str(&sender, &body)
}

pub fn parse_sms_str(sender: &str, body: &str) -> Option<SmsTransaction> {
    let now = std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .map(|d| d.as_millis() as i64)
        .unwrap_or(0);

    parse_sms_internal(sender, body, now)
}

/// Internal SMS parsing function with custom timestamp support for testing.
pub fn parse_sms_internal(sender: &str, body: &str, timestamp: i64) -> Option<SmsTransaction> {
    let body_trimmed = body.trim();
    if body_trimmed.is_empty() {
        return None;
    }

    // 1. Validate Sender (must be TRAI format or trusted sender)
    let is_trusted = is_trusted_sender_str(sender);
    if !is_trusted && !is_trai_format(sender) {
        return None;
    }

    // 2. Discard OTP and Promotional Messages
    if is_otp_message(body_trimmed) || is_promotional_message(body_trimmed) {
        return None;
    }

    // 3. Extract Core Entities
    let amount = extract_sms_amount(body_trimmed)?;
    let transaction_type = detect_sms_transaction_type(body_trimmed)?;
    let account_last4 = extract_sms_account_last4(body_trimmed);
    let merchant = extract_sms_merchant(body_trimmed);
    let bank = identify_bank(sender, body_trimmed);

    // 4. Categorization via Learning Engine
    let engine = get_category_engine();
    let category_match = engine.get_category(merchant.clone());
    let category = category_match.category;

    // 5. Confidence Calculation
    let mut confidence = 0.0f32;
    if amount > 0.0 { confidence += 0.30; }
    confidence += 0.25; // Transaction type confirmed
    if merchant != "Unknown Merchant" { confidence += 0.15; }
    if account_last4.is_some() { confidence += 0.10; }
    if is_trusted { confidence += 0.10; }
    if category != "Unknown" { confidence += 0.10; }

    Some(SmsTransaction {
        amount,
        transaction_type,
        category,
        merchant,
        account_last4,
        bank,
        raw_text: body_trimmed.to_string(),
        confidence: confidence.min(1.0),
        timestamp,
    })
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_trai_sender_validation() {
        assert!(is_trusted_sender_str("AD-HDFCBK"));
        assert!(is_trusted_sender_str("VK-SBIINB"));
        assert!(is_trusted_sender_str("VM-ICICIB"));
        assert!(is_trusted_sender_str("AXISBK"));
        assert!(is_trusted_sender_str("KOTAKB"));
        assert!(!is_trusted_sender_str("9876543210"));
        assert!(!is_trusted_sender_str("SPAMMER"));
    }

    #[test]
    fn test_dynamic_sender_learning() {
        let custom_sender = "NEWBNK";
        assert!(!is_trusted_sender_str(custom_sender));

        learn_trusted_sender_str(custom_sender);
        assert!(is_trusted_sender_str(custom_sender));
    }

    #[test]
    fn test_hdfc_sms_debit_excluding_balance() {
        let sms = "Dear Customer, Rs.450.00 has been debited from account **1234 to SWIGGY on 24-SEP-26. Info: UPI/SWIGGY/123. Avl Bal: Rs.15,420.50.";
        let parsed = parse_sms_internal("AD-HDFCBK", sms, 1_700_000_000).expect("Should parse HDFC SMS");

        assert_eq!(parsed.amount, 450.0);
        assert_eq!(parsed.transaction_type, TransactionType::Debit);
        assert_eq!(parsed.account_last4, Some("1234".to_string()));
        assert_eq!(parsed.merchant, "SWIGGY");
        assert_eq!(parsed.bank, Some("HDFC Bank".to_string()));
        assert_eq!(parsed.category, "Food & Dining");
        assert!(parsed.confidence >= 0.85);
    }

    #[test]
    fn test_sbi_sms_debit_excluding_balance() {
        let sms = "Your A/C XXXXXXXX4321 debited by Rs 1,500.00 on 24Sep26 transfer to ZOMATO Ref No 6271829. Avail Bal Rs 8,300.00.";
        let parsed = parse_sms_internal("VK-SBIINB", sms, 1_700_000_000).expect("Should parse SBI SMS");

        assert_eq!(parsed.amount, 1500.0);
        assert_eq!(parsed.transaction_type, TransactionType::Debit);
        assert_eq!(parsed.account_last4, Some("4321".to_string()));
        assert_eq!(parsed.merchant, "ZOMATO");
        assert_eq!(parsed.bank, Some("State Bank of India".to_string()));
        assert_eq!(parsed.category, "Food & Dining");
    }

    #[test]
    fn test_icici_sms_debit() {
        let sms = "Acct XX5678 debited with INR 2,999.00 on 24-Sep-26. Info: BIL*Amazon India. Avail Bal: INR 45,210.00.";
        let parsed = parse_sms_internal("VM-ICICIB", sms, 1_700_000_000).expect("Should parse ICICI SMS");

        assert_eq!(parsed.amount, 2999.0);
        assert_eq!(parsed.transaction_type, TransactionType::Debit);
        assert_eq!(parsed.account_last4, Some("5678".to_string()));
        assert_eq!(parsed.merchant, "Amazon India");
        assert_eq!(parsed.bank, Some("ICICI Bank".to_string()));
    }

    #[test]
    fn test_axis_sms_debit_towards_merchant() {
        let sms = "INR 350.00 debited from A/c no. XX8765 on 24-09-2026 14:30:15 towards UBER INDIA. Avail Bal INR 12,050.25.";
        let parsed = parse_sms_internal("AX-AXISBK", sms, 1_700_000_000).expect("Should parse Axis SMS");

        assert_eq!(parsed.amount, 350.0);
        assert_eq!(parsed.transaction_type, TransactionType::Debit);
        assert_eq!(parsed.account_last4, Some("8765".to_string()));
        assert_eq!(parsed.merchant, "UBER INDIA");
        assert_eq!(parsed.bank, Some("Axis Bank".to_string()));
        assert_eq!(parsed.category, "Transportation");
    }

    #[test]
    fn test_credit_salary_sms() {
        let sms = "Dear Customer, A/C XX1234 is credited with INR 75,000.00 on 24-Sep-26 towards Salary. Avail Bal: INR 80,450.00.";
        let parsed = parse_sms_internal("HDFCBK", sms, 1_700_000_000).expect("Should parse salary credit");

        assert_eq!(parsed.amount, 75000.0);
        assert_eq!(parsed.transaction_type, TransactionType::Credit);
        assert_eq!(parsed.account_last4, Some("1234".to_string()));
        assert_eq!(parsed.bank, Some("HDFC Bank".to_string()));
    }

    #[test]
    fn test_credit_refund_sms() {
        let sms = "Refund of Rs. 450.00 credited to your Account XX4321 on 24-Sep-26. Info: Swiggy. Updated Bal: Rs. 8,750.00.";
        let parsed = parse_sms_internal("AD-HDFCBK", sms, 1_700_000_000).expect("Should parse refund credit");

        assert_eq!(parsed.amount, 450.0);
        assert_eq!(parsed.transaction_type, TransactionType::Credit);
        assert_eq!(parsed.account_last4, Some("4321".to_string()));
    }

    #[test]
    fn test_discard_otp_sms() {
        let sms = "492019 is your OTP for transaction of INR 500.00 at Swiggy on HDFC Bank Card ending 1234. OTP valid for 10 mins. Do not share with anyone.";
        assert!(parse_sms_internal("AD-HDFCBK", sms, 1_700_000_000).is_none());
    }

    #[test]
    fn test_discard_promotional_sms() {
        let sms = "Congratulations! You are pre-approved for personal loan of Rs 5,00,000 at 10.5% interest. Apply now.";
        assert!(parse_sms_internal("AD-HDFCBK", sms, 1_700_000_000).is_none());
    }

    #[test]
    fn test_kotak_bank_sms() {
        let sms = "Kotak Bank: Sent Rs. 850.00 from Kotak Bank A/c XX3456 to Dunzo via UPI Ref 92837482. Balance Rs. 24,100.00.";
        let parsed = parse_sms_internal("BP-KOTAKB", sms, 1_700_000_000).expect("Should parse Kotak SMS");

        assert_eq!(parsed.amount, 850.0);
        assert_eq!(parsed.transaction_type, TransactionType::Debit);
        assert_eq!(parsed.account_last4, Some("3456".to_string()));
        assert_eq!(parsed.bank, Some("Kotak Mahindra Bank".to_string()));
        assert_eq!(parsed.merchant, "Dunzo");
    }

    #[test]
    fn test_upi_vpa_extraction() {
        let sms = "Paid Rs 320 to VPA swiggy@icici on 24-Sep-26. A/C XX9999 debited. Avail Bal Rs 5,000.";
        let parsed = parse_sms_internal("HDFCBK", sms, 1_700_000_000).expect("Should parse VPA SMS");

        assert_eq!(parsed.amount, 320.0);
        assert_eq!(parsed.transaction_type, TransactionType::Debit);
        assert_eq!(parsed.merchant, "swiggy");
        assert_eq!(parsed.category, "Food & Dining");
    }

    #[test]
    fn test_mathematical_symbol_debit() {
        let sms = "-₹1,250.00 debited from A/C XX5544 towards Blinkit on 24-Sep-26. Avail Bal: ₹8,000.";
        let parsed = parse_sms_internal("AD-HDFCBK", sms, 1_700_000_000).expect("Should parse math symbol debit");

        assert_eq!(parsed.amount, 1250.0);
        assert_eq!(parsed.transaction_type, TransactionType::Debit);
        assert_eq!(parsed.merchant, "Blinkit");
        assert_eq!(parsed.category, "Groceries");
    }
}
