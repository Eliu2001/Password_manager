# Plan técnico — Spec 005: TOTP para entradas

**Estado de la spec:** aprobada por el usuario el 2026-11-15; implementación autorizada.  
**Alcance:** secretos TOTP asociados a entradas, comandos CLI y formato v2 con conversión explícita desde v1.  
**Predecesora:** Spec 004 (`password-generator`), cerrada.

## 1. Contexto y decisiones aprobadas

El proyecto usa Java 21, Maven y JUnit 5. `crypto` implementa Argon2id y
AES-256-GCM; `storage` serializa una bóveda como payload cifrado; `model` posee
las entradas y sus contraseñas; `cli` coordina comandos interactivos. La
implementación actual solo acepta y escribe formato v1. No se agregan
dependencias ni se modifica `docs/THREAT_MODEL.md`.

La Spec 005 añade cálculo TOTP RFC 6238 con HMAC-SHA-1, seis dígitos y periodo
fijo de 30 segundos; entrada manual Base32 (20–64 bytes decodificados); comandos
`set-totp`, `remove-totp`, `totp` y `upgrade`; y formato v2. Las bóvedas nuevas
se crean en v2. v1 se mantiene legible y guardable en v1, pero no admite gestión
TOTP hasta una conversión explícita confirmada. El payload v2 añade al final de
cada registro un uint32 big-endian y los bytes del secreto, donde longitud cero
significa ausencia. Se mantiene el límite de archivo completo de 16 MiB.

El secreto se pide dos veces sin eco. Base32 admite mayúsculas/minúsculas y
padding correcto opcional; no admite espacios ni separadores. El reemplazo de un
secreto existente y `upgrade` requieren confirmación exacta `sí`. El código se
imprime una vez por `CliConsole.printSecret`; los segundos restantes van a la
salida normal, sin refresco automático.

## 2. Arquitectura y responsabilidades

### `cl.germain.vault.totp`

- **`TotpGenerator` (nombre conceptual):** calcula códigos a partir de bytes del
  secreto y un `Clock` inyectable. Usa `javax.crypto.Mac` con `HmacSHA1`, contador
  `epochSecond / 30`, truncamiento dinámico RFC 4226 y formato de seis dígitos
  en un `char[]`. La fuente temporal de producción es UTC; las pruebas usan un
  reloj controlado. No depende de `cli`, `model`, `storage` ni `crypto`.
- **Decodificador Base32 (nombre conceptual):** valida la gramática aprobada,
  padding y bits de relleno canónicos de RFC 4648; convierte directamente desde
  `char[]` a `byte[]`, valida el rango 20–64 bytes y no construye un `String` con
  el secreto. Los bytes temporales se limpian en todos los caminos.
- No se implementa criptografía propia: HMAC proviene del JDK. No se incorpora
  una librería Base32 nueva sin aprobación.

### `cl.germain.vault.model`

- `Entry` incorpora un secreto TOTP opcional como `byte[]` poseído por la
  instancia. Al establecerlo copia el arreglo entrante; al consultarlo devuelve
  una copia propiedad del llamador. Reemplazar, quitar o cerrar la entrada borra
  la copia interna.
- `Vault` expone operaciones por ID para establecer/quitar el secreto, sin
  exponer mutadores directos de `Entry`; respeta entradas cerradas y traduce ID
  inexistente con el estilo de errores existente.
- El cierre y la eliminación de entradas limpian tanto la contraseña como el
  secreto TOTP que posea la entrada.

### `cl.germain.vault.storage`

- `VaultHeader` pasa de versión constante a parsear y ensamblar v1/v2, conservando
  el encabezado de 50 bytes, el AAD original exacto de 34 bytes y los mismos
  parámetros criptográficos. La versión que se cifra debe ser la misma que se
  usa para construir el AAD.
- Codec y parser reciben/derivan la versión de formato de la sesión. El codec v1
  conserva exactamente el payload existente; el codec v2 agrega longitud uint32
  y bytes TOTP al final de cada entrada. El parser valida longitud y bytes
  disponibles antes de reservar buffers y rechaza truncamientos, trailing data,
  secretos fuera de rango e IDs repetidos sin retornar un modelo parcial.
- `VaultSession` conserva la versión original. `save()` mantiene esa versión:
  sesiones v1 continúan serializando v1; sesiones v2 serializan v2. Las bóvedas
  nuevas se crean en v2.
