package br.org.gam.api.account.persistence;

import br.org.gam.api.account.domain.MyEmail;
import br.org.gam.api.rbac.AccountRole.domain.AccountRole;
import br.org.gam.api.rbac.AccountRole.persistence.AccountRoleEntity;
import br.org.gam.api.shared.auditing.FullAuditableEntity;
import jakarta.persistence.*;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.Set;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.SQLRestriction;

@SQLRestriction("deleted_at is NULL")
@Setter
@Getter
@NoArgsConstructor
@Entity
@Table(name = "accounts")
public class AccountEntity extends FullAuditableEntity {

    @Id
    @Column(name = "id")
    private UUID id;

    @Convert(converter = EmailConverterJPA.class)
    @Column(name = "email", nullable = false)
    private MyEmail email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @OneToMany(mappedBy = "account")
    @BatchSize(size = 20)
    private Set<AccountRoleEntity> accountRoles;
}
