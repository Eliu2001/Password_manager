# Tareas — Spec 002: vault-storage

Tareas ordenadas por dependencia y pensadas para bloques de **20–30 minutos**.
Si una tarea supera ese tamaño, dividirla antes de continuar. Completar cada
tarea con evidencia breve (archivos/pruebas ejecutadas y resultado); no marcarla
completa sin verificar su criterio.

## Preparación y modelo

- [x] **T01 — Revisar alcance aprobado y estructura existente** (20 min)  
  **RF:** todos · **AC:** todos  
  **Hecho cuando:** se confirma que la implementación sigue la spec aprobada, el
  plan y las convenciones del proyecto; no se añaden dependencias.
  **Evidencia:** Spec 002 aprobada y plan revisado; no existía paquete `model` ni
  código de storage. Se corrigió en el plan el texto que aún indicaba que las
  aclaraciones estaban pendientes de aprobación. `./mvnw.cmd verify`: BUILD
  SUCCESS, 25 pruebas, 0 fallos.

- [x] **T02 — Definir tipos del modelo y copias defensivas** (25 min)  
  **RF:** R3, R4, R7 · **AC:** AC-4, AC-5, AC-16  
  **Hecho cuando:** se define e implementa `Entry` y `Vault` en `model`, IDs de
  16 bytes con copias defensivas, secuencia ordenada y fechas con `Clock`.
  **Evidencia:** creados `Entry` y `Vault`; el ID se copia al construir y al
  consultar, y fechas se representan como `Instant` con `Clock` inyectable en
  `Vault`. `EntryTest` verifica identidad/fechas y copias. `./mvnw.cmd verify`:
  BUILD SUCCESS, 26 pruebas, 0 fallos.

- [x] **T03 — Implementar operaciones por ID y ciclo de vida del modelo** (25 min)  
  **RF:** R4, R7 · **AC:** AC-4, AC-16  
  **Hecho cuando:** agregar, obtener, listar, actualizar y quitar funcionan por
  ID; duplicados servicio/usuario se aceptan, IDs repetidos se rechazan y las
  entradas no se mutan directamente.
  **Evidencia:** `Vault` implementa add/get/list/update/remove por ID, conserva
  orden, permite duplicados de servicio/usuario y actualiza `updatedAt` vía
  `Clock`; IDs se generan con `SecureRandom` y Entry no expone mutadores.
  `EntryTest` cubre CRUD y orden. `./mvnw.cmd verify`: BUILD SUCCESS, 27 pruebas,
  0 fallos.

- [x] **T04 — Implementar propiedad y limpieza de contraseñas de entrada** (25 min)  
  **RF:** R3, R7 · **AC:** AC-16, AC-17  
  **Hecho cuando:** agregar/actualizar copia el `char[]`; reemplazo, quitar y
  cierre borran la copia poseída; consultas devuelven copias y temporales
  propios se limpian en `finally`.
  **Evidencia:** `Entry` y `Vault` mantienen copias propias, las consultas
  entregan copias y se borran arreglos internos al actualizar, quitar y cerrar.
  `EntryTest` inspecciona en pruebas los buffers internos sintéticos y verifica
  que quedan a cero. `./mvnw.cmd verify`: BUILD SUCCESS, 28 pruebas, 0 fallos.

- [x] **T05 — Probar modelo, fechas y secretos** (25 min)  
  **RF:** R3, R4, R7 · **AC:** AC-4, AC-16  
  **Hecho cuando:** pruebas con `Clock` fijo verifican igualdad inicial de
  fechas, avance de `updatedAt`, orden, CRUD por ID, copias de arreglos y
  borrado al reemplazar/quitar/cerrar.
  **Evidencia:** `EntryTest` cubre CRUD y orden, entradas duplicadas por
  servicio/usuario, rechazo de IDs restaurados repetidos, campos obligatorios,
  `Clock` con fracciones de milisegundo truncadas, copia de contraseñas y borrado
  al actualizar/quitar/cerrar. `./mvnw.cmd verify`: BUILD SUCCESS, 30 pruebas,
  0 fallos.

## Codec y formato

- [x] **T06 — Implementar codificación del payload** (25 min)  
  **RF:** R3, R4 · **AC:** AC-4, AC-5  
  **Hecho cuando:** serializa contador, ID, fechas `int64` Unix UTC big-endian y
  cuatro campos con longitud uint32 en el orden especificado; mantiene el orden
  de entradas y limpia temporales.
  **Evidencia:** `VaultPayloadCodec` serializa contador/ID/fechas/campos con
  longitudes big-endian, conserva orden y limita el payload al tamaño de archivo
  permitido. Codifica UTF-8 estrictamente y limpia copias/buffers temporales.
  Pruebas comparan bytes exactos y bóveda vacía. `./mvnw.cmd verify`: BUILD
  SUCCESS, 32 pruebas, 0 fallos.

