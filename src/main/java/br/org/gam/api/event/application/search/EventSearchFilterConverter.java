package br.org.gam.api.event.application.search;

import br.org.gam.api.event.domain.EventStatus;
import br.org.gam.api.event.domain.EventType;
import br.org.gam.api.event.persistence.EventEntity;
import br.org.gam.api.shared.specification.ComparationMethods;
import br.org.gam.api.shared.specification.ResourceSearchFilterConverter;
import br.org.gam.api.shared.specification.SearchDTO;
import br.org.gam.api.shared.specification.SearchFilterConverter;
import br.org.gam.api.shared.specification.SearchFilterDefinition;
import br.org.gam.api.shared.specification.SearchValueParsers;
import java.util.Map;
import java.util.HashMap;
import java.time.Instant;
import java.util.Collection;
import java.util.Set;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

@Component
public class EventSearchFilterConverter implements SearchFilterConverter<EventEntity> {

    private static final Map<String, SearchFilterDefinition<EventEntity>> DEFINITIONS = Map.ofEntries(
            Map.entry("id", SearchFilterDefinition.path(
                    "id",
                    "id",
                    Set.of(ComparationMethods.EQUALS, ComparationMethods.IN),
                    Map.of(
                            ComparationMethods.EQUALS, SearchValueParsers::uuid,
                            ComparationMethods.IN, SearchValueParsers.in(SearchValueParsers::uuid)
                    )
            )),
            Map.entry("title", SearchFilterDefinition.path(
                    "title",
                    "title",
                    Set.of(ComparationMethods.EQUALS, ComparationMethods.LIKE),
                    Map.of(
                            ComparationMethods.EQUALS, SearchValueParsers.boundedText(255),
                            ComparationMethods.LIKE, SearchValueParsers.boundedText(255)
                    )
            )),
            Map.entry("description", SearchFilterDefinition.path(
                    "description",
                    "description",
                    Set.of(ComparationMethods.LIKE),
                    Map.of(ComparationMethods.LIKE, SearchValueParsers.boundedText(10_000))
            )),
            Map.entry("gamLocationId", SearchFilterDefinition.path(
                    "gamLocationId",
                    "location.id",
                    Set.of(ComparationMethods.EQUALS, ComparationMethods.IN),
                    Map.of(
                            ComparationMethods.EQUALS, SearchValueParsers::uuid,
                            ComparationMethods.IN, SearchValueParsers.in(SearchValueParsers::uuid)
                    )
            )),
            Map.entry("requiredPermissionId", SearchFilterDefinition.path(
                    "requiredPermissionId",
                    "requiredPermission.id",
                    Set.of(ComparationMethods.EQUALS, ComparationMethods.IN),
                    Map.of(
                            ComparationMethods.EQUALS, SearchValueParsers::uuid,
                            ComparationMethods.IN, SearchValueParsers.in(SearchValueParsers::uuid)
                    )
            )),
            Map.entry("requiredPermissionCode", SearchFilterDefinition.path(
                    "requiredPermissionCode",
                    "requiredPermission.code",
                    Set.of(ComparationMethods.EQUALS, ComparationMethods.IN),
                    Map.of(
                            ComparationMethods.EQUALS, SearchValueParsers::text,
                            ComparationMethods.IN, SearchValueParsers.in(SearchValueParsers::text)
                    )
            )),
            Map.entry("type", SearchFilterDefinition.path(
                    "type",
                    "type",
                    Set.of(ComparationMethods.EQUALS, ComparationMethods.IN),
                    Map.of(
                            ComparationMethods.EQUALS, SearchValueParsers.enumValue(EventType.class),
                            ComparationMethods.IN, SearchValueParsers.in(SearchValueParsers.enumValue(EventType.class))
                    )
            )),
            Map.entry("status", SearchFilterDefinition.path(
                    "status",
                    "status",
                    Set.of(ComparationMethods.EQUALS, ComparationMethods.IN),
                    Map.of(
                            ComparationMethods.EQUALS, SearchValueParsers.enumValue(EventStatus.class),
                            ComparationMethods.IN, SearchValueParsers.in(SearchValueParsers.enumValue(EventStatus.class))
                    )
            )),
            Map.entry("beginDate", SearchFilterDefinition.path(
                    "beginDate",
                    "beginDate",
                    Set.of(ComparationMethods.GREATER_THAN_OR_EQUAL, ComparationMethods.LESS_THAN_OR_EQUAL),
                    Map.of(
                            ComparationMethods.GREATER_THAN_OR_EQUAL, SearchValueParsers::instant,
                            ComparationMethods.LESS_THAN_OR_EQUAL, SearchValueParsers::instant
                    )
            )),
            Map.entry("endDate", SearchFilterDefinition.path(
                    "endDate",
                    "endDate",
                    Set.of(ComparationMethods.GREATER_THAN_OR_EQUAL, ComparationMethods.LESS_THAN_OR_EQUAL),
                    Map.of(
                            ComparationMethods.GREATER_THAN_OR_EQUAL, SearchValueParsers::instant,
                            ComparationMethods.LESS_THAN_OR_EQUAL, SearchValueParsers::instant
                    )
            ))
    );

    @Override
    public Specification<EventEntity> convert(SearchDTO searchDTO) {
        return ResourceSearchFilterConverter.convert(searchDTO, DEFINITIONS);
    }

    public Specification<EventEntity> convert(SearchDTO searchDTO, Instant evaluationInstant) {
        Map<String, SearchFilterDefinition<EventEntity>> definitions = new HashMap<>(DEFINITIONS);
        definitions.put("status", new SearchFilterDefinition<>(
                "status",
                Set.of(ComparationMethods.EQUALS, ComparationMethods.IN),
                Map.of(
                        ComparationMethods.EQUALS, SearchValueParsers.enumValue(EventStatus.class),
                        ComparationMethods.IN, SearchValueParsers.in(SearchValueParsers.enumValue(EventStatus.class))
                ),
                (method, value) -> effectiveStatusSpecification(value, evaluationInstant)
        ));
        return ResourceSearchFilterConverter.convert(searchDTO, definitions);
    }

    private Specification<EventEntity> effectiveStatusSpecification(Object value, Instant evaluationInstant) {
        Collection<?> values = value instanceof Collection<?> collection ? collection : java.util.List.of(value);
        return (root, query, cb) -> {
            var temporalState = root.get("status").in(EventStatus.SCHEDULED, EventStatus.COMPLETED);
            var result = cb.disjunction();
            for (Object item : values) {
                EventStatus status = (EventStatus) item;
                var predicate = switch (status) {
                    case SCHEDULED -> cb.and(
                            temporalState,
                            cb.greaterThan(root.get("endDate"), evaluationInstant)
                    );
                    case COMPLETED -> cb.and(
                            temporalState,
                            cb.lessThanOrEqualTo(root.get("endDate"), evaluationInstant)
                    );
                    case LOCKED, FINALIZED, CANCELLED -> cb.equal(root.get("status"), status);
                };
                result = cb.or(result, predicate);
            }
            return result;
        };
    }
}
