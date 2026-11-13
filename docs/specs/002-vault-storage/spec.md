# Spec 002 — vault-storage

**Estado:** aprobada el 2026-11-12; implementación autorizada.  
**Fecha:** 2026-11-12  
**Predecesora:** `docs/specs/001-crypto-core/spec.md`, cerrada.

## 1. Objetivo

Definir el formato binario v1 de la bóveda y la capa `storage` que la crea,
abre, persiste y guarda durante una sesión. La capa reutiliza `crypto` para
derivar la clave y cifrar/descifrar el payload completo; no implementa
primitivas criptográficas propias.

## 2. Alcance

**Dentro:**

- Formato de archivo v1, encabezado, codificación del payload y validación.
- Crear una bóveda vacía, abrir una bóveda existente y guardar cambios de forma
  explícita.
- Sesión de bóveda con ruta fija, clave derivada y ciclo de vida de secretos.
- Modelo mínimo en memoria (`Entry` y `Vault`) y operaciones por ID necesarias
  para agregar, obtener, listar, actualizar y quitar entradas.
- Límites de tamaño, permisos privados, bloqueo concurrente y escritura
  atómica.

**Fuera:**

- CLI y ubicación predeterminada de archivos; storage recibe una ruta explícita.
- Reglas de negocio de credenciales, búsquedas, filtros, presentación y comandos
  de usuario. Una spec posterior definirá esas funciones; esta spec solo incluye
  las operaciones mínimas por ID descritas en R7.
- Generador de contraseñas, autobloqueo, TOTP, sincronización, backups,
  migración automática de formatos y recuperación/reparación de archivos.
- Definición del modelo de amenazas, que permanece en `docs/THREAT_MODEL.md`.

## 3. Requisitos

### R1 — Formato exterior versionado

El archivo v1 tiene un encabezado fijo de **50 bytes**, seguido inmediatamente
por el ciphertext y su etiqueta GCM. El encabezado, en este orden, contiene:

| Campo | Tamaño | Codificación |
|---|---:|---|
| Magic `PVLT` | 4 bytes | ASCII |
| Versión de formato (`1`) | 2 bytes | uint16 big-endian |
| Memoria Argon2id, en KiB | 4 bytes | uint32 big-endian |
| Iteraciones Argon2id | 4 bytes | uint32 big-endian |
| Paralelismo Argon2id | 4 bytes | uint32 big-endian |
| Sal | 16 bytes | bytes |
| Nonce AES-GCM | 12 bytes | bytes |
| Longitud de ciphertext **incluida la etiqueta** | 4 bytes | uint32 big-endian |

La suma del encabezado es 50 bytes. La versión v1 fija Argon2id versión 19,
AES-256-GCM y etiqueta de 128 bits; no se agregan identificadores de algoritmo
separados en esta versión.

### R2 — Encabezado autenticado

Los **primeros 34 bytes exactos** leídos del encabezado —magic, versión,
parámetros Argon2id y sal— se entregan como AAD a AES-GCM, sin reconstruirlos a
partir de campos parseados. Los 12 bytes siguientes son el nonce generado
internamente por `CryptoService`; cualquier cambio en él hace fallar la
autenticación GCM. Los 4 bytes finales declaran la longitud de ciphertext
incluida la etiqueta; se validan contra el tamaño exacto del archivo antes de
descifrar. Así, cualquier alteración del encabezado causa rechazo: los primeros
34 bytes por autenticación, el nonce por autenticación y la longitud por
validación estructural (o autenticación si cambia el ciphertext procesado).

### R3 — Payload cifrado

Todo el contenido de la bóveda se serializa en un único payload y se cifra como
un solo bloque AES-GCM. El payload v1 es:

1. Cantidad de entradas: uint32 big-endian.
2. Para cada entrada, en el orden conservado:
   - ID aleatorio de 128 bits: 16 bytes.
   - Fecha de creación (`createdAt`): entero de 64 bits con signo, milisegundos
     Unix UTC, big-endian.
   - Fecha de última actualización (`updatedAt`): entero de 64 bits con signo,
     milisegundos Unix UTC, big-endian.
   - Sitio/servicio: longitud uint32 big-endian seguida por bytes UTF-8.
   - Usuario: longitud uint32 big-endian seguida por bytes UTF-8.
   - Contraseña: longitud uint32 big-endian seguida por bytes UTF-8.
   - Notas: longitud uint32 big-endian seguida por bytes UTF-8.

