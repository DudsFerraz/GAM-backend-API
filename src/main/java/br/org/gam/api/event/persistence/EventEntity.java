package br.org.gam.api.event.persistence;

import br.org.gam.api.event.domain.EventStatus;
import br.org.gam.api.event.domain.EventType;
import br.org.gam.api.gamLocation.persistence.GamLocationEntity;
import br.org.gam.api.rbac.permission.persistence.PermissionEntity;
import br.org.gam.api.shared.auditing.FullAuditableEntity;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.type.SqlTypes;

@Setter
@Entity
@Getter
@NoArgsConstructor
@SQLRestriction("deleted_at IS NULL")
@Table(name = "events")
public class EventEntity extends FullAuditableEntity {

    @Id
    private UUID id;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "description")
    private String description;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "gam_location_id", referencedColumnName = "id", nullable = false)
    private GamLocationEntity location;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "required_permission_id", referencedColumnName = "id")
    private PermissionEntity requiredPermission;

    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "type", nullable = false)
    private EventType type;

    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "status", nullable = false)
    private EventStatus status;

    @Column(name = "cancellation_reason", columnDefinition = "TEXT")
    private String cancellationReason;

    @Column(name = "begin_date", nullable = false)
    private Instant beginDate;

    @Column(name = "end_date", nullable = false)
    private Instant endDate;

}
