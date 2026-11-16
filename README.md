# Gestor de contraseñas — Password Vault

Password Vault es un gestor local de credenciales cifradas. La bóveda se guarda
en un archivo local y la CLI solicita contraseñas y otros secretos mediante una
terminal interactiva. La última versión publicada es la **1.2.1**, que incluye
las mejoras de navegación guiada y visualización etiquetada de contraseñas. El
candidato **1.2.2** corrige el uso del asistente guiado en Linux y está en
preparación local; todavía no está publicado.

## Requisitos

- Java 21 o posterior compatible, instalado y disponible como `java` en PATH.
- Una terminal interactiva real para los comandos que operan sobre la bóveda.
  `--help` puede ejecutarse sin terminal.

El JAR no incluye una JVM ni es un ejecutable nativo. El asistente de consola
pregunta explícitamente dónde crear o abrir una bóveda.

En Linux, instala Java 21 o posterior. Por ejemplo, en Ubuntu/Debian puedes usar
`sudo apt install openjdk-21-jre`; para compilar desde el código fuente se requiere
el JDK (`sudo apt install openjdk-21-jdk`). En Fedora, los paquetes equivalentes
son `java-21-openjdk` y `java-21-openjdk-devel`. Comprueba la versión con
`java -version` (y `javac -version` si vas a compilar).

## Descargar y ejecutar

Descarga del [GitHub Release `v1.2.1`](https://github.com/Eliu2001/Password_manager/releases/tag/v1.2.1)
los tres archivos: `password-vault-1.2.1.jar`, `vault.cmd` y
`password-vault-1.2.1.jar.sha256`. Guarda los tres en la misma carpeta; el
checksum contiene el SHA-256 hexadecimal del JAR.

En Windows PowerShell, verifica el checksum con el mismo directorio de descarga:

```powershell
$expected = (Get-Content .\password-vault-1.2.1.jar.sha256 -Raw).Trim().ToLowerInvariant()
$actual = (Get-FileHash .\password-vault-1.2.1.jar -Algorithm SHA256).Hash.ToLowerInvariant()
if ($expected -ne $actual) { throw 'El checksum no coincide' }
```

En Linux y otros sistemas POSIX:

```sh
expected=$(cat password-vault-1.2.1.jar.sha256)
actual=$(sha256sum password-vault-1.2.1.jar | cut -d ' ' -f 1)
test "$expected" = "$actual" && echo "Checksum correcto" || echo "Checksum incorrecto"
```

El checksum permite comprobar que el JAR coincide con el valor publicado; no es
una firma digital ni autentica por sí solo quién publicó ambos archivos.

**Nota para Linux:** el JAR publicado `v1.2.1` todavía no tiene la corrección del
asistente `start` en Linux. El candidato `1.2.2` sí la incluye, pero por ahora está
preparado localmente y aún no publicado en GitHub. Mientras tanto, compila el
código actual como se explica en [Ejecución desde el código fuente](#ejecución-desde-el-código-fuente)
o usa los comandos directos con `--vault`.

Muestra la ayuda:

```text
java -jar password-vault-1.2.1.jar --help
```

### Lanzador sencillo para Windows

El `vault.cmd` adjunto al Release `v1.2.1` inicia el asistente guiado. En Windows,
ábrelo desde una terminal interactiva. Necesitas Java 21 o posterior disponible
como `java` en `PATH`; no combines el launcher ni el JAR con archivos de otra
versión.

También puedes iniciar el asistente directamente desde el JAR:

```powershell
java -jar .\password-vault-1.2.1.jar start
```

En Linux, para usar el asistente con el código corregido antes de que se publique
el JAR `1.2.2`, clona el repositorio y compílalo (Java 21 JDK requerido):

```sh
git clone https://github.com/Eliu2001/Password_manager.git
cd Password_manager
./mvnw verify
java -jar target/password-vault.jar start
```

Usa una terminal interactiva real; no pipes ni redirecciones. En Linux el comando
`start` está soportado por el código actual y por el candidato `1.2.2`, pero no por
el JAR `v1.2.1` publicado. El asistente `start` no está documentado como compatible
con macOS. Cuando `v1.2.2` esté publicado, el comando será
`java -jar password-vault-1.2.2.jar start`.

El asistente recuerda solo la ruta de la bóveda, nunca contraseñas ni secretos:

- En Windows: `%LOCALAPPDATA%\PasswordVault\vault-path.txt`.
- En Linux: `$XDG_CONFIG_HOME/password-vault/vault-path.txt` si
  `XDG_CONFIG_HOME` es una ruta absoluta; si no, `$HOME/.config/password-vault/vault-path.txt`.

Linux crea automáticamente el directorio de configuración de la aplicación y
protege el directorio y el archivo con permisos `0700` y `0600`. No tienes que
crear manualmente archivos o carpetas de configuración.

#### Primera configuración

1. En Windows, ejecuta `vault.cmd` o inicia el JAR con `start`. En Linux, usa el
   JAR `1.2.2` cuando esté publicado o compila el código actual como se indica
   arriba.
2. Elige **Crear bóveda nueva** o **Abrir bóveda existente** y escribe la ruta
   absoluta completa del archivo `.pvlt`, no solo una carpeta. Para crearla, la
   carpeta padre debe existir y permitir escritura; el archivo destino no debe
   existir. El programa crea el archivo de bóveda, pero no crea sus carpetas padre.
   En el prompt escribe la ruta real, por ejemplo `/home/ana/vault.pvlt`; no
   escribas `~/vault.pvlt` ni `$HOME/vault.pvlt`, porque el prompt no expande esos
   atajos del shell.
3. Introduce la contraseña maestra cuando se solicite. Al crear, confírmala; no
   se muestra en pantalla ni se guarda en la configuración.
4. El asistente recuerda únicamente la ruta, en la ubicación de configuración
   correspondiente a tu sistema descrita arriba. Para elegir otra bóveda, sal de
   la sesión y elimina únicamente el archivo `vault-path.txt` de configuración;
   esto no elimina ni modifica el archivo `.pvlt`. Al volver a ejecutar `start`, el
   asistente volverá a ofrecer Crear/Abrir/Salir.
5. El menú muestra la bóveda seleccionada y su estado desbloqueado, y organiza las
   opciones en **Entradas**, **Autenticación TOTP** y **Herramientas**. En terminales
   estrechas usa una distribución vertical. Los colores discretos se desactivan
   cuando ANSI no está disponible o se define `NO_COLOR`; la versión monocromática
   conserva los rótulos y bordes ASCII. Los avisos y errores llevan texto explícito,
   además del color. Escribe `0` para salir; el autobloqueo predeterminado es de
   cinco minutos.

Para que el asistente vuelva a preguntar qué bóveda usar en Linux, sal primero
del programa y elimina solo el archivo de configuración de ruta (no la bóveda):

```sh
if [ -n "${XDG_CONFIG_HOME:-}" ] && [ "${XDG_CONFIG_HOME#/}" != "$XDG_CONFIG_HOME" ]; then
  config_base="$XDG_CONFIG_HOME"
else
  config_base="$HOME/.config"
fi
rm -f -- "$config_base/password-vault/vault-path.txt"
```

#### Solución de problemas al crear una bóveda

Si aparece **«No se pudo completar la operación o guardar la configuración local»**
después de introducir la contraseña maestra, comprueba la carpeta padre de la ruta
elegida (sin el nombre final `.pvlt`):

```sh
parent="/ruta/de/la/carpeta"
ls -ld -- "$parent"
test -d "$parent" && echo "La carpeta existe" || echo "La carpeta no existe"
test -w "$parent" && echo "Se puede escribir" || echo "No se puede escribir"
```

La carpeta debe existir y ser escribible por tu usuario. Si falta, créala primero
con `mkdir -p -- "$parent"`; no hace falta crear una carpeta llamada `docs` dentro
de la bóveda. Para discos externos, comprueba que estén montados y permitan
escritura. Evita `chmod 777` y no ejecutes el gestor con `sudo` para sortear
permisos.

Si el archivo `.pvlt` sí existe pese al error, puede haber fallado el guardado de
la ruta de configuración. Revisa que la carpeta base de configuración sea
accesible por tu usuario:

```sh
if [ -n "${XDG_CONFIG_HOME:-}" ] && [ "${XDG_CONFIG_HOME#/}" != "$XDG_CONFIG_HOME" ]; then
  config_base="$XDG_CONFIG_HOME"
else
  config_base="$HOME/.config"
fi
ls -ld -- "$config_base" "$config_base/password-vault" 2>/dev/null
test -w "$config_base" && echo "Configuración escribible" || echo "Revisar permisos de configuración"
```

Si el error ocurrió después de elegir **Crear**, verifica si el archivo `.pvlt`
quedó creado antes de volver a intentarlo:

```sh
ls -l -- "/ruta/que-elegiste/vault.pvlt"
```

Si existe, no vuelvas a elegir **Crear** sobre esa ruta: elige **Abrir bóveda
existente** o usa los comandos directos con `--vault`. Si no existe y la carpeta
escribible, comprueba que ejecutas el JAR `1.2.2` o el código actual; el JAR
publicado `1.2.1` no incluye la corrección de `start` para Linux. Nunca compartas
tu contraseña maestra ni el archivo de bóveda al pedir ayuda.

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
java -jar password-vault-1.2.1.jar create --vault ./example.pvlt
```

La CLI también ofrece:

```text
java -jar password-vault-1.2.1.jar add --vault ./example.pvlt
java -jar password-vault-1.2.1.jar list --vault ./example.pvlt
java -jar password-vault-1.2.1.jar show --vault ./example.pvlt --id <id>
java -jar password-vault-1.2.1.jar delete --vault ./example.pvlt --id <id>
java -jar password-vault-1.2.1.jar set-totp --vault ./example.pvlt --id <id>
java -jar password-vault-1.2.1.jar remove-totp --vault ./example.pvlt --id <id>
java -jar password-vault-1.2.1.jar totp --vault ./example.pvlt --id <id>
java -jar password-vault-1.2.1.jar upgrade --vault ./example.pvlt
java -jar password-vault-1.2.1.jar session --vault ./example.pvlt --timeout-minutes 5
java -jar password-vault-1.2.1.jar generate --length 20
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

- **Publicado:** `v1.2.1` es la última versión descargable. Descarga sus tres
  assets desde el [GitHub Release](https://github.com/Eliu2001/Password_manager/releases/tag/v1.2.1).
- **Candidato local:** `1.2.2` contiene la corrección Linux de `start`; se
  prepararon `password-vault-1.2.2.jar`, `password-vault-1.2.2.jar.sha256` y
  `vault.cmd`, pero aún no hay Release/tag ni descarga pública. La verificación
  automatizada Linux pasó en CI; no se realizó una prueba manual en TTY Linux.
- **Histórico:** `v1.2.0` incluye el flujo guiado inicial; `v1.1.0` contiene la
  interfaz CLI anterior. No combines assets de versiones distintas.

El checksum verifica coincidencia de bytes, no es una firma digital. No se incluyen
instalador nativo ni runtime Java.
