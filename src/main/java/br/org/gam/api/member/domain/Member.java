package br.org.gam.api.member.domain;

import br.org.gam.api.account.domain.Account;
import br.org.gam.api.shared.domain.GamName;
import br.org.gam.api.shared.domain.GamEmail;
import br.org.gam.api.shared.persistence.UUIDGenerator;
import br.org.gam.api.shared.phonenumber.GamPhoneNumber;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.time.LocalDate;
import java.time.Period;
import java.util.Objects;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class Member {
    private UUID id;
    private Account account;
    private GamName name;
    private LocalDate birthDate;
    private GamPhoneNumber phoneNumber;
    private LocalDate gamEntryDate;
    private String residentialCity;
    private GamEmail contactEmail;
    private DietaryRestriction dietaryRestriction;
    private Map<MemberExperienceType, InformationStatus> experiences;
    private Map<MemberSacramentType, InformationStatus> sacraments;
    private Set<MemberContributionArea> contributionAreas;
    private Set<String> otherContributionAreas;
    private MemberStatus status;
    private long version;

    /**
     * @deprecated <b>ESTE CONSTRUTOR É EXCLUSIVO PARA USO INTERNO (JPA/MapStruct).</b>
     * <br> <br>
     * <b> Use o método fábrica {@link #register(Account account, GamName name, LocalDate birthDate, GamPhoneNumber phoneNumber)}.
     */
    @Deprecated
    public Member(UUID id, Account account, GamName name, LocalDate birthDate, GamPhoneNumber phoneNumber, MemberStatus status) {
        this(id, account, name, birthDate, phoneNumber, status, 0L);
    }

    @Deprecated
    public Member(UUID id, Account account, GamName name, LocalDate birthDate, GamPhoneNumber phoneNumber,
                  MemberStatus status, long version) {
        this(id, account, name, birthDate, phoneNumber, status, version, null, null, null,
                DietaryRestriction.notInformed(), Map.of(), Map.of(), Set.of(), Set.of());
    }

    @Deprecated
    @Default
    public Member(UUID id, Account account, GamName name, LocalDate birthDate, GamPhoneNumber phoneNumber,
                  MemberStatus status, long version, LocalDate gamEntryDate, String residentialCity,
                  GamEmail contactEmail, DietaryRestriction dietaryRestriction,
                  Map<MemberExperienceType, InformationStatus> experiences,
                  Map<MemberSacramentType, InformationStatus> sacraments,
                  Set<MemberContributionArea> contributionAreas, Set<String> otherContributionAreas) {
        this.id = id;
        this.account = account;
        this.name = name;
        this.birthDate = birthDate;
        this.phoneNumber = phoneNumber;
        this.gamEntryDate = gamEntryDate;
        this.residentialCity = residentialCity;
        this.contactEmail = contactEmail;
        this.dietaryRestriction = dietaryRestriction == null ? DietaryRestriction.notInformed() : dietaryRestriction;
        this.experiences = immutableEnumMap(MemberExperienceType.class, experiences);
        this.sacraments = immutableEnumMap(MemberSacramentType.class, sacraments);
        this.contributionAreas = Set.copyOf(contributionAreas == null ? Set.of() : contributionAreas);
        this.otherContributionAreas = Set.copyOf(otherContributionAreas == null ? Set.of() : otherContributionAreas);
        this.status = status;
        this.version = version;
    }

    public static Member register(Account account, GamName name, LocalDate birthDate, GamPhoneNumber phoneNumber){
        Objects.requireNonNull(account, "Account cannot be null.");
        Objects.requireNonNull(name, "Name cannot be null.");
        Objects.requireNonNull(birthDate, "Birth date cannot be null.");
        Objects.requireNonNull(phoneNumber, "Phone number cannot be null.");
        validateEligibility(birthDate, LocalDate.now());

        MemberStatus status = MemberStatus.ACTIVE;

        UUID id = UUIDGenerator.generateUUIDV7();

        return new Member(id, account, name, birthDate, phoneNumber, status);
    }

    public static Member register(Account account, GamName name, LocalDate birthDate, GamPhoneNumber phoneNumber,
                                  LocalDate gamEntryDate, String residentialCity, GamEmail contactEmail) {
        Member member = register(account, name, birthDate, phoneNumber);
        member.changeGamEntryDate(gamEntryDate, LocalDate.now());
        member.residentialCity = Objects.requireNonNull(residentialCity, "Residential city cannot be null.");
        member.contactEmail = Objects.requireNonNull(contactEmail, "Contact email cannot be null.");
        member.dietaryRestriction = DietaryRestriction.notInformed();
        member.experiences = defaultStatuses(MemberExperienceType.class);
        member.sacraments = defaultStatuses(MemberSacramentType.class);
        member.contributionAreas = Set.of();
        member.otherContributionAreas = Set.of();
        return member;
    }

    public static Member importApproved(UUID id, GamName name, LocalDate birthDate, GamPhoneNumber phoneNumber,
                                        LocalDate gamEntryDate, String residentialCity, GamEmail contactEmail,
                                        DietaryRestriction dietaryRestriction,
                                        Map<MemberExperienceType, InformationStatus> experiences,
                                        Map<MemberSacramentType, InformationStatus> sacraments,
                                        Set<MemberContributionArea> contributionAreas) {
        Objects.requireNonNull(id, "Member id cannot be null.");
        Objects.requireNonNull(name, "Name cannot be null.");
        Objects.requireNonNull(phoneNumber, "Phone number cannot be null.");
        validateEligibility(birthDate, LocalDate.now());
        if (gamEntryDate == null || gamEntryDate.isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("GAM entry date cannot be in the future.");
        }
        return new Member(id, null, name, birthDate, phoneNumber, MemberStatus.ACTIVE, 0L,
                gamEntryDate, Objects.requireNonNull(residentialCity), Objects.requireNonNull(contactEmail),
                Objects.requireNonNull(dietaryRestriction), requireCompleteStatuses(MemberExperienceType.class, experiences),
                requireCompleteStatuses(MemberSacramentType.class, sacraments),
                Objects.requireNonNull(contributionAreas), Set.of());
    }

    public static void validateEligibility(LocalDate birthDate, LocalDate today) {
        Objects.requireNonNull(birthDate, "Birth date cannot be null.");
        Objects.requireNonNull(today, "Eligibility date cannot be null.");
        if (birthDate.isAfter(today)) throw new IllegalArgumentException("Birth date cannot be in the future.");
        if (birthDate.isAfter(today.minusYears(17))) {
            throw new IllegalArgumentException("Member must be at least 17 years old.");
        }
    }

    public void activate(){
        this.status = MemberStatus.ACTIVE;
    }

    public void deactivate(){
        this.status = MemberStatus.INACTIVE;
    }

    public void linkAccount(Account account) {
        Objects.requireNonNull(account, "Account cannot be null.");
        if (this.account != null) {
            throw new IllegalStateException("Member Account link is immutable.");
        }
        this.account = account;
    }

    public void replaceCoreProfile(GamName name, LocalDate birthDate, String residentialCity,
                                   GamPhoneNumber phoneNumber, GamEmail contactEmail, LocalDate today) {
        this.name = Objects.requireNonNull(name, "Name cannot be null.");
        validateEligibility(birthDate, today);
        this.birthDate = birthDate;
        this.residentialCity = Objects.requireNonNull(residentialCity, "Residential city cannot be null.");
        this.phoneNumber = Objects.requireNonNull(phoneNumber, "Phone number cannot be null.");
        this.contactEmail = Objects.requireNonNull(contactEmail, "Contact email cannot be null.");
    }

    public void changeGamEntryDate(LocalDate gamEntryDate, LocalDate today) {
        Objects.requireNonNull(gamEntryDate, "GAM entry date cannot be null.");
        if (gamEntryDate.isAfter(Objects.requireNonNull(today, "Change date cannot be null."))) {
            throw new IllegalArgumentException("GAM entry date cannot be in the future.");
        }
        this.gamEntryDate = gamEntryDate;
    }

    public void replaceDietaryRestriction(DietaryRestriction dietaryRestriction) {
        this.dietaryRestriction = Objects.requireNonNull(dietaryRestriction,
                "Dietary restriction cannot be null.");
    }

    public void replaceExperiences(Map<MemberExperienceType, InformationStatus> experiences) {
        this.experiences = requireCompleteStatuses(MemberExperienceType.class, experiences);
    }

    public void replaceSacraments(Map<MemberSacramentType, InformationStatus> sacraments) {
        this.sacraments = requireCompleteStatuses(MemberSacramentType.class, sacraments);
    }

    public void replaceContributionProfile(Set<MemberContributionArea> contributionAreas,
                                           Set<String> otherContributionAreas) {
        this.contributionAreas = Set.copyOf(Objects.requireNonNull(contributionAreas));
        this.otherContributionAreas = Set.copyOf(Objects.requireNonNull(otherContributionAreas));
    }

    private static <E extends Enum<E>> Map<E, InformationStatus> immutableEnumMap(
            Class<E> type, Map<E, InformationStatus> values) {
        EnumMap<E, InformationStatus> copy = new EnumMap<>(type);
        if (values != null) copy.putAll(values);
        if (copy.containsValue(null)) throw new IllegalArgumentException("Information status cannot be null.");
        return Map.copyOf(copy);
    }

    private static <E extends Enum<E>> Map<E, InformationStatus> defaultStatuses(Class<E> type) {
        EnumMap<E, InformationStatus> values = new EnumMap<>(type);
        for (E key : type.getEnumConstants()) values.put(key, InformationStatus.NOT_INFORMED);
        return Map.copyOf(values);
    }

    private static <E extends Enum<E>> Map<E, InformationStatus> requireCompleteStatuses(
            Class<E> type, Map<E, InformationStatus> values) {
        if (values == null) {
            throw new IllegalArgumentException("Complete information status catalog is required.");
        }
        Map<E, InformationStatus> copy = immutableEnumMap(type, values);
        if (!copy.keySet().equals(Set.of(type.getEnumConstants()))) {
            throw new IllegalArgumentException("Complete information status catalog is required.");
        }
        return copy;
    }

    public int getAge(){
        return Period.between(this.birthDate, LocalDate.now()).getYears();
    }

    public UUID getId() {
        return id;
    }

    public Account getAccount() {
        return account;
    }

    public GamName getName() {
        return name;
    }

    public LocalDate getBirthDate() {
        return birthDate;
    }

    public GamPhoneNumber getPhoneNumber() {
        return phoneNumber;
    }

    public MemberStatus getStatus() {
        return status;
    }

    public LocalDate getGamEntryDate() { return gamEntryDate; }
    public String getResidentialCity() { return residentialCity; }
    public GamEmail getContactEmail() { return contactEmail; }
    public DietaryRestriction getDietaryRestriction() { return dietaryRestriction; }
    public Map<MemberExperienceType, InformationStatus> getExperiences() { return experiences; }
    public Map<MemberSacramentType, InformationStatus> getSacraments() { return sacraments; }
    public Set<MemberContributionArea> getContributionAreas() { return contributionAreas; }
    public Set<String> getOtherContributionAreas() { return otherContributionAreas; }

    public long getVersion() {
        return version;
    }

}

@Target(ElementType.CONSTRUCTOR)
@Retention(RetentionPolicy.CLASS)
@interface Default {
}
