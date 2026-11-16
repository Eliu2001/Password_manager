# Plan técnico — Spec 010: Asistente y menú guiado de consola

**Estado de la spec:** aprobada e implementación autorizada el 2026-11-15.\
**Predecesoras:** Specs 003 (`cli-mvp`), 005 (`totp`), 006 (`auto-lock`) y 008 (`windows-launcher`), cerradas.\
**Alcance:** flujo Windows de primer uso con config local, seguido de menú textual para las operaciones existentes.

## 1. Contexto y decisiones aprobadas

La CLI existente exige `--vault` en cada comando independiente. El modo
`session --vault <ruta>` abre la bóveda una vez, solicita la contraseña maestra,
permite operar hasta `exit` y aplica auto-lock de cinco minutos. La sesión actual
acepta comandos textuales; `list` y confirmaciones imprimen UUID completos. La
implementación ya posee `VaultStorage.create/open`, y `create` devuelve un
`VaultSession` abierto que puede entregarse al componente propietario. Los
comandos de `VaultCliCommands.executeInSession` reutilizan la lógica de CRUD,
TOTP y upgrade; `generate` ya tiene su canal privado de salida.

Spec 010 añade un flujo guiado accesible por el launcher Windows. El usuario
aprobó menú de consola para todas las funciones actuales, selección con índices
numéricos efímeros, búsqueda parcial por servicio (case-insensitive) y guardar
solo la ruta en `%LOCALAPPDATA%\PasswordVault\vault-path.txt`. UUIDs persistidos,
formato v1/v2 y comandos avanzados no cambian. Sin dependencias nuevas; no se toca
`docs/THREAT_MODEL.md`.

## 2. Arquitectura y responsabilidades

- **`vault.cmd`:** invocar el nuevo flujo `start` del JAR 1.1.0, preservando la
  ventana, validación de Java/JAR y estado de salida. El script ya no decide la
  ruta: el asistente Java la lee/pide. No contiene secretos ni una ruta de usuario.
- **`VaultPathConfig` (paquete `cli`):** resolver `%LOCALAPPDATA%` requerido,
  determinar la ruta del archivo de configuración, leer exactamente una ruta
  absoluta UTF-8 y escribir solo ese valor. Sin fallback a rutas por defecto. Si
  `%LOCALAPPDATA%` falta, la ruta no es válida o el archivo está corrupto, mostrar
  error seguro y permitir corregir/cancelar. Persistir con temporal + reemplazo
  controlado para no dejar contenido parcial; no prometer ACL distinta de la
  heredada del perfil Windows.
- **`GuidedVaultApplication` (paquete `cli`):** comprobar consola antes de
  secretos/storage; determinar si usa la ruta local guardada o presenta el
  asistente Crear/Abrir/Salir. Exigir archivo absoluto, no directorio; para Abrir
  exigir existencia y para Crear usar `VaultStorage.create` que nunca sobrescribe.
  Persistir la ruta solo después de que create/open haya tenido éxito. Si create
  devuelve sesión, reutilizarla en el menú para no pedir otra vez la contraseña.
- **`CliSessionRunner`:** permitir iniciar el ciclo de sesión a partir de un
  `VaultSession` ya autenticado (creación/apertura del asistente) y mantener el
  ciclo actual de lock, reautenticación, EOF, error y cierre. El modo legacy REPL
  continúa disponible. El runner sigue siendo dueño del `VaultSession` y lo cierra
  en todos los caminos.
- **`GuidedVaultMenu` (paquete `cli`):** presentar opciones en español y convertir
  cada acción a los comandos existentes. Las acciones sobre entradas enumeran la
  vista actual y resuelven la elección numérica al UUID real solo en memoria. Una
  búsqueda por servicio lista resultados y exige elección explícita si hay más
  de uno. Después de mutaciones se invalida cualquier vista/índice guardado.
- **`ConsoleTheme` (paquete `cli`):** renderizar cabecera `PASSWORD VAULT`, crédito
  `Creado por Eliu2001`, bordes y opciones con ANSI azul/cian solo si detecta
  terminal compatible; ofrecer fallback sin color y modo forzado para pruebas.
  No añadir dependencias ni teñir datos de entradas, secretos o errores.
- **`VaultCliCommands`:** reutilizar métodos/confirmaciones/persistencia. En salida
  guiada no imprimir UUIDs innecesarios (incluido el ID que hoy se muestra tras
  `add`, o en `show`/`delete`); preservar la salida legacy donde el comando se
  invoca directamente. Los secretos mantienen `readPassword`/`printSecret` y
  buffers `char[]`/bytes se limpian como ahora.
- **Actualizar contraseña:** resolver ID solo desde el índice vigente, leer dos
  veces con `readPassword`, comparar, actualizar con `Vault.update` preservando
  UUID y metadatos, guardar solo si coincide y limpiar ambos arreglos en todos los
  caminos. No incluir el secreto en salida ni en comandos textuales.
