package br.org.gam.api.common.specification;

import br.org.gam.api.testing.annotation.StructuralTest;
import br.org.gam.api.testing.annotation.UnitTest;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@UnitTest
@DisplayName("Specification Builder")
@SuppressWarnings({"unchecked", "rawtypes"})
class SpecificationBuilderStructuralTest {

    @Nested
    @StructuralTest
    @DisplayName("Structural")
    class Structural {

        @Test
        @DisplayName("null filters -> empty specification")
        void nullFiltersShouldReturnEmptySpecification() {
            Specification<Object> specification = SpecificationBuilder.build(null);

            assertThat(specification).isNotNull();
        }

        @Test
        @DisplayName("valid and invalid filters -> only valid filters are applied")
        void validAndInvalidFiltersShouldApplyOnlyValidFilters() {
            Root<Object> root = mock(Root.class);
            CriteriaQuery<?> query = mock(CriteriaQuery.class);
            CriteriaBuilder builder = mock(CriteriaBuilder.class);
            Path<Object> namePath = mock(Path.class);
            Predicate predicate = mock(Predicate.class);
            SpecificationFilter validFilter = new SpecificationFilter("name", "Ana", ComparationMethods.EQUALS);
            SpecificationFilter invalidFilter = new SpecificationFilter("ignored", " ", ComparationMethods.EQUALS);

            when(root.get("name")).thenReturn(namePath);
            when(builder.equal(namePath, "Ana")).thenReturn(predicate);

            Predicate result = SpecificationBuilder.<Object>build(List.of(validFilter, invalidFilter))
                    .toPredicate(root, query, builder);

            assertThat(result).isSameAs(predicate);
            verify(query).distinct(true);
            verify(root).get("name");
            verifyNoInteractions(namePath);
        }
    }
}
