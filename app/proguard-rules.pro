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

# Hilt/Dagger - Prevent R8 from obfuscating dependency injection code
-dontwarn dagger.hilt.**
-dontwarn javax.inject.**
-dontwarn com.test1.tv.**Hilt_**

-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }
-keep class dagger.hilt.android.internal.** { *; }
-keep class dagger.hilt.android.internal.lifecycle.** { *; }
-keep class hilt_aggregated_deps.** { *; }
-keep class com.test1.tv.**HiltComponents* { *; }
-keep class com.test1.tv.**HiltModules* { *; }

# Keep ViewModels with Hilt annotations
-keep,allowobfuscation @dagger.hilt.android.lifecycle.HiltViewModel class *
-keep class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}

# Keep Hilt generated classes but allow obfuscation
-keep,allowobfuscation class **_HiltComponents$** { *; }
-keep,allowobfuscation class **_HiltModules** { *; }
-keep,allowobfuscation class **_Factory
-keep,allowobfuscation class **_MembersInjector

# Keep injected constructors
-keepclasseswithmembers class * {
    @javax.inject.Inject <init>(...);
}

# Keep Hilt Android entry points
-keep,allowobfuscation @dagger.hilt.android.AndroidEntryPoint class *

# Material Components - Fix ColorStateList inflation
-keep class com.google.android.material.** { *; }
-dontwarn com.google.android.material.**
-keepclassmembers class com.google.android.material.** {
    *;
}

# Room database implementations must survive minification so Room can find the generated `_Impl`.
-keep class androidx.room.RoomDatabase { *; }
-keep class * extends androidx.room.RoomDatabase { *; }

# Gson/Retrofit models - Keep for serialization
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.test1.tv.data.model.** { *; }
-keep class com.test1.tv.data.remote.response.** { *; }

# Strip all Log.v (Verbose), Log.d (Debug), and Log.i (Info)
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
