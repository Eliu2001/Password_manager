# Plan técnico — Spec 007: Distribución del CLI

**Estado de la spec:** implementación autorizada el 2026-11-15; plugin `maven-shade-plugin:3.6.2` aprobado.\
**Predecesora:** Spec 006 (`auto-lock`), cerrada.\
**Alcance:** empaquetar el CLI en un JAR autocontenido para Java 21 y preparar su distribución manual como `v1.0.0`.

## 1. Contexto y decisiones aprobadas

Al iniciar la implementación, el proyecto usaba Maven Wrapper y Java 21,
`pom.xml` identificaba el artefacto como
`cl.germain:password_manager:1.0-SNAPSHOT`, declaraba JUnit 5 solo para tests y
Bouncy Castle 1.86 como dependencia de runtime. El plugin exec ya conocía la
clase principal y CI Linux configuraba Java 21 para `bash ./mvnw verify`; no
existían empaquetado autocontenido ni README.

La spec aprueba cambiar la versión Maven a `1.0.0`, generar localmente
`target/password-vault.jar`, incluir dependencias de runtime sin minimizar ni
reubicar bytecode, conservar avisos/licencias, documentar el uso en español y
adjuntar manualmente el JAR y un checksum SHA-256 al Release `v1.0.0`. CI debe
arrancar `--help` desde el artefacto. No se automatiza la publicación ni se
incluye una JVM.

El usuario autorizó añadir `org.apache.maven.plugins:maven-shade-plugin:3.6.2`
para el empaquetado. Se fija esa versión y se mantiene como plugin de build, no
como dependencia runtime. No se agregan otras dependencias.

## 2. Arquitectura y responsabilidades

- **Maven (`pom.xml`):** fijar versión del proyecto en `1.0.0`; configurar el
  plugin de empaquetado aprobado para producir el nombre estable
  `password-vault.jar` en `package`, declarar `Main-Class` como
  `cl.germain.vault.cli.Main` y conservar dependencias de runtime necesarias.
  JUnit y demás dependencias de test no deben entrar en el JAR. No aplicar
  minimización, relocation ni transformación de bytecode; preservar metadatos de
  proveedor que requiera el JDK.
- **Artefacto:** `target/password-vault.jar` será el resultado del lifecycle
  normal `verify`; no se versiona ni se incluye en el repositorio. La
  comprobación del manifest, clases y contenido se hará con herramientas JDK o
  Maven, sin agregar dependencias runtime.
- **CI (`.github/workflows/verify.yml`):** mantener Java 21 y el verify normal;
  añadir una prueba explícita `java -jar target/password-vault.jar --help` y
  comprobar su salida/código. No invocar comandos operativos que requieran TTY.
- **README (`README.md`):** ser la guía primaria en español: propósito,
  requisitos, descarga desde Releases, ejecución del JAR, comandos y rutas,
  autobloqueo, límites de consola/secretos, ejecución desde fuente, checksum,
  dependencias/licencias y pasos manuales para crear el Release.
- **Release/checksum:** las instrucciones describirán cómo nombrar el adjunto
  publicado `password-vault-1.0.0.jar` y producir/verificar
  `password-vault-1.0.0.jar.sha256`. El build no publicará ni generará una
  release remota automáticamente.
- **Estado:** `docs/STATUS.md` recogerá artefacto, versión y resultados por
  plataforma únicamente después de ejecutarlos. `AGENTS.md` puede enlazar al
  README si resulta necesario; no se cambiarán formato de bóveda ni Threat Model.

## 3. Flujo de build y uso

1. Maven compila, prueba y verifica formato con Java 21; durante `package`
   produce `target/password-vault.jar` con manifest ejecutable y dependencias de
   runtime.
2. `verify` comprueba además estructura/contenido relevante y el arranque
   `java -jar target/password-vault.jar --help`, independiente de Maven,
   classpath adicional, directorios de dependencias y conexión de red una vez
   construido.
3. El usuario instala Java 21 compatible, descarga el JAR desde GitHub Releases
   y ejecuta `java -jar password-vault-1.0.0.jar ...`. Las operaciones que
   manejan secretos siguen exigiendo consola interactiva real; no se añade
   fallback a stdin ni se pasan secretos por argumentos o variables de entorno.
