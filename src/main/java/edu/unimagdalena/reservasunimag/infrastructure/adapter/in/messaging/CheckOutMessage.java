package edu.unimagdalena.reservasunimag.infrastructure.adapter.in.messaging;

import edu.unimagdalena.reservasunimag.domain.model.compliance.Verdict;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.Set;
import java.util.UUID;

/**
 * Un evento del topic {@code module3.reservation.check-out.v1} (UC12 Contratos §1), ya validado. {@code occurredAt} es
 * el de {@code data} —cuándo volvió el activo o se revisó el espacio—, no el del envoltorio. Una descripción de daños
 * no se lee: solo se anota que vino, para rechazar el check-out (FR-010).
 */
record CheckOutMessage(UUID eventId, UUID reservationId, Instant occurredAt, Verdict verdict, boolean carriesDamage) {

    static final String TYPE = "CheckOutRegistered";
    private static final Set<String> DAMAGE_FIELDS = Set.of("damage", "damages", "damageDescription");

    static CheckOutMessage parse(ObjectMapper objectMapper, String json) {
        JsonNode event;
        try {
            event = objectMapper.readTree(json);
        } catch (JacksonException notJson) {
            throw new InvalidEventException("el evento no es JSON", notJson);
        }
        if (event == null || !event.isObject()) {
            throw new InvalidEventException("el evento no es un objeto JSON");
        }
        UUID eventId = uuid(event, "eventId");
        if (!TYPE.equals(text(event, "type"))) {
            throw new InvalidEventException("tipo de evento desconocido: " + text(event, "type"));
        }
        JsonNode data = event.get("data");
        if (data == null || !data.isObject()) {
            throw new InvalidEventException("falta data");
        }
        Verdict verdict = null;
        if (data.hasNonNull("verdict")) {
            try {
                verdict = Verdict.valueOf(text(data, "verdict"));
            } catch (IllegalArgumentException unknown) {
                throw new InvalidEventException("dictamen desconocido: " + text(data, "verdict"));
            }
        }
        boolean carriesDamage = DAMAGE_FIELDS.stream().anyMatch(data::hasNonNull);
        return new CheckOutMessage(eventId, uuid(data, "reservationId"), instant(data, "occurredAt"), verdict,
                carriesDamage);
    }

    private static String text(JsonNode node, String field) {
        if (!node.hasNonNull(field) || !node.get(field).isString() || node.get(field).asString().isBlank()) {
            throw new InvalidEventException("falta " + field);
        }
        return node.get(field).asString();
    }

    private static UUID uuid(JsonNode node, String field) {
        try {
            return UUID.fromString(text(node, field));
        } catch (IllegalArgumentException malformed) {
            throw new InvalidEventException(field + " no es un uuid", malformed);
        }
    }

    private static Instant instant(JsonNode node, String field) {
        try {
            return OffsetDateTime.parse(text(node, field)).toInstant();
        } catch (DateTimeParseException malformed) {
            throw new InvalidEventException(field + " no es una fecha ISO-8601 con zona", malformed);
        }
    }
}
