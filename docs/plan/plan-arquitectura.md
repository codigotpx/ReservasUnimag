# Plan técnico general: arquitectura y organización

**Fecha**: 2026-09-19
**Specs**: [docs/specs/](../specs/) — índice en [spec-modulo2.md](../specs/spec-modulo2.md)
**Pendientes**: [pendientes-clarificacion.md](../specs/pendientes-clarificacion.md)

## Resumen

Se construye con **arquitectura hexagonal (puertos y adaptadores)** organizada en dos capas: `domain`, que guarda las entidades, las reglas de negocio, los puertos y los casos de uso, e `infrastructure`, que guarda todo lo tecnológico (REST, JPA, clientes HTTP, seguridad). La regla que no se rompe es que **`domain` no conoce a `infrastructure`**.

El repositorio contiene el backend (Spring Boot, en la raíz) y el frontend (Next.js, en `frontend/`). Los datos viven en **PostgreSQL**, elegido porque la regla central del proyecto —que dos ocupaciones de un mismo recurso no se crucen— se puede garantizar en la propia base con rangos de tiempo y restricciones de exclusión. La integración con el Módulo 3 sobre hechos ya ocurridos va por **Apache Kafka**: publicamos la ficha y la cancelación, y consumimos la no asistencia y el check-out. Del lado que publica hay una tabla de *outbox* y del lado que consume una de *inbox*, para que ningún evento se pierda ni se aplique dos veces.

Este documento llega hasta la organización de carpetas y el modelo de datos. Las tareas de implementación de cada caso de uso van en un plan por caso de uso, más adelante.

## Contexto técnico

| | |
|---|---|
| **Lenguaje backend** | Java 21 |
| **Framework backend** | Spring Boot 4.1.1: Web MVC, Data JPA, Security, Validation, RestClient |
| **Frontend** | Next.js (App Router) con React y TypeScript |
| **Base de datos** | PostgreSQL, con la extensión `btree_gist` |
| **Migraciones** | Flyway, con SQL versionado |
| **Mensajería** | Apache Kafka, con Spring for Apache Kafka (`spring-kafka`). Eventos en JSON, con la clave de partición por reserva |
| **Autenticación** | Login propio: usuarios en nuestra base, contraseñas con BCrypt y sesión por JWT |
| **Documentación de la API** | OpenAPI (springdoc) |
| **Pruebas** | JUnit 5, Testcontainers (PostgreSQL y Kafka), WireMock para los Módulos 1 y 3, ArchUnit para la regla de dependencias |
| **Build** | Gradle con Groovy DSL (backend) y npm (frontend) |
| **Zona horaria** | `America/Bogota`. Todas las fechas se guardan como `timestamptz`. |
| **Ventana de operación** | 06:00 a 22:00, sin franjas que crucen la medianoche |
| **Metas de rendimiento** | NEEDS CLARIFICATION: depende de P-17 (los tiempos de respuesta entre módulos no cuadran). |
| **Escala** | NEEDS CLARIFICATION: cuántos usuarios concurrentes y cuántos recursos. |

## Arquitectura

### Por qué hexagonal

El profesor trabaja con arquitectura hexagonal, y además encaja con cómo está hecho el Módulo 2:

- **Dependemos de dos sistemas que no controlamos.** El Módulo 1 (inventario) y el Módulo 3 (cumplimiento y sanciones) todavía tienen el contrato abierto (P-16, P-20). Con un puerto en `domain`, los casos de uso se programan y se prueban con un adaptador falso, y cuando el otro módulo entregue su API solo se escribe el adaptador real.
- **El Módulo 3 está en los dos lados del hexágono.** Nos avisa (no asistencia en UC9, check-out en UC12) y lo consultamos o le avisamos (sanciones en UC6, ficha en UC10, cancelación en UC11). En hexagonal son adaptadores distintos sobre el mismo dominio, y el que sean eventos de Kafka o llamadas REST no cambia el caso de uso.
- **Las caídas de otros módulos (P-10, P-14) se resuelven en el adaptador**, con timeouts, reintentos y respuestas por defecto, sin tocar el caso de uso.
- **Las reglas de negocio cambian mientras se cierran los pendientes**, y al vivir en Java puro se prueban en milisegundos, sin levantar Spring ni la base de datos.

### Convención de nombres

El código va **en inglés**: clases, atributos, métodos, puertos, paquetes, tablas, columnas, rutas HTTP y campos JSON. La documentación —esta y la de cada caso de uso— se queda en español, y los specs también.

