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
-dontwarn com.google.android.gms.internal.mlkit_vision_text_common.**

# Tesseract4Android & Leptonica (F-Droid build JNI & native bindings)
-keep class cz.adaptech.tesseract4android.** { *; }
-keep class com.googlecode.leptonica.android.** { *; }
-keep class com.googlecode.tesseract.android.** { *; }
-dontwarn cz.adaptech.tesseract4android.**
-dontwarn com.googlecode.leptonica.android.**
-dontwarn com.googlecode.tesseract.android.**

# ProfileInstaller (Benchmark support)
-keep class androidx.profileinstaller.** { *; }
-dontwarn androidx.profileinstaller.**