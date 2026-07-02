package br.org.gam.api.event.Oratorio.persistence;

import br.org.gam.api.event.Oratorio.domain.Oratorio;
import br.org.gam.api.event.persistence.EventEntity;
import br.org.gam.api.member.persistence.MemberEntity;
import br.org.gam.api.oratoriano.persistence.OratorianoEntity;
import br.org.gam.api.shared.auditing.FullAuditableEntity;
import jakarta.persistence.*;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Setter
@Getter
@Entity
@NoArgsConstructor
@Table(name = "oratorios")
public class OratorioEntity extends FullAuditableEntity {

    @EqualsAndHashCode.Include
    @Id
    private UUID id;

    @OneToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "event_id", referencedColumnName = "id", unique = true)
    private EventEntity event;

    @Column(name = "cancellation_reason", columnDefinition = "TEXT")
    private String cancellationReason;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "oratorio_lanche",
            joinColumns = @JoinColumn(name = "oratorio_id"),
            inverseJoinColumns = @JoinColumn(name = "member_id")
    )
    private Set<MemberEntity> lancheMembers = new HashSet<>();

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "oratorio_bt_jovens",
            joinColumns = @JoinColumn(name = "oratorio_id"),
            inverseJoinColumns = @JoinColumn(name = "member_id")
    )
    private Set<MemberEntity> btJovensMembers = new HashSet<>();

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "oratorio_bt_criancas",
            joinColumns = @JoinColumn(name = "oratorio_id"),
            inverseJoinColumns = @JoinColumn(name = "member_id")
    )
    private Set<MemberEntity> btCriancasMembers = new HashSet<>();

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "oratorio_presences_oratorianos",
            joinColumns = @JoinColumn(name = "oratorio_id"),
            inverseJoinColumns = @JoinColumn(name = "oratoriano_id")
    )
    private Set<OratorianoEntity> oratorianos  = new HashSet<>();

}
