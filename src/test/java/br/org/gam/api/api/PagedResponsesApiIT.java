package br.org.gam.api.api;

import br.org.gam.api.testing.annotation.ApiTest;
import br.org.gam.api.testing.annotation.FunctionalTest;
import br.org.gam.api.testing.annotation.IntegrationTest;
import br.org.gam.api.testing.integration.BaseApiIntegrationTest;
import io.restassured.response.ExtractableResponse;
import io.restassured.response.Response;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@ApiTest
@FunctionalTest
@IntegrationTest
@DisplayName("API - Paged response contract")
class PagedResponsesApiIT extends BaseApiIntegrationTest {

    @Test
    @DisplayName("REQ-OPENAPI-007 - first page with default size -> GAM-owned paged response envelope")
    void firstPageShouldUseTheGAMOwnedEnvelopeAndDefaultSize() {
        AuthSession session = registerAndLogin("MEMBER");

        ExtractableResponse<Response> response = authenticatedJsonRequest(session)
                .get("/locations?page=0")
                .then()
                .statusCode(200)
                .extract();
        Map<String, Object> page = response.jsonPath().getMap("$");

        assertThat(page).containsOnlyKeys(
                "items", "page", "size", "totalElements", "totalPages", "first", "last"
        );
        assertThat(page).containsEntry("page", 0)
                .containsEntry("size", 20)
                .containsEntry("first", true)
                .containsEntry("last", true);
    }

    @Test
    @DisplayName("REQ-OPENAPI-007 - page size above 100 -> HTTP 400 instead of silent clamping")
    void pageSizeAboveTheMaximumShouldBeRejected() {
        AuthSession session = registerAndLogin("MEMBER");

        authenticatedJsonRequest(session)
                .get("/locations?page=0&size=101")
                .then()
                .statusCode(400);
    }

    @Test
    @DisplayName("REQ-OPENAPI-007 - unknown sort field -> HTTP 400")
    void unknownSortFieldShouldBeRejected() {
        AuthSession session = registerAndLogin("MEMBER");

        authenticatedJsonRequest(session)
                .get("/locations?page=0&size=20&sort=internalPersistenceField,asc")
                .then()
                .statusCode(400);
    }
}
