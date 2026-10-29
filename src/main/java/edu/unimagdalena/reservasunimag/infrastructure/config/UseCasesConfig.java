package edu.unimagdalena.reservasunimag.infrastructure.config;

import edu.unimagdalena.reservasunimag.domain.model.compliance.NoShowPolicy;
import edu.unimagdalena.reservasunimag.domain.model.reservation.CancellationPolicy;
import edu.unimagdalena.reservasunimag.domain.port.in.ApplySchedulePort;
import edu.unimagdalena.reservasunimag.domain.port.in.CancelForMaintenancePort;
import edu.unimagdalena.reservasunimag.domain.port.in.CancelReservationPort;
import edu.unimagdalena.reservasunimag.domain.port.in.CheckAvailabilityPort;
import edu.unimagdalena.reservasunimag.domain.port.in.ListMyReservationsPort;
import edu.unimagdalena.reservasunimag.domain.port.in.QueryCancellationReportsPort;
import edu.unimagdalena.reservasunimag.domain.port.in.QueryPendingCheckOutsPort;
import edu.unimagdalena.reservasunimag.domain.port.in.QueryScheduleUploadsPort;
import edu.unimagdalena.reservasunimag.domain.port.in.RegisterExtraordinaryNeedPort;
import edu.unimagdalena.reservasunimag.domain.port.in.ValidateSchedulePort;
import edu.unimagdalena.reservasunimag.domain.port.in.QueryStatusChangesPort;
import edu.unimagdalena.reservasunimag.domain.port.in.RegisterUseStartedPort;
import edu.unimagdalena.reservasunimag.domain.port.in.UpdateResourceStatusPort;
import edu.unimagdalena.reservasunimag.domain.port.in.ReceiveCheckOutPort;
import edu.unimagdalena.reservasunimag.domain.port.in.ReceiveNoShowPort;
import edu.unimagdalena.reservasunimag.domain.port.in.ReportCancellationPort;
import edu.unimagdalena.reservasunimag.domain.port.in.ReportReservationPort;
import edu.unimagdalena.reservasunimag.domain.port.in.ReserveResourcesPort;
import edu.unimagdalena.reservasunimag.domain.port.in.CheckSanctionsPort;
import edu.unimagdalena.reservasunimag.domain.port.in.GetProfilePort;
import edu.unimagdalena.reservasunimag.domain.port.in.SearchResourcesPort;
import edu.unimagdalena.reservasunimag.domain.port.in.SignInPort;
import edu.unimagdalena.reservasunimag.domain.port.in.VoidNoShowPort;
import edu.unimagdalena.reservasunimag.domain.port.out.AbsenceRepositoryPort;
import edu.unimagdalena.reservasunimag.domain.port.out.Clock;
import edu.unimagdalena.reservasunimag.domain.port.out.AcademicBlockRepositoryPort;
import edu.unimagdalena.reservasunimag.domain.port.out.BusinessCalendar;
import edu.unimagdalena.reservasunimag.domain.port.out.CancellationReportRepositoryPort;
import edu.unimagdalena.reservasunimag.domain.port.out.CheckOutRepositoryPort;
import edu.unimagdalena.reservasunimag.domain.port.out.CompliancePort;
import edu.unimagdalena.reservasunimag.domain.port.out.DenialRepositoryPort;
import edu.unimagdalena.reservasunimag.domain.port.out.Module3NotifierPort;
import edu.unimagdalena.reservasunimag.domain.port.out.ReservationRepositoryPort;
import edu.unimagdalena.reservasunimag.domain.port.out.ScheduleReader;
import edu.unimagdalena.reservasunimag.domain.port.out.ScheduleUploadRepositoryPort;
import edu.unimagdalena.reservasunimag.domain.port.out.TransactionPort;
import edu.unimagdalena.reservasunimag.domain.port.out.InventoryPort;
import edu.unimagdalena.reservasunimag.domain.port.out.InventoryStatusNotifierPort;
import edu.unimagdalena.reservasunimag.domain.port.out.StatusChangeRepositoryPort;
import edu.unimagdalena.reservasunimag.domain.port.out.OccupancyRepositoryPort;
import edu.unimagdalena.reservasunimag.domain.port.out.PasswordHasherPort;
import edu.unimagdalena.reservasunimag.domain.port.out.UserRepositoryPort;
import edu.unimagdalena.reservasunimag.domain.usecase.auth.GetProfileUseCase;
import edu.unimagdalena.reservasunimag.domain.usecase.auth.SignInUseCase;
import edu.unimagdalena.reservasunimag.domain.usecase.cancelreservation.CancelForMaintenanceUseCase;
import edu.unimagdalena.reservasunimag.domain.usecase.cancelreservation.CancelReservationUseCase;
import edu.unimagdalena.reservasunimag.domain.usecase.cancelreservation.ReservationCanceller;
import edu.unimagdalena.reservasunimag.domain.usecase.checkavailability.CheckAvailabilityUseCase;
import edu.unimagdalena.reservasunimag.domain.usecase.checksanctions.CheckSanctionsUseCase;
import edu.unimagdalena.reservasunimag.domain.usecase.importschedule.ApplyScheduleUseCase;
import edu.unimagdalena.reservasunimag.domain.usecase.importschedule.QueryScheduleUploadsUseCase;
import edu.unimagdalena.reservasunimag.domain.usecase.importschedule.RegisterExtraordinaryNeedUseCase;
import edu.unimagdalena.reservasunimag.domain.usecase.importschedule.SchedulePlanner;
import edu.unimagdalena.reservasunimag.domain.usecase.importschedule.SessionExpander;
import edu.unimagdalena.reservasunimag.domain.usecase.importschedule.ValidateScheduleUseCase;
import edu.unimagdalena.reservasunimag.domain.usecase.listreservations.ListMyReservationsUseCase;
import edu.unimagdalena.reservasunimag.domain.usecase.receivecheckout.CheckOutValidator;
import edu.unimagdalena.reservasunimag.domain.usecase.receivecheckout.QueryPendingCheckOutsUseCase;
import edu.unimagdalena.reservasunimag.domain.usecase.receivecheckout.ReceiveCheckOutUseCase;
import edu.unimagdalena.reservasunimag.domain.usecase.receivenoshow.ReceiveNoShowUseCase;
import edu.unimagdalena.reservasunimag.domain.usecase.receivenoshow.VoidNoShowUseCase;
import edu.unimagdalena.reservasunimag.domain.usecase.reportcancellation.QueryCancellationReportsUseCase;
import edu.unimagdalena.reservasunimag.domain.usecase.reportcancellation.ReportCancellationUseCase;
import edu.unimagdalena.reservasunimag.domain.usecase.reportreservation.ReportReservationUseCase;
import edu.unimagdalena.reservasunimag.domain.usecase.reserveresources.AcademicBlockCreator;
import edu.unimagdalena.reservasunimag.domain.usecase.reserveresources.DueDateCalculator;
import edu.unimagdalena.reservasunimag.domain.usecase.reserveresources.ReserveResourcesUseCase;
import edu.unimagdalena.reservasunimag.domain.usecase.searchresources.NextFreeSlotFinder;
import edu.unimagdalena.reservasunimag.domain.usecase.searchresources.SearchResourcesUseCase;
import edu.unimagdalena.reservasunimag.domain.usecase.updatestatus.QueryStatusChangesUseCase;
import edu.unimagdalena.reservasunimag.domain.usecase.updatestatus.RegisterUseStartedUseCase;
import edu.unimagdalena.reservasunimag.domain.usecase.updatestatus.UpdateResourceStatusUseCase;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Los casos de uso no llevan {@code @Service}: se registran aquí, y aquí se decide su transacción. */
@Configuration(proxyBeanMethods = false)
class UseCasesConfig {

