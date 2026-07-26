package br.org.gam.api.shared.specification;

import java.util.List;
import java.util.LinkedHashMap;
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
        List<Specification<E>> specifications = new java.util.ArrayList<>();
        for (int index = 0; index < searchDTO.filters().size(); index++) {
            specifications.add(toSpecification(searchDTO.filters().get(index), index, definitions));
        }

        return Specification.allOf(specifications);
    }

    private static <E> Specification<E> toSpecification(
            SpecificationFilterDTO dto,
            int filterIndex,
            Map<String, SearchFilterDefinition<E>> definitions
    ) {
        SearchFilterDefinition<E> definition = definitions.get(dto.field());
        if (definition == null) {
            throw new InvalidSearchFilterException(
                    "Unknown filter field.",
                    Map.of("filterIndex", filterIndex)
            );
        }

        ComparationMethods method;
        try {
            method = ComparationMethods.valueOf(dto.comparisonMethod());
        } catch (IllegalArgumentException exception) {
            throw new InvalidSearchFilterException(
                    "Unknown comparison method.",
                    knownDetails(filterIndex, definition.publicField(), null)
            );
        }

        return definition.toSpecification(dto, method, filterIndex);
    }

    static Map<String, Object> knownDetails(
            int filterIndex,
            String field,
            ComparationMethods comparisonMethod
    ) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("filterIndex", filterIndex);
        details.put("field", field);
        if (comparisonMethod != null) {
            details.put("comparisonMethod", comparisonMethod.name());
        }
        return details;
    }
}
