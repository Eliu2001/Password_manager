# Tareas — Spec 010: Asistente y menú guiado de consola

Tareas ordenadas por dependencia y diseñadas para bloques de **20–30 minutos**.
Completar cada tarea con evidencia breve; no marcarla antes de verificar sus
criterios. No incluir secretos reales ni archivos de bóveda en el repo. Spec 010
fue aprobada por el usuario el 2026-11-15; el usuario autorizó implementar tras
preparar plan y tareas. No realizar push ni publicar Release como parte de esta
spec.

## Preparación y configuración local

- [x] **T01 — Confirmar estado y contratos de CLI/Storage** (20 min)\
  **RF:** RF-1–RF-9 · **AC:** AC-1–AC-10\
  **Hecho cuando:** se revisa `git status` para preservar cambios, `Main`, parser,
  runner, handler, `VaultStorage.create/open`, consola, TOTP/generator, `vault.cmd`,
  README y las specs aprobadas. Se identifica ownership de sesión y no se cambia
  formato/dependencias.\
  **Evidencia:** HEAD/estado local y contratos relevantes registrados antes de
  implementar.\
  **Resultado:** HEAD `0ed8bc8` (`main`); working tree solo tenía la carpeta
  nueva Spec 010. `create` devuelve sesión abierta; runner actualmente siempre
  autentica con open; comandos de sesión usan VaultCliCommands; consola ya
  sustituible. Formatos/deps no precisan cambios.

- [x] **T02 — Implementar `VaultPathConfig` aislado** (25 min)\
  **RF:** RF-2, RF-3 · **AC:** AC-3\
  **Hecho cuando:** clase `cli` resuelve `%LOCALAPPDATA%\PasswordVault\vault-path.txt`,
  lee/escribe solo una ruta absoluta UTF-8, rechaza config vacía/malformada y
  escribe con temporal/reemplazo sin truncar config anterior ante fallo. La ruta
  de raíz se puede inyectar en pruebas.\
  **Evidencia:** `VaultPathConfigTest` valida ausencia, ruta absoluta con espacios,
  rutas relativas, contenido extra y rutas multilinea. Maven directo
  `-Dtest=VaultPathConfigTest test`: BUILD SUCCESS. Wrapper falla porque su script
  PowerShell indexa `.Target[0]` en directorio `.m2` normal; se invocó la
  distribución Maven 3.10.0 ya instalada sin modificar el wrapper.

- [ ] **T03 — Implementar asistente de selección de bóveda** (25 min)\
  **RF:** RF-1, RF-2, RF-8 · **AC:** AC-1, AC-2, AC-8\
  **Hecho cuando:** entrada sin config/ruta ausente ofrece Crear/Abrir/Salir,
  pide ruta absoluta de archivo, rechaza directorios/archivo existente para Create,
  requiere existencia para Open y cancela sin fallback. Comprobar consola antes
  de leer o mutar.\
  **Evidencia:** matriz con config ausente/corrupta, Create/Open/cancel, path
  relativo, padre ausente, directorio, destino existente, consola no disponible y
  cero llamadas de Storage ante selección inválida.

- [ ] **T04 — Integrar autenticación, configuración y ownership de sesión** (25 min)\
  **RF:** RF-2, RF-3, RF-8 · **AC:** AC-2, AC-3, AC-8\
  **Hecho cuando:** guardar ruta solo tras create/open exitoso; crear reutiliza el
  `VaultSession` devuelto por Storage sin prompt de contraseña adicional; abrir
  autentica una vez. Fallo al guardar config cierra recursos y no indica setup
  completo. No se pierde una sesión activa ni se deja lock/clave abiertos.\
  **Evidencia:** dobles verifican orden Storage/config, contraseña `char[]` borrada,
  config sin secretos, sesión cerrada ante errores y sesión transferida al runner
  exactamente una vez.

## Runner y menú guiado

- [ ] **T05 — Añadir entrada `start` y modo de sesión guiada** (25 min)\
  **RF:** RF-4, RF-8 · **AC:** AC-4, AC-8\
  **Hecho cuando:** `CliApplication`/`Main` exponen `start` para launcher; un
  `CliSessionRunner` puede adoptar una sesión ya autenticada, conservar timeout,
  reautenticación tras auto-lock, EOF/interrupción/cierre y dejar el REPL `session`
  existente compatible.\
  **Evidencia:** pruebas de transferencia ownership, exit, EOF, error y auto-lock;
  tests previos de sesión siguen pasando.

