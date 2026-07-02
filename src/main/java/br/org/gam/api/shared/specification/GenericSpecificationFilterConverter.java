package br.org.gam.api.shared.specification;

import java.util.function.Function;
import java.util.List;
import java.util.Map;

public class GenericSpecificationFilterConverter {

    private GenericSpecificationFilterConverter() {}

    public static List<SpecificationFilter> convert(List<SpecificationFilterDTO> dtos,
                                                    Map<String, Function<String, Object>> parserMap) {

        if (dtos == null) return List.of();

        return dtos.stream()
                .map(dto -> toSpecificationFilter(dto, parserMap))
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    private static SpecificationFilter toSpecificationFilter(SpecificationFilterDTO dto,
                                                             Map<String, Function<String, Object>> parserMap) {

        Function<String, Object> parser = parserMap.get(dto.field());

        if (parser == null) throw new IllegalArgumentException("Campo de filtro desconhecido: " + dto.field());

        try {
            Object typedValue = parser.apply(dto.value());

            return new SpecificationFilter(dto.field(), typedValue, dto.comparationMethod());
        } catch (Exception e) {
            throw new IllegalArgumentException("Valor inválido para o campo '" + dto.field() + "': " + dto.value(), e);
        }
    }
}
