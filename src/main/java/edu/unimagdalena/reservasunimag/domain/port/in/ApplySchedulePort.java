package edu.unimagdalena.reservasunimag.domain.port.in;

import edu.unimagdalena.reservasunimag.domain.model.upload.ScheduleUpload;
import edu.unimagdalena.reservasunimag.domain.model.user.CurrentUser;

import java.util.UUID;

/** UC3, paso 2: aplicar exactamente la carga que se validó, todo en una transacción (FR-008). */
public interface ApplySchedulePort {

    /**
     * @throws edu.unimagdalena.reservasunimag.domain.model.error.ScheduleUploadNotFoundException si la carga no existe
     * @throws edu.unimagdalena.reservasunimag.domain.model.error.UploadNotYoursException         si la validó otra persona
     * @throws edu.unimagdalena.reservasunimag.domain.model.error.UploadNotApplicableException    no aplicable, vencida, ya aplicada o con el impacto cambiado
     */
    ScheduleUpload confirm(CurrentUser user, UUID uploadId);
}
