// NO-NETWORK
package com.cashbuddy.core.anomaly

interface AnomalyNotifier {
    fun sendAnomalyAlert(transactionId: Long, amount: Double, merchant: String)
}
