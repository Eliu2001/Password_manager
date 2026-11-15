# Tareas — Spec 006: Autobloqueo de sesión

Tareas ordenadas por dependencia y pensadas para bloques de **20–30 minutos**.
Si una tarea supera ese tamaño, dividirla antes de continuar. Completar cada
tarea con evidencia breve (archivos/pruebas ejecutadas y resultado); no marcarla
completa sin verificar sus criterios. No usar secretos reales ni fixtures
sensibles. La implementación de esta spec fue autorizada por el usuario el
2026-11-15.

## Preparación y contratos

- [x] **T01 — Revisar aprobación, plan y contratos actuales** (20 min)\
  **RF:** R1–R5 · **AC:** AC-1–AC-10\
  **Hecho cuando:** se confirma Spec 006 aprobada y Spec 005 cerrada; se revisan
  `CliApplication`, parser/modelo de comando, consola, handlers, `VaultStorage`,
  `VaultSession.close()` y `Vault.close()`; se documenta ownership de sesión y
  entradas `char[]`. No se altera trabajo local ajeno.\
  **Evidencia:** enlaces/rutas de APIs y resultado de inspección de estado base.

- [x] **T02 — Añadir regresiones del flujo CLI one-shot** (25 min)\
  **RF:** R4 · **AC:** AC-7, AC-10\
  **Hecho cuando:** tests protegen parseo/dispatch, apertura/cierre y mensajes de
  comandos existentes antes de cambiar el ownership para sesiones compartidas.\
  **Evidencia:** tests focalizados pasan sin cambios de comportamiento.

## Sintaxis y lectura temporizada

- [x] **T03 — Modelar el comando de entrada a sesión** (25 min)\
  **RF:** R1 · **AC:** AC-1\
  **Hecho cuando:** representación tipada distingue `session` del resto de
  comandos y conserva ruta/timeout validados, sin secretos ni I/O.\
  **Evidencia:** pruebas del modelo para timeout 5 por defecto y rango permitido.

- [x] **T04 — Parsear argumentos externos de sesión** (25 min)\
  **RF:** R1 · **AC:** AC-1\
  **Hecho cuando:** parser acepta `session --vault <ruta>` con timeout opcional
  y rechaza opciones ausentes, duplicadas, desconocidas, no enteras y fuera de
  1–60 antes de cualquier acceso a archivos.\
  **Evidencia:** pruebas de tabla y doble de Storage que verifica cero llamadas en
  los errores.

- [x] **T05 — Definir parseo de líneas internas de sesión** (25 min)\
  **RF:** R4, R5 · **AC:** AC-7, AC-9\
  **Hecho cuando:** líneas sin `--vault` se convierten en operaciones permitidas,
  `help` o `exit`; `create`, argumentos de ruta y comandos no permitidos se
  rechazan sin ejecutar handlers.\
  **Evidencia:** tabla de comandos válidos/inválidos y confirmación de que la
  línea no contiene una vía para inyectar secretos en la operación.

- [x] **T06 — Introducir fuente monotónica inyectable** (20 min)\
  **RF:** R2 · **AC:** AC-3, AC-6\
  **Hecho cuando:** componente `cli` obtiene ticks de `System.nanoTime()` en
  producción y acepta fake en pruebas; conversiones del timeout están acotadas y
  no dependen de reloj de pared.\
  **Evidencia:** tests de conversiones/límites sin esperas reales.

- [x] **T07 — Definir contrato de lectura con deadline/cancelación** (25 min)\
  **RF:** R2 · **AC:** AC-4, AC-6\
  **Hecho cuando:** consola distingue línea, EOF, fallo y timeout; cancelar una
  solicitud marca su resultado como descartable, sin solapar lecturas de
  `System.console()`.\
  **Evidencia:** pruebas del contrato con fake que controla el orden de línea y
  deadline. Si las APIs no permiten cumplir la spec sin lecturas concurrentes,
  detenerse y pedir ajuste antes de implementar.

- [x] **T08 — Implementar lector único para consola real** (25 min)\
  **RF:** R2, R5 · **AC:** AC-2, AC-4, AC-9\
  **Hecho cuando:** adaptador usa un único lector dedicado, no lee secretos desde
  stdin ni permite que una línea vieja pase a ser un comando tras el timeout.\
  **Evidencia:** pruebas de exclusión de lecturas concurrentes y línea tardía;
  lectura de contraseña activa sigue usando `readPassword` sin eco.

## Reutilización segura de comandos

- [x] **T09 — Separar apertura de sesión del cuerpo de comandos** (25 min)\
  **RF:** R3, R4 · **AC:** AC-7, AC-8\
  **Hecho cuando:** `VaultCliCommands` delega los comandos one-shot desde wrappers
  que autentican/abren/cierran hacia operaciones que reciben sesión activa y no
  cierran recursos que no poseen.\
  **Evidencia:** pruebas unitarias de ownership y regresiones T02 siguen pasando.

