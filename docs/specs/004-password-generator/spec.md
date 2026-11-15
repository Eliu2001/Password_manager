# Spec 004 — password-generator

**Estado:** aprobada el 2026-11-14; implementación autorizada.  
**Fecha:** 2026-11-14  
**Predecesora:** `docs/specs/003-cli-mvp/spec.md`, cerrada.

## 1. Objetivo

Añadir generación de contraseñas aleatorias al gestor, con un comando interactivo
independiente que muestre el resultado únicamente en la consola real. El generador
no abre ni modifica una bóveda.

## 2. Alcance

**Dentro:**

- Nuevo paquete `cl.germain.vault.generator` con generación de contraseñas en
  memoria como `char[]`.
- Nuevo comando CLI `generate`, opcionalmente con longitud explícita.
- Uso de `SecureRandom`; resultado con letras ASCII mayúsculas y minúsculas,
  dígitos y puntuación ASCII imprimible.
- Validación de longitud, pruebas de composición y limpieza del arreglo bajo
  propiedad de CLI.

**Fuera:**

- Generar automáticamente durante `add` o guardar el resultado en una bóveda.
- Copiar al portapapeles, mostrar por stdout redirigible o escribir en archivo.
- Historial, evaluación de fortaleza, listas de exclusión personalizadas,
  configuración de categorías, generación de frases o dependencias nuevas.
- Cambios al formato v1, criptografía de bóveda o `docs/THREAT_MODEL.md`.

## 3. Interfaz acordada

```text
Main generate [--length <n>]
```

- Si se omite `--length`, se usa una longitud de 20 caracteres.
- El rango permitido es de 12 a 128 caracteres, inclusive.
- `--length` debe aparecer como máximo una vez y su valor debe ser un entero
  decimal completo dentro del rango. Argumentos desconocidos, duplicados,
  incompletos o fuera de rango se rechazan antes de generar.
- `--help` incluye el comando y su sintaxis.
- El comando requiere consola interactiva disponible. Sin ella, falla con un
  mensaje en español y no recurre a stdin.

## 4. Requisitos

### R1 — Aleatoriedad y composición

El generador usa `SecureRandom` para escoger caracteres. Cada resultado contiene
al menos un carácter de cada categoría: `A–Z`, `a–z`, `0–9` y puntuación ASCII
imprimible. El conjunto de símbolos corresponde a los caracteres imprimibles
ASCII `!` a `~` que no sean letras ni dígitos; no incluye espacio ni controles.
Los caracteres se distribuyen a lo largo de todo el resultado, sin posiciones
reservadas por categoría. La selección de índices no debe introducir sesgo por
reducción modular.

### R2 — Longitud y validación

La longitud por omisión es 20; se aceptan valores enteros de 12 a 128 inclusive.
Se rechazan entradas no numéricas, incompletas, repetidas o fuera de rango antes
de usar aleatoriedad o imprimir un resultado. Los errores de uso están en español
y no incluyen trazas.

### R3 — Manejo del resultado

La API del generador devuelve el resultado en un `char[]`; no convierte la
contraseña generada a `String`. La propiedad del arreglo devuelto pasa al
llamador, quien debe borrarlo tras su uso. El comando `generate` lo imprime una
sola vez mediante el canal privado `CliConsole.printSecret`, y lo borra en todos
los caminos, incluso si falla la impresión. No lo envía a stdout, errores, logs,
argumentos, entorno, stdin, archivos ni a una bóveda.

### R4 — Fallos seguros y límites

La falta de consola, la interrupción y los errores de generación no imprimen el
resultado ni producen un estado de éxito falso. Si la escritura a la consola falla
después de comenzar, la CLI no reintenta por otro canal ni informa éxito; no se
puede garantizar que una consola haya mostrado cero caracteres antes del fallo.
La CLI traduce errores a mensajes genéricos en español sin incluir el resultado
ni causas sensibles. La longitud fuera de rango es un error de argumentos y no
inicia generación.

### R5 — Arquitectura y dependencias

El generador reside en `cl.germain.vault.generator` y no depende de `cli`,
`storage` ni `model`. La CLI lo invoca; el módulo `crypto` permanece
independiente. No se agregan dependencias ni se cambia el formato v1. No se
modifica `docs/THREAT_MODEL.md` sin solicitud expresa.

## 5. Criterios de aceptación

| ID | Criterio |
|---|---|
| AC-1 | La API genera un `char[]` de la longitud solicitada y cada salida contiene al menos un carácter de cada una de las cuatro categorías acordadas. |
| AC-2 | El conjunto de caracteres está limitado a ASCII mayúsculas, minúsculas, dígitos y puntuación imprimible `!`–`~`, sin espacios ni controles. |
| AC-3 | La CLI acepta `generate` sin longitud (20) y `--length` entre 12 y 128 inclusive; rechaza sintaxis inválida antes de generar. |
| AC-4 | La generación usa `SecureRandom` y selección de índices sin reducción modular sesgada; las pruebas no presentan la aleatoriedad como garantía absoluta. |
| AC-5 | Sin consola interactiva, el comando falla sin mostrar resultado y sin recurrir a stdin. |
| AC-6 | El resultado se entrega solo por `printSecret`, nunca por stdout/error/log/archivo/bóveda, y el arreglo se borra en éxito y error. |
| AC-7 | Mensajes y estados de salida son correctos y no revelan el resultado ni causas sensibles ante fallos. |
| AC-8 | `--help` documenta el nuevo comando; no se agregan dependencias ni cambia el formato v1; `./mvnw.cmd verify` termina correctamente. |

## 6. Pruebas requeridas

JUnit 5 y datos sintéticos, sin secretos reales ni fixtures sensibles. La
cobertura incluye:

- Longitudes mínima, predeterminada y máxima; primer valor inválido por debajo
  y por encima, entrada no numérica, duplicada o incompleta.
- Composición por categorías, exclusión de espacios/controles y longitud exacta.
- Parser: comando y opción válidos, errores de sintaxis antes de invocar al
  generador, y actualización de ayuda.
- Consola ausente y fallos de escritura simulados; confirmar que no existe
  fallback a stdin ni reintento del resultado por canales públicos.
- Limpieza observable del `char[]` generado después de imprimir y al fallar.
- Revisión de dependencias y de independencia del paquete `generator`.

Las pruebas no deben depender de que dos generaciones consecutivas sean
distintas como garantía absoluta. Se puede probar que el generador usa
`SecureRandom` mediante una abstracción sustituible en pruebas, sin debilitar el
camino de producción ni permitir que el llamador controle la fuente aleatoria en
producción.

## 7. Cierre

La spec se considerará cerrada cuando AC-1–AC-8 tengan evidencia automatizada,
el comando y la API estén integrados, el resultado se limpie en todos los caminos,
`./mvnw.cmd verify` termine en verde y `docs/STATUS.md` registre el resultado y
cualquier limitación. Esta spec no requiere cambiar `docs/FORMAT.md` ni
`docs/THREAT_MODEL.md`.
