# Feature Specification: Importar horarios semestrales

**Created**: 2026-08-24
**Módulo**: 2 — Operación de Reservas y Priorización Académica
**Caso de uso (diagrama)**: `Importar horarios semestrales`
**Prioridad global**: P2

## Contexto

Módulo de carga académica: la fuente de verdad de la prioridad institucional. La Dirección de Programa carga las clases fijas del semestre, que quedan registradas como "Bloqueo Académico" sobre los recursos, y registra necesidades institucionales extraordinarias que prevalecen sobre las reservas estudiantiles ya confirmadas.

Alimenta la validación de disponibilidad de `Consultar recursos` y la denegación `RES-001` de `Reservar recursos`.

**Actores**

| Actor | Tipo | Participación |
|---|---|---|
| Dirección de Programa | Primario (humano) | Importa la carga académica semestral y registra actividades extraordinarias. |
| Módulo 1 | Secundario (sistema) | Recibe los eventos de bloqueo de recursos y de cancelación por prioridad académica. |

**Casos de uso relacionados**

- `Consultar recursos` — consume los bloqueos generados; ver [spec-modulo2-uc1-consultar-recursos.md](./spec-modulo2-uc1-consultar-recursos.md)
- `Reservar recursos` — deniega con `RES-001` sobre estos bloqueos; ver [spec-modulo2-uc2-reservar-recursos.md](./spec-modulo2-uc2-reservar-recursos.md)
- `Cancelar reserva` — recibe las cancelaciones automáticas por prioridad; ver [spec-modulo2-uc4-cancelar-reserva.md](./spec-modulo2-uc4-cancelar-reserva.md)
- `Notificar estado de recursos` (`<<include>>`) — ver [spec-modulo2-uc5-notificar-estado-recursos.md](./spec-modulo2-uc5-notificar-estado-recursos.md)

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Importar horarios semestrales y aplicar jerarquía académica (Priority: P2)

Como Dirección de Programa, quiero cargar el archivo de horarios del semestre para que las clases fijas queden registradas automáticamente como "Bloqueo Académico", y quiero poder registrar necesidades institucionales extraordinarias que prevalezcan sobre las reservas estudiantiles ya existentes, para garantizar la continuidad de la actividad docente.

**Why this priority**: Es la fuente de verdad que alimenta la validación de disponibilidad de `Consultar recursos` y la denegación `RES-001` de `Reservar recursos`. Es P2 y no P1 porque en un MVP los bloqueos pueden cargarse manualmente; la importación masiva es lo que hace el proceso sostenible a escala de semestre.

**Independent Test**: Se puede probar de forma independiente cargando un archivo de horarios de prueba y verificando en el calendario de recursos que las franjas correspondientes quedaron en `BLOQUEO_ACADEMICO`, más un caso de carga extraordinaria que desplaza una reserva estudiantil preexistente.

**Acceptance Scenarios**:

1. **Scenario**: Importación exitosa del semestre
   - **Given** la Dirección de Programa dispone de un archivo válido con las clases del semestre 2026-2
   - **When** ejecuta la importación
   - **Then** el sistema crea un bloqueo académico por cada sesión de clase, reporta el total de registros procesados y deja esas franjas no reservables por estudiantes

2. **Scenario**: Archivo con filas inválidas
   - **Given** el archivo contiene filas con recurso inexistente u horario mal formado
   - **When** se ejecuta la importación
   - **Then** el sistema procesa todo el archivo, en el caso de que haya al menos una fila inválida rechaza todo el archivo y entrega un reporte detallado indicando número de fila y motivo del rechazo

3. **Scenario**: Jerarquía — necesidad institucional extraordinaria
   - **Given** el "Auditorio Menor" tiene una reserva estudiantil `CONFIRMADA` el 2026-09-10 de 14:00 a 16:00
   - **When** la Dirección de Programa registra una actividad académica extraordinaria sobre ese recurso y franja
   - **Then** el sistema cancela automáticamente la reserva estudiantil con motivo `CANCELADA_POR_PRIORIDAD_ACADEMICA`, crea el bloqueo académico y deja registrado el evento para su notificación

4. **Scenario**: Una reserva académica nunca es desplazada
   - **Given** una franja ya está en `BLOQUEO_ACADEMICO`
   - **When** se intenta registrar otra actividad académica solapada sobre el mismo recurso
   - **Then** el sistema reporta el conflicto y exige resolución manual, sin cancelar automáticamente el bloqueo existente

### Edge Cases

- **Importación retroactiva**: ¿qué pasa si se importa un horario que colisiona con decenas de reservas estudiantiles ya confirmadas? El sistema debe presentar el impacto antes de confirmar y cancelar en lote de forma transaccional.
- **Cambio de horario a mitad de semestre**: la reimportación debe ser idempotente sobre las sesiones ya cargadas y no duplicar bloqueos.
- **Recurso dado de baja**: filas del archivo que referencien un recurso en `FUERA_DE_SERVICIO` deben reportarse como conflicto y no generar bloqueo silencioso.
- **Archivo vacío o sin filas válidas**: la importación debe terminar sin efectos y con un reporte explícito, nunca con un éxito falso.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: La Dirección de Programa DEBE poder importar el horario semestral de forma masiva desde un archivo.
- **FR-002**: El sistema DEBE crear automáticamente un `BLOQUEO_ACADEMICO` por cada sesión de clase importada.
- **FR-003**: El sistema DEBE validar cada fila del archivo y entregar un reporte de importación con registros procesados, rechazados y motivo del rechazo.
- **FR-004**: El sistema DEBE otorgar a las reservas académicas prioridad total sobre las estudiantiles.
- **FR-005**: El sistema DEBE cancelar automáticamente las reservas estudiantiles en conflicto cuando se registre una necesidad institucional extraordinaria, marcándolas con motivo `CANCELADA_POR_PRIORIDAD_ACADEMICA`.
- **FR-006**: El sistema NO DEBE cancelar automáticamente un bloqueo académico existente por causa de otro bloqueo académico; ese conflicto se resuelve manualmente.
- **FR-007**: La reimportación de un horario ya cargado DEBE ser idempotente y no generar bloqueos duplicados.
- **FR-008**: El sistema DEBE presentar el impacto sobre reservas estudiantiles confirmadas antes de aplicar una importación o una actividad extraordinaria, y aplicar el lote de forma transaccional.
- **FR-009**: El sistema DEBE mantener registro de auditoría de toda creación de bloqueo académico, con autor y marca de tiempo.

### Key Entities

- **BloqueoAcadémico**: ocupación de máxima prioridad derivada de la carga académica. Atributos: recurso, franja, asignatura, programa, docente, origen (horario regular o extraordinario).
- **HorarioSemestral**: agrupación de sesiones de clase importadas para un periodo académico; conserva el resultado de la importación.
- **Recurso**: espacio o equipo sobre el que se aplica el bloqueo.
- **FranjaHoraria**: intervalo con fecha, hora de inicio y hora de fin; unidad sobre la que se calcula el solapamiento.
- **Reserva**: apartado estudiantil que puede ser desplazado por la jerarquía académica.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: La importación de un horario semestral completo se procesa en menos de 5 minutos y reporta el 100 % de las filas rechazadas con su motivo.
- **SC-002**: El 100 % de las sesiones de clase importadas quedan reflejadas como franjas no reservables por estudiantes.
- **SC-003**: Cero bloqueos duplicados tras una reimportación del mismo horario.
- **SC-004**: El 100 % de las reservas estudiantiles en conflicto con una actividad extraordinaria quedan canceladas con motivo `CANCELADA_POR_PRIORIDAD_ACADEMICA` y con evento emitido.
