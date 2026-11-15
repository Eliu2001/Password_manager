# Tareas — Spec 004: password-generator

Tareas ordenadas por dependencia y pensadas para bloques de **20–30 minutos**.
Si una tarea supera ese tamaño, dividirla antes de continuar. Completar cada
tarea con evidencia breve (archivos/pruebas ejecutadas y resultado); no marcarla
completa sin verificar su criterio. No usar secretos reales ni fixtures sensibles.

## Preparación y API del generador

- [x] **T01 — Revisar spec, plan y puntos de integración** (20 min)\
  **RF:** R1–R5 · **AC:** AC-1–AC-8\
  **Hecho cuando:** se confirma aprobación de Spec 004 y cierre de Spec 003; se
  inspeccionan `CliArgumentParser`, `CliCommand`, `CliApplication`, `CliConsole`,
  `VaultCliCommands` y `Main`; se verifica que no existe implementación previa
  de `generator` y se identifican los límites de propiedad del `char[]`.\
  **Evidencia:** revisados `CliArgumentParser`, `CliCommand`, `CliApplication`,
  `CliConsole`, `VaultCliCommands`, `Main`, spec y plan; no existía código en
  `generator`. No se agregan dependencias ni se cambia formato o Threat Model.

- [x] **T02 — Crear API y validación de longitud** (25 min)\
  **RF:** R1, R2 · **AC:** AC-1, AC-2\
  **Hecho cuando:** `PasswordGenerator` ofrece una operación pública que acepta
  longitudes 12–128 inclusive y devuelve un `char[]`; rechaza longitudes inválidas
  antes de generar y no expone contraseñas como `String`.\
  **Evidencia:** `PasswordGenerator.generate(int)` valida 12–128 y devuelve
  `char[]`; `PasswordGeneratorTest` cubre 11, 12, 128, 129 y longitudes exactas.

- [x] **T03 — Implementar composición y selección aleatoria** (25 min)\
  **RF:** R1 · **AC:** AC-1, AC-2, AC-4\
  **Hecho cuando:** cada resultado contiene una mayúscula, minúscula, dígito y
  símbolo; el alfabeto se restringe al conjunto ASCII aprobado y el relleno y
  Fisher–Yates usan selecciones acotadas de `SecureRandom`, sin reducción modular.\
  **Evidencia:** `PasswordGenerator` usa `SecureRandom.nextInt(bound)` para grupos,
  relleno y Fisher–Yates; pruebas controladas verifican categorías, conjunto ASCII
  permitido, exclusión de espacios/controles y llamadas acotadas.

- [x] **T04 — Probar construcción determinista del generador** (25 min)\
  **RF:** R1, R2 · **AC:** AC-1, AC-2, AC-4\
  **Hecho cuando:** las pruebas del paquete de `generator` usan una fuente
  controlada por un punto de inyección no público, comprueban los casos límite y
  no afirman garantías absolutas a partir de muestras aleatorias.\
  **Evidencia:** `PasswordGeneratorTest` cubre casos válidos/inválidos,
  composición y caracteres permitidos usando un `SecureRandom` controlado; el
  constructor de inyección es package-private. `./mvnw.cmd verify`: BUILD SUCCESS,
  80 pruebas, 0 fallos y 1 omitida (symlink no disponible en Windows).

## Parser e integración CLI

- [x] **T05 — Añadir `GENERATE` y parsing de `--length`** (25 min)\
  **RF:** R2, R5 · **AC:** AC-3\
  **Hecho cuando:** `CliCommand` representa el comando sin ruta de bóveda y con
  longitud predeterminada 20 o la longitud validada; el parser acepta solo un
  entero decimal completo entre 12 y 128, y rechaza opciones desconocidas,
  repetidas, incompletas, no numéricas o fuera de rango antes de cualquier
  consulta a consola/generador.\
  **Evidencia:** `CliCommand` admite `GENERATE` sin ruta y guarda la longitud;
  `CliArgumentParser` valida sintaxis y rango sin I/O.

- [x] **T06 — Probar parser, defaults y argumentos inválidos** (25 min)\
  **RF:** R2 · **AC:** AC-3\
  **Hecho cuando:** la matriz cubre comando sin `--length`, límites 12/128,
  valores 11/129, número malformado, opción repetida/incompleta y opción no
  admitida; errores en español sin revelar valores sensibles.\
  **Evidencia:** `CliArgumentParserTest` cubre default, límites, fuera de rango,
  no numéricos, overflow, signo, opción duplicada/incompleta e incompatibles;
  `CliApplicationTest` comprueba el rechazo antes del gate de consola.

