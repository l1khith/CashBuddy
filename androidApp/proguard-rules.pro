# CashBuddy R8 & ProGuard Optimization Rules

# SQLCipher
-keep class net.zetetic.** { *; }
-dontwarn net.zetetic.**


# SQLDelight
-keep class app.cash.sqldelight.** { *; }
-keep class com.cashbuddy.db.** { *; }
-keep class com.cashbuddy.data.** { *; }

# Serialization & Annotations
-keepattributes *Annotation*,InnerClasses,Signature,EnclosingMethod
-keepclassmembers class * {
    @kotlinx.serialization.SerialName <fields>;
}
-keepclassmembers class * {
    *** Companion;
}
-keepclasseswithmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclassmembers class * extends kotlinx.serialization.internal.GeneratedSerializer {
    <init>(...);
}
-keepclassmembers class * implements kotlinx.serialization.KSerializer {
    <init>(...);
}
-dontnote kotlinx.serialization.**

# Koin Dependency Injection
-keepclassmembers class * {
    public <init>(...);
}
-keep class org.koin.** { *; }
-dontwarn org.koin.**

# Navigation 3 & Compose Destinations
-keep class com.cashbuddy.presentation.navigation.** { *; }

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

# Strip verbose, debug, and info logs in release builds to prevent leaking sensitive financial data
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
}