- La conversión se modela como una operación explícita de sesión, por ejemplo
  `upgradeToV2()`: verifica sesión v1, prepara payload v2 y persiste mediante el
  mismo camino atómico. Solo después del reemplazo exitoso actualiza la versión
  de sesión y la huella. Un fallo deja el archivo v1 intacto y la sesión abierta
  en v1. La CLI pide confirmación antes de invocar esta operación.
- Se reutilizan sin relajación lock, chequeo de huella, permisos privados,
  temporal en el mismo directorio, `force(true)`, movimiento atómico y limpieza
  best-effort de Spec 002. La conversión no crea un segundo archivo de bóveda ni
  sobrescribe con fallback no atómico.

### `cl.germain.vault.cli`

- Extender `CliCommand.Type`, parser, ayuda y despacho, conservando la validación
  completa de opciones/IDs antes de abrir archivos y el gate común de consola.
- Ampliar la abstracción de consola solo si hace falta facilitar lectura,
  confirmación y prueba de limpieza; la producción seguirá usando
  `System.console()` y `readPassword`, sin fallback a stdin.
- `VaultCliCommands` coordina la contraseña maestra, apertura/cierre de sesión,
  prompts, operaciones del modelo, guardado y mensajes en español. No contiene
  lógica Base32 ni cálculo HOTP/TOTP.
- Para comandos TOTP en sesión v1, rechazar `set-totp`/`remove-totp` con
  instrucción genérica de ejecutar `upgrade`; no modificar ni guardar.

## 3. Flujos y propiedad de secretos

### Calcular un código

1. Validar comando, `--vault`, ID y ausencia de opciones extra antes del
   filesystem.
2. Pasar el gate de consola, pedir contraseña maestra sin eco y abrir la bóveda.
3. Rechazar de forma segura si la entrada no existe, no tiene secreto o la
   versión es v1. Obtener una copia del secreto TOTP y borrarla en `finally`.
4. Calcular `counter = floor(epochSecond / 30)` con el reloj de la operación; no
   aceptar tiempo anterior al epoch ni errores aritméticos. Construir exactamente
   seis dígitos en `char[]`.
5. Llamar una sola vez a `printSecret(code)`. Mostrar los segundos restantes
   (`30 - floorMod(epochSecond, 30)`) por salida normal; no refrescar ni reenviar
   el código. Borrar el código en `finally` y cerrar sesión siempre.

Si `Mac`/`SecretKeySpec` o el proveedor JDK crea copias internas de la clave,
Java no ofrece garantía de borrado total de esas copias. Se limpian todos los
arreglos bajo control de la aplicación y no se promete eliminación de memoria
fuera de su control.

### Asociar o retirar TOTP

1. Validar sintaxis e ID antes de abrir la bóveda y pasar el gate de consola.
2. Pedir contraseña maestra y abrir la sesión; si es v1, informar que debe
   completarse `upgrade` y salir sin modificarla.
3. En `set-totp`, pedir dos `char[]` Base32 sin eco. Comparar sin construir
   `String`; ante discrepancia cancelar. Decodificar/validar la primera, borrar
   ambas lecturas y temporales propios en todos los caminos. Si ya existe secreto,
   pedir confirmación exacta `sí` antes de reemplazar.
4. El modelo copia el secreto validado; limpiar el arreglo temporal del CLI.
   Llamar `save()` y reportar éxito solo tras guardar.
5. En `remove-totp`, error si no existe secreto; pedir confirmación exacta `sí`,
   retirar/limpiar copia interna y guardar únicamente ante confirmación.
6. Cerrar sesión y borrar contraseña maestra y secretos temporales aun ante
   cancelación, excepción o fallo de persistencia.

### Conversión v1 → v2

1. Parsear `upgrade --vault <ruta>` y pasar gate de consola.
2. Pedir contraseña maestra, abrir/validar completamente v1 y obtener bloqueo
   exclusivo conforme a Storage.
3. Pedir confirmación exacta `sí`. Otra respuesta cierra sin save ni mutación.
4. Serializar en memoria a v2 y comprobar límite de 16 MiB antes de persistir.
5. Reutilizar la operación atómica de Storage. Revalidar huella antes del move;
   si falla KDF, parsing, serialización, permisos, huella o atomic move, preservar
   el archivo anterior y dejar la sesión coherente/reintentable.
6. Informar éxito solo después de movimiento y actualización de versión/huella.