- [x] **T07 — Integrar ayuda, gate de consola y despacho** (25 min)\
  **RF:** R2, R4, R5 · **AC:** AC-3, AC-5, AC-8\
  **Hecho cuando:** `--help` documenta `generate [--length <n>]`; la aplicación
  requiere consola antes de despachar el comando y conecta el generador por una
  dependencia explícita. La operación no abre ni modifica una bóveda.\
  **Evidencia:** ayuda incluye comando, sintaxis, default y rango; prueba de
  aplicación confirma que sin consola el handler no se despacha. `Main` inyecta
  `PasswordGenerator` en el ejecutor CLI.

- [x] **T08 — Implementar presentación privada y limpieza** (25 min)\
  **RF:** R3, R4 · **AC:** AC-5–AC-7\
  **Hecho cuando:** la CLI genera y llama una sola vez a
  `CliConsole.printSecret(char[])`; borra el arreglo en `finally`, informa éxito
  solo tras impresión correcta y no reintenta el secreto por otro canal.\
  **Evidencia:** `generate` llama una vez a `printSecret`, limpia su arreglo en
  `finally` y muestra éxito solo tras imprimir; pruebas cubren longitud default y
  explícita, aislamiento del canal y limpieza. `./mvnw.cmd verify`: BUILD SUCCESS,
  83 pruebas, 0 fallos y 1 omitida (symlink no disponible en Windows).

## Pruebas de seguridad e integración

- [x] **T09 — Probar aislamiento del secreto y ausencia de consola** (25 min)\
  **RF:** R3, R4 · **AC:** AC-5, AC-6\
  **Hecho cuando:** con consola no disponible no se genera ni se lee stdin; en
  éxito el secreto solo llega a `printSecret` y no aparece en salida normal,
  errores, logs ni otros canales capturados.\
  **Evidencia:** `CliApplicationTest.generateUsesInteractiveGateAndPrivateChannelWithoutVaultAccess`
  verifica consola ausente sin despacho/lecturas y que el resultado solo se
  entrega al canal privado; salida normal contiene únicamente el mensaje de
  estado y no hay salida de error.

- [x] **T10 — Probar fallos, estados y limpieza** (25 min)\
  **RF:** R3, R4 · **AC:** AC-6, AC-7\
  **Hecho cuando:** fallo de generación o impresión produce mensaje español
  genérico y estado no exitoso, no reintenta por stdout/error y limpia el
  `char[]` poseído por CLI. Se documenta que una escritura fallida podría haber
  mostrado un prefijo en la consola real.\
  **Evidencia:** `generateFailuresAreGenericAndClearBuffersWithoutFallback`
  inyecta fallo del generador y de impresión; comprueba mensajes genéricos,
  ausencia de éxito/salida alternativa y borrado del buffer en fallo de impresión.
  La escritura parcial previa a un error del terminal no es reversible.

- [x] **T11 — Revisar arquitectura, dependencias y alcance** (20 min)\
  **RF:** R5 · **AC:** AC-8\
  **Hecho cuando:** `generator` no depende de `cli`, `storage`, `model` ni
  `crypto`; `crypto` sigue independiente; no se agregan dependencias ni se
  modifican formato v1, `docs/FORMAT.md` o `docs/THREAT_MODEL.md`.\
  **Evidencia:** `generator` solo depende del JDK; no hay imports de `generator`
  en `crypto`; `pom.xml`, formato v1 y `docs/THREAT_MODEL.md` no cambiaron.

- [x] **T12 — Ejecutar verificación y registrar cierre** (25 min)\
  **RF:** R1–R5 · **AC:** AC-1–AC-8\
  **Hecho cuando:** `./mvnw.cmd verify` termina en verde; se revisan las pruebas
  y se actualiza `docs/STATUS.md` con resultado, total de pruebas y limitaciones
  observadas. No se declara Spec 004 cerrada si falta evidencia para algún AC.\
  **Evidencia:** `./mvnw.cmd verify`: BUILD SUCCESS, 84 pruebas, 0 fallos y 1
  omitida por symlink no disponible en Windows. AC-1–AC-8 quedan cubiertos por
  las pruebas del generador, parser y CLI registradas en T01–T11.

## Fuera de alcance

No implementar generación dentro de `add`, almacenamiento automático, portapapeles,
salida por stdout redirigible, escritura a archivo, historial, evaluación de
fortaleza, categorías configurables, frases de contraseña, dependencias nuevas,
cambios al formato o cambios a `docs/THREAT_MODEL.md`.
