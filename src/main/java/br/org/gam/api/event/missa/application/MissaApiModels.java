package br.org.gam.api.event.missa.application;

import br.org.gam.api.event.application.EventRDTO;
import br.org.gam.api.event.domain.EventStatus;
import br.org.gam.api.event.missa.domain.MissaResponsibility;
import br.org.gam.api.member.domain.MemberStatus;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.lang.Nullable;

public final class MissaApiModels {
    private MissaApiModels() {
    }

    @JsonIgnoreProperties(ignoreUnknown = false)
    public record CreateMissaDTO(
            @NotNull @NotBlank @Schema(minLength = 1, maxLength = 255) String title,
            @Nullable @Schema(nullable = true, maxLength = 10_000) String description,
            @NotNull UUID gamLocationId,
            @Nullable @Schema(nullable = true) UUID requiredPermissionId,
            @NotNull Instant beginDate,
            @NotNull Instant endDate
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = false)
    public record ReplaceMissaDTO(
            @NotNull @NotBlank @Schema(minLength = 1, maxLength = 255) String title,
            @Nullable @Schema(nullable = true, maxLength = 10_000) String description,
            @NotNull UUID gamLocationId,
            @Nullable @Schema(nullable = true) UUID requiredPermissionId,
            @NotNull Instant beginDate,
            @NotNull Instant endDate,
            @Nullable
            @Schema(
                    minLength = 1,
                    description = "When supplied, leading and trailing Unicode White_Space code points are removed "
                            + "before validation; the normalized reason must contain from 1 through 2,000 Unicode "
                            + "code points."
            )
            String reason
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = false)
    public record AssignmentDTO(
            @Nullable
            @Schema(
                    minLength = 1,
                    description = "When supplied, leading and trailing Unicode White_Space code points are removed "
                            + "before validation; the normalized reason must contain from 1 through 2,000 Unicode "
                            + "code points."
            )
            String reason
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = false)
    public record ReasonDTO(
            @NotNull
            @Schema(
                    minLength = 1,
                    description = "Required for cancellation, deletion, restoration, and revocation operations. "
                            + "Leading and trailing Unicode White_Space code points are removed before validation; "
                            + "the normalized reason must contain from 1 through 2,000 Unicode code points."
            )
            String reason
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = false)
    public record ReopenDTO(
            @NotNull ReopenTargetStatus targetStatus,
            @NotNull
            @Schema(
                    minLength = 1,
                    description = "Required for reopening. Leading and trailing Unicode White_Space code points "
                            + "are removed before validation; the normalized reason must contain from 1 through "
                            + "2,000 Unicode code points."
            )
            String reason
    ) {
    }

    public enum ReopenTargetStatus {
        COMPLETED,
        LOCKED
    }

    public record AssignedMemberRDTO(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String firstName,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String surname,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) MemberStatus status
    ) {
    }

    public record ResponsibilityRDTO(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) MissaResponsibility responsibility,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<AssignedMemberRDTO> members
    ) {
    }

    public record MissaRDTO(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) EventRDTO event,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<ResponsibilityRDTO> assignments
    ) {
    }
}
