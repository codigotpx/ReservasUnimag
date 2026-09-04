# Feature Specification: Importar horarios semestrales

**Created**: 2026-08-24
**Módulo**: 2 — Operación de Reservas y Priorización Académica
**Caso de uso (diagrama)**: `Importar horarios semestrales`
**Prioridad global**: P2

## Contexto

La Dirección de Programa carga al sistema el horario de clases del semestre, y esas clases quedan apartadas automáticamente, de modo que ningún estudiante pueda reservar un salón a la hora en que hay clase.

Este es el caso de uso que le dice al sistema qué le pertenece a la actividad docente; los demás se apoyan en él. Cuando un estudiante consulta qué espacios hay libres, el sistema oculta los que tienen clase; si intenta reservar uno de ellos, se lo niega explicando que el recurso está reservado para actividad docente.

Además de la carga del semestre completo, la Dirección de Programa puede registrar una **necesidad extraordinaria**: un examen adicional, una jornada institucional, la visita de un par académico. Esas actividades pesan más que las reservas de estudiantes que ya estén confirmadas: el sistema las cancela, deja constancia del motivo y avisa a quienes las tenían.

### Glosario del documento

| Palabra | Qué significa aquí |
|---|---|
| **Recurso** | Cualquier espacio o equipo que se pueda apartar: salón, laboratorio, sala de estudio, auditorio, videobeam. |
| **Franja horaria** | Un día con una hora de inicio y una de fin. Por ejemplo: 10 de septiembre, de 14:00 a 16:00. |
| **Bloqueo académico** | Marca que pone el sistema sobre un recurso en una franja para indicar que allí hay clase o actividad docente. Mientras esté puesta, ningún estudiante puede apartar ese recurso en esa franja. El sistema la registra con el estado `BLOQUEO_ACADEMICO`. |
| **Reserva estudiantil** | Apartado hecho por un estudiante o monitor. Pesa menos que un bloqueo académico. Una reserva vigente deja el recurso en estado `RESERVADO`; solo pasa a `EN_USO` cuando llega la franja y la persona se presenta. |
| **Carga académica del semestre** | El archivo con todas las clases del periodo: qué asignatura, en qué salón, qué día y a qué hora. |
| **Necesidad extraordinaria** | Actividad docente que no estaba en el horario original y que se registra ya empezado el semestre. |
| **Reporte de importación** | Resumen que devuelve el sistema al terminar de leer el archivo: cuántas clases quedaron cargadas, cuántas filas se rechazaron y por qué. |
| **Cruce de horarios** | Dos franjas sobre el mismo recurso que comparten aunque sea un minuto. Basta ese cruce para que haya conflicto. |

**Actores**

| Actor | Tipo | Participación |
|---|---|---|
| Dirección de Programa | Primario | Carga el horario del semestre y registra las actividades extraordinarias. |
| Módulo 1 | Secundario | Recibe los avisos de qué recursos quedaron bloqueados y qué reservas se cancelaron, para informar a los estudiantes afectados. |

**Casos de uso relacionados**

- `Consultar recursos` — usa los bloqueos que aquí se crean para no mostrar como libre un salón que tiene clase; ver [spec-modulo2-uc1-consultar-recursos.md](./spec-modulo2-uc1-consultar-recursos.md)
- `Reservar recursos` — cuando un estudiante intenta apartar un recurso con clase, esa reserva se rechaza con el error `RES-001`: el recurso está reservado para actividad docente; ver [spec-modulo2-uc2-reservar-recursos.md](./spec-modulo2-uc2-reservar-recursos.md)
- `Cancelar reserva` — cuando una actividad extraordinaria desplaza reservas de estudiantes, esas reservas terminan cancelándose por esta vía, sin que el estudiante haya hecho nada; ver [spec-modulo2-uc4-cancelar-reserva.md](./spec-modulo2-uc4-cancelar-reserva.md)
- `Actualizar estado de los recursos` — marcar un bloqueo académico es un cambio de estado: el recurso queda bloqueado en esa franja y el Módulo 1 se entera, **sin que nadie tenga que pedirlo**; ver [spec-modulo2-uc7-actualizar-estado-recursos.md](./spec-modulo2-uc7-actualizar-estado-recursos.md)
- `Notificar estado de recursos al finalizar reserva` — cuando esta carga desplaza reservas de estudiantes, el cierre de cada una se le informa al Módulo 3; ver [spec-modulo2-uc5-notificar-estado-recursos.md](./spec-modulo2-uc5-notificar-estado-recursos.md)

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Cargar el horario del semestre y hacer valer la prioridad académica (Priority: P2)

Como Dirección de Programa, quiero cargar de una sola vez el archivo con las clases del semestre para que esos salones queden apartados automáticamente, y quiero poder registrar después actividades académicas imprevistas que se impongan sobre las reservas de estudiantes ya hechas, para que ninguna clase se quede sin espacio.

**Why this priority**: De aquí sale toda la prioridad académica del módulo: es lo que alimenta la validación de disponibilidad de `Consultar recursos` y el rechazo `RES-001` de `Reservar recursos`. Sin estos bloqueos, la consulta muestra como libres salones que tienen clase y el sistema permite reservas que después habría que deshacer a mano. Es P2 y no P1 porque en una primera versión los bloqueos se pueden cargar uno por uno; la carga masiva es lo que hace sostenible el proceso cuando son cientos de clases por semestre.

**Independent Test**: Se puede probar sola, sin depender del resto del módulo. Se carga un archivo de horarios de prueba y se revisa en el calendario de cada recurso que las franjas de clase quedaron en `BLOQUEO_ACADEMICO`. Aparte, se registra una actividad extraordinaria sobre un salón que ya tenía una reserva estudiantil y se verifica que esa reserva quedó cancelada con el motivo correcto.

