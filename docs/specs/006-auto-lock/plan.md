# Plan técnico — Spec 006: Autobloqueo de sesión

**Estado de la spec:** aprobada e implementación autorizada el 2026-11-15.\
**Predecesora:** Spec 005 (`totp`), cerrada.\
**Alcance:** modo interactivo persistente de una sola bóveda, comandos existentes y bloqueo por inactividad entre comandos.

## 1. Contexto y decisiones aprobadas

La CLI actual ejecuta un comando por proceso. `CliApplication` parsea a un
`CliCommand` y `VaultCliCommands` implementa las operaciones, normalmente
abriendo y cerrando un `VaultSession` por cada comando. `VaultSession.close()`
cierra el `Vault`, la clave derivada y el lock; `VaultStorage.open()` autentica y
construye esa sesión. `SystemCliConsole` delega lecturas visibles y sin eco en
`System.console()` y no usa stdin como fallback.

Spec 006 agrega `Main session --vault <ruta> [--timeout-minutes <n>]`, timeout
default de 5 minutos y rango 1–60. En sesión se reutiliza una bóveda autenticada
para ejecutar `add`, `list`, `show`, `delete`, `set-totp`, `remove-totp`, `totp`
y `upgrade`; `help` y `exit` controlan el ciclo de sesión. Los cambios/formato de
bóveda no cambian. No se agregan dependencias ni se modifica
`docs/THREAT_MODEL.md` sin autorización expresa.

El timer usa tiempo monotónico y mide inactividad entre comandos: se inicia al
abrir/reanudar y se reinicia cuando termina cada comando. Un comando iniciado no
se interrumpe. Al vencer durante la espera del siguiente comando, se cierra la
sesión abierta, se descarta la entrada pendiente y se requiere una autenticación
nueva antes de despachar comandos.

## 2. Arquitectura propuesta

### Componentes y responsabilidades

- **`CliArgumentParser` / representación de comando:** extender el modelo de
  argumentos para `session`, su ruta y timeout validados antes de I/O. Separar
  explícitamente el comando de entrada a sesión de los comandos que se escriben
  dentro de ella. Las líneas de sesión se parsean como sintaxis de comandos sin
  `--vault`; no contienen secretos ni se conservan como historial.
- **Coordinador de sesión en `cli`:** nuevo componente, por ejemplo
  `CliSessionRunner`, dueño del `VaultSession` activo y del bucle `help`/`exit` /
  comandos. No expone ni guarda referencias a `Vault` o `VaultSession` después
  del bloqueo/cierre. Recibe consola, `VaultStorage`, handler, timeout y fuente
  de tiempo monotónica inyectables.
- **Reloj monotónico:** interfaz pequeña en `cli` (por ejemplo `Ticker`) basada
  en `System.nanoTime()` en producción. Comparar duraciones con aritmética que
  tolere el wraparound documentado de `nanoTime` y convertir minutos a nanosegundos
  evitando overflow; los límites de 1–60 minutos mantienen la conversión acotada.
- **Lectura temporizada de comandos:** `CliConsole` obtiene una operación
  cancelable/temporizada para la línea de comando, sin cambiar las lecturas de
  contraseña de los comandos activos. El adaptador de producción usa un único
  lector dedicado de `System.console()` para no permitir lecturas concurrentes.
  El coordinador espera el comando o el deadline; al deadline invalida la
  solicitud, cierra el `VaultSession` y descarta cualquier línea que devuelva la
  lectura ya pendiente antes de aceptar entrada posterior.
- **Handler de comandos:** separar la lógica operativa de la autenticación y del
  ciclo de vida. Los comandos existentes por proceso conservan su apertura y
  cierre actuales; el modo sesión invoca variantes que reciben el
  `VaultSession` ya autenticado y no lo cierran individualmente. El runner es el
  único dueño del cierre de esa sesión.
- **`Main` / `CliApplication`:** validar consola y argumentos de sesión antes de
  abrir Storage; traducir excepciones a mensajes españoles genéricos; garantizar
  cierre en `exit`, EOF, interrupción y fallos.

No crear un hilo de lectura por comando ni competir entre `readLine` y
`readPassword` sobre `System.console()`. Tras un timeout durante la espera de una
línea, puede ser necesario esperar a que esa lectura pendiente termine para
descartarla y pedir la contraseña maestra con el único lector. La bóveda debe
estar cerrada desde el instante del deadline, incluso durante esa espera; se
documentará esta limitación observable. Si la API de consola no permite
implementar el descarte sin lecturas concurrentes o sin conservar una sesión
desbloqueada, detenerse y revisar la spec antes de relajar el requisito.

