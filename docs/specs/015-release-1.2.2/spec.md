# Spec 015 — Preparación del Release 1.2.2

**Estado:** preparación local cerrada el 2026-11-16; no publicado.
**Predecesoras:** Spec 014 (`linux-guided-config`) implementada; Spec 013
(`release-1.2.1`) cerrada.

## 1. Objetivo

Preparar el candidato `1.2.2` con la corrección de configuración del asistente
guiado en Linux, de modo que los assets puedan publicarse como una nueva versión
sin reemplazar el Release `v1.2.1`.

## 2. Alcance

**Dentro:**

- Actualizar la versión Maven y referencias del launcher/README a `1.2.2`.
- Ejecutar `./mvnw.cmd verify`, verificar la ayuda del JAR y `git diff --check`.
- Preparar fuera del repositorio `password-vault-1.2.2.jar`, `vault.cmd` y
  `password-vault-1.2.2.jar.sha256` en
  `Downloads/password-vault-v1.2.2-release`.
- Verificar inventario exacto, hash recalculado y checksum del JAR final.
- Registrar evidencia local, CI Linux y límites de prueba manual en `docs/STATUS.md`.

**Fuera:**

- Crear tag `v1.2.2`, GitHub Release o cargar assets remotamente.
- Cambiar el formato de bóveda, cifrado, dependencias o `docs/THREAT_MODEL.md`.
- Sobrescribir una carpeta de staging preexistente o incluir bóvedas/secretos.

## 3. Decisiones

- Versión objetivo: `1.2.2`; `v1.2.1` sigue siendo el último Release publicado
  hasta una publicación remota explícita.
- Assets: JAR ejecutable, launcher Windows `vault.cmd` y checksum SHA-256 del JAR.
- Si el staging `Downloads/password-vault-v1.2.2-release` ya existe, detenerse sin
  modificarla.
- El checksum comprueba coincidencia de bytes, no autentica al publicador.
- Preparación local solamente; no hacer commit/push por esta spec.

## 4. Requisitos y criterios de aceptación

- **R1 / AC-1 — Versión coherente:** POM y launcher identifican `1.2.2`; README
  distingue el Release publicado `v1.2.1` del candidato local.
- **R2 / AC-2 — Build:** `./mvnw.cmd verify` pasa; el JAR construido termina
  `--help` con estado 0 y muestra `start`.
- **R3 / AC-3 — Staging:** la carpeta externa contiene exactamente los tres assets
  aprobados. No se sobrescribe staging previa.
- **R4 / AC-4 — Integridad:** el `.sha256` del JAR staged coincide con el SHA-256
  recalculado sobre el asset final.
- **R5 / AC-5 — Corrección Linux:** el source de este candidato incluye la ruta
  XDG/fallback y permisos privados; CI Linux del commit de implementación
  `8287ced` pasa. No afirmar una prueba TTY Linux manual no realizada.
- **R6 / AC-6 — Alcance:** `git diff --check` pasa; no se crean tag/Release ni se
  cargan assets; no se cambia formato, dependencias ni Threat Model.

## 5. Seguridad y cierre

- No copiar bóvedas, contraseñas, secretos TOTP ni datos personales al staging.
- Calcular checksum después de copiar el JAR con su nombre definitivo.
- Los secretos continúan solicitándose solo en terminal interactiva; no automatizar
  comandos operativos mediante pipes o redirecciones.
- Cerrar cuando AC-1–AC-6 estén verificados y `docs/STATUS.md` registre tamaños,
  hash, pruebas y que la publicación remota sigue pendiente.

## 7. Cierre

AC-1–AC-6 verificados. El staging validado queda preparado fuera del repositorio;
tag, GitHub Release y carga de assets quedan pendientes de publicación explícita.
