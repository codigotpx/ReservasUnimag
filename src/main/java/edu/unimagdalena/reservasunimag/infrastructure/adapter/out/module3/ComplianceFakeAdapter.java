package edu.unimagdalena.reservasunimag.infrastructure.adapter.out.module3;

import edu.unimagdalena.reservasunimag.domain.model.compliance.ComplianceReport;
import edu.unimagdalena.reservasunimag.domain.model.compliance.Sanction;
import edu.unimagdalena.reservasunimag.domain.model.compliance.SanctionScope;
import edu.unimagdalena.reservasunimag.domain.model.reservation.TimeSlot;
import edu.unimagdalena.reservasunimag.domain.port.out.CompliancePort;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Reportes de ejemplo del perfil local. El monitor de prueba ({@code 2020114002}) tiene una sanción vigente, para
 * poder ver el aviso; el resto de las personas está al día.
 */
@Component
@Profile("local")
class ComplianceFakeAdapter implements CompliancePort {

    private static final String SANCTIONED_CODE = "2020114002";

    @Override
    public ComplianceReport report(String personCode) {
        if (SANCTIONED_CODE.equals(personCode)) {
            // El día de Bogotá, como el resto del módulo: con la zona del servidor, en UTC cambia a las 19:00.
            LocalDate today = LocalDate.now(TimeSlot.ZONE);
            return new ComplianceReport(
                    List.of(new Sanction("Dos ausencias no justificadas en el semestre", today.minusDays(5),
                            today.plusDays(25), SanctionScope.TODO)),
                    2, 1, Instant.now());
        }
        return ComplianceReport.empty();
    }
}
