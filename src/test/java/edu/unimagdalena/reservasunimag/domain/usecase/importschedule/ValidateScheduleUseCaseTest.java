package edu.unimagdalena.reservasunimag.domain.usecase.importschedule;

import edu.unimagdalena.reservasunimag.domain.model.academicblock.AcademicData;
import edu.unimagdalena.reservasunimag.domain.model.academicblock.BlockType;
import edu.unimagdalena.reservasunimag.domain.model.academicblock.ClassSession;
import edu.unimagdalena.reservasunimag.domain.model.error.ExternalServiceUnavailableException;
import edu.unimagdalena.reservasunimag.domain.model.error.InvalidScheduleFileException;
import edu.unimagdalena.reservasunimag.domain.model.upload.RowVerdict;
import edu.unimagdalena.reservasunimag.domain.model.upload.ScheduleFile;
import edu.unimagdalena.reservasunimag.domain.model.upload.ScheduleUpload;
import edu.unimagdalena.reservasunimag.domain.model.upload.UploadStatus;
import edu.unimagdalena.reservasunimag.domain.model.upload.ValidatedRow;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDate;

import static edu.unimagdalena.reservasunimag.domain.usecase.importschedule.ScheduleTestBed.TERM_END;
import static edu.unimagdalena.reservasunimag.domain.usecase.importschedule.ScheduleTestBed.TERM_START;
import static edu.unimagdalena.reservasunimag.domain.usecase.importschedule.ScheduleTestBed.at;
import static edu.unimagdalena.reservasunimag.support.ResourceFixtures.DIRECTOR;
import static edu.unimagdalena.reservasunimag.support.ResourceFixtures.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** UC3, paso 1: el reporte fila por fila, y que validar nunca cambia ninguna reserva. */
class ValidateScheduleUseCaseTest {

    private final ScheduleTestBed bed = new ScheduleTestBed();

    private ScheduleUpload validate() {
        return bed.validate.validate(DIRECTOR, new ScheduleFile("horario.csv", new byte[0]), "2026-2", TERM_START,
                TERM_END);
    }

    private static ValidatedRow row(ScheduleUpload upload, int number) {
        return upload.rows().stream().filter(row -> row.row() == number).findFirst().orElseThrow();
    }

