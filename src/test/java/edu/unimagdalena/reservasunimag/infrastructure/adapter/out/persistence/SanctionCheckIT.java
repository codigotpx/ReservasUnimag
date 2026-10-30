package edu.unimagdalena.reservasunimag.infrastructure.adapter.out.persistence;

import edu.unimagdalena.reservasunimag.TestcontainersConfiguration;
import edu.unimagdalena.reservasunimag.domain.model.common.PageRequest;
import edu.unimagdalena.reservasunimag.domain.model.compliance.ComplianceReport;
import edu.unimagdalena.reservasunimag.domain.model.compliance.SanctionCheck;
import edu.unimagdalena.reservasunimag.domain.model.compliance.SanctionCheckFilter;
import edu.unimagdalena.reservasunimag.domain.model.compliance.SanctionCheckOutcome;
import edu.unimagdalena.reservasunimag.domain.model.compliance.SanctionCheckPage;
import edu.unimagdalena.reservasunimag.domain.model.compliance.SanctionCheckPurpose;
import edu.unimagdalena.reservasunimag.domain.model.compliance.SanctionScope;
import edu.unimagdalena.reservasunimag.domain.model.error.DenialCode;
import edu.unimagdalena.reservasunimag.domain.model.error.ReservationDeniedException;
import edu.unimagdalena.reservasunimag.domain.model.reservation.ReservationRequest;
import edu.unimagdalena.reservasunimag.domain.model.reservation.TimeSlot;
import edu.unimagdalena.reservasunimag.domain.model.resource.ResourceCategory;
import edu.unimagdalena.reservasunimag.domain.model.user.CurrentUser;
import edu.unimagdalena.reservasunimag.domain.model.user.Role;
import edu.unimagdalena.reservasunimag.domain.port.in.ReserveResourcesPort;
import edu.unimagdalena.reservasunimag.domain.port.out.SanctionCheckRepositoryPort;
import edu.unimagdalena.reservasunimag.domain.port.out.TransactionPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static edu.unimagdalena.reservasunimag.support.Database.clean;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** El registro de UC6 contra PostgreSQL: sobrevive al rollback, no tumba la consulta y no es una tabla de sanciones. */
@SpringBootTest(properties = {"reservations.outbox.enabled=false", "reservations.academic.use-start-enabled=false"})
@ActiveProfiles("local")
@Import(TestcontainersConfiguration.class)
class SanctionCheckIT {

    /** Tiene una sanción vigente en el Módulo 3 falso del perfil local. */
    private static final CurrentUser SANCTIONED = new CurrentUser(
            UUID.fromString("00000000-0000-0000-0000-000000000002"), "2020114002", Role.MONITOR);
    private static final LocalDate TOMORROW = LocalDate.now(TimeSlot.ZONE).plusDays(1);
    private static final LocalDate TODAY = LocalDate.now(TimeSlot.ZONE);

    @Autowired
    private ReserveResourcesPort reserveResources;
    @Autowired
    private SanctionCheckRepositoryPort checks;
    @Autowired
    private TransactionPort transactions;
    @Autowired
    private JdbcClient jdbc;
    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping mappings;

    @BeforeEach
    void emptyTables() {
        clean(jdbc);
    }

    private static SanctionCheck clear(String personCode, Instant askedAt) {
        return SanctionCheck.answered(personCode, SanctionCheckPurpose.SEARCH_RESOURCES, null, askedAt,
                askedAt.plusMillis(40), new ComplianceReport(List.of(), 1, 0, askedAt), Optional.empty());
    }

    private SanctionCheckPage search(SanctionCheckFilter filter) {
        return checks.search(filter, new PageRequest(1, 20));
    }