### Flujo de sesión

1. Parsear `session`, validar ruta, opciones y timeout. Rechazar errores antes de
   preguntar por secretos o tocar Storage.
2. Comprobar consola disponible. Pedir contraseña maestra con lectura sin eco,
   abrir una única sesión mediante `VaultStorage.open` y borrar el `char[]` de
   entrada en `finally`.
3. Inicializar deadline monotónico y mostrar el prompt de sesión. La consola
   entrega líneas de comando mediante el lector único.
4. Si el deadline vence primero, invalidar el comando pendiente, cerrar el
   `VaultSession` y esperar que el lector descarte su resultado. Después pedir
   contraseña maestra; solo al abrir correctamente una sesión nueva reiniciar el
   deadline y aceptar comandos.
5. Parsear cada línea de forma independiente y ejecutar solo los comandos
   permitidos usando el `VaultSession` de la sesión. Un error sintáctico o
   recuperable no termina la sesión. Tras completar cada comando, reiniciar el
   deadline; si el plazo venció mientras el comando estaba activo, bloquear antes
   de leer/despachar el siguiente.
6. En `exit`, EOF, interrupción o error fatal, cerrar el lector/coordinador y
   `VaultSession`, borrar buffers controlables y terminar sin guardar
   implícitamente.

### Integración de comandos

La refactorización debe mantener un único cuerpo de negocio por comando siempre
que sea posible. El límite de apertura se moverá desde cada método de
`VaultCliCommands` a un wrapper de operación: el wrapper actual pide la clave,
abre/cierra la sesión y delega; el modo sesión delega con la sesión ya abierta.
Las funciones de cada comando no deben cerrar una sesión que no poseen.

`create` no se admite dentro del modo abierto. `generate` sigue siendo comando
independiente y no usa la sesión. `upgrade` opera sobre la sesión activa,
actualizando su versión a través de `VaultSession.upgradeToV2()` y respetando las
confirmaciones existentes. Evitar pasar credenciales maestras o secretos en
`CliCommand`, strings de línea, logs o excepciones.

## 3. Timeout, concurrencia y fallos

- Usar deadline monotónico, no `Clock.systemUTC()`, que se usa para fechas/TOTP y
  puede saltar por ajustes de hora.
- La espera del comando debe retornar uno de: línea disponible, EOF/error,
  timeout/cancelación. Una línea que llegue después de cancelar la solicitud se
  descarta y nunca se procesa como comando posterior.
- El único consumidor de entrada evita que una línea tardía del lector antiguo
  se confunda con la contraseña de reautenticación. No invocar `readPassword`
  hasta resolver/descartar la solicitud pendiente de `readLine`.
- Cerrar primero `VaultSession` al vencer; luego emitir el estado de bloqueo y
  gestionar la entrada pendiente/reautenticación. El cierre no llama a `save()`.
- Durante un comando no corre un bloqueo concurrente. Mutaciones y guardados
  mantienen su semántica atómica actual; el bloqueo se ejecuta en el límite
  posterior al comando si ya venció el deadline.
- Errores de comando recuperables conservan el proceso y la sesión solo si el
  estado sigue válido. Fallos que vuelvan inseguro el estado cierran la sesión;
  el error no incluirá causas sensibles ni contenido.
- `exit`, EOF e interrupción tienen limpieza idempotente. Si cerrar la sesión
  falla, comunicar fallo genérico y no informar terminación exitosa.
- No prometer borrado absoluto de memoria Java: verificar limpieza de buffers
  bajo propiedad de CLI/modelo y documentar copias internas de JDK/proveedor como
  limitación ya reconocida por el proyecto.

## 4. Pruebas y trazabilidad

Pruebas JUnit 5 en los paquetes existentes. Usar consola falsa con lecturas
temporizadas controlables, reloj monotónico falso, `VaultStorage`/handler
sustituibles y bóvedas temporales con datos sintéticos. No usar `sleep` real para
probar timeout ni debilitar el timeout de producción.

