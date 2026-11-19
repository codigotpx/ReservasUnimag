package edu.unimagdalena.reservasunimag.domain.usecase.importschedule;

import edu.unimagdalena.reservasunimag.domain.model.academicblock.AcademicBlock;
import edu.unimagdalena.reservasunimag.domain.model.academicblock.ClassSession;
import edu.unimagdalena.reservasunimag.domain.model.error.ResourceTakenException;
import edu.unimagdalena.reservasunimag.domain.model.error.ScheduleUploadNotFoundException;
import edu.unimagdalena.reservasunimag.domain.model.error.UploadNotApplicableException;
import edu.unimagdalena.reservasunimag.domain.model.error.UploadNotYoursException;
import edu.unimagdalena.reservasunimag.domain.model.reservation.CancellationOrigin;
import edu.unimagdalena.reservasunimag.domain.model.upload.ApplyResult;
import edu.unimagdalena.reservasunimag.domain.model.upload.ScheduleUpload;
import edu.unimagdalena.reservasunimag.domain.model.upload.UploadStatus;
import edu.unimagdalena.reservasunimag.domain.model.upload.ValidatedRow;
import edu.unimagdalena.reservasunimag.domain.model.user.CurrentUser;
import edu.unimagdalena.reservasunimag.domain.port.in.ApplySchedulePort;
import edu.unimagdalena.reservasunimag.domain.port.in.CancelReservationPort;
import edu.unimagdalena.reservasunimag.domain.port.out.Clock;
import edu.unimagdalena.reservasunimag.domain.port.out.ScheduleUploadRepositoryPort;
import edu.unimagdalena.reservasunimag.domain.port.out.TransactionPort;
import edu.unimagdalena.reservasunimag.domain.usecase.reserveresources.AcademicBlockCreator;

import java.time.Instant;
import java.util.UUID;

/**
 * UC3, paso 2. Se vuelve a calcular el plan con los datos de ahora y se aplica solo si el impacto es exactamente el que
 * la persona aprobó: si cambió, se rechaza en vez de cancelarle la reserva a alguien que no estaba en la lista
 * (IMPACT_CHANGED). Todo va en una transacción —retirar, cancelar y bloquear—, así que la restricción de exclusión
 * nunca ve dos ocupaciones confirmadas a la vez y una carga nunca queda a medias (FR-008).
 */
public class ApplyScheduleUseCase implements ApplySchedulePort {

    private final ScheduleUploadRepositoryPort uploads;
    private final SchedulePlanner planner;
    private final CancelReservationPort cancelReservation;
    private final AcademicBlockCreator blockCreator;
    private final TransactionPort transactions;
    private final Clock clock;

    public ApplyScheduleUseCase(ScheduleUploadRepositoryPort uploads, SchedulePlanner planner,
                                CancelReservationPort cancelReservation, AcademicBlockCreator blockCreator,
                                TransactionPort transactions, Clock clock) {
        this.uploads = uploads;
        this.planner = planner;
        this.cancelReservation = cancelReservation;
        this.blockCreator = blockCreator;
        this.transactions = transactions;
        this.clock = clock;
    }

    @Override
    public ScheduleUpload confirm(CurrentUser user, UUID uploadId) {
        ScheduleUpload upload = uploads.findById(uploadId)
                .orElseThrow(() -> new ScheduleUploadNotFoundException(uploadId));
        if (!user.id().equals(upload.uploadedBy())) {
            throw new UploadNotYoursException();
        }
        Instant now = clock.now();
        requireConfirmable(upload, now);

        SchedulePlan plan = planner.plan(upload.academicTerm(), upload.termStart(), upload.termEnd(),
                upload.rows().stream().map(ValidatedRow::toRaw).toList(), now);
        int atValidation = upload.impact().reservationsToCancel().size();
        if (!plan.applicable() || !plan.impact().equals(upload.impact())) {
            throw UploadNotApplicableException.impactChanged(atValidation, plan.displaced().size());
        }

        try {
            return transactions.required(() -> {
                ScheduleUpload locked = uploads.lockById(uploadId)
                        .orElseThrow(() -> new ScheduleUploadNotFoundException(uploadId));
                if (locked.status() == UploadStatus.APPLIED) {
                    throw UploadNotApplicableException.alreadyApplied(locked.appliedAt());
                }
                for (AcademicBlock block : plan.toWithdraw()) {
                    cancelReservation.cancelBySystem(block.reservationId(), CancellationOrigin.SCHEDULE_WITHDRAWN,
                            "La clase " + block.academic().label() + " salió del horario " + upload.academicTerm()
                                    + ".");
                }
                plan.displaced().forEach((reservationId, academic) -> cancelReservation.cancelBySystem(reservationId,
                        CancellationOrigin.ACADEMIC_PRIORITY,
                        "La franja se asignó a actividad docente: " + academic.label() + "."));
                for (ClassSession session : plan.toCreate()) {
                    blockCreator.create(session, plan.resources().get(session.resourceId()), uploadId,
                            upload.academicTerm());
                }
                int cancelled = plan.toWithdraw().size() + plan.displaced().size();
                ScheduleUpload applied = locked.applied(clock.now(), new ApplyResult(plan.toCreate().size(),
                        plan.unchanged(), plan.toWithdraw().size(), plan.displaced().size(), cancelled));
                uploads.markApplied(applied);
                return applied;
            });
        } catch (ResourceTakenException taken) {
            // Alguien tomó una franja entre la recomprobación y la escritura: lo aplicado ya no sería lo aprobado.
            throw UploadNotApplicableException.impactChanged(atValidation, atValidation + 1);
        }
    }

    private void requireConfirmable(ScheduleUpload upload, Instant now) {
        switch (upload.status()) {
            case APPLIED -> throw UploadNotApplicableException.alreadyApplied(upload.appliedAt());
            case REJECTED -> throw UploadNotApplicableException.notApplicable(
                    upload.summary().rowsWithBlockingVerdict());
            case EXPIRED -> throw UploadNotApplicableException.expired(upload.expiresAt());
            case VALIDATED -> {
                if (now.isAfter(upload.expiresAt())) {
                    transactions.required(() -> {
                        uploads.markExpired(upload.id(), now);
                        return null;
                    });
                    throw UploadNotApplicableException.expired(upload.expiresAt());
                }
            }
        }
    }
}
