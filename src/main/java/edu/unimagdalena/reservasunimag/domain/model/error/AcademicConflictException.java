package edu.unimagdalena.reservasunimag.domain.model.error;

import edu.unimagdalena.reservasunimag.domain.model.academicblock.AcademicData;
import edu.unimagdalena.reservasunimag.domain.model.upload.RowVerdict;

/**
 * Una necesidad extraordinaria no puede tomar la franja: otra clase, un activo en manos de alguien o un recurso en
 * mantenimiento (UC3 FR-006, FR-010). {@code conflictingBlock} existe solo en {@code ACADEMIC_CLASH}.
 */
public class AcademicConflictException extends RuntimeException {

    private final RowVerdict verdict;
    private final AcademicData conflictingBlock;

    public AcademicConflictException(RowVerdict verdict, String detail, AcademicData conflictingBlock) {
        super(detail);
        this.verdict = verdict;
        this.conflictingBlock = conflictingBlock;
    }

    public RowVerdict verdict() {
        return verdict;
    }

    public AcademicData conflictingBlock() {
        return conflictingBlock;
    }
}
