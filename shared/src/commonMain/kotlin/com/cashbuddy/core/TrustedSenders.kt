package com.cashbuddy.core

/**
 * In-memory manager for learned trusted bank/UPI senders.
 */
object TrustedSenderManager {
    private val trustedSenders = mutableSetOf(
        "HDFCBK", "ICICIB", "SBIINB", "SBICRD", "AXISBK", "KOTAKB",
        "INDUSB", "YESBNK", "PNBSMS", "CANBNK", "UNIONB",
        "IDFCFB", "BOISMS", "CBISMS", "UCOBNK", "PAYTM",
        "GPAY", "PHONEPE", "BHIMUPI", "CRED",
        "FEDBNK", "RBLBNK", "SCISMS", "CITIBK", "HSBCIN", "AUBANK", "BANDHN",
        "JUPITR", "FIBANK", "ONECRD", "SLICEC", "TATANEU"
    )

    fun learnTrustedSender(sender: String) {
        val clean = sender.trim().uppercase()
        if (clean.isNotEmpty()) {
            trustedSenders.add(clean)
        }
    }

    fun isTrustedSender(sender: String): Boolean {
        val clean = sender.trim().uppercase()
        return trustedSenders.any { clean.contains(it) || it.contains(clean) }
    }
}

fun learnTrustedSender(sender: String) = TrustedSenderManager.learnTrustedSender(sender)
fun isTrustedSender(sender: String): Boolean = TrustedSenderManager.isTrustedSender(sender)
