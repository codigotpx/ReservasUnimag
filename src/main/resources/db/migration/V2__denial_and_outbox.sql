-- UC2: registro de denegaciones y outbox hacia Kafka.
-- Modelo en docs/plan/plan-arquitectura.md#modelo.

-- Cada negativa del diccionario RES-00X queda registrada, sin más datos del titular que su id (UC2 FR-005).
CREATE TABLE denial (
    id          uuid        PRIMARY KEY,
    user_id     uuid        NOT NULL REFERENCES app_user (id),
    resource_id varchar(64) NOT NULL,
    code        varchar(16) NOT NULL,
    occurred_at timestamptz NOT NULL
);

CREATE INDEX denial_user ON denial (user_id);

-- Outbox: el evento se inserta junto con la reserva y un publicador lo lleva a Kafka después del commit.
-- El id es también el eventId del evento publicado.
CREATE TABLE outbox_message (
    id              uuid        PRIMARY KEY,
    type            varchar(32) NOT NULL
        CONSTRAINT outbox_message_type CHECK (type IN (
            'RESERVATION_RECORD', 'CANCELLATION', 'NO_SHOW_ACK', 'CHECK_OUT_ACK', 'RESOURCE_USE_STARTED')),
    reservation_id  uuid        NOT NULL REFERENCES reservation (id),
    payload         jsonb       NOT NULL,
    status          varchar(8)  NOT NULL DEFAULT 'PENDING'
        CONSTRAINT outbox_message_status CHECK (status IN ('PENDING', 'SENT', 'FAILED')),
    attempts        int         NOT NULL DEFAULT 0,
    next_attempt_at timestamptz NOT NULL DEFAULT now(),
    last_error      varchar(500),
    created_at      timestamptz NOT NULL DEFAULT now(),
    sent_at         timestamptz
);

CREATE INDEX outbox_message_pending ON outbox_message (next_attempt_at, created_at) WHERE (status = 'PENDING');
CREATE INDEX outbox_message_reservation ON outbox_message (reservation_id);
