package br.org.gam.api.shared.activitylog;

import br.org.gam.api.rbac.role.domain.SystemRole;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.AuditorAware;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Service
public class ActivityLogger {
    private final ActivityLogRepository activityLogRepository;
    private final AuditorAware<UUID> auditorAware;

    public ActivityLogger(ActivityLogRepository activityLogRepository, AuditorAware<UUID> auditorAware) {
        this.activityLogRepository = activityLogRepository;
        this.auditorAware = auditorAware;
    }

    public void log(
            ActivityAction action,
            ActivityTargetType targetType,
            UUID targetId,
            String reason,
            String ignoredSummary,
            Map<String, Object> metadata
    ) {
        Actor actor = resolveActor(action);
        persist(action, actor, targetType, targetId, null, reason, metadata);
    }

    public void logAnonymous(
            ActivityAction action,
            ActivityTargetType targetType,
            UUID targetId,
            String reason,
            Map<String, Object> metadata
    ) {
        persist(
                action,
                new Actor(ActivityActorKind.ANONYMOUS, null, null),
                targetType,
                targetId,
                null,
                reason,
                metadata
        );
    }

    public void logScope(
            ActivityAction action,
            ActivityTargetType targetType,
            String targetScope,
            String reason,
            Map<String, Object> metadata
    ) {
        persist(action, resolveActor(action), targetType, null, targetScope, reason, metadata);
    }

    void logDeveloper(
            ActivityAction action,
            ActivityTargetType targetType,
            UUID targetId,
            String reason,
            Map<String, Object> metadata
    ) {
        persist(action, developerActor(), targetType, targetId, null, reason, metadata);
    }

    private void persist(
            ActivityAction action,
            Actor actor,
            ActivityTargetType targetType,
            UUID targetId,
            String targetScope,
            String reason,
            Map<String, Object> metadata
    ) {
        requireNonNull(action, "Activity action is required.");
        requireNonNull(targetType, "Activity target type is required.");
        validateActor(action, actor);
        validateTarget(action, targetType, targetId, targetScope);

        Map<String, Object> normalizedMetadata = normalizeMetadata(action, metadata);
        String normalizedReason = normalizeReason(action, reason, normalizedMetadata);
        UUID requestId = currentRequest()
                .map(RequestCorrelationFilter::requestId)
                .orElse(null);

        activityLogRepository.save(new ActivityLogEntity(
                action,
                actor.kind(),
                actor.accountId(),
                actor.reference(),
                targetType,
                targetId,
                targetScope,
                normalizedReason,
                normalizedMetadata,
                requestId
        ));
    }

    private Actor resolveActor(ActivityAction action) {
        Optional<UUID> accountId = auditorAware.getCurrentAuditor();
        if (accountId.isPresent()) {
            return new Actor(ActivityActorKind.ACCOUNT, accountId.get(), null);
        }

        if (isDeveloperAction(action)) {
            return developerActor();
        }

        throw new IllegalArgumentException("A trusted Account actor is required.");
    }

    private Actor developerActor() {
        return new Actor(ActivityActorKind.DEVELOPER, null, DeveloperActorReference.resolveRequired());
    }

    private void validateActor(ActivityAction action, Actor actor) {
        boolean allowed = switch (action) {
            case ACCOUNT_REGISTERED -> actor.kind() == ActivityActorKind.ANONYMOUS;
            case ACCOUNT_ROLE_ADDED, ACCOUNT_ROLE_REMOVED ->
                    actor.kind() == ActivityActorKind.ACCOUNT || actor.kind() == ActivityActorKind.DEVELOPER;
            case DEVELOPER_RESTORE_EXECUTED,
                 DEVELOPER_HARD_DELETE_EXECUTED,
                 DEVELOPER_VIEWED_SOFT_DELETED_RECORDS,
                 MEMBER_INFORMATION_IMPORTED -> actor.kind() == ActivityActorKind.DEVELOPER;
            default -> actor.kind() == ActivityActorKind.ACCOUNT;
        };

        if (!allowed) {
            throw new IllegalArgumentException("Actor kind is not registered for activity action " + action + ".");
        }

        boolean validForm = switch (actor.kind()) {
            case ACCOUNT -> actor.accountId() != null && actor.reference() == null;
            case ANONYMOUS -> actor.accountId() == null && actor.reference() == null;
            case SYSTEM, DEVELOPER -> actor.accountId() == null
                    && actor.reference() != null
                    && !actor.reference().isBlank();
        };
        if (!validForm) {
            throw new IllegalArgumentException("Invalid activity actor form.");
        }
    }

