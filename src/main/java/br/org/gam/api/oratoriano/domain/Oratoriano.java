package br.org.gam.api.oratoriano.domain;

import br.org.gam.api.shared.domain.Name;
import br.org.gam.api.shared.persistence.UUIDGenerator;
import br.org.gam.api.shared.phonenumber.MyPhoneNumber;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

public class Oratoriano {
    private UUID id;
    private Name name;
    private LocalDate birthDate;
    private MyPhoneNumber phoneNumber;

    /**
     * @deprecated <b>ESTE CONSTRUTOR É EXCLUSIVO PARA USO INTERNO (JPA/MapStruct).</b>
     * <br> <br>
     * <b> Use o método fábrica {@link #register(Name name, LocalDate birthDate, MyPhoneNumber phoneNumber)}.
     */
    @Deprecated
    public Oratoriano(UUID id, Name name, LocalDate birthDate, MyPhoneNumber phoneNumber) {
        this.id = id;
        this.name = name;
        this.birthDate = birthDate;
        this.phoneNumber = phoneNumber;
    }

    public static Oratoriano register(Name name, LocalDate birthDate, MyPhoneNumber phoneNumber) {
        Objects.requireNonNull(name, "name cannot be null");
        if (birthDate != null && birthDate.isAfter(LocalDate.now())) throw new IllegalArgumentException("Birth date cannot be in the future.");

        UUID id = UUIDGenerator.generateUUIDV7();

        return new Oratoriano(id, name, birthDate, phoneNumber);
    }

    public void setBirthDate(LocalDate birthDate) {
        this.birthDate = birthDate;
    }

    public void setPhoneNumber(MyPhoneNumber phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public UUID getId() {
        return id;
    }

    public Name getName() {
        return name;
    }

    public LocalDate getBirthDate() {
        return birthDate;
    }

    public MyPhoneNumber getPhoneNumber() {
        return phoneNumber;
    }
}
