# Add project specific ProGuard rules here.
# Keep kotlinx.serialization metadata.
-keepattributes *Annotation*, InnerClasses
-keep,includedescriptorclasses class com.enes.**$$serializer { *; }
-keepclassmembers class com.enes.** {
    *** Companion;
}

# --- LiquidTube ---
-keepattributes *Annotation*, InnerClasses, Signature, EnclosingMethod
-dontwarn org.slf4j.**
-dontwarn java.lang.management.**
-dontwarn io.ktor.util.debug.**
# kotlinx.serialization models
-keep class com.enes.innertube.models.** { *; }
-keepclassmembers class com.enes.** { *** Companion; }
-keepclasseswithmembers class com.enes.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.enes.**$$serializer { *; }
# Ktor engine + content negotiation
-keep class io.ktor.client.engine.okhttp.** { *; }
-keep class io.ktor.serialization.kotlinx.** { *; }

# Premium module: the hidden WebView calls these by name.
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
