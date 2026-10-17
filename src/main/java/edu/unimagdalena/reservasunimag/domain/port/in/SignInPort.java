package edu.unimagdalena.reservasunimag.domain.port.in;

import edu.unimagdalena.reservasunimag.domain.model.user.User;

/** Login mínimo de UC1 Contratos §4. El autorregistro espera su propio spec. */
public interface SignInPort {

    /** @throws edu.unimagdalena.reservasunimag.domain.model.error.InvalidCredentialsException */
    User signIn(String email, String rawPassword);
}
