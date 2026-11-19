package edu.unimagdalena.reservasunimag.domain.model.error;

/** El Módulo 1 o el Módulo 3 no respondieron a tiempo o respondieron con error. */
public class ExternalServiceUnavailableException extends RuntimeException {

    public enum ExternalModule {
        MODULO_1,
        MODULO_3
    }

    private final ExternalModule module;

    public ExternalServiceUnavailableException(ExternalModule module, String message, Throwable cause) {
        super(message, cause);
        this.module = module;
    }

    public ExternalModule module() {
        return module;
    }
}
