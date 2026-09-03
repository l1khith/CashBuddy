use regex::Regex;
use std::sync::OnceLock;

static ACCOUNT_REGEX: OnceLock<Option<Regex>> = OnceLock::new();

fn get_account_regex() -> Option<&'static Regex> {
    ACCOUNT_REGEX
        .get_or_init(|| {
            Regex::new(r"(?i)(?:(?:a/c|acct|account|card)\s*(?:no\.?)?\s*(?:ending\s+with|ending|\*{2,}|xx)?\s*)([0-9]{3,4})")
                .ok()
        })
        .as_ref()
}

pub fn extract_account(text: &str) -> Option<String> {
    let regex = get_account_regex()?;
    let captures = regex.captures(text)?;
    let digits = captures.get(1)?.as_str();

    Some(format!("XX{}", digits))
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_extract_account() {
        assert_eq!(
            extract_account("debited from A/C XX1234 on 03-Sep"),
            Some("XX1234".to_string())
        );
        assert_eq!(
            extract_account("spent on card ending 9876"),
            Some("XX9876".to_string())
        );
        assert_eq!(extract_account("No account mentioned"), None);
    }
}
