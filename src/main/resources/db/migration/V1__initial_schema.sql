-- Esquema inicial: usuarios, reservas (espacios, activos y bloqueos académicos), préstamos y cargas de horario.
-- Modelo en docs/plan/plan-arquitectura.md#modelo y docs/plan/modelo-datos-der.md.

CREATE EXTENSION IF NOT EXISTS btree_gist;

CREATE TABLE app_user (
    id            uuid         PRIMARY KEY,
    code          varchar(32)  NOT NULL UNIQUE,
    name          varchar(200) NOT NULL,
    email         varchar(254) NOT NULL UNIQUE,
    password_hash varchar(100) NOT NULL,
    role          varchar(32)  NOT NULL
        CONSTRAINT app_user_role CHECK (role IN ('ESTUDIANTE', 'MONITOR', 'DIRECCION_PROGRAMA')),
    active        boolean      NOT NULL DEFAULT true,
    created_at    timestamptz  NOT NULL DEFAULT now()
);

-- Una sola tabla para espacios y activos, estudiantiles y académicas.
CREATE TABLE reservation (
    id                  uuid        PRIMARY KEY,
    user_id             uuid        REFERENCES app_user (id),
    resource_id         varchar(64) NOT NULL,
    resource_category   varchar(16) NOT NULL
        CONSTRAINT reservation_category CHECK (resource_category IN ('ESPACIO', 'ACTIVO')),
    origin              varchar(16) NOT NULL
        CONSTRAINT reservation_origin CHECK (origin IN ('ESTUDIANTIL', 'ACADEMICO')),
    status              varchar(48) NOT NULL
        CONSTRAINT reservation_status CHECK (status IN (
            'CONFIRMADA', 'CANCELADA', 'CANCELADA_POR_PRIORIDAD_ACADEMICA',
            'CANCELADA_POR_RECURSO_NO_DISPONIBLE', 'FINALIZADA')),
    starts_at           timestamptz NOT NULL,
    ends_at             timestamptz NOT NULL,
    occupancy           tstzrange   GENERATED ALWAYS AS (tstzrange(starts_at, ends_at, '[)')) STORED,
    use_started_at      timestamptz,
    cancellation_reason varchar(64),
    cancelled_at        timestamptz,
    created_at          timestamptz NOT NULL DEFAULT now(),
    version             bigint      NOT NULL DEFAULT 0,
    CONSTRAINT reservation_ends_after_start CHECK (ends_at > starts_at),
    -- Una reserva estudiantil siempre tiene titular; un bloqueo académico nunca.
    CONSTRAINT reservation_holder_by_origin CHECK ((origin = 'ACADEMICO') = (user_id IS NULL)),
    -- Dos ocupaciones confirmadas de un mismo recurso no se cruzan, aunque lleguen al mismo tiempo.
    CONSTRAINT reservation_no_overlap
        EXCLUDE USING gist (resource_id WITH =, occupancy WITH &&) WHERE (status = 'CONFIRMADA')
);

CREATE INDEX reservation_user ON reservation (user_id) WHERE user_id IS NOT NULL;

-- Detalle de una reserva de activo.
CREATE TABLE loan (
    reservation_id     uuid        PRIMARY KEY REFERENCES reservation (id),
    term_business_days int         NOT NULL CHECK (term_business_days > 0),
    picked_up_at       timestamptz,
    returned_at        timestamptz
);

-- Préstamos entregados y sin devolver: siguen ocupando aunque haya pasado su vencimiento (UC2 FR-015).
CREATE INDEX loan_outstanding ON loan (reservation_id)
    WHERE picked_up_at IS NOT NULL AND returned_at IS NULL;

CREATE TABLE semester_schedule (
    id            uuid        PRIMARY KEY,
    academic_term varchar(16) NOT NULL,
    uploaded_by   uuid        NOT NULL REFERENCES app_user (id),
    uploaded_at   timestamptz NOT NULL DEFAULT now(),
    result        jsonb
);

CREATE TABLE academic_block (
    reservation_id       uuid         PRIMARY KEY REFERENCES reservation (id),
    semester_schedule_id uuid         REFERENCES semester_schedule (id),
    type                 varchar(16)  NOT NULL
        CONSTRAINT academic_block_type CHECK (type IN ('REGULAR', 'EXTRAORDINARIO')),
    course               varchar(200) NOT NULL,
    program              varchar(200) NOT NULL,
    instructor           varchar(200) NOT NULL,
    -- Solo una necesidad extraordinaria llega sin carga de horario.
    CONSTRAINT academic_block_schedule_by_type
        CHECK ((type = 'EXTRAORDINARIO') = (semester_schedule_id IS NULL))
);
