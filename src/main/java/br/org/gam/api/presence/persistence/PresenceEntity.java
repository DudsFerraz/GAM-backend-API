package br.org.gam.api.presence.persistence;

import br.org.gam.api.event.persistence.EventEntity;
import br.org.gam.api.member.persistence.MemberEntity;
import br.org.gam.api.shared.auditing.FullAuditableEntity;
import jakarta.persistence.*;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

@Setter
@Entity
@Getter
@NoArgsConstructor
@SQLRestriction("deleted_at IS NULL")
@Table(name = "presences")
public class PresenceEntity extends FullAuditableEntity {

    @Id
    @Column(name = "id")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id")
    private MemberEntity member;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id")
    private EventEntity event;

    @Column(name = "observations")
    private String observations;
}
