# Contratos de integración entre módulos

**Fecha**: 2026-10-05
**Fuente**: la sección **Contratos** de cada plan en [docs/plan/](.) y [plan-arquitectura.md](./plan-arquitectura.md)
**Pendientes**: [pendientes-clarificacion.md](../specs/pendientes-clarificacion.md)

## Para qué es este documento

Juntar en una sola hoja todo lo que el Módulo 2 **recibe**, **pide** y **manda**, para poder sentarse con los equipos del Módulo 1 y del Módulo 3 y cerrar los contratos. Cada fila apunta al plan donde está el JSON completo: **este documento no redefine nada**, solo lo índexa.

Regla de oro: el JSON de cada integración vive en la sección `## Contratos` de un único plan. Si cambia ahí, cambia en los *fixtures*, el OpenAPI y los tipos del frontend — no al revés.

## Resumen en una tabla

| UC | Caso de uso | Kafka | Pedimos a otro módulo (REST) | Mandamos a otro módulo |
|---|---|---|---|---|
| UC1 | Consultar recursos | — | Módulo 1 (catálogo), Módulo 3 (sanciones) | — |
| UC2 | Reservar recursos | publica | Módulo 1 (ficha del recurso), Módulo 3 (sanciones) | ficha de reserva |
| UC3 | Importar horarios semestrales | publica | Módulo 1 (estado por lote) | ficha + cancelaciones; el `EN_USO` de cada clase sale después, por UC7 |
| UC4 | Cancelar reserva | publica | — | cancelación |
| UC6 | Consultar sanciones | — | Módulo 3 (sanciones) | — |
| UC7 | Actualizar estado de recursos | publica | Módulo 1 (ficha del recurso y estado por lote, antes del inicio de uso) | inicio de uso (`EN_USO`), de una reserva o de una clase |
| UC8 | Consultar disponibilidad | — | Módulo 1 (estado por lote) | — |
| UC9 | Recibir reporte de no asistencia | **consume** + publica acuse | — | acuse de no asistencia |
| UC10 | Reportar información de la reserva | publica (es el dueño del envío) | — | ficha de reserva |
| UC11 | Reportar cancelación de reserva | publica (es el dueño del envío) | — | cancelación |
| UC12 | Recibir check-out | **consume** + publica acuse y pérdida | — | acuse de check-out, pérdida declarada |

Tres cosas que no cambian en ningún caso de uso:

- **Nadie de fuera nos llama por HTTP.** `/api/**` es solo el frontend, detrás del JWT. El Módulo 3 entra por Kafka y el Módulo 1 no entra.
- **Lo único síncrono hacia el Módulo 3 son las sanciones**, porque la reserva no puede decidir sin esa respuesta. Todo lo demás es evento.
- **Al Módulo 1 solo le mandamos el inicio de uso**, de una reserva o de una clase. `RESERVADO` y `BLOQUEO_ACADEMICO` no salen del módulo; el fin de uso de un espacio va dentro del mismo aviso, como `useEndsAt`, y el de un activo se lo reporta el Módulo 3 con la devolución, igual que el mantenimiento (UC7 FR-004a, FR-012).

## 1. Lo que pedimos por REST

### Al Módulo 1 — `InventoryPort`

Las tres operaciones son **nuestra propuesta** mientras P-16 siga abierto. Las llamadas entre módulos van sin autenticación: no entra en el alcance del proyecto.

