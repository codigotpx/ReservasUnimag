-- Solo perfil local: un usuario por rol para probar y hacer la demo. Contraseña de todos: unimag2026
-- Es repetible (R__): Flyway lo vuelve a aplicar si cambia, y ON CONFLICT lo hace inofensivo.

INSERT INTO app_user (id, code, name, email, password_hash, role)
VALUES
    ('00000000-0000-0000-0000-000000000001', '2021114001', 'Estudiante de Prueba',
     'estudiante@unimagdalena.edu.co', '$2a$10$m0c.I5GUn5lxaRxY1tSgu.W7eqyBA7850AkudwTBIRUHwoWKTUH.O', 'ESTUDIANTE'),
    ('00000000-0000-0000-0000-000000000002', '2020114002', 'Monitor de Prueba',
     'monitor@unimagdalena.edu.co', '$2a$10$m0c.I5GUn5lxaRxY1tSgu.W7eqyBA7850AkudwTBIRUHwoWKTUH.O', 'MONITOR'),
    ('00000000-0000-0000-0000-000000000003', 'DP-0001', 'Dirección de Programa de Prueba',
     'direccion@unimagdalena.edu.co', '$2a$10$m0c.I5GUn5lxaRxY1tSgu.W7eqyBA7850AkudwTBIRUHwoWKTUH.O', 'DIRECCION_PROGRAMA')
ON CONFLICT (id) DO NOTHING;
