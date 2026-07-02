package br.org.gam.api.account.persistence;

import br.org.gam.api.account.domain.MyEmail;
import br.org.gam.api.shared.persistence.BaseRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface AccountRepository extends BaseRepository<AccountEntity, UUID>,
                                            JpaSpecificationExecutor<AccountEntity> {

    boolean existsByEmail(MyEmail email);
    Optional<AccountEntity> findByEmail(MyEmail email);
}
