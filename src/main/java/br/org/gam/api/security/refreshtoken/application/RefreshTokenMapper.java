package br.org.gam.api.security.refreshtoken.application;

import br.org.gam.api.account.application.AccountMapper;
import br.org.gam.api.security.refreshtoken.domain.RefreshToken;
import br.org.gam.api.security.refreshtoken.persistence.RefreshTokenEntity;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring", uses = {AccountMapper.class} )
public interface RefreshTokenMapper {
    RefreshTokenEntity domainToEntity(RefreshToken refreshToken);
    RefreshToken entityToDomain(RefreshTokenEntity refreshTokenEntity);
}
