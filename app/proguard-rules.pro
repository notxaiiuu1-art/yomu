# kotlinx.serialization: keep generated serializers of the app's models.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers @kotlinx.serialization.Serializable class app.orbis.** {
    *** Companion;
    *** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class app.orbis.**$$serializer { *; }
-keepclasseswithmembers class app.orbis.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-dontwarn com.google.zxing.**
