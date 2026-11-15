# Spec 007 — Distribución del CLI

**Estado:** aprobada el 2026-11-15; implementación autorizada por el usuario el 2026-11-15 tras aprobar `maven-shade-plugin:3.6.2`.  
**Predecesora:** `docs/specs/006-auto-lock/spec.md`, cerrada.

## 1. Objetivo

Permitir que otras personas descarguen y ejecuten el gestor desde GitHub Releases
sin instalar Maven ni resolver manualmente dependencias. La primera distribución
será un JAR autocontenido para Java 21, acompañado por una guía de instalación y
uso en español.

## 2. Alcance

**Dentro:**

- Crear el primer artefacto público como `1.0.0`.
- Empaquetar la CLI y sus dependencias de runtime en un JAR ejecutable con
  `Main-Class: cl.germain.vault.cli.Main`.
- Generar el artefacto mediante Maven Wrapper y verificar que se ejecuta sin
  Maven ni dependencias externas del proyecto.
- Añadir `README.md` en español con requisitos, descarga, instalación/ejecución,
  uso de comandos y ejecución desde el código fuente.
- Documentar el flujo manual para adjuntar el JAR a un GitHub Release `v1.0.0`;
  adjuntar también un checksum SHA-256; el build no publica automáticamente
  releases.
- Verificar en CI que el JAR empaquetado inicia la ayuda sin requerir una
  consola interactiva.

**Fuera:**

- `jpackage`, instaladores nativos o runtime Java incluido. El usuario instala
  Java 21.
- Publicación automática desde tags, firma de artefactos, actualizaciones
  automáticas, repositorio Maven o aplicación gráfica.
- Cambios a comandos, formato de bóveda, criptografía o modelo de amenazas.
- Incluir bóvedas, credenciales, datos de ejemplo sensibles o configuraciones
  personales dentro del JAR o del repositorio.

## 3. Distribución y uso

La primera versión será Java `1.0.0` y se adjuntará manualmente como
`password-vault-1.0.0.jar` al GitHub Release `v1.0.0`, junto con
`password-vault-1.0.0.jar.sha256`, que contendrá el hash SHA-256 hexadecimal del
JAR. El build local producirá un nombre estable, `target/password-vault.jar`,
para que CI y README no dependan de renombrar el archivo de `target/`.

Requisitos del usuario:

- Java 21 o posterior compatible instalado y disponible como `java` en PATH.
- Terminal interactiva real para comandos operativos; `--help` no requiere una.
- El JAR descargado desde la sección Releases del repositorio. Alternativamente,
  el usuario puede clonar el código y utilizar Maven Wrapper.

Invocación:

```text
java -jar password-vault-1.0.0.jar --help
java -jar password-vault-1.0.0.jar create --vault <ruta>
java -jar password-vault-1.0.0.jar session --vault <ruta>
```

Los secretos seguirán solicitándose por la consola interactiva, nunca como
argumentos, variables de entorno o stdin. El README advertirá que no se debe
compartir el archivo de bóveda ni incluirlo en issues, capturas o repositorios.

## 4. Requisitos

### R1 — Artefacto ejecutable y autocontenido

- Maven genera `target/password-vault.jar` con `Main-Class` igual a
  `cl.germain.vault.cli.Main`.
- El JAR incluye las dependencias de runtime necesarias, incluida la versión
  aprobada de Bouncy Castle, sin depender de `target/dependency`, Maven ni un
  classpath adicional.
- Dependencias de test, en particular JUnit, no se empaquetan.
- El empaquetado no minimiza, reubica ni modifica bytecode de la aplicación o de
  las dependencias. No debe romper metadatos de proveedor requeridos por el JDK.
- Se conservan en el artefacto o en avisos incluidos con la distribución las
  licencias y atribuciones requeridas por las dependencias empaquetadas.
- El plugin de empaquetado se declara con versión fija. No se agregan dependencias
  de runtime.

### R2 — Versión y build

- La primera distribución se construye como versión Maven `1.0.0`; el archivo
  local tiene el nombre estable `password-vault.jar`.
- `./mvnw.cmd verify` y `./mvnw verify` producen el artefacto como parte del
  lifecycle normal y comprueban formato y pruebas.
- El build funciona con Java 21 en Windows y Linux sin pasos manuales de
  empaquetado posteriores a Maven.
- CI prueba el artefacto empaquetado ejecutando la ayuda con `java -jar`; no
  intenta ejecutar comandos operativos sin terminal.

### R3 — Guía de usuario