| Cobertura | Criterios |
|---|---|
| Parser de entrada `session`, timeout default/límites y rechazo antes de Storage | AC-1 |
| Consola no disponible, lectura de maestra, autenticación y borrado del `char[]` | AC-2, AC-5, AC-8 |
| Deadline monotónico, timeout exacto, cambios del reloj de pared y reinicio después de comando | AC-3, AC-6 |
| Carrera línea/deadline, cancelación, descarte de línea tardía y prohibición de despacho antes de reautenticar | AC-4, AC-5 |
| Reapertura correcta/incorrecta, reintento, cancelación y salida desde estado bloqueado | AC-5, AC-8 |
| Comandos de Specs 003/005 en una sesión compartida; guardado/cancelación, ayuda y comandos no permitidos | AC-7 |
| `exit`, EOF, interrupción, error fatal, liberación lock/sesión y ausencia de save implícito | AC-8 |
| Revisión de secretos en prompts, canales, errores, líneas, buffers, logs y ausencia de dependencia/formato nuevo | AC-9, AC-10 |
| Verificación integral en Windows; CI Linux al publicar, registrando solo cobertura realmente ejecutada | AC-10 |

Los dobles deben permitir observar `VaultSession.close()`, el lock liberado, los
intentos de `save()`, y si una línea expirada llegó o no al handler. Revisar que
el lector temporizado no introduzca una segunda lectura concurrente en producción.

## 5. Secuencia de implementación

1. Confirmar estado limpio/alcance y contratos de CLI/Storage; cerrar primero
   cualquier especificación previa según Constitución.
2. Añadir tests de caracterización/regresión del comportamiento actual de
   comandos one-shot y parsing, antes de refactorizar ownership.
3. Ampliar parser/tipos para opción `session`, límites 1–60 y comandos internos
   sin ruta; probar que toda validación precede a I/O.
4. Diseñar e implementar abstracción de reloj monotónico y contrato de lectura
   de línea cancelable/temporizada; probar carreras de timeout con dobles.
5. Refactorizar `VaultCliCommands` separando wrappers de autenticación/ownership
   de las operaciones que reciben una sesión compartida; conservar el flujo
   one-shot por regresión.
6. Implementar runner de sesión, lector único y transiciones abierto/bloqueado/
   reautenticando/salida con cierre seguro.
7. Integrar en `CliApplication`/`Main` y registrar comando en ayuda; cubrir
   errores/cancelaciones y límites de comandos admitidos.
8. Añadir pruebas integradas de persistencia y limpieza, revisar que no se guarda
   al bloquear/salir y ejecutar Spotless/`./mvnw.cmd verify`.
9. Revisar `docs/STATUS.md`, `AGENTS.md` o README si aplica; no tocar
   `docs/THREAT_MODEL.md` ni cambiar formato sin autorización explícita. Registrar
   límites de consola/plataforma y el run Linux cuando exista.

## 6. Riesgos, limitaciones y bloqueos

- **Lectura console con deadline:** `Console.readLine()` es bloqueante y no
  ofrece timeout. La arquitectura de lector único debe resolver timeout y línea
  tardía sin concurrencia de entrada. El riesgo afecta la capacidad de presentar
  inmediatamente el prompt de reautenticación si la lectura original sigue
  esperando; la bóveda se cierra al vencer de todas formas. Si no se puede cumplir
  de forma segura el descarte exigido por la spec, se detiene y solicita ajustar
  la spec, no se elimina el timeout.
- **Propiedad de sesión:** los handlers actuales abren/cerran sus propias
  sesiones. Refactorizar ownership puede introducir cierre prematuro, doble
  cierre o dejar un lock; deben cubrirse con pruebas.
- **Ventana en ejecución de comando:** la spec excluye el bloqueo a mitad de un
  comando; los datos permanecen descifrados durante la interacción/prompts de
  ese comando. Es una decisión de alcance aprobada, no una garantía de timeout
  continuo.
- **Memoria Java:** `VaultSession.close()` cierra objetos bajo control del
  proyecto, no garantiza borrar copias internas del JDK ni cadenas de entrada
  ordinaria.
- No se ha identificado necesidad de dependencias nuevas ni cambios al formato.
  Si al implementar se vuelve necesario modificar el modelo de amenazas,
  detenerse y pedir autorización expresa antes de editarlo.

## 7. Cierre

Implementar solo después de autorización explícita para implementación. Cerrar la
spec tras evidenciar AC-1–AC-10, `./mvnw.cmd verify` en verde, recursos liberados
en todos los estados y limitaciones de consola/plataforma registradas en
`docs/STATUS.md`.
