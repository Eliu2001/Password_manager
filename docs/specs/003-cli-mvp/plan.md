# Plan técnico — Spec 003: cli-mvp

**Estado de la spec:** aprobada el 2026-11-12; implementación autorizada.\
**Alcance:** CLI interactiva en español para `create`, `add`, `list`, `show`, `delete` y `--help`.\
**Predecesora:** Spec 002 (`vault-storage`), cerrada.

## 1. Contexto y decisiones aprobadas

El proyecto usa Java 21, Maven y JUnit 5. Ya existen los paquetes `crypto`, `model` y `storage`, incluyendo `VaultStorage`, `VaultSession`, `Vault`, `Entry` y manejo de contraseñas de entrada como `char[]`. La CLI se añade en `cl.germain.vault.cli`; no se agregan dependencias, no se modifica el formato v1 ni `docs/THREAT_MODEL.md`.

Todos los comandos operativos requieren `--vault <ruta>` y `System.console()` disponible. La CLI no aceptará secretos en argumentos, variables de entorno ni stdin, ni operará de forma no interactiva. Las opciones y los IDs se validan antes de tocar archivos. IDs: exactamente 32 dígitos hexadecimales minúsculos.

Contraseñas maestras y de entradas se leen sin eco y se mantienen como `char[]`; la CLI borra los arreglos que posee en todos los caminos. No se recortan ni normalizan los campos de entrada. Solo la respuesta exacta `sí` o `si` confirma revelación o borrado; espacios adicionales y otras mayúsculas/minúsculas no confirman. `show` puede imprimir la contraseña exclusivamente a la consola interactiva.

## 2. Arquitectura propuesta

### Componentes

- `cl.germain.vault.cli.Main`: punto de entrada Java; coordina el análisis de argumentos, la consola, la ejecución y el código de salida. No contiene lógica criptográfica ni de persistencia.
- Parser de argumentos en `cli`: convierte `String[]` a una representación de comando validada; detecta comando/opción desconocidos, repetidos, incompletos o fuera de lugar. Debe poder probarse sin filesystem.
- Abstracción de consola en `cli`: interfaz sustituible en pruebas para disponibilidad, lectura con/sin eco, confirmaciones y canales separados de salida normal/error/consola. Adaptador de producción usa `System.console()`; no debe hacer fallback a `System.in`.
- Coordinador/ejecutor de comandos: recibe dependencias explícitas (parser, consola y `VaultStorage` o una fachada pequeña sobre él), maneja prompts, ciclo de vida de sesión, mensajes y estados de salida.
- Pruebas en el mismo paquete, con una consola falsa y directorios temporales; no incluir secretos reales ni archivos de bóveda versionados.

La CLI depende de `storage` y `model`; `crypto` permanece independiente. Reutilizar las APIs existentes de `VaultStorage.create/open` y de `VaultSession` para CRUD, `save()` y `close()`. No duplicar lógica de Storage ni cambiar su formato.

### Flujo común

1. Resolver `--help` sin pedir consola ni acceder a una bóveda. Para otros comandos, parsear y validar por completo antes de archivos.
2. Confirmar disponibilidad de la consola antes de leer secretos o llamar a Storage. Si no existe, emitir error en español, devolver estado distinto de cero y dejar intacta la bóveda.
3. Ejecutar el comando dentro de un ámbito que garantice cierre de sesión y limpieza de los `char[]` poseídos aun ante lectura interrumpida, fallo de Storage o excepción.
4. Traducir errores a mensajes españoles de alto nivel sin trazas ni causas sensibles. No reportar éxito hasta completar la operación.

## 3. Comportamiento por comando

- **create:** pedir contraseña maestra dos veces sin eco; comparar sin crear una copia persistente; ante discrepancia/interrupción cancelar sin archivo. En coincidencia llamar a `VaultStorage.create`. No sobrescribir; borrar ambas entradas bajo propiedad de CLI.
- **add:** abrir sesión tras pedir la contraseña maestra una vez; solicitar servicio, usuario, contraseña y notas en ese orden. Rechazar campos requeridos vacíos sin agregar ni guardar. Entregar contraseña al modelo de acuerdo con su contrato de copia/ownership, borrar el arreglo temporal propio y guardar explícitamente. Mostrar ID solo tras save exitoso.
- **list:** abrir y listar en orden; mostrar ID, servicio y usuario, nunca contraseña ni notas. Bóveda vacía: mensaje claro. No llamar a `save()`.
- **show:** validar ID antes de abrir; si existe, mostrar ID/servicio/usuario/notas, pedir confirmación y revelar contraseña solo tras `sí` o `si` exacto. Escribirla solamente mediante el canal de consola interactiva; borrar inmediatamente la copia `char[]` devuelta por el modelo, tanto si se revela como si se cancela. No guardar.
- **delete:** mostrar ID/servicio/usuario, pedir confirmación; solo `sí` o `si` exactos eliminan y guardan. Cualquier otra respuesta cancela normalmente, sin save. ID desconocido no modifica la bóveda.
- En todos los comandos existentes, el `char[]` de contraseña maestra es limpiado por CLI después de la llamada a Storage; la sesión se cierra en éxito, cancelación y error. Los errores de autenticación conservan el mensaje genérico, sin distinguir causas.

## 4. Validación, fallos y secretos

