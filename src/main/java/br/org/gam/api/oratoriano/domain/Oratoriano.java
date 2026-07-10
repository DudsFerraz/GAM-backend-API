package br.org.gam.api.oratoriano.domain;

import br.org.gam.api.shared.domain.GamName;
import br.org.gam.api.shared.persistence.UUIDGenerator;
import br.org.gam.api.shared.phonenumber.GamPhoneNumber;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

public class Oratoriano {
    private UUID id;
    private GamName name;
    private LocalDate birthDate;
    private GamPhoneNumber phoneNumber;

    /**
     * @deprecated <b>ESTE CONSTRUTOR É EXCLUSIVO PARA USO INTERNO (JPA/MapStruct).</b>
     * <br> <br>
     * <b> Use o método fábrica {@link #register(GamName name, LocalDate birthDate, GamPhoneNumber phoneNumber)}.
     */
    @Deprecated
    public Oratoriano(UUID id, GamName name, LocalDate birthDate, GamPhoneNumber phoneNumber) {
        this.id = id;
        this.name = name;
        this.birthDate = birthDate;
        this.phoneNumber = phoneNumber;
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
}
