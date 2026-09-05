use std::collections::HashMap;
use std::sync::RwLock;

#[derive(Debug, Clone, PartialEq)]
pub struct CategoryMatch {
    pub category: String,
    pub confidence: f32,
    pub source: String,
}

#[derive(Debug, Clone)]
pub struct MerchantRuleEntry {
    pub merchant: String,
    pub category: String,
}

pub struct CategoryEngine {
    user_rules: RwLock<HashMap<String, String>>,
    keyword_map: Vec<(&'static str, &'static str)>,
}

impl CategoryEngine {
    pub fn new() -> Self {
        let mut keyword_map: Vec<(&'static str, &'static str)> = Vec::new();

        // 1. Food & Dining
        keyword_map.push(("swiggy", "Food & Dining"));
        keyword_map.push(("zomato", "Food & Dining"));
        keyword_map.push(("mcdonald", "Food & Dining"));
        keyword_map.push(("kfc", "Food & Dining"));
        keyword_map.push(("burger king", "Food & Dining"));
        keyword_map.push(("domino", "Food & Dining"));
        keyword_map.push(("pizza hut", "Food & Dining"));
        keyword_map.push(("starbucks", "Food & Dining"));
        keyword_map.push(("subway", "Food & Dining"));
        keyword_map.push(("barbeque nation", "Food & Dining"));
        keyword_map.push(("haldiram", "Food & Dining"));
        keyword_map.push(("chai point", "Food & Dining"));
        keyword_map.push(("chaayos", "Food & Dining"));
        keyword_map.push(("faasos", "Food & Dining"));
        keyword_map.push(("behrouz", "Food & Dining"));
        keyword_map.push(("eatfit", "Food & Dining"));
        keyword_map.push(("restaurant", "Food & Dining"));
        keyword_map.push(("cafe", "Food & Dining"));
        keyword_map.push(("dhaba", "Food & Dining"));
        keyword_map.push(("bistro", "Food & Dining"));
        keyword_map.push(("canteen", "Food & Dining"));
        keyword_map.push(("kitchen", "Food & Dining"));
        keyword_map.push(("biryani", "Food & Dining"));
        keyword_map.push(("sweets", "Food & Dining"));
        keyword_map.push(("bakery", "Food & Dining"));
        keyword_map.push(("dining", "Food & Dining"));
        keyword_map.push(("tea", "Food & Dining"));
        keyword_map.push(("coffee", "Food & Dining"));

        // 2. Groceries
        keyword_map.push(("blinkit", "Groceries"));
        keyword_map.push(("zepto", "Groceries"));
        keyword_map.push(("instamart", "Groceries"));
        keyword_map.push(("bigbasket", "Groceries"));
        keyword_map.push(("bb daily", "Groceries"));
        keyword_map.push(("dmart", "Groceries"));
        keyword_map.push(("spencer", "Groceries"));
        keyword_map.push(("nature's basket", "Groceries"));
        keyword_map.push(("supermarket", "Groceries"));
        keyword_map.push(("provision", "Groceries"));
        keyword_map.push(("kirana", "Groceries"));
        keyword_map.push(("vegetables", "Groceries"));
        keyword_map.push(("fruits", "Groceries"));
        keyword_map.push(("dairy", "Groceries"));
        keyword_map.push(("milk", "Groceries"));

        // 3. Transportation
        keyword_map.push(("uber", "Transportation"));
        keyword_map.push(("ola", "Transportation"));
        keyword_map.push(("rapido", "Transportation"));
        keyword_map.push(("irctc", "Transportation"));
        keyword_map.push(("metro", "Transportation"));
        keyword_map.push(("redbus", "Transportation"));
        keyword_map.push(("makemytrip", "Transportation"));
        keyword_map.push(("yatra", "Transportation"));
        keyword_map.push(("goibibo", "Transportation"));
        keyword_map.push(("cleartrip", "Transportation"));
        keyword_map.push(("indigo", "Transportation"));
        keyword_map.push(("air india", "Transportation"));
        keyword_map.push(("vistara", "Transportation"));
        keyword_map.push(("spicejet", "Transportation"));
        keyword_map.push(("akasa", "Transportation"));
        keyword_map.push(("petrol", "Transportation"));
        keyword_map.push(("fuel", "Transportation"));
        keyword_map.push(("hpcl", "Transportation"));
        keyword_map.push(("bpcl", "Transportation"));
        keyword_map.push(("indianoil", "Transportation"));
        keyword_map.push(("shell", "Transportation"));
        keyword_map.push(("fastag", "Transportation"));
        keyword_map.push(("toll", "Transportation"));
        keyword_map.push(("parking", "Transportation"));
        keyword_map.push(("auto", "Transportation"));
        keyword_map.push(("cab", "Transportation"));
        keyword_map.push(("taxi", "Transportation"));

        // 4. Shopping & Retail
        keyword_map.push(("amazon", "Shopping & Retail"));
        keyword_map.push(("flipkart", "Shopping & Retail"));
        keyword_map.push(("myntra", "Shopping & Retail"));
        keyword_map.push(("meesho", "Shopping & Retail"));
        keyword_map.push(("ajio", "Shopping & Retail"));
        keyword_map.push(("nykaa", "Shopping & Retail"));
        keyword_map.push(("tata cliq", "Shopping & Retail"));
        keyword_map.push(("reliancedigital", "Shopping & Retail"));
        keyword_map.push(("reliance", "Shopping & Retail"));
        keyword_map.push(("croma", "Shopping & Retail"));
        keyword_map.push(("ikea", "Shopping & Retail"));
        keyword_map.push(("decathlon", "Shopping & Retail"));
        keyword_map.push(("zara", "Shopping & Retail"));
        keyword_map.push(("h&m", "Shopping & Retail"));
        keyword_map.push(("uniqlo", "Shopping & Retail"));
        keyword_map.push(("westside", "Shopping & Retail"));
        keyword_map.push(("lifestyle", "Shopping & Retail"));
        keyword_map.push(("shoppers stop", "Shopping & Retail"));
        keyword_map.push(("mall", "Shopping & Retail"));
        keyword_map.push(("store", "Shopping & Retail"));
        keyword_map.push(("retail", "Shopping & Retail"));

        // 5. Bills & Utilities
        keyword_map.push(("bescom", "Bills & Utilities"));
        keyword_map.push(("tneb", "Bills & Utilities"));
        keyword_map.push(("mahadiscom", "Bills & Utilities"));
        keyword_map.push(("cesc", "Bills & Utilities"));
        keyword_map.push(("bsnl", "Bills & Utilities"));
        keyword_map.push(("airtel", "Bills & Utilities"));
        keyword_map.push(("jio", "Bills & Utilities"));
        keyword_map.push(("vi ", "Bills & Utilities"));
        keyword_map.push(("vodafone", "Bills & Utilities"));
        keyword_map.push(("tata play", "Bills & Utilities"));
        keyword_map.push(("dish tv", "Bills & Utilities"));
        keyword_map.push(("sun direct", "Bills & Utilities"));
        keyword_map.push(("adani gas", "Bills & Utilities"));
        keyword_map.push(("indraprastha gas", "Bills & Utilities"));
        keyword_map.push(("mahanagar gas", "Bills & Utilities"));
        keyword_map.push(("electricity", "Bills & Utilities"));
        keyword_map.push(("power", "Bills & Utilities"));
        keyword_map.push(("water", "Bills & Utilities"));
        keyword_map.push(("piped gas", "Bills & Utilities"));
        keyword_map.push(("broadband", "Bills & Utilities"));
        keyword_map.push(("wifi", "Bills & Utilities"));
        keyword_map.push(("lpg", "Bills & Utilities"));
        keyword_map.push(("cylinder", "Bills & Utilities"));
        keyword_map.push(("billdesk", "Bills & Utilities"));
        keyword_map.push(("recharge", "Bills & Utilities"));

        // 6. Entertainment & Recreation
        keyword_map.push(("bookmyshow", "Entertainment & Recreation"));
        keyword_map.push(("pvr", "Entertainment & Recreation"));
        keyword_map.push(("inox", "Entertainment & Recreation"));
        keyword_map.push(("cinepolis", "Entertainment & Recreation"));
        keyword_map.push(("netflix", "Entertainment & Recreation"));
        keyword_map.push(("prime video", "Entertainment & Recreation"));
        keyword_map.push(("disney", "Entertainment & Recreation"));
        keyword_map.push(("hotstar", "Entertainment & Recreation"));
        keyword_map.push(("spotify", "Entertainment & Recreation"));
        keyword_map.push(("apple music", "Entertainment & Recreation"));
        keyword_map.push(("youtube", "Entertainment & Recreation"));
        keyword_map.push(("gaana", "Entertainment & Recreation"));
        keyword_map.push(("wynk", "Entertainment & Recreation"));
        keyword_map.push(("playstation", "Entertainment & Recreation"));
        keyword_map.push(("steam", "Entertainment & Recreation"));
        keyword_map.push(("gaming", "Entertainment & Recreation"));
        keyword_map.push(("cult.fit", "Entertainment & Recreation"));
        keyword_map.push(("gym", "Entertainment & Recreation"));
        keyword_map.push(("fitness", "Entertainment & Recreation"));
        keyword_map.push(("movie", "Entertainment & Recreation"));
        keyword_map.push(("cinema", "Entertainment & Recreation"));

        // 7. Healthcare & Medical
        keyword_map.push(("apollo", "Healthcare & Medical"));
        keyword_map.push(("1mg", "Healthcare & Medical"));
        keyword_map.push(("pharmeasy", "Healthcare & Medical"));
        keyword_map.push(("netmeds", "Healthcare & Medical"));
        keyword_map.push(("medplus", "Healthcare & Medical"));
        keyword_map.push(("practo", "Healthcare & Medical"));
        keyword_map.push(("max healthcare", "Healthcare & Medical"));
        keyword_map.push(("fortis", "Healthcare & Medical"));
        keyword_map.push(("dr lal", "Healthcare & Medical"));
        keyword_map.push(("metropolis", "Healthcare & Medical"));
        keyword_map.push(("clinic", "Healthcare & Medical"));
        keyword_map.push(("hospital", "Healthcare & Medical"));
        keyword_map.push(("pharmacy", "Healthcare & Medical"));
        keyword_map.push(("chemist", "Healthcare & Medical"));
        keyword_map.push(("diagnostic", "Healthcare & Medical"));
        keyword_map.push(("dental", "Healthcare & Medical"));
        keyword_map.push(("lenskart", "Healthcare & Medical"));

        // 8. Financial Services & Investments
        keyword_map.push(("zerodha", "Financial Services & Investments"));
        keyword_map.push(("groww", "Financial Services & Investments"));
        keyword_map.push(("upstox", "Financial Services & Investments"));
        keyword_map.push(("angel one", "Financial Services & Investments"));
        keyword_map.push(("indmoney", "Financial Services & Investments"));
        keyword_map.push(("kuvera", "Financial Services & Investments"));
        keyword_map.push(("smallcase", "Financial Services & Investments"));
        keyword_map.push(("coin", "Financial Services & Investments"));
        keyword_map.push(("etmoney", "Financial Services & Investments"));
        keyword_map.push(("lic", "Financial Services & Investments"));
        keyword_map.push(("hdfc life", "Financial Services & Investments"));
        keyword_map.push(("icici pru", "Financial Services & Investments"));
        keyword_map.push(("sbi life", "Financial Services & Investments"));
        keyword_map.push(("max life", "Financial Services & Investments"));
        keyword_map.push(("star health", "Financial Services & Investments"));
        keyword_map.push(("policybazaar", "Financial Services & Investments"));
        keyword_map.push(("insurance", "Financial Services & Investments"));
        keyword_map.push(("mutual fund", "Financial Services & Investments"));
        keyword_map.push(("brokerage", "Financial Services & Investments"));
        keyword_map.push(("securities", "Financial Services & Investments"));

        // 9. Education
        keyword_map.push(("byju", "Education"));
        keyword_map.push(("unacademy", "Education"));
        keyword_map.push(("vedantu", "Education"));
        keyword_map.push(("upgrad", "Education"));
        keyword_map.push(("udemy", "Education"));
        keyword_map.push(("coursera", "Education"));
        keyword_map.push(("school", "Education"));
        keyword_map.push(("college", "Education"));
        keyword_map.push(("university", "Education"));
        keyword_map.push(("institute", "Education"));
        keyword_map.push(("tuition", "Education"));
        keyword_map.push(("academy", "Education"));
        keyword_map.push(("classes", "Education"));
        keyword_map.push(("course", "Education"));
        keyword_map.push(("exam", "Education"));

        Self {
            user_rules: RwLock::new(HashMap::new()),
            keyword_map,
        }
    }

