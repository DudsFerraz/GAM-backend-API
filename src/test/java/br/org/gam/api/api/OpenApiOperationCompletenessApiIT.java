package br.org.gam.api.api;

import br.org.gam.api.testing.annotation.ApiTest;
import br.org.gam.api.testing.annotation.FunctionalTest;
import br.org.gam.api.testing.annotation.IntegrationTest;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@ApiTest
@FunctionalTest
@IntegrationTest
@DisplayName("API - OpenAPI operation completeness")
class OpenApiOperationCompletenessApiIT extends AbstractOpenApiDocumentationApiIT {

    private static final Set<String> HTTP_METHODS = Set.of("get", "post", "put", "patch", "delete");
    private static final Set<String> CONSUMER_TAGS = Set.of(
            "Authentication", "Accounts", "Members", "Membership Solicitations", "Events", "GamLocations",
            "Presences", "RBAC", "Oratorios", "Oratorianos", "Oratoriano Forms"
    );
    private static final List<String> NULLABLE_OBJECT_DRAFT_FIELDS = List.of(
            "address", "responsible", "father", "mother", "health", "declarations"
    );
    private static final List<String> NULLABLE_FORM_LIFECYCLE_FIELDS = List.of(
            "completedAt", "completedBy", "revokedAt", "revokedBy"
    );
    private static final Map<String, List<String>> REQUIRED_FORM_RESPONSE_FIELDS = Map.of(
            "FormRDTO", List.of(
                    "id", "oratorianoId", "version", "status", "origin", "draftRevision", "data",
                    "signedOn", "createdBy", "createdAt", "completedAt", "completedBy", "revokedAt", "revokedBy"
            ),
            "FormHistoryRDTO", List.of(
                    "id", "version", "status", "origin", "signedOn", "createdAt", "createdBy",
                    "completedAt", "completedBy", "revokedAt", "revokedBy", "attachmentExists", "attachmentPageCount"
            )
    );
    private static final Map<String, List<String>> NULLABLE_FORM_RESPONSE_FIELDS = Map.of(
            "FormRDTO", List.of("signedOn", "createdBy"),
            "FormHistoryRDTO", List.of("signedOn", "createdBy")
    );
    private static final Map<String, List<String>> NULLABLE_NESTED_FORM_DRAFT_FIELDS = Map.of(
            "AddressDTO", List.of("addressLine", "addressNumber", "neighborhood", "cep", "city"),
            "ResponsibleDTO", List.of(
                    "relationship", "relationshipComplement", "firstName", "surname", "cpf",
                    "phoneNumber", "email", "atLeast18"
            ),
            "ParentDTO", List.of("firstName", "surname", "cpf"),
            "HealthDTO", List.of(
                    "medicalFollowUp", "physicalActivityRestriction", "medicineUse", "allergies",
                    "convulsions", "frequentFainting", "heartCondition", "otherHealthCondition", "otherCare"
            ),
            "DeclarationsDTO", List.of(
                    "signerRelationshipConfirmed", "informationTruthConfirmed",
                    "healthInformationCurrentConfirmed", "informationUseUnderstood", "formReviewed",
                    "imageAndVoiceAuthorizationAccepted"
            ),
            "HealthQuestionDTO", List.of("answer", "explanation", "importantInstructions")
    );
    private static final Map<String, List<String>> NULLABLE_ENUM_NESTED_FORM_DRAFT_FIELDS = Map.of(
            "ResponsibleDTO", List.of("relationship"),
            "HealthQuestionDTO", List.of("answer")
    );
    private static final String SCHEMA_REF_PREFIX = "#/components/schemas/";

    private static final List<Route> ORATORIO_MODULE_ROUTES = List.of(
            route("patch", "/members/{memberId}/oratorio-coordinator/grant"),
            route("patch", "/members/{memberId}/oratorio-coordinator/revoke"),
            route("post", "/oratorios"),
            route("get", "/oratorios/{oratorioId}"),
            route("put", "/oratorios/{oratorioId}/planning"),
            route("put", "/oratorios/{oratorioId}/teams/{teamType}/members/{memberId}"),
            route("delete", "/oratorios/{oratorioId}/teams/{teamType}/members/{memberId}"),
            route("patch", "/oratorios/{oratorioId}/lock"),
            route("patch", "/oratorios/{oratorioId}/finalize"),
            route("patch", "/oratorios/{oratorioId}/reopen"),
            route("patch", "/oratorios/{oratorioId}/cancel"),
            route("delete", "/oratorios/{oratorioId}"),
            route("get", "/oratorios/{oratorioId}/attendance/members"),
            route("get", "/oratorios/{oratorioId}/attendance/oratorianos"),
            route("get", "/oratorios/{oratorioId}/attendance/present"),
            route("put", "/oratorios/{oratorioId}/attendance/members/{memberId}"),
            route("delete", "/oratorios/{oratorioId}/attendance/members/{memberId}"),
            route("put", "/oratorios/{oratorioId}/attendance/oratorianos/{oratorianoId}"),
            route("delete", "/oratorios/{oratorioId}/attendance/oratorianos/{oratorianoId}"),
            route("post", "/oratorios/{oratorioId}/attendance/oratorianos/register-and-mark"),
            route("post", "/oratorianos"),
            route("get", "/oratorianos/{oratorianoId}"),
            route("put", "/oratorianos/{oratorianoId}"),
            route("delete", "/oratorianos/{oratorianoId}"),
            route("patch", "/oratorianos/{oratorianoId}/restore"),
            route("get", "/oratorianos/{oratorianoId}/attendances"),
            route("get", "/oratorianos/{oratorianoId}/attendance-summary"),
            route("post", "/oratorianos/search"),
            route("post", "/oratorianos/{oratorianoId}/forms"),
            route("get", "/oratorianos/{oratorianoId}/forms"),
            route("get", "/oratorianos/{oratorianoId}/forms/{formId}"),
            route("put", "/oratorianos/{oratorianoId}/forms/{formId}"),
            route("delete", "/oratorianos/{oratorianoId}/forms/{formId}"),
            route("patch", "/oratorianos/{oratorianoId}/forms/{formId}/complete"),
            route("patch", "/oratorianos/{oratorianoId}/forms/{formId}/revoke"),
            route("post", "/oratorianos/{oratorianoId}/forms/{formId}/print-snapshots"),
            route("get", "/oratorianos/{oratorianoId}/forms/{formId}/print-snapshots"),
            route("get", "/oratorianos/{oratorianoId}/forms/{formId}/print-snapshots/{printSnapshotId}/pdf"),
            route("put", "/oratorianos/{oratorianoId}/forms/{formId}/signed-attachments"),
            route("get", "/oratorianos/{oratorianoId}/forms/{formId}/signed-attachments"),
            route("get", "/oratorianos/{oratorianoId}/forms/{formId}/signed-attachments/{attachmentId}")
    );

