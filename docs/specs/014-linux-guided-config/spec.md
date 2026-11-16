# Spec 014 — Configuración del asistente guiado en Linux

**Estado:** aprobada por el usuario el 2026-11-16; implementación autorizada.
**Predecesora:** Spec 010 (`guided-console-ux`), cerrada.

## 1. Objetivo

Permitir que el comando `start` del JAR publicado funcione en Linux, guardando la
ruta de la bóveda en una ubicación de configuración propia de Linux, sin cambiar
el comportamiento existente de Windows ni el formato de la bóveda.

## 2. Alcance

**Dentro:**

- Resolver en Linux la ubicación de configuración usando `XDG_CONFIG_HOME` cuando
  sea una ruta absoluta; si no está definida o no es absoluta, usar
  `~/.config/password-vault/vault-path.txt`.
- Conservar en Windows la ubicación `%LOCALAPPDATA%/PasswordVault/vault-path.txt`.
- Restringir en Linux los permisos del directorio de la aplicación al usuario
  (0700) y del archivo de ruta (0600), incluyendo configuración preexistente.
- Mantener el archivo limitado a una sola ruta absoluta UTF-8; no guardar
  contraseñas maestras, credenciales, secretos TOTP ni contenido de bóveda.
- Actualizar README y `docs/STATUS.md` para documentar el inicio asistido en Linux
  y sus límites.

**Fuera:**

- Cambiar el formato o cifrado de la bóveda, comandos distintos de `start`, menú,
  modelo de amenazas o dependencias.
- Cambiar la ubicación/configuración de Windows, implementar macOS o crear un
  instalador nativo/runtime.
- Guardar automáticamente contraseñas o crear una bóveda sin elección explícita.

## 3. Decisiones acordadas

- La corrección solicitada cubre Linux; Windows conserva su ruta actual.
- La ubicación Linux sigue XDG Base Directory: `XDG_CONFIG_HOME` absoluto cuando
  se proporcione; en otro caso, `$HOME/.config/password-vault/vault-path.txt`.
- La ruta guardada es metadato local que puede revelar la ubicación de la bóveda;
  en Linux se protegerá con permisos POSIX 0700 para el directorio de aplicación
  y 0600 para el archivo.
- No se cambian dependencias, formato de bóveda ni `docs/THREAT_MODEL.md`.

## 4. Requisitos

- **R1 — Resolución Linux:** en Linux, `start` resolverá la configuración desde
  `XDG_CONFIG_HOME/password-vault/vault-path.txt` solo si la variable contiene una
  ruta absoluta. Si no existe, está vacía o es relativa, usará
  `$HOME/.config/password-vault/vault-path.txt`. Si no hay un `HOME` utilizable,
  fallará con un mensaje claro y seguro, sin acceder a una ruta predeterminada ni
  a la bóveda.
- **R2 — Compatibilidad Windows:** Windows seguirá leyendo/escribiendo
  `%LOCALAPPDATA%/PasswordVault/vault-path.txt`; los comandos existentes no
  cambiarán.
- **R3 — Privacidad POSIX:** al crear/actualizar configuración en Linux, el
  directorio de aplicación tendrá permisos 0700 y el archivo 0600. La escritura
  continuará siendo atómica con temporal en el mismo directorio. Los fallos al
  establecer permisos o escribir se reportarán sin datos sensibles y no se
  continuará como si la ruta hubiese quedado guardada.
- **R4 — Contenido y validación:** conservar UTF-8 estricto, una ruta absoluta en
  una sola línea y rechazo de configuración malformada. No escribir secretos ni
  contenido de bóveda.
- **R5 — Asistente:** `start` podrá crear o abrir una bóveda en Linux y entrar al
  menú guiado tras guardar correctamente su ruta. La consola interactiva y el
  flujo explícito Crear/Abrir/Salir siguen siendo obligatorios.
- **R6 — Documentación:** README distinguirá correctamente el uso Linux de
  `start`; `docs/STATUS.md` registrará pruebas y plataformas verificadas. No
  afirmar compatibilidad macOS como parte de este cambio.
- **R7 — Alcance seguro:** no cambiar cifrado, formato, dependencias ni
  `docs/THREAT_MODEL.md`; no pasar secretos por argumentos, entorno, stdin, pipes
  o redirecciones.

## 5. Criterios de aceptación

- **AC-1:** en Linux, un `XDG_CONFIG_HOME` absoluto se utiliza como base y uno
  relativo, vacío o ausente produce el fallback a `$HOME/.config`.
- **AC-2:** sin `HOME` utilizable y sin `XDG_CONFIG_HOME` absoluto, `start` falla
  de forma clara antes de crear/abrir una bóveda.
- **AC-3:** tras guardar la configuración Linux, el directorio de aplicación es
  0700 y el archivo `vault-path.txt` es 0600; una ruta de archivo existente con
  permisos más abiertos queda restringida.
- **AC-4:** lectura/escritura mantiene la validación UTF-8 y de ruta actual;
  la escritura atómica preserva el archivo previo ante errores y no deja
  temporales tras completar o fallar.
- **AC-5:** pruebas en Linux cubren resolución XDG/fallback, permisos, persistencia,
  configuración malformada y fallo seguro. La suite existente Windows confirma
  que la ruta `%LOCALAPPDATA%` sigue funcionando.
- **AC-6:** el `start` real en Linux puede completar el flujo asistido en una TTY
  y abrir el menú; no se automatizan contraseñas reales mediante pipes ni se
  guardan secretos en pruebas.
- **AC-7:** README ya no indica usar `start` en Linux antes de esta corrección y,
  tras implementarla, describe ubicación y comandos; `docs/STATUS.md` registra
  evidencia. `mvn verify` pasa.
- **AC-8:** no cambian los formatos v1/v2, dependencias, cifrado ni Threat Model.

## 6. Pruebas y seguridad

- Probar resolución de rutas con variables de entorno inyectables, incluyendo
  `XDG_CONFIG_HOME` absoluto/relativo/ausente y `HOME` ausente.
- En Linux, verificar permisos POSIX exactos del subdirectorio de la aplicación,
  el archivo final y el temporal cuando corresponda; comprobar endurecimiento de
  una configuración preexistente.
- Probar fallos de creación, permisos y reemplazo atómico: informar error seguro,
  no abrir otra bóveda por fallback y cerrar la sesión si ya se había abierto.
- Mantener pruebas de UTF-8, rutas absolutas, contenido de una línea y limpieza de
  buffers temporales. No incluir contraseñas, secretos ni bóvedas reales.
- Ejecutar `mvn verify` y validar el flujo de `start` con terminal Linux real;
  registrar por separado pruebas automatizadas y prueba manual TTY.

## 7. Documentación y cierre

Actualizar README y `docs/STATUS.md`. No modificar `docs/FORMAT.md` ni
`docs/THREAT_MODEL.md`. Cerrar cuando AC-1–AC-8 estén cubiertos, `mvn verify`
pase en Linux y Windows, y se registre el resultado de la prueba TTY Linux.

La spec queda pendiente de aprobación; no autoriza implementación hasta que el
usuario apruebe explícitamente este alcance.
