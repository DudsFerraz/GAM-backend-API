package br.org.gam.api.shared.activitylog;

import br.org.gam.api.shared.persistence.UUIDGenerator;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "activity_logs")
public class ActivityLogEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 100, updatable = false)
    private ActivityAction action;

    @Enumerated(EnumType.STRING)
    @Column(name = "actor_kind", nullable = false, length = 20, updatable = false)
    private ActivityActorKind actorKind;

    @Column(name = "actor_account_id", updatable = false)
    private UUID actorAccountId;

    @Column(name = "actor_reference", length = 255, updatable = false)
    private String actorReference;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 100, updatable = false)
    private ActivityTargetType targetType;

    @Column(name = "target_id", updatable = false)
    private UUID targetId;

    @Column(name = "target_scope", length = 255, updatable = false)
    private String targetScope;

    @Column(name = "reason", columnDefinition = "TEXT", updatable = false)
    private String reason;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", nullable = false, columnDefinition = "jsonb", updatable = false)
    private Map<String, Object> metadata = Map.of();

    @Column(name = "request_id", updatable = false)
    private UUID requestId;

    ActivityLogEntity(
            ActivityAction action,
            ActivityActorKind actorKind,
            UUID actorAccountId,
            String actorReference,
            ActivityTargetType targetType,
            UUID targetId,
            String targetScope,
            String reason,
            Map<String, Object> metadata,
            UUID requestId
    ) {
        this.action = action;
        this.actorKind = actorKind;
        this.actorAccountId = actorAccountId;
        this.actorReference = actorReference;
        this.targetType = targetType;
        this.targetId = targetId;
        this.targetScope = targetScope;
        this.reason = reason;
        this.metadata = Collections.unmodifiableMap(new LinkedHashMap<>(metadata));
        this.requestId = requestId;
    }

    @PrePersist
    void prePersist() {
        if (id == null) {
            id = UUIDGenerator.generateUUIDV7();
        }
        if (occurredAt == null) {
            occurredAt = Instant.now();
        }
    }

    public String getRequestId() {
        return requestId == null ? null : requestId.toString();
    }

    public String getIpAddress() {
        return null;
    }

    public String getUserAgent() {
        return null;
    }
}
