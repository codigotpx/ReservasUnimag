package edu.unimagdalena.reservasunimag.domain.model.user;

public enum Role {
    ESTUDIANTE,
    /** Especialización de Estudiante: hereda todas sus capacidades. */
    MONITOR,
    DIRECCION_PROGRAMA;

    /** Si quien tiene este rol puede hacer lo que se le permite a {@code required}. */
    public boolean includes(Role required) {
        return this == required || (this == MONITOR && required == ESTUDIANTE);
    }
}
