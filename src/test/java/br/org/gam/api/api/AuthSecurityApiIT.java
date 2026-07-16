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
import org.springframework.beans.factory.annotation.Value;

import static org.assertj.core.api.Assertions.assertThat;
@ApiTest
@FunctionalTest
@IntegrationTest
@SecurityTest
@DisplayName("API - Authentication security contract")
class AuthSecurityApiIT extends BaseApiIntegrationTest {

    private static final String TRUSTED_ORIGIN = "http://localhost:3000";

    @Value("${jwt.refresh-expiration-ms}")
    private long refreshTokenLifetimeMs;

    @Test
    @DisplayName("REQ-AUTH-013 and REQ-AUTH-007 - valid XSRF proof -> login creates a refresh session")
    void validXsrfProofShouldPermitCrossSiteLogin() {
        String email = uniqueEmail("csrf-login");
        registerAccount(email, TEST_PASSWORD, "CSRF Login");

        ExtractableResponse<Response> challenge = jsonRequest()
                .header("Origin", TRUSTED_ORIGIN)
                .body(loginPayload(email, TEST_PASSWORD))
                .post("/auth/login")
                .then()
                .extract();
        String csrfToken = challenge.cookie("XSRF-TOKEN");

        ExtractableResponse<Response> response = csrfRequest(csrfToken)
                .body(loginPayload(email, TEST_PASSWORD))
                .post("/auth/login")
                .then()
                .statusCode(200)
                .body("token", org.hamcrest.Matchers.not(org.hamcrest.Matchers.blankOrNullString()))
                .extract();

        assertThat(csrfToken).isNotBlank();
        assertThat(challenge.statusCode() / 100).isNotEqualTo(2);
        assertThat(response.cookie("refreshToken")).isNotBlank();
        assertThat(response.asString()).doesNotContain(response.cookie("refreshToken"));
    }

    @Test
    @DisplayName("REQ-AUTH-013, REQ-AUTH-015, REQ-AUTH-018, REQ-BROWSER-AUTH-002, and REQ-WEB-002 - valid XSRF proof -> refresh rotates and logout expires same-origin cookie")
    void validXsrfProofShouldPermitRefreshRotationAndLogout() {
        String email = uniqueEmail("csrf-session");
        registerAccount(email, TEST_PASSWORD, "CSRF Session");
        String oldRefreshToken = login(email, TEST_PASSWORD).cookie("refreshToken");

        ExtractableResponse<Response> challenge = jsonRequest()
                .header("Origin", TRUSTED_ORIGIN)
                .cookie("refreshToken", oldRefreshToken)
                .post("/auth/refresh")
                .then()
                .extract();
        String csrfToken = challenge.cookie("XSRF-TOKEN");

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

        assertThat(csrfToken).isNotBlank();
        assertThat(challenge.statusCode() / 100).isNotEqualTo(2);
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
    @DisplayName("REQ-AUTH-013 - mismatched XSRF header -> rejected without consuming refresh token")
    void mismatchedXsrfHeaderShouldRejectRefreshWithoutRotation() {
        String email = uniqueEmail("csrf-mismatch");
        registerAccount(email, TEST_PASSWORD, "CSRF Mismatch");
        String refreshToken = login(email, TEST_PASSWORD).cookie("refreshToken");

        ExtractableResponse<Response> challenge = jsonRequest()
                .header("Origin", TRUSTED_ORIGIN)
                .cookie("refreshToken", refreshToken)
                .post("/auth/refresh")
                .then()
                .extract();
        String csrfToken = challenge.cookie("XSRF-TOKEN");

        ExtractableResponse<Response> mismatchedResponse = jsonRequest()
                .header("Origin", TRUSTED_ORIGIN)
                .cookie("XSRF-TOKEN", csrfToken)
                .header("X-XSRF-TOKEN", "wrong-csrf-proof")
                .cookie("refreshToken", refreshToken)
                .post("/auth/refresh")
                .then()
                .extract();

        assertThat(challenge.statusCode() / 100).isNotEqualTo(2);
        assertThat(mismatchedResponse.statusCode() / 100).isNotEqualTo(2);
        assertThat(refreshTokenExists(refreshToken)).isTrue();
    }

    @Test
    @DisplayName("REQ-AUTH-012 and REQ-AUTH-013 - XSRF cookie -> readable cross-site security attributes")
    void xsrfCookieShouldUseCrossSiteSecurityAttributes() {
        String email = uniqueEmail("csrf-cookie");
        registerAccount(email, TEST_PASSWORD, "CSRF Cookie");

        ExtractableResponse<Response> response = jsonRequest()
                .header("Origin", TRUSTED_ORIGIN)
                .body(loginPayload(email, TEST_PASSWORD))
                .post("/auth/login")
                .then()
                .extract();

        assertThat(response.statusCode() / 100).isNotEqualTo(2);
        assertThat(response.cookie("XSRF-TOKEN")).isNotBlank();
        assertThat(response.header("Set-Cookie"))
                .contains("XSRF-TOKEN=", "Secure", "SameSite=None", "Path=/")
                .doesNotContain("HttpOnly");
    }

    @Test
    @DisplayName("REQ-AUTH-013 - no Origin and no XSRF proof -> login, refresh, and logout are rejected")
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
    @DisplayName("REQ-AUTH-019 - registration remains public without XSRF proof because it does not use a refresh cookie")
    void registrationShouldRemainPublicWithoutXsrfProof() {
        String email = uniqueEmail("csrf-register");

        ExtractableResponse<Response> response = jsonRequest()
                .header("Origin", TRUSTED_ORIGIN)
                .body(registerPayload(email, TEST_PASSWORD, "CSRF Register"))
                .post("/auth/register")
                .then()
                .statusCode(201)
                .extract();

        trackAccount(UUID.fromString(response.path("id")));
        assertThat(response.cookie("refreshToken")).isNull();
    }

    private io.restassured.specification.RequestSpecification csrfRequest(String csrfToken) {
        return jsonRequest()
                .header("Origin", TRUSTED_ORIGIN)
                .cookie("XSRF-TOKEN", csrfToken)
                .header("X-XSRF-TOKEN", csrfToken);
    }

    private String uniqueEmail(String prefix) {
        return prefix + "-" + UUID.randomUUID() + "@example.com";
    }
}
