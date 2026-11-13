# Spec 001 — crypto-core

**Estado:** cerrada el 2026-11-12. Todas las tareas T01–T23 están completadas;
`./mvnw.cmd verify` finalizó correctamente con 25 pruebas y 0 fallos.

## 1. Objetivo
Proveer las dos operaciones criptográficas de base del proyecto: derivar una
clave a partir de la contraseña maestra y cifrar y descifrar datos con
autenticación. Todo el resto del sistema depende de este módulo, por eso se
especifica y se prueba primero.

## 2. Alcance y fuera de alcance
**Dentro:** derivación de clave, generación de sal, cifrado y descifrado
autenticado, validación de parámetros.
**Fuera:** formato del archivo de bóveda (spec 002), lectura y escritura en
disco, CLI, almacenamiento de la clave entre operaciones, rotación de claves.

## 3. Requisitos
- **R1 Derivación.** Dados una contraseña maestra (`char[]`), una sal de 16
  bytes y parámetros de Argon2id (memoria, iteraciones, paralelismo), produce
  una clave de 32 bytes. La misma entrada y la misma implementación producen
  siempre la misma clave. Cambiar la sal, la contraseña o los parámetros debe
  cambiar el resultado en los vectores de prueba definidos; no se afirma una
  garantía matemática de ausencia de colisiones.
- **R2 Sal.** Genera sales de 16 bytes con `SecureRandom`.
- **R3 Cifrado.** Dados una clave de 32 bytes, un texto plano y datos
  asociados (AAD), cifra con AES-256-GCM y etiqueta de 128 bits. El nonce de
  12 bytes lo genera el propio módulo con `SecureRandom` en cada llamada; el
  llamador no puede fijarlo. La unicidad de nonces aleatorios es probabilística,
  no una garantía absoluta; el diseño debe hacer despreciable la probabilidad
  de colisión para el uso previsto.
- **R4 Descifrado.** Dados la clave, el resultado del cifrado y los mismos AAD,
  devuelve el texto plano original. Los errores de datos o secreto (clave
  incorrecta, ciphertext malformado o alterado, o AAD no coincidente) fallan
  con el mismo tipo de excepción criptográfica genérica, sin revelar cuál
  ocurrió, sin causa sensible y sin devolver datos parciales. El mal uso de la
  API (por ejemplo, argumentos nulos o longitudes inválidas) se informa como
  error de argumentos y se distingue de esos errores criptográficos. Ningún
  mensaje ni causa puede incluir datos sensibles.
- **R5 Validación.** Rechaza argumentos nulos, claves que no midan 32 bytes,
  sales que no midan 16, contraseñas vacías y parámetros de Argon2id fuera de
  los límites definidos en el plan técnico (protege contra la rebaja de costo
  desde una cabecera manipulada). Esta spec no establece máximos de tamaño para
  texto plano, AAD o ciphertext; sus tamaños quedan limitados por la memoria
  disponible y las restricciones de la plataforma.
- **R6 Higiene de secretos.** Ningún mensaje, causa de excepción ni log contiene
  la contraseña, la clave ni el texto plano. El llamador conserva la propiedad
  del `char[]` y debe borrarlo al terminar; el módulo no lo modifica. Para
  normalizar contraseña a NFC, primero comprueba si ya está normalizada usando
  un `CharBuffer` sobre el arreglo. Si no lo está, el resultado `String` de
  `Normalizer.normalize` se copia inmediatamente a un `char[]` temporal, no se
  conserva ni registra y se trata como una limitación de memoria; el módulo
  borra los temporales propios, incluidos los bytes UTF-8. La codificación UTF-8
  es estricta: se rechazan sustitutos UTF-16 aislados en vez de reemplazarlos.
- **R7 Independencia.** El paquete `crypto` no importa nada de los demás
  paquetes del proyecto.

## 4. Criterios de aceptación
Cada criterio se convierte en al menos una prueba.

| ID | Criterio |
|---|---|
| AC-1 | Cifrar y luego descifrar con la misma clave devuelve exactamente el original, incluido un texto plano vacío y uno grande |
| AC-2 | Descifrar con una clave incorrecta falla |
| AC-3 | Alterar un solo byte del resultado cifrado (nonce, contenido o etiqueta) hace fallar el descifrado |
| AC-4 | Descifrar con AAD distintos a los usados al cifrar falla |
| AC-5 | Cifrar dos veces el mismo texto con la misma clave genera nonces distintos en la prueba; esto verifica el comportamiento esperado sin constituir una garantía absoluta de unicidad |
| AC-6 | La misma contraseña, sal y parámetros producen la misma clave; los vectores de prueba para entradas distintas producen resultados distintos |
| AC-7 | La clave derivada mide 32 bytes y la sal generada mide 16 |
| AC-8 | Dos sales generadas consecutivamente son distintas |
| AC-9 | Argumentos nulos, claves o sales con longitud inválida, contraseñas vacías y parámetros fuera de límites son rechazados como errores de argumentos |
| AC-10 | Los errores de autenticación/datos de descifrado usan la misma excepción externa; los errores de uso de API se distinguen; mensajes y causas no contienen datos sensibles |
| AC-11 | La derivación coincide con los vectores Argon2id de RFC 9106 usando una ruta de prueba de bajo nivel que recibe los bytes exactos del vector |
| AC-12 | `crypto` no tiene dependencias hacia otros paquetes del proyecto (verificable por inspección de imports) |
| AC-13 | El módulo no modifica el `char[]` de entrada; se valida la ruta NFC sin crear `String` cuando ya está normalizado y la ruta no NFC copia inmediatamente el resultado a un `char[]` temporal |

El tiempo de desbloqueo objetivo (entre 0,5 y 1 s) no es una prueba
automática, porque depende del equipo. La medición manual se registra en el
plan y en T23.

## 5. Decisiones resueltas por aprobación
1. Límites Argon2id: memoria 19–256 MiB, iteraciones 2–10 y paralelismo 1–4;
   valores nominales 64 MiB, 3 iteraciones y paralelismo 1.
2. Resultado cifrado: tipo estructurado con nonce de 12 bytes y ciphertext con
   etiqueta GCM al final; validar longitudes y hacer copias defensivas.
3. Contraseña: propiedad del `char[]` por el llamador; UTF-8 y NFC sin recortar
   espacios. Ruta RFC 9106 de bajo nivel autorizada solo para pruebas.
4. Dependencia: `org.bouncycastle:bcprov-jdk18on:1.86`, fijada sin rangos. Se
   verificó publicación/antigüedad y se consultó vulnerabilidades antes de
   incorporarla; el vector RFC 9106 §5.3 pasó en T09.

## 6. Cierre

La implementación satisface los criterios AC-1–AC-13 según las pruebas y
revisiones registradas en `tasks.md`. El módulo `crypto` no define la
serialización de la bóveda; esta queda para la spec de almacenamiento.
