package br.org.gam.api.api;

import br.org.gam.api.testing.annotation.ApiTest;
import br.org.gam.api.testing.annotation.FunctionalTest;
import br.org.gam.api.testing.annotation.IntegrationTest;
import br.org.gam.api.testing.annotation.SecurityTest;
import br.org.gam.api.testing.integration.BaseApiIntegrationTest;
import io.restassured.response.ExtractableResponse;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@ApiTest
@FunctionalTest
@IntegrationTest
@SecurityTest
@TestPropertySource(properties = "gam.request-correlation.mode=TRUSTED_PROXY")
@DisplayName("API - Trusted-proxy request correlation")
class TrustedProxyRequestCorrelationApiIT extends BaseApiIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("REQ-ACTIVITY-007 and REQ-WEB-012 - valid proxy request id -> response and activity reuse it")
    void validRequestIdShouldBeAcceptedAndCorrelated() {
        UUID trustedRequestId = UUID.randomUUID();

        ExtractableResponse<Response> response = register("valid", trustedRequestId.toString());

        UUID accountId = trackedAccountId(response);
        assertThat(UUID.fromString(response.header("X-Request-Id"))).isEqualTo(trustedRequestId);
        assertThat(storedRequestId(accountId)).isEqualTo(trustedRequestId);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("missingOrInvalidRequestIds")
    @DisplayName("REQ-ACTIVITY-007 and REQ-WEB-012 - missing or invalid proxy request id -> generated UUID v7")
    void missingOrInvalidRequestIdShouldGenerateAndCorrelateUuidV7(String scenario, String inboundRequestId) {
        ExtractableResponse<Response> response = register(scenario, inboundRequestId);

        UUID accountId = trackedAccountId(response);
        UUID responseRequestId = UUID.fromString(response.header("X-Request-Id"));
        assertThat(responseRequestId.version()).isEqualTo(7);
        assertThat(storedRequestId(accountId)).isEqualTo(responseRequestId);
    }

    private ExtractableResponse<Response> register(String scenario, String inboundRequestId) {
        RequestSpecification request = jsonRequest();
        if (inboundRequestId != null) {
            request.header("X-Request-Id", inboundRequestId);
        }
        return request
                .body(registerPayload(
                        "trusted-proxy-" + scenario + "-" + UUID.randomUUID() + "@example.com",
                        TEST_PASSWORD,
                        "Trusted Proxy Correlation"
                ))
                .post("/auth/register")
                .then()
                .statusCode(201)
                .extract();
    }

    private UUID trackedAccountId(ExtractableResponse<Response> response) {
        UUID accountId = UUID.fromString(response.path("id"));
        trackAccount(accountId);
        return accountId;
    }

    private UUID storedRequestId(UUID accountId) {
        return jdbcTemplate.queryForObject(
                "SELECT request_id FROM activity_logs "
                        + "WHERE action = 'ACCOUNT_REGISTERED' AND target_id = ?",
                UUID.class,
                accountId
        );
    }

    private static Stream<Arguments> missingOrInvalidRequestIds() {
        return Stream.of(
                Arguments.of("absent", null),
                Arguments.of("invalid", "not-a-uuid")
        );
    }
}
