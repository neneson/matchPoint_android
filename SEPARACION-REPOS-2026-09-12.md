# Separación de matchpoint en dos repos (2026-09-12)

Objetivo: separar el código de `matchpoint` (un solo repo con 3 ramas) en dos repos GitHub:
- `neneson/matchpoint` → queda con la rama `devRene` (y `main`, conectada a Lovable, intacta).
- `neneson/matchPoint_android` → queda con la rama `wearOS` (repo estaba vacío).

No hubo commits nuevos: las 3 ramas locales ya estaban al día con `origin` antes de empezar.

## Estado previo

```bash
cd /mnt/1TB/Rene/Jobs/Claude/matchpoint
git status
git remote -v
git branch -a
```

Resultado: un solo remoto `origin` → `github.com/neneson/matchpoint.git`, con ramas
`main`, `devRene`, `wearOS` (las 3 también existían en el remoto).

Verificación con `gh`:

```bash
gh auth status
gh repo view neneson/matchPoint_android   # ya existía, vacío (isEmpty: true)
gh repo view neneson/matchpoint           # README confirma que main sincroniza con Lovable
```

Chequeo de que no había nada pendiente de subir:

```bash
git fetch origin --prune
git rev-parse wearOS origin/wearOS devRene origin/devRene main origin/main
# los 3 pares de hashes coinciden → nada que commitear/pushear en contenido
```

## Pasos ejecutados

1. Agregar el segundo repo como remoto nuevo (`android`):

   ```bash
   git remote add android https://github.com/neneson/matchPoint_android.git
   ```

2. Pushear la rama local `wearOS` al nuevo remoto (el repo estaba vacío, así que
   `wearOS` queda como rama por defecto automáticamente):

   ```bash
   git push android wearOS:wearOS
   ```

3. Apuntar el tracking local de `wearOS` al remoto nuevo:

   ```bash
   git branch --set-upstream-to=android/wearOS wearOS
   ```

4. Borrar la rama `wearOS` del repo original, ya que su contenido vive ahora en
   `matchPoint_android` (se deja `main` y `devRene` intactas):

   ```bash
   git push origin --delete wearOS
   ```

5. Verificación final:

   ```bash
   git ls-remote --heads origin      # solo main y devRene
   git branch -vv                    # wearOS -> android/wearOS ; devRene y main -> origin
   gh repo view neneson/matchPoint_android --json defaultBranchRef,isEmpty
   ```

## Resultado

- Un solo directorio local (`matchpoint/`) con **dos remotos**: `origin` (matchpoint) y
  `android` (matchPoint_android).
- `github.com/neneson/matchpoint`: ramas `main` (Lovable) y `devRene`.
- `github.com/neneson/matchPoint_android`: rama `wearOS` (por defecto).

## Nota

Este workspace tiene la regla general de **no hacer `git commit`/`git push`** (matchpoint
está conectado a Lovable vía la rama `main`). Para esta tarea puntual el usuario autorizó
el push explícitamente, limitado a `devRene` (matchpoint) y `wearOS` (matchPoint_android).
La regla general se mantiene para el resto del trabajo.
