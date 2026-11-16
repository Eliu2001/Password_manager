# Tareas — Spec 005: TOTP para entradas

Tareas ordenadas por dependencia y pensadas para bloques de **20–30 minutos**.
Si una tarea supera ese tamaño, dividirla antes de continuar. Completar cada
tarea con evidencia breve (archivos/pruebas ejecutadas y resultado); no marcarla
completa sin verificar sus criterios. No usar secretos reales ni fixtures
sensibles.

## Preparación y compatibilidad v1

- [x] **T01 — Revisar spec, plan y puntos de integración** (20 min)\
  **RF:** R1–R5 · **AC:** AC-1–AC-10\
  **Hecho cuando:** se confirma la aprobación de Spec 005 y cierre de Spec 004;
  se inspeccionan `Entry`, `Vault`, `VaultHeader`, codecs, `VaultSession`,
  `VaultStorage` y componentes CLI; se identifican ownership y puntos de
  compatibilidad/versionado.\
  **Evidencia:** inventario realizado: `Entry` posee y limpia la contraseña;
  `Vault` restaura y elimina entradas por ID; `VaultHeader` fija v1/AAD;
  `VaultPayloadCodec`/`VaultPayloadParser` serializan y parsean exclusivamente
  v1; `VaultStorage.create/open` crea y carga sesiones y `VaultSession.save()`
  reconstruye encabezado/AAD v1 y persiste mediante temporal y movimiento
  atómico; cierre de sesión cierra modelo/clave/lock. En CLI se revisaron
  `CliCommand`, `CliArgumentParser`, `CliApplication`, `CliConsole`,
  `VaultCliCommands`, `CliCommandHandler` y `Main`: comandos enumerados
  explícitamente, parseo previo a I/O, gate común de consola, lectura `char[]`
  sin eco y canal `printSecret`. Búsqueda en `src` no encontró implementación
  TOTP/Base32. Spec 004 figura cerrada en `docs/STATUS.md` y aprobada en su spec.
  **Estado documental:** el plan registra aprobación el 2026-11-15 y la
  autorización explícita para implementar fue reiterada por el usuario en esta
  ejecución; el encabezado de `spec.md` quedó actualizado para reflejarlo. No se
  hicieron cambios a dependencias ni a `docs/THREAT_MODEL.md`.

- [x] **T02 — Añadir pruebas de regresión para apertura y guardado v1** (25 min)\
  **RF:** R4 · **AC:** AC-8, AC-9\
  **Hecho cuando:** pruebas con bóvedas sintéticas v1 verifican lectura y guardado
  conservando versión 1 y el esquema payload histórico antes de cambiar codecs.\
  **Evidencia:** las pruebas demuestran que los datos v1 existentes sobreviven
  abrir/guardar/reabrir y que los bytes nuevos no se interpretan como secretos.

## Cálculo TOTP y Base32

- [x] **T03 — Implementar API TOTP con reloj inyectable** (25 min)\
  **RF:** R1 · **AC:** AC-1, AC-2\
  **Hecho cuando:** `cl.germain.vault.totp` calcula RFC 6238 con HMAC-SHA-1,
  contador de 30 segundos y entrega código como `char[]`; producción usa reloj
  UTC y pruebas pueden fijar el `Clock`.\
  **Evidencia:** API no expone código como `String`, no depende de otros paquetes
  del proyecto y limpia buffers de su propiedad.

- [x] **T04 — Probar vectores RFC 6238 y formato del código** (25 min)\
  **RF:** R1 · **AC:** AC-1, AC-2\
  **Hecho cuando:** vectores RFC 6238 SHA-1 verifican códigos esperados,
  truncamiento dinámico y cambios de periodo; se comprueban exactamente seis
  dígitos, incluidos ceros iniciales.\
  **Evidencia:** tests en el paquete `totp` usan tiempo controlado y datos de
  conformidad públicos, nunca secretos reales.

- [x] **T05 — Validar límites temporales y limpiar cálculo** (20 min)\
  **RF:** R1 · **AC:** AC-2\
  **Hecho cuando:** tiempos pre-epoch y condiciones aritméticas inválidas fallan
  sin devolver código parcial; los límites exactos de periodo y segundos restantes
  quedan definidos y probados.\
  **Evidencia:** tests verifican rechazo, código no filtrado en excepciones y
  cálculo de `30 - floorMod(epochSecond, 30)`.

