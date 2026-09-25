// NO-NETWORK
package com.cashbuddy.core.prob

class PolicyEngine {
    enum class Action { AUTO_LOG, LOG_AND_FLAG, ASK_USER, IGNORE }

    fun action(p: Double): Action = when {
        p >= 0.90 -> Action.AUTO_LOG
        p >= 0.65 -> Action.LOG_AND_FLAG
        p >= 0.30 -> Action.ASK_USER
        else -> Action.IGNORE
    }
}
