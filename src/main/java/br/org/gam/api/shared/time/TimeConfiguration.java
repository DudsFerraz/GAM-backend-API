package br.org.gam.api.shared.time;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class TimeConfiguration {

    private static final ZoneId APPLICATION_ZONE = ZoneId.of("America/Sao_Paulo");

    @Bean
    Clock applicationClock() {
        return Clock.system(APPLICATION_ZONE);
    }
}
