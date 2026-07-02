package br.org.gam.api;

import br.org.gam.api.shared.persistence.DefaultBaseRepository;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.SpringApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@EnableJpaRepositories(
		basePackages = "br.org.gam.api",
		repositoryBaseClass = DefaultBaseRepository.class
)
@EnableJpaAuditing
@SpringBootApplication
public class GamApiApplication {
    public static void main(String[] args) {
		SpringApplication.run(GamApiApplication.class, args);
	}

}
