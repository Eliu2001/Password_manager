# Spec 005 — TOTP para entradas

**Estado:** aprobada el 2026-11-15; implementación autorizada.  
**Predecesora:** `docs/specs/004-password-generator/spec.md`, cerrada.

En las confirmaciones existentes, solo las respuestas exactas `sí` y `si`
(minúsculas, sin espacios adicionales) cuentan como afirmativas; cualquier otra
entrada cancela o rechaza la operación.

## 1. Objetivo

Permitir almacenar dentro de la bóveda el secreto TOTP de una cuenta y consultar
su código temporal desde la CLI. Los secretos se almacenan únicamente cifrados
como parte del payload de la bóveda; no se implementa un segundo factor para
desbloquear la bóveda.

## 2. Alcance

**Dentro:**

- Cálculo TOTP conforme a RFC 6238, con HMAC-SHA-1, seis dígitos y periodo de
  30 segundos.
- Ingreso manual de secretos Base32, asociación y retirada de un secreto TOTP
  de una entrada existente mediante comandos por ID.
- Presentación del código y del tiempo restante exclusivamente por la consola
  interactiva privada.
- Formato de bóveda v2 y operación explícita para convertir una bóveda v1 a v2.
- Limpieza de arreglos y buffers secretos bajo propiedad de la aplicación.

**Fuera:**

- TOTP como segundo factor para desbloquear la bóveda.
- Importación de QR o URI `otpauth://`, generación de secretos para configurar
  cuentas nuevas, algoritmos o parámetros TOTP configurables.
- Edición general de entradas, portapapeles, sincronización, recuperación de
  secretos y cambios a las primitivas criptográficas.
- Modificación de `docs/THREAT_MODEL.md` sin solicitud expresa.

## 3. Interfaz

```text
Main set-totp --vault <ruta> --id <id>
Main remove-totp --vault <ruta> --id <id>
Main totp --vault <ruta> --id <id>
Main upgrade --vault <ruta>
```

Todos los comandos requieren consola interactiva y contraseña maestra mediante
los flujos existentes. `set-totp` pide el secreto Base32 sin eco y confirma su
asociación; `remove-totp` requiere confirmación exacta `sí` o `si`; `totp` muestra el
código vigente y segundos restantes. `upgrade` realiza una conversión explícita
de v1 a v2, sin migrar automáticamente al abrir o guardar.

## 4. Requisitos

### R1 — Cálculo TOTP

- La implementación sigue RFC 6238 usando HMAC-SHA-1, seis dígitos decimales y
  un paso de 30 segundos. El contador se calcula a partir del tiempo Unix UTC.
- El tiempo proviene de un `Clock` inyectable para pruebas. Se rechazan tiempos
  anteriores al epoch Unix y cualquier condición aritmética inválida sin emitir
  códigos parciales.
- La implementación no crea ni registra el código en logs, excepciones ni
  mensajes de error. El código se representa como `char[]` hasta su impresión.
- La prueba de conformidad cubre los vectores de RFC 6238 aplicables a SHA-1.

### R2 — Secreto Base32

- La CLI recibe el secreto manualmente mediante lectura sin eco. No acepta
  secretos en argumentos, variables de entorno ni stdin.
- La entrada debe ser Base32 RFC 4648; se aceptan mayúsculas o minúsculas y
  padding `=` opcional, correcto y solo al final. Se rechazan espacios,
  separadores, caracteres ajenos al alfabeto y padding incorrecto. La CLI
  compara dos lecturas `char[]`; si no coinciden, cancela sin cambios. La
  decodificación no requiere conservar una representación `String`.
- Se rechazan entradas vacías, malformadas o que decodifiquen a un secreto
  menor que 20 bytes o mayor que 64 bytes antes de cambiar la entrada o guardar.
  Los mensajes no incluyen el texto ingresado ni causas que lo contengan.
- El secreto decodificado se maneja como bytes; se limpia todo buffer temporal
  propio en éxito y error. La representación almacenada en memoria y su ciclo de
  vida deben integrarse con `Entry`/`VaultSession` sin exponer referencias
  mutables ni conservar copias innecesarias.

