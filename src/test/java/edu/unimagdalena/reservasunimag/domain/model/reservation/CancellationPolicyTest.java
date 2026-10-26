package edu.unimagdalena.reservasunimag.domain.model.reservation;

import edu.unimagdalena.reservasunimag.domain.model.error.CancellationDeniedException;
import edu.unimagdalena.reservasunimag.domain.model.error.CancellationDeniedException.Reason;
import edu.unimagdalena.reservasunimag.domain.model.error.DenialCode;
import edu.unimagdalena.reservasunimag.domain.model.resource.ResourceCategory;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static edu.unimagdalena.reservasunimag.support.ResourceFixtures.STUDENT;
import static edu.unimagdalena.reservasunimag.support.ResourceFixtures.at;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** La regla de los 10 minutos de UC4 FR-007 y el activo ya entregado de FR-008. */
class CancellationPolicyTest {

    private static final Instant TEN = at(10, 0);
    private final CancellationPolicy policy = new CancellationPolicy(Duration.ofMinutes(10));

    private static Reservation space() {
        return Reservation.confirmed(UUID.randomUUID(), STUDENT.id(), "ESP-0107", ResourceCategory.ESPACIO, TEN,
                at(12, 0), at(8, 0), null);
    }

    private static Reservation asset(Loan loan) {
        return Reservation.confirmed(UUID.randomUUID(), STUDENT.id(), "ACT-000913", ResourceCategory.ACTIVO, TEN,
                TEN.plus(Duration.ofDays(3)), at(8, 0), loan);
    }

    @Test
    void withElevenMinutesLeftItIsCancellable() {
        assertThat(policy.cancellableByHolder(space(), at(9, 49))).isTrue();
        assertThatCode(() -> policy.requireCancellableByHolder(space(), at(9, 49))).doesNotThrowAnyException();
    }

    @Test
    void exactlyAtTheTenMinuteMarkItIsStillCancellable() {
        // Edge case "Cancelación en el límite del plazo": el borde es inclusivo.
        assertThat(policy.cancellableByHolder(space(), at(9, 50))).isTrue();
        assertThatCode(() -> policy.requireCancellableByHolder(space(), at(9, 50))).doesNotThrowAnyException();
    }

    @Test
    void oneSecondLaterIsTooLate() {
        assertThat(policy.cancellableByHolder(space(), at(9, 50).plusSeconds(1))).isFalse();
    }

    @Test
    void withNineMinutesLeftItIsCan001WithTheNoticeAndTheReference() {
        assertThatThrownBy(() -> policy.requireCancellableByHolder(space(), at(9, 51)))
                .isInstanceOfSatisfying(CancellationDeniedException.class, denied -> {
                    assertThat(denied.code()).isEqualTo(DenialCode.CAN_001);
                    assertThat(denied.code().code()).isEqualTo("CAN-001");
                    assertThat(denied.reason()).isEqualTo(Reason.TOO_LATE);
                    assertThat(denied.extensions()).containsEntry("minNoticeMinutes", 10L)
                            .containsEntry("referenceAt", TEN);
                    assertThat(denied.getMessage()).startsWith("Esta reserva empieza en menos de 10 minutos.");
                });
    }

    @Test
    void aSpaceThatAlreadyStartedIsNotCancellable() {
        assertThatThrownBy(() -> policy.requireCancellableByHolder(space(), at(10, 30)))
                .isInstanceOfSatisfying(CancellationDeniedException.class, denied -> {
                    assertThat(denied.reason()).isEqualTo(Reason.TOO_LATE);
                    assertThat(denied.getMessage()).startsWith("Esta reserva ya empezó.");
                });
    }

    @Test
    void anAssetNotPickedUpIsCancellableUntilTenMinutesBeforePickup() {
        assertThat(policy.cancellableByHolder(asset(Loan.reserved(3)), at(9, 50))).isTrue();
        assertThatThrownBy(() -> policy.requireCancellableByHolder(asset(Loan.reserved(3)), at(9, 55)))
                .isInstanceOfSatisfying(CancellationDeniedException.class,
                        denied -> assertThat(denied.getMessage()).startsWith("La recogida de este activo"));
    }

    @Test
    void anAssetAlreadyPickedUpIsNeverCancellableNotEvenByTheSystem() {
        Instant pickedUp = at(9, 0);
        Reservation inHands = asset(new Loan(3, pickedUp, null));

        assertThat(policy.cancellableByHolder(inHands, at(8, 0))).isFalse();
        assertThatThrownBy(() -> policy.requireCancellableByHolder(inHands, at(8, 0)))
                .isInstanceOfSatisfying(CancellationDeniedException.class, denied -> {
                    assertThat(denied.code()).isEqualTo(DenialCode.CAN_001);
                    assertThat(denied.reason()).isEqualTo(Reason.ASSET_ALREADY_PICKED_UP);
                    assertThat(denied.extensions()).containsEntry("pickedUpAt", pickedUp)
                            .containsEntry("reason", "ASSET_ALREADY_PICKED_UP");
                });
        assertThatThrownBy(() -> policy.requireOpen(inHands)).isInstanceOf(CancellationDeniedException.class);
    }

    @Test
    void aFinishedReservationIsNotCancellable() {
        Reservation finished = new Reservation(UUID.randomUUID(), STUDENT.id(), "ESP-0107", ResourceCategory.ESPACIO,
                ReservationOrigin.ESTUDIANTIL, ReservationStatus.FINALIZADA, TEN, at(12, 0), at(8, 0), null);

        assertThat(policy.cancellableByHolder(finished, at(8, 0))).isFalse();
        assertThatThrownBy(() -> policy.requireOpen(finished))
                .isInstanceOfSatisfying(CancellationDeniedException.class,
                        denied -> assertThat(denied.reason()).isEqualTo(Reason.RESERVATION_FINISHED));
    }

    @Test
    void theNoticeIsTheWholeMinutesUntilTheReference() {
        assertThat(policy.noticeMinutes(space(), at(8, 30))).isEqualTo(90);
        assertThat(policy.noticeMinutes(space(), at(9, 50).minusSeconds(30))).isEqualTo(10);
    }

    @Test
    void theMinimumNoticeMustBePositive() {
        assertThatThrownBy(() -> new CancellationPolicy(Duration.ZERO)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void onlyAConfirmedReservationCanBeCancelledAndItKeepsEverythingButTheStatus() {
        Reservation confirmed = space();
        Reservation cancelled = confirmed.cancel(CancellationOrigin.ACADEMIC_PRIORITY);

        assertThat(cancelled.status()).isEqualTo(ReservationStatus.CANCELADA_POR_PRIORIDAD_ACADEMICA);
        assertThat(cancelled.startsAt()).isEqualTo(confirmed.startsAt());
        assertThat(cancelled.endsAt()).isEqualTo(confirmed.endsAt());
        assertThatThrownBy(() -> cancelled.cancel(CancellationOrigin.HOLDER)).isInstanceOf(IllegalStateException.class);
    }
}