- **`PasswordAge` (paquete `cli`):** calcular texto de antigüedad desde
  `Entry.updatedAt` usando `Clock` inyectable. Comparar el umbral con meses
  calendario UTC; al llegar a seis meses añadir un aviso de rotación. Etiquetar
  como última actualización aproximada, no como fecha exacta de cambio de
  contraseña. No mutar ni guardar la bóveda.
- **`CliApplication` / `Main`:** añadir entrada `start`; comprobar consola antes de
  abrir/crear configuración/Storage y traducir errores sin causas sensibles.
  `--help`, comandos one-shot y `session` actuales permanecen compatibles.
- **`README.md` y `docs/STATUS.md`:** documentar primer uso, ruta local guardada,
  flujo del menú, selección, búsqueda, revelación y plataforma/limitaciones.

La configuración almacena metadato (ruta), no credenciales. La ruta puede revelar
la ubicación de una bóveda a otras personas con acceso a la cuenta de Windows; no
se atribuye confidencialidad al archivo más allá de ACL heredadas de
`%LOCALAPPDATA%`. No guardar el path en logs/errores generales.

## 3. Flujo de primer uso y sesión

1. `vault.cmd` ejecuta `java -jar <jar> start`; `CliApplication` exige
   `System.console()` antes de pedir entrada o acceder a configuración/Storage.
2. Si existe una ruta local válida y apunta a un archivo, iniciar apertura con
   prompt de contraseña. Si no existe configuración o el archivo ya no está,
   ofrecer Crear nueva / Abrir existente / Salir y solicitar una ruta completa.
3. Abrir valida archivo existente; Crear exige que el destino no exista y su
   directorio padre exista, preservando el contrato de Storage. No crear archivos
   o padres por inferencia.
4. Tras abrir correctamente o crear el archivo, persistir la ruta en config. Una
   falla al persistir cierra la sesión y no informa que quedó todo configurado;
   el usuario puede corregir/cancelar. En creación, reutilizar la sesión que
   devuelve Storage.
5. Presentar menú dentro del ciclo de `CliSessionRunner`; el timeout continúa
   aplicándose entre acciones. Al bloquear, volver a autenticar; al salir/cancelar,
   cerrar la sesión y borrar buffers controlables.

## 4. Menú, selección y búsqueda

El menú ofrece: agregar, listar/seleccionar, buscar por servicio, mostrar entrada,
actualizar contraseña, borrar, configurar TOTP, retirar TOTP, consultar código
TOTP, upgrade v1→v2, generar contraseña, ayuda y salir. Cada opción pide solo la
información necesaria y delega las acciones/confirmaciones actuales. `generate` no
modifica la bóveda.

Cada fila muestra “última actualización: hace …” basado en `updatedAt`, con un
aviso al llegar a seis meses calendario UTC. El aviso es informativo y no bloquea
uso ni modifica datos; no se cambia el formato persistido.

La cabecera acredita de forma estática a `Eliu2001`; no consulta identidad del
sistema. ANSI se activa con señal detectable (`TERM_PROGRAM` Warp, `WT_SESSION`,
`COLORTERM` o `TERM` compatible), salvo `NO_COLOR`; el fallback ASCII conserva
estructura y legibilidad. La selección de tema se inyecta para probar ambos modos.

La enumeración utiliza el orden actual de `Vault.list()`, con posiciones desde 1.
Se entrega al comando el UUID asociado a la posición dentro de la instantánea
vigente, no se convierte ni guarda el índice. La mutación invalida esa instantánea;
el siguiente selector obtiene una vista nueva. Mostrar solo índice, servicio y
usuario; nunca contraseña, notas o secreto/código TOTP. `show` conserva el prompt
exacto `sí` antes de revelar; `delete`, `set/remove-totp` y `upgrade` conservan
confirmaciones actuales.

La búsqueda usa `toLowerCase(Locale.ROOT)` para localizar coincidencias parciales
del servicio sin sensibilidad a mayúsculas, sin normalizar Unicode. Cero
coincidencias informa que no se encontraron entradas. Resultados múltiples se
enumeran y el usuario elige un índice; ninguna coincidencia ejecuta por sí sola
una operación sensible. No imprimir IDs largos en el modo guiado.

## 5. Configuración, errores y persistencia

- Ruta de config exacta: `%LOCALAPPDATA%\PasswordVault\vault-path.txt`, UTF-8,
  una única línea con ruta absoluta. No serializar otros valores.
- Al crear el directorio de configuración, no cambiar permisos explícitamente;
  Windows hereda ACL del perfil. La prueba verifica ruta/contenido, no una garantía
  de ACL que el proyecto no haya establecido.
- La lectura rechaza vacío, relativas, datos extra y errores de decodificación.
  Config inválida se trata como no confiable; no intentar una ruta alternativa ni
  abrir otra bóveda automáticamente.
- Al guardar, usar temporal en el mismo directorio, reemplazo y limpieza del
  temporal ante error. Las operaciones de config nunca deben sobrescribir la
  bóveda; errores se informan de forma genérica y permiten salida segura.
- No guardar contraseña maestra ni de entradas en config. Ningún secreto se lee
  por `readLine`, argumentos, entorno o stdin; password/TOTP siguen por canales
  privados existentes.
