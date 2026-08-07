package br.org.gam.api.member.persistence;

import br.org.gam.api.account.persistence.AccountEntity;
import br.org.gam.api.member.domain.MemberStatus;
import br.org.gam.api.member.domain.InformationStatus;
import br.org.gam.api.member.domain.MemberContributionArea;
import br.org.gam.api.shared.auditing.FullAuditableEntity;
import br.org.gam.api.shared.domain.GamName;
import br.org.gam.api.shared.domain.GamEmail;
import br.org.gam.api.shared.phonenumber.GamPhoneNumber;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.type.SqlTypes;

@Setter
@Getter
@NoArgsConstructor
@Entity
@SQLRestriction("deleted_at IS NULL")
@Table(name = "members")
public class MemberEntity extends FullAuditableEntity {

    @Id
    @Column(name = "id")
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", referencedColumnName = "id", unique = true)
    private AccountEntity account;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Embedded
    private GamName name;

    @Column(name = "birth_date", nullable = false)
    private LocalDate birthDate;

    @Column(name = "gam_entry_date", nullable = false)
    private LocalDate gamEntryDate;

    @Column(name = "residential_city", nullable = false, length = 100)
    private String residentialCity;

    @Column(name = "phone_number", nullable = false)
    private GamPhoneNumber phoneNumber;

    @Column(name = "contact_email", nullable = false, length = 320)
    private GamEmail contactEmail;

    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "dietary_restriction_status", nullable = false)
    private InformationStatus dietaryRestrictionStatus = InformationStatus.NOT_INFORMED;

    @Column(name = "dietary_restriction_details", length = 2000)
    private String dietaryRestrictionDetails;

    @Column(name = "import_batch_id")
    private UUID importBatchId;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "member_experiences", joinColumns = @JoinColumn(name = "member_id"))
    private Set<MemberExperienceValue> experiences = new LinkedHashSet<>();

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "member_sacraments", joinColumns = @JoinColumn(name = "member_id"))
    private Set<MemberSacramentValue> sacraments = new LinkedHashSet<>();

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "member_contribution_areas", joinColumns = @JoinColumn(name = "member_id"))
    @Column(name = "contribution_area", nullable = false)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private Set<MemberContributionArea> contributionAreas = new LinkedHashSet<>();

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "member_other_contribution_areas", joinColumns = @JoinColumn(name = "member_id"))
    @Column(name = "contribution_area", nullable = false, length = 100)
    private Set<String> otherContributionAreas = new LinkedHashSet<>();

    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "status")
    private MemberStatus status;
}
