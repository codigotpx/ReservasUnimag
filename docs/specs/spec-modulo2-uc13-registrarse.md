# Feature Specification: Registrarse

**Created**: 2026-09-30

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Registro de un nuevo estudiante (Priority: P1)

Un estudiante de la universidad se registra en el sistema para poder apartar recursos físicos: salones, laboratorios, salas de estudio o activos como libros, microscopios o videobeams.

**Why this priority**: Sin el registro no hay titular posible para las reservas (UC2), los préstamos (UC8, UC12) ni las consultas (UC1). Es el P1 que abre el ciclo de uso real del módulo.

**Independent Test**: Puede probarse de forma independiente: con un correo institucional nuevo, el registro crea la cuenta con rol `ESTUDIANTE` y la contraseña se persiste hasheada.

**Acceptance Scenarios**:

1. **Scenario**: Registro con datos válidos
   - **Given** un correo institucional que aún no está registrado
   - **When** el usuario envía código, nombre, correo y contraseña válidos
   - **Then** se crea el usuario con rol `ESTUDIANTE`, activo, con el identificador generado y la contraseña hasheada

2. **Scenario**: Registro con correo duplicado
   - **Given** un usuario ya registrado con ese correo
   - **When** otra persona intenta registrarse con el mismo correo
   - **Then** el sistema responde `409 Conflict` con el problema `user-already-registered`

3. **Scenario**: Registro con correo no válido
   - **Given** un correo que no es institucional
   - **When** el correo no cumple el formato (no termina en `@unimagdalena.edu.co`)
   - **Then** el sistema responde `400 Bad Request` con el problema `invalid-parameters`

---

### Edge Cases

- ¿Qué pasa si el correo institucional ya tiene cuenta pero con distinto código? → se rechaza por el correo (duplicado).
- ¿Qué pasa si el código ya pertenece a otra cuenta? → se aplica la misma política de duplicado por correo; la base de datos también lo impone por constraint único.
- ¿Qué pasa si el usuario se registra y el recurso `app_user` queda con el rol por defecto? → el rol por defecto es `ESTUDIANTE`.
- ¿Qué pasa si el registro falla por validación de formato? → `400 invalid-parameters` con los campos en error.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema debe permitir a una persona registrarse con código, nombre, email y contraseña.
- **FR-002**: El sistema debe validar que el email pertenezca a la universidad (`@unimagdalena.edu.co`).
- **FR-003**: El sistema debe validar el código del estudiante: máximo 10 caracteres, únicamente números.
- **FR-004**: El sistema debe hashear la contraseña utilizando BCrypt antes de persistirla.
- **FR-005**: El sistema debe asignar el rol `ESTUDIANTE` por defecto; los roles `MONITOR` y `DIRECCION_PROGRAMA` no se asignan por registro propio.

### Key Entities *(include if feature involves data)*

- **[User / app_user]**: usuario del módulo, con identificador, código, nombre, correo, hash de contraseña, rol, estado activo y fecha de creación.
- **[RegisterUserCommand]**: datos enviados en el registro.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Un registro válido crea la cuenta con rol `ESTUDIANTE` en el estado activo.
- **SC-002**: Un registro con correo ya registrado se rechaza sin duplicar.
- **SC-003**: Un registro con datos inválidos se rechaza con `400 invalid-parameters`.
