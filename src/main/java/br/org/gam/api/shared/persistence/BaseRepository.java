package br.org.gam.api.shared.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.NoRepositoryBean;

@NoRepositoryBean
public interface BaseRepository<T extends SoftDeletable, ID> extends JpaRepository<T, ID> {
}
