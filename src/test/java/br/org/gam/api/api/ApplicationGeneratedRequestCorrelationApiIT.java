package br.org.gam.api.api;

import br.org.gam.api.testing.annotation.ApiTest;
import br.org.gam.api.testing.annotation.FunctionalTest;
import br.org.gam.api.testing.annotation.IntegrationTest;
import br.org.gam.api.testing.annotation.SecurityTest;
import br.org.gam.api.testing.integration.BaseApiIntegrationTest;
import io.restassured.response.ExtractableResponse;
import io.restassured.response.Response;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@ApiTest
@FunctionalTest
@IntegrationTest
@SecurityTest
@TestPropertySource(properties = "gam.request-correlation.mode=APPLICATION_GENERATED")
@DisplayName("API - Application-generated request correlation")
class ApplicationGeneratedRequestCorrelationApiIT extends BaseApiIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("REQ-ACTIVITY-007 and REQ-WEB-012 - forwarding headers cannot enable request-id trust")
    void forwardingHeadersShouldNotMakeAnInboundRequestIdTrusted() {
        UUID inboundRequestId = UUID.randomUUID();
        ExtractableResponse<Response> response = withUntrustedForwardingHeaders(jsonRequest())
                .header("Forwarded", "for=203.0.113.9;proto=https")
                .header("X-Forwarded-For", "203.0.113.9")
                .header("X-Request-Id", inboundRequestId.toString())
                .body(registerPayload(
                        "application-generated-" + UUID.randomUUID() + "@example.com",
                        TEST_PASSWORD,
                        "Application Generated Correlation"
                ))
                .post("/auth/register")
                .then()
                .statusCode(201)
                .extract();

        UUID accountId = UUID.fromString(response.path("id"));
        trackAccount(accountId);
        UUID responseRequestId = UUID.fromString(response.header("X-Request-Id"));
        assertThat(responseRequestId).isNotEqualTo(inboundRequestId);
        assertThat(responseRequestId.version()).isEqualTo(7);
        assertThat(storedRequestId(accountId)).isEqualTo(responseRequestId);
    }

    private UUID storedRequestId(UUID accountId) {
        return jdbcTemplate.queryForObject(
                "SELECT request_id FROM activity_logs "
                        + "WHERE action = 'ACCOUNT_REGISTERED' AND target_id = ?",
                UUID.class,
                accountId
        );
    }
}
