-- UC6: registro de cada consulta al Módulo 3 y de lo que contestó (FR-009).
-- No es una tabla de sanciones (FR-005): guarda que preguntamos y qué nos dijeron, por eso las columnas de la
-- respuesta se llaman answered_*. Modelo en docs/plan/plan-uc6-consultar-sanciones.md#2-la-tabla-sanction_check.
CREATE TABLE sanction_check (
    id                       uuid         PRIMARY KEY,
    person_code              varchar(32)  NOT NULL,
    asked_by                 varchar(24)  NOT NULL
        CONSTRAINT sanction_check_asked_by CHECK (asked_by IN ('SEARCH_RESOURCES', 'RESERVE_RESOURCES')),
    -- Lo que se quería reservar: explica por qué una sanción de otro alcance no denegó.
    resource_category        varchar(16)
        CONSTRAINT sanction_check_category CHECK (resource_category IN ('ESPACIO', 'ACTIVO')),
    asked_at                 timestamptz  NOT NULL,
    outcome                  varchar(16)  NOT NULL
        CONSTRAINT sanction_check_outcome CHECK (outcome IN ('CLEAR', 'SANCTIONED', 'NO_HISTORY', 'NOT_VERIFIED')),
    answered_sanction_reason varchar(500),
    answered_ends_on         date,
    answered_scope           varchar(16)
        CONSTRAINT sanction_check_scope CHECK (answered_scope IN ('ESPACIOS', 'ACTIVOS', 'TODO')),
    end_date_missing         boolean      NOT NULL DEFAULT false,
    answered_absence_count   int,
    answered_late_returns    int,
    duration_ms              int          NOT NULL,
    failure_detail           varchar(500),
    CONSTRAINT sanction_check_category_only_reserving
        CHECK ((asked_by = 'RESERVE_RESOURCES') = (resource_category IS NOT NULL)),
    CONSTRAINT sanction_check_sanction_only_sanctioned
        CHECK ((outcome = 'SANCTIONED') = (answered_scope IS NOT NULL)),
    CONSTRAINT sanction_check_failure_only_not_verified
        CHECK (failure_detail IS NULL OR outcome = 'NOT_VERIFIED')
);

CREATE INDEX sanction_check_person ON sanction_check (person_code, asked_at);
CREATE INDEX sanction_check_outcome ON sanction_check (outcome, asked_at);
CREATE INDEX sanction_check_asked_at ON sanction_check (asked_at);
