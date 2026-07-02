package br.org.gam.api.member.domain;

import br.org.gam.api.account.domain.Account;
import br.org.gam.api.shared.domain.Name;
import br.org.gam.api.shared.persistence.UUIDGenerator;
import br.org.gam.api.shared.phonenumber.MyPhoneNumber;
import java.time.LocalDate;
import java.time.Period;
import java.util.Objects;
import java.util.UUID;

public class Member {
    private UUID id;
    private Account account;
    private Name name;
    private LocalDate birthDate;
    private MyPhoneNumber phoneNumber;
    private MemberStatus status;

    /**
     * @deprecated <b>ESTE CONSTRUTOR É EXCLUSIVO PARA USO INTERNO (JPA/MapStruct).</b>
     * <br> <br>
     * <b> Use o método fábrica {@link #register(Account account, Name name, LocalDate birthDate, MyPhoneNumber phoneNumber)}.
     */
    @Deprecated
    public Member(UUID id, Account account, Name name, LocalDate birthDate, MyPhoneNumber phoneNumber, MemberStatus status) {
        this.id = id;
        this.account = account;
        this.name = name;
        this.birthDate = birthDate;
        this.phoneNumber = phoneNumber;
        this.status = status;
    }

    public static Member register(Account account, Name name, LocalDate birthDate, MyPhoneNumber phoneNumber){
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

    public Name getName() {
        return name;
    }

    public LocalDate getBirthDate() {
        return birthDate;
    }

    public MyPhoneNumber getPhoneNumber() {
        return phoneNumber;
    }

    public MemberStatus getStatus() {
        return status;
    }

}
