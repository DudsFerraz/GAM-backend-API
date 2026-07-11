package br.org.gam.api.security.refreshtoken.application;

import br.org.gam.api.account.application.useCases.registerAccount.RegisterAccount;
import br.org.gam.api.account.application.useCases.registerAccount.RegisterAccountDTO;
import br.org.gam.api.security.application.InvalidTokenFormatException;
import br.org.gam.api.security.application.TokensDTO;
import br.org.gam.api.security.jwt.JwtService;
import br.org.gam.api.security.refreshtoken.persistence.RefreshTokenEntity;
import br.org.gam.api.security.refreshtoken.persistence.RefreshTokenRepository;
import br.org.gam.api.shared.domain.GamEmail;
import br.org.gam.api.shared.persistence.UUIDGenerator;
import br.org.gam.api.testing.annotation.FunctionalTest;
import br.org.gam.api.testing.annotation.IntegrationTest;
import br.org.gam.api.testing.annotation.PersistenceTest;
import br.org.gam.api.testing.annotation.SecurityTest;
import br.org.gam.api.testing.integration.PostgreSQLIntegrationTest;
import java.time.Instant;
import java.sql.Timestamp;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.catchThrowable;

@FunctionalTest
@IntegrationTest
@PersistenceTest
@SecurityTest
@DisplayName("Integration - Refresh-token sessions")
class RefreshTokenServiceIT extends PostgreSQLIntegrationTest {

    private static final String PASSWORD = "Refresh-service-password";

    private final Set<UUID> accountIds = new LinkedHashSet<>();

    @Autowired
    private RegisterAccount registerAccount;

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanup() {
        for (UUID accountId : accountIds) {
            jdbcTemplate.update("DELETE FROM refresh_tokens WHERE account_id = ?", accountId);
            jdbcTemplate.update("DELETE FROM account_roles WHERE account_id = ?", accountId);
            jdbcTemplate.update("DELETE FROM accounts WHERE id = ?", accountId);
        }
        accountIds.clear();
    }

    @Test
    @DisplayName("REQ-AUTH-015 - valid refresh token -> hard-deleted, replaced, and bound to the same Account")
    void validRefreshTokenShouldRotateOnceForTheSameAccount() {
        RegisteredAccount account = registerUniqueAccount("rotation");
        UUID oldToken = refreshTokenService.createRefreshToken(account.email());

        TokensDTO refreshed = refreshTokenService.refresh(oldToken.toString());

        assertThat(refreshed.refreshToken()).isNotEqualTo(oldToken);
        assertThat(refreshTokenRepository.findByToken(oldToken)).isEmpty();
        assertThat(refreshTokenRepository.findByToken(refreshed.refreshToken()))
                .get()
                .extracting(token -> token.getAccount().getId())
                .isEqualTo(account.id());
        assertThat(jwtService.extractUsername(refreshed.accessToken())).isEqualTo(account.id().toString());

        Throwable reuseFailure = catchThrowable(() -> refreshTokenService.refresh(oldToken.toString()));
        assertThat(reuseFailure).isNotNull();
    }