- [x] **T07 — Implementar UTF-8 estricto y validación de campos** (25 min)  
  **RF:** R3, R5 · **AC:** AC-5, AC-8  
  **Hecho cuando:** encoder/decoder rechazan malformados, no normalizan ni
  recortan, rechazan campos obligatorios vacíos y codifican notas ausentes o
  vacías como longitud cero.
  **Evidencia:** añadido `StrictUtf8` con REPORT para entrada/salida; contraseñas
  se decodifican a `char[]`, y la conversión limpia buffers propios. Pruebas
  verifican UTF-8 inválido, UTF-16 inválido, campos requeridos vacíos y
  preservación de Unicode descompuesto sin normalizar. `./mvnw.cmd verify`:
  BUILD SUCCESS, 35 pruebas, 0 fallos.

- [x] **T08 — Implementar parser defensivo del payload** (25 min)  
  **RF:** R3, R5 · **AC:** AC-4, AC-8, AC-17  
  **Hecho cuando:** comprueba límites antes de reservar, detecta overflow,
  longitudes/campos incompletos, contador imposible, IDs repetidos y bytes
  sobrantes; no publica entradas parcialmente parseadas.
  **Evidencia:** `VaultPayloadParser` valida contador, longitudes unsigned y
  bytes disponibles antes de reservar; restaura todo el payload o cierra el
  Vault parcial, limpia IDs/campos/password temporales y rechaza trailing bytes,
  UTF-8 inválido e IDs repetidos. `./mvnw.cmd verify`: BUILD SUCCESS, 38 pruebas,
  0 fallos.

- [x] **T09 — Probar round-trip y parsing adversarial** (25 min)  
  **RF:** R3, R5 · **AC:** AC-4, AC-5, AC-8  
  **Hecho cuando:** pruebas cubren caracteres multibyte, ausencia de NFC/trim,
  UTF-8 inválido, notas vacías, duplicados permitidos, IDs duplicados,
  truncamientos, longitudes incoherentes y overflow.
  **Evidencia:** pruebas verifican Unicode multibyte/descompuesto exacto,
  encoding layout, notas vacías, par servicio/usuario duplicado conservando
  orden, campos obligatorios vacíos, UTF-8 inválido, IDs duplicados, contador
  imposible, longitudes incoherentes y trailing bytes. `./mvnw.cmd verify`:
  BUILD SUCCESS, 40 pruebas, 0 fallos.

- [x] **T10 — Implementar encabezado v1 byte a byte** (25 min)  
  **RF:** R1, R2 · **AC:** AC-1, AC-2  
  **Hecho cuando:** codec crea/lee los 50 bytes con magic, versión, parámetros,
  salt, nonce y longitud en anchos y orden exactos big-endian; conserva los
  bytes originales y expone los primeros 34 para AAD.
  **Evidencia:** `VaultHeader` codifica y parsea encabezado de 50 bytes,
  conserva una copia exacta y devuelve el prefijo original de 34 bytes como AAD.
  `VaultHeaderTest` compara el formato byte a byte y verifica copias defensivas.
  `./mvnw.cmd verify`: BUILD SUCCESS, 42 pruebas, 0 fallos.

- [x] **T11 — Probar encabezado, AAD y parámetros KDF** (25 min)  
  **RF:** R1, R2, R5 · **AC:** AC-1, AC-2, AC-7, AC-9  
  **Hecho cuando:** pruebas comparan bytes esperados, alteran campos autenticados
  válidos y confirman error genérico; parámetros fuera de rango se rechazan
  antes de derivar.
  **Evidencia:** pruebas alteran cada byte de los 50 del encabezado y confirman
  rechazo por autenticación o validación estructural; prueban magic/versión,
  longitud de ciphertext y límites Argon2id, incluidos extremos aceptados.
  `./mvnw.cmd verify`: BUILD SUCCESS, 45 pruebas, 0 fallos.

## Paths, bloqueo y permisos

