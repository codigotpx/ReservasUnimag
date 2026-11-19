package edu.unimagdalena.reservasunimag.infrastructure.adapter.in.web.security;

/** Claims propios del JWT (UC1 Contratos §4). {@code sub} es el id del usuario. */
public final class JwtClaimNames {

    public static final String CODE = "code";
    public static final String ROLE = "role";

    private JwtClaimNames() {
    }
}
