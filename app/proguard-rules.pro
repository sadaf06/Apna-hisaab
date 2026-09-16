# Custom ProGuard/R8 rules for Apna Hisaab app

# 1. General Kotlin and Coroutines rules
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod, SourceFile, LineNumberTable
-dontwarn kotlin.**
-dontwarn kotlinx.coroutines.**

# 2. Keep App Data Models and Entities (crucial for Moshi, Room, and Firebase serialization)
-keep class com.example.data.** { *; }
-keep class com.example.data.local.** { *; }
# API models (Gemini request/response) — Retrofit @Body / Moshi
-keep class com.example.api.** { *; }


# 3. Retrofit rules
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations

# 4. OkHttp rules
-dontwarn okhttp3.**
-keep class okhttp3.** { *; }
-dontwarn okio.**

# 5. Moshi rules (JSON parsing)
-dontwarn com.squareup.moshi.**
-keep class com.squareup.moshi.** { *; }
# Keep Moshi's generated JsonAdapters (** matches classes inside packages too)
-keep class **JsonAdapter { *; }
-keep class * { @com.squareup.moshi.JsonQualifier <fields>; }
# Keep @JsonClass models + their constructors/fields so codegen adapters resolve
-keepnames @com.squareup.moshi.JsonClass class *
-keepclassmembers @com.squareup.moshi.JsonClass class * { <init>(...); <fields>; }

# 6. Room Database rules
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.**

# 7. Firebase rules (Auth, Firestore, and AI SDKs)
-dontwarn com.google.firebase.**
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }

# 8. dev.chrisbanes.haze (Haze glass effect library)
-dontwarn dev.chrisbanes.haze.**
-keep class dev.chrisbanes.haze.** { *; }

# Please add these rules to your existing keep rules in order to suppress warnings.
# This is generated automatically by the Android Gradle plugin.
-dontwarn java.lang.management.ManagementFactory
-dontwarn java.lang.management.RuntimeMXBean
