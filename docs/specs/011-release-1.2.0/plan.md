# Plan técnico — Spec 011: Preparación del Release 1.2.0

**Estado:** ejecución autorizada para preparación local el 2026-11-16.
**Predecesora:** Spec 010, cerrada.
**Alcance:** versionar y preparar los tres assets de distribución; sin publicar el Release.

## 1. Cambios

- `pom.xml`: versión `1.1.0` a `1.2.0`; mantener `target/password-vault.jar`.
- `vault.cmd`: apuntar al JAR versionado `password-vault-1.2.0.jar` y conservar
  inicio `start`, validaciones y manejo del estado de salida.
- `README.md`: distinguir claramente el Release publicado `v1.1.0` de la versión
  guiada que se prepara como `v1.2.0`; explicar que los assets no son intercambiables.
- `docs/STATUS.md`: registrar resultado local, staging y pendiente remoto.
- `docs/specs/011-release-1.2.0/`: mantener trazabilidad de spec, plan y tareas.
- Staging externo: crear una carpeta nueva en Downloads, copiar el JAR renombrado
  y `vault.cmd`, calcular checksum SHA-256 después de copiar y verificarlo.

## 2. Secuencia

1. Verificar estado Git y confirmar que la carpeta staging objetivo no existe.
2. Actualizar POM, launcher y documentación a `1.2.0` sin borrar la referencia
   histórica a `v1.1.0`.
3. Ejecutar `./mvnw.cmd verify` y comprobar `java -jar target/password-vault.jar --help`.
4. Crear `Downloads/password-vault-v1.2.0-release`; copiar JAR renombrado y
   launcher; crear SHA-256 de la copia final.
5. Verificar exactamente tres assets, hash y ayuda del JAR staged.
6. Registrar resultados y pendientes. No crear tag/Release ni subir assets. El
   push del commit de código/documentación puede realizarse si el usuario lo
   autoriza separadamente.

## 3. Verificación

| AC | Evidencia |
|---|---|
| AC-1 | Inspección del POM/launcher y ejecución de tests para `start`. |
| AC-2 | Revisión de README contra el Release publicado `v1.1.0`. |
| AC-3 | Inventario de archivos en staging y comprobación de que no hay extras. |
| AC-4 | Ayuda ejecutada desde el JAR staged y checksum recalculado. |
| AC-5 | Salida final de Maven y `git diff --check`. |
| AC-6 | Lectura de `docs/STATUS.md`; comprobar que no hubo operaciones remotas. |

El usuario reportó que la interfaz guiada y su presentación visual funcionaron
en una terminal Windows real. Esta evidencia es atribuida al usuario y no se
presenta como ejecución manual del agente.

## 4. Riesgos y límites

- No reemplazar `v1.1.0`: tag y assets ya publicados siguen siendo la distribución
  anterior.
- No sobrescribir una carpeta staging preexistente; elegir otra ruta si aparece.
- Hash calculado sobre el JAR staged, no sobre el original previo al copiado.
- Esta tarea no prueba la página de Release una vez publicada.
- La publicación remota y la creación del tag siguen a cargo del usuario.
