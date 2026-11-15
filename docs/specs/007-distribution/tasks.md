# Tareas — Spec 007: Distribución del CLI

Tareas ordenadas por dependencia y pensadas para bloques de **20–30 minutos**.
Si una tarea supera ese tamaño, dividirla antes de continuar. Completar cada
tarea con evidencia breve (archivos/pruebas ejecutadas y resultado); no marcarla
completa sin verificar sus criterios. No usar secretos reales ni fixtures
sensibles. Implementación autorizada por el usuario el 2026-11-15; plugin
`org.apache.maven.plugins:maven-shade-plugin:3.6.2` aprobado.

## Preparación y autorización

- [x] **T01 — Confirmar autorización y revisar build/distribución** (20 min)\
  **RF:** R1, R2, R4 · **AC:** AC-1–AC-3, AC-6\
  **Hecho cuando:** se confirma que la predecesora está cerrada y existe
  autorización explícita para implementar; se inspeccionan POM, wrapper, Java
  requerido, manifest/entry point, dependencias transitivas/licencias, CI y
  comportamiento de `--help`/consola. Se identifica plugin candidato sin añadirlo
  todavía.\
  **Evidencia:** inventario de APIs/build y estado inicial de `verify`; no se
  sobrescriben cambios locales ajenos.

  **Resultado:** revisados `pom.xml`, Java 21, entry point, dependencia Bouncy
  Castle y sus recursos, workflow Linux, parser/ayuda y política de consola. El
  build base `./mvnw.cmd verify` pasó antes del empaquetado (120 pruebas, 0
  fallos, 1 omitida).

- [x] **T02 — Obtener aprobación del plugin y su versión exacta** (20 min)\
  **RF:** R1, R2 · **AC:** AC-1–AC-3, AC-6\
  **Hecho cuando:** el usuario aprueba expresamente añadir el plugin de
  empaquetado con versión fija; se registra versión y configuración prevista.
  No se agrega dependencia runtime ni se continúa si falta aprobación.\
  **Evidencia:** decisión/versionado aprobados antes del cambio en `pom.xml`.

  **Resultado:** el usuario aprobó `org.apache.maven.plugins:maven-shade-plugin`
  `3.6.2` antes de modificar el POM.

## Artefacto Maven

- [x] **T03 — Fijar versión de distribución y nombre del JAR** (20 min)\
  **RF:** R1, R2 · **AC:** AC-1, AC-3\
  **Hecho cuando:** Maven identifica el proyecto como `1.0.0` y el build produce
  el nombre estable `target/password-vault.jar`, sin pasos manuales posteriores.\
  **Evidencia:** propiedades efectivas/artefacto comprobados y prueba de build.

  **Resultado:** `pom.xml` declara `1.0.0` y `<finalName>password-vault</finalName>`;
  `./mvnw.cmd verify` generó el artefacto esperado.

- [x] **T04 — Configurar JAR ejecutable y dependencias runtime** (25 min)\
  **RF:** R1 · **AC:** AC-1, AC-2, AC-6\
  **Hecho cuando:** el plugin aprobado y fijado produce un JAR con
  `Main-Class: cl.germain.vault.cli.Main`, dependencias runtime necesarias y sin
  dependencias de test; no minimiza, reubica ni transforma bytecode.\
  **Evidencia:** `jar`/manifest y contenido del artefacto inspeccionados.

  **Resultado:** manifest, clase BC, ausencia de JUnit y ejecución directa de la
  ayuda verificados en `target/password-vault.jar`.

- [x] **T05 — Preservar recursos de proveedor y avisos/licencias** (25 min)\
  **RF:** R1 · **AC:** AC-2, AC-6\
  **Hecho cuando:** metadatos de servicio requeridos por JDK/proveedores y avisos
  de licencias de las dependencias empaquetadas se conservan en el artefacto o
  avisos incluidos con él.\
  **Evidencia:** contenido/avisos inspeccionados y política de recursos
  documentada; no se ocultan conflictos mediante minimización.

  **Resultado:** se conservan `META-INF/LICENSE.md` y el descriptor de servicio
  de Bouncy Castle; `ServicesResourceTransformer` combina servicios y se excluyen
  únicamente firmas `.SF`, `.DSA`, `.RSA` invalidadas al ensamblar.

