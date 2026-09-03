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
