package edu.unimagdalena.reservasunimag.infrastructure.adapter.in.web.security;

import edu.unimagdalena.reservasunimag.domain.model.user.User;
import edu.unimagdalena.reservasunimag.infrastructure.config.ReservationProperties;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;

/** Firma el JWT de sesión con {@code sub}, {@code code}, {@code role}, emisor y vigencia (UC1 Contratos §4). */
@Component
public class JwtTokenIssuer {

    private final JwtEncoder encoder;
    private final Clock clock;
    private final ReservationProperties.Jwt jwt;

    public JwtTokenIssuer(JwtEncoder encoder, Clock clock, ReservationProperties properties) {
        this.encoder = encoder;
        this.clock = clock;
        this.jwt = properties.jwt();
    }

    public String issue(User user) {
        Instant now = clock.instant();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(jwt.issuer())
                .issuedAt(now)
                .expiresAt(now.plus(jwt.ttl()))
                .subject(user.id().toString())
                .claim(JwtClaimNames.CODE, user.code())
                .claim(JwtClaimNames.ROLE, user.role().name())
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
