use crate::parser::account_extractor::extract_account;
use crate::parser::amount_extractor::extract_amount;
use crate::parser::fraud_detector::FraudDetector;
use crate::parser::merchant_extractor::extract_merchant;
use crate::parser::type_detector::detect_type;
use crate::{Category, ParsedTransaction, RawNotification};
use std::collections::HashSet;

pub struct NotificationParser {
    allowed_packages: HashSet<&'static str>,
    otp_keywords: &'static [&'static str],
    promo_keywords: &'static [&'static str],
    fraud_detector: FraudDetector,
}

impl NotificationParser {
    pub fn new() -> Self {
        let mut allowed = HashSet::new();
        // Curated Indian UPI & Wallet apps
        allowed.insert("com.google.android.apps.nbu.paisa.user");
        allowed.insert("com.phonepe.app");
        allowed.insert("net.one97.paytm");
        allowed.insert("in.org.npci.upiapp");
        allowed.insert("com.dreamplug.androidapp");
        allowed.insert("in.amazon.mShop.android.shopping");
        allowed.insert("com.whatsapp");
        allowed.insert("com.naviapp");
        allowed.insert("com.mobikwik_new");
        allowed.insert("com.freecharge.android");

        // Curated Indian Banks (Private, Public, Neobanks)
        allowed.insert("com.snapwork.hdfc");
        allowed.insert("com.csam.icici.bank.imobile");
        allowed.insert("com.axis.mobile");
        allowed.insert("com.msf.kbank.mobile");
        allowed.insert("com.indusind.mpassbook");
        allowed.insert("com.idfcfirstbank.optimus");
        allowed.insert("com.fedmobile");
        allowed.insert("com.yesbank");
        allowed.insert("com.rblbank.mobank");
        allowed.insert("com.bandhan.mpassbook");
        allowed.insert("com.sbi.lotusintouch");
        allowed.insert("com.sbicard.customerapp");
        allowed.insert("com.bankofbaroda.mconnect");
        allowed.insert("com.pnb.one");
        allowed.insert("com.canarabank.mobility");
        allowed.insert("com.unionbank.ecommerce.mobile.android");
        allowed.insert("com.infrasoft.indianbank");
        allowed.insert("com.boi.omnineo");
        allowed.insert("com.cbi.mobile");
        allowed.insert("com.uco.ucobank");
        allowed.insert("money.jupiter");
        allowed.insert("money.fi.banking");
        allowed.insert("com.onecard.app");
        allowed.insert("org.slicepay");
        allowed.insert("in.uni.cards");
        allowed.insert("com.scapia.cards");
        allowed.insert("com.niyo.equitas");

        Self {
            allowed_packages: allowed,
            otp_keywords: &[
                "otp",
                "one time password",
                "verification code",
                "secret code",
                "do not share",
                "valid for",
            ],
            promo_keywords: &[
                "cashback up to",
                "congratulations",
                "pre-approved",
                "special offer",
                "voucher",
                "win cash",
                "apply now",
                "discount",
            ],
            fraud_detector: FraudDetector::new(),
        }
    }

    pub fn parse(&self, notification: RawNotification) -> Option<ParsedTransaction> {
        // Layer 1: Source Validation
        if !self.allowed_packages.contains(notification.package_name.as_str()) {
            return None;
        }

        // Layer 2: Content Classification & Discard
        let text_lower = notification.text.to_lowercase();
        if self.is_otp(&text_lower) || self.is_promotional(&text_lower) {
            return None;
        }

        // Layer 3: Entity Extraction
        let amount = extract_amount(&notification.text)?;
        let tx_type = detect_type(&text_lower)?;
        let merchant = extract_merchant(&notification.text, &notification.package_name);
        let account_id = extract_account(&notification.text);

        // Layer 4: Categorization
        let category = self.categorize(&merchant, &text_lower);

        // Layer 5: Confidence & Velocity Validation
        let velocity_ok = self.fraud_detector.check_velocity(&notification.package_name, notification.timestamp);
        let has_merchant = merchant != "Unknown";
        let has_account = account_id.is_some();

        let mut confidence = self.calculate_confidence(
            amount > 0.0,
            true,
            has_merchant,
            has_account,
            true,
            category != Category::Unknown,
        );

        if !velocity_ok {
            confidence = (confidence - 0.30).max(0.10);
        }

        Some(ParsedTransaction {
            amount,
            transaction_type: tx_type,
            category,
            merchant,
            account_id,
            source_app: notification.package_name,
            raw_text: notification.text,
            confidence,
            timestamp: notification.timestamp,
        })
    }

