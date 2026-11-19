package edu.unimagdalena.reservasunimag.domain.usecase.importschedule;

import edu.unimagdalena.reservasunimag.domain.model.academicblock.AcademicBlock;
import edu.unimagdalena.reservasunimag.domain.model.academicblock.AcademicData;
import edu.unimagdalena.reservasunimag.domain.model.academicblock.ClassSession;
import edu.unimagdalena.reservasunimag.domain.model.reservation.Reservation;
import edu.unimagdalena.reservasunimag.domain.model.reservation.ReservationHolder;
import edu.unimagdalena.reservasunimag.domain.model.reservation.TimeSlot;
import edu.unimagdalena.reservasunimag.domain.model.resource.OperationalStatus;
import edu.unimagdalena.reservasunimag.domain.model.resource.Resource;
import edu.unimagdalena.reservasunimag.domain.model.upload.AffectedReservation;
import edu.unimagdalena.reservasunimag.domain.model.upload.RawScheduleRow;
import edu.unimagdalena.reservasunimag.domain.model.upload.RowVerdict;
import edu.unimagdalena.reservasunimag.domain.model.upload.ValidatedRow;
import edu.unimagdalena.reservasunimag.domain.port.out.AcademicBlockRepositoryPort;
import edu.unimagdalena.reservasunimag.domain.port.out.InventoryPort;
import edu.unimagdalena.reservasunimag.domain.port.out.ReservationRepositoryPort;
import edu.unimagdalena.reservasunimag.domain.port.out.UserRepositoryPort;
import edu.unimagdalena.reservasunimag.domain.usecase.importschedule.ScheduleRowParser.ParsedRow;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * El dictamen de cada fila y el impacto de la carga. Lo usan los dos pasos: validar lo muestra y confirmar lo vuelve a
 * calcular para comprobar que no cambió. Se le pregunta al Módulo 1 una vez por recurso distinto —no por fila ni por
 * sesión— y lo que ya ocupa los recursos se lee con una consulta por lote.
 * <p>
 * Una recarga del mismo periodo reconcilia por la huella de cada sesión (FR-007): la que ya existe queda igual, la
 * nueva se crea y la que estaba y ya no viene se retira. Lo que se retira no cuenta como choque para lo que lo
 * reemplaza.
 */
public class SchedulePlanner {

    private static final DateTimeFormatter HOUR = DateTimeFormatter.ofPattern("HH:mm");

    private final InventoryPort inventory;
    private final AcademicBlockRepositoryPort academicBlocks;
    private final ReservationRepositoryPort reservations;
    private final UserRepositoryPort users;
    private final SessionExpander expander;

    public SchedulePlanner(InventoryPort inventory, AcademicBlockRepositoryPort academicBlocks,
                    ReservationRepositoryPort reservations, UserRepositoryPort users, SessionExpander expander) {
        this.inventory = inventory;
        this.academicBlocks = academicBlocks;
        this.reservations = reservations;
        this.users = users;
        this.expander = expander;
    }

