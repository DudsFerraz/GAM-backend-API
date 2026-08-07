package br.org.gam.api.member.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberInformationImportBatchRepository
        extends JpaRepository<MemberInformationImportBatchEntity, UUID> {
}
