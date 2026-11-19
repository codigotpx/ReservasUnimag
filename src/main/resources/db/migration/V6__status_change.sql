-- UC7: el historial de cambios de ocupación de cada recurso (FR-009).
-- Modelo en docs/plan/plan-uc7-actualizar-estado-recursos.md#1-la-tabla-status_change.
-- El inicio de uso se marca en reservation.use_started_at, que ya existía desde V1.

CREATE TABLE status_change (
    id                      uuid        PRIMARY KEY,
    resource_id             varchar(64) NOT NULL,
    resource_category       varchar(16) NOT NULL,
    reservation_id          uuid        REFERENCES reservation (id),
    occupancy_starts_at     timestamptz NOT NULL,
    occupancy_ends_at       timestamptz NOT NULL,
    -- Solo con los datos del Módulo 2: el estado operativo del Módulo 1 no se consulta para auditar.
    previous_status_module2 varchar(24) NOT NULL
        CONSTRAINT status_change_previous CHECK (previous_status_module2 IN (
            'DISPONIBLE', 'RESERVADO', 'BLOQUEO_ACADEMICO')),
    new_status              varchar(24) NOT NULL
        CONSTRAINT status_change_new CHECK (new_status IN (
            'DISPONIBLE', 'EN_USO', 'EN_MANTENIMIENTO', 'RESERVADO', 'BLOQUEO_ACADEMICO')),
    reason                  varchar(32) NOT NULL
        CONSTRAINT status_change_reason CHECK (reason IN (
            'RESERVATION_CONFIRMED', 'ACADEMIC_BLOCK_CREATED', 'USE_STARTED', 'RESERVATION_CANCELLED',
            'NO_SHOW_REGISTERED', 'CHECK_OUT_RECEIVED', 'LOAN_DECLARED_LOST')),
    changed_at              timestamptz NOT NULL,
    notified_status         varchar(8)  NOT NULL
        CONSTRAINT status_change_notified CHECK (notified_status IN ('NOT_SENT', 'PENDING', 'SENT', 'FAILED')),
    -- El aviso al Módulo 1; solo existe en el inicio de uso (FR-012).
    outbox_message_id       uuid,
    CONSTRAINT status_change_notice_only_on_use CHECK (
        (outbox_message_id IS NULL) = (notified_status = 'NOT_SENT')
        AND (outbox_message_id IS NULL OR reason = 'USE_STARTED'))
);

CREATE INDEX status_change_resource ON status_change (resource_id, changed_at);
CREATE INDEX status_change_reservation ON status_change (reservation_id);
