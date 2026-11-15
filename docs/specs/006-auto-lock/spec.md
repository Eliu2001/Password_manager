# Spec 006 — Autobloqueo de sesión

**Estado:** cerrada el 2026-11-15; implementación autorizada el 2026-11-15.  
**Predecesora:** `docs/specs/005-totp/spec.md`, cerrada.

## 1. Objetivo

Añadir un modo interactivo de sesión que permita ejecutar varios comandos sobre
una bóveda desbloqueada y que la vuelva a bloquear tras un período de
inactividad. El autobloqueo debe cerrar los recursos descifrados y exigir de
nuevo la contraseña maestra antes de continuar.

## 2. Alcance

**Dentro:**

- Comando `session` que abre una bóveda y mantiene una sesión interactiva.
- Ejecución dentro de la sesión de los comandos compatibles con una bóveda ya
  abierta: `add`, `list`, `show`, `delete`, `set-totp`, `remove-totp`, `totp` y
  `upgrade`.
- Comandos de sesión `help` y `exit`.
- Timeout de inactividad configurable al iniciar la sesión, con valor
  predeterminado y límites definidos en esta spec.
- Bloqueo al vencer el timeout, cierre de la sesión de almacenamiento, limpieza
  de secretos/buffers bajo control de la aplicación y reautenticación para
  reanudar.
- Pruebas deterministas del reloj/timeout y de la liberación de recursos.

**Fuera:**

- Cambios al formato de bóveda o a las primitivas criptográficas.
- Persistir preferencias del timeout, configuraciones globales o una ubicación
  predeterminada de bóveda.
- Copiar secretos al portapapeles o limpiarlo; la CLI actual no usa portapapeles.
- Mantener la bóveda desbloqueada entre procesos o después de `exit`.
- Operación no interactiva, servicio en segundo plano, interfaz gráfica,
  sincronización o nuevos mecanismos de recuperación.
- Aplicar el timeout mientras un comando está ejecutándose; el período de
  inactividad se mide entre comandos. Los prompts que forman parte de un comando
  activo no suspenden ni interrumpen ese comando.

## 3. Interfaz

Entrada al modo de sesión:

```text
Main session --vault <ruta> [--timeout-minutes <n>]
```

- `--vault` es obligatorio. `--timeout-minutes` es opcional, acepta enteros de
  1 a 60 inclusive y usa 5 si se omite.
- La CLI requiere `System.console()` y solicita la contraseña maestra sin eco
  antes de abrir la bóveda.
- Dentro de la sesión, se aceptan los comandos de bóveda del alcance sin volver
  a indicar `--vault` ni solicitar la contraseña maestra en cada comando.
- `help` muestra la sintaxis disponible. `exit` cierra de forma ordenada la
  sesión y termina el proceso.
- La entrada de comandos no contiene secretos; estos continúan solicitándose
  mediante los prompts protegidos definidos por las specs previas.

## 4. Requisitos

### R1 — Entrada y autenticación de sesión

- El parser valida el comando, la ruta y el timeout antes de abrir archivos.
- La sesión requiere una terminal interactiva; si no hay consola, falla sin
  abrir ni modificar la bóveda y sin leer secretos desde argumentos, entorno o
  stdin como alternativa.
- La contraseña maestra se solicita sin eco y se maneja como `char[]`. Se borra
  cada arreglo bajo propiedad de la CLI al terminar la autenticación, tanto en
  éxito como en error/cancelación.
- Los errores de apertura/autenticación son genéricos, en español, y no incluyen
  secretos ni causas sensibles.

### R2 — Timeout e inactividad

- El timeout predeterminado es 5 minutos. `--timeout-minutes` acepta de 1 a 60
  minutos inclusive; valores no enteros o fuera del rango se rechazan antes de
  abrir la bóveda.
- El período de inactividad comienza al abrir la sesión y se reinicia cuando un
  comando de sesión termina. El cálculo temporal debe usar una fuente de tiempo
  monotónica, inyectable en pruebas; cambios de hora del sistema no deben alargar
  ni acortar el timeout.
- El timeout se evalúa mientras la sesión espera el siguiente comando. Al vencer,
  la CLI descarta cualquier línea de comando pendiente, bloquea la bóveda y pide
  de nuevo la contraseña maestra antes de aceptar otro comando.
- Un comando ya iniciado se ejecuta hasta terminar; el timeout no lo interrumpe
  a mitad de una mutación o guardado. Al terminar, si el plazo ya venció, se
  bloquea antes de despachar el siguiente comando.
- No se registra la actividad ni el tiempo de inactividad en logs.

### R3 — Bloqueo, reanudación y salida

- Al bloquear, la CLI cierra `VaultSession`, liberando el bloqueo exclusivo,
  borrando la clave derivada y cerrando el `Vault` conforme al contrato de
  Storage. La bóveda no queda abierta en memoria por una referencia retenida por
  la sesión de CLI.
- La reanudación solicita otra vez la contraseña maestra sin eco y abre una
  sesión nueva. Un fallo de autenticación no desbloquea la bóveda ni termina
  exponiendo datos; se permite volver a intentar o salir.
- `exit`, EOF, interrupción y errores fatales cierran los recursos en todos los
  caminos. Ningún error informa éxito si el comando o el cierre fallaron.