    private void validateTarget(
            ActivityAction action,
            ActivityTargetType targetType,
            UUID targetId,
            String targetScope
    ) {
        if ((targetId == null) == (targetScope == null)) {
            throw new IllegalArgumentException("Exactly one activity target form is required.");
        }
        if (targetScope != null && targetScope.isBlank()) {
            throw new IllegalArgumentException("Activity target scope must not be blank.");
        }
        if (action == ActivityAction.DEVELOPER_VIEWED_SOFT_DELETED_RECORDS) {
            if (targetId != null || !"SOFT_DELETED_RECORDS".equals(targetScope)) {
                throw new IllegalArgumentException(
                        "Developer deleted-record inspection requires the SOFT_DELETED_RECORDS scope."
                );
            }
            return;
        }

        ActivityTargetType expected = expectedTargetType(action);
        if (expected != null && targetType != expected) {
            throw new IllegalArgumentException(
                    "Activity action " + action + " requires target type " + expected + "."
            );
        }
        if (expected != null && targetId == null) {
            throw new IllegalArgumentException(
                    "Activity action " + action + " requires a resource target."
            );
        }
    }

    private ActivityTargetType expectedTargetType(ActivityAction action) {
        return switch (action) {
            case ACCOUNT_REGISTERED -> ActivityTargetType.ACCOUNT;
            case ACCOUNT_ROLE_ADDED, ACCOUNT_ROLE_REMOVED -> ActivityTargetType.ACCOUNT_ROLE_ASSIGNMENT;
            case EVENT_CREATED, EVENT_UPDATED, EVENT_CANCELLED, EVENT_LOCKED, EVENT_FINALIZED,
                 EVENT_REOPENED, EVENT_DELETED -> ActivityTargetType.EVENT;
            case GAM_LOCATION_CREATED, GAM_LOCATION_UPDATED, GAM_LOCATION_REMOVED -> ActivityTargetType.GAM_LOCATION;
            case MEMBER_REGISTERED, MEMBER_ACTIVATED, MEMBER_DEACTIVATED, MEMBER_ACCOUNT_LINKED, COORDINATOR_GRANTED,
                 COORDINATOR_REVOKED, ORATORIO_COORDINATOR_GRANTED, ORATORIO_COORDINATOR_REVOKED,
                 MEMBER_PROFILE_UPDATED, MEMBER_GAM_ENTRY_DATE_UPDATED, MEMBER_DIETARY_RESTRICTION_UPDATED,
                 MEMBER_EXPERIENCES_UPDATED, MEMBER_SACRAMENTS_UPDATED, MEMBER_CONTRIBUTION_PROFILE_UPDATED ->
                    ActivityTargetType.MEMBER;
            case MEMBER_ANNUAL_INFORMATION_READ -> ActivityTargetType.MEMBER_ANNUAL_INFORMATION_RESPONSE;
            case MEMBER_INFORMATION_IMPORTED -> ActivityTargetType.MEMBER_INFORMATION_IMPORT_BATCH;
            case MEMBERSHIP_SOLICITATION_SUBMITTED, MEMBERSHIP_SOLICITATION_APPROVED,
                 MEMBERSHIP_SOLICITATION_REJECTED -> ActivityTargetType.MEMBERSHIP_SOLICITATION;
            case PRESENCE_REGISTERED, PRESENCE_UPDATED, PRESENCE_REMOVED -> ActivityTargetType.PRESENCE;
            case ORATORIO_CREATED, ORATORIO_PLANNING_UPDATED, ORATORIO_TEAM_MEMBER_ASSIGNED,
                 ORATORIO_TEAM_MEMBER_REMOVED, ORATORIO_CANCELLED, ORATORIO_LOCKED,
                 ORATORIO_FINALIZED, ORATORIO_REOPENED, ORATORIO_DELETED,
                 ORATORIO_MEMBER_ATTENDANCE_REGISTERED, ORATORIO_MEMBER_ATTENDANCE_REMOVED,
                 ORATORIANO_ATTENDANCE_REGISTERED, ORATORIANO_ATTENDANCE_REMOVED,
                 ORATORIANO_REGISTERED_AND_MARKED_PRESENT, ORATORIANO_REGISTERED,
                 ORATORIANO_UPDATED, ORATORIANO_DELETED, ORATORIANO_RESTORED,
                 ORATORIANO_FORM_DRAFT_CREATED, ORATORIANO_FORM_DRAFT_UPDATED,
                 ORATORIANO_FORM_DRAFT_DELETED, ORATORIANO_FORM_COMPLETED, ORATORIANO_FORM_REVOKED,
                 ORATORIANO_FORM_PRINT_SNAPSHOT_CREATED, ORATORIANO_FORM_PDF_RENDERED,
                 ORATORIANO_FORM_DETAIL_READ, ORATORIANO_FORM_ATTACHMENTS_REPLACED,
                 ORATORIANO_FORM_ATTACHMENT_DOWNLOADED -> null;
            case DEVELOPER_RESTORE_EXECUTED, DEVELOPER_HARD_DELETE_EXECUTED,
                 DEVELOPER_VIEWED_SOFT_DELETED_RECORDS -> null;
        };
    }