- [x] **T06 — Probar independencia del JAR y su ayuda** (25 min)\
  **RF:** R1, R2 · **AC:** AC-2, AC-3\
  **Hecho cuando:** `java -jar target/password-vault.jar --help` termina con
  éxito y muestra los comandos esperados sin Maven, classpath adicional,
  directorio `target/dependency` ni red una vez construido.\
  **Evidencia:** ejecución local registrada, estado de salida y texto de ayuda;
  las pruebas no invocan comandos operativos ni introducen secretos.

  **Resultado:** `java -jar target/password-vault.jar --help` devolvió código 0
  y mostró la ayuda esperada en Windows.

## CI y checksums

- [x] **T07 — Añadir prueba del JAR empaquetado a CI Linux** (25 min)\
  **RF:** R2 · **AC:** AC-3\
  **Hecho cuando:** workflow con Java 21 ejecuta `verify` y, sobre el artefacto
  producido, invoca `java -jar ... --help`, comprueba éxito y presencia de ayuda.\
  **Evidencia:** workflow revisado y run Linux verde; no se marca completo antes
  de disponer del run real.

  **Evidencia:** run Linux 43773642522, commit `f8b25dd`, terminó en `success`.
  `Verify project` y `Verify packaged CLI` pasaron; el segundo comprobó manifest,
  clases/runtime, licencia, provider service, ausencia de JUnit y `java -jar
  ... --help`. El manifest se normaliza de CRLF antes de comparar.

- [x] **T08 — Validar generación y verificación SHA-256** (25 min)\
  **RF:** R3 · **AC:** AC-4, AC-7\
  **Hecho cuando:** comandos Windows y POSIX calculan y verifican el checksum del
  JAR; el nombre publicado queda definido como
  `password-vault-1.0.0.jar.sha256`.\
  **Evidencia:** hash recalculado sobre el mismo artefacto y verificación positiva
  en la plataforma ejecutada; registrar cualquier otra plataforma como pendiente.

  **Evidencia:** cálculo/verificación PowerShell sobre el artefacto Windows y
  `sha256sum` en el run Linux 43773642522; ambas comprobaciones pasaron.

## Guía de usuario

- [x] **T09 — Documentar requisitos, descarga e instalación en README** (25 min)\
  **RF:** R3, R4 · **AC:** AC-4, AC-5\
  **Hecho cuando:** `README.md` en español explica propósito, Java 21 o
  compatible, descarga desde Releases y ejecución con `java -jar`; aclara que no
  incluye JVM y ofrece alternativa desde código fuente con Maven Wrapper.\
  **Evidencia:** revisión textual contra los requisitos de la spec y comandos
  realmente disponibles.

- [x] **T10 — Documentar comandos, consola y protección de bóveda** (25 min)\
  **RF:** R3, R4 · **AC:** AC-5, AC-6\
  **Hecho cuando:** README refleja comandos y argumentos actuales, rutas
  explícitas, autobloqueo, terminal interactiva y solicitud de secretos sin eco;
  advierte no usar argumentos, variables de entorno o stdin para secretos y no
  compartir/incluir vaults.\
  **Evidencia:** comparación con parser/ayuda y revisión de que no se inventan
  comportamientos.

- [x] **T11 — Documentar dependencias, licencias y checksum** (25 min)\
  **RF:** R1, R3 · **AC:** AC-2, AC-4, AC-7\
  **Hecho cuando:** README identifica dependencias de terceros/licencias e
  incluye instrucciones funcionales para verificar SHA-256 en Windows y POSIX,
  explicando el alcance de integridad sin afirmar firma.\
  **Evidencia:** comandos probados sobre el artefacto y referencia a avisos
  incluidos.

  **Evidencia:** README identifica Bouncy Castle 1.86/licencia incluida y contiene
  verificación SHA-256 para Windows/POSIX. PowerShell y run Linux
  43773642522 validaron sus comandos.

- [x] **T12 — Documentar publicación manual del Release v1.0.0** (20 min)\
  **RF:** R3 · **AC:** AC-7\
  **Hecho cuando:** README describe crear el tag/Release `v1.0.0` y adjuntar el
  JAR renombrado y su checksum; deja explícito que no hay publicación automática,
  instalador nativo ni runtime incluido.\
  **Evidencia:** pasos revisados y coherentes con los nombres acordados; no se
  configura workflow de release.

  **Resultado:** README documenta adjuntar JAR y checksum generado manualmente;
  no se configura publicación automática.

