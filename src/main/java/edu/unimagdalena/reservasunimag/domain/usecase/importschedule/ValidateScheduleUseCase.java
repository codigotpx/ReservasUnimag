package edu.unimagdalena.reservasunimag.domain.usecase.importschedule;

import edu.unimagdalena.reservasunimag.domain.model.error.InvalidScheduleFileException;
import edu.unimagdalena.reservasunimag.domain.model.upload.ScheduleFile;
import edu.unimagdalena.reservasunimag.domain.model.upload.ScheduleUpload;
import edu.unimagdalena.reservasunimag.domain.model.upload.UploadStatus;
import edu.unimagdalena.reservasunimag.domain.model.user.CurrentUser;
import edu.unimagdalena.reservasunimag.domain.port.in.ValidateSchedulePort;
import edu.unimagdalena.reservasunimag.domain.port.out.Clock;
import edu.unimagdalena.reservasunimag.domain.port.out.ScheduleReader;
import edu.unimagdalena.reservasunimag.domain.port.out.ScheduleUploadRepositoryPort;
import edu.unimagdalena.reservasunimag.domain.port.out.TransactionPort;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * UC3, paso 1. El reporte lista todas las filas con problema de una vez, para corregir el archivo en una sola pasada
 * (escenario 2). La carga queda guardada —aplicable o no— como constancia (FR-009), y una aplicable vence a los
 * {@code validity}: pasado ese plazo el impacto que se mostró ya no es de fiar.
 */
public class ValidateScheduleUseCase implements ValidateSchedulePort {

    private final ScheduleReader reader;
    private final SchedulePlanner planner;
    private final ScheduleUploadRepositoryPort uploads;
    private final TransactionPort transactions;
    private final Clock clock;
    private final Duration validity;

    public ValidateScheduleUseCase(ScheduleReader reader, SchedulePlanner planner,
                                   ScheduleUploadRepositoryPort uploads, TransactionPort transactions, Clock clock,
                                   Duration validity) {
        this.reader = reader;
        this.planner = planner;
        this.uploads = uploads;
        this.transactions = transactions;
        this.clock = clock;
        this.validity = validity;
    }

    @Override
    public ScheduleUpload validate(CurrentUser user, ScheduleFile file, String academicTerm, LocalDate termStart,
                                   LocalDate termEnd) {
        if (!termEnd.isAfter(termStart)) {
            throw InvalidScheduleFileException.invalidTerm();
        }
        var rows = reader.read(file.content());
        Instant now = clock.now();
        SchedulePlan plan = planner.plan(academicTerm, termStart, termEnd, rows, now);
        boolean applicable = plan.applicable();
        ScheduleUpload upload = new ScheduleUpload(UUID.randomUUID(), academicTerm, termStart, termEnd,
                file.fileName(), user.id(), null, applicable ? UploadStatus.VALIDATED : UploadStatus.REJECTED, now,
                applicable ? now.plus(validity) : null, null, plan.summary(), plan.impact(), null, plan.rows());
        transactions.required(() -> {
            uploads.insert(upload);
            return null;
        });
        return upload;
    }
}
