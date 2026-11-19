package edu.unimagdalena.reservasunimag.domain.usecase.importschedule;

import edu.unimagdalena.reservasunimag.domain.model.error.ExternalServiceUnavailableException;
import edu.unimagdalena.reservasunimag.domain.model.error.ExternalServiceUnavailableException.ExternalModule;
import edu.unimagdalena.reservasunimag.domain.model.reservation.Loan;
import edu.unimagdalena.reservasunimag.domain.model.reservation.Reservation;
import edu.unimagdalena.reservasunimag.domain.model.resource.Catalog;
import edu.unimagdalena.reservasunimag.domain.model.resource.OperationalStatus;
import edu.unimagdalena.reservasunimag.domain.model.resource.Resource;
import edu.unimagdalena.reservasunimag.domain.model.resource.ResourceCategory;
import edu.unimagdalena.reservasunimag.domain.model.resource.ResourceFilter;
import edu.unimagdalena.reservasunimag.domain.model.upload.RawScheduleRow;
import edu.unimagdalena.reservasunimag.domain.model.user.Role;
import edu.unimagdalena.reservasunimag.domain.model.user.User;
import edu.unimagdalena.reservasunimag.domain.port.out.InventoryPort;
import edu.unimagdalena.reservasunimag.domain.port.out.TransactionPort;
import edu.unimagdalena.reservasunimag.domain.port.out.UserRepositoryPort;
import edu.unimagdalena.reservasunimag.domain.usecase.reportreservation.ReportReservationUseCase;
import edu.unimagdalena.reservasunimag.domain.usecase.reserveresources.AcademicBlockCreator;
import edu.unimagdalena.reservasunimag.support.CancellationFakes.InMemoryReservations;
import edu.unimagdalena.reservasunimag.support.CancellationFakes.RecordingNotifier;
import edu.unimagdalena.reservasunimag.support.RecordFakes.InMemoryReservationRecords;
import edu.unimagdalena.reservasunimag.support.RecordingStatusUpdates;
import edu.unimagdalena.reservasunimag.support.ScheduleFakes.InMemoryAcademicBlocks;
import edu.unimagdalena.reservasunimag.support.ScheduleFakes.InMemoryScheduleUploads;
import edu.unimagdalena.reservasunimag.support.ScheduleFakes.RecordingCancellations;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import static edu.unimagdalena.reservasunimag.support.ResourceFixtures.NOW;
import static edu.unimagdalena.reservasunimag.support.ResourceFixtures.STUDENT;
import static edu.unimagdalena.reservasunimag.support.ResourceFixtures.asset;
import static edu.unimagdalena.reservasunimag.support.ResourceFixtures.space;

/**
 * Todo lo que necesitan las pruebas de UC3 con puertos falsos. Hoy es el martes 2026-09-01 a las 09:14, y el periodo
 * va de ese día al 30 de septiembre: una clase de lunes tiene cuatro sesiones (7, 14, 21 y 28).
 */
class ScheduleTestBed {

    static final LocalDate TERM_START = LocalDate.of(2026, 9, 1);
    static final LocalDate TERM_END = LocalDate.of(2026, 9, 30);
    static final String HEADER = "course_code,course,group,program,instructor,resource_id,day_of_week,start_time,"
            + "end_time";

    Instant now = NOW;
    boolean inventoryDown;
    final Map<String, Resource> catalog = new HashMap<>(Map.of(
            "ESP-0412", space("ESP-0412", "Laboratorio de Redes", 30),
            "ESP-0201", space("ESP-0201", "Auditorio Principal", 200),
            "ESP-0220", space("ESP-0220", "Laboratorio de Química", 30, OperationalStatus.EN_MANTENIMIENTO),
            "ACT-000913", asset("ACT-000913", "Microscopio M-015")));

    final InventoryPort inventory = new InventoryPort() {
        @Override
        public Catalog searchCatalog(ResourceFilter filter) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<Resource> details(String resourceId) {
            if (inventoryDown) {
                throw new ExternalServiceUnavailableException(ExternalModule.MODULO_1, "caído", null);
            }
            return Optional.ofNullable(catalog.get(resourceId));
        }

        @Override
        public Map<String, OperationalStatus> operationalStatus(Collection<String> resourceIds) {
            throw new UnsupportedOperationException();
        }
    };

