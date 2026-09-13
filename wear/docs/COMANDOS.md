# Comandos básicos — Matchpoint Wear (Kotlin)

Todo desde `/mnt/1TB/Rene/Jobs/Claude/matchpoint/wear`. Setup de atajos:

```bash
export ANDROID_HOME=~/Android/Sdk
export ADB=$ANDROID_HOME/platform-tools/adb
export EMU=$ANDROID_HOME/emulator/emulator
cd /mnt/1TB/Rene/Jobs/Claude/matchpoint/wear
```

## Build

```bash
./gradlew assembleDebug          # compila, ~30 s
```

## Empaquetar (APK instalable)

```bash
./gradlew assembleDebug          # app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease        # firmado con la keystore del teléfono, ~85 s
```

## Abrir en el simulador (AVD `matchpoint_wear`)

```bash
$EMU -avd matchpoint_wear -no-snapshot-save -no-boot-anim -gpu swiftshader_indirect &
$ADB wait-for-device
until [ "$($ADB shell getprop sys.boot_completed | tr -d '\r')" = "1" ]; do sleep 5; done

$ADB install -r app/build/outputs/apk/debug/app-debug.apk
$ADB shell am start -n cl.matchpoint.marcador.wear/.presentation.MainActivity
```

## Enviar a un dispositivo real (reloj o celular conectado por USB)

```bash
$ADB devices                     # confirmar que aparece el device
$ADB install -r app/build/outputs/apk/debug/app-debug.apk
$ADB shell am start -n cl.matchpoint.marcador.wear/.presentation.MainActivity
```

> `INSTALL_FAILED_UPDATE_INCOMPATIBLE` = ya está instalada con otra firma (debug vs
> release). Antes: `$ADB uninstall cl.matchpoint.marcador.wear`.

## Todo en una línea (compilar + instalar + lanzar)

```bash
./gradlew assembleDebug \
  && $ADB install -r app/build/outputs/apk/debug/app-debug.apk \
  && $ADB shell am start -n cl.matchpoint.marcador.wear/.presentation.MainActivity
```

Detalle completo (logs, screencap, recrear el AVD) en `EJECUTAR-EMULADOR.md`.

---

# App de teléfono (Capacitor) — referencia rápida

Distinta app (`cl.matchpoint.marcador`, React/TanStack empaquetado con Capacitor), desde
`/mnt/1TB/Rene/Jobs/Claude/matchpoint`:

```bash
npm run build && npx cap sync android          # build web + copia a android/
cd android && ./gradlew assembleDebug          # empaquetar APK
adb install -r app/build/outputs/apk/debug/app-debug.apk   # enviar al celular
```

Sin AVD de teléfono creado en esta máquina — probado siempre en dispositivo real
(Galaxy A16). Detalle en `../BUILD-CAPACITOR.md`.
