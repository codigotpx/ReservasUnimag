package edu.unimagdalena.reservasunimag.infrastructure.adapter.in.web.auth;

import edu.unimagdalena.reservasunimag.domain.model.user.CurrentUser;
import edu.unimagdalena.reservasunimag.domain.model.user.User;
import edu.unimagdalena.reservasunimag.domain.port.in.GetProfilePort;
import edu.unimagdalena.reservasunimag.domain.port.in.SignInPort;
import edu.unimagdalena.reservasunimag.infrastructure.adapter.in.web.security.JwtTokenIssuer;
import edu.unimagdalena.reservasunimag.infrastructure.adapter.in.web.security.SessionCookieFactory;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.CredentialsExpiredException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Login mínimo de UC1 Contratos §4: la sesión es un JWT en una cookie {@code httpOnly}. */
@RestController
@RequestMapping("/api/auth")
class AuthController {

    private final SignInPort signIn;
    private final GetProfilePort getProfile;
    private final JwtTokenIssuer tokenIssuer;
    private final SessionCookieFactory cookies;

    AuthController(SignInPort signIn, GetProfilePort getProfile, JwtTokenIssuer tokenIssuer,
                   SessionCookieFactory cookies) {
        this.signIn = signIn;
        this.getProfile = getProfile;
        this.tokenIssuer = tokenIssuer;
        this.cookies = cookies;
    }

    /** {@code 204} sin cuerpo, para que el JWT no quede al alcance de JavaScript. */
    @PostMapping("/login")
    ResponseEntity<Void> login(@Valid @RequestBody LoginRequest request) {
        User user = signIn.signIn(request.email(), request.password());
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookies.session(tokenIssuer.issue(user)).toString())
                .build();
    }

    @GetMapping("/me")
    ProfileResponse me(CurrentUser currentUser) {
        return getProfile.getProfile(currentUser)
                .map(ProfileResponse::from)
                // El token sigue firmado, pero el usuario ya no existe o se desactivó.
                .orElseThrow(() -> new CredentialsExpiredException("Usuario inactivo"));
    }

    @PostMapping("/logout")
    ResponseEntity<Void> logout() {
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookies.cleared().toString())
                .build();
    }
}