    SchedulePlan plan(String academicTerm, LocalDate termStart, LocalDate termEnd, List<RawScheduleRow> rawRows,
                      Instant now) {
        List<ParsedRow> parsed = rawRows.stream().map(ScheduleRowParser::parse).toList();

        // Una sola pregunta por recurso distinto. Si el Módulo 1 no responde, falla la carga entera.
        Map<String, Optional<Resource>> resources = new LinkedHashMap<>();
        for (ParsedRow row : parsed) {
            if (row.valid()) {
                resources.computeIfAbsent(row.weekly().resourceId(), inventory::details);
            }
        }

        Map<Integer, SessionExpander.Expansion> expansions = new HashMap<>();
        Set<String> fileKeys = new HashSet<>();
        Instant to = now;
        for (ParsedRow row : parsed) {
            if (row.valid() && usable(resources.get(row.weekly().resourceId()))) {
                var expansion = expander.expand(row.weekly(), academicTerm, termStart, termEnd, now);
                expansions.put(row.raw().row(), expansion);
                for (ClassSession session : expansion.sessions()) {
                    fileKeys.add(session.sessionKey());
                    if (session.endsAt().isAfter(to)) {
                        to = session.endsAt();
                    }
                }
            }
        }

        Map<String, AcademicBlock> existing = academicBlocks.findUpcomingRegular(academicTerm, now).stream()
                .filter(block -> block.sessionKey() != null)
                .collect(Collectors.toMap(AcademicBlock::sessionKey, block -> block, (a, b) -> a));
        List<AcademicBlock> toWithdraw = existing.values().stream()
                .filter(block -> !fileKeys.contains(block.sessionKey()))
                .toList();
        Set<UUID> withdrawn = toWithdraw.stream().map(AcademicBlock::reservationId).collect(Collectors.toSet());

        List<String> resourceIds = resources.keySet().stream().toList();
        List<AcademicBlock> blocks = resourceIds.isEmpty() || !to.isAfter(now) ? List.of()
                : academicBlocks.findConfirmed(resourceIds, now, to).stream()
                .filter(block -> !withdrawn.contains(block.reservationId()))
                .toList();
        List<Reservation> students = resourceIds.isEmpty() || !to.isAfter(now) ? List.of()
                : reservations.findStudentOccupying(resourceIds, now, to);

        List<ValidatedRow> rows = new ArrayList<>();
        List<ClassSession> toCreate = new ArrayList<>();
        Map<UUID, AcademicData> displaced = new LinkedHashMap<>();
        Map<String, List<ClassSession>> accepted = new HashMap<>();
        Map<UUID, ReservationHolder> holders = new HashMap<>();
        int unchanged = 0;
        int skipped = 0;

        for (ParsedRow row : parsed) {
            RawScheduleRow raw = row.raw();
            if (!row.valid()) {
                rows.add(validated(raw, null, RowVerdict.REJECTED_FORMAT, 0, row.problem(), row.column(), null,
                        List.of()));
                continue;
            }
            String resourceId = row.weekly().resourceId();
            Optional<Resource> resource = resources.get(resourceId);
            String name = resource.map(Resource::name).orElse(null);
            if (resource.isEmpty()) {
                rows.add(validated(raw, null, RowVerdict.REJECTED_RESOURCE, 0,
                        "El recurso " + resourceId + " no existe en el inventario.", RawScheduleRow.RESOURCE_ID, null,
                        List.of()));
                continue;
            }
            if (resource.get().operationalStatus() == OperationalStatus.EN_MANTENIMIENTO) {
                rows.add(validated(raw, name, RowVerdict.RESOURCE_UNDER_MAINTENANCE, 0,
                        "El recurso está en mantenimiento. Esta clase necesita otro espacio.", null, null, List.of()));
                continue;
            }

            var expansion = expansions.get(raw.row());
            skipped += expansion.skippedNonBusinessDay();
            AcademicData clash = null;
            Reservation untouchable = null;
            Map<UUID, AffectedReservation> affected = new LinkedHashMap<>();
            List<ClassSession> rowCreate = new ArrayList<>();
            int rowUnchanged = 0;
            for (ClassSession session : expansion.sessions()) {
                if (existing.containsKey(session.sessionKey())) {
                    rowUnchanged++;
                    accepted.computeIfAbsent(resourceId, key -> new ArrayList<>()).add(session);
                    continue;
                }
                clash = clashWith(session, accepted.getOrDefault(resourceId, List.of()), blocks);
                if (clash != null) {
                    break;
                }
                for (Reservation student : students) {
                    if (SlotConflicts.occupies(student, session)) {
                        if (SlotConflicts.untouchable(student)) {
                            untouchable = student;
                        } else {
                            affected.putIfAbsent(student.id(), affectedOf(student, holders));
                        }
                    }
                }
                rowCreate.add(session);
            }

            if (clash != null) {
                rows.add(validated(raw, name, RowVerdict.ACADEMIC_CLASH, 0, "Se cruza con otra clase ya bloqueada "
                        + "en este recurso: " + clash.label() + ".", null, clash, List.of()));
                continue;
            }
            if (untouchable != null) {
                rows.add(validated(raw, name, RowVerdict.ASSET_ALREADY_PICKED_UP, 0, untouchableProblem(untouchable),
                        null, null, List.of()));
                continue;
            }
            accepted.computeIfAbsent(resourceId, key -> new ArrayList<>()).addAll(rowCreate);
            toCreate.addAll(rowCreate);
            unchanged += rowUnchanged;
            affected.keySet().forEach(id -> displaced.putIfAbsent(id, row.weekly().academic()));
            RowVerdict verdict = !affected.isEmpty() ? RowVerdict.DISPLACES_RESERVATIONS
                    : rowCreate.isEmpty() && rowUnchanged > 0 ? RowVerdict.UNCHANGED
                    : RowVerdict.OK;
            String note = rowCreate.isEmpty() && rowUnchanged == 0
                    ? "El periodo no tiene ninguna sesión futura en día hábil para esta clase." : null;
            rows.add(validated(raw, name, verdict, rowCreate.size() + rowUnchanged, note, null, null,
                    List.copyOf(affected.values())));
        }

        Map<String, Resource> found = new HashMap<>();
        resources.forEach((id, resource) -> resource.ifPresent(r -> found.put(id, r)));
        return new SchedulePlan(rows, toCreate, unchanged, skipped, toWithdraw, displaced, found);
    }

