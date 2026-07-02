package br.org.gam.api.account.application.useCases.LoginAccount;

import br.org.gam.api.security.application.TokensDTO;

public interface LoginAccount {
    TokensDTO login(LoginAccountDTO dto);
}
