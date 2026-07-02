package br.org.gam.api.event.Missa.application.useCases.GetMissaInstance;

import br.org.gam.api.event.Missa.application.MissaMapper;
import br.org.gam.api.event.Missa.application.MissaNotFoundException;
import br.org.gam.api.event.Missa.domain.Missa;
import br.org.gam.api.event.Missa.persistence.MissaEntity;
import br.org.gam.api.event.Missa.persistence.MissaRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class SpringGetMissaInstance implements GetMissaInstance {
    private final MissaRepository missaRepo;
    private final MissaMapper missaMapper;

    public SpringGetMissaInstance(MissaRepository missaRepo, MissaMapper missaMapper) {
        this.missaRepo = missaRepo;
        this.missaMapper = missaMapper;
    }

    @Override
    public Missa domainById(UUID id) {
        return missaRepo.findById(id)
                .map(missaMapper::entityToDomain)
                .orElseThrow(() -> new MissaNotFoundException("Could not find missa with id " + id));
    }

    @Override
    public MissaEntity entityById(UUID id) {
        return missaRepo.findById(id)
                .orElseThrow(() -> new MissaNotFoundException("Could not find missa with id " + id));
    }
}
