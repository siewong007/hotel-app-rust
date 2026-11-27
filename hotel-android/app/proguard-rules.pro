# Keep Kotlin Serialization serializers and Retrofit interfaces intact under R8.
-keepattributes *Annotation*, InnerClasses, Signature, EnclosingMethod
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }

-keep,includedescriptorclasses class com.hotelapp.staff.data.dto.**$$serializer { *; }
-keepclassmembers class com.hotelapp.staff.data.dto.** { *** Companion; }
-keepclasseswithmembers class com.hotelapp.staff.data.dto.** { kotlinx.serialization.KSerializer serializer(...); }

# Keep @Serializable type-safe Navigation destinations.
-keep,includedescriptorclasses class com.hotelapp.staff.navigation.**$$serializer { *; }
-keepclassmembers class com.hotelapp.staff.navigation.** { *** Companion; }
-keepclasseswithmembers class com.hotelapp.staff.navigation.** { kotlinx.serialization.KSerializer serializer(...); }

# Keep @Serializable local storage, sync, and Glance widget models.
-keep,includedescriptorclasses class com.hotelapp.staff.core.sync.**$$serializer { *; }
-keepclassmembers class com.hotelapp.staff.core.sync.** { *** Companion; }
-keepclasseswithmembers class com.hotelapp.staff.core.sync.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.hotelapp.staff.core.network.**$$serializer { *; }
-keepclassmembers class com.hotelapp.staff.core.network.** { *** Companion; }
-keepclasseswithmembers class com.hotelapp.staff.core.network.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.hotelapp.staff.core.storage.**$$serializer { *; }
-keepclassmembers class com.hotelapp.staff.core.storage.** { *** Companion; }
-keepclasseswithmembers class com.hotelapp.staff.core.storage.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.hotelapp.staff.widget.**$$serializer { *; }
-keepclassmembers class com.hotelapp.staff.widget.** { *** Companion; }
-keepclasseswithmembers class com.hotelapp.staff.widget.** { kotlinx.serialization.KSerializer serializer(...); }

# Keep Retrofit service interfaces and generic signatures.
-keep,allowobfuscation,allowshrinking interface com.hotelapp.staff.data.api.** { *; }
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
-dontwarn retrofit2.Platform**
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement

# Keep GlanceAppWidget canonical class names intact so persisted widget bindings survive app updates.
-keepnames class * extends androidx.glance.appwidget.GlanceAppWidget
