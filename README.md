# Gestor de contraseñas — Password Vault

Password Vault es un gestor local de credenciales cifradas. La bóveda se guarda
en un archivo local y la CLI solicita contraseñas y otros secretos mediante una
terminal interactiva. La versión actual de distribución es la **1.1.0**.

## Requisitos

- Java 21 o posterior compatible, instalado y disponible como `java` en PATH.
- Una terminal interactiva real para los comandos que operan sobre la bóveda.
  `--help` puede ejecutarse sin terminal.

El JAR no incluye una JVM ni es un ejecutable nativo. El asistente de consola
pregunta explícitamente dónde crear o abrir una bóveda.

## Descargar y ejecutar

Descarga del [GitHub Release `v1.1.0`](https://github.com/Eliu2001/Password_manager/releases/tag/v1.1.0)
los tres archivos: `password-vault-1.1.0.jar`, `vault.cmd` y
`password-vault-1.1.0.jar.sha256`. El checksum contiene el SHA-256 hexadecimal
del JAR.

En Windows PowerShell, verifica el checksum con el mismo directorio de descarga:

```powershell
$expected = (Get-Content .\password-vault-1.1.0.jar.sha256 -Raw).Trim().ToLowerInvariant()
$actual = (Get-FileHash .\password-vault-1.1.0.jar -Algorithm SHA256).Hash.ToLowerInvariant()
if ($expected -ne $actual) { throw 'El checksum no coincide' }
```

En Linux y otros sistemas POSIX:

```sh
expected=$(cat password-vault-1.1.0.jar.sha256)
actual=$(sha256sum password-vault-1.1.0.jar | cut -d ' ' -f 1)
test "$expected" = "$actual" && echo "Checksum correcto" || echo "Checksum incorrecto"
```

El checksum permite comprobar que el JAR coincide con el valor publicado; no es
una firma digital ni autentica por sí solo quién publicó ambos archivos.

Muestra la ayuda:

```text
java -jar password-vault-1.1.0.jar --help
```

### Lanzador sencillo para Windows

El Release `v1.1.0` incluye [`vault.cmd`](vault.cmd). Para probar el asistente
guiado descrito abajo, usa el `vault.cmd` y el JAR construidos desde esta revisión;
los assets ya publicados de `v1.1.0` no incluyen esta nueva interfaz. Necesitas
Java 21 o posterior disponible como `java` en `PATH`.

#### Primera configuración

1. Ejecuta `vault.cmd` en una terminal interactiva.
2. Elige **Crear bóveda nueva** o **Abrir bóveda existente** y escribe la ruta
   completa del archivo `.pvlt` (no solo una carpeta). Para crearla, el directorio
   padre ya debe existir y el destino no debe existir.
3. Introduce la contraseña maestra cuando se solicite. Al crear, confírmala; no
   se muestra en pantalla ni se guarda en la configuración.
4. El asistente recuerda únicamente la ruta en
   `%LOCALAPPDATA%\PasswordVault\vault-path.txt`. Para cambiar de bóveda, mueve o
   elimina ese archivo de configuración y vuelve a iniciar el asistente.
5. El menú muestra la bóveda seleccionada y su estado desbloqueado, y organiza las
   opciones en **Entradas**, **Autenticación TOTP** y **Herramientas**. En terminales
   estrechas usa una distribución vertical. Los colores discretos se desactivan
   cuando ANSI no está disponible o se define `NO_COLOR`; la versión monocromática
   conserva los rótulos y bordes ASCII. Los avisos y errores llevan texto explícito,
   además del color. Escribe `0` para salir; el autobloqueo predeterminado es de
   cinco minutos.

#### Ver una contraseña guardada

Dentro de la sesión del lanzador:

1. Elige **Ver entrada** o **Buscar servicio** y selecciona el número mostrado.
   Los números son temporales y solo valen para esa lista.
2. Cuando pregunte **«¿Revelar la contraseña?»**, escribe `sí` o `si` para
   mostrarla en la consola. Cualquier otra respuesta no la revela.

Para rotar una contraseña, elige **Actualizar contraseña**, selecciona la entrada
e introduce la contraseña nueva dos veces. Solo se guarda si ambas entradas
coinciden; servicio, usuario y notas se conservan. Los listados muestran la
última actualización aproximada de cada entrada y recomiendan cambiarla al llegar
a seis meses. Es una aproximación basada en la fecha general de actualización,
no una fecha exacta exclusiva de la contraseña.

La contraseña se muestra en pantalla: evita capturas y no la compartas. La opción
**Generar contraseña** la muestra por la consola privada y no la guarda
automáticamente. También puedes elegir **Ayuda** en el menú. No ejecutes estos
comandos mediante pipes ni redirecciones; la CLI necesita una terminal
interactiva real y solicita los secretos directamente en ella.

## Comandos

Indica siempre una ruta explícita para la bóveda. Por ejemplo, para crear una
bóveda ficticia en el directorio actual:

```text
java -jar password-vault-1.1.0.jar create --vault ./example.pvlt
```

La CLI también ofrece:

```text
java -jar password-vault-1.1.0.jar add --vault ./example.pvlt
java -jar password-vault-1.1.0.jar list --vault ./example.pvlt
java -jar password-vault-1.1.0.jar show --vault ./example.pvlt --id <id>
java -jar password-vault-1.1.0.jar delete --vault ./example.pvlt --id <id>
java -jar password-vault-1.1.0.jar set-totp --vault ./example.pvlt --id <id>
java -jar password-vault-1.1.0.jar remove-totp --vault ./example.pvlt --id <id>
java -jar password-vault-1.1.0.jar totp --vault ./example.pvlt --id <id>
java -jar password-vault-1.1.0.jar upgrade --vault ./example.pvlt
java -jar password-vault-1.1.0.jar session --vault ./example.pvlt --timeout-minutes 5
java -jar password-vault-1.1.0.jar generate --length 20
```

Los IDs se obtienen al listar las entradas. La sesión mantiene la bóveda abierta
y se bloquea tras cinco minutos de inactividad por defecto; el timeout puede
ajustarse entre 1 y 60 minutos. `generate` muestra una contraseña generada en la
consola privada y no la guarda en la bóveda.

Los comandos operativos necesitan una terminal interactiva con `System.console()`.
Las contraseñas y secretos se solicitan sin eco: **no los pases como argumentos,
variables de entorno ni por stdin**, y no ejecutes estos comandos mediante pipes.
Si no hay una consola disponible, las operaciones se rechazan. No compartas el
archivo de bóveda ni lo adjuntes a issues, capturas o repositorios. Usa rutas y
datos ficticios en ejemplos.

## Ejecución desde el código fuente

Clona el repositorio y usa Maven Wrapper con Java 21. En Windows:

```powershell
.\mvnw.cmd verify
java -jar .\target\password-vault.jar --help
```

En Linux/macOS:

```sh
./mvnw verify
java -jar target/password-vault.jar --help
```

Para ejecutar directamente desde Maven durante el desarrollo:

```text
./mvnw -q compile exec:java -Dexec.args="--help"
```

En Windows PowerShell usa `.\mvnw.cmd -q compile exec:java '-Dexec.args=--help'`.

## Dependencias de terceros y licencias

La distribución incluye **Bouncy Castle `bcprov-jdk18on` 1.86**, bajo la
licencia de Bouncy Castle. El texto de licencia se conserva dentro del JAR en
`META-INF/LICENSE.md`. JUnit 5 se usa únicamente para pruebas y no se incluye en
el artefacto distribuido.

## Publicación de la versión 1.1.0

La publicación es manual: crea el tag `v1.1.0` y el GitHub Release correspondiente.
Ejecuta `./mvnw.cmd verify` (Windows) o `./mvnw verify` (POSIX), copia
`target/password-vault.jar` como `password-vault-1.1.0.jar` y copia el `vault.cmd`
genérico sin cambiar el marcador `VAULT_PATH`. Adjunta los tres archivos juntos:
el JAR, `vault.cmd` y el checksum `password-vault-1.1.0.jar.sha256`. Calcula el
hash después de copiar/renombrar el JAR. En PowerShell:

```powershell
(Get-FileHash .\password-vault-1.1.0.jar -Algorithm SHA256).Hash.ToLowerInvariant() |
  Set-Content .\password-vault-1.1.0.jar.sha256 -NoNewline
```

En Linux/POSIX:

```sh
sha256sum password-vault-1.1.0.jar | cut -d ' ' -f 1 > password-vault-1.1.0.jar.sha256
```

Antes de publicar, comprueba el checksum con los comandos de descarga descritos
arriba. No hay publicación automática desde tags; tampoco se distribuyen
instaladores nativos ni un runtime Java. El `.cmd` contiene una ruta de ejemplo:
edita `VAULT_PATH` después de descargarlo, no antes de adjuntarlo al Release.
