package br.org.gam.api.member.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnnualMemberInformationResponseRepository
        extends JpaRepository<AnnualMemberInformationResponseEntity, UUID> {
    @EntityGraph(attributePaths = {"member", "occupations"})
    Optional<AnnualMemberInformationResponseEntity> findByMemberIdAndSurveyCycle(UUID memberId, int surveyCycle);
    boolean existsByMemberIdAndSurveyCycle(UUID memberId, int surveyCycle);
}