Lo que **no** se traduce son los valores que los specs ya fijaron, porque son el vocabulario con el que el equipo y el profesor discuten el módulo: los estados (`DISPONIBLE`, `EN_USO`, `EN_MANTENIMIENTO`, `RESERVADO`, `BLOQUEO_ACADEMICO`), los estados de la reserva (`CONFIRMADA`, `CANCELADA_POR_PRIORIDAD_ACADEMICA`, …), el origen (`ESTUDIANTIL`, `ACADEMICO`), la categoría (`ESPACIO`, `ACTIVO`), los roles y los códigos del diccionario de errores (`RES-001` a `RES-004`, `CAN-001`, `CAN-002`). Traducirlos obligaría a mantener una tabla de equivalencias entre el código y los specs, y a que nadie supiera cuál de los dos manda.

Dos consecuencias prácticas:

- Una enumeración con nombre en inglés y valores en español es lo normal aquí: `ReservationStatus.CONFIRMADA`, `ResourceCategory.ESPACIO`. Es deliberado.
- La tabla de usuarios se llama **`app_user`** y no `user`, porque `user` es palabra reservada en PostgreSQL y obligaría a escribirla entre comillas en cada consulta.

### Capas y regla de dependencia

```text
   QUIÉN NOS LLAMA (entrada)                         A QUIÉN LLAMAMOS (salida)
   Frontend (REST)                 ─┐              ┌─→ PostgreSQL
                                    ├─→ [DOMAIN] ──┼─→ Módulo 1 (inventario, estado operativo: REST)
   Kafka (UC9, UC12 ← Módulo 3)    ─┘              ├─→ Módulo 3 (sanciones: REST síncrono)
                                                   └─→ Kafka (fichas y cancelaciones → Módulo 3)
```

| Capa | Contiene | Puede importar |
|---|---|---|
| `domain.model` | Entidades, objetos de valor y reglas: franja de máximo 2 horas, ventana de 06:00 a 22:00, vencimiento a las 22:00 del día hábil, cupo de 3, transiciones de estado de la reserva | Solo Java estándar |
| `domain.port.in` | Una interfaz por caso de uso: lo que la aplicación ofrece | `domain.model` |
| `domain.port.out` | Lo que la aplicación necesita del exterior: repositorios, Módulo 1, Módulo 3, reloj | `domain.model` |
| `domain.usecase` | La implementación de cada caso de uso, que orquesta el modelo usando los puertos de salida | `domain.*` |
| `infrastructure.adapter.in` | Controladores REST para el frontend y consumidores de Kafka para el Módulo 3 | `domain.port.in`, `domain.model` |
| `infrastructure.adapter.out` | Persistencia JPA y clientes HTTP; cada uno implementa un puerto de salida | `domain.port.out`, `domain.model` |
| `infrastructure.config` | Beans de Spring, seguridad, propiedades | Todo |

**Regla**: `domain` no importa nada de `infrastructure`, ni de Spring, JPA, Jackson o HTTP. Una prueba de ArchUnit lo verifica en cada build.

Decisiones que se derivan de la regla:

- **El dominio es puro.** Las entidades de `domain.model` no llevan `@Entity`. Las entidades JPA viven en `infrastructure.adapter.out.persistence`, y un mapper traduce entre las dos.
- **Los casos de uso no llevan `@Service`.** Se registran como beans en `infrastructure.config`, y las transacciones (`@Transactional`) se abren en esa misma capa, envolviendo el caso de uso.
- **La hora se inyecta con un puerto `Clock`.** Casi todas las reglas dependen de "ahora" (10 minutos de no-show, vencimiento a las 22:00, antelación de cancelación), y las pruebas necesitan controlarlo.
- **Los `<<include>>` del diagrama son llamadas entre casos de uso.** Por ejemplo, `ReserveResourcesUseCase` usa `CheckAvailability`, `CheckSanctions`, `UpdateResourceStatus` y `ReportReservation` a través de sus puertos de entrada.
- **El diccionario de errores es parte del dominio** (`DenialCode`). El adaptador web lo traduce a respuestas `ProblemDetail` (RFC 9457).

### Casos de uso y dónde viven

| UC | Caso de uso | Puerto de entrada | Lo dispara |
|---|---|---|---|
| UC1 | Consultar recursos | `SearchResourcesPort` | Frontend |
| UC2 | Reservar recursos | `ReserveResourcesPort` | Frontend, UC3 |
| UC3 | Importar horarios semestrales | `ImportSchedulePort` | Frontend (Dirección) |
| UC4 | Cancelar reserva | `CancelReservationPort` | Frontend, UC3 |
| UC6 | Consultar sanciones | `CheckSanctionsPort` | UC1, UC2 |
| UC7 | Actualizar estado de los recursos | `UpdateResourceStatusPort` | UC2, UC4, UC9, UC12 |
| UC8 | Consultar disponibilidad de los recursos | `CheckAvailabilityPort` | UC1, UC2 |
| UC9 | Recibir reporte de no asistencia | `ReceiveNoShowPort` | Módulo 3 |
| UC10 | Reportar información de la reserva | `ReportReservationPort` | UC2 |
| UC11 | Reportar cancelación de reserva | `ReportCancellationPort` | UC4 |
| UC12 | Recibir check-out | `ReceiveCheckOutPort` | Módulo 3 |
| — | Registrarse | `RegisterUserPort` | Frontend |
| — | Iniciar sesión | `SignInPort` | Frontend |