- [x] **T12 — Canonizar rutas y validar enlaces simbólicos** (25 min)  
  **RF:** R6, R10 · **AC:** AC-11, AC-14  
  **Hecho cuando:** resuelve el padre por ruta real, canoniza archivo existente,
  usa ruta canónica para el lock y rechaza bóveda/lock simbólicos; documenta
  dependencia de mayúsculas del filesystem.
  **Evidencia:** `VaultPaths.resolve` canonicaliza directorio padre y archivos
  existentes, deriva lock estable y rechaza enlaces simbólicos/destinos no
  regulares. `VaultPathsTest`: rutas normalizadas y padre ausente pasan. Prueba
  de symlink omitida por assumption: Windows devolvió `FileSystemException` al
  intentar crearlos; debe ejecutarse en CI con symlinks habilitados. `verify`:
  BUILD SUCCESS, 49 pruebas, 0 fallos, 1 omitida.

- [x] **T13 — Implementar lock lateral exclusivo** (25 min)  
  **RF:** R10 · **AC:** AC-14  
  **Hecho cuando:** crea/abre archivo lateral privado, adquiere lock exclusivo,
  lo retiene hasta `close()` y maneja fallos de adquisición sin filtrar secretos.
  **Evidencia:** `VaultLock` obtiene `FileLock.tryLock()` sobre el sidecar estable,
  rechaza solapamiento en el proceso y usa `NOFOLLOW_LINKS`; pruebas verifican
  contención, liberación, cierre idempotente y archivo vacío. Los permisos
  privados se completan y verifican en T14–T15. `verify`: BUILD SUCCESS,
  51 pruebas, 0 fallos, 1 omitida por symlink no disponible.

- [x] **T14 — Implementar permisos privados por plataforma** (25 min)  
  **RF:** R9, R10 · **AC:** AC-12, AC-13  
  **Hecho cuando:** POSIX usa 0600 y Windows ACL queda restringida al usuario
  actual, configurados antes de escribir; si no se pueden asegurar, falla
  cerrado.
  **Evidencia:** `PrivateFilePermissions` crea POSIX con `rw-------` y verifica
  0600; en Windows reemplaza/verifica la ACL con acceso solo del propietario.
  Se integra en la adquisición del lock antes de abrirlo para escritura.
  `PrivateFilePermissionsTest` pasa en Windows en el filesystem de CI; la rama
  POSIX queda pendiente de ejecución en CI POSIX. `verify`: BUILD SUCCESS,
  53 pruebas, 0 fallos, 1 omitida por symlink no disponible.

- [x] **T15 — Probar lock, rutas y permisos disponibles** (25 min)  
  **RF:** R10 · **AC:** AC-13, AC-14  
  **Hecho cuando:** segunda sesión no obtiene lock, cierre lo libera, lock no
  contiene secretos y se verifican permisos en la plataforma ejecutada. Las
  pruebas de symlink/permisos no disponibles usan assumptions y registran la
  limitación; no se afirma verificación de otra plataforma.
  **Evidencia:** pruebas verifican bloqueo exclusivo, liberación, aliases
  canónicos compartiendo lock, lock vacío y permisos ACL del usuario actual en
  Windows. La prueba de symlinks sigue omitida por `FileSystemException` en este
  entorno Windows; POSIX 0600 queda pendiente de CI POSIX. `verify`: BUILD
  SUCCESS, 54 pruebas, 0 fallos, 1 omitida.

## Servicio de almacenamiento y sesión

- [x] **T16 — Implementar serialización del encabezado autenticado** (25 min)  
  **RF:** R1, R2, R6 · **AC:** AC-1, AC-2  
  **Hecho cuando:** el ciphertext incluye tag, el encabezado declara su longitud
  exacta y los primeros 34 bytes generados se usan sin reconstrucción como AAD.
  **Evidencia:** `VaultStorage` cifra el payload usando el prefijo original de
  34 bytes como AAD y ensambla nonce/longitud del ciphertext+tag; `VaultHeaderTest`
  compara el encabezado byte a byte y `VaultStorageTest` crea/abre una bóveda.
  `./mvnw.cmd verify`: BUILD SUCCESS, 58 pruebas, 0 fallos, 1 omitida.

- [x] **T17 — Implementar creación exclusiva de bóveda** (25 min)  
  **RF:** R6, R10 · **AC:** AC-3, AC-11, AC-14  
  **Hecho cuando:** `create` valida path, obtiene lock, deriva con parámetros
  nominales, cifra payload vacío y persiste sin sobrescribir; fallos limpian
  clave/lock y no dejan sesión parcial.
  **Evidencia:** `VaultStorage.create` usa parámetros nominales, lock y
  `Files.createFile` exclusivo antes de escribir; errores cierran recursos y
  eliminan el archivo recién creado best-effort. Test cubre create/open vacío y
  rechazo de destino existente. `./mvnw.cmd verify`: BUILD SUCCESS, 58 pruebas.

