package br.org.gam.api.api;

import br.org.gam.api.security.jwt.JwtService;
import br.org.gam.api.testing.annotation.ApiTest;
import br.org.gam.api.testing.annotation.FunctionalTest;
import br.org.gam.api.testing.annotation.IntegrationTest;
import br.org.gam.api.testing.annotation.SecurityTest;
import br.org.gam.api.testing.integration.BaseApiIntegrationTest;
import io.jsonwebtoken.Claims;
import io.restassured.response.ExtractableResponse;
import io.restassured.response.Response;
import java.net.URI;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.hamcrest.Matchers.blankOrNullString;
import static org.hamcrest.Matchers.not;

@ApiTest
@FunctionalTest
@IntegrationTest
@SecurityTest
@DisplayName("API - Authentication requirements")
class AuthApiIT extends BaseApiIntegrationTest {

    private static final String TRUSTED_ORIGIN = "http://localhost:3000";
    private static final String UNTRUSTED_ORIGIN = "https://untrusted.example";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @Value("${jwt.expiration-ms}")
    private long accessTokenLifetimeMs;

    @Value("${jwt.refresh-expiration-ms}")
    private long refreshTokenLifetimeMs;

    @Test
    @DisplayName("REQ-AUTH-001, REQ-AUTH-002, REQ-AUTH-003, and REQ-AUTH-005 - valid registration -> unprivileged Account only")
    void validRegistrationShouldCreateOnlyAnUnprivilegedAccount() {
        String email = uniqueEmail("register");
        String rawPassword = "registration-password";

        ExtractableResponse<Response> response = jsonRequest()
                .body(registerPayload(email, rawPassword, "  Eduardo  "))
                .post("/auth/register")
                .then()
                .statusCode(201)
                .body("id", not(blankOrNullString()))
                .extract();

        UUID accountId = UUID.fromString(response.path("id"));
        trackAccount(accountId);

        assertThat(URI.create(response.header("Location")).getPath())
                .isEqualTo("/accounts/" + accountId);
        assertThat(response.jsonPath().getMap("$")).containsOnlyKeys("id");
        assertThat(response.cookies()).doesNotContainKey("refreshToken");
        assertThat(accountExists(accountId)).isTrue();
        assertThat(count("SELECT COUNT(*) FROM account_roles WHERE account_id = ?", accountId)).isZero();
        assertThat(count("SELECT COUNT(*) FROM refresh_tokens WHERE account_id = ?", accountId)).isZero();

        Map<String, Object> storedAccount = jdbcTemplate.queryForMap(
                "SELECT email, password_hash, display_name FROM accounts WHERE id = ?",
                accountId
        );
        assertThat(storedAccount.get("email")).isEqualTo(email);
        assertThat(storedAccount.get("display_name")).isEqualTo("Eduardo");
        assertThat(storedAccount.get("password_hash").toString())
                .startsWith("{pbkdf2}")
                .isNotEqualTo(rawPassword);
        assertThat(passwordEncoder.matches(rawPassword, storedAccount.get("password_hash").toString())).isTrue();
    }

    @ParameterizedTest(name = "password length {0} -> HTTP {1}")
    @MethodSource("passwordBoundaries")
    @DisplayName("REQ-AUTH-003 - BVA - password length -> registration boundary")
    void passwordLengthShouldRespectRegistrationBoundary(int length, int expectedStatus) {
        ExtractableResponse<Response> response = jsonRequest()
                .body(registerPayload(uniqueEmail("password-" + length), "a".repeat(length), "Password Boundary"))
                .post("/auth/register")
                .then()
                .extract();

        trackCreatedAccount(response);
        assertThat(response.statusCode()).isEqualTo(expectedStatus);
    }

    private static Stream<Arguments> passwordBoundaries() {
        return Stream.of(
                Arguments.of(7, 400),
                Arguments.of(8, 201),
                Arguments.of(128, 201),
                Arguments.of(129, 400)
        );
    }

