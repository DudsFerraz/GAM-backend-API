package br.org.gam.api.member.persistence;

import br.org.gam.api.member.domain.InformationStatus;
import br.org.gam.api.member.domain.MemberExperienceType;
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
public class MemberExperienceValue {
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "experience_type", nullable = false)
    private MemberExperienceType type;

    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "status", nullable = false)
    private InformationStatus status;

    @Override public boolean equals(Object other) {
        return other instanceof MemberExperienceValue value && type == value.type;
    }
    @Override public int hashCode() { return Objects.hash(type); }
}
