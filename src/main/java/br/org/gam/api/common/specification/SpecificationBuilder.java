package br.org.gam.api.common.specification;

import org.springframework.data.jpa.domain.Specification;

import java.util.List;
import java.util.stream.Collectors;

public class SpecificationBuilder {
    public static <E> Specification<E> build(List<SpecificationFilter> filters) {
        if (filters == null) return Specification.allOf(List.of());

        List<Specification<E>> specs = filters.stream()
                .filter(SpecificationFilter::isValid)
                .map(filter -> filter.comparationMethod().<E>create(filter.field(), filter.value()))
                .collect(Collectors.toList());

        return Specification.allOf(specs);
    }


}
