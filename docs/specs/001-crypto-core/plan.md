# Plan técnico — Spec 001: crypto-core

**Estado:** cerrado el 2026-11-12 tras completar T01–T23 y verificar todos los criterios.  
**Alcance:** plan de implementación del módulo criptográfico; no define el formato del archivo de bóveda.

## 1. Contexto

El proyecto usa Java 21 y Maven. `pom.xml` fija JUnit Jupiter 5.14.4 y
`org.bouncycastle:bcprov-jdk18on:1.86`. El paquete `crypto` y las pruebas
correspondientes están implementados. La Spec 001 está cerrada.

El módulo ofrecerá derivación Argon2id, generación de sal, cifrado AES-256-GCM y descifrado autenticado. Debe mantenerse independiente de los demás paquetes del proyecto.

## 2. Decisiones aprobadas

### Argon2id

- Memoria admitida: **19–256 MiB**, equivalente a **19 456–262 144 KiB** en la interfaz Argon2.
- Iteraciones: **2–10**.
- Paralelismo: **1–4**.
- Valores nominales ya registrados en `docs/THREAT_MODEL.md`: **64 MiB, 3 iteraciones y paralelismo 1**.
- Rechazar parámetros fuera de estos rangos antes de iniciar la derivación, incluidos los recibidos posteriormente desde una cabecera.
- Derivar 32 bytes con Argon2id versión 19, sujeto a que la librería aprobada soporte y pase los vectores establecidos.

### Contraseña y secretos

- La API recibe `char[]`; el llamador conserva su propiedad. El módulo no modifica ni borra ese arreglo.
- La conversión a bytes será UTF-8 y la semántica de contraseña aplicará normalización Unicode NFC, sin recortar espacios.
- La codificación UTF-8 será estricta y rechazará sustitutos UTF-16 aislados; no se reemplazarán silenciosamente.
- Para evitar crear un `String` en el caso común, comprobar si el `char[]` ya está en NFC envolviéndolo en un `CharBuffer` y pasándolo a `Normalizer.isNormalized`. Si ya está normalizado, codificarlo directamente como UTF-8. Solo si no está en NFC, llamar a `Normalizer.normalize`, copiar inmediatamente su resultado a un `char[]` temporal y no conservar referencias al `String` transitorio. El temporal propio se borra al terminar; el `String` retornado por el JDK no puede borrarse y se declara como limitación de memoria.
- El módulo sobrescribe los bytes de contraseña codificados y los buffers temporales que cree, incluidas las copias internas de plaintext que estén bajo su control.
- Los bytes UTF-8 se entregan a la operación consumidora solo durante una llamada acotada y se borran al retornar o lanzar una excepción.
- La clave derivada se entrega mediante un objeto propio `DerivedKey`, que implementa `AutoCloseable` y borra su arreglo al cerrarse. El llamador debe cerrarlo, preferiblemente con `try-with-resources`.
- El plaintext devuelto por descifrado pertenece al llamador, que debe borrarlo cuando termine de usarlo.
- No se registran secretos en logs, mensajes ni causas de excepciones.

### Aleatoriedad y cifrado

- El módulo crea y posee su propio `SecureRandom`.
- Generar sales aleatorias de 16 bytes.
- Generar un nonce aleatorio de 12 bytes en cada cifrado. La unicidad es probabilística, no una garantía absoluta; no se debe reutilizar deliberadamente un nonce con la misma clave.
- Usar AES-256-GCM con etiqueta de 128 bits.

### Dependencia Argon2

- Dependencia aprobada: `org.bouncycastle:bcprov-jdk18on`.
- La versión debe fijarse exactamente en `pom.xml`, sin rangos; verificar publicación en Maven Central, antigüedad mínima de dos semanas, vulnerabilidades conocidas y vectores RFC 9106 antes de incorporarla.
- Consulta realizada el 11 de noviembre de 2026: Maven Central mostraba 1.87 publicada ese día, mientras 1.86 estaba publicada desde el 11 de septiembre de 2026. Snyk reportó cero vulnerabilidades directas conocidas aplicables a 1.86 en esa consulta (no es garantía de ausencia futura).
- Dependencia 1.86 incorporada con versión fija en `pom.xml` y registrada en `AGENTS.md`; el vector RFC 9106 §5.3 pasó en T09.

## 3. Diseño propuesto

### Componentes y API conceptual

- `Argon2Parameters`: valores explícitos de memoria, iteraciones y paralelismo, con validación de rangos.
- Servicio o funciones `crypto` para derivar clave, generar sal, cifrar y descifrar. No importar clases de otros paquetes del proyecto.
- `EncryptedData`: tipo estructurado con nonce de 12 bytes y contenido cifrado que contiene ciphertext seguido por la etiqueta GCM, conforme a la salida del JDK.
- `DerivedKey`: propietario interno de los 32 bytes derivados; `close()` sobrescribe el arreglo y deja el objeto inutilizable.

Los tipos que contienen arreglos validan sus longitudes al construirse y hacen copias defensivas al recibir y exponer los arreglos.

### Separación de responsabilidades

