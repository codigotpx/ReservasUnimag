-- UC10: la constancia de cada ficha que se le reporta al Módulo 3 y de cómo acabó su envío. Es el gemelo de
-- cancellation_report (UC11): outbox_message se puede purgar; esta tabla no (FR-006).
-- Modelo en docs/plan/plan-uc10-reportar-informacion-reserva.md#4-la-tabla-reservation_record.

-- La clave primaria es la reserva: la base impide dos fichas de la misma reserva (FR-005, SC-003). Los datos de la
-- clase no se copian: salen de academic_block por la misma reserva.
CREATE TABLE reservation_record (
    reservation_id          uuid        PRIMARY KEY REFERENCES reservation (id),
    -- El id de outbox_message y el eventId del evento. Sin FK: la outbox se purga y el historial se queda.
    event_id                uuid        NOT NULL UNIQUE,
    origin                  varchar(16) NOT NULL
        CONSTRAINT reservation_record_origin CHECK (origin IN ('ESTUDIANTIL', 'ACADEMICO')),
    sanctionable            boolean     NOT NULL,
    holder_user_id          uuid        REFERENCES app_user (id),
    resource_id             varchar(64) NOT NULL,
    resource_category       varchar(16) NOT NULL
        CONSTRAINT reservation_record_category CHECK (resource_category IN ('ESPACIO', 'ACTIVO')),
    occupancy_starts_at     timestamptz NOT NULL,
    occupancy_ends_at       timestamptz NOT NULL,
    loan_term_business_days int,
    sent_at                 timestamptz NOT NULL,
    delivery_status         varchar(8)  NOT NULL DEFAULT 'PENDING'
        CONSTRAINT reservation_record_delivery CHECK (delivery_status IN ('PENDING', 'SENT', 'FAILED')),
    attempts                int         NOT NULL DEFAULT 0,
    first_attempt_at        timestamptz,
    delivered_at            timestamptz,
    last_error              varchar(500),
    -- Una ficha académica no tiene titular y nunca es sancionable (FR-008, SC-006).
    CONSTRAINT reservation_record_holder_only_student
        CHECK ((origin = 'ESTUDIANTIL') = (holder_user_id IS NOT NULL)),
    CONSTRAINT reservation_record_sanctionable
        CHECK (sanctionable = (origin = 'ESTUDIANTIL'))
);

CREATE INDEX reservation_record_delivery ON reservation_record (delivery_status, sent_at);
CREATE INDEX reservation_record_origin ON reservation_record (origin, sent_at);
