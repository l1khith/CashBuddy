# PaisaPal KMP — UI/UX Specification

## 1. Design System & Theming

PaisaPal UI is developed with **Compose Multiplatform 1.11+** following Google's **Material Design 3 (Material You)** design guidelines.

### 1.1 Color Palette
- **Primary / Seed**: `#1E88E5` (Fintech Trust Blue)
- **Secondary**: `#26A69A` (Teal Mint)
- **Debit / Expense Accent**: `#E53935` (Crimson Coral)
- **Credit / Income Accent**: `#43A047` (Vibrant Emerald)
- **Surface Dark**: `#121212`
- **Surface Light**: `#F8F9FA`
- **Confidence High Badge**: `#4CAF50` (Green, $\ge 0.85$)
- **Confidence Medium Badge**: `#FFA726` (Amber, $0.50 - 0.84$)
- **Confidence Low Badge**: `#EF5350` (Red, $< 0.50$)

---

## 2. Navigation Architecture (Navigation 3)

Type-safe destinations implemented using Kotlin `@Serializable` objects:

```kotlin
sealed interface ScreenRoute {
    @Serializable data object Home : ScreenRoute
    @Serializable data object Transactions : ScreenRoute
    @Serializable data class TransactionDetail(val transactionId: Long) : ScreenRoute
    @Serializable data object ReviewInbox : ScreenRoute
    @Serializable data object Stats : ScreenRoute
    @Serializable data object Accounts : ScreenRoute
    @Serializable data object Budgets : ScreenRoute
    @Serializable data object Goals : ScreenRoute
    @Serializable data object Settings : ScreenRoute
    @Serializable data class AddTransaction(val prefilledId: Long? = null) : ScreenRoute
}
```

---

## 3. Screen Specifications & State Contracts

### 3.1 Home Screen (MVVM)
- **Balance Header**: Total combined active balance across accounts with hide/show toggle.
- **Monthly Delta**: Month-to-date total debits vs total credits with dynamic progress bar.
- **Review Banner**: Displayed when `unreviewedCount > 0`. Shows pending count and direct action to Review Inbox.
- **Recent Transactions**: Last 20 confirmed transactions grouped by date.
- **Quick Action FAB**: Add manual transaction.

#### State Contract:
```kotlin
data class HomeUiState(
    val isLoading: Boolean = false,
    val balance: Double = 0.0,
    val recentTransactions: List<TransactionWithCategory> = emptyList(),
    val unreviewedCount: Int = 0,
    val monthlyDebit: Double = 0.0,
    val monthlyCredit: Double = 0.0,
    val error: String? = null
)
```

### 3.2 Review Inbox Screen (MVI Pattern)
Designed for high-throughput, low-friction triaging of passive notification alerts.

- **Confidence Badges**: Clear visual rating of parser certainty.
- **Swipe Actions**:
  - Swipe Right: Confirm transaction.
  - Swipe Left: Reject / Discard transaction.
- **Inline Category Quick-Chip Selector**: Easily reclassify with 1 tap.
- **Batch Operations**: "Confirm All High-Confidence ($\ge 0.85$)" button.

#### MVI Contract:
```kotlin
sealed interface ReviewIntent {
    data class LoadPending(val forceRefresh: Boolean = false) : ReviewIntent
    data class ConfirmTransaction(val transactionId: Long) : ReviewIntent
    data class RejectTransaction(val transactionId: Long) : ReviewIntent
    data class ModifyCategory(val transactionId: Long, val newCategoryId: Long) : ReviewIntent
    data class FilterByConfidence(val minConfidence: Float) : ReviewIntent
    data object ConfirmAllHighConfidence : ReviewIntent
}

data class ReviewState(
    val transactions: List<TransactionWithCategory> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val stats: ReviewStats = ReviewStats()
)
```

### 3.3 Transactions Screen
- Search bar with live query filter.
- Filter chips: Type (All, Expense, Income), Category, Account, Date Range (Today, Week, Month, Custom).
- Grouped by day with subtotal summary.

### 3.4 Statistics & Insights Screen
- **Monthly Comparison Chart**: Multi-bar monthly spending comparisons.
- **Category Donut / Pie Chart**: Percentage breakdown by category.
- **Spending Trend**: 30-day cumulative velocity vs previous period.
- **Actionable Insights Cards**: E.g. *"Food spending increased by 18% compared to last month."*

### 3.5 Accounts Screen
- Card carousel for all accounts (Bank accounts, Wallets, Cash, Credit Cards).
- Individual account detail view with transaction filtering.
- Add / Edit Account modal.

### 3.6 Budgets & Goals Screen
- Category-level monthly spending limits with percentage gauge.
- Push alerts when spending exceeds 80% and 100% threshold.
- Visual goal saving tracker with expected completion dates.

### 3.7 Settings Screen
- Notification Listener permission status with 1-tap system settings intent.
- Auto-confirm threshold slider (Default ₹10,000).
- Biometric lock toggle.
- Export Data (CSV, Excel format via MediaStore API).
- Encrypted Database Backup & Restore.
- About & 100% Local Privacy Verification badge.