`crypto` opera sobre bytes y tipos propios; no conoce la disposición del archivo ni depende de `storage`. `storage` decidirá la serialización del nonce y ciphertext conforme a `FORMAT.md`. Ese archivo no existe actualmente, por lo que el diseño de integración queda pendiente de la spec de almacenamiento.

## 4. Validaciones y errores

- Rechazar argumentos nulos, claves distintas de 32 bytes, sales distintas de 16 bytes, contraseñas vacías y parámetros Argon2id fuera de rango.
- Separar errores de uso de API (por ejemplo, argumentos nulos o longitudes inválidas) de fallos de datos o secretos durante el descifrado.
- Clave incorrecta, AAD distinto, ciphertext malformado o alterado producen el mismo tipo de excepción criptográfica externa, sin revelar cuál fue la causa y sin causas que expongan secretos.
- Fallar de forma cerrada: no devolver plaintext antes de completar la autenticación ni devolver resultados parciales.
- No se fijan límites máximos adicionales para plaintext, AAD o ciphertext; la spec actual los deja limitados por la memoria disponible y la plataforma.

## 5. Pruebas y criterios de aceptación

Usar JUnit 5, con pruebas en el mismo paquete que las clases probadas.

- **Argon2id:** vectores conocidos de RFC 9106, longitud de salida de 32 bytes, repetibilidad para entradas iguales y resultados de prueba distintos al variar contraseña, sal o parámetros.
- **Validación de parámetros:** probar ambos extremos admitidos y valores inmediatamente fuera de cada extremo; cubrir combinaciones inválidas.
- **Secreto de contraseña:** verificar que el módulo no modifica el `char[]` del llamador y borrar los buffers codificados temporales que cree.
- **`DerivedKey`:** verificar longitud, disponibilidad antes de cerrar, borrado al cerrar y rechazo de uso posterior.
- **Sal:** longitud de 16 bytes y comportamiento aleatorio esperado en llamadas consecutivas.
- **AES-GCM:** cifrar-descifrar el mismo plaintext, incluyendo vacío y grande; probar clave incorrecta, AAD distinto y alteraciones de nonce, ciphertext y etiqueta.
- **`EncryptedData`:** longitudes inválidas rechazadas y aislamiento mediante copias defensivas de entrada y salida.
- **Errores:** verificar distinción entre errores de argumentos y criptográficos; mensajes y causas sin secretos; ausencia de plaintext parcial.
- **Nonce:** comprobar en pruebas que las llamadas generan nonces distintos en las muestras examinadas; esto no demuestra unicidad absoluta.
- Tras implementar, ejecutar `mvn verify`.

## 6. Riesgos y limitaciones

- Si la contraseña no está en NFC, la ruta aprobada requiere un `String` transitorio retornado por `Normalizer.normalize`; no se guarda ni registra, pero no se puede borrar explícitamente. La ruta común para contraseñas ya normalizadas evita esa conversión.
- El vector Argon2id de RFC 9106 usa bytes de contraseña arbitrarios; se probó por una ruta de bajo nivel solo en tests, sin alterar la semántica de la API de producción.
- En Java no puede garantizarse el borrado de copias internas que el JDK mantenga en `Cipher` o `SecretKeySpec`. Registrar esta limitación en `docs/THREAT_MODEL.md` mediante el proceso de aprobación requerido.
- La especificación de formato aún no existe en `docs/FORMAT.md`; este plan no define ni modifica dicho formato.
- La aleatoriedad de 96 bits hace extremadamente improbable una colisión de nonce, pero no la hace matemáticamente imposible; `AGENTS.md` y la spec describen esta unicidad como probabilística y prohíben la reutilización deliberada.

## 7. Resolución de preguntas y cierre

1. **Vector RFC 9106:** resuelto en T09; se comparan los bytes de salida del vector oficial en una prueba exclusiva.
2. **Nonce y contraseña:** resueltos y reflejados en spec/AGENTS.md; el llamador conserva la propiedad de `char[]` y la unicidad del nonce es probabilística.
3. **Formato de storage:** queda fuera de esta spec. `crypto` entrega `EncryptedData`; la spec de storage y `docs/FORMAT.md` definirán serialización y versión.
4. **Verificación:** T01–T23 completadas; `./mvnw.cmd verify` pasa con 25 pruebas y 0 fallos. La medición nominal manual de Argon2id registró 938,128 ms en el entorno anotado en T23.

No quedan bloqueos dentro del alcance de crypto-core. La ausencia de `docs/FORMAT.md` es una dependencia futura de storage, no un criterio pendiente de esta spec.

## 8. Fuentes

- `AGENTS.md`
- `docs/constitution.md`
- `docs/specs/001-crypto-core/spec.md`
- `docs/THREAT_MODEL.md`
- RFC 9106, sección 5.3: <https://www.rfc-editor.org/rfc/rfc9106.html>
- Maven Central: <https://repo.maven.apache.org/maven2/org/bouncycastle/bcprov-jdk18on/>
- Verificación de vulnerabilidades consultada: <https://security.snyk.io/package/maven/org.bouncycastle%3Abcprov-jdk18on>
