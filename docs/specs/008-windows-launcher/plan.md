# Plan técnico — Spec 008: Lanzador para Windows

**Estado de la spec:** implementación autorizada y Spec 008 cerrada el 2026-11-15.\
**Predecesora:** Spec 007 (`distribution`), cerrada y publicada.\
**Alcance:** añadir `vault.cmd` genérico para iniciar una sesión de la CLI y documentar su configuración en README.

## 1. Contexto y decisiones aprobadas

El proyecto distribuye `password-vault-1.0.0.jar`, ejecutable con Java 21 o
posterior. La CLI ya acepta `session --vault <ruta>`; el timeout predeterminado
es cinco minutos. `Main` propaga un estado distinto de cero mediante
`System.exit`, y el flujo de sesión requiere una consola interactiva real.
`README.md` ya documenta la creación explícita de bóveda, el uso interactivo y la
prohibición de pasar secretos por argumentos, variables de entorno o stdin.

Se acordó que el lanzador será un archivo versionado `vault.cmd`, genérico y sin
rutas personales. El usuario editará `VAULT_PATH` al inicio del script; el JAR
debe quedar junto a este. El script iniciará directamente `session`, sin hacer
`cd` que afecte a la selección de bóveda ni crear el archivo automáticamente. No
se tocará el Release `v1.0.0` ni se decidirá adjuntar el script a una versión.

## 2. Arquitectura y responsabilidades

- **`vault.cmd` (raíz del repositorio):** script Windows independiente, sin
  dependencias nuevas. Resolverá `password-vault-1.0.0.jar` a partir de la
  ubicación del propio script (`%~dp0`), no del directorio de trabajo actual.
- **Configuración:** `VAULT_PATH` será una ruta absoluta editable y entrecomillada
  en la asignación propia de CMD para admitir espacios. Un valor vacío o el
  marcador de ejemplo se rechazará antes de arrancar Java. La comprobación debe
  ser exacta y no imprimir el contenido de la bóveda.
- **Precondiciones:** verificar la presencia del JAR y que `java` se pueda
  resolver en `PATH`; si falta alguno, imprimir instrucciones en español y
  terminar con estado de error sin iniciar la CLI. No hace falta inspeccionar la
  versión de Java desde el script: Java 21+ se explicará en README.
- **Ejecución:** invocar `java -jar` con la ruta del JAR y los argumentos
  `session --vault` más el valor `VAULT_PATH`, manteniendo intactos stdout,
  stderr, stdin y la consola privada. No configurar timeout para conservar el
  valor por defecto del CLI.
- **Cierre visible:** después de terminar la CLI, guardar su estado de salida,
  mostrar una pausa que permita leer el resultado en la ventana de doble clic y
  devolver el estado original. Las rutas de error de precondición también
  mantendrán visible su mensaje y devolverán estado no exitoso.
- **`README.md`:** añadir una sección breve con ubicación de archivos,
  configuración de ruta absoluta, creación inicial de la bóveda mediante la CLI,
  inicio/cierre, Java 21+, solución de errores y precauciones sobre secretos.
- **`docs/STATUS.md`:** al implementar, registrar resultado y plataforma de las
  validaciones realmente ejecutadas. No modificar `docs/FORMAT.md` ni
  `docs/THREAT_MODEL.md`.

No se modifica Java, parser, formato de bóveda, dependencias, empaquetado o CI.
El `.cmd` no crea la bóveda, no solicita secretos, no los guarda y no convierte
la invocación en un proceso redirigido o canalizado.

## 3. Flujo esperado

1. El usuario obtiene `vault.cmd` del repositorio y lo coloca junto al JAR
   descargado.
2. Edita `VAULT_PATH` para indicar una ruta absoluta. Si la bóveda todavía no
   existe, la crea explícitamente con el comando `create` documentado en README.
3. Abre `vault.cmd` mediante doble clic o desde cualquier directorio.
4. El script valida ruta configurada, JAR y Java; al fallar, informa al usuario y
   no ejecuta `session`.
5. El script ejecuta el JAR con la bóveda configurada. La CLI obtiene la
   contraseña desde la consola sin eco y aplica sus políticas existentes.
6. Al salir de la sesión, el script conserva visible la salida, espera una tecla
   y devuelve el código de salida del proceso Java.

## 4. Validación y trazabilidad

