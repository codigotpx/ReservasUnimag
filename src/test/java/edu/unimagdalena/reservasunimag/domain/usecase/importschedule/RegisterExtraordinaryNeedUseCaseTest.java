package edu.unimagdalena.reservasunimag.domain.usecase.importschedule;

import edu.unimagdalena.reservasunimag.domain.model.academicblock.AcademicData;
import edu.unimagdalena.reservasunimag.domain.model.academicblock.BlockType;
import edu.unimagdalena.reservasunimag.domain.model.academicblock.ExtraordinaryNeed;
import edu.unimagdalena.reservasunimag.domain.model.academicblock.ExtraordinaryNeedResult;
import edu.unimagdalena.reservasunimag.domain.model.error.AcademicConflictException;
import edu.unimagdalena.reservasunimag.domain.model.error.ResourceNotFoundException;
import edu.unimagdalena.reservasunimag.domain.model.reservation.CancellationOrigin;
import edu.unimagdalena.reservasunimag.domain.model.reservation.ReservationOrigin;
import edu.unimagdalena.reservasunimag.domain.model.reservation.ReservationStatus;
import edu.unimagdalena.reservasunimag.domain.model.upload.RowVerdict;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** UC3 FR-005: la necesidad extraordinaria, que se impone sobre las reservas estudiantiles y sobre nada más. */
class RegisterExtraordinaryNeedUseCaseTest {

    private static final LocalDate DAY = LocalDate.of(2026, 9, 10);
    private static final AcademicData VISIT = new AcademicData("EXT-001", "Visita de pares académicos", "1",
            "Ingeniería de Sistemas", "Nombre del instructor");

    private final ScheduleTestBed bed = new ScheduleTestBed();
    private final RegisterExtraordinaryNeedUseCase useCase = new RegisterExtraordinaryNeedUseCase(bed.inventory,
            bed.academicBlocks, bed.reservations, bed.users, bed.cancellations, bed.blockCreator, bed.transactions);

    private static ExtraordinaryNeed need(String resourceId, int startHour, int endHour) {
        return new ExtraordinaryNeed(resourceId, DAY, LocalTime.of(startHour, 0), LocalTime.of(endHour, 0), VISIT);
    }

    @Test
    void withoutConfirmNothingChangesAndTheImpactIsShown() {
        var displaced = bed.student("ESP-0201", DAY, 14, 16);

        ExtraordinaryNeedResult preview = useCase.register(need("ESP-0201", 14, 16), false);

        assertThat(preview.applied()).isFalse();
        assertThat(preview.verdict()).isEqualTo(RowVerdict.DISPLACES_RESERVATIONS);
        assertThat(preview.resourceName()).isEqualTo("Auditorio Principal");
        assertThat(preview.affected()).singleElement()
                .satisfies(affected -> assertThat(affected.reservationId()).isEqualTo(displaced.id()));
        assertThat(bed.reservations.stored.get(displaced.id()).status()).isEqualTo(ReservationStatus.CONFIRMADA);
        assertThat(bed.academicBlocks.stored).isEmpty();
    }

    @Test
    void confirmingCancelsTheStudentReservationAndCreatesTheExtraordinaryBlock() {
        var displaced = bed.student("ESP-0201", DAY, 14, 16);

        ExtraordinaryNeedResult applied = useCase.register(need("ESP-0201", 14, 16), true);

        assertThat(applied.applied()).isTrue();
        assertThat(applied.block().origin()).isEqualTo(ReservationOrigin.ACADEMICO);
        assertThat(bed.academicBlocks.stored.get(applied.block().id()).type()).isEqualTo(BlockType.EXTRAORDINARIO);
        assertThat(bed.academicBlocks.scheduleOf.get(applied.block().id())).isNull();
        assertThat(bed.cancellations.calls).singleElement().satisfies(call -> {
            assertThat(call.reservationId()).isEqualTo(displaced.id());
            assertThat(call.origin()).isEqualTo(CancellationOrigin.ACADEMIC_PRIORITY);
        });
    }

    @Test
    void anotherClassIsNotDisplaced() {
        useCase.register(need("ESP-0201", 8, 10), true);

        assertThatThrownBy(() -> useCase.register(need("ESP-0201", 9, 11), true))
                .isInstanceOfSatisfying(AcademicConflictException.class, conflict -> {
                    assertThat(conflict.verdict()).isEqualTo(RowVerdict.ACADEMIC_CLASH);
                    assertThat(conflict.conflictingBlock().label()).isEqualTo("EXT-001 grupo 1");
                    assertThat(conflict.getMessage()).contains("de 08:00 a 10:00");
                });
        assertThat(bed.academicBlocks.stored).hasSize(1);
    }

    @Test
    void anAssetAlreadyInHandsIsNotTakenAway() {
        bed.loanInHands("ACT-000913", DAY.minusDays(1), 3);

        assertThatThrownBy(() -> useCase.register(need("ACT-000913", 8, 10), true))
                .isInstanceOfSatisfying(AcademicConflictException.class,
                        conflict -> assertThat(conflict.verdict()).isEqualTo(RowVerdict.ASSET_ALREADY_PICKED_UP));
    }

    @Test
    void aThreeHourNeedIsFineBecauseTheTwoHourCapIsForStudents() {
        assertThat(useCase.register(need("ESP-0412", 14, 17), true).applied()).isTrue();
    }

    @Test
    void aResourceUnderMaintenanceOrUnknownCannotBeUsed() {
        assertThatThrownBy(() -> useCase.register(need("ESP-0220", 8, 10), false))
                .isInstanceOfSatisfying(AcademicConflictException.class,
                        conflict -> assertThat(conflict.verdict()).isEqualTo(RowVerdict.RESOURCE_UNDER_MAINTENANCE));
        assertThatThrownBy(() -> useCase.register(need("NO-EXISTE", 8, 10), false))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
