package br.org.gam.api.member.solicitation.persistence;

import br.org.gam.api.account.persistence.AccountEntity;
import br.org.gam.api.member.persistence.MemberEntity;
import br.org.gam.api.member.solicitation.domain.MembershipSolicitationStatus;
import br.org.gam.api.shared.auditing.FullAuditableEntity;
import br.org.gam.api.shared.domain.GamName;
import br.org.gam.api.shared.domain.GamEmail;
import br.org.gam.api.shared.phonenumber.GamPhoneNumber;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Formula;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Setter
@NoArgsConstructor
@Entity
@SQLRestriction("deleted_at IS NULL")
@Table(name = "membership_solicitations")
public class MembershipSolicitationEntity extends FullAuditableEntity {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private AccountEntity account;

    @Column(name = "account_id", insertable = false, updatable = false)
    private UUID applicantAccountId;

    @Formula("(select applicant.email from accounts applicant where applicant.id = account_id)")
    private String applicantEmail;

    @Formula("(select applicant.display_name from accounts applicant where applicant.id = account_id)")
    private String applicantDisplayName;

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

    @Column(name = "justification", nullable = false, length = 2000)
    private String justification;

    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "status", nullable = false, columnDefinition = "membership_solicitation_status_enum")
    private MembershipSolicitationStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by_account_id")
    private AccountEntity reviewedBy;

    @Column(name = "reviewed_by_account_id", insertable = false, updatable = false)
    private UUID reviewerAccountId;

    @Formula("(select reviewer.email from accounts reviewer where reviewer.id = reviewed_by_account_id)")
    private String reviewerEmail;

    @Formula("(select reviewer.display_name from accounts reviewer where reviewer.id = reviewed_by_account_id)")
    private String reviewerDisplayName;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "review_reason", length = 2000)
    private String reviewReason;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id")
    private MemberEntity member;

    @Column(name = "member_id", insertable = false, updatable = false)
    private UUID approvedMemberId;

    @Version
    @Column(name = "version", nullable = false)
    private long version;
}