- [x] **T10 — Adaptar comandos de consulta a sesión compartida** (25 min)\
  **RF:** R4 · **AC:** AC-7\
  **Hecho cuando:** `list` y `show` usan el `VaultSession` recibido, conservan
  canal privado, confirmación de `show` y no llaman `close()` ni `save()` fuera
  de su contrato.\
  **Evidencia:** dobles verifican contenido/canal y ausencia de doble cierre/save.

- [x] **T11 — Adaptar mutaciones CRUD a sesión compartida** (25 min)\
  **RF:** R3, R4 · **AC:** AC-7, AC-8\
  **Hecho cuando:** `add`/`delete` usan la sesión compartida y conservan
  validación, confirmaciones, limpieza y persistencia solo según reglas previas.\
  **Evidencia:** pruebas de éxito, cancelación, save y error sin cierre prematuro.

- [x] **T12 — Adaptar TOTP y upgrade a sesión compartida** (25 min)\
  **RF:** R4 · **AC:** AC-7\
  **Hecho cuando:** `set-totp`, `remove-totp`, `totp` y `upgrade` operan sobre la
  sesión recibida sin pedir contraseña maestra ni cerrar la sesión exterior.\
  **Evidencia:** pruebas de v1/v2, prompts secretos, confirmaciones y persistencia.

- [x] **T13 — Probar regresión de comandos one-shot tras refactor** (25 min)\
  **RF:** R3, R4 · **AC:** AC-7, AC-8\
  **Hecho cuando:** cada comando standalone conserva apertura, cierre, estados,
  persistencia y limpieza previos tras compartir internamente el código de
  operación.\
  **Evidencia:** suite CLI existente y nuevos tests de regresión pasan.

## Coordinador y estados de sesión

- [x] **T14 — Implementar autenticación inicial del runner** (25 min)\
  **RF:** R1 · **AC:** AC-2, AC-5, AC-8\
  **Hecho cuando:** runner solicita maestra sin eco, abre `VaultSession` solo con
  consola disponible y borra el arreglo poseído en éxito, error o cancelación.\
  **Evidencia:** tests con Storage falso para orden, error genérico y limpieza.

- [x] **T15 — Implementar loop, ayuda y salida explícita** (25 min)\
  **RF:** R3, R4 · **AC:** AC-7, AC-8\
  **Hecho cuando:** runner despacha líneas permitidas sobre la sesión activa;
  `help` no accede a Storage, `exit` cierra sesión y ningún comando se ejecuta
  después de salir.\
  **Evidencia:** pruebas de orden, varios comandos por una apertura y cierre único.

- [x] **T16 — Aplicar deadline e inactividad entre comandos** (25 min)\
  **RF:** R2 · **AC:** AC-3, AC-6\
  **Hecho cuando:** deadline inicia al abrir, se reinicia al terminar comando,
  usa tiempo monotónico y el comando iniciado no se interrumpe al vencer.\
  **Evidencia:** tests justo antes/en el límite, tras comando y con reloj de pared
  alterado.

- [x] **T17 — Bloquear y descartar entrada al vencer** (25 min)\
  **RF:** R2, R3 · **AC:** AC-4, AC-8\
  **Hecho cuando:** al timeout se cierra `VaultSession` sin `save()`, se invalida
  la línea en vuelo y no se despacha su resultado; se verifica que el lock se
  libera antes de reautenticar.\
  **Evidencia:** carrera determinista línea/deadline y observación del cierre.

- [x] **T18 — Reautenticar y reanudar de forma cerrada** (25 min)\
  **RF:** R3 · **AC:** AC-5, AC-8\
  **Hecho cuando:** contraseña incorrecta/cancelación no expone datos ni deja
  sesión abierta; reintento correcto abre sesión nueva y reinicia timer; salida
  desde bloqueo cierra ordenadamente.\
  **Evidencia:** escenarios incorrecto, cancelado, retry y éxito con conteo de
  sesiones/locks.

- [x] **T19 — Manejar errores recuperables y fatales de comandos** (25 min)\
  **RF:** R3, R4, R5 · **AC:** AC-7–AC-9\
  **Hecho cuando:** errores recuperables permiten continuar solo si el estado de
  Storage sigue válido; errores fatales cierran recursos, usan mensaje genérico
  y nunca informan éxito falso.\
  **Evidencia:** matriz de excepciones y aserciones de continuación/cierre.

- [x] **T20 — Integrar sesión en aplicación, Main y ayuda** (25 min)\
  **RF:** R1, R4 · **AC:** AC-1, AC-2, AC-7\
  **Hecho cuando:** `Main`/`CliApplication` encaminan el modo sesión después de
  validar argumentos y consola; ayuda global e interna documentan sintaxis y
  comandos admitidos.\
  **Evidencia:** pruebas de integración, códigos de salida y rechazo sin consola
  antes de Storage.

## Cobertura de seguridad y cierre

- [x] **T21 — Probar ciclo completo con Storage temporal** (25 min)\
  **RF:** R3, R4 · **AC:** AC-5, AC-7, AC-8\
  **Hecho cuando:** pruebas abren sesión, ejecutan varios comandos, verifican
  cambios guardados y reabren bóveda; bloqueos/cancelaciones no persisten cambios
  implícitos.\
  **Evidencia:** round-trip con fixtures sintéticos y comparación de estado
  persistido.

