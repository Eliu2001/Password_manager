# Tareas — Spec 003: cli-mvp

Tareas ordenadas por dependencia y pensadas para bloques de **20–30 minutos**.
Si una tarea supera ese tamaño, dividirla antes de continuar. Completar cada
tarea con evidencia breve (archivos/pruebas ejecutadas y resultado); no marcarla
completa sin verificar su criterio. No usar secretos reales ni fixtures sensibles.

## Preparación y parser

- [x] **T01 — Revisar alcance, plan y APIs existentes** (20 min)\
  **RF:** R1–R10 · **AC:** AC-1–AC-13\
  **Hecho cuando:** se confirma que Spec 003 está aprobada, Spec 002 cerrada, se
  inspeccionan las firmas de `VaultStorage`, `VaultSession`, `Vault` y `Entry`, y
  se documentan en la evidencia las reglas de ownership de `char[]`; no se
  agregan dependencias ni se alteran cambios locales ajenos a esta tarea.
  **Evidencia:** Spec 003 aprobada y plan existente revisados; Spec 002 cerrada.
  `VaultStorage.create/open` reciben `char[]`; `Entry.password()` devuelve copia
  caller-owned; `VaultSession.close()` cierra Vault/clave/lock. No se añadieron
  dependencias. Cambios locales anteriores en `docs/STATUS.md`, pruebas de
  storage y `.github/` se conservaron.

- [x] **T02 — Definir tipos del parser y resultado de ejecución** (25 min)\
  **RF:** R8, R9 · **AC:** AC-1, AC-2, AC-11\
  **Hecho cuando:** `cli` tiene representaciones explícitas para los comandos,
  argumentos válidos y errores/estados de salida; los tipos no abren archivos ni
  contienen datos secretos.
  **Evidencia:** añadidos `CliCommand`, `CliArgumentException` y `CliExitStatus`;
  representan comando/argumentos validados, error seguro y códigos success,
  cancelled y error. Son tipos sin I/O ni secretos.

- [x] **T03 — Implementar parsing de comandos y opciones** (25 min)\
  **RF:** R8 · **AC:** AC-1, AC-2\
  **Hecho cuando:** el parser reconoce `create`, `add`, `list`, `show`, `delete`
  y `--help`; valida `--vault` y `--id` según corresponda y rechaza argumentos
  desconocidos, duplicados, incompletos y no admitidos antes de invocar
  colaboradores de filesystem.
  **Evidencia:** `CliArgumentParser` valida sintaxis completa, tipos de opciones,
  duplicados, campos ausentes e ID requerido/inesperado; solo convierte la ruta
  léxicamente y no realiza I/O.

- [x] **T04 — Implementar validación/formato de IDs** (20 min)\
  **RF:** R8 · **AC:** AC-1, AC-8, AC-9\
  **Hecho cuando:** solo se aceptan 32 caracteres hexadecimales minúsculos para
  `show`/`delete`, se convierten exactamente a los 16 bytes del modelo y el
  formato de salida produce el mismo patrón; pruebas cubren longitud, mayúsculas,
  caracteres no hexadecimales y round-trip.
  **Evidencia:** `parseId` acepta únicamente ASCII `[0-9a-f]{32}` y retorna 16
  bytes; `formatId` hace round-trip en minúscula y valida tamaño. Pruebas cubren
  mayúsculas, longitud, carácter inválido y aislamiento de copias.

- [x] **T05 — Probar parser y ayuda sin acceso a archivos** (25 min)\
  **RF:** R8, R9 · **AC:** AC-1, AC-2, AC-11, AC-12\
  **Hecho cuando:** pruebas de tabla cubren comandos/opciones válidos e inválidos,
  errores en español sin trazas y `--help` sin interacción con Storage ni acceso
  a rutas.
  **Evidencia:** `CliArgumentParserTest` cubre los cinco comandos, help, opciones
  válidas e inválidas, IDs, errores españoles sin causa ni valores suministrados.
  El parser no contiene dependencias de Storage/filesystem. `./mvnw.cmd verify`:
  BUILD SUCCESS, 67 pruebas, 0 fallos, 1 omitida por symlink no disponible en
  esta plataforma.

## Consola y coordinación común

- [x] **T06 — Definir abstracción de consola sustituible** (25 min)\
  **RF:** R1, R9 · **AC:** AC-3, AC-11, AC-12\
  **Hecho cuando:** la interfaz separa disponibilidad, prompts, lectura visible y
  lectura sin eco, respuestas ausentes/interrumpidas, salida normal, errores y
  canal privado para revelar contraseña; pruebas pueden sustituir cada canal.
  **Evidencia:** creada `CliConsole` con canales separados y operaciones para
  lectura visible/sin eco; `CliConsoleContractTest` ejercita canales, EOF e
  interrupción con un doble sustituible.

