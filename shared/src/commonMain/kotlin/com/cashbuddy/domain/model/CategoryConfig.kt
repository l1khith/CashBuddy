package com.cashbuddy.domain.model

import androidx.compose.ui.graphics.Color
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class CategoryConfig(
    val id: Long,
    val name: String,
    val type: String,
    val colorHex: String,
    val icon: String,
    val keywords: List<String> = emptyList()
)

object CategoryRegistry {
    // JSON-driven category definitions with hex colors and icons
    val CATEGORIES_JSON = """
    [
      {
        "id": 1,
        "name": "Food & Dining",
        "type": "EXPENSE",
        "colorHex": "#F97066",
        "icon": "restaurant",
        "keywords": ["food", "dining", "restaurant", "cafe", "swiggy", "zomato", "uber eats", "starbucks", "mcdonalds", "burger", "pizza", "tea", "coffee", "chai"]
      },
      {
        "id": 2,
        "name": "Transportation",
        "type": "EXPENSE",
        "colorHex": "#2DD4BF",
        "icon": "directions_car",
        "keywords": ["transport", "transportation", "travel", "cab", "uber", "ola", "lyft", "fuel", "gas", "petrol", "diesel", "metro", "transit", "train", "flight", "auto"]
      },
      {
        "id": 3,
        "name": "Shopping",
        "type": "EXPENSE",
        "colorHex": "#38BDF8",
        "icon": "shopping_bag",
        "keywords": ["shopping", "groceries", "retail", "store", "amazon", "flipkart", "walmart", "target", "myntra", "supermarket", "mart"]
      },
      {
        "id": 4,
        "name": "Bills & Utilities",
        "type": "EXPENSE",
        "colorHex": "#86EFAC",
        "icon": "receipt",
        "keywords": ["bills", "bill", "utilities", "electric", "electricity", "water", "gas bill", "wifi", "broadband", "recharge", "airtel", "jio", "vi", "dth"]
      },
      {
        "id": 5,
        "name": "Entertainment",
        "type": "EXPENSE",
        "colorHex": "#C4B5FD",
        "icon": "movie",
        "keywords": ["entertainment", "movie", "cinema", "theatre", "ott", "netflix", "prime", "hotstar", "spotify", "youtube", "game", "gaming"]
      },
      {
        "id": 6,
        "name": "Healthcare",
        "type": "EXPENSE",
        "colorHex": "#FCD34D",
        "icon": "local_hospital",
        "keywords": ["health", "healthcare", "medical", "pharmacy", "hospital", "doctor", "clinic", "medicine", "chemist", "apollo", "1mg"]
      },
      {
        "id": 7,
        "name": "Education",
        "type": "EXPENSE",
        "colorHex": "#A78BFA",
        "icon": "school",
        "keywords": ["education", "school", "college", "course", "fee", "tuition", "books", "udemy", "coursera", "learning"]
      },
      {
        "id": 8,
        "name": "Housing",
        "type": "EXPENSE",
        "colorHex": "#FB7185",
        "icon": "home",
        "keywords": ["housing", "rent", "apartment", "maintenance", "flat", "society", "pg", "hostel"]
      },
      {
        "id": 9,
        "name": "Insurance",
        "type": "EXPENSE",
        "colorHex": "#60A5FA",
        "icon": "shield",
        "keywords": ["insurance", "policy", "premium", "lic", "health insurance", "term plan"]
      },
      {
        "id": 10,
        "name": "Investments",
        "type": "EXPENSE",
        "colorHex": "#34D399",
        "icon": "trending_up",
        "keywords": ["investment", "investments", "stock", "stocks", "mutual fund", "sip", "zerodha", "groww", "crypto", "gold", "fd"]
      },
      {
        "id": 11,
        "name": "Salary",
        "type": "INCOME",
        "colorHex": "#10B981",
        "icon": "account_balance",
        "keywords": ["salary", "payroll", "stipend", "wages", "bonus"]
      },
      {
        "id": 12,
        "name": "Refund",
        "type": "INCOME",
        "colorHex": "#FB923C",
        "icon": "replay",
        "keywords": ["refund", "cashback", "reversal"]
      },
      {
        "id": 13,
        "name": "Gift",
        "type": "INCOME",
        "colorHex": "#F472B6",
        "icon": "card_giftcard",
        "keywords": ["gift", "reward", "prize"]
      },
      {
        "id": 14,
        "name": "Uncategorized",
        "type": "EXPENSE",
        "colorHex": "#9CA3AF",
        "icon": "help_outline",
        "keywords": []
      }
    ]
    """.trimIndent()

    private val json = Json { ignoreUnknownKeys = true }
    val defaultCategories: List<CategoryConfig> = json.decodeFromString(CATEGORIES_JSON)

    private val nameToConfig: Map<String, CategoryConfig> = defaultCategories.associateBy { it.name.lowercase() }
    private val colorCache: MutableMap<String, Color> = mutableMapOf()

    fun parseHexColor(hex: String?, fallback: Color = Color(0xFF9CA3AF)): Color {
        if (hex.isNullOrBlank()) return fallback
        val clean = hex.removePrefix("#")
        return try {
            when (clean.length) {
                6 -> {
                    val rgb = clean.toLong(16)
                    Color((0xFF000000 or rgb).toInt())
                }
                8 -> {
                    val argb = clean.toLong(16)
                    Color(argb)
                }
                else -> fallback
            }
        } catch (_: Exception) {
            fallback
        }
    }

    fun getColor(categoryName: String?): Color {
        if (categoryName.isNullOrBlank()) return Color(0xFF9CA3AF)
        val key = categoryName.trim()
        colorCache[key]?.let { return it }

        val lower = key.lowercase()
        // 1. Direct name lookup
        nameToConfig[lower]?.let {
            val c = parseHexColor(it.colorHex)
            colorCache[key] = c
            return c
        }

        // 2. Keyword sub-match
        for (cat in defaultCategories) {
            if (lower.contains(cat.name.lowercase()) || cat.keywords.any { lower.contains(it) }) {
                val c = parseHexColor(cat.colorHex)
                colorCache[key] = c
                return c
            }
        }

        val fallback = Color(0xFF9CA3AF)
        colorCache[key] = fallback
        return fallback
    }

    fun getIcon(categoryName: String?): String {
        if (categoryName.isNullOrBlank()) return "help_outline"
        val lower = categoryName.lowercase().trim()
        nameToConfig[lower]?.let { return it.icon }

        for (cat in defaultCategories) {
            if (lower.contains(cat.name.lowercase()) || cat.keywords.any { lower.contains(it) }) {
                return cat.icon
            }
        }
        return "help_outline"
    }
}