### R3 — Asociación y consulta

- `set-totp` agrega o reemplaza el secreto de la entrada identificada solo
  después de validarlo. Si ya existe uno, solicita confirmación exacta `sí` o `si`;
  cualquier otra respuesta cancela. Persiste mediante `save()` y no informa
  éxito antes de guardar.
- `remove-totp` quita el secreto solo ante la respuesta exacta `sí` o `si` y guarda el
  cambio. Cancelar no modifica el archivo.
- `totp` abre la bóveda, calcula una vez el código para la entrada indicada y lo
  entrega por `CliConsole.printSecret`; muestra los segundos restantes por salida
  normal. No refresca automáticamente. El tiempo restante es `30 - (epochSecond
  mod 30)`, por lo que al inicio de un periodo muestra 30. El código y sus
  buffers se limpian en todos los caminos.
- Un ID inexistente, una entrada sin TOTP o una retirada cuando no hay secreto
  produce un error claro en español, sin mostrar datos de otras entradas ni
  guardar cambios. La consulta no guarda cambios.

### R4 — Formato v2 y compatibilidad

- Las bóvedas nuevas se crean en formato v2. v2 conserva el encabezado de 50
  bytes de v1 salvo el campo de versión, y añade al final de cada registro de
  entrada un uint32 big-endian con la longitud del secreto TOTP decodificado,
  seguido de sus bytes; longitud cero significa ausencia de TOTP. No se añade
  flag adicional. Se mantienen los enteros big-endian, UTF-8 estricto y el
  máximo de 16 MiB para el archivo completo.
- Las bóvedas v1 se pueden abrir y guardar en v1 sin conversión implícita. No se
  permite establecer ni retirar TOTP mientras la sesión siga en v1; se informa
  que primero debe ejecutarse `upgrade`.
- `upgrade` convierte explícitamente una bóveda v1 a v2 mediante escritura
  atómica, preservando entradas e identificadores. Un fallo conserva intacto el
  archivo v1 y no deja una bóveda parcialmente convertida. Solicita confirmación
  exacta `sí` o `si`; cualquier otra respuesta cancela sin modificar el archivo. Respeta
  bloqueo, permisos, detección de cambios externos y manejo de fallos de Spec 002.
  Si el archivo v2 resultante excede 16 MiB, falla sin reemplazar el original.
- Se actualiza `docs/FORMAT.md`; v1 continúa documentado y v2 se documenta sin
  ambigüedad. No se modifica `docs/THREAT_MODEL.md` sin autorización.

### R5 — Fallos, secretos y CLI

- Los comandos requieren `System.console()`; no se usa stdin como alternativa.
- Sesiones, claves, secretos decodificados y códigos se limpian/cierra en todos
  los caminos según sus propietarios. El secreto TOTP nunca aparece en pantalla,
  salvo el código vigente solicitado; no se escribe en stdout redirigible,
  errores, logs ni excepciones.
- Errores de argumentos, Base32, almacenamiento e interacción se muestran en
  español sin trazas ni contenido sensible. La operación falla cerrada y no
  presenta resultados parciales ni éxito falso.
- No se agregan dependencias sin aprobación. El paquete `totp` no depende de
  `cli`, `storage` ni `model`; la CLI coordina sus operaciones.

## 5. Criterios de aceptación propuestos