| Criterios | Verificación prevista |
|---|---|
| AC-1 | Revisar el archivo versionado: sin ruta de usuario, credenciales, datos reales ni bóveda. |
| AC-2 | Ejecutar el script con el directorio de trabajo distinto de la carpeta del script; un `java` de prueba comprobará que el JAR resuelto pertenece a `%~dp0`. |
| AC-3 | Con un ejecutable Java de prueba que capture argumentos inocuos, comprobar que una ruta absoluta con espacios se transmite como un argumento único; probar valor de ejemplo/vacío y verificar que Java no se invoca. |
| AC-4 | Confirmar argumentos exactos `-jar`, JAR, `session`, `--vault`, ruta; revisar que no se redirigen streams y que no se suministra timeout. Una prueba manual real de sesión es complementaria, no bloqueante. |
| AC-5 | Probar, por separado, JAR ausente y Java no disponible; ambos casos deben mostrar mensaje, no invocar CLI y devolver error. |
| AC-6 | Usar Java de prueba que devuelva éxito y error: confirmar que la pausa aparece y que tras ella `vault.cmd` devuelve el mismo estado de salida. |
| AC-7 | Revisar README frente al script y la CLI, incluyendo creación separada, cierre y prohibición de secretos en el script/argumentos. |
| AC-8 | Ejecutar la matriz de escenarios en Windows; correr `./mvnw.cmd verify` y registrar resultado. No afirmar validación POSIX ni CI para el lanzador Windows. |

La prueba con Java de prueba debe vivir fuera del repositorio, usar solo una
ruta sintética (incluyendo un espacio) y no capturar salida de una bóveda real.
Para probar el código de salida y la pausa sin bloquear, la matriz automatizada
puede alimentar la tecla de continuación al proceso CMD de prueba; la validación
manual de la consola real se hará sin redirecciones. Si la combinación de CMD y
pausa no permite conservar el código de salida de forma fiable, detenerse y
ajustar el diseño antes de relajar AC-6.

## 5. Secuencia de trabajo

1. Confirmar aprobación de Spec 008 y revisar parser, comportamiento de `session`,
   `Main`/códigos de salida, README y estado de trabajo para no sobrescribir
   cambios ajenos.
2. Implementar `vault.cmd` con ruta del JAR relativa al script, configuración
   explícita, precondiciones, llamada directa a la CLI y preservación del estado.
3. Validar el lanzador con Java de prueba: ubicación, argumentos/ruta con espacios,
   rechazo de configuración incompleta, JAR/Java ausentes, pausa y códigos de
   salida.
4. Opcionalmente, hacer una comprobación manual del inicio de sesión desde una
   terminal real de Windows con bóveda temporal no sensible; registrar si se hizo,
   sin convertirla en requisito de cierre de la spec.
5. Documentar descarga/ubicación, configuración, creación inicial, uso y
   solución de errores en README; registrar evidencia y limitaciones en
   `docs/STATUS.md`.
6. Revisar que no se añadieron rutas personales, secretos, bóvedas, dependencias,
   cambios al Release ni cambios al modelo de amenazas/formato; ejecutar
   `./mvnw.cmd verify` y revisar el diff.
7. Cerrar únicamente al demostrar AC-1–AC-8 y registrar qué pruebas Windows
   automatizadas/manuales se ejecutaron. No se requiere publicar un nuevo Release.

## 6. Riesgos y límites

- La sintaxis de expansión y quoting de CMD es frágil, especialmente ante espacios
  y caracteres especiales; validar el argumento observado, no solo inspeccionar
  visualmente el script.
- CMD no puede confirmar por sí solo que una JVM disponible sea Java 21+ sin
  agregar lógica de versión; el requisito se comunica en README y el usuario
  corrige el runtime incompatible.
- La pausa añade una espera también al salir desde una terminal ya abierta, pero
  es parte del requisito para que doble clic no cierre la ventana de inmediato.
- Un lanzador genérico requiere edición por usuario. No hay persistencia de
  configuración fuera de `vault.cmd`, selección de rutas ni actualización
  automática del JAR.
- Las comprobaciones con Java de prueba demuestran el contrato de argumentos y
  estado, no sustituyen la prueba de consola real; registrar por separado lo que
  realmente se ejecutó.
- El script y la guía no deben ofrecer una vía alternativa de entrada para
  secretos. La autenticación permanece a cargo de la CLI existente.

## 7. Cierre

Cerrar con AC-1–AC-8 cubiertos, README actualizado y `./mvnw.cmd verify` en verde.
Registrar la validación en Windows y cualquier limitación manual en
`docs/STATUS.md`. La implementación no altera el Release `v1.0.0`.
