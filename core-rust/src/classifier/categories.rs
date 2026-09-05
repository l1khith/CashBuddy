use crate::Category;

pub const CATEGORY_LABELS: &[&str] = &[
    "Food",
    "Transport",
    "Shopping",
    "Bills",
    "Entertainment",
    "Health",
    "Education",
    "Housing",
    "Insurance",
    "Investments",
    "Salary",
    "Refund",
    "Gift",
    "Unknown",
];

pub const MODEL_LABELS: &[&str] = &[
    "Charity & Donations",
    "Entertainment & Recreation",
    "Financial Services",
    "Food & Dining",
    "Government & Legal",
    "Healthcare & Medical",
    "Income",
    "Shopping & Retail",
    "Transportation",
    "Utilities & Services",
];

pub fn model_label_to_category(label: &str) -> Category {
    match label {
        "Charity & Donations" => Category::Gift,
        "Entertainment & Recreation" => Category::Entertainment,
        "Financial Services" => Category::Investments,
        "Food & Dining" => Category::Food,
        "Government & Legal" => Category::Bills,
        "Healthcare & Medical" => Category::Health,
        "Income" => Category::Salary,
        "Shopping & Retail" => Category::Shopping,
        "Transportation" => Category::Transport,
        "Utilities & Services" => Category::Bills,
        _ => parse_category(label),
    }
}

pub fn parse_category(label: &str) -> Category {
    match label.to_lowercase().as_str() {
        "food" | "dining" => Category::Food,
        "transport" | "travel" => Category::Transport,
        "shopping" => Category::Shopping,
        "bills" | "utilities" => Category::Bills,
        "entertainment" => Category::Entertainment,
        "health" | "medical" => Category::Health,
        "education" => Category::Education,
        "housing" | "rent" => Category::Housing,
        "insurance" => Category::Insurance,
        "investments" => Category::Investments,
        "salary" => Category::Salary,
        "refund" => Category::Refund,
        "gift" => Category::Gift,
        _ => Category::Unknown,
    }
}
