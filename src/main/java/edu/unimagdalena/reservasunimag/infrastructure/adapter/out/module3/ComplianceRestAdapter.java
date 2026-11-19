package edu.unimagdalena.reservasunimag.infrastructure.adapter.out.module3;

import edu.unimagdalena.reservasunimag.domain.model.compliance.ComplianceReport;
import edu.unimagdalena.reservasunimag.domain.model.compliance.Sanction;
import edu.unimagdalena.reservasunimag.domain.model.compliance.SanctionScope;
import edu.unimagdalena.reservasunimag.domain.model.error.ExternalServiceUnavailableException;
import edu.unimagdalena.reservasunimag.domain.model.error.ExternalServiceUnavailableException.ExternalModule;
import edu.unimagdalena.reservasunimag.domain.port.out.CompliancePort;
import edu.unimagdalena.reservasunimag.infrastructure.adapter.out.module3.dto.ComplianceReportDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;

/**
 * Cliente del Módulo 3 según UC1 Contratos §3. El {@code 404} es una persona sin historial y se lee como reporte
 * vacío (UC6 FR-008); el resto de las fallas sale como {@link ExternalServiceUnavailableException}. Una respuesta
 * que dice ser de otra persona tampoco se usa (UC6 FR-007, SC-005). El código institucional va en la URL, así que no
 * se registra.
 */
@Component
@Profile("!local")
class ComplianceRestAdapter implements CompliancePort {

    private static final Logger log = LoggerFactory.getLogger(ComplianceRestAdapter.class);

    private final RestClient client;

    ComplianceRestAdapter(RestClient module3RestClient) {
        this.client = module3RestClient;
    }

    @Override
    public ComplianceReport report(String personCode) {
        long startedAt = System.nanoTime();
        try {
            ComplianceReportDto body = client.get()
                    .uri("/api/v1/compliance/people/{code}", personCode)
                    .retrieve()
                    .body(ComplianceReportDto.class);
            if (body == null) {
                return ComplianceReport.empty();
            }
            if (body.person() != null && body.person().code() != null && !body.person().code().equals(personCode)) {
                log.error("Módulo 3: el reporte pedido no es de la persona consultada; se descarta");
                throw new ExternalServiceUnavailableException(ExternalModule.MODULO_3,
                        "El Módulo 3 devolvió el reporte de otra persona", null);
            }
            // Sin endsOn la sanción sigue vigente y el registro de la consulta marca que faltó el dato (UC6).
            List<Sanction> sanctions = body.activeSanctions() == null ? List.of() : body.activeSanctions().stream()
                    .map(dto -> new Sanction(dto.reason(), dto.startsOn(), dto.endsOn(),
                            SanctionScope.parse(dto.scope())))
                    .toList();
            log.info("Módulo 3: reporte de cumplimiento en {} ms", millisSince(startedAt));
            return new ComplianceReport(sanctions, body.absenceCount(), body.lateReturns(),
                    body.generatedAt() == null ? null : body.generatedAt().toInstant());
        } catch (HttpClientErrorException.NotFound noHistory) {
            log.info("Módulo 3: persona sin historial ({} ms)", millisSince(startedAt));
            return ComplianceReport.empty();
        } catch (RestClientException failure) {
            log.error("Módulo 3: reporte falló tras {} ms: {}", millisSince(startedAt), failure.getMessage());
            throw new ExternalServiceUnavailableException(ExternalModule.MODULO_3,
                    "No se pudo leer el reporte de cumplimiento del Módulo 3", failure);
        }
    }

    private static long millisSince(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }
}
