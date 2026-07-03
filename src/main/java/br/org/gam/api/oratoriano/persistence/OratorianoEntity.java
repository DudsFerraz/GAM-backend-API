package br.org.gam.api.oratoriano.persistence;

import br.org.gam.api.shared.auditing.FullAuditableEntity;
import br.org.gam.api.shared.domain.Name;
import br.org.gam.api.shared.phonenumber.MyPhoneNumber;
import jakarta.persistence.*;
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
    private Name name;

    @Column(name = "birth_date")
    private LocalDate birthDate;

    @Column(name = "phone_number")
    private MyPhoneNumber phoneNumber;
}