    @Bean
    SignInPort signInPort(UserRepositoryPort users, PasswordHasherPort passwordHasher) {
        return new SignInUseCase(users, passwordHasher);
    }

    @Bean
    GetProfilePort getProfilePort(UserRepositoryPort users) {
        return new GetProfileUseCase(users);
    }

    @Bean
    CheckAvailabilityPort checkAvailabilityPort(OccupancyRepositoryPort occupancies, Clock clock) {
        return new CheckAvailabilityUseCase(occupancies, clock);
    }

    @Bean
    CheckSanctionsPort checkSanctionsPort(CompliancePort compliance) {
        return new CheckSanctionsUseCase(compliance);
    }

    @Bean
    NextFreeSlotFinder nextFreeSlotFinder(OccupancyRepositoryPort occupancies, Clock clock,
                                          ReservationProperties properties) {
        return new NextFreeSlotFinder(occupancies, clock, properties.nextSlotStep());
    }

    @Bean
    SearchResourcesPort searchResourcesPort(InventoryPort inventory, CheckAvailabilityPort availability,
                                            CheckSanctionsPort sanctions, NextFreeSlotFinder nextFreeSlot,
                                            Clock clock) {
        return new SearchResourcesUseCase(inventory, availability, sanctions, nextFreeSlot, clock);
    }