- Índice inválido, resultado viejo, vacío, EOF o interrupción no debe despachar
 un comando ni mutar la bóveda. Delete y las demás confirmaciones aceptan solo
 las respuestas exactas `sí` o `si`, sin espacios adicionales.

## 6. Validación y trazabilidad

| Criterio | Prueba prevista |
|---|---|
| AC-1, AC-2 | Dobles de consola/Storage: opciones de wizard, create/open/salir, ruta absoluta, padre ausente, existente/no existente, no sobrescritura; cero accesos antes de elección. |
| AC-3 | Config store en temporal: contenido es exactamente ruta UTF-8; ausencia/corrupción/extra lines y falla de escritura no abren fallback ni incluyen secretos. |
| AC-4 | Matriz menú de cada operación delegada; cancelar/no confirma; generar conserva salida privada y no guarda. |
| AC-5 | Mapear índices, vacío/fuera de rango, mutation invalidation, insertar/borrar, verificar UUID de destino y `save()` según operación. |
| AC-6 | Búsqueda parcial case-insensitive, sin resultados, única y duplicados; ningún match se ejecuta automáticamente; comparar servicio/usuario solo. |
| AC-7 | Confirmaciones exactas `sí` o `si`, canales `printSecret`/privado, no exponer ID/nota/password/TOTP en listados/búsqueda. |
| AC-8 | Consola no disponible y entrada cancelada: ningún prompt secreto/archivo creado/config tocada. |
| AC-9 | Pruebas existentes v1/v2 y persistencia UUID; confirmar sin cambios a codecs/formato. |
| AC-10 | README, `./mvnw.cmd verify`; validación Windows de `%LOCALAPPDATA%`, lanzamiento y límites reales de ACL/TTY. |
| AC-11 | Cabecera, crédito, bordes, ANSI azul/cian y fallback sin secuencias de escape. |
| AC-12 | Actualización con coincidencia, discrepancia y cancelación; limpieza, UUID/metadatos preservados y persistencia solo en éxito. |
| AC-13 | Clock fijo antes/en/después de seis meses UTC; etiqueta aproximada, aviso en umbral y ausencia de mutación/save. |

Las pruebas unitarias usan config root y archivos temporales, consola y reloj
sustituibles; no se crean bóvedas con datos sensibles. La integración del launcher
se prueba en Windows con entorno de config temporal/falso cuando sea posible, y
la prueba real de sesión requiere TTY. Registrar pruebas reales sin confundirlas
con dobles.

## 7. Secuencia de trabajo

1. Revisar estado del repo y contratos CLI/Storage; preservar cambios ajenos.
2. Implementar y probar lectura/escritura de config local aislada.
3. Implementar wizard create/open y transferencia/ownership de `VaultSession` al
   runner sin una autenticación duplicada; cubrir cancelación, errores y cierre.
4. Añadir interfaz guiada de menú, render indexado, selección, búsqueda y
   adaptación segura de salida para no mostrar UUID en flujos normales.
5. Integrar `start` con `CliApplication`/`Main`; cambiar `vault.cmd` para invocarlo
   y mantener comandos anteriores.
6. Completar tests de índices, duplicados, confirmaciones, secretos, timeout,
   fallos de config y regresión de comandos one-shot/session.
7. Actualizar README/STATUS; ejecutar pruebas Windows/`verify`, inspeccionar
   seguridad, compatibilidad v1/v2 y diff.
8. Cerrar solo tras AC-1–AC-10 y evidencia; registrar si hubo TTY real y límites
   del entorno. No publicar ni cambiar Release como parte de esta spec.

## 8. Riesgos y límites

- Reutilizar el session runner es importante para no duplicar timeout, ownership,
  reautenticación y cleanup. Si la integración requiere cambiar el modelo de
  amenazas o formato, parar y actualizar spec con aprobación antes de código.
- La ruta guardada no es un secreto criptográfico, pero revela metadatos locales.
  Los permisos dependen de ACL de Windows; no prometer ACL privada extra sin
  verificación/plataforma.
- Índices son deliberadamente temporales: no son identificadores, no se guardan
  y pueden cambiar después de mutaciones. Mostrar/confirmar servicio y usuario
  antes de borrar evita que un índice malinterpretado borre silenciosamente otra
  cuenta.
- La búsqueda parcial puede devolver coincidencias duplicadas; siempre obliga a
  elegir una posición. No usa fuzzy matching ni normalización Unicode.
- Un menú enteramente textual sigue necesitando terminal real y no cambia el
  riesgo de observación visual de secretos. No se añade portapapeles.
- La ruta de config depende de `%LOCALAPPDATA%`; si no está disponible, `start`
  falla con instrucciones y los comandos explícitos existentes siguen siendo la
  alternativa.

## 9. Cierre

Completar AC-1–AC-10, `./mvnw.cmd verify`, regresiones existentes y validación
Windows del inicio asistido. Actualizar `docs/STATUS.md`. No modificar
`docs/FORMAT.md` ni `docs/THREAT_MODEL.md`; push/publicación no forman parte del
alcance de esta spec.