4. Para una publicación manual, quien mantiene el proyecto crea el tag/Release
   `v1.0.0`, adjunta el JAR con nombre versionado y un archivo de checksum
   SHA-256 calculado sobre esos mismos bytes. Los comandos Windows y POSIX para
   calcular/verificar el hash se documentan en README.

## 4. Validación y estrategia de pruebas

| Criterios | Verificación prevista |
|---|---|
| AC-1 | Inspeccionar versión Maven, nombre/ruta del artefacto y manifest `Main-Class`. |
| AC-2 | Inspeccionar entradas del JAR: Bouncy Castle presente, JUnit ausente, avisos/licencias preservados; ejecutar `java -jar ... --help` sin classpath ni Maven y comprobar ayuda/código exitoso. |
| AC-3 | Ejecutar `./mvnw.cmd verify` localmente en Windows y el workflow Linux con Java 21; ambos deben construir y ejecutar la ayuda del artefacto. Registrar plataformas realmente ejecutadas. |
| AC-4, AC-5 | Revisar README contra el parser/ayuda y el comportamiento real: requisitos, descarga, comandos, rutas, autobloqueo, terminal, manejo de secretos y Maven Wrapper. |
| AC-6 | Revisar ejemplos y repositorio para confirmar que son ficticios, sin vaults/secrets; comprobar que no se agregó runtime, fallback inseguro ni cambios de formato/Threat Model. |
| AC-7 | Validar comandos de checksum para Windows y POSIX sobre un artefacto de build; revisar instrucciones de Release manual y ausencia de automatización de publicación. |

No se automatizará entrada de secretos, ni se probarán comandos operativos por
pipes. El hash verifica integridad respecto al checksum publicado, no acredita
por sí solo la identidad del publicador; no se afirmará que el artefacto está
firmado. No se debe marcar verificada una plataforma o instrucción que no se
haya ejecutado.

## 5. Secuencia de trabajo

1. Confirmar autorización de implementación y aprobación del plugin Maven/version
   fija; revisar los contratos actuales de build, CI, CLI y licencias transitivas.
2. Actualizar versión Maven y configurar empaquetado ejecutable con nombre
   estable y manifest; verificar que el lifecycle `verify` genera el JAR.
3. Inspeccionar JAR, contenido runtime/test, manifest y avisos/licencias; probar
   arranque aislado local y checksum en Windows.
4. Extender CI Linux para arrancar la ayuda del JAR generado con Java 21.
5. Redactar README en español fiel al CLI actual, con seguridad de consola,
   requisitos, instalación, comandos, fuente, licencias, checksum y flujo manual
   de Release.
6. Revisar alcance y documentación; ejecutar `./mvnw.cmd verify`, revisar diff y
   registrar resultados/limitaciones en `docs/STATUS.md`.
7. Esperar ejecución CI Linux y registrar su evidencia antes de declarar cerrada
   la spec. La creación/publicación real del Release no forma parte del build ni
   es condición para probar el flujo documentado.

## 6. Riesgos y límites

- El plugin de empaquetado requiere mantenimiento de versión; la versión 3.6.2
  fue aprobada explícitamente para esta implementación.
- Los JAR de dependencias pueden contener archivos de servicio o avisos que una
  combinación/filtrado incorrecto del empaquetador puede sobrescribir. Revisar
  metadatos y recursos necesarios; no resolverlo con minimización o relocation.
- La portabilidad del artefacto significa ejecución sobre Java 21 compatible,
  no ejecución nativa ni JVM incluida. La CI Linux y la validación Windows son
  comprobaciones separadas.
- El checksum detecta discrepancias con el valor obtenido por un canal confiable,
  pero no reemplaza firma criptográfica ni protege si JAR y checksum se
  sustituyen juntos.
- Los comandos de ayuda pueden funcionar sin TTY; operaciones con secretos deben
  continuar fallando sin `System.console()` según la política actual.
- No se tocarán `docs/FORMAT.md` ni `docs/THREAT_MODEL.md`, y no se cambiarán
  comandos, dependencias runtime ni el formato de bóveda.

## 7. Cierre

Cerrar únicamente con evidencia de AC-1–AC-7, `./mvnw.cmd verify` en verde,
arranque real del JAR en Windows y CI Linux, README coherente y resultados de
plataforma registrados en `docs/STATUS.md`.