    @Test
    @DisplayName("REQ-OPENAPI-003, REQ-OPENAPI-004, and REQ-OPENAPI-012 - every public operation -> complete consumer contract")
    void everyPublicOperationShouldHaveStableConsumerFacingDocumentation() {
        Map<String, Object> contract = openApiContract().jsonPath().getMap("$");
        List<Map<String, Object>> operations = operations(contract);
        SoftAssertions assertions = new SoftAssertions();

        assertions.assertThat(operations).isNotEmpty();
        assertions.assertThat(operations)
                .extracting(operation -> operation.get("operationId"))
                .doesNotContainNull()
                .doesNotHaveDuplicates()
                .allSatisfy(operationId -> assertions.assertThat(operationId.toString())
                        .matches("[a-z][A-Za-z0-9]*"));

        for (Map<String, Object> operation : operations) {
            String operationId = String.valueOf(operation.get("operationId"));
            assertions.assertThat(strings(operation, "tags"))
                    .as("%s tag", operationId)
                    .hasSize(1)
                    .allSatisfy(tag -> assertions.assertThat(CONSUMER_TAGS).contains(tag));
            assertions.assertThat(operation.get("summary"))
                    .as("%s summary", operationId)
                    .isInstanceOf(String.class)
                    .asString()
                    .isNotBlank();
            assertions.assertThat(operation.get("description"))
                    .as("%s purpose", operationId)
                    .isInstanceOf(String.class)
                    .asString()
                    .isNotBlank();

            Map<String, Object> responses = object(operation, "responses");
            assertions.assertThat(responses).as("%s responses", operationId).isNotEmpty();
            if (!"getCsrfProof".equals(operationId)) {
                assertions.assertThat(responses.keySet())
                        .as("%s error responses", operationId)
                        .anySatisfy(status -> assertions.assertThat(status).startsWith("4"));
            }
            assertExamples(operationId, operation, assertions);
        }

        assertions.assertAll();
    }

    @Test
    @DisplayName("REQ-ACTIVITY-007 and REQ-OPENAPI-003 - every response -> UUID X-Request-Id header")
    void everyResponseShouldDocumentTheRequestCorrelationHeader() {
        Map<String, Object> contract = openApiContract().jsonPath().getMap("$");
        List<String> missingHeaders = new ArrayList<>();
        List<String> invalidSchemas = new ArrayList<>();

        for (Map<String, Object> operation : operations(contract)) {
            String operationId = String.valueOf(operation.get("operationId"));
            Map<String, Object> responses = object(operation, "responses");
            responses.forEach((status, responseValue) -> {
                @SuppressWarnings("unchecked")
                Map<String, Object> response = (Map<String, Object>) responseValue;
                Map<String, Object> headers = object(response, "headers");
                String responseName = operationId + " response " + status;
                if (headers == null || !(headers.get("X-Request-Id") instanceof Map<?, ?>)) {
                    missingHeaders.add(responseName);
                    return;
                }

                Map<String, Object> requestIdHeader = object(headers, "X-Request-Id");
                Map<String, Object> schema = object(requestIdHeader, "schema");
                if (schema == null
                        || !"string".equals(schema.get("type"))
                        || !"uuid".equals(schema.get("format"))) {
                    invalidSchemas.add(responseName);
                }
            });
        }

        assertThat(missingHeaders)
                .as("responses missing the X-Request-Id header")
                .isEmpty();
        assertThat(invalidSchemas)
                .as("X-Request-Id headers without a string/uuid schema")
                .isEmpty();
    }

    @Test
    @DisplayName("REQ-ORATORIO-012, REQ-ORATORIO-ATT-011, REQ-ORATORIANO-012 and REQ-ORATORIANO-FORM-019 - accepted route catalog -> generated contract")
    void acceptedOratorioModuleRouteCatalogShouldBeGeneratedExactly() {
        Map<String, Object> paths = object(openApiContract().jsonPath().getMap("$"), "paths");
        SoftAssertions assertions = new SoftAssertions();

        for (Route route : ORATORIO_MODULE_ROUTES) {
            Object pathItem = paths.get(route.path());
            assertions.assertThat(pathItem)
                    .as("%s %s path", route.method().toUpperCase(), route.path())
                    .isInstanceOf(Map.class);
            if (pathItem instanceof Map<?, ?> methods) {
                assertions.assertThat(methods.containsKey(route.method()))
                        .as("%s %s method", route.method().toUpperCase(), route.path())
                        .isTrue();
            }
        }

        assertions.assertAll();
    }