    @Test
    @DisplayName("REQ-AUTH-015 - concurrent refresh requests -> one rotation succeeds")
    void concurrentRefreshRequestsShouldConsumeRefreshTokenAtMostOnce() throws Exception {
        RegisteredAccount account = registerUniqueAccount("concurrent");
        UUID oldToken = refreshTokenService.createRefreshToken(account.email());
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try {
            Future<Object> firstAttempt = executor.submit(refreshAttempt(oldToken, ready, start));
            Future<Object> secondAttempt = executor.submit(refreshAttempt(oldToken, ready, start));

            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<Object> outcomes = List.of(
                    firstAttempt.get(30, TimeUnit.SECONDS),
                    secondAttempt.get(30, TimeUnit.SECONDS)
            );

            assertThat(outcomes.stream().filter(TokensDTO.class::isInstance).count()).isEqualTo(1);
            assertThat(outcomes.stream().filter(Throwable.class::isInstance).count()).isEqualTo(1);
            assertThat(countRefreshTokens(account.id())).isEqualTo(1);
            assertThat(refreshTokenRepository.findByToken(oldToken)).isEmpty();
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("REQ-AUTH-011 - non-v4 UUID-shaped secret -> invalid refresh-token format")
    void nonV4RefreshTokenShouldBeRejected() {
        UUID resourceId = UUIDGenerator.generateUUIDV7();

        Throwable failure = catchThrowable(() -> refreshTokenService.refresh(resourceId.toString()));

        assertThat(failure).isInstanceOf(InvalidTokenFormatException.class);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "not-a-uuid"})
    @DisplayName("REQ-AUTH-018 - missing or malformed logout token -> idempotent success")
    void missingOrMalformedLogoutTokenShouldRemainIdempotent(String token) {
        assertThatCode(() -> refreshTokenService.logout(token)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("REQ-AUTH-008 and REQ-AUTH-018 - logout one session repeatedly -> only that session is deleted")
    void logoutShouldBeIdempotentAndAffectOnlyThePresentedSession() {
        RegisteredAccount account = registerUniqueAccount("logout");
        UUID firstToken = refreshTokenService.createRefreshToken(account.email());
        UUID secondToken = refreshTokenService.createRefreshToken(account.email());

        refreshTokenService.logout(firstToken.toString());
        refreshTokenService.logout(firstToken.toString());

        assertThat(refreshTokenRepository.findByToken(firstToken)).isEmpty();
        assertThat(refreshTokenRepository.findByToken(secondToken)).isPresent();
    }

    @Test
    @DisplayName("REQ-AUTH-016 - expired refresh token -> hard-deleted when failure is detected")
    void expiredRefreshTokenShouldBeHardDeletedWhenDetected() {
        RegisteredAccount account = registerUniqueAccount("expired");
        UUID token = refreshTokenService.createRefreshToken(account.email());
        jdbcTemplate.update(
                "UPDATE refresh_tokens SET expiry_date = ? WHERE token = ?",
                Timestamp.from(Instant.now().minusSeconds(1)),
                token
        );

        Throwable failure = catchThrowable(() -> refreshTokenService.refresh(token.toString()));

        assertThat(failure).isNotNull();
        assertThat(refreshTokenRepository.findByToken(token)).isEmpty();
    }

    @Test
    @DisplayName("REQ-AUTH-011 - refresh secret -> UUID v4 distinct from UUID v7 row identity")
    void refreshSecretShouldBeDistinctFromItsPersistedResourceIdentity() {
        RegisteredAccount account = registerUniqueAccount("identity");
        UUID secret = refreshTokenService.createRefreshToken(account.email());
        RefreshTokenEntity row = refreshTokenRepository.findByToken(secret).orElseThrow();

        assertThat(secret.version()).isEqualTo(4);
        assertThat(row.getId().version()).isEqualTo(7);
        assertThat(row.getId()).isNotEqualTo(secret);
    }

    @Test
    @DisplayName("REQ-AUTH-020 - refresh rotation and logout -> no custom activity-log events")
    void refreshAndLogoutShouldNotCreateActivityLogEvents() {
        RegisteredAccount account = registerUniqueAccount("audit");
        UUID token = refreshTokenService.createRefreshToken(account.email());
        long before = countActivityLogs();

        TokensDTO refreshed = refreshTokenService.refresh(token.toString());
        refreshTokenService.logout(refreshed.refreshToken().toString());

        assertThat(countActivityLogs()).isEqualTo(before);
    }

    private RegisteredAccount registerUniqueAccount(String prefix) {
        GamEmail email = GamEmail.of(prefix + "-" + UUID.randomUUID() + "@example.com");
        UUID id = registerAccount.register(new RegisterAccountDTO(email, PASSWORD, "Refresh Token Test")).id();
        accountIds.add(id);
        return new RegisteredAccount(id, email);
    }

    private long countActivityLogs() {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM activity_logs", Long.class);
        return count == null ? 0 : count;
    }

    private long countRefreshTokens(UUID accountId) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM refresh_tokens WHERE account_id = ?",
                Long.class,
                accountId
        );
        return count == null ? 0 : count;
    }

    private java.util.concurrent.Callable<Object> refreshAttempt(
            UUID token,
            CountDownLatch ready,
            CountDownLatch start
    ) {
        return () -> {
            ready.countDown();
            if (!start.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Concurrent refresh test did not start on time.");
            }

            try {
                return refreshTokenService.refresh(token.toString());
            } catch (Throwable failure) {
                return failure;
            }
        };
    }

    private record RegisteredAccount(UUID id, GamEmail email) {
    }
}
