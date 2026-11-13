# Plan técnico — Spec 002: vault-storage

**Estado:** aprobado para implementación tras la aprobación de la Spec 002 el 2026-11-12.  
**Alcance:** formato binario v1, modelo mínimo de entradas y persistencia local de bóvedas.  
**Predecesora:** Spec 001 (`crypto-core`), cerrada.

## 1. Contexto y decisiones aprobadas

El proyecto usa Java 21, Maven y JUnit 5. El paquete `crypto` está implementado e independiente; `CryptoService` ofrece generación de sal, derivación mediante `DerivedKey`, cifrado AES-GCM con nonce interno y descifrado autenticado. No se agregan dependencias.

La spec 002 define un archivo v1 con encabezado de 50 bytes; sus primeros 34 bytes exactos se usan como AAD, mientras nonce y longitud se autentican/validan según R2. El payload UTF-8 estricto se cifra como un bloque; máximo 16 MiB, escritura temporal forzada y reemplazo atómico, permisos privados y bloqueo lateral exclusivo.

### Aclaraciones acordadas para este plan

Estas decisiones amplían o concretan la propuesta inicial; ya están incorporadas en `spec.md` y aprobadas antes de iniciar implementación:

- Se implementa un modelo mínimo real en `cl.germain.vault.model`: `Entry` (ID de 16 bytes, fechas, sitio, usuario, contraseña y notas) y `Vault` como secuencia ordenada.
- La sesión ofrece operaciones por ID para agregar, obtener, listar en orden, actualizar y quitar, además de `save()` y `close()`. Búsqueda textual y comandos de usuario quedan para Spec 003.
- `Entry` contiene `createdAt` y `updatedAt`, serializados cada uno como `int64` con signo de milisegundos Unix UTC, big-endian. El modelo trunca los instantes del `Clock` a milisegundos para round-trip exacto: al crear son iguales y al actualizar solo cambia `updatedAt`.
- Al agregar/actualizar, el modelo copia los `char[]` de contraseña; el llamador conserva el arreglo original y debe borrarlo. Copias devueltas al consultar pertenecen al llamador y este debe borrarlas. Se borran copias internas al reemplazar, quitar o cerrar la sesión. Los temporales internos se limpian en `finally`.
- Antes de derivar una clave se validan estructura, longitud de archivo y parámetros KDF. Aperturas fallidas no exponen entradas parciales y limpian buffers bajo control de storage.
- Se vuelve a comprobar la huella SHA-256 inmediatamente antes del movimiento atómico. Es una reducción de la ventana, no elimina la carrera con procesos externos no compatibles; el bloqueo solo coordina sesiones compatibles.
- Canonización de rutas: resolver el padre existente mediante ruta real; si la bóveda existe, resolver también su ruta real. El lock se nombra a partir de la ruta canónica. Rechazar bóveda o lock que sean enlaces simbólicos. La equivalencia de mayúsculas depende del filesystem/plataforma.
- `create` rechaza destinos que sean enlaces simbólicos aunque el destino apuntado no exista. Probarlo cuando la plataforma permita crear symlinks; si no, usar suposición de JUnit y registrar la limitación.
- Probar el archivo válido de exactamente 16.777.216 bytes y el rechazo de un archivo de 16.777.217 bytes antes de leer el contenido/derivar clave. Usar parámetros KDF mínimos permitidos para las pruebas válidas de límite; archivos fabricados para rechazos por tamaño no requieren KDF.
- Verificar permisos en la plataforma de ejecución: POSIX 0600 en POSIX y ACL restringida al usuario actual en Windows, tanto después de crear como después de guardar. No declarar verificado lo que no se ejecutó; registrar limitaciones y pruebas condicionadas por plataforma.

## 2. Arquitectura propuesta

### Paquetes y responsabilidades