- `README.md` explica propósito y alcance del gestor, requisitos de Java 21,
  descarga desde GitHub Releases, ejecución del JAR, ejemplos de comandos y
  ejecución desde fuente con Maven Wrapper; identifica también las dependencias
  de terceros distribuidas y sus licencias.
- README explica cómo verificar el checksum SHA-256 publicado junto al JAR con
  herramientas disponibles en Windows y POSIX.
- Explica que los comandos operativos requieren terminal interactiva y que
  contraseñas/secretos se solicitan sin eco; no deben pasarse por argumentos,
  variables de entorno ni stdin.
- Indica ubicación explícita de la bóveda, el comportamiento del autobloqueo y
  que el JAR no incluye ni crea automáticamente una bóveda preconfigurada.
- Todos los ejemplos usan rutas y datos ficticios; no incluyen secretos reales
  ni archivos de bóveda.
- Las instrucciones describen publicación manual: crear el Release/tag `v1.0.0`
  y adjuntar el artefacto compilado y su checksum SHA-256. No se configura
  publicación automática.

### R4 — Compatibilidad y límites

- El JAR es portable entre sistemas compatibles con Java 21; no se afirma que sea
  ejecutable directamente por el sistema operativo ni que incluya JVM.
- La ausencia de `System.console()` sigue rechazando comandos operativos; no se
  añade fallback a stdin por distribuir un JAR.
- No cambian formato, dependencias de runtime, comandos, protocolos ni
  `docs/THREAT_MODEL.md`.

## 5. Criterios de aceptación

| ID | Criterio |
|---|---|
| AC-1 | El proyecto se identifica como `1.0.0` y Maven genera `target/password-vault.jar` con manifest `Main-Class: cl.germain.vault.cli.Main`. |
| AC-2 | El JAR incluye dependencias de runtime requeridas y excluye dependencias de test; conserva los avisos de licencia necesarios y `java -jar target/password-vault.jar --help` funciona sin classpath/Maven. |
| AC-3 | `./mvnw.cmd verify` y CI Linux construyen y ejecutan la ayuda desde el artefacto empaquetado en Java 21. |
| AC-4 | Las instrucciones de README permiten a una persona con Java 21 descargar el JAR de Releases y arrancar la CLI desde una terminal. |
| AC-5 | README explica requisitos, comandos, terminal interactiva, solicitud segura de secretos, rutas de bóveda, autobloqueo y ejecución desde fuente. |
| AC-6 | Los ejemplos/documentación no contienen secretos ni bóvedas reales; no se añade fallback de entrada inseguro, dependencia runtime ni cambio a formato/Threat Model. |
| AC-7 | Se documenta el flujo manual para el Release `v1.0.0`, adjuntando JAR y checksum SHA-256; no hay automatización de publicación ni se afirma una distribución nativa/runtime incluido. |

## 6. Pruebas requeridas

- Verificar manifest y contenido del artefacto mediante herramientas JDK/Maven;
  confirmar inclusión de Bouncy Castle y ausencia de JUnit.
- Calcular SHA-256 del artefacto y comprobar que las instrucciones de verificación
  en README funcionan en Windows/POSIX.
- Ejecutar `java -jar target/password-vault.jar --help` en Windows y CI Linux,
  comprobar estado exitoso y comandos en la ayuda.
- Asegurar que no se necesita classpath adicional, directorio de dependencias ni
  conexión de red al arrancar el JAR una vez generado.
- Ejecutar `./mvnw.cmd verify` y `bash ./mvnw verify` en CI.
- Revisar README para coherencia con parser/ayuda y ausencia de datos sensibles.
- No probar comandos operativos mediante pipes ni automatizar entradas de
  contraseña para esta spec; la política de consola interactiva no cambia.

## 7. Documentación y cierre

`README.md` será la guía primaria para usuarios. `AGENTS.md` conservará los
comandos de desarrollo y podrá enlazar el README. `docs/STATUS.md` registrará la
versión, artefacto, build y plataformas realmente verificadas. No se cambia
`docs/FORMAT.md` ni `docs/THREAT_MODEL.md`.

La Spec 007 y su implementación fueron aprobadas el 2026-11-15. La implementación
está en curso; el usuario aprobó `org.apache.maven.plugins:maven-shade-plugin`
versión `3.6.2`. El cierre exigirá evidencia para AC-1–AC-7, JAR ejecutable
validado localmente y en CI Linux, README actualizado y `./mvnw.cmd verify` en
verde.
