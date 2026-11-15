# AGENTS.md — password-vault

## Proyecto
Gestor de contraseñas cifrado, en Java 21, que guarda las credenciales en un
archivo de bóveda local cifrado. Es un proyecto de portafolio: la prioridad es
la corrección criptográfica y la claridad del código, no las funciones extra.
Alcance del MVP: CLI para crear una bóveda y agregar, listar, ver y borrar
entradas. Después: generador de contraseñas, autobloqueo, 2FA (TOTP).

## Seguridad (lo más importante)
- Cifrado: AES-256-GCM, con nonce aleatorio de 12 bytes por cifrado; la unicidad es probabilística y está prohibido reutilizar un nonce con la misma clave.
- Derivación de clave: Argon2id desde la contraseña maestra y una sal aleatoria.
  No se guarda ningún hash de la contraseña maestra: se sabe que es correcta
  porque el descifrado GCM no falla.
- Toda la aleatoriedad sale de `SecureRandom`.
- La contraseña maestra se maneja como `char[]`; el llamador es responsable de borrarla al terminar de usarla.
- Nada sensible va a logs, mensajes de error ni excepciones.
- Prohibido inventar algoritmos propios o reutilizar un nonce con la misma clave.
- El archivo de la bóveda lleva un número de versión de formato desde el inicio.

## Comandos
- Compilar y probar: `mvn verify`
- Solo tests: `mvn test`
- Ejecutar: `<por definir: clase principal cl.germain.vault.cli.Main>`
- Lint/formato: `<por definir: Spotless o Checkstyle>`

## Estilo y convenciones
- Java 21, Maven, paquetes por funcionalidad bajo `cl.germain.vault`:
  `crypto`, `model`, `storage`, `generator`, `totp`, `cli`.
- `crypto` no depende de ningún otro paquete; el resto depende de él.
- Código, nombres, comentarios y commits en inglés; mensajes que ve el usuario
  en español.
- Pruebas con JUnit 5, en el mismo paquete que la clase probada.

## Reglas
- Lee `docs/constitution.md` y la spec activa antes de tocar código.
- No agregar dependencias sin preguntar. Dependencias aprobadas: JUnit 5 y
  `org.bouncycastle:bcprov-jdk18on:1.86` para Argon2id, con versión exacta.
- No cambiar el formato del archivo de bóveda sin actualizar `docs/FORMAT.md`
  y subir el número de versión.
- No modificar `docs/THREAT_MODEL.md` sin que yo lo pida.
- No usar `String` para contraseñas, salvo el resultado transitorio de NFC para
  entradas no normalizadas: copiarlo inmediatamente a `char[]`, no conservarlo
  ni registrarlo, y reconocer que no puede borrarse explícitamente. No escribir
  datos sensibles en pruebas ni en archivos versionados.
- Si una decisión de seguridad no está en la spec, preguntar antes de decidir.

## Al terminar cualquier tarea
- Ejecutar `mvn verify` y confirmar que pasa todo.
- Cada decisión de seguridad nueva debe tener al menos una prueba.
- Actualizar `docs/` si cambió el formato o el modelo de amenazas.
- Resumir qué cambió y qué quedó pendiente.