**Acceptance Scenarios**:

*Cada escenario se lee así: **Given** es la situación de partida, **When** es lo que ocurre, y **Then** es lo que el sistema debe hacer.*

1. **Scenario**: La carga del semestre sale bien
   - **Given** la Dirección de Programa tiene el archivo con las clases del semestre 2026-2 y todas sus filas están correctas
   - **When** ejecuta la carga
   - **Then** el sistema crea un bloqueo académico por cada sesión de clase, informa cuántas quedaron cargadas y, desde ese momento, ningún estudiante puede reservar esos salones en esas franjas

2. **Scenario**: El archivo trae filas con errores
   - **Given** el archivo contiene filas que nombran un salón que no existe o que traen una hora mal escrita
   - **When** se ejecuta la carga
   - **Then** el sistema revisa el archivo completo y, si encuentra al menos una fila con error, no carga nada: devuelve un reporte que señala fila por fila cuál es el problema, para corregir el archivo y volver a intentarlo

3. **Scenario**: Una actividad académica imprevista desplaza una reserva de estudiante
   - **Given** el "Auditorio Menor" tiene una reserva estudiantil `CONFIRMADA` para el 2026-09-10, de 14:00 a 16:00
   - **When** la Dirección de Programa registra sobre ese mismo auditorio y esa misma franja una actividad académica extraordinaria
   - **Then** el sistema cancela la reserva del estudiante con el motivo `CANCELADA_POR_PRIORIDAD_ACADEMICA`, crea el bloqueo académico y deja listo el aviso para que el estudiante se entere

4. **Scenario**: Una clase nunca desplaza a otra clase
   - **Given** una franja ya está en `BLOQUEO_ACADEMICO`
   - **When** se intenta registrar otra actividad académica que se cruza con ella en el mismo recurso
   - **Then** el sistema avisa del choque y pide que lo resuelvan las personas responsables, sin cancelar por su cuenta el bloqueo que ya estaba

### Edge Cases

- **Carga tardía del horario**: si el horario se carga cuando los estudiantes ya hicieron decenas de reservas, el sistema debe mostrar primero cuántas y cuáles reservas se cancelarían, esperar la confirmación de la Dirección de Programa y luego aplicar todo junto: o se cancelan todas y se crean todos los bloqueos, o no se cambia nada. Nunca a medias.
- **Cambio de horario a mitad de semestre**: volver a cargar un horario que ya se había cargado debe actualizar lo que existe, no repetirlo. Una misma clase no puede quedar bloqueada dos veces sobre el mismo salón.
- **Recurso dado de baja**: si una fila del archivo nombra un recurso en `EN_MANTENIMIENTO`, el sistema debe reportarlo como conflicto y no bloquearlo en silencio, porque esa clase necesita otro espacio.
- **Archivo vacío o sin ninguna fila válida**: la carga debe terminar sin cambiar nada y decirlo con claridad. Nunca puede mostrar un mensaje de éxito cuando no cargó ninguna clase.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: La Dirección de Programa DEBE poder cargar el horario del semestre completo desde un archivo, en una sola operación.
- **FR-002**: El sistema DEBE crear automáticamente un `BLOQUEO_ACADEMICO` por cada sesión de clase que traiga el archivo.
- **FR-003**: El sistema DEBE revisar fila por fila el archivo y entregar un reporte con las clases cargadas, las filas rechazadas y el motivo de cada rechazo.
- **FR-004**: El sistema DEBE dar siempre preferencia a la actividad académica sobre las reservas de estudiantes.
- **FR-005**: Cuando se registre una actividad académica extraordinaria, el sistema DEBE cancelar las reservas estudiantiles que se crucen con ella, marcándolas con el motivo `CANCELADA_POR_PRIORIDAD_ACADEMICA`.
- **FR-006**: El sistema NO DEBE cancelar por su cuenta un `BLOQUEO_ACADEMICO` existente para dar paso a otro; ese choque lo resuelven las personas responsables.
- **FR-007**: Volver a cargar un horario ya cargado NO DEBE generar bloqueos repetidos de las mismas clases.
- **FR-008**: Antes de aplicar una carga o una actividad extraordinaria que afecte reservas ya confirmadas, el sistema DEBE mostrar cuántas y cuáles se cancelarían, y aplicar el cambio completo o no aplicarlo en absoluto.
- **FR-009**: El sistema DEBE guardar constancia de cada bloqueo académico creado, con quién lo creó y en qué fecha y hora.

### Key Entities

- **BloqueoAcadémico**: el apartado de mayor prioridad, que nace de la carga académica. Guarda de qué recurso se trata, en qué franja, para qué asignatura, de qué programa, con qué docente y si viene del horario regular o de una actividad extraordinaria.
- **HorarioSemestral**: el conjunto de clases cargadas para un periodo académico, junto con el resultado de esa carga.
- **Recurso**: el espacio o equipo sobre el que se aplica el bloqueo.
- **FranjaHoraria**: la fecha con hora de inicio y hora de fin; es lo que se compara para saber si dos cosas se cruzan.
- **Reserva**: el apartado que hizo un estudiante y que la prioridad académica puede desplazar.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: La carga del horario de un semestre completo termina en menos de 5 minutos e informa el motivo del 100 % de las filas rechazadas.
- **SC-002**: El 100 % de las clases cargadas quedan bloqueadas: ningún estudiante puede reservar esos recursos en esas franjas.
- **SC-003**: Volver a cargar el mismo horario deja cero bloqueos repetidos.
- **SC-004**: El 100 % de las reservas estudiantiles desplazadas por una actividad extraordinaria quedan canceladas con el motivo `CANCELADA_POR_PRIORIDAD_ACADEMICA` y con su aviso enviado.