    fn is_otp(&self, text_lower: &str) -> bool {
        self.otp_keywords.iter().any(|&k| text_lower.contains(k))
    }

    fn is_promotional(&self, text_lower: &str) -> bool {
        self.promo_keywords.iter().any(|&k| text_lower.contains(k))
    }

    fn categorize(&self, merchant: &str, text_lower: &str) -> Category {
        let m_lower = merchant.to_lowercase();

        if m_lower.contains("swiggy")
            || m_lower.contains("zomato")
            || m_lower.contains("starbucks")
            || m_lower.contains("mcdonald")
            || text_lower.contains("dining")
            || text_lower.contains("restaurant")
        {
            Category::Food
        } else if m_lower.contains("uber")
            || m_lower.contains("ola")
            || m_lower.contains("metro")
            || m_lower.contains("petrol")
            || text_lower.contains("fuel")
        {
            Category::Transport
        } else if m_lower.contains("amazon")
            || m_lower.contains("flipkart")
            || m_lower.contains("myntra")
        {
            Category::Shopping
        } else if m_lower.contains("airtel")
            || m_lower.contains("jio")
            || m_lower.contains("bescom")
            || text_lower.contains("electricity")
            || text_lower.contains("utility")
        {
            Category::Bills
        } else if m_lower.contains("netflix")
            || m_lower.contains("spotify")
            || m_lower.contains("bookmyshow")
            || m_lower.contains("pvr")
        {
            Category::Entertainment
        } else if m_lower.contains("pharmacy")
            || m_lower.contains("apollo")
            || m_lower.contains("hospital")
        {
            Category::Health
        } else if text_lower.contains("salary") {
            Category::Salary
        } else if text_lower.contains("refund") {
            Category::Refund
        } else {
            Category::Unknown
        }
    }

    fn calculate_confidence(
        &self,
        has_amount: bool,
        has_type: bool,
        has_merchant: bool,
        has_account: bool,
        source_known: bool,
        category_known: bool,
    ) -> f32 {
        let mut score = 0.0f32;
        if has_amount { score += 0.30; }
        if has_type { score += 0.30; }
        if has_merchant { score += 0.15; }
        if has_account { score += 0.10; }
        if source_known { score += 0.10; }
        if category_known { score += 0.05; }
        score.min(1.0)
    }
}

impl Default for NotificationParser {
    fn default() -> Self {
        Self::new()
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_parse_valid_notification() {
        let parser = NotificationParser::new();
        let raw = RawNotification {
            package_name: "com.google.android.apps.nbu.paisa.user".to_string(),
            title: "Payment to Swiggy".to_string(),
            text: "Paid ₹450 to Swiggy via GPay. A/C XX1234 debited".to_string(),
            timestamp: 1_700_000_000_000,
        };

        let parsed = parser.parse(raw);
        assert!(parsed.is_some());
        let tx = parsed.unwrap();
        assert_eq!(tx.amount, 450.0);
        assert_eq!(tx.merchant, "Swiggy");
        assert_eq!(tx.category, Category::Food);
        assert!(tx.confidence >= 0.85);
    }

    #[test]
    fn test_discard_otp() {
        let parser = NotificationParser::new();
        let raw = RawNotification {
            package_name: "com.snapwork.hdfc".to_string(),
            title: "HDFC Alert".to_string(),
            text: "Your OTP for NetBanking is 492019. Do not share with anyone.".to_string(),
            timestamp: 1_700_000_000_000,
        };
        assert!(parser.parse(raw).is_none());
    }
}