    @Test
    @DisplayName("REQ-ORATORIANO-012 and REQ-OPENAPI-004 - attendance summary schema distinguishes always-present and requested-only counts")
    void attendanceSummarySchemaShouldExposeAccurateConsumerVisibleOptionality() {
        Map<String, Object> contract = openApiContract().jsonPath().getMap("$");
        Map<String, Object> operation = operation(
                object(contract, "paths"),
                route("get", "/oratorianos/{oratorianoId}/attendance-summary")
        );
        Map<String, Object> response = object(object(operation, "responses"), "200");
        Map<String, Object> mediaType = object(object(response, "content"), "application/json");
        Map<String, Object> responseSchema = object(mediaType, "schema");
        String reference = String.valueOf(responseSchema.get("$ref"));
        String schemaName = reference.substring(reference.lastIndexOf('/') + 1);
        Map<String, Object> summarySchema = object(
                object(object(contract, "components"), "schemas"),
                schemaName
        );
        Map<String, Object> properties = object(summarySchema, "properties");
        List<String> required = summarySchema.get("required") instanceof List<?> values
                ? values.stream().map(String::valueOf).toList()
                : List.of();

        assertThat(properties).containsOnlyKeys(
                "oratorioAttendances",
                "oratorioDistinctMonthsAttendances",
                "oratorioDistinctYearsAttendances",
                "oratorioYearAttendances",
                "oratorioYearDistinctMonthsAttendances",
                "oratorioMonthAttendances"
        );
        assertThat(required).containsExactlyInAnyOrder(
                "oratorioAttendances",
                "oratorioDistinctMonthsAttendances",
                "oratorioDistinctYearsAttendances"
        );
        for (String requestedOnly : List.of(
                "oratorioYearAttendances",
                "oratorioYearDistinctMonthsAttendances",
                "oratorioMonthAttendances"
        )) {
            Map<String, Object> property = object(properties, requestedOnly);
            assertThat(property.get("nullable"))
                    .as("%s must be optional but non-null", requestedOnly)
                    .isNotEqualTo(true);
            assertThat(property.get("type"))
                    .as("%s must not admit explicit null", requestedOnly)
                    .isNotInstanceOf(List.class);
        }
    }

