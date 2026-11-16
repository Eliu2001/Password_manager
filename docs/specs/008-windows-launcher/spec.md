# Spec 008 — Lanzador para Windows

**Estado:** cerrada el 2026-11-15; AC-1–AC-8 cubiertos.  
**Predecesora:** `docs/specs/007-distribution/spec.md`, cerrada y publicada.

## 1. Objetivo

Simplificar el inicio de Password Vault en Windows para que el usuario pueda
abrir una sesión sin escribir repetidamente el comando completo de Java ni la
ruta de la bóveda.

## 2. Alcance

**Dentro:**

- Añadir al repositorio un lanzador genérico `vault.cmd`, sin rutas personales.
- El lanzador espera `password-vault-1.0.0.jar` en la misma carpeta que
  `vault.cmd`.
- Configurar la ruta de bóveda mediante una variable `VAULT_PATH` editable al
  inicio del archivo. La ruta configurada debe ser explícita y admitir espacios.
- Al ejecutarlo, iniciar directamente el comando `session` sobre esa bóveda,
  con el timeout predeterminado de la CLI (cinco minutos).
- Añadir al README instrucciones en español para colocar el JAR y el lanzador,
  configurar `VAULT_PATH`, iniciar la sesión, finalizarla y resolver errores
  claros de configuración.
- Mantener la creación inicial de la bóveda como una operación separada y
  explícita con la CLI; el lanzador no debe crear ni elegir una bóveda por su
  cuenta.

**Fuera:**

- Lanzador personal, rutas específicas de una computadora o bóveda incluida en
  el repositorio/distribución.
- Instalador, acceso directo, interfaz gráfica, selección interactiva de rutas o
  persistencia automática de la ruta.
- Cambios al formato de bóveda, al código Java, al Release ya publicado o a la
  automatización de publicación.
- Incluir el lanzador como adjunto del Release `v1.0.0`; cualquier futura
  distribución del script en un Release requiere decidirse por separado.

## 3. Requisitos funcionales

- **RF-1 — Ubicación independiente del directorio actual:** resolver el JAR
  respecto de la carpeta del propio `vault.cmd`, incluso si se inicia mediante
  doble clic o desde otro directorio.
- **RF-2 — Bóveda explícita:** pasar `VAULT_PATH` como el valor de `--vault`,
  entrecomillado de forma que funcione con espacios. Un valor vacío o de ejemplo
  sin configurar debe producir un mensaje instructivo y no ejecutar la CLI.
- **RF-3 — Inicio de sesión:** invocar `java -jar <jar> session --vault
  <VAULT_PATH>` y conservar el comportamiento interactivo de la CLI, incluido
  el bloqueo por inactividad predeterminado.
- **RF-4 — Comprobaciones previas:** si falta el JAR o Java no está disponible,
  mostrar un mensaje en español que indique cómo corregirlo; no intentar iniciar
  una sesión incompleta.
- **RF-5 — Resultado visible:** mantener la ventana abierta al terminar o fallar
  para que el usuario pueda leer el resultado cuando el script se abre con doble
  clic. No ocultar el código de salida de la CLI ni mostrar datos de la bóveda.
- **RF-6 — Documentación:** explicar que Java 21 o posterior es requisito, que
  el JAR y el script van juntos, cómo editar la ruta absoluta de la bóveda y que
  primero se debe crear la bóveda si aún no existe.

## 4. Seguridad y restricciones

- El script no recibe, guarda ni transmite la contraseña maestra ni otros
  secretos. La CLI debe seguir solicitándolos en una terminal interactiva sin
  eco.
- No pasar secretos mediante argumentos, variables de entorno o entrada
  redirigida; no redirigir la consola de la CLI ni ejecutar el proceso mediante
  pipes.
- No usar `cd` hacia una ubicación implícita para decidir dónde está la bóveda;
  la ruta configurada determina el archivo que se abre.
- No incluir en el repositorio una ruta real del usuario, datos de prueba
  sensibles ni una bóveda.
- No agregar dependencias. No modificar `docs/THREAT_MODEL.md` ni el formato de
  bóveda.

## 5. Criterios de aceptación

- **AC-1:** existe `vault.cmd` versionado, genérico y sin rutas personales.
- **AC-2:** el script localiza el JAR en su propia carpeta, con independencia del
  directorio de trabajo actual.
- **AC-3:** una `VAULT_PATH` absoluta con espacios se pasa correctamente a la CLI
  como una sola ruta; una ruta sin configurar se rechaza antes de invocarla.
- **AC-4:** el lanzador invoca `session` sin cambiar sus requisitos de consola,
  manejo de secretos ni timeout predeterminado.
- **AC-5:** la ausencia de Java/JAR da un mensaje entendible y no ejecuta la
  sesión.
- **AC-6:** tras finalizar el proceso, la ventana del doble clic permanece
  disponible para leer mensajes y el estado de salida no se pierde.
- **AC-7:** README explica configuración, primer uso (crear bóveda por separado),
  inicio, cierre y requisitos; no recomienda incluir secretos en el script.
- **AC-8:** se valida el comportamiento del `.cmd` en Windows con escenarios de
  éxito y errores de configuración, además de ejecutar `./mvnw.cmd verify`.

## 6. Decisiones acordadas

- Configuración de la bóveda mediante una variable editable dentro del `.cmd`.
- JAR ubicado junto al `.cmd`.
- Inicio directo de una sesión con la ruta explícita configurada.

## 7. Cierre

La spec se cerró el 2026-11-15: AC-1–AC-8 cubiertos, README actualizado,
`./mvnw.cmd verify` en verde y escenarios del lanzador validados en Windows con
Java de prueba. La prueba manual complementaria de TTY real no se realizó y no
bloqueó el cierre. Esta spec no autorizó cambios en el Release ya publicado ni
la publicación de una nueva versión.