    @ParameterizedTest(name = "displayName length {0} -> HTTP {1}")
    @MethodSource("displayNameBoundaries")
    @DisplayName("REQ-AUTH-002 - BVA - trimmed displayName length -> registration boundary")
    void trimmedDisplayNameLengthShouldRespectRegistrationBoundary(int length, int expectedStatus) {
        String displayName = length == 0 ? "   " : "  " + "a".repeat(length) + "  ";

        ExtractableResponse<Response> response = jsonRequest()
                .body(registerPayload(uniqueEmail("display-" + length), TEST_PASSWORD, displayName))
                .post("/auth/register")
                .then()
                .extract();

        trackCreatedAccount(response);
        assertThat(response.statusCode()).isEqualTo(expectedStatus);
        if (expectedStatus == 201) {
            assertThat(storedDisplayName(UUID.fromString(response.path("id"))))
                    .hasSize(length)
                    .doesNotStartWith(" ")
                    .doesNotEndWith(" ");
        }
    }

    private static Stream<Arguments> displayNameBoundaries() {
        return Stream.of(
                Arguments.of(0, 400),
                Arguments.of(1, 201),
                Arguments.of(50, 201),
                Arguments.of(51, 400)
        );
    }

    @ParameterizedTest
    @MethodSource("requiredRegistrationFields")
    @DisplayName("REQ-AUTH-002 - EP - missing required registration field -> HTTP 400")
    void missingRequiredRegistrationFieldShouldReturnBadRequest(String missingField) {
        Map<String, Object> payload = new HashMap<>(
                registerPayload(uniqueEmail("missing-" + missingField), TEST_PASSWORD, "Required Field")
        );
        payload.remove(missingField);

        jsonRequest()
                .body(payload)
                .post("/auth/register")
                .then()
                .statusCode(400);
    }

    private static Stream<String> requiredRegistrationFields() {
        return Stream.of("email", "password", "displayName");
    }

    @Test
    @DisplayName("REQ-AUTH-004 - duplicate normalized GamEmail -> HTTP 409 without Account disclosure")
    void duplicateNormalizedEmailShouldReturnConflictWithoutAccountDisclosure() {
        String email = uniqueEmail("duplicate");
        UUID existingAccountId = registerAccount(email, TEST_PASSWORD, "Original Account");

        ExtractableResponse<Response> response = jsonRequest()
                .body(registerPayload(email.toUpperCase(), TEST_PASSWORD, "Duplicate Account"))
                .post("/auth/register")
                .then()
                .statusCode(409)
                .extract();

        assertThat(response.asString()).doesNotContain(existingAccountId.toString());
        assertThat(count("SELECT COUNT(*) FROM accounts WHERE lower(email) = lower(?)", email)).isEqualTo(1);
    }

