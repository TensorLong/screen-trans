# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# 최적화 단계
-optimizationpasses 1

# Android SDK 클래스와 관련된 경고 무시
-dontwarn android.**

# Android SDK 클래스 유지 (난독화 및 제거 방지)
-keep class android.** { *; }

# Android SDK 인터페이스 유지 (난독화 및 제거 방지)
-keep interface android.** { *; }

# Keep Application class
-keep class android.app.Application { *; }

# AndroidX와 관련된 경고 무시
-dontwarn androidx.**

# AndroidX 클래스 유지 (난독화 및 제거 방지)
-keep class androidx.** { *; }

# AndroidX 인터페이스 유지 (난독화 및 제거 방지)
-keep interface androidx.** { *; }

# java 패키지를 변경하거나 제거하지 않도록 함
-dontwarn java.**
-keep class java.** { *; }
-keep interface java.** { *; }

# javax.mail, javax.json, javax.script 등 다양한 확장 기능을 가진 라이브러리를 안전하게 유지
-dontwarn javax.**
-keep class javax.** { *; }
-keep interface javax.** { *; }

# org.json 패키지의 모든 클래스가 ProGuard에 의해 변경되지 않도록 보호
-dontwarn org.json.**
-keep class org.json.** { *; }
-keep interface org.json.** { *; }

-dontwarn sun.reflect.CallerSensitive

# Keep all Activities, Services, BroadcastReceivers, and ContentProviders
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider

# Keep annotations(Hilt, Gson)
-keepattributes *Annotation*
-keepattributes RuntimeVisibleAnnotations
-keepattributes AnnotationDefault

# Preserve line numbers in the original source files for debugging (해킹에 취약함)
-keepattributes SourceFile,LineNumberTable

# 내부 클래스와 관련된 메타데이터 유지
-keepattributes InnerClasses

# enum 클래스의 특정 메서드(멤버)를 유지
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Timber 설정
-assumenosideeffects class timber.log.Timber {
    public static void d(...);
    public static void v(...);
    public static void i(...);
    public static void w(...);
    public static void e(...);
}

# Compose
-keep class androidx.compose.** { *; }
-keep class androidx.compose.ui.** { *; }
-dontwarn androidx.compose.**

# Coroutines
-dontwarn kotlinx.coroutines.**
-keep class kotlinx.coroutines.** { *; }

# Hilt/Dagger
-dontwarn dagger.**
-keep class dagger.** { *; }
-keep interface dagger.** { *; }

# Generated Hilt Code
-keep class **_MembersInjector { *; }
-keep class **_Factory { *; }
-keep class **_Component { *; }
-keep class **_Subcomponent { *; }

# Retrofit
-keep class retrofit2.** { *; }
-keepattributes Signature
-keepattributes Exceptions
-dontwarn retrofit2.**
-dontwarn okhttp3.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }

# Gson
-keep class com.google.gson.** { *; }
-dontwarn com.google.gson.**
-dontwarn sun.misc.**

# Firebase Core
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**

# Firebase Analytics
-keep class com.google.android.gms.measurement.** { *; }
-dontwarn com.google.android.gms.measurement.**

# Firebase Crashlytics
-keep class com.google.firebase.crashlytics.** { *; }
-dontwarn com.google.firebase.crashlytics.**

# Firebase Config
-keep class com.google.firebase.remoteconfig.** { *; }
-dontwarn com.google.firebase.remoteconfig.**

# Firebase Auth
-keep class com.google.firebase.auth.** { *; }
-dontwarn com.google.firebase.auth.**

# Firebase App Check
-keep class com.google.firebase.appcheck.** { *; }
-dontwarn com.google.firebase.appcheck.**

# Play Integrity (Firebase 관련)
-keep class com.google.android.play.core.integrity.** { *; }
-dontwarn com.google.android.play.core.integrity.**

# ML Kit
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**
-dontwarn com.google.android.gms.**

# ExoPlayer
-keep class com.google.android.exoplayer2.** { *; }
-dontwarn com.google.android.exoplayer2.**

# Coil
-keep class coil.** { *; }
-dontwarn coil.**

# DeepL API (deepl-java)
-keep class com.deepl.** { *; }
-dontwarn com.deepl.**

# DeepL의 JSON 변환에 사용되는 데이터 클래스 보호
-keepclassmembers class com.deepl.api.* {
    private <fields>;
    private <methods>;
}

-dontwarn org.jetbrains.kotlin.**
-keep class org.jetbrains.kotlin.** { *; }
-keep class org.jetbrains.kotlin.compiler.plugin.** { *; }
-keep class org.jetbrains.kotlin.diagnostics.** { *; }
-keep class org.jetbrains.kotlin.fir.extensions.** { *; }

# META-INF/services 디렉토리 보호
-keepnames class * implements org.jetbrains.kotlin.compiler.plugin.*
-keepnames class * implements org.jetbrains.kotlin.diagnostics.*
-keepnames class * implements org.jetbrains.kotlin.fir.extensions.*
