Las longitudes cuentan **bytes**, no caracteres. Sitio/servicio, usuario y
contraseña son obligatorios y no vacíos. Las notas pueden estar ausentes o
vacías; ambos casos se codifican como longitud cero. No se recortan ni normalizan
los campos. La codificación y decodificación UTF-8 son estrictas: secuencias
malformadas se rechazan, nunca se reemplazan silenciosamente.

La contraseña de una entrada se maneja como `char[]` en memoria, no como
`String`, conforme a las reglas del proyecto. Su codificación en archivo no
aplica NFC: debe preservar exactamente el valor ingresado. La contraseña maestra
continúa siguiendo las reglas de Spec 001.

### R4 — Identidad y orden de entradas

El modelo genera IDs de 16 bytes usando `SecureRandom`; storage los conserva sin
alteración y rechaza IDs repetidos en el mismo payload. Se permiten múltiples
entradas con la misma combinación de sitio/servicio y usuario. El orden de la
lista se conserva al guardar y abrir. `createdAt` y `updatedAt` se asignan usando
un `Clock` inyectable: al crear una entrada ambas fechas son iguales; al
actualizarla solo cambia `updatedAt`. El modelo trunca los instantes del reloj a
milisegundos, de acuerdo con la precisión de la representación persistida.

### R5 — Tamaño, longitudes y parsing seguro

- El tamaño máximo del **archivo completo**, encabezado incluido, es **16 MiB**.
- El lector comprueba el tamaño antes de cargar el archivo y no asigna memoria
  basada en longitudes no validadas.
- El ciphertext declarado mide como mínimo 16 bytes (etiqueta GCM) y debe
  terminar exactamente al final del archivo. Se rechazan truncamientos,
  bytes sobrantes, longitudes incoherentes, contadores imposibles, overflow,
  campos incompletos, IDs repetidos y UTF-8 inválido.
- Antes de derivar la clave, los parámetros no confiables del encabezado deben
  validarse contra los límites de Spec 001: memoria 19 456–262 144 KiB,
  iteraciones 2–10 y paralelismo 1–4.
- No hay un límite por campo adicional al límite total del archivo y las
  capacidades de la plataforma.

### R6 — Creación y apertura

- Storage recibe un `Path` explícito; no elige una ubicación ni crea directorios
  padre. Si el directorio padre no existe, la operación falla.
- `create(path, masterPassword)` falla si el destino ya existe. Crea una bóveda
  vacía —payload con contador cero—, la cifra y la persiste inmediatamente con
  los parámetros nominales Argon2id (65 536 KiB, 3 iteraciones, paralelismo 1).
  Si tiene éxito, devuelve una sesión abierta.
- `open(path, masterPassword)` lee y valida el encabezado, deriva la clave,
  autentica y descifra el payload completo, lo parsea y solo entonces devuelve
  una sesión. No entrega entradas parciales.
- La sal se genera con `CryptoService.generateSalt()` y el nonce con el cifrado
  de `CryptoService`; nunca se aceptan nonces suministrados por el llamador.
- El AAD entregado al cifrado/descifrado son los primeros 34 bytes originales
  del encabezado conforme a R2.
- No se guarda ni retiene la contraseña maestra. El llamador conserva su
  `char[]` y es responsable de borrarlo, como establece Spec 001.

### R7 — Sesión y ciclo de vida

- Una sesión mantiene el `DerivedKey` necesario para guardar cambios, nunca la
  contraseña maestra, y queda vinculada al `Path` con que se creó o abrió. No
  hay “Guardar como” en esta spec.
- La sesión expone el `Vault` en memoria y operaciones por ID para agregar,
  obtener, listar en orden, actualizar y quitar entradas. La modificación de
  entradas se realiza mediante esas operaciones, no mutando directamente una
  `Entry`. La sesión también expone `save()` y `close()`.
- El modelo copia los arreglos `char[]` de contraseña recibidos al agregar o
  actualizar; el llamador conserva la propiedad de los arreglos que entrega y
  debe borrarlos. Al consultar una contraseña, el modelo devuelve una copia que
  el llamador debe borrar. El modelo borra sus copias al reemplazarlas, quitar
  una entrada o cerrar la sesión. Los temporales internos se borran en bloques
  `finally`.
- El modelo mínimo contiene `Entry` (ID, fechas, sitio/servicio, usuario,
  contraseña y notas) y `Vault` como secuencia ordenada. El `Clock` es
  inyectable para pruebas. No se incluyen búsquedas ni reglas de negocio o
  presentación.
- Guardar es explícito. `close()` no guarda automáticamente: descarta cambios
  no guardados, invalida la sesión, borra el `DerivedKey` y los arreglos
  `char[]` de contraseñas de entradas que la sesión posea, y libera el bloqueo.