- [x] **T07 — Implementar adaptador de `System.console()`** (25 min)\
  **RF:** R1, R6, R9 · **AC:** AC-3, AC-8, AC-11\
  **Hecho cuando:** el adaptador usa `System.console()` y sus APIs para entrada
  sin eco y escritura privada; si no hay consola, no usa `System.in` ni
  `System.out` como alternativa para secretos y comunica ausencia de terminal
  mediante el resultado acordado.
  **Evidencia:** `SystemCliConsole` toma `System.console()`, usa `readPassword`
  y `Console.writer()` para secretos; cuando la consola falta, lectura y salida
  privada fallan sin fallback. `SystemCliConsoleTest` verifica ese comportamiento.

- [x] **T08 — Probar consola, canales y lectura interrumpida** (25 min)\
  **RF:** R1, R6, R9 · **AC:** AC-3, AC-8, AC-11, AC-12\
  **Hecho cuando:** dobles verifican lecturas con/sin eco, respuesta ausente,
  interrupción y que el canal privado no se mezcla con stdout/error; ninguna
  lectura fallida incluye su contenido en mensajes.
  **Evidencia:** `CliConsoleContractTest` cubre canales, `null` como fin/cancelación
  e interrupción en lecturas visibles y secretas; `SystemCliConsoleTest` confirma
  rechazo sin consola y sin fallback de secretos.

- [x] **T09 — Implementar `Main` y despacho seguro** (25 min)\
  **RF:** R1, R8, R9, R10 · **AC:** AC-1–AC-3, AC-11\
  **Hecho cuando:** `Main` delega en parser/ejecutor, resuelve ayuda y errores de
  uso sin consola ni Storage, y para comandos operativos valida argumentos y
  consola antes de tocar archivos; códigos de salida respetan la spec.
  **Evidencia:** añadidos `Main` y `CliApplication`; help funciona sin consola,
  argumentos inválidos se rechazan antes de consultar disponibilidad y comandos
  operativos requieren consola antes del despacho. `CliApplicationTest` cubre
  esos flujos y verifica estado/mensajes en español. `./mvnw.cmd verify`: BUILD
  SUCCESS, 74 pruebas, 0 fallos, 1 omitida por symlink no disponible en esta
  plataforma.

- [x] **T10 — Implementar manejo común de errores y ciclo de vida** (25 min)\
  **RF:** R1, R3, R9 · **AC:** AC-3, AC-5, AC-10–AC-12\
  **Hecho cuando:** recursos y sesiones se cierran en éxito, cancelación y
  excepción; errores de autenticación se presentan genéricamente y no se
  imprimen trazas, causas sensibles, secretos ni resultados parciales.
  **Evidencia:** `CliApplication` mapea errores de I/O/autenticación a un mensaje
  genérico, no imprime causas y restaura el flag de interrupción. Los comandos
  usan try-with-resources; pruebas de cancelación, fallo de autenticación y
  operaciones completas verifican liberación de locks y limpieza de arreglos.

## Comandos

- [x] **T11 — Implementar creación interactiva** (25 min)\
  **RF:** R1, R2 · **AC:** AC-3, AC-4, AC-11, AC-12\
  **Hecho cuando:** `create` solicita dos contraseñas maestras sin eco, trata
  discrepancia/cancelación antes de crear, llama a `VaultStorage.create` solo si
  coinciden, no sobrescribe y borra ambos arreglos bajo propiedad de CLI en
  éxito/error.
  **Evidencia:** `VaultCliCommands.create` implementado; usa `readPassword`,
  compara `char[]`, no llama Storage ante discrepancia/cancelación y limpia ambas
  entradas en `finally`; éxito solo se informa tras cerrar sesión.

- [x] **T12 — Probar creación, cancelación y limpieza** (25 min)\
  **RF:** R1, R2 · **AC:** AC-3, AC-4, AC-11, AC-12\
  **Hecho cuando:** pruebas verifican igualdad, discrepancia, EOF/interrupción,
  destino existente, ausencia de consola, archivo creado solo en éxito, estados
  de salida y borrado de arreglos en todos los caminos.
  **Evidencia:** `CliApplicationTest` cubre creación confirmada, discrepancia,
  EOF, interrupción, destino existente, ausencia de consola, resultado del
  archivo y limpieza de arreglos. `./mvnw.cmd verify`: BUILD SUCCESS.

