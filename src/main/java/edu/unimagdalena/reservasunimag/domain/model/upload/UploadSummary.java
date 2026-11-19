package edu.unimagdalena.reservasunimag.domain.model.upload;

/**
 * El resumen del reporte. {@code rowsOk} cuenta las filas que se pueden aplicar con cambios, incluidas las que
 * desplazan reservas. Cuando la carga no es aplicable, lo que se crearía o cancelaría va en cero.
 */
public record UploadSummary(int rowsRead, int rowsOk, int rowsUnchanged, int rowsWithBlockingVerdict,
                            int sessionsToCreate, int sessionsSkippedNonBusinessDay, int blocksToWithdraw,
                            int reservationsToCancel) {
}
