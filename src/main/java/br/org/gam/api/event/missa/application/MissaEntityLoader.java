package br.org.gam.api.event.missa.application;

import br.org.gam.api.event.missa.persistence.MissaEntity;
import br.org.gam.api.event.missa.persistence.MissaRepository;
import br.org.gam.api.shared.exception.NotFoundException;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class MissaEntityLoader {

    private final MissaRepository missaRepo;

    public MissaEntityLoader(MissaRepository missaRepo) {
        this.missaRepo = missaRepo;
    }

    public MissaEntity requiredById(UUID id) {
        return missaRepo.findById(id)
                .orElseThrow(() -> NotFoundException.resource("Missa", id));
    }
}
