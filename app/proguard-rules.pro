-optimizationpasses 5

-repackageclasses ''

-allowaccessmodification

-keepattributes Annotation,InnerClasses,EnclosingMethod,Signature,Exceptions

-renamesourcefileattribute SourceFile

-keep interface org.xmlpull.v1.XmlPullParser { *; }
-keep interface org.xmlpull.v1.XmlPullParserFactory { *; }
-keep interface org.xmlpull.v1.XmlSerializer { *; }
-keep class org.xmlpull.v1.** { *; }

-keep class androidx.core.content.FileProvider { *; }

-keepclasseswithmembernames class * {
    native <methods>;
}

-keepclassmembers class kotlin.Metadata { *; }
-keep class kotlin.** { *; }
-keep class kotlinx.** { *; }

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

-keep class *Binding { *; }
-keepclassmembers class * extends androidx.databinding.ViewDataBinding {
    public static inflate(...);
    public static bind(...);
}

-keep class android.util.Base64 {
    public static byte[] decode(java.lang.String, int);
}

-keep class com.reandroid.** { *; }
-keepnames class com.reandroid.** { *; }
-keepclassmembers class com.reandroid.** { *; }
-dontwarn com.reandroid.**

-keep class com.android.apksig.** { *; }
-keepnames class com.android.apksig.** { *; }
-keepclassmembers class com.android.apksig.** { *; }
-dontwarn com.android.apksig.**

-keep class io.github.muntashirakon.apksig.** { *; }
-keepnames class io.github.muntashirakon.apksig.** { *; }
-keepclassmembers class io.github.muntashirakon.apksig.** { *; }
-dontwarn io.github.muntashirakon.apksig.**

-keep class java.security.** { *; }
-keep class javax.security.** { *; }
-keep class javax.crypto.** { *; }
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**

-keep class java.util.zip.** { *; }