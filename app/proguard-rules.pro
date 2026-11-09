# kotlinx.serialization: le classi @Serializable dell'app
-keepattributes *Annotation*, InnerClasses
-keepclassmembers @kotlinx.serialization.Serializable class com.daboprint3dtech.blink.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.daboprint3dtech.blink.**$$serializer { *; }

# Worker istanziati via reflection da WorkManager
-keep class com.daboprint3dtech.blink.work.** extends androidx.work.ListenableWorker { <init>(...); }
