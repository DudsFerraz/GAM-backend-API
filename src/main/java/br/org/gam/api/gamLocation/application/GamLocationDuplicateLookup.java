package br.org.gam.api.gamLocation.application;

import br.org.gam.api.gamLocation.persistence.GamLocationRepository;
import br.org.gam.api.gamLocation.persistence.GamLocationEntity;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GamLocationDuplicateLookup {
    private final GamLocationRepository repository;

    public GamLocationDuplicateLookup(GamLocationRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public Optional<GamLocationEntity> findActiveDuplicate(GamLocationNormalizer.Values values) {
        return repository.findActiveDuplicate(
                values.identityName(), values.identityStreet(), values.identityCity(), values.identityState(),
                values.identityPostalCode(), values.identityCountryCode()
        );
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public Optional<GamLocationEntity> findActiveDuplicateExcluding(java.util.UUID excludedId,
                                                                  GamLocationNormalizer.Values values) {
        return repository.findActiveDuplicateExcluding(
                excludedId, values.identityName(), values.identityStreet(), values.identityCity(),
                values.identityState(), values.identityPostalCode(), values.identityCountryCode()
        );
    }
}
