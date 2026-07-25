package br.org.gam.api.oratoriano.application;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
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

    public record AccountReferenceRDTO(UUID id) {
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
            String firstName,
            String surname,
            LocalDate birthDate,
            String cpf,
            String rg,
            AddressDTO address,
            String phoneNumber,
            String schoolName,
            String schoolGrade,
            ResponsibleDTO responsible,
            ParentDTO father,
            ParentDTO mother,
            HealthDTO health,
            DeclarationsDTO declarations,
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
            Map<String, Object> data,
            LocalDate signedOn,
            AccountReferenceRDTO createdBy,
            Instant createdAt
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

    public record AttachmentRDTO(
            UUID id,
            String originalFilename,
            String verifiedMimeType,
            long byteLength,
            int pageOrder
    ) {
    }
}
