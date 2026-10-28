package edu.unimagdalena.reservasunimag.domain.model.resource;

/** Ficha de un recurso: categoría y plazo máximo de préstamo (0 es uso en sitio; ausente es ficha incompleta). */
public class ResourceDetails {

    private final String resourceId;
    private final ResourceCategory category;
    private final Integer maxLoanDays;

    public ResourceDetails(String resourceId, ResourceCategory category, Integer maxLoanDays) {
        this.resourceId = resourceId;
        this.category = category;
        this.maxLoanDays = maxLoanDays;
    }

    public String getResourceId() {
        return resourceId;
    }

    public ResourceCategory getCategory() {
        return category;
    }

    /** Plazo máximo de préstamo en días; {@code 0} es uso en sitio. */
    public Integer getMaxLoanDays() {
        return maxLoanDays;
    }

    public boolean isLoanable() {
        return maxLoanDays != null && maxLoanDays > 0;
    }
}
