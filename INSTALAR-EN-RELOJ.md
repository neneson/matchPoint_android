# Instalar Matchpoint en el reloj

**APK listo:** `matchpoint-wear-1.0.apk` (2,6 MB, firmado, R8, `versionCode 2`).
No confundir con `matchpoint-1.0.apk`, que es el del **teléfono** (Capacitor).

```
paquete    cl.matchpoint.marcador.wear
minSdk     30  → Wear OS 3 o superior (Galaxy Watch 4 en adelante)
firma      misma keystore que la app de teléfono (android/matchpoint-release.jks)
```

## 1. Preparar el reloj (una sola vez)

En el reloj:

1. **Ajustes → Acerca del reloj → Software** → tocar 5 veces *Número de compilación*
   (aparece "Ya eres desarrollador").
2. **Ajustes → Opciones de desarrollador** → activar **Depuración ADB** y
   **Depurar por Wi-Fi**.
3. Anotar la **IP** que muestra ahí mismo (o en Ajustes → Conexiones → Wi-Fi → la red).

El reloj y el PC tienen que estar en la **misma red Wi-Fi**.

## 2. Conectar e instalar

```bash
export ADB=~/Android/Sdk/platform-tools/adb
cd /mnt/1TB/Rene/Jobs/Claude/matchpoint

$ADB connect <IP-del-reloj>:5555      # aceptar el diálogo que sale en el reloj
$ADB devices                          # debe aparecer <IP>:5555  device
$ADB install matchpoint-wear-1.0.apk
```

> **Wear OS 4/5** puede pedir emparejar primero. En ese caso *Opciones de desarrollador →
> Depuración inalámbrica* muestra un **código y un puerto**:
> `$ADB pair <IP>:<puerto-de-emparejamiento>` y después el `connect` de arriba.

> Si sale `INSTALL_FAILED_UPDATE_INCOMPATIBLE` es que ya hay una versión firmada con otra
> clave: `$ADB uninstall cl.matchpoint.marcador.wear` y reinstalar.

La app queda en la lista de aplicaciones del reloj como **Matchpoint**.

## 3. Primer arranque

Al tocar **Empezar** el reloj pide permiso de notificaciones: hay que **concederlo**, es lo
que mantiene el marcador visible en el watch face y el partido vivo con la pantalla
apagada. Si se negó por accidente:

```bash
$ADB shell pm grant cl.matchpoint.marcador.wear android.permission.POST_NOTIFICATIONS
```

En la pantalla de preparación está el ajuste **Pantalla**:

- *Siempre encendida* — el marcador se ve sin levantar la muñeca. Es lo que más batería
  gasta.
- *Se apaga (ahorra)* — el reloj se apaga solo; el partido sigue corriendo y guardándose.

## 4. Medir la batería (el pendiente de la Fase 6)

Antes del partido:

```bash
$ADB shell dumpsys battery | grep level      # anotar el %
$ADB shell dumpsys batterystats --reset
```

Jugar el partido completo con la app abierta. Al terminar:

```bash
$ADB shell dumpsys battery | grep level      # % final
$ADB shell dumpsys batterystats > ~/batterystats-partido.txt
grep -A30 "Estimated power use" ~/batterystats-partido.txt
```

Conviene medir **dos partidos**: uno con *Siempre encendida* y otro con *Se apaga*. La
diferencia entre ambos es el precio real del always-on, que es justamente el número que
falta para cerrar la Fase 6.

## 5. Volver a generar el APK

```bash
cd wear
ANDROID_HOME=~/Android/Sdk ./gradlew assembleRelease
cp app/build/outputs/apk/release/app-release.apk ../matchpoint-wear-1.0.apk
```

**Subir `versionCode` en `wear/app/build.gradle.kts` en cada APK nuevo**, o el reloj
rechaza la instalación por downgrade.