Un cambio externo concurrente no cooperativo puede competir con el último
chequeo y el move, igual que en Spec 002; el lock solo coordina clientes
compatibles. No se afirma atomicidad donde el filesystem no la soporte.

## 4. Compatibilidad de formato y documentación

- Mantener v1 byte a byte: encabezado/serialización históricos, interpretación
  del payload y capacidad de abrir y guardar bóvedas v1 sin TOTP.
- v2 utiliza el mismo encabezado de 50 bytes y los mismos campos excepto
  versión `2`; los primeros 34 bytes reales, con versión 2, son el AAD del
  cifrado. No se intenta parsear un payload como versión equivocada.
- En v2 cada entrada conserva los campos v1 en el mismo orden y luego añade el
  uint32 big-endian de longitud TOTP y los bytes secretos; cero significa
  ausente. Las longitudes nunca justifican reservas antes de validar tamaño
  disponible, rango 20–64 (si no es cero), y máximo del payload.
- `docs/FORMAT.md` documentará ambas versiones y marcará v2 como formato de
  creación actual. No se borra la descripción normativa de v1.
- No tocar `docs/THREAT_MODEL.md`. Actualizar `docs/STATUS.md` solo al informar
  estado/verificación de implementación y cierre, no como parte de este plan.

## 5. Pruebas y trazabilidad de aceptación

Las pruebas usan JUnit 5, vectores públicos RFC 6238, datos sintéticos y
directorios temporales. No se incluirán secretos reales ni archivos de bóveda
sensibles versionados.

| Cobertura | Criterios |
|---|---|
| Algoritmo: vectores RFC SHA-1, rollover de periodo, seis dígitos incluyendo ceros iniciales, epoch y rechazo de tiempos negativos | AC-1, AC-2 |
| Fuente temporal controlable y exactitud de los segundos restantes en límites de periodo | AC-1, AC-2 |
| Base32: mayúsculas/minúsculas, padding correcto opcional, padding interno/excesivo, caracteres inválidos, bits no canónicos, longitudes 20/64 y fuera de límites | AC-3 |
| Modelo: secreto ausente/presente, copias defensivas, reemplazo, retiro, borrado al quitar/cerrar, operaciones por ID | AC-4, AC-6 |
| Storage v1: fixtures sintéticos v1 abren/guardan sin cambio de versión ni reinterpretación; versión/bytes mantienen compatibilidad | AC-8, AC-9 |
| Storage v2: header/AAD versión 2, round-trip con y sin secreto, campos/IDs/orden preservados, rechazo de truncamiento, longitud inválida, trailing bytes y tampering | AC-4, AC-9 |
| CLI/parser: sintaxis, opciones repetidas/desconocidas/incompletas, IDs y rechazo previo a filesystem; ayuda actualizada | AC-5, AC-7 |
| `set-totp`: lectura doble, discrepancia, Base32 inválido, reemplazo confirmado/cancelado, bóveda v1, save/error y limpieza observable | AC-3, AC-5, AC-6 |
| `remove-totp`: sí/no/EOF, secreto ausente, v1, save/error, sin modificación al cancelar | AC-5, AC-6, AC-8 |
| `totp`: salida privada única, segundos por salida normal, sin código en stdout/error, casos ID inexistente/sin secreto/v1, limpieza y errores | AC-2, AC-5–AC-7 |
| `upgrade`: requiere v1, confirmación exacta, cancelación sin cambios, preservación de IDs/fechas/campos, tamaño máximo, cambio externo, fallo atómico/permiso y reopen en v2 | AC-8, AC-9 |
| Revisión de dependencias, independencia de `totp`, ausencia de modificación del Threat Model y verificación completa | AC-10 |

Los puntos de inyección existentes para KDF, consola y movimiento atómico se
reutilizarán o ampliarán con visibilidad limitada a pruebas. No se debilitará el
camino de producción para hacer deterministas las pruebas.

## 6. Secuencia de trabajo propuesta

1. **Inventario y pruebas de compatibilidad:** fijar fixtures v1 sintéticos,
   revisar API exacta de model/storage/CLI y definir regresión v1 antes de tocar
   codecs. Trazabilidad: AC-8, AC-9.
2. **API TOTP y pruebas RFC:** implementar cálculo HMAC-SHA-1, `Clock` y salida
   `char[]`; probar vectores, periodos y errores. Trazabilidad: AC-1, AC-2.