    @Test
    @DisplayName("REQ-OPENAPI-003 and module route catalogs - Oratorio operations -> exact tags, success statuses, media types, and paging parameters")
    void acceptedOratorioModuleOperationsShouldExposeExactHttpContracts() {
        Map<String, Object> contract = openApiContract().jsonPath().getMap("$");
        Map<String, Object> paths = object(contract, "paths");
        SoftAssertions assertions = new SoftAssertions();
        Map<Route, String> expectedSuccess = Map.ofEntries(
                Map.entry(route("post", "/oratorios"), "201"),
                Map.entry(route("put", "/oratorios/{oratorioId}/planning"), "200"),
                Map.entry(route("put", "/oratorios/{oratorioId}/teams/{teamType}/members/{memberId}"), "204"),
                Map.entry(route("delete", "/oratorios/{oratorioId}/teams/{teamType}/members/{memberId}"), "204"),
                Map.entry(route("patch", "/oratorios/{oratorioId}/lock"), "204"),
                Map.entry(route("patch", "/oratorios/{oratorioId}/finalize"), "204"),
                Map.entry(route("patch", "/oratorios/{oratorioId}/reopen"), "204"),
                Map.entry(route("patch", "/oratorios/{oratorioId}/cancel"), "204"),
                Map.entry(route("delete", "/oratorios/{oratorioId}"), "204"),
                Map.entry(route("post", "/oratorianos"), "201"),
                Map.entry(route("delete", "/oratorianos/{oratorianoId}"), "204"),
                Map.entry(route("patch", "/oratorianos/{oratorianoId}/restore"), "204"),
                Map.entry(route("post", "/oratorianos/{oratorianoId}/forms"), "201"),
                Map.entry(route("delete", "/oratorianos/{oratorianoId}/forms/{formId}"), "204"),
                Map.entry(route("patch", "/oratorianos/{oratorianoId}/forms/{formId}/complete"), "204"),
                Map.entry(route("patch", "/oratorianos/{oratorianoId}/forms/{formId}/revoke"), "204"),
                Map.entry(route("post", "/oratorianos/{oratorianoId}/forms/{formId}/print-snapshots"), "201")
        );

        for (Route route : ORATORIO_MODULE_ROUTES) {
            Map<String, Object> operation = operation(paths, route);
            assertions.assertThat(operation)
                    .as("%s %s operation", route.method().toUpperCase(), route.path())
                    .isNotNull();
            if (operation == null) {
                continue;
            }
            assertions.assertThat(strings(operation, "tags"))
                    .as("%s %s tag", route.method().toUpperCase(), route.path())
                    .containsExactly(expectedModuleTag(route.path()));
        }
        expectedSuccess.forEach((route, expectedStatus) -> {
            Map<String, Object> responses = object(operation(paths, route), "responses");
            Set<String> successfulStatuses = responses.keySet().stream()
                    .filter(status -> status.startsWith("2"))
                    .collect(Collectors.toSet());
            assertions.assertThat(successfulStatuses)
                    .as("%s %s success response", route.method().toUpperCase(), route.path())
                    .containsOnly(expectedStatus)
                    .hasSize(1);
        });

        Route pdfRoute = route(
                "get",
                "/oratorianos/{oratorianoId}/forms/{formId}/print-snapshots/{printSnapshotId}/pdf"
        );
        Map<String, Object> pdfResponse = object(
                object(operation(paths, pdfRoute), "responses"),
                "200"
        );
        assertions.assertThat(object(pdfResponse, "content").keySet())
                .as("rendered PDF response media type")
                .containsExactly("application/pdf");

        Route attachmentUpload = route(
                "put",
                "/oratorianos/{oratorianoId}/forms/{formId}/signed-attachments"
        );
        Map<String, Object> requestBody = object(operation(paths, attachmentUpload), "requestBody");
        assertions.assertThat(object(requestBody, "content").keySet())
                .as("signed attachment upload media type")
                .containsExactly("multipart/form-data");

        Route attachmentDownload = route(
                "get",
                "/oratorianos/{oratorianoId}/forms/{formId}/signed-attachments/{attachmentId}"
        );
        Map<String, Object> attachmentDownloadResponse = object(
                object(operation(paths, attachmentDownload), "responses"),
                "200"
        );
        Map<String, Object> attachmentDownloadMediaType = object(
                object(attachmentDownloadResponse, "content"),
                "*/*"
        );
        assertions.assertThat(object(attachmentDownloadMediaType, "schema"))
                .as("signed attachment download byte schema")
                .containsEntry("type", "string")
                .containsEntry("format", "byte");
        assertions.assertThat(attachmentDownloadMediaType.get("example"))
                .as("signed attachment download Base64 example")
                .isEqualTo("U3ludGhldGljIEdBTSBiaW5hcnkgY29udGVudA==");

        assertParameterNames(
                paths,
                route("get", "/oratorianos/{oratorianoId}/forms"),
                assertions,
                "oratorianoId", "page", "size"
        );
        assertParameterNames(
                paths,
                route("get", "/oratorianos/{oratorianoId}/forms/{formId}/print-snapshots"),
                assertions,
                "oratorianoId", "formId", "page", "size", "sort"
        );
        assertParameterNames(
                paths,
                route("get", "/oratorianos/{oratorianoId}/attendances"),
                assertions,
                "oratorianoId", "page", "size"
        );
        assertParameterNames(
                paths,
                route("post", "/oratorianos/search"),
                assertions,
                "sort", "attendanceYear", "page", "size"
        );
        Map<String, Object> search = operation(
                paths,
                route("post", "/oratorianos/search")
        );
        Map<String, Object> searchSort = objects(search, "parameters").stream()
                .filter(parameter -> "sort".equals(parameter.get("name")))
                .findFirst()
                .orElseThrow();
        Map<String, Object> searchSortSchema = object(searchSort, "schema");
        assertions.assertThat(searchSortSchema)
                .as("Oratoriano search repeatable sort schema")
                .containsEntry("type", "array");
        assertions.assertThat(searchSort)
                .as("Oratoriano search repeatable query serialization")
                .containsEntry("style", "form")
                .containsEntry("explode", true);
        Map<String, Object> searchSortItems = object(searchSortSchema, "items");
        assertions.assertThat(searchSortItems)
                .as("each repeated Oratoriano sort value")
                .containsEntry("type", "string");
        assertions.assertThat(strings(searchSortItems, "enum"))
                .as("consumer-visible Oratoriano sort item grammar and allowed values")
                .containsExactlyInAnyOrder(
                        "oratorioYearAttendances,asc",
                        "oratorioYearAttendances,desc"
                );
        assertions.assertThat(searchSortSchema.get("default"))
                .as("omitting sort selects the deterministic ordinary-profile default")
                .isNull();
        assertions.assertThat(String.valueOf(searchSort.get("description")))
                .as("allowed derived sort plus normalized-name and UUID tie-breakers")
                .contains("Allowed fields: oratorioYearAttendances.")
                .contains("oratorioYearAttendances")
                .containsIgnoringCase("name")
                .containsIgnoringCase("UUID");

        Map<String, Object> attendanceSummary = operation(
                paths,
                route("get", "/oratorianos/{oratorianoId}/attendance-summary")
        );
        Map<String, Object> attendanceMonth = objects(attendanceSummary, "parameters").stream()
                .filter(parameter -> "month".equals(parameter.get("name")))
                .findFirst()
                .orElseThrow();
        assertions.assertThat(object(attendanceMonth, "schema"))
                .as("attendance-summary month boundary")
                .containsEntry("type", "integer")
                .containsEntry("minimum", 1)
                .containsEntry("maximum", 12);

        for (Route creation : List.of(
                route("post", "/oratorios"),
                route("post", "/oratorianos"),
                route("post", "/oratorianos/{oratorianoId}/forms")
        )) {
            Map<String, Object> created = object(
                    object(operation(paths, creation), "responses"),
                    "201"
            );
            Object headersValue = created.get("headers");
            assertions.assertThat(headersValue)
                    .as("%s response headers", creation.path())
                    .isInstanceOf(Map.class);
            Map<String, Object> headers = mapOrEmpty(headersValue);
            Object locationHeaderValue = headers.get("Location");
            assertions.assertThat(locationHeaderValue)
                    .as("%s Location response header", creation.path())
                    .isInstanceOf(Map.class);
            Map<String, Object> locationHeader = mapOrEmpty(locationHeaderValue);
            assertions.assertThat(mapOrEmpty(locationHeader.get("schema")))
                    .as("%s Location response schema", creation.path())
                    .containsEntry("type", "string")
                    .containsEntry("format", "uri");
        }
        assertions.assertAll();
    }

    @Test
    @DisplayName("REQ-ORATORIANO-FORM-021 through REQ-ORATORIANO-FORM-023 and REQ-OPENAPI-004/012 - form schemas -> typed data, shared attachment metadata, and actor labels")
    void oratorianoFormSchemasShouldExposeTheAcceptedTypedRepresentations() {
        Map<String, Object> contract = openApiContract().jsonPath().getMap("$");
        Map<String, Object> schemas = object(object(contract, "components"), "schemas");
        Map<String, Object> form = object(schemas, "FormRDTO");
        Map<String, Object> formProperties = object(form, "properties");
        assertThat(object(formProperties, "data").get("$ref"))
                .isEqualTo("#/components/schemas/FormDraftDTO");

        Map<String, Object> draft = object(schemas, "FormDraftDTO");
        assertThat(draft.get("required")).isNull();
        Map<String, Object> draftProperties = object(draft, "properties");
        assertThat(draftProperties).containsKeys(
                "firstName", "surname", "birthDate", "cpf", "rg", "address", "phoneNumber",
                "schoolName", "schoolGrade", "responsible", "father", "mother", "health",
                "declarations", "signedOn"
        );

        Map<String, Object> accountReference = object(schemas, "AccountReferenceRDTO");
        assertThat(object(accountReference, "properties")).containsOnlyKeys("id", "displayName");

        Map<String, Object> attachment = object(schemas, "AttachmentRDTO");
        assertThat(object(attachment, "properties")).containsOnlyKeys(
                "id", "originalFilename", "verifiedMimeType", "byteLength", "pageOrder", "pageCount"
        );

        Map<String, Object> printSnapshot = object(schemas, "PrintSnapshotMetadataRDTO");
        assertThat(object(printSnapshot, "properties")).containsOnlyKeys(
                "id", "draftRevision", "mode", "generatedAt", "templateVersion", "pageCount"
        );
    }

