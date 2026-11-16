# Spec 013 — Preparación del Release 1.2.1

**Estado:** preparación local cerrada el 2026-11-16; AC-1–AC-6 cubiertos. Release remoto no publicado.
**Predecesoras:** Specs 011 (`release-1.2.0`) y 012 (`guided-navigation-and-secret-display`), cerradas.

## 1. Objetivo

Preparar el siguiente paquete local de Password Vault como `v1.2.1`, incluyendo
las mejoras recientes de navegación guiada y visualización etiquetada de
contraseñas, manteniendo claro que `v1.2.0` es el último Release publicado hasta
que el usuario publique la nueva versión.

## 2. Alcance

**Dentro:**

- Actualizar versión Maven, nombre del JAR esperado por `vault.cmd` y referencias
  de descarga/verificación para el candidato `1.2.1`.
- Documentar en README la versión publicada `v1.2.0` y el paquete candidato
  `v1.2.1`, sin afirmar que el nuevo Release ya está publicado.
- Ejecutar `./mvnw.cmd verify` y comprobar `--help` del JAR.
- Preparar fuera del repo `password-vault-1.2.1.jar`, `vault.cmd` y
  `password-vault-1.2.1.jar.sha256` en
  `Downloads/password-vault-v1.2.1-release`.
- Recalcular SHA-256 sobre el JAR staged; verificar ayuda y que staging contiene
  exactamente los tres assets esperados.
- Registrar tamaños, hash, resultados y estado remoto en `docs/STATUS.md`.

**Fuera:**

- Crear el tag `v1.2.1`, publicar GitHub Release o cargar assets.
- Cambiar cifrado, formato de bóveda, dependencias o Threat Model.
- Incluir bóvedas, contraseñas, rutas personales u otros datos del usuario en los
  assets.

## 3. Decisiones

- Versión objetivo: `1.2.1`; Release publicado actual: `v1.2.0`.
- Assets separados: JAR ejecutable, `vault.cmd`, archivo SHA-256 del JAR.
- Staging previsto: `Downloads/password-vault-v1.2.1-release`. Si ya existe,
  detenerse y no sobrescribirla.
- SHA-256 detecta discrepancias de bytes, pero no es una firma digital.
- Preparación local solamente. Commit/push de cambios del repositorio sí se
  realizarán conforme a la instrucción del usuario; publicación/tag/carga siguen
  fuera de alcance.

## 4. Requisitos

- **R1 — Referencias coherentes:** POM, launcher, comandos README y nombres de
  assets usan `1.2.1`; README mantiene `v1.2.0` como último publicado hasta la
  publicación futura.
- **R2 — Build verificado:** `./mvnw.cmd verify` pasa con el conteo y omisión
  observados; `java -jar target/password-vault.jar --help` termina con estado 0.
- **R3 — Assets íntegros:** el staging contiene únicamente los tres assets
  aprobados y el hash SHA-256 del JAR staged coincide con el `.sha256`.
- **R4 — Launcher correcto:** `vault.cmd` busca `password-vault-1.2.1.jar` junto
  al script e inicia `start`, sin rutas personales.
- **R5 — Estado documentado:** `docs/STATUS.md` registra ruta, tamaños, hash,
  verificación, commit/push y que Release/tag/carga no se hicieron.
- **R6 — Seguridad y alcance:** no incluir datos de usuario ni modificar formato,
  dependencias o modelo de amenazas; no publicar remoto.

## 5. Criterios de aceptación

- **AC-1:** POM y launcher identifican `1.2.1`; el launcher conserva inicio guiado.
- **AC-2:** README documenta assets y comandos de `1.2.1`, y distingue al Release
  publicado `v1.2.0` del candidato local.
- **AC-3:** staging externo contiene exactamente los tres nombres versionados y
  no sobrescribe contenido preexistente.
- **AC-4:** ayuda del JAR staged termina con estado 0; SHA-256 local recalculado
  coincide con el archivo `.sha256`.
- **AC-5:** `./mvnw.cmd verify` y `git diff --check` pasan; Spotless no reporta
  archivos Java sin formato.
- **AC-6:** `docs/STATUS.md` describe resultados y pendientes; commit y push del
  repositorio se completan, pero no se crea tag/Release ni se suben assets.

## 6. Seguridad

- No crear ni copiar bóvedas o secretos de prueba a staging.
- Calcular checksum solo después de copiar/renombrar el JAR final.
- No cambiar `docs/FORMAT.md` ni `docs/THREAT_MODEL.md`.
- No ejecutar comandos que requieran secretos mediante pipes o redirecciones.

## 7. Cierre

Cerrar cuando AC-1–AC-6 tengan evidencia, los tres assets estén preparados fuera
del repo, y los cambios del repositorio estén en un commit enviado a `origin/main`.
El Release remoto permanece sin publicar hasta autorización específica.