    @Bean
    ReportReservationPort reportReservationPort(Module3NotifierPort notifier, Clock clock) {
        return new ReportReservationUseCase(notifier, clock);
    }

    @Bean
    UpdateResourceStatusPort updateResourceStatusPort(StatusChangeRepositoryPort statusChanges,
                                                      InventoryStatusNotifierPort inventoryNotifier, Clock clock,
                                                      ReservationProperties properties) {
        return new UpdateResourceStatusUseCase(statusChanges, inventoryNotifier, clock,
                properties.module1().notifyUseStarted());
    }

    @Bean
    RegisterUseStartedPort registerUseStartedPort(ReservationRepositoryPort reservations,
                                                  StatusChangeRepositoryPort statusChanges,
                                                  UpdateResourceStatusPort updateStatus, InventoryPort inventory,
                                                  TransactionPort transactions, Clock clock,
                                                  ReservationProperties properties) {
        return new RegisterUseStartedUseCase(reservations, statusChanges, updateStatus, inventory, transactions, clock,
                properties.minCancellationNotice());
    }

    @Bean
    QueryStatusChangesPort queryStatusChangesPort(StatusChangeRepositoryPort statusChanges, InventoryPort inventory) {
        return new QueryStatusChangesUseCase(statusChanges, inventory);
    }

    @Bean
    AcademicBlockCreator academicBlockCreator(ReservationRepositoryPort reservations,
                                              AcademicBlockRepositoryPort academicBlocks,
                                              UpdateResourceStatusPort updateStatus,
                                              ReportReservationPort reportReservation, Clock clock) {
        return new AcademicBlockCreator(reservations, academicBlocks, updateStatus, reportReservation, clock);
    }

    @Bean
    SchedulePlanner schedulePlanner(InventoryPort inventory, AcademicBlockRepositoryPort academicBlocks,
                                    ReservationRepositoryPort reservations, UserRepositoryPort users,
                                    BusinessCalendar calendar) {
        return new SchedulePlanner(inventory, academicBlocks, reservations, users, new SessionExpander(calendar));
    }

    @Bean
    ValidateSchedulePort validateSchedulePort(ScheduleReader reader, SchedulePlanner planner,
                                              ScheduleUploadRepositoryPort uploads, TransactionPort transactions,
                                              Clock clock, ReservationProperties properties) {
        return new ValidateScheduleUseCase(reader, planner, uploads, transactions, clock,
                properties.upload().validity());
    }

    @Bean
    ApplySchedulePort applySchedulePort(ScheduleUploadRepositoryPort uploads, SchedulePlanner planner,
                                        CancelReservationPort cancelReservation, AcademicBlockCreator blockCreator,
                                        TransactionPort transactions, Clock clock) {
        return new ApplyScheduleUseCase(uploads, planner, cancelReservation, blockCreator, transactions, clock);
    }

    @Bean
    QueryScheduleUploadsPort queryScheduleUploadsPort(ScheduleUploadRepositoryPort uploads) {
        return new QueryScheduleUploadsUseCase(uploads);
    }

    @Bean
    RegisterExtraordinaryNeedPort registerExtraordinaryNeedPort(InventoryPort inventory,
                                                                AcademicBlockRepositoryPort academicBlocks,
                                                                ReservationRepositoryPort reservations,
                                                                UserRepositoryPort users,
                                                                CancelReservationPort cancelReservation,
                                                                AcademicBlockCreator blockCreator,
                                                                TransactionPort transactions) {
        return new RegisterExtraordinaryNeedUseCase(inventory, academicBlocks, reservations, users, cancelReservation,
                blockCreator, transactions);
    }

    @Bean
    DueDateCalculator dueDateCalculator(BusinessCalendar calendar) {
        return new DueDateCalculator(calendar);
    }

