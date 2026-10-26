package edu.unimagdalena.reservasunimag.infrastructure.adapter.in.messaging;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.UUID;

/**
 * Un evento del topic {@code module3.reservation.no-show.v1} (UC9 Contratos §1), ya validado. El reporte y su
 * anulación llegan por el mismo topic para que la clave de partición impida que la anulación adelante al reporte.
 * {@code verifiedAt} solo existe en un reporte y {@code voidedReason} solo en una anulación. {@code note} y
 * {@code verifiedBy} se ignoran: no los necesitamos y pueden ser datos personales del otro lado.
 */
record NoShowMessage(UUID eventId, Type type, UUID reservationId, Instant verifiedAt, String voidedReason) {

    enum Type {
        NoShowReported,
        NoShowReportVoided
    }

    static NoShowMessage parse(ObjectMapper objectMapper, String json) {
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
        Type type;
        try {
            type = Type.valueOf(text(event, "type"));
        } catch (IllegalArgumentException unknown) {
            throw new InvalidEventException("tipo de evento desconocido: " + text(event, "type"));
        }
        JsonNode data = event.get("data");
        if (data == null || !data.isObject()) {
            throw new InvalidEventException("falta data");
        }
        UUID reservationId = uuid(data, "reservationId");
        return switch (type) {
            case NoShowReported -> new NoShowMessage(eventId, type, reservationId, instant(data, "verifiedAt"), null);
            case NoShowReportVoided -> new NoShowMessage(eventId, type, reservationId, null,
                    data.hasNonNull("voidedReason") ? data.get("voidedReason").asString() : null);
        };
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
