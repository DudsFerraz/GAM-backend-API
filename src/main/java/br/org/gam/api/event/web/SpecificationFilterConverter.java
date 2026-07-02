package br.org.gam.api.event.web;

import br.org.gam.api.shared.specification.GenericSpecificationFilterConverter;
import br.org.gam.api.shared.specification.SpecificationFilter;
import br.org.gam.api.shared.specification.SpecificationFilterDTO;
import java.time.OffsetDateTime;
import java.util.function.Function;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component("eventSpecificationFilterConverter")
class SpecificationFilterConverter {
    private static final Map<String, Function<String, Object>> PARSER_MAP = new HashMap<>();

    static {
        PARSER_MAP.put("id", UUID::fromString);
        PARSER_MAP.put("title", val -> val);
        PARSER_MAP.put("description", val -> val);
        PARSER_MAP.put("location.id", UUID::fromString);
        PARSER_MAP.put("beginDate", OffsetDateTime::parse);
        PARSER_MAP.put("endDate", OffsetDateTime::parse);
        PARSER_MAP.put("createdAt", OffsetDateTime::parse);
        PARSER_MAP.put("updatedAt", OffsetDateTime::parse);
    }

    public List<SpecificationFilter> convert(List<SpecificationFilterDTO> dtos) {
        return GenericSpecificationFilterConverter.convert(dtos, PARSER_MAP);
    }
}
