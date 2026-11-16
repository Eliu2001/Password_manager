# Plan técnico — Spec 009: Release 1.1.0 con lanzador Windows

**Estado de la spec:** aprobada e implementación autorizada el 2026-11-15.\
**Predecesoras:** Specs 007 (`distribution`) y 008 (`windows-launcher`), cerradas.\
**Alcance:** versionar la distribución como 1.1.0 y preparar JAR, lanzador Windows y checksum para publicación manual.

## 1. Contexto y decisiones aprobadas

El repositorio construye un JAR autocontenido a `target/password-vault.jar` con
Java 21 y publica actualmente `v1.0.0`, que no debe modificarse. Spec 008 añadió
localmente `vault.cmd`, que espera `password-vault-1.0.0.jar` junto al script y
documenta su configuración. Esa implementación aún no está en GitHub; se
integrará conservando sus cambios.

El usuario aprobó crear `v1.1.0` y preparar para una misma página Release tres
assets: `password-vault-1.1.0.jar`, `vault.cmd` y
`password-vault-1.1.0.jar.sha256`. La publicación y carga de assets son manuales;
no se crea tag/Release remoto ni se hace push como parte de este trabajo sin una
autorización expresa adicional.

No se agregan dependencias. No hay cambios Java, de formato de bóveda o de modelo
de amenazas. El checksum corresponde solo a los bytes finales del JAR y no se
presenta como firma/autenticación del publicador.

## 2. Componentes y cambios

- **`pom.xml`:** cambiar la versión Maven de `1.0.0` a `1.1.0`. Mantener el
  nombre de build `target/password-vault.jar`, el manifest, shade configuration y
  dependencias aprobadas sin cambios.
- **`vault.cmd`:** actualizar únicamente el nombre del JAR relativo al lanzador a
  `password-vault-1.1.0.jar`; mantener `VAULT_PATH`, validaciones, passthrough de
  consola, pausa y propagación del estado de salida de Spec 008.
- **`README.md`:** indicar `1.1.0` como la versión actual, cambiar nombres y
  ejemplos de descarga/checksum, explicar que JAR, `.cmd` y `.sha256` se
  descargan del mismo Release, e incluir el flujo manual de staging/publicación.
  Mantener los comandos desde fuente según el nombre real del build Maven.
- **Staging de Release:** fuera del repositorio, crear una carpeta visible y
  dedicada, por ejemplo `Downloads/password-vault-v1.1.0-release`. Copiar el JAR
  construido con nombre versionado, copiar el `vault.cmd` genérico sin editarlo
  con rutas personales y generar el SHA-256 del JAR final. No sobrescribir una
  carpeta existente; elegir otra ubicación o pedir decisión.
- **`docs/STATUS.md`:** registrar versión, rutas/nombres, hash, resultado de
  pruebas y que Release/tag/push siguen manuales/no ejecutados.
- **Specs 008/009:** actualizar solo la evidencia/estado necesario para hacer
  constar que Spec 008 fue publicada posteriormente como asset dentro de
  `v1.1.0` y el cierre de Spec 009. No reescribir el alcance histórico de Spec 007
  ni alterar su Release `v1.0.0`.

## 3. Flujo de preparación

1. Confirmar working tree y preservar los cambios locales de Spec 008.
2. Incrementar Maven y referencias de distribución a `1.1.0`; apuntar el
   lanzador a `password-vault-1.1.0.jar`.
3. Ejecutar el ciclo Maven y comprobar que el JAR autocontenido se construye y
   funciona con `--help`.
4. Crear staging externo sin sobrescribir datos; copiar y renombrar JAR, copiar
   el `.cmd` versionado y calcular el checksum hexadecimal sobre ese JAR final.
5. Verificar igualdad entre SHA esperado y calculado, hashes/longitudes de copia,
   argumentos launcher con JAR `1.1.0` y ayudas/documentación.
6. Completar y revisar los documentos de estado/tareas. Dejar los assets listos
   para que el usuario cree manualmente tag/Release `v1.1.0` y los cargue.

## 4. Validación y trazabilidad

| Criterio | Verificación |
|---|---|
| AC-1 | Inspeccionar versión del POM y referencias activas de README/script; comprobar que el tag/Release `v1.0.0` no se toca. |
| AC-2 | Ejecutar `./mvnw.cmd verify`; luego `java -jar target/password-vault.jar --help` y comprobar estado 0/ayuda. |
| AC-3 | Copiar el JAR construido como `password-vault-1.1.0.jar`; generar y recalcular SHA-256 sobre la copia final; comparar exactamente. |
| AC-4 | Prueba Windows del `vault.cmd` empaquetado junto al archivo JAR esperado, con Java de prueba: ubicación relativa, argumentos, ruta sintética con espacios, errores y códigos de salida. |
| AC-5 | Revisar instrucciones, nombres de los tres assets y seguridad de la configuración en README. |
| AC-6 | Registrar `verify`, ayuda, checksum y pruebas Windows realmente ejecutadas; no inferir prueba TTY real de los mocks. |
| AC-7 | Inspeccionar `git status`/comandos realizados: ningún push, tag o publicación remota. |

Las pruebas de script usan únicamente rutas y argumentos sintéticos. No se
capturan secretos ni se incluye la bóveda. Los comandos de ayuda no requieren
TTY; no automatizar una sesión que maneje contraseñas mediante pipes o
redirección. La publicación del Release se verifica solo si el usuario la hace y
autoriza una comprobación posterior.

## 5. Secuencia de implementación

1. Revalidar la spec aprobada, estado del repo y contenidos locales de Spec 008.
2. Cambiar versión POM y referencias versionadas del README; actualizar el nombre
   esperado por `vault.cmd`.
3. Ejecutar `verify`, probar el JAR, revisar nombre final, manifest y licencia.
4. Ejecutar matriz de Windows para el launcher actualizado y casos de fallo.
5. Preparar los tres assets en carpeta externa visible, sin rutas personales;
   recalcular el SHA del JAR staged y verificar la copia.
6. Registrar estado/resultados y revisar diferencias sin incluir artefactos,
   vaults o temporales en el repo.
7. Cerrar con todos los assets listos. Detenerse antes de push, tag y publicación
   remota, que no están autorizados por esta spec.

## 6. Riesgos y límites

- Si se actualiza el POM pero no el nombre que busca el `.cmd`, el paquete no
  arranca; los tests deben comprobar el nombre exacto.
- Un checksum calculado antes de copiar/renombrar no acredita el archivo staged;
  se genera y verifica sobre el JAR final dentro de la carpeta de Release.
- La configuración de `VAULT_PATH` debe permanecer en el marcador genérico al
  publicar; incluir una ruta personal sería una filtración de privacidad.
- La carpeta staging puede contener artefactos grandes y no debe quedar dentro
  del repositorio. No sobrescribir archivos previos.
- Esta preparación no comprueba el funcionamiento del asset una vez cargado en
  GitHub; hace falta publicación manual y, si se solicita, verificación posterior.

## 7. Cierre

Cerrar con AC-1–AC-7 cubiertos, `./mvnw.cmd verify` verde, JAR/ayuda/checksum y
launcher Windows validados, tres assets disponibles en staging externo y
`docs/STATUS.md` actualizado. Dejar explícito que no se hizo push, tag ni Release.
