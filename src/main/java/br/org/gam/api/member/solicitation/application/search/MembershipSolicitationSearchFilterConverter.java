package br.org.gam.api.member.solicitation.application.search;

import br.org.gam.api.member.solicitation.domain.MembershipSolicitationStatus;
import br.org.gam.api.member.solicitation.persistence.MembershipSolicitationEntity;
import br.org.gam.api.shared.specification.ComparationMethods;
import br.org.gam.api.shared.specification.ResourceSearchFilterConverter;
import br.org.gam.api.shared.specification.SearchDTO;
import br.org.gam.api.shared.specification.SearchFilterConverter;
import br.org.gam.api.shared.specification.SearchFilterDefinition;
import br.org.gam.api.shared.specification.SearchValueParsers;
import br.org.gam.api.shared.specification.SpecificationFactory;
import java.util.Map;
import java.util.Set;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

@Component
public class MembershipSolicitationSearchFilterConverter
        implements SearchFilterConverter<MembershipSolicitationEntity> {

    private static final Map<String, SearchFilterDefinition<MembershipSolicitationEntity>> DEFINITIONS = Map.ofEntries(
            Map.entry("id", SearchFilterDefinition.path(
                    "id", "id", Set.of(ComparationMethods.EQUALS, ComparationMethods.IN),
                    Map.of(
                            ComparationMethods.EQUALS, SearchValueParsers::uuid,
                            ComparationMethods.IN, SearchValueParsers.in(SearchValueParsers::uuid)
                    )
            )),
            Map.entry("accountId", SearchFilterDefinition.path(
                    "accountId", "account.id", Set.of(ComparationMethods.EQUALS),
                    Map.of(ComparationMethods.EQUALS, SearchValueParsers::uuid)
            )),
            Map.entry("email", SearchFilterDefinition.path(
                    "email", "account.email", Set.of(ComparationMethods.EQUALS, ComparationMethods.LIKE),
                    Map.of(
                            ComparationMethods.EQUALS, SearchValueParsers::emailEquals,
                            ComparationMethods.LIKE, SearchValueParsers::emailLike
                    )
            )),
            Map.entry("name", new SearchFilterDefinition<>(
                    "name", Set.of(ComparationMethods.LIKE),
                    Map.of(ComparationMethods.LIKE, SearchValueParsers::normalizedFullNameLike),
                    (method, value) -> SpecificationFactory.likeFullName(
                            "name.firstName",
                            "name.surname",
                            (String) value
                    )
            )),
            Map.entry("status", SearchFilterDefinition.path(
                    "status", "status", Set.of(ComparationMethods.EQUALS, ComparationMethods.IN),
                    Map.of(
                            ComparationMethods.EQUALS,
                            SearchValueParsers.enumValue(MembershipSolicitationStatus.class),
                            ComparationMethods.IN,
                            SearchValueParsers.in(SearchValueParsers.enumValue(MembershipSolicitationStatus.class))
                    )
            )),
            Map.entry("submittedAt", SearchFilterDefinition.path(
                    "submittedAt", "createdAt",
                    Set.of(ComparationMethods.GREATER_THAN_OR_EQUAL, ComparationMethods.LESS_THAN_OR_EQUAL),
                    Map.of(
                            ComparationMethods.GREATER_THAN_OR_EQUAL, SearchValueParsers::instant,
                            ComparationMethods.LESS_THAN_OR_EQUAL, SearchValueParsers::instant
                    )
            )),
            Map.entry("decidedAt", SearchFilterDefinition.path(
                    "decidedAt", "decidedAt",
                    Set.of(ComparationMethods.GREATER_THAN_OR_EQUAL, ComparationMethods.LESS_THAN_OR_EQUAL),
                    Map.of(
                            ComparationMethods.GREATER_THAN_OR_EQUAL, SearchValueParsers::instant,
                            ComparationMethods.LESS_THAN_OR_EQUAL, SearchValueParsers::instant
                    )
            )),
            Map.entry("reviewedByAccountId", SearchFilterDefinition.path(
                    "reviewedByAccountId", "reviewedBy.id", Set.of(ComparationMethods.EQUALS),
                    Map.of(ComparationMethods.EQUALS, SearchValueParsers::uuid)
            ))
    );

    @Override
    public Specification<MembershipSolicitationEntity> convert(SearchDTO searchDTO) {
        return ResourceSearchFilterConverter.convert(searchDTO, DEFINITIONS);
    }
}
