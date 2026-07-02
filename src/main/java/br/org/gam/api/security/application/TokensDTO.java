package br.org.gam.api.security.application;

import java.util.UUID;

public record TokensDTO(
        String accessToken,
        UUID refreshToken
) {
}
