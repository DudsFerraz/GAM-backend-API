package br.org.gam.api.gamLocation.application;

import br.org.gam.api.gamLocation.persistence.GamLocationEntity;
import br.org.gam.api.shared.exception.ForbiddenOperationException;

public final class GamLocationMutationPolicy {
    private GamLocationMutationPolicy() {
    }

    public static void requireUserManaged(GamLocationEntity entity) {
        if (entity.isSystemManaged()) {
            throw ForbiddenOperationException.resource(
                    "GamLocation",
                    entity.getId(),
                    "System-managed GamLocations cannot be changed through product workflows."
            );
        }
    }
}