    @Test
    @DisplayName("REQ-ORATORIANO-FORM-003/023 and REQ-OPENAPI-004/012 - FormRDTO lifecycle fields -> effective nullable schemas")
    void formLifecycleFieldsShouldRemainNullableAfterResolvingReferences() {
        Map<String, Object> contract = openApiContract().jsonPath().getMap("$");
        Map<String, Object> schemas = object(object(contract, "components"), "schemas");
        Map<String, Object> formProperties = object(object(schemas, "FormRDTO"), "properties");
        SoftAssertions assertions = new SoftAssertions();

        for (String propertyName : NULLABLE_FORM_LIFECYCLE_FIELDS) {
            assertions.assertThat(schemaEffectivelyAllowsNull(
                            object(formProperties, propertyName),
                            schemas,
                            Set.of()
                    ))
                    .as("FormRDTO.%s accepts null after resolving sibling $ref", propertyName)
                    .isTrue();
        }

        assertions.assertAll();
    }

    @Test
    @DisplayName("REQ-ORATORIANO-FORM-016/022/023 and REQ-OPENAPI-004/012 - form response schemas -> every serialized property required")
    void formResponseSchemasShouldRequireEverySerializedProperty() {
        Map<String, Object> schemas = object(
                object(openApiContract().jsonPath().getMap("$"), "components"),
                "schemas"
        );
        SoftAssertions assertions = new SoftAssertions();

        for (Map.Entry<String, List<String>> schemaEntry : REQUIRED_FORM_RESPONSE_FIELDS.entrySet()) {
            assertExactRequiredProperties(
                    assertions,
                    object(schemas, schemaEntry.getKey()),
                    schemaEntry.getValue().toArray(new String[0])
            );
        }

        assertions.assertAll();
    }

    @Test
    @DisplayName("REQ-ORATORIANO-FORM-003/010/016/023 and REQ-OPENAPI-004/012 - draft and history response fields -> effective nullability")
    void formResponseNullableFieldsShouldRemainNullableAfterResolvingReferences() {
        Map<String, Object> contract = openApiContract().jsonPath().getMap("$");
        Map<String, Object> schemas = object(object(contract, "components"), "schemas");
        SoftAssertions assertions = new SoftAssertions();

        for (Map.Entry<String, List<String>> schemaEntry : NULLABLE_FORM_RESPONSE_FIELDS.entrySet()) {
            Map<String, Object> properties = object(object(schemas, schemaEntry.getKey()), "properties");
            for (String propertyName : schemaEntry.getValue()) {
                assertions.assertThat(schemaEffectivelyAllowsNull(
                                object(properties, propertyName),
                                schemas,
                                Set.of()
                        ))
                        .as("%s.%s accepts null after resolving sibling $ref", schemaEntry.getKey(), propertyName)
                        .isTrue();
            }
        }

        assertions.assertAll();
    }

    @Test
    @DisplayName("REQ-ORATORIANO-FORM-022 and REQ-OPENAPI-004/012 - FormDraftDTO properties -> explicit nullable schemas")
    void formDraftSchemaShouldExplicitlyAllowNullForEveryField() {
        Map<String, Object> schemas = object(
                object(openApiContract().jsonPath().getMap("$"), "components"),
                "schemas"
        );
        Map<String, Object> draftProperties = object(object(schemas, "FormDraftDTO"), "properties");

        assertThat(draftProperties)
                .allSatisfy((propertyName, propertySchema) -> assertThat(schemaAllowsNull(propertySchema))
                        .as("FormDraftDTO.%s explicitly admits null", propertyName)
                        .isTrue());
    }

    @Test
    @DisplayName("REQ-ORATORIANO-FORM-022 and REQ-OPENAPI-004/012 - object-valued draft fields -> effective nullability after $ref resolution")
    void nullableObjectDraftFieldsShouldRemainNullableAfterResolvingReferences() {
        Map<String, Object> contract = openApiContract().jsonPath().getMap("$");
        Map<String, Object> schemas = object(object(contract, "components"), "schemas");
        Map<String, Object> draftProperties = object(object(schemas, "FormDraftDTO"), "properties");
        SoftAssertions assertions = new SoftAssertions();

        for (String propertyName : NULLABLE_OBJECT_DRAFT_FIELDS) {
            assertions.assertThat(schemaEffectivelyAllowsNull(
                            object(draftProperties, propertyName),
                            schemas,
                            Set.of()
                    ))
                    .as("FormDraftDTO.%s accepts null after resolving sibling $ref", propertyName)
                    .isTrue();
        }

        assertions.assertAll();
    }