- `cl.germain.vault.model.Entry`: ID inmutable de 128 bits, fechas, campos de texto y propiedad de una copia interna de contraseña `char[]`. Acceso a contraseña mediante copia defensiva; reemplazo/cierre borra el arreglo poseído.
- `cl.germain.vault.model.Vault`: conserva una lista ordenada, asigna IDs con `SecureRandom`, asigna fechas con `Clock`, implementa operaciones por ID y cierre que borra secretos de sus entradas. Rechaza IDs repetidos.
- `cl.germain.vault.storage`: codec de encabezado/payload, validación estructural, coordinación con `CryptoService`, gestión de paths, lock, huella, permisos, temporales y reemplazo atómico.
- `VaultSession`: ruta canónica fija, `DerivedKey`, `Vault`, lock adquirido y huella del archivo. Expone el Vault y las operaciones por ID; `save()` explícito; `close()` descarta cambios no guardados y libera/borra recursos.

`storage` depende de `crypto` y `model`; `crypto` no depende de ninguno. El modelo no realiza I/O ni conoce el formato binario. Las excepciones públicas de storage no incluyen secretos, plaintext ni contenido de entradas; los fallos de autenticación se traducen conservando el error genérico definido en Spec 001.

### Camino de creación

1. Validar argumentos y resolver la ruta canónica del padre existente; rechazar destino existente o symlink.
2. Crear/asegurar archivo lateral de lock privado, rechazar symlink y adquirir bloqueo exclusivo.
3. Generar salt con `CryptoService`, derivar clave con parámetros nominales y crear `Vault` vacío.
4. Serializar contador cero y construir el prefijo de 34 bytes (magic, versión, parámetros y sal). Cifrar usando ese prefijo como AAD; después completar el encabezado con el nonce de `EncryptedData` y la longitud ciphertext+tag.
5. Persistir con creación exclusiva, permisos privados antes de escribir, `force(true)` y sin sobrescribir un destino concurrentemente creado.
6. Calcular huella y devolver sesión abierta. En cualquier fallo, cerrar clave/Vault y liberar lock; no dejar una sesión parcial.

### Camino de apertura

1. Canonizar ruta, rechazar symlink y adquirir lock antes de leer contenido.
2. Comprobar existencia, tipo de archivo y tamaño máximo antes de cargarlo. Leer/validar encabezado fijo y comprobar longitud declarada contra tamaño exacto.
3. Validar magic, versión y parámetros Argon2id antes de derivar. Versión desconocida produce formato no soportado sin descifrar.
4. Pasar los primeros 34 bytes originales del encabezado como AAD; usar el nonce leído en su campo y descifrar todo el ciphertext, y solo entonces parsear payload completo.
5. Validar límites/consistencia, UTF-8 estricto, campos obligatorios, contador, longitudes, IDs únicos y consumo exacto del payload. Construir el Vault solo tras validar o mantenerlo privado hasta completar validación.
6. Limpiar ciphertext/plaintext y buffers temporales bajo control de storage en `finally`; guardar huella y devolver sesión únicamente con éxito.

### Camino de guardado

1. Asegurar que la sesión sigue abierta; serializar el estado completo del Vault en buffers temporales controlados.
2. Comprobar huella actual frente a la última huella de sesión; cualquier diferencia rechaza el guardado sin cerrar sesión ni cambiar el archivo.
3. Construir el prefijo de 34 bytes y cifrar con él como AAD. Completar después los 16 bytes restantes del encabezado con nonce de `EncryptedData` y longitud ciphertext+tag. Comprobar tamaño máximo antes de persistir.
4. Crear temporal en el mismo directorio, asegurar permisos privados antes de escribir, escribir solo bytes cifrados y ejecutar `FileChannel.force(true)`.
5. Volver a comprobar huella inmediatamente antes de solicitar `ATOMIC_MOVE` de reemplazo. Si el movimiento atómico o permisos no se pueden asegurar, fallar cerrado, intentar borrar temporal y conservar la sesión abierta.
6. Tras éxito, actualizar huella. Limpiar serialización/plaintext y buffers temporales en `finally`.

La protección contra una escritura externa concurrente entre la segunda comprobación y el movimiento no es absoluta; los locks coordinan únicamente clientes compatibles.

## 3. Formato y límites de implementación

