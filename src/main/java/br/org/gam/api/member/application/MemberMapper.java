package br.org.gam.api.member.application;

import br.org.gam.api.account.application.AccountMapper;
import br.org.gam.api.member.application.useCases.registerMember.RegisterMemberRDTO;
import br.org.gam.api.member.domain.Member;
import br.org.gam.api.member.domain.InformationStatus;
import br.org.gam.api.member.domain.MemberExperienceType;
import br.org.gam.api.member.domain.MemberSacramentType;
import br.org.gam.api.member.persistence.MemberEntity;
import br.org.gam.api.member.persistence.MemberExperienceValue;
import br.org.gam.api.member.persistence.MemberSacramentValue;
import br.org.gam.api.account.persistence.AccountEntity;
import br.org.gam.api.shared.auditing.IgnoreFullAuditFields;
import br.org.gam.api.shared.domain.GamName;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.MappingTarget;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Mapper(componentModel = "spring", uses = {AccountMapper.class})
public interface MemberMapper {

    // =====================================================================================
    // Domain <-> Persistence
    // =====================================================================================

    @IgnoreFullAuditFields
    @Mapping(target = "dietaryRestrictionStatus", source = "dietaryRestriction.status")
    @Mapping(target = "dietaryRestrictionDetails", source = "dietaryRestriction.details")
    @Mapping(target = "importBatchId", ignore = true)
    MemberEntity domainToEntity(Member memberDomain);

    @Mapping(target = "dietaryRestriction", expression = "java(new br.org.gam.api.member.domain.DietaryRestriction(memberEntity.getDietaryRestrictionStatus(), memberEntity.getDietaryRestrictionDetails()))")
    Member entityToDomain(MemberEntity memberEntity);

    @IgnoreFullAuditFields
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "account", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "importBatchId", ignore = true)
    @Mapping(target = "dietaryRestrictionStatus", source = "dietaryRestriction.status")
    @Mapping(target = "dietaryRestrictionDetails", source = "dietaryRestriction.details")
    void updateEntity(Member member, @MappingTarget MemberEntity entity);

    default MemberEntity importedDomainToEntity(Member member, UUID importBatchId) {
        MemberEntity entity = domainToEntity(member);
        entity.setImportBatchId(importBatchId);
        return entity;
    }

    static MemberEntity attachLinkedAccount(MemberEntity entity, AccountEntity account) {
        entity.setAccount(account);
        return entity;
    }

    // =====================================================================================
    // Persistence -> RDTO
    // =====================================================================================

    RegisterMemberRDTO entityToRegisterMemberRDTO(MemberEntity memberEntity);

    @Mapping(target = "firstName", source = "memberEntity.name.firstName")
    @Mapping(target = "surname", source = "memberEntity.name.surname")
    @Mapping(target = "id", source = "memberEntity.id")
    @Mapping(target = "dietaryRestriction", expression = "java(new MemberRDTO.DietaryRestrictionRDTO(memberEntity.getDietaryRestrictionStatus(), memberEntity.getDietaryRestrictionDetails()))")
    MemberRDTO entityToRDTO(MemberEntity memberEntity);

    // =====================================================================================
    // Helpers
    // =====================================================================================

    @Named("nameToString")
    default String nameToString(GamName name) {
        if (name == null) return null;
        return name.toString();
    }

    default Map<MemberExperienceType, InformationStatus> mapExperiences(Set<MemberExperienceValue> values) {
        EnumMap<MemberExperienceType, InformationStatus> result = new EnumMap<>(MemberExperienceType.class);
        if (values != null) values.forEach(value -> result.put(value.getType(), value.getStatus()));
        return result;
    }

    default Set<MemberExperienceValue> mapExperiences(Map<MemberExperienceType, InformationStatus> values) {
        LinkedHashSet<MemberExperienceValue> result = new LinkedHashSet<>();
        if (values != null) values.forEach((type, status) -> result.add(new MemberExperienceValue(type, status)));
        return result;
    }

    default Map<MemberSacramentType, InformationStatus> mapSacraments(Set<MemberSacramentValue> values) {
        EnumMap<MemberSacramentType, InformationStatus> result = new EnumMap<>(MemberSacramentType.class);
        if (values != null) values.forEach(value -> result.put(value.getType(), value.getStatus()));
        return result;
    }

    default Set<MemberSacramentValue> mapSacraments(Map<MemberSacramentType, InformationStatus> values) {
        LinkedHashSet<MemberSacramentValue> result = new LinkedHashSet<>();
        if (values != null) values.forEach((type, status) -> result.add(new MemberSacramentValue(type, status)));
        return result;
    }
}