    @Test
    @DisplayName("REQ-ORATORIANO-FORM-022 and REQ-OPENAPI-004/012 - nullable enum-backed draft fields -> enum includes null")
    void nullableEnumDraftFieldsShouldIncludeNullInEffectiveEnum() {
        Map<String, Object> contract = openApiContract().jsonPath().getMap("$");
        Map<String, Object> schemas = object(object(contract, "components"), "schemas");
        SoftAssertions assertions = new SoftAssertions();

        for (Map.Entry<String, List<String>> schemaEntry : NULLABLE_ENUM_NESTED_FORM_DRAFT_FIELDS.entrySet()) {
            Map<String, Object> properties = object(object(schemas, schemaEntry.getKey()), "properties");
            for (String propertyName : schemaEntry.getValue()) {
                Object schemaValue = object(properties, propertyName);
                boolean effectivelyAllowsNull = schemaEffectivelyAllowsNull(schemaValue, schemas, Set.of());
                assertions.assertThat(effectivelyAllowsNull)
                        .as("%s.%s remains effectively nullable", schemaEntry.getKey(), propertyName)
                        .isTrue();
                if (effectivelyAllowsNull) {
                    assertions.assertThat(schemaEffectivelyIncludesNullInEnum(
                                    schemaValue,
                                    schemas,
                                    Set.of()
                            ))
                            .as("%s.%s effective enum includes JSON null", schemaEntry.getKey(), propertyName)
                            .isTrue();
                }
            }
        }

        assertions.assertAll();
    }

    @Test
    @DisplayName("REQ-ORATORIANO-FORM-003/022 and REQ-OPENAPI-004/012 - nested draft fields -> effective nullable schemas")
    void nestedFormDraftFieldsShouldRemainNullableAfterResolvingReferences() {
        Map<String, Object> contract = openApiContract().jsonPath().getMap("$");
        Map<String, Object> schemas = object(object(contract, "components"), "schemas");
        SoftAssertions assertions = new SoftAssertions();

        for (Map.Entry<String, List<String>> schemaEntry : NULLABLE_NESTED_FORM_DRAFT_FIELDS.entrySet()) {
            Map<String, Object> properties = object(object(schemas, schemaEntry.getKey()), "properties");
            assertions.assertThat(properties.keySet())
                    .as("%s nested properties", schemaEntry.getKey())
                    .containsAll(schemaEntry.getValue());
            for (String propertyName : schemaEntry.getValue()) {
                assertions.assertThat(schemaEffectivelyAllowsNull(
                                object(properties, propertyName),
                                schemas,
                                Set.of()
                        ))
                        .as("%s.%s accepts null after resolving references", schemaEntry.getKey(), propertyName)
                        .isTrue();
            }
        }

        assertions.assertAll();
    }

    @Test
    @DisplayName("REQ-ORATORIANO-FORM-020/021/023 and REQ-OPENAPI-004/012 - exact metadata records -> every property required")
    void formMetadataSchemasShouldRequireEveryExactProperty() {
        Map<String, Object> schemas = object(
                object(openApiContract().jsonPath().getMap("$"), "components"),
                "schemas"
        );

        assertExactRequiredProperties(
                object(schemas, "AccountReferenceRDTO"),
                "id", "displayName"
        );
        assertExactRequiredProperties(
                object(schemas, "AttachmentRDTO"),
                "id", "originalFilename", "verifiedMimeType", "byteLength", "pageOrder", "pageCount"
        );
        assertExactRequiredProperties(
                object(schemas, "PrintSnapshotMetadataRDTO"),
                "id", "draftRevision", "mode", "generatedAt", "templateVersion", "pageCount"
        );
    }

