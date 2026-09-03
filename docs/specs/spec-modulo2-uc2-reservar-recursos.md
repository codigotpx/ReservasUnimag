# Feature Specification: Reservar recursos

**Created**: 2026-08-24
**Módulo**: 2 — Operación de Reservas y Priorización Académica
**Caso de uso**: `Reservar recursos`, continuación de `Consultar recursos`
**Prioridad global**: P1

## Contexto

Propuesta de valor central del Módulo 2: la interfaz de apartado estudiantil. Extiende `Consultar recursos` —el estudiante llega aquí desde la lista de recursos disponibles— y aplica el motor de reglas de negocio que decide si la reserva se confirma o se deniega, con un diccionario de errores que explica siempre la causa exacta.

**Actores**

| Actor | Tipo | Participación |
|---|---|---|
| Estudiante | Primario | Solicita el apartado de un recurso para una franja horaria. |
| Monitor | Primario | Especialización de Estudiante: hereda esta capacidad. |
| Módulo 1 | Secundario | Aporta el estado real del recurso y recibe el estado actualizado tras la reserva. |
| Módulo 3 | Secundario | Provee las sanciones vigentes de la persona y recibe el reporte cuando no se presenta a usar lo que apartó. |

**Casos de uso relacionados**

- `Consultar recursos` — ver [spec-modulo2-uc1-consultar-recursos.md](./spec-modulo2-uc1-consultar-recursos.md)
- `Actualizar estado de los recursos` — **paso que ocurre siempre por dentro**: al confirmar la reserva, el recurso cambia de estado y el Módulo 1 se entera; no se puede saltar; ver [spec-modulo2-uc7-actualizar-estado-recursos.md](./spec-modulo2-uc7-actualizar-estado-recursos.md)
- `Consultar disponibilidad de los recursos` — la comprobación que se hace en el último momento, antes de confirmar; ver [spec-modulo2-uc8-consultar-disponibilidad-recursos.md](./spec-modulo2-uc8-consultar-disponibilidad-recursos.md)
- `Consultar reportes` — de ahí sale la información de sanciones que sustenta la denegación `RES-003`; ver [spec-modulo2-uc6-consultar-reportes.md](./spec-modulo2-uc6-consultar-reportes.md)
- `Reportar no asistencia` — lo que ocurre si la persona no llega a usar lo que apartó; ver [spec-modulo2-uc9-reportar-no-asistencia.md](./spec-modulo2-uc9-reportar-no-asistencia.md)
- `Notificar estado de recursos al finalizar reserva` — le cuenta al Módulo 3 cómo terminó la reserva; ver [spec-modulo2-uc5-notificar-estado-recursos.md](./spec-modulo2-uc5-notificar-estado-recursos.md)
- `Importar horarios semestrales` — origen de las denegaciones `RES-001`; ver [spec-modulo2-uc3-importar-horarios-semestrales.md](./spec-modulo2-uc3-importar-horarios-semestrales.md)

**Diccionario de errores**

| Código | Causa |
|---|---|
| `RES-001` | Conflicto académico: el recurso está reservado para actividad docente. |
| `RES-002` | Límite máximo de préstamos vigentes alcanzado. |
| `RES-003` | Sanción activa sobre el usuario. |
| `RES-004` | El recurso acaba de ser tomado (conflicto de concurrencia). |

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Reservar recursos con validación de reglas (Priority: P1)

Como Estudiante, quiero apartar un recurso disponible para una franja horaria concreta, y que el sistema me explique con un mensaje claro y específico cuando no puedo hacerlo, para saber exactamente qué regla estoy incumpliendo (conflicto académico, límite de préstamos alcanzado o sanción activa).

**Why this priority**: Es la propuesta de valor central del módulo. Junto con `Consultar recursos` constituye el MVP mínimo demostrable: consultar y apartar. El "Diccionario de Errores" es parte inseparable de esta historia porque una denegación sin causa explicada genera reprocesos y tickets de soporte.

**Independent Test**: Se puede probar de forma independiente ejecutando el flujo de reserva sobre un recurso disponible y verificando la persistencia de la reserva, y luego forzando cada una de las tres condiciones de denegación para verificar que se retorna el código de error correspondiente. No requiere reportería ni notificaciones.

**Acceptance Scenarios**:

1. **Scenario**: Reserva exitosa
   - **Given** el Estudiante no tiene sanciones activas, tiene 1 de 3 préstamos vigentes y la "Sala de Estudio 3" está `DISPONIBLE` el 2026-09-01 de 10:00 a 12:00
   - **When** solicita la reserva de ese recurso en esa franja
   - **Then** el sistema crea la reserva en estado `CONFIRMADA` y marca el recurso como `RESERVADO` para esa franja; cuando llega la hora de la franja y la persona se presenta, el recurso pasa a `EN_USO`. El sistema devuelve el identificador de la reserva

2. **Scenario**: Denegación por conflicto académico (RES-001)
   - **Given** el "Laboratorio de Redes" quedó marcado como `BLOQUEO_ACADEMICO` el 2026-09-01 de 08:00 a 10:00
   - **When** el Estudiante intenta reservarlo en esa franja
   - **Then** el sistema rechaza la solicitud con el error `RES-001 — Conflicto académico: el recurso está reservado para actividad docente` y no crea ningún registro

