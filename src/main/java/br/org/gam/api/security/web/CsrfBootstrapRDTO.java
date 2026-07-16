package br.org.gam.api.security.web;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "CSRF proof established for the same-origin browser authentication workflow.")
public record CsrfBootstrapRDTO(
        @Schema(
                description = "Unpredictable token that must be echoed in the named request header.",
                example = "<browser CSRF token>",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        String token,
        @Schema(
                description = "Request header that carries the CSRF proof.",
                example = "X-XSRF-TOKEN",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        String headerName
) {
}
