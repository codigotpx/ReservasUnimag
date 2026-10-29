package edu.unimagdalena.reservasunimag.domain.model.academicblock;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;

/**
 * La huella de una sesión: el mismo periodo, recurso, franja, asignatura y grupo dan siempre la misma clave. Es lo que
 * hace que recargar el mismo horario no duplique nada (UC3 FR-007, SC-003): la base la tiene en un índice único.
 */
public final class SessionKeys {

    private SessionKeys() {
    }

    public static String of(String academicTerm, String resourceId, Instant start, Instant end, String courseCode,
                            String group) {
        String source = String.join("|", academicTerm, resourceId, start.toString(), end.toString(), courseCode,
                group);
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(source.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("la JVM no trae SHA-256", impossible);
        }
    }
}
