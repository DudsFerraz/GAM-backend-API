package br.org.gam.api.event.Oratorio.persistence;

import br.org.gam.api.event.Oratorio.domain.Oratorio;
import br.org.gam.api.shared.persistence.BaseRepository;
import java.util.UUID;

public interface OratorioRepository extends BaseRepository<OratorioEntity, UUID> {
}
