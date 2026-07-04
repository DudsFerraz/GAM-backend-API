package br.org.gam.api.shared.maintenance;

import br.org.gam.api.shared.activitylog.ActivityAction;
import br.org.gam.api.shared.activitylog.ActivityEvents;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("maintenance")
public class SoftDeletedRecordMaintenanceJob implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(SoftDeletedRecordMaintenanceJob.class);
    private static final UUID BULK_TARGET_ID = new UUID(0L, 0L);

    private static final Set<String> ALLOWED_TABLES = Set.of(
            "accounts",
            "roles",
            "permissions",
            "account_roles",
            "role_permissions",
            "locations",
            "events",
            "members",
            "presences",
            "oratorios",
            "missas",
            "oratorianos"
    );

    private final EntityManager entityManager;
    private final ActivityEvents activityEvents;
    private final ConfigurableApplicationContext applicationContext;

    public SoftDeletedRecordMaintenanceJob(EntityManager entityManager, ActivityEvents activityEvents,
                                           ConfigurableApplicationContext applicationContext) {
        this.entityManager = entityManager;
        this.activityEvents = activityEvents;
        this.applicationContext = applicationContext;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        String action = requiredOption(args, "maintenance.action");
        String table = requiredOption(args, "maintenance.table");
        validateTable(table);

        switch (action) {
            case "inspect-soft-deleted" -> inspectSoftDeleted(table);
            case "restore" -> restore(table, requiredUuid(args, "maintenance.id"), requiredOption(args, "maintenance.reason"));
            case "hard-delete" -> hardDelete(table, requiredUuid(args, "maintenance.id"), requiredOption(args, "maintenance.reason"));
            default -> throw new IllegalArgumentException("Unsupported maintenance.action: " + action);
        }

        SpringApplication.exit(applicationContext, () -> 0);
    }

    private void inspectSoftDeleted(String table) {
        List<?> ids = entityManager.createNativeQuery(
                        "SELECT id FROM " + table + " WHERE deleted_at IS NOT NULL ORDER BY deleted_at DESC")
                .getResultList();

        log.info("Soft-deleted records in {}: {}", table, ids);
        activityEvents.developerMaintenance(
                ActivityAction.DEVELOPER_VIEWED_SOFT_DELETED_RECORDS,
                BULK_TARGET_ID,
                table,
                null,
                "Developer inspected soft-deleted records in " + table,
                Map.of("table", table, "count", ids.size())
        );
    }

    private void restore(String table, UUID id, String reason) {
        int updated = entityManager.createNativeQuery(
                        "UPDATE " + table + " SET deleted_at = NULL, deleted_by = NULL WHERE id = :id AND deleted_at IS NOT NULL")
                .setParameter("id", id)
                .executeUpdate();

        if (updated != 1) {
            throw new IllegalArgumentException("Expected one soft-deleted record to restore, but restored " + updated + ".");
        }

        activityEvents.developerMaintenance(
                ActivityAction.DEVELOPER_RESTORE_EXECUTED,
                id,
                table,
                reason,
                "Developer restored soft-deleted record " + table + "/" + id,
                Map.of("table", table, "id", id)
        );
    }

    private void hardDelete(String table, UUID id, String reason) {
        int deleted = entityManager.createNativeQuery(
                        "DELETE FROM " + table + " WHERE id = :id AND deleted_at IS NOT NULL")
                .setParameter("id", id)
                .executeUpdate();

        if (deleted != 1) {
            throw new IllegalArgumentException("Expected one soft-deleted record to hard delete, but deleted " + deleted + ".");
        }

        activityEvents.developerMaintenance(
                ActivityAction.DEVELOPER_HARD_DELETE_EXECUTED,
                id,
                table,
                reason,
                "Developer hard-deleted soft-deleted record " + table + "/" + id,
                Map.of("table", table, "id", id)
        );
    }

    private void validateTable(String table) {
        if (!ALLOWED_TABLES.contains(table)) {
            throw new IllegalArgumentException("Unsupported maintenance.table: " + table);
        }
    }

    private String requiredOption(ApplicationArguments args, String name) {
        List<String> values = args.getOptionValues(name);
        if (values == null || values.isEmpty() || values.getFirst().isBlank()) {
            throw new IllegalArgumentException("Missing required option --" + name);
        }
        return values.getFirst().trim();
    }

    private UUID requiredUuid(ApplicationArguments args, String name) {
        return UUID.fromString(requiredOption(args, name));
    }
}
