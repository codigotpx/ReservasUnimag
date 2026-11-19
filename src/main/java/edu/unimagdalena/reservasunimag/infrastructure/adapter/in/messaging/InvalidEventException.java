package edu.unimagdalena.reservasunimag.infrastructure.adapter.in.messaging;

/**
 * Un evento que no se puede aplicar tal como llegó: no es JSON, le falta un campo obligatorio o su tipo no es uno de
 * los del topic. No mejora reintentando, así que va al DLT con esta causa en las cabeceras (plan general, Mensajería).
 */
public class InvalidEventException extends RuntimeException {

    public InvalidEventException(String message) {
        super(message);
    }

    public InvalidEventException(String message, Throwable cause) {
        super(message, cause);
    }
}
