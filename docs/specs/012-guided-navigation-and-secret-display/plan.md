# Plan técnico — Spec 012: Navegación recuperable y visualización etiquetada

**Estado de la spec:** aprobada por el usuario el 2026-11-16.
**Predecesoras:** Specs 010 y 011, cerradas.
**Alcance:** reintentos/volver en el wizard y la navegación guiada; etiquetar
contraseñas reveladas sin cambiar las reglas de seguridad.

## 1. Contexto y decisiones aprobadas

La navegación está repartida entre `GuidedVaultApplication`, `CliSessionRunner`,
`GuidedVaultMenu` y `VaultCliCommands`. El menú del runner se vuelve a mostrar
después de cada acción, pero algunos errores internos (índice inválido u opción
inválida tras una búsqueda) terminan la pantalla secundaria y devuelven al menú
principal sin permitir reintentar ahí. El selector acepta `0` para cancelar, pero
un índice inválido finaliza la selección. El wizard reintenta tras una ruta inválida,
pero no ofrece un control visible para volver desde el prompt de ruta.

Al confirmar `show`, `VaultCliCommands.show` genera metadatos como líneas `String`
y la contraseña como `char[]` separado en `CommandResult`. `emit` imprime los
metadatos y después el secreto mediante `CliConsole.printSecret`; la consola real
escribe el arreglo directamente, sin rótulo. Se conservará este canal privado y se
agregará únicamente una etiqueta constante antes de imprimir el arreglo.

Decisiones aprobadas:

- Ante opciones/índices inválidos, mostrar error y volver a pedir en la misma
  pantalla.
- Las pantallas secundarias anuncian cómo volver/cancelar. `0` cancela una
  selección y vuelve al menú guiado. En el wizard, `0` en el selector inicial sigue
  significando salir; `0` en la ruta vuelve a Crear/Abrir/Salir.
- La revelación muestra `Contraseña:` inmediatamente antes de escribir el secreto
  en la siguiente línea con `printSecret`.
- Sin dependencias nuevas ni cambios a formato, persistencia, confirmaciones,
  Threat Model o comportamiento legacy de la CLI.

## 2. Componentes y cambios

- **`GuidedVaultApplication`:** reintentar opciones inválidas en el selector;
  pedir la ruta con indicación `0 para volver`; `0` vuelve al selector y rutas
  inválidas repiten el prompt. No invocar Storage antes de una ruta válida.
- **`GuidedVaultMenu.select`:** sobre índice inválido, informar y repetir lista y
  prompt con `0 para volver`. `0`, EOF o interrupción no despachan acción. Resolver
  el ID desde la instantánea vigente en cada selección.
- **`GuidedVaultMenu.search`:** `0` cancela la consulta; una acción desconocida
  después de seleccionar resultados informa y vuelve a pedir, mostrando opción de
  cancelar. No cambiar búsqueda parcial ni selección explícita de coincidencias.
- **`GuidedVaultMenu.step`:** conservar el menú principal como retorno de las
  cancelaciones y asegurar que reintento/volver no ejecuta comandos.
- **`VaultCliCommands.show` / `CommandResult`:** solo en modo guiado y solo tras
  confirmación afirmativa, agregar la línea constante `Contraseña:` antes de la
  emisión de `result.secret()`. El secreto sigue siendo `char[]` independiente; no
  concatenarlo, formatearlo ni copiarlo a `String`. Conservar salida legacy.
- **Pruebas de consola:** los dobles registran eventos ordenados; el secreto se
  conserva como arreglo sintético separado y se limpia después de la aserción. No
  ampliar la API de consola productiva salvo que la implementación lo requiera.
- **README / STATUS:** documentar las opciones de reintento/volver y el formato
  de contraseña revelada; registrar pruebas y limitaciones observadas.

## 3. Flujos esperados

### Wizard

