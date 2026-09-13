# Cambio: ícono de la aplicación (2026-09-12)

Ícono de Android reemplazado por la pelota de tenis (`src/images/ball.png`, 512×512, fondo transparente).

## Generación

`ball.png` ya trae el contenido recortado con `bbox` (62,54)-(450,458) sobre fondo
transparente. Sin ImageMagick/`@capacitor/assets` disponibles: se generó con PIL
(script ad-hoc), recortando al bbox, centrando en un lienzo cuadrado y reescalando por densidad:

- **Legacy** (`ic_launcher.png` / `ic_launcher_round.png`, mdpi→xxxhdpi): pelota al 85 % del lienzo, fondo transparente.
- **Adaptive foreground** (`ic_launcher_foreground.png`): pelota al 62 % del lienzo (dentro de la
  zona segura de 108dp) para no cortarse con ninguna máscara de launcher.
- Fondo del adaptive icon: `#112555` (azul marino, ya fijado en `ic_launcher_background.xml` por un
  cambio previo en este mismo working tree — no se tocó).

Los XML `drawable`/`drawable-v24` del ícono placeholder (logo de Android por defecto)
ya estaban borrados de una sesión anterior; el favicon web (`public/favicon.ico`) y los
mipmaps de `wear/` también venían actualizados — no se volvieron a tocar.

## Build

`npm install` (node_modules no existía) + `cd android && ./gradlew assembleDebug` →
`BUILD SUCCESSFUL`. Ícono verificado extrayendo `res/mipmap-xxxhdpi-v4/ic_launcher_foreground.png`
del APK generado (`android/app/build/outputs/apk/debug/app-debug.apk`).

**No se sobrescribió** `matchpoint-1.0.apk` (el release 1.0 commiteado): un debug build no
lleva la firma de release. Si se quiere un nuevo release firmado con el ícono nuevo, es un
paso aparte.

## Pendiente

- Instalar el nuevo `app-debug.apk` en un dispositivo para confirmar el ícono en pantalla
  real (no se hizo en esta sesión).
