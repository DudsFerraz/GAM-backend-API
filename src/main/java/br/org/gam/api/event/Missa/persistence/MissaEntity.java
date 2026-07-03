package br.org.gam.api.event.Missa.persistence;

import br.org.gam.api.event.Missa.domain.Missa;
import br.org.gam.api.event.persistence.EventEntity;
import br.org.gam.api.member.persistence.MemberEntity;
import br.org.gam.api.shared.auditing.FullAuditableEntity;
import jakarta.persistence.*;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

@EqualsAndHashCode(onlyExplicitlyIncluded = true)
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

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "comentarios_member", referencedColumnName = "id", unique = true)
    private MemberEntity comentariosMember;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "leitura_1_member", referencedColumnName = "id", unique = true)
    private MemberEntity leitura1Member;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "salmo_member", referencedColumnName = "id", unique = true)
    private MemberEntity salmoMember;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "leitura_2_member", referencedColumnName = "id", unique = true)
    private MemberEntity leitura2Member;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "preces_member", referencedColumnName = "id", unique = true)
    private MemberEntity precesMember;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "missa_acolhida_members",
            joinColumns = @JoinColumn(name = "missa_id"),
            inverseJoinColumns = @JoinColumn(name = "member_id")
    )
    private Set<MemberEntity> acolhidaMembers = new HashSet<>();
}
