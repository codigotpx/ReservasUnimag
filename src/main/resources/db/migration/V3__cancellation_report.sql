-- UC4 y UC11: la constancia de cada cancelación que se le reporta al Módulo 3, y el candado que impide que una
-- reserva quede a la vez cancelada y ausente.
-- Modelo en docs/plan/plan-uc11-reportar-cancelacion-reserva.md#4-la-tabla-cancellation_report.

-- Una reserva se cierra una sola vez: por cancelación (UC4) o por ausencia (UC9). Quien llega segundo choca con la
-- clave primaria (UC11 FR-011, SC-006).
CREATE TABLE reservation_closure (
    reservation_id uuid        PRIMARY KEY REFERENCES reservation (id),
    closure_type   varchar(16) NOT NULL
        CONSTRAINT reservation_closure_type CHECK (closure_type IN ('CANCELLATION', 'ABSENCE')),
    created_at     timestamptz NOT NULL DEFAULT now()
);

-- Historial permanente de lo reportado. outbox_message se puede purgar; esta tabla no (UC11 FR-010).
-- La clave primaria es la reserva: la base impide reportar dos veces la misma cancelación (UC11 FR-007, SC-003).
CREATE TABLE cancellation_report (
    reservation_id         uuid         PRIMARY KEY REFERENCES reservation (id),
    -- El id de outbox_message y el eventId del evento. Sin FK: la outbox se purga y el historial se queda.
    event_id               uuid         NOT NULL UNIQUE,
    cancellation_origin    varchar(32)  NOT NULL
        CONSTRAINT cancellation_report_origin CHECK (cancellation_origin IN (
            'HOLDER', 'ACADEMIC_PRIORITY', 'RESOURCE_UNAVAILABLE')),
    reservation_status     varchar(48)  NOT NULL
        CONSTRAINT cancellation_report_status CHECK (reservation_status IN (
            'CANCELADA', 'CANCELADA_POR_PRIORIDAD_ACADEMICA', 'CANCELADA_POR_RECURSO_NO_DISPONIBLE')),
    reason                 varchar(500) NOT NULL,
    attributable_to_person boolean      NOT NULL,
    notice_minutes         int,
    cancelled_at           timestamptz  NOT NULL,
    resource_id            varchar(64)  NOT NULL,
    resource_category      varchar(16)  NOT NULL,
    holder_user_id         uuid         REFERENCES app_user (id),
    released_starts_at     timestamptz  NOT NULL,
    released_ends_at       timestamptz  NOT NULL,
    delivery_status        varchar(8)   NOT NULL DEFAULT 'PENDING'
        CONSTRAINT cancellation_report_delivery CHECK (delivery_status IN ('PENDING', 'SENT', 'FAILED')),
    attempts               int          NOT NULL DEFAULT 0,
    first_attempt_at       timestamptz,
    delivered_at           timestamptz,
    last_error             varchar(500),
    -- Solo la persona que cancela tiene antelación, y solo esa cancelación se le puede atribuir.
    CONSTRAINT cancellation_report_notice_only_holder
        CHECK (cancellation_origin = 'HOLDER' OR notice_minutes IS NULL),
    CONSTRAINT cancellation_report_attributable
        CHECK (attributable_to_person = (cancellation_origin = 'HOLDER'))
);

CREATE INDEX cancellation_report_delivery ON cancellation_report (delivery_status, cancelled_at);
CREATE INDEX cancellation_report_origin ON cancellation_report (cancellation_origin, cancelled_at);