- [x] **T06 — Implementar decodificador Base32 sobre `char[]`** (25 min)\
  **RF:** R2 · **AC:** AC-3\
  **Hecho cuando:** acepta mayúsculas/minúsculas y padding opcional correcto,
  rechaza espacios/separadores/caracteres inválidos/padding incorrecto y valida
  secreto decodificado de 20–64 bytes.\
  **Evidencia:** no convierte el secreto a `String`; limpia buffers temporales en
  éxito y error, y valida bits de relleno canónicos RFC 4648.

- [x] **T07 — Probar Base32 y límites adversariales** (25 min)\
  **RF:** R2 · **AC:** AC-3\
  **Hecho cuando:** matriz cubre ambos casos de capitalización, padding omitido y
  correcto, padding interno/excesivo, bits no canónicos, caracteres inválidos,
  vacío y tamaños 19/20/64/65 bytes decodificados.\
  **Evidencia:** casos inválidos no producen bytes utilizables y las entradas
  sintéticas se limpian cuando pertenecen a la prueba.

## Modelo y ciclo de vida

- [x] **T08 — Añadir propiedad TOTP opcional a `Entry`** (25 min)\
  **RF:** R2, R5 · **AC:** AC-4, AC-6\
  **Hecho cuando:** `Entry` copia defensivamente el `byte[]` al establecerlo,
  devuelve copia de propiedad del llamador y puede retirarlo; las operaciones
  rechazan estado cerrado.\
  **Evidencia:** el reemplazo/retirada/cierre limpia la copia interna y no expone
  referencias mutables.

- [x] **T09 — Integrar mutaciones TOTP por ID en `Vault`** (25 min)\
  **RF:** R3 · **AC:** AC-4, AC-5, AC-6\
  **Hecho cuando:** `Vault` permite establecer, consultar y retirar secreto por
  ID sin mutadores públicos en `Entry`; IDs inexistentes siguen el contrato del
  modelo existente.\
  **Evidencia:** las operaciones preservan orden/identidad y no aceptan secretos
  fuera de los límites aprobados.

- [x] **T10 — Probar ciclo de vida y limpieza del modelo** (25 min)\
  **RF:** R2, R3, R5 · **AC:** AC-4, AC-6\
  **Hecho cuando:** pruebas observables cubren secreto ausente/presente, copia
  defensiva, reemplazo, retirada, cierre de bóveda y eliminación de entrada.\
  **Evidencia:** todas las copias bajo propiedad de `Entry` se borran en los
  caminos definidos y el llamador conserva propiedad de sus arreglos originales.

## Versionado y codecs de almacenamiento

- [x] **T11 — Parsear y ensamblar encabezados v1/v2** (25 min)\
  **RF:** R4 · **AC:** AC-8, AC-9\
  **Hecho cuando:** `VaultHeader` maneja versiones 1 y 2, conserva exactamente
  50 bytes, campos, rangos KDF y AAD de 34 bytes; versiones desconocidas siguen
  fallando antes de descifrar.\
  **Evidencia:** la versión incluida en el prefijo AAD coincide con la versión
  serializada y se mantienen validación de longitud y nonce GCM.

- [x] **T12 — Probar encabezados, AAD y rechazos de versión** (25 min)\
  **RF:** R4 · **AC:** AC-8, AC-9\
  **Hecho cuando:** tests byte a byte verifican versiones 1/2 y el AAD exacto;
  alteraciones de versión/cabecera, magic desconocida, versión no soportada y
  longitudes incoherentes son rechazadas sin modificar el archivo.\
  **Evidencia:** preservar pruebas existentes de header v1 y añadir casos v2.

- [x] **T13 — Hacer codec y parser conscientes de la versión** (25 min)\
  **RF:** R4 · **AC:** AC-8, AC-9\
  **Hecho cuando:** codecs seleccionan esquema según versión; v1 mantiene
  serialización histórica byte por byte y v2 incorpora el campo TOTP sin
  ambigüedad.\
  **Evidencia:** pruebas de regresión v1 pasan y no se infiere versión a partir
  del contenido del payload.

