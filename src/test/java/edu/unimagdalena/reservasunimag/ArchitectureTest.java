package edu.unimagdalena.reservasunimag;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/** La regla que no se rompe: {@code domain} no conoce a {@code infrastructure} ni a ningún framework. */
@AnalyzeClasses(packages = "edu.unimagdalena.reservasunimag", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    private static final String DOMAIN = "edu.unimagdalena.reservasunimag.domain..";

    @ArchTest
    static final ArchRule domainDoesNotDependOnInfrastructure = noClasses()
            .that().resideInAPackage(DOMAIN)
            .should().dependOnClassesThat().resideInAPackage("edu.unimagdalena.reservasunimag.infrastructure..");

    @ArchTest
    static final ArchRule domainDoesNotDependOnFrameworks = noClasses()
            .that().resideInAPackage(DOMAIN)
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework..",
                    "jakarta.persistence..",
                    "jakarta.servlet..",
                    "org.hibernate..",
                    "org.apache.kafka..",
                    "com.fasterxml..",
                    "tools.jackson..",
                    "lombok..");

    /**
     * UC9 FR-001 y UC7: el Módulo 2 nunca declara una ausencia ni libera una ocupación por su cuenta. Lo único
     * programado es el publicador de la outbox y el inicio de uso de las clases, que solo registra un hecho que nadie
     * más registra; ninguna tarea revisa reservas pasadas.
     */
    @ArchTest
    static final ArchRule onlyTheOutboxAndTheClassStartRunOnASchedule = methods()
            .that().areAnnotatedWith("org.springframework.scheduling.annotation.Scheduled")
            .should().beDeclaredInClassesThat().haveFullyQualifiedName(
                    "edu.unimagdalena.reservasunimag.infrastructure.adapter.out.messaging.OutboxPublisher")
            .orShould().beDeclaredInClassesThat().haveFullyQualifiedName(
                    "edu.unimagdalena.reservasunimag.infrastructure.adapter.in.scheduler.AcademicUseStartScheduler");

    /** UC7 FR-012: al Módulo 1 solo le sale el inicio de uso, y el puerto no tiene otra forma de mandarle algo. */
    @ArchTest
    static final ArchRule theInventoryNotifierOnlyKnowsUseStarted = methods()
            .that().areDeclaredIn("edu.unimagdalena.reservasunimag.domain.port.out.InventoryStatusNotifierPort")
            .should().haveRawParameterTypes("edu.unimagdalena.reservasunimag.domain.model.reservation.UseStart");

    @ArchTest
    static final ArchRule portsAreInterfaces = classes()
            .that().resideInAPackage("edu.unimagdalena.reservasunimag.domain.port..")
            .and().doNotHaveSimpleName("package-info")
            .should().beInterfaces()
            .because("un tipo auxiliar de un puerto va en domain.model");
}
