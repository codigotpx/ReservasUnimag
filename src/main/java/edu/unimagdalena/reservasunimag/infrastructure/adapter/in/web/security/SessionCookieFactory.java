package edu.unimagdalena.reservasunimag.infrastructure.adapter.in.web.security;

import edu.unimagdalena.reservasunimag.infrastructure.config.ReservationProperties;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

/** La cookie que lleva el JWT: {@code HttpOnly}, {@code SameSite=Lax} y {@code Secure} salvo en local. */
@Component
public class SessionCookieFactory {

    private final ReservationProperties.Jwt jwt;

    public SessionCookieFactory(ReservationProperties properties) {
        this.jwt = properties.jwt();
    }

    public ResponseCookie session(String token) {
        return base(token).maxAge(jwt.ttl()).build();
    }

    public ResponseCookie cleared() {
        return base("").maxAge(Duration.ZERO).build();
    }

    private ResponseCookie.ResponseCookieBuilder base(String value) {
        return ResponseCookie.from(jwt.cookieName(), value)
                .httpOnly(true)
                .secure(jwt.cookieSecure())
                .sameSite("Lax")
                .path("/");
    }
}
