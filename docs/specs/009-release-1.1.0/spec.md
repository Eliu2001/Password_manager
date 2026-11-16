# Spec 009 — Release 1.1.0 con lanzador Windows

**Estado:** preparación local cerrada el 2026-11-15; AC-1–AC-7 cubiertos.  
**Predecesoras:** Spec 007 (`distribution`) y Spec 008 (`windows-launcher`), cerradas.

## 1. Objetivo

Publicar una nueva versión de Password Vault con el JAR y el lanzador Windows
descargables desde una misma página de GitHub Releases, evitando que el usuario
tenga que combinar archivos de ubicaciones distintas.

## 2. Alcance

**Dentro:**

- Preparar la siguiente versión como **1.1.0**, sin modificar ni reemplazar el
  Release publicado `v1.0.0`.
- Actualizar la versión del proyecto y las referencias versionadas en el
  empaquetado/documentación para la distribución `1.1.0`.
- Preparar tres adjuntos para el Release manual `v1.1.0`:
  `password-vault-1.1.0.jar`, `vault.cmd` y
  `password-vault-1.1.0.jar.sha256`.
- Ajustar el lanzador para que busque `password-vault-1.1.0.jar` en la misma
  carpeta en la que se descargan los adjuntos; se conserva la configuración
  editable `VAULT_PATH` y el comportamiento de sesión aprobado en Spec 008.
- Actualizar README para que la versión recomendada y las instrucciones de
  descarga reflejen `v1.1.0`, expliquen que los tres adjuntos se descargan juntos
  y describan la preparación/publicación manual.
- Verificar el artefacto y los archivos preparados antes de la publicación. La
  creación del tag/Release y la carga/publicación en GitHub serán manuales por el
  usuario, salvo autorización posterior expresa.

**Fuera:**

- Modificar, borrar o reemplazar el Release o tag `v1.0.0`.
- Publicación automática en GitHub, automatización de tags, firmas digitales,
  instaladores nativos, JVM incluida o cambios funcionales a la CLI.
- Incluir la ruta de bóveda del usuario, una bóveda o secretos en cualquiera de
  los adjuntos.
- Cambios al formato de bóveda, criptografía o `docs/THREAT_MODEL.md`.

## 3. Decisiones acordadas

- Versión nueva: `1.1.0` y tag `v1.1.0`.
- El Release incluirá JAR, lanzador `.cmd` y checksum SHA-256 del JAR.
- El checksum es del JAR; no se presenta como firma digital ni como autenticación
  independiente del publicador.
- El lanzador requiere que el usuario edite `VAULT_PATH` antes de usarlo, y espera
  el JAR versionado junto al `.cmd`.
- La publicación en GitHub seguirá siendo manual.

## 4. Requisitos

- **R1 — Versión consistente:** POM, nombres de artefacto descargable, launcher,
  README y notas/instrucciones de publicación se refieren a `1.1.0` donde
  corresponda; el JAR publicado se llama `password-vault-1.1.0.jar`.
- **R2 — Lanzador compatible:** `vault.cmd` busca
  `password-vault-1.1.0.jar` en su carpeta, conserva las comprobaciones y el
  comportamiento de Spec 008, y no contiene rutas personales.
- **R3 — Adjuntos completos:** los archivos a cargar son exactamente el JAR
  versionado, `vault.cmd` y el archivo
  `password-vault-1.1.0.jar.sha256` con el hash hexadecimal del mismo JAR.
- **R4 — Verificación:** ejecutar `./mvnw.cmd verify`, probar la ayuda del JAR,
  verificar el checksum del archivo final y probar el launcher con Java de prueba
  para la ruta de JAR actualizada y los escenarios de Spec 008.
- **R5 — Documentación:** README identifica el Release `v1.1.0`, los tres assets,
  su colocación conjunta, configuración de `VAULT_PATH`, Java 21+, uso seguro y
  pasos manuales de publicación/verificación.
- **R6 — Publicación controlada:** no crear ni publicar el Release/tag remoto ni
  hacer push como parte de esta spec sin autorización explícita para esas acciones.

## 5. Criterios de aceptación

- **AC-1:** POM y README presentan `1.1.0` como nueva versión; `v1.0.0` permanece
  intacto.
- **AC-2:** el build produce un JAR ejecutable; `java -jar ... --help` termina
  correctamente.
- **AC-3:** el JAR final tiene el nombre `password-vault-1.1.0.jar` y el archivo
  `.sha256` coincide con su SHA-256 calculado.
- **AC-4:** `vault.cmd` del paquete encuentra el JAR `1.1.0` en su carpeta y
  conserva los comportamientos/configuración cubiertos por Spec 008.
- **AC-5:** README enumera los tres adjuntos y explica cómo descargarlos y
  utilizarlos juntos sin exponer secretos ni incluir la bóveda.
- **AC-6:** `./mvnw.cmd verify`, las comprobaciones del JAR/checksum y las pruebas
  Windows del lanzador pasan; se registra la evidencia real.
- **AC-7:** no se publica ni se reemplaza ningún Release remoto sin autorización
  explícita del usuario.

## 6. Seguridad y restricciones

- No escribir contraseñas, tokens, rutas privadas o datos de bóveda en los
  adjuntos, ejemplos o logs de prueba.
- El script continúa delegando autenticación a la consola de la CLI y no recibe
  secretos por argumentos, variables de entorno ni redirecciones.
- No agregar dependencias. No cambiar formatos, `docs/FORMAT.md` ni
  `docs/THREAT_MODEL.md`.
- El checksum solo permite verificar que el JAR coincide con el valor adjunto; no
  afirma autoría criptográfica. El hash debe calcularse después de crear el
  archivo final con su nombre de distribución.

## 7. Cierre

La preparación local se cerró el 2026-11-15: AC-1–AC-7 cubiertos, `verify` en
verde, ayuda/checksum/launcher validados y tres adjuntos listos en staging externo.
`docs/STATUS.md` registra versión, checksum y resultados. No se hizo push ni se
creó/publicó el tag o Release; esas acciones siguen manuales y pendientes del
usuario.
