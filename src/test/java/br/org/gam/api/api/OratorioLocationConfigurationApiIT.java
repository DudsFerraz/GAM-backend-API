package br.org.gam.api.api;

import br.org.gam.api.testing.annotation.ApiTest;
import br.org.gam.api.testing.annotation.FunctionalTest;
import br.org.gam.api.testing.annotation.IntegrationTest;
import io.restassured.response.ExtractableResponse;
import io.restassured.response.Response;
import java.util.Map;
import java.util.UUID;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@ApiTest
@FunctionalTest
@IntegrationTest
@TestPropertySource(properties = "gam.oratorio.location-code=DBA")
@DisplayName("API - Oratorio system location configuration")
class OratorioLocationConfigurationApiIT extends OratorioModuleApiTestSupport {

    @Test
    @DisplayName("REQ-GAM-LOCATION-CATALOG-008 and REQ-ORATORIO-002 - DBA deployment override -> Oratorio references DBA by code")
    void deploymentOverrideShouldSelectCurrentSystemLocationByCode() {
        AuthSession caller = sudoSession();

        ExtractableResponse<Response> response = authenticatedJsonRequest(caller)
                .body(Map.of("date", "2035-08-19"))
                .post("/oratorios")
                .then()
                .extract();

        assertThat(response.statusCode()).isEqualTo(201);
        UUID id = UUID.fromString(response.path("id"));
        trackOratorio(id);
        assertThat(response.<String>path("event.gamLocation.code")).isEqualTo("DBA");
        assertThat(response.<Boolean>path("event.gamLocation.systemManaged")).isTrue();
        assertThat(response.<String>path("event.gamLocation.name")).isEqualTo("Dom Bosco Assunção");
    }

    @Test
    @DisplayName("REQ-GAM-LOCATION-CATALOG-008 and REQ-ORATORIO-002 - configured location retired after startup validation -> atomic unavailable conflict")
    void configuredLocationRetiredAfterStartupValidationShouldBlockCreation() {
        assertUnavailableAfterStartupValidation(
                "2035-08-20",
                "catalog_current = FALSE"
        );
    }

    @Test
    @DisplayName("REQ-GAM-LOCATION-CATALOG-008 and REQ-ORATORIO-002 - configured location loses system ownership after startup validation -> atomic unavailable conflict")
    void configuredLocationOwnershipDriftAfterStartupValidationShouldBlockCreation() {
        assertUnavailableAfterStartupValidation(
                "2035-08-21",
                "code = NULL, system_managed = FALSE, catalog_current = FALSE"
        );
    }

    private void assertUnavailableAfterStartupValidation(String date, String driftAssignments) {
        UUID configuredLocationId = jdbcTemplate.queryForObject(
                "SELECT id FROM gam_locations WHERE code = 'DBA'",
                UUID.class
        );
        long activityCountBefore = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM activity_logs WHERE action = 'ORATORIO_CREATED'",
                Long.class
        );
        SoftAssertions softly = new SoftAssertions();

        try {
            jdbcTemplate.update(
                    "UPDATE gam_locations SET " + driftAssignments + " WHERE id = ?",
                    configuredLocationId
            );

            ExtractableResponse<Response> response = authenticatedJsonRequest(sudoSession())
                    .body(Map.of("date", date))
                    .post("/oratorios")
                    .then()
                    .extract();
            if (response.statusCode() == 201) {
                trackOratorio(UUID.fromString(response.path("id")));
            }

            softly.assertThat(response.statusCode())
                    .as("configured location unavailable response")
                    .isEqualTo(409);
            softly.assertThat(response.<String>path("code"))
                    .as("configured location unavailable error code")
                    .isEqualTo("ORATORIO_LOCATION_UNAVAILABLE");
            softly.assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM oratorios WHERE local_date = CAST(? AS DATE)",
                    Long.class,
                    date
            )).as("no Oratorio persisted for unavailable configured location")
                    .isZero();
            softly.assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM activity_logs WHERE action = 'ORATORIO_CREATED'",
                    Long.class
            )).as("no Oratorio creation activity emitted")
                    .isEqualTo(activityCountBefore);
        } finally {
            jdbcTemplate.update(
                    "UPDATE gam_locations SET code = 'DBA', system_managed = TRUE, "
                            + "catalog_current = TRUE WHERE id = ?",
                    configuredLocationId
            );
        }

        softly.assertAll();
    }
}
