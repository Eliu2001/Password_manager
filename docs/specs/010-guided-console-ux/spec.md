# Spec 010 — Asistente y menú guiado de consola

**Estado:** aprobada por el usuario el 2026-11-15; implementación autorizada tras plan y tareas.
**Predecesoras:** Specs 003 (`cli-mvp`), 005 (`totp`), 006 (`auto-lock`) y 008 (`windows-launcher`), cerradas.

El 2026-11-16 se autorizó aceptar `sí` o `si` exactos en las confirmaciones
existentes, sin recorte de espacios ni conversión de mayúsculas.

## 1. Objetivo

Reducir la confusión del primer uso y evitar que el usuario tenga que copiar IDs
hexadecimales de 32 caracteres. La aplicación ofrecerá un asistente textual para
crear/abrir una bóveda y un menú guiado que permita gestionar entradas y las
funciones existentes desde una terminal.

## 2. Alcance

**Dentro:**

- Asistente de inicio para elegir explícitamente entre crear una bóveda nueva o
  abrir una existente cuando no haya una configuración válida o la ruta guardada
  ya no exista.
- Guardar de forma local únicamente la ruta de la bóveda seleccionada en
  `%LOCALAPPDATA%\PasswordVault\vault-path.txt`; nunca guardar contraseña maestra,
  contraseña de entrada ni secreto TOTP en esa configuración.
- Menú textual en español para las funciones existentes: agregar, listar,
  buscar, ver/revelar, actualizar la contraseña de una entrada, borrar,
  configurar/retirar/consultar TOTP, actualizar una bóveda v1 y generar una
  contraseña.
- Presentación visual del menú con título destacado `PASSWORD VAULT`, el crédito
  `Creado por Eliu2001`, bordes/separadores y acentos azul/cian. Usar secuencias
  ANSI solo en la consola interactiva; ofrecer una presentación legible sin color
  cuando la terminal no anuncie soporte. Sin dependencias de interfaz.
- Búsqueda por coincidencia parcial, sin distinguir mayúsculas/minúsculas, sobre
  el nombre del servicio. Cero resultados informa claramente; varios resultados
  se presentan para elegir, sin seleccionar automáticamente el primero.
- Mostrar posiciones numéricas temporales en listados/resultados y aceptar esas
  posiciones para seleccionar una entrada. Los números solo valen para la lista
  actual; los UUID internos de 128 bits permanecen estables en el archivo y no se
  cambian ni se muestran como requisito de uso normal.
- Conservar los comandos CLI y `session` existentes para compatibilidad y uso
  avanzado. El lanzador Windows inicia el asistente/menú nuevo.
- Actualizar README y `docs/STATUS.md` con configuración, funcionamiento y
  limitaciones.

**Fuera:**

- GUI nativa, TUI con dependencia externa, ratón, portapapeles, búsqueda web,
  autocompletado o sincronización.
- Cambiar IDs persistidos, modelo/serialización o formato v1/v2 de la bóveda.
- Renombrar UUIDs, regenerarlos o migrar archivos existentes.
- Recuperar una contraseña maestra olvidada, cambiar reglas criptográficas,
  cambiar el modelo de amenazas o guardar cualquier secreto en configuración.
- Crear directorios padre de la bóveda sin confirmación explícita ni cambiar la
  regla existente de Storage que no los crea automáticamente.

## 3. Decisiones acordadas

- La interfaz será un menú guiado de texto en consola, no una aplicación gráfica;
  los comandos actuales seguirán disponibles.
- El menú abarcará funciones principales y avanzadas ya existentes.
- Los selectores numéricos serán índices efímeros del resultado/listado actual.
- La búsqueda será parcial y no sensible a mayúsculas sobre el servicio; los
  resultados múltiples requerirán selección explícita.
- La ruta seleccionada se guardará en configuración local bajo
  `%LOCALAPPDATA%\PasswordVault`; el archivo no contendrá secretos.
- UUIDs y formato en disco permanecerán sin cambios.
- El título/autor y los colores son presentación estática; no se infieren datos
  personales del sistema ni se alteran los mensajes de seguridad/confirmación.
- Actualizar contraseña conserva UUID, servicio, usuario y notas; solicita la
  nueva contraseña dos veces por consola privada y guarda solo si ambas coinciden.
- Mostrar una antigüedad aproximada de contraseña basada en el `updatedAt`
  existente; usar el rótulo “última actualización” y avisar cuando tenga seis
  meses o más. No se agrega un timestamp ni se cambia el formato.

## 4. Requisitos funcionales

- **RF-1 — Asistente explícito:** sin configuración, con configuración inválida
  o bóveda ausente, presentar opciones claras para crear una nueva, abrir una
  existente o salir. No inferir ni elegir silenciosamente una ruta.