    private String normalizeReason(
            ActivityAction action,
            String reason,
        Map<String, Object> metadata
    ) {
        if (action == ActivityAction.EVENT_UPDATED) {
            boolean audienceChanged = contains(metadata.get("changedFields"), "requiredPermissionId");
            if (reason == null) {
                if (audienceChanged) {
                    throw new IllegalArgumentException("Activity action EVENT_UPDATED requires a reason.");
                }
                return null;
            }
            return ActivityReasonNormalizer.normalizeSupplied(reason);
        }

        if (reasonMode(action) == ReasonMode.NONE) {
            if (reason != null) {
                throw new IllegalArgumentException("Activity action " + action + " does not accept a reason.");
            }
            return null;
        }

        if (reason == null) {
            if (reasonMode(action) == ReasonMode.REQUIRED) {
                throw new IllegalArgumentException("Activity action " + action + " requires a reason.");
            }
            return null;
        }

        return ActivityReasonNormalizer.normalizeSupplied(reason);
    }

    private boolean contains(Object values, String expected) {
        for (Object value : (Iterable<?>) values) {
            if (expected.equals(value)) {
                return true;
            }
        }
        return false;
    }

    private ReasonMode reasonMode(ActivityAction action) {
        return switch (action) {
            case ACCOUNT_REGISTERED, EVENT_CREATED, EVENT_LOCKED, EVENT_FINALIZED,
                 GAM_LOCATION_CREATED, GAM_LOCATION_UPDATED, MEMBERSHIP_SOLICITATION_SUBMITTED,
                 ORATORIO_CREATED, ORATORIO_LOCKED, ORATORIO_FINALIZED,
                 ORATORIO_MEMBER_ATTENDANCE_REGISTERED, ORATORIANO_ATTENDANCE_REGISTERED,
                 ORATORIANO_REGISTERED_AND_MARKED_PRESENT, ORATORIANO_REGISTERED,
                 ORATORIANO_FORM_DRAFT_CREATED, ORATORIANO_FORM_DRAFT_UPDATED,
                 ORATORIANO_FORM_COMPLETED, ORATORIANO_FORM_PRINT_SNAPSHOT_CREATED,
                 ORATORIANO_FORM_PDF_RENDERED, ORATORIANO_FORM_DETAIL_READ,
                 ORATORIANO_FORM_ATTACHMENTS_REPLACED, ORATORIANO_FORM_ATTACHMENT_DOWNLOADED,
                 PRESENCE_REGISTERED, PRESENCE_UPDATED, MEMBER_ANNUAL_INFORMATION_READ -> ReasonMode.NONE;
            case ACCOUNT_ROLE_ADDED, ACCOUNT_ROLE_REMOVED, EVENT_CANCELLED, EVENT_REOPENED,
                 EVENT_DELETED, GAM_LOCATION_REMOVED, MEMBER_REGISTERED, MEMBER_ACTIVATED,
                 MEMBER_DEACTIVATED, MEMBER_ACCOUNT_LINKED, MEMBER_PROFILE_UPDATED,
                 MEMBER_GAM_ENTRY_DATE_UPDATED, MEMBER_DIETARY_RESTRICTION_UPDATED,
                 MEMBER_EXPERIENCES_UPDATED, MEMBER_SACRAMENTS_UPDATED,
                 MEMBER_CONTRIBUTION_PROFILE_UPDATED, MEMBER_INFORMATION_IMPORTED,
                 COORDINATOR_GRANTED, COORDINATOR_REVOKED,
                 ORATORIO_COORDINATOR_GRANTED, ORATORIO_COORDINATOR_REVOKED,
                 MEMBERSHIP_SOLICITATION_APPROVED, MEMBERSHIP_SOLICITATION_REJECTED,
                 ORATORIO_CANCELLED, ORATORIO_REOPENED, ORATORIO_DELETED,
                 ORATORIANO_DELETED, ORATORIANO_RESTORED, ORATORIANO_FORM_DRAFT_DELETED,
                 ORATORIANO_FORM_REVOKED, PRESENCE_REMOVED, DEVELOPER_RESTORE_EXECUTED,
                 DEVELOPER_HARD_DELETE_EXECUTED, DEVELOPER_VIEWED_SOFT_DELETED_RECORDS ->
                    ReasonMode.REQUIRED;
            default -> ReasonMode.OPTIONAL;
        };
    }

