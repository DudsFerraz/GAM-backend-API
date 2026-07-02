package br.org.gam.api.event.application.useCases.SearchEvents;

import br.org.gam.api.event.application.EventRDTO;
import br.org.gam.api.shared.specification.SpecificationFilter;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface SearchEvents {
    Page<EventRDTO> search(List<SpecificationFilter> filters, Pageable pageable);
}
