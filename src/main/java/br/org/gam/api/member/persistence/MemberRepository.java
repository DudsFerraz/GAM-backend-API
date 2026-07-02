package br.org.gam.api.member.persistence;

import br.org.gam.api.shared.persistence.BaseRepository;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface MemberRepository extends BaseRepository<MemberEntity, UUID>,
                                           JpaSpecificationExecutor<MemberEntity> {

    boolean existsByAccountId(UUID accountId);
}