    private Map<String, Object> normalizeMetadata(ActivityAction action, Map<String, Object> metadata) {
        Map<String, Object> normalized = metadata == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(metadata));
        if (normalized.containsKey(null)) {
            throw new IllegalArgumentException("Activity metadata keys must not be null.");
        }
        if (normalized.keySet().stream().anyMatch(this::isProhibitedMetadataKey)) {
            throw new IllegalArgumentException("Activity metadata contains a prohibited key.");
        }

        switch (action) {
            case ACCOUNT_REGISTERED, GAM_LOCATION_CREATED, GAM_LOCATION_REMOVED ->
                    requireExactKeys(normalized, Set.of());
            case ACCOUNT_ROLE_ADDED, ACCOUNT_ROLE_REMOVED -> {
                requireExactKeys(normalized, Set.of("accountId", "roleId", "systemManaged"));
                requireType(normalized, "accountId", UUID.class);
                requireType(normalized, "roleId", UUID.class);
                requireType(normalized, "systemManaged", Boolean.class);
            }
            case EVENT_CREATED -> {
                requireAllowedKeys(
                        normalized,
                        Set.of("type", "status", "gamLocationId", "requiredPermissionId"),
                        Set.of("type", "status", "gamLocationId", "requiredPermissionId")
                );
                requireType(normalized, "type", String.class);
                requireType(normalized, "status", String.class);
                requireType(normalized, "gamLocationId", UUID.class);
                requireOptionalType(normalized, "requiredPermissionId", UUID.class);
            }
            case EVENT_UPDATED -> {
                requireAllowedKeys(
                        normalized,
                        Set.of("changedFields", "fromStatus", "toStatus"),
                        Set.of("changedFields")
                );
                requireStableEventChangedFields(normalized.get("changedFields"));
                boolean hasFromStatus = normalized.containsKey("fromStatus");
                boolean hasToStatus = normalized.containsKey("toStatus");
                if (hasFromStatus != hasToStatus) {
                    throw new IllegalArgumentException(
                            "EVENT_UPDATED status metadata requires both fromStatus and toStatus."
                    );
                }
                if (hasFromStatus) {
                    requireType(normalized, "fromStatus", String.class);
                    requireType(normalized, "toStatus", String.class);
                }
            }
            case EVENT_CANCELLED, EVENT_LOCKED, EVENT_FINALIZED, EVENT_REOPENED -> {
                requireExactKeys(normalized, Set.of("fromStatus", "toStatus"));
                requireType(normalized, "fromStatus", String.class);
                requireType(normalized, "toStatus", String.class);
            }
            case EVENT_DELETED -> {
                requireExactKeys(normalized, Set.of("type", "fromStatus", "gamLocationId"));
                requireType(normalized, "type", String.class);
                requireType(normalized, "fromStatus", String.class);
                requireType(normalized, "gamLocationId", UUID.class);
            }
            case COORDINATOR_GRANTED, COORDINATOR_REVOKED,
                 ORATORIO_COORDINATOR_GRANTED, ORATORIO_COORDINATOR_REVOKED -> {
                requireExactKeys(normalized, Set.of("accountId", "roleId"));
                requireType(normalized, "accountId", UUID.class);
                requireType(normalized, "roleId", UUID.class);
            }
            case MEMBER_ACCOUNT_LINKED -> {
                requireExactKeys(normalized, Set.of("accountId", "roles"));
                requireType(normalized, "accountId", UUID.class);
                Object roles = normalized.get("roles");
                if (!(roles instanceof List<?> roleValues)
                        || roleValues.size() != 1
                        || !(SystemRole.MEMBER.getCode().equals(roleValues.getFirst())
                        || SystemRole.VISITOR.getCode().equals(roleValues.getFirst()))) {
                    throw new IllegalArgumentException(
                            "MEMBER_ACCOUNT_LINKED roles must contain exactly MEMBER or VISITOR."
                    );
                }
            }
            case MEMBER_PROFILE_UPDATED, MEMBER_GAM_ENTRY_DATE_UPDATED,
                 MEMBER_DIETARY_RESTRICTION_UPDATED, MEMBER_EXPERIENCES_UPDATED,
                 MEMBER_SACRAMENTS_UPDATED, MEMBER_CONTRIBUTION_PROFILE_UPDATED -> {
                requireExactKeys(normalized, Set.of("changedFields"));
                Object changedFields = normalized.get("changedFields");
                if (!(changedFields instanceof List<?> values) || values.isEmpty()
                        || values.stream().anyMatch(value -> !(value instanceof String))) {
                    throw new IllegalArgumentException("Member update changedFields must be a non-empty string list.");
                }
            }
            case MEMBER_ANNUAL_INFORMATION_READ -> {
                requireExactKeys(normalized, Set.of("memberId", "surveyCycle"));
                requireType(normalized, "memberId", UUID.class);
                requireType(normalized, "surveyCycle", Integer.class);
            }
            case MEMBER_INFORMATION_IMPORTED -> {
                requireExactKeys(normalized, Set.of("surveyCycle", "memberCount", "responseCount"));
                requireType(normalized, "surveyCycle", Integer.class);
                requireType(normalized, "memberCount", Integer.class);
                requireType(normalized, "responseCount", Integer.class);
            }
            case ORATORIO_CREATED, ORATORIANO_REGISTERED, ORATORIANO_DELETED, ORATORIANO_RESTORED ->
                    requireExactKeys(normalized, Set.of());
            case ORATORIO_PLANNING_UPDATED -> {
                requireExactKeys(normalized, Set.of("changedFields"));
                requireChangedFields(
                        normalized.get("changedFields"),
                        java.util.List.of(
                                "lancheDescription",
                                "gincanaDescription",
                                "boaTardeCriancasPlan",
                                "boaTardeJovensPlan"
                        )
                );
            }
            case ORATORIO_TEAM_MEMBER_ASSIGNED, ORATORIO_TEAM_MEMBER_REMOVED -> {
                requireExactKeys(normalized, Set.of("memberId", "teamType"));
                requireType(normalized, "memberId", UUID.class);
                requireType(normalized, "teamType", String.class);
            }
            case ORATORIO_CANCELLED, ORATORIO_LOCKED, ORATORIO_FINALIZED, ORATORIO_REOPENED -> {
                requireExactKeys(normalized, Set.of("fromStatus", "toStatus"));
                requireType(normalized, "fromStatus", String.class);
                requireType(normalized, "toStatus", String.class);
            }
            case ORATORIO_DELETED -> {
                requireExactKeys(normalized, Set.of("status"));
                requireType(normalized, "status", String.class);
            }
            case ORATORIO_MEMBER_ATTENDANCE_REGISTERED, ORATORIO_MEMBER_ATTENDANCE_REMOVED -> {
                requireExactKeys(normalized, Set.of("oratorioId", "memberId"));
                requireType(normalized, "oratorioId", UUID.class);
                requireType(normalized, "memberId", UUID.class);
            }
            case ORATORIANO_ATTENDANCE_REGISTERED, ORATORIANO_ATTENDANCE_REMOVED,
                 ORATORIANO_REGISTERED_AND_MARKED_PRESENT -> {
                requireExactKeys(normalized, Set.of("oratorioId", "oratorianoId"));
                requireType(normalized, "oratorioId", UUID.class);
                requireType(normalized, "oratorianoId", UUID.class);
            }
            case ORATORIANO_UPDATED -> {
                requireExactKeys(normalized, Set.of("changedFields"));
                requireChangedFields(
                        normalized.get("changedFields"),
                        java.util.List.of("name", "birthDate", "phoneNumber")
                );
            }
            case ORATORIANO_FORM_DRAFT_CREATED -> {
                requireExactKeys(normalized, Set.of("oratorianoId", "origin"));
                requireType(normalized, "oratorianoId", UUID.class);
                requireType(normalized, "origin", String.class);
            }
            case ORATORIANO_FORM_DRAFT_UPDATED -> {
                requireExactKeys(normalized, Set.of("oratorianoId", "draftRevision"));
                requireType(normalized, "oratorianoId", UUID.class);
                requireType(normalized, "draftRevision", Long.class);
            }
            case ORATORIANO_FORM_DRAFT_DELETED, ORATORIANO_FORM_COMPLETED,
                 ORATORIANO_FORM_REVOKED, ORATORIANO_FORM_DETAIL_READ -> {
                requireExactKeys(normalized, Set.of("oratorianoId"));
                requireType(normalized, "oratorianoId", UUID.class);
            }
            case ORATORIANO_FORM_PRINT_SNAPSHOT_CREATED, ORATORIANO_FORM_PDF_RENDERED -> {
                requireExactKeys(normalized, Set.of("oratorianoId", "printSnapshotId"));
                requireType(normalized, "oratorianoId", UUID.class);
                requireType(normalized, "printSnapshotId", UUID.class);
            }
            case ORATORIANO_FORM_ATTACHMENTS_REPLACED -> {
                requireExactKeys(normalized, Set.of("oratorianoId", "attachmentCount"));
                requireType(normalized, "oratorianoId", UUID.class);
                requireNonNegativeInteger(normalized, "attachmentCount");
            }
            case ORATORIANO_FORM_ATTACHMENT_DOWNLOADED -> {
                requireExactKeys(normalized, Set.of("oratorianoId", "attachmentId"));
                requireType(normalized, "oratorianoId", UUID.class);
                requireType(normalized, "attachmentId", UUID.class);
            }
            case GAM_LOCATION_UPDATED -> {
                requireExactKeys(normalized, Set.of("changedFields"));
                Object changedFields = normalized.get("changedFields");
                if (!(changedFields instanceof Iterable<?> values)) {
                    throw new IllegalArgumentException("changedFields must be a collection.");
                }
                Set<String> unique = new HashSet<>();
                for (Object value : values) {
                    if (!(value instanceof String field) || field.isBlank() || !unique.add(field)) {
                        throw new IllegalArgumentException("changedFields must contain unique nonblank names.");
                    }
                }
            }
            case PRESENCE_REGISTERED, PRESENCE_REMOVED -> {
                requireExactKeys(normalized, Set.of("memberId", "eventId", "observationsPresent"));
                requireType(normalized, "memberId", UUID.class);
                requireType(normalized, "eventId", UUID.class);
                requireType(normalized, "observationsPresent", Boolean.class);
            }
            case PRESENCE_UPDATED -> {
                requireExactKeys(
                        normalized,
                        Set.of("memberId", "eventId", "previousObservationsPresent", "newObservationsPresent")
                );
                requireType(normalized, "memberId", UUID.class);
                requireType(normalized, "eventId", UUID.class);
                requireType(normalized, "previousObservationsPresent", Boolean.class);
                requireType(normalized, "newObservationsPresent", Boolean.class);
            }
            case DEVELOPER_VIEWED_SOFT_DELETED_RECORDS -> {
                requireExactKeys(normalized, Set.of("count"));
                requireType(normalized, "count", Integer.class);
                if ((Integer) normalized.get("count") < 0) {
                    throw new IllegalArgumentException("Deleted-record inspection count must be non-negative.");
                }
            }
            default -> {
                // Feature-owned schemas remain validated at their typed event construction seams.
            }
        }
        return normalized;
    }

    private void requireStableEventChangedFields(Object changedFields) {
        if (!(changedFields instanceof java.util.List<?> values) || values.isEmpty()) {
            throw new IllegalArgumentException("changedFields must be a non-empty array.");
        }
        java.util.List<String> stableOrder = java.util.List.of(
                "title",
                "description",
                "gamLocationId",
                "requiredPermissionId",
                "beginDate",
                "endDate"
        );
        int previousIndex = -1;
        Set<String> unique = new HashSet<>();
        for (Object value : values) {
            if (!(value instanceof String field) || !unique.add(field)) {
                throw new IllegalArgumentException("changedFields must contain distinct stable field names.");
            }
            int index = stableOrder.indexOf(field);
            if (index < 0 || index <= previousIndex) {
                throw new IllegalArgumentException("changedFields must follow the stable field order.");
            }
            previousIndex = index;
        }
    }

    private void requireChangedFields(Object changedFields, java.util.List<String> stableOrder) {
        if (!(changedFields instanceof java.util.List<?> values) || values.isEmpty()) {
            throw new IllegalArgumentException("changedFields must be a non-empty array.");
        }
        int previousIndex = -1;
        Set<String> unique = new HashSet<>();
        for (Object value : values) {
            if (!(value instanceof String field) || !unique.add(field)) {
                throw new IllegalArgumentException("changedFields must contain distinct stable field names.");
            }
            int index = stableOrder.indexOf(field);
            if (index < 0 || index <= previousIndex) {
                throw new IllegalArgumentException("changedFields must follow the stable field order.");
            }
            previousIndex = index;
        }
    }

    private boolean isProhibitedMetadataKey(String key) {
        String normalized = key.toLowerCase(java.util.Locale.ROOT);
        return normalized.contains("password")
                || normalized.contains("token")
                || normalized.contains("cookie")
                || normalized.contains("authorization")
                || normalized.contains("csrf")
                || normalized.contains("secret")
                || (normalized.contains("observations") && !normalized.endsWith("observationspresent"))
                || normalized.contains("ipaddress")
                || normalized.contains("useragent");
    }

    private void requireExactKeys(Map<String, Object> metadata, Set<String> keys) {
        requireAllowedKeys(metadata, keys, keys);
    }

    private void requireAllowedKeys(
            Map<String, Object> metadata,
            Set<String> allowed,
            Set<String> required
    ) {
        if (!allowed.containsAll(metadata.keySet()) || !metadata.keySet().containsAll(required)) {
            throw new IllegalArgumentException("Activity metadata does not satisfy its closed schema.");
        }
    }

    private void requireType(Map<String, Object> metadata, String key, Class<?> type) {
        if (!type.isInstance(metadata.get(key))) {
            throw new IllegalArgumentException("Activity metadata key " + key + " has an invalid type.");
        }
    }

    private void requireOptionalType(Map<String, Object> metadata, String key, Class<?> type) {
        if (metadata.containsKey(key) && metadata.get(key) != null && !type.isInstance(metadata.get(key))) {
            throw new IllegalArgumentException("Activity metadata key " + key + " has an invalid type.");
        }
    }

    private void requireNonNegativeInteger(Map<String, Object> metadata, String key) {
        requireType(metadata, key, Integer.class);
        if ((Integer) metadata.get(key) < 0) {
            throw new IllegalArgumentException("Activity metadata key " + key + " must be non-negative.");
        }
    }

    private Optional<HttpServletRequest> currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return Optional.of(attributes.getRequest());
        }
        return Optional.empty();
    }

    private boolean isDeveloperAction(ActivityAction action) {
        return action == ActivityAction.DEVELOPER_RESTORE_EXECUTED
                || action == ActivityAction.DEVELOPER_HARD_DELETE_EXECUTED
                || action == ActivityAction.DEVELOPER_VIEWED_SOFT_DELETED_RECORDS
                || action == ActivityAction.MEMBER_INFORMATION_IMPORTED;
    }

    private <T> T requireNonNull(T value, String message) {
        if (value == null) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    private enum ReasonMode {
        NONE,
        OPTIONAL,
        REQUIRED
    }

    private record Actor(ActivityActorKind kind, UUID accountId, String reference) {
    }
}
