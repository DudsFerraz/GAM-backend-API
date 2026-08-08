package br.org.gam.api.rbac.accountRole.persistence;

import br.org.gam.api.shared.persistence.BaseRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface AccountRoleRepository extends BaseRepository<AccountRoleEntity, UUID> {
    @Query("""
            select accountRole
            from AccountRoleEntity accountRole
            join accountRole.account account
            join accountRole.role role
            where account.id = :accountId
              and role.id = :roleId
              and accountRole.deletedAt is null
              and account.deletedAt is null
              and role.deletedAt is null
            """)
    Optional<AccountRoleEntity> findByAccount_IdAndRole_Id(@Param("accountId") UUID accountId,
                                                            @Param("roleId") UUID roleId);

    @Query("""
            select accountRole
            from AccountRoleEntity accountRole
            join fetch accountRole.account account
            join fetch accountRole.role role
            where accountRole.id = :assignmentId
              and account.id = :accountId
              and accountRole.deletedAt is null
              and account.deletedAt is null
              and role.deletedAt is null
            """)
    Optional<AccountRoleEntity> findActiveAssignment(@Param("accountId") UUID accountId,
                                                      @Param("assignmentId") UUID assignmentId);

    @Query("""
            select accountRole
            from AccountRoleEntity accountRole
            join accountRole.account account
            join accountRole.role role
            where account.id = :accountId
              and accountRole.deletedAt is null
              and account.deletedAt is null
              and role.deletedAt is null
            """)
    List<AccountRoleEntity> findAllByAccount_Id(@Param("accountId") UUID accountId);

    boolean existsByAccount_IdAndRole_Id(UUID accountId, UUID roleId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select accountRole
            from AccountRoleEntity accountRole
            join accountRole.account account
            join accountRole.role role
            where role.name = :roleName
              and accountRole.deletedAt is null
              and account.deletedAt is null
              and role.deletedAt is null
            """)
    List<AccountRoleEntity> lockActiveAccountRolesByRoleName(@Param("roleName") String roleName);

    @Query("""
            select accountRole
            from AccountRoleEntity accountRole
            join accountRole.account account
            join accountRole.role role
            where role.name = :roleName
              and accountRole.deletedAt is null
              and account.deletedAt is null
              and role.deletedAt is null
            """)
    List<AccountRoleEntity> findActiveAccountRolesByRoleName(@Param("roleName") String roleName);

    @Query("""
            select count(accountRole) > 0
            from AccountRoleEntity accountRole
            join accountRole.account account
            join accountRole.role role
            where account.id = :accountId
              and role.name = :roleName
              and accountRole.deletedAt is null
              and account.deletedAt is null
              and role.deletedAt is null
            """)
    boolean existsActiveByAccountIdAndRoleName(@Param("accountId") UUID accountId,
                                                @Param("roleName") String roleName);
}
