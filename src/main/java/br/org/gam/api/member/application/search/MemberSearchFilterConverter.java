package br.org.gam.api.member.application.search;

import br.org.gam.api.member.domain.MemberStatus;
import br.org.gam.api.member.domain.InformationStatus;
import br.org.gam.api.member.domain.MemberContributionArea;
import br.org.gam.api.member.domain.MemberExperienceType;
import br.org.gam.api.member.domain.MemberSacramentType;
import br.org.gam.api.member.persistence.MemberEntity;
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
import jakarta.persistence.criteria.Join;

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
                    Map.of(ComparationMethods.LIKE, SearchValueParsers::normalizedFullNameLike),
                    (method, value) -> SpecificationFactory.likeFullName(
                            "name.firstName",
                            "name.surname",
                            (String) value
                    )
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
            Map.entry("gamEntryDate", SearchFilterDefinition.path(
                    "gamEntryDate", "gamEntryDate",
                    Set.of(ComparationMethods.EQUALS, ComparationMethods.GREATER_THAN_OR_EQUAL, ComparationMethods.LESS_THAN_OR_EQUAL),
                    Map.of(ComparationMethods.EQUALS, SearchValueParsers::localDate,
                            ComparationMethods.GREATER_THAN_OR_EQUAL, SearchValueParsers::localDate,
                            ComparationMethods.LESS_THAN_OR_EQUAL, SearchValueParsers::localDate)
            )),
            Map.entry("residentialCity", SearchFilterDefinition.path(
                    "residentialCity", "residentialCity", Set.of(ComparationMethods.EQUALS, ComparationMethods.LIKE),
                    Map.of(ComparationMethods.EQUALS, SearchValueParsers::text,
                            ComparationMethods.LIKE, SearchValueParsers::text)
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
            Map.entry("contactEmail", SearchFilterDefinition.path(
                    "contactEmail",
                    "contactEmail",
                    Set.of(ComparationMethods.EQUALS, ComparationMethods.LIKE),
                    Map.of(ComparationMethods.EQUALS, SearchValueParsers::emailEquals,
                            ComparationMethods.LIKE, SearchValueParsers::emailLike)
            )),
            Map.entry("accountEmail", SearchFilterDefinition.path(
                    "accountEmail",
                    "account.email",
                    Set.of(ComparationMethods.EQUALS, ComparationMethods.LIKE),
                    Map.of(
                            ComparationMethods.EQUALS, SearchValueParsers::emailEquals,
                            ComparationMethods.LIKE, SearchValueParsers::emailLike
                    )
            )),
            Map.entry("hasLinkedAccount", new SearchFilterDefinition<>(
                    "hasLinkedAccount", Set.of(ComparationMethods.EQUALS),
                    Map.of(ComparationMethods.EQUALS, SearchValueParsers::booleanValue),
                    (method, value) -> (root, query, builder) -> Boolean.TRUE.equals(value)
                            ? builder.isNotNull(root.get("account")) : builder.isNull(root.get("account"))
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
            informationStatus("jornadaMissionaria", "experiences", "type", MemberExperienceType.JORNADA_MISSIONARIA),
            informationStatus("cursoDeLideranca", "experiences", "type", MemberExperienceType.CURSO_DE_LIDERANCA),
            informationStatus("pascoaJuvenil", "experiences", "type", MemberExperienceType.PASCOA_JUVENIL),
            informationStatus("acampabosco", "experiences", "type", MemberExperienceType.ACAMPABOSCO),
            informationStatus("batismo", "sacraments", "type", MemberSacramentType.BATISMO),
            informationStatus("primeiraComunhao", "sacraments", "type", MemberSacramentType.PRIMEIRA_COMUNHAO),
            informationStatus("crisma", "sacraments", "type", MemberSacramentType.CRISMA),
            Map.entry("contributionArea", new SearchFilterDefinition<>(
                    "contributionArea", Set.of(ComparationMethods.EQUALS, ComparationMethods.IN),
                    Map.of(ComparationMethods.EQUALS, SearchValueParsers.enumValue(MemberContributionArea.class),
                            ComparationMethods.IN, SearchValueParsers.in(SearchValueParsers.enumValue(MemberContributionArea.class))),
                    (method, value) -> (root, query, builder) -> {
                        query.distinct(true);
                        Join<Object, Object> contribution = root.join("contributionAreas");
                        return method == ComparationMethods.IN
                                ? contribution.in((java.util.Collection<?>) value)
                                : builder.equal(contribution, value);
                    }
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

    private static Map.Entry<String, SearchFilterDefinition<MemberEntity>> informationStatus(
            String publicName, String collection, String typeField, Enum<?> type) {
        return Map.entry(publicName, new SearchFilterDefinition<>(
                publicName, Set.of(ComparationMethods.EQUALS, ComparationMethods.IN),
                Map.of(ComparationMethods.EQUALS, SearchValueParsers.enumValue(InformationStatus.class),
                        ComparationMethods.IN, SearchValueParsers.in(SearchValueParsers.enumValue(InformationStatus.class))),
                (method, value) -> (root, query, builder) -> {
                    query.distinct(true);
                    Join<Object, Object> joined = root.join(collection);
                    var status = joined.get("status");
                    var statusPredicate = method == ComparationMethods.IN
                            ? status.in((java.util.Collection<?>) value) : builder.equal(status, value);
                    return builder.and(builder.equal(joined.get(typeField), type), statusPredicate);
                }
        ));
    }

    @Override
    public Specification<MemberEntity> convert(SearchDTO searchDTO) {
        return ResourceSearchFilterConverter.convert(searchDTO, DEFINITIONS);
    }
}
