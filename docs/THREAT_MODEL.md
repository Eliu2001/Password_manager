# Modelo de amenazas — password-vault

## 1. Propósito
Este documento define qué protege password-vault, contra quién y con qué
decisiones técnicas. También declara lo que no protege. Cada decisión de
seguridad del proyecto debe poder rastrearse hasta una amenaza de este archivo.

## 2. Activos
- Contenido de cada entrada: sitio, usuario, contraseña y notas.
- Secreto TOTP decodificado asociado a una entrada.
- Código TOTP temporal generado para una cuenta.
- Contraseña maestra.
- Clave de cifrado derivada de la contraseña maestra.
- Integridad del archivo de bóveda.

## 3. Escenarios dentro del alcance

| Escenario | Alcance | Respuesta |
|---|---|---|
| Alguien obtiene el archivo de la bóveda (equipo robado, respaldo filtrado) | Completo | Todo el contenido va cifrado en un solo bloque con AES-256-GCM |
| Fuerza bruta offline contra la contraseña maestra | Completo | Argon2id con parámetros costosos |
| Modificación del archivo | Completo | La etiqueta de autenticación de GCM rechaza cualquier alteración |
| Alguien mira por encima del hombro | Parcial | Contraseña maestra y secreto Base32 se leen sin eco; el código TOTP solicitado se muestra una vez en la consola interactiva y aún puede ser observado |
| Uso del equipo con la bóveda abierta | Parcial | Autobloqueo y limpieza del portapapeles |
| Dependencia comprometida | Parcial | Versiones fijadas y escaneo de dependencias en CI |

## 4. Fuera del alcance
- **Malware o keylogger en el equipo.** Si el equipo está comprometido,
  ningún gestor local puede protegerse.
- **Análisis de la memoria en ejecución.** Se reduce la exposición, pero no se
  garantiza (ver limitaciones).
- **Reemplazo del archivo por una versión antigua** (ataque de rollback).
  Mejora futura.
- **Recuperación de una contraseña maestra olvidada.** No existe por diseño.

## 5. Decisiones de seguridad

| Decisión | Valor | Amenaza que responde |
|---|---|---|
| Cifrado | AES-256-GCM, etiqueta de 128 bits | Robo del archivo, modificación |
| Nonce | 12 bytes aleatorios, nuevo en cada guardado | Reutilización de nonce con la misma clave |
| KDF | Argon2id: 64 MiB de memoria, 3 iteraciones, paralelismo 1 | Fuerza bruta offline |
| Sal | 16 bytes aleatorios; se renueva si cambia la contraseña maestra | Ataques con tablas precalculadas |
| Clave derivada | 32 bytes | Tamaño de clave de AES-256 |
| Parámetros del KDF | Guardados en la cabecera | Permiten subir el costo sin romper bóvedas existentes |
| Cabecera | Autenticada como dato asociado (AAD) de GCM | Modificar la cabecera, por ejemplo para bajar el costo de Argon2 |
| Verificación de la contraseña | Solo por el éxito del descifrado | Evita guardar un hash de la contraseña maestra |
| Contenido | Un solo bloque cifrado, incluidos los nombres de sitio | Un atacante no sabe en qué servicios hay cuentas |
| Escritura | Archivo temporal y luego renombrar; permisos 600 | Corrupción por caída a mitad de guardado, lectura por otros usuarios |
| Aleatoriedad | Solo `SecureRandom` | Valores predecibles |
| Secreto TOTP almacenado | Bytes decodificados, dentro del payload cifrado v2; 20–64 bytes | Robo del archivo de bóveda |
| Cálculo TOTP | RFC 6238, HMAC-SHA-1, seis dígitos y paso fijo de 30 segundos | Generar códigos temporales para cuentas; no autentica el desbloqueo de la bóveda |
| Canal CLI TOTP | Lectura Base32 sin eco; código solicitado solo por la consola interactiva privada | Evitar exposición accidental en argumentos, stdin, stdout redirigible y errores |

Reglas de implementación asociadas:
- La contraseña maestra se maneja como `char[]` y se borra tras usarla.
- Nada sensible va a logs, mensajes de error ni excepciones.
- No se implementan algoritmos criptográficos propios.

## 6. Información visible sin la contraseña
La cabecera queda en claro: número de versión del formato, parámetros de
Argon2id y sal. Revela que el archivo es una bóveda de este programa. Se
acepta esta exposición: ocultarla agregaría complejidad sin una ganancia real
de seguridad para el alcance del proyecto.

## 7. Limitaciones conocidas
- Un equipo comprometido derrota cualquier gestor local.
- En Java no se puede garantizar el borrado total de secretos en memoria. Borrar
  el `char[]` reduce la exposición, pero el recolector de basura puede dejar copias.
- El JDK puede conservar copias internas de la clave en `Cipher` y `SecretKeySpec`
  que la aplicación no puede borrar explícitamente. La normalización NFC de una
  contraseña que no esté normalizada también puede crear un `String` transitorio;
  se reduce su exposición copiándolo de inmediato a un `char[]` y no conservando
  referencias, pero no se puede garantizar su borrado en memoria.
- No hay recuperación: perder la contraseña maestra implica perder la bóveda.
- No hay protección contra el reemplazo del archivo por una versión antigua.
- TOTP es una función para cuentas guardadas, no un segundo factor para abrir la
  bóveda. La privacidad del código durante su visualización depende de la
  protección de la sesión de consola y del equipo.
- El proveedor JDK puede conservar copias internas del secreto usadas por HMAC;
  la aplicación borra los arreglos bajo su control, pero no promete borrar esas
  copias internas.
- La seguridad depende de una contraseña maestra fuerte: Argon2id encarece el
  ataque, no lo impide.

## 8. Revisión
Este modelo se revisa al terminar cada spec que cambie el formato del archivo,
la criptografía o el manejo de secretos.
