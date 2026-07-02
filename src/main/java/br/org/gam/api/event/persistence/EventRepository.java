package br.org.gam.api.event.persistence;

import br.org.gam.api.shared.persistence.BaseRepository;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface EventRepository extends BaseRepository<EventEntity, UUID>,
                                          JpaSpecificationExecutor<EventEntity> {
}
