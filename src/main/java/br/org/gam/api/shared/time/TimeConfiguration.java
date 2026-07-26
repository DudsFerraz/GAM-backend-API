package br.org.gam.api.shared.time;

import java.time.Clock;
import java.time.ZoneId;
import java.util.Optional;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;

@Configuration(proxyBeanMethods = false)
public class TimeConfiguration {

    private static final ZoneId APPLICATION_ZONE = ZoneId.of("America/Sao_Paulo");

    @Bean
    Clock applicationClock() {
        return Clock.system(APPLICATION_ZONE);
    }

    @Bean
    DateTimeProvider auditingDateTimeProvider(Clock clock) {
        return () -> Optional.of(clock.instant());
    }
}
