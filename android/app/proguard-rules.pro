# Add project specific ProGuard rules here.
# Keep serialization classes
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-keep,includedescriptorclasses class com.bigdictaphone.app.**$$serializer { *; }
-keepclassmembers class com.bigdictaphone.app.** {
    *** Companion;
}
-keepclasseswithmembers class com.bigdictaphone.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}
