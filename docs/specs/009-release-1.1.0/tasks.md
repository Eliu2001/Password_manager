# Tareas — Spec 009: Release 1.1.0 con lanzador Windows

Tareas ordenadas por dependencia y diseñadas para bloques de **20–30 minutos**.
Registrar evidencia breve; no marcar una tarea completa antes de verificarla.
Preservar cambios locales de Spec 008 y no incluir temporales, secretos o bóvedas
en el repositorio. La spec fue aprobada y su implementación autorizada el
2026-11-15. Push, tag y publicación remota requieren autorización explícita
separada.

## Preparación y referencias de versión

- [x] **T01 — Revisar autorización, estado y referencias existentes** (20 min)\
  **RF:** R1, R6 · **AC:** AC-1, AC-7\
  **Hecho cuando:** se confirma Spec 009 aprobada y Specs 007/008 cerradas; se
  inspecciona el working tree para conservar cambios locales; se buscan todas las
  referencias `1.0.0` en POM, launcher, README, workflow y docs, distinguiendo las
  históricas de las referencias activas.\
  **Evidencia:** estado Git/HEAD registrado y mapa de referencias versionadas;
  confirmar que no se hará push/tag/publicación.

  **Resultado:** spec aprobada; HEAD `eb68880` en `main`. Cambios locales de
  Spec 008 (README, STATUS, `vault.cmd` y sus documentos) detectados y preservados.
  Mapeadas referencias activas en POM, README y launcher; referencias de Specs
  007/008 son históricas. No habrá push/tag/publicación remota.

- [x] **T02 — Actualizar versión Maven y launcher** (20 min)\
  **RF:** R1, R2 · **AC:** AC-1, AC-4\
  **Hecho cuando:** POM indica `1.1.0`, conserva `target/password-vault.jar` como
  artefacto de build y `vault.cmd` busca `password-vault-1.1.0.jar`; el Release
  anterior no se altera.\
  **Evidencia:** inspección del POM y del nombre exacto en script; no cambiar
  configuración de bóveda ni lógica de Spec 008.

  **Resultado:** `pom.xml` indica `1.1.0`, conserva `target/password-vault.jar`;
  `vault.cmd` espera `password-vault-1.1.0.jar`.

- [x] **T03 — Actualizar instrucciones activas de README** (25 min)\
  **RF:** R1, R5 · **AC:** AC-1, AC-5\
  **Hecho cuando:** README usa `1.1.0` en descargas, checksum, ejemplos e
  instrucciones de publicación; enumera los tres assets, explica que se descargan
  juntos, mantiene instrucciones de uso seguro y diferencia el nombre empaquetado
  del artefacto Maven en `target`.\
  **Evidencia:** buscar referencias obsoletas; mantener solo referencias `1.0.0`
  cuando describan explícitamente la versión histórica.

  **Resultado:** README usa `1.1.0` para descargas, checksum, comandos y
  publicación; documenta los tres assets juntos y conserva `target/password-vault.jar`
  para ejecución desde el código fuente. No quedan referencias activas a `1.0.0`.

## Build y preparación de assets

- [x] **T04 — Construir y comprobar JAR 1.1.0** (25 min)\
  **RF:** R1, R4 · **AC:** AC-1, AC-2\
  **Hecho cuando:** `./mvnw.cmd verify` crea el JAR autocontenido y
  `java -jar target/password-vault.jar --help` termina con 0 y muestra ayuda;
  manifest y dependencias/licencia conservan lo definido por Spec 007.\
  **Evidencia:** salida de Maven, estado/captura de ayuda y versión Maven efectiva.

  **Resultado:** `./mvnw.cmd verify` pasó con Maven identificando el proyecto
  como `1.1.0`; `java -jar target/password-vault.jar --help` devolvió 0 y mostró
  la ayuda. El artefacto conserva el empaquetado autocontenido.

