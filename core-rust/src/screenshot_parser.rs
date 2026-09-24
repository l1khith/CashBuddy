//! Screenshot Parser for CashBuddy.
//! Parses OCR text extracted from Indian UPI payment screens (Google Pay, PhonePe, Paytm, BHIM, CRED, Amazon Pay).

use crate::category_engine::CategoryEngine;
use crate::TransactionType;
use regex::Regex;
use std::sync::OnceLock;

#[derive(Debug, Clone, PartialEq)]
pub struct ScreenshotTransaction {
    pub amount: f64,
    pub transaction_type: TransactionType,
    pub merchant: String,
    pub category: String,
    pub utr_or_ref: Option<String>,
    pub app_name: String,
    pub confidence: f32,
    pub raw_text: String,
}

static AMOUNT_REGEX: OnceLock<Option<Regex>> = OnceLock::new();
static UTR_REGEX: OnceLock<Option<Regex>> = OnceLock::new();
static VPA_REGEX: OnceLock<Option<Regex>> = OnceLock::new();

fn get_amount_regex() -> Option<&'static Regex> {
    AMOUNT_REGEX
        .get_or_init(|| Regex::new(r"(?i)(?:₹|Rs\.?|INR)\s*([0-9]{1,3}(?:,[0-9]{2,3})*(?:\.[0-9]{1,2})?|[0-9]+(?:\.[0-9]{1,2})?)").ok())
        .as_ref()
}

fn get_utr_regex() -> Option<&'static Regex> {
    UTR_REGEX
        .get_or_init(|| {
            Regex::new(r"(?i)(?:UPI\s+(?:transaction\s+id|ref(?:\s+no|\s+id|\s+number)?)|Transaction\s+ID|UTR)[:\s]+([A-Za-z0-9]{8,35})").ok()
        })
        .as_ref()
}

fn get_vpa_regex() -> Option<&'static Regex> {
    VPA_REGEX
        .get_or_init(|| Regex::new(r"(?i)\b([a-zA-Z0-9.\-_]{2,64}@[a-zA-Z]{2,32})\b").ok())
        .as_ref()
}

/// Parse OCR text from UPI payment screenshots into structured transaction
pub fn parse_screenshot_text(raw_text: String) -> Option<ScreenshotTransaction> {
    parse_screenshot_internal(&raw_text)
}

pub fn parse_screenshot_internal(text: &str) -> Option<ScreenshotTransaction> {
    let trimmed = text.trim();
    if trimmed.is_empty() {
        return None;
    }

    // 1. Detect App
    let app_name = detect_app_name(trimmed);

    // 2. Extract Amount
    let amount = extract_screenshot_amount(trimmed)?;
    if amount <= 0.0 {
        return None;
    }

    // 3. Detect Transaction Type (Debit vs Credit)
    let tx_type = detect_screenshot_tx_type(trimmed);

    // 4. Extract Merchant
    let merchant = extract_screenshot_merchant(trimmed);
    if merchant.is_empty() {
        return None;
    }

    // 5. Extract UTR / Reference ID
    let utr_or_ref = extract_screenshot_utr(trimmed);

    // 6. Category mapping via CategoryEngine
    let engine = CategoryEngine::new();
    let category_match = engine.get_category(merchant.clone());
    let category = category_match.category;

    // 7. Calculate Confidence
    let mut confidence: f32 = 0.85;
    if utr_or_ref.is_some() {
        confidence += 0.05;
    }
    if app_name != "UPI" {
        confidence += 0.05;
    }
    if category != "Unknown" {
        confidence = confidence.max(0.90);
    }
    let confidence = confidence.min(0.99);

    Some(ScreenshotTransaction {
        amount,
        transaction_type: tx_type,
        merchant,
        category,
        utr_or_ref,
        app_name,
        confidence,
        raw_text: trimmed.to_string(),
    })
}

