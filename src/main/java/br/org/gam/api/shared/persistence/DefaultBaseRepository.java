package br.org.gam.api.shared.persistence;

import br.org.gam.api.security.application.AccountDetails;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.repository.support.JpaEntityInformation;
import org.springframework.data.jpa.repository.support.SimpleJpaRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

public class DefaultBaseRepository<T extends SoftDeletable, ID> extends SimpleJpaRepository<T, ID>
                            implements BaseRepository<T, ID> {
    private final JpaEntityInformation<T, ?> entityInformation;
    private final EntityManager entityManager;
    private final Clock clock;

    public DefaultBaseRepository(JpaEntityInformation<T, ?> entityInformation, EntityManager entityManager,
                                 Clock clock) {
        super(entityInformation, entityManager);
        this.entityInformation = entityInformation;
        this.entityManager = entityManager;
        this.clock = clock;
    }

    private UUID getCurrentAuditor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof AccountDetails accountDetails)) {
            return null;
        }
        return accountDetails.getId();
    }

    @Override
    @Transactional
    public void delete(T entity) {
        final Instant now = clock.instant();
        final UUID deletedBy = getCurrentAuditor();

        int updatedRows = entityManager.createQuery("""
                        update %s entity
                        set entity.deletedAt = :deletedAt,
                            entity.deletedBy = :deletedBy
                        where entity = :entity
                          and entity.deletedAt is null
                        """.formatted(entityInformation.getEntityName()))
                .setParameter("deletedAt", now)
                .setParameter("deletedBy", deletedBy)
                .setParameter("entity", entity)
                .executeUpdate();

        if (updatedRows == 1) {
            entity.setDeletedAt(now);
            entity.setDeletedBy(deletedBy);
            entityManager.detach(entity);
        }
    }
}
