package br.org.gam.api.event.Missa.persistence;

import br.org.gam.api.event.Missa.domain.Missa;
import br.org.gam.api.shared.persistence.BaseRepository;
import java.util.UUID;

public interface MissaRepository extends BaseRepository<MissaEntity, UUID> {
}
