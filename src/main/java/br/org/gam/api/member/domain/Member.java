package br.org.gam.api.member.domain;

import br.org.gam.api.account.domain.Account;
import br.org.gam.api.shared.domain.GamName;
import br.org.gam.api.shared.persistence.UUIDGenerator;
import br.org.gam.api.shared.phonenumber.GamPhoneNumber;
import java.time.LocalDate;
import java.time.Period;
import java.util.Objects;
import java.util.UUID;

public class Member {
    private UUID id;
    private Account account;
    private GamName name;
    private LocalDate birthDate;
    private GamPhoneNumber phoneNumber;
    private MemberStatus status;

    /**
     * @deprecated <b>ESTE CONSTRUTOR É EXCLUSIVO PARA USO INTERNO (JPA/MapStruct).</b>
     * <br> <br>
     * <b> Use o método fábrica {@link #register(Account account, GamName name, LocalDate birthDate, GamPhoneNumber phoneNumber)}.
     */
    @Deprecated
    public Member(UUID id, Account account, GamName name, LocalDate birthDate, GamPhoneNumber phoneNumber, MemberStatus status) {
        this.id = id;
        this.account = account;
        this.name = name;
        this.birthDate = birthDate;
        this.phoneNumber = phoneNumber;
        this.status = status;
    }

    public static Member register(Account account, GamName name, LocalDate birthDate, GamPhoneNumber phoneNumber){
        Objects.requireNonNull(account, "Account cannot be null.");
        Objects.requireNonNull(name, "Name cannot be null.");
        Objects.requireNonNull(birthDate, "Birth date cannot be null.");
        Objects.requireNonNull(phoneNumber, "Phone number cannot be null.");
        if (birthDate.isAfter(LocalDate.now())) throw new IllegalArgumentException("Birth date cannot be in the future.");

        MemberStatus status = MemberStatus.PENDENT;

        UUID id = UUIDGenerator.generateUUIDV7();

        return new Member(id, account, name, birthDate, phoneNumber, status);
    }

    public void activate(){
        this.status = MemberStatus.ACTIVE;
    }

    public void deactivate(){
        this.status = MemberStatus.INACTIVE;
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

}
