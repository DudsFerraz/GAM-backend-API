package br.org.gam.api.location.persistence;

import br.org.gam.api.shared.persistence.BaseRepository;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LocationRepository extends BaseRepository<LocationEntity, UUID> {
}
