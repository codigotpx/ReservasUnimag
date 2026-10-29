package edu.unimagdalena.reservasunimag.domain.usecase.importschedule;

import edu.unimagdalena.reservasunimag.domain.model.error.UploadNotApplicableException;
import edu.unimagdalena.reservasunimag.domain.model.error.UploadNotApplicableException.Code;
import edu.unimagdalena.reservasunimag.domain.model.error.UploadNotYoursException;
import edu.unimagdalena.reservasunimag.domain.model.reservation.CancellationOrigin;
import edu.unimagdalena.reservasunimag.domain.model.reservation.ReservationOrigin;
import edu.unimagdalena.reservasunimag.domain.model.reservation.ReservationStatus;
import edu.unimagdalena.reservasunimag.domain.model.resource.StatusChangeReason;
import edu.unimagdalena.reservasunimag.domain.model.upload.RowVerdict;
import edu.unimagdalena.reservasunimag.domain.model.upload.ScheduleFile;
import edu.unimagdalena.reservasunimag.domain.model.upload.ScheduleUpload;
import edu.unimagdalena.reservasunimag.domain.model.upload.UploadStatus;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDate;

import static edu.unimagdalena.reservasunimag.domain.usecase.importschedule.ScheduleTestBed.TERM_END;
import static edu.unimagdalena.reservasunimag.domain.usecase.importschedule.ScheduleTestBed.TERM_START;
import static edu.unimagdalena.reservasunimag.support.ResourceFixtures.DIRECTOR;
import static edu.unimagdalena.reservasunimag.support.ResourceFixtures.STUDENT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** UC3, paso 2: aplicar exactamente lo validado, y los cuatro motivos para no hacerlo. */
class ApplyScheduleUseCaseTest {

    private final ScheduleTestBed bed = new ScheduleTestBed();

    private ScheduleUpload validate() {
        return bed.validate.validate(DIRECTOR, new ScheduleFile("horario.csv", new byte[0]), "2026-2", TERM_START,
                TERM_END);
    }

    private long academicReservations() {
        return bed.reservations.stored.values().stream()
                .filter(r -> r.origin() == ReservationOrigin.ACADEMICO && r.status() == ReservationStatus.CONFIRMADA)
                .count();
    }

    @Test
    void confirmingCreatesTheBlocksThroughUc2AndCancelsTheDisplacedReservations() {
        var displaced = bed.student("ESP-0412", LocalDate.of(2026, 9, 7), 9, 11);
        bed.row("IS-402", "1", "ESP-0412", "LUNES", "08:00", "10:00");
        ScheduleUpload validated = validate();

        ScheduleUpload applied = bed.apply.confirm(DIRECTOR, validated.id());

        assertThat(applied.status()).isEqualTo(UploadStatus.APPLIED);
        assertThat(applied.result().blocksCreated()).isEqualTo(4);
        assertThat(applied.result().reservationsCancelled()).isEqualTo(1);
        assertThat(applied.result().eventsQueued()).isEqualTo(1);
        assertThat(academicReservations()).isEqualTo(4);
        assertThat(bed.cancellations.calls).singleElement().satisfies(call -> {
            assertThat(call.reservationId()).isEqualTo(displaced.id());
            assertThat(call.origin()).isEqualTo(CancellationOrigin.ACADEMIC_PRIORITY);
            assertThat(call.reason()).isEqualTo("La franja se asignó a actividad docente: IS-402 grupo 1.");
        });
        // Cada sesión pasa por UC7 y por UC10, como cualquier reserva.
        assertThat(bed.statusUpdates.reasons()).containsOnly(StatusChangeReason.ACADEMIC_BLOCK_CREATED).hasSize(4);
        assertThat(bed.notifier.created).hasSize(4).allSatisfy(record -> {
            assertThat(record.holder()).isNull();
            assertThat(record.academic().courseCode()).isEqualTo("IS-402");
        });
        assertThat(bed.uploads.stored.get(validated.id()).status()).isEqualTo(UploadStatus.APPLIED);
    }

    @Test
    void reloadingTheSameFileDuplicatesNothing() {
        bed.row("IS-402", "1", "ESP-0412", "LUNES", "08:00", "10:00");
        bed.apply.confirm(DIRECTOR, validate().id());

        ScheduleUpload reloaded = validate();

        assertThat(reloaded.rows().get(0).verdict()).isEqualTo(RowVerdict.UNCHANGED);
        assertThat(reloaded.summary().sessionsToCreate()).isZero();
        assertThat(bed.apply.confirm(DIRECTOR, reloaded.id()).result().blocksUnchanged()).isEqualTo(4);
        assertThat(academicReservations()).isEqualTo(4);
    }

