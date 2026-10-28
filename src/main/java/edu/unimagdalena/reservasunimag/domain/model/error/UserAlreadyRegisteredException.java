package edu.unimagdalena.reservasunimag.domain.model.error;

/** El correo o el código ya pertenece a otro usuario: el registro no debe repetirse. */
public class UserAlreadyRegisteredException extends RuntimeException {

    public UserAlreadyRegisteredException() {
        super("El usuario ya está registrado");
    }
}
