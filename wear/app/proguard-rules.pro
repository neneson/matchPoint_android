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

# Los `@Serializable` del dominio se serializan por nombre de propiedad.
-keepclassmembers @kotlinx.serialization.Serializable class cl.matchpoint.marcador.wear.** {
    <fields>;
}