3. **Scenario**: Denegación por límite de préstamos (RES-002)
   - **Given** el Estudiante ya tiene el número máximo de reservas vigentes permitido
   - **When** intenta crear una reserva adicional
   - **Then** el sistema rechaza la solicitud con el error `RES-002 — Límite máximo de préstamos alcanzado` e informa cuántas reservas vigentes tiene y cuándo se libera la próxima

4. **Scenario**: Denegación por sanción activa (RES-003)
   - **Given** el Estudiante tiene una sanción vigente hasta el 2026-09-15
   - **When** intenta reservar cualquier recurso
   - **Then** el sistema rechaza la solicitud con el error `RES-003 — Sanción activa` e informa la fecha de finalización de la sanción

### Edge Cases

- **Solapamiento parcial**: ¿Qué ocurre si una solicitud de reserva de 09:30 a 10:30 cae parcialmente sobre un bloqueo académico de 08:00 a 10:00? Debe denegarse por `RES-001`.
- **Concurrencia sobre la misma franja**: si dos estudiantes solicitan simultáneamente el mismo recurso y franja, exactamente uno queda `CONFIRMADA` y el otro recibe `RES-004 — El recurso acaba de ser tomado`; dos reservas solapadas nunca pueden coexistir.
- **Sanción que inicia con reservas vigentes**: se le debe impedir crear nuevas reservas y además se le deben cancelar las que ya tenían.
- **Múltiples causas de denegación simultáneas**: se aplica el orden de validación definido (sanción, luego límite, luego conflicto/ocupación) y se devuelve un único código, el primero que falla.
- **No presentación (no-show)**: el recurso pasa a `EN_USO` al llegar la hora de la franja, aunque la persona no se haya presentado; tras 10 minutos sin presentarse, el recurso vuelve a `DISPONIBLE` y la ausencia se le reporta al Módulo 3, que es quien decide y aplica la sanción. Este módulo no sanciona a nadie.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Los usuarios DEBEN poder crear una reserva sobre un recurso disponible indicando fecha, hora de inicio y hora de fin.
- **FR-002**: El sistema DEBE validar, antes de confirmar, y en este orden: sanción activa, límite de préstamos vigentes y conflicto académico u ocupación. La sanción se comprueba contra el Módulo 3 mediante `Consultar reportes`.
- **FR-003**: El sistema DEBE denegar la reserva con un código y mensaje específico del diccionario de errores: `RES-001` conflicto académico, `RES-002` límite máximo de préstamos alcanzado, `RES-003` sanción activa, `RES-004` recurso tomado concurrentemente.
- **FR-004**: El sistema DEBE garantizar que no existan dos reservas confirmadas solapadas sobre el mismo recurso, incluso bajo solicitudes concurrentes.
- **FR-005**: El sistema DEBE registrar toda denegación con su código, usuario, recurso y marca de tiempo, para alimentar la reportería.
- **FR-006**: El sistema DEBE revalidar la disponibilidad en el momento de confirmar, mediante `Consultar disponibilidad de los recursos`, sin confiar en el resultado de la consulta previa.
- **FR-007**: El sistema DEBE mantener registro de auditoría de toda creación de reserva, con autor y marca de tiempo.
- **FR-008**: El límite máximo de préstamos simultáneos DEBE ser parametrizable. [NEEDS CLARIFICATION: valor por defecto y si varía por rol o tipo de recurso]
- **FR-009**: La duración máxima de una reserva DEBE ser parametrizable. [NEEDS CLARIFICATION: valor no especificado]
- **FR-010**: El sistema DEBE liberar automáticamente el recurso y reportar la ausencia del titular al Módulo 3 si transcurren 10 minutos desde el inicio de la franja sin que se registre el uso; la sanción la decide y la aplica el Módulo 3.
- **FR-011**: Al confirmar una reserva, el sistema DEBE ejecutar siempre `Actualizar estado de los recursos`, sin que el usuario tenga que pedirlo y sin posibilidad de omitirlo.

### Key Entities

- **Reserva**: apartado de un Recurso por un Usuario en una FranjaHoraria. Atributos: identificador, titular, recurso, franja, estado (`CONFIRMADA`, `CANCELADA`, `CANCELADA_POR_PRIORIDAD_ACADEMICA`, `FINALIZADA`), origen (estudiantil o académico).
- **Recurso**: espacio o equipo reservable, con su estado operativo por franja.
- **FranjaHoraria**: intervalo con fecha, hora de inicio y hora de fin; unidad sobre la que se calcula el solapamiento.
- **Usuario**: persona con un rol (Estudiante, Monitor); relación con sus reservas vigentes y su cupo de préstamos.
- **Sanción**: restricción temporal sobre un Usuario. Atributos: motivo, fecha de inicio, fecha de fin, estado.
- **Denegación**: registro de un intento de reserva rechazado, con código del diccionario de errores, usuario, recurso y marca de tiempo.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Un estudiante completa el flujo de consultar y reservar un recurso en menos de 2 minutos desde el inicio de la sesión.
- **SC-002**: El 100 % de los intentos de reserva sobre franjas con bloqueo académico son denegados, con cero reservas estudiantiles solapadas con clases en un semestre completo.
- **SC-003**: El 100 % de las denegaciones se entregan con un código y un mensaje del diccionario de errores que identifica la causa exacta; ninguna denegación genérica.
- **SC-004**: Cero reservas duplicadas sobre el mismo recurso y franja bajo pruebas de concurrencia.
- **SC-005**: Reducción del 50 % en las solicitudes manuales de apartado de espacios gestionadas por la Dirección de Programa durante el primer semestre de operación.
