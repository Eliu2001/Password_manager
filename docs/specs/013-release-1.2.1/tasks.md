# Tareas — Spec 013: Preparación del Release 1.2.1

Tareas de 20–30 minutos, con evidencia por criterio. Release remoto fuera de
alcance; commit/push del repositorio autorizados por el usuario.

- [x] **T01 — Versionar candidato y alinear documentación** (25 min) · R1, R4 · AC-1, AC-2
  - Actualizar POM y launcher a `1.2.1` manteniendo el comando `start`.
  - README debe mostrar `v1.2.0` como publicado y `v1.2.1` como candidato local.
  - **Hecho cuando:** nombres, links, comandos y estado no confunden publicado/candidato.
- [x] **T02 — Verificar build y ayuda** (25 min) · R2 · AC-1, AC-5
  - Ejecutar `./mvnw.cmd verify`, `git diff --check` y ayuda del JAR de `target`.
  - **Hecho cuando:** build verde; anotar pruebas/skip y `start` visible en ayuda.
- [x] **T03 — Preparar y validar staging** (25 min) · R3 · AC-3, AC-4
  - Confirmar ausencia de carpeta antes de crearla; copiar JAR versionado y launcher;
    generar `.sha256` después de copiar.
  - **Hecho cuando:** exactamente tres assets; hash recalculado coincide; JAR staged
    ejecuta `--help` con estado 0.
- [x] **T04 — Registrar cierre y publicar cambios de repositorio** (25 min) · R5, R6 · AC-5, AC-6
  - Completar STATUS/tareas con tamaños, hash, verificación, commit/push y estado remoto.
  - Ejecutar diff check final, commit y push a `origin/main`.
  - **Hecho cuando:** árbol limpio y sincronizado; no existe tag/Release nuevo ni
    carga de assets ejecutada por esta tarea.

## Cierre

Cerrar solo con evidencia de AC-1–AC-6. No crear `v1.2.1` ni publicar o subir
assets a GitHub sin autorización específica.

**Resultado — 2026-11-16:** POM/launcher y README identifican el candidato `1.2.1`;
`v1.2.0` sigue siendo el último Release publicado. `./mvnw.cmd verify` pasó con
145 pruebas, 0 fallos, 0 errores y 1 omitida por symlink no disponible en Windows.
La ayuda del JAR staged termina con estado 0 y muestra `start`. Staging contiene
exactamente `password-vault-1.2.1.jar` (6,772,248 bytes), `vault.cmd` (591 bytes)
y `password-vault-1.2.1.jar.sha256` (64 caracteres). SHA-256 recalculado sobre
el JAR staged: `6aeb92e8b530be88bb231dfdf7ba203ce5953e7b8b5fe305c8bc7aaba6468a5e`.
Cambios del repositorio comprometidos y enviados a `origin/main`. Después, el
usuario publicó el Release manualmente con los tres assets. El tag `v1.2.1` apunta
a `0597bf3`; GitHub informa para el JAR SHA-256
`6aeb92e8b530be88bb231dfdf7ba203ce5953e7b8b5fe305c8bc7aaba6468a5e`, igual al
hash del staging.
