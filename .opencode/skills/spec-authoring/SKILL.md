---
name: Spec Authoring
description: Aclarar requisitos y redactar o revisar una spec de este proyecto antes de planificar o implementar.
---

# Skill: redactar y revisar specs

Usa este flujo cuando el usuario quiera definir, revisar, ampliar o aprobar una
spec del gestor de bóvedas. El resultado de esta skill es únicamente una spec;
no redactes `plan.md` ni `tasks.md` salvo que el usuario también lo pida.

## Fuentes y precedencia

1. Lee `AGENTS.md`, `docs/constitution.md`, la spec relevante y los documentos
   relacionados que existan (`docs/STATUS.md`, specs predecesoras y
   `docs/FORMAT.md`).
2. Sigue las reglas de seguridad y arquitectura del proyecto. No contradigas
   `docs/THREAT_MODEL.md`; no lo modifiques sin petición expresa.
3. La spec activa debe ser aprobada antes de implementar. No interpretes una
   petición de redactar/revisar como aprobación salvo que el usuario lo diga
   claramente.

## Proceso

1. Identifica objetivo, alcance, exclusiones, dependencias, requisitos
   verificables, criterios de aceptación y condiciones de cierre.
2. Separa hechos ya acordados de decisiones pendientes. Para toda ambigüedad
   criptográfica, de secretos, formato, permisos o concurrencia, pregunta al
   usuario; no inventes una decisión.
3. Pregunta en grupos pequeños y concretos. Explica brevemente el impacto de
   cada alternativa y registra las respuestas aprobadas.
4. Conserva el alcance solicitado. Mantén las reglas de negocio, CLI y funciones
   futuras fuera de la spec si el usuario no las incluyó.
5. Redacta en español siguiendo el estilo de las specs existentes. Usa nombres
   de código en inglés y requisitos numerados (`R1`, `R2`, ...), con criterios
   (`AC-1`, `AC-2`, ...) comprobables.
6. Incluye explícitamente manejo de secretos, fallos seguros, límites,
   dependencias permitidas, pruebas, documentación afectada y criterios de
   aprobación/cierre cuando correspondan.
7. Si hay cambios de formato, exige actualizar `docs/FORMAT.md` y conservar la
   política de versionado. No cambies el Threat Model sin autorización.
8. Antes de guardar, presenta las decisiones y preguntas pendientes si aún se
   necesita aprobación del usuario. No marques la spec aprobada hasta recibir
   confirmación explícita.
9. Al terminar, resume cambios, estado de aprobación, preguntas pendientes y
   documentos que todavía no se han creado. No implementes código.

## Restricciones

- No agregues dependencias sin aprobación.
- No escribas datos sensibles en ejemplos, pruebas ni archivos versionados.
- No asumas que una prueba de aleatoriedad demuestra una garantía absoluta.
- Si una decisión necesaria no está especificada, detente y pregunta.
