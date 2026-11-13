# Formato de archivo de bóveda

## Versión 1

El archivo v1 consta de un encabezado binario fijo de 50 bytes seguido
inmediatamente por el ciphertext AES-256-GCM, que incluye su etiqueta de 128
bits. Todos los enteros multibyte son big-endian. El tamaño máximo del archivo
completo es 16 MiB.

### Encabezado

| Offset | Campo | Tamaño | Codificación |
|---:|---|---:|---|
| 0 | Magic `PVLT` | 4 | ASCII |
| 4 | Versión (`1`) | 2 | uint16 big-endian |
| 6 | Memoria Argon2id (KiB) | 4 | uint32 big-endian |
| 10 | Iteraciones Argon2id | 4 | uint32 big-endian |
| 14 | Paralelismo Argon2id | 4 | uint32 big-endian |
| 18 | Sal | 16 | bytes |
| 34 | Nonce AES-GCM | 12 | bytes |
| 46 | Longitud de ciphertext incluida la etiqueta | 4 | uint32 big-endian |

La versión fija Argon2id v19, AES-256-GCM, clave de 256 bits y etiqueta GCM de
128 bits. La sal mide 16 bytes y el nonce 12 bytes. Los parámetros Argon2id se
validan antes de ejecutar la KDF: memoria 19 456–262 144 KiB, iteraciones 2–10
y paralelismo 1–4. Los valores nominales para creación son 65 536 KiB, 3
iteraciones y paralelismo 1.

### AAD e integridad del encabezado

El AAD de AES-GCM son exactamente los **primeros 34 bytes** del encabezado
(offsets 0–33): magic, versión, parámetros Argon2id y sal. Storage conserva esos
bytes originales y no los reconstruye a partir de los campos parseados.

El nonce de offsets 34–45 se genera internamente por `CryptoService` al cifrar;
no lo proporciona storage ni el llamador. Aunque no forma parte del AAD, GCM
usa el nonce para la operación autenticada, por lo que cambiarlo hace fallar la
verificación de la etiqueta. La longitud de offsets 46–49 se valida antes de
descifrar: debe ser al menos 16 bytes y coincidir exactamente con los bytes que
siguen al encabezado y con el final del archivo. Cualquier alteración del
encabezado se rechaza por autenticación GCM o validación estructural.

### Payload cifrado

El payload completo se cifra como un único bloque. Su estructura es:

1. Cantidad de entradas: uint32 big-endian.
2. Para cada entrada, en el orden conservado:
   1. ID: 16 bytes aleatorios.
   2. `createdAt`: int64 con signo big-endian, milisegundos Unix UTC.
   3. `updatedAt`: int64 con signo big-endian, milisegundos Unix UTC.
   4. Sitio/servicio: longitud uint32 big-endian y bytes UTF-8.
   5. Usuario: longitud uint32 big-endian y bytes UTF-8.
   6. Contraseña: longitud uint32 big-endian y bytes UTF-8.
   7. Notas: longitud uint32 big-endian y bytes UTF-8.

Las longitudes de campos cuentan bytes. Sitio/servicio, usuario y contraseña
son obligatorios y no vacíos. Notas ausentes o vacías se codifican con longitud
cero. Los campos no se recortan ni normalizan; el UTF-8 se codifica/decodifica
estrictamente. La contraseña de una entrada se maneja como `char[]` y no aplica
NFC. Las fechas en memoria se truncan a milisegundos para coincidir con la
precisión almacenada.

El lector rechaza payload incompleto, datos sobrantes, longitudes inválidas,
overflow, contadores imposibles, IDs repetidos y UTF-8 malformado. No devuelve
entradas parciales.

### Límites y rechazo

- Tamaño completo máximo: 16 777 216 bytes, incluido encabezado.
- Ciphertext+tag: mínimo 16 bytes; debe terminar exactamente al final del
  archivo.
- No hay límites de campo independientes del tamaño total.
- Magic/versión desconocidas, parámetros KDF fuera de rango y estructura
  incoherente se rechazan sin reparar ni modificar el archivo.
- Contraseña maestra incorrecta y fallo de autenticación usan el error
  criptográfico genérico de Spec 001.

La definición normativa de implementación está en
`docs/specs/002-vault-storage/spec.md`.