3. **Base32 seguro:** decodificador sobre `char[]`, política de padding/canonical
   bits, límites 20–64 y limpieza; pruebas adversariales. Trazabilidad: AC-3.
4. **Modelo de secretos TOTP:** ampliar Entry/Vault y el cierre con copias
   defensivas, mutación por ID y limpieza; pruebas de ciclo de vida. Trazabilidad:
   AC-4, AC-6.
5. **Versionar header y sesión:** parsear v1/v2 y conservar versión por sesión;
   nuevas creaciones v2; `save()` preserva la versión abierta. Añadir pruebas de
   encabezado/AAD y compatibilidad v1. Trazabilidad: AC-8, AC-9.
6. **Codec/parser v2:** añadir campo TOTP por entrada, validación de tamaño y
   fallos cerrados; round-trip y matriz adversarial; mantener payload v1 exacto.
   Trazabilidad: AC-4, AC-9.
7. **Conversión explícita:** añadir operación de sesión/storage que transforma
   v1 a v2 a través del guardado atómico existente; probar cancelación externa,
   tamaño, fallo y preservación del original. Trazabilidad: AC-8, AC-9.
8. **Parser y CLI:** añadir comandos/opciones/ayuda y flujos de secretos,
   confirmaciones, canales, mensajes y estados; probar consola ausente, errores y
   limpieza. Trazabilidad: AC-5–AC-7.
9. **Documentación de formato:** actualizar `docs/FORMAT.md` para v2 y conservar
   la especificación completa de v1. Trazabilidad: AC-9.
10. **Revisión integral:** arquitectura y dependencias, secretos/salidas,
    preservación de v1, tests completos y `./mvnw.cmd verify`; registrar resultado
    y limitaciones en `docs/STATUS.md`. Trazabilidad: AC-1–AC-10.

Cada cambio que afecte al formato se implementará con tests de compatibilidad y
actualización de documentación dentro de la misma etapa; no cerrar la spec si
queda algún AC sin evidencia.

## 7. Riesgos y limitaciones

- **Incompatibilidad v1:** un byte de payload adicional solo es válido en v2.
  La versión debe seleccionarse desde header autenticado y no inferirse del
  contenido; pruebas congeladas de v1 previenen regresiones.
- **AAD por versión:** el byte de versión pertenece a los primeros 34 bytes; al
  guardar o convertir v2 debe usarse el prefijo original/construido con versión
  2 para cifrar. Un prefijo v1 con ciphertext v2 fallará autenticación.
- **Conversión y write path:** no usar export/import a otra ruta ni fallback
  no-atómico. La operación debe compartir lock, huella, permisos y move atómico
  de Storage; cualquier limitación descubierta requiere parar y revisar antes de
  debilitar requisitos.
- **Límite de archivo:** secretos agregan bytes por entrada; v2 o su conversión
  pueden exceder 16 MiB y deben fallar antes del reemplazo.
- **Memoria Java:** limpiar buffers bajo control del programa reduce exposición,
  pero `Mac`, proveedor criptográfico y runtime pueden mantener copias internas.
- **Canal de código:** un fallo de consola puede ocurrir tras imprimir un prefijo;
  no hay rollback fiable. No reintentar ni redirigir el código.
- **Compatibilidad de la API de consola:** `readPassword` entrega `char[]`, que
  sirve para evitar `String`; confirmar que dobles de test verifican borrado de
  buffers propios sin retener secretos reales.
- **Plataformas:** atomic move, permisos ACL/POSIX y symlinks dependen del
  entorno; mantener pruebas existentes y registrar cualquier limitación sin
  relajar el requisito.

No se identifican decisiones funcionales abiertas. Si durante implementación
aparece una incompatibilidad entre conservar v1, la mutabilidad de la sesión y
la conversión atómica, se detiene el trabajo y se solicita aclaración; no se
cambia el formato ni el modelo de amenazas por iniciativa propia.

## 8. Cierre

El cierre exige pruebas para AC-1–AC-10, compatibilidad v1, formato v2
documentado en `docs/FORMAT.md`, ausencia de dependencias no aprobadas, ninguna
modificación no autorizada a `docs/THREAT_MODEL.md` y `./mvnw.cmd verify` en
verde. Registrar pruebas, resultado y limitaciones en `docs/STATUS.md`. Este
plan no autoriza cambios al modelo de amenazas ni amplía el alcance aprobado.
