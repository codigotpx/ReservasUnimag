# Feature Specification: Módulo 2 — Operación de Reservas y Priorización Académica

**Created**: 2026-08-24

## Contexto

Motor de reglas de negocio encargado de gestionar el uso de los recursos físicos de la universidad (salones, laboratorios, salas de estudio, equipos) y de resolver los conflictos de interés entre la actividad académica institucional y el apartado estudiantil.

**Actores**

| Actor | Tipo | Descripción |
|---|---|---|
| Estudiante | Primario (humano) | Consulta recursos, reserva y cancela sus propias reservas. |
| Monitor | Primario (humano) | Especialización de Estudiante: hereda todas sus capacidades y además consulta reportes de uso. |
| Dirección de Programa | Primario (humano) | Importa la carga académica semestral y consulta reportes institucionales. |
| Módulo 1 | Secundario (sistema) | Módulo de identidad/recursos y notificaciones; recibe los eventos de cambio de estado. |
| Módulo 3 | Secundario (sistema) | Módulo de analítica/reportería; consume y provee la información consolidada de uso. |

**Trazabilidad diagrama → historias**

| Óvalo del diagrama de casos de uso | Historia |
|---|---|
| Consultar recursos | US1 (P1) |
| Reservar recursos `<<extend>>` | US2 (P1) |
| Importar horarios semestrales | US3 (P2) |
| Cancelar reserva `<<extend>>` | US4 (P2) |
| Notificar estado de recursos `<<include>>` | US5 (P3) |
| Consultar reportes `<<include>>` | US6 (P3) |

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Consultar recursos disponibles (Priority: P1)

Como Estudiante (o Monitor), quiero consultar el catálogo de recursos filtrando por fecha, franja horaria y tipo de recurso, para ver únicamente aquellos que están realmente disponibles y no perder tiempo intentando apartar espacios ocupados o bloqueados por clase.

**Why this priority**: Es la puerta de entrada del módulo y el único caso de uso que no depende de ningún otro (todos los demás lo extienden o lo consumen). Por sí solo ya entrega valor: elimina el recorrido físico por el campus para averiguar qué está libre.

**Independent Test**: Se puede probar de forma independiente cargando un conjunto de recursos con estados mixtos (`DISPONIBLE`, `EN_USO`, `BLOQUEO_ACADEMICO`, `FUERA_DE_SERVICIO`) y verificando que la consulta para una franja dada devuelve solo los disponibles, sin necesidad de que exista la funcionalidad de reserva.

**Acceptance Scenarios**:

1. **Scenario**: Consulta de franja completamente libre
   - **Given** el recurso "Sala de Estudio 3" no tiene reservas ni clases registradas para el 2026-09-01 entre 10:00 y 12:00
   - **When** el Estudiante consulta los recursos disponibles para esa fecha y franja
   - **Then** el sistema muestra "Sala de Estudio 3" con estado `DISPONIBLE` y su capacidad asociada

2. **Scenario**: Ocultamiento por bloqueo académico
   - **Given** el "Laboratorio de Redes" tiene una clase importada del horario semestral el 2026-09-01 de 08:00 a 10:00
   - **When** el Estudiante consulta los recursos disponibles para el 2026-09-01 de 08:00 a 10:00
   - **Then** el "Laboratorio de Redes" no aparece dentro de los recursos seleccionables y se indica que está en `BLOQUEO_ACADEMICO`

3. **Scenario**: Ocultamiento por recurso en uso
   - **Given** el "Auditorio Menor" tiene una reserva estudiantil confirmada el 2026-09-02 de 14:00 a 16:00
   - **When** otro Estudiante consulta ese mismo día y franja
   - **Then** el "Auditorio Menor" se muestra como `EN_USO` y no es seleccionable

4. **Scenario**: Sin resultados
   - **Given** todos los recursos del tipo "Laboratorio" están ocupados o bloqueados en la franja consultada
   - **When** el Estudiante ejecuta la consulta filtrando por tipo "Laboratorio"
   - **Then** el sistema devuelve una lista vacía con un mensaje explicativo y sugiere la siguiente franja con disponibilidad

---

### User Story 2 - Reservar recursos con validación de reglas (Priority: P1)

Como Estudiante, quiero apartar un recurso disponible para una franja horaria concreta, y que el sistema me explique con un mensaje claro y específico cuando no puedo hacerlo, para saber exactamente qué regla estoy incumpliendo (conflicto académico, límite de préstamos alcanzado o sanción activa).

