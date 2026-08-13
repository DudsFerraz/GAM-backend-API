package br.org.gam.api.event.missa.persistence;

import br.org.gam.api.event.persistence.EventEntity;
import br.org.gam.api.shared.auditing.FullAuditableEntity;
import jakarta.persistence.*;
import java.util.UUID;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = false)
@Setter
@Getter
@Entity
@NoArgsConstructor
@SQLRestriction("deleted_at IS NULL")
@Table(name = "missas")
public class MissaEntity extends FullAuditableEntity {

    @EqualsAndHashCode.Include
    @Id
    private UUID id;

    @OneToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "event_id", referencedColumnName = "id")
    private EventEntity event;

}
