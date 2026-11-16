# Spec 011 — Preparación del Release 1.2.0

**Estado:** preparación local cerrada el 2026-11-16; AC-1–AC-6 cubiertos. El usuario publicó posteriormente `v1.2.0`; ver `docs/STATUS.md`.
**Predecesora:** Spec 010 (`guided-console-ux`), cerrada.

## 1. Objetivo

Preparar una distribución descargable `v1.2.0` que incluya la interfaz guiada de
consola, evitando la confusión entre el Release publicado `v1.1.0` y el código
actual de `main`.

## 2. Alcance

**Dentro:**

- Cambiar la versión Maven a `1.2.0` y actualizar el nombre esperado por el
  launcher Windows.
- Alinear README y `docs/STATUS.md`: identificar `v1.1.0` como Release publicado
  con CLI anterior y `v1.2.0` como siguiente distribución con flujo guiado.
- Ejecutar `verify`, comprobar ayuda del JAR, launcher y SHA-256.
- Preparar fuera del repositorio los tres adjuntos `password-vault-1.2.0.jar`,
  `vault.cmd` y `password-vault-1.2.0.jar.sha256` en
  `Downloads/password-vault-v1.2.0-release`.
- Registrar checksum, tamaños, resultado y limitaciones. Si la carpeta objetivo
  ya existe, detenerse y no sobrescribirla.

**Fuera:**

- Crear o subir tag/Release en GitHub, hacer push o reemplazar `v1.1.0`.
- Cambiar criptografía, formato de bóveda, dependencias o modelo de amenazas.
- Incluir bóvedas, secretos, rutas personales o datos de usuario en los assets.

## 3. Decisiones

- Versión objetivo: `1.2.0`; tag futuro esperado: `v1.2.0`.
- Assets: JAR ejecutable autocontenido, launcher genérico y checksum SHA-256 del
  JAR final.
- El checksum no es firma digital ni autentica por sí solo al publicador.
- La prueba TTY de Spec 010 fue reportada como exitosa por el usuario el
  2026-11-16; se registrará con esa procedencia, no como reproducida por el agente.
- Preparación local solamente. La publicación manual requiere que el usuario
  cree el tag/Release y cargue los tres assets.

## 4. Requisitos

- **R1 — Versión coherente:** POM, `vault.cmd`, nombres de assets y documentación
  identifican `1.2.0`; la documentación distingue la experiencia antigua de
  `v1.1.0` de la guiada de `v1.2.0`.
- **R2 — Assets íntegros:** staging contiene exactamente el JAR versionado,
  `vault.cmd` y el SHA-256 calculado sobre ese JAR staged.
- **R3 — Launcher:** el `.cmd` busca `password-vault-1.2.0.jar` junto al script,
  inicia `start` y no incluye rutas de bóveda personales.
- **R4 — Verificación:** `./mvnw.cmd verify`, ayuda del JAR staged, comprobación
  del SHA y revisión de los nombres/tamaños pasan.
- **R5 — Staging seguro:** carpeta externa al repo, sin archivos de usuario; no
  sobrescribir una carpeta preexistente.
- **R6 — Control remoto:** no crear tag ni GitHub Release ni subir assets. El
  push de cambios de código/documentación requiere autorización expresa separada;
  el usuario la otorgó después de cerrar la preparación local.

## 5. Criterios de aceptación

- **AC-1:** el POM informa `1.2.0`; el launcher espera el JAR `1.2.0` y ejecuta
  `start`.
- **AC-2:** README indica inequívocamente que `v1.1.0` sigue siendo el Release
  publicado anterior y que sus assets no contienen el menú guiado; documenta el
  estado del candidato `v1.2.0`.
- **AC-3:** la carpeta staging contiene los tres nombres aprobados y ningún otro
  archivo de usuario o secreto.
- **AC-4:** el JAR staged ejecuta `--help` con estado 0 y el SHA publicado local
  coincide con el calculado sobre la copia staged.
- **AC-5:** `./mvnw.cmd verify` pasa y `git diff --check` no reporta errores.
- **AC-6:** `docs/STATUS.md` registra la ruta, tamaños, hashes y que la publicación
  remota no se hizo.

## 6. Seguridad

- No incluir bóvedas ni contraseñas de prueba en staging.
- No cambiar `docs/FORMAT.md` ni `docs/THREAT_MODEL.md`.
- Mantener la exigencia de consola real para leer secretos; no probar secretos con
  pipes ni redirecciones.

## 7. Cierre

Cerrar cuando AC-1–AC-6 estén comprobados y los tres assets existan en staging.
La preparación local no implica publicación: detenerse antes de tag, Release,
carga de assets o push.
