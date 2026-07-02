package br.org.gam.api.security.refreshtoken.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshTokenEntity, UUID> {
    Optional<RefreshTokenEntity> findByToken(UUID token);

    @Modifying
    @Query("delete from RefreshTokenEntity refreshToken where refreshToken.token = :token")
    void deleteByToken(@Param("token") UUID token);
}
