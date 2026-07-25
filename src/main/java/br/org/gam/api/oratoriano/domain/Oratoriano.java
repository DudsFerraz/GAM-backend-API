package br.org.gam.api.oratoriano.domain;

import br.org.gam.api.shared.domain.GamName;
import br.org.gam.api.shared.persistence.UUIDGenerator;
import br.org.gam.api.shared.phonenumber.GamPhoneNumber;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class Oratoriano {
    private UUID id;
    private GamName name;
    private LocalDate birthDate;
    private GamPhoneNumber phoneNumber;
    private final List<LocalDate> activeOratorioAttendanceDates;

    /**
     * @deprecated <b>ESTE CONSTRUTOR É EXCLUSIVO PARA USO INTERNO (JPA/MapStruct).</b>
     * <br> <br>
     * <b> Use o método fábrica {@link #register(GamName name, LocalDate birthDate, GamPhoneNumber phoneNumber)}.
     */
    @Deprecated
    public Oratoriano(UUID id, GamName name, LocalDate birthDate, GamPhoneNumber phoneNumber) {
        this(id, name, birthDate, phoneNumber, List.of());
    }

    private Oratoriano(
            UUID id,
            GamName name,
            LocalDate birthDate,
            GamPhoneNumber phoneNumber,
            Collection<LocalDate> activeOratorioAttendanceDates
    ) {
        this.id = id;
        this.name = name;
        this.birthDate = birthDate;
        this.phoneNumber = phoneNumber;
        this.activeOratorioAttendanceDates = List.copyOf(activeOratorioAttendanceDates);
    }

    public static Oratoriano register(GamName name, LocalDate birthDate, GamPhoneNumber phoneNumber) {
        Objects.requireNonNull(name, "name cannot be null");
        if (birthDate != null && birthDate.isAfter(LocalDate.now())) throw new IllegalArgumentException("Birth date cannot be in the future.");

        UUID id = UUIDGenerator.generateUUIDV7();

        return new Oratoriano(id, name, birthDate, phoneNumber);
    }

    public void setBirthDate(LocalDate birthDate) {
        this.birthDate = birthDate;
    }

    public void setPhoneNumber(GamPhoneNumber phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public UUID getId() {
        return id;
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

    public Oratoriano withActiveOratorioAttendances(Collection<LocalDate> occurrenceDates) {
        Objects.requireNonNull(occurrenceDates, "occurrenceDates cannot be null");
        if (occurrenceDates.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("occurrenceDates cannot contain null");
        }
        return new Oratoriano(id, name, birthDate, phoneNumber, occurrenceDates);
    }

    public long oratorioAttendances() {
        return activeOratorioAttendanceDates.size();
    }

    public long oratorioYearAttendances(int year) {
        return activeOratorioAttendanceDates.stream()
                .filter(date -> date.getYear() == year)
                .count();
    }

    public long oratorioMonthAttendances(int year, int month) {
        return activeOratorioAttendanceDates.stream()
                .filter(date -> date.getYear() == year && date.getMonthValue() == month)
                .count();
    }

    public long oratorioDistinctMonthsAttendances() {
        return activeOratorioAttendanceDates.stream()
                .map(YearMonth::from)
                .distinct()
                .count();
    }

    public long oratorioYearDistinctMonthsAttendances(int year) {
        return activeOratorioAttendanceDates.stream()
                .filter(date -> date.getYear() == year)
                .map(LocalDate::getMonthValue)
                .distinct()
                .count();
    }

    public long oratorioDistinctYearsAttendances() {
        return activeOratorioAttendanceDates.stream()
                .map(LocalDate::getYear)
                .distinct()
                .count();
    }
}
