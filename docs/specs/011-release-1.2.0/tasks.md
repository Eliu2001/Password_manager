# Tareas — Spec 011: Preparación del Release 1.2.0

- [x] **T01 — Actualizar versión y referencias** · R1, AC-1, AC-2
  - Cambiar POM y `vault.cmd` a 1.2.0.
  - Alinear README distinguiendo distribución publicada de candidato.
- [x] **T02 — Verificar build y artefacto** · R4, AC-4, AC-5
  - Ejecutar `./mvnw.cmd verify` y `java -jar target/password-vault.jar --help`.
- [x] **T03 — Preparar staging externo** · R2, R5, AC-3, AC-4
  - Crear carpeta solo si no existe; copiar JAR/launcher, calcular y verificar SHA.
- [x] **T04 — Registrar cierre local** · R6, AC-6
  - Actualizar `docs/STATUS.md` con tamaños, hash y pendiente de publicación.
  - Verificar diff; no crear tag/Release ni subir assets.

## Cierre

Cerrar T01–T04 únicamente con la evidencia registrada. El Release remoto queda
sin publicar hasta que el usuario realice manualmente los pasos correspondientes.

**Resultado — 2026-11-16:** POM y launcher actualizados; `./mvnw.cmd verify`
BUILD SUCCESS con 143 pruebas (0 fallos, 0 errores, 1 omitida por symlink no
disponible en Windows). La ayuda del JAR staged ejecutó con estado 0 y mostró
`start`. Staging externo contiene exactamente tres archivos: JAR (6,771,889
bytes), checksum (64 caracteres hexadecimales) y launcher (591 bytes). SHA-256
recalculado sobre la copia staged:
`c4bb0a28ab426c4ba1b29d1c6641f80c42d8fcd9026802c7a8689239a2fbc341`.
En el cierre de preparación no se creó tag/Release ni se subieron assets. El
usuario autorizó después el push del commit de cambios del repositorio; esa
autorización no incluye publicar el Release.