- [x] **T14 — Serializar payload v2 y limpiar buffers** (25 min)\
  **RF:** R4, R5 · **AC:** AC-4, AC-9\
  **Hecho cuando:** cada registro v2 agrega uint32 big-endian y bytes TOTP tras
  los campos v1; longitud cero representa ausencia y las longitudes respetan
  límites.\
  **Evidencia:** buffers temporales de secreto y plaintext se limpian; el payload
  cifrado completo sigue limitado por 16 MiB.

- [x] **T15 — Parsear payload v2 de forma cerrada** (25 min)\
  **RF:** R4, R5 · **AC:** AC-4, AC-9\
  **Hecho cuando:** parser v2 valida uint32, bytes disponibles, secreto opcional
  20–64 bytes, campos completos, IDs únicos y consumo exacto antes de devolver
  el `Vault`.\
  **Evidencia:** cualquier error cierra/borrra el modelo parcial y buffers sin
  exponer entradas parciales.

- [x] **T16 — Probar parsing adversarial y round-trip v2** (25 min)\
  **RF:** R4, R5 · **AC:** AC-4, AC-9\
  **Hecho cuando:** round-trip de entradas con/sin TOTP conserva campos, IDs,
  fechas y orden; se rechazan secreto truncado, longitud inválida, overflow,
  trailing bytes, secreto fuera de rango y UTF-8/estructura inválidos.\
  **Evidencia:** sin secretos reales en fixtures; verificar que el archivo de
  bóveda no contiene el secreto Base32 ni los bytes sintéticos en claro.

- [x] **T17 — Conservar versión en `VaultSession` y creación** (25 min)\
  **RF:** R4 · **AC:** AC-8, AC-9\
  **Hecho cuando:** `VaultSession` retiene versión cargada; bóvedas nuevas se
  crean en v2; `save()` de una sesión v1 sigue escribiendo v1 y `save()` de v2
  escribe v2.\
  **Evidencia:** pruebas create/open/save/reopen cubren ambas versiones y confirman
  que no hay conversión implícita.

- [x] **T18 — Probar round-trip y compatibilidad entre versiones** (25 min)\
  **RF:** R4 · **AC:** AC-4, AC-8, AC-9\
  **Hecho cuando:** pruebas integradas abren fixtures v1 y v2, guardan/reabren
  cada una conservando su versión, y comprueban rechazo de downgrade/lectura
  cruzada inválida.\
  **Evidencia:** comparar el esquema v1 con fixtures sintéticos congelados y
  confirmar que sus registros no cambian de interpretación.

## Conversión explícita v1 → v2

- [x] **T19 — Implementar conversión de sesión con write path existente** (25 min)\
  **RF:** R4 · **AC:** AC-8, AC-9\
  **Hecho cuando:** operación explícita de sesión convierte v1 a v2, verifica
  máximo antes de escribir y reutiliza lock, huella, permisos, temporal, force y
  atomic move de Storage.\
  **Evidencia:** solo actualiza versión/huella tras reemplazo exitoso; fallo deja
  archivo v1 intacto y sesión en estado coherente/reintentable.

- [x] **T20 — Probar conversión, cancelación indirecta y límites** (25 min)\
  **RF:** R4 · **AC:** AC-8, AC-9\
  **Hecho cuando:** tests cubren v1→v2 preservando datos/IDs/fechas, intento de
  upgrade en v2, payload resultante mayor de 16 MiB y cambios externos detectados.\
  **Evidencia:** casos de error conservan el archivo anterior y no informan éxito.

- [x] **T21 — Probar fallos de permisos y movimiento atómico en upgrade** (25 min)\
  **RF:** R4, R5 · **AC:** AC-8, AC-9\
  **Hecho cuando:** se inyectan fallos previos/al movimiento y se comprueba
  preservación del destino, cleanup temporal, sesión abierta y ningún fallback
  no atómico.\
  **Evidencia:** en Windows, el hook de movimiento simula
  `AtomicMoveNotSupportedException`; se verifica archivo intacto, sesión v1
  abierta y temporal eliminado. La escritura/reemplazo atómico real usa las
  pruebas existentes de Storage; el fallo del filesystem no se afirma como real.

