package br.org.gam.api.security.application;

import br.org.gam.api.account.persistence.AccountEntity;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

public class AccountDetails implements UserDetails {

    private final UUID id;
    private final String email; // "username"
    private final String password;
    private final Collection<? extends GrantedAuthority> authorities;

    public AccountDetails(AccountEntity account, Collection<? extends GrantedAuthority> authorities) {
        this.id = account.getId();
        this.email = account.getEmail().value();
        this.password = account.getPasswordHash();
        this.authorities = authorities;
    }

    public UUID getId() {
        return id;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return this.authorities;
    }

    @Override
    public String getPassword() {
        return this.password;
    }

    @Override
    public String getUsername() {
        return this.email;
    }

    // tudo true por enquanto
    @Override public boolean isAccountNonExpired() { return true; }
    @Override public boolean isAccountNonLocked() { return true; }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public boolean isEnabled() { return true; }
}