    @Test
    @DisplayName("REQ-AUTH-004 - concurrent duplicate normalized registration -> one creation and one conflict")
    void concurrentDuplicateRegistrationShouldReturnOneCreatedAndOneConflict() throws Exception {
        String email = uniqueEmail("concurrent-duplicate");
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            List<Future<ExtractableResponse<Response>>> attempts = List.of(
                    submitConcurrentRegistration(executor, ready, start, email),
                    submitConcurrentRegistration(executor, ready, start, email.toUpperCase())
            );

            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<ExtractableResponse<Response>> responses = attempts.stream()
                    .map(future -> getRegistrationResponse(future))
                    .toList();

            assertThat(responses)
                    .extracting(ExtractableResponse::statusCode)
                    .containsExactlyInAnyOrder(201, 409);
            responses.stream()
                    .filter(response -> response.statusCode() == 201)
                    .forEach(this::trackCreatedAccount);
        } finally {
            executor.shutdownNow();
        }
    }

    private Future<ExtractableResponse<Response>> submitConcurrentRegistration(
            ExecutorService executor,
            CountDownLatch ready,
            CountDownLatch start,
            String email
    ) {
        return executor.submit(() -> {
            ready.countDown();
            assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
            return jsonRequest()
                    .body(registerPayload(email, TEST_PASSWORD, "Concurrent Registration"))
                    .post("/auth/register")
                    .then()
                    .extract();
        });
    }

    private ExtractableResponse<Response> getRegistrationResponse(
            Future<ExtractableResponse<Response>> future
    ) {
        try {
            return future.get(15, TimeUnit.SECONDS);
        } catch (Exception exception) {
            throw new AssertionError("Concurrent registration request failed", exception);
        }
    }

    @Test
    @DisplayName("REQ-AUTH-006 and REQ-AUTH-007 - normalized GamEmail login -> access token body and refresh cookie only")
    void normalizedEmailLoginShouldReturnAccessTokenAndCookieOnly() {
        String email = uniqueEmail("login");
        UUID accountId = registerAccount(email, TEST_PASSWORD, "Login Account");

        ExtractableResponse<Response> response = login(email.toUpperCase(), TEST_PASSWORD);
        String accessToken = response.path("token");
        String refreshToken = response.cookie("refreshToken");

        assertThat(response.jsonPath().getMap("$")).containsOnlyKeys("token");
        assertThat(accessToken).isNotBlank();
        assertThat(jwtService.extractUsername(accessToken)).isEqualTo(accountId.toString());
        assertThatCode(() -> UUID.fromString(refreshToken)).doesNotThrowAnyException();
        assertThat(refreshTokenExists(refreshToken)).isTrue();
        assertThat(response.asString()).doesNotContain(refreshToken);
    }

    @Test
    @DisplayName("REQ-AUTH-006 - unknown email and wrong password -> identical generic failure")
    void unknownEmailAndWrongPasswordShouldReturnTheSameGenericFailure() {
        String email = uniqueEmail("login-failure");
        registerAccount(email, TEST_PASSWORD, "Login Failure Account");

        ExtractableResponse<Response> wrongPassword = loginAttempt(email, "wrong-password");
        ExtractableResponse<Response> unknownEmail = loginAttempt(uniqueEmail("unknown"), "wrong-password");

        assertThat(wrongPassword.statusCode()).isEqualTo(unknownEmail.statusCode());
        assertThat(stableError(wrongPassword)).isEqualTo(stableError(unknownEmail));
        assertThat(wrongPassword.asString()).doesNotContain(email);
    }

    @Test
    @DisplayName("REQ-AUTH-008 - repeated login -> distinct concurrently active sessions")
    void repeatedLoginShouldCreateDistinctActiveSessions() {
        String email = uniqueEmail("sessions");
        UUID accountId = registerAccount(email, TEST_PASSWORD, "Multiple Sessions");

        String firstRefreshToken = login(email, TEST_PASSWORD).cookie("refreshToken");
        String secondRefreshToken = login(email, TEST_PASSWORD).cookie("refreshToken");

        assertThat(firstRefreshToken).isNotEqualTo(secondRefreshToken);
        assertThat(refreshTokenExists(firstRefreshToken)).isTrue();
        assertThat(refreshTokenExists(secondRefreshToken)).isTrue();
        assertThat(count("SELECT COUNT(*) FROM refresh_tokens WHERE account_id = ?", accountId)).isEqualTo(2);
    }

    @Test
    @DisplayName("REQ-AUTH-009 - access token -> Account UUID subject and no role authorities")
    void accessTokenShouldIdentifyAccountWithoutCarryingRoleAuthorities() {
        AuthSession session = registerAndLogin("COORD");

        Claims claims = jwtService.extractClaim(session.accessToken(), claimsValue -> claimsValue);

        assertThat(claims.getSubject()).isEqualTo(session.accountId().toString());
        assertThat(claims).doesNotContainKeys("roles", "role", "authorities", "permissions");
        assertThat(claims.values()).noneMatch(value -> value.toString().startsWith("ROLE_"));
    }

    @Test
    @DisplayName("REQ-AUTH-009 - existing access token -> permissions resolved from current RBAC state")
    void existingAccessTokenShouldUseCurrentPermissionState() {
        AuthSession session = registerAndLogin(null);
        UUID targetAccountId = registerAccount(uniqueEmail("rbac-target"), TEST_PASSWORD, "RBAC Target");
        grantRole(session.accountId(), "COORD");

        authenticatedJsonRequest(session)
                .get("/accounts/{id}", targetAccountId)
                .then()
                .statusCode(200);
    }

    @Test
    @DisplayName("REQ-AUTH-007, REQ-AUTH-010, REQ-AUTH-011, REQ-BROWSER-AUTH-002, and REQ-WEB-002 - production login cookie -> same-origin contract")
    void loginTokensAndCookieShouldUseConfiguredSecureContract() {
        String email = uniqueEmail("token-contract");
        registerAccount(email, TEST_PASSWORD, "Token Contract");
        Instant beforeLogin = Instant.now();
        ExtractableResponse<Response> response = login(email, TEST_PASSWORD);
        Instant afterLogin = Instant.now();

        String accessToken = response.path("token");
        UUID refreshToken = UUID.fromString(response.cookie("refreshToken"));
        Claims claims = jwtService.extractClaim(accessToken, claimsValue -> claimsValue);
        long actualAccessLifetimeMs = claims.getExpiration().getTime() - claims.getIssuedAt().getTime();
        Map<String, Object> refreshRow = jdbcTemplate.queryForMap(
                "SELECT id, token FROM refresh_tokens WHERE token = ?",
                refreshToken
        );
        Instant storedRefreshExpiry = jdbcTemplate.queryForObject(
                "SELECT expiry_date FROM refresh_tokens WHERE token = ?",
                (resultSet, rowNumber) -> resultSet.getTimestamp(1).toInstant(),
                refreshToken
        );

        assertThat(actualAccessLifetimeMs).isEqualTo(accessTokenLifetimeMs);
        assertThat(accessTokenLifetimeMs).isLessThan(refreshTokenLifetimeMs);
        assertThat(storedRefreshExpiry).isBetween(
                beforeLogin.plusMillis(refreshTokenLifetimeMs),
                afterLogin.plusMillis(refreshTokenLifetimeMs)
        );
        assertThat(refreshToken.version()).isEqualTo(4);
        assertThat(refreshRow.get("id")).isNotEqualTo(refreshRow.get("token"));
        assertThat(((UUID) refreshRow.get("id")).version()).isEqualTo(7);
        assertThat(response.header("Set-Cookie"))
                .contains(
                        "refreshToken=",
                        "HttpOnly",
                        "SameSite=Lax",
                        "Path=/api/auth",
                        "Max-Age=" + refreshTokenLifetimeMs / 1000
                )
                .contains("Secure")
                .doesNotContain("Domain=");
    }

    @Test
    @DisplayName("REQ-AUTH-012 - credentialed CORS -> trusted origin only")
    void credentialedCorsShouldAllowOnlyTheTrustedOrigin() {
        ExtractableResponse<Response> trusted = preflight(TRUSTED_ORIGIN);
        ExtractableResponse<Response> untrusted = preflight(UNTRUSTED_ORIGIN);

        assertThat(trusted.statusCode()).isBetween(200, 299);
        assertThat(trusted.header("Access-Control-Allow-Origin")).isEqualTo(TRUSTED_ORIGIN);
        assertThat(trusted.header("Access-Control-Allow-Credentials")).isEqualTo("true");
        assertThat(trusted.header("Access-Control-Allow-Headers")).contains("X-XSRF-TOKEN");
        assertThat(untrusted.header("Access-Control-Allow-Origin")).isNull();
        assertThat(untrusted.header("Access-Control-Allow-Credentials")).isNull();
    }

    @Test
    @DisplayName("REQ-AUTH-013 - cross-site login, refresh, and logout without CSRF proof -> rejected")
    void cookieAuthenticatedRequestsWithoutCsrfProofShouldBeRejected() {
        String email = uniqueEmail("csrf");
        registerAccount(email, TEST_PASSWORD, "CSRF Account");
        String refreshToken = login(email, TEST_PASSWORD).cookie("refreshToken");

        ExtractableResponse<Response> loginResponse = jsonRequest()
                .header("Origin", TRUSTED_ORIGIN)
                .body(loginPayload(email, TEST_PASSWORD))
                .post("/auth/login")
                .then()
                .extract();
        ExtractableResponse<Response> refreshResponse = jsonRequest()
                .header("Origin", TRUSTED_ORIGIN)
                .cookie("refreshToken", refreshToken)
                .post("/auth/refresh")
                .then()
                .extract();
        ExtractableResponse<Response> logoutResponse = jsonRequest()
                .header("Origin", TRUSTED_ORIGIN)
                .cookie("refreshToken", refreshToken)
                .post("/auth/logout")
                .then()
                .extract();

        assertThat(List.of(
                loginResponse.statusCode(),
                refreshResponse.statusCode(),
                logoutResponse.statusCode()
        )).allSatisfy(status -> assertThat(status / 100).isNotEqualTo(2));
    }

    @Test
    @DisplayName("REQ-AUTH-014 - body, query, and custom header refresh tokens -> not consumed")
    void refreshTokenShouldBeConsumedOnlyFromTheCookie() {
        String email = uniqueEmail("refresh-input");
        registerAccount(email, TEST_PASSWORD, "Refresh Input");
        String refreshToken = login(email, TEST_PASSWORD).cookie("refreshToken");

        List<ExtractableResponse<Response>> responses = List.of(
                jsonRequest().body(Map.of("refreshToken", refreshToken)).post("/auth/refresh").then().extract(),
                jsonRequest().queryParam("refreshToken", refreshToken).post("/auth/refresh").then().extract(),
                jsonRequest().header("refreshToken", refreshToken).post("/auth/refresh").then().extract()
        );

        assertThat(responses).allSatisfy(response ->
                assertThat(response.statusCode() / 100).isNotEqualTo(2));
        assertThat(refreshTokenExists(refreshToken)).isTrue();
    }

    @Test
    @DisplayName("REQ-AUTH-017 - body, query, and custom header logout tokens -> stored session unaffected")
    void logoutShouldConsumeRefreshTokenOnlyFromTheCookie() {
        String email = uniqueEmail("logout-input");
        registerAccount(email, TEST_PASSWORD, "Logout Input");
        String refreshToken = login(email, TEST_PASSWORD).cookie("refreshToken");

        jsonRequest().body(Map.of("refreshToken", refreshToken)).post("/auth/logout");
        jsonRequest().queryParam("refreshToken", refreshToken).post("/auth/logout");
        jsonRequest().header("refreshToken", refreshToken).post("/auth/logout");

        assertThat(refreshTokenExists(refreshToken)).isTrue();
    }

    @Test
    @DisplayName("REQ-AUTH-019 - auth routes -> no bearer token required; protected route -> HTTP 401")
    void authRoutesShouldBePublicWhileAccountRoutesRemainProtected() {
        String csrfToken = csrfToken();
        ExtractableResponse<Response> refresh = jsonRequest()
                .cookie("XSRF-TOKEN", csrfToken)
                .header("X-XSRF-TOKEN", csrfToken)
                .post("/auth/refresh")
                .then()
                .extract();
        ExtractableResponse<Response> logout = jsonRequest()
                .cookie("XSRF-TOKEN", csrfToken)
                .header("X-XSRF-TOKEN", csrfToken)
                .post("/auth/logout")
                .then()
                .extract();

        assertThat(refresh.statusCode()).isEqualTo(403);
        assertThat(logout.statusCode()).isEqualTo(200);
        jsonRequest()
                .get("/accounts/{id}", UUID.randomUUID())
                .then()
                .statusCode(401);
    }

    @Test
    @DisplayName("REQ-AUTH-020 - login success and failure -> no custom activity-log events")
    void loginShouldNotCreateActivityLogEvents() {
        String email = uniqueEmail("audit");
        registerAccount(email, TEST_PASSWORD, "Audit Boundary");
        long before = count("SELECT COUNT(*) FROM activity_logs");

        login(email, TEST_PASSWORD);
        loginAttempt(email, "wrong-password");

        assertThat(count("SELECT COUNT(*) FROM activity_logs")).isEqualTo(before);
    }

    private ExtractableResponse<Response> loginAttempt(String email, String password) {
        return jsonRequest()
                .body(loginPayload(email, password))
                .post("/auth/login")
                .then()
                .extract();
    }

    private ExtractableResponse<Response> preflight(String origin) {
        return jsonRequest()
                .header("Origin", origin)
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "X-XSRF-TOKEN,Content-Type")
                .options("/auth/refresh")
                .then()
                .extract();
    }

    private String csrfToken() {
        return jsonRequest()
                .post("/auth/refresh")
                .then()
                .extract()
                .cookie("XSRF-TOKEN");
    }

    private Map<String, Object> stableError(ExtractableResponse<Response> response) {
        Map<String, Object> error = new LinkedHashMap<>(response.jsonPath().getMap("$"));
        error.remove("timestamp");
        return error;
    }

    private void trackCreatedAccount(ExtractableResponse<Response> response) {
        if (response.statusCode() == 201 && response.path("id") != null) {
            trackAccount(UUID.fromString(response.path("id")));
        }
    }

    private String storedDisplayName(UUID accountId) {
        return jdbcTemplate.queryForObject(
                "SELECT display_name FROM accounts WHERE id = ?",
                String.class,
                accountId
        );
    }

    private long count(String sql, Object... arguments) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class, arguments);
        return value == null ? 0 : value;
    }

    private String uniqueEmail(String prefix) {
        return prefix + "-" + UUID.randomUUID() + "@example.com";
    }
}