    /// Load user-learned rules from local database into memory on startup
    pub fn load_user_rules(&self, rules: Vec<MerchantRuleEntry>) {
        if let Ok(mut lock) = self.user_rules.write() {
            lock.clear();
            for entry in rules {
                let key = entry.merchant.trim().to_lowercase();
                if !key.is_empty() {
                    lock.insert(key, entry.category);
                }
            }
        }
    }

    /// Learn a new correction immediately in memory (O(1) update)
    pub fn learn_correction(&self, merchant: String, category: String) {
        let key = merchant.trim().to_lowercase();
        if key.is_empty() || category.is_empty() {
            return;
        }
        if let Ok(mut lock) = self.user_rules.write() {
            lock.insert(key, category);
        }
    }

    /// Priority-based category lookup:
    /// 1. User learned rules (Confidence: 1.0, Source: "UserRule")
    /// 2. Curated keyword map (Confidence: 0.90, Source: "KeywordMap")
    /// 3. Fallback / Unknown (Confidence: 0.50, Source: "Fallback")
    pub fn get_category(&self, merchant: String) -> CategoryMatch {
        let m_lower = merchant.trim().to_lowercase();
        if m_lower.is_empty() {
            return CategoryMatch {
                category: "Unknown".to_string(),
                confidence: 0.50,
                source: "Fallback".to_string(),
            };
        }

        // Tier 1: User rules (exact or bidirectional substring match)
        if let Ok(lock) = self.user_rules.read() {
            if let Some(cat) = lock.get(&m_lower) {
                return CategoryMatch {
                    category: cat.clone(),
                    confidence: 1.0,
                    source: "UserRule".to_string(),
                };
            }
            // Substring search in user rules if longer than 3 chars
            if m_lower.len() >= 3 {
                for (pattern, cat) in lock.iter() {
                    if pattern.len() >= 3 && (m_lower.contains(pattern) || pattern.contains(&m_lower)) {
                        return CategoryMatch {
                            category: cat.clone(),
                            confidence: 1.0,
                            source: "UserRule".to_string(),
                        };
                    }
                }
            }
        }

        // Tier 2: Curated Indian keyword map
        for &(keyword, cat) in &self.keyword_map {
            if m_lower.contains(keyword) {
                return CategoryMatch {
                    category: cat.to_string(),
                    confidence: 0.90,
                    source: "KeywordMap".to_string(),
                };
            }
        }

        // Tier 3: Fallback
        CategoryMatch {
            category: "Unknown".to_string(),
            confidence: 0.50,
            source: "Fallback".to_string(),
        }
    }
}

