# Gestor de contraseñas — Password Vault

Password Vault es un gestor local de credenciales cifradas. La bóveda se guarda
en un archivo local y la CLI solicita contraseñas y otros secretos mediante una
terminal interactiva. La última versión publicada es la **1.2.0**, que incluye
el asistente y menú guiados. La versión **1.2.1** está preparada localmente, pero
su Release todavía no está publicado.

## Requisitos

- Java 21 o posterior compatible, instalado y disponible como `java` en PATH.
- Una terminal interactiva real para los comandos que operan sobre la bóveda.
  `--help` puede ejecutarse sin terminal.

El JAR no incluye una JVM ni es un ejecutable nativo. El asistente de consola
pregunta explícitamente dónde crear o abrir una bóveda.

## Descargar y ejecutar

Descarga del [GitHub Release `v1.2.0`](https://github.com/Eliu2001/Password_manager/releases/tag/v1.2.0)
los tres archivos: `password-vault-1.2.0.jar`, `vault.cmd` y
`password-vault-1.2.0.jar.sha256`. Guarda los tres en la misma carpeta; el
checksum contiene el SHA-256 hexadecimal del JAR.

En Windows PowerShell, verifica el checksum con el mismo directorio de descarga:

```powershell
$expected = (Get-Content .\password-vault-1.2.0.jar.sha256 -Raw).Trim().ToLowerInvariant()
$actual = (Get-FileHash .\password-vault-1.2.0.jar -Algorithm SHA256).Hash.ToLowerInvariant()
if ($expected -ne $actual) { throw 'El checksum no coincide' }
```

En Linux y otros sistemas POSIX:

```sh
expected=$(cat password-vault-1.2.0.jar.sha256)
actual=$(sha256sum password-vault-1.2.0.jar | cut -d ' ' -f 1)
test "$expected" = "$actual" && echo "Checksum correcto" || echo "Checksum incorrecto"
```

El checksum permite comprobar que el JAR coincide con el valor publicado; no es
una firma digital ni autentica por sí solo quién publicó ambos archivos.

Muestra la ayuda:

```text
java -jar password-vault-1.2.0.jar --help
```

### Lanzador sencillo para Windows

El `vault.cmd` adjunto al Release `v1.2.0` inicia el asistente guiado. En Windows,
ábrelo desde una terminal interactiva. Necesitas Java 21 o posterior disponible
como `java` en `PATH`; no combines el launcher ni el JAR con archivos de otra
versión.

También puedes iniciar el asistente directamente desde el JAR:

```powershell
java -jar .\password-vault-1.2.0.jar start
```

En Linux/macOS:

```sh
java -jar ./password-vault-1.2.0.jar start
```

Usa una terminal interactiva real; no pipes ni redirecciones. El flujo guiado
recuerda solo la ruta en `%LOCALAPPDATA%\PasswordVault\vault-path.txt`.

#### Primera configuración

1. Ejecuta `vault.cmd` o inicia el JAR con el comando `start` anterior.
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
   Los números son temporales y solo valen para esa lista. Si escribes una opción
   o índice inválido, puedes intentarlo de nuevo; escribe `0` para volver/cancelar
   una selección. En el asistente, `0` en la ruta vuelve a Crear/Abrir/Salir.
2. Cuando pregunte **«¿Revelar la contraseña?»**, escribe `sí` o `si` para
   mostrarla en la consola. Se presenta bajo el rótulo **Contraseña:**. Cualquier
   otra respuesta no la revela.

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
java -jar password-vault-1.2.0.jar create --vault ./example.pvlt
```

La CLI también ofrece:

```text
java -jar password-vault-1.2.0.jar add --vault ./example.pvlt
java -jar password-vault-1.2.0.jar list --vault ./example.pvlt
java -jar password-vault-1.2.0.jar show --vault ./example.pvlt --id <id>
java -jar password-vault-1.2.0.jar delete --vault ./example.pvlt --id <id>
java -jar password-vault-1.2.0.jar set-totp --vault ./example.pvlt --id <id>
java -jar password-vault-1.2.0.jar remove-totp --vault ./example.pvlt --id <id>
java -jar password-vault-1.2.0.jar totp --vault ./example.pvlt --id <id>
java -jar password-vault-1.2.0.jar upgrade --vault ./example.pvlt
java -jar password-vault-1.2.0.jar session --vault ./example.pvlt --timeout-minutes 5
java -jar password-vault-1.2.0.jar generate --length 20
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

## Estado de distribución

- **Publicado:** `v1.2.0` es la última versión descargable e incluye el flujo
  guiado. Descarga sus assets desde el [GitHub Release](https://github.com/Eliu2001/Password_manager/releases/tag/v1.2.0).
- **Histórico:** `v1.1.0` sigue disponible, pero contiene la interfaz CLI anterior
  y no incluye el asistente ni el menú guiados.
- **Candidato preparado:** `v1.2.1` incorpora las mejoras recientes de navegación
  y presentación de contraseñas. Sus assets están preparados localmente, pero
  aún no hay un Release descargable; usa `v1.2.0` hasta que se publique.

El checksum verifica coincidencia de bytes, no es una firma digital. No se incluyen
instalador nativo ni runtime Java.
