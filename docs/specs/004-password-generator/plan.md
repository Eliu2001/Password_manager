# Plan técnico — Spec 004: password-generator

**Estado de la spec:** cerrada el 2026-11-14; T01–T12 completadas.  
**Alcance:** API de generación y comando interactivo `generate` con longitud opcional.  
**Predecesora:** Spec 003 (`cli-mvp`), cerrada.

## 1. Contexto y decisiones aprobadas

El proyecto usa Java 21, Maven y JUnit 5. La CLI existente está en
`cl.germain.vault.cli`; `CliArgumentParser` produce un `CliCommand`,
`CliApplication` valida la disponibilidad de consola y despacha a
`VaultCliCommands`. `CliConsole.printSecret(char[])` es el canal existente para
mostrar secretos exclusivamente en la consola interactiva. No existe todavía el
paquete `generator`.

La Spec 004 añade `cl.germain.vault.generator` y `Main generate
[--length <n>]`. La longitud predeterminada será 20 y el rango válido 12–128.
Cada resultado incluirá al menos un carácter de cada grupo (mayúsculas,
minúsculas, dígitos y puntuación ASCII imprimible `!`–`~` excluidos letras y
dígitos). El resultado será `char[]`, se mostrará solo por `printSecret` y la CLI
lo borrará; no se abre ni modifica una bóveda. No se agregan dependencias, no se
cambia el formato v1 ni se modifica `docs/THREAT_MODEL.md`.

## 2. Arquitectura propuesta

### Componentes

- **`cl.germain.vault.generator.PasswordGenerator`:** API pública para generar
  un `char[]` a la longitud indicada. Un constructor de producción crea y posee
  su `SecureRandom`; un constructor package-private permitirá pruebas controladas
  sin exponer el control de aleatoriedad a los consumidores de producción.
- **Parser y `CliCommand`:** añadir el tipo `GENERATE` y representar la longitud
  validada, usando 20 si se omitió la opción. El comando no contiene ruta de
  bóveda ni datos secretos.
- **`CliApplication`:** extender la ayuda y conservar el gate común de consola.
  Los errores de sintaxis se detectan antes de consultar consola o invocar al
  generador.
- **`VaultCliCommands` (o ejecutor CLI equivalente):** recibir explícitamente
  `PasswordGenerator`, ejecutar `generate`, presentar mensajes en español y
  gestionar la propiedad y limpieza del arreglo.
- **Pruebas:** paquetes de prueba correspondientes a `generator` y `cli`, con
  aleatoriedad sustituible solo en el límite package-private y consola falsa para
  separar `printSecret` de stdout/error.

`generator` no depende de `cli`, `storage`, `model` ni `crypto`; la CLI depende de
`generator`. La clase `crypto` permanece independiente. No duplicar el parser ni
el canal de consola existentes.

### Generación de caracteres

1. Validar en el generador que la longitud está entre 12 y 128, inclusive.
2. Crear el arreglo de salida con el tamaño validado.
3. Seleccionar con `SecureRandom.nextInt(bound)` un carácter de cada uno de los
   cuatro grupos y colocarlo en el arreglo. Esta selección acotada evita la
   reducción modular sesgada.
4. Rellenar las posiciones restantes seleccionando de la unión de los cuatro
   grupos mediante el mismo método acotado.
5. Aplicar Fisher–Yates con `SecureRandom.nextInt(i + 1)` para mezclar el arreglo,
   de modo que las posiciones de los caracteres obligatorios no sean fijas.
6. Devolver el arreglo al llamador, que adquiere la responsabilidad de borrarlo.

El generador no crea `String` con la contraseña. Los alfabetos son constantes no
secretas. Las pruebas controladas pueden sustituir la fuente aleatoria solo
dentro del paquete de pruebas; producción usa exclusivamente `SecureRandom`.

### Flujo CLI

1. Parsear `generate` y, si existe, el valor decimal completo de `--length`.
   Rechazar opciones desconocidas, repetidas, incompletas, no numéricas o fuera
   de rango antes de consultar la consola y antes de generar.
2. Resolver `--help` sin requerir una consola y mostrar la nueva sintaxis.
3. Para `generate`, requerir `CliConsole.isAvailable()`. No leer stdin ni
   solicitar datos adicionales.
4. Generar el `char[]`, entregarlo una sola vez a
   `CliConsole.printSecret(char[])` y borrar el arreglo en `finally`.
5. Informar éxito solo después de que la impresión retorne correctamente. Ante
   error, usar un mensaje español genérico, no incluir resultado/causa y no
   reintentar por stdout, error u otro canal.

Si la escritura falla una vez iniciada, el adaptador de consola podría haber
mostrado un prefijo antes del fallo; el programa no puede retirar esos caracteres.
El comportamiento verificable es no reintentar por otro canal, borrar el arreglo
y no afirmar éxito.

## 3. Validación, fallos y secretos

- Validar toda la gramática de argumentos antes del gate de consola y de invocar
  el generador. No reinterpretar entradas inválidas ni truncar longitudes.
- El API del generador también valida el rango; no depende exclusivamente de la
  validación CLI.
