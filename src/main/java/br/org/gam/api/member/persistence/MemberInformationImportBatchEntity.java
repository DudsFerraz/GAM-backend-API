package br.org.gam.api.member.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@Entity
@Table(name = "member_information_import_batches")
public class MemberInformationImportBatchEntity {
    @Id private UUID id;
    @Column(name = "survey_cycle", nullable = false) private int surveyCycle;
    @Column(name = "dataset_checksum", nullable = false, length = 71) private String datasetChecksum;
    @Column(name = "imported_member_count", nullable = false) private int importedMemberCount;
    @Column(name = "imported_response_count", nullable = false) private int importedResponseCount;
    @Column(name = "executed_at", nullable = false) private Instant executedAt;
    @Column(name = "reason", nullable = false, length = 2000) private String reason;

    public MemberInformationImportBatchEntity(UUID id, int surveyCycle, String datasetChecksum,
                                               int importedMemberCount, int importedResponseCount,
                                               Instant executedAt, String reason) {
        this.id = id;
        this.surveyCycle = surveyCycle;
        this.datasetChecksum = datasetChecksum;
        this.importedMemberCount = importedMemberCount;
        this.importedResponseCount = importedResponseCount;
        this.executedAt = executedAt;
        this.reason = reason;
    }
}
