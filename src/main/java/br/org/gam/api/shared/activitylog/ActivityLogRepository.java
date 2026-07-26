package br.org.gam.api.shared.activitylog;

import java.util.UUID;
import org.springframework.data.repository.Repository;

public interface ActivityLogRepository extends Repository<ActivityLogEntity, UUID> {

    <S extends ActivityLogEntity> S save(S activity);
}
