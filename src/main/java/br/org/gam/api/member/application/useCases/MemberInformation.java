package br.org.gam.api.member.application.useCases;

import br.org.gam.api.member.application.MemberInformationRDTO;
import br.org.gam.api.member.application.MemberMapper;
import br.org.gam.api.account.application.AccountMapper;
import br.org.gam.api.member.application.MemberPreconditionException;
import br.org.gam.api.member.domain.DietaryRestriction;
import br.org.gam.api.member.domain.InformationStatus;
import br.org.gam.api.member.domain.MemberContributionArea;
import br.org.gam.api.member.domain.MemberExperienceType;
import br.org.gam.api.member.domain.MemberSacramentType;
import br.org.gam.api.member.domain.Member;
import br.org.gam.api.member.domain.MemberInformationText;
import br.org.gam.api.member.persistence.MemberEntity;
import br.org.gam.api.member.persistence.MemberRepository;
import br.org.gam.api.shared.activitylog.ActivityAction;
import br.org.gam.api.shared.activitylog.ActivityEvents;
import br.org.gam.api.shared.activitylog.ActivityReasonNormalizer;
import br.org.gam.api.shared.activitylog.ActivityTargetType;
import br.org.gam.api.shared.domain.GamName;
import br.org.gam.api.shared.exception.NotFoundException;
import br.org.gam.api.shared.exception.RequestValidationException;
import jakarta.transaction.Transactional;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class MemberInformation {
    private static final Set<String> FIXED_CONTRIBUTION_LABELS = Set.of(
            "Apitar jogo", "Arbitragem de jogos", "Artesanato", "Cantar ou tocar instrumento",
            "Cantar/tocar instrumento", "Música", "Conduzir momentos de oração", "Condução de oração",
            "Contar histórias no Boa Tarde", "Contar histórias (boa tarde)", "Dança", "Escultura de bexiga",
            "Escultura de balões", "Futebol", "Vôlei", "Voleibol", "Basquete", "Basketball", "Handebol",
            "Handball", "Fotografia e vídeo", "Fotografia/vídeos (marketing)", "Leitura em público",
            "Pintura facial", "Primeiros socorros", "Puxar gincana", "Condução de gincanas",
            "Repertórios de gincanas", "Tecnologia", "Tererê", "Terere");
    private final MemberRepository members;
    private final ActivityEvents activities;
    private final MemberMapper memberMapper;

    public MemberInformation(MemberRepository members, ActivityEvents activities) {
        this(members, activities, standaloneMapper());
    }

    @Autowired
    public MemberInformation(MemberRepository members, ActivityEvents activities, MemberMapper memberMapper) {
        this.members = members;
        this.activities = activities;
        this.memberMapper = memberMapper;
    }

    private static MemberMapper standaloneMapper() {
        try {
            Class<?> implementation = Class.forName("br.org.gam.api.member.application.MemberMapperImpl");
            AccountMapper accountMapper = (AccountMapper) java.lang.reflect.Proxy.newProxyInstance(
                    AccountMapper.class.getClassLoader(), new Class<?>[]{AccountMapper.class},
                    (proxy, method, arguments) -> null);
            return (MemberMapper) implementation.getConstructor(AccountMapper.class).newInstance(accountMapper);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Member mapper implementation is unavailable.", exception);
        }
    }

    @Transactional
    public MemberInformationRDTO.ExperiencesAndSacraments experiencesAndSacraments(UUID memberId) {
        MemberEntity member = required(memberId);
        EnumMap<MemberExperienceType, InformationStatus> experiences = defaults(MemberExperienceType.class);
        member.getExperiences().forEach(value -> experiences.put(value.getType(), value.getStatus()));
        EnumMap<MemberSacramentType, InformationStatus> sacraments = defaults(MemberSacramentType.class);
        member.getSacraments().forEach(value -> sacraments.put(value.getType(), value.getStatus()));
        return new MemberInformationRDTO.ExperiencesAndSacraments(experiences, sacraments);
    }

    @Transactional
    public Versioned<MemberInformationRDTO.ExperiencesAndSacraments> versionedExperiencesAndSacraments(UUID memberId) {
        MemberEntity entity = required(memberId);
        return new Versioned<>(experiencesAndSacraments(entity), entity.getVersion());
    }

    @Transactional
    public MemberInformationRDTO.ContributionProfileResponse contributionProfile(UUID memberId) {
        MemberEntity member = required(memberId);
        List<MemberContributionArea> fixed = Arrays.stream(MemberContributionArea.values())
                .filter(member.getContributionAreas()::contains).toList();
        List<String> custom = member.getOtherContributionAreas().stream()
                .sorted(String.CASE_INSENSITIVE_ORDER.thenComparing(Comparator.naturalOrder())).toList();
        return new MemberInformationRDTO.ContributionProfileResponse(
                new MemberInformationRDTO.ContributionProfileRead(fixed, custom));
    }

    @Transactional
    public Versioned<MemberInformationRDTO.ContributionProfileResponse> versionedContributionProfile(UUID memberId) {
        MemberEntity entity = required(memberId);
        return new Versioned<>(contributionProfile(entity), entity.getVersion());
    }

    @Transactional
    public String updateCore(UUID id, String ifMatch, MemberInformationDTO.Core dto) {
        return update(id, ifMatch, dto.reason(), ActivityAction.MEMBER_PROFILE_UPDATED, member -> {
            GamName name;
            try {
                name = new GamName(dto.firstName(), dto.surname());
            } catch (IllegalArgumentException exception) {
                invalid(exception.getMessage() != null && exception.getMessage().startsWith("surname")
                        ? "/surname" : "/firstName", "FORMAT");
                return;
            }
            String city = normalizeCity(dto.residentialCity());
            try {
                member.replaceCoreProfile(name, dto.birthDate(), city, dto.phoneNumber(), dto.contactEmail(), LocalDate.now());
            } catch (IllegalArgumentException exception) {
                invalid("/birthDate", "RANGE");
            }
        });
    }

    @Transactional
    public String updateGamEntryDate(UUID id, String ifMatch, MemberInformationDTO.GamEntryDate dto) {
        if (dto.gamEntryDate().isAfter(LocalDate.now())) invalid("/gamEntryDate", "RANGE");
        return update(id, ifMatch, dto.reason(), ActivityAction.MEMBER_GAM_ENTRY_DATE_UPDATED,
                member -> member.changeGamEntryDate(dto.gamEntryDate(), LocalDate.now()));
    }

    @Transactional
    public String updateDietaryRestriction(UUID id, String ifMatch, MemberInformationDTO.DietaryRestriction dto) {
        DietaryRestriction value;
        try {
            value = new DietaryRestriction(dto.status(), dto.details());
        } catch (IllegalArgumentException exception) {
            invalid("/details", "RELATION");
            return null;
        }
        return update(id, ifMatch, dto.reason(), ActivityAction.MEMBER_DIETARY_RESTRICTION_UPDATED,
                member -> member.replaceDietaryRestriction(value));
    }

    @Transactional
    public String updateExperiences(UUID id, String ifMatch, MemberInformationDTO.Experiences dto) {
        requireExactKeys(dto.experiences(), MemberExperienceType.values(), "/experiences");
        return update(id, ifMatch, dto.reason(), ActivityAction.MEMBER_EXPERIENCES_UPDATED,
                member -> member.replaceExperiences(dto.experiences()));
    }

    @Transactional
    public String updateSacraments(UUID id, String ifMatch, MemberInformationDTO.Sacraments dto) {
        requireExactKeys(dto.sacraments(), MemberSacramentType.values(), "/sacraments");
        return update(id, ifMatch, dto.reason(), ActivityAction.MEMBER_SACRAMENTS_UPDATED,
                member -> member.replaceSacraments(dto.sacraments()));
    }

    @Transactional
    public String updateContributionProfile(UUID id, String ifMatch, MemberInformationDTO.ContributionProfile dto) {
        Set<MemberContributionArea> fixed = new LinkedHashSet<>(dto.contributionAreas());
        if (fixed.size() != dto.contributionAreas().size()) invalid("/contributionAreas", "DUPLICATE");
        Set<String> custom = normalizeCustom(dto.otherContributionAreas());
        return update(id, ifMatch, dto.reason(), ActivityAction.MEMBER_CONTRIBUTION_PROFILE_UPDATED,
                member -> member.replaceContributionProfile(fixed, custom));
    }

    public String etag(long version) { return "\"member-" + version + "\""; }

    @Transactional
    public String etag(UUID memberId) { return etag(required(memberId).getVersion()); }

    private String update(UUID id, String suppliedEtag, String suppliedReason, ActivityAction action,
                          Consumer<Member> mutation) {
        String reason = ActivityReasonNormalizer.normalizeRequired(suppliedReason);
        MemberEntity member = members.findByIdForUpdate(id)
                .orElseThrow(() -> NotFoundException.resource("Member", id));
        requireCurrent(suppliedEtag, member.getVersion());
        Member aggregate = memberMapper.entityToDomain(member);
        MemberSnapshot before = MemberSnapshot.of(aggregate);
        mutation.accept(aggregate);
        MemberSnapshot after = MemberSnapshot.of(aggregate);
        List<String> changedFields = before.changedFields(after);
        if (changedFields.isEmpty()) return etag(member.getVersion());
        memberMapper.updateEntity(aggregate, member);
        members.saveAndFlush(member);
        activities.moduleActivity(action, ActivityTargetType.MEMBER, id, reason, null,
                Map.of("changedFields", changedFields));
        return etag(member.getVersion());
    }

    private MemberEntity required(UUID id) {
        return members.findById(id).orElseThrow(() -> NotFoundException.resource("Member", id));
    }

    private MemberInformationRDTO.ExperiencesAndSacraments experiencesAndSacraments(MemberEntity member) {
        EnumMap<MemberExperienceType, InformationStatus> experiences = defaults(MemberExperienceType.class);
        member.getExperiences().forEach(value -> experiences.put(value.getType(), value.getStatus()));
        EnumMap<MemberSacramentType, InformationStatus> sacraments = defaults(MemberSacramentType.class);
        member.getSacraments().forEach(value -> sacraments.put(value.getType(), value.getStatus()));
        return new MemberInformationRDTO.ExperiencesAndSacraments(experiences, sacraments);
    }

    private MemberInformationRDTO.ContributionProfileResponse contributionProfile(MemberEntity member) {
        List<MemberContributionArea> fixed = Arrays.stream(MemberContributionArea.values())
                .filter(member.getContributionAreas()::contains).toList();
        List<String> custom = member.getOtherContributionAreas().stream()
                .sorted(String.CASE_INSENSITIVE_ORDER.thenComparing(Comparator.naturalOrder())).toList();
        return new MemberInformationRDTO.ContributionProfileResponse(
                new MemberInformationRDTO.ContributionProfileRead(fixed, custom));
    }

    public record Versioned<T>(T body, long version) {}

    private void requireCurrent(String supplied, long version) {
        if (supplied == null) throw new MemberPreconditionException(MemberPreconditionException.Kind.REQUIRED);
        if (!supplied.matches("\\\"member-[0-9]+\\\"")) {
            throw new MemberPreconditionException(MemberPreconditionException.Kind.MALFORMED);
        }
        if (!supplied.equals(etag(version))) {
            throw new MemberPreconditionException(MemberPreconditionException.Kind.FAILED);
        }
    }

    private String normalizeCity(String value) {
        String normalized = value == null ? "" : MemberInformationText.collapsed(value);
        int count = normalized.codePointCount(0, normalized.length());
        if (count < 1 || count > 100) invalid("/residentialCity", "SIZE");
        return normalized;
    }

    private Set<String> normalizeCustom(List<String> values) {
        if (values.size() > 10) invalid("/otherContributionAreas", "SIZE");
        Set<String> result = new java.util.TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (String value : values) {
            String normalized = value == null ? "" : MemberInformationText.collapsed(value);
            int count = normalized.codePointCount(0, normalized.length());
            if (count < 1 || count > 100 || FIXED_CONTRIBUTION_LABELS.stream().anyMatch(normalized::equalsIgnoreCase)
                    || !result.add(normalized)) invalid("/otherContributionAreas", "DUPLICATE");
        }
        return new LinkedHashSet<>(result);
    }

    private <E extends Enum<E>> void requireExactKeys(Map<E, InformationStatus> values, E[] expected, String field) {
        if (values == null || values.size() != expected.length || !values.keySet().equals(Set.of(expected))
                || values.containsValue(null)) invalid(field, "ALLOWED_VALUE");
    }

    private <E extends Enum<E>> EnumMap<E, InformationStatus> defaults(Class<E> type) {
        EnumMap<E, InformationStatus> result = new EnumMap<>(type);
        Arrays.stream(type.getEnumConstants()).forEach(value -> result.put(value, InformationStatus.NOT_INFORMED));
        return result;
    }

    private void invalid(String field, String code) { throw new RequestValidationException("body", field, code); }

    private record MemberSnapshot(Object name, LocalDate birthDate, LocalDate gamEntryDate, String city, Object phone,
                                  Object email, InformationStatus dietaryStatus, String dietaryDetails,
                                  Set<String> experiences, Set<String> sacraments,
                                  Set<MemberContributionArea> fixed, Set<String> custom) {
        static MemberSnapshot of(Member m) {
            return new MemberSnapshot(m.getName(), m.getBirthDate(), m.getGamEntryDate(), m.getResidentialCity(),
                    m.getPhoneNumber(), m.getContactEmail(), m.getDietaryRestriction().status(),
                    m.getDietaryRestriction().details(), m.getExperiences().entrySet().stream()
                            .map(value -> value.getKey() + "=" + value.getValue()).collect(java.util.stream.Collectors.toSet()),
                    m.getSacraments().entrySet().stream().map(value -> value.getKey() + "=" + value.getValue())
                            .collect(java.util.stream.Collectors.toSet()),
                    Set.copyOf(m.getContributionAreas()), Set.copyOf(m.getOtherContributionAreas()));
        }
        List<String> changedFields(MemberSnapshot other) {
            List<String> fields = new ArrayList<>();
            GamName beforeName = (GamName) name;
            GamName afterName = (GamName) other.name;
            if (!java.util.Objects.equals(beforeName.firstName(), afterName.firstName())) fields.add("firstName");
            if (!java.util.Objects.equals(beforeName.surname(), afterName.surname())) fields.add("surname");
            if (!java.util.Objects.equals(birthDate, other.birthDate)) fields.add("birthDate");
            if (!java.util.Objects.equals(gamEntryDate, other.gamEntryDate)) fields.add("gamEntryDate");
            if (!java.util.Objects.equals(city, other.city)) fields.add("residentialCity");
            if (!java.util.Objects.equals(phone, other.phone)) fields.add("phoneNumber");
            if (!java.util.Objects.equals(email, other.email)) fields.add("contactEmail");
            if (!java.util.Objects.equals(dietaryStatus, other.dietaryStatus)) fields.add("dietaryRestriction.status");
            if (!java.util.Objects.equals(dietaryDetails, other.dietaryDetails)) fields.add("dietaryRestriction.details");
            if (!java.util.Objects.equals(experiences, other.experiences)) fields.add("experiences");
            if (!java.util.Objects.equals(sacraments, other.sacraments)) fields.add("sacraments");
            if (!java.util.Objects.equals(fixed, other.fixed)) fields.add("contributionAreas");
            if (!java.util.Objects.equals(custom, other.custom)) fields.add("otherContributionAreas");
            return fields;
        }
    }
}