## Pruebas, revisión y cierre

- [x] **T13 — Revisar ejemplos y ausencia de datos sensibles** (20 min)\
  **RF:** R3, R4 · **AC:** AC-5, AC-6\
  **Hecho cuando:** todos los ejemplos usan rutas/datos ficticios y el diff no
  contiene credenciales, códigos TOTP ni archivos de bóveda; no se modifica el
  fallback de consola.\
  **Evidencia:** búsqueda y revisión manual de README, workflow y cambios.

  **Resultado:** ejemplos usan rutas/IDs ficticios; revisión de README, workflow
  y cambios sin bóvedas, credenciales ni códigos TOTP. La CLI y el gate de
  `System.console()` no se modificaron.

- [x] **T14 — Verificar compatibilidad Windows y lifecycle Maven** (25 min)\
  **RF:** R2 · **AC:** AC-1–AC-3\
  **Hecho cuando:** `./mvnw.cmd verify` genera y ejecuta el JAR con Java 21 en
  Windows, además de comprobar formato y tests.\
  **Evidencia:** salida real, conteo y plataforma registrados; no se afirma Linux
  desde esta tarea.

  **Resultado:** Windows/Java 21, `./mvnw.cmd verify`: BUILD SUCCESS; 120 pruebas,
  0 fallos, 0 errores, 1 omitida por symlink no disponible. El artefacto se generó
  y ejecutó correctamente. Dos verifies consecutivos no re-sombrean el JAR; solo
  se reporta la colisión esperada del manifest.

- [x] **T15 — Revisar alcance, dependencias y documentación de proyecto** (20 min)\
  **RF:** R1–R4 · **AC:** AC-2, AC-5–AC-7\
  **Hecho cuando:** diff confirma sin nuevas dependencias runtime, sin cambios al
  formato de bóveda/`docs/FORMAT.md` ni `docs/THREAT_MODEL.md`, sin comandos
  nuevos y sin publicación automatizada; `docs/STATUS.md` registra solo
  evidencia realmente obtenida. `AGENTS.md` puede enlazar el README si hace falta.\
  **Evidencia:** revisión de diff, POM, imports y coherencia de estado.

  **Resultado:** solo se añadió el plugin de build aprobado; no hay nueva
  dependencia runtime, cambio de formato, Threat Model, comando CLI ni publicación
  automática. `docs/STATUS.md` distingue resultados locales de CI pendiente.

- [x] **T16 — Ejecutar verificación integral y registrar cierre** (25 min)\
  **RF:** R1–R4 · **AC:** AC-1–AC-7\
  **Hecho cuando:** `./mvnw.cmd verify` y CI Linux pasan, el JAR se ejecuta en
  ambas plataformas, README está completo y todos los AC tienen evidencia; se
  registran resultados/limitaciones en `docs/STATUS.md`.\
  **Evidencia:** BUILD SUCCESS, conteo de pruebas y enlace/ID del run Linux. No
  cerrar la spec si falta alguna plataforma o AC exigido.

  **Resultado:** `./mvnw.cmd verify` pasó en Windows (120 pruebas, 0 fallos,
  0 errores, 1 omitida por symlink no disponible). CI Linux terminó en `success`
  en [run 43773642522](https://github.com/Eliu2001/Password_manager/actions/runs/43773642522).
  Artefacto, ayuda, checksum, documentación y AC-1–AC-7 revisados; Spec 007
  cerrada el 2026-11-15. Posteriormente se publicó el Release
  [`v1.0.0`](https://github.com/Eliu2001/Password_manager/releases/tag/v1.0.0);
  se descargaron y verificaron ambos adjuntos y el arranque del JAR.

## Fuera de alcance

No implementar `jpackage`, instalador nativo, runtime incluido, firma,
actualizaciones, repositorio Maven, GUI o publicación automática. No probar
comandos operativos mediante pipes ni automatizar contraseñas. Crear un Release
real no es parte del build; las instrucciones y archivos adjuntos sí deben quedar
preparados/documentados. La implementación quedó cerrada al completar las tareas
y obtener evidencia Windows/Linux. El Release manual se publicó posteriormente;
la automatización de publicación sigue fuera de alcance.