**Why this priority**: Es la propuesta de valor central del módulo. Junto con US1 constituye el MVP mínimo demostrable: consultar y apartar. El "Diccionario de Errores" es parte inseparable de esta historia porque una denegación sin causa explicada genera reprocesos y tickets de soporte.

**Independent Test**: Se puede probar de forma independiente ejecutando el flujo de reserva sobre un recurso disponible y verificando la persistencia de la reserva, y luego forzando cada una de las tres condiciones de denegación para verificar que se retorna el código de error correspondiente. No requiere reportería ni notificaciones.

**Acceptance Scenarios**:

1. **Scenario**: Reserva exitosa
   - **Given** el Estudiante no tiene sanciones activas, tiene 1 de 3 préstamos vigentes y la "Sala de Estudio 3" está `DISPONIBLE` el 2026-09-01 de 10:00 a 12:00
   - **When** solicita la reserva de ese recurso en esa franja
   - **Then** el sistema crea la reserva en estado `CONFIRMADA`, marca el recurso como `EN_USO` para esa franja y devuelve el identificador de la reserva

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

5. **Scenario**: Doble reserva concurrente sobre el mismo recurso
   - **Given** dos estudiantes solicitan simultáneamente la misma franja del mismo recurso disponible
   - **When** ambas solicitudes se procesan
   - **Then** exactamente una queda `CONFIRMADA` y la otra recibe `RES-004 — El recurso acaba de ser tomado`, sin que existan dos reservas solapadas

---

### User Story 3 - Importar horarios semestrales y aplicar jerarquía académica (Priority: P2)

Como Dirección de Programa, quiero cargar el archivo de horarios del semestre para que las clases fijas queden registradas automáticamente como "Bloqueo Académico", y quiero poder registrar necesidades institucionales extraordinarias que prevalezcan sobre las reservas estudiantiles ya existentes, para garantizar la continuidad de la actividad docente.

**Why this priority**: Es la fuente de verdad que alimenta la validación de disponibilidad de US1 y la denegación `RES-001` de US2. Es P2 y no P1 porque en un MVP los bloqueos pueden cargarse manualmente; la importación masiva es lo que hace el proceso sostenible a escala de semestre.

**Independent Test**: Se puede probar de forma independiente cargando un archivo de horarios de prueba y verificando en el calendario de recursos que las franjas correspondientes quedaron en `BLOQUEO_ACADEMICO`, más un caso de carga extraordinaria que desplaza una reserva estudiantil preexistente.

**Acceptance Scenarios**:

1. **Scenario**: Importación exitosa del semestre
   - **Given** la Dirección de Programa dispone de un archivo válido con las clases del semestre 2026-2
   - **When** ejecuta la importación
   - **Then** el sistema crea un bloqueo académico por cada sesión de clase, reporta el total de registros procesados y deja esas franjas no reservables por estudiantes

2. **Scenario**: Archivo con filas inválidas
   - **Given** el archivo contiene filas con recurso inexistente u horario mal formado
   - **When** se ejecuta la importación
   - **Then** el sistema procesa las filas válidas, rechaza las inválidas y entrega un reporte detallado indicando número de fila y motivo del rechazo

3. **Scenario**: Jerarquía — necesidad institucional extraordinaria
   - **Given** el "Auditorio Menor" tiene una reserva estudiantil `CONFIRMADA` el 2026-09-10 de 14:00 a 16:00
   - **When** la Dirección de Programa registra una actividad académica extraordinaria sobre ese recurso y franja
   - **Then** el sistema cancela automáticamente la reserva estudiantil con motivo `CANCELADA_POR_PRIORIDAD_ACADEMICA`, crea el bloqueo académico y deja registrado el evento para su notificación

4. **Scenario**: Una reserva académica nunca es desplazada
   - **Given** una franja ya está en `BLOQUEO_ACADEMICO`
   - **When** se intenta registrar otra actividad académica solapada sobre el mismo recurso
   - **Then** el sistema reporta el conflicto y exige resolución manual, sin cancelar automáticamente el bloqueo existente

---

### User Story 4 - Cancelar reserva (Priority: P2)

Como Estudiante, quiero cancelar una reserva que ya no voy a usar, para liberar el recurso a tiempo para otros compañeros y no acumular ausencias que deriven en una sanción.

**Why this priority**: Cierra el ciclo de vida de la reserva y es la principal fuente de liberación de disponibilidad. Sin esta historia el sistema funciona, pero la ocupación real se degrada por reservas fantasma; por eso va inmediatamente después del núcleo consultar/reservar.

