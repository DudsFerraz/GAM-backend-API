package br.org.gam.api.member.solicitation.application.useCases;

import br.org.gam.api.account.application.AccountEntityLoader;
import br.org.gam.api.account.persistence.AccountEntity;
import br.org.gam.api.member.domain.Member;
import br.org.gam.api.member.persistence.MemberRepository;
import br.org.gam.api.member.solicitation.application.MembershipSolicitationMapper;
import br.org.gam.api.member.solicitation.application.MembershipSolicitationRDTO;
import br.org.gam.api.member.solicitation.domain.MembershipSolicitationStatus;
import br.org.gam.api.member.solicitation.persistence.MembershipSolicitationEntity;
import br.org.gam.api.member.solicitation.persistence.MembershipSolicitationRepository;
import br.org.gam.api.shared.activitylog.ActivityEvents;
import br.org.gam.api.shared.domain.GamName;
import br.org.gam.api.shared.exception.ConflictException;
import br.org.gam.api.shared.exception.RequestValidationException;
import br.org.gam.api.shared.persistence.UUIDGenerator;
import br.org.gam.api.shared.validation.RequiredReason;
import jakarta.transaction.Transactional;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.data.domain.AuditorAware;
import org.springframework.stereotype.Service;

@Service
public class SubmitMembershipSolicitation {
    private final MembershipSolicitationRepository solicitationRepo;
    private final MembershipSolicitationMapper mapper;
    private final MemberRepository memberRepo;
    private final AccountEntityLoader accountEntityLoader;
    private final AuditorAware<UUID> auditorAware;
    private final ActivityEvents activityEvents;

    public SubmitMembershipSolicitation(MembershipSolicitationRepository solicitationRepo,
                                        MembershipSolicitationMapper mapper, MemberRepository memberRepo,
                                        AccountEntityLoader accountEntityLoader, AuditorAware<UUID> auditorAware,
                                        ActivityEvents activityEvents) {
        this.solicitationRepo = solicitationRepo;
        this.mapper = mapper;
        this.memberRepo = memberRepo;
        this.accountEntityLoader = accountEntityLoader;
        this.auditorAware = auditorAware;
        this.activityEvents = activityEvents;
    }

    @Transactional
    public MembershipSolicitationRDTO submit(SubmitMembershipSolicitationDTO dto) {
        String justification = RequiredReason.normalize(
                dto.justification(), "Membership solicitation justification is required."
        );
        validateEligibility(dto.birthDate());
        if (dto.gamEntryDate().isAfter(LocalDate.now())) {
            throw new RequestValidationException("body", "/gamEntryDate", "RANGE");
        }
        GamName name = validatedName(dto.firstName(), dto.surname());

        UUID accountId = auditorAware.getCurrentAuditor()
                .orElseThrow(() -> new IllegalStateException("Authenticated Account is required."));
        AccountEntity account = accountEntityLoader.requiredByIdForUpdate(accountId);

        if (memberRepo.existsByAccountId(accountId)) {
            throw ConflictException.resource("Account", accountId, "This Account already has a lifetime Member.");
        }
        if (solicitationRepo.existsByAccount_IdAndStatus(accountId, MembershipSolicitationStatus.PENDING)) {
            throw ConflictException.resource(
                    "MembershipSolicitation", accountId,
                    "A pending membership solicitation already exists for this Account."
            );
        }

        MembershipSolicitationEntity solicitation = new MembershipSolicitationEntity();
        solicitation.setId(UUIDGenerator.generateUUIDV7());
        solicitation.setAccount(account);
        solicitation.setName(name);
        solicitation.setBirthDate(dto.birthDate());
        solicitation.setGamEntryDate(dto.gamEntryDate());
        String residentialCity = br.org.gam.api.member.domain.MemberInformationText.collapsed(dto.residentialCity());
        if (residentialCity.codePointCount(0, residentialCity.length()) > 100) {
            throw new br.org.gam.api.shared.exception.RequestValidationException(
                    "body", "/residentialCity", "SIZE");
        }
        solicitation.setResidentialCity(residentialCity);
        solicitation.setPhoneNumber(dto.phoneNumber());
        solicitation.setContactEmail(dto.contactEmail());
        solicitation.setJustification(justification);
        solicitation.setStatus(MembershipSolicitationStatus.PENDING);

        MembershipSolicitationEntity saved = solicitationRepo.saveAndFlush(solicitation);
        activityEvents.membershipSolicitationSubmitted(saved.getId(), accountId);
        return mapper.entityToRDTO(saved);
    }

    private void validateEligibility(LocalDate birthDate) {
        try {
            Member.validateEligibility(birthDate, LocalDate.now());
        } catch (IllegalArgumentException exception) {
            throw new RequestValidationException("body", "/birthDate", "RANGE");
        }
    }

    private GamName validatedName(String firstName, String surname) {
        try {
            return new GamName(firstName, surname);
        } catch (IllegalArgumentException exception) {
            String field = exception.getMessage() != null && exception.getMessage().startsWith("surname")
                    ? "/surname"
                    : "/firstName";
            String code = exception.getMessage() != null
                    && (exception.getMessage().contains("exceed")
                    || exception.getMessage().contains("at least"))
                    ? "SIZE"
                    : "FORMAT";
            throw new RequestValidationException("body", field, code);
        }
    }
}
