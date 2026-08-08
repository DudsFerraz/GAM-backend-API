package br.org.gam.api.shared.health.application;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Minimal public production readiness status.")
public record HealthRDTO(
        @Schema(
                description = "UP when the application and required database connectivity are ready; DOWN otherwise.",
                allowableValues = {"UP", "DOWN"},
                example = "UP",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        String status
) {
    public static HealthRDTO up() {
        return new HealthRDTO("UP");
    }

    public static HealthRDTO down() {
        return new HealthRDTO("DOWN");
    }

}
