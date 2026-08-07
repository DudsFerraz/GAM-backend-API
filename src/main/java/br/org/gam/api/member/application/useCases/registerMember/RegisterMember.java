package br.org.gam.api.member.application.useCases.registerMember;

import br.org.gam.api.account.application.AccountDomainLoader;
import br.org.gam.api.account.domain.Account;
import br.org.gam.api.member.application.MemberMapper;
import br.org.gam.api.member.domain.Member;
import br.org.gam.api.member.persistence.MemberEntity;
import br.org.gam.api.member.persistence.MemberRepository;
import br.org.gam.api.member.domain.MemberInformationText;
import br.org.gam.api.shared.domain.GamName;
import br.org.gam.api.shared.exception.ConflictException;
import br.org.gam.api.shared.exception.RequestValidationException;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;
import java.time.LocalDate;

@Service
public class RegisterMember {

    private final MemberRepository memberRepo;

    private final MemberMapper memberMapper;
    private final AccountDomainLoader getAccountInstance;

    public RegisterMember(MemberRepository memberRepo, MemberMapper memberMapper, AccountDomainLoader getAccountInstance) {
        this.memberRepo = memberRepo;
        this.memberMapper = memberMapper;
        this.getAccountInstance = getAccountInstance;
    }

    @Transactional
    public RegisterMemberRDTO register(RegisterMemberDTO dto) {
        GamName name = validatedName(dto.firstName(), dto.surname());
        validateEligibility(dto);

        if (memberRepo.existsByAccountId(dto.accountId())){
            throw ConflictException.resource("Account", dto.accountId(), "A member is already linked to this account.");
        }

        Account relatedAccount = getAccountInstance.requiredById(dto.accountId());

        Member newMember = Member.register(relatedAccount, name, dto.birthDate(), dto.phoneNumber(),
                validatedGamEntryDate(dto.gamEntryDate()), normalizeCity(dto.residentialCity()), dto.contactEmail());

        MemberEntity newMemberEntity = memberMapper.domainToEntity(newMember);
        MemberEntity savedMemberEntity = memberRepo.save(newMemberEntity);

        return memberMapper.entityToRegisterMemberRDTO(savedMemberEntity);
    }

    private LocalDate validatedGamEntryDate(LocalDate value) {
        if (value == null || value.isAfter(LocalDate.now())) {
            throw new RequestValidationException("body", "/gamEntryDate", "RANGE");
        }
        return value;
    }

    private String normalizeCity(String value) {
        if (value == null) throw new RequestValidationException("body", "/residentialCity", "REQUIRED");
        String normalized = MemberInformationText.collapsed(value);
        int size = normalized.codePointCount(0, normalized.length());
        if (size < 1 || size > 100) throw new RequestValidationException("body", "/residentialCity", "SIZE");
        return normalized;
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

    private void validateEligibility(RegisterMemberDTO dto) {
        try {
            Member.validateEligibility(dto.birthDate(), java.time.LocalDate.now());
        } catch (IllegalArgumentException exception) {
            throw new RequestValidationException("body", "/birthDate", "RANGE");
        }
    }
}
