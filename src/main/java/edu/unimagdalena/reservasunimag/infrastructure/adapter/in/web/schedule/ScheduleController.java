package edu.unimagdalena.reservasunimag.infrastructure.adapter.in.web.schedule;

import edu.unimagdalena.reservasunimag.domain.model.academicblock.AcademicData;
import edu.unimagdalena.reservasunimag.domain.model.academicblock.ExtraordinaryNeed;
import edu.unimagdalena.reservasunimag.domain.model.common.PageRequest;
import edu.unimagdalena.reservasunimag.domain.model.upload.ScheduleFile;
import edu.unimagdalena.reservasunimag.domain.model.user.CurrentUser;
import edu.unimagdalena.reservasunimag.domain.model.user.Role;
import edu.unimagdalena.reservasunimag.domain.port.in.ApplySchedulePort;
import edu.unimagdalena.reservasunimag.domain.port.in.QueryScheduleUploadsPort;
import edu.unimagdalena.reservasunimag.domain.port.in.RegisterExtraordinaryNeedPort;
import edu.unimagdalena.reservasunimag.domain.port.in.ValidateSchedulePort;
import edu.unimagdalena.reservasunimag.infrastructure.config.ReservationProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.time.LocalDate;
import java.util.UUID;

/**
 * UC3: la carga del horario en dos pasos —validar y confirmar—, el histórico y la necesidad extraordinaria. Todo exige
 * el rol {@code DIRECCION_PROGRAMA}; con otro rol la respuesta es 403, no 401.
 */
@RestController
@RequestMapping("/api/schedules")
class ScheduleController {

    private static final Logger log = LoggerFactory.getLogger(ScheduleController.class);

    private final ValidateSchedulePort validateSchedule;
    private final ApplySchedulePort applySchedule;
    private final QueryScheduleUploadsPort queryUploads;
    private final RegisterExtraordinaryNeedPort registerExtraordinaryNeed;
    private final int pageSize;

    ScheduleController(ValidateSchedulePort validateSchedule, ApplySchedulePort applySchedule,
                       QueryScheduleUploadsPort queryUploads, RegisterExtraordinaryNeedPort registerExtraordinaryNeed,
                       ReservationProperties properties) {
        this.validateSchedule = validateSchedule;
        this.applySchedule = applySchedule;
        this.queryUploads = queryUploads;
        this.registerExtraordinaryNeed = registerExtraordinaryNeed;
        this.pageSize = properties.pageSize();
    }

    @PostMapping(path = "/validations", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<ScheduleReportResponse> validate(
            CurrentUser currentUser,
            @RequestPart("file") MultipartFile file,
            @RequestParam @NotBlank(message = "es obligatorio") String academicTerm,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate termStart,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate termEnd) {
        requireDirection(currentUser);
        var upload = validateSchedule.validate(currentUser, new ScheduleFile(file.getOriginalFilename(), bytes(file)),
                academicTerm.strip(), termStart, termEnd);
        // Sin nombres de estudiantes: el autor, el periodo y los conteos bastan para auditar (plan de UC3, T041).
        log.info("Carga de horario {} validada por {}: periodo {}, {} filas, {} con problemas, estado {}",
                upload.id(), currentUser.id(), upload.academicTerm(), upload.summary().rowsRead(),
                upload.summary().rowsWithBlockingVerdict(), upload.status());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ScheduleReportResponse.from(upload));
    }

    @PostMapping("/{uploadId}/confirmation")
    ResponseEntity<ScheduleConfirmationResponse> confirm(CurrentUser currentUser, @PathVariable UUID uploadId) {
        requireDirection(currentUser);
        var applied = applySchedule.confirm(currentUser, uploadId);
        log.info("Carga de horario {} aplicada por {}: {}", uploadId, currentUser.id(), applied.result());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(ScheduleConfirmationResponse.from(applied));
    }

    @GetMapping
    ResponseEntity<ScheduleUploadsResponse> list(
            CurrentUser currentUser,
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "debe ser mayor o igual a 1") int page) {
        requireDirection(currentUser);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(ScheduleUploadsResponse.from(queryUploads.list(new PageRequest(page, pageSize))));
    }

    @GetMapping("/{uploadId}")
    ResponseEntity<ScheduleReportResponse> find(CurrentUser currentUser, @PathVariable UUID uploadId) {
        requireDirection(currentUser);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(ScheduleReportResponse.from(queryUploads.find(uploadId)));
    }

    @PostMapping("/extraordinary-needs")
    ResponseEntity<Object> extraordinaryNeed(CurrentUser currentUser,
                                             @Valid @RequestBody ExtraordinaryNeedRequest body) {
        requireDirection(currentUser);
        var need = new ExtraordinaryNeed(body.resourceId(), body.date(), body.start(), body.end(),
                new AcademicData(body.courseCode(), body.course(), body.group(), body.program(),
                        body.instructor() == null || body.instructor().isBlank() ? null : body.instructor()));
        var result = registerExtraordinaryNeed.register(need, body.confirm());
        if (!result.applied()) {
            return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                    .body(ExtraordinaryNeedResponse.from(result));
        }
        return ResponseEntity.created(URI.create("/api/reservations/" + result.block().id()))
                .cacheControl(CacheControl.noStore())
                .body(ExtraordinaryNeedResponse.from(result));
    }

    private static byte[] bytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }

    private static void requireDirection(CurrentUser user) {
        if (user.role() != Role.DIRECCION_PROGRAMA) {
            throw new AccessDeniedException("Solo Dirección de Programa carga horarios");
        }
    }
}
