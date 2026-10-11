package com.cashbuddy.platform

private var androidVersionName: String = "1.0"
private var androidVersionCode: Int = 1

fun setAppVersionInfo(versionName: String, versionCode: Int) {
    androidVersionName = versionName
    androidVersionCode = versionCode
}

actual fun currentTimeMillis(): Long = System.currentTimeMillis()

actual fun getAppVersionDisplay(): String = "CashBuddy v$androidVersionName (Build $androidVersionCode)"
