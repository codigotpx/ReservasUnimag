package edu.unimagdalena.reservasunimag.domain.usecase.importschedule;

import edu.unimagdalena.reservasunimag.domain.model.academicblock.AcademicBlock;
import edu.unimagdalena.reservasunimag.domain.model.academicblock.AcademicData;
import edu.unimagdalena.reservasunimag.domain.model.academicblock.ClassSession;
import edu.unimagdalena.reservasunimag.domain.model.resource.Resource;
import edu.unimagdalena.reservasunimag.domain.model.upload.RowVerdict;
import edu.unimagdalena.reservasunimag.domain.model.upload.UploadImpact;
import edu.unimagdalena.reservasunimag.domain.model.upload.UploadSummary;
import edu.unimagdalena.reservasunimag.domain.model.upload.ValidatedRow;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Lo que una carga haría si se aplicara ahora: el reporte por fila, las sesiones a crear, los bloqueos que salen del
 * horario y las reservas estudiantiles desplazadas, cada una con la clase que la desplaza.
 */
record SchedulePlan(List<ValidatedRow> rows, List<ClassSession> toCreate, int unchanged, int skippedNonBusinessDay,
                    List<AcademicBlock> toWithdraw, Map<UUID, AcademicData> displaced,
                    Map<String, Resource> resources) {

    boolean applicable() {
        return rows.stream().noneMatch(row -> row.verdict().blocking());
    }

    UploadImpact impact() {
        if (!applicable()) {
            return UploadImpact.NONE;
        }
        return new UploadImpact(displaced.keySet(),
                toWithdraw.stream().map(AcademicBlock::reservationId).collect(Collectors.toSet()));
    }

    UploadSummary summary() {
        boolean applicable = applicable();
        return new UploadSummary(
                rows.size(),
                count(RowVerdict.OK) + count(RowVerdict.DISPLACES_RESERVATIONS),
                count(RowVerdict.UNCHANGED),
                (int) rows.stream().filter(row -> row.verdict().blocking()).count(),
                applicable ? toCreate.size() : 0,
                applicable ? skippedNonBusinessDay : 0,
                applicable ? toWithdraw.size() : 0,
                applicable ? displaced.size() : 0);
    }

    private int count(RowVerdict verdict) {
        return (int) rows.stream().filter(row -> row.verdict() == verdict).count();
    }
}
