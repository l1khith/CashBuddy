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

# AndroidX Security Crypto / Google Tink (reflective KeyManager & protobuf instantiation)
-keep class com.google.crypto.tink.** { *; }
-dontwarn com.google.crypto.tink.**
-dontwarn com.google.errorprone.annotations.**

# Google ML Kit Text Recognition (JNI & reflective initializers)
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit_vision_text_common.** { *; }
-dontwarn com.google.mlkit.**