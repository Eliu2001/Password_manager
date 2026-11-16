# Gestor de contraseñas — Password Vault

Password Vault es un gestor local de credenciales cifradas. La bóveda se guarda
en un archivo local y la CLI solicita contraseñas y otros secretos mediante una
terminal interactiva. La versión actual de distribución es la **1.1.0**.

## Requisitos

- Java 21 o posterior compatible, instalado y disponible como `java` en PATH.
- Una terminal interactiva real para los comandos que operan sobre la bóveda.
  `--help` puede ejecutarse sin terminal.

El JAR no incluye una JVM, no es un ejecutable nativo y no crea una bóveda
preconfigurada. Debes indicar explícitamente dónde guardar la bóveda.

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

El Release `v1.1.0` incluye [`vault.cmd`](vault.cmd). Descarga los tres assets en
una carpeta de aplicación y mantenlos juntos: `password-vault-1.1.0.jar`,
`vault.cmd` y `password-vault-1.1.0.jar.sha256`. Necesitas Java 21 o posterior
disponible como `java` en `PATH`.

#### Primera configuración

1. Decide dónde guardar la bóveda. La ruta debe señalar **el archivo de bóveda**
   (por ejemplo, `E:\PasswordVault\mi-boveda.pvlt`), no solo la carpeta
   `E:\PasswordVault`.
2. Si la carpeta aún no existe, créala en PowerShell:

   ```powershell
   New-Item -ItemType Directory -Force 'E:\PasswordVault' | Out-Null
   ```

3. Si ya tienes un archivo `.pvlt`, conserva una copia y usa su ruta exacta en el
   paso siguiente. **No ejecutes `create` sobre una bóveda existente.** Si aún no
   tienes una, abre PowerShell en la carpeta donde están los archivos y créala
   una sola vez:

   ```powershell
   java -jar .\password-vault-1.1.0.jar create --vault 'E:\PasswordVault\mi-boveda.pvlt'
   ```

   La CLI pedirá la contraseña maestra y su confirmación sin mostrarlas en
   pantalla. No escribas la contraseña como parte del comando.
4. Haz clic derecho en `vault.cmd` y elige **Editar**. Cambia el marcador de
   `VAULT_PATH` por la ruta completa del archivo, entre comillas:

   ```bat
   set "VAULT_PATH=E:\PasswordVault\mi-boveda.pvlt"
   ```

   Guarda el archivo. No pongas aquí la contraseña maestra.
5. Haz doble clic en `vault.cmd` e introduce la contraseña maestra que usaste al
   crear esa bóveda. El lanzador abre `session` directamente y aplica el
   autobloqueo predeterminado de cinco minutos. Escribe `exit` para cerrar la
   sesión; pulsa una tecla para cerrar la ventana cuando termine.

Si aparece **«No se pudo abrir la bóveda»**, comprueba que `VAULT_PATH` apunta al
archivo `.pvlt` correcto (no a una carpeta) y vuelve a introducir la contraseña
maestra elegida al crearlo. No borres ni vuelvas a crear encima de un archivo
existente.

#### Ver una contraseña guardada

Dentro de la sesión del lanzador:

1. Escribe `list` para mostrar los IDs, servicios y usuarios guardados.
2. Escribe `show --id <id>` usando el ID de la entrada que necesitas.
3. Cuando pregunte **«¿Revelar la contraseña?»**, escribe `sí` para mostrarla en
   la consola. Cualquier otra respuesta no la revela.

La contraseña se muestra en pantalla: evita capturas y no la compartas. También
puedes escribir `help` para consultar los comandos disponibles. No ejecutes estos
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