    @Test
    void aCleanFileIsApplicableAndCreatesNothingYet() {
        bed.row("IS-402", "1", "ESP-0412", "LUNES", "08:00", "10:00");
        bed.row("IS-510", "1", "ESP-0201", "JUEVES", "14:00", "17:00");

        ScheduleUpload upload = validate();

        assertThat(upload.status()).isEqualTo(UploadStatus.VALIDATED);
        assertThat(upload.applicable()).isTrue();
        assertThat(upload.requiresConfirmation()).isFalse();
        assertThat(upload.expiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(30)));
        assertThat(upload.summary().sessionsToCreate()).isEqualTo(8);
        assertThat(row(upload, 2).verdict()).isEqualTo(RowVerdict.OK);
        assertThat(row(upload, 2).sessions()).isEqualTo(4);
        assertThat(row(upload, 2).resourceName()).isEqualTo("Laboratorio de Redes");
        assertThat(bed.reservations.stored).isEmpty();
        assertThat(bed.uploads.stored).containsKey(upload.id());
    }

    @Test
    void oneBlockingRowMakesTheWholeUploadNotApplicableAndAllProblemsAreListed() {
        bed.row("IS-402", "1", "ESP-0412", "LUNES", "08:00", "10:00");
        bed.row("FI-202", "2", "ESP-9999", "MIERCOLES", "10:00", "12:00");
        bed.row("IS-404", "1", "ESP-0412", "LUNES", "9:3", "11:30");
        bed.row("QU-101", "1", "ESP-0220", "MARTES", "08:00", "10:00");

        ScheduleUpload upload = validate();

        assertThat(upload.status()).isEqualTo(UploadStatus.REJECTED);
        assertThat(upload.applicable()).isFalse();
        assertThat(upload.expiresAt()).isNull();
        assertThat(upload.summary().rowsWithBlockingVerdict()).isEqualTo(3);
        assertThat(upload.summary().sessionsToCreate()).isZero();
        assertThat(row(upload, 3).verdict()).isEqualTo(RowVerdict.REJECTED_RESOURCE);
        assertThat(row(upload, 4).verdict()).isEqualTo(RowVerdict.REJECTED_FORMAT);
        assertThat(row(upload, 4).column()).isEqualTo("start_time");
        assertThat(row(upload, 5).verdict()).isEqualTo(RowVerdict.RESOURCE_UNDER_MAINTENANCE);
        assertThat(bed.reservations.stored).isEmpty();
    }

    @Test
    void aStudentReservationInTheWayIsListedWithItsHolderAndRequiresConfirmation() {
        var displaced = bed.student("ESP-0412", LocalDate.of(2026, 9, 7), 9, 11);
        bed.row("IS-402", "1", "ESP-0412", "LUNES", "08:00", "10:00");

        ScheduleUpload upload = validate();

        assertThat(row(upload, 2).verdict()).isEqualTo(RowVerdict.DISPLACES_RESERVATIONS);
        assertThat(row(upload, 2).affected()).singleElement().satisfies(affected -> {
            assertThat(affected.reservationId()).isEqualTo(displaced.id());
            assertThat(affected.holder().name()).isEqualTo("Nombre del estudiante");
        });
        assertThat(upload.requiresConfirmation()).isTrue();
        assertThat(upload.summary().reservationsToCancel()).isEqualTo(1);
        assertThat(upload.impact().reservationsToCancel()).containsExactly(displaced.id());
        // Validar no toca la reserva de nadie (FR-008).
        assertThat(bed.reservations.stored.get(displaced.id()).status().name()).isEqualTo("CONFIRMADA");
    }

    @Test
    void anAssetAlreadyInHandsCannotBeTakenByAClass() {
        bed.loanInHands("ACT-000913", LocalDate.of(2026, 8, 31), 3);
        bed.row("BI-101", "1", "ACT-000913", "MIERCOLES", "08:00", "10:00");

        ScheduleUpload upload = validate();

        assertThat(row(upload, 2).verdict()).isEqualTo(RowVerdict.ASSET_ALREADY_PICKED_UP);
        assertThat(row(upload, 2).problem()).contains("hasta el 2026-09-03").doesNotContain("2019114045");
    }

    @Test
    void aClassNeverDisplacesAnotherClassFromTheFileOrFromBefore() {
        bed.row("IS-402", "1", "ESP-0412", "LUNES", "08:00", "10:00");
        bed.row("IS-301", "1", "ESP-0412", "LUNES", "09:00", "11:00");

        ScheduleUpload upload = validate();

        assertThat(row(upload, 3).verdict()).isEqualTo(RowVerdict.ACADEMIC_CLASH);
        assertThat(row(upload, 3).conflictingBlock().label()).isEqualTo("IS-402 grupo 1");
        assertThat(row(upload, 3).problem()).isEqualTo("Se cruza con otra clase ya bloqueada en este recurso: "
                + "IS-402 grupo 1.");

        // Un bloqueo extraordinario ya creado tampoco se desplaza.
        var other = new ScheduleTestBed();
        other.blockCreator.create(new ClassSession("ESP-0201", at(LocalDate.of(2026, 9, 10), 15),
                        at(LocalDate.of(2026, 9, 10), 16), new AcademicData("EXT-001", "Visita", "1", "Sistemas", null),
                        BlockType.EXTRAORDINARIO, null), other.catalog.get("ESP-0201"), null, null);
        other.row("IS-510", "1", "ESP-0201", "JUEVES", "14:00", "16:00");
        var clashing = other.validate.validate(DIRECTOR, new ScheduleFile("h.csv", new byte[0]), "2026-2",
                TERM_START, TERM_END);
        assertThat(clashing.rows().get(0).verdict()).isEqualTo(RowVerdict.ACADEMIC_CLASH);
    }

    @Test
    void theTermMustEndAfterItStarts() {
        assertThatThrownBy(() -> bed.validate.validate(DIRECTOR, new ScheduleFile("h.csv", new byte[0]), "2026-2",
                TERM_END, TERM_START)).isInstanceOfSatisfying(InvalidScheduleFileException.class,
                invalid -> assertThat(invalid.code()).isEqualTo(InvalidScheduleFileException.Code.INVALID_TERM));
    }

    @Test
    void withoutModule1NothingCanBeValidated() {
        bed.row("IS-402", "1", "ESP-0412", "LUNES", "08:00", "10:00");
        bed.inventoryDown = true;

        assertThatThrownBy(this::validate).isInstanceOf(ExternalServiceUnavailableException.class);
        assertThat(bed.uploads.stored).isEmpty();
    }
}
