package br.org.gam.api.shared.specification;

import br.org.gam.api.testing.annotation.FunctionalTest;
import br.org.gam.api.testing.annotation.UnitTest;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@UnitTest
@FunctionalTest
@DisplayName("Functional - Specification Filters")
class SpecificationFilterFunctionalTest {

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "   ", "\t"})
    @DisplayName("EP - invalid filter value -> filter is ignored")
    void invalidFilterValueShouldBeIgnored(String value) {
        SpecificationFilter filter = new SpecificationFilter("name", value, ComparationMethods.EQUALS);

        assertThat(filter.isValid()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"Maria", "0"})
    @DisplayName("EP - non-blank string value -> filter is valid")
    void nonBlankStringValueShouldBeValid(String value) {
        SpecificationFilter filter = new SpecificationFilter("name", value, ComparationMethods.EQUALS);

        assertThat(filter.isValid()).isTrue();
    }

    @Test
    @DisplayName("EP - non-string value -> filter is valid")
    void nonStringValueShouldBeValid() {
        SpecificationFilter filter = new SpecificationFilter("birthDate", Instant.EPOCH, ComparationMethods.GREATER_THAN_OR_EQUAL);

        assertThat(filter.isValid()).isTrue();
    }

    @Test
    @DisplayName("EP - null DTO list -> empty filters")
    void nullDtoListShouldReturnEmptyFilters() {
        List<SpecificationFilter> filters = GenericSpecificationFilterConverter.convert(null, Map.of());

        assertThat(filters).isEmpty();
    }

    @Test
    @DisplayName("EP - known filter field -> typed specification filter")
    void knownFilterFieldShouldReturnTypedSpecificationFilter() {
        SpecificationFilterDTO dto = new SpecificationFilterDTO("age", "18", ComparationMethods.GREATER_THAN_OR_EQUAL);

        List<SpecificationFilter> filters = GenericSpecificationFilterConverter.convert(
                List.of(dto),
                Map.of("age", Integer::valueOf)
        );

        assertThat(filters)
                .singleElement()
                .satisfies(filter -> {
                    assertThat(filter.field()).isEqualTo("age");
                    assertThat(filter.value()).isEqualTo(18);
                    assertThat(filter.comparationMethod()).isEqualTo(ComparationMethods.GREATER_THAN_OR_EQUAL);
                });
    }

    @Test
    @DisplayName("EP - unknown filter field -> validation error")
    void unknownFilterFieldShouldReturnValidationError() {
        SpecificationFilterDTO dto = new SpecificationFilterDTO("unknown", "value", ComparationMethods.EQUALS);

        assertThatThrownBy(() -> GenericSpecificationFilterConverter.convert(List.of(dto), Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Campo de filtro desconhecido: unknown");
    }

    @Test
    @DisplayName("EP - invalid typed filter value -> validation error")
    void invalidTypedFilterValueShouldReturnValidationError() {
        SpecificationFilterDTO dto = new SpecificationFilterDTO("age", "not-a-number", ComparationMethods.EQUALS);

        assertThatThrownBy(() -> GenericSpecificationFilterConverter.convert(List.of(dto), Map.of("age", Integer::valueOf)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Valor inv");
    }

    @Test
    @DisplayName("EP - LIKE with non-string value -> validation error")
    void likeWithNonStringValueShouldReturnValidationError() {
        assertThatThrownBy(() -> ComparationMethods.LIKE.create("name", 10))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Valor para 'LIKE' deve ser uma String.");
    }

    @Test
    @DisplayName("EP - GREATER_THAN_OR_EQUAL with non-comparable value -> validation error")
    void greaterThanOrEqualWithNonComparableValueShouldReturnValidationError() {
        assertThatThrownBy(() -> ComparationMethods.GREATER_THAN_OR_EQUAL.create("metadata", new Object()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Valor para 'GREATER_THAN_OR_EQUAL' deve ser Comparable (ex: OffsetDateTime, Integer, Double).");
    }

    @Test
    @DisplayName("EP - LESS_THAN_OR_EQUAL with non-comparable value -> validation error")
    void lessThanOrEqualWithNonComparableValueShouldReturnValidationError() {
        assertThatThrownBy(() -> ComparationMethods.LESS_THAN_OR_EQUAL.create("metadata", new Object()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Valor para 'LESS_THAN_OR_EQUAL' deve ser Comparable.");
    }

    @Test
    @DisplayName("EP - IN with scalar value -> validation error")
    void inWithScalarValueShouldReturnValidationError() {
        assertThatThrownBy(() -> ComparationMethods.IN.create("name", "Maria"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Valor para 'IN' deve ser uma Collection (ex: List, Set).");
    }
}