| ID | Criterio |
|---|---|
| AC-1 | Los vectores de prueba RFC 6238 para HMAC-SHA-1 producen los códigos esperados; el reloj puede controlarse en pruebas. |
| AC-2 | El cálculo respeta seis dígitos, periodo de 30 segundos y límites del tiempo; no filtra códigos en errores. |
| AC-3 | La validación Base32 acepta y rechaza exactamente las variantes definidas, sin modificar la entrada ante error. |
| AC-4 | Un secreto asociado se persiste cifrado, sobrevive a cerrar/reabrir y no aparece en el archivo en claro. |
| AC-5 | `set-totp`, `remove-totp` y `totp` validan argumentos/IDs antes de abrir archivos y siguen confirmaciones y persistencia especificadas. |
| AC-6 | El código se entrega únicamente por el canal privado de consola; el secreto y el código se limpian en éxito, cancelación y error. |
| AC-7 | La ausencia de consola no lee stdin, no calcula/expone códigos ni modifica archivos. |
| AC-8 | Las bóvedas v1 conservan el comportamiento acordado y `upgrade` solo convierte tras consentimiento explícito, de forma atómica y sin pérdida de datos. |
| AC-9 | Formato v2, límites, parsing adversarial y compatibilidad v1 están documentados en `docs/FORMAT.md` y probados. |
| AC-10 | No se agregan dependencias no aprobadas ni se modifica `docs/THREAT_MODEL.md`; `./mvnw.cmd verify` termina en verde. |

## 6. Pruebas requeridas

- Vectores RFC 6238; límites de periodo y tiempo usando `Clock` controlable.
- Validación Base32 con casos válidos e inválidos, mayúsculas/minúsculas,
  padding opcional correcto, padding malformado y longitudes decodificadas límite.
- Persistencia round-trip y pruebas de que ningún secreto aparece en claro en el
  archivo ni en mensajes/salidas no autorizadas.
- Parser y flujos CLI: confirmación, cancelación, entrada ausente, consola
  ausente, IDs desconocidos, errores de guardado y limpieza de arreglos.
- Compatibilidad con archivos v1, formato v2, conversión explícita, fallos de
  conversión, atomicidad, permisos y bloqueo; sin incluir secretos reales en
  fixtures.
- `./mvnw.cmd verify` al cierre.

## 7. Decisiones acordadas

**Acordado durante la definición:**

1. TOTP es para cuentas guardadas en entradas, no para desbloquear la bóveda.
2. El secreto se ingresa manualmente; no se importan QR/URI ni se generan
   secretos para aprovisionar servicios.
3. Parámetros fijos: HMAC-SHA-1, seis dígitos y periodo de 30 segundos.
4. Se introduce formato v2 y no hay migración implícita; se conserva lectura de
   v1 y se solicita un comando explícito `upgrade` para conversión.
5. Se gestionan secretos de entradas existentes mediante operaciones por ID,
   incluyendo establecer y retirar TOTP.

6. Base32 acepta mayúsculas/minúsculas y padding opcional correcto; no acepta
   espacios ni separadores. Se solicitan dos lecturas y discrepancia cancela.
7. El secreto decodificado mide entre 20 y 64 bytes. `Entry` posee copia
   defensiva `byte[]`; consultas devuelven copias borrables y se limpian las
   copias al reemplazar, quitar o cerrar sesión.
8. Si ya hay secreto, reemplazar requiere confirmación exacta `sí` o `si`. Retirar un
   secreto inexistente y consultar una entrada sin TOTP fallan claramente sin
   guardar.
9. Bóvedas nuevas usan v2; v1 se puede abrir/guardar en v1, pero no modificar
   TOTP hasta `upgrade`. El payload v2 añade longitud uint32 y bytes al final de
   cada registro; longitud cero representa ausencia. Se mantiene el máximo de
   archivo de 16 MiB.
10. `upgrade` requiere confirmación exacta `sí` o `si`; cancelar no modifica el archivo.
    El código TOTP se entrega una vez por canal privado, el tiempo restante por
    salida normal y no hay refresco automático.

No quedan decisiones funcionales pendientes. La implementación conserva las
garantías de bloqueo, permisos y escritura atómica de Spec 002; las pruebas de
conversión registran tanto el éxito como fallos simulados y cambios externos.

## 8. Cierre

La spec fue aprobada el 2026-11-15 y su implementación quedó cerrada el
2026-11-15. T01–T35 están completadas; AC-1–AC-10 tienen evidencia en las
pruebas y en `docs/FORMAT.md`. La verificación final y sus limitaciones de
plataforma están registradas en `docs/STATUS.md`. El modelo de amenazas se
actualizó con aprobación expresa del usuario.
