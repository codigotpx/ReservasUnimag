package edu.unimagdalena.reservasunimag.domain.model.error;

/** Correo inexistente, contraseña incorrecta o usuario inactivo: no se distingue cuál (UC1 Contratos §4). */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("Credenciales inválidas");
    }
}
