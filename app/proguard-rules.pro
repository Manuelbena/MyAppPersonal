# Reglas de R8 para la versión release (isMinifyEnabled = true).
# Room, Hilt, Compose, WorkManager, Glance y Credential Manager traen las suyas en sus librerías.

# Trazas de errores legibles (con número de línea) aunque el código esté ofuscado
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Google API Client (Calendar y Tasks): rellena los modelos por reflexión a partir de los campos
# anotados con @Key. Sin esto, los eventos y tareas llegan vacíos en release.
-keepattributes Signature,RuntimeVisibleAnnotations,AnnotationDefault,EnclosingMethod,InnerClasses
-keep class com.google.api.services.calendar.** { *; }
-keep class com.google.api.services.tasks.** { *; }
-keepclassmembers class * {
    @com.google.api.client.util.Key <fields>;
}
-keep class com.google.api.client.** { *; }

# Clases opcionales que esas librerías mencionan pero que Android no tiene (no se usan)
-dontwarn com.google.api.client.extensions.android.**
-dontwarn com.google.appengine.**
-dontwarn javax.annotation.**
-dontwarn javax.naming.**
-dontwarn org.apache.http.**
-dontwarn org.ietf.jgss.**
-dontwarn org.joda.time.**
-dontwarn sun.misc.Unsafe
