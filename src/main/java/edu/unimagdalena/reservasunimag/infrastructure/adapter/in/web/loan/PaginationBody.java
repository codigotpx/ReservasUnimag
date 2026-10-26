package edu.unimagdalena.reservasunimag.infrastructure.adapter.in.web.loan;

import edu.unimagdalena.reservasunimag.domain.model.common.Page;

record PaginationBody(int page, int pageSize, int totalPages, long total) {

    static PaginationBody of(Page<?> page) {
        return new PaginationBody(page.page(), page.pageSize(), page.totalPages(), page.total());
    }
}
