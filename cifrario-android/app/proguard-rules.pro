# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-keepclassmembers @kotlinx.serialization.Serializable class ** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
# Jsoup: dependência opcional não usada no Android
-dontwarn org.jspecify.annotations.**
-dontwarn com.google.re2j.**
