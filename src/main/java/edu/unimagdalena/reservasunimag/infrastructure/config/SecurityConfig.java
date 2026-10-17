package edu.unimagdalena.reservasunimag.infrastructure.config;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import edu.unimagdalena.reservasunimag.domain.model.user.Role;
import edu.unimagdalena.reservasunimag.infrastructure.adapter.in.web.error.ProblemResponses;
import edu.unimagdalena.reservasunimag.infrastructure.adapter.in.web.security.JwtClaimNames;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.util.WebUtils;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * JWT propio, solo entre el frontend y nuestro {@code /api/**}. Viaja en una cookie {@code httpOnly}, así que
 * JavaScript nunca lo ve. Las llamadas a los Módulos 1 y 3 y todo lo de Kafka van sin este token.
 *
 * <p>CSRF va desactivado a propósito: la sesión no vive en el servidor, la cookie es {@code SameSite=Lax} y el
 * frontend llama por el mismo origen gracias al <em>rewrite</em> de Next.js.
 */
@Configuration(proxyBeanMethods = false)
class SecurityConfig {

    private static final String ROLE_PREFIX = "ROLE_";

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, BearerTokenResolver cookieTokenResolver,
                                            JwtAuthenticationConverter jwtAuthenticationConverter,
                                            ProblemResponses problemResponses) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/auth/login", "/api/auth/logout").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html", "/error").permitAll()
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().denyAll())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .bearerTokenResolver(cookieTokenResolver)
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter))
                        .authenticationEntryPoint(problemResponses::unauthenticated))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(problemResponses::unauthenticated)
                        .accessDeniedHandler(problemResponses::forbidden));
        return http.build();
    }

    /** Lee el token de la cookie de sesión y no de la cabecera {@code Authorization}. */
    @Bean
    BearerTokenResolver cookieTokenResolver(ReservationProperties properties) {
        String cookieName = properties.jwt().cookieName();
        return request -> {
            var cookie = WebUtils.getCookie(request, cookieName);
            return cookie == null || cookie.getValue().isBlank() ? null : cookie.getValue();
        };
    }

    /** El claim {@code role} se vuelve la autoridad {@code ROLE_<rol>} de Spring Security. */
    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt ->
                List.of(new SimpleGrantedAuthority(ROLE_PREFIX + jwt.getClaimAsString(JwtClaimNames.ROLE))));
        return converter;
    }

    /** {@code MONITOR} hereda todo lo de {@code ESTUDIANTE} (UC1 FR-005). */
    @Bean
    static RoleHierarchy roleHierarchy() {
        return RoleHierarchyImpl.withRolePrefix(ROLE_PREFIX)
                .role(Role.MONITOR.name()).implies(Role.ESTUDIANTE.name())
                .build();
    }

    @Bean
    JwtEncoder jwtEncoder(ReservationProperties properties) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(secretKey(properties)));
    }

    @Bean
    JwtDecoder jwtDecoder(ReservationProperties properties) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(secretKey(properties))
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(properties.jwt().issuer()));
        return decoder;
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    private static SecretKey secretKey(ReservationProperties properties) {
        byte[] bytes = properties.jwt().secret().getBytes(StandardCharsets.UTF_8);
        return new SecretKeySpec(bytes, "HmacSHA256");
    }
}