- Mantener exactamente el encabezado de 50 bytes de R1, big-endian, y usar los primeros 34 bytes originales como AAD. Alterar nonce provoca fallo GCM; la longitud declarada se valida contra el tamaño del archivo.
- Payload: contador uint32, seguido por ID 16 bytes y cuatro campos prefijados por longitud uint32 en el orden de R3. Contraseña de entrada se codifica UTF-8 estrictamente, sin NFC ni recorte.
- Usar `CharsetEncoder`/`CharsetDecoder` configurados para `REPORT`; no sustitución silenciosa. Codificación/decodificación y buffers de bytes se limpian cuando sean propiedad de storage.
- Nunca reservar buffers usando longitudes no validadas. Validar sumas con aritmética comprobada y contra bytes disponibles/tamaño máximo. Rechazar trailing bytes, truncamiento, overflow, contadores imposibles y ciphertext menor que 16 bytes.
- No imponer límites por campo adicionales al máximo total especificado.
- `Entry` debe preservar ID en round-trip; la creación normal del modelo genera IDs aleatorios con `SecureRandom`. Storage no regenera ni altera IDs.
- `docs/FORMAT.md` debe registrar el formato v1 y versión antes del cierre de la spec.

## 4. Sistema de archivos y seguridad

- Usar `NOFOLLOW_LINKS` donde aplique para detectar symlinks y rechazar bóveda/lock simbólicos. La ruta canónica del lock debe derivarse de la ruta canónica de bóveda para colapsar rutas relativas, `..` y enlaces en directorios.
- Crear archivo lock lateral con permisos privados y mantener `FileLock` hasta `close()`. El archivo puede persistir vacío; no escribir secretos en él.
- En POSIX usar atributos/permisos equivalentes a 0600 desde la creación, antes de cualquier contenido. En Windows aplicar ACL equivalente restringida al usuario actual. Si no es posible comprobar/asegurar privacidad, fallar cerrado.
- No hacer fallback a movimiento no atómico. Temporal en directorio destino, cifrado únicamente, `force(true)` antes del reemplazo y limpieza best-effort ante fallos.
- `create` debe ser exclusivo también frente a carreras de creación; nunca truncar un destino que aparezca concurrentemente.
- Las excepciones de I/O pueden incluir contexto estructural no sensible, nunca nombres de campos/valores, contraseña, clave, plaintext o contenido del Vault.

## 5. Pruebas y cobertura de aceptación

Pruebas JUnit 5 en los mismos paquetes que las clases probadas, con datos sintéticos no sensibles y directorios temporales. El `Clock` será fijo/controlado en pruebas del modelo. No incluir secretos reales ni archivos sensibles versionados.

| Área | Cobertura requerida | AC |
|---|---|---|
| Codec encabezado | 50 bytes, magic, versión, anchos, orden, endianess, AAD de 34 bytes exactos y alteraciones de cada campo del encabezado | AC-1, AC-2 |
| Ciclo de vida | Crear cifra inmediatamente; abrir vacío; crear existente y padre ausente fallan; save explícito, reopen y close descarta cambios | AC-3, AC-11, AC-16 |
| Modelo | IDs, fechas con Clock, orden, CRUD por ID, duplicados servicio/usuario, rechazo de ID duplicado, copias y borrado de contraseñas | AC-4, AC-16 |
| Campos y UTF-8 | Round-trip sin NFC/trim; campos requeridos vacíos; notas vacías; bytes UTF-8 malformados y sustitutos inválidos | AC-5 |
| Límites | Archivo válido exactamente 16.777.216 bytes admitido; 16.777.217 rechazado antes de contenido/KDF; parámetros mínimos en caso válido | AC-6, AC-7 |
| Parsing adversarial | Truncamiento, bytes extra, longitud inconsistente, overflow, contador imposible, ID repetido, campo incompleto y ciphertext < tag; sin retorno parcial | AC-8, AC-17 |
| Formatos y autenticación | Magic/version desconocidas sin modificación ni descifrado; versión no soportada; contraseña incorrecta y tamper con mismo error genérico | AC-9, AC-10 |
| Escritura | Reemplazo atómico, force antes de mover, archivo anterior preservado ante fallo, limpieza temporal y sin fallback no atómico | AC-12, AC-13 |
| Lock y huella | Segunda sesión falla; close libera lock; lock no contiene secretos; cambio externo impide save y conserva sesión/archivo externo; recheck justo antes de move | AC-14, AC-15 |
| Paths y symlinks | Rutas equivalentes comparten lock; padre real; symlinks de bóveda/lock rechazados; create rechaza symlink a destino ausente | requisitos aclarados |
| Permisos | POSIX 0600 o ACL Windows restringida al usuario, tras create y save; fallar cerrado cuando no puede asegurarse | AC-13 y R10 |

