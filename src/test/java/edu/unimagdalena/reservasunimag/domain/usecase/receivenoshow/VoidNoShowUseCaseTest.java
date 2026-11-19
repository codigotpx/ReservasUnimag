package edu.unimagdalena.reservasunimag.domain.usecase.receivenoshow;

import edu.unimagdalena.reservasunimag.domain.model.compliance.Absence;
import edu.unimagdalena.reservasunimag.domain.port.out.TransactionPort;
import edu.unimagdalena.reservasunimag.support.InMemoryAbsences;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.function.Supplier;

import static edu.unimagdalena.reservasunimag.support.ResourceFixtures.at;
import static org.assertj.core.api.Assertions.assertThat;

/** UC9 FR-012: anular un reporte enviado por error deshace la constancia, no el reparto del recurso. */
class VoidNoShowUseCaseTest {

    private final InMemoryAbsences absences = new InMemoryAbsences();
    private final TransactionPort transactions = new TransactionPort() {
        @Override
        public <T> T required(Supplier<T> work) {
            return work.get();
        }
    };
    private final VoidNoShowUseCase useCase = new VoidNoShowUseCase(absences, transactions, () -> at(11, 2));

    private UUID registered() {
        UUID reservationId = UUID.randomUUID();
        absences.insert(Absence.registered(reservationId, UUID.randomUUID(), at(10, 10), at(10, 11), true));
        return reservationId;
    }

    @Test
    void voidingMarksTheAbsenceWithItsDateAndReasonAndReleasesTheClosure() {
        UUID reservationId = registered();

        assertThat(useCase.voidReport(reservationId, "La persona sí se presentó.")).isTrue();

        Absence voided = absences.stored.get(reservationId);
        assertThat(voided.voided()).isTrue();
        assertThat(voided.voidedAt()).isEqualTo(at(11, 2));
        assertThat(voided.voidedReason()).isEqualTo("La persona sí se presentó.");
        assertThat(absences.closed).doesNotContain(reservationId);
        assertThat(absences.findActiveByReservations(java.util.List.of(reservationId))).isEmpty();
    }

    @Test
    void voidingSomethingThatDoesNotExistCreatesNothing() {
        assertThat(useCase.voidReport(UUID.randomUUID(), "x")).isFalse();
        assertThat(absences.stored).isEmpty();
    }

    @Test
    void voidingTwiceKeepsTheFirstVoiding() {
        UUID reservationId = registered();
        useCase.voidReport(reservationId, "primera");

        assertThat(useCase.voidReport(reservationId, "segunda")).isFalse();
        assertThat(absences.stored.get(reservationId).voidedReason()).isEqualTo("primera");
    }
}
