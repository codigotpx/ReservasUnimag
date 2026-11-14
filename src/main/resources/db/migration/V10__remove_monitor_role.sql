-- El rol MONITOR desaparece del Módulo 2: no tenía ninguna capacidad propia y heredaba todo de ESTUDIANTE (P-09).
-- El monitor es un actor del Módulo 3. Quien lo tenía pasa a ESTUDIANTE, sin perder nada de lo que podía hacer.
UPDATE app_user SET role = 'ESTUDIANTE' WHERE role = 'MONITOR';

ALTER TABLE app_user
    DROP CONSTRAINT app_user_role,
    ADD CONSTRAINT app_user_role CHECK (role IN ('ESTUDIANTE', 'DIRECCION_PROGRAMA'));

-- Las filas de las cargas del horario guardan el rol del titular de cada reserva afectada dentro de su JSON.
UPDATE schedule_upload_row
SET data = replace(data::text, '"holderRole": "MONITOR"', '"holderRole": "ESTUDIANTE"')::jsonb
WHERE data::text LIKE '%"holderRole": "MONITOR"%';

