package edu.unimagdalena.reservasunimag.domain.model.compliance;

import edu.unimagdalena.reservasunimag.domain.model.reservation.Loan;
import edu.unimagdalena.reservasunimag.domain.model.reservation.Reservation;
import edu.unimagdalena.reservasunimag.domain.model.resource.ResourceCategory;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.UUID;

import static edu.unimagdalena.reservasunimag.support.ResourceFixtures.STUDENT;
import static edu.unimagdalena.reservasunimag.support.ResourceFixtures.at;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** El plazo de UC9 FR-011: desde cuándo se admite un reporte de no asistencia. */
class NoShowPolicyTest {

    private final NoShowPolicy policy = new NoShowPolicy(Duration.ofMinutes(10));

    private static Reservation space() {
        return Reservation.confirmed(UUID.randomUUID(), STUDENT.id(), "ESP-0107", ResourceCategory.ESPACIO,
                at(10, 0), at(12, 0), at(8, 0), null);
    }

    @Test
    void atExactlyTenMinutesTheReportIsAdmitted() {
        // Edge case "Llega justo en el minuto 10": 9 minutos y 59 segundos para llegar, y a los 10 ya se admite.
        assertThat(policy.admits(space(), at(10, 10))).isTrue();
        assertThat(policy.admits(space(), at(10, 10).minusSeconds(1))).isFalse();
    }

    @Test
    void elevenIsAdmittedAndNineIsTooEarly() {
        assertThat(policy.admits(space(), at(10, 11))).isTrue();
        assertThat(policy.admits(space(), at(10, 9))).isFalse();
        assertThat(policy.acceptedFrom(space())).isEqualTo(at(10, 10));
    }

    @Test
    void anAssetIsMeasuredFromThePickupNotFromTheDueDate() {
        Reservation loan = Reservation.confirmed(UUID.randomUUID(), STUDENT.id(), "ACT-000913",
                ResourceCategory.ACTIVO, at(14, 30), at(14, 30).plus(Duration.ofDays(3)), at(8, 0), Loan.reserved(3));

        assertThat(policy.acceptedFrom(loan)).isEqualTo(at(14, 40));
        assertThat(policy.admits(loan, at(14, 40))).isTrue();
    }

    @Test
    void itIsMeasuredAgainstTheOnSiteCheckSoALateDeliveryIsStillOnTime() {
        // Comprobado a las 10:12 aunque llegue horas después: el plazo mira verifiedAt, no la llegada.
        assertThat(policy.admits(space(), at(10, 12))).isTrue();
    }

    @Test
    void theGraceMustBePositive() {
        assertThatThrownBy(() -> new NoShowPolicy(Duration.ZERO)).isInstanceOf(IllegalArgumentException.class);
    }
}