**Independent Test**: Se puede probar de forma independiente creando una reserva confirmada, cancelándola y verificando que el recurso vuelve a aparecer como `DISPONIBLE` en la consulta de esa misma franja y que el contador de préstamos vigentes del estudiante disminuye.

**Acceptance Scenarios**:

1. **Scenario**: Cancelación por el titular
   - **Given** el Estudiante tiene una reserva `CONFIRMADA` que aún no ha iniciado
   - **When** solicita cancelarla
   - **Then** el sistema cambia la reserva a `CANCELADA`, libera la franja del recurso y descuenta el préstamo de su cupo vigente

2. **Scenario**: Cancelación no permitida sobre reserva ajena
   - **Given** una reserva pertenece a otro estudiante
   - **When** un estudiante distinto intenta cancelarla
   - **Then** el sistema rechaza la operación con `CAN-001 — No autorizado sobre esta reserva` y la reserva permanece `CONFIRMADA`

3. **Scenario**: Cancelación fuera de plazo
   - **Given** la reserva ya inició o finalizó
   - **When** el Estudiante intenta cancelarla
   - **Then** el sistema rechaza la operación con `CAN-002 — La reserva ya no es cancelable` e indica el plazo mínimo de antelación exigido

4. **Scenario**: Cancelación automática por prioridad académica
   - **Given** una reserva estudiantil es desplazada por una necesidad institucional extraordinaria (US3)
   - **When** el sistema ejecuta la cancelación automática
   - **Then** la reserva queda `CANCELADA_POR_PRIORIDAD_ACADEMICA`, no se contabiliza como ausencia ni penaliza al estudiante, y se emite el evento correspondiente

---

### User Story 5 - Notificar estado de recursos (Priority: P3)

Como sistema, quiero publicar hacia el Módulo 1 cada cambio de estado de un recurso o de una reserva (consulta que deriva en ocupación, confirmación, cancelación manual y cancelación por prioridad académica), para que los interesados sean informados oportunamente y los demás módulos operen sobre información consistente.

**Why this priority**: Es un caso de uso `<<include>>` transversal a consultar, reservar y cancelar. Es P3 porque las historias base ya entregan valor sin él, pero sin notificación el estudiante desplazado por una clase se entera al llegar al salón.

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

---

### User Story 6 - Consultar reportes de uso (Priority: P3)

Como Monitor o Dirección de Programa, quiero consultar reportes consolidados de ocupación, denegaciones y cancelaciones por recurso y por periodo, para evaluar la utilización real de la infraestructura y sustentar decisiones sobre horarios y capacidad.

**Why this priority**: Es valor analítico que solo existe una vez hay historial de operación acumulado. Depende de los datos generados por US1–US4 y se apoya en el Módulo 3, por lo que es lo último en la secuencia de entrega.

**Independent Test**: Se puede probar de forma independiente sembrando un histórico de reservas y verificando que los indicadores del reporte (tasa de ocupación, top de recursos, conteo de denegaciones por código de error, reservas desplazadas por prioridad académica) coinciden con los valores esperados para el periodo consultado.

**Acceptance Scenarios**:

1. **Scenario**: Reporte de ocupación por periodo
   - **Given** existe historial de reservas del 2026-09-01 al 2026-09-30
   - **When** la Dirección de Programa consulta el reporte de ocupación para ese rango
   - **Then** el sistema muestra por recurso las horas reservadas, las horas de bloqueo académico y el porcentaje de utilización

2. **Scenario**: Reporte de denegaciones
   - **Given** se registraron denegaciones con códigos `RES-001`, `RES-002` y `RES-003`
   - **When** el Monitor consulta el reporte de denegaciones
   - **Then** el sistema muestra el conteo agrupado por código y por recurso, permitiendo identificar cuellos de botella

3. **Scenario**: Restricción de alcance
   - **Given** un Estudiante sin rol de Monitor
   - **When** intenta acceder a la consulta de reportes
   - **Then** el sistema deniega el acceso con `REP-001 — No autorizado`

4. **Scenario**: Periodo sin datos
   - **Given** no existe actividad registrada en el rango solicitado
   - **When** se genera el reporte
   - **Then** el sistema devuelve el reporte con indicadores en cero y una nota explícita de "sin datos para el periodo"

---

### Edge Cases

