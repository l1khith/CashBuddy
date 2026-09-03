use regex::Regex;
use std::sync::OnceLock;

static AMOUNT_REGEX: OnceLock<Option<Regex>> = OnceLock::new();

fn get_amount_regex() -> Option<&'static Regex> {
    AMOUNT_REGEX
        .get_or_init(|| {
            Regex::new(r"(?i)(?:(?:₹|Rs\.?|INR)\s*([\d,]+\.?\d*)|([\d,]+\.?\d*)\s*(?:₹|Rs\.?|INR))")
                .ok()
        })
        .as_ref()
}

pub fn extract_amount(text: &str) -> Option<f64> {
    let regex = get_amount_regex()?;
    let captures = regex.captures(text)?;

    // Check group 1 (prefix symbol) or group 2 (suffix symbol)
    let raw_val = captures.get(1).or_else(|| captures.get(2))?.as_str();
    let sanitized = raw_val.replace(',', "");

    sanitized.parse::<f64>().ok()
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_extract_amount_various_formats() {
        assert_eq!(extract_amount("Paid ₹500 to Swiggy"), Some(500.0));
        assert_eq!(extract_amount("Debited Rs. 1,234.50 from A/C"), Some(1234.50));
        assert_eq!(extract_amount("INR 50,000 credited to account"), Some(50000.0));
        assert_eq!(extract_amount("Sent 450.00 INR via UPI"), Some(450.0));
        assert_eq!(extract_amount("No money mentioned here"), None);
    }
}
