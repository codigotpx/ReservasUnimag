package edu.unimagdalena.reservasunimag.support;

import edu.unimagdalena.reservasunimag.domain.model.reservation.Occupancy;
import edu.unimagdalena.reservasunimag.domain.model.reservation.OccupancyType;
import edu.unimagdalena.reservasunimag.domain.model.reservation.TimeSlot;
import edu.unimagdalena.reservasunimag.domain.model.resource.OperationalStatus;
import edu.unimagdalena.reservasunimag.domain.model.resource.Resource;
import edu.unimagdalena.reservasunimag.domain.model.user.CurrentUser;
import edu.unimagdalena.reservasunimag.domain.model.user.Role;
import edu.unimagdalena.reservasunimag.domain.port.out.Clock;
import edu.unimagdalena.reservasunimag.domain.port.out.OccupancyRepositoryPort;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** Datos y puertos falsos compartidos por las pruebas de los casos de uso. */
public final class ResourceFixtures {

    public static final LocalDate DAY = LocalDate.of(2026, 9, 1);
    /** 09:14 en Bogotá del día de las pruebas. */
    public static final Instant NOW = DAY.atTime(9, 14).atZone(TimeSlot.ZONE).toInstant();
    public static final Clock CLOCK = () -> NOW;
    public static final TimeSlot TEN_TO_TWELVE = slot(10, 12);

    public static final CurrentUser STUDENT =
            new CurrentUser(UUID.fromString("00000000-0000-0000-0000-000000000001"), "2021114001", Role.ESTUDIANTE);
    public static final CurrentUser MONITOR =
            new CurrentUser(UUID.fromString("00000000-0000-0000-0000-000000000002"), "2020114002", Role.MONITOR);
    public static final CurrentUser DIRECTOR =
            new CurrentUser(UUID.fromString("00000000-0000-0000-0000-000000000003"), "DP-0001",
                    Role.DIRECCION_PROGRAMA);

    private ResourceFixtures() {
    }

    public static TimeSlot slot(int startHour, int endHour) {
        return new TimeSlot(DAY, LocalTime.of(startHour, 0), LocalTime.of(endHour, 0));
    }

    public static Instant at(int hour, int minute) {
        return DAY.atTime(hour, minute).atZone(TimeSlot.ZONE).toInstant();
    }

    public static Resource space(String id, String name, int capacity) {
        return space(id, name, capacity, OperationalStatus.DISPONIBLE);
    }

    public static Resource space(String id, String name, int capacity, OperationalStatus status) {
        return Resource.space(id, name, "LABORATORIO", "Bloque 7", status, "Ingeniería", capacity,
                List.of("PROYECTOR"));
    }

    public static Resource asset(String id, String name) {
        return Resource.asset(id, name, "MICROSCOPIO", "Laboratorio", OperationalStatus.DISPONIBLE, "BUENO", 3);
    }

    public static Occupancy occupancy(String resourceId, OccupancyType type, int startHour, int endHour) {
        return new Occupancy(resourceId, type, at(startHour, 0), at(endHour, 0));
    }

    /** Ocupaciones en memoria; filtra por recurso y por cruce, como lo hace el adaptador de persistencia. */
    public static final class InMemoryOccupancies implements OccupancyRepositoryPort {

        private final List<Occupancy> occupancies;
        public int queries;

        public InMemoryOccupancies(Occupancy... occupancies) {
            this.occupancies = List.of(occupancies);
        }

        @Override
        public List<Occupancy> findOccupancies(Collection<String> resourceIds, Instant from, Instant to) {
            queries++;
            return occupancies.stream()
                    .filter(occupancy -> resourceIds.contains(occupancy.resourceId()))
                    .filter(occupancy -> occupancy.overlaps(from, to))
                    .toList();
        }
    }
}