- **Solapamiento parcial**: ¿Qué ocurre si una solicitud de reserva de 09:30 a 10:30 cae parcialmente sobre un bloqueo académico de 08:00 a 10:00? Debe denegarse por `RES-001`: cualquier intersección no vacía es conflicto.
- **Importación retroactiva**: ¿Qué pasa si se importa un horario que colisiona con decenas de reservas estudiantiles ya confirmadas? El sistema debe presentar el impacto antes de confirmar y cancelar en lote de forma transaccional.
- **Sanción que inicia con reservas vigentes**: ¿Se cancelan las reservas ya confirmadas de un estudiante sancionado, o solo se le impide crear nuevas? [NEEDS CLARIFICATION: política de sanción retroactiva no especificada]
- **Recurso dado de baja**: cuando un recurso pasa a `FUERA_DE_SERVICIO`, sus reservas futuras deben cancelarse con motivo propio y notificarse, sin penalizar a los titulares.
- **Cambio de horario a mitad de semestre**: la reimportación debe ser idempotente sobre las sesiones ya cargadas y no duplicar bloqueos.
- **Concurrencia en la última franja**: dos confirmaciones simultáneas sobre el mismo recurso y franja nunca pueden coexistir.
- **Zona horaria y cambio de día**: reservas que cruzan la medianoche o franjas definidas en el límite del día. [NEEDS CLARIFICATION: ¿se permiten reservas que crucen la medianoche?]
- **No presentación (no-show)**: ¿tras cuántos minutos sin uso se libera automáticamente el recurso y se contabiliza ausencia? [NEEDS CLARIFICATION: umbral de no-show y su relación con las sanciones no especificados]

## Requirements *(mandatory)*

### Functional Requirements

**Consulta de recursos (US1)**

- **FR-001**: El sistema DEBE permitir consultar los recursos filtrando por fecha, franja horaria, tipo de recurso y capacidad mínima.
- **FR-002**: El sistema DEBE excluir de la selección estudiantil todo recurso cuyo estado en la franja consultada sea `BLOQUEO_ACADEMICO`, `EN_USO` o `FUERA_DE_SERVICIO`.
- **FR-003**: El sistema DEBE calcular la disponibilidad como la ausencia de intersección con cualquier bloqueo académico o reserva vigente sobre el mismo recurso.

**Reserva (US2)**

- **FR-004**: Los usuarios DEBEN poder crear una reserva sobre un recurso disponible indicando fecha, hora de inicio y hora de fin.
- **FR-005**: El sistema DEBE validar, antes de confirmar, y en este orden: sanción activa, límite de préstamos vigentes y conflicto académico u ocupación.
- **FR-006**: El sistema DEBE denegar la reserva con un código y mensaje específico del diccionario de errores: `RES-001` conflicto académico, `RES-002` límite máximo de préstamos alcanzado, `RES-003` sanción activa, `RES-004` recurso tomado concurrentemente.
- **FR-007**: El sistema DEBE garantizar que no existan dos reservas confirmadas solapadas sobre el mismo recurso, incluso bajo solicitudes concurrentes.
- **FR-008**: El sistema DEBE registrar toda denegación con su código, usuario, recurso y marca de tiempo, para alimentar la reportería.

**Carga académica (US3)**

- **FR-009**: La Dirección de Programa DEBE poder importar el horario semestral de forma masiva desde un archivo.
- **FR-010**: El sistema DEBE crear automáticamente un `BLOQUEO_ACADEMICO` por cada sesión de clase importada.
- **FR-011**: El sistema DEBE validar cada fila del archivo y entregar un reporte de importación con registros procesados, rechazados y motivo del rechazo.
- **FR-012**: El sistema DEBE otorgar a las reservas académicas prioridad total sobre las estudiantiles.
- **FR-013**: El sistema DEBE cancelar automáticamente las reservas estudiantiles en conflicto cuando se registre una necesidad institucional extraordinaria, marcándolas con motivo `CANCELADA_POR_PRIORIDAD_ACADEMICA`.
- **FR-014**: El sistema NO DEBE cancelar automáticamente un bloqueo académico existente por causa de otro bloqueo académico; ese conflicto se resuelve manualmente.
- **FR-015**: La reimportación de un horario ya cargado DEBE ser idempotente y no generar bloqueos duplicados.

**Cancelación (US4)**

- **FR-016**: Los usuarios DEBEN poder cancelar sus propias reservas no iniciadas, respetando la antelación mínima configurada.
- **FR-017**: El sistema DEBE liberar la franja del recurso y actualizar el cupo de préstamos vigentes al cancelar.
- **FR-018**: El sistema DEBE impedir que un usuario cancele reservas de las que no es titular (`CAN-001`) y que cancele reservas ya iniciadas o finalizadas (`CAN-002`).
- **FR-019**: Las cancelaciones por prioridad académica NO DEBEN penalizar al estudiante ni computar como ausencia.