- Los cambios confirmados por comandos se guardan con las reglas existentes. El
  autobloqueo no guarda cambios implícitamente; una operación cancelada o fallida
  no debe producir persistencia parcial.

### R4 — Comandos dentro de la sesión

- `add`, `list`, `show`, `delete`, `set-totp`, `remove-totp`, `totp` y `upgrade`
  conservan sus validaciones, confirmaciones, salida y reglas de persistencia
  definidas en las Specs 003 y 005, excepto que usan la sesión ya autenticada.
- `create` no está disponible dentro de una sesión abierta. `generate` sigue
  disponible como comando independiente y no requiere abrir la bóveda.
- Un error recuperable de un comando informa el error genérico correspondiente y
  permite continuar en la sesión sin exponer datos ni dejar recursos temporales
  abiertos. Los errores de sesión/storage que invaliden el estado cierran la
  sesión de forma segura.

### R5 — Secretos y seguridad

- Ninguna contraseña maestra, contraseña de entrada, secreto TOTP, código TOTP,
  nota ni otro contenido sensible aparece en la línea de comandos, historial de
  comandos, logs, errores o salidas redirigibles.
- Los arreglos y buffers temporales bajo control de la aplicación se limpian en
  éxito, error, cancelación, autobloqueo y salida. No se promete borrar copias
  internas no controlables del JDK ni del proveedor criptográfico.
- La implementación no agrega dependencias ni cambia la versión/formato de la
  bóveda. `crypto` continúa independiente de los demás paquetes.

## 5. Criterios de aceptación

| ID | Criterio |
|---|---|
| AC-1 | `session` exige `--vault`, reconoce timeout opcional y rechaza argumentos ausentes, duplicados, desconocidos, no enteros o fuera de 1–60 antes de acceder a archivos. |
| AC-2 | Sin consola interactiva, la sesión falla antes de abrir/modificar la bóveda y no obtiene secretos de argumentos, entorno ni stdin. |
| AC-3 | El timeout predeterminado es 5 minutos; el reloj inyectable y monotónico permite probar los límites y el vencimiento sin esperas reales. |
| AC-4 | Al vencer mientras espera un comando, se descarta la entrada pendiente, se cierra `VaultSession` y no se procesa ningún comando hasta reautenticarse correctamente. |
| AC-5 | La sesión se reanuda solo después de una autenticación válida; contraseñas incorrectas/cancelación no revelan contenido y permiten reintentar o salir de manera segura. |
| AC-6 | Cada comando completado reinicia el período; los cambios de hora de pared no alteran la duración; ningún comando activo se interrumpe por el timeout. |
| AC-7 | Los comandos habilitados conservan el comportamiento/persistencia de las Specs 003/005 y no solicitan de nuevo la contraseña maestra, salvo en la reanudación tras bloqueo. |
| AC-8 | `exit`, EOF, interrupciones y errores cierran sesión, liberan el lock y limpian buffers controlables; el autobloqueo nunca guarda implícitamente. |
| AC-9 | Secretos y contenido de la bóveda no aparecen en historial/comandos, logs, errores ni canales redirigibles. |
| AC-10 | No se añaden dependencias ni cambia el formato; `./mvnw.cmd verify` termina correctamente y la documentación de estado refleja las limitaciones de plataforma. |

## 6. Pruebas requeridas

JUnit 5 con consola sustituible, reloj monotónico inyectable y Storage temporal.
No se incluirán secretos reales ni archivos de bóveda versionados.

La cobertura incluirá:

- Parsing de la sintaxis de sesión, valores predeterminados y límites del
  timeout; rechazo antes de acceso a archivos.
- Ausencia de consola, autenticación correcta/incorrecta/cancelada y limpieza de
  arreglos de contraseña.
- Timeout al inicio, justo antes del límite y al vencer; reinicio después de un
  comando; independencia respecto a cambios del reloj de pared.
- Expiración durante la espera de un comando, descarte de la línea pendiente y
  comprobación de que no se ejecuta hasta reautenticar.
- Reanudación correcta/incorrecta, `help`, `exit`, EOF e interrupción.
- Ejecución de comandos existentes en la sesión, persistencia, cancelación,
  errores recuperables/fatales y cierre de locks/sesiones.
- Limpieza al bloquear de `Vault`, clave derivada y buffers bajo control de la
  aplicación, sin afirmar borrado de copias internas del JDK.
- Revisión de canales para asegurar que comandos, historial, errores y logs no
  revelan secretos o contenido.

## 7. Documentación y cierre

La implementación no cambia `docs/FORMAT.md`. Se actualizarán `AGENTS.md` o la
guía de uso cuando se documente el nuevo comando. `docs/STATUS.md` registrará el
cierre, verificación y limitaciones de plataforma. `docs/THREAT_MODEL.md` no se
modificará como parte de esta spec sin autorización expresa; antes de implementar
se revisará si el comportamiento aprobado requiere una actualización de ese
modelo y se solicitará autorización si fuera necesaria.

La Spec 006 fue aprobada, implementada y cerrada el 2026-11-15. T01–T26 están
completadas; AC-1–AC-10 tienen evidencia en pruebas y revisión documentada en
`tasks.md`. `./mvnw.cmd verify` terminó correctamente: 120 pruebas, 0 fallos,
0 errores y 1 omitida por limitación de symlink en Windows. La ejecución real en
Linux queda pendiente de CI; no se afirma cobertura de esa plataforma.