- [x] **T22 — Probar EOF, interrupción y cierre en todas las rutas** (25 min)\
  **RF:** R3, R5 · **AC:** AC-8, AC-10\
  **Hecho cuando:** salida normal, EOF, interrupción, error de consola y fallo de
  Storage cierran sesión/lock de manera idempotente y limpian buffers controlables.\
  **Evidencia:** pruebas observables de `close`, locks y buffers, sin asserts de
  borrado de memoria no controlable.

- [x] **T23 — Revisar canales, secretos y referencias retenidas** (25 min)\
  **RF:** R1, R3, R5 · **AC:** AC-2, AC-5, AC-9\
  **Hecho cuando:** no hay secretos en comandos, historial, logs, errores ni
  salida redirigible; tras bloqueo el runner no mantiene referencias al modelo o
  sesión cerrados.\
  **Evidencia:** inspección de canales más tests de no exposición y referencias.

- [x] **T24 — Verificar dependencias, formato y formato de bóveda** (20 min)\
  **RF:** R5 · **AC:** AC-10\
  **Hecho cuando:** no se añadieron dependencias, no cambió `docs/FORMAT.md` ni
  los codecs y `crypto` continúa independiente.\
  **Evidencia:** revisión de diff/imports/dependencias y Spotless check.

- [x] **T25 — Documentar uso y estado de Spec 006** (25 min)\
  **RF:** R1, R2, R3 · **AC:** AC-1, AC-3, AC-10\
  **Hecho cuando:** documentación aplicable explica comando, timeout default y
  límites, comportamiento del autobloqueo y limitación observable de la consola;
  `docs/STATUS.md` registra resultados, plataforma y pendientes reales. No se
  cambia `docs/THREAT_MODEL.md` sin autorización expresa.\
  **Evidencia:** enlaces/rutas y revisión de consistencia documental.

- [x] **T26 — Ejecutar verificación integral y registrar cierre** (25 min)\
  **RF:** R1–R5 · **AC:** AC-1–AC-10\
  **Hecho cuando:** Spotless y `./mvnw.cmd verify` pasan; se registra el resultado
  exacto, omisiones/plataforma y CI Linux solo si se ejecutó. Spec no se marca
  cerrada si queda un AC sin evidencia.\
  **Evidencia:** salida BUILD SUCCESS, conteo de pruebas y limitaciones verificadas.

## Fuera de alcance

No implementar clipboard, persistencia de preferencias, cambios al formato,
autobloqueo durante un comando, modo servicio, interfaz gráfica ni nuevas
dependencias. Si la consola bloqueante impide satisfacer el descarte de entrada
pendiente de forma segura, pausar y solicitar revisión de la spec; no marcar
cumplido el requisito por simulación solamente.

## Evidencia de ejecución — 2026-11-15

- **T01:** revisadas Constitución, specs 003/005/006, plan y contratos de CLI,
  `VaultStorage`, `VaultSession` y `Vault`; verificación base `./mvnw.cmd verify`
  pasó antes de modificar código.
- **T02, T09, T13:** las pruebas existentes de `CliApplicationTest` y los flujos
  one-shot pasaron tras extraer operaciones compartidas.
- **T03–T05:** `CliSessionParserTest` cubre timeout default/rangos, entradas
  inválidas y comandos internos permitidos/rechazados; `CliSessionApplicationTest`
  comprueba validación y consola antes del handler.
- **T06–T08:** `TimedConsoleInputTest` verifica timeout, rechazo de lectura
  solapada, descarte de línea pendiente, EOF y error; `SystemCliConsole` usa un
  lector daemon único y no inicia una segunda lectura antes de descartar.
- **T10–T12, T14–T21:** `CliSessionRunnerTest` integra con Storage temporal los
  comandos add/list, show, TOTP set/query/remove, delete, upgrade, reautenticación,
  persistencia y timeout. Comprueba cierre del lock antes de descartar entrada y
  que el comando de sesión no aparece en salida redirigible.
- **T16:** el reloj monotónico falso prueba reset del intervalo y bloqueo después
  de que un comando activo cruza el deadline sin interrumpirlo.
- **T22:** pruebas de EOF/interrupción comprueban cierre del adaptador y que la
  bóveda puede reabrirse (lock liberado).
- **T23:** los datos de bóveda se capturan por canal privado; contraseña/TOTP por
  `printSecret`; las pruebas comprueban que servicio/notas no van a salida normal.
- **T24:** sin cambios de dependencias, codecs ni `docs/FORMAT.md`; Spotless se
  aplicó. `docs/THREAT_MODEL.md` no se modificó.
- **T25:** `AGENTS.md` documenta un ejemplo de invocación y `docs/STATUS.md`
  registra la sesión, bloqueo, limitación del prompt, resultado de verificación y
  CI/TTY pendientes.
- **T26:** `./mvnw.cmd verify -q` pasó el 2026-11-15; 120 pruebas, 0 fallos,
  0 errores y 1 omitida por symlink no disponible en Windows. `git diff --check`
  pasó. No se afirma un run Linux para Spec 006.
