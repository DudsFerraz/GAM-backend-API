package br.org.gam.api.shared.persistence;

import jakarta.persistence.EntityManager;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.repository.support.JpaEntityInformation;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactoryBean;
import org.springframework.data.jpa.repository.support.JpaRepositoryImplementation;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.core.RepositoryInformation;
import org.springframework.data.repository.core.support.RepositoryFactorySupport;

public class GamJpaRepositoryFactoryBean<T extends Repository<S, ID>, S, ID>
        extends JpaRepositoryFactoryBean<T, S, ID> {

    private Clock clock;

    public GamJpaRepositoryFactoryBean(Class<? extends T> repositoryInterface) {
        super(repositoryInterface);
    }

    @Autowired
    public void setClock(Clock clock) {
        this.clock = clock;
    }

    @Override
    protected RepositoryFactorySupport createRepositoryFactory(EntityManager entityManager) {
        return new GamJpaRepositoryFactory(entityManager, clock);
    }

    private static class GamJpaRepositoryFactory extends JpaRepositoryFactory {

        private final Clock clock;

        GamJpaRepositoryFactory(EntityManager entityManager, Clock clock) {
            super(entityManager);
            this.clock = clock;
        }

        @Override
        @SuppressWarnings({"rawtypes", "unchecked"})
        protected JpaRepositoryImplementation<?, ?> getTargetRepository(
                RepositoryInformation information, EntityManager entityManager) {
            JpaEntityInformation<?, ?> entityInformation = getEntityInformation(information.getDomainType());
            return new DefaultBaseRepository(entityInformation, entityManager, clock);
        }
    }
}
