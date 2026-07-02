package br.org.gam.api.shared.specification;

import org.springframework.data.jpa.domain.Specification;

public interface SearchFilterConverter<E> {

    Specification<E> convert(SearchDTO searchDTO);
}
