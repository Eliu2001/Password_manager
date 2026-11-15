# Estado del proyecto — 2026-11-15

## Spec cerrada

- **Spec:** `docs/specs/001-crypto-core/spec.md` — Spec 001, `crypto-core`.
- **Cierre:** 2026-11-12; T01–T23 completadas; `./mvnw.cmd verify`: **BUILD SUCCESS**, 25 pruebas, 0 fallos.
- **Plan:** `docs/specs/001-crypto-core/plan.md`.
- **Lista de tareas:** `docs/specs/001-crypto-core/tasks.md`.
- **Alcance:** Argon2id, generación de sal, AES-256-GCM, descifrado autenticado y validación. Formato de bóveda, almacenamiento en disco y CLI quedan fuera de esta spec.

## Spec cerrada

- **Spec:** `docs/specs/002-vault-storage/spec.md` — formato binario y persistencia de bóveda.
- **Cierre:** 2026-11-12; T01–T30 completadas; `./mvnw.cmd verify`: **BUILD SUCCESS**, 63 pruebas, 0 fallos, 1 omitida.
- **Estado:** cerrada con limitaciones de plataforma documentadas.
- **Alcance acordado:** crear, abrir y guardar bóvedas; modelo mínimo `Entry`/`Vault` y operaciones por ID en sesión. Reglas de negocio, búsquedas y CLI quedan para specs posteriores.
- **Plan técnico:** `docs/specs/002-vault-storage/plan.md`.
- **Tareas:** `docs/specs/002-vault-storage/tasks.md`.

## Progreso de Spec 002

- **Completadas:** T01–T30. Servicio create/open/save, sesión, persistencia
  atómica, detección de cambios externos, pruebas de límites y matrices adversariales.
- **Última verificación local de Spec 002:** `./mvnw.cmd verify` — **BUILD
  SUCCESS**, 63 pruebas, 0 fallos y 1 omitida (Windows; symlink no disponible).
