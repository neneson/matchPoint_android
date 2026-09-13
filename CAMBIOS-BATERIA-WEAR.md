# Ahorro de batería en el reloj (Fase 6)

Optimización de consumo de `wear/`. Detalle técnico y capturas en
[`wear/docs/FASE-6.md`](wear/docs/FASE-6.md).

## Lo grande

| Cambio | Qué ahorra |
|---|---|
| **Pantalla siempre encendida ahora es opcional** (ajuste en "Nuevo partido") y se desactiva sola si el reloj entra en ahorro de energía | Es el mayor consumidor de la app: el OLED encendido las 2 h del partido |
| Always-on sólo con partido en juego, no desde que se abre la app | Antes preparar un partido —o dejar la app abierta al terminar— dejaba el reloj encendido sin límite |
| El cronómetro no cuenta si nadie lo mira, y en ambient no tiene bucle propio | Hasta **7.200 despertares de CPU por partido** que ya no ocurren |
| La notificación del watch face es local y no se puentea al teléfono | ~200 encendidos de radio Bluetooth por partido |
| El servicio no trabaja mientras la app esté en pantalla | ~200 lecturas de disco + notificaciones por partido (verificado: 0 con la app delante, 1 al salir) |
| Fondo y botones casi negros (`#000000` / `#0B0C0F`) | En OLED un píxel negro no pide corriente; los botones ocupan media pantalla |
| Canal de notificación de importancia baja y silencioso | Antes tenía derecho a vibrar y sonar **en cada punto** |
| R8 + shrink de recursos | APK release **23,3 MB → 2,5 MB**; menos dex que cargar = arranque más barato |

Además: escrituras en flash confladas y sin reescrituras idénticas, cronómetro aislado en
su propio `Text` para no recomponer el marcador entero cada segundo, dominio marcado como
estable para Compose, y estilos de texto como constantes.

## APK listo para el reloj

`matchpoint-wear-1.0.apk` en la raíz (2,6 MB, firmado con la keystore del teléfono).
Instrucciones de instalación y de medición de batería en
[`INSTALAR-EN-RELOJ.md`](INSTALAR-EN-RELOJ.md).

Probar el APK corriendo (no sólo compilarlo) sacó **dos bugs** que no se ven de otra forma:

1. **R8 rompía el ambient.** La app se veía bien, pero al apagarse la pantalla salía el
   ambient genérico borroso del sistema en vez del nuestro. Faltaban reglas de `proguard`
   para `androidx.wear.ambient`. Sólo pasaba en release.
2. **Apagar el always-on no lo apagaba.** Quitar el observador de ambient no suelta la
   pantalla: la API de Wear tiene `setAmbientEnabled()` y no tiene contrario. Con el
   partido **ya terminado** el reloj seguía encendiéndose indefinidamente, y el apagado
   automático por ahorro de energía no hacía nada — o sea, dos de las promesas de esta
   misma tanda no se cumplían. Se arregla llamando a mano al `onDestroy` del observador.

Ambos corregidos y verificados. Detalle en `wear/docs/FASE-6.md`.

## Estado

- **39 tests JVM verdes.** 7 son específicos de batería: cuentan despertares del cronómetro
  con un reloj inyectado y un planificador virtual, porque una regresión aquí no se ve en
  pantalla sino en el porcentaje de batería tres horas después.
- Verificado en el emulador `matchpoint_wear`: el ajuste alterna y persiste tras un
  arranque en frío; con always-on apagado la app se va al fondo, con él encendido sigue
  pintando el marcador en ambient; sin partido no hay servicio ni receptores registrados.
- **El APK release probado corriendo**, no sólo compilando: ambient propio, partido que
  sobrevive a un `force-stop`, Ongoing Activity correcta, cero crashes.

## Lo que falta

- **Medir mAh reales en el reloj físico.** Todo lo anterior está medido en trabajo evitado
  (despertares, notificaciones, escrituras), no en batería: el emulador no modela la
  corriente del OLED ni la radio.
- El modo de ahorro de energía no se pudo disparar en el emulador (la imagen Wear ignora
  `low_power=1`); la ruta está verificada hasta el registro del receptor.

> Nada está commiteado: los cambios quedan en el working tree, como siempre.