impl Default for CategoryEngine {
    fn default() -> Self {
        Self::new()
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_category_engine_priority() {
        let engine = CategoryEngine::new();

        // Initially Zomato matches KeywordMap -> Food & Dining
        let res = engine.get_category("Zomato India".to_string());
        assert_eq!(res.category, "Food & Dining");
        assert_eq!(res.confidence, 0.90);
        assert_eq!(res.source, "KeywordMap");

        // User overrides Zomato to Entertainment & Recreation
        engine.learn_correction("Zomato".to_string(), "Entertainment & Recreation".to_string());
        let res2 = engine.get_category("Zomato India".to_string());
        assert_eq!(res2.category, "Entertainment & Recreation");
        assert_eq!(res2.confidence, 1.0);
        assert_eq!(res2.source, "UserRule");
    }

    #[test]
    fn test_unknown_fallback() {
        let engine = CategoryEngine::new();
        let res = engine.get_category("XYZ Random Merchant 999".to_string());
        assert_eq!(res.category, "Unknown");
        assert_eq!(res.confidence, 0.50);
        assert_eq!(res.source, "Fallback");
    }

    #[test]
    fn test_batch_load_rules() {
        let engine = CategoryEngine::new();
        let entries = vec![
            MerchantRuleEntry {
                merchant: "Chaiwala Uncle".to_string(),
                category: "Food & Dining".to_string(),
            },
            MerchantRuleEntry {
                merchant: "Landlord Ramesh".to_string(),
                category: "Housing & Rent".to_string(),
            },
        ];
        engine.load_user_rules(entries);

        let res = engine.get_category("Payment to Chaiwala Uncle".to_string());
        assert_eq!(res.category, "Food & Dining");
        assert_eq!(res.confidence, 1.0);
        assert_eq!(res.source, "UserRule");

        let res_rent = engine.get_category("Landlord Ramesh".to_string());
        assert_eq!(res_rent.category, "Housing & Rent");
        assert_eq!(res_rent.confidence, 1.0);
        assert_eq!(res_rent.source, "UserRule");
    }
}
