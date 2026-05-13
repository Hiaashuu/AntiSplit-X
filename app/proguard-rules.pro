# ─────────────────────────────────────────────────────────────────────────────
# Optimization
# ─────────────────────────────────────────────────────────────────────────────
-optimizationpasses 5

-repackageclasses ''

# ⚠ DO NOT add -adaptclassstrings — breaks Class.forName() string matching.
# ⚠ DO NOT add -overloadaggressively — breaks method-name reflection lookup.

-allowaccessmodification

-keepattributes Annotation,InnerClasses,EnclosingMethod,Signature,Exceptions

-renamesourcefileattribute SourceFile

# ─────────────────────────────────────────────────────────────────────────────
# XML Pull Parser  ← THIS IS THE NEW FIX
# ─────────────────────────────────────────────────────────────────────────────
# FileProvider reads file_paths.xml using android.content.res.XmlBlock$Parser,
# which is an Android FRAMEWORK class that implements XmlPullParser.
# R8 cannot rename framework classes, so if it renames this interface the
# cast fails with IncompatibleClassChangeError at install-intent time.
-keep interface org.xmlpull.v1.XmlPullParser { *; }
-keep interface org.xmlpull.v1.XmlPullParserFactory { *; }
-keep interface org.xmlpull.v1.XmlSerializer { *; }
-keep class org.xmlpull.v1.** { *; }

# FileProvider is in the manifest so R8 keeps the class, but we also need
# to keep its internal XML-parsing methods intact.
-keep class androidx.core.content.FileProvider { *; }

# ─────────────────────────────────────────────────────────────────────────────
# Native (JNI) methods
# ─────────────────────────────────────────────────────────────────────────────
-keepclasseswithmembernames class * {
    native <methods>;
}

# ─────────────────────────────────────────────────────────────────────────────
# Kotlin
# ─────────────────────────────────────────────────────────────────────────────
-keepclassmembers class kotlin.Metadata { *; }
-keep class kotlin.** { *; }
-keep class kotlinx.** { *; }

# ─────────────────────────────────────────────────────────────────────────────
# Android component entry points
# ─────────────────────────────────────────────────────────────────────────────
-keep public class * extends android.app.Application
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider
-keep public class * extends androidx.core.app.CoreComponentFactory

-keepclassmembers class * extends android.app.Activity {
    public <init>(...);
}

-keep public class * extends android.view.View {
    public <init>(android.content.Context);
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
}

# ─────────────────────────────────────────────────────────────────────────────
# ViewBinding / DataBinding
# ─────────────────────────────────────────────────────────────────────────────
-keep class *Binding { *; }
-keepclassmembers class * extends androidx.databinding.ViewDataBinding {
    public static inflate(...);
    public static bind(...);
}

# ─────────────────────────────────────────────────────────────────────────────
# Base64
# ─────────────────────────────────────────────────────────────────────────────
-keep class android.util.Base64 {
    public static byte[] decode(java.lang.String, int);
}

# ─────────────────────────────────────────────────────────────────────────────
# APKEditor (com.reandroid.**)
# ─────────────────────────────────────────────────────────────────────────────
-keep class com.reandroid.** { *; }
-keepnames class com.reandroid.** { *; }
-keepclassmembers class com.reandroid.** { *; }
-dontwarn com.reandroid.**

# ─────────────────────────────────────────────────────────────────────────────
# ApkSig
# ─────────────────────────────────────────────────────────────────────────────
-keep class com.android.apksig.** { *; }
-keepnames class com.android.apksig.** { *; }
-keepclassmembers class com.android.apksig.** { *; }
-dontwarn com.android.apksig.**

-keep class io.github.muntashirakon.apksig.** { *; }
-keepnames class io.github.muntashirakon.apksig.** { *; }
-keepclassmembers class io.github.muntashirakon.apksig.** { *; }
-dontwarn io.github.muntashirakon.apksig.**

# ─────────────────────────────────────────────────────────────────────────────
# Java Security
# ─────────────────────────────────────────────────────────────────────────────
-keep class java.security.** { *; }
-keep class javax.security.** { *; }
-keep class javax.crypto.** { *; }
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**

# ─────────────────────────────────────────────────────────────────────────────
# ZIP streams
# ─────────────────────────────────────────────────────────────────────────────
-keep class java.util.zip.** { *; }