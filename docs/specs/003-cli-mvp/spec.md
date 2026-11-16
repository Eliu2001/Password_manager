# Spec 003 — cli-mvp

**Estado:** aprobada el 2026-11-12; implementación autorizada.  
**Fecha:** 2026-11-12  
**Predecesora:** `docs/specs/002-vault-storage/spec.md`, cerrada.

## 1. Objetivo

Definir una interfaz de línea de comandos interactiva en español para el MVP
del gestor: crear una bóveda y agregar, listar, consultar y borrar entradas.
La CLI coordina el modelo y `VaultStorage`; no implementa criptografía ni
cambia el formato binario v1.

## 2. Alcance

**Dentro:**

- Punto de entrada `cl.germain.vault.cli.Main` y comandos `create`, `add`,
  `list`, `show` y `delete`.
- Ruta de bóveda explícita en cada operación mediante `--vault <ruta>`; no se
  establece ubicación predeterminada.
- Entrada interactiva de datos y contraseñas, presentación de resultados y
  errores en español.
- Uso de `VaultStorage` y operaciones de `VaultSession`; persistencia explícita
  de altas y borrados confirmados.
- Validación de argumentos, IDs, campos requeridos y cierre/limpieza de secretos.

**Fuera:**

- Ubicación predeterminada o descubrimiento automático de bóvedas.
- Secretos en argumentos, variables de entorno o stdin; operación no interactiva.
- Generador de contraseñas, autobloqueo, TOTP, búsqueda, edición de entradas,
  exportación/importación y sincronización.
- Cambios al formato de archivo, al modelo de amenazas o a las primitivas
  criptográficas.
- Instalador, ejecutable nativo, autocompletado del shell y empaquetado de
  distribución. La clase indicada será el punto de entrada Java.

## 3. Interfaz

La sintaxis lógica de los comandos es:

```text
Main create --vault <ruta>
Main add --vault <ruta>
Main list --vault <ruta>
Main show --vault <ruta> --id <id>
Main delete --vault <ruta> --id <id>
Main --help
```

`Main` representa la invocación de `cl.germain.vault.cli.Main`. `--vault` es
obligatorio y se acepta una sola vez en cada comando operativo. `--id` es
obligatorio en `show` y `delete`, y no se acepta en los demás comandos. Los
argumentos desconocidos, repetidos o incompletos se rechazan antes de abrir o
crear archivos. `--help` presenta comandos y sintaxis sin solicitar secretos ni
acceder a una bóveda.

Los IDs se muestran y reciben como exactamente 32 dígitos hexadecimales en
minúscula, correspondientes a los 16 bytes del modelo.

## 4. Requisitos

### R1 — Consola interactiva y fallos de entrada

Todo comando operativo requiere `System.console()` disponible. Si no hay
consola, la CLI informa en español que necesita una terminal interactiva, sale
con estado distinto de cero y no crea, abre, modifica ni guarda la bóveda. No
lee contraseñas desde argumentos, variables de entorno ni stdin como alternativa.

La interrupción de una lectura o una respuesta ausente cancela la operación.
Los recursos abiertos se cierran y los arreglos de secretos bajo propiedad de la
CLI se borran. Los errores de lectura no deben incluir el contenido introducido.

### R2 — Creación

`create` pide la contraseña maestra dos veces, sin eco. Las dos entradas deben
coincidir; si no coinciden, cancela antes de crear el archivo. Si coinciden,
llama a `VaultStorage.create` con la ruta indicada. La creación conserva las
reglas de Storage: no sobrescribe una bóveda existente, no crea directorios
padre y deja una bóveda vacía cifrada. La CLI no conserva la contraseña al
terminar y borra las copias de `char[]` que posea, también cuando ocurre un
error.

### R3 — Apertura de bóvedas existentes

`add`, `list`, `show` y `delete` piden la contraseña maestra una vez, sin eco,
y abren la ruta indicada mediante `VaultStorage.open`. Una contraseña incorrecta
se comunica con un mensaje genérico en español, sin distinguir causas de
autenticación ni incluir datos sensibles. La CLI no almacena ni conserva la
contraseña maestra después de la operación.

