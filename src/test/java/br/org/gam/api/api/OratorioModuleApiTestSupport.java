package br.org.gam.api.api;

import io.restassured.response.ExtractableResponse;
import io.restassured.response.Response;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

@Import(OratorioModuleApiTestSupport.TestClockConfiguration.class)
abstract class OratorioModuleApiTestSupport extends MemberApiTestSupport {

    protected static final String ORATORIO_REASON = "  Correcting the Oratorio record  ";

    private final List<UUID> oratorioIds = new ArrayList<>();
    private final List<UUID> oratorianoIds = new ArrayList<>();

    @AfterEach
    void cleanupOratorioModuleFixtures() {
        if (tableExists("oratoriano_form_attachments")) {
            jdbcTemplate.update("DELETE FROM oratoriano_form_attachments");
        }
        if (tableExists("oratoriano_form_print_snapshots")) {
            jdbcTemplate.update("DELETE FROM oratoriano_form_print_snapshots");
        }
        if (tableExists("oratoriano_additional_forms")) {
            jdbcTemplate.update("DELETE FROM oratoriano_additional_forms");
        }
        if (tableExists("oratoriano_attendances")) {
            jdbcTemplate.update("DELETE FROM oratoriano_attendances");
        }
        if (tableExists("presences")) {
            for (UUID oratorioId : oratorioIds) {
                jdbcTemplate.update("DELETE FROM presences WHERE event_id = ?", oratorioId);
            }
        }
        if (tableExists("oratorio_team_assignments")) {
            jdbcTemplate.update("DELETE FROM oratorio_team_assignments");
        }
        if (tableExists("oratorios")) {
            for (UUID oratorioId : oratorioIds) {
                jdbcTemplate.update("DELETE FROM oratorios WHERE id = ?", oratorioId);
            }
        }
        if (tableExists("oratorianos")) {
            for (UUID oratorianoId : oratorianoIds) {
                jdbcTemplate.update("DELETE FROM oratorianos WHERE id = ?", oratorianoId);
            }
        }
        oratorioIds.clear();
        oratorianoIds.clear();
    }

    protected AuthSession sudoSession() {
        return newSession("SUDO");
    }

    protected UUID createOratoriano(AuthSession caller, String firstName, String surname) {
        ExtractableResponse<Response> response = authenticatedJsonRequest(caller)
                .body(oratorianoRegistrationPayload(firstName, surname))
                .post("/oratorianos")
                .then()
                .statusCode(201)
                .extract();
        UUID id = UUID.fromString(response.path("id"));
        trackOratoriano(id);
        return id;
    }

    protected UUID createOratorio(AuthSession caller, LocalDate localDate) {
        ExtractableResponse<Response> response = authenticatedJsonRequest(caller)
                .body(Map.of("date", localDate.toString()))
                .post("/oratorios")
                .then()
                .statusCode(201)
                .extract();
        UUID id = UUID.fromString(response.path("id"));
        trackOratorio(id);
        return id;
    }

    protected UUID createActiveMember(AuthSession caller, String displayName) {
        UUID accountId = newAccount(displayName);
        return registerMember(caller, accountId);
    }

    protected void trackOratoriano(UUID id) {
        oratorianoIds.add(id);
    }

    protected void trackOratorio(UUID id) {
        oratorioIds.add(id);
        trackEvent(id);
    }

    protected long activityCountForTarget(UUID targetId) {
        return Objects.requireNonNull(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM activity_logs WHERE target_id = ?",
                Long.class,
                targetId
        ), "Expected activity count");
    }

    protected long activityCountForActionAndTarget(String action, UUID targetId) {
        return Objects.requireNonNull(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM activity_logs WHERE action = ? AND target_id = ?",
                Long.class,
                action,
                targetId
        ), "Expected activity count");
    }

    protected static Map<String, Object> oratorianoRegistrationPayload(String firstName, String surname) {
        return Map.of("firstName", firstName, "surname", surname);
    }

    protected static Map<String, Object> oratorianoReplacementPayload(
            String firstName,
            String surname,
            String birthDate,
            String phoneNumber,
            String reason
    ) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("firstName", firstName);
        payload.put("surname", surname);
        payload.put("birthDate", birthDate);
        payload.put("phoneNumber", phoneNumber);
        payload.put("reason", reason);
        return payload;
    }

    protected static Map<String, Object> nullableReasonPayload(String reason) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("reason", reason);
        return payload;
    }

    protected boolean tableExists(String tableName) {
        Boolean exists = jdbcTemplate.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM information_schema.tables "
                        + "WHERE table_schema = 'public' AND table_name = ?)",
                Boolean.class,
                tableName
        );
        return Boolean.TRUE.equals(exists);
    }

    protected void setCurrentInstant(Instant instant) {
        TestClockConfiguration.SCRIPTED_INSTANTS.clear();
        TestClockConfiguration.CURRENT_INSTANT.set(instant);
    }

    protected void setCurrentInstants(Instant... instants) {
        if (instants.length == 0) {
            throw new IllegalArgumentException("At least one instant is required");
        }
        TestClockConfiguration.SCRIPTED_INSTANTS.clear();
        for (Instant instant : instants) {
            TestClockConfiguration.SCRIPTED_INSTANTS.add(instant);
        }
        TestClockConfiguration.CURRENT_INSTANT.set(instants[instants.length - 1]);
    }

    @TestConfiguration
    static class TestClockConfiguration {

        private static final AtomicReference<Instant> CURRENT_INSTANT =
                new AtomicReference<>(Instant.parse("2026-07-25T12:00:00Z"));
        private static final ConcurrentLinkedQueue<Instant> SCRIPTED_INSTANTS =
                new ConcurrentLinkedQueue<>();

        @Bean
        @Primary
        Clock oratorioTestClock() {
            return new Clock() {
                @Override
                public ZoneId getZone() {
                    return ZoneId.of("America/Sao_Paulo");
                }

                @Override
                public Clock withZone(ZoneId zone) {
                    return Clock.fixed(CURRENT_INSTANT.get(), zone);
                }

                @Override
                public Instant instant() {
                    Instant scripted = SCRIPTED_INSTANTS.poll();
                    return scripted == null ? CURRENT_INSTANT.get() : scripted;
                }
            };
        }
    }
}
