package edu.unimagdalena.reservasunimag.domain.usecase.updatestatus;

import edu.unimagdalena.reservasunimag.domain.model.reservation.CancellationOrigin;
import edu.unimagdalena.reservasunimag.domain.model.reservation.Loan;
import edu.unimagdalena.reservasunimag.domain.model.reservation.Reservation;
import edu.unimagdalena.reservasunimag.domain.model.reservation.ReservationOrigin;
import edu.unimagdalena.reservasunimag.domain.model.reservation.ReservationStatus;
import edu.unimagdalena.reservasunimag.domain.model.resource.DisplayedStatus;
import edu.unimagdalena.reservasunimag.domain.model.resource.NotifiedStatus;
import edu.unimagdalena.reservasunimag.domain.model.resource.ResourceCategory;
import edu.unimagdalena.reservasunimag.domain.model.resource.StatusChange;
import edu.unimagdalena.reservasunimag.domain.model.resource.StatusChangeReason;
import edu.unimagdalena.reservasunimag.support.InMemoryStatusChanges;
import edu.unimagdalena.reservasunimag.support.InMemoryStatusChanges.RecordingInventoryNotifier;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.UUID;

import static edu.unimagdalena.reservasunimag.support.ResourceFixtures.CLOCK;
import static edu.unimagdalena.reservasunimag.support.ResourceFixtures.NOW;
import static edu.unimagdalena.reservasunimag.support.ResourceFixtures.STUDENT;
import static edu.unimagdalena.reservasunimag.support.ResourceFixtures.at;
import static org.assertj.core.api.Assertions.assertThat;

/** El registro de UC7 FR-009 y el aviso de FR-004, que solo sale en el inicio de uso (FR-012). */
class UpdateResourceStatusUseCaseTest {

    private final InMemoryStatusChanges statusChanges = new InMemoryStatusChanges();
    private final RecordingInventoryNotifier notifier = new RecordingInventoryNotifier();
    private final UpdateResourceStatusUseCase useCase = new UpdateResourceStatusUseCase(statusChanges, notifier, CLOCK,
            true);

    private static Reservation space() {
        return Reservation.confirmed(UUID.randomUUID(), STUDENT.id(), "ESP-0107", ResourceCategory.ESPACIO,
                at(10, 0), at(12, 0), at(8, 0), null);
    }

    @Test
    void everyReasonLeavesItsRecordWithTheOccupancyAndBothStatuses() {
        Reservation reservation = space();

        StatusChange confirmed = useCase.record(reservation, StatusChangeReason.RESERVATION_CONFIRMED);
        StatusChange cancelled = useCase.record(reservation.cancel(CancellationOrigin.HOLDER),
                StatusChangeReason.RESERVATION_CANCELLED);

        assertThat(confirmed.previousStatusModule2()).isEqualTo(DisplayedStatus.DISPONIBLE);
        assertThat(confirmed.newStatus()).isEqualTo(DisplayedStatus.RESERVADO);
        assertThat(cancelled.previousStatusModule2()).isEqualTo(DisplayedStatus.RESERVADO);
        assertThat(cancelled.newStatus()).isEqualTo(DisplayedStatus.DISPONIBLE);
        assertThat(confirmed.occupancyStart()).isEqualTo(at(10, 0));
        assertThat(confirmed.occupancyEnd()).isEqualTo(at(12, 0));
        assertThat(confirmed.changedAt()).isEqualTo(NOW);
        assertThat(statusChanges.stored).containsExactly(confirmed, cancelled);
    }

    @Test
    void onlyTheUseStartIsNotifiedAndItIsBornPending() {
        Reservation reservation = space();
        for (StatusChangeReason reason : new StatusChangeReason[]{StatusChangeReason.RESERVATION_CONFIRMED,
                StatusChangeReason.RESERVATION_CANCELLED, StatusChangeReason.NO_SHOW_REGISTERED,
                StatusChangeReason.CHECK_OUT_RECEIVED}) {
            assertThat(useCase.record(reservation, reason).notifiedStatus()).isEqualTo(NotifiedStatus.NOT_SENT);
        }
        assertThat(notifier.sent).isEmpty();

        StatusChange started = useCase.record(reservation.startUse(at(10, 3)), StatusChangeReason.USE_STARTED);

        assertThat(started.notifiedStatus()).isEqualTo(NotifiedStatus.PENDING);
        assertThat(started.outboxMessageId()).isNotNull();
        assertThat(started.newStatus()).isEqualTo(DisplayedStatus.EN_USO);
        assertThat(notifier.sent).singleElement().satisfies(useStart -> {
            assertThat(useStart.startedAt()).isEqualTo(at(10, 3));
            // En un espacio, el aviso lleva la hora de fin para que el Módulo 1 lo cierre solo (FR-004a).
            assertThat(useStart.useEndsAt()).isEqualTo(at(12, 0));
        });
    }

    @Test
    void anAssetUseStartHasNoEndBecauseItEndsWithTheReturn() {
        Reservation loan = Reservation.confirmed(UUID.randomUUID(), STUDENT.id(), "ACT-000913",
                ResourceCategory.ACTIVO, at(10, 0), at(10, 0).plus(Duration.ofDays(3)), at(8, 0), Loan.reserved(3));

        useCase.record(loan.startUse(at(10, 1)), StatusChangeReason.USE_STARTED);

        assertThat(notifier.sent.get(0).useEndsAt()).isNull();
    }

    @Test
    void theAcademicPreviousStatusIsTheBlockAndNeverMaintenance() {
        Reservation block = new Reservation(UUID.randomUUID(), null, "ESP-0107", ResourceCategory.ESPACIO,
                ReservationOrigin.ACADEMICO, ReservationStatus.CONFIRMADA, at(10, 0), at(12, 0), at(8, 0), null);

        StatusChange created = useCase.record(block, StatusChangeReason.ACADEMIC_BLOCK_CREATED);
        StatusChange started = useCase.record(block.startUse(at(10, 0)), StatusChangeReason.USE_STARTED);

        assertThat(created.previousStatusModule2()).isEqualTo(DisplayedStatus.DISPONIBLE);
        assertThat(created.newStatus()).isEqualTo(DisplayedStatus.BLOQUEO_ACADEMICO);
        assertThat(started.previousStatusModule2()).isEqualTo(DisplayedStatus.BLOQUEO_ACADEMICO);
        assertThat(statusChanges.stored).extracting(StatusChange::previousStatusModule2)
                .doesNotContain(DisplayedStatus.EN_MANTENIMIENTO, DisplayedStatus.EN_USO);
    }

    @Test
    void withTheValveClosedTheUseStartIsRecordedWithoutNotice() {
        var closed = new UpdateResourceStatusUseCase(statusChanges, notifier, CLOCK, false);

        StatusChange started = closed.record(space().startUse(at(10, 3)), StatusChangeReason.USE_STARTED);

        assertThat(started.notifiedStatus()).isEqualTo(NotifiedStatus.NOT_SENT);
        assertThat(notifier.sent).isEmpty();
    }
}
