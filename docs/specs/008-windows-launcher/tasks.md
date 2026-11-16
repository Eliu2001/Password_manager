# Tareas — Spec 008: Lanzador para Windows

Tareas ordenadas por dependencia y pensadas para bloques de **20–30 minutos**.
Si una tarea supera ese tamaño, dividirla antes de continuar. Completar cada
tarea con evidencia breve (archivos/pruebas ejecutadas y resultado); no marcarla
completa sin verificar sus criterios. No usar secretos reales ni bóvedas en el
repositorio. La spec y la implementación fueron aprobadas por el usuario el
2026-11-15.

## Preparación y contratos

- [x] **T01 — Confirmar autorización e inspeccionar contratos** (20 min)\
  **RF:** RF-1–RF-6 · **AC:** AC-1–AC-8\
  **Hecho cuando:** se confirma autorización de implementación y se revisan
  `Main`, el parser/ayuda para `session`, timeout default, códigos de salida,
  consola, README, `.gitignore` y estado Git. Se confirma que Spec 007 está
  cerrada y no hay `vault.cmd` preexistente. No se pisan cambios locales ajenos.\
  **Evidencia:** inventario de contratos, rama/estado inicial y confirmación de
  que no se requiere cambio Java, dependencia, formato o Release.

  **Resultado:** autorización explícita recibida. En `main` (`eb68880`), árbol
  limpio salvo los documentos recién creados de Spec 008; `vault.cmd` no existía.
  Revisados `Main`, `CliApplication`, sesión, timeout default 5, propagación de
  error mediante `System.exit`, README y Java 21. No se requiere cambio Java,
  dependencia, formato ni Release.

## Lanzador y pruebas Windows

- [x] **T02 — Crear lanzador genérico y configuración explícita** (25 min)\
  **RF:** RF-1, RF-2, RF-3 · **AC:** AC-1–AC-4\
  **Hecho cuando:** `vault.cmd` resuelve el JAR junto al script, contiene solo
  una variable `VAULT_PATH` de ejemplo editable, rechaza vacío/marcador antes de
  invocar Java y llama `session --vault` preservando la ruta con espacios. No
  incluye rutas personales ni redirige/canaliza la consola.\
  **Evidencia:** revisión del script y primer ensayo con Java de prueba y ruta
  sintética absoluta con espacios.

  **Resultado:** `vault.cmd` usa `%~dp0` para localizar el JAR, trae marcador de
  configuración y entrega la ruta con espacios como argumento único. Invocación
  con Java de prueba desde otro directorio capturó los cinco argumentos
  esperados; se observó `session` sin timeout explícito.

- [x] **T03 — Manejar precondiciones y ventana de doble clic** (25 min)\
  **RF:** RF-4, RF-5 · **AC:** AC-5, AC-6\
  **Hecho cuando:** ausencia del JAR o Java da mensajes de error en español sin
  invocar CLI; la pausa conserva visible la salida y el script devuelve el código
  de Java sin sustituirlo por el código de `pause`.\
  **Evidencia:** pruebas separadas de JAR ausente, Java ausente y Java de prueba
  con códigos 0 y no-cero; capturar los estados antes/después de la pausa.

  **Resultado:** el estado no-cero 7 de Java de prueba se conservó tras `pause`;
  ausencia de JAR/Java y marcador sin configurar mostraron error y devolvieron 1.

- [x] **T04 — Validar ubicación relativa y quoting de argumentos** (25 min)\
  **RF:** RF-1, RF-2, RF-3 · **AC:** AC-2, AC-3, AC-4\
  **Hecho cuando:** la matriz invoca el script desde un directorio de trabajo
  distinto y el Java de prueba confirma JAR desde la carpeta del script, ruta de
  bóveda íntegra como un solo argumento, argumentos exactos de `session` y
  ausencia de timeout opcional. Una configuración no válida no invoca Java.\
  **Evidencia:** transcripción de argumentos inocuos con una ruta que incluya
  espacios y resultado de cada caso; temporales fuera del repositorio.

  **Resultado:** prueba Windows con launcher en carpeta con espacios, invocado
  desde `%TEMP%`; JAR relativo al script y vault `vault file.pvlt` llegaron como
  argumentos independientes íntegros. El Java de prueba no fue invocado al usar
  el marcador.