**Notificación (US5)**

- **FR-020**: El sistema DEBE publicar hacia el Módulo 1 un evento por cada transición de estado relevante: `RESERVA_CONFIRMADA`, `RESERVA_CANCELADA`, `RESERVA_CANCELADA_POR_PRIORIDAD`, `RECURSO_BLOQUEADO`, `RECURSO_LIBERADO`.
- **FR-021**: Cada evento DEBE incluir identificador de reserva o recurso, franja horaria, destinatario y motivo.
- **FR-022**: La indisponibilidad del Módulo 1 NO DEBE impedir la operación de negocio; los eventos DEBEN reintentarse hasta su entrega.

**Reportes (US6)**

- **FR-023**: El sistema DEBE ofrecer reportes de ocupación por recurso y periodo, de denegaciones agrupadas por código y de reservas desplazadas por prioridad académica.
- **FR-024**: El acceso a los reportes DEBE restringirse a los roles Monitor y Dirección de Programa (`REP-001` para accesos no autorizados).
- **FR-025**: El sistema DEBE exponer la información consolidada de uso hacia el Módulo 3.

**Transversales**

- **FR-026**: El sistema DEBE aplicar al rol Monitor todas las capacidades del rol Estudiante, más el acceso a reportes.
- **FR-027**: El sistema DEBE mantener un registro de auditoría de toda creación, cancelación y bloqueo, con autor y marca de tiempo.
- **FR-028**: El límite máximo de préstamos simultáneos DEBE ser parametrizable. [NEEDS CLARIFICATION: valor por defecto y si varía por rol o tipo de recurso]
- **FR-029**: La duración máxima de una reserva y la antelación mínima de cancelación DEBEN ser parametrizables. [NEEDS CLARIFICATION: valores no especificados]

### Key Entities

- **Recurso**: espacio o equipo reservable. Atributos: identificador, nombre, tipo, ubicación, capacidad, estado operativo.
- **FranjaHoraria**: intervalo con fecha, hora de inicio y hora de fin; unidad sobre la que se calcula el solapamiento.
- **Reserva**: apartado de un Recurso por un Usuario en una FranjaHoraria. Atributos: identificador, titular, recurso, franja, estado (`CONFIRMADA`, `CANCELADA`, `CANCELADA_POR_PRIORIDAD_ACADEMICA`, `FINALIZADA`), origen (estudiantil o académico), motivo de cancelación.
- **BloqueoAcadémico**: ocupación de máxima prioridad derivada de la carga académica. Atributos: recurso, franja, asignatura, programa, docente, origen (horario regular o extraordinario).
- **HorarioSemestral**: agrupación de sesiones de clase importadas para un periodo académico; conserva el resultado de la importación.
- **Usuario**: persona con un rol (Estudiante, Monitor, Dirección de Programa); relación con sus reservas vigentes.
- **Sanción**: restricción temporal sobre un Usuario. Atributos: motivo, fecha de inicio, fecha de fin, estado.
- **Denegación**: registro de un intento de reserva rechazado, con código del diccionario de errores, usuario, recurso y marca de tiempo.
- **EventoDeEstado**: mensaje publicado hacia el Módulo 1 ante cada transición notificable.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Un estudiante completa el flujo de consultar y reservar un recurso en menos de 2 minutos desde el inicio de la sesión.
- **SC-002**: El 100 % de los intentos de reserva sobre franjas con bloqueo académico son denegados, con cero reservas estudiantiles solapadas con clases en un semestre completo.
- **SC-003**: El 100 % de las denegaciones se entregan con un código y un mensaje del diccionario de errores que identifica la causa exacta; ninguna denegación genérica.
- **SC-004**: La importación de un horario semestral completo se procesa en menos de 5 minutos y reporta el 100 % de las filas rechazadas con su motivo.
- **SC-005**: El 95 % de los estudiantes afectados por una cancelación por prioridad académica reciben la notificación dentro de los 5 minutos siguientes al desplazamiento.
- **SC-006**: La consulta de disponibilidad responde en menos de 2 segundos con 500 usuarios concurrentes.
- **SC-007**: Cero reservas duplicadas sobre el mismo recurso y franja bajo pruebas de concurrencia.
- **SC-008**: Reducción del 50 % en las solicitudes manuales de apartado de espacios gestionadas por la Dirección de Programa durante el primer semestre de operación.