fn detect_app_name(text: &str) -> String {
    let lower = text.to_lowercase();
    if lower.contains("google pay") || lower.contains("gpay") {
        "Google Pay".to_string()
    } else if lower.contains("phonepe") {
        "PhonePe".to_string()
    } else if lower.contains("paytm") {
        "Paytm".to_string()
    } else if lower.contains("bhim") {
        "BHIM".to_string()
    } else if lower.contains("cred") {
        "CRED".to_string()
    } else if lower.contains("amazon pay") {
        "Amazon Pay".to_string()
    } else {
        "UPI".to_string()
    }
}

fn extract_screenshot_amount(text: &str) -> Option<f64> {
    if let Some(re) = get_amount_regex() {
        if let Some(caps) = re.captures(text) {
            if let Some(m) = caps.get(1) {
                let clean = m.as_str().replace(',', "");
                if let Ok(val) = clean.parse::<f64>() {
                    return Some(val);
                }
            }
        }
    }

    // Fallback: search for numbers after "₹" symbol
    for line in text.lines() {
        let trimmed = line.trim();
        if trimmed.starts_with('₹') || trimmed.starts_with("Rs") {
            let digits_part: String = trimmed
                .chars()
                .filter(|c| c.is_ascii_digit() || *c == '.' || *c == ',')
                .collect();
            let clean = digits_part.replace(',', "");
            if let Ok(val) = clean.parse::<f64>() {
                if val > 0.0 {
                    return Some(val);
                }
            }
        }
    }

    None
}

fn detect_screenshot_tx_type(text: &str) -> TransactionType {
    let lower = text.to_lowercase();
    if lower.contains("received from")
        || lower.contains("payment received")
        || lower.contains("money received")
        || lower.contains("credited to")
    {
        TransactionType::Credit
    } else {
        TransactionType::Debit
    }
}

fn extract_screenshot_merchant(text: &str) -> String {
    let lines: Vec<&str> = text.lines().map(|l| l.trim()).filter(|l| !l.is_empty()).collect();

    // Strategy 1: Look for line right after "Paid to", "Payment to", "Money Sent Successfully to", "To", "Received from"
    for (i, line) in lines.iter().enumerate() {
        let lower = line.to_lowercase();
        if lower == "paid to"
            || lower == "payment to"
            || lower == "payment of"
            || lower == "sent to"
            || lower == "to"
            || lower == "to:"
            || lower == "received from"
            || lower == "from"
            || lower == "from:"
            || lower.ends_with("successfully to")
        {
            if let Some(next_line) = lines.get(i + 1) {
                let candidate = clean_merchant_line(next_line);
                if !candidate.is_empty() && !is_system_label(&candidate) {
                    return candidate;
                }
            }
        }

        // Inline e.g. "Paid to Chai Point", "To: Reliance Digital", "Received from Ramesh"
        if lower.starts_with("paid to ") {
            let candidate = clean_merchant_line(&line[8..]);
            if !candidate.is_empty() && !is_system_label(&candidate) {
                return candidate;
            }
        }
        if lower.starts_with("received from ") {
            let candidate = clean_merchant_line(&line[14..]);
            if !candidate.is_empty() && !is_system_label(&candidate) {
                return candidate;
            }
        }
        if lower.starts_with("to: ") {
            let candidate = clean_merchant_line(&line[4..]);
            if !candidate.is_empty() && !is_system_label(&candidate) {
                return candidate;
            }
        }
        if lower.starts_with("payment to ") {
            let candidate = clean_merchant_line(&line[11..]);
            if !candidate.is_empty() && !is_system_label(&candidate) {
                return candidate;
            }
        }
    }

    // Strategy 2: VPA identifier (e.g. swiggy@icici -> "swiggy")
    if let Some(vpa_re) = get_vpa_regex() {
        if let Some(caps) = vpa_re.captures(text) {
            if let Some(m) = caps.get(1) {
                let vpa = m.as_str();
                let username = vpa.split('@').next().unwrap_or(vpa);
                let cleaned = clean_merchant_line(username);
                if !cleaned.is_empty() {
                    return cleaned;
                }
            }
        }
    }

    "UPI Merchant".to_string()
}

