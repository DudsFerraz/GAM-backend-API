package br.org.gam.api.rbac.role.persistence;

import br.org.gam.api.rbac.accountRole.persistence.AccountRoleEntity;
import br.org.gam.api.rbac.rolePermission.persistence.RolePermissionEntity;
import br.org.gam.api.shared.auditing.FullAuditableEntity;
import jakarta.persistence.*;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.SQLRestriction;

@BatchSize(size = 20)
@SQLRestriction("deleted_at IS NULL")
@Setter
@Getter
@NoArgsConstructor
@Entity
@Table(name = "roles")
public class RoleEntity extends FullAuditableEntity {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description", nullable = false)
    private String description;

    @Column(name = "system_managed", nullable = false)
    private boolean systemManaged;

    @OneToMany(mappedBy = "role", fetch = FetchType.LAZY)
    private Set<AccountRoleEntity> accountRoles = new HashSet<>();

    @OneToMany(mappedBy = "role", fetch = FetchType.LAZY)
    private Set<RolePermissionEntity> rolePermissions = new HashSet<>();
}
