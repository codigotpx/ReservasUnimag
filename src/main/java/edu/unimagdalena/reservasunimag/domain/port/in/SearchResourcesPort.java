package edu.unimagdalena.reservasunimag.domain.port.in;

import edu.unimagdalena.reservasunimag.domain.model.resource.SearchResourcesQuery;
import edu.unimagdalena.reservasunimag.domain.model.resource.SearchResourcesResult;

/** UC1: consultar recursos con su estado en una franja. */
public interface SearchResourcesPort {

    SearchResourcesResult search(SearchResourcesQuery query);
}