fn clean_merchant_line(line: &str) -> String {
    let mut cleaned = line
        .replace("✓", "")
        .replace("✔", "")
        .replace("•", "")
        .trim()
        .to_string();

    // Strip trailing punctuation
    while cleaned.ends_with('.') || cleaned.ends_with(',') || cleaned.ends_with(':') {
        cleaned.pop();
        cleaned = cleaned.trim().to_string();
    }

    cleaned
}

fn is_system_label(s: &str) -> bool {
    let lower = s.to_lowercase();
    lower == "completed"
        || lower == "successful"
        || lower == "failed"
        || lower == "pending"
        || lower == "transfer details"
        || lower == "transaction details"
        || lower.starts_with("upi transaction id")
        || lower.starts_with("transaction id")
        || lower.starts_with("utr")
        || lower.starts_with("₹")
        || lower.starts_with("rs")
}

fn extract_screenshot_utr(text: &str) -> Option<String> {
    if let Some(re) = get_utr_regex() {
        if let Some(caps) = re.captures(text) {
            if let Some(m) = caps.get(1) {
                return Some(m.as_str().trim().to_string());
            }
        }
    }
    None
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_google_pay_screenshot() {
        let text = r#"
            Google Pay
            ₹450
            Paid to Chai Point
            chai@icici
            Completed
            May 14, 2024 12:45 PM
            UPI transaction ID: 413418291039
            To: Chai Point
            From: State Bank of India
        "#;

        let parsed = parse_screenshot_internal(text);
        assert!(parsed.is_some());
        let tx = parsed.unwrap();
        assert_eq!(tx.amount, 450.0);
        assert_eq!(tx.transaction_type, TransactionType::Debit);
        assert_eq!(tx.merchant, "Chai Point");
        assert_eq!(tx.category, "Food & Dining");
        assert_eq!(tx.app_name, "Google Pay");
        assert_eq!(tx.utr_or_ref, Some("413418291039".to_string()));
        assert!(tx.confidence >= 0.85);
    }

    #[test]
    fn test_phonepe_screenshot() {
        let text = r#"
            Transaction Successful
            02:45 PM on 14 May 2024
            ₹1,200
            Paid to
            BigBasket
            Transfer Details
            Transaction ID: T2405141445123456789
            Debited from
            XXXXXX1234
            UTR: 413512345678
            PhonePe
        "#;

        let parsed = parse_screenshot_internal(text);
        assert!(parsed.is_some());
        let tx = parsed.unwrap();
        assert_eq!(tx.amount, 1200.0);
        assert_eq!(tx.transaction_type, TransactionType::Debit);
        assert_eq!(tx.merchant, "BigBasket");
        assert_eq!(tx.category, "Groceries");
        assert_eq!(tx.app_name, "PhonePe");
        assert_eq!(tx.utr_or_ref, Some("T2405141445123456789".to_string()));
    }

    #[test]
    fn test_paytm_screenshot() {
        let text = r#"
            Paytm
            ₹350
            Money Sent Successfully to
            Swiggy
            swiggy@icici
            14 May 2024, 01:15 PM
            UPI Ref No: 413598765432
        "#;

        let parsed = parse_screenshot_internal(text);
        assert!(parsed.is_some());
        let tx = parsed.unwrap();
        assert_eq!(tx.amount, 350.0);
        assert_eq!(tx.merchant, "Swiggy");
        assert_eq!(tx.category, "Food & Dining");
        assert_eq!(tx.app_name, "Paytm");
        assert_eq!(tx.utr_or_ref, Some("413598765432".to_string()));
    }

    #[test]
    fn test_credit_received_screenshot() {
        let text = r#"
            Google Pay
            Payment received
            ₹2,000
            Received from
            Ramesh Kumar
            Completed
            UPI transaction ID: 998877665544
        "#;

        let parsed = parse_screenshot_internal(text);
        assert!(parsed.is_some());
        let tx = parsed.unwrap();
        assert_eq!(tx.amount, 2000.0);
        assert_eq!(tx.transaction_type, TransactionType::Credit);
        assert_eq!(tx.merchant, "Ramesh Kumar");
    }
}
