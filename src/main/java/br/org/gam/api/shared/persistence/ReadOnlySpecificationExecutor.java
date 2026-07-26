package br.org.gam.api.shared.persistence;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor.SpecificationFluentQuery;
import org.springframework.data.repository.NoRepositoryBean;

@NoRepositoryBean
public interface ReadOnlySpecificationExecutor<T> {

    Optional<T> findOne(Specification<T> specification);

    List<T> findAll(Specification<T> specification);

    Page<T> findAll(Specification<T> specification, Pageable pageable);

    Page<T> findAll(Specification<T> specification, Specification<T> countSpecification, Pageable pageable);

    List<T> findAll(Specification<T> specification, Sort sort);

    long count(Specification<T> specification);

    boolean exists(Specification<T> specification);

    <S extends T, R> R findBy(
            Specification<T> specification,
            Function<? super SpecificationFluentQuery<S>, R> queryFunction
    );
}
