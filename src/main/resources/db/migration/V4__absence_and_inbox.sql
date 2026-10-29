-- UC9: la constancia de cada ausencia que reporta el Módulo 3, y la inbox que descarta los eventos repetidos.
-- Modelo en docs/plan/plan-uc9-recibir-reporte-no-asistencia.md#3-la-tabla-absence.

-- Una ausencia por reserva: la clave primaria es la reserva (UC9 FR-007, SC-002). Ni la persona ni el recurso se
-- duplican aquí; salen de reservation.
CREATE TABLE absence (
    reservation_id    uuid         PRIMARY KEY REFERENCES reservation (id),
    -- El eventId del reporte del Módulo 3.
    source_event_id   uuid         NOT NULL UNIQUE,
    -- La comprobación en sitio, que es lo que se mide contra el plazo de los 10 minutos (FR-011).
    verified_at       timestamptz  NOT NULL,
    reported_at       timestamptz  NOT NULL,
    -- false si la franja ya había terminado: se registró, pero no había nada que liberar (FR-008).
    resource_released boolean      NOT NULL,
    voided            boolean      NOT NULL DEFAULT false,
    voided_at         timestamptz,
    voided_reason     varchar(500),
    CONSTRAINT absence_voided_stamped CHECK (voided = (voided_at IS NOT NULL))
);

CREATE INDEX absence_reported ON absence (reported_at);

-- Inbox: los eventos del Módulo 3 ya procesados. La clave primaria descarta una reentrega (plan general, Mensajería).
CREATE TABLE inbox_message (
    event_id       uuid         PRIMARY KEY,
    topic          varchar(128) NOT NULL,
    type           varchar(16)  NOT NULL
        CONSTRAINT inbox_message_type CHECK (type IN ('NO_SHOW', 'CHECK_OUT')),
    -- Nulo si la reserva no existe; ese evento acaba en el DLT y su transacción no deja esta fila.
    reservation_id uuid         REFERENCES reservation (id),
    payload        jsonb        NOT NULL,
    processed_at   timestamptz  NOT NULL DEFAULT now()
);

CREATE INDEX inbox_message_reservation ON inbox_message (reservation_id);