- [x] **T05 — Revisar passthrough de consola y sesión** (25 min)\
  **RF:** RF-3, RF-5 · **AC:** AC-4, AC-6\
  **Hecho cuando:** la revisión y la prueba con Java de prueba confirman que
  `session` recibe la consola sin redirección, sin timeout explícito y conserva
  salida/error; se registra como complementaria cualquier prueba manual real de
  TTY. No introducir secretos por línea de comandos, variables de entorno,
  redirección o pipes.\
  **Evidencia:** argumentos observados, revisión de streams/código y plataforma;
  no anotar contraseñas ni contenido de bóveda. La prueba manual de TTY no bloquea
  completar la tarea si no está disponible.

  **Resultado:** el script no redirige la invocación a Java; mock confirmó
  argumentos/salida y el estado de salida. No se ejecutó una sesión manual con
  bóveda real; queda como comprobación complementaria, no bloqueante.

## Documentación y cierre

- [x] **T06 — Documentar instalación y uso del lanzador** (25 min)\
  **RF:** RF-6 · **AC:** AC-7\
  **Hecho cuando:** README explica que el `.cmd` se obtiene del repositorio y se
  coloca junto al JAR, Java 21+, cómo editar `VAULT_PATH` absoluta, cómo crear
  primero la bóveda mediante CLI, iniciar/cerrar sesión y resolver los errores
  previstos. No recomienda guardar secretos en el archivo.\
  **Evidencia:** revisar cada instrucción contra `vault.cmd`, la ayuda/CLI actual
  y la spec; ejemplos sin datos sensibles.

  **Resultado:** README documenta ubicación junto al JAR, Java 21+, edición de
  ruta, creación inicial separada, sesión/cierre y manejo de errores/secretos.

- [x] **T07 — Revisar seguridad y alcance de distribución** (20 min)\
  **RF:** RF-1–RF-6 · **AC:** AC-1, AC-4, AC-7\
  **Hecho cuando:** diff confirma ausencia de rutas personales, secretos, bóvedas,
  dependencias nuevas, cambios al formato/Threat Model, adjuntos o automatización
  del Release. Streams de consola y manejo de secretos permanecen bajo control de
  la CLI.\
  **Evidencia:** revisión de `git diff` y búsqueda de rutas/secretos sensibles;
  `docs/THREAT_MODEL.md` y `docs/FORMAT.md` sin cambios.

  **Resultado:** script genérico sin secretos ni rutas personales; no se tocaron
  Java, dependencias, formato, Threat Model o archivos del Release. Pruebas
  temporales quedaron fuera del repositorio.

- [x] **T08 — Ejecutar verificación integral y registrar resultados** (25 min)\
  **RF:** RF-1–RF-6 · **AC:** AC-1–AC-8\
  **Hecho cuando:** `./mvnw.cmd verify` pasa, `git diff --check` pasa, README y
  matriz Windows quedan revisados, y `docs/STATUS.md` registra únicamente pruebas
  ejecutadas (incluido si no fue posible hacer la sesión TTY manual). No cerrar la
  spec con fallos o aceptación sin evidencia.\
  **Evidencia:** salida BUILD SUCCESS y resumen de pruebas, resultados de casos
  Windows, diff limpio y estado de los AC.

  **Resultado:** `./mvnw.cmd verify` pasó el 2026-11-15: 120 pruebas, 0 fallos,
  0 errores y 1 omitida por symlink no disponible en Windows. Matriz Windows con
  Java de prueba validó éxito 0, error 7 preservado, ruta con espacios, JAR junto
  al script desde otro directorio, marcador/ruta vacía y Java/JAR ausentes.
  `git diff --check` pasó. No se hizo prueba manual TTY con bóveda real.

## Criterio de cierre

T01–T08 están completadas con la evidencia anotada. Spec 008 cerrada con AC-1–AC-8
cubiertos, `verify` exitoso y escenarios del `.cmd` validados en Windows. No se
hizo la comprobación manual TTY complementaria. La publicación del lanzador en
otro Release permanece fuera de alcance.
