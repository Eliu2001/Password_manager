# Plan técnico — Spec 013: Preparación del Release 1.2.1

**Estado:** preparación local autorizada por el usuario el 2026-11-16.
**Predecesoras:** Specs 011 y 012, cerradas.
**Alcance:** versionar, verificar y preparar assets locales; sin publicar Release.

## 1. Cambios previstos

- `pom.xml`: `1.2.0` → `1.2.1`; se conserva `target/password-vault.jar` como
  nombre de build.
- `vault.cmd`: buscar `password-vault-1.2.1.jar` y arrancar `start`.
- `README.md`: mantener descarga publicada `v1.2.0` como referencia actual e
  identificar `v1.2.1` como candidato preparado; mostrar comandos del candidato.
- `docs/STATUS.md`: registrar verificación, staging, hash, commit/push y acciones
  de publicación remota pendientes.
- `docs/specs/013-release-1.2.1/`: spec, plan y tareas trazables.
- Staging en Downloads, externo al repo: copiar JAR renombrado y `vault.cmd`,
  crear checksum SHA-256 del JAR staged y comprobar inventario exacto.

## 2. Secuencia

1. Verificar rama/árbol Git y que `Downloads/password-vault-v1.2.1-release` no
   exista; abortar staging si existe.
2. Actualizar POM, launcher, README y estado manteniendo `v1.2.0` como último
   Release publicado.
3. Ejecutar `./mvnw.cmd verify`, `git diff --check` y ayuda del JAR construido.
4. Crear staging externo, copiar los dos assets fuente, generar SHA-256 después
   de copiar y comprobar hash/inventario.
5. Actualizar estado/tareas con tamaños y evidencia final.
6. Commitear y hacer push a `origin/main`, según petición del usuario. No crear
   tag `v1.2.1`, GitHub Release ni subir assets.

## 3. Evidencia

| AC | Verificación |
|---|---|
| AC-1 | POM/launcher y prueba de `start` dentro de `verify`. |
| AC-2 | Revisión del README y consistencia de referencias `v1.2.0`/`v1.2.1`. |
| AC-3 | Inventario de la carpeta externa antes y después de staging. |
| AC-4 | Ejecutar `--help` desde el JAR staged y recalcular SHA-256. |
| AC-5 | Salida de Maven, Spotless y `git diff --check`. |
| AC-6 | `docs/STATUS.md`, hash del commit, resultado del push y ausencia de Release/tag/upload. |

## 4. Riesgos y límites

- No reemplazar ni modificar `v1.2.0` ya publicado.
- No sobrescribir un staging preexistente.
- El checksum no autentica al publicador; publicación y carga de assets son
  pasos manuales fuera de esta preparación.
- El push documenta el candidato en el repositorio, pero no crea un Release de
  GitHub ni prueba su futura página de descarga.
