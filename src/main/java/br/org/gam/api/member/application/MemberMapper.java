package br.org.gam.api.member.application;

import br.org.gam.api.account.application.AccountMapper;
import br.org.gam.api.member.application.useCases.RegisterMember.RegisterMemberRDTO;
import br.org.gam.api.member.domain.Member;
import br.org.gam.api.member.persistence.MemberEntity;
import br.org.gam.api.shared.auditing.IgnoreFullAuditFields;
import br.org.gam.api.shared.domain.Name;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

@Mapper(componentModel = "spring", uses = {AccountMapper.class})
public interface MemberMapper {

    // =====================================================================================
    // Domain <-> Persistence
    // =====================================================================================

    @IgnoreFullAuditFields
    MemberEntity domainToEntity(Member memberDomain);

    Member entityToDomain(MemberEntity memberEntity);

    // =====================================================================================
    // Persistence -> RDTO
    // =====================================================================================

    RegisterMemberRDTO entityToRegisterMemberRDTO(MemberEntity memberEntity);

    @Mapping(target = "name", source = "memberEntity.name", qualifiedByName = "nameToString")
    @Mapping(target = "id", source = "memberEntity.id")
    MemberRDTO entityToRDTO(MemberEntity memberEntity);

    // =====================================================================================
    // Helpers
    // =====================================================================================

    @Named("nameToString")
    default String nameToString(Name name) {
        if (name == null) return null;
        return name.toString();
    }
}
