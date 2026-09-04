# CashBuddy R8 & ProGuard Optimization Rules

# SQLCipher
-keep class net.zetetic.** { *; }
-dontwarn net.zetetic.**

# UniFFI & JNA Native Interop
-keep class com.sun.jna.** { *; }
-keep class * implements com.sun.jna.** { *; }
-keep class com.cashbuddy.core.** { *; }
-dontwarn com.sun.jna.**

# SQLDelight
-keep class app.cash.sqldelight.** { *; }
-keep class com.cashbuddy.db.** { *; }

# Serialization & Annotations
-keepattributes *Annotation*,InnerClasses,Signature
-dontnote kotlinx.serialization.**