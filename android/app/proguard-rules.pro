# Moshi
-keepclassmembers class * {
    @com.squareup.moshi.Json <fields>;
}
-keep class ir.siaheh.data.model.** { *; }
-keepclassmembers class ir.siaheh.data.model.** { *; }

# Retrofit
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**

# Kotlin serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

# JLatexMath (LaTeX rendering)
-keep class ru.noties.jlatexmath.** { *; }
-dontwarn ru.noties.jlatexmath.**
