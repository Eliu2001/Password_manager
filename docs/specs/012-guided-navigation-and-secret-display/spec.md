# Spec 012 — Navegación recuperable y visualización etiquetada de contraseñas

**Estado:** aprobada por el usuario el 2026-11-16; plan y tareas preparados. Implementación pendiente de autorización explícita.
**Predecesoras:** Spec 010 (`guided-console-ux`) y Spec 011 (`release-1.2.0`), cerradas.

## 1. Objetivo

Mejorar la recuperación ante entradas inválidas en los flujos guiados y hacer
inequívoco el bloque de contraseña cuando el usuario confirma su revelado.

## 2. Alcance

**Dentro:**

- En submenús, selecciones de entradas y prompts guiados, repetir el prompt tras
  una opción inválida en vez de abandonar inesperadamente la pantalla.
- Ofrecer una opción explícita para volver/cancelar desde esas pantallas. En
  selección de entradas, `0` vuelve/cancela; en el asistente inicial, `0` sigue
  saliendo de la aplicación.
- Al revelar una contraseña de entrada, mostrar una sección identificada como
  `Contraseña:` y escribir el contenido secreto en la línea siguiente, conservando
  la confirmación exacta `sí`/`si` y el canal privado de consola.
- Añadir pruebas automatizadas de reintento, navegación hacia atrás, cancelación,
  orden de salida y separación entre etiqueta y secreto.

**Fuera:**

- Cambios en cifrado, persistencia, formato de bóveda, dependencias, configuración
  de rutas o reglas de confirmación.
- Revelar contraseñas sin confirmación, cambiar el comportamiento de contraseñas
  maestras, TOTP o generador.
- Guardar, registrar o convertir contraseñas a `String`; imprimir secretos en
  errores o listados.
- Cambiar `docs/THREAT_MODEL.md` sin petición expresa.

## 3. Requisitos

- **R1 — Recuperación en prompts:** un valor inválido muestra un mensaje claro y
  vuelve a pedir la entrada en la misma pantalla; no despacha ninguna acción.
- **R2 — Volver/cancelar:** las pantallas secundarias tienen una opción visible
  para volver o cancelar. Cancelar no modifica la bóveda ni guarda datos.
- **R3 — Asistente:** una ruta no válida permite volver a elegir Crear/Abrir/Salir
  sin cerrar la aplicación; `0` en el menú inicial conserva su significado de salir.
- **R4 — Selector y búsqueda:** un índice inválido permite reintentar; `0` cancela
  la operación y vuelve al menú guiado. Las opciones inválidas tras una búsqueda
  también se re-preguntan, con opción visible para cancelar.
- **R5 — Etiqueta de secreto:** tras confirmar el revelado, la salida guiada
  identifica explícitamente la contraseña con el rótulo `Contraseña:` y presenta
  el secreto separado visualmente de servicio, usuario y notas.
- **R6 — Manejo seguro:** el secreto sigue siendo `char[]`, se escribe solo con
  `printSecret` en consola privada y se limpia en los flujos existentes. El rótulo
  no concatena ni copia el secreto a un `String`.
- **R7 — Sin cambios de negocio:** se conservan las confirmaciones exactas y las
  reglas de acceso/persistencia; fallos y cancelaciones no ejecutan mutaciones.

## 4. Criterios de aceptación

- **AC-1:** entrada inválida en menú/prompt secundario re-muestra el prompt con
  texto de error y no ejecuta la operación.
- **AC-2:** cada pantalla secundaria anuncia cómo volver/cancelar; al elegir esa
  opción, retorna al menú guiado y no altera el archivo de bóveda.
- **AC-3:** al ingresar una ruta no válida durante el asistente, se puede reintentar
  la ruta o volver al selector Crear/Abrir/Salir.
- **AC-4:** índice inválido re-pregunta; `0` cancela y retorna al menú; búsquedas
  múltiples no seleccionan automáticamente una entrada.
- **AC-5:** las pruebas de revelado verifican que, tras confirmar `sí`/`si`, se
  imprime el rótulo antes del secreto, que el secreto no aparece en otros canales
  ni dentro de objetos `String`, y que una confirmación negativa no lo imprime.
- **AC-6:** las pruebas verifican que opciones inválidas, volver y cancelar no
  mutan ni guardan la bóveda.
- **AC-7:** `./mvnw.cmd verify` y `git diff --check` pasan; README explica las
  teclas/opciones para reintentar y volver.

## 5. Seguridad y pruebas

- Usar consolas sustituibles y bóvedas temporales/sintéticas; no incluir secretos
  reales ni archivos de usuario en pruebas o documentación.
- Verificar que la contraseña revelada solo sale después de confirmación exacta y
  mediante el canal privado; evitar capturarla como `String` en producción.
- Comprobar que volver/cancelar no guarda ni modifica datos, y que reintentar no
  reutiliza una selección obsoleta.
- No alterar `docs/FORMAT.md` ni `docs/THREAT_MODEL.md`.

## 6. Documentación y cierre

README debe explicar la navegación recuperable y la presentación del bloque de
contraseña. `docs/STATUS.md` registra pruebas y limitaciones. La implementación
solo empieza después de aprobación explícita de esta spec y de crear el plan y las
tareas requeridas por el flujo del proyecto.