- **RF-2 — Persistencia de ruta:** solicitar una ruta completa al asistente y
  guardar solo la ruta tras completar correctamente la selección/operación. Si la
  configuración está dañada o no se puede leer/escribir, informar y permitir
  cancelar o corregir; nunca caer a una ruta predeterminada.
- **RF-3 — Configuración segura:** escribir bajo `%LOCALAPPDATA%` usando
  codificación definida; configuración ausente/corrupta se rechaza o repara con
  intervención explícita. No escribir contraseñas, contenido de bóveda ni
  secretos TOTP. La ruta es metadato local y puede revelar la ubicación del
  archivo a quien acceda a la cuenta Windows.
- **RF-4 — Menú de funciones:** ofrecer acciones numeradas en español para las
  funciones existentes aprobadas. Cada acción invoca la misma lógica/confirmación
  y persistencia de sus comandos actuales; cancelar vuelve al menú de forma
  segura.
- **RF-5 — Índices temporales:** cada listado muestra posiciones consecutivas,
  servicio y usuario, nunca contraseña/notas/TOTP. Para una acción sobre una
  entrada, el índice se resuelve contra el resultado vigente de esa vista; tras
  una mutación se invalida y debe volver a listarse/buscarse antes de otra
  selección. Un índice inválido o vencido no afecta ninguna entrada.
- **RF-6 — Búsqueda:** buscar por texto parcial del servicio, sin distinguir
  mayúsculas/minúsculas. Ningún resultado produce mensaje; una coincidencia única
  se ofrece sin ejecutar automáticamente una operación sensible; varias se
  enumeran y se elige un índice. Los campos mostrados siguen siendo solo servicio
  y usuario.
- **RF-7 — Secretos y confirmaciones:** mostrar contraseña de entrada solo tras
  una confirmación afirmativa exacta (`sí` o `si`) y por la consola privada;
  borrado y operaciones TOTP conservan sus confirmaciones actuales aceptando esas
  mismas dos respuestas. No se recortan espacios ni se convierten mayúsculas. No
  almacenar ni mostrar contraseñas en menús, búsqueda, configuración, historial,
  logs o errores.
- **RF-8 — Consola y compatibilidad:** el asistente/menú requiere `System.console()`
  real; no usa stdin como fallback ni pipes. Los comandos `create`, `session` y
  one-shot existentes continúan aceptando los IDs largos actuales. `vault.cmd`
  llama al nuevo flujo guiado y conserva el manejo de errores/códigos de salida.
- **RF-9 — Formato/dependencias:** UUIDs, formato de bóveda v1/v2 y dependencias
  no cambian. No se agrega librería de interfaz.
- **RF-10 — Presentación visual:** al entrar al menú, mostrar el título destacado
  `PASSWORD VAULT`, el crédito `Creado por Eliu2001`, bordes/separadores y colores
  azul/cian cuando se detecte soporte ANSI. En terminales sin soporte, mostrar el
  mismo contenido y jerarquía con texto ASCII sin secuencias de escape. Mantener
  visibles todas las opciones, legibles los prompts y las confirmaciones, y no
  incluir escape codes en datos/logs ni agregar dependencias.
- **RF-11 — Actualizar contraseña:** desde una entrada seleccionada por índice,
  solicitar la contraseña nueva dos veces mediante `readPassword`; si no
  coinciden, cancelar sin cambiar ni guardar. Si coinciden, reemplazar únicamente
  la contraseña de esa entrada, conservar UUID/servicio/usuario/notas y persistir
  explícitamente la sesión. Limpiar ambos `char[]` en éxito, cancelación y error.
- **RF-12 — Antigüedad aproximada:** mostrar para cada entrada “última
  actualización: hace …” usando `Entry.updatedAt`. Al llegar o superar seis meses
  según meses calendario UTC, mostrar que conviene cambiar la contraseña. Como
  `updatedAt` es general y no exclusivo de contraseña, etiquetar claramente el
  resultado como aproximado; no declarar que es la fecha exacta del último cambio
  de contraseña. No modificar almacenamiento/formato.

## 5. Criterios de aceptación

- **AC-1:** sin ruta configurada, el asistente presenta crear/abrir/salir; nunca
  elige o crea una bóveda sin la selección explícita del usuario.
- **AC-2:** crear exige una ruta completa de archivo y nunca sobrescribe una
  bóveda existente; abrir exige un archivo existente. La ruta se persiste solo
  después de una acción exitosa.
- **AC-3:** configuración local contiene únicamente la ruta; contraseñas maestras,
  contraseñas guardadas y secretos TOTP no aparecen en ella.
- **AC-4:** menú accesible ofrece CRUD, búsqueda, operaciones TOTP, upgrade,
  generador, ayuda y salida; cada acción mantiene reglas de confirmación y
  persistencia previas.
