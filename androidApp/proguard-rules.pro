# CashBuddy R8 & ProGuard Optimization Rules

# SQLCipher
-keep class net.zetetic.** { *; }
-dontwarn net.zetetic.**


# SQLDelight
-keep class app.cash.sqldelight.** { *; }
-keep class com.cashbuddy.db.** { *; }

# Serialization & Annotations
-keepattributes *Annotation*,InnerClasses,Signature
-dontnote kotlinx.serialization.**