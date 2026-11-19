package edu.unimagdalena.reservasunimag.domain.model.resource;

import edu.unimagdalena.reservasunimag.domain.model.reservation.Occupancy;
import edu.unimagdalena.reservasunimag.domain.model.reservation.OccupancyType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static edu.unimagdalena.reservasunimag.support.ResourceFixtures.NOW;
import static edu.unimagdalena.reservasunimag.support.ResourceFixtures.TEN_TO_TWELVE;
import static edu.unimagdalena.reservasunimag.support.ResourceFixtures.at;
import static edu.unimagdalena.reservasunimag.support.ResourceFixtures.occupancy;
import static edu.unimagdalena.reservasunimag.support.ResourceFixtures.slot;
import static edu.unimagdalena.reservasunimag.domain.model.resource.OperationalStatus.DISPONIBLE;
import static edu.unimagdalena.reservasunimag.domain.model.resource.OperationalStatus.EN_MANTENIMIENTO;
import static edu.unimagdalena.reservasunimag.domain.model.resource.OperationalStatus.EN_USO;
import static org.assertj.core.api.Assertions.assertThat;

class DisplayedStatusTest {

    private static final Instant LATER = at(15, 0);

    private static DisplayedStatus resolve(OperationalStatus operational, Occupancy... occupancies) {
        return DisplayedStatus.resolve(operational, List.of(occupancies), TEN_TO_TWELVE, LATER);
    }

    @Test
    void anUnoccupiedAvailableResourceIsAvailableAndSelectable() {
        DisplayedStatus status = resolve(DISPONIBLE);

        assertThat(status).isEqualTo(DisplayedStatus.DISPONIBLE);
        assertThat(status.selectable()).isTrue();
        assertThat(status.reason()).isNull();
    }

    @Test
    void onlyAvailableIsSelectableAndEveryOtherStatusExplainsItself() {
        for (DisplayedStatus status : DisplayedStatus.values()) {
            assertThat(status.selectable()).isEqualTo(status == DisplayedStatus.DISPONIBLE);
            assertThat(status.reason() == null).isEqualTo(status == DisplayedStatus.DISPONIBLE);
        }
    }

    @Test
    void maintenanceWinsOverEverythingAndAppliesToAnyTimeSlot() {
        assertThat(resolve(EN_MANTENIMIENTO)).isEqualTo(DisplayedStatus.EN_MANTENIMIENTO);
        assertThat(resolve(EN_MANTENIMIENTO, occupancy("R", OccupancyType.ACADEMIC_BLOCK, 10, 12)))
                .isEqualTo(DisplayedStatus.EN_MANTENIMIENTO);
    }

    @Test
    void anAcademicBlockWinsOverAReservation() {
        assertThat(resolve(DISPONIBLE,
                occupancy("R", OccupancyType.SPACE_RESERVATION, 10, 11),
                occupancy("R", OccupancyType.ACADEMIC_BLOCK, 11, 12)))
                .isEqualTo(DisplayedStatus.BLOQUEO_ACADEMICO);
    }

    @Test
    void aReservationOrAHeldLoanIsReserved() {
        assertThat(resolve(DISPONIBLE, occupancy("R", OccupancyType.SPACE_RESERVATION, 10, 12)))
                .isEqualTo(DisplayedStatus.RESERVADO);
        assertThat(resolve(DISPONIBLE, occupancy("R", OccupancyType.LOAN_HELD, 9, 11)))
                .isEqualTo(DisplayedStatus.RESERVADO);
    }

    @Test
    void aPickedUpLoanIsInUseAndWinsTheTieWithAReservation() {
        assertThat(resolve(DISPONIBLE,
                occupancy("R", OccupancyType.SPACE_RESERVATION, 10, 11),
                occupancy("R", OccupancyType.LOAN_PICKED_UP, 9, 13)))
                .isEqualTo(DisplayedStatus.EN_USO);
    }

    @Test
    void anOpenEndedLoanOccupiesEvenAfterItsOriginalEnd() {
        Occupancy overdue = new Occupancy("R", OccupancyType.LOAN_PICKED_UP, at(7, 0), null);

        assertThat(resolve(DISPONIBLE, overdue)).isEqualTo(DisplayedStatus.EN_USO);
    }

    @Test
    void touchingIntervalsDoNotOverlap() {
        assertThat(resolve(DISPONIBLE,
                occupancy("R", OccupancyType.SPACE_RESERVATION, 8, 10),
                occupancy("R", OccupancyType.SPACE_RESERVATION, 12, 14)))
                .isEqualTo(DisplayedStatus.DISPONIBLE);
    }

    @Test
    void aOneMinuteOverlapIsEnoughToBeReserved() {
        Occupancy tail = new Occupancy("R", OccupancyType.SPACE_RESERVATION, at(8, 0), at(10, 1));

        assertThat(resolve(DISPONIBLE, tail)).isEqualTo(DisplayedStatus.RESERVADO);
    }

    @Test
    void operationalInUseOnlyCountsWhenTheSlotIncludesTheCurrentMoment() {
        assertThat(DisplayedStatus.resolve(EN_USO, List.of(), slot(9, 11), NOW)).isEqualTo(DisplayedStatus.EN_USO);
        assertThat(DisplayedStatus.resolve(EN_USO, List.of(), slot(14, 16), NOW))
                .isEqualTo(DisplayedStatus.DISPONIBLE);
    }
}