- [ ] **T06 — Construir menú textual y navegación segura** (25 min)\
  **RF:** RF-4, RF-7 · **AC:** AC-4, AC-7\
  **Hecho cuando:** menú en español enumera operaciones existentes y acepta
  opciones numéricas, ayuda/salir; entrada vacía/incorrecta no despacha acción,
  operación cancelada vuelve al menú y salida limpia sesión. No hay secretos en
  texto del menú ni historial.\
  **Evidencia:** pruebas de tabla de opciones, invalid input, EOF, cancelación y
  retorno al menú con consola sustituible.

- [ ] **T07 — Añadir listados indexados y selección efímera** (25 min)\
  **RF:** RF-5 · **AC:** AC-5, AC-7, AC-9\
  **Hecho cuando:** listados muestran desde 1 posición, servicio y usuario, sin
  UUID, contraseña, notas o TOTP. Índice se resuelve contra la instantánea actual;
  mutaciones invalidan el resultado y ninguna selección inválida ejecuta
  operación.\
  **Evidencia:** pruebas con UUIDs sintéticos para primer/último/índice fuera de
  rango, lista vacía, orden, adición/borrado y verificación del UUID destino/save.

- [ ] **T08 — Implementar búsqueda parcial y elección de resultados** (25 min)\
  **RF:** RF-6 · **AC:** AC-5, AC-6\
  **Hecho cuando:** búsqueda `Locale.ROOT`, parcial, sin normalización Unicode y
  limitada a servicio; cero resultados informa, uno exige confirmación de acción,
  múltiples requieren elegir posición y no se toma el primero automáticamente.\
  **Evidencia:** pruebas de case-insensitive, coincidencia parcial, Unicode según
  regla, cero/uno/múltiples matches, índice inválido y campos de salida permitidos.

## Operaciones, seguridad y compatibilidad

- [ ] **T09 — Adaptar CRUD y salida para modo guiado** (25 min)\
  **RF:** RF-4, RF-5, RF-7 · **AC:** AC-4, AC-5, AC-7\
  **Hecho cuando:** add/show/delete usan ID resuelto en memoria; modo guiado no
  obliga a copiar ni imprime IDs largos. `show` requiere `sí`, `delete` conserva
  confirmación, cancelaciones no guardan y altas/borrados exitosos persisten según
  contrato existente. Los comandos legacy conservan compatibilidad.\
  **Evidencia:** tests de secreto solo por `printSecret`, confirmación exacta,
  cancel/save, ruta de entrada y diferencias de salida legacy/guided.

- [ ] **T10 — Integrar TOTP, upgrade y generador en el menú** (25 min)\
  **RF:** RF-4, RF-7 · **AC:** AC-4, AC-7\
  **Hecho cuando:** todas las funciones avanzadas del alcance son accesibles con
  selección por posición; flujos TOTP/upgrade conservan confirmaciones, formato y
  save; generate sigue independiente de vault y usa canal privado.\
  **Evidencia:** pruebas guiadas de no-TOTP/v1, confirmaciones, cancelación,
  secreción de código, upgrade y salida de generador; limpiar buffers controlables.

- [ ] **T11 — Revisar regresiones y archivos v1/v2** (25 min)\
  **RF:** RF-8, RF-9 · **AC:** AC-8, AC-9\
  **Hecho cuando:** comandos one-shot/`session`, parser de IDs largos y formatos
  v1/v2 se mantienen; sin nuevas dependencias, cambios de codec ni Threat Model.
  Consola ausente no lee secretos ni toca archivos.\
  **Evidencia:** suite CLI/storage existente, regresiones new guided mode y diff de
  `pom.xml`, `docs/FORMAT.md`, `docs/THREAT_MODEL.md` sin cambios.

## Launcher, documentación y cierre

- [ ] **T12 — Conectar `vault.cmd` al asistente** (20 min)\
  **RF:** RF-8 · **AC:** AC-1, AC-8, AC-10\
  **Hecho cuando:** launcher invoca `start` en el JAR junto a él, sin `VAULT_PATH`
  hardcoded; conserva comprobaciones de Java/JAR, passthrough de consola, pausa y
  estado de salida. Mensajes de precondición siguen claros.\
  **Evidencia:** Java de prueba captura `start`, pruebas Java/JAR ausentes y
  códigos de salida; validación real Windows separada si está disponible.

