package br.org.gam.api.oratoriano.application.search;

import br.org.gam.api.oratoriano.persistence.OratorianoEntity;
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
public class OratorianoSearchFilterConverter implements SearchFilterConverter<OratorianoEntity> {

    private static final Map<String, SearchFilterDefinition<OratorianoEntity>> DEFINITIONS = Map.of(
            "id", SearchFilterDefinition.path(
                    "id",
                    "id",
                    Set.of(ComparationMethods.EQUALS, ComparationMethods.IN),
                    Map.of(
                            ComparationMethods.EQUALS, SearchValueParsers::uuid,
                            ComparationMethods.IN, SearchValueParsers.in(SearchValueParsers::uuid)
                    )
            ),
            "name", SearchFilterDefinition.path(
                    "name",
                    "nameKey",
                    Set.of(ComparationMethods.EQUALS, ComparationMethods.LIKE),
                    Map.of(
                            ComparationMethods.EQUALS, SearchValueParsers::humanEquivalentName,
                            ComparationMethods.LIKE, SearchValueParsers::humanEquivalentName
                    )
            )
    );

    @Override
    public Specification<OratorianoEntity> convert(SearchDTO searchDTO) {
        return ResourceSearchFilterConverter.convert(searchDTO, DEFINITIONS);
    }
}