## CLI y presentación

- [x] **T22 — Ampliar `CliCommand` y parser para nuevos comandos** (25 min)\
  **RF:** R3, R5 · **AC:** AC-5, AC-7\
  **Hecho cuando:** parser reconoce `set-totp`, `remove-totp`, `totp` con
  `--vault` e ID, y `upgrade` con `--vault`; valida IDs/opciones antes de I/O.\
  **Evidencia:** comando mantiene argumentos no secretos y rechaza opciones
  desconocidas, duplicadas, incompletas o incompatibles.

- [x] **T23 — Probar parser, estados de argumentos y ayuda** (25 min)\
  **RF:** R3, R5 · **AC:** AC-5, AC-7\
  **Hecho cuando:** tabla cubre comandos válidos, ID minúscula de 32 hex,
  opciones inválidas y ausencia de consulta a consola/storage ante error; ayuda
  lista sintaxis en español.\
  **Evidencia:** tests usan parser aislado sin acceder al filesystem.

- [x] **T24 — Integrar prompts, confirmaciones y canal de consola** (25 min)\
  **RF:** R3, R5 · **AC:** AC-5–AC-7\
  **Hecho cuando:** consola permite leer dos Base32 como `char[]` sin eco,
  confirmar reemplazo/retirada/upgrade y enviar código solo por `printSecret`; no
  hay fallback a stdin.\
  **Evidencia:** dobles de consola permiten observar llamadas, canal, cancelación
  y limpieza sin usar datos sensibles reales.

- [x] **T25 — Implementar `set-totp`** (25 min)\
  **RF:** R2, R3, R5 · **AC:** AC-3–AC-6\
  **Hecho cuando:** abre bóveda v2, obtiene entrada por ID, verifica dos lecturas,
  decodifica y valida, confirma reemplazo exacto cuando aplica, actualiza, guarda
  y reporta éxito solo tras `save()`.\
  **Evidencia:** en v1 informa que requiere `upgrade`; limpia master password,
  lecturas y bytes decodificados en todos los caminos.

- [x] **T26 — Probar `set-totp` y fallos de persistencia** (25 min)\
  **RF:** R2, R3, R5 · **AC:** AC-3–AC-7\
  **Hecho cuando:** se prueban lectura coincidente/discrepante/EOF, Base32
  inválido, secreto fuera de rango, ID inexistente, reemplazo sí/no, bóveda v1
  y save fallido.\
  **Evidencia:** `CliApplicationTest` cubre coincidencia/discrepancia/EOF,
  Base32 inválido y corto, ID inexistente, reemplazo confirmado/cancelado, v1,
  cambio externo antes de save y limpieza; los archivos no se guardan parcialmente.

- [x] **T27 — Implementar `remove-totp`** (25 min)\
  **RF:** R3, R5 · **AC:** AC-5, AC-6, AC-8\
  **Hecho cuando:** operación exige versión v2, secreto existente y confirmación
  exacta `sí` o `si`; solo entonces quita, guarda e informa éxito.\
  **Evidencia:** cancelación, EOF, secreto ausente, ID inexistente y v1 no guardan
  cambios; sesión/master password se limpian y cierran.

- [x] **T28 — Probar `remove-totp` y persistencia** (25 min)\
  **RF:** R3, R5 · **AC:** AC-5, AC-6, AC-8\
  **Hecho cuando:** integración verifica confirmación positiva tras reopen y
  archivo sin cambios ante no/EOF/error; quitar borra la copia interna.\
  **Evidencia:** prueba confirmación, no, EOF, secreto ausente y fallo de save por
  modificación externa; confirma persistencia al reabrir y error genérico.

- [x] **T29 — Implementar consulta `totp`** (25 min)\
  **RF:** R1, R3, R5 · **AC:** AC-1, AC-2, AC-5–AC-7\
  **Hecho cuando:** comando requiere v2 y secreto; obtiene copia, calcula un
  código con reloj actual, llama una vez a `printSecret`, muestra segundos
  restantes por salida normal y no refresca.\
  **Evidencia:** borra código, secreto y master password en `finally`; no guarda.

