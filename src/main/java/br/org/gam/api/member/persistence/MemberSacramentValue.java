package br.org.gam.api.member.persistence;

import br.org.gam.api.member.domain.InformationStatus;
import br.org.gam.api.member.domain.MemberSacramentType;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.util.Objects;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Embeddable
@NoArgsConstructor
@AllArgsConstructor
public class MemberSacramentValue {
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "sacrament_type", nullable = false)
    private MemberSacramentType type;

    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "status", nullable = false)
    private InformationStatus status;

    @Override public boolean equals(Object other) {
        return other instanceof MemberSacramentValue value && type == value.type;
    }
    @Override public int hashCode() { return Objects.hash(type); }
}
