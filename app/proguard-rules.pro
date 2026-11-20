# Rivara release shrinking rules.

# Keep line numbers so Crashlytics stack traces stay readable.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# NewPipeExtractor (YouTube) uses reflection and runs JavaScript through Rhino.
-keep class org.schabi.newpipe.extractor.** { *; }
-keep class org.mozilla.javascript.** { *; }
-keep class org.mozilla.classfile.ClassFileWriter
-dontwarn org.mozilla.javascript.**
-dontwarn org.mozilla.classfile.**
-dontwarn javax.script.**
-dontwarn java.beans.**
-dontwarn jdk.dynalink.**

# jsoup / JSON libraries pulled in by NewPipe.
-keep class org.jsoup.** { *; }
-dontwarn org.jsoup.**
-keep class com.grack.nanojson.** { *; }

# kotlinx.serialization: keep generated serializers for app models.
-keepattributes *Annotation*, InnerClasses
-keepclassmembers class com.example.musicapp.** {
    *** Companion;
}
-keepclasseswithmembers class com.example.musicapp.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.example.musicapp.**$$serializer { *; }

# OkHttp optional platform integrations.
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# WorkManager (background downloads) creates its Room database and startup initializers
# reflectively; R8 must keep their no-argument constructors.
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-keep class androidx.work.impl.WorkDatabase_Impl { <init>(); }
-keep class * implements androidx.startup.Initializer { <init>(); }
-keep class * extends androidx.work.ListenableWorker {
    <init>(android.content.Context, androidx.work.WorkerParameters);
}
