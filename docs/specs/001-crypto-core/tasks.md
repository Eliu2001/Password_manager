# Tareas — Spec 001: crypto-core

Las tareas están ordenadas por dependencia y dimensionadas para bloques de **20–30 minutos como máximo**. Cada tarea debe terminar con su criterio verificable; si excede el tiempo, dividirla antes de continuar.

## Preparación y parámetros

- [x] **T01 — Verificar la dependencia aprobada** (20 min)  
  **RF:** R1 · **AC:** AC-11  
  **Hecho cuando:** Maven Central confirma `org.bouncycastle:bcprov-jdk18on:1.86` publicado hace al menos dos semanas, y una consulta de vulnerabilidades no muestra avisos conocidos aplicables; se registra la fecha y las fuentes en el PR o aquí si aún no existe PR.  
  **Evidencia (2026-11-11):** Maven Central lista la versión 1.86 con fecha de publicación **2026-09-11**, por lo que supera el mínimo de dos semanas. La consulta de Snyk del artefacto reporta **0 vulnerabilidades directas conocidas** para 1.86. Maven Central también lista 1.87 publicada hoy; 1.86 sigue estando publicada y la consulta no reporta avisos conocidos aplicables a esa versión. Fuentes: [Maven Central 1.86](https://repo.maven.apache.org/maven2/org/bouncycastle/bcprov-jdk18on/1.86/) · [Snyk](https://security.snyk.io/package/maven/org.bouncycastle%3Abcprov-jdk18on).

- [x] **T02 — Preparar JUnit 5 y ejecutar la verificación base** (25 min)  
  **RF:** soporte de pruebas para R1–R7 · **AC:** todos  
  **Hecho cuando:** JUnit 5 está configurado según Maven, existe una prueba mínima ejecutable y `mvnw verify` termina correctamente.  
  **Evidencia:** Apache Maven 3.10.0 descargado desde `dlcdn.apache.org`; SHA-512 comparado y coincidente con checksum oficial. Wrapper generado y configurado con distribución oficial y SHA-256 verificado. `./mvnw.cmd verify`: BUILD SUCCESS, 1 prueba, 0 fallos.

- [x] **T03 — Añadir Bouncy Castle con versión fija** (25 min)  
  **RF:** R1 · **AC:** AC-11  
  **Hecho cuando:** `pom.xml` declara `bcprov-jdk18on` exactamente en versión `1.86`, sin rangos, y Maven resuelve la dependencia.  
  **Evidencia:** `./mvnw.cmd verify`: BUILD SUCCESS; la dependencia fue descargada/resuelta y la prueba base pasó (1 test, 0 fallos).

- [x] **T04 — Crear el tipo de parámetros Argon2id** (25 min)  
  **RF:** R1, R5 · **AC:** AC-9  
  **Hecho cuando:** el tipo representa memoria en KiB, iteraciones y paralelismo, define los valores nominales de 64 MiB/3/1 y rechaza valores fuera de los rangos aprobados.  
  **Evidencia:** `Argon2Parameters` valida los tres rangos y provee los valores nominales. `./mvnw.cmd verify`: BUILD SUCCESS, 3 pruebas ejecutadas, 0 fallos.

- [x] **T05 — Probar límites Argon2id** (25 min)  
  **RF:** R5 · **AC:** AC-9  
  **Hecho cuando:** pruebas cubren mínimo y máximo admitidos de memoria (19 456–262 144 KiB), iteraciones (2–10), paralelismo (1–4), y valores justo fuera de cada límite.  
  **Evidencia:** `Argon2ParametersTest` comprueba ambos extremos válidos y los valores inmediatamente fuera de rango para las tres dimensiones. `./mvnw.cmd verify`: **BUILD SUCCESS**, 4 pruebas, 0 fallos.

## Contraseña y derivación

- [x] **T06 — Implementar comprobación de NFC sin conversión en el caso común** (25 min)  
  **RF:** R1, R6 · **AC:** AC-13  
  **Hecho cuando:** la ruta NFC usa `Normalizer.isNormalized` sobre un `CharBuffer` del `char[]` y no invoca `Normalizer.normalize` si ya está normalizado.  
  **Evidencia:** `PasswordNormalizer` comprueba NFC vía `CharBuffer`; para entrada ya normalizada devuelve el mismo arreglo sin entrar a la rama de normalización. `PasswordNormalizerTest` verifica la identidad del arreglo.

- [x] **T07 — Implementar normalización NFC para entradas no normalizadas** (25 min)  
  **RF:** R1, R6 · **AC:** AC-13  
  **Hecho cuando:** la ruta no NFC copia inmediatamente el resultado de `Normalizer.normalize` a un `char[]` temporal y no conserva ni registra el `String` retornado.  
  **Evidencia:** la ruta no NFC copia el resultado directamente a un arreglo nuevo; la referencia `String` queda limitada a la variable local del método. Pruebas validan el resultado NFC, que el arreglo es distinto y que la entrada original no se modifica. `./mvnw.cmd verify`: **BUILD SUCCESS**, 6 pruebas, 0 fallos.

- [x] **T08 — Implementar codificación UTF-8 y limpieza de temporales** (25 min)  
  **RF:** R1, R6 · **AC:** AC-13  
  **Hecho cuando:** la derivación recibe bytes UTF-8 sin recortar espacios, el módulo sobrescribe sus buffers temporales al completar y no modifica el `char[]` del llamador.  
  **Evidencia:** `PasswordEncoding` entrega bytes UTF-8 al consumidor en alcance acotado y borra los bytes y buffers temporales en éxito y error; la entrada no se modifica. UTF-8 rechaza sustitutos aislados, según decisión aprobada. `./mvnw.cmd verify`: **BUILD SUCCESS**, 9 pruebas, 0 fallos.

- [x] **T09 — Añadir prueba RFC 9106 de bajo nivel** (25 min)  
  **RF:** R1 · **AC:** AC-11  
  **Hecho cuando:** una prueba alimenta los bytes exactos del vector Argon2id de RFC 9106 sección 5.3 y compara el resultado esperado; la ruta no forma parte de la API de producción.  
  **Evidencia:** `Argon2Rfc9106Test` comprueba el tag de 32 bytes con Bouncy Castle 1.86, versión 19, memoria 32 KiB, 3 iteraciones, paralelismo 4 y los bytes de password/salt/secret/associated data publicados en RFC 9106 §5.3. La prueba vive solo en `src/test`. `./mvnw.cmd verify`: **BUILD SUCCESS**, 10 pruebas, 0 fallos.

- [x] **T10 — Implementar derivación Argon2id** (25 min)  
  **RF:** R1, R5 · **AC:** AC-6, AC-7, AC-9  
  **Hecho cuando:** la API valida parámetros y sal, deriva exactamente 32 bytes y pruebas confirman repetibilidad con entradas iguales.  
  **Evidencia:** `Argon2idKdf` implementa Argon2id v1.3 con BC y deriva 32 bytes tras validar contraseña, sal y parámetros. Pruebas cubren repetibilidad y variación de contraseña, sal y parámetros. `./mvnw.cmd verify`: **BUILD SUCCESS**, 12 pruebas, 0 fallos.

- [x] **T11 — Implementar `DerivedKey` y su cierre seguro** (25 min)  
  **RF:** R1, R6 · **AC:** AC-7  
  **Hecho cuando:** `DerivedKey` implementa `AutoCloseable`, `close()` sobrescribe el arreglo interno y el objeto rechaza obtener o usar la clave después del cierre.  
  **Evidencia:** `DerivedKey` toma propiedad del arreglo, da acceso temporal solo dentro del paquete, borra al cerrar (idempotente) y rechaza el uso posterior. Pruebas inspeccionan que los bytes quedan a cero. `./mvnw.cmd verify`: **BUILD SUCCESS**, 14 pruebas, 0 fallos.

- [x] **T12 — Completar pruebas de contraseña y derivación** (25 min)  
  **RF:** R1, R5, R6 · **AC:** AC-6, AC-9, AC-13  
  **Hecho cuando:** pruebas cubren contraseña vacía, sal inválida, UTF-8/NFC, conservación del `char[]` del llamador y borrado del temporal controlado por el módulo en éxito y error.  
  **Evidencia:** pruebas verifican contraseña vacía/sal inválida, equivalencia NFC sin modificar los arreglos del llamador, UTF-8 estricto y borrado de bytes temporales en éxito y excepción del consumidor. `./mvnw.cmd verify`: **BUILD SUCCESS**, 15 pruebas, 0 fallos.

## Sal, cifrado y descifrado

- [x] **T13 — Implementar el generador de sal con `SecureRandom` propio** (25 min)  
  **RF:** R2, R5 · **AC:** AC-7, AC-8, AC-9  
  **Hecho cuando:** el módulo crea y posee `SecureRandom`, genera sales de 16 bytes y pruebas verifican longitud y diferencia en llamadas consecutivas.  
  **Evidencia:** `CryptoService` posee un `SecureRandom` propio y `generateSalt()` devuelve 16 bytes; prueba verifica longitud y diferencia en dos llamadas seguidas. `./mvnw.cmd verify`: **BUILD SUCCESS**, 16 pruebas, 0 fallos.

- [x] **T14 — Implementar `EncryptedData`** (25 min)  
  **RF:** R3, R5 · **AC:** AC-3, AC-9  
  **Hecho cuando:** el tipo exige nonce de 12 bytes y contenido cifrado con etiqueta presente, y prueba que hace copias defensivas al recibir y exponer arreglos.  
  **Evidencia:** `EncryptedData` valida nonce de 12 bytes y ciphertext/tag de al menos 16 bytes, y clona arreglos al construir y al exponerlos. Pruebas cubren longitudes, null y copias defensivas. `./mvnw.cmd verify`: **BUILD SUCCESS**, 18 pruebas, 0 fallos.

- [x] **T15 — Implementar cifrado AES-256-GCM** (25 min)  
  **RF:** R3 · **AC:** AC-5, AC-7  
  **Hecho cuando:** la API usa clave de 32 bytes, nonce aleatorio de 12 bytes no fijable por el llamador, AAD y etiqueta de 128 bits; devuelve `EncryptedData`.  
  **Evidencia:** `CryptoService.encrypt` cifra con AES-256-GCM, genera internamente nonce de 12 bytes, aplica AAD/etiqueta de 128 bits y devuelve `EncryptedData`; se valida longitud del tag y errores de argumentos/clave cerrada. `./mvnw.cmd verify`: **BUILD SUCCESS**, 20 pruebas, 0 fallos.

- [x] **T16 — Probar cifrado y aleatoriedad del nonce** (25 min)  
  **RF:** R3 · **AC:** AC-5  
  **Hecho cuando:** dos cifrados de la misma entrada y clave generan nonces distintos en la prueba, sin documentar esto como garantía absoluta de unicidad.  
  **Evidencia:** `CryptoServiceEncryptionTest` cifra dos veces la misma entrada con la misma clave y comprueba nonces distintos en la muestra. Esto no se presenta como garantía absoluta. `./mvnw.cmd verify`: **BUILD SUCCESS**, 21 pruebas, 0 fallos.

- [x] **T17 — Implementar descifrado AES-256-GCM autenticado** (25 min)  
  **RF:** R4 · **AC:** AC-1, AC-4  
  **Hecho cuando:** descifrar un resultado válido con la misma clave y AAD devuelve exactamente el plaintext original solo después de validar la etiqueta.  
  **Evidencia:** `CryptoService.decrypt` aplica AES-GCM y devuelve el plaintext solo tras `doFinal` autenticar; pruebas verifican round-trip con AAD coincidente. `./mvnw.cmd verify`: **BUILD SUCCESS**, 22 pruebas, 0 fallos.

- [x] **T18 — Uniformar errores de datos y errores de argumentos** (25 min)  
  **RF:** R4, R5, R6 · **AC:** AC-2, AC-3, AC-4, AC-9, AC-10  
  **Hecho cuando:** clave incorrecta, AAD diferente, ciphertext malformado o alterado comparten la misma excepción externa; errores de argumentos se distinguen y no se filtran causas sensibles.  
  **Evidencia:** claves incorrectas, AAD distintos y alteraciones separadas de nonce, ciphertext y tag producen `DecryptionException` con mensaje fijo y sin causa. Nulos y clave cerrada conservan errores de argumentos/estado distintos. `./mvnw.cmd verify`: **BUILD SUCCESS**, 24 pruebas, 0 fallos.

- [x] **T19 — Probar round-trip, alteraciones y casos límite** (25 min)  
  **RF:** R3, R4, R5 · **AC:** AC-1–AC-4, AC-9  
  **Hecho cuando:** pruebas cubren plaintext vacío y grande, AAD vacío/no coincidente, clave incorrecta y alteraciones separadas de nonce, ciphertext y etiqueta; no se devuelve plaintext parcial.  
  **Evidencia:** pruebas cubren round-trip de plaintext vacío y de 1 MiB con AAD vacío, AAD distinto, clave incorrecta y cambios independientes de nonce, ciphertext y tag; los fallos nunca devuelven resultado. `./mvnw.cmd verify`: **BUILD SUCCESS**, 25 pruebas, 0 fallos.

## Higiene, arquitectura y cierre

- [x] **T20 — Revisar limpieza de secretos y mensajes** (25 min)  
  **RF:** R6 · **AC:** AC-10, AC-13  
  **Hecho cuando:** pruebas y revisión de código confirman que no se registran secretos, el módulo no modifica el `char[]` recibido y borra buffers temporales propios en éxito y error.  
  **Evidencia:** revisión de `PasswordEncoding`, `Argon2idKdf`, `DerivedKey` y `CryptoService`: buffers propios se limpian en `finally`/`close`; no hay logging; la excepción de descifrado es fija y sin causa. Se añadió prueba para el mensaje/cause de Unicode malformado. `./mvnw.cmd verify`: **BUILD SUCCESS**, 25 pruebas, 0 fallos.

- [x] **T21 — Verificar independencia del paquete `crypto`** (20 min)  
  **RF:** R7 · **AC:** AC-12  
  **Hecho cuando:** inspección de imports confirma que `crypto` no depende de otros paquetes del proyecto y el resultado queda registrado en la revisión.  
  **Evidencia:** búsqueda de `import cl.germain.vault.` en `src/main/java/cl/germain/vault/crypto` sin coincidencias. `./mvnw.cmd verify`: **BUILD SUCCESS**, 25 pruebas, 0 fallos.

- [x] **T22 — Ejecutar verificación completa** (25 min)  
  **RF:** R1–R7 · **AC:** AC-1–AC-13  
  **Hecho cuando:** `mvn verify` pasa y el informe de pruebas no contiene fallos.  
  **Evidencia:** `./mvnw.cmd verify`: **BUILD SUCCESS**, 25 pruebas, 0 fallos.

- [x] **T23 — Medir manualmente el tiempo de derivación** (25 min)  
  **RF:** R1 · **AC:** objetivo no automatizado de la spec  
  **Hecho cuando:** se registra el entorno, parámetros y tiempo medido; la medición se reporta sin convertirla en una prueba automática dependiente del equipo.  
  **Medición (2026-11-12):** Windows, Eclipse Temurin Java 21.0.13, AMD Ryzen 5 4500 6-Core Processor; parámetros nominales Argon2id: 65 536 KiB, 3 iteraciones, paralelismo 1; una ejecución medida: **938,128 ms**. Medición manual puntual, sin warm-up; no es garantía de latencia ni prueba automatizada.
