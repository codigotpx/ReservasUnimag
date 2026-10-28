package edu.unimagdalena.reservasunimag.domain.port.in;

import edu.unimagdalena.reservasunimag.domain.model.user.User;

/** Alta de usuario: el titular se registra solo y el rol por defecto es {@code ESTUDIANTE}. */
public interface RegisterUserPort {

    User register(String code, String name, String email, String password);
}
