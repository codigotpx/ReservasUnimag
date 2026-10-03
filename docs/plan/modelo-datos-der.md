# Diagrama entidad–relación del Módulo 2

**Fecha**: 2026-10-02
**Fuente**: [plan-arquitectura.md § Base de datos](./plan-arquitectura.md#base-de-datos)

Este documento solo dibuja el modelo de datos ya definido en el plan de arquitectura. No agrega tablas ni columnas: si algo falta aquí, falta allá.

## Alcance

El Módulo 2 es dueño de los usuarios, las reservas (estudiantiles y académicas), los préstamos, los reportes de cumplimiento que recibe y las tablas de mensajería. **No** guarda el recurso ni su estado operativo, que son del Módulo 1, ni las sanciones, que son del Módulo 3. Por eso `recurso_id` aparece como un `varchar` sin tabla propia: es una referencia a una entidad externa, y en el diagrama se dibuja como `recurso_modulo1` con borde conceptual, no como una tabla de nuestro esquema.

## Diagrama

```mermaid
erDiagram
    usuario {
        uuid        id              PK
        varchar     codigo          UK "codigo estudiantil o institucional"
        varchar     nombre
        varchar     correo          UK "dominio institucional"
        varchar     contrasena_hash     "BCrypt"
        varchar     rol                 "ESTUDIANTE | MONITOR | DIRECCION_PROGRAMA"
        boolean     activo
        timestamptz creado_en
    }

    reserva {
        uuid        id                  PK
        uuid        usuario_id          FK "nulo solo si origen = ACADEMICO"
        varchar     recurso_id             "identificador del Modulo 1"
        varchar     categoria_recurso      "ESPACIO | ACTIVO"
        varchar     origen                 "ESTUDIANTIL | ACADEMICO"
        varchar     estado                 "CONFIRMADA | CANCELADA | CANCELADA_POR_PRIORIDAD_ACADEMICA | CANCELADA_POR_RECURSO_NO_DISPONIBLE | FINALIZADA"
        timestamptz inicio                 "inicio de la franja, o entrega del prestamo"
        timestamptz fin                    "fin de la franja, o vencimiento del prestamo"
        tstzrange   ocupacion              "generada: tstzrange(inicio, fin, [)"
        varchar     motivo_cancelacion     "nulo"
        timestamptz cancelada_en           "nulo"
        timestamptz creada_en
        bigint      version                "bloqueo optimista"
    }

    prestamo {
        uuid        reserva_id          PK "tambien FK"
        int         plazo_dias_habiles     "segun el tipo del activo (Modulo 1)"
        timestamptz entregado_en           "nulo: cuando se recogio"
        boolean     renovado               "una sola renovacion (UC2 FR-016)"
        timestamptz devuelto_en            "nulo: llega por check-out (UC12)"
    }

    bloqueo_academico {
        uuid        reserva_id              PK "tambien FK"
        uuid        horario_semestral_id    FK "nulo si es necesidad extraordinaria"
        varchar     tipo                       "REGULAR | EXTRAORDINARIO"
        varchar     asignatura
        varchar     programa
        varchar     docente
    }

    horario_semestral {
        uuid        id                  PK
        varchar     periodo_academico      "p. ej. 2026-2"
        uuid        cargado_por         FK "usuario de Direccion de Programa"
        timestamptz cargado_en
        jsonb       resultado              "resumen de la carga: creados, choques, rechazos"
    }

    ausencia {
        uuid        id              PK
        uuid        reserva_id      FK "unico"
        timestamptz reportada_en       "cuando llego el aviso del Modulo 3"
        boolean     anulada            "anulacion de un reporte enviado por error"
    }

    check_out {
        uuid        id              PK
        uuid        reserva_id      FK "unico"
        timestamptz ocurrido_en        "devolucion o revision real"
        varchar     dictamen           "nulo; solo espacios: SIN_NOVEDAD | REQUIERE_MANTENIMIENTO"
        timestamptz recibido_en
    }

    denegacion {
        uuid        id              PK
        uuid        usuario_id      FK
        varchar     recurso_id         "identificador del Modulo 1"
        varchar     codigo             "del diccionario de errores"
        timestamptz ocurrida_en
    }

    mensaje_saliente {
        uuid        id              PK "tambien es el eventoId publicado"
        varchar     tipo               "FICHA_RESERVA | CANCELACION; determina el topic"
        uuid        reserva_id      FK "clave de particion en Kafka"
        jsonb       payload            "el evento serializado"
        varchar     estado             "PENDIENTE | ENVIADO | FALLIDO"
        int         intentos
        timestamptz proximo_intento    "espera creciente tras un fallo"
        varchar     ultimo_error       "nulo; para diagnosticar los FALLIDO"
        timestamptz creado_en
        timestamptz enviado_en
    }

    mensaje_entrante {
        uuid        evento_id       PK "el eventoId del Modulo 3; la PK descarta el repetido"
        varchar     topic              "de que topic llego"
        varchar     tipo               "NO_ASISTENCIA | CHECK_OUT"
        uuid        reserva_id      FK "nulo si la reserva no existe"
        jsonb       payload            "el evento tal como llego"
        timestamptz procesado_en
    }

    recurso_modulo1 {
        varchar id                  PK "NO es tabla nuestra: vive en el Modulo 1"
        varchar categoria              "ESPACIO | ACTIVO"
        varchar estado_operativo       "DISPONIBLE | EN_USO | EN_MANTENIMIENTO"
    }

    usuario            |o--o{ reserva           : "es titular de"
    usuario            ||--o{ denegacion        : "acumula"
    usuario            ||--o{ horario_semestral : "carga"

    reserva            ||--o| prestamo          : "detalla si es ACTIVO"
    reserva            ||--o| bloqueo_academico : "detalla si es ACADEMICO"
    reserva            ||--o| ausencia          : "recibe (UC9)"
    reserva            ||--o| check_out         : "recibe (UC12)"
    reserva            ||--o{ mensaje_saliente  : "origina (UC10, UC11)"
    reserva            |o--o{ mensaje_entrante  : "referida por"

    horario_semestral  ||--o{ bloqueo_academico : "genera"

    recurso_modulo1    ||--o{ reserva           : "se reserva (por recurso_id, sin FK)"
    recurso_modulo1    ||--o{ denegacion        : "se deniega (por recurso_id, sin FK)"
```

## Cómo leer las relaciones

| Relación | Cardinalidad | Por qué |
|---|---|---|
| `usuario` → `reserva` | 0..1 a 0..* | La reserva académica no tiene titular: `usuario_id` es nulo cuando `origen = 'ACADEMICO'`, y la restricción `reserva_titular_segun_origen` lo obliga en los dos sentidos. |
| `reserva` → `prestamo` | 1 a 0..1 | Solo las reservas de activos tienen préstamo. La PK de `prestamo` es la propia `reserva_id`, así que no puede haber dos. |
| `reserva` → `bloqueo_academico` | 1 a 0..1 | Solo las de origen `ACADEMICO`. Mismo patrón: PK compartida con `reserva`. |
| `horario_semestral` → `bloqueo_academico` | 1 a 0..* | Una carga semestral genera muchos bloqueos; un bloqueo extraordinario no viene de ninguna carga, de ahí que `horario_semestral_id` admita nulo. |
| `reserva` → `ausencia` / `check_out` | 1 a 0..1 | El `reserva_id` es único en las dos tablas: un solo reporte por reserva. |
| `reserva` → `mensaje_saliente` | 1 a 0..* | Una reserva produce la ficha (UC10) y, si se cancela, el reporte de cancelación (UC11). |
| `reserva` → `mensaje_entrante` | 0..1 a 0..* | Varios eventos del Módulo 3 pueden referirse a la misma reserva, y `reserva_id` queda nulo si el evento se rechazó porque la reserva no existe. |
| `recurso_modulo1` → `reserva` / `denegacion` | conceptual | No hay clave foránea: el recurso vive en el Módulo 1 y nosotros guardamos solo su `recurso_id` y su categoría. La integridad se valida contra el `InventarioPort`, no contra la base. |

## Restricciones que el diagrama no puede dibujar

```sql
CREATE EXTENSION IF NOT EXISTS btree_gist;

ALTER TABLE reserva ADD CONSTRAINT reserva_sin_cruces
  EXCLUDE USING gist (recurso_id WITH =, ocupacion WITH &&)
  WHERE (estado = 'CONFIRMADA');

ALTER TABLE reserva ADD CONSTRAINT reserva_titular_segun_origen
  CHECK ((origen = 'ACADEMICO') = (usuario_id IS NULL));
```

```sql
CREATE INDEX mensaje_saliente_pendientes
  ON mensaje_saliente (proximo_intento, creado_en)
  WHERE (estado = 'PENDIENTE');
```

- **`reserva_sin_cruces`** es la regla central del modelo: la base impide que dos reservas confirmadas del mismo recurso se solapen, sin importar si son franjas de espacio, préstamos de varios días, reservas estudiantiles o bloqueos académicos. Es la razón por la que todo vive en una sola tabla `reserva` con una columna `ocupacion` de tipo rango.
- **`reserva_titular_segun_origen`** es la que hace coherente la cardinalidad 0..1 entre `usuario` y `reserva`.
- Los parámetros del negocio (cupo de 3, ventana de 06:00 a 22:00, 10 minutos de no-show, 2 horas por franja) **no son tablas**: van en `application.properties`.

## Pendientes que tocan el modelo

Los mismos del plan de arquitectura, acotados a lo que cambiaría este diagrama:

| Pendiente | Qué tabla afectaría |
|---|---|
| UC7 | Falta la tabla de historial de cambios de estado (`CambioDeEstado`); se diseña cuando se responda P-20. |
| UC9 | En qué estado queda una reserva con ausencia: la lista de `reserva.estado` no tiene uno propio. |
| P-08 | Un préstamo que nunca se devuelve: hoy `reserva.fin` y la ocupación duran hasta el check-out. |
| Registro | El spec de registro e inicio de sesión puede añadir columnas a `usuario` (dominio de correo permitido, datos obligatorios). |