    @Test
    void aClassThatLeftTheFileIsWithdrawnAndCanComeBackLater() {
        bed.row("IS-402", "1", "ESP-0412", "LUNES", "08:00", "10:00");
        bed.row("IS-510", "1", "ESP-0201", "JUEVES", "14:00", "16:00");
        bed.apply.confirm(DIRECTOR, validate().id());
        bed.file.remove(1);

        ScheduleUpload withoutIt = validate();
        assertThat(withoutIt.summary().blocksToWithdraw()).isEqualTo(4);
        assertThat(withoutIt.requiresConfirmation()).isTrue();
        bed.apply.confirm(DIRECTOR, withoutIt.id());

        assertThat(bed.cancellations.calls).hasSize(4).allSatisfy(call -> {
            assertThat(call.origin()).isEqualTo(CancellationOrigin.SCHEDULE_WITHDRAWN);
            assertThat(call.reason()).isEqualTo("La clase IS-510 grupo 1 salió del horario 2026-2.");
        });
        assertThat(academicReservations()).isEqualTo(4);

        bed.row("IS-510", "1", "ESP-0201", "JUEVES", "14:00", "16:00");
        assertThat(bed.apply.confirm(DIRECTOR, validate().id()).result().blocksCreated()).isEqualTo(4);
        assertThat(academicReservations()).isEqualTo(8);
    }

    @Test
    void anUploadThatWasNotApplicableIsRejectedAgain() {
        bed.row("FI-202", "2", "ESP-9999", "MIERCOLES", "10:00", "12:00");
        ScheduleUpload rejected = validate();

        assertThatThrownBy(() -> bed.apply.confirm(DIRECTOR, rejected.id()))
                .isInstanceOfSatisfying(UploadNotApplicableException.class, error -> {
                    assertThat(error.code()).isEqualTo(Code.UPLOAD_NOT_APPLICABLE);
                    assertThat(error.extensions()).containsEntry("rowsWithBlockingVerdict", 1);
                });
    }

    @Test
    void anExpiredUploadMustBeValidatedAgain() {
        bed.row("IS-402", "1", "ESP-0412", "LUNES", "08:00", "10:00");
        ScheduleUpload validated = validate();
        bed.now = bed.now.plus(Duration.ofMinutes(31));

        assertThatThrownBy(() -> bed.apply.confirm(DIRECTOR, validated.id()))
                .isInstanceOfSatisfying(UploadNotApplicableException.class,
                        error -> assertThat(error.code()).isEqualTo(Code.UPLOAD_EXPIRED));
        assertThat(bed.uploads.stored.get(validated.id()).status()).isEqualTo(UploadStatus.EXPIRED);
        assertThat(academicReservations()).isZero();
    }

    @Test
    void confirmingTwiceDoesNotApplyTwice() {
        bed.row("IS-402", "1", "ESP-0412", "LUNES", "08:00", "10:00");
        ScheduleUpload validated = validate();
        bed.apply.confirm(DIRECTOR, validated.id());

        assertThatThrownBy(() -> bed.apply.confirm(DIRECTOR, validated.id()))
                .isInstanceOfSatisfying(UploadNotApplicableException.class,
                        error -> assertThat(error.code()).isEqualTo(Code.UPLOAD_ALREADY_APPLIED));
        assertThat(academicReservations()).isEqualTo(4);
    }

    @Test
    void anImpactThatGrewBetweenValidatingAndConfirmingCancelsNothing() {
        bed.student("ESP-0412", LocalDate.of(2026, 9, 7), 9, 11);
        bed.row("IS-402", "1", "ESP-0412", "LUNES", "08:00", "10:00");
        ScheduleUpload validated = validate();
        var newcomer = bed.student("ESP-0412", LocalDate.of(2026, 9, 14), 8, 9);

        assertThatThrownBy(() -> bed.apply.confirm(DIRECTOR, validated.id()))
                .isInstanceOfSatisfying(UploadNotApplicableException.class, error -> {
                    assertThat(error.code()).isEqualTo(Code.IMPACT_CHANGED);
                    assertThat(error.extensions()).containsEntry("reservationsToCancelAtValidation", 1)
                            .containsEntry("reservationsToCancelNow", 2);
                    assertThat(error.getMessage()).contains("ahora se cancelarían 2 reservas en vez de 1");
                });
        assertThat(bed.cancellations.calls).isEmpty();
        assertThat(bed.reservations.stored.get(newcomer.id()).status()).isEqualTo(ReservationStatus.CONFIRMADA);
    }

    @Test
    void onlyWhoValidatedCanConfirm() {
        bed.row("IS-402", "1", "ESP-0412", "LUNES", "08:00", "10:00");
        ScheduleUpload validated = validate();

        assertThatThrownBy(() -> bed.apply.confirm(STUDENT, validated.id()))
                .isInstanceOf(UploadNotYoursException.class);
    }
}
