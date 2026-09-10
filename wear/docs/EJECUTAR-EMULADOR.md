# Ejecutar Matchpoint Wear en el emulador

Todo desde `/mnt/1TB/Rene/Jobs/Claude/matchpoint/wear`. El SDK está en `~/Android/Sdk`.

Atajos que uso en los ejemplos:

```bash
export ANDROID_HOME=~/Android/Sdk
export ADB=$ANDROID_HOME/platform-tools/adb
export EMU=$ANDROID_HOME/emulator/emulator
cd /mnt/1TB/Rene/Jobs/Claude/matchpoint/wear
```

## 1. Arrancar el emulador

El AVD `matchpoint_wear` ya existe (454×454 redonda, `android-34;android-wear;x86_64`):

```bash
$EMU -list-avds                       # debe listar: matchpoint_wear
$EMU -avd matchpoint_wear -no-snapshot-save -no-boot-anim -gpu swiftshader_indirect &
```

Esperar a que termine de arrancar (tarda ~1 min):

```bash
$ADB wait-for-device
until [ "$($ADB shell getprop sys.boot_completed | tr -d '\r')" = "1" ]; do sleep 5; done
$ADB devices                          # emulator-5554  device
```

## 2. Compilar

```bash
ANDROID_HOME=$ANDROID_HOME ./gradlew assembleDebug     # ~30 s
ANDROID_HOME=$ANDROID_HOME ./gradlew assembleRelease   # ~85 s, firmado con la keystore del teléfono
```

APKs en `app/build/outputs/apk/{debug,release}/`.

## 3. Instalar y lanzar

```bash
$ADB install -r app/build/outputs/apk/debug/app-debug.apk
$ADB shell am start -n cl.matchpoint.marcador.wear/.presentation.MainActivity
```

Un ciclo completo (compilar + instalar + lanzar) en una línea:

```bash
ANDROID_HOME=$ANDROID_HOME ./gradlew assembleDebug \
  && $ADB install -r app/build/outputs/apk/debug/app-debug.apk \
  && $ADB shell am start -n cl.matchpoint.marcador.wear/.presentation.MainActivity
```

> Si al instalar sale `INSTALL_FAILED_UPDATE_INCOMPATIBLE` es que está la versión firmada
> con otra clave (debug vs release). Desinstalar primero:
> `$ADB uninstall cl.matchpoint.marcador.wear`

## 4. Ver qué pasa

```bash
# Captura de pantalla a un archivo local
$ADB shell screencap -p /sdcard/cap.png && $ADB pull /sdcard/cap.png ./cap.png

# Logs de la app
$ADB logcat -c                                  # limpiar
$ADB logcat -b crash                            # crashes
$ADB logcat --pid=$($ADB shell pidof cl.matchpoint.marcador.wear)

# Qué actividad está al frente / medidas de la pantalla
$ADB shell dumpsys activity activities | grep -m1 topResumedActivity
$ADB shell wm size; $ADB shell wm density
```

## 5. Parar / limpiar

```bash
$ADB shell am force-stop cl.matchpoint.marcador.wear
$ADB uninstall cl.matchpoint.marcador.wear
$ADB emu kill                                   # apagar el emulador
```

## Recrear el AVD desde cero (sólo si se pierde)

```bash
$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager "system-images;android-34;android-wear;x86_64"
$ANDROID_HOME/cmdline-tools/latest/bin/avdmanager create avd \
  -n matchpoint_wear -k "system-images;android-34;android-wear;x86_64" -d wearos_large_round
```

## Nota: la app de teléfono NO corre aquí

`cl.matchpoint.marcador` (el APK de Capacitor) instala pero muestra *"This app requires a
WebView to work"*: la imagen Wear del emulador no trae ningún proveedor de WebView.
Ver `FASE-0-1.md`.