    @Bean
    ReserveResourcesPort reserveResourcesPort(InventoryPort inventory, CheckSanctionsPort sanctions,
                                              CheckAvailabilityPort availability,
                                              ReservationRepositoryPort reservations, DenialRepositoryPort denials,
                                              UserRepositoryPort users, ReportReservationPort reportReservation,
                                              UpdateResourceStatusPort updateStatus, DueDateCalculator dueDates,
                                              TransactionPort transactions, Clock clock,
                                              ReservationProperties properties) {
        return new ReserveResourcesUseCase(inventory, sanctions, availability, reservations, denials, users,
                reportReservation, updateStatus, dueDates, transactions, clock, properties.maxQuota(),
                properties.maxSpaceDuration());
    }

    @Bean
    CancellationPolicy cancellationPolicy(ReservationProperties properties) {
        return new CancellationPolicy(properties.minCancellationNotice());
    }

    @Bean
    ListMyReservationsPort listMyReservationsPort(ReservationRepositoryPort reservations, InventoryPort inventory,
                                                  AbsenceRepositoryPort absences,
                                                  CancellationPolicy cancellationPolicy, Clock clock,
                                                  ReservationProperties properties) {
        return new ListMyReservationsUseCase(reservations, inventory, absences, cancellationPolicy, clock,
                properties.maxQuota());
    }

    @Bean
    ReceiveNoShowPort receiveNoShowPort(ReservationRepositoryPort reservations, AbsenceRepositoryPort absences,
                                        UserRepositoryPort users, Module3NotifierPort notifier,
                                        UpdateResourceStatusPort updateStatus, TransactionPort transactions,
                                        Clock clock, ReservationProperties properties) {
        return new ReceiveNoShowUseCase(reservations, absences, users, notifier, updateStatus,
                new NoShowPolicy(properties.noShowGrace()), transactions, clock);
    }

    @Bean
    ReceiveCheckOutPort receiveCheckOutPort(ReservationRepositoryPort reservations, CheckOutRepositoryPort checkOuts,
                                            AbsenceRepositoryPort absences,
                                            CancelForMaintenancePort cancelForMaintenance,
                                            Module3NotifierPort notifier, UpdateResourceStatusPort updateStatus,
                                            TransactionPort transactions, Clock clock) {
        return new ReceiveCheckOutUseCase(reservations, checkOuts, new CheckOutValidator(absences),
                cancelForMaintenance, notifier, updateStatus, transactions, clock);
    }

    @Bean
    QueryPendingCheckOutsPort queryPendingCheckOutsPort(CheckOutRepositoryPort checkOuts, InventoryPort inventory,
                                                        Clock clock, ReservationProperties properties) {
        return new QueryPendingCheckOutsUseCase(checkOuts, inventory, clock, properties.loanLossThreshold());
    }

    @Bean
    VoidNoShowPort voidNoShowPort(AbsenceRepositoryPort absences, TransactionPort transactions, Clock clock) {
        return new VoidNoShowUseCase(absences, transactions, clock);
    }

    @Bean
    ReportCancellationPort reportCancellationPort(CancellationReportRepositoryPort reports,
                                                  Module3NotifierPort notifier) {
        return new ReportCancellationUseCase(reports, notifier);
    }

    @Bean
    QueryCancellationReportsPort queryCancellationReportsPort(CancellationReportRepositoryPort reports,
                                                              InventoryPort inventory) {
        return new QueryCancellationReportsUseCase(reports, inventory);
    }

    @Bean
    ReservationCanceller reservationCanceller(ReservationRepositoryPort reservations, UserRepositoryPort users,
                                              InventoryPort inventory, ReportCancellationPort reportCancellation,
                                              UpdateResourceStatusPort updateStatus) {
        return new ReservationCanceller(reservations, users, inventory, reportCancellation, updateStatus);
    }

    @Bean
    CancelReservationPort cancelReservationPort(ReservationRepositoryPort reservations,
                                                ReservationCanceller canceller, CancellationPolicy policy,
                                                TransactionPort transactions, Clock clock,
                                                ReservationProperties properties) {
        return new CancelReservationUseCase(reservations, canceller, policy, transactions, clock,
                properties.maxQuota());
    }

    @Bean
    CancelForMaintenancePort cancelForMaintenancePort(ReservationRepositoryPort reservations,
                                                      UserRepositoryPort users, InventoryPort inventory,
                                                      ReservationCanceller canceller, TransactionPort transactions,
                                                      Clock clock) {
        return new CancelForMaintenanceUseCase(reservations, users, inventory, canceller, transactions, clock);
    }
}
