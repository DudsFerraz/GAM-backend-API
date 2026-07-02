package br.org.gam.api.shared.specification;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.springframework.data.jpa.domain.Specification;

public record SearchFilterDefinition<E>(
        String publicField,
        Set<ComparationMethods> allowedMethods,
        Map<ComparationMethods, Function<JsonNode, Object>> parsers,
        BiFunction<ComparationMethods, Object, Specification<E>> specificationFactory
) {

    public Specification<E> toSpecification(SpecificationFilterDTO dto) {
        ComparationMethods method = dto.comparationMethod();

        if (!allowedMethods.contains(method)) {
            throw new InvalidSearchFilterException(
                    "Unsupported comparison method " + method + " for field " + publicField + "."
            );
        }

        Function<JsonNode, Object> parser = parsers.get(method);
        if (parser == null) {
            throw new InvalidSearchFilterException(
                    "Unsupported comparison method " + method + " for field " + publicField + "."
            );
        }

        try {
            Object value = parser.apply(dto.value());
            return specificationFactory.apply(method, value);
        } catch (RuntimeException e) {
            throw new InvalidSearchFilterException("Invalid filter value for " + publicField + ".", e);
        }
    }

    public static <E> SearchFilterDefinition<E> path(
            String publicField,
            String internalTarget,
            Set<ComparationMethods> allowedMethods,
            Map<ComparationMethods, Function<JsonNode, Object>> parsers
    ) {
        return new SearchFilterDefinition<>(
                publicField,
                allowedMethods,
                parsers,
                (method, value) -> method.create(internalTarget, value)
        );
    }
}
