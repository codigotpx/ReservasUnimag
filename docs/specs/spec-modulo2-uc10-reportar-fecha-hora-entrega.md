# Feature Specification: Reportar fecha y hora de entrega

**Created**: 2026-09-03
**Módulo**: 2 — Operación de Reservas y Priorización Académica
**Caso de uso (diagrama)**: `Reportar fecha y hora de entrega` (informa al Módulo 3)
**Prioridad global**: P3

## Contexto

Cuando lo que se apartó es un equipo prestado —un microscopio, un kit de dibujo, un videobeam— no basta con saber que se lo llevaron: hay que saber cuándo lo devolvieron. Este caso de uso registra ese momento y se lo reporta al Módulo 3, que compara la hora real de devolución contra la hora que estaba pactada y decide si fue una entrega a tiempo o hubo mora.

Igual que con las ausencias, el reparto es claro: el Módulo 2 registra y reporta los hechos, el Módulo 3 saca las consecuencias. Según [gestionunimag.md](../gestionunimag.md), una entrega a tiempo sube el "score" de confianza de la persona y un retraso genera suspensión temporal de reservas, pero esas dos cosas las aplica el Módulo 3.

**Actores**

| Actor | Tipo | Participación |
|---|---|---|
| Módulo 3 | Secundario | Recibe la fecha y hora de devolución; calcula si hubo mora y aplica la consecuencia. |
| Estudiante / Monitor | Primarios | Devuelven el recurso prestado. |
| Monitor | Primario | Puede registrar la devolución de un recurso en nombre del punto de préstamo. |

**Casos de uso relacionados**

- `Reservar recursos` — define la franja y con ella la hora pactada de devolución; ver [spec-modulo2-uc2-reservar-recursos.md](./spec-modulo2-uc2-reservar-recursos.md)
- `Actualizar estado de los recursos` — al registrarse la devolución, el recurso vuelve a estar disponible; ver [spec-modulo2-uc7-actualizar-estado-recursos.md](./spec-modulo2-uc7-actualizar-estado-recursos.md)
- `Reportar no asistencia` — el caso contrario: la persona nunca llegó a llevarse el recurso; ver [spec-modulo2-uc9-reportar-no-asistencia.md](./spec-modulo2-uc9-reportar-no-asistencia.md)
- `Consultar reportes` — el camino de vuelta: las moras que aquí se reportan son parte de lo que el Módulo 3 devuelve después como sanción; ver [spec-modulo2-uc6-consultar-reportes.md](./spec-modulo2-uc6-consultar-reportes.md)

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Registrar y reportar la devolución de un recurso (Priority: P3)

Como sistema, quiero registrar la fecha y la hora exactas en que se devuelve un recurso prestado y reportárselas al Módulo 3, para que la universidad pueda distinguir a quien cumple de quien se retrasa y para que el recurso vuelva a estar disponible en cuanto regresa.

**Why this priority**: Es P3 porque el núcleo del módulo —consultar y reservar— funciona sin ella, y porque afecta sobre todo a los equipos en préstamo, no a los espacios. Pero sin este registro el Módulo 3 no puede calcular la mora, y toda la matriz de sanciones por retraso se queda sin datos.

**Independent Test**: Se puede probar sola registrando la devolución de un préstamo y verificando que quedó guardada la fecha y hora, que se envió al Módulo 3 y que el recurso volvió a estar disponible. No necesita que las sanciones ni los reportes estén implementados.

**Acceptance Scenarios**:

1. **Scenario**: Devolución a tiempo
   - **Given** un Estudiante tiene prestado el "Microscopio 07" con devolución pactada el 2026-09-01 a las 16:00
   - **When** lo devuelve a las 15:40 y queda registrada la devolución
   - **Then** el sistema guarda la fecha y hora reales, reporta al Módulo 3 que la devolución fue anterior a la hora pactada, y el recurso vuelve a estar disponible

2. **Scenario**: Devolución con retraso
   - **Given** un Estudiante tiene prestado el "Kit de dibujo 22" con devolución pactada a las 16:00
   - **When** lo devuelve a las 18:30
   - **Then** el sistema guarda la fecha y hora reales, reporta al Módulo 3 la devolución junto con el retraso de 2 horas y 30 minutos, y el recurso vuelve a estar disponible

3. **Scenario**: Recurso devuelto con daño
   - **Given** un Estudiante devuelve el "Videobeam 12" y quien lo recibe reporta un daño
   - **When** se registra la devolución con la novedad
   - **Then** el sistema guarda la fecha y hora, reporta al Módulo 3 la devolución junto con la novedad, y el recurso pasa a mantenimiento en vez de volver a estar disponible