- Usar solo `SecureRandom` y selecciones acotadas `nextInt(bound)` para evitar
  sesgo por módulo. El mezclado también usa esa instancia.
- La contraseña generada no se convierte a `String`, no se registra ni forma
  parte de excepciones/mensajes. `printSecret` es el único canal de presentación.
- La CLI conserva la propiedad del arreglo generado y lo borra tanto si la
  impresión tiene éxito como si falla. La limpieza reduce exposición, pero Java
  y el terminal pueden conservar copias que el programa no controla.
- La falta de consola falla sin generar. Fallos de fuente aleatoria o de salida
  producen estado de error y mensaje genérico; no se informa éxito.
- No se toca almacenamiento, formato, `docs/FORMAT.md` ni
  `docs/THREAT_MODEL.md`.

## 4. Pruebas y trazabilidad de aceptación

JUnit 5, sin datos sensibles versionados. Las pruebas de distribución no
afirmarán garantías absolutas a partir de unas pocas muestras; composición y
conjunto permitido sí se verificarán en cada resultado determinista/controlado.

| Cobertura | Criterios |
|---|---|
| API: longitudes 12, 20 y 128; validación de 11 y 129; longitud exacta y composición de las cuatro categorías | AC-1, AC-2 |
| API: todos los caracteres pertenecen al conjunto acordado; no hay espacios ni controles; selección acotada y mezcla usan `SecureRandom` | AC-2, AC-4 |
| Parser: `generate`, longitud ausente/presente, argumentos inválidos y rechazo antes de invocar al generador | AC-3 |
| Ayuda: sintaxis `generate [--length <n>]` y longitud predeterminada/rango | AC-3, AC-8 |
| CLI: consola ausente no genera ni lee stdin; resultado solo en `printSecret` | AC-5, AC-6 |
| CLI: limpieza del arreglo tras salida normal y excepción de escritura; no fallback, reintento ni estado de éxito falso | AC-6, AC-7 |
| Integración: ejecución del comando y traducción de errores a mensajes/estados españoles no sensibles | AC-5–AC-7 |
| Revisión: dependencias, arquitectura, formato v1 y Threat Model sin cambios | AC-8 |

Para verificar el uso correcto de aleatoriedad se usarán pruebas del mismo
paquete con una instancia controlada de `SecureRandom` o un doble equivalente
encapsulado package-private; el constructor no será público. Los tests no
concluirán que dos salidas distintas prueban una garantía matemática.

## 5. Secuencia de implementación

1. **Crear `PasswordGenerator` y validar longitud:** API pública y límites
   12–128; pruebas de rango y arreglo de longitud exacta.
2. **Implementar selección aleatoria y composición:** alfabetos, inclusión de
   una categoría por grupo, relleno y Fisher–Yates con `SecureRandom`; pruebas
   de composición, conjunto imprimible y selección acotada.
3. **Extender el modelo del comando y parser:** `GENERATE`, longitud opcional,
   validación de argumentos y pruebas que demuestren rechazo temprano.
4. **Actualizar ayuda y despacho de CLI:** sintaxis del nuevo comando,
   disponibilidad de consola y conexión explícita al generador.
5. **Implementar presentación segura:** llamada única a `printSecret`, limpieza
   en `finally`, estados y errores españoles genéricos.
6. **Completar pruebas CLI:** consola ausente, secreto aislado, fallo de escritura,
   no fallback/reintento y limpieza del arreglo.
7. **Revisión de integración y seguridad:** imports/dependencias, `crypto`
   independiente, sin cambios de formato ni Threat Model.
8. **Verificación y cierre:** ejecutar `./mvnw.cmd verify`, registrar resultados,
   cantidad de pruebas y limitaciones en `docs/STATUS.md`; cerrar solo si AC-1–AC-8
   están cubiertos.

## 6. Riesgos y limitaciones

- Java no garantiza borrado total de memoria; el plan exige borrar el arreglo
  poseído por CLI, no promete eliminar copias internas del runtime o terminal.
- Un error de escritura puede ocurrir tras mostrar parcialmente los caracteres;
  no existe rollback fiable de la salida del terminal. No se permite reintentar
  o redirigir el secreto a otro canal.
- La composición obligatoria se consigue incluyendo un carácter de cada grupo y
  mezclando el arreglo. Las pruebas de distribución no prueban aleatoriedad
  absoluta ni ausencia matemática de colisiones entre salidas.
- La puntuación ASCII amplia podría no ser aceptada por todos los sitios; el
  ajuste de categorías o listas de exclusión está fuera de alcance.
- `System.console()` puede no estar disponible en IDEs y ejecuciones redirigidas;
  el rechazo es comportamiento requerido, no motivo para usar stdin.

## 7. Cierre

La implementación debe cubrir AC-1–AC-8, no añadir dependencias, conservar el
formato v1 y el modelo de amenazas, borrar el arreglo bajo propiedad de CLI y
terminar `./mvnw.cmd verify` en verde. Los resultados y limitaciones se registran
en `docs/STATUS.md`. No modificar `docs/THREAT_MODEL.md` sin petición expresa.
