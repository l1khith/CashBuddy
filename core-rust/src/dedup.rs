//! Deduplication engine for CashBuddy.
//! Detects duplicate transactions captured across multiple channels (SMS, Screenshot, Notification).
//! Standard rule: Same amount + same merchant + within a 5-minute time window.

pub fn is_duplicate_transaction(
    amount1: f64,
    merchant1: String,
    time1: i64,
    amount2: f64,
    merchant2: String,
    time2: i64,
    window_secs: i64,
) -> bool {
    is_duplicate_internal(amount1, &merchant1, time1, amount2, &merchant2, time2, window_secs)
}

pub fn is_duplicate_internal(
    amount1: f64,
    merchant1: &str,
    time1: i64,
    amount2: f64,
    merchant2: &str,
    time2: i64,
    window_secs: i64,
) -> bool {
    // 1. Amount match within 1 paisa (0.01) tolerance
    let amount_diff = (amount1 - amount2).abs();
    if amount_diff > 0.01 {
        return false;
    }

    // 2. Time difference within window (handle both ms and seconds)
    let time_diff = (time1 - time2).abs();
    let window_ms = if window_secs > 1_000 {
        window_secs
    } else {
        window_secs * 1_000
    };
    if time_diff > window_ms {
        return false;
    }

    // 3. Merchant similarity check
    let m1 = normalize_merchant(merchant1);
    let m2 = normalize_merchant(merchant2);

    if m1.is_empty() || m2.is_empty() {
        return false;
    }

    if m1 == m2 {
        return true;
    }

    // Containment check for merchants with length >= 3
    if m1.len() >= 3 && m2.len() >= 3 && (m1.contains(&m2) || m2.contains(&m1)) {
        return true;
    }

    false
}

fn normalize_merchant(s: &str) -> String {
    s.trim()
        .to_lowercase()
        .replace("pvt ltd", "")
        .replace("private limited", "")
        .replace("ltd", "")
        .replace("india", "")
        .trim()
        .to_string()
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_exact_duplicate() {
        let t1 = 1700000000000;
        let t2 = 1700000060000; // 60s later
        assert!(is_duplicate_internal(450.0, "Swiggy", t1, 450.0, "Swiggy", t2, 300));
    }

    #[test]
    fn test_merchant_variation_duplicate() {
        let t1 = 1700000000000;
        let t2 = 1700000120000; // 2 min later
        assert!(is_duplicate_internal(120.0, "Uber India", t1, 120.0, "Uber", t2, 300));
    }

    #[test]
    fn test_different_amount_not_duplicate() {
        let t1 = 1700000000000;
        let t2 = 1700000010000;
        assert!(!is_duplicate_internal(450.0, "Swiggy", t1, 550.0, "Swiggy", t2, 300));
    }

    #[test]
    fn test_outside_window_not_duplicate() {
        let t1 = 1700000000000;
        let t2 = 1700000400000; // 400s later (> 300s)
        assert!(!is_duplicate_internal(450.0, "Swiggy", t1, 450.0, "Swiggy", t2, 300));
    }

    #[test]
    fn test_different_merchant_not_duplicate() {
        let t1 = 1700000000000;
        let t2 = 1700000020000;
        assert!(!is_duplicate_internal(450.0, "Swiggy", t1, 450.0, "Zomato", t2, 300));
    }
}
