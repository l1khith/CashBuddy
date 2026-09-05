use std::collections::HashMap;
use std::sync::Mutex;

const MAX_TRANSACTIONS_PER_MINUTE: usize = 5;

pub struct FraudDetector {
    /// Tracks (package_name -> timestamps in ms)
    history: Mutex<HashMap<String, Vec<i64>>>,
}

impl FraudDetector {
    pub fn new() -> Self {
        Self {
            history: Mutex::new(HashMap::new()),
        }
    }

    /// Returns true if transaction velocity is normal, false if anomalous
    pub fn check_velocity(&self, package_name: &str, timestamp: i64) -> bool {
        let mut map = match self.history.lock() {
            Ok(guard) => guard,
            Err(poisoned) => poisoned.into_inner(),
        };

        let timestamps = map.entry(package_name.to_string()).or_default();

        // Evict events older than 60,000 ms (1 minute)
        let cutoff = timestamp - 60_000;
        timestamps.retain(|&t| t > cutoff);

        let result = if timestamps.len() >= MAX_TRANSACTIONS_PER_MINUTE {
            false
        } else {
            timestamps.push(timestamp);
            true
        };

        // Evict package entries with no recent activity to prevent unbounded map growth
        map.retain(|_, v| !v.is_empty());

        result
    }

    /// Computes deterministic signature for duplicate suppression (5-minute window)
    pub fn compute_dedup_hash(amount: f64, merchant: &str, timestamp: i64) -> String {
        let window_bucket = timestamp / 300_000; // 5-minute bucket
        let cents = (amount * 100.0).round() as i64;
        format!("{}:{}:{}", cents, merchant.trim().to_lowercase(), window_bucket)
    }
}

impl Default for FraudDetector {
    fn default() -> Self {
        Self::new()
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_velocity_limit() {
        let detector = FraudDetector::new();
        let pkg = "com.phonepe.app";
        let now = 1_000_000;

        for _ in 0..5 {
            assert!(detector.check_velocity(pkg, now));
        }
        // 6th event in same minute should be flagged
        assert!(!detector.check_velocity(pkg, now + 1000));
    }
}
