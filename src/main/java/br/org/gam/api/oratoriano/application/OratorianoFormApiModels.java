package br.org.gam.api.oratoriano.application;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class OratorianoFormApiModels {
    private OratorianoFormApiModels() {
    }

    public enum FormOrigin {
        PAPER_TRANSCRIPTION,
        DIRECT_SYSTEM_ENTRY
    }

    public enum FormStatus {
        DRAFT,
        COMPLETED,
        SUPERSEDED,
        REVOKED
    }

    public enum PrintMode {
        IDENTIFIED_BLANK,
        PREFILLED
    }

    public enum ResponsibleRelationship {
        SELF,
        MOTHER,
        FATHER,
        RELATIVE,
        REFERENCE_ADULT
    }

    public enum HealthAnswer {
        YES,
        NO,
        NOT_INFORMED
    }

    public record CreateFormDTO(FormOrigin origin) {
    }

    public record CompleteFormDTO(
            @NotNull
            @Schema(
                    requiredMode = Schema.RequiredMode.REQUIRED,
                    format = "uuid",
                    example = "019f6343-321a-7c90-a096-a551e8f88eb4"
            )
            UUID printSnapshotId,
            @Schema(example = "true")
            Boolean overwriteNewerProfileValues
    ) {
    }

    public record AccountReferenceRDTO(
            @Schema(format = "uuid", requiredMode = Schema.RequiredMode.REQUIRED)
            UUID id,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String displayName
    ) {
    }

    public record AddressDTO(
            String addressLine,
            String addressNumber,
            String neighborhood,
            String cep,
            String city
    ) {
    }

    public record ParentDTO(
            String firstName,
            String surname,
            String cpf
    ) {
    }

    public record ResponsibleDTO(
            ResponsibleRelationship relationship,
            String relationshipComplement,
            String firstName,
            String surname,
            String cpf,
            String phoneNumber,
            String email,
            Boolean atLeast18
    ) {
    }

    public record HealthQuestionDTO(
            HealthAnswer answer,
            String explanation,
            String importantInstructions
    ) {
    }

    public record HealthDTO(
            HealthQuestionDTO medicalFollowUp,
            HealthQuestionDTO physicalActivityRestriction,
            HealthQuestionDTO medicineUse,
            HealthQuestionDTO allergies,
            HealthQuestionDTO convulsions,
            HealthQuestionDTO frequentFainting,
            HealthQuestionDTO heartCondition,
            HealthQuestionDTO otherHealthCondition,
            String otherCare
    ) {
    }

    public record DeclarationsDTO(
            Boolean signerRelationshipConfirmed,
            Boolean informationTruthConfirmed,
            Boolean healthInformationCurrentConfirmed,
            Boolean informationUseUnderstood,
            Boolean formReviewed,
            Boolean imageAndVoiceAuthorizationAccepted
    ) {
    }

    @Schema(description = "Editable structured transcription for one additional-form draft")
    public record FormDraftDTO(
            @Schema(types = {"string", "null"})
            String firstName,
            @Schema(types = {"string", "null"})
            String surname,
            @Schema(types = {"string", "null"}, format = "date")
            LocalDate birthDate,
            @Schema(types = {"string", "null"})
            String cpf,
            @Schema(types = {"string", "null"})
            String rg,
            @Schema(types = {"object", "null"})
            AddressDTO address,
            @Schema(types = {"string", "null"})
            String phoneNumber,
            @Schema(types = {"string", "null"})
            String schoolName,
            @Schema(types = {"string", "null"})
            String schoolGrade,
            @Schema(types = {"object", "null"})
            ResponsibleDTO responsible,
            @Schema(types = {"object", "null"})
            ParentDTO father,
            @Schema(types = {"object", "null"})
            ParentDTO mother,
            @Schema(types = {"object", "null"})
            HealthDTO health,
            @Schema(types = {"object", "null"})
            DeclarationsDTO declarations,
            @Schema(types = {"string", "null"}, format = "date")
            LocalDate signedOn
    ) {
    }

    public record FormRDTO(
            UUID id,
            UUID oratorianoId,
            int version,
            FormStatus status,
            FormOrigin origin,
            long draftRevision,
            FormDraftDTO data,
            LocalDate signedOn,
            AccountReferenceRDTO createdBy,
            Instant createdAt,
            Instant completedAt,
            AccountReferenceRDTO completedBy,
            Instant revokedAt,
            AccountReferenceRDTO revokedBy
    ) {
    }

    public record FormHistoryRDTO(
            UUID id,
            int version,
            FormStatus status,
            FormOrigin origin,
            LocalDate signedOn,
            Instant createdAt,
            AccountReferenceRDTO createdBy,
            Instant completedAt,
            AccountReferenceRDTO completedBy,
            Instant revokedAt,
            AccountReferenceRDTO revokedBy,
            boolean attachmentExists,
            int attachmentPageCount
    ) {
    }

    public record PrintSnapshotRDTO(
            UUID id,
            UUID formId,
            long draftRevision,
            PrintMode mode,
            Instant generatedAt,
            String templateVersion,
            int pageCount,
            String fingerprint
    ) {
    }

    public record PrintSnapshotMetadataRDTO(
            @Schema(format = "uuid", requiredMode = Schema.RequiredMode.REQUIRED)
            UUID id,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            long draftRevision,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            PrintMode mode,
            @Schema(format = "date-time", requiredMode = Schema.RequiredMode.REQUIRED)
            Instant generatedAt,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String templateVersion,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            int pageCount
    ) {
    }

    public record AttachmentRDTO(
            @Schema(format = "uuid", requiredMode = Schema.RequiredMode.REQUIRED)
            UUID id,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String originalFilename,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String verifiedMimeType,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            long byteLength,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            int pageOrder,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            int pageCount
    ) {
    }
}