    /** La primera clase con la que se cruza: otra fila del mismo archivo, o un bloqueo ya existente. */
    private static AcademicData clashWith(ClassSession session, List<ClassSession> sameFile,
                                          List<AcademicBlock> blocks) {
        for (ClassSession other : sameFile) {
            if (session.overlaps(other.startsAt(), other.endsAt())) {
                return other.academic();
            }
        }
        for (AcademicBlock block : blocks) {
            if (block.resourceId().equals(session.resourceId()) && block.overlaps(session.startsAt(), session.endsAt())
                    && !java.util.Objects.equals(block.sessionKey(), session.sessionKey())) {
                return block.academic();
            }
        }
        return null;
    }

    private AffectedReservation affectedOf(Reservation reservation, Map<UUID, ReservationHolder> holders) {
        ReservationHolder holder = holders.computeIfAbsent(reservation.userId(),
                id -> users.findById(id).map(ReservationHolder::of).orElse(null));
        return new AffectedReservation(reservation.id(), holder, reservation.startsAt(), reservation.endsAt(),
                reservation.category());
    }

    /** Dice hasta cuándo está comprometido, pero no quién lo tiene (UC8 FR-005). */
    private static String untouchableProblem(Reservation reservation) {
        if (reservation.inHands()) {
            var due = reservation.endsAt().atZone(TimeSlot.ZONE);
            return "El activo ya está en manos de una persona hasta el " + due.toLocalDate() + " a las "
                    + due.format(HOUR) + ". Hay que pedirlo de vuelta antes de bloquearlo para la clase.";
        }
        return "La reserva que ocupa esa franja ya está en uso. Hay que resolverlo con quien la tiene antes de "
                + "bloquearla para la clase.";
    }

    private static boolean usable(Optional<Resource> resource) {
        return resource.isPresent() && resource.get().operationalStatus() != OperationalStatus.EN_MANTENIMIENTO;
    }

    private static ValidatedRow validated(RawScheduleRow raw, String resourceName, RowVerdict verdict, int sessions,
                                          String problem, String column, AcademicData conflicting,
                                          List<AffectedReservation> affected) {
        String instructor = raw.value(RawScheduleRow.INSTRUCTOR);
        return new ValidatedRow(raw.row(), raw.value(RawScheduleRow.RESOURCE_ID), resourceName,
                raw.value(RawScheduleRow.COURSE_CODE), raw.value(RawScheduleRow.COURSE),
                raw.value(RawScheduleRow.GROUP),
                raw.value(RawScheduleRow.PROGRAM), instructor.isEmpty() ? null : instructor,
                raw.value(RawScheduleRow.DAY_OF_WEEK), raw.value(RawScheduleRow.START_TIME),
                raw.value(RawScheduleRow.END_TIME), verdict, sessions, problem, column, conflicting, affected);
    }
}
