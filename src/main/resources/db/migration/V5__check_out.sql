-- UC12: la devolución de un activo o la revisión de un espacio, tal como la reportó el Módulo 3.
-- Modelo en docs/plan/plan-uc12-recibir-check-out.md#5-la-tabla-check_out.

-- Un check-out por reserva: la clave primaria es la reserva (UC12 FR-007, SC-003). No hay ninguna columna de daños,
-- y el CHECK hace imposible guardar un dictamen en un activo o una revisión de espacio sin él (FR-010, FR-011).
CREATE TABLE check_out (
    reservation_id  uuid        PRIMARY KEY REFERENCES reservation (id),
    kind            varchar(16) NOT NULL
        CONSTRAINT check_out_kind CHECK (kind IN ('ASSET_RETURN', 'SPACE_REVIEW')),
    -- El eventId del Módulo 3.
    source_event_id uuid        NOT NULL UNIQUE,
    -- Cuándo volvió el activo o se revisó el espacio; se guarda con la precisión que llegue, sin redondear.
    occurred_at     timestamptz NOT NULL,
    -- Cuándo nos llegó el aviso (FR-009).
    received_at     timestamptz NOT NULL,
    verdict         varchar(24)
        CONSTRAINT check_out_verdict CHECK (verdict IN ('SIN_NOVEDAD', 'REQUIERE_MANTENIMIENTO')),
    -- El vencimiento vigente al cerrar el préstamo; solo en activos.
    due_at_snapshot timestamptz,
    CONSTRAINT check_out_verdict_by_kind CHECK ((kind = 'SPACE_REVIEW') = (verdict IS NOT NULL)),
    CONSTRAINT check_out_due_by_kind CHECK ((kind = 'ASSET_RETURN') = (due_at_snapshot IS NOT NULL))
);

CREATE INDEX check_out_received ON check_out (received_at);
