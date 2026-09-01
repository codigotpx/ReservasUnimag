# Feature Specification: Notificar estado de recursos

**Created**: 2026-08-24
**Módulo**: 2 — Operación de Reservas y Priorización Académica
**Caso de uso (diagrama)**: `Notificar estado de recursos` (paso que ocurre siempre dentro de consultar, reservar y cancelar)
**Prioridad global**: P3

## Contexto

Caso de uso transversal: no se pide por separado, sino que ocurre siempre, por dentro, cada vez que algo cambia. Cuando un recurso o una reserva cambian de estado, este caso de uso le avisa al Módulo 1, que es el encargado de hacérselo llegar a la persona. Así todos se enteran a tiempo y los demás módulos trabajan con la misma información.

Ocurre dentro de `Consultar recursos`, `Reservar recursos`, `Cancelar reserva` e `Importar horarios semestrales`: ninguno de ellos tiene que pedirlo, y ninguno puede saltárselo.

**Actores**

| Actor | Tipo | Participación |
|---|---|---|
| Módulo 1 | Secundario (sistema) | Destinatario de los eventos; responsable de la entrega al usuario final. |
| Estudiante / Monitor / Dirección de Programa | Primarios (humanos) | Destinatarios finales de la información notificada. |

**Casos de uso relacionados**

- `Consultar recursos` — ver [spec-modulo2-uc1-consultar-recursos.md](./spec-modulo2-uc1-consultar-recursos.md)
- `Reservar recursos` — ver [spec-modulo2-uc2-reservar-recursos.md](./spec-modulo2-uc2-reservar-recursos.md)
- `Importar horarios semestrales` — ver [spec-modulo2-uc3-importar-horarios-semestrales.md](./spec-modulo2-uc3-importar-horarios-semestrales.md)
- `Cancelar reserva` — ver [spec-modulo2-uc4-cancelar-reserva.md](./spec-modulo2-uc4-cancelar-reserva.md)

**Catálogo de eventos**

| Evento | Se emite cuando |
|---|---|
| `RESERVA_CONFIRMADA` | Una reserva estudiantil queda persistida. |
| `RESERVA_CANCELADA` | El titular cancela su reserva. |
| `RESERVA_CANCELADA_POR_PRIORIDAD` | La jerarquía académica desplaza una reserva estudiantil. |
| `RECURSO_BLOQUEADO` | Un recurso pasa a `BLOQUEO_ACADEMICO` en una franja. |
| `RECURSO_LIBERADO` | Una franja vuelve a estar disponible. |

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Notificar estado de recursos (Priority: P3)

Como sistema, quiero publicar hacia el Módulo 1 cada cambio de estado de un recurso o de una reserva (consulta que deriva en ocupación, confirmación, cancelación manual y cancelación por prioridad académica), para que los interesados sean informados oportunamente y los demás módulos operen sobre información consistente.

**Why this priority**: Es un paso que ocurre siempre dentro de consultar, reservar y cancelar. Es P3 porque las historias base ya entregan valor sin él, pero sin notificación el estudiante desplazado por una clase se entera al llegar al salón.

**Independent Test**: Se puede probar de forma independiente disparando cada transición de estado y verificando contra un doble de prueba del Módulo 1 que se emitió un evento con el tipo, el recurso, la franja y el destinatario correctos.

**Acceptance Scenarios**:

1. **Scenario**: Notificación de reserva confirmada
   - **Given** un Estudiante confirma una reserva
   - **When** la reserva queda persistida
   - **Then** el sistema publica hacia el Módulo 1 un evento `RESERVA_CONFIRMADA` con identificador de reserva, recurso, franja y destinatario

2. **Scenario**: Notificación de desplazamiento académico
   - **Given** una reserva estudiantil fue cancelada automáticamente por prioridad académica
   - **When** se ejecuta la cancelación
   - **Then** el sistema publica un evento `RESERVA_CANCELADA_POR_PRIORIDAD` que incluye el motivo institucional y las alternativas de recursos disponibles en la misma franja

3. **Scenario**: Módulo 1 no disponible
   - **Given** el Módulo 1 no responde temporalmente
   - **When** ocurre un cambio de estado notificable
   - **Then** la operación de negocio se completa igualmente, el evento se encola y se reintenta, y no se pierde ninguna notificación

### Edge Cases

- **Cancelación masiva por importación**: una importación que desplaza decenas de reservas debe notificar a cada titular sin duplicar ni agrupar de forma que se pierda el detalle de su reserva.
- **Reintentos y duplicados**: el reintento tras una entrega fallida no debe producir dos notificaciones al mismo destinatario para el mismo evento.
- **Orden de eventos**: `RECURSO_LIBERADO` y `RECURSO_BLOQUEADO` sobre el mismo recurso y franja deben entregarse en el orden en que ocurrieron.
- **Destinatario sin canal de contacto válido**: el evento se registra igualmente y el fallo de entrega queda visible para soporte.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema DEBE publicar hacia el Módulo 1 un evento por cada transición de estado relevante: `RESERVA_CONFIRMADA`, `RESERVA_CANCELADA`, `RESERVA_CANCELADA_POR_PRIORIDAD`, `RECURSO_BLOQUEADO`, `RECURSO_LIBERADO`.
- **FR-002**: Cada evento DEBE incluir identificador de reserva o recurso, franja horaria, destinatario y motivo.
- **FR-003**: La indisponibilidad del Módulo 1 NO DEBE impedir la operación de negocio; los eventos DEBEN reintentarse hasta su entrega.
- **FR-004**: El evento `RESERVA_CANCELADA_POR_PRIORIDAD` DEBE incluir las alternativas de recursos disponibles en la misma franja.
- **FR-005**: La emisión de eventos DEBE ser idempotente frente a reintentos, sin generar notificaciones duplicadas al mismo destinatario.
- **FR-006**: El sistema DEBE conservar el registro de cada evento emitido y su resultado de entrega.

### Key Entities

- **EventoDeEstado**: mensaje publicado hacia el Módulo 1 ante cada transición notificable. Atributos: tipo, identificador de reserva o recurso, franja, destinatario, motivo, marca de tiempo, estado de entrega.
- **Reserva**: origen de los eventos de confirmación y cancelación.
- **Recurso**: origen de los eventos de bloqueo y liberación.
- **Usuario**: destinatario de la notificación.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: El 95 % de los estudiantes afectados por una cancelación por prioridad académica reciben la notificación dentro de los 5 minutos siguientes al desplazamiento.
- **SC-002**: Cero eventos perdidos ante una indisponibilidad del Módulo 1 de hasta 30 minutos.
- **SC-003**: El 100 % de las transiciones de estado notificables generan exactamente un evento entregado por destinatario.
- **SC-004**: Ninguna operación de negocio (reserva, cancelación, importación) falla por causa de un error en la notificación.