- **AC-5:** las entradas se pueden seleccionar por número en listados y
  resultados; índices inválidos/obsoletos fallan sin actuar sobre otra entrada.
- **AC-6:** búsqueda parcial del servicio ignora mayúsculas/minúsculas, informa
  cero resultados y nunca elige automáticamente entre resultados múltiples.
- **AC-7:** contraseña de entrada solo se revela tras la respuesta exacta `sí` o
  `si` y por canal privado; listas, búsquedas y menús no exponen secretos/notas.
  Borrado y confirmaciones TOTP/upgrade aceptan únicamente esas dos respuestas;
  cualquier otra entrada, incluidos espacios adicionales o mayúsculas, no confirma.
- **AC-8:** con la consola no disponible, el flujo falla antes de leer secretos o
  cambiar la bóveda; ningún fallback a stdin ni uso mediante pipes.
- **AC-9:** UUIDs de entradas previas siguen idénticos y los archivos v1/v2
  existentes continúan abriéndose; no cambia `docs/FORMAT.md` salvo que se cambie
  el formato, lo que queda fuera de esta spec.
- **AC-10:** README describe asistente, configuración local, menú, índices, search,
  cómo revelar contraseñas y limitaciones; `./mvnw.cmd verify` pasa y se registran
  pruebas Windows relevantes.
- **AC-11:** el menú presenta `PASSWORD VAULT` y `Creado por Eliu2001` con bordes y
  acentos azul/cian en una terminal ANSI compatible; en modo sin color mantiene
  el título, autor, separadores y legibilidad sin imprimir códigos ANSI. No cambia
  opciones, confirmaciones ni salida de secretos.
- **AC-12:** se puede actualizar la contraseña desde una entrada seleccionada;
  ambas lecturas deben coincidir; discrepancia/cancelación no altera archivo ni
  modelo; éxito persiste solo la nueva contraseña y conserva el UUID y los demás
  campos. Los buffers de entrada quedan borrados y la contraseña nunca se imprime.
- **AC-13:** listados y búsqueda muestran la última actualización aproximada de
  cada entrada y advierten a partir de seis meses calendario UTC; el cálculo usa
  `updatedAt`, no altera el formato y la etiqueta no afirma una fecha exacta de
  cambio de contraseña.

## 6. Seguridad y pruebas requeridas

- Usar consola sustituible y Storage temporal para probar decisiones, cancelación,
  errores de config, menús, índices, búsquedas y persistencia; no incluir secretos
  reales ni bóvedas en el repo.
- Comprobar que archivo de configuración contiene solo ruta y que errores de
  acceso/configuración no registran secretos ni abren otra bóveda.
- Comprobar que indexar/buscar no llama `save()` ni revela campos ocultos; borrar
  siempre requiere la respuesta exacta `sí` o `si`, y entradas con espacios,
  mayúsculas u otras respuestas no confirman.
- Verificar selección tras añadir/borrar, IDs no encontrados, duplicados de
  servicio, coincidencias múltiples, entradas vacías y caracteres Unicode/case
  folding dentro de las reglas acordadas.
- Probar que los flujos TOTP, upgrade y generator conservan el canal privado y
  limpian buffers conforme a sus specs; no automatizar entrada secreta real.
- Mantener `crypto` independiente; no modificar `docs/THREAT_MODEL.md` sin
  petición expresa ni cambiar el formato.
- Probar salida coloreada y modo sin color con consola sustituible; verificar
  título/crédito, estructura del menú, ausencia de escapes en fallback y que no se
  filtren códigos en prompts, secretos o mensajes de error.
- Probar actualización con contraseñas coincidentes, discrepantes y entrada
  cancelada; comparar ID y metadatos antes/después, comprobar save solo en éxito,
  limpiar ambos arreglos y confirmar que no aparece el contenido en salidas.
- Probar antigüedad con `Clock` fijo justo antes, en y después del límite de seis
  meses; verificar meses calendario, UTC, texto aproximado y que la presentación
  no muta ni guarda la bóveda.

## 7. Documentación y cierre

La spec y su plan deben aclarar cómo se inicia el asistente desde el lanzador y
qué comandos avanzados quedan disponibles. README debe mostrar pasos Windows sin
rutas reales del usuario. `docs/STATUS.md` registra resultados de Windows y
limitaciones de consola/configuración. Cerrar solo con AC-1–AC-10 cubiertos y
`./mvnw.cmd verify` verde. Las ampliaciones visual y de actualización de
contraseña solicitadas el 2026-11-15 agregan AC-11 y RF-11/AC-12. La antigüedad
usa `updatedAt` como aproximación, acordada el 2026-11-15; no cambia formato ni
dependencias.
