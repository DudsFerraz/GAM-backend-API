package br.org.gam.api.shared.persistence;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.support.JpaEntityInformation;
import org.springframework.data.jpa.repository.support.SimpleJpaRepository;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

public class DefaultBaseRepository<T extends SoftDeletable, ID> extends SimpleJpaRepository<T, ID>
                            implements BaseRepository<T, ID>, ApplicationContextAware {
    private ApplicationContext applicationContext;
    private final EntityManager entityManager;

    public DefaultBaseRepository(JpaEntityInformation<T, ?> entityInformation, EntityManager entityManager) {

        super(entityInformation, entityManager);
        this.entityManager = entityManager;
    }

    @Override
    public void setApplicationContext(@NonNull ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    private AuditorAware<UUID> getAuditorAware() {
        try {
            return this.applicationContext.getBean(AuditorAware.class);
        } catch (Exception e) {
            return () -> Optional.empty();
        }
    }

    @Override
    @Transactional
    public void delete(T entity) {
        final Instant now = Instant.now();
        final UUID deletedBy = getAuditorAware().getCurrentAuditor().orElse(null);

        entity.setDeletedAt(now);
        entity.setDeletedBy(deletedBy);

        save(entity);
    }

    @Transactional
    @Override
    public void hardDelete(T entity) {
        entityManager.remove(entity);
    }

    @Override
    public List<T> findAllDeleted() {
        Class<T> domainType = getDomainClass();

        Table tableAnnotation = domainType.getAnnotation(Table.class);
        String tableName = (tableAnnotation != null && !tableAnnotation.name().isEmpty())
                ? tableAnnotation.name()
                : domainType.getSimpleName();

        String queryStr = "SELECT * FROM " + tableName + " WHERE deleted_at IS NOT NULL ORDER BY deleted_at DESC";

        return entityManager.createNativeQuery(queryStr, domainType).getResultList();
    }
}
