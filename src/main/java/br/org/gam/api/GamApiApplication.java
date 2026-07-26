package br.org.gam.api;

import br.org.gam.api.shared.persistence.DefaultBaseRepository;
import br.org.gam.api.shared.persistence.GamJpaRepositoryFactoryBean;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.SpringApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@EnableJpaRepositories(
		basePackages = "br.org.gam.api",
		repositoryBaseClass = DefaultBaseRepository.class,
		repositoryFactoryBeanClass = GamJpaRepositoryFactoryBean.class
)
@EnableJpaAuditing(dateTimeProviderRef = "auditingDateTimeProvider")
@SpringBootApplication
public class GamApiApplication {
    public static void main(String[] args) {
		SpringApplication.run(GamApiApplication.class, args);
	}

}