Registrarse e iniciar sesión no están en el diagrama de casos de uso. Aparecen porque el equipo decidió tener login propio con autorregistro, y necesitan su propio spec (ver [Pendientes](#pendientes-que-afectan-la-arquitectura)).

### Integraciones

| Dirección | Con quién | Cómo | Puerto |
|---|---|---|---|
| Salida | Módulo 1: catálogo y estado operativo | REST síncrono con RestClient, con timeout; la política ante caídas depende de P-14 | `InventoryPort` |
| Salida | Módulo 3: sanciones vigentes | REST síncrono con timeout; la política ante caídas depende de P-10 | `CompliancePort` |
| Salida | Módulo 3: ficha de reserva (UC10) y cancelación (UC11) | **Kafka**: el evento se guarda en la *outbox*, en la misma transacción que la reserva, y un publicador lo lleva al topic | `Module3NotifierPort` |
| Salida | Módulo 1: inicio de uso de un recurso (UC7) | **Kafka**, por la misma *outbox*. Es lo único que le mandamos: el resto de su estado operativo se lo reporta el Módulo 3 | `InventoryStatusNotifierPort` |
| Entrada | Módulo 3: no asistencia (UC9) y check-out (UC12) | **Kafka**: un consumidor por topic, con *inbox* para no procesar dos veces el mismo evento | `ReceiveNoShowPort`, `ReceiveCheckOutPort` |
| Entrada | Frontend | Endpoints REST bajo `/api/**`, autenticados con JWT | Los demás puertos de entrada |

La ficha y la cancelación son asíncronas por dos razones: no se pueden perder si el Módulo 3 está caído justo cuando alguien reserva o cancela, y reservar no debe quedarse esperando a otro módulo para responderle al estudiante. Eso es lo que resuelve Kafka.

La *outbox* se queda delante del broker. Escribir en PostgreSQL y publicar en Kafka no se pueden hacer en una sola transacción, así que el evento se inserta en `outbox_message` junto con la reserva y un publicador lo envía después del *commit*. Sin esa tabla, una caída entre el *commit* y el `send` perdería el evento, y un `send` antes de un *rollback* anunciaría una reserva que no existe. Además, las entidades `ReservationRecord` y `CancellationReport` de UC10 y UC11 ya piden guardar el resultado del envío.

### Mensajería con Kafka

Siete topics: cinco que publicamos y dos que consumimos. Lo único que sigue siendo REST hacia el Módulo 3 es la consulta de sanciones (UC6), porque el caso de uso necesita la respuesta antes de decidir si la reserva procede; y hacia el Módulo 1, el catálogo y el estado operativo, que son consultas.

| Topic | Evento | Caso de uso | Nosotros |
|---|---|---|---|
| `module2.reservation.record.v1` | `ReservationRecordCreated`, `LoanDeclaredLost` | UC10, UC12 | publicamos |
| `module2.reservation.cancellation.v1` | `ReservationCancelled` | UC11 | publicamos |
| `module2.reservation.no-show-ack.v1` | `NoShowReportAcknowledged` | UC9 | publicamos |
| `module2.reservation.check-out-ack.v1` | `CheckOutAcknowledged` | UC12 | publicamos |
| `module2.resource.status.v1` | `ResourceUseStarted` | UC7, hacia el **Módulo 1** | publicamos |
| `module3.reservation.no-show.v1` | `NoShowReported` | UC9 | consumimos |
| `module3.reservation.check-out.v1` | `CheckOutRegistered` | UC12 | consumimos |

Los cuatro primeros nacieron con este plan; los acuses y el del inicio de uso los añadieron los planes de UC9, UC12 y UC7, y están en [contratos-integracion.md](./contratos-integracion.md) con su JSON. Convención para los siete: el prefijo es el módulo dueño del evento, el sufijo `v1` es la versión del contrato, la clave de partición es el `reservation_id` y el valor es JSON con el envoltorio `eventId`, `type`, `version`, `occurredAt` y `data`. Los nombres y los campos definitivos se cierran con el Módulo 3 (ver [Pendientes](#pendientes-que-afectan-la-arquitectura)); mientras tanto, esta es la convención con la que trabajamos.

#### Lo que publicamos (UC7, UC9, UC10, UC11 y UC12)

- **La clave `reservation_id` da el orden que nos importa**: todos los eventos de una reserva caen en la misma partición, así que la cancelación nunca le llega al Módulo 3 antes que su ficha.
- **Nada de Avro ni *Schema Registry*.** Son unos pocos eventos entre tres equipos: el JSON y un documento de contrato compartido bastan, y evitan sumar otra pieza de infraestructura al proyecto.
- **Un cambio incompatible crea `...v2`** y los dos topics conviven mientras el otro módulo migra; añadir un campo opcional no cambia el nombre.
- **La entrega es *at-least-once*, y el `eventId` permite descartar el repetido.** Si el publicador cae después del `send` y antes de marcar `SENT`, el evento se republica. Por eso cada evento lleva el `eventId`, que es el mismo `outbox_message.id`: identifica el hecho, no el intento de envío.
- **Productor**: `acks=all` y `enable.idempotence=true`, para que un reintento del cliente no duplique ni reordene dentro de una partición.
- **El publicador** toma los `outbox_message` en `PENDING` cuyo `next_attempt_at` ya pasó, ordenados por `created_at`, publica y marca `SENT`. Corre con `@Scheduled` y los lee con `SELECT ... FOR UPDATE SKIP LOCKED`, para que dos instancias del backend no publiquen el mismo evento.
- **Reintentos con espera creciente.** Si el `send` falla, sube `intentos`, se guarda `last_error` y el registro sigue `PENDING` con un `next_attempt_at` más lejano (5 s, 30 s, 2 min, 10 min…). Al agotar `max-attempts` queda `FAILED` para revisión manual. No hace falta un *dead letter topic* del lado del productor: el evento nunca se pierde, sigue en la tabla.
- **Si Kafka está caído, el usuario no se entera.** Reservar y cancelar se confirman igual y los eventos salen cuando el broker vuelve. Es la diferencia con las llamadas síncronas al Módulo 1 y al Módulo 3, cuya política ante caídas todavía depende de P-10 y P-14.
- **Kafka no se filtra al dominio.** `Module3NotifierPort` sigue recibiendo objetos de `domain.model`; el `KafkaTemplate`, los nombres de los topics y la serialización viven en `infrastructure.adapter.out.mensajeria`, y ArchUnit lo verifica igual que con JPA y HTTP.

#### Lo que consumimos (UC9 y UC12)

- **Un `@KafkaListener` por topic**, en el grupo `module2-reservations`, que traduce el evento a la petición del puerto de entrada y llama a `ReceiveNoShowPort` o `ReceiveCheckOutPort`. El consumidor es un adaptador de entrada más, al mismo nivel que un controlador REST.
- ***Inbox* para la idempotencia.** Kafka entrega *at-least-once*, así que el mismo evento puede llegar dos veces. El `eventId` se inserta en `inbox_message` dentro de la misma transacción que ejecuta el caso de uso: si ya estaba, el evento se descarta sin volver a aplicarlo. Es lo que evita cerrar dos veces un check-out o contar dos ausencias por una reentrega.
- **El *offset* se confirma después del *commit*** (`AckMode.MANUAL`). Confirmarlo antes perdería el evento si la transacción falla; hacerlo después solo puede repetirlo, y de eso ya se encarga la *inbox*.
- **Errores recuperables**: el broker no responde, la base está caída, el Módulo 1 no contesta cuando UC12 consulta el recurso. El `DefaultErrorHandler` reintenta con espera creciente sin mover el *offset*; el consumidor se queda parado en ese evento, que es lo correcto, porque saltárselo rompería el orden de la reserva.
- **Errores no recuperables**: el JSON no se puede deserializar, falta un campo obligatorio, el `reservation_id` no existe o la reserva está en un estado que no admite el reporte. Estos no mejoran con reintentos, así que el evento va al *dead letter topic* `module2.dlt.<topic-de-origen>` con la causa en las cabeceras, se registra y el consumidor sigue. Del lado que publicamos no hace falta DLT porque el evento se queda en `outbox_message`; aquí sí, porque es la única copia que tenemos del evento que llegó.
- **Concurrencia igual al número de particiones del topic**, para no perder el orden por reserva dentro de una partición.
- **No exponemos endpoints de integración.** UC9 y UC12 entran solo por Kafka, así que `/api/integration/module3/**` y su API key ya no existen: quien autentica es el broker (SASL más TLS), con las credenciales en variables de entorno, no en `application.properties`.

#### Local y pruebas

En desarrollo el broker se levanta con Docker Compose, junto con PostgreSQL. Las pruebas del publicador y de los consumidores usan Testcontainers (`KafkaContainer`), y las de los consumidores comprueban además los tres caminos: evento nuevo, evento repetido que la *inbox* descarta y evento inválido que acaba en el DLT. Los casos de uso se siguen probando con un `Module3NotifierPort` falso, sin broker.

```properties
spring.kafka.bootstrap-servers=localhost:9092
spring.kafka.producer.acks=all
spring.kafka.producer.properties.enable.idempotence=true
spring.kafka.consumer.group-id=module2-reservations
spring.kafka.consumer.auto-offset-reset=earliest
spring.kafka.consumer.enable-auto-commit=false
spring.kafka.listener.ack-mode=manual

# publicamos
reservations.kafka.topic.record=module2.reservation.record.v1
reservations.kafka.topic.cancellation=module2.reservation.cancellation.v1
# consumimos
reservations.kafka.topic.no-show=module3.reservation.no-show.v1
reservations.kafka.topic.check-out=module3.reservation.check-out.v1

reservations.outbox.interval=5s
reservations.outbox.batch=50
reservations.outbox.max-attempts=10
```

### Seguridad

- **Autorregistro** con correo institucional. El rol por defecto es `ESTUDIANTE`.
- Contraseñas con **BCrypt**. El login devuelve un **JWT** firmado por el backend, con el id y el rol del usuario.
- El JWT viaja en una **cookie `httpOnly`**. El frontend llama al backend a través de un *rewrite* de Next.js (`/api/*` → Spring), para que todo quede en el mismo origen sin configurar CORS y el token no quede expuesto a JavaScript.
- **Roles**: `ESTUDIANTE`, `MONITOR` (hereda todo lo de `ESTUDIANTE`) y `DIRECCION_PROGRAMA`. La autorización por rol se aplica en el adaptador web; el caso de uso recibe el usuario ya identificado.
- **No hay API key ni endpoints de integración**: UC9 y UC12 entran por Kafka, y ahí quien autentica es el broker (SASL más TLS), con las credenciales en variables de entorno.
- Por eso `/api/**` queda entero detrás del JWT: todo lo que llega por HTTP es el frontend.

## Organización de carpetas

### Repositorio

```text
ReservasUnimag/
├── build.gradle, settings.gradle, gradlew   backend (Spring Boot), en la raíz
├── src/                                     código y pruebas del backend
├── frontend/                                frontend (Next.js), con su propio package.json
├── docs/                                    specs, planes y diagramas
├── README.md
└── CONTRIBUTING.md
```

El backend se queda en la raíz para no mover lo que ya existe ni romper el README y los comandos de Gradle.

### Backend

```text
src/main/java/edu/unimagdalena/reservasunimag/
├── ReservasUnimagApplication.java
│
├── domain/                              sin Spring, JPA ni HTTP
│   ├── model/
│   │   ├── reserva/                     Reservation, ReservationStatus, ReservationOrigin,
│   │   │                                TimeSlot, LoanPeriod, Loan
│   │   ├── bloqueo/                     AcademicData, BlockType, SemesterSchedule
│   │   ├── cumplimiento/                Absence, CheckOut, Verdict, Sanction,
│   │   │                                ComplianceReport
│   │   ├── recurso/                     ResourceRef, ResourceCategory (ESPACIO/ACTIVO),
│   │   │                                OperationalStatus, DisplayedStatus
│   │   ├── usuario/                     User, Role
│   │   └── error/                       DenialCode, ReservationDeniedException
│   ├── port/
│   │   ├── in/                          ReserveResourcesPort, CancelReservationPort, ...
│   │   └── out/                         ReservationRepositoryPort, ScheduleRepositoryPort,
│   │                                    UserRepositoryPort, InventoryPort,
│   │                                    CompliancePort, Module3NotifierPort,
│   │                                    PasswordHasherPort, Clock
│   └── usecase/                         ReserveResourcesUseCase, CancelReservationUseCase,
│                                        ImportScheduleUseCase, ...
│
└── infrastructure/
    ├── adapter/
    │   ├── in/
    │   │   ├── web/
    │   │   │   ├── recurso/             controladores y DTO de UC1 y UC8
    │   │   │   ├── reserva/             UC2 y UC4
    │   │   │   ├── horario/             UC3
    │   │   │   ├── auth/                registro e inicio de sesión
    │   │   │   └── error/               manejador global → ProblemDetail
    │   │   └── mensajeria/              consumidores Kafka de UC9 y UC12,
    │   │                                inbox y envío al DLT
    │   └── out/
    │       ├── persistence/
    │       │   ├── entity/              entidades JPA
    │       │   ├── repository/          interfaces Spring Data
    │       │   ├── mapper/              dominio ↔ JPA
    │       │   └── *Adapter.java        implementan los *RepositoryPort
    │       ├── modulo1/                 cliente RestClient → InventoryPort
    │       ├── modulo3/                 cliente RestClient → CompliancePort
    │       ├── mensajeria/              outbox: el adaptador que guarda el evento
    │       │                            (→ Module3NotifierPort) y el publicador
    │       │                            @Scheduled que lo lleva al topic
    │       └── security/                BCrypt → PasswordHasherPort, emisión de JWT
    └── config/                          beans de casos de uso, transacciones, seguridad,
                                         propiedades (cupo, ventana, plazos), Clock

src/main/resources/
├── application.properties
└── db/migration/                        V1__initial_schema.sql, V2__..., ...

src/test/java/edu/unimagdalena/reservasunimag/
├── domain/model/                        pruebas unitarias de las reglas
├── domain/usecase/                      casos de uso con puertos falsos
├── infrastructure/adapter/              @WebMvcTest, Testcontainers y WireMock
└── ArchitectureTest.java                ArchUnit: domain no depende de infrastructure
```

### Frontend

```text
frontend/
├── package.json, next.config.ts, tsconfig.json
└── src/
    ├── app/                             rutas (App Router)
    │   ├── (auth)/login/, registro/
    │   ├── (estudiante)/recursos/       UC1, UC8 y reservar (UC2)
    │   ├── (estudiante)/my-reservations/   ver y cancelar (UC4)
    │   └── (direccion)/horarios/        importar horarios (UC3)
    ├── components/                      componentes visuales reutilizables
    ├── features/                        lógica por funcionalidad: recursos, reservas,
    │                                    horarios, auth (hooks, formularios, tipos)
    └── lib/api/                         cliente HTTP hacia /api del backend
```

El frontend no tiene reglas de negocio: valida formularios por usabilidad, pero la decisión final la toma siempre el backend.

## Base de datos

### Por qué PostgreSQL

1. **Garantiza que no haya cruces.** Con rangos de tiempo (`tstzrange`) y una restricción de exclusión, la propia base impide que dos reservas confirmadas del mismo recurso se solapen, aunque dos estudiantes den clic al mismo tiempo. Sirve igual para una franja de 2 horas de un espacio y para un préstamo de varios días de un activo, que es el cruce "franja contra periodo" de UC1, UC2 y UC8.
2. **Transacciones ACID.** UC3 cancela reservas estudiantiles y crea bloqueos, todo o nada. UC2 cuenta el cupo e inserta la reserva en una sola operación. La *outbox* se escribe en la misma transacción que la reserva, que es justamente lo que Kafka no puede garantizar por sí solo.
3. **Fechas con zona horaria** (`timestamptz`), necesarias porque todas las reglas están en hora de Bogotá.
4. **Los datos son relacionales, con reglas fuertes.** Necesitamos restricciones, no un esquema flexible, así que una base NoSQL no aporta.
5. **Ya está en el stack**, y Testcontainers lo levanta en las pruebas con el mismo motor de producción.

Se usa **Flyway** en vez de dejar que Hibernate genere el esquema (`ddl-auto=validate`), porque la restricción de exclusión y la extensión `btree_gist` solo se pueden escribir en SQL.

### Qué guardamos y qué no

Según el reparto de estados que aclaró el profesor ([spec-modulo2.md](../specs/spec-modulo2.md)):

| Guardamos (el Módulo 2 es dueño) | No guardamos (solo referenciamos o consultamos) |
|---|---|
| Usuarios y credenciales (login propio) | **Recurso** y su estado operativo (`DISPONIBLE`, `EN_USO`, `EN_MANTENIMIENTO`): son del Módulo 1. Guardamos solo `resource_id` y la categoría, más la marca de cuándo empezó el uso, que es lo que le avisamos (UC7 FR-004). |
| Reservas y bloqueos académicos (`RESERVADO`, `BLOQUEO_ACADEMICO` y los estados de la reserva) | **Sanciones**: son del Módulo 3. Se consultan en cada reserva y no se copian. |
| Préstamos, ausencias, check-out, denegaciones, historial de cambios de estado | |
| Cargas de horario semestral | |
| Eventos pendientes hacia el Módulo 3 (*outbox*) y eventos ya procesados del Módulo 3 (*inbox*) | |

### Modelo

El diagrama entidad–relación completo de estas tablas está en [modelo-datos-der.md](./modelo-datos-der.md).

```text
usuario 1───* reserva *───(resource_id: Módulo 1)
                 │
                 ├── 0..1 prestamo           (si el recurso es un activo)
                 ├── 0..1 academic_block  (si el origen es ACADEMICO) *───1 semester_schedule
                 ├── 0..1 ausencia
                 ├── 0..1 check_out
                 └── 0..* status_change   (historial de cambios de estado, UC7)
usuario 1───* denegacion
outbox_message  (outbox → Kafka: al Módulo 3 la ficha, la cancelación y los acuses; al Módulo 1 el inicio de uso)
inbox_message  (inbox: eventos del Módulo 3 ya procesados)
```

**`app_user`**
| Columna | Tipo | Nota |
|---|---|---|
| id | uuid PK | |
| code | varchar, único | código estudiantil o institucional |
| name | varchar | |
| email | varchar, único | dominio institucional |
| password_hash | varchar | BCrypt |
| role | varchar | `ESTUDIANTE`, `MONITOR`, `DIRECCION_PROGRAMA` |
| active | boolean | |
| created_at | timestamptz | |

**`reservation`**: una sola tabla para espacios y activos, estudiantiles y académicas.
| Columna | Tipo | Nota |
|---|---|---|
| id | uuid PK | |
| user_id | uuid FK, nulo | nulo solo si el origen es `ACADEMICO` |
| resource_id | varchar | identificador del Módulo 1 |
| resource_category | varchar | `ESPACIO`, `ACTIVO` |
| origin | varchar | `ESTUDIANTIL`, `ACADEMICO` |
| status | varchar | `CONFIRMADA`, `CANCELADA`, `CANCELADA_POR_PRIORIDAD_ACADEMICA`, `CANCELADA_POR_RECURSO_NO_DISPONIBLE`, `FINALIZADA` |
| starts_at | timestamptz | inicio de la franja, o entrega del préstamo |
| ends_at | timestamptz | fin de la franja, o vencimiento del préstamo |
| occupancy | tstzrange, generada | `tstzrange(starts_at, ends_at, '[)')` |
| use_started_at | timestamptz, nulo | cuándo empezó el uso; es lo que se le avisa al Módulo 1 (UC7) |
| cancellation_reason | varchar, nulo | |
| cancelled_at | timestamptz, nulo | |
| created_at | timestamptz | |
| version | bigint | bloqueo optimista |

```sql
CREATE EXTENSION IF NOT EXISTS btree_gist;

ALTER TABLE reservation ADD CONSTRAINT reservation_no_overlap
  EXCLUDE USING gist (resource_id WITH =, occupancy WITH &&)
  WHERE (status = 'CONFIRMADA');

ALTER TABLE reservation ADD CONSTRAINT reservation_holder_by_origin
  CHECK ((origin = 'ACADEMICO') = (user_id IS NULL));
```

El bloqueo académico es una reserva con `origen = 'ACADEMICO'`, porque cada clase importada pasa por `Reservar recursos` (P-06). Así una sola restricción protege contra los cruces entre clases, reservas estudiantiles y préstamos. Cuando UC3 desplaza una reserva estudiantil, la cancela y crea el bloqueo en la misma transacción, y la restricción nunca ve los dos confirmados a la vez.

**`loan`**: el detalle de una reserva de activo.
| Columna | Tipo | Nota |
|---|---|---|
| reservation_id | uuid PK, FK | |
| term_business_days | int | según el tipo del activo (Módulo 1) |
| picked_up_at | timestamptz, nulo | cuándo se recogió |
| returned_at | timestamptz, nulo | llega por check-out (UC12) |

**`academic_block`**
| Columna | Tipo | Nota |
|---|---|---|
| reservation_id | uuid PK, FK | |
| semester_schedule_id | uuid FK, nulo | nulo si es una necesidad extraordinaria |
| type | varchar | `REGULAR`, `EXTRAORDINARIO` |
| course | varchar | |
| program | varchar | |
| instructor | varchar | |

**`semester_schedule`**
| Columna | Tipo | Nota |
|---|---|---|
| id | uuid PK | |
| academic_term | varchar | p. ej. `2026-2` |
| uploaded_by | uuid FK → usuario | Dirección de Programa |
| uploaded_at | timestamptz | |
| result | jsonb | resumen de la carga: creados, choques, rechazos |

**`absence`** (UC9)
| Columna | Tipo | Nota |
|---|---|---|
| id | uuid PK | |
| reservation_id | uuid FK, único | |
| reported_at | timestamptz | cuándo llegó el aviso del Módulo 3 |
| voided | boolean | anulación de un reporte enviado por error |

**`check_out`** (UC12)
| Columna | Tipo | Nota |
|---|---|---|
| id | uuid PK | |
| reservation_id | uuid FK, único | |
| occurred_at | timestamptz | devolución o revisión real |
| verdict | varchar, nulo | solo espacios: `NO_ISSUES`, `NEEDS_MAINTENANCE` |
| received_at | timestamptz | |

**`status_change`** (UC7): el historial de FR-009. Una fila por cambio de ocupación, con el motivo y, solo en el inicio de uso, el aviso que salió.
| Columna | Tipo | Nota |
|---|---|---|
| id | uuid PK | |
| resource_id | varchar | del Módulo 1 |
| resource_category | varchar | `ESPACIO`, `ACTIVO` |
| reservation_id | uuid FK, nulo | |
| occupancy_starts_at, occupancy_ends_at | timestamptz | la franja, o el periodo del préstamo |
| previous_status_module2 | varchar | solo lo que sabe el Módulo 2 |
| new_status | varchar | uno de los cinco estados |
| reason | varchar | `RESERVATION_CONFIRMED`, `ACADEMIC_BLOCK_CREATED`, `USE_STARTED`, `RESERVATION_CANCELLED`, `NO_SHOW_REGISTERED`, `CHECK_OUT_RECEIVED`, `LOAN_DECLARED_LOST` |
| changed_at | timestamptz | |
| notified_status | varchar | `NOT_SENT`, `PENDING`, `SENT`, `FAILED`; solo `USE_STARTED` nace `PENDING` |
| outbox_message_id | uuid FK, nulo | el aviso al Módulo 1, si lo hubo |

**`denial`** (UC2)
| Columna | Tipo | Nota |
|---|---|---|
| id | uuid PK | |
| user_id | uuid FK | |
| resource_id | varchar | |
| code | varchar | del diccionario de errores |
| occurred_at | timestamptz | |

**`outbox_message`** (*outbox* hacia Kafka: la ficha de UC10, la cancelación de UC11, los acuses de UC9 y UC12, y el inicio de uso de UC7)
| Columna | Tipo | Nota |
|---|---|---|
| id | uuid PK | también es el `eventId` del evento publicado |
| type | varchar | `RESERVATION_RECORD`, `CANCELLATION`, `NO_SHOW_ACK`, `CHECK_OUT_ACK`, `RESOURCE_USE_STARTED`; determina el topic y el destinatario |
| reservation_id | uuid FK | además es la clave de partición en Kafka |
| payload | jsonb | el evento serializado |
| status | varchar | `PENDING`, `SENT`, `FAILED` |
| attempts | int | |
| next_attempt_at | timestamptz | espera creciente tras un fallo |
| last_error | varchar, nulo | para diagnosticar los `FAILED` |
| created_at, sent_at | timestamptz | |

```sql
CREATE INDEX outbox_message_pending
  ON outbox_message (next_attempt_at, created_at)
  WHERE (status = 'PENDING');
```

**`inbox_message`** (*inbox* de UC9 y UC12, desde Kafka)
| Columna | Tipo | Nota |
|---|---|---|
| event_id | uuid PK | el `eventId` que envía el Módulo 3; la PK es la que descarta el repetido |
| topic | varchar | de qué topic llegó |
| type | varchar | `NO_SHOW`, `CHECK_OUT` |
| reservation_id | uuid FK, nulo | nulo si el evento se rechazó porque la reserva no existe |
| payload | jsonb | el evento tal como llegó, para poder reprocesar o auditar |
| processed_at | timestamptz | |

La inserción va en la misma transacción que el caso de uso. Si choca con la clave primaria, el evento ya se aplicó y el consumidor solo confirma el *offset*.

Los parámetros del negocio (cupo de 3, ventana de 06:00 a 22:00, 10 minutos de no-show, 2 horas por franja) van en `application.properties`, no en tablas, mientras nadie necesite cambiarlos en caliente.

## Pendientes que afectan la arquitectura

| Pendiente | Qué afecta |
|---|---|
| P-10, P-14 | Política de los adaptadores del Módulo 3 y del Módulo 1 cuando no responden: bloquear o permitir, y qué mostrar |
| P-16, P-20 | Contrato del `InventoryPort` con el Módulo 1 |
| P-17 | Timeouts de los clientes y metas de rendimiento |
| Kafka | Contrato de los topics con el Módulo 3, y del de inicio de uso con el Módulo 1: nombres y campos de cada evento. Lo de este documento es nuestra convención de partida |
| Kafka | Particiones y réplicas por topic: de ahí sale la concurrencia de los consumidores |
| P-08 | Qué pasa con un préstamo que nunca se devuelve: hoy la ocupación dura hasta el check-out |
| UC9 | En qué estado queda una reserva con ausencia. La lista de estados de UC2 no tiene uno propio: NEEDS CLARIFICATION |
| UC9, UC12 | Qué hacemos con un evento que no encaja con el estado de la reserva, por ejemplo un check-out de una reserva ya finalizada: hoy va al DLT |
| Registro | Spec de registro e inicio de sesión: dominio de correo permitido, datos obligatorios y cómo se asignan los roles `MONITOR` y `DIRECCION_PROGRAMA`, que no pueden salir del autorregistro |
| Escala | Usuarios concurrentes y volumen de recursos esperados |
