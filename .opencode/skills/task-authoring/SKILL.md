---
name: Task Authoring
description: Descomponer una spec aprobada y su plan técnico en tareas pequeñas, ordenadas, verificables y trazables a los criterios de aceptación.
---

# Skill: redactar listas de tareas

Usa esta skill para crear o revisar `tasks.md` de una spec. Produce únicamente
la lista de tareas; no modifiques la spec ni el plan salvo petición expresa.

## Fuentes y condiciones

1. Lee `AGENTS.md`, `docs/constitution.md`, la spec aprobada, su `plan.md` y el
   `tasks.md` de la spec anterior para mantener estilo y convenciones.
2. Comprueba que la spec está aprobada y que existe un plan adecuado. Si faltan,
   pregunta antes de convertir supuestos en tareas de implementación.
3. Inspecciona el estado actual del repo para no planificar como nuevo algo ya
   implementado ni sobrescribir trabajo existente.

## Descomposición

- Ordena tareas por dependencias: preparación, modelo/API, implementación,
  integración, pruebas, documentación y cierre, según el alcance.
- Mantén cada tarea en un bloque de 20–30 minutos como máximo. Divide tareas
  mayores en entregables independientes.
- Usa checklist y numeración estable (`T01`, `T02`, ...).
- Para cada tarea indica duración estimada, requisitos funcionales (`RF`),
  criterios de aceptación (`AC`) y una condición objetiva de “Hecho cuando”.
- Las pruebas deben ser tareas explícitas, no una nota genérica al final. Cubre
  todos los AC y relaciona cada prueba con el comportamiento esperado.
- Incluye tareas para documentación requerida (`docs/FORMAT.md`, `docs/STATUS.md`)
  y verificación final cuando corresponda.
- Deja claro qué queda fuera de alcance y no conviertas tareas futuras en
  requisitos implícitos.
- Para permisos, symlinks, atomicidad u otras capacidades dependientes de
  plataforma, exige registrar plataforma y limitaciones observadas; nunca marcar
  como probado lo que no se ejecutó.
- No incluyas secretos reales ni fixtures sensibles.

## Verificación y cierre

Una tarea no se marca completa sin evidencia del criterio. Según `AGENTS.md`,
al finalizar el trabajo ejecuta `./mvnw.cmd verify` en Windows (o el wrapper
equivalente en la plataforma activa), e informa el resultado. No marques la
spec cerrada hasta que se cumplan sus criterios de cierre.

Al terminar, resume el archivo creado/actualizado, cobertura de AC, verificación
y cualquier bloqueo o pendiente. Esta skill organiza el trabajo; no implementa
las tareas.
