package br.org.gam.api.api;

import br.org.gam.api.testing.annotation.ApiTest;
import br.org.gam.api.testing.annotation.FunctionalTest;
import br.org.gam.api.testing.annotation.IntegrationTest;
import br.org.gam.api.testing.annotation.SecurityTest;
import br.org.gam.api.testing.integration.BaseApiIntegrationTest;
import io.restassured.response.ExtractableResponse;
import io.restassured.response.Response;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
@ApiTest
@FunctionalTest
@IntegrationTest
@SecurityTest
@DisplayName("API - Authentication security contract")
class AuthSecurityApiIT extends BaseApiIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Value("${jwt.refresh-expiration-ms}")
    private long refreshTokenLifetimeMs;

    @Test
    @DisplayName("REQ-BROWSER-AUTH-003 - CSRF bootstrap -> no-store JSON and matching readable cookie")
    void csrfBootstrapShouldReturnNoStoreJsonAndMatchingReadableCookie() {
        ExtractableResponse<Response> response = csrfBootstrap();
        String token = response.jsonPath().getString("token");
        String headerName = response.jsonPath().getString("headerName");

        assertThat(response.jsonPath().getMap("$")).containsOnlyKeys("token", "headerName");
        assertThat(token).isEqualTo(response.cookie("XSRF-TOKEN"));
        assertThat(token).isNotBlank();
        assertThat(headerName).isEqualTo("X-XSRF-TOKEN");
        assertThat(response.header("Cache-Control")).containsIgnoringCase("no-store");
        assertThat(response.header("Set-Cookie"))
                .contains("XSRF-TOKEN=", "Secure", "SameSite=Lax", "Path=/api/auth")
                .doesNotContain("HttpOnly", "Domain=");
        assertThat(response.cookies()).doesNotContainKey("refreshToken");
    }

    @Test
    @DisplayName("REQ-BROWSER-AUTH-004 and REQ-AUTH-007 - valid CSRF proof and exact Origin -> login creates a refresh session")
    void validXsrfProofShouldPermitSameOriginLogin() {
        String email = uniqueEmail("csrf-login");
        registerAccount(email, TEST_PASSWORD, "CSRF Login");

        ExtractableResponse<Response> response = csrfRequest(csrfBootstrap().cookie("XSRF-TOKEN"))
                .body(loginPayload(email, TEST_PASSWORD))
                .post("/auth/login")
                .then()
                .statusCode(200)
                .body("token", org.hamcrest.Matchers.not(org.hamcrest.Matchers.blankOrNullString()))
                .extract();

        assertThat(response.cookie("refreshToken")).isNotBlank();
        assertThat(response.asString()).doesNotContain(response.cookie("refreshToken"));
    }

    @Test
    @DisplayName("REQ-BROWSER-AUTH-004, REQ-AUTH-015, REQ-AUTH-018, REQ-BROWSER-AUTH-002, and REQ-WEB-002 - valid CSRF proof -> refresh rotates and logout expires same-origin cookie")
    void validXsrfProofShouldPermitRefreshRotationAndLogout() {
        String email = uniqueEmail("csrf-session");
        registerAccount(email, TEST_PASSWORD, "CSRF Session");
        String oldRefreshToken = login(email, TEST_PASSWORD).cookie("refreshToken");

        String csrfToken = csrfBootstrap().cookie("XSRF-TOKEN");

        ExtractableResponse<Response> refreshResponse = csrfRequest(csrfToken)
                .cookie("refreshToken", oldRefreshToken)
                .post("/auth/refresh")
                .then()
                .statusCode(200)
                .body("token", org.hamcrest.Matchers.not(org.hamcrest.Matchers.blankOrNullString()))
                .extract();
        String replacementRefreshToken = refreshResponse.cookie("refreshToken");

        ExtractableResponse<Response> logoutResponse = csrfRequest(csrfToken)
                .cookie("refreshToken", replacementRefreshToken)
                .post("/auth/logout")
                .then()
                .statusCode(200)
                .extract();

        assertThat(replacementRefreshToken).isNotBlank().isNotEqualTo(oldRefreshToken);
        assertThat(refreshTokenExists(oldRefreshToken)).isFalse();
        assertThat(refreshTokenExists(replacementRefreshToken)).isFalse();
        assertThat(refreshResponse.header("Set-Cookie"))
                .contains(
                        "refreshToken=",
                        "HttpOnly",
                        "SameSite=Lax",
                        "Path=/api/auth",
                        "Max-Age=" + refreshTokenLifetimeMs / 1000
                )
                .contains("Secure")
                .doesNotContain("Domain=");
        assertThat(logoutResponse.header("Set-Cookie"))
                .contains("refreshToken=", "Max-Age=0", "HttpOnly", "Secure", "SameSite=Lax", "Path=/api/auth")
                .doesNotContain("Domain=");
    }

    @Test
    @DisplayName("REQ-BROWSER-AUTH-004 - mismatched CSRF header -> rejected without consuming refresh token")
    void mismatchedXsrfHeaderShouldRejectRefreshWithoutRotation() {
        String email = uniqueEmail("csrf-mismatch");
        registerAccount(email, TEST_PASSWORD, "CSRF Mismatch");
        String refreshToken = login(email, TEST_PASSWORD).cookie("refreshToken");

        String csrfToken = csrfBootstrap().cookie("XSRF-TOKEN");

        ExtractableResponse<Response> mismatchedResponse = jsonRequest()
                .header("Origin", TRUSTED_ORIGIN)
                .cookie("XSRF-TOKEN", csrfToken)
                .header("X-XSRF-TOKEN", "wrong-csrf-proof")
                .cookie("refreshToken", refreshToken)
                .post("/auth/refresh")
                .then()
                .extract();

        assertThat(mismatchedResponse.statusCode() / 100).isNotEqualTo(2);
        assertThat(refreshTokenExists(refreshToken)).isTrue();
    }

    @Test
    @DisplayName("REQ-BROWSER-AUTH-004 - mismatched Origin -> valid CSRF proof cannot create a login session")
    void mismatchedOriginShouldRejectLoginWithoutCreatingSession() {
        String email = uniqueEmail("csrf-origin");
        UUID accountId = registerAccount(email, TEST_PASSWORD, "Origin Validation");
        String csrfToken = csrfBootstrap().cookie("XSRF-TOKEN");

        ExtractableResponse<Response> response = csrfRequest(csrfToken, UNTRUSTED_ORIGIN, null)
                .body(loginPayload(email, TEST_PASSWORD))
                .post("/auth/login")
                .then()
                .extract();

        assertCommonForbiddenError(response);
        assertThat(refreshTokenCount(accountId)).isZero();
    }

    @Test
    @DisplayName("REQ-BROWSER-AUTH-004 - absent Origin with matching Referer -> valid CSRF proof is accepted")
    void matchingRefererShouldBeUsedWhenOriginIsAbsent() {
        String email = uniqueEmail("csrf-referer");
        registerAccount(email, TEST_PASSWORD, "Referer Fallback");

        ExtractableResponse<Response> response = csrfRequest(
                        csrfBootstrap().cookie("XSRF-TOKEN"),
                        null,
                        TRUSTED_ORIGIN + "/login?source=same-origin"
                )
                .body(loginPayload(email, TEST_PASSWORD))
                .post("/auth/login")
                .then()
                .extract();

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.cookie("refreshToken")).isNotBlank();
    }

    @Test
    @DisplayName("REQ-BROWSER-AUTH-004 - Origin takes precedence over Referer -> mismatched Origin is rejected")
    void originHeaderShouldTakePrecedenceOverReferer() {
        String email = uniqueEmail("csrf-precedence");
        UUID accountId = registerAccount(email, TEST_PASSWORD, "Origin Precedence");

        ExtractableResponse<Response> originMismatch = csrfRequest(
                        csrfBootstrap().cookie("XSRF-TOKEN"),
                        UNTRUSTED_ORIGIN,
                        TRUSTED_ORIGIN + "/login"
                )
                .body(loginPayload(email, TEST_PASSWORD))
                .post("/auth/login")
                .then()
                .extract();

        ExtractableResponse<Response> refererMismatch = csrfRequest(
                        csrfBootstrap().cookie("XSRF-TOKEN"),
                        TRUSTED_ORIGIN,
                        UNTRUSTED_ORIGIN + "/login"
                )
                .body(loginPayload(email, TEST_PASSWORD))
                .post("/auth/login")
                .then()
                .extract();

        assertThat(originMismatch.statusCode() / 100).isNotEqualTo(2);
        assertThat(refererMismatch.statusCode()).isEqualTo(200);
        assertThat(refreshTokenCount(accountId)).isEqualTo(1);
    }

    @Test
    @DisplayName("REQ-BROWSER-AUTH-004 - default HTTPS port -> effective-port equivalent Origin is accepted")
    void defaultHttpsPortShouldUseEffectivePortComparison() {
        String acceptedEmail = uniqueEmail("csrf-default-port");
        registerAccount(acceptedEmail, TEST_PASSWORD, "Default Port");
        ExtractableResponse<Response> accepted = csrfRequest(
                        csrfBootstrap().cookie("XSRF-TOKEN"),
                        TRUSTED_ORIGIN + ":443",
                        null
                )
                .body(loginPayload(acceptedEmail, TEST_PASSWORD))
                .post("/auth/login")
                .then()
                .extract();

        String rejectedEmail = uniqueEmail("csrf-non-default-port");
        UUID rejectedAccountId = registerAccount(rejectedEmail, TEST_PASSWORD, "Non Default Port");
        ExtractableResponse<Response> rejected = csrfRequest(
                        csrfBootstrap().cookie("XSRF-TOKEN"),
                        TRUSTED_ORIGIN + ":444",
                        null
                )
                .body(loginPayload(rejectedEmail, TEST_PASSWORD))
                .post("/auth/login")
                .then()
                .extract();

        assertThat(accepted.statusCode()).isEqualTo(200);
        assertThat(rejected.statusCode() / 100).isNotEqualTo(2);
        assertThat(refreshTokenCount(rejectedAccountId)).isZero();
    }

    @Test
    @DisplayName("REQ-BROWSER-AUTH-004 - HTTP Origin versus HTTPS canonical origin -> rejected despite valid CSRF proof")
    void originSchemeShouldMatchCanonicalOrigin() {
        String email = uniqueEmail("csrf-scheme");
        UUID accountId = registerAccount(email, TEST_PASSWORD, "Origin Scheme");

        ExtractableResponse<Response> response = csrfRequest(
                        csrfBootstrap().cookie("XSRF-TOKEN"),
                        "http://test.example",
                        null
                )
                .body(loginPayload(email, TEST_PASSWORD))
                .post("/auth/login")
                .then()
                .extract();

        assertThat(response.statusCode() / 100).isNotEqualTo(2);
        assertThat(refreshTokenCount(accountId)).isZero();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidSourceEvidence")
    @DisplayName("REQ-BROWSER-AUTH-004 - invalid or absent source evidence -> login session is not created")
    void invalidSourceEvidenceShouldFailClosed(
            String caseName,
            String origin,
            String referer
    ) {
        String email = uniqueEmail("csrf-fail-closed");
        UUID accountId = registerAccount(email, TEST_PASSWORD, "Fail Closed");

        ExtractableResponse<Response> response = csrfRequest(
                        csrfBootstrap().cookie("XSRF-TOKEN"),
                        origin,
                        referer
                )
                .body(loginPayload(email, TEST_PASSWORD))
                .post("/auth/login")
                .then()
                .extract();

        assertThat(response.statusCode() / 100).isNotEqualTo(2);
        assertThat(refreshTokenCount(accountId)).isZero();
    }

    private static Stream<Arguments> invalidSourceEvidence() {
        return Stream.of(
                Arguments.of("mismatched Origin", UNTRUSTED_ORIGIN, null),
                Arguments.of("opaque null Origin", "null", null),
                Arguments.of("Origin suffix match", TRUSTED_ORIGIN + ".attacker.invalid", null),
                Arguments.of("absent Origin and Referer", null, null),
                Arguments.of("untrusted Referer fallback", null, UNTRUSTED_ORIGIN + "/login")
        );
    }

    @Test
    @DisplayName("REQ-BROWSER-AUTH-004 - invalid source evidence -> refresh and logout do not mutate the session")
    void invalidSourceEvidenceShouldNotRotateOrDeleteRefreshSession() {
        AuthSession session = registerAndLogin(null);
        long initialRefreshTokenCount = refreshTokenCount(session.accountId());

        ExtractableResponse<Response> refreshResponse = csrfRequest(
                        csrfBootstrap().cookie("XSRF-TOKEN"),
                        UNTRUSTED_ORIGIN,
                        null
                )
                .cookie("refreshToken", session.refreshToken())
                .post("/auth/refresh")
                .then()
                .extract();
        ExtractableResponse<Response> logoutResponse = csrfRequest(
                        csrfBootstrap().cookie("XSRF-TOKEN"),
                        "null",
                        null
                )
                .cookie("refreshToken", session.refreshToken())
                .post("/auth/logout")
                .then()
                .extract();

        assertThat(refreshResponse.statusCode() / 100).isNotEqualTo(2);
        assertThat(logoutResponse.statusCode() / 100).isNotEqualTo(2);
        assertThat(refreshTokenExists(session.refreshToken())).isTrue();
        assertThat(refreshTokenCount(session.accountId())).isEqualTo(initialRefreshTokenCount);
    }

    @Test
    @DisplayName("REQ-BROWSER-AUTH-003 - CSRF bootstrap cookie -> same-origin security attributes")
    void xsrfCookieShouldUseSameOriginSecurityAttributes() {
        ExtractableResponse<Response> response = csrfBootstrap();

        assertThat(response.cookie("XSRF-TOKEN")).isNotBlank();
        assertThat(response.header("Set-Cookie"))
                .contains("XSRF-TOKEN=", "Secure", "SameSite=Lax", "Path=/api/auth")
                .doesNotContain("HttpOnly", "Domain=");
    }

    @Test
    @DisplayName("REQ-BROWSER-AUTH-004 - no Origin and no CSRF proof -> login, refresh, and logout are rejected")
    void noOriginWithoutXsrfProofShouldRejectCookieAuthEndpoints() {
        String email = uniqueEmail("csrf-no-origin");
        registerAccount(email, TEST_PASSWORD, "CSRF No Origin");
        String refreshToken = login(email, TEST_PASSWORD).cookie("refreshToken");

        ExtractableResponse<Response> loginResponse = jsonRequest()
                .body(loginPayload(email, TEST_PASSWORD))
                .post("/auth/login")
                .then()
                .extract();
        ExtractableResponse<Response> refreshResponse = jsonRequest()
                .cookie("refreshToken", refreshToken)
                .post("/auth/refresh")
                .then()
                .extract();
        ExtractableResponse<Response> logoutResponse = jsonRequest()
                .cookie("refreshToken", refreshToken)
                .post("/auth/logout")
                .then()
                .extract();

        assertThat(java.util.List.of(
                loginResponse.statusCode(),
                refreshResponse.statusCode(),
                logoutResponse.statusCode()
        )).allSatisfy(status -> assertThat(status / 100).isNotEqualTo(2));
        assertThat(refreshTokenExists(refreshToken)).isTrue();
    }

    @Test
    @DisplayName("REQ-AUTH-019 and REQ-BROWSER-AUTH-004 - registration remains public without CSRF proof because it does not use a refresh cookie")
    void registrationShouldRemainPublicWithoutXsrfProof() {
        String email = uniqueEmail("csrf-register");

        ExtractableResponse<Response> response = jsonRequest()
                .body(registerPayload(email, TEST_PASSWORD, "CSRF Register"))
                .post("/auth/register")
                .then()
                .statusCode(201)
                .extract();

        trackAccount(UUID.fromString(response.path("id")));
        assertThat(response.cookie("refreshToken")).isNull();
    }

    private void assertCommonForbiddenError(ExtractableResponse<Response> response) {
        Map<String, Object> error = response.jsonPath().getMap("$");

        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(error)
                .containsOnlyKeys("timestamp", "status", "code", "message", "details")
                .containsEntry("status", 403);
        assertThat(error.get("timestamp")).isInstanceOf(String.class);
        assertThat((String) error.get("timestamp")).isNotBlank().endsWith("Z");
        assertThat(error.get("code")).isInstanceOf(String.class);
        assertThat((String) error.get("code")).isNotBlank();
        assertThat(error.get("message")).isInstanceOf(String.class);
        assertThat((String) error.get("message")).isNotBlank();
        assertThat(error.get("details")).isInstanceOf(Map.class);
    }

    private String uniqueEmail(String prefix) {
        return prefix + "-" + UUID.randomUUID() + "@example.com";
    }

    private long refreshTokenCount(UUID accountId) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM refresh_tokens WHERE account_id = ?",
                Long.class,
                accountId
        );
        return count == null ? 0 : count;
    }
}