    @Test
    @DisplayName("REQ-ORATORIANO-FORM-020 and REQ-OPENAPI-007/012 - print-snapshot success example -> coherent zero-based page")
    void printSnapshotMetadataExampleShouldBeACoherentZeroBasedPage() {
        Map<String, Object> contract = openApiContract().jsonPath().getMap("$");
        Map<String, Object> operation = operation(
                object(contract, "paths"),
                route("get", "/oratorianos/{oratorianoId}/forms/{formId}/print-snapshots")
        );
        Map<String, Object> response = object(object(operation, "responses"), "200");
        Map<String, Object> example = object(
                object(object(response, "content"), "*/*"),
                "example"
        );
        int page = ((Number) example.get("page")).intValue();
        int size = ((Number) example.get("size")).intValue();
        long totalElements = ((Number) example.get("totalElements")).longValue();
        int totalPages = ((Number) example.get("totalPages")).intValue();
        boolean first = (Boolean) example.get("first");
        boolean last = (Boolean) example.get("last");
        int expectedTotalPages = totalElements == 0
                ? 0
                : (int) Math.ceil((double) totalElements / size);

        assertThat(page).isGreaterThanOrEqualTo(0);
        assertThat(size).isBetween(1, 100);
        assertThat(totalElements).isGreaterThanOrEqualTo(0);
        assertThat(totalPages).isEqualTo(expectedTotalPages);
        assertThat(first).isEqualTo(page == 0);
        assertThat(last).isEqualTo(totalPages == 0 || page >= totalPages - 1);
        assertThat(objects(example, "items")).hasSizeLessThanOrEqualTo(size);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mapOrEmpty(Object value) {
        return value instanceof Map<?, ?> ? (Map<String, Object>) value : Map.of();
    }

    private void assertExactRequiredProperties(Map<String, Object> schema, String... propertyNames) {
        assertThat(object(schema, "properties")).containsOnlyKeys(propertyNames);
        assertThat(strings(schema, "required")).containsExactlyInAnyOrder(propertyNames);
    }

    private void assertExactRequiredProperties(
            SoftAssertions assertions,
            Map<String, Object> schema,
            String... propertyNames
    ) {
        assertions.assertThat(object(schema, "properties")).containsOnlyKeys(propertyNames);
        assertions.assertThat(strings(schema, "required")).containsExactlyInAnyOrder(propertyNames);
    }

    private boolean schemaAllowsNull(Object schemaValue) {
        if (!(schemaValue instanceof Map<?, ?> rawSchema)) {
            return false;
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> schema = (Map<String, Object>) rawSchema;
        if (Boolean.TRUE.equals(schema.get("nullable"))) {
            return true;
        }
        Object type = schema.get("type");
        if (type instanceof Collection<?> types && types.contains("null")) {
            return true;
        }
        for (String composition : List.of("anyOf", "oneOf")) {
            Object alternatives = schema.get(composition);
            if (alternatives instanceof Collection<?> choices
                    && choices.stream().anyMatch(this::isNullSchema)) {
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private boolean schemaEffectivelyAllowsNull(
            Object schemaValue,
            Map<String, Object> schemas,
            Set<String> resolvingRefs
    ) {
        if (!(schemaValue instanceof Map<?, ?> rawSchema)) {
            return false;
        }
        Map<String, Object> schema = (Map<String, Object>) rawSchema;
        if (Boolean.FALSE.equals(schema.get("nullable"))) {
            return false;
        }

        Object type = schema.get("type");
        boolean typeAllowsNull = type == null
                || "null".equals(type)
                || type instanceof Collection<?> types && types.contains("null");
        if (!typeAllowsNull) {
            return false;
        }

        Object reference = schema.get("$ref");
        if (reference != null) {
            if (!(reference instanceof String referenceValue)
                    || !referenceValue.startsWith(SCHEMA_REF_PREFIX)
                    || resolvingRefs.contains(referenceValue)) {
                return false;
            }
            Set<String> nextResolvingRefs = new HashSet<>(resolvingRefs);
            nextResolvingRefs.add(referenceValue);
            if (!schemaEffectivelyAllowsNull(
                    schemas.get(referenceValue.substring(SCHEMA_REF_PREFIX.length())),
                    schemas,
                    nextResolvingRefs
            )) {
                return false;
            }
        }

        Object anyOf = schema.get("anyOf");
        if (anyOf instanceof Collection<?> alternatives
                && alternatives.stream().noneMatch(
                        alternative -> schemaEffectivelyAllowsNull(alternative, schemas, resolvingRefs)
                )) {
            return false;
        }

        Object oneOf = schema.get("oneOf");
        if (oneOf instanceof Collection<?> alternatives
                && alternatives.stream().filter(
                        alternative -> schemaEffectivelyAllowsNull(alternative, schemas, resolvingRefs)
                ).count() != 1) {
            return false;
        }

        Object allOf = schema.get("allOf");
        if (allOf instanceof Collection<?> alternatives
                && alternatives.stream().anyMatch(
                        alternative -> !schemaEffectivelyAllowsNull(alternative, schemas, resolvingRefs)
                )) {
            return false;
        }

        return true;
    }

    @SuppressWarnings("unchecked")
    private boolean schemaEffectivelyIncludesNullInEnum(
            Object schemaValue,
            Map<String, Object> schemas,
            Set<String> resolvingRefs
    ) {
        if (!(schemaValue instanceof Map<?, ?> rawSchema)) {
            return false;
        }
        Map<String, Object> schema = (Map<String, Object>) rawSchema;
        Object enumValue = schema.get("enum");
        if (enumValue instanceof Collection<?> enumValues && enumValues.contains(null)) {
            return true;
        }

        Object reference = schema.get("$ref");
        if (reference instanceof String referenceValue
                && referenceValue.startsWith(SCHEMA_REF_PREFIX)
                && !resolvingRefs.contains(referenceValue)) {
            Set<String> nextResolvingRefs = new HashSet<>(resolvingRefs);
            nextResolvingRefs.add(referenceValue);
            if (schemaEffectivelyIncludesNullInEnum(
                    schemas.get(referenceValue.substring(SCHEMA_REF_PREFIX.length())),
                    schemas,
                    nextResolvingRefs
            )) {
                return true;
            }
        }

        for (String composition : List.of("anyOf", "oneOf", "allOf")) {
            Object alternatives = schema.get(composition);
            if (alternatives instanceof Collection<?> choices
                    && choices.stream().anyMatch(
                            alternative -> schemaEffectivelyIncludesNullInEnum(
                                    alternative,
                                    schemas,
                                    resolvingRefs
                            )
                    )) {
                return true;
            }
        }

        return false;
    }

    private boolean isNullSchema(Object schemaValue) {
        if (!(schemaValue instanceof Map<?, ?> schema)) {
            return false;
        }
        Object type = schema.get("type");
        return "null".equals(type)
                || type instanceof Collection<?> types && types.contains("null");
    }

    @Test
    @DisplayName("OpenAPI guideline and module requirements - operation-specific preconditions, idempotency, stable codes, and details")
    void acceptedOratorioModuleOperationsShouldDocumentTheirDomainSpecificBehavior() {
        Map<String, Object> paths = object(openApiContract().jsonPath().getMap("$"), "paths");
        SoftAssertions assertions = new SoftAssertions();

        Map<String, Object> createOccurrence = operation(paths, route("post", "/oratorios"));
        assertions.assertThat(String.valueOf(createOccurrence.get("description")))
                .as("Oratorio creation preconditions")
                .containsIgnoringCase("date")
                .contains("ORATORIO_DATE_ALREADY_EXISTS");
        Map<String, Object> duplicateDate = errorExample(createOccurrence, "409");
        assertions.assertThat(duplicateDate)
                .containsEntry("status", 409)
                .containsEntry("code", "ORATORIO_DATE_ALREADY_EXISTS");
        assertions.assertThat(object(duplicateDate, "details"))
                .containsEntry("resource", "Oratorio")
                .containsKey("identifier");

        Map<String, Object> markMemberPresent = operation(
                paths,
                route("put", "/oratorios/{oratorioId}/attendance/members/{memberId}")
        );
        assertions.assertThat(String.valueOf(markMemberPresent.get("description")))
                .as("tracker check behavior")
                .containsIgnoringCase("idempotent");

        Map<String, Object> completeForm = operation(
                paths,
                route("patch", "/oratorianos/{oratorianoId}/forms/{formId}/complete")
        );
        assertions.assertThat(String.valueOf(completeForm.get("description")))
                .as("form completion preconditions and overwrite choice")
                .containsIgnoringCase("signed attachment")
                .containsIgnoringCase("print snapshot")
                .containsIgnoringCase("overwrite");
        Map<String, Object> overwriteChoice = errorExample(completeForm, "409");
        assertions.assertThat(overwriteChoice)
                .containsEntry("status", 409)
                .containsEntry("code", "ORATORIANO_FORM_PROFILE_OVERWRITE_CHOICE_REQUIRED");
        assertions.assertThat(object(overwriteChoice, "details"))
                .containsEntry("resource", "OratorianoForm")
                .containsKey("identifier");
        assertions.assertThat(String.valueOf(
                        object(object(completeForm, "responses"), "409").get("description")
                ))
                .as("all stable form-completion conflict codes")
                .contains(
                        "ORATORIANO_FORM_PROFILE_OVERWRITE_CHOICE_REQUIRED",
                        "ORATORIANO_FORM_PROFILE_SOURCE_IS_NEWER"
                );
        Map<String, Object> completionRequest = object(completeForm, "requestBody");
        assertions.assertThat(completionRequest)
                .as("completion overwrite-choice request")
                .isNotNull();
        if (completionRequest != null) {
            Map<String, Object> completionJson = object(
                    object(completionRequest, "content"),
                    "application/json"
            );
            assertions.assertThat(completionJson)
                    .as("completion JSON media type")
                    .isNotNull();
            if (completionJson != null) {
                Map<String, Object> example = object(completionJson, "example");
                assertions.assertThat(example)
                        .as("selected-snapshot completion example")
                        .containsEntry("overwriteNewerProfileValues", true)
                        .containsKey("printSnapshotId");
                if (example.get("printSnapshotId") != null) {
                    assertions.assertThatCode(() -> UUID.fromString(
                                    example.get("printSnapshotId").toString()
                            ))
                            .as("completion printSnapshotId example")
                            .doesNotThrowAnyException();
                }
            }
        }

        Map<String, Object> contract = openApiContract().jsonPath().getMap("$");
        Map<String, Object> completeFormSchema = object(
                object(object(contract, "components"), "schemas"),
                "CompleteFormDTO"
        );
        Object requiredCompletionFieldsValue = completeFormSchema.get("required");
        assertions.assertThat(requiredCompletionFieldsValue)
                .as("completion request required fields")
                .isInstanceOf(List.class);
        List<String> requiredCompletionFields = requiredCompletionFieldsValue instanceof List<?> fields
                ? fields.stream().map(String::valueOf).toList()
                : List.of();
        assertions.assertThat(requiredCompletionFields)
                .as("selected print snapshot is required for completion")
                .contains("printSnapshotId");
        assertions.assertThat(object(
                        object(completeFormSchema, "properties"),
                        "printSnapshotId"
                ))
                .containsEntry("type", "string")
                .containsEntry("format", "uuid");

        assertions.assertAll();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> operations(Map<String, Object> contract) {
        List<Map<String, Object>> operations = new ArrayList<>();
        Map<String, Object> paths = object(contract, "paths");

        for (Object pathItem : paths.values()) {
            Map<String, Object> methods = (Map<String, Object>) pathItem;
            methods.forEach((method, operation) -> {
                if (HTTP_METHODS.contains(method)) {
                    operations.add((Map<String, Object>) operation);
                }
            });
        }
        return operations;
    }

    @SuppressWarnings("unchecked")
    private void assertExamples(String operationId, Map<String, Object> operation, SoftAssertions assertions) {
        Map<String, Object> requestBody = object(operation, "requestBody");
        if (requestBody != null) {
            assertions.assertThat(hasExample(object(requestBody, "content").values()))
                    .as("%s request example", operationId)
                    .isTrue();
        }

        Map<String, Object> responses = object(operation, "responses");
        for (Object responseValue : responses.values()) {
            Map<String, Object> response = (Map<String, Object>) responseValue;
            Map<String, Object> content = object(response, "content");
            if (content != null) {
                assertions.assertThat(hasExample(content.values()))
                        .as("%s response example", operationId)
                        .isTrue();
            }
        }
    }

    @SuppressWarnings("unchecked")
    private boolean hasExample(Collection<Object> mediaTypes) {
        return mediaTypes.stream()
                .map(mediaType -> (Map<String, Object>) mediaType)
                .anyMatch(mediaType -> mediaType.containsKey("example") || mediaType.containsKey("examples"));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> operation(Map<String, Object> paths, Route route) {
        return (Map<String, Object>) ((Map<String, Object>) paths.get(route.path())).get(route.method());
    }

    private Map<String, Object> errorExample(Map<String, Object> operation, String status) {
        Map<String, Object> response = object(object(operation, "responses"), status);
        Map<String, Object> mediaType = object(object(response, "content"), "application/json");
        return object(mediaType, "example");
    }

    private String expectedModuleTag(String path) {
        if (path.contains("/forms")) {
            return "Oratoriano Forms";
        }
        if (path.startsWith("/oratorianos")) {
            return "Oratorianos";
        }
        if (path.startsWith("/oratorios")) {
            return "Oratorios";
        }
        return "Members";
    }

    private void assertParameterNames(
            Map<String, Object> paths,
            Route route,
            SoftAssertions assertions,
            String... expectedNames
    ) {
        Map<String, Object> operation = operation(paths, route);
        assertions.assertThat(operation)
                .as("%s %s operation", route.method().toUpperCase(), route.path())
                .isNotNull();
        if (operation == null) {
            return;
        }
        List<Map<String, Object>> parameters = objects(operation, "parameters");
        assertions.assertThat(parameters)
                .as("%s %s parameters", route.method().toUpperCase(), route.path())
                .extracting(parameter -> parameter.get("name"))
                .containsExactlyInAnyOrder((Object[]) expectedNames);
    }

    private static Route route(String method, String path) {
        return new Route(method, path);
    }

    private record Route(String method, String path) {
    }
}
