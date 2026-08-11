package br.org.gam.api.gamLocation.application.useCases;

import br.org.gam.api.gamLocation.application.GamLocationEntityLoader;
import br.org.gam.api.gamLocation.application.GamLocationMapper;
import br.org.gam.api.gamLocation.application.GamLocationRDTO;
import br.org.gam.api.gamLocation.persistence.GamLocationRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

@Service
public class GetGamLocations {
    private static final Set<String> NULLABLE_ADDRESS_SORT_FIELDS = Set.of(
            "city", "state", "countryCode"
    );

    private final GamLocationEntityLoader loader;
    private final GamLocationMapper mapper;
    private final GamLocationRepository repository;

    public GetGamLocations(GamLocationEntityLoader loader, GamLocationMapper mapper,
                           GamLocationRepository repository) {
        this.loader = loader;
        this.mapper = mapper;
        this.repository = repository;
    }

    public GamLocationRDTO byId(UUID id) {
        return mapper.entityToRDTO(loader.requiredById(id));
    }

    public Page<GamLocationRDTO> all(Pageable pageable) {
        return repository.findAll(effectivePageable(pageable)).map(mapper::entityToRDTO);
    }

    private Pageable effectivePageable(Pageable pageable) {
        if (pageable.getSort().isUnsorted()) {
            return pageable;
        }

        Sort.Order primaryOrder = pageable.getSort().iterator().next();
        if (NULLABLE_ADDRESS_SORT_FIELDS.contains(primaryOrder.getProperty())) {
            return PageRequest.of(
                    pageable.getPageNumber(),
                    pageable.getPageSize(),
                    Sort.by(
                            primaryOrder.nullsLast(),
                            Sort.Order.asc("name"),
                            Sort.Order.asc("id")
                    )
            );
        }

        List<Sort.Order> orders = new ArrayList<>();
        pageable.getSort().forEach(orders::add);
        if (pageable.getSort().getOrderFor("name") == null) {
            orders.add(Sort.Order.asc("name"));
        }
        if (pageable.getSort().getOrderFor("id") == null) {
            orders.add(Sort.Order.asc("id"));
        }
        return PageRequest.of(
                pageable.getPageNumber(),
                pageable.getPageSize(),
                Sort.by(orders)
        );
    }
}
