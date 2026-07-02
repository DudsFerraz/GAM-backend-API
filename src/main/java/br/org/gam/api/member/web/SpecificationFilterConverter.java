package br.org.gam.api.member.web;

import br.org.gam.api.member.domain.MemberStatus;
import br.org.gam.api.shared.specification.GenericSpecificationFilterConverter;
import br.org.gam.api.shared.specification.SpecificationFilter;
import br.org.gam.api.shared.specification.SpecificationFilterDTO;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.function.Function;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component("memberSpecificationFilterConverter")
class SpecificationFilterConverter {
    private static final Map<String, Function<String, Object>> PARSER_MAP = new HashMap<>();

    static {
        PARSER_MAP.put("id", UUID::fromString);
        PARSER_MAP.put("birthDate", LocalDate::parse);
        PARSER_MAP.put("phoneNumber", val -> val);
        PARSER_MAP.put("status", val -> MemberStatus.valueOf(val.toUpperCase()));
        PARSER_MAP.put("createdAt", OffsetDateTime::parse);
        PARSER_MAP.put("updatedAt", OffsetDateTime::parse);
        PARSER_MAP.put("fullName", val -> val);
        PARSER_MAP.put("account.id", UUID::fromString);
        PARSER_MAP.put("account.displayName", val -> val);
        PARSER_MAP.put("account.email", val -> val);
        PARSER_MAP.put("account.accountRoles.role.name", val -> val);
    }

    public List<SpecificationFilter> convert(List<SpecificationFilterDTO> dtos) {
        return GenericSpecificationFilterConverter.convert(dtos, PARSER_MAP);
    }
}
