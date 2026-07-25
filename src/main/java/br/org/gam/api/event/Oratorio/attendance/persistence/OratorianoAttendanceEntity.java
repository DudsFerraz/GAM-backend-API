package br.org.gam.api.event.oratorio.attendance.persistence;

import br.org.gam.api.event.oratorio.persistence.OratorioEntity;
import br.org.gam.api.oratoriano.persistence.OratorianoEntity;
import br.org.gam.api.shared.auditing.FullAuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

@Getter
@Setter
@NoArgsConstructor
@Entity
@SQLRestriction("deleted_at IS NULL")
@Table(name = "oratoriano_attendances")
public class OratorianoAttendanceEntity extends FullAuditableEntity {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "oratorio_id", nullable = false, updatable = false)
    private OratorioEntity oratorio;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "oratoriano_id", nullable = false, updatable = false)
    private OratorianoEntity oratoriano;

    @Column(name = "registered_at", nullable = false, updatable = false)
    private Instant registeredAt;
}
