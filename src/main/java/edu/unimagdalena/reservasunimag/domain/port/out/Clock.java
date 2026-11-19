package edu.unimagdalena.reservasunimag.domain.port.out;

import java.time.Instant;

/** "Ahora", inyectable para que las pruebas controlen el tiempo (no-show, vencimientos, antelación). */
public interface Clock {

    Instant now();
}