- [x] **T30 — Probar salida confidencial y fallos de `totp`** (25 min)\
  **RF:** R1, R3, R5 · **AC:** AC-1, AC-2, AC-5–AC-7\
  **Hecho cuando:** pruebas cubren resultado correcto/vector, ID inexistente,
  entrada sin secreto, v1, consola ausente, fallo de cálculo e impresión.\
  **Evidencia:** prueba vector RFC a nivel API, salida privada única, ID/secreto
  ausentes, v1, consola ausente, cálculo pre-epoch e impresión fallida; se borran
  los buffers y no se escribe código en salida normal.

- [x] **T31 — Integrar comando `upgrade` en la CLI** (25 min)\
  **RF:** R4, R5 · **AC:** AC-5, AC-7, AC-8\
  **Hecho cuando:** CLI abre bóveda, pide confirmación exacta `sí` o `si`, invoca la
  conversión explícita y reporta éxito solo después de completarse; otra respuesta
  cancela sin guardar.\
  **Evidencia:** mensajes españoles genéricos, sesión/copia maestra cerradas y
  limpiadas ante éxito/error/cancelación.

- [x] **T32 — Probar flujo CLI de `upgrade`** (25 min)\
  **RF:** R4, R5 · **AC:** AC-5, AC-7, AC-8\
  **Hecho cuando:** integración cubre confirmación, cancelación, v2 ya actualizada,
  v1 incorrecta/alterada, consola ausente y fallo de conversión.\
  **Evidencia:** cancelación conserva bytes; confirmación convierte y reabre;
  v1 alterada y segundo upgrade v2 fallan sin éxito falso. El fallo atómico se
  cubre en pruebas de Storage.

## Documentación y cierre

- [x] **T33 — Documentar formatos v1 y v2** (25 min)\
  **RF:** R4 · **AC:** AC-8, AC-9\
  **Hecho cuando:** `docs/FORMAT.md` conserva definición v1 y documenta v2:
  versión/AAD, encabezado, campo TOTP por entrada, longitudes, límites y rechazo.\
  **Evidencia:** cotejar byte offsets y payload documentado con codecs y pruebas;
  anotar v2 como versión actual de creación.

- [x] **T34 — Revisar arquitectura, dependencias y seguridad** (25 min)\
  **RF:** R1–R5 · **AC:** AC-1–AC-10\
  **Hecho cuando:** `totp` no depende de paquetes prohibidos; `crypto` continúa
  independiente; no hay dependencia nueva ni secretos en logs, errores o
  fixtures; `docs/THREAT_MODEL.md` no cambió sin autorización.\
  **Evidencia:** `totp` importa solo JDK; `crypto` no importa paquetes del
  proyecto; `pom.xml` no cambió. `docs/THREAT_MODEL.md` se actualizó luego con
  aprobación explícita del usuario. Pruebas cubren canales y errores genéricos.
  Limitación: proveedores JDK pueden mantener copias internas de clave HMAC no
  borrables por la aplicación.

- [x] **T35 — Ejecutar verificación completa y registrar resultado** (25 min)\
  **RF:** R1–R5 · **AC:** AC-1–AC-10\
  **Hecho cuando:** `./mvnw.cmd verify` termina en verde y cada AC tiene evidencia
  enlazada a tests/documentación; `docs/STATUS.md` registra resultado, conteo de
  pruebas y limitaciones por plataforma.\
  **Evidencia:** `./mvnw.cmd verify` — BUILD SUCCESS, 103 pruebas, 0 fallos y 1
  omitida (symlink no disponible en Windows). Resultado y limitaciones registrados
  en `docs/STATUS.md`; AC-1–AC-10 vinculados a pruebas y `docs/FORMAT.md`.

## Fuera de alcance

No implementar TOTP como segundo factor de desbloqueo, importación QR/URI,
generación de secretos para aprovisionar cuentas, parámetros TOTP configurables,
edición general, portapapeles, nuevas dependencias ni cambios a
`docs/THREAT_MODEL.md` sin solicitud expresa.
