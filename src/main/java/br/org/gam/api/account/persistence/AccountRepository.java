package br.org.gam.api.account.persistence;

import br.org.gam.api.shared.domain.GamEmail;
import br.org.gam.api.shared.persistence.BaseRepository;
import br.org.gam.api.shared.persistence.ReadOnlySpecificationExecutor;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface AccountRepository extends BaseRepository<AccountEntity, UUID>,
                                            ReadOnlySpecificationExecutor<AccountEntity> {

    boolean existsByEmail(GamEmail email);
    Optional<AccountEntity> findByEmail(GamEmail email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select account
            from AccountEntity account
            where account.id = :id
            """)
    Optional<AccountEntity> findByIdForUpdate(@Param("id") UUID id);
}
