package br.org.gam.api.rbac.Permission.persistence;

import br.org.gam.api.shared.auditing.FullAuditableEntity;
import jakarta.persistence.*;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

@SQLRestriction("deleted_at IS NULL")
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "permissions")
public class PermissionEntity extends FullAuditableEntity {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description", nullable = false)
    private String description;
}
