package edu.unimagdalena.reservasunimag.domain.model.reservation;

import edu.unimagdalena.reservasunimag.domain.model.resource.ResourceCategory;
import edu.unimagdalena.reservasunimag.domain.model.user.User;
import java.time.Instant;
import java.util.UUID;

/** Reserva: apartado de un recurso por un titular en una franja, o bloqueo académico. */
public class Reservation {

    private final UUID id;
    private final User holder;
    private final String resourceId;
    private final ResourceCategory category;
    private final ReservationOrigin origin;
    private final ReservationStatus status;
    private final Instant startsAt;
    private final Instant endsAt;
    private final Instant createdAt;
    private final long version;
    private final Loan loan;

    public Reservation(UUID id, User holder, String resourceId, ResourceCategory category, ReservationOrigin origin,
                      ReservationStatus status, Instant startsAt, Instant endsAt, Instant createdAt, long version, Loan loan) {
        this.id = id;
        this.holder = holder;
        this.resourceId = resourceId;
        this.category = category;
        this.origin = origin;
        this.status = status;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.createdAt = createdAt;
        this.version = version;
        this.loan = loan;
    }

    public UUID getId() {
        return id;
    }

    public User getHolder() {
        return holder;
    }

    public String getResourceId() {
        return resourceId;
    }

    public ResourceCategory getCategory() {
        return category;
    }

    public ReservationOrigin getOrigin() {
        return origin;
    }

    public ReservationStatus getStatus() {
        return status;
    }

    public Instant getStartsAt() {
        return startsAt;
    }

    public Instant getEndsAt() {
        return endsAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public long getVersion() {
        return version;
    }

    public Loan getLoan() {
        return loan;
    }
}