    final UserRepositoryPort users = new UserRepositoryPort() {
        @Override
        public Optional<User> findByEmail(String email) {
            return Optional.empty();
        }

        @Override
        public Optional<User> findById(UUID id) {
            return Optional.of(new User(id, "2019114045", "Nombre del estudiante", "e@unimag.edu.co", "hash",
                    Role.ESTUDIANTE, true, NOW));
        }
    };

    final TransactionPort transactions = new TransactionPort() {
        @Override
        public <T> T required(Supplier<T> work) {
            return work.get();
        }
    };

    final InMemoryReservations reservations = new InMemoryReservations();
    final InMemoryAcademicBlocks academicBlocks = new InMemoryAcademicBlocks(reservations);
    final InMemoryScheduleUploads uploads = new InMemoryScheduleUploads();
    final RecordingNotifier notifier = new RecordingNotifier();
    final InMemoryReservationRecords records = new InMemoryReservationRecords();
    final RecordingStatusUpdates statusUpdates = new RecordingStatusUpdates();
    final RecordingCancellations cancellations = new RecordingCancellations(reservations, () -> now);
    final AcademicBlockCreator blockCreator = new AcademicBlockCreator(reservations, academicBlocks, statusUpdates,
            new ReportReservationUseCase(records, notifier, () -> now), () -> now);
    final SchedulePlanner planner = new SchedulePlanner(inventory, academicBlocks, reservations, users,
            new SessionExpander(date -> date.getDayOfWeek() != DayOfWeek.SATURDAY
                    && date.getDayOfWeek() != DayOfWeek.SUNDAY));
    final List<RawScheduleRow> file = new ArrayList<>();
    final ValidateScheduleUseCase validate = new ValidateScheduleUseCase(content -> List.copyOf(file), planner,
            uploads, transactions, () -> now, Duration.ofMinutes(30));
    final ApplyScheduleUseCase apply = new ApplyScheduleUseCase(uploads, planner, cancellations, blockCreator,
            transactions, () -> now);

    /** Agrega una fila al archivo con su número real: la primera de datos es la 2. */
    void row(String courseCode, String group, String resourceId, String day, String start, String end) {
        Map<String, String> values = new HashMap<>(Map.of(
                RawScheduleRow.COURSE_CODE, courseCode, RawScheduleRow.COURSE, "Curso " + courseCode,
                RawScheduleRow.GROUP, group, RawScheduleRow.PROGRAM, "Ingeniería de Sistemas",
                RawScheduleRow.RESOURCE_ID, resourceId, RawScheduleRow.DAY_OF_WEEK, day,
                RawScheduleRow.START_TIME, start, RawScheduleRow.END_TIME, end));
        file.add(new RawScheduleRow(file.size() + 2, values));
    }

    /** Una reserva estudiantil confirmada en el recurso, el día y las horas dados. */
    Reservation student(String resourceId, LocalDate date, int startHour, int endHour) {
        return reservations.add(Reservation.confirmed(UUID.randomUUID(), STUDENT.id(), resourceId,
                ResourceCategory.ESPACIO, at(date, startHour), at(date, endHour), NOW.minusSeconds(3600), null));
    }

    Reservation loanInHands(String resourceId, LocalDate pickup, int days) {
        return reservations.add(Reservation.confirmed(UUID.randomUUID(), STUDENT.id(), resourceId,
                ResourceCategory.ACTIVO, at(pickup, 8), at(pickup, 8).plus(Duration.ofDays(days)),
                NOW.minusSeconds(3600), new Loan(3, at(pickup, 8), null)));
    }

    static Instant at(LocalDate date, int hour) {
        return date.atTime(hour, 0).atZone(edu.unimagdalena.reservasunimag.domain.model.reservation.TimeSlot.ZONE)
                .toInstant();
    }
}