Cada sesión se cierra en todos los caminos. Los comandos de consulta no guardan;
`add` guarda después de agregar correctamente y `delete` solo después de una
confirmación positiva. Ante error de guardado, se informa el fallo sin mostrar
causas que contengan datos y se cierra la sesión según el contrato de Storage.

### R4 — Alta interactiva

`add` solicita, en este orden, servicio, usuario, contraseña y notas. Todos los
campos se introducen por prompts; la contraseña usa lectura sin eco y permanece
como `char[]`. Servicio, usuario y contraseña son obligatorios y no vacíos; las
notas pueden quedar vacías. Las entradas se conservan exactamente como se
introducen: la CLI no recorta espacios ni aplica normalización Unicode.

Si la validación falla, no se agrega ni se guarda una entrada parcial. En éxito,
la CLI agrega la entrada a la sesión, llama a `save()` y muestra el ID asignado
en formato de 32 dígitos hexadecimales en minúscula. Borra los arreglos de
contraseña temporales que posea después de su uso.

### R5 — Listado

`list` presenta las entradas en el orden de la bóveda, con ID, servicio y
usuario. No muestra contraseñas ni notas. Una bóveda vacía produce un mensaje
claro en español. El comando no llama a `save()`.

### R6 — Consulta y revelación de contraseña

`show` recibe un ID válido y completo. Si no existe, informa que no se encontró
la entrada sin exponer información de otras entradas. Si existe, presenta ID,
servicio, usuario y notas, y pide confirmación antes de revelar la contraseña.
Solo las respuestas exactas `sí` o `si` autorizan la revelación; cualquier otra
respuesta, incluida una respuesta vacía, con espacios adicionales o mayúsculas,
cancela la revelación. La contraseña se escribe
únicamente en la consola interactiva, no en logs, errores ni una salida destinada
a pipes. La copia `char[]` de la contraseña se borra después de imprimirla o
cancelar. `show` no guarda cambios.

### R7 — Borrado confirmado

`delete` recibe un ID válido y completo. Si existe la entrada, muestra su ID,
servicio y usuario, nunca la contraseña, y pide confirmación. Solo la respuesta
exacta `sí` o `si` borra la entrada y ejecuta `save()`. Cualquier otra respuesta,
incluidos espacios adicionales y mayúsculas, cancela sin guardar; cancelar es un
resultado normal y no modifica el archivo. Un ID
desconocido se informa sin modificar la bóveda.

### R8 — Validación de argumentos e IDs

El parser valida el nombre del comando, las opciones requeridas, las opciones
duplicadas y las opciones no admitidas antes de cualquier operación de archivos.
Los IDs de `show` y `delete` deben ser exactamente 32 caracteres hexadecimales
en minúscula; IDs inválidos se rechazan antes de abrir la bóveda. Los mensajes
de uso y validación se presentan en español, sin trazas de excepción.

### R9 — Errores y confidencialidad

Los mensajes visibles al usuario son en español. No se imprimen ni registran
contraseñas, claves, plaintext, contenido de entradas ni causas de autenticación.
La contraseña maestra y las contraseñas de entradas no se aceptan en la línea de
comandos ni se convierten a `String`. La CLI falla de forma cerrada ante errores
de validación, apertura, lectura, descifrado, persistencia o interacción: no
presenta datos parciales ni informa éxito si la operación no terminó.

La salida de ayuda y errores de uso no requiere abrir una bóveda. Un comando
completado correctamente devuelve estado cero; errores de argumentos, consola,
lectura, storage o autenticación devuelven estado distinto de cero. Cancelar un
borrado o cancelar la revelación de una contraseña no es un error.

### R10 — Arquitectura y dependencias

La CLI reside en `cl.germain.vault.cli` y depende de `storage` y `model`; no se
añaden dependencias sin aprobación. `crypto` sigue independiente de la CLI. La
CLI no cambia `docs/FORMAT.md` ni `docs/THREAT_MODEL.md` porque esta spec no
cambia el formato ni el modelo de amenazas.

## 5. Criterios de aceptación

