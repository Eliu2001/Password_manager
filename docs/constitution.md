# Constitución del proyecto

1. **Spec primero:** no se escribe código sin una spec activa bajo `docs/specs/<nombre-de-la-spec>/spec.md`. Si aparece algo que la spec no cubre, se detiene el trabajo, se actualiza la spec y solo después se programa. `docs/THREAT_MODEL.md` es la fuente de verdad de las decisiones de seguridad: si el código o una spec lo contradice, se corrige primero el modelo, con aprobación del autor, y después el código.
2. **Stack:** usar Java 21 y Maven, con paquetes bajo `cl.germain.vault`.
3. **Criptografía:** usar AES-256-GCM y Argon2id, con aleatoriedad de `SecureRandom` y sin reutilizar nonces. No se implementan algoritmos propios: solo primitivas del JDK o de librerías aprobadas.
4. **Falla cerrada:** ante cualquier fallo de validación o descifrado, rechazar la operación y nunca devolver datos parciales.
5. **Secretos:** manejar la contraseña maestra como `char[]`, borrarla tras usarla y no exponer datos sensibles en logs, errores ni pruebas.
6. **Arquitectura e idioma:** mantener `crypto` independiente del resto del código. Nombres, código y comentarios en inglés; mensajes al usuario en español.
7. **Pruebas:** usar JUnit 5. Cada decisión nueva de seguridad debe tener una prueba, y toda tarea termina con `mvn verify` en verde.
8. **Alcance:** no se inicia una spec hasta que la anterior esté cerrada con sus pruebas pasando. No agregar dependencias ni decidir aspectos de seguridad no especificados sin aprobación. Todo cambio de formato del archivo exige actualizar `docs/FORMAT.md` y subir su número de versión.