- [x] **T18 — Implementar apertura completa y errores de formato** (25 min)  
  **RF:** R5, R6, R8 · **AC:** AC-7–AC-10, AC-17  
  **Hecho cuando:** `open` valida tamaño/encabezado/parámetros antes de KDF,
  autentica, parsea todo y solo entonces devuelve sesión; versión desconocida se
  distingue como no soportada y fallos limpian buffers.
  **Evidencia:** `VaultStorage.open` valida tamaño antes de cargar y el
  encabezado antes de derivar; autentica antes de parsear el payload completo.
  Pruebas cubren contraseña incorrecta y alteración de ciphertext. `verify`:
  BUILD SUCCESS, 58 pruebas, 0 fallos, 1 omitida.

- [x] **T19 — Implementar sesión y operaciones por ID** (25 min)  
  **RF:** R7 · **AC:** AC-4, AC-14, AC-16  
  **Hecho cuando:** sesión expone Vault y operaciones acordadas, ruta fija,
  `DerivedKey`, huella y lock; `close()` invalida sesión, descarta cambios,
  limpia secretos y libera lock sin guardar.
  **Evidencia:** `VaultSession` mantiene ruta/clave/huella/lock, delega CRUD a
  `Vault` y close borra clave y entradas, libera lock sin persistir. Test cubre
  save y descarte al cerrar. `verify`: BUILD SUCCESS, 58 pruebas.

- [x] **T20 — Probar create/open, autenticación y fallos sin datos parciales** (25 min)  
  **RF:** R6–R8 · **AC:** AC-3, AC-7–AC-11, AC-17  
  **Hecho cuando:** se prueba bóveda vacía, contraseña errónea y alteraciones con
  error genérico, formatos inválidos sin modificación, paths ausentes y ausencia
  de datos parciales.
  **Evidencia:** `VaultStorageTest` cubre bóveda vacía, contraseña errónea,
  ciphertext alterado, archivo no modificado por fallos, persistencia explícita y
  descarte al cerrar; header/parser existentes cubren entradas estructurales.
  `verify`: BUILD SUCCESS, 58 pruebas.

## Guardado atómico y concurrencia

- [x] **T21 — Implementar huella y detección de cambios externos** (25 min)  
  **RF:** R9, R10 · **AC:** AC-15  
  **Hecho cuando:** se conserva SHA-256 efímera tras abrir/crear/guardar y
  `save()` rechaza cambios externos manteniendo sesión abierta.
  **Evidencia:** sesión mantiene SHA-256 y compara antes de guardar y justo antes
  del movimiento atómico; test modifica el archivo desde fuera y verifica rechazo,
  conservación del archivo externo y sesión todavía abierta. `verify`: BUILD SUCCESS,
  58 pruebas, 0 fallos, 1 omitida.

- [x] **T22 — Implementar guardado cifrado a temporal privado** (25 min)  
  **RF:** R7, R9 · **AC:** AC-12, AC-13, AC-16  
  **Hecho cuando:** serializa/cifra estado completo, crea temporal en mismo
  directorio con permisos previos, escribe solo ciphertext, ejecuta
  `FileChannel.force(true)` y limpia buffers en éxito/error.
  **Evidencia:** `VaultSession.save` serializa y cifra en memoria, crea el
  temporal en el directorio destino, aplica permisos antes de escribir ciphertext,
  ejecuta `force(true)` y limpia buffers/temporal en `finally`. Test de fallo
  pre-move valida el archivo temporal cifrado y su limpieza. `verify`: BUILD
  SUCCESS, 62 pruebas, 0 fallos, 1 omitida.

- [x] **T23 — Implementar comprobación final y movimiento atómico** (25 min)  
  **RF:** R9, R10 · **AC:** AC-12, AC-13, AC-15  
  **Hecho cuando:** vuelve a comprobar la huella justo antes de `ATOMIC_MOVE`, no
  hace fallback no atómico, conserva archivo anterior si falla antes del
  reemplazo y elimina temporal best-effort.
  **Evidencia:** `save` revalida SHA-256 inmediatamente antes de `Files.move` con
  `ATOMIC_MOVE`; no hay fallback. Pruebas inyectan error antes de mover y
  `AtomicMoveNotSupportedException`, verificando archivo anterior intacto,
  sesión abierta y limpieza del temporal. Movimiento atómico real pasa en Windows.