    @Test
    void aRes003DenialLeavesTheCheckWithWhatModule3Answered() {
        assertThatThrownBy(() -> reserveResources.reserve(ReservationRequest.forSpace(SANCTIONED, "ESP-0107",
                TOMORROW, LocalTime.of(10, 0), LocalTime.of(11, 0))))
                .isInstanceOfSatisfying(ReservationDeniedException.class,
                        denied -> assertThat(denied.code()).isEqualTo(DenialCode.RES_003));

        SanctionCheck check = search(new SanctionCheckFilter("2020114002", null, TODAY, TODAY)).page().items()
                .getFirst();
        assertThat(check.outcome()).isEqualTo(SanctionCheckOutcome.SANCTIONED);
        assertThat(check.purpose()).isEqualTo(SanctionCheckPurpose.RESERVE_RESOURCES);
        assertThat(check.category()).isEqualTo(ResourceCategory.ESPACIO);
        assertThat(check.answered().sanctionReason()).isEqualTo("Dos ausencias no justificadas en el semestre");
        assertThat(check.answered().endsOn()).isEqualTo(TODAY.plusDays(25));
        assertThat(check.answered().scope()).isEqualTo(SanctionScope.TODO);
        assertThat(check.answered().absenceCount()).isEqualTo(2);
        assertThat(jdbc.sql("SELECT count(*) FROM denial WHERE code = 'RES-003'").query(Long.class).single())
                .isEqualTo(1);
    }

    @Test
    void theCheckSurvivesTheRollbackOfTheTransactionThatAskedForIt() {
        SanctionCheck check = clear("2021114001", Instant.now());

        assertThatThrownBy(() -> transactions.required(() -> {
            checks.record(check);
            throw new IllegalStateException("la reserva se deshace");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(jdbc.sql("SELECT count(*) FROM sanction_check WHERE id = :id").param("id", check.id())
                .query(Long.class).single()).isEqualTo(1);
    }

    @Test
    void whenTheCheckCannotBeWrittenTheQueryDoesNotFail() {
        SanctionCheck tooLong = clear("X".repeat(40), Instant.now());

        assertThatCode(() -> checks.record(tooLong)).doesNotThrowAnyException();
        assertThat(jdbc.sql("SELECT count(*) FROM sanction_check").query(Long.class).single()).isZero();
    }

    @Test
    void itReadsBackAsWrittenAndFiltersByPersonOutcomeAndBogotaDays() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        SanctionCheck mine = clear("2021114001", now);
        SanctionCheck notVerified = SanctionCheck.notVerified("2021114001", SanctionCheckPurpose.RESERVE_RESOURCES,
                ResourceCategory.ACTIVO, now.plusSeconds(1), now.plusSeconds(3), "Read timed out");
        SanctionCheck other = clear("2019114045", now);
        SanctionCheck old = clear("2021114001", now.minus(40, ChronoUnit.DAYS));
        List.of(mine, notVerified, other, old).forEach(checks::record);

        SanctionCheckPage page = search(new SanctionCheckFilter("2021114001", null, TODAY.minusDays(1), TODAY));
        assertThat(page.page().items()).containsExactly(notVerified, mine);
        assertThat(page.summary()).isEqualTo(new SanctionCheckPage.Summary(2, 1, 0, 0, 1));

        assertThat(search(new SanctionCheckFilter(null, SanctionCheckOutcome.NOT_VERIFIED, null, null)).page().items())
                .singleElement().satisfies(check -> {
                    assertThat(check.failure()).isEqualTo("Read timed out");
                    assertThat(check.durationMs()).isEqualTo(2000);
                    assertThat(check.answered()).isNull();
                });
        assertThat(search(new SanctionCheckFilter(null, null, null, null)).summary().total()).isEqualTo(4);
    }

    @Test
    void noTableKeepsSanctionsOnlyTheRecordOfWhatWasAnswered() {
        assertThat(jdbc.sql("""
                        SELECT table_name FROM information_schema.tables
                        WHERE table_schema = 'public' AND table_name LIKE '%sanction%'""")
                .query(String.class).list()).containsExactly("sanction_check");
        assertThat(jdbc.sql("""
                        SELECT column_name FROM information_schema.columns
                        WHERE table_name = 'sanction_check' AND column_name LIKE 'sanction%'""")
                .query(String.class).list()).isEmpty();
    }

    @Test
    void theOnlyRouteAboutSanctionsIsTheAuditAndNoneAnswersWhetherSomeoneIsSanctioned() {
        List<String> routes = mappings.getHandlerMethods().keySet().stream()
                .flatMap(info -> info.getPatternValues().stream())
                .filter(path -> path.contains("sanction") || path.contains("compliance"))
                .toList();

        assertThat(routes).containsExactly("/api/sanction-checks");
    }
}