1. Renderizar Crear/Abrir/Salir; opción inválida informa y repite el selector.
2. Opción válida avanza a la ruta completa, con ayuda `0 para volver`.
3. `0` retorna al paso 1; ruta inválida informa y repite el paso 2.
4. Solo una ruta válida continúa a autenticación/Storage. `0` en el primer
   selector conserva el código de salida/cancelación actual.

### Selección y búsqueda

1. Renderizar resultados y pedir índice indicando `0 para volver`.
2. Índice inválido informa y repite selector sin despachar; `0` vuelve al menú.
3. En búsqueda, acción válida despacha solo esa acción; opción inválida informa y
   repite el prompt; Cancelar vuelve al menú sin mutación.

### Revelación

1. Conservar el prompt y la confirmación exacta `sí`/`si`.
2. Si se rechaza, no emitir etiqueta ni secreto.
3. Si se confirma, emitir metadatos, la etiqueta constante y luego el secreto por
   `printSecret(char[])`; conservar la limpieza existente en `finally`.

## 4. Seguridad y compatibilidad

- No usar `String.valueOf(secret)`, concatenación, formato ni interpolación con
  el secreto. La etiqueta es constante y no sensible.
- El secreto no se incluye como contenido en `CommandResult.output()`; solo la
  etiqueta constante puede estar allí. `emit` debe seguir limpiando
  `CommandResult.secret()` en `finally`.
- Cancelación, error de parseo e índice inválido no mutan ni guardan la bóveda.
- Tras cada reintento, resolver la selección contra la lista vigente; no retener
  índices/entradas obsoletos.
- Conservar confirmaciones, comandos legacy, códigos de salida y comportamiento
  de una consola no disponible. No modificar `docs/FORMAT.md` ni
  `docs/THREAT_MODEL.md`.

## 5. Pruebas trazadas a criterios

| AC | Evidencia de prueba |
|---|---|
| AC-1 | Wizard, selector, acción de búsqueda: inválido seguido de válido; verificar mensaje, prompt repetido y una sola acción. |
| AC-2 | Comprobar controles de volver/cancelar y que cancelar retorna al menú sin comando ni cambio de bytes del archivo. |
| AC-3 | Ruta inválida, `0` para volver, elegir otro modo/ruta válida; no acceder a Storage por el intento descartado. |
| AC-4 | Índice inválido reintenta; índice válido apunta al ID esperado; `0` cancela; búsqueda múltiple no elige sola. |
| AC-5 | Orden de eventos: metadatos, etiqueta privada, secreto `char[]`; respuesta negativa no imprime etiqueta/secreto; probar regresión legacy. |
| AC-6 | Contar llamadas a comandos/guardado y comparar modelo/bytes ante error, volver y cancelar. |
| AC-7 | README/STATUS actualizados; `./mvnw.cmd verify`, Spotless y `git diff --check`. |

Las pruebas no convierten el secreto a `String` para verificar el orden: registran
la etiqueta no sensible y el evento de secreto como arreglo sintético independiente,
que se limpia después de la aserción. La prueba TTY manual se registrará separada
de la evidencia automatizada.

## 6. Secuencia, riesgos y cierre

1. Añadir reintentos/volver del wizard y pruebas de ruta/modo y no acceso a Storage.
2. Añadir ciclos de reintento/volver del selector y acción de búsqueda; probar no
   mutación, no despacho y resolución de índice actual.
3. Etiquetar la contraseña guiada con texto constante; probar orden, cancelación,
   limpieza y compatibilidad legacy.
4. Actualizar README/STATUS; ejecutar Spotless, `./mvnw.cmd verify` y
   `git diff --check`; revisar cada AC.
5. Cerrar solo con AC-1–AC-7 cubiertos. La visualización real en TTY se reporta
   separada; no se afirma como verificada por el agente.

Riesgos: al reintentar puede cambiar la lista; se reconstruye desde la sesión y
se mapea índice a ID en cada intento. `printSecret` requiere consola privada; no
se añade fallback a `String` ni a salida redirigible. Una contraseña confirmada
sigue siendo observable en pantalla por quien pueda mirar la terminal.