- Si guardar falla antes del reemplazo atómico, la sesión permanece abierta con
  los cambios en memoria para permitir reintentar; el archivo anterior se
  conserva.

### R8 — Versiones y fallos de apertura

Una magic incorrecta, versión desconocida, encabezado inválido, truncamiento,
longitud incoherente o payload inválido causa el rechazo de la operación. No se
intenta reparar, migrar ni modificar automáticamente el archivo. La versión
desconocida se informa como formato no soportado; no se intenta descifrarla como
v1.

Una contraseña maestra incorrecta y cualquier fallo de autenticación GCM usan
el error criptográfico genérico definido en Spec 001. Los errores estructurales
pueden distinguirse de los de autenticación, pero ningún error, causa o log
puede incluir contraseña, clave, plaintext ni contenido de entradas.

### R9 — Guardado seguro y atómico

- El reemplazo usa un archivo temporal en el mismo directorio, con permisos
  privados establecidos antes de escribir. El temporal contiene solo el archivo
  cifrado, nunca plaintext.
- Antes del reemplazo se fuerza el contenido del temporal al almacenamiento con
  `FileChannel.force(true)` y se solicita un reemplazo atómico.
- Si no se puede asegurar el reemplazo atómico, el guardado falla cerrado: no
  hay fallback no atómico y la bóveda anterior permanece intacta. Se intenta
  eliminar el temporal ante cualquier fallo.
- `save` reemplaza el archivo asociado a la sesión. `create` nunca sobrescribe
  un archivo existente.
- Tras un guardado exitoso se actualiza la huella de la sesión. La sincronización
  durable del cambio de nombre de directorio depende del sistema operativo y no
  se promete uniformemente.

### R10 — Permisos y concurrencia

- En Linux/macOS, el archivo de bóveda, archivo temporal y archivo de bloqueo
  deben tener permisos POSIX equivalentes a **0600**. En Windows se aplicará una
  ACL equivalente, restringida al usuario actual.
- Si no se pueden asegurar permisos privados, la operación falla; no se guarda
  con permisos más amplios.
- Una sesión adquiere un bloqueo exclusivo de sistema operativo sobre un archivo
  lateral estable asociado a la ruta (por ejemplo, `nombre-de-boveda.lock`) y
  lo mantiene hasta `close()`. El archivo lateral puede permanecer después de
  cerrar; el bloqueo del sistema operativo se libera.
- Si el bloqueo no puede adquirirse, crear/abrir falla. El bloqueo protege
  frente a otras sesiones compatibles; además, storage conserva temporalmente
  una huella SHA-256 del archivo completo y rechaza guardar si detecta que el
  archivo cambió externamente desde su última lectura/guardado. Esa huella no se
  persiste y no es un hash de contraseña.

## 4. Criterios de aprobación

Cada criterio debe tener al menos una prueba automatizada, salvo la revisión de
permisos/atomicidad cuando una plataforma de CI no permita demostrarlo; en ese
caso se registra la plataforma y la limitación, sin debilitar el requisito.

| ID | Criterio |
|---|---|
| AC-1 | El serializador produce el encabezado v1 de 50 bytes con magic, anchos, orden y endianess exactos. |
| AC-2 | Se usan como AAD los primeros 34 bytes originales; alterar cualquier byte del encabezado (incluidos nonce y longitud) hace que la apertura falle por autenticación GCM o validación estructural. |
| AC-3 | Crear produce inmediatamente una bóveda vacía cifrada y abrirla devuelve cero entradas. |
| AC-4 | Guardar y volver a abrir conserva IDs, fechas, campos y orden; admite duplicados sitio/usuario y rechaza IDs duplicados. El modelo asigna fechas con el `Clock` y actualiza `updatedAt` al editar. |
| AC-5 | Los campos se codifican en UTF-8 estricto sin normalizar ni recortar; los campos obligatorios vacíos o datos UTF-8 inválidos se rechazan. |
| AC-6 | El tamaño total de 16 MiB se acepta; un archivo mayor se rechaza antes de cargar/parsear su payload. |
| AC-7 | Se rechazan parámetros Argon2id fuera de los rangos aprobados antes de ejecutar la KDF. |
| AC-8 | Truncamiento, bytes sobrantes, longitudes incoherentes, contador imposible, overflow y ciphertext menor que la etiqueta se rechazan sin datos parciales. |
| AC-9 | Magic o versión desconocidas se rechazan sin descifrar, reparar ni modificar el archivo. |
| AC-10 | Contraseña maestra incorrecta y alteración autenticada fallan con el mismo error genérico de Spec 001, sin causa sensible. |
| AC-11 | `create` no sobrescribe un destino existente; storage no crea directorios padre. |
| AC-12 | Un guardado exitoso reemplaza atómicamente el archivo, fuerza el temporal antes del reemplazo y conserva el anterior ante fallo. |
| AC-13 | Si el filesystem no soporta movimiento atómico o permisos privados, la operación falla cerrada y limpia el temporal cuando sea posible. |
| AC-14 | Una segunda sesión no adquiere el bloqueo exclusivo; el bloqueo se libera al cerrar y el archivo lateral no contiene secretos. |
| AC-15 | Una modificación externa detectada por la huella impide guardar y conserva tanto el archivo externo como la sesión abierta. |
| AC-16 | Guardar solo ocurre por llamada explícita; cerrar sin guardar descarta cambios y borra clave y arreglos de contraseñas que pertenezcan a la sesión. |
| AC-17 | Fallos de lectura, descifrado o parsing no exponen entradas parciales y limpian los buffers temporales bajo control de storage. |

