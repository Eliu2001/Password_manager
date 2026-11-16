# Tareas — Spec 012: Navegación recuperable y visualización etiquetada

Tareas en orden de dependencia, en bloques de 20–30 minutos. No incluir secretos
reales ni bóvedas en el repositorio. Spec 012 fue aprobada por el usuario el
2026-11-16; implementación autorizada por el usuario el 2026-11-16.

## Wizard y navegación

- [x] **T01 — Reintentos y retorno en el asistente** (25 min)\
  **RF:** R1–R3 · **AC:** AC-1, AC-2, AC-3\
  **Hecho cuando:** opción inválida repite el selector; prompt de ruta anuncia
  `0 para volver`; ruta inválida re-pregunta y `0` retorna al selector inicial;
  salir conserva su código actual. Ningún intento inválido llama a Storage.\
  **Pruebas:** `GuidedVaultApplicationTest` cubre inválido→válido, ruta errónea,
  volver a cambiar Crear/Abrir y ausencia de acceso a Storage por intentos fallidos.

  **Resultado:** pruebas focalizadas de wizard pasan; se cubre opción inválida,
  ruta inválida con reintento, `0` para volver y cambio de Crear a Abrir.

- [x] **T02 — Reintento y volver en selector de entradas** (25 min)\
  **RF:** R1, R2, R4 · **AC:** AC-1, AC-2, AC-4, AC-6\
  **Hecho cuando:** lista/prompt anuncia `0 para volver`; índice inválido muestra
  error y repite; `0` vuelve al menú sin ejecutar comando. Cada intento resuelve el
  índice contra la lista vigente.\
  **Pruebas:** `GuidedVaultMenuTest` verifica inválido→índice válido, inválido→0,
  ID correcto, cero llamadas indebidas y archivo sin cambios al cancelar.

  **Resultado:** pruebas focalizadas verifican índice inválido→índice válido y
  selección correcta, además de inválido→`0` sin revelar ni modificar la bóveda.

- [x] **T03 — Reintento y cancelación en búsqueda** (25 min)\
  **RF:** R1, R2, R4, R7 · **AC:** AC-1, AC-2, AC-4, AC-6\
  **Hecho cuando:** `0` cancela la consulta; opciones de acción inválidas
  informan y vuelven a preguntar con Cancelar visible; cancelar retorna al menú
  sin mutación; varias coincidencias siguen requiriendo selección explícita.\
  **Pruebas:** cero/varias coincidencias, opción inválida→válida, opción inválida→
  cancelar y comprobación de no despacho/guardado.

  **Resultado:** prueba focalizada cubre acción inválida→Cancelar y acción
  inválida→válida en búsqueda múltiple; confirma reintentos y ausencia de mutación
  cuando se cancela.

## Salida privada y cierre

- [x] **T04 — Etiquetar la salida de contraseña revelada** (25 min)\
  **RF:** R5–R7 · **AC:** AC-5, AC-6\
  **Hecho cuando:** solo tras confirmación afirmativa, el modo guiado escribe la
  línea constante `Contraseña:` antes del `char[]` secreto con `printSecret`; no
  construye `String` del secreto, conserva limpieza y salida legacy.\
  **Pruebas:** doble de consola registra orden metadatos→etiqueta→secreto sin
  convertir el arreglo a `String`; cancelación no emite etiqueta ni secreto; probar
  regresión para salida legacy y limpieza de buffers.

  **Resultado:** prueba focalizada verifica etiqueta privada inmediatamente antes
  del evento `printSecret`, no registra el contenido secreto como texto y confirma
  que al rechazar el revelado no aparece etiqueta ni secreto.

- [x] **T05 — Documentar UX y verificar cierre** (25 min)\
  **RF:** R1–R7 · **AC:** AC-1–AC-7\
  **Hecho cuando:** README describe reintento, volver/cancelar y formato del bloque;
  STATUS registra resultados, plataforma y limitaciones. Spotless, `./mvnw.cmd
  verify` y `git diff --check` pasan. Todos los AC tienen evidencia; no se cambia
  formato ni Threat Model.\
  **Evidencia:** salida de verificación, conteo de pruebas y revisión del diff.

  **Resultado:** README actualizado. `./mvnw.cmd verify` pasó con 145 pruebas,
  0 fallos, 0 errores y 1 omitida por symlink no disponible en Windows; Spotless y
  `git diff --check` pasaron. No se cambió formato ni Threat Model. No se ejecutó
  una prueba manual TTY específica para esta modificación.

## Fuera de alcance

No cambiar persistencia, criptografía, confirmaciones, formato, dependencias ni CLI
legacy. La Spec 012 se cierra con T01–T05 y su evidencia registradas arriba; la
preparación del Release 1.2.1 queda fuera de esta spec.
