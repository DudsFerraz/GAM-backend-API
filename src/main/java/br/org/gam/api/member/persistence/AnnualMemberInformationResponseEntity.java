package br.org.gam.api.member.persistence;

import br.org.gam.api.member.domain.InformationStatus;
import br.org.gam.api.member.domain.MemberCoordinationInterest;
import br.org.gam.api.member.domain.MemberMassAttendanceFrequency;
import br.org.gam.api.member.domain.MemberOccupation;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "annual_member_information_responses",
        uniqueConstraints = @UniqueConstraint(columnNames = {"member_id", "survey_cycle"}))
public class AnnualMemberInformationResponseEntity {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private MemberEntity member;
    @Column(name = "survey_cycle", nullable = false) private int surveyCycle;
    @Column(name = "submitted_at") private Instant submittedAt;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "annual_member_occupations", joinColumns = @JoinColumn(name = "response_id"))
    @Column(name = "occupation", nullable = false)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private Set<MemberOccupation> occupations = new LinkedHashSet<>();
    @Column(name = "occupations_details", length = 2000) private String occupationsDetails;

    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "health_condition_status", nullable = false) private InformationStatus healthConditionStatus;
    @Column(name = "health_condition_details", length = 2000) private String healthConditionDetails;
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "religious_vocation_considered", nullable = false) private InformationStatus religiousVocationConsidered;
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "mass_attendance_frequency", nullable = false) private MemberMassAttendanceFrequency massAttendanceFrequency;
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "saturday_oratorio_impediment_status", nullable = false) private InformationStatus saturdayOratorioImpedimentStatus;
    @Column(name = "saturday_oratorio_impediment_details", length = 2000) private String saturdayOratorioImpedimentDetails;
    @Column(name = "formation_and_meeting_interests", length = 2000) private String formationAndMeetingInterests;
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "coordination_interest", nullable = false) private MemberCoordinationInterest coordinationInterest;
    @Column(name = "additional_comments", length = 2000) private String additionalComments;
    @Column(name = "oratorio_activity_suggestions", length = 2000) private String oratorioActivitySuggestions;
    @Column(name = "instagram_post_suggestions", length = 2000) private String instagramPostSuggestions;
    @Column(name = "import_batch_id") private UUID importBatchId;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
}