- [x] **T24 — Probar guardado explícito, reintento y reemplazo atómico** (25 min)  
  **RF:** R7, R9, R10 · **AC:** AC-12–AC-16  
  **Hecho cuando:** pruebas cubren save/reopen, close sin save, reintento tras
  fallo, force antes de mover, archivo anterior intacto en error, cambio externo
  rechazado y temporal limpiado cuando sea posible.
  **Evidencia:** `VaultStorageTest` verifica save/reopen, close sin save,
  reintento luego de un fallo inyectado, hook posterior a `force(true)`, archivo
  anterior intacto, detección externa, manejo de atomic move no soportado y
  limpieza temporal. `./mvnw.cmd verify`: BUILD SUCCESS, 62 pruebas.

## Límites, documentación y cierre

- [x] **T25 — Probar tamaño máximo y rechazo antes de KDF** (25 min)  
  **RF:** R5 · **AC:** AC-6, AC-7  
  **Hecho cuando:** un archivo válido exactamente de 16.777.216 bytes se acepta
  con parámetros KDF mínimos y uno de 16.777.217 bytes se rechaza antes de leer
  contenido/derivar clave; rechazos usan fixtures que no requieren KDF.
  **Evidencia:** test abre archivo válido de exactamente 16.777.216 bytes,
  cifrado con parámetros Argon2id mínimos, y rechaza archivo sparse de
  16.777.217 bytes. `VaultKeyDeriver` de prueba confirma que el KDF no se invoca.
  `verify`: BUILD SUCCESS, 62 pruebas.

- [x] **T26 — Completar matriz de archivos inválidos** (25 min)  
  **RF:** R5, R8 · **AC:** AC-8–AC-10, AC-17  
  **Hecho cuando:** pruebas cubren magic/versión, truncamiento, bytes sobrantes,
  incoherencias de longitud, ciphertext menor que tag, contador imposible y
  parsing inválido; se comprueba que el archivo no se modifica.
  **Evidencia:** matriz en `VaultStorageTest`, `VaultHeaderTest` y
  `VaultPayloadCodecTest`/parser cubre magic/versión, truncamientos, trailing
  bytes, longitud inválida, ciphertext menor que tag, contador imposible, UTF-8,
  IDs y payload malformado; archivos inválidos conservan sus bytes. BUILD SUCCESS.

- [x] **T27 — Documentar formato binario en `docs/FORMAT.md`** (25 min)  
  **RF:** R1–R5 · **AC:** AC-1–AC-9  
  **Hecho cuando:** el documento describe exactamente v1, encabezado, AAD,
  fechas, payload, UTF-8, límites y errores; coincide con implementación y spec.
  **Evidencia:** `docs/FORMAT.md` describe encabezado/offsets, AAD exacto,
  ciphertext+tag, layout del payload, fechas, UTF-8 y rechazos v1; revisado contra
  `spec.md` y `VaultHeader`/codec. `./mvnw.cmd verify`: BUILD SUCCESS.

- [x] **T28 — Registrar cobertura y limitaciones de plataforma** (20 min)  
  **RF:** R9, R10 · **AC:** AC-12–AC-14  
  **Hecho cuando:** se documenta qué OS/filesystem se probó, resultados de
  permisos/atomicidad y cualquier assumption o limitación sin marcar como
  verificado lo que no se ejecutó.
  **Evidencia:** `docs/STATUS.md` registra Windows, ACL del usuario actual,
  movimiento atómico real y simulación de operación no soportada. POSIX 0600 no
  se ejecutó; symlink se omitió por `FileSystemException`; no se declaran probados.

- [x] **T29 — Ejecutar verificación completa y revisar higiene** (25 min)  
  **RF:** R1–R10 · **AC:** AC-1–AC-17  
  **Hecho cuando:** `./mvnw.cmd verify` termina correctamente; revisión confirma
  que no hay secretos en logs/excepciones/tests versionados y no se añadieron
  dependencias sin aprobación.
  **Evidencia:** `./mvnw.cmd verify`: BUILD SUCCESS, 62 pruebas, 0 fallos,
  1 omitida. Revisión de storage: sin logging; errores no incluyen contenido o
  contraseñas; fixtures son sintéticos; `pom.xml` mantiene únicamente JUnit 5 y
  Bouncy Castle 1.86 aprobados.

- [x] **T30 — Actualizar estado y cerrar Spec 002** (20 min)  
  **RF:** todos · **AC:** AC-1–AC-17  
  **Hecho cuando:** tareas y evidencias están completas, `docs/STATUS.md`
  registra resultados/pendientes y el cierre cumple los requisitos de la spec.
  No modificar `docs/THREAT_MODEL.md` sin petición expresa.
  **Evidencia:** tareas T01–T30 completadas; AC-1–AC-17 cubiertos por pruebas o
  limitación de plataforma registrada en estado. `docs/FORMAT.md` actualizado y
  verificación verde. Threat model sin cambios.
