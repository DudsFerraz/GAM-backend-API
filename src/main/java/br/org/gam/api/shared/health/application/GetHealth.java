package br.org.gam.api.shared.health.application;

import br.org.gam.api.health.application.HealthReadiness;
import org.springframework.stereotype.Service;

@Service
public class GetHealth {

    private final HealthReadiness healthReadiness;

    public GetHealth(HealthReadiness healthReadiness) {
        this.healthReadiness = healthReadiness;
    }

    public HealthRDTO get() {
        return healthReadiness.isReady() ? HealthRDTO.up() : HealthRDTO.down();
    }
}
