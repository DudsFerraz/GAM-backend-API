package br.org.gam.api.member.application.search;

import br.org.gam.api.member.domain.MemberStatus;
import br.org.gam.api.member.persistence.MemberEntity;
import br.org.gam.api.shared.specification.ComparationMethods;
import br.org.gam.api.shared.specification.ResourceSearchFilterConverter;
import br.org.gam.api.shared.specification.SearchDTO;
import br.org.gam.api.shared.specification.SearchFilterConverter;
import br.org.gam.api.shared.specification.SearchFilterDefinition;
import br.org.gam.api.shared.specification.SearchValueParsers;
import br.org.gam.api.shared.specification.SpecificationFactory;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

@Component
public class MemberSearchFilterConverter implements SearchFilterConverter<MemberEntity> {

    private static final Map<String, SearchFilterDefinition<MemberEntity>> DEFINITIONS = Map.ofEntries(
            Map.entry("id", SearchFilterDefinition.path(
                    "id",
                    "id",
                    Set.of(ComparationMethods.EQUALS, ComparationMethods.IN),
                    Map.of(
                            ComparationMethods.EQUALS, SearchValueParsers::uuid,
                            ComparationMethods.IN, SearchValueParsers.in(SearchValueParsers::uuid)
                    )
            )),
            Map.entry("name", new SearchFilterDefinition<>(
                    "name",
                    Set.of(ComparationMethods.LIKE),
                    Map.of(ComparationMethods.LIKE, SearchValueParsers::text),
                    (method, value) -> SpecificationFactory.likeAny(List.of("name.firstName", "name.surname"), (String) value)
            )),
            Map.entry("birthDate", SearchFilterDefinition.path(
                    "birthDate",
                    "birthDate",
                    Set.of(ComparationMethods.EQUALS, ComparationMethods.GREATER_THAN_OR_EQUAL, ComparationMethods.LESS_THAN_OR_EQUAL),
                    Map.of(
                            ComparationMethods.EQUALS, SearchValueParsers::localDate,
                            ComparationMethods.GREATER_THAN_OR_EQUAL, SearchValueParsers::localDate,
                            ComparationMethods.LESS_THAN_OR_EQUAL, SearchValueParsers::localDate
                    )
            )),
            Map.entry("phoneNumber", SearchFilterDefinition.path(
                    "phoneNumber",
                    "phoneNumber",
                    Set.of(ComparationMethods.EQUALS, ComparationMethods.LIKE),
                    Map.of(
                            ComparationMethods.EQUALS, SearchValueParsers::phoneNumberEquals,
                            ComparationMethods.LIKE, SearchValueParsers::phoneNumberLike
                    )
            )),
            Map.entry("status", SearchFilterDefinition.path(
                    "status",
                    "status",
                    Set.of(ComparationMethods.EQUALS, ComparationMethods.IN),
                    Map.of(
                            ComparationMethods.EQUALS, SearchValueParsers.enumValue(MemberStatus.class),
                            ComparationMethods.IN, SearchValueParsers.in(SearchValueParsers.enumValue(MemberStatus.class))
                    )
            )),
            Map.entry("accountId", SearchFilterDefinition.path(
                    "accountId",
                    "account.id",
                    Set.of(ComparationMethods.EQUALS),
                    Map.of(ComparationMethods.EQUALS, SearchValueParsers::uuid)
            )),
            Map.entry("email", SearchFilterDefinition.path(
                    "email",
                    "account.email",
                    Set.of(ComparationMethods.EQUALS, ComparationMethods.LIKE),
                    Map.of(
                            ComparationMethods.EQUALS, SearchValueParsers::emailEquals,
                            ComparationMethods.LIKE, SearchValueParsers::emailLike
                    )
            )),
            Map.entry("role", SearchFilterDefinition.path(
                    "role",
                    "account.accountRoles.role.name",
                    Set.of(ComparationMethods.EQUALS, ComparationMethods.IN),
                    Map.of(
                            ComparationMethods.EQUALS, SearchValueParsers::text,
                            ComparationMethods.IN, SearchValueParsers.in(SearchValueParsers::text)
                    )
            )),
            Map.entry("createdAt", SearchFilterDefinition.path(
                    "createdAt",
                    "createdAt",
                    Set.of(ComparationMethods.GREATER_THAN_OR_EQUAL, ComparationMethods.LESS_THAN_OR_EQUAL),
                    Map.of(
                            ComparationMethods.GREATER_THAN_OR_EQUAL, SearchValueParsers::instant,
                            ComparationMethods.LESS_THAN_OR_EQUAL, SearchValueParsers::instant
                    )
            )),
            Map.entry("updatedAt", SearchFilterDefinition.path(
                    "updatedAt",
                    "updatedAt",
                    Set.of(ComparationMethods.GREATER_THAN_OR_EQUAL, ComparationMethods.LESS_THAN_OR_EQUAL),
                    Map.of(
                            ComparationMethods.GREATER_THAN_OR_EQUAL, SearchValueParsers::instant,
                            ComparationMethods.LESS_THAN_OR_EQUAL, SearchValueParsers::instant
                    )
            ))
    );

    @Override
    public Specification<MemberEntity> convert(SearchDTO searchDTO) {
        return ResourceSearchFilterConverter.convert(searchDTO, DEFINITIONS);
    }
}
