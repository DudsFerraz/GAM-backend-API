package br.org.gam.api.account.application.search;

import br.org.gam.api.account.persistence.AccountEntity;
import br.org.gam.api.shared.specification.ComparationMethods;
import br.org.gam.api.shared.specification.ResourceSearchFilterConverter;
import br.org.gam.api.shared.specification.SearchDTO;
import br.org.gam.api.shared.specification.SearchFilterConverter;
import br.org.gam.api.shared.specification.SearchFilterDefinition;
import br.org.gam.api.shared.specification.SearchValueParsers;
import java.util.Map;
import java.util.Set;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

@Component
public class AccountSearchFilterConverter implements SearchFilterConverter<AccountEntity> {

    private static final Map<String, SearchFilterDefinition<AccountEntity>> DEFINITIONS = Map.ofEntries(
            Map.entry("id", SearchFilterDefinition.path(
                    "id",
                    "id",
                    Set.of(ComparationMethods.EQUALS, ComparationMethods.IN),
                    Map.of(
                            ComparationMethods.EQUALS, SearchValueParsers::uuid,
                            ComparationMethods.IN, SearchValueParsers.in(SearchValueParsers::uuid)
                    )
            )),
            Map.entry("displayName", SearchFilterDefinition.path(
                    "displayName",
                    "displayName",
                    Set.of(ComparationMethods.EQUALS, ComparationMethods.LIKE),
                    Map.of(
                            ComparationMethods.EQUALS, SearchValueParsers::text,
                            ComparationMethods.LIKE, SearchValueParsers::text
                    )
            )),
            Map.entry("email", SearchFilterDefinition.path(
                    "email",
                    "email",
                    Set.of(ComparationMethods.EQUALS, ComparationMethods.LIKE),
                    Map.of(
                            ComparationMethods.EQUALS, SearchValueParsers::emailEquals,
                            ComparationMethods.LIKE, SearchValueParsers::emailLike
                    )
            )),
            Map.entry("roleName", SearchFilterDefinition.path(
                    "roleName",
                    "accountRoles.role.name",
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
    public Specification<AccountEntity> convert(SearchDTO searchDTO) {
        return ResourceSearchFilterConverter.convert(searchDTO, DEFINITIONS);
    }
}
