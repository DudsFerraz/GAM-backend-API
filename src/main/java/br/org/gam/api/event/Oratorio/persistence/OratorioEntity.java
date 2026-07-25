package br.org.gam.api.event.oratorio.persistence;

import br.org.gam.api.event.persistence.EventEntity;
import br.org.gam.api.member.persistence.MemberEntity;
import br.org.gam.api.oratoriano.persistence.OratorianoEntity;
import br.org.gam.api.shared.auditing.FullAuditableEntity;
import jakarta.persistence.*;
import java.util.HashSet;
import java.time.LocalDate;
import java.util.Set;
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
@Table(name = "oratorios")
public class OratorioEntity extends FullAuditableEntity {

    @EqualsAndHashCode.Include
    @Id
    private UUID id;

    @OneToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "event_id", referencedColumnName = "id")
    private EventEntity event;

    @Column(name = "cancellation_reason", columnDefinition = "TEXT")
    private String cancellationReason;

    @Column(name = "local_date", nullable = false, updatable = false)
    private LocalDate localDate;

    @Column(name = "lanche_description", columnDefinition = "TEXT")
    private String lancheDescription;

    @Column(name = "gincana_description", columnDefinition = "TEXT")
    private String gincanaDescription;

    @Column(name = "boa_tarde_criancas_plan", columnDefinition = "TEXT")
    private String boaTardeCriancasPlan;

    @Column(name = "boa_tarde_jovens_plan", columnDefinition = "TEXT")
    private String boaTardeJovensPlan;

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
