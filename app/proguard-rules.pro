# R8 / ProGuard rules for the release build of SALi-HCNSEC.
#
# Library consumer rules (Room, Retrofit, OkHttp, Compose) already cover most of
# what is needed; the rules below protect the JSON layer, which is the only part
# of this app that relies on reflective name lookups.

# --- Readable crash reports -------------------------------------------------
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Keep generic signatures / annotations needed by Retrofit + Moshi
-keepattributes Signature,InnerClasses,EnclosingMethod
-keepattributes RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations
-keepattributes AnnotationDefault

# --- Moshi (all DTOs use @JsonClass(generateAdapter = true) codegen) ---------
# Moshi resolves generated adapters with Class.forName("<Dto>JsonAdapter"), so
# neither the DTO class name nor the adapter class name may be obfuscated.
-keep @com.squareup.moshi.JsonClass class * { *; }
-keep class **JsonAdapter { <init>(...); }
-keepclassmembers class * { @com.squareup.moshi.Json <fields>; }
-keep @com.squareup.moshi.JsonQualifier @interface *
-keepclassmembers @com.squareup.moshi.JsonClass class * { <init>(); }
-dontwarn com.squareup.moshi.**
-dontwarn org.jetbrains.annotations.**

# --- Retrofit ---------------------------------------------------------------
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
-if interface * { @retrofit2.http.* public abstract *** *(...); }
-keep,allowobfuscation,allowshrinking class <1>
-dontwarn retrofit2.**
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn javax.annotation.**

# --- Room -------------------------------------------------------------------
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-dontwarn androidx.room.paging.**

# --- PDFBox Android -----------------------------------------------------------
# JPXFilter has an optional hard dependency on the JPEG2000 decoder library
# (com.gemalto.jp2). It is only exercised when decoding JPX images inside a
# PDF, which PDFBox-Android handles gracefully at runtime when absent.
-dontwarn com.gemalto.jp2.**

# --- Coroutines / Kotlin ----------------------------------------------------
-dontwarn kotlinx.coroutines.**
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }

# --- App entry points -------------------------------------------------------
-keep class com.example.SaliApplication { *; }
-keep class com.example.MainActivity { *; }