- [x] **T13 — Implementar apertura autenticada común** (25 min)\
  **RF:** R1, R3, R9 · **AC:** AC-3, AC-5, AC-10–AC-12\
  **Hecho cuando:** los comandos existentes piden una contraseña maestra sin eco,
  la pasan como `char[]` a `VaultStorage.open`, la CLI borra su arreglo tras la
  llamada incluso ante excepción, y la sesión queda cerrada en todos los
  caminos.
  **Evidencia:** `add/list/show/delete` solicitan maestro con `readPassword`, lo
  pasan como `char[]` a `VaultStorage.open` y lo limpian en `finally`; sesiones
  usan try-with-resources. Pruebas verifican autenticación fallida genérica,
  limpieza y liberación del lock.

- [x] **T14 — Implementar `list`** (25 min)\
  **RF:** R3, R5 · **AC:** AC-5, AC-7, AC-10, AC-11\
  **Hecho cuando:** se presenta el orden del Vault y solo ID, servicio y usuario;
  bóveda vacía da mensaje claro; no se leen contraseñas/notas ni se llama a
  `save()`.
  **Evidencia:** `VaultCliCommands.list` captura el resultado y solo lo emite
  tras cierre limpio; presenta el orden y los tres campos permitidos, o mensaje
  de bóveda vacía. No consulta contraseña/notas ni invoca `save()`.

- [x] **T15 — Probar listado y ausencia de persistencia** (25 min)\
  **RF:** R3, R5 · **AC:** AC-7, AC-10–AC-12\
  **Hecho cuando:** pruebas cubren bóveda vacía y no vacía, orden/campos,
  exclusión de contraseña/notas en todos los canales, ningún `save()` y cierre de
  sesión en éxito y fallo.
  **Evidencia:** pruebas de integración cubren bóveda vacía y con entrada, orden,
  campos y ausencia de contraseña/notas; apertura/cierre real de Storage valida
  liberación de sesión.

- [x] **T16 — Implementar alta interactiva** (25 min)\
  **RF:** R3, R4 · **AC:** AC-5, AC-6, AC-10–AC-12\
  **Hecho cuando:** `add` pide servicio, usuario, contraseña y notas en orden;
  valida los campos obligatorios sin recortar/normalizar; agrega solo tras
  validar, llama a `save()` y muestra ID únicamente después del guardado exitoso.
  Los arreglos temporales de contraseña propiedad de CLI se borran.
  **Evidencia:** `VaultCliCommands.add` implementado con prompts en orden,
  validación sin trim/NFC, alta y save explícitos; limpia maestro/contraseña en
  `finally` y emite el ID solo tras save y cierre correcto.

- [x] **T17 — Probar alta, validación y round-trip** (25 min)\
  **RF:** R3, R4 · **AC:** AC-6, AC-10–AC-12\
  **Hecho cuando:** pruebas cubren campos vacíos, notas vacías, espacios y
  Unicode exactos, ausencia de alta/save parcial, contraseña sin eco,
  persistencia real reabierta mediante Storage, formato de ID y limpieza ante
  éxito/fallo.
  **Evidencia:** pruebas cubren requeridos vacíos sin cambio del archivo,
  notas vacías en integración, preservación de espacios/Unicode, save/reopen,
  ID lowercase y limpieza de arreglos bajo propiedad de CLI.

- [x] **T18 — Implementar consulta y revelación segura (`show`)** (25 min)\
  **RF:** R3, R6, R8 · **AC:** AC-5, AC-8, AC-10–AC-12\
  **Hecho cuando:** un ID inválido se rechaza antes de abrir; para un ID válido se
  muestran solo los campos acordados; la contraseña se obtiene como copia
  `char[]`, se revela únicamente tras `sí` o `si` exacto por canal privado y se borra
  tanto en confirmación como cancelación; nunca se guarda.
  **Evidencia:** `VaultCliCommands.show` obtiene contraseña solo tras `sí` o `si` exacto,
  la copia a `char[]`, la emite por `printSecret` y la borra en `finally`; datos
  de entrada solo se imprimen tras cierre limpio y no se llama a `save()`.

- [x] **T19 — Probar consulta, confirmación y canal de revelación** (25 min)\
  **RF:** R6, R8, R9 · **AC:** AC-8, AC-10–AC-12\
  **Hecho cuando:** pruebas cubren ID inválido/inexistente/válido, respuestas
  exactas y no exactas incluyendo vacío, contraseña ausente de stdout/error,
  copia borrada y ausencia de `save()`.
  **Evidencia:** pruebas cubren ID inexistente, reveal afirmativo y respuesta no
  exacta, salida por canal privado, contraseña ausente de salida/error normal y
  borrado de copia. ID inválido se rechaza en las pruebas de parser (T04/T05).

- [x] **T20 — Implementar borrado con confirmación (`delete`)** (25 min)\
  **RF:** R3, R7, R8 · **AC:** AC-5, AC-9–AC-12\
  **Hecho cuando:** se muestra ID/servicio/usuario sin contraseña; solo `sí` o `si`
  elimina y guarda; las demás respuestas cancelan normalmente sin guardar; ID
  desconocido no modifica la bóveda.
  **Evidencia:** `VaultCliCommands.delete` muestra solo ID/servicio/usuario,
  requiere igualdad exacta con `sí`, borra y guarda únicamente al confirmar y
  emite éxito tras cierre limpio.