- Validar gramática, opciones obligatorias y permitidas, repeticiones e IDs antes de cualquier acceso a rutas o creación/apertura de sesión.
- Tratar EOF, lectura ausente e interrupción como cancelación/fallo según el comando; liberar todo recurso adquirido y no incluir el contenido leído en el mensaje.
- No pasar contraseñas como `String`, ni registrarlas, concatenarlas en mensajes o excepciones. La salida de contraseña de `show` es la única excepción de presentación permitida y debe dirigirse al canal de consola real, nunca a stdout redirigible, logs o errores.
- No mostrar datos parciales después de error de lectura, descifrado, persistencia o interacción. Errores generales pueden indicar operación fallida, pero no incluir causas ni contenido de entradas.
- Probar limpieza de arreglos mediante buffers sintéticos observables a través de la abstracción de consola/colaboradores de prueba, sin debilitar el camino de producción.

## 5. Pruebas y trazabilidad de aceptación

JUnit 5 con dobles de consola y Storage donde ayuden a probar orden y ausencia de operaciones; añadir integración con Storage real para round-trip/persistencia. Capturar separadamente salida normal, errores y canal privado de consola.

| Cobertura | Criterios |
|---|---|
| Parser: cinco comandos, ayuda, sintaxis, opciones ausentes/repetidas/desconocidas; comprobar rechazo antes de tocar rutas | AC-1, AC-2 |
| Consola ausente en cada operación; create no crea y operaciones existentes no cambian archivo; demostrar que no hay fallback a stdin | AC-3 |
| Create: coincidencia, discrepancia, cancelación, destino existente, ausencia de eco, limpieza y estados | AC-4, AC-5, AC-11, AC-12 |
| Add: prompts/orden, obligatorios, notas vacías, espacios y Unicode preservados, falta de alta parcial, save, ID minúsculo y limpieza | AC-6, AC-10, AC-12 |
| List: orden, bóveda vacía, campos mostrados y exclusión de contraseña/notas; ningún save | AC-7, AC-10, AC-11 |
| Show: ID inválido/inexistente, confirmación exacta positiva/negativa/vacía, canal de revelación, limpieza de copia y ningún save | AC-8, AC-10, AC-11, AC-12 |
| Delete: confirmación positiva, negativa, cancelación e ID inexistente; persistencia solo en positivo y archivo inalterado en cancelación | AC-9, AC-10, AC-12 |
| Fallos de autenticación, lectura, Storage y save: mensajes no sensibles, sin éxito falso, sesión cerrada y sin salida parcial | AC-5, AC-10, AC-11, AC-12 |
| Revisión de dependencias/formato y verificación integral | AC-13 |

Las pruebas usarán datos sintéticos y archivos temporales. La revisión final debe verificar ausencia de secretos en argumentos, entorno, stdin, logs, errores y fixtures versionados.

## 6. Secuencia de implementación

1. Inspeccionar firmas concretas de `VaultStorage`, `VaultSession`, `Vault` y `Entry`; registrar en el diseño cómo se obtienen campos y copias de contraseña sin violar ownership.
2. Implementar parser de argumentos y modelo inmutable de comando; probar tabla de argumentos y que errores no acceden a Storage.
3. Introducir abstracción de consola y adaptador `System.console()` sin fallback; probar disponibilidad, lecturas, EOF/interrupción, confirmaciones y canales.
4. Implementar coordinación común: validación previa, estados/mensajes españoles, limpieza y cierre en `finally`/`try-with-resources` según APIs.
5. Implementar `create`, después flujo común de apertura y `list`.
6. Implementar `add` con validación, copia/limpieza de contraseña y persistencia explícita.
7. Implementar `show` y `delete`, asegurando confirmación exacta y restricciones de canal/no persistencia.
8. Completar pruebas de errores, cancelaciones, limpieza, no consola e integración persistida; corregir según criterios AC.
9. Ejecutar `./mvnw.cmd verify`, revisar confidencialidad, dependencias y que no se tocó formato/Threat Model; registrar resultados y limitaciones en `docs/STATUS.md` durante implementación/cierre.

## 7. Riesgos y límites

- `System.console()` puede ser `null` en IDEs, procesos de prueba o ejecución redirigida; es un rechazo requerido, no motivo para leer de stdin.
- Las pruebas no deben capturar una contraseña revelada por la misma ruta usada para stdout/error: mantener explícito el canal privado de consola y verificar su destino.
- Java/JDK puede mantener copias internas de datos; la CLI garantiza limpieza de los arreglos bajo su propiedad, no borrado absoluto de memoria del proceso.
- Las APIs concretas del modelo pueden obligar a copiar contraseñas para consultas; cada copia se debe borrar por quien la recibe. No añadir métodos de conveniencia que alteren el contrato de ownership sin autorización.
- Una lectura de `System.console().readPassword()` puede devolver `null`; se trata como cancelación y se limpian los recursos previos.
- No se identifican decisiones de seguridad abiertas en la spec. Si la implementación revela una ambigüedad sobre canales de salida, propiedad de buffers, cancelación o errores, detenerse y pedir aclaración antes de decidir.

## 8. Cierre

La implementación debe cubrir AC-1–AC-13 con pruebas, conservar formato v1 y dependencias aprobadas, ejecutar `./mvnw.cmd verify` en verde y registrar el resultado/limitaciones en `docs/STATUS.md`. No modificar `docs/THREAT_MODEL.md` sin petición expresa.
