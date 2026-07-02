package br.org.gam.api.shared.specification;

import java.util.List;
import java.util.Map;
import org.springframework.data.jpa.domain.Specification;

public final class ResourceSearchFilterConverter {

    private ResourceSearchFilterConverter() {
    }

    public static <E> Specification<E> convert(
            SearchDTO searchDTO,
            Map<String, SearchFilterDefinition<E>> definitions
    ) {
        if (searchDTO == null || searchDTO.filters() == null) {
            return Specification.allOf(List.of());
        }

        List<Specification<E>> specifications = searchDTO.filters().stream()
                .map(dto -> toSpecification(dto, definitions))
                .toList();

        return Specification.allOf(specifications);
    }

    private static <E> Specification<E> toSpecification(
            SpecificationFilterDTO dto,
            Map<String, SearchFilterDefinition<E>> definitions
    ) {
        if (dto == null || dto.field() == null || dto.field().isBlank()) {
            throw new InvalidSearchFilterException("Unknown filter field.");
        }

        SearchFilterDefinition<E> definition = definitions.get(dto.field());
        if (definition == null) {
            throw new InvalidSearchFilterException("Unknown filter field " + dto.field() + ".");
        }

        return definition.toSpecification(dto);
    }
}