- [x] **T21 — Probar borrado confirmado y cancelaciones** (25 min)\
  **RF:** R7, R8 · **AC:** AC-9–AC-12\
  **Hecho cuando:** pruebas cubren positivo, negativo, vacío, EOF/interrupción e
  ID inexistente; comparan archivo antes/después para cancelaciones y verifican
  persistencia/reapertura del borrado confirmado, cierre y mensajes no sensibles.
  **Evidencia:** integración compara bytes antes/después de cancelar con `si`,
  respuesta vacía, EOF e interrupción; cubre borrado confirmado y reapertura sin
  la entrada; ID desconocido conserva archivo. Las sesiones se reabren después
  de cancelar para confirmar lock liberado. `./mvnw.cmd verify`: BUILD SUCCESS,
  76 pruebas, 0 fallos, 1 omitida por symlink no disponible en esta plataforma.

## Integración, seguridad y cierre

- [x] **T22 — Probar ausencia de consola en todos los comandos** (25 min)\
  **RF:** R1 · **AC:** AC-3, AC-10, AC-12\
  **Hecho cuando:** cada comando operativo falla con estado no cero antes de
  leer secretos/llamar a Storage; en create no aparece archivo y en los demás el
  contenido/estado de la bóveda permanece intacto; se verifica ausencia de
  fallback a stdin.
  **Evidencia:** `CliApplicationTest` ejecuta create/add/list/show/delete sin
  consola, verifica cero lecturas, archivo existente intacto y create sin
  archivo en destino nuevo.

- [x] **T23 — Probar matriz de errores y ciclo de vida** (25 min)\
  **RF:** R1, R3, R9 · **AC:** AC-5, AC-10–AC-12\
  **Hecho cuando:** errores de autenticación, lectura, apertura y guardado no
  exponen causas/secretos ni datos parciales, no reportan éxito, cierran sesión y
  limpian los arreglos `char[]` bajo propiedad de CLI.
  **Evidencia:** pruebas cubren contraseña maestra incorrecta, fallo de lectura
  con causa sintética, fallo de guardado por cambio externo, cancelación e
  interrupción; comprueban mensaje genérico, preservación del archivo externo,
  ausencia de éxito, borrado de arreglos y liberación de locks.

- [x] **T24 — Revisar confidencialidad, arquitectura y dependencias** (20 min)\
  **RF:** R9, R10 · **AC:** AC-11, AC-13\
  **Hecho cuando:** revisión confirma mensajes de usuario en español, sin
  secretos/trazas/causas sensibles; no se añadieron dependencias, no se alteró
  formato v1 ni `docs/THREAT_MODEL.md`, y `crypto` sigue independiente.
  **Evidencia:** revisión del código CLI y dependencias; no se cambió `pom.xml`,
  `docs/FORMAT.md` ni `docs/THREAT_MODEL.md`. La implementación CLI depende de
  `storage`/`model`; `crypto` no importa CLI.

- [x] **T25 — Ejecutar verificación completa y registrar resultados** (25 min)\
  **RF:** todos · **AC:** AC-1–AC-13\
  **Hecho cuando:** `./mvnw.cmd verify` termina en verde; se registra resultado,
  número de pruebas y limitaciones observadas en `docs/STATUS.md`, sin declarar
  ejecutada cobertura de una plataforma no probada.
  **Evidencia:** `./mvnw.cmd verify`: BUILD SUCCESS, 77 pruebas, 0 fallos y 1
  omitida (symlink no disponible en Windows). El resultado y esta limitación se
  registraron en `docs/STATUS.md`.

- [x] **T26 — Confirmar cierre de Spec 003** (20 min)\
  **RF:** todos · **AC:** AC-1–AC-13\
  **Hecho cuando:** tareas y evidencia cubren todos los criterios de aceptación,
  los cinco comandos y ayuda están implementados, verificación está en verde y
  `docs/STATUS.md` registra estado y pendientes. No modificar
  `docs/THREAT_MODEL.md` sin solicitud expresa.
  **Evidencia:** AC-1–AC-13 verificados, comandos/help implementados y tareas
  T01–T26 completadas. Cierre registrado el 2026-11-14; persiste una omisión de
  prueba de symlink específica de Windows ya documentada para CI Linux.

## Fuera de alcance

No planificar ubicación predeterminada, operación no interactiva o lectura de
secretos desde argumentos/entorno/stdin, edición/búsqueda, generador, autobloqueo,
TOTP, importación/exportación, sincronización, empaquetado ni cambios al formato.