## 5. Decisiones resueltas

1. La Spec 002 cubre formato y persistencia (crear, abrir y guardar); no incluye
   CRUD de entradas ni CLI.
2. Formato binario v1, magic `PVLT`, versión 1 y encabezado fijo de 50 bytes
   descrito en R1; todos los enteros son big-endian.
3. Los primeros 34 bytes exactos del encabezado son AAD. El nonce lo genera
   `CryptoService` internamente y queda protegido por la autenticación GCM; la
   longitud se valida contra el tamaño exacto del archivo. Alterar cualquier
   byte del encabezado causa rechazo. El payload completo se cifra como un único
   bloque.
4. Tamaño máximo del archivo completo: 16 MiB.
5. Campos por entrada: sitio/servicio, usuario, contraseña y notas; los tres
   primeros son obligatorios y no vacíos. UTF-8 estricto, sin recorte ni NFC;
   notas ausentes se codifican vacías.
6. Entradas duplicadas por sitio/usuario son válidas. El modelo genera IDs
   aleatorios de 128 bits con `SecureRandom`; storage preserva IDs y rechaza
   repeticiones. Se conserva el orden. El modelo mínimo implementa operaciones
   por ID y fechas `createdAt`/`updatedAt` mediante `Clock` inyectable.
7. Creación produce una bóveda vacía persistida inmediatamente; crear falla si
   el archivo existe. El guardado es explícito y la ruta queda fija por sesión.
8. La sesión conserva solo `DerivedKey`, borra clave y contraseñas de entrada al
   cerrarse, y descarta cambios no guardados. Un fallo de guardado deja abierta
   la sesión para reintentar.
9. Rutas explícitas, sin creación automática de directorios padre. Rechazo sin
   modificación ante versiones/formato inválidos y sin migración automática.
10. Límite POSIX 0600/ACL equivalente en Windows; si no puede asegurarse,
    fallar. Uso de archivo lateral para bloqueo exclusivo, huella SHA-256
    efímera para detectar cambios externos, temporal en el mismo directorio,
    `force(true)` y reemplazo atómico sin fallback.
11. El modelo copia y limpia contraseñas de entrada según R7; las copias
   entregadas al consultar pertenecen al llamador. Búsquedas y reglas de negocio
   quedan para una spec posterior.
12. Fechas del payload: `createdAt` y `updatedAt`, cada una como int64 con signo
   de milisegundos Unix UTC, big-endian; al crear son iguales y solo `updatedAt`
   cambia al actualizar. El modelo trunca los instantes asignados a milisegundos.
13. Se heredan de Spec 001 Argon2id v19, rangos KDF, parámetros nominales,
   AES-256-GCM, sal de 16 bytes, nonce aleatorio de 12 bytes, etiqueta de 128
   bits, responsabilidad del llamador de borrar la contraseña maestra y
   errores genéricos de autenticación.

## 6. Cierre

La Spec 002 se considerará cerrada solo cuando:

- AC-1–AC-17 estén probados o la limitación de plataforma esté documentada y
  revisada sin relajar los requisitos de seguridad.
- `docs/FORMAT.md` documente exactamente el formato v1 y su número de versión.
- Tareas de implementación y verificación estén completas y
  `./mvnw.cmd verify` termine correctamente.
- `docs/STATUS.md` registre resultados y pendientes. `docs/THREAT_MODEL.md` no
  se modifica como parte de esta spec sin solicitud expresa del usuario.

El cierre no inicia automáticamente otra spec; el siguiente alcance se decide
por separado, conforme a la constitución del proyecto.
