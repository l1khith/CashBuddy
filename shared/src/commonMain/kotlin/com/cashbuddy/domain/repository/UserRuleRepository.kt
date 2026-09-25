// NO-NETWORK
package com.cashbuddy.domain.repository

data class UserRule(
    val merchantNormalized: String,
    val category: String,
    val updatedAt: Long,
    val source: RuleSource
)

enum class RuleSource {
    USER_EXPLICIT,
    INFERRED
}

interface UserRuleRepository {
    suspend fun getAll(): List<UserRule>
    suspend fun getForMerchant(merchantNormalized: String): UserRule?
    suspend fun saveRule(rule: UserRule)
    suspend fun deleteRule(merchantNormalized: String)
    suspend fun deleteAll()
}