- [x] **T05 — Preparar JAR y lanzador en staging externo** (20 min)\
  **RF:** R3 · **AC:** AC-3, AC-5\
  **Hecho cuando:** en una carpeta visible fuera del repositorio están
  `password-vault-1.1.0.jar` copiado del build y `vault.cmd` genérico sin editar
  con una ruta personal. No sobrescribir carpeta/archivos existentes.\
  **Evidencia:** ruta de staging, nombres y tamaños de ambos archivos; confirmar
  que no hay vault ni datos privados.

  **Resultado:** staging externo visible en Downloads, con JAR de 6,749,826
  bytes y `vault.cmd` de 999 bytes; sin ruta personal en el script ni bóveda.

- [x] **T06 — Generar y verificar SHA-256 del JAR staged** (20 min)\
  **RF:** R3, R4 · **AC:** AC-3\
  **Hecho cuando:** `password-vault-1.1.0.jar.sha256` contiene el hash
  hexadecimal esperado del JAR ya copiado/renombrado y la verificación recalculada
  desde staging coincide exactamente.\
  **Evidencia:** hash calculado dos veces y tamaños/nombres de JAR y checksum; no
  afirmar firma o autenticidad del publicador.

  **Resultado:** `password-vault-1.1.0.jar.sha256` contiene 64 caracteres
  hexadecimales. SHA-256 calculado y recalculado coincide:
  `cd741c0e3cd0d8155fe46cc645bc209568a3a3db47552a0c3bada722b0c6c44b`.

- [x] **T07 — Probar launcher actualizado con Java de prueba** (25 min)\
  **RF:** R2, R4 · **AC:** AC-4, AC-6\
  **Hecho cuando:** desde Windows se prueba el `vault.cmd` staged con el JAR
  esperado y el directorio de trabajo distinto; ruta sintética con espacios,
  argumentos exactos, configuración vacía/marcador, Java/JAR ausentes y códigos
  de salida permanecen cubiertos.\
  **Evidencia:** resultados de los casos con temporales fuera del repo, sin
  credenciales ni salida de bóvedas.

  **Resultado:** Windows mock ejecutado desde otro directorio; confirmó JAR
  `password-vault-1.1.0.jar`, ruta con espacios como argumento único y secuencia
  `-jar`, JAR, `session`, `--vault`, ruta. Los escenarios de error/códigos están
  cubiertos por las pruebas Windows de Spec 008; no se usaron secretos.

## Auditoría, documentación y cierre

- [x] **T08 — Revisar coherencia, seguridad y alcance remoto** (20 min)\
  **RF:** R1–R6 · **AC:** AC-1, AC-4, AC-5, AC-7\
  **Hecho cuando:** README, POM, `.cmd` y staging concuerdan; `v1.0.0` no se
  modifica; script mantiene marcador `VAULT_PATH`; no hay dependencias nuevas,
  rutas personales, bóvedas ni secretos. Confirma que no hubo push, tag o
  publicación.\
  **Evidencia:** búsqueda de referencias, `git diff --check` y lista final de
  assets; `docs/FORMAT.md` y `docs/THREAT_MODEL.md` sin cambios.

  **Resultado:** no quedan referencias activas `1.0.0` en README/script/POM; las
  menciones de Specs 007/008 y STATUS son históricas. Assets verificados; no se
  hizo push, tag ni publicación. Formato y Threat Model intactos.

- [x] **T09 — Registrar estado y completar verificación** (25 min)\
  **RF:** R1–R6 · **AC:** AC-1–AC-7\
  **Hecho cuando:** `docs/STATUS.md` registra versión, SHA, build, validación
  Windows, ruta de staging y acciones remotas pendientes; todos los AC tienen
  evidencia; `./mvnw.cmd verify` y `git diff --check` pasan. Cerrar sin afirmar que
  el Release está publicado.\
  **Evidencia:** salida final de Maven, conteo de tests, diff limpio y estado de
  los tres assets.

  **Resultado:** `docs/STATUS.md` registra versión, checksum, resultados Windows
  y staging externo. La verificación final pasó: 120 pruebas, 0 fallos, 0 errores,
  1 omitida por symlink no disponible; `git diff --check` limpio. No hubo acciones
  remotas.

## Criterio de cierre

T01–T09 completadas con evidencia. Spec 009 cerrada tras dejar los tres assets
verificados en staging externo. Push, tag `v1.1.0`, carga y publicación en GitHub
quedan a cargo del usuario.
