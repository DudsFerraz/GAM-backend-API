package br.org.gam.api.shared.activitylog;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ActivityLogRepository extends JpaRepository<ActivityLogEntity, UUID> {
}
