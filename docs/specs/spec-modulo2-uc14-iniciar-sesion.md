# Feature Specification: Iniciar sesión

**Created**: 2026-09-30

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Inicio de sesión con correo y contraseña (Priority: P1)

Un usuario registrado introduce su correo y su contraseña y el sistema le entrega una sesión: un JWT firmado por el backend que viaja en una cookie `httpOnly` y que habilita el resto de los casos de uso autenticados (consultar recursos, reservar, cancelar, etc.).

**Why this priority**: Sin sesión no hay casos de uso posteriores que requieran identidad (UC1, UC2, UC4, UC8, UC10, UC11, UC12). Es el P1 que sostiene el resto.

**Independent Test**: Puede probarse de forma independiente: con un usuario activo y sus credenciales correctas, el login responde `204` con cookie de sesión y `GET /api/auth/me` devuelve el perfil.

**Acceptance Scenarios**:

1. **Scenario**: Login correcto
   - **Given** un usuario activo con correo y contraseña correctos
   - **When** el usuario hace `POST /api/auth/login`
   - **Then** el sistema responde `204 No Content` con la cookie de sesión (`httpOnly`, `SameSite=Lax`, `Secure` salvo en local, máximo de 8 horas) y el JWT con `sub`, `code` y `role`

2. **Scenario**: Credenciales incorrectas
   - **Given** un usuario con correo conocido pero contraseña incorrecta, o un correo desconocido, o un usuario inactivo
   - **When** el usuario hace `POST /api/auth/login`
   - **Then** el sistema responde `401 Unauthorized` con el problema `invalid-credentials`

3. **Scenario**: Body de login inválido
   - **Given** un cuerpo con email con formato inválido o password en blanco
   - **When** el usuario hace `POST /api/auth/login`
   - **Then** el sistema responde `400 Bad Request` con el problema `invalid-parameters`

4. **Scenario**: Sesión requerida
   - **Given** el usuario no tiene cookie de sesión
   - **When** consulta un endpoint protegido como `GET /api/auth/me`
   - **Then** el sistema responde `401 Unauthorized` con el problema `unauthenticated`

5. **Scenario**: Token manipulado
   - **Given** un token firmado que no coincide con lo esperado
   - **When** el usuario consulta `GET /api/auth/me` con esa cookie
   - **Then** el sistema responde `401 Unauthorized` con el problema `unauthenticated`

---

### Edge Cases

- ¿Qué pasa si el correo tiene espacios o mayúsculas distintas? → se normaliza (trim + minúsculas) antes de buscar.
- ¿Qué pasa si el usuario está inactivo? → no emite sesión (se trata como credenciales inválidas).
- ¿Qué pasa si el token supera la vigencia? → el validador con issuer y TTL lo rechaza.
- ¿Qué pasa si el logout? → la cookie se limpia con `maxAge=0`.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema debe autenticar a los usuarios utilizando BCrypt password comparison.
- **FR-002**: El sistema debe emitir un JWT con `sub`, `code`, `role`, el que lo emitió y vigencia, firmado con HS256.
- **FR-003**: El sistema debe entregar un JWT a través de una cookie de sesión `httpOnly`, `SameSite=Lax`, `Secure` salvo en local.
- **FR-004**: El sistema debe mapear el `role` dependiendo de su autoridad `ROLE_<rol>`; `MONITOR` hereda `ESTUDIANTE`.
- **FR-005**: El sistema debe mostrar `401` con `invalid-credentials` para emails incorrectos, contraseñas inválidas o usuarios inactivos.

### Key Entities *(include if feature involves data)*

- **[User / app_user]**: usuario con identificador, código, correo, hash de contraseña, rol y estado.
- **[JWT]**: token de sesión con claims propios (`code`, `role`), sujeto por el validador con issuer y TTL.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: El login correcto responde `204` con cookie de sesión de 8 horas.
- **SC-002**: El login con credenciales inválidas responde `401` con problema `invalid-credentials`.
- **SC-003**: Un token manipado o sin sesión es rechazado con `401 unauthenticated`.