| ID | Criterio |
|---|---|
| AC-1 | `Main` reconoce los cinco comandos acordados, `--help` y la sintaxis de opciones; argumentos ausentes, duplicados o desconocidos se rechazan antes de acceder a archivos. |
| AC-2 | Todos los comandos operativos requieren `--vault`; no hay ruta predeterminada y los directorios padre no se crean. |
| AC-3 | Sin `System.console()`, cada comando operativo falla antes de mutar archivos y no lee secretos desde argumentos, entorno o stdin. |
| AC-4 | `create` solicita dos veces la contraseña maestra sin eco; discrepancia o cancelación no crea bóveda; coincidencia crea bóveda vacía sin sobrescribir. |
| AC-5 | La contraseña maestra se solicita sin eco al abrir una bóveda, se usa como `char[]`, se borra la copia que posee la CLI y fallos de autenticación no revelan su causa. |
| AC-6 | `add` solicita todos los campos, mantiene exactos los campos de texto, valida obligatorios, no guarda entradas parciales y en éxito guarda y presenta un ID de 32 caracteres hexadecimales en minúscula. |
| AC-7 | `list` conserva orden y muestra solo ID, servicio y usuario; no muestra contraseña ni notas, incluso para una bóveda vacía. |
| AC-8 | `show` no revela la contraseña sin la respuesta exacta `sí` o `si`; al confirmarla la muestra en consola y limpia la copia `char[]`; IDs inválidos o desconocidos no revelan otra entrada. |
| AC-9 | `delete` modifica y guarda únicamente ante la respuesta exacta `sí` o `si`; cancelación, ID inválido o desconocido dejan el archivo sin cambios. |
| AC-10 | Operaciones de consulta nunca guardan cambios; sesiones y recursos se cierran ante éxito, cancelación y error. |
| AC-11 | Errores y salidas de CLI están en español y no incluyen secretos, contenido de entradas, trazas ni causas sensibles; no se reporta éxito ante una operación fallida. |
| AC-12 | Las pruebas automatizadas cubren parsing, mensajes/estados, no consola, flujo de cada comando, cancelaciones, persistencia y limpieza de arreglos sensibles bajo propiedad de la CLI. |
| AC-13 | No se agregan dependencias, no cambia el formato v1 y `./mvnw.cmd verify` termina correctamente. |

## 6. Pruebas requeridas

Las pruebas JUnit 5 usan directorios temporales, datos sintéticos y una
abstracción de consola sustituible en pruebas para simular entradas, cancelación,
ausencia de terminal y captura separada de salida normal/errores. No se incluirán
contraseñas reales ni archivos de bóveda versionados.

La cobertura incluirá, al menos:

- Tabla de comandos válidos e inválidos, opciones requeridas/duplicadas y
  rechazo antes de acceder a rutas.
- Falta de consola para cada comando operativo, comprobando que `create` no deja
  archivo y que las operaciones existentes no cambian el archivo.
- Creación con confirmación coincidente, discrepante y cancelada; destino
  existente; mensajes y estado de salida.
- Alta válida, campos requeridos vacíos, notas vacías, preservación de espacios
  y Unicode, contraseña sin eco y round-trip mediante Storage.
- Listado ordenado, vacío y comprobación de que no aparecen contraseña ni notas.
- Consulta de ID válido/inválido/inexistente, confirmación afirmativa/negativa y
  verificación de que la contraseña solo aparece en el canal de consola.
- Borrado de ID válido con respuesta exacta `sí` o `si`, respuesta negativa,
  cancelación e ID inexistente; archivo persistido sin cambio en cancelaciones.
- Errores de contraseña maestra, almacenamiento y guardado, confirmando que no
  hay datos parciales, mensajes sensibles ni sesiones abiertas.
- Borrado de buffers `char[]` controlados por la CLI tras éxito y error.

## 7. Cierre

La Spec 003 se considerará cerrada solo cuando:

- AC-1–AC-13 tengan pruebas automatizadas satisfactorias.
- La CLI implemente los cinco comandos MVP y la ayuda acordada, sin modificar
  el formato de bóveda ni añadir dependencias no aprobadas.
- Los mensajes estén en español y las pruebas de confidencialidad y ciclo de
  vida de secretos pasen.
- `./mvnw.cmd verify` termine correctamente y `docs/STATUS.md` registre el
  resultado y las limitaciones pendientes.

Esta spec no modifica `docs/THREAT_MODEL.md`. Si durante la implementación surge
una decisión de seguridad que no esté cubierta aquí o por el modelo vigente, se
detiene el trabajo y se pide aprobación antes de decidir.