Las pruebas de symlink se condicionan con supuestos de JUnit cuando la plataforma/permisos no permitan crearlos; se registra la limitación. Los permisos se verifican únicamente en la familia de plataforma disponible en la ejecución. No se marcará cobertura de la otra familia sin ejecutarla en CI apropiado. Para atomicidad y `force`, diseñar puntos de inyección de fallos de filesystem/operaciones que permitan probar orden y conservación del destino sin debilitar la implementación de producción.

## 6. Secuencia de trabajo propuesta

1. Actualizar y aprobar `spec.md` con el modelo, CRUD mínimo, fechas, ownership de contraseñas, paths canónicos y comprobación doble de huella; confirmar que no cambia el Threat Model.
2. Diseñar e implementar `Entry` y `Vault`, con IDs aleatorios, fechas vía `Clock`, lista ordenada y borrado de copias de contraseña.
3. Implementar codec de payload con UTF-8 estricto, límites, validación completa y limpieza de buffers; añadir pruebas adversariales.
4. Implementar codec de encabezado v1, prefijo AAD original de 34 bytes y validación KDF; pruebas byte a byte y de autenticación/estructura.
5. Implementar lock, paths canónicos, symlink rejection, permisos y huella; pruebas de concurrencia y modificación externa.
6. Implementar `VaultSession` y servicio de storage para create/open/save/close, conectando crypto, modelo y codec.
7. Implementar persistencia temporal segura, `force(true)`, movimiento atómico y pruebas de fallos.
8. Completar pruebas de tamaño exacto y máximo + 1, y matriz de plataforma ejecutable.
9. Documentar formato exacto en `docs/FORMAT.md`; registrar estado y limitaciones observadas en `docs/STATUS.md` y tareas de spec.
10. Ejecutar `./mvnw.cmd verify` en Windows (o wrapper equivalente en otra plataforma), revisar que todas las pruebas pasen y cerrar solo tras AC-1–AC-17 y limitaciones documentadas.

## 7. Riesgos, limitaciones y preguntas

- Java puede mantener copias internas en `Cipher`, `SecretKeySpec`, codificadores y buffers del sistema; limpiar arreglos bajo control del código no garantiza borrado de todas las copias del runtime.
- La revalidación de huella justo antes del movimiento reduce, pero no elimina, la carrera con procesos que no respetan el archivo lock.
- La semántica de mayúsculas de rutas y ACL depende del filesystem y la plataforma.
- Symlinks pueden no estar disponibles en CI sin privilegios; usar suposiciones condicionadas y registrar plataforma.
- Atomic move, permisos Windows y sincronización durable del directorio varían por filesystem; no se debilita el requisito cuando no están soportados.
- El modelo y operaciones CRUD mínimas son ampliación respecto al alcance “CRUD fuera” de la propuesta original. Debe quedar aprobada en `spec.md` antes de implementar; búsquedas de texto y CLI no forman parte de este plan.
- Las fechas aprobadas son `createdAt` y `updatedAt`; no se añade semántica de expiración.

## 8. Cierre

La implementación de este plan no comienza hasta que la propuesta de Spec 002, incluida la ampliación del modelo mínimo, esté aprobada. El cierre exige cubrir AC-1–AC-17 o registrar limitaciones de plataforma revisadas, documentar el formato v1 en `docs/FORMAT.md`, actualizar `docs/STATUS.md` y completar `./mvnw.cmd verify`. `docs/THREAT_MODEL.md` no se modifica sin petición expresa.
