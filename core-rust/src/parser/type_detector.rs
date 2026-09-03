use crate::TransactionType;

const DEBIT_KEYWORDS: &[&str] = &[
    "debited",
    "paid",
    "spent",
    "sent",
    "transferred to",
    "purchase at",
    "withdrawn",
    "charged",
];

const CREDIT_KEYWORDS: &[&str] = &[
    "credited",
    "received",
    "deposited",
    "added",
    "refunded",
    "cashback",
];

pub fn detect_type(text_lower: &str) -> Option<TransactionType> {
    // Check debit signals
    if DEBIT_KEYWORDS.iter().any(|&k| text_lower.contains(k)) {
        return Some(TransactionType::Debit);
    }

    // Check credit signals
    if CREDIT_KEYWORDS.iter().any(|&k| text_lower.contains(k)) {
        return Some(TransactionType::Credit);
    }

    None
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_detect_type() {
        assert_eq!(detect_type("debited for ₹200"), Some(TransactionType::Debit));
        assert_eq!(detect_type("received rs 500 from friend"), Some(TransactionType::Credit));
        assert_eq!(detect_type("random information alert"), None);
    }
}
