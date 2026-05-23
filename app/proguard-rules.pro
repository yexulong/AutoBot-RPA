# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in ${sdk.dir}/tools/proguard/proguard-android.txt
# You can edit the include path and order by changing the proguardFiles
# directive in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Keep the Hilt generated classes
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ComponentSupplier { *; }

# Keep Room entities
-keep class com.autobot.rpa.data.model.** { *; }

# Keep Kotlin Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

# Keep Kotlin metadata
-keep class kotlin.Metadata { *; }

# Compose rules to prevent lock verification warnings
-keep class androidx.compose.runtime.** { *; }
-keep class androidx.compose.ui.** { *; }
-dontwarn androidx.compose.runtime.**
-dontwarn androidx.compose.ui.**

# Keep Compose snapshot classes to avoid lock verification issues
-keepclassmembers class androidx.compose.runtime.snapshots.SnapshotStateMap {
    *;
}
-keepclassmembers class androidx.compose.runtime.snapshots.** {
    *;
}
