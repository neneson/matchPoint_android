# Reglas de R8 (Fase 6: `isMinifyEnabled = true` en release).
#
# Lo único que R8 no puede deducir solo es kotlinx.serialization: los `$$serializer` se
# generan en compilación y sólo se alcanzan por reflexión desde `serializer()`. El
# artefacto ya trae reglas de consumidor, pero se dejan explícitas porque una regresión
# aquí no rompe el build: rompe el guardado del partido en tiempo de ejecución.

-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**

-keepclassmembers class cl.matchpoint.marcador.wear.** {
    *** Companion;
}
-keepclasseswithmembers class cl.matchpoint.marcador.wear.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class cl.matchpoint.marcador.wear.**$$serializer { *; }

# Ambient / always-on. Esto NO es opcional: sin ello el release compila, instala y corre,
# pero al apagarse la pantalla el reloj deja de tratar la app como always-on y pinta su
# ambient genérico (una foto borrosa de la app con el reloj encima) en vez de
# `AmbientScoreboard`. En logcat se ve como
# `AmbientTaskStateMachine: TaskInteractive -> TaskAmbientLite` — en una build sana dice
# `-> TaskAmbiactive ... is not eligible for ambient lite`.
#
# La causa: `AmbientLifecycleObserver` termina en `WearableControllerProvider$1`, que
# hereda de `com.google.android.wearable.compat.WearableActivityController$AmbientCallback`
# —una clase de la librería compartida que entra por `<uses-library required="false">`— y
# cuyos métodos llama el sistema **por nombre**. El aar trae su propia regla
# (`-keep,allowoptimization class androidx.wear.ambient.* { public *; }`) pero no alcanza.
-keep class androidx.wear.ambient.** { *; }
-keep class com.google.android.wearable.** { *; }
-dontwarn com.google.android.wearable.**

# Los `@Serializable` del dominio se serializan por nombre de propiedad.
-keepclassmembers @kotlinx.serialization.Serializable class cl.matchpoint.marcador.wear.** {
    <fields>;
}