4. **Scenario**: El préstamo sigue abierto
   - **Given** la hora pactada de devolución ya pasó y nadie ha devuelto el "Microscopio 07"
   - **When** se consulta el estado del préstamo
   - **Then** el préstamo figura como pendiente de devolución y el recurso sigue sin estar disponible para otros

### Edge Cases

- **Devolución exactamente en la hora pactada**: el criterio debe ser explícito y siempre el mismo, para que devolver a las 16:00 en punto no se cuente unas veces como puntual y otras como retraso.
- **Devolución que nunca llega (umbral de pérdida de 7 días)**: Si transcurren 7 días calendario desde la fecha y hora pactadas de devolución sin que el recurso haya sido entregado:
  1. El préstamo se da por vencido de forma definitiva y se cierra con el estado `NO_DEVUELTO_PERDIDO`.
  2. El recurso se retira permanentemente de la oferta de reservas (baja lógica, no eliminación de la base de datos) y se le notifica al Módulo 1 para que actualice su estado patrimonial a `DADO_DE_BAJA`.
  3. Se escala el caso al Módulo 3 con el expediente completo (persona, recurso, placa de inventario y días de mora) para que aplique la sanción disciplinaria correspondiente e inicie el proceso administrativo de cobro por reposición.
- **Doble registro de la misma devolución**: registrarla dos veces no puede generar dos reportes ni dos cálculos de mora.
- **El Módulo 3 no responde**: la devolución se registra igual y el recurso se libera igual; el reporte queda pendiente y se reintenta hasta entregarse.
- **Devolución antes de la hora de inicio**: si alguien devuelve un recurso que nunca llegó a usar, se registra igual y no cuenta como retraso.
- **Espacios físicos (salones, auditorios, salas de estudio)**: Para los espacios físicos no se realiza un reporte físico de entrega o devolución. Su liberación ocurre automáticamente al cumplirse la hora de fin de la franja horaria pactada (mediante el ciclo de vida del recurso gestionado por el sistema). Por lo tanto, este caso de uso aplica de forma exclusiva a recursos muebles y equipos en préstamo físico (videobeams, microscopios, kits de dibujo, etc.).

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema DEBE permitir registrar la fecha y hora reales en que se devuelve un recurso prestado.
- **FR-002**: El sistema DEBE guardar, junto a cada préstamo, la fecha y hora pactadas de devolución y las reales.
- **FR-003**: El sistema DEBE reportar cada devolución al Módulo 3 indicando la persona, el recurso, la hora pactada, la hora real y la diferencia entre ambas.
- **FR-004**: El sistema NO DEBE calcular ni aplicar la sanción por mora; eso corresponde al Módulo 3.
- **FR-005**: Al registrarse la devolución, el recurso DEBE volver a estar disponible, salvo que se haya reportado una novedad que lo mande a mantenimiento.
- **FR-006**: El sistema DEBE permitir registrar una novedad junto a la devolución cuando el recurso vuelva dañado, e informarla al Módulo 3.
- **FR-007**: Una misma devolución NO DEBE reportarse más de una vez.
- **FR-008**: Si el Módulo 3 no está disponible, la devolución DEBE quedar registrada y el reporte DEBE reintentarse hasta entregarse.
- **FR-009**: El sistema DEBE mostrar como pendientes los préstamos cuya hora pactada ya pasó y que aún no han sido devueltos.
- **FR-010**: El sistema DEBE guardar quién registró la devolución y cuándo, para poder auditarla.

### Key Entities

- **Préstamo**: entrega de un recurso a una persona por un tiempo acordado. Atributos: persona, recurso, fecha y hora de entrega, fecha y hora pactadas de devolución, fecha y hora reales de devolución, estado.
- **Devolución**: hecho de que el recurso vuelve. Atributos: préstamo de origen, fecha y hora reales, quién la registró, novedad si la hubo, resultado del reporte al Módulo 3.
- **Novedad**: daño o incidencia detectada al recibir el recurso.
- **Recurso**: el equipo prestado que vuelve al inventario.
- **Usuario**: la persona responsable del préstamo.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: El 100 % de las devoluciones quedan reportadas al Módulo 3 con la hora pactada y la hora real.
- **SC-002**: Un recurso devuelto vuelve a aparecer como disponible en menos de 5 segundos.
- **SC-003**: Cero devoluciones reportadas dos veces sobre el mismo préstamo.
- **SC-004**: Cero préstamos vencidos que no aparezcan en la lista de pendientes de devolución.
- **SC-005**: Cero reportes perdidos ante una caída del Módulo 3 de hasta 30 minutos.
