package br.org.gam.api.oratoriano.application;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class OratorianoApiModels {
    private OratorianoApiModels() {
    }

    public record RegisterOratorianoDTO(
            @NotBlank @Schema(maxLength = 32) String firstName,
            @NotBlank @Schema(maxLength = 64) String surname
    ) {
    }

    public record ReplaceOratorianoDTO(
            @NotBlank @Schema(maxLength = 32) String firstName,
            @NotBlank @Schema(maxLength = 64) String surname,
            LocalDate birthDate,
            String phoneNumber,
            String reason
    ) {
    }

    public record ReasonDTO(String reason) {
    }

    public record OratorianoRDTO(
            UUID id,
            String firstName,
            String surname,
            LocalDate birthDate,
            String phoneNumber
    ) {
    }

    public record AttendanceSummaryRDTO(
            long oratorioAttendances,
            long oratorioDistinctMonthsAttendances,
            long oratorioDistinctYearsAttendances,
            long oratorioYearAttendances,
            long oratorioYearDistinctMonthsAttendances,
            long oratorioMonthAttendances
    ) {
    }

    public record AttendanceHistoryItemRDTO(
            UUID oratorioId,
            LocalDate localDate,
            String status
    ) {
    }
}
