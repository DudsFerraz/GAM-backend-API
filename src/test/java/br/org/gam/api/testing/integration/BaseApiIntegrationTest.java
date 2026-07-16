package br.org.gam.api.testing.integration;

import br.org.gam.api.shared.domain.GamEmail;
import br.org.gam.api.account.persistence.AccountEntity;
import br.org.gam.api.account.persistence.AccountRepository;
import br.org.gam.api.event.persistence.EventRepository;
import br.org.gam.api.location.persistence.LocationRepository;
import br.org.gam.api.rbac.accountRole.persistence.AccountRoleEntity;
import br.org.gam.api.rbac.accountRole.persistence.AccountRoleRepository;
import br.org.gam.api.rbac.permission.domain.PermissionEnum;
import br.org.gam.api.rbac.permission.persistence.PermissionEntity;
import br.org.gam.api.rbac.permission.persistence.PermissionRepository;
import br.org.gam.api.rbac.role.persistence.RoleEntity;
import br.org.gam.api.rbac.role.persistence.RoleRepository;
import br.org.gam.api.security.refreshtoken.persistence.RefreshTokenRepository;
import br.org.gam.api.shared.persistence.UUIDGenerator;
import br.org.gam.api.testing.annotation.ApiTest;
import io.restassured.http.ContentType;
import io.restassured.response.ExtractableResponse;
import io.restassured.response.Response;
import io.restassured.RestAssured;
import io.restassured.specification.RequestSpecification;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class BaseApiIntegrationTest {

    protected static final String TEST_PASSWORD = "ApiTest-password-123";
    protected static final String TRUSTED_ORIGIN = "https://test.example";
    protected static final String UNTRUSTED_ORIGIN = "https://untrusted.example";
    private static final String TEST_JWT_SECRET = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";

    private static final DockerImageName POSTGRES_IMAGE = DockerImageName.parse("postgres:18-alpine");
    private static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(POSTGRES_IMAGE);

    private final Set<UUID> createdAccountIds = new LinkedHashSet<>();
    private final List<UUID> createdLocationIds = new ArrayList<>();
    private final List<UUID> createdEventIds = new ArrayList<>();

    @LocalServerPort
    private int port;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private AccountRoleRepository accountRoleRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private LocationRepository locationRepository;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private PermissionRepository permissionRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    static {
        postgres.start();
    }

    @DynamicPropertySource
    static void configurePostgreSQL(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("jwt.secret-key", () -> TEST_JWT_SECRET);
    }

    @BeforeEach
    void configureRestAssured() {
        RestAssured.baseURI = "http://localhost";
        RestAssured.port = port;
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
    }

    @AfterEach
    void cleanupApiFixtures() {
        jdbcTemplate.update("DELETE FROM activity_logs");

        for (int i = createdEventIds.size() - 1; i >= 0; i--) {
            jdbcTemplate.update("DELETE FROM events WHERE id = ?", createdEventIds.get(i));
        }

        for (int i = createdLocationIds.size() - 1; i >= 0; i--) {
            jdbcTemplate.update("DELETE FROM locations WHERE id = ?", createdLocationIds.get(i));
        }

        for (UUID accountId : createdAccountIds) {
            jdbcTemplate.update("DELETE FROM refresh_tokens WHERE account_id = ?", accountId);
            jdbcTemplate.update("DELETE FROM account_roles WHERE account_id = ?", accountId);
            jdbcTemplate.update("DELETE FROM accounts WHERE id = ?", accountId);
        }

        createdEventIds.clear();
        createdLocationIds.clear();
        createdAccountIds.clear();
        RestAssured.reset();
    }

    protected RequestSpecification jsonRequest() {
        return RestAssured.given()
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON);
    }

    protected RequestSpecification authenticatedJsonRequest(AuthSession session) {
        return jsonRequest()
                .header("Authorization", "Bearer " + session.accessToken());
    }

    protected AuthSession registerAndLogin(String roleName) {
        String suffix = UUID.randomUUID().toString();
        String email = "api-" + suffix + "@example.com";
        String displayName = "API " + roleName;
        UUID accountId = registerAccount(email, TEST_PASSWORD, displayName);

        if (roleName != null) {
            grantRole(accountId, roleName);
        }

        ExtractableResponse<Response> loginResponse = login(email, TEST_PASSWORD);
        return new AuthSession(
                accountId,
                email,
                TEST_PASSWORD,
                loginResponse.path("token"),
                loginResponse.cookie("refreshToken")
        );
    }

    protected UUID registerAccount(String email, String password, String displayName) {
        ExtractableResponse<Response> response = jsonRequest()
                .body(registerPayload(email, password, displayName))
                .post("/auth/register")
                .then()
                .statusCode(201)
                .extract();

        UUID accountId = UUID.fromString(response.path("id"));
        createdAccountIds.add(accountId);
        return accountId;
    }

    protected ExtractableResponse<Response> login(String email, String password) {
        Map<String, Object> payload = loginPayload(email, password);
        String csrfToken = csrfBootstrap()
                .cookie("XSRF-TOKEN");

        return csrfRequest(csrfToken)
                .body(payload)
                .post("/auth/login")
                .then()
                .statusCode(200)
                .extract();
    }

    protected ExtractableResponse<Response> csrfBootstrap() {
        return jsonRequest()
                .get("/auth/csrf")
                .then()
                .statusCode(200)
                .extract();
    }

    protected RequestSpecification csrfRequest(String csrfToken) {
        return csrfRequest(csrfToken, TRUSTED_ORIGIN, null);
    }

    protected RequestSpecification csrfRequest(String csrfToken, String origin, String referer) {
        RequestSpecification request = jsonRequest()
                .cookie("XSRF-TOKEN", csrfToken)
                .header("X-XSRF-TOKEN", csrfToken);
        if (origin != null) {
            request.header("Origin", origin);
        }
        if (referer != null) {
            request.header("Referer", referer);
        }
        return request;
    }

    protected void assertPublicApiLocation(ExtractableResponse<Response> response, String resourcePath) {
        assertThat(response.header("Location")).isEqualTo("/api" + resourcePath);
    }

    protected RequestSpecification withUntrustedForwardingHeaders(RequestSpecification request) {
        return request
                .header("Host", "internal.invalid")
                .header("X-Forwarded-Host", "attacker.invalid")
                .header("X-Forwarded-Proto", "http");
    }

    protected RequestSpecification withCanonicalForwardingHeaders(RequestSpecification request) {
        return request
                .header("Host", "internal.invalid")
                .header("X-Forwarded-Host", "test.example")
                .header("X-Forwarded-Proto", "https")
                .header("X-Forwarded-Port", "443");
    }

    protected void grantRole(UUID accountId, String roleName) {
        AccountEntity account = accountRepository.findById(accountId).orElseThrow();
        RoleEntity role = roleRepository.findByName(roleName).orElseThrow();

        AccountRoleEntity accountRole = new AccountRoleEntity();
        accountRole.setId(UUIDGenerator.generateUUIDV7());
        accountRole.setAccount(account);
        accountRole.setRole(role);

        accountRoleRepository.saveAndFlush(accountRole);
    }

    protected UUID permissionId(PermissionEnum permissionEnum) {
        return permissionId(permissionEnum.getCode());
    }

    protected UUID permissionId(String permissionCode) {
        return permissionRepository.findAll().stream()
                .filter(permission -> permission.getCode().equals(permissionCode))
                .map(PermissionEntity::getId)
                .findFirst()
                .orElseThrow();
    }

    protected Map<String, Object> registerPayload(String email, String password, String displayName) {
        return Map.of(
                "email", email,
                "password", password,
                "displayName", displayName
        );
    }

    protected Map<String, Object> loginPayload(String email, String password) {
        return Map.of(
                "email", email,
                "password", password
        );
    }

    protected Map<String, Object> locationPayload(String name) {
        return Map.of(
                "name", name,
                "street", "Rua API, 123",
                "city", "Sao Paulo",
                "state", "SP",
                "postalCode", "01000-000",
                "countryCode", "BR"
        );
    }

    protected Map<String, Object> eventPayload(String title, UUID locationId, UUID permissionId) {
        return Map.of(
                "title", title,
                "description", "API integration event",
                "locationId", locationId.toString(),
                "requiredPermissionId", permissionId.toString(),
                "beginDate", Instant.now().plusSeconds(3600).toString(),
                "endDate", Instant.now().plusSeconds(7200).toString(),
                "type", "GENERIC"
        );
    }

    protected UUID createLocation(AuthSession session, String name) {
        ExtractableResponse<Response> response = authenticatedJsonRequest(session)
                .body(locationPayload(name))
                .post("/locations")
                .then()
                .statusCode(201)
                .extract();

        UUID locationId = UUID.fromString(response.path("id"));
        createdLocationIds.add(locationId);
        return locationId;
    }

    protected void trackEvent(UUID eventId) {
        createdEventIds.add(eventId);
    }

    protected void trackLocation(UUID locationId) {
        createdLocationIds.add(locationId);
    }

    protected void trackAccount(UUID accountId) {
        createdAccountIds.add(accountId);
    }

    protected void softDeleteAccount(UUID accountId) {
        jdbcTemplate.update("UPDATE accounts SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?", accountId);
    }

    protected void softDeleteAccountRole(UUID accountId, String roleName) {
        jdbcTemplate.update(
                "UPDATE account_roles SET deleted_at = CURRENT_TIMESTAMP "
                        + "WHERE account_id = ? AND role_id = (SELECT id FROM roles WHERE name = ?)",
                accountId,
                roleName
        );
    }

    protected boolean refreshTokenExists(String refreshToken) {
        return refreshTokenRepository.findByToken(UUID.fromString(refreshToken)).isPresent();
    }

    protected boolean accountExists(UUID accountId) {
        return accountRepository.findById(accountId).isPresent();
    }

    protected boolean eventExists(UUID eventId) {
        return eventRepository.findById(eventId).isPresent();
    }

    protected boolean locationExists(UUID locationId) {
        return locationRepository.findById(locationId).isPresent();
    }

    public record AuthSession(
            UUID accountId,
            String email,
            String password,
            String accessToken,
            String refreshToken
    ) {
    }
}
