---
name: Plan Authoring
description: Crear o revisar el plan técnico de una spec aprobada, identificando diseño, pruebas, riesgos y dependencias sin implementar.
---

# Skill: redactar planes técnicos

Usa esta skill cuando el usuario quiera `plan.md` para una spec. Esta skill
produce únicamente el plan técnico; no edites la spec ni generes `tasks.md`
salvo que el usuario lo solicite expresamente.

## Fuentes y condiciones

1. Lee `AGENTS.md`, `docs/constitution.md`, la spec objetivo, las specs
   predecesoras y planes existentes relacionados.
2. Comprueba que la spec está aprobada. Si sigue en propuesta o borrador, no
   presentes el plan como autorizado para implementación; señala el bloqueo y
   pregunta si el usuario quiere un borrador de planificación.
3. Inspecciona el código existente y sus APIs antes de proponer componentes o
   asumir que una capacidad ya existe.
4. No modifiques `docs/THREAT_MODEL.md` sin petición expresa.

## Contenido del plan

Redacta el plan en español y adapta la estructura a las convenciones del repo.
Incluye cuando aplique:

- contexto, alcance y decisiones aprobadas;
- arquitectura y responsabilidades por paquete/componente;
- APIs conceptuales y flujo de datos;
- integración con implementaciones existentes;
- validación, errores y limpieza de secretos;
- persistencia, formato, permisos, concurrencia y fallos de filesystem;
- estrategia de pruebas ligada a cada AC;
- secuencia de trabajo y dependencias;
- riesgos, limitaciones de plataforma y preguntas abiertas.

## Reglas de diseño

- Mantén `crypto` independiente y no agregues dependencias sin autorización.
- No resuelvas por cuenta propia ambigüedades de seguridad: pregunta y refleja
  solo decisiones aprobadas.
- Distingue garantías de limitaciones (por ejemplo, limpieza de memoria Java,
  atomicidad del filesystem o aleatoriedad probabilística).
- No propongas reducir requisitos de seguridad para facilitar pruebas; usa
  inyección de dependencias o puntos de fallo de prueba sin debilitar producción.
- Traza requisitos y AC desde la spec; no inventes alcance funcional nuevo.
- Si el plan revela que la spec no cubre una decisión necesaria, pausa y pide
  actualizar/aprobar primero la spec.

## Cierre

Antes de editar, explica brevemente decisiones nuevas o preguntas pendientes.
Al terminar, resume el archivo creado/actualizado, estado de la spec, riesgos y
pendientes. No implementes código como parte del trabajo de planificación.
