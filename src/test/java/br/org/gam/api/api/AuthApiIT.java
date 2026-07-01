package br.org.gam.api.api;

import br.org.gam.api.testing.annotation.ApiTest;
import br.org.gam.api.testing.annotation.IntegrationTest;
import br.org.gam.api.testing.annotation.SecurityTest;
import br.org.gam.api.testing.integration.BaseApiIntegrationTest;
import io.restassured.response.ExtractableResponse;
import io.restassured.response.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.hamcrest.Matchers.blankOrNullString;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;

@ApiTest
@IntegrationTest
@SecurityTest
@DisplayName("API - Authentication")
class AuthApiIT extends BaseApiIntegrationTest {

    @Test
    @DisplayName("valid registration -> HTTP 201, Location header, and persisted account")
    void validRegistrationShouldReturnCreatedAccount() {
        String email = "register-" + UUID.randomUUID() + "@example.com";

        ExtractableResponse<Response> response = jsonRequest()
                .body(registerPayload(email, TEST_PASSWORD, "Register API"))
                .post("/auth/register")
                .then()
                .statusCode(201)
                .header("Location", containsString("/auth/register/"))
                .body("id", not(blankOrNullString()))
                .extract();

        UUID accountId = UUID.fromString(response.path("id"));
        trackAccount(accountId);
        assertThat(accountExists(accountId)).isTrue();
    }

    @Test
    @DisplayName("valid login -> access token and refresh cookie")
    void validLoginShouldReturnAccessTokenAndRefreshCookie() {
        String email = "login-" + UUID.randomUUID() + "@example.com";
        registerAccount(email, TEST_PASSWORD, "Login API");

        ExtractableResponse<Response> response = login(email, TEST_PASSWORD);

        String accessToken = response.path("token");
        String refreshToken = response.cookie("refreshToken");

        assertThat(accessToken).isNotBlank();
        assertThatCode(() -> UUID.fromString(refreshToken)).doesNotThrowAnyException();
        assertThat(refreshTokenExists(refreshToken)).isTrue();
        assertThat(response.header("Set-Cookie"))
                .contains("refreshToken=", "HttpOnly", "Path=/", "Max-Age=604800", "SameSite=Strict");
    }

    @Test
    @DisplayName("refresh cookie -> rotated refresh cookie and new access token")
    void refreshCookieShouldRotateRefreshToken() {
        String email = "refresh-" + UUID.randomUUID() + "@example.com";
        registerAccount(email, TEST_PASSWORD, "Refresh API");
        ExtractableResponse<Response> loginResponse = login(email, TEST_PASSWORD);
        String oldRefreshToken = loginResponse.cookie("refreshToken");

        ExtractableResponse<Response> refreshResponse = jsonRequest()
                .cookie("refreshToken", oldRefreshToken)
                .post("/auth/refresh")
                .then()
                .statusCode(200)
                .body("token", not(blankOrNullString()))
                .extract();

        String newRefreshToken = refreshResponse.cookie("refreshToken");
        assertThat(newRefreshToken).isNotBlank().isNotEqualTo(oldRefreshToken);
        assertThat(refreshTokenExists(oldRefreshToken)).isFalse();
        assertThat(refreshTokenExists(newRefreshToken)).isTrue();
    }

    @Test
    @DisplayName("logout with refresh cookie -> refresh token deleted and cookie expired")
    void logoutWithRefreshCookieShouldDeleteTokenAndExpireCookie() {
        String email = "logout-" + UUID.randomUUID() + "@example.com";
        registerAccount(email, TEST_PASSWORD, "Logout API");
        String refreshToken = login(email, TEST_PASSWORD).cookie("refreshToken");

        ExtractableResponse<Response> response = jsonRequest()
                .cookie("refreshToken", refreshToken)
                .post("/auth/logout")
                .then()
                .statusCode(200)
                .extract();

        assertThat(refreshTokenExists(refreshToken)).isFalse();
        assertThat(response.asString()).contains("You've been signed out!");
        assertThat(response.header("Set-Cookie"))
                .contains("refreshToken=", "Max-Age=0", "HttpOnly", "Path=/", "SameSite=Strict");
    }

    @Test
    @DisplayName("missing refresh cookie -> HTTP 403")
    void missingRefreshCookieShouldReturnForbidden() {
        jsonRequest()
                .post("/auth/refresh")
                .then()
                .statusCode(403);
    }

    @Test
    @DisplayName("invalid registration payload -> HTTP 400")
    void invalidRegistrationPayloadShouldReturnBadRequest() {
        jsonRequest()
                .body(registerPayload("", "", ""))
                .post("/auth/register")
                .then()
                .statusCode(400)
                .body("status", org.hamcrest.Matchers.equalTo(400))
                .body("error", org.hamcrest.Matchers.equalTo("Bad Request"));
    }

    @Test
    @DisplayName("duplicate email registration -> HTTP 409")
    void duplicateEmailRegistrationShouldReturnConflict() {
        String email = "duplicate-" + UUID.randomUUID() + "@example.com";
        registerAccount(email, TEST_PASSWORD, "Duplicate API");

        jsonRequest()
                .body(registerPayload(email, TEST_PASSWORD, "Duplicate API"))
                .post("/auth/register")
                .then()
                .statusCode(409)
                .body("status", org.hamcrest.Matchers.equalTo(409))
                .body("message", containsString("already registered"));
    }
}
