use regex::Regex;
use std::sync::OnceLock;

static MERCHANT_PATTERNS: OnceLock<Vec<Regex>> = OnceLock::new();

fn get_merchant_patterns() -> &'static [Regex] {
    MERCHANT_PATTERNS
        .get_or_init(|| {
            vec![
                Regex::new(r"(?i)(?:paid|sent)\s+.*?to\s+([A-Za-z0-9&._'-]+(?:\s+[A-Za-z0-9&._'-]+)?)(?:\s+(?:via|using|ref|on|a/c|\.|$))").ok(),
                Regex::new(r"(?i)(?:spent|paid|purchase)\s+.*?at\s+([A-Za-z0-9&._'-]+(?:\s+[A-Za-z0-9&._'-]+)?)(?:\s+(?:for|via|using|ref|on|a/c|\.|$))").ok(),
                Regex::new(r"(?i)(?:to|from)\s+([A-Za-z0-9&._'-]+(?:\s+[A-Za-z0-9&._'-]+)?)(?:\s+(?:via|using|ref|on|a/c|\.|$))").ok(),
                Regex::new(r"(?i)(?:towards|for|vpa)\s+([A-Za-z0-9&._'-]+)").ok(),
            ]
            .into_iter()
            .flatten()
            .collect()
        })
        .as_slice()
}

pub fn extract_merchant(text: &str, package_name: &str) -> String {
    for pattern in get_merchant_patterns() {
        if let Some(captures) = pattern.captures(text) {
            if let Some(m) = captures.get(1) {
                let trimmed = m.as_str().trim();
                if !trimmed.is_empty() && trimmed.len() > 1 {
                    return trimmed.to_string();
                }
            }
        }
    }

    // Known app package fallback if no entity extracted
    match package_name {
        "com.phonepe.app" => "PhonePe Transfer".to_string(),
        "com.google.android.apps.nbu.paisa.user" => "Google Pay Payment".to_string(),
        "net.one97.paytm" => "Paytm Payment".to_string(),
        "com.dreamplug.androidapp" => "CRED Pay".to_string(),
        _ => "Unknown Merchant".to_string(),
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_extract_merchant() {
        assert_eq!(
            extract_merchant("Paid ₹500 to Swiggy via GPay", "com.google.android.apps.nbu.paisa.user"),
            "Swiggy"
        );
        assert_eq!(
            extract_merchant("Purchase at Starbucks for Rs. 350", "com.snapwork.hdfc"),
            "Starbucks"
        );
    }
}
