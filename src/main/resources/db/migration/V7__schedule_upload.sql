-- UC3: la carga del horario semestral en dos pasos (validar y confirmar), sus filas y la huella de cada sesión.
-- Modelo en docs/plan/plan-uc3-importar-horarios-semestrales.md.

-- La clase de cada bloqueo y la huella que hace idempotente la recarga (FR-007, SC-003). Al cancelar un bloqueo la
-- huella se pone a NULL: PostgreSQL no considera iguales dos NULL en un índice único, así que la clase se puede volver
-- a cargar después de retirarla.
ALTER TABLE academic_block
    ADD COLUMN course_code varchar(32) NOT NULL,
    ADD COLUMN group_code  varchar(32) NOT NULL,
    ADD COLUMN session_key varchar(64),
    ALTER COLUMN instructor DROP NOT NULL;

CREATE UNIQUE INDEX academic_block_session_key ON academic_block (session_key);

-- La carga guardada entre validar y confirmar: lo que se aplica es exactamente lo que la persona aprobó, y queda la
-- constancia de FR-009 aunque nunca se confirme. uploaded_at es el instante de la validación.
ALTER TABLE semester_schedule
    ADD COLUMN file_name     varchar(255),
    ADD COLUMN term_starts_on date        NOT NULL,
    ADD COLUMN term_ends_on   date        NOT NULL,
    ADD COLUMN status        varchar(16) NOT NULL
        CONSTRAINT semester_schedule_status CHECK (status IN ('VALIDATED', 'APPLIED', 'REJECTED', 'EXPIRED')),
    ADD COLUMN expires_at    timestamptz,
    ADD COLUMN applied_at    timestamptz,
    -- El resumen del reporte y el impacto que se mostró: las reservas a cancelar y los bloqueos a retirar.
    ADD COLUMN summary       jsonb       NOT NULL,
    ADD COLUMN impact        jsonb       NOT NULL,
    ADD CONSTRAINT semester_schedule_term CHECK (term_ends_on > term_starts_on);

CREATE INDEX semester_schedule_term_status ON semester_schedule (academic_term, status);

-- Cada fila del archivo con su dictamen (FR-003). data guarda la fila tal como se reportó.
CREATE TABLE schedule_upload_row (
    semester_schedule_id uuid        NOT NULL REFERENCES semester_schedule (id),
    row_number           int         NOT NULL,
    verdict              varchar(32) NOT NULL
        CONSTRAINT schedule_upload_row_verdict CHECK (verdict IN ('OK', 'UNCHANGED', 'DISPLACES_RESERVATIONS',
            'REJECTED_FORMAT', 'REJECTED_RESOURCE', 'ACADEMIC_CLASH', 'ASSET_ALREADY_PICKED_UP',
            'RESOURCE_UNDER_MAINTENANCE')),
    data                 jsonb       NOT NULL,
    PRIMARY KEY (semester_schedule_id, row_number)
);

-- Retirar una clase del horario es una cancelación más que se le reporta al Módulo 3, con su propio origen.
ALTER TABLE cancellation_report DROP CONSTRAINT cancellation_report_origin;
ALTER TABLE cancellation_report ADD CONSTRAINT cancellation_report_origin CHECK (cancellation_origin IN (
    'HOLDER', 'ACADEMIC_PRIORITY', 'RESOURCE_UNAVAILABLE', 'SCHEDULE_WITHDRAWN'));