- [x] **T13 — Actualizar README y estado de proyecto** (25 min)\
  **RF:** RF-1–RF-9 · **AC:** AC-10\
  **Hecho cuando:** README detalla wizard, config local y metadatos, opciones de
  menú, números temporales, búsqueda, revelación y límites/errores; STATUS registra
  plataforma, pruebas y limitaciones realmente observadas.\
  **Evidencia:** README aclara que el Release 1.1.0 publicado no trae aún el nuevo
  flujo; pasos para fuente/revisión actualizados sin rutas reales ni secretos.
  `docs/STATUS.md` registra estado, verificación y limitaciones de TTY/launcher.

- [x] **T14 — Añadir cabecera visual y tema azul/cian** (25 min)\
  **RF:** RF-10 · **AC:** AC-11\
  **Hecho cuando:** menú muestra `PASSWORD VAULT`, `Creado por Eliu2001`, bordes
  y acentos ANSI azul/cian si se detecta soporte; fallback ASCII sin escapes si
  no. No se agregan dependencias ni se colorean secretos/errores.\
  **Evidencia:** pruebas de tema forzado con/sin color, título, crédito, opciones y
  ausencia de códigos ANSI en fallback; detección Warp y precedencia `NO_COLOR`.
  `ConsoleThemeTest`: 3 pruebas pasan.

- [x] **T15 — Permitir actualizar la contraseña de una entrada** (25 min)\
  **RF:** RF-11 · **AC:** AC-12\
  **Hecho cuando:** acción guiada selecciona por índice vigente, lee la contraseña
  nueva dos veces sin eco, cancela si no coinciden y si coinciden actualiza solo
  la contraseña, guarda y preserva UUID/servicio/usuario/notas. Ambos arreglos se
  limpian y el secreto nunca se imprime.\
  **Evidencia:** pruebas de éxito, discrepancia, cancelación, save/no-save, campos
  preservados, limpieza y ausencia de contraseña en salida.
  `GuidedPasswordUpdateTest`: éxito, discrepancia y cancelación; verifica archivo,
  campos preservados, buffers borrados y ausencia del valor secreto en salida.

- [x] **T16 — Actualizar documentación de menú y pruebas** (20 min)\
  **RF:** RF-4, RF-10–RF-12 · **AC:** AC-4, AC-10–AC-13\
  **Hecho cuando:** README documenta cabecera/autor, opción de actualizar
  contraseña, repetición de entrada, antigüedad aproximada y alcance de TOTP;
  STATUS registra pruebas y limitaciones reales de Windows.\
  **Evidencia:** README y STATUS describen el menú, rotación, `updatedAt` como
  aproximación y el umbral de seis meses; ejemplos sin rutas del usuario/secretos.

- [x] **T17 — Mostrar antigüedad aproximada de contraseñas** (25 min)\
  **RF:** RF-12 · **AC:** AC-13\
  **Hecho cuando:** listados y búsquedas muestran “última actualización: hace …”
  desde `Entry.updatedAt`, marcan seis meses calendario UTC o más y aclaran que
  es una aproximación. No modifica ni guarda la bóveda.\
  **Evidencia:** `PasswordAgeTest` usa reloj fijo para casos antes/en/después del
  límite; verifica texto y advertencia. Confirmar que el menú lo presenta sin
  mutación. `GuidedVaultMenuTest` verifica la antigüedad en el listado y que no se
  guarda de nuevo.

- [ ] **T18 — Verificación integral, plataforma y cierre** (25 min)\
  **RF:** RF-1–RF-12 · **AC:** AC-1–AC-13\
  **Hecho cuando:** `./mvnw.cmd verify` y `git diff --check` pasan; prueba Windows
  de config/launcher y suite automatizada cubren todos los AC. Se registra TTY/ACL
  solo si realmente se validó. No crear Release ni hacer push.\
  **Evidencia:** BUILD SUCCESS/conteo de pruebas, matriz AC, resultados Windows,
  status limpio y confirmación de que formato/Threat Model no se alteraron.

## Criterio de cierre

Cerrar Spec 010 solo al completar T01–T18 con evidencia y cubrir AC-1–AC-13. No
incluir push, publicación, GUI nativa, portapapeles o cambio del formato como
requisitos implícitos.
