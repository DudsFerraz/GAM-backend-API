package br.org.gam.api.oratoriano.application;

import br.org.gam.api.oratoriano.persistence.OratorianoEntity;
import br.org.gam.api.oratoriano.persistence.OratorianoRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class OratorianoEntityLoader {

    private final OratorianoRepository oratorianoRepo;

    public OratorianoEntityLoader(OratorianoRepository oratorianoRepo) {
        this.oratorianoRepo = oratorianoRepo;
    }

    public OratorianoEntity requiredById(UUID id) {
        return oratorianoRepo.findById(id)
                .orElseThrow(() -> new OratorianoNotFoundException("Could not find oratoriano with id " + id));
    }
}