- **Limitaciones de plataforma:** ejecución local en Windows; ACL del usuario
  actual comprobada; escritura y reemplazo atómico real pasaron en el filesystem
  temporal de Windows. El fallo de atomic move se simuló con inyección y comprobó
  fail-closed, preservación del destino y limpieza temporal. En Linux, el workflow
  `.github/workflows/verify.yml` ejecutó estas pruebas en la revisión acumulada
  de Spec 002/003: [run 1, ID 43654420072](https://github.com/Eliu2001/Password_manager/actions/runs/43654420072),
  terminado en `success` el 2026-11-14. El mismo workflow para Spec 004 terminó
  en `success`: [run 2, ID 43656670683](https://github.com/Eliu2001/Password_manager/actions/runs/43656670683),
  el 2026-11-15. Ambos cubren filesystem Linux, incluidos permisos POSIX y
  symlinks.
- **Formato:** `docs/FORMAT.md` actualizado. El AAD son los primeros 34 bytes
  exactos del encabezado; nonce protegido por GCM y longitud validada
  estructuralmente, según la decisión aprobada en Spec 002.
- **Cierre:** AC-1–AC-17 probados; formato v1 documentado en `docs/FORMAT.md`.
  La verificación Linux posterior confirmó las pruebas dependientes de
  plataforma.

## Tareas completadas y verificación

- **T01 — Verificar la dependencia aprobada:** completada el 2026-11-11. Maven Central lista `org.bouncycastle:bcprov-jdk18on:1.86`, publicada el 2026-09-11; supera las dos semanas requeridas. Snyk reportó 0 vulnerabilidades directas conocidas para 1.86 en la consulta realizada. La versión 1.87 aparece publicada el 2026-11-11, por lo que no superaba el período mínimo ese día. Evidencia y fuentes: `docs/specs/001-crypto-core/tasks.md` T01; [Maven Central 1.86](https://repo.maven.apache.org/maven2/org/bouncycastle/bcprov-jdk18on/1.86/) y [Snyk](https://security.snyk.io/package/maven/org.bouncycastle%3Abcprov-jdk18on). El resultado de escaneo no constituye garantía de ausencia de vulnerabilidades.
- **T02 — Preparar JUnit 5 y ejecutar verificación base:** completada. Se añadió JUnit Jupiter **5.14.4** con scope `test` en `pom.xml` y `src/test/java/cl/germain/JUnitSmokeTest.java`. Apache Maven **3.10.0** se descargó a `C:\Users\villa\AppData\Local\Temp\opencode\apache-maven-3.10.0` desde `https://dlcdn.apache.org/maven/maven-3/3.10.0/binaries/`; SHA-512 esperado y calculado coincidieron: `22d31676d5b92ed53308c19ecded3245d0efd0cddfec51ec7bab62f4f13ad70ddddceff8e62940e0e8e2421f3cde8106737f4becec0aa034ffeff2a754e88337`. Se generó Maven Wrapper 3.3.4 (`mvnw`, `mvnw.cmd`, `.mvn/wrapper/maven-wrapper.properties`); el wrapper usa la URL oficial de Apache para Maven 3.10.0 y `distributionSha256Sum=1f6d9909266510f039f59aa0e13dcd2c66da85f043e56276e41f2918f8bddaff`. Verificación `./mvnw.cmd verify`: **BUILD SUCCESS**, 1 prueba, 0 fallos.
- **T03 — Añadir Bouncy Castle con versión fija:** completada. `pom.xml` declara `org.bouncycastle:bcprov-jdk18on:1.86` con versión exacta, sin rangos. Verificación `./mvnw.cmd verify`: **BUILD SUCCESS**, dependencia resuelta, 1 prueba, 0 fallos.
- **T04 — Crear el tipo de parámetros Argon2id:** completada. Implementación: `src/main/java/cl/germain/vault/crypto/Argon2Parameters.java`; pruebas: `src/test/java/cl/germain/vault/crypto/Argon2ParametersTest.java`. El tipo valida memoria en KiB, iteraciones y paralelismo, y proporciona los valores nominales 65 536 KiB/3/1. Verificación `./mvnw.cmd verify`: **BUILD SUCCESS**, 3 pruebas, 0 fallos.
- **T05 — Probar límites Argon2id:** completada. `Argon2ParametersTest` verifica mínimo y máximo admitidos, además del primer valor inválido fuera del rango para memoria, iteraciones y paralelismo. Verificación `./mvnw.cmd verify`: **BUILD SUCCESS**, 4 pruebas, 0 fallos.
- **T06 — Implementar comprobación de NFC sin conversión en el caso común:** completada. `PasswordNormalizer` usa `Normalizer.isNormalized` sobre un `CharBuffer` y conserva el mismo arreglo cuando ya está normalizado. Cubierto por `PasswordNormalizerTest`.
- **T07 — Implementar normalización NFC para entradas no normalizadas:** completada. La ruta no NFC copia el resultado normalizado a un `char[]` distinto y no conserva el `String` local. Pruebas comprueban la salida y que no se modifica la entrada. Verificación `./mvnw.cmd verify`: **BUILD SUCCESS**, 6 pruebas, 0 fallos.
- **T08 — Implementar codificación UTF-8 y limpieza de temporales:** completada. `PasswordEncoding` entrega temporalmente los bytes UTF-8 al consumidor y limpia buffers de bytes y el arreglo normalizado propio en éxito o error; el `char[]` original no se modifica. Verificación `./mvnw.cmd verify`: **BUILD SUCCESS**, 9 pruebas, 0 fallos.
- **T09 — Añadir prueba RFC 9106 de bajo nivel:** completada. `Argon2Rfc9106Test`, solo en `src/test`, verifica el vector Argon2id §5.3 con los bytes de entrada exactos, incluyendo secret y associated data. Verificación `./mvnw.cmd verify`: **BUILD SUCCESS**, 10 pruebas, 0 fallos.
- **T10 — Implementar derivación Argon2id:** completada. `Argon2idKdf` valida y deriva 32 bytes con Argon2id v1.3; las pruebas verifican reproducibilidad y cambios por contraseña/sal/parámetros. Verificación `./mvnw.cmd verify`: **BUILD SUCCESS**, 12 pruebas, 0 fallos.
- **T11 — Implementar `DerivedKey` y su cierre seguro:** completada. La clase posee el arreglo de clave, permite uso interno acotado, lo borra con `close()` y rechaza operaciones posteriores. Verificación `./mvnw.cmd verify`: **BUILD SUCCESS**, 14 pruebas, 0 fallos.
- **T12 — Completar pruebas de contraseña y derivación:** completada. Se cubren entradas inválidas, equivalencia NFC, preservación del `char[]`, codificación estricta y limpieza de bytes temporales tras éxito/error. Verificación `./mvnw.cmd verify`: **BUILD SUCCESS**, 15 pruebas, 0 fallos.
- **T13 — Implementar el generador de sal con `SecureRandom` propio:** completada. `CryptoService` posee `SecureRandom` y genera sales de 16 bytes; dos llamadas consecutivas se prueban distintas. Verificación `./mvnw.cmd verify`: **BUILD SUCCESS**, 16 pruebas, 0 fallos.
- **T14 — Implementar `EncryptedData`:** completada. Valida nonce/ciphertext-tag y mantiene copias defensivas de entrada/salida. Verificación `./mvnw.cmd verify`: **BUILD SUCCESS**, 18 pruebas, 0 fallos.
- **T15 — Implementar cifrado AES-256-GCM:** completada. `CryptoService.encrypt` genera nonce aleatorio interno, cifra con clave `DerivedKey`, AAD y tag de 128 bits, y devuelve `EncryptedData`. Verificación `./mvnw.cmd verify`: **BUILD SUCCESS**, 20 pruebas, 0 fallos.
- **T16 — Probar cifrado y aleatoriedad del nonce:** completada. La prueba compara nonces de dos cifrados consecutivos de la misma entrada y clave; la aleatoriedad no se afirma como garantía absoluta. Verificación `./mvnw.cmd verify`: **BUILD SUCCESS**, 21 pruebas, 0 fallos.
- **T17 — Implementar descifrado AES-256-GCM autenticado:** completada. `CryptoService.decrypt` devuelve plaintext solo si GCM autentica correctamente. Verificación `./mvnw.cmd verify`: **BUILD SUCCESS**, 22 pruebas, 0 fallos.
- **T18 — Uniformar errores de datos y errores de argumentos:** completada. Fallos de clave/AAD/tag usan `DecryptionException` con mensaje fijo y sin causa; errores de uso permanecen distintos. Verificación `./mvnw.cmd verify`: **BUILD SUCCESS**, 24 pruebas, 0 fallos.
- **T19 — Probar round-trip, alteraciones y casos límite:** completada. Verificados plaintext vacío y de 1 MiB, AAD vacío/no coincidente, clave errónea y alteraciones de nonce/ciphertext/tag. Verificación `./mvnw.cmd verify`: **BUILD SUCCESS**, 25 pruebas, 0 fallos.
- **T20 — Revisar limpieza de secretos y mensajes:** completada. Revisión de limpieza, logging y excepciones; añadida aserción de error Unicode sin datos de entrada. Verificación `./mvnw.cmd verify`: **BUILD SUCCESS**, 25 pruebas, 0 fallos.
- **T21 — Verificar independencia del paquete `crypto`:** completada. Inspección de imports sin referencias a otros paquetes del proyecto. `./mvnw.cmd verify`: **BUILD SUCCESS**, 25 pruebas, 0 fallos.
- **T22 — Ejecutar verificación completa:** completada. `./mvnw.cmd verify`: **BUILD SUCCESS**, 25 pruebas, 0 fallos.
- **T23 — Medir manualmente el tiempo de derivación:** completada. Una derivación nominal (64 MiB, 3 iteraciones, paralelismo 1) midió **938,128 ms** en Windows, Temurin 21.0.13 y AMD Ryzen 5 4500; es una medición puntual, no garantía ni prueba automática.

## Decisiones aprobadas

- **Argon2id:** memoria mínima **19 MiB = 19 456 KiB**; máxima **256 MiB = 262 144 KiB**; iteraciones **2–10**; paralelismo **1–4**. Valores nominales: **64 MiB = 65 536 KiB, 3 iteraciones, paralelismo 1**. Rechazar fuera de rango antes de ejecutar KDF.
- **Dependencia Argon2:** `org.bouncycastle:bcprov-jdk18on:1.86`, versión fija. Verificación inicial de antigüedad y vulnerabilidades anotada en T01; no implica garantía de que no aparezcan avisos posteriores.
- **Contraseña:** la API recibe `char[]`; el llamador mantiene su propiedad y lo borra al terminar; el módulo no modifica el arreglo. Codificación **UTF-8**, normalización **Unicode NFC**, sin recortar espacios. Para la ruta ya normalizada se comprueba NFC por `CharBuffer` y no se crea un `String`; para una entrada no NFC se copia inmediatamente el `String` transitorio de `Normalizer.normalize` a un `char[]`, sin conservarlo ni registrarlo. El módulo borra los buffers temporales que crea.
- **UTF-8 malformado:** sustitutos UTF-16 aislados se rechazan; no se reemplazan silenciosamente.
- **Aleatoriedad:** el módulo crea y posee su propio `SecureRandom`; sal de **16 bytes** y nonce de **12 bytes** en cada cifrado. La unicidad de nonce aleatorio es probabilística; se prohíbe reutilizarlo con la misma clave.
- **Cifrado:** AES-256-GCM, clave de **32 bytes**, etiqueta de **128 bits**, AAD y nonce no fijable por el llamador.
- **Resultado cifrado:** `EncryptedData` será un tipo estructurado con nonce separado de 12 bytes y contenido AES-GCM con etiqueta al final. Validará longitudes y hará copias defensivas. La serialización del archivo corresponde a `storage`, no a `crypto`.
- **Clave derivada:** `DerivedKey` implementará `AutoCloseable` y sobrescribirá su arreglo al cerrarse; el llamador la usará con `try-with-resources`. El plaintext devuelto por descifrado pertenece al llamador, que debe borrarlo.
- **Pruebas Argon2id:** autorizada una ruta de prueba de bajo nivel para los bytes exactos de RFC 9106 sección 5.3; no se expondrá como API de producción.
- El vector RFC usa memoria de 32 KiB y se aplica exclusivamente en la prueba de conformidad; no altera los límites de parámetros de producción.
- **Errores:** errores de argumentos separados de fallos criptográficos/de datos; errores de clave, AAD o ciphertext no deben revelar la causa concreta ni devolver plaintext parcial.
- **Límite de memoria:** `docs/THREAT_MODEL.md` documenta que Java/JDK puede conservar copias no borrables de clave en `Cipher`/`SecretKeySpec` y un `String` transitorio en normalización NFC de entradas no normalizadas.

## Pendientes actuales

- **CI Linux para Spec 005:** aún no ejecutado para la implementación TOTP que
  está en el árbol de trabajo local. El run más reciente de GitHub es el de Spec
  004 enlazado arriba; el run de TOTP debe registrarse al publicar estos cambios.
- **Trazabilidad histórica:** están registrados los IDs/URLs de las ejecuciones
  Linux citadas para Specs 002/003 y 004.
- **Producto/entrega:** `AGENTS.md` ya define idioma, comando de ejecución y
  formato. Siguen pendientes una guía `README.md` y decidir si se distribuye un
  artefacto CLI autocontenido; son tareas de entrega, no bloqueos de las Specs
  001–005.

## Spec 003 — cli-mvp

- **Estado:** cerrada el 2026-11-14; T01–T26 completadas; AC-1–AC-13 cubiertos.
- **Plan:** `docs/specs/003-cli-mvp/plan.md`.
- **Tareas:** `docs/specs/003-cli-mvp/tasks.md`.
- **Alcance implementado:** punto de entrada `cl.germain.vault.cli.Main`,
  parser/ayuda y comandos interactivos `create`, `add`, `list`, `show` y
  `delete`; consola obligatoria, sin fallback de secretos a stdin; persistencia
  explícita para altas y borrados confirmados.
- **Última verificación:** `./mvnw.cmd verify` — **BUILD SUCCESS**, 77 pruebas,
  0 fallos y 1 omitida.
- **Verificación multiplataforma:** además de la verificación local en Windows,
  el workflow `.github/workflows/verify.yml` terminó en verde en GitHub Actions
  sobre Linux; la prueba de symlink y las comprobaciones POSIX ya no quedan
  pendientes. No se conserva aquí el enlace ni el identificador de esa ejecución.
- **Seguridad:** no se añadieron dependencias ni se modificaron el formato v1 o
  `docs/THREAT_MODEL.md`; no se registraron secretos en salidas/errores.

## Spec 004 — password-generator

- **Estado:** cerrada el 2026-11-14; T01–T12 completadas; AC-1–AC-8 cubiertos.
- **Spec:** `docs/specs/004-password-generator/spec.md`.
- **Plan:** `docs/specs/004-password-generator/plan.md`.
- **Tareas:** `docs/specs/004-password-generator/tasks.md`.
- **Alcance aprobado:** comando interactivo `generate`, longitud predeterminada
  de 20 (configurable de 12 a 128), salida solo por consola privada; sin guardar
  la contraseña en una bóveda.
- **Alcance implementado:** API `PasswordGenerator` y comando interactivo
  `generate [--length <n>]`; salida solo por consola privada, sin abrir ni guardar
  una bóveda.
- **Última verificación:** `./mvnw.cmd verify` — **BUILD SUCCESS**, 84 pruebas,
  0 fallos y 1 omitida (symlink no disponible en Windows).
- **Seguridad y arquitectura:** sin dependencias nuevas ni cambios al formato v1
  o `docs/THREAT_MODEL.md`; el arreglo generado se limpia en éxito y al fallar la
  impresión. `generator` no depende de otros paquetes del proyecto.

## Spec 005 — totp

- **Estado:** cerrada el 2026-11-15; T01–T35 completadas; AC-1–AC-10 cubiertos.
- **Spec/plan/tareas:** `docs/specs/005-totp/`.
- **Alcance implementado:** generación TOTP RFC 6238 SHA-1 y decodificación
  Base32; secretos opcionales borrables en el modelo; encabezado/payload v2,
  creación v2, persistencia v1 sin migración implícita y conversión explícita;
  CLI `set-totp`, `remove-totp`, `totp` y `upgrade`.
- **Formato/amenazas:** `docs/FORMAT.md` documenta v1/v2 y marca v2 como formato
  actual de creación. No se añadieron dependencias. `docs/THREAT_MODEL.md` se
  actualizó después con aprobación explícita del usuario para incluir secreto y
  uso TOTP, su canal de presentación y las limitaciones de memoria.
- **Verificación:** `./mvnw.cmd verify` — **BUILD SUCCESS**, 103 pruebas, 0
  fallos y 1 omitida.
- **Plataforma/limitaciones:** verificación local en Windows. La prueba de
  symlink se omitió porque no está disponible en este entorno. La conversión
  comprobó un reemplazo correcto y simuló el fallo de movimiento atómico mediante
  el hook de prueba; no se afirma que un filesystem real haya rechazado el move.
  Las copias internas que `Mac`/proveedor JDK pueda crear no son borrables por la
  aplicación. No se ejecutó CI Linux como parte de esta tarea.
