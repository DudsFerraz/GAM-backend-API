package br.org.gam.api.account.application;

import br.org.gam.api.shared.domain.GamEmail;
import java.util.UUID;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(additionalProperties = Schema.AdditionalPropertiesValue.FALSE,
        requiredProperties = {"id", "email", "displayName"})
public record AccountSummaryRDTO(
        UUID id,
        GamEmail email,
        String displayName
) {
}