| Operación | Quién la usa | Contrato | Nota |
|---|---|---|---|
| `GET /api/v1/resources` (catálogo paginado y filtrado) | UC1 | [UC1 §2.1](./plan-uc1-consultar-recursos.md#21-catálogo-filtrado--get-apiv1resources) | Trae ya el `operationalStatus`, así que UC1 no pide el estado aparte |
| `GET /api/v1/resources/{id}` (ficha de uno) | UC2, UC7, UC8 | [UC2 §4](./plan-uc2-reservar-recursos.md#4-módulo-1--ficha-de-un-recurso) | Mismo objeto que `content[]` del catálogo, servido de a uno. Es la puerta de toda consulta de **un** recurso: la disponibilidad individual de UC8, la revalidación de UC2 —una sola vez por petición— y la comprobación de UC7 antes de un inicio de uso |
| `POST /api/v1/resources/operational-status` (estado por lote) | UC3, UC7 | [UC1 §2.2](./plan-uc1-consultar-recursos.md#22-estado-operativo-por-lote--post-apiv1resourcesoperational-status) | `POST` por longitud de URL; no modifica nada. Solo lo usan los que preguntan por muchos recursos de una vez |

Lo que hay que cerrar con ellos:

- `operationalStatus` solo puede valer `DISPONIBLE`, `EN_USO` o `EN_MANTENIMIENTO`. Si llega `RESERVADO` o `BLOQUEO_ACADEMICO` lo tratamos como contrato roto, no como un estado más (P-20.4: hay que quitarlos de su spec).
- **`loanTermBusinessDays`** en cada activo. Sin ese campo, FR-012 de UC2 no se puede implementar y la reserva responde `503 INCOMPLETE_RESOURCE_DETAILS`. Valores hablados: Libro 7, Kit de dibujo 3, Videobeam 1, Microscopio 0 (`0` = uso en sitio, distinto de que falte).
- Si `EN_MANTENIMIENTO` trae **fechas** o es un estado sin horizonte (P-20.3). Hoy, sin fechas, un recurso en mantenimiento bloquea todas sus franjas futuras.
- Un `resourceId` que vuelva en `notFound` lo tratamos como **no comprobable**, nunca como disponible.

### Al Módulo 3 — `CompliancePort`

| Operación | Quién la usa | Contrato |
|---|---|---|
| `GET /api/v1/compliance/people/{code}` | UC1, UC2, UC6 | [UC1 §3.1](./plan-uc1-consultar-recursos.md#31-reporte-de-cumplimiento--get-apiv1compliancepeoplecode) + [UC6 §1](./plan-uc6-consultar-sanciones.md#1-la-petición-al-módulo-3) |

- Se pide por **código institucional** de una sola persona, nunca por lotes ni por correo, y el `code` sale del claim del JWT.
- Campos de cada sanción: `reason`, `startsOn`, `endsOn`, `scope` (`ESPACIOS` | `ACTIVOS` | `TODO`). Si falta `endsOn` la tratamos como **vigente**.
- Persona sin historial: `200` con el reporte vacío o `404`; las dos se leen igual, "sin sanciones".
- **No guardamos ni cacheamos el reporte.** Se consulta en cada operación.
- Diferencia deliberada: si no responde, **consultar** sigue (aviso `NOT_VERIFIED`) y **confirmar** se bloquea con `503`. Es la lectura conservadora de P-10 y hay que ratificarla.

## 2. Topics de Kafka

Convención de los cuatro originales y de los que nacieron después: prefijo del módulo dueño del evento, sufijo `vN` del contrato, clave de partición `reservation_id`, valor JSON con el envoltorio `eventId`, `type`, `version`, `occurredAt`, `data`.

### Lo que publicamos

| Topic | Evento | UC que lo emite | Contrato | Estado |
|---|---|---|---|---|
| `module2.reservation.record.v1` | `ReservationRecordCreated` | UC2, UC3 (bloqueos) · envío de UC10 | [UC2 §6](./plan-uc2-reservar-recursos.md#6-evento-hacia-el-módulo-3--module2reservationrecordv1) | acordado en el plan general |
| `module2.reservation.record.v1` | `LoanDeclaredLost` | UC12 (umbral de 7 días) | [UC12 §2](./plan-uc12-recibir-check-out.md#2-evento-que-publicamos--el-acuse) | **por acordar** |
| `module2.reservation.cancellation.v1` | `ReservationCancelled` | UC3, UC4 · envío de UC11 | [UC3 §7](./plan-uc3-importar-horarios-semestrales.md#7-evento-de-cancelación--module2reservationcancellationv1) + [UC4 §3](./plan-uc4-cancelar-reserva.md#3-evento-hacia-el-módulo-3) | acordado en el plan general |
| `module2.reservation.no-show-ack.v1` | `NoShowReportAcknowledged` | UC9 | [UC9 §2](./plan-uc9-recibir-reporte-no-asistencia.md#2-evento-que-publicamos--module2reservationno-show-ackv1) | **por acordar** |
| `module2.reservation.check-out-ack.v1` | `CheckOutAcknowledged` | UC12 | [UC12 §2](./plan-uc12-recibir-check-out.md#2-evento-que-publicamos--el-acuse) | **por acordar** |
| `module2.resource.status.v1` | `ResourceUseStarted` | UC7 (reservas y clases), hacia el **Módulo 1** | [UC7 §4](./plan-uc7-actualizar-estado-recursos.md#4-el-aviso-al-módulo-1--module2resourcestatusv1) | **por acordar** |

Todos salen por la **outbox**: el evento se inserta en `outbox_message` en la misma transacción que la reserva y un publicador lo lleva al topic después del *commit*. Consecuencias para el otro lado:

- Entrega **at-least-once**: el Módulo 3 tiene que **descartar por `eventId`**, y el Módulo 1 también con el aviso de inicio de uso. Sin eso, FR-005 de UC10, FR-007 de UC11 y FR-008 de UC7 no se cumplen.
- El orden dentro de una misma reserva está garantizado por la clave de partición; **entre reservas distintas no**, así que para ordenar hay que usar `cancelledAt` / `occurredAt` del `data`.
- Si Kafka está caído, reservar y cancelar se confirman igual y los eventos salen después. El usuario no se entera.

### Lo que consumimos

| Topic | Evento | UC | Contrato |
|---|---|---|---|
| `module3.reservation.no-show.v1` | `NoShowReported`, `NoShowReportVoided` | UC9 | [UC9 §1](./plan-uc9-recibir-reporte-no-asistencia.md#1-evento-que-consumimos--module3reservationno-showv1) |
| `module3.reservation.check-out.v1` | `CheckOutRegistered` | UC12 | [UC12 §1](./plan-uc12-recibir-check-out.md#1-evento-que-consumimos--module3reservationcheck-outv1) |

- **Inbox** por `eventId` para la idempotencia, y *offset* confirmado después del *commit*.
- Lo que no se puede deserializar, le falta un campo o apunta a una reserva inexistente va al DLT `module2.dlt.<topic-de-origen>`. Un **rechazo de negocio no va al DLT**: se contesta por el topic de acuse.
- La anulación de un reporte de no asistencia va por **el mismo topic** que el reporte, para que la clave de partición garantice que no lo adelante.
- `verifiedAt` (UC9) y `data.occurredAt` (UC12) son los instantes de negocio, distintos del `occurredAt` del envoltorio. Las reglas de plazo se miden contra ellos.
- **No aceptamos ningún campo de daños** en el check-out: eso va del Módulo 3 al Módulo 1 directamente.

## 3. Lo que hay que acordar con cada equipo

### Con el Módulo 1

| # | Qué | Pendiente | Sin respuesta |
|---|---|---|---|
| 1 | Contrato de las tres operaciones REST | P-16 | UC1, UC2, UC3 y UC8 se escriben contra nuestra propuesta |
| 2 | `loanTermBusinessDays` por tipo de activo | P-16 | UC2 FR-012 no se puede implementar |
| 3 | ¿`EN_MANTENIMIENTO` trae fechas? | P-20.3 | UC1, UC3, UC7 y UC8 bloquean todo el futuro del recurso |
| 4 | Quitar `RESERVADO` y `BLOQUEO_ACADEMICO` de su spec | P-20.4 | el reparto de estados queda ambiguo y UC7 FR-003 mal redactado |
| 5 | ¿Nos tiene que llegar algo cuando ellos ponen un recurso en mantenimiento? | P-20.2 | UC4 FR-010 no tiene disparador y el endpoint manual de UC4 §2 se queda |
| 6 | **Aceptar el aviso de inicio de uso**: topic `module2.resource.status.v1`, envoltorio y deduplicación por `eventId` | P-20.1 (lo que queda) | publicamos contra nuestra propuesta y nadie lo consume; UC7 FR-004 no llega a destino |
| 7 | Que **cierren el uso de un espacio con `useEndsAt`**, que va en el mismo evento, en vez de esperar un segundo aviso de fin | P-20.1 (lo que queda) | su `EN_USO` se queda pegado, sobre todo con las clases, que empiezan todos los días y nadie les hace check-out |
| 8 | Tiempos de respuesta y timeouts | P-17 | SC-001 de UC1 y UC8 no se puede verificar |

### Con el Módulo 3

| # | Qué | Dónde | Sin respuesta |
|---|---|---|---|
| 1 | **Los tres topics nuevos**: `no-show-ack`, `check-out-ack` y `LoanDeclaredLost` | UC9 §2, UC12 §2 | respondemos a un topic que nadie consume; UC9 FR-002/FR-011 y UC12 FR-005 quedan a medias |
| 2 | Que **deduplican por `eventId`** | UC10 notas | UC10 FR-005 y UC11 FR-007 no se cumplen |
| 3 | Que **ordenan por `cancelledAt`** y no por llegada | UC11 notas | el edge case de dos cancelaciones seguidas falla |
| 4 | Si el Módulo 3 no responde, ¿bloqueamos la reserva o la permitimos? | P-10 | hoy bloqueamos con `503` (conservador) |
| 5 | ¿La sanción distingue espacios de activos (`scope`)? | P-11 | leemos `scope` pero no filtra: cualquier sanción bloquea cualquier reserva |
| 6 | ¿Premian la cancelación a tiempo? | P-13 | `noticeMinutes` va sin precisión ni redondeo definidos |
| 7 | ¿Van a revisar **todos** los espacios o es opcional? | UC12 notas | la lista de FR-015 crece sin límite |
| 8 | ¿Esperan la cancelación cuando se **retira** una clase del horario? | P-19 | la emitimos por si acaso |
| 9 | Umbral de ausencias que origina sanción | P-01 | no nos afecta: la decisión es suya |
| 10 | Particiones y réplicas de cada topic | plan general | de ahí sale la concurrencia de nuestros consumidores |

## 4. Incoherencias nuestras que hay que arreglar antes de mostrar esto

| # | Qué pasa | Dónde | Arreglo |
|---|---|---|---|
| 1 | ~~El plan general dice **cuatro topics** y los planes definen **siete**~~ | [plan-arquitectura.md](./plan-arquitectura.md#mensajería-con-kafka) | **arreglado**: la tabla del plan general ya lista los siete, con los acuses y el del inicio de uso |
| 2 | `LoanDeclaredLost` viaja por el topic de la **ficha**, que es de otro hecho | UC12 §2 | decidir si va ahí o nace `module2.loan.lost.v1` |
| 3 | `CAN-001` y `CAN-002` están **al revés** en el diccionario consolidado respecto a UC4 y UC11 | [spec-modulo2.md](../specs/spec-modulo2.md#diccionario-de-errores-consolidado) | corregir el consolidado; hoy `CAN-002` no se emite |
| 4 | No hay código de error para `EN_MANTENIMIENTO` | UC2 §1 | crear `RES-005`; hoy va `RES-004` con `detectedStatus` para poder migrar |
| 5 | Falta el spec de **registro e inicio de sesión** | plan general | dominio de correo, datos obligatorios y cómo se asignan `MONITOR` y `DIRECCION_PROGRAMA` |
| 6 | Falta el **calendario de festivos** de la universidad | UC2, UC3 | hoy es una lista a mano en `application.properties` |
| 7 | Nadie fijó cuándo se **purga** la outbox | UC10, UC11 | los historiales propios existen para no depender de eso |
| 8 | Metas de rendimiento y escala sin número | plan general | depende de P-17 |
| 9 | ~~UC1 §2.2 dice que la operación por lote la usa "la revalidación de UC2", pero el plan de UC2 revalida con la **ficha individual**~~ | UC1 §2.2, UC2 § Decisiones, UC8 § Decisiones | **arreglado**: la ficha es la puerta de toda consulta de un recurso y el lote queda para UC3 y UC7. UC2 la pide una sola vez por petición y le pasa el estado al cruce de disponibilidad |
| 10 | `USO-001` y `USO-002`, del inicio de uso, no están en el diccionario consolidado | [UC7 §3](./plan-uc7-actualizar-estado-recursos.md#3-post-apireservationsreservationidstart-use) vs. [spec-modulo2.md](../specs/spec-modulo2.md#diccionario-de-errores-consolidado) | añadirlos, junto con `RES-005` |

## 5. Pendientes por plan

Lo que cada plan deja explícitamente abierto, para saber qué se puede empezar y qué no.

| Plan | Bloquea implementar | Decisión nuestra o del profesor |
|---|---|---|
| [UC1](./plan-uc1-consultar-recursos.md) | P-16 (contrato y auth del Módulo 1) | P-14, P-10, P-17, P-20.3 |
| [UC2](./plan-uc2-reservar-recursos.md) | **P-16 `loanTermBusinessDays`**, calendario de festivos | P-10, P-11, P-19, P-02, P-08, `RES-005` |
| [UC3](./plan-uc3-importar-horarios-semestrales.md) | formato del archivo (se asume CSV semanal), calendario de festivos | P-19 ×3, P-20.3, P-20.5, vigencia de 30 min de la carga validada |
| [UC4](./plan-uc4-cancelar-reserva.md) | P-20.2 (mantenimiento sin disparador) | contradicción `CAN-001`/`CAN-002`, sanción retroactiva (P-03), P-13 |
| [UC6](./plan-uc6-consultar-sanciones.md) | — | P-11, P-10, formato de la vigencia (fechas vs. instantes) |
| [UC7](./plan-uc7-actualizar-estado-recursos.md) | **P-02** (por dónde entra el registro del inicio de uso; hoy, un `POST` provisional del titular) | P-20.1 (el contrato del topic), P-20.4, P-20.3, `USO-001` y `USO-002` en el diccionario |
| [UC8](./plan-uc8-consultar-disponibilidad-recursos.md) | — | P-20.3, P-16, P-17, redacción del último edge case (`400` vs. "no disponible") |
| [UC9](./plan-uc9-recibir-reporte-no-asistencia.md) | **topic de acuse**, particiones del topic | FR-010 (canal de aviso a la persona), edge case del recurso caído, P-01 |
| [UC10](./plan-uc10-reportar-informacion-reserva.md) | **deduplicación por `eventId`** en el Módulo 3 | purga de la outbox |
| [UC11](./plan-uc11-reportar-cancelacion-reserva.md) | **orden por `cancelledAt`** en el Módulo 3 | P-13, cuarto origen de cancelación (P-03), purga de la outbox |
| [UC12](./plan-uc12-recibir-check-out.md) | **topics de acuse y de pérdida** | el aviso de baja de un recurso perdido no se manda (UC7 FR-012) y ampliarlo es P-20.1, P-20.2 (`REQUIERE_MANTENIMIENTO` no hace nada), umbral de 7 días calendario, revisión de espacios opcional |

**Propuestas nuestras que cierran un pendiente** y solo necesitan un sí:

- Una reserva con ausencia queda **`FINALIZADA`**, y la fila de `absence` explica por qué (UC9). Cierra el `NEEDS CLARIFICATION` del plan general sin inventar un estado nuevo.
- Un préstamo no devuelto se declara perdido a los **7 días calendario** de la fecha de vencimiento (UC12 Phase 4, desactivable). Cierra P-08.
- El aviso de inicio de uso de UC7 va por **outbox y Kafka**, nunca por REST síncrono (FR-007, SC-005), y lleva un solo `newStatus` posible, `EN_USO` (FR-012). El fin viaja dentro, en `useEndsAt`: con fecha en un espacio y nulo en un activo, para que el Módulo 1 libere lo uno y mantenga lo otro sin un segundo evento (FR-004a).
- El inicio de uso lo registra el titular desde la app con `POST /api/reservations/{id}/start-use` ([UC7 §3](./plan-uc7-actualizar-estado-recursos.md#3-post-apireservationsreservationidstart-use)), con la misma ventana de 10 minutos de UC9 y UC4. Cierra P-02 con lo único que podemos construir solos; cualquier otro medio llama al mismo puerto.

## Lo que no tiene plan

- **Spec 13, cancelar por mantenimiento de un espacio**: no tiene plan propio porque lo implementa UC4 con `POST /api/resources/{resourceId}/cancel-reservations` ([UC4 §2](./plan-uc4-cancelar-reserva.md#2-post-apiresourcesresourceidcancel-reservations)). Ese endpoint existe **solo mientras nadie nos avise del cambio de estado** (P-20.2).
- **Registrarse e iniciar sesión**: hay un login mínimo en [UC1 §4](./plan-uc1-consultar-recursos.md#4-login-mínimo-t013) para poder probar y demostrar, pero el spec sigue pendiente.
- No existe ningún UC5 en el diagrama vigente: la numeración salta de UC4 a UC6.
