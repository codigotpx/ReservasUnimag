# Feature Specification: Consultar reportes

**Created**: 2026-08-24
**Módulo**: 2 — Operación de Reservas y Priorización Académica
**Caso de uso (diagrama)**: `Consultar reportes` (`<<include>>`)
**Prioridad global**: P3

## Contexto

Capa analítica del módulo. El Monitor y la Dirección de Programa consultan reportes consolidados de ocupación, denegaciones y cancelaciones por recurso y por periodo, construidos sobre el historial que generan los demás casos de uso. La información consolidada se expone hacia el Módulo 3.

**Actores**

| Actor | Tipo | Participación |
|---|---|---|
| Monitor | Primario (humano) | Consulta reportes de uso; es la capacidad que lo distingue del Estudiante. |
| Dirección de Programa | Primario (humano) | Consulta reportes institucionales de ocupación y denegaciones. |
| Módulo 3 | Secundario (sistema) | Consume y provee la información consolidada de uso. |

**Casos de uso relacionados**

- `Consultar recursos`, `Reservar recursos`, `Importar horarios semestrales` y `Cancelar reserva` — generan los datos que alimentan estos reportes.

**Diccionario de errores**

| Código | Causa |
|---|---|
| `REP-001` | No autorizado: el rol no tiene acceso a los reportes. |

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Consultar reportes de uso (Priority: P3)

Como Monitor o Dirección de Programa, quiero consultar reportes consolidados de ocupación, denegaciones y cancelaciones por recurso y por periodo, para evaluar la utilización real de la infraestructura y sustentar decisiones sobre horarios y capacidad.

**Why this priority**: Es valor analítico que solo existe una vez hay historial de operación acumulado. Depende de los datos generados por los casos de uso de consulta, reserva, importación y cancelación, y se apoya en el Módulo 3, por lo que es lo último en la secuencia de entrega.

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

### Edge Cases

- **Periodo que cruza semestres**: el reporte debe delimitar con claridad qué bloqueos académicos pertenecen a cada horario semestral cargado.
- **Recurso dado de baja durante el periodo**: sus horas deben seguir apareciendo en el histórico, marcadas como recurso fuera de servicio desde la fecha de baja.
- **Rango de fechas invertido o excesivamente amplio**: el sistema debe validar el rango y acotar el volumen del reporte.
- **Reservas canceladas por prioridad académica**: no deben computar como ocupación efectiva, pero sí aparecer en su propio indicador de desplazamientos.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema DEBE ofrecer reportes de ocupación por recurso y periodo, de denegaciones agrupadas por código y de reservas desplazadas por prioridad académica.
- **FR-002**: El acceso a los reportes DEBE restringirse a los roles Monitor y Dirección de Programa (`REP-001` para accesos no autorizados).
- **FR-003**: El sistema DEBE exponer la información consolidada de uso hacia el Módulo 3.
- **FR-004**: El sistema DEBE devolver un reporte con indicadores en cero y nota explícita cuando el periodo consultado no tenga datos.
- **FR-005**: Los reportes DEBEN permitir filtrar por recurso, tipo de recurso y rango de fechas.
- **FR-006**: El sistema DEBE calcular la ocupación diferenciando horas reservadas, horas de bloqueo académico y horas libres.

### Key Entities

- **Reserva**: fuente de las horas reservadas y de las cancelaciones contabilizadas.
- **BloqueoAcadémico**: fuente de las horas de uso docente del recurso.
- **Denegación**: registro de intento rechazado, con código del diccionario de errores, usuario, recurso y marca de tiempo; base del reporte de denegaciones.
- **Recurso**: unidad de agregación principal de los reportes.
- **Usuario**: consultante del reporte; su rol determina el alcance permitido.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Los indicadores del reporte coinciden al 100 % con el histórico sembrado en las pruebas para el periodo consultado.
- **SC-002**: Un reporte de un mes completo se genera en menos de 10 segundos.
- **SC-003**: Cero accesos a reportes por parte de roles no autorizados.
- **SC-004**: La Dirección de Programa identifica los tres recursos con mayor demanda insatisfecha (denegaciones `RES-001`/`RES-004`) en menos de 5 minutos de uso del reporte.
