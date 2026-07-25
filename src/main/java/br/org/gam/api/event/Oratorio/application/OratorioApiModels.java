package br.org.gam.api.event.oratorio.application;

import br.org.gam.api.event.application.EventRDTO;
import br.org.gam.api.member.domain.MemberStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class OratorioApiModels {
    private OratorioApiModels() {
    }

    public record CreateOratorioDTO(
            @NotNull
            @Schema(description = "Oratorio occurrence date in America/Sao_Paulo", format = "date")
            LocalDate date
    ) {
    }

    public record PlanningDTO(
            @Size(max = 10_000) String lancheDescription,
            @Size(max = 10_000) String gincanaDescription,
            @Size(max = 10_000) String boaTardeCriancasPlan,
            @Size(max = 10_000) String boaTardeJovensPlan
    ) {
    }

    public record ReasonDTO(String reason) {
    }

    public record ReopenDTO(
            @NotNull br.org.gam.api.event.domain.EventStatus targetStatus,
            String reason
    ) {
    }

    public enum TeamType {
        LANCHE,
        GINCANA,
        BOA_TARDE_CRIANCAS,
        BOA_TARDE_JOVENS
    }

    public record ScheduleItemRDTO(
            String startTime,
            String endTime,
            String activity,
            boolean closingBoundary
    ) {
    }

    public record TeamMemberRDTO(
            UUID id,
            String firstName,
            String surname,
            MemberStatus status
    ) {
    }

    public record TeamRDTO(
            TeamType type,
            List<TeamMemberRDTO> members
    ) {
    }

    public record OratorioRDTO(
            UUID id,
            EventRDTO event,
            List<ScheduleItemRDTO> schedule,
            PlanningDTO planning,
            List<TeamRDTO> teams
    ) {
    }

    public record AttendancePersonRDTO(
            UUID id,
            String firstName,
            String surname,
            String status,
            boolean deleted
    ) {
    }

    public record AttendanceRDTO(
            UUID id,
            AttendancePersonRDTO person,
            Instant registeredAt
    ) {
    }

    public record RosterEntryRDTO(
            AttendancePersonRDTO person,
            AttendanceRDTO attendance
    ) {
    }

    public record PresentSummaryRDTO(
            List<AttendanceRDTO> members,
            List<AttendanceRDTO> oratorianos
    ) {
    }

    public record QuickRegistrationRDTO(
            br.org.gam.api.oratoriano.application.OratorianoApiModels.OratorianoRDTO oratoriano,
            AttendanceRDTO attendance
    ) {
    }
}
