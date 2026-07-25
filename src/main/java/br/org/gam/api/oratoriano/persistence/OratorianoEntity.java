package br.org.gam.api.oratoriano.persistence;

import br.org.gam.api.shared.auditing.FullAuditableEntity;
import br.org.gam.api.shared.domain.GamName;
import br.org.gam.api.shared.phonenumber.GamPhoneNumber;
import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

@Setter
@Getter
@NoArgsConstructor
@Entity
@SQLRestriction("deleted_at IS NULL")
@Table(name = "oratorianos")
public class OratorianoEntity extends FullAuditableEntity {

    @Id
    @Column(name = "id")
    private UUID id;

    @Embedded
    private GamName name;

    @Column(name = "name_key", nullable = false, updatable = true, columnDefinition = "TEXT")
    private String nameKey;

    @Column(name = "birth_date")
    private LocalDate birthDate;

    @Column(name = "phone_number")
    private GamPhoneNumber phoneNumber;

    @Column(name = "name_source_form_id")
    private UUID nameSourceFormId;

    @Column(name = "name_source_signed_on")
    private LocalDate nameSourceSignedOn;

    @Column(name = "name_manual_updated_at")
    private Instant nameManualUpdatedAt;

    @Column(name = "birth_date_source_form_id")
    private UUID birthDateSourceFormId;

    @Column(name = "birth_date_source_signed_on")
    private LocalDate birthDateSourceSignedOn;

    @Column(name = "birth_date_manual_updated_at")
    private Instant birthDateManualUpdatedAt;

    @Column(name = "phone_source_form_id")
    private UUID phoneSourceFormId;

    @Column(name = "phone_source_signed_on")
    private LocalDate phoneSourceSignedOn;

    @Column(name = "phone_manual_updated_at")
    private Instant phoneManualUpdatedAt;
}
