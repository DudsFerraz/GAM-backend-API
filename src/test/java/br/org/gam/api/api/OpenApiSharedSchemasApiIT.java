package br.org.gam.api.api;

import br.org.gam.api.testing.annotation.ApiTest;
import br.org.gam.api.testing.annotation.FunctionalTest;
import br.org.gam.api.testing.annotation.IntegrationTest;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@ApiTest
@FunctionalTest
@IntegrationTest
@DisplayName("API - OpenAPI shared schemas")
class OpenApiSharedSchemasApiIT extends AbstractOpenApiDocumentationApiIT {

    private static final Set<String> CURRENT_SYSTEM_GAM_LOCATION_CODES = Set.of(
            "DBSM", "DBA", "DBCA"
    );
    private static final int[] UNICODE_WHITE_SPACE_CODE_POINTS = {
            0x0009, 0x000A, 0x000B, 0x000C, 0x000D,
            0x0020, 0x0085, 0x00A0, 0x1680,
            0x2000, 0x2001, 0x2002, 0x2003, 0x2004, 0x2005,
            0x2006, 0x2007, 0x2008, 0x2009, 0x200A,
            0x2028, 0x2029, 0x202F, 0x205F, 0x3000
    };

    @Test
    @DisplayName("REQ-SEARCH-002/004/012 and REQ-OPENAPI-004 - shared search schemas -> strict canonical grammar")
    void sharedSearchSchemasShouldExposeTheStrictCanonicalGrammar() {
        Map<String, Object> schemas = schemas();
        Map<String, Object> search = object(schemas, "SearchDTO");
        Map<String, Object> searchProperties = object(search, "properties");
        Map<String, Object> filters = object(searchProperties, "filters");
        Map<String, Object> filterSchema = resolveSchema(
                openApiContract().body(),
                object(filters, "items")
        );
        Map<String, Object> filterProperties = object(filterSchema, "properties");
        Map<String, Object> comparisonMethod = object(filterProperties, "comparisonMethod");

        assertThat(search.get("additionalProperties")).isEqualTo(false);
        assertThat(strings(search, "required")).containsExactly("filters");
        assertThat(searchProperties).containsOnlyKeys("filters");
        assertThat(filters)
                .containsEntry("type", "array")
                .containsEntry("minItems", 0)
                .containsEntry("maxItems", 20);

        assertThat(filterSchema.get("additionalProperties")).isEqualTo(false);
        assertThat(strings(filterSchema, "required"))
                .containsExactlyInAnyOrder("field", "value", "comparisonMethod");
        assertThat(filterProperties).containsOnlyKeys("field", "value", "comparisonMethod");
        assertThat(object(filterProperties, "field").get("minLength")).isEqualTo(1);
        assertThat(comparisonMethod.get("minLength")).isEqualTo(1);
        assertThat(comparisonMethod.get("enum")).isEqualTo(List.of(
                "EQUALS",
                "LIKE",
                "IN",
                "GREATER_THAN_OR_EQUAL",
                "LESS_THAN_OR_EQUAL"
        ));
        assertThat(filterSchema.toString()).doesNotContain("comparationMethod");
    }

    @Test
    @DisplayName("REQ-SEARCH-002/012 and REQ-OPENAPI-004 - field -> non-whitespace string schema")
    void sharedSearchFieldShouldDocumentItsNonWhitespaceConstraint() {
        assertNonWhitespaceString(
                object(searchFilterProperties(), "field"),
                "field"
        );
    }

    @Test
    @DisplayName("REQ-SEARCH-002/012 and REQ-OPENAPI-004 - comparisonMethod -> non-whitespace string schema")
    void sharedSearchComparisonMethodShouldDocumentItsNonWhitespaceConstraint() {
        assertNonWhitespaceString(
                object(searchFilterProperties(), "comparisonMethod"),
                "comparisonMethod"
        );
    }

    @Test
    @DisplayName("REQ-SEARCH-004/012 and REQ-OPENAPI-004 - value -> strict nonblank scalar or bounded IN array")
    void sharedSearchValueShouldDocumentStrictScalarAndInAlternatives() {
        Map<String, Object> contract = openApiContract().body();
        Map<String, Object> value = resolveSchema(
                contract,
                object(searchFilterProperties(), "value")
        );

        assertStrictSearchValueAlternatives(contract, value);
    }

    @Test
    @DisplayName("REQ-SEARCH-012 - every structured-search operation -> required shared body and resource field catalog")
    void everyStructuredSearchOperationShouldRequireTheSharedBodyAndDocumentItsResourceCatalog() {
        Map<String, Object> contract = openApiContract().body();
        Map<String, Object> paths = object(contract, "paths");
        Map<String, Set<String>> catalogs = Map.of(
                "/accounts/search", Set.of("id", "email", "displayName", "role", "createdAt", "updatedAt"),
                "/events/search", Set.of(
                        "id", "title", "description", "gamLocationId", "requiredPermissionId",
                        "requiredPermissionCode", "type", "status", "beginDate", "endDate"
                ),
                "/members/search", Set.of(
                        "id", "name", "birthDate", "phoneNumber", "status", "accountId",
                        "email", "role", "createdAt", "updatedAt"
                ),
                "/membership-solicitations/search", Set.of(
                        "id", "accountId", "email", "name", "status", "submittedAt",
                        "decidedAt", "reviewedByAccountId"
                ),
                "/oratorianos/search", Set.of("id", "name")
        );

        catalogs.forEach((route, fields) -> {
            Map<String, Object> operation = object(object(paths, route), "post");
            Map<String, Object> requestBody = object(operation, "requestBody");
            assertThat(requestBody.get("required")).as(route).isEqualTo(true);

            Map<String, Object> content = object(requestBody, "content");
            Map<String, Object> mediaType = object(content, "application/json");
            Map<String, Object> schema = resolveSchema(contract, object(mediaType, "schema"));
            assertThat(schema).as(route).isEqualTo(object(object(object(contract, "components"), "schemas"), "SearchDTO"));

            String documentation = operation.toString();
            assertThat(documentation).as(route).contains("comparisonMethod").doesNotContain("comparationMethod");
            fields.forEach(field -> assertThat(documentation).as(route + " field " + field).contains(field));
            searchFieldDocumentation().get(route).forEach(fieldContract ->
                    assertFieldMethodAndValueDocumentation(
                            String.valueOf(operation.get("description")),
                            route,
                            fieldContract
                    )
            );
        });
    }

    @Test
    @DisplayName("REQ-SEARCH-012 and REQ-OPENAPI-003/007 - every search field -> owning value semantics")
    void everyStructuredSearchFieldShouldDocumentItsOwningValueSemantics() {
        Map<String, Object> contract = openApiContract().body();
        Map<String, Object> paths = object(contract, "paths");

        searchFieldSemantics().forEach((route, fieldSemantics) -> {
            Map<String, Object> operation = object(object(paths, route), "post");
            String description = String.valueOf(operation.get("description"));

            assertThat(fieldSemantics.keySet())
                    .as(route + " semantic field coverage")
                    .containsExactlyInAnyOrderElementsOf(
                            searchFieldDocumentation().get(route).stream()
                                    .map(SearchFieldDocumentation::field)
                                    .toList()
                    );
            fieldSemantics.forEach((field, semanticPatterns) -> {
                String sentence = fieldSentence(description, route, field);
                semanticPatterns.forEach(pattern -> assertThat(sentence)
                        .as("%s field %s owning semantic: %s", route, field, pattern)
                        .containsPattern("(?i).*" + pattern + ".*"));
            });
        });
    }

    @Test
    @DisplayName("REQ-SEARCH-004/005/012 and REQ-OPENAPI-003/004 - comparison methods -> explicit value-shape relationships")
    void everyStructuredSearchOperationShouldRelateMethodsToTheirValueShapes() {
        Map<String, Object> contract = openApiContract().body();
        Map<String, Object> paths = object(contract, "paths");

        searchFieldDocumentation().keySet().forEach(route -> {
            String description = String.valueOf(object(object(paths, route), "post").get("description"));
            int firstFieldContract = description.indexOf("id allows ");
            assertThat(firstFieldContract).as(route + " first field contract").isPositive();
            String sharedGrammar = description.substring(0, firstFieldContract);

            assertThat(sharedGrammar)
                    .as(route + " EQUALS shape")
                    .containsPattern("(?is).*EQUALS.{0,100}(?:one|single).{0,30}(?:scalar|string).*");
            assertThat(sharedGrammar)
                    .as(route + " LIKE shape")
                    .containsPattern("(?is).*LIKE.{0,100}(?:one|single).{0,30}nonblank.{0,30}string.*");
            assertThat(sharedGrammar)
                    .as(route + " IN shape and scalar parsing")
                    .containsPattern("(?is).*IN.{0,100}array.{0,50}(?:1|one).{0,30}100"
                            + ".{0,100}(?:scalar|equality).{0,30}(?:parsing|normalization).*");
            assertThat(sharedGrammar)
                    .as(route + " ordered-bound scalar shape")
                    .containsPattern("(?is).*(?:GREATER_THAN_OR_EQUAL|LESS_THAN_OR_EQUAL)"
                            + ".{0,120}(?:one|single).{0,30}scalar.*");
        });
    }

    @Test
    @DisplayName("REQ-SEARCH-012 and REQ-OPENAPI-003/007 - every search request example -> resource-valid executable filter")
    void everyStructuredSearchOperationShouldProvideAValidRequestExample() {
        Map<String, Object> contract = openApiContract().body();
        Map<String, Object> paths = object(contract, "paths");

        searchFieldDocumentation().forEach((route, fields) -> {
            Map<String, Object> operation = object(object(paths, route), "post");
            Map<String, Object> requestBody = object(operation, "requestBody");
            Map<String, Object> mediaType = object(object(requestBody, "content"), "application/json");
            Map<String, Object> example = object(mediaType, "example");
            List<?> filters = (List<?>) example.get("filters");

            assertThat(filters).as(route + " example filters").hasSize(1);
            assertThat(filters.get(0)).as(route + " example filter").isInstanceOf(Map.class);
            @SuppressWarnings("unchecked")
            Map<String, Object> filter = (Map<String, Object>) filters.get(0);
            assertThat(filter).containsOnlyKeys("field", "value", "comparisonMethod");
            assertThat(filter.get("field")).isInstanceOf(String.class);
            assertThat(filter.get("comparisonMethod")).isInstanceOf(String.class);

            String field = String.valueOf(filter.get("field"));
            String comparisonMethod = String.valueOf(filter.get("comparisonMethod"));
            SearchFieldDocumentation fieldContract = fields.stream()
                    .filter(candidate -> candidate.field().equals(field))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError(
                            route + " example field is outside its documented catalog: " + field
                    ));
            assertThat(comparisonMethod)
                    .as(route + " example comparisonMethod for " + field)
                    .isIn(fieldContract.methods());
            assertExampleValueCompatible(route, field, comparisonMethod, filter.get("value"));
        });
    }

    @Test
    @DisplayName("REQ-SEARCH-009/012 and REQ-OPENAPI-003/006/007 - every search 400 response -> exact failure categories")
    void everyStructuredSearchOperationShouldDocumentSpecificErrorCodes() {
        Map<String, Object> contract = openApiContract().body();
        Map<String, Object> paths = object(contract, "paths");

        searchFieldDocumentation().keySet().forEach(route -> {
            Map<String, Object> operation = object(object(paths, route), "post");
            Map<String, Object> badRequest = object(object(operation, "responses"), "400");
            Map<String, Object> mediaType = object(object(badRequest, "content"), "application/json");
            Map<String, Object> schema = resolveSchema(contract, object(mediaType, "schema"));
            assertThat(schema)
                    .as(route + " shared error schema")
                    .isEqualTo(object(object(object(contract, "components"), "schemas"), "ApiErrorDTO"));

            Map<String, Object> examples = object(mediaType, "examples");
            assertThat(examples)
                    .as(route + " exact search error examples")
                    .containsOnlyKeys(
                            "malformedJson",
                            "validationError",
                            "invalidSearchFilter",
                            "invalidParameterType"
                    );
            assertSearchErrorExample(examples, "malformedJson", "MALFORMED_JSON", route);
            assertSearchErrorExample(examples, "validationError", "VALIDATION_ERROR", route);
            assertSearchErrorExample(examples, "invalidSearchFilter", "INVALID_SEARCH_FILTER", route);
            assertSearchErrorExample(examples, "invalidParameterType", "INVALID_PARAMETER_TYPE", route);
            assertThat(badRequest.toString()).as(route).doesNotContain("INVALID_REQUEST");
        });
    }

    @Test
    @DisplayName("REQ-SEARCH-005/006/010/012 and REQ-OPENAPI-003/004 - nullable stored fields -> non-null submitted values")
    void nullableSolicitationFieldsShouldDistinguishStoredNullsFromSubmittedFilterValues() {
        Map<String, Object> contract = openApiContract().body();
        Map<String, Object> paths = object(contract, "paths");
        String description = String.valueOf(
                object(object(paths, "/membership-solicitations/search"), "post").get("description")
        );

        String decidedAt = fieldSentence(
                description,
                "/membership-solicitations/search",
                "decidedAt"
        );
        assertThat(decidedAt)
                .containsPattern("(?i).*(?:stored|resource).{0,30}(?:may be null|nullable).*")
                .containsPattern("(?i).*submitted.{0,30}value.{0,30}non-null"
                        + ".{0,50}(?:RFC.?3339|timestamp).*")
                .containsPattern("(?i).*pending.{0,50}(?:null|no decision).{0,50}(?:does not match|no match).*")
                .doesNotContainPattern("(?i).*value is (?:a )?nullable.*");

        String reviewedByAccountId = fieldSentence(
                description,
                "/membership-solicitations/search",
                "reviewedByAccountId"
        );
        assertThat(reviewedByAccountId)
                .containsPattern("(?i).*(?:stored|resource).{0,30}(?:may be null|nullable).*")
                .containsPattern("(?i).*submitted.{0,30}value.{0,30}non-null.{0,30}UUID.*")
                .containsPattern("(?i).*pending.{0,50}(?:null|no reviewer).{0,50}(?:does not match|no match).*")
                .doesNotContainPattern("(?i).*value is (?:a )?nullable.*");
    }

    @Test
    @DisplayName("REQ-OPENAPI-006 - generated ApiErrorDTO schema -> exact five-field envelope without error reason phrase")
    void apiErrorSchemaShouldExposeOnlyTheCommonFiveFieldEnvelope() {
        Map<String, Object> schemas = schemas();
        Map<String, Object> error = object(schemas, "ApiErrorDTO");
        assertThat(error).isNotNull();
        Map<String, Object> properties = object(error, "properties");

        assertThat(properties).containsOnlyKeys("timestamp", "status", "code", "message", "details");
        assertThat(object(properties, "timestamp"))
                .containsEntry("type", "string")
                .containsEntry("format", "date-time");
        assertThat(object(properties, "status")).containsEntry("type", "integer");
        assertThat(object(properties, "details")).containsEntry("type", "object");
    }

    @Test
    @DisplayName("REQ-OPENAPI-007 - paged GamLocation response schema -> GAM envelope rather than Spring Page internals")
    void pagedGamLocationResponseSchemaShouldUseTheGAMOwnedEnvelope() {
        Map<String, Object> contract = openApiContract().jsonPath().getMap("$");
        Map<String, Object> paths = object(contract, "paths");
        Map<String, Object> operation = object(object(paths, "/gam-locations"), "get");
        Map<String, Object> responses = object(operation, "responses");
        Map<String, Object> response = object(responses, "200");
        Map<String, Object> mediaType = first(object(response, "content").values());
        Map<String, Object> schema = resolveSchema(contract, object(mediaType, "schema"));

        assertThat(object(schema, "properties")).containsOnlyKeys(
                "items", "page", "size", "totalElements", "totalPages", "first", "last"
        );
    }

    @Test
    @DisplayName("REQ-OPENAPI-004 and REQ-OPENAPI-008 - common response schemas -> UUID/date/timestamp/enum/nullability contract")
    void commonResponseSchemasShouldUseStableConsumerRepresentations() {
        Map<String, Object> schemas = schemas();
        Map<String, Object> member = object(schemas, "MemberRDTO");
        Map<String, Object> event = object(schemas, "EventRDTO");
        Map<String, Object> gamLocation = object(schemas, "GamLocationRDTO");

        assertThat(object(object(member, "properties"), "id"))
                .containsEntry("type", "string")
                .containsEntry("format", "uuid");
        assertThat(object(object(member, "properties"), "birthDate"))
                .containsEntry("type", "string")
                .containsEntry("format", "date");
        assertThat(object(object(event, "properties"), "beginDate"))
                .containsEntry("type", "string")
                .containsEntry("format", "date-time");
        Map<String, Object> eventType = object(object(event, "properties"), "type");
        Object eventTypeValues = eventType.get("enum");
        if (eventTypeValues == null && eventType.get("$ref") != null) {
            String reference = eventType.get("$ref").toString();
            eventTypeValues = object(schemas, reference.substring(reference.lastIndexOf('/') + 1)).get("enum");
        }
        assertThat(eventTypeValues).isInstanceOf(List.class);
        assertThat((List<?>) eventTypeValues)
                .allSatisfy(value -> assertThat(value.toString()).matches("[A-Z][A-Z0-9_]*"));
        Object requiredValue = gamLocation.get("required");
        assertThat(requiredValue).isInstanceOf(List.class);
        List<?> required = (List<?>) requiredValue;
        assertThat(required).allSatisfy(value -> assertThat(value).isInstanceOf(String.class));
        assertThat(required.stream().map(String.class::cast).toList())
                .containsExactlyInAnyOrder(
                        "id", "code", "systemManaged", "name", "street", "city", "state",
                        "postalCode", "countryCode", "latitude", "longitude"
                );

        Map<String, Object> gamLocationProperties = object(gamLocation, "properties");
        assertNullType(object(gamLocationProperties, "code"));
        assertThat(object(gamLocationProperties, "systemManaged"))
                .containsEntry("type", "boolean");
        assertNullType(object(gamLocationProperties, "street"));
        assertNullType(object(gamLocationProperties, "postalCode"));
        assertNullType(object(gamLocationProperties, "latitude"));
        assertNullType(object(gamLocationProperties, "longitude"));
    }

    @Test
    @DisplayName("REQ-GAM-LOCATION-002 and REQ-GAM-LOCATION-004 - GamLocation mutation schema -> lengths, ranges, and precision")
    void gamLocationMutationSchemaShouldExposeValidationConstraints() {
        Map<String, Object> properties = object(
                object(schemas(), "GamLocationMutationDTO"), "properties"
        );

        assertLength(properties, "name", 1, 255);
        assertLength(properties, "street", 1, 255);
        assertLength(properties, "city", 1, 100);
        assertLength(properties, "state", 1, 50);
        assertLength(properties, "postalCode", 1, 20);
        assertLength(properties, "countryCode", 2, 2);
        assertThat(object(properties, "countryCode")).containsKey("pattern");

        assertNullType(object(properties, "street"));
        assertNullType(object(properties, "postalCode"));
        assertNullType(object(properties, "latitude"));
        assertNullType(object(properties, "longitude"));

        assertCoordinate(object(properties, "latitude"), -90, 90);
        assertCoordinate(object(properties, "longitude"), -180, 180);
    }

    @Test
    @DisplayName("REQ-GAM-LOCATION-010 - removal schema -> required reason with minimum")
    void gamLocationRemovalSchemaShouldRequireNonEmptyReason() {
        Map<String, Object> schema = object(schemas(), "RemoveGamLocationDTO");
        Map<String, Object> reason = object(object(schema, "properties"), "reason");

        assertThat(strings(schema, "required")).contains("reason");
        assertThat(reason)
                .containsEntry("type", "string")
                .containsEntry("minLength", 1);
    }

    @Test
    @DisplayName("REQ-ACTIVITY-008, REQ-EVENT-013/015/019 and REQ-OPENAPI-004 - Event reason schemas -> normalized Unicode code-point rule")
    void eventReasonSchemasShouldDocumentNormalizedUnicodeCodePointBoundary() {
        Map<String, Object> schemas = schemas();
        SoftAssertions softly = new SoftAssertions();

        for (String schemaName : List.of("EventReasonDTO", "ReopenEventDTO", "EventReplacementDTO")) {
            Map<String, Object> schema = object(schemas, schemaName);
            Map<String, Object> reason = object(object(schema, "properties"), "reason");
            String description = String.valueOf(reason.get("description"));

            softly.assertThat(reason)
                    .as("%s reason schema", schemaName)
                    .containsEntry("type", "string")
                    .containsEntry("minLength", 1)
                    .doesNotContainKey("maxLength");
            softly.assertThat(description)
                    .as("%s reason description", schemaName)
                    .containsIgnoringCase("Unicode")
                    .contains("White_Space")
                    .containsIgnoringCase("leading")
                    .containsIgnoringCase("trailing")
                    .containsIgnoringCase("1")
                    .containsIgnoringCase("2,000")
                    .containsIgnoringCase("code point");
        }

        softly.assertThat(strings(object(schemas, "EventReplacementDTO"), "required"))
                .as("EventReplacementDTO required properties")
                .doesNotContain("reason");

        softly.assertAll();
    }

    @Test
    @DisplayName("REQ-ORATORIO-009/010, REQ-ORATORIANO-005/009/010, REQ-ORATORIANO-FORM-002/018 and REQ-OPENAPI-004 - specialized audited reasons -> owner-specific requirement semantics")
    void specializedAuditedReasonSchemasShouldDocumentOwnerSpecificRequirementSemantics() {
        Map<String, Object> schemas = schemas();
        SoftAssertions softly = new SoftAssertions();

        for (String schemaName : List.of("ReasonDTO", "ReopenDTO", "ReplaceOratorianoDTO")) {
            Map<String, Object> reason = object(
                    object(object(schemas, schemaName), "properties"),
                    "reason"
            );
            String description = String.valueOf(reason.get("description"));

            softly.assertThat(reason)
                    .as("%s reason schema", schemaName)
                    .containsEntry("type", "string")
                    .containsEntry("minLength", 1)
                    .doesNotContainKey("maxLength");
            softly.assertThat(description)
                    .as("%s reason description", schemaName)
                    .containsIgnoringCase("normalized")
                    .containsIgnoringCase("Unicode")
                    .contains("White_Space")
                    .containsIgnoringCase("leading")
                    .containsIgnoringCase("trailing")
                    .containsIgnoringCase("1")
                    .containsIgnoringCase("2,000")
                    .containsIgnoringCase("code point");
        }

        Map<String, Object> reasonDtoSchema = object(schemas, "ReasonDTO");
        Map<String, Object> reasonDto = object(object(reasonDtoSchema, "properties"), "reason");
        softly.assertThat(strings(reasonDtoSchema, "required"))
                .as("ReasonDTO required properties")
                .contains("reason");
        softly.assertThat(String.valueOf(reasonDto.get("description")))
                .as("ReasonDTO operation-specific requirement description")
                .containsIgnoringCase("required")
                .containsIgnoringCase("cancellation")
                .containsIgnoringCase("deletion")
                .containsIgnoringCase("restoration")
                .containsIgnoringCase("revocation");

        Map<String, Object> reopen = object(schemas, "ReopenDTO");
        softly.assertThat(strings(reopen, "required"))
                .as("ReopenDTO required properties")
                .contains("reason");

        Map<String, Object> replacement = object(schemas, "ReplaceOratorianoDTO");
        softly.assertThat(strings(replacement, "required"))
                .as("ReplaceOratorianoDTO required properties")
                .doesNotContain("reason");
        softly.assertThat(String.valueOf(
                        object(object(replacement, "properties"), "reason").get("description")
                ))
                .as("ReplaceOratorianoDTO conditional reason description")
                .containsIgnoringCase("required")
                .containsIgnoringCase("name")
                .containsIgnoringCase("change");

        softly.assertAll();
    }

    @Test
    @DisplayName("REQ-ACTIVITY-008, owning reason requirements and REQ-OPENAPI-004 - required audited reason schemas -> normalized Unicode code-point rule")
    void requiredAuditedReasonSchemasShouldDocumentNormalizedUnicodeCodePointBoundary() {
        Map<String, Object> schemas = schemas();
        SoftAssertions softly = new SoftAssertions();

        for (String schemaName : List.of(
                "AddAccountRoleDTO",
                "DropAccountRoleDTO",
                "RemoveGamLocationDTO",
                "DeactivateMemberDTO",
                "ReviewMembershipSolicitationDTO",
                "LinkMemberAccountDTO",
                "CoordinatorTransitionDTO",
                "RegisterMemberDTO"
        )) {
            Map<String, Object> schema = object(schemas, schemaName);
            Map<String, Object> reason = object(object(schema, "properties"), "reason");
            String description = String.valueOf(reason.get("description"));

            softly.assertThat(strings(schema, "required"))
                    .as("%s required properties", schemaName)
                    .contains("reason");
            softly.assertThat(reason)
                    .as("%s reason schema", schemaName)
                    .containsEntry("type", "string")
                    .containsEntry("minLength", 1);
            if (Set.of("LinkMemberAccountDTO", "CoordinatorTransitionDTO").contains(schemaName)) {
                softly.assertThat(reason)
                        .as("%s normalized reason schema without raw maximum", schemaName)
                        .doesNotContainKey("maxLength");
            } else if (Set.of(
                    "DeactivateMemberDTO",
                    "ReviewMembershipSolicitationDTO",
                    "RegisterMemberDTO"
            ).contains(schemaName)) {
                softly.assertThat(reason)
                        .as("%s reason maximum", schemaName)
                        .containsEntry("maxLength", 2000);
            } else {
                softly.assertThat(reason)
                        .as("%s reason schema without raw maximum", schemaName)
                        .doesNotContainKey("maxLength");
            }
            softly.assertThat(description)
                    .as("%s reason description", schemaName)
                    .containsIgnoringCase("normalized")
                    .containsIgnoringCase("Unicode")
                    .contains("White_Space")
                    .containsIgnoringCase("1")
                    .containsIgnoringCase("2,000")
                    .containsIgnoringCase("code point");
        }

        softly.assertAll();
    }

    @Test
    @DisplayName("REQ-GAM-LOCATION-005 and REQ-GAM-LOCATION-006 - GamLocation operations -> specific conflict codes")
    void gamLocationOperationsShouldDocumentSpecificConflictCodes() {
        Map<String, Object> contract = openApiContract().jsonPath().getMap("$");
        Map<String, Object> paths = object(contract, "paths");

        assertConflictCode(object(object(paths, "/gam-locations"), "post"), "GAM_LOCATION_ALREADY_EXISTS");
        assertConflictCode(object(object(paths, "/gam-locations/{id}"), "put"), "GAM_LOCATION_ALREADY_EXISTS");
        assertConflictCode(object(object(paths, "/gam-locations/{id}"), "delete"), "GAM_LOCATION_IN_USE");
    }

    @Test
    @DisplayName("REQ-GAM-LOCATION-001 and REQ-GAM-LOCATION-CATALOG-002 - create/update success examples -> ordinary ownership representation")
    void gamLocationMutationSuccessExamplesShouldRepresentOrdinaryRecords() {
        Map<String, Object> contract = openApiContract().jsonPath().getMap("$");
        Map<String, Object> paths = object(contract, "paths");
        Map<String, Object> create = object(object(paths, "/gam-locations"), "post");
        Map<String, Object> update = object(object(paths, "/gam-locations/{id}"), "put");

        assertThat(responseExampleValues(create, "201"))
                .as("createGamLocation ordinary success example")
                .anySatisfy(this::assertOrdinaryGamLocationExample);
        assertThat(responseExampleValues(update, "200"))
                .as("updateGamLocation ordinary success example")
                .anySatisfy(this::assertOrdinaryGamLocationExample);
    }

    @Test
    @DisplayName("REQ-GAM-LOCATION-CATALOG-002 and REQ-OPENAPI-004 - direct and embedded GamLocation success examples -> valid ownership pairing")
    void gamLocationSuccessExamplesShouldUseValidOwnershipPairings() {
        Map<String, Object> contract = openApiContract().jsonPath().getMap("$");
        Map<String, Object> paths = object(contract, "paths");
        Map<String, List<Map<String, Object>>> representations = Map.of(
                "direct GamLocation",
                responseExampleValues(
                        object(object(paths, "/gam-locations/{id}"), "get"),
                        "200"
                ),
                "listed GamLocation",
                pagedGamLocationExamples(
                        object(object(paths, "/gam-locations"), "get"),
                        "200"
                ),
                "Event.gamLocation",
                responseExampleValues(
                        object(object(paths, "/events/{id}"), "get"),
                        "200"
                ).stream().map(example -> object(example, "gamLocation")).toList(),
                "Oratorio.event.gamLocation",
                responseExampleValues(
                        object(object(paths, "/oratorios/{oratorioId}"), "get"),
                        "200"
                ).stream()
                        .map(example -> object(object(example, "event"), "gamLocation"))
                        .toList()
        );
        SoftAssertions softly = new SoftAssertions();

        representations.forEach((label, examples) -> {
            softly.assertThat(examples)
                    .as("%s success examples", label)
                    .isNotEmpty();
            examples.forEach(example ->
                    assertValidGamLocationOwnershipPairing(softly, label, example)
            );
        });

        softly.assertAll();
    }

    @Test
    @DisplayName("REQ-GAM-LOCATION-CATALOG-004 and REQ-OPENAPI-004 - system update/removal -> documented FORBIDDEN_OPERATION response")
    void systemGamLocationMutationOperationsShouldDocumentForbiddenOperation() {
        Map<String, Object> contract = openApiContract().jsonPath().getMap("$");
        Map<String, Object> paths = object(contract, "paths");
        SoftAssertions softly = new SoftAssertions();

        for (Map.Entry<String, Map<String, Object>> operation : Map.of(
                "updateGamLocation", object(object(paths, "/gam-locations/{id}"), "put"),
                "removeGamLocation", object(object(paths, "/gam-locations/{id}"), "delete")
        ).entrySet()) {
            Map<String, Object> forbidden = object(
                    object(operation.getValue(), "responses"),
                    "403"
            );
            softly.assertThat(String.valueOf(forbidden.get("description")))
                    .as("%s 403 description", operation.getKey())
                    .contains("FORBIDDEN")
                    .contains("FORBIDDEN_OPERATION");
            softly.assertThat(responseExampleCodes(forbidden))
                    .as("%s 403 example codes", operation.getKey())
                    .contains("ACCESS_DENIED", "FORBIDDEN_OPERATION");
        }

        softly.assertAll();
    }

    @Test
    @DisplayName("REQ-GAM-LOCATION-005 - GamLocation creation -> Location response header")
    void gamLocationCreationShouldDocumentLocationResponseHeader() {
        Map<String, Object> contract = openApiContract().jsonPath().getMap("$");
        Map<String, Object> paths = object(contract, "paths");
        Map<String, Object> create = object(object(paths, "/gam-locations"), "post");
        Map<String, Object> created = object(object(create, "responses"), "201");
        Map<String, Object> headers = object(created, "headers");

        assertThat(headers).containsKey("Location");
        assertThat(object(headers, "Location")).satisfies(header ->
                assertThat(object(header, "schema")).containsEntry("type", "string")
        );
    }

    private Map<String, Object> schemas() {
        Map<String, Object> contract = openApiContract().jsonPath().getMap("$");
        return object(object(contract, "components"), "schemas");
    }

    private Map<String, Object> searchFilterProperties() {
        return object(object(schemas(), "SpecificationFilterDTO"), "properties");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> first(Collection<Object> values) {
        return (Map<String, Object>) values.iterator().next();
    }

    private Map<String, Object> resolveSchema(Map<String, Object> contract, Map<String, Object> schema) {
        if (schema.get("$ref") == null) {
            return schema;
        }
        String reference = String.valueOf(schema.get("$ref"));
        String schemaName = reference.substring(reference.lastIndexOf('/') + 1);
        return object(object(object(contract, "components"), "schemas"), schemaName);
    }

    @SuppressWarnings("unchecked")
    private void assertStrictSearchValueAlternatives(
            Map<String, Object> contract,
            Map<String, Object> value
    ) {
        assertThat(value)
                .as("Search filter value must not be an unconstrained JsonNode schema")
                .isNotEmpty()
                .containsOnlyKeys("oneOf")
                .containsKey("oneOf");
        List<Map<String, Object>> alternatives = (List<Map<String, Object>>) value.get("oneOf");
        assertThat(alternatives).hasSize(2);

        Map<String, Object> scalar = alternatives.stream()
                .map(alternative -> resolveSchema(contract, alternative))
                .filter(alternative -> "string".equals(alternative.get("type")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing nonblank scalar-string search value alternative"));
        Map<String, Object> array = alternatives.stream()
                .map(alternative -> resolveSchema(contract, alternative))
                .filter(alternative -> "array".equals(alternative.get("type")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing IN array search value alternative"));

        assertNonWhitespaceString(scalar, "scalar search value");
        assertThat(scalar).doesNotContainKeys("nullable", "additionalProperties");
        assertThat(array)
                .containsEntry("type", "array")
                .containsEntry("minItems", 1)
                .containsEntry("maxItems", 100)
                .doesNotContainKey("nullable");
        Map<String, Object> item = resolveSchema(contract, object(array, "items"));
        assertThat(item).containsEntry("type", "string");
        assertNonWhitespaceString(item, "IN search value item");
        assertThat(item).doesNotContainKeys("nullable", "additionalProperties");
    }

    private void assertNonWhitespaceString(Map<String, Object> schema, String label) {
        assertThat(schema).as(label).containsEntry("type", "string").containsKey("pattern");
        Pattern pattern = Pattern.compile(String.valueOf(schema.get("pattern")));
        assertThat(pattern.matcher("canonical").matches()).as(label + " accepts text").isTrue();
        for (int codePoint : UNICODE_WHITE_SPACE_CODE_POINTS) {
            String whitespace = new String(Character.toChars(codePoint));
            assertThat(pattern.matcher(whitespace).matches())
                    .as("%s rejects Unicode whitespace-only U+%04X", label, codePoint)
                    .isFalse();
        }
        assertThat(pattern.matcher("\u200B").matches())
                .as(label + " preserves ZERO WIDTH SPACE content")
                .isTrue();
        assertThat(pattern.matcher(new String(Character.toChars(0x1F642))).matches())
                .as(label + " accepts supplementary content")
                .isTrue();
    }

    private void assertSearchErrorExample(
            Map<String, Object> examples,
            String exampleName,
            String expectedCode,
            String route
    ) {
        Map<String, Object> wrapper = object(examples, exampleName);
        Map<String, Object> value = object(wrapper, "value");
        assertThat(value)
                .as(route + " " + expectedCode)
                .containsOnlyKeys("timestamp", "status", "code", "message", "details")
                .containsEntry("status", 400)
                .containsEntry("code", expectedCode);
        Instant.parse(String.valueOf(value.get("timestamp")));
        assertThat(value.get("message")).isInstanceOf(String.class);
        assertThat(String.valueOf(value.get("message"))).isNotBlank();
        assertThat(value.get("details")).isInstanceOf(Map.class);

        if ("INVALID_PARAMETER_TYPE".equals(expectedCode)) {
            assertThat(object(value, "details"))
                    .as(route + " query parameter conversion details")
                    .containsExactlyInAnyOrderEntriesOf(Map.of(
                            "location", "query",
                            "field", "page",
                            "expectedType", "INTEGER"
                    ));
        }

        if ("INVALID_SEARCH_FILTER".equals(expectedCode)) {
            Map<String, Object> details = object(value, "details");
            assertThat(details)
                    .as(route + " mandatory safe semantic-filter location")
                    .containsEntry("filterIndex", 0);
            assertThat(details.keySet())
                    .as(route + " safe semantic-filter detail keys")
                    .isSubsetOf("filterIndex", "field", "comparisonMethod");

            if (details.get("field") != null) {
                String field = String.valueOf(details.get("field"));
                assertThat(searchFieldDocumentation().get(route))
                        .extracting(SearchFieldDocumentation::field)
                        .contains(field);
            }
            if (details.get("comparisonMethod") != null) {
                assertThat(String.valueOf(details.get("comparisonMethod"))).isIn(
                        "EQUALS",
                        "LIKE",
                        "IN",
                        "GREATER_THAN_OR_EQUAL",
                        "LESS_THAN_OR_EQUAL"
                );
            }
            assertThat(details).doesNotContainKeys("value", "submittedValue", "internalPath");
        }
    }

    private void assertFieldMethodAndValueDocumentation(
            String description,
            String route,
            SearchFieldDocumentation contract
    ) {
        String fieldSentence = fieldSentence(description, route, contract.field());
        List<String> documentedMethods = List.of(
                        "EQUALS",
                        "LIKE",
                        "IN",
                        "GREATER_THAN_OR_EQUAL",
                        "LESS_THAN_OR_EQUAL"
                ).stream()
                .filter(method -> Pattern.compile("\\b" + method + "\\b").matcher(fieldSentence).find())
                .toList();
        assertThat(documentedMethods)
                .as("%s field %s allowed methods", route, contract.field())
                .containsExactlyElementsOf(contract.methods());
        assertThat(fieldSentence)
                .as("%s field %s value semantics", route, contract.field())
                .containsPattern("(?is).*\\bvalue\\b.{0,80}(?:" + contract.valuePattern() + ").*");
    }

    private String fieldSentence(String description, String route, String field) {
        List<String> fieldSentences = Stream.of(description.split("(?<=\\.)\\s+"))
                .map(String::trim)
                .filter(sentence -> sentence.startsWith(field + " allows "))
                .toList();
        assertThat(fieldSentences)
                .as("%s must document field %s in one dedicated contract sentence", route, field)
                .hasSize(1);
        return fieldSentences.get(0);
    }

    private void assertExampleValueCompatible(
            String route,
            String field,
            String comparisonMethod,
            Object submittedValue
    ) {
        List<?> scalarValues;
        if ("IN".equals(comparisonMethod)) {
            assertThat(submittedValue).as(route + " IN example value").isInstanceOf(List.class);
            scalarValues = (List<?>) submittedValue;
            assertThat(scalarValues).as(route + " IN example items").hasSizeBetween(1, 100);
        } else {
            assertThat(submittedValue).as(route + " scalar example value").isInstanceOf(String.class);
            scalarValues = List.of(submittedValue);
        }

        for (Object scalarValue : scalarValues) {
            assertThat(scalarValue).as(route + " example scalar").isInstanceOf(String.class);
            String value = String.valueOf(scalarValue);
            assertThat(value).as(route + " example scalar").isNotBlank();

            if (Set.of(
                    "id", "accountId", "gamLocationId", "requiredPermissionId",
                    "reviewedByAccountId"
            ).contains(field)) {
                assertThat(value).as(route + " canonical UUID example").hasSize(36);
                UUID.fromString(value);
            } else if (Set.of(
                    "createdAt", "updatedAt", "beginDate", "endDate",
                    "submittedAt", "decidedAt"
            ).contains(field)) {
                assertThat(value).as(route + " UTC timestamp example").endsWith("Z");
                Instant.parse(value);
            } else if ("birthDate".equals(field)) {
                LocalDate.parse(value);
            } else if ("email".equals(field)) {
                assertThat(value).as(route + " email example").hasSizeGreaterThanOrEqualTo(3);
                if ("EQUALS".equals(comparisonMethod)) {
                    assertThat(value).contains("@");
                    assertThat(value.substring(value.indexOf('@') + 1)).contains(".");
                }
            } else if ("phoneNumber".equals(field)) {
                assertThat(value.replaceAll("\\D", ""))
                        .as(route + " phone example digits")
                        .hasSizeGreaterThanOrEqualTo(4);
            } else if (Set.of("status", "type", "role", "requiredPermissionCode").contains(field)) {
                assertThat(value).as(route + " canonical code example").matches("[A-Z][A-Z0-9_]*");
            }
        }
    }

    private Map<String, Map<String, List<String>>> searchFieldSemantics() {
        return Map.of(
                "/accounts/search", Map.of(
                        "id", List.of("canonical.{0,20}UUID"),
                        "email", List.of("(?:minimum|min).{0,20}3", "@", "(?:dot|period)"),
                        "displayName", List.of("1.{0,20}50", "case[- ]sensitive"),
                        "role", List.of("case[- ]sensitive", "(?:active|current)"),
                        "createdAt", List.of("RFC.?3339", "(?:UTC|Z)"),
                        "updatedAt", List.of("RFC.?3339", "(?:UTC|Z)")
                ),
                "/events/search", Map.ofEntries(
                        Map.entry("id", List.of("canonical.{0,20}UUID")),
                        Map.entry("title", List.of("1.{0,20}255", "case[- ]sensitive")),
                        Map.entry("description", List.of("nonblank", "10,?000")),
                        Map.entry("gamLocationId", List.of("canonical.{0,20}UUID")),
                        Map.entry("requiredPermissionId", List.of("UUID", "null(?:able)?")),
                        Map.entry("requiredPermissionCode", List.of("(?:active|current)", "null(?:able)?")),
                        Map.entry("type", List.of("uppercase", "enum", "GENERIC", "ORATORIO", "MISSA")),
                        Map.entry("status", List.of(
                                "effective", "single request instant", "SCHEDULED",
                                "COMPLETED", "LOCKED", "FINALIZED", "CANCELLED"
                        )),
                        Map.entry("beginDate", List.of("RFC.?3339", "(?:UTC|Z)")),
                        Map.entry("endDate", List.of("RFC.?3339", "(?:UTC|Z)"))
                ),
                "/members/search", Map.of(
                        "id", List.of("canonical.{0,20}UUID"),
                        "name", List.of("(?:trim|boundary)", "collapse.{0,30}whitespace", "diacritic", "punctuation"),
                        "birthDate", List.of("(?:ISO.?8601|yyyy-MM-dd)"),
                        "phoneNumber", List.of("E.?164", "(?:remove|ignore).{0,30}format", "(?:minimum|min).{0,20}4"),
                        "status", List.of("uppercase", "enum", "ACTIVE", "INACTIVE"),
                        "accountId", List.of("canonical.{0,20}UUID"),
                        "email", List.of("(?:minimum|min).{0,20}3", "@", "(?:dot|period)"),
                        "role", List.of("case[- ]sensitive", "(?:active|current)"),
                        "createdAt", List.of("RFC.?3339", "(?:UTC|Z)"),
                        "updatedAt", List.of("RFC.?3339", "(?:UTC|Z)")
                ),
                "/membership-solicitations/search", Map.of(
                        "id", List.of("canonical.{0,20}UUID"),
                        "accountId", List.of("canonical.{0,20}UUID"),
                        "email", List.of("(?:minimum|min).{0,20}3", "@", "(?:dot|period)"),
                        "name", List.of("immutable", "collapse.{0,30}whitespace", "diacritic", "punctuation"),
                        "status", List.of("uppercase", "enum", "PENDING", "APPROVED", "REJECTED"),
                        "submittedAt", List.of("RFC.?3339", "(?:UTC|Z)"),
                        "decidedAt", List.of("null(?:able)?", "pending.{0,30}(?:no match|does not match)", "(?:UTC|Z)"),
                        "reviewedByAccountId", List.of("UUID", "null(?:able)?", "pending.{0,30}(?:no match|does not match)")
                ),
                "/oratorianos/search", Map.of(
                        "id", List.of("canonical.{0,20}UUID"),
                        "name", List.of(
                                "collapse.{0,30}whitespace",
                                "case[- ]insensitive",
                                "diacritic[- ]insensitive",
                                "punctuation.{0,30}(?:meaningful|preserv)",
                                "EQUALS.{0,30}(?:full|complete)",
                                "LIKE.{0,30}substring"
                        )
                )
        );
    }

    private Map<String, List<SearchFieldDocumentation>> searchFieldDocumentation() {
        return Map.of(
                "/accounts/search", List.of(
                        fieldDocumentation("id", "uuid", "EQUALS", "IN"),
                        fieldDocumentation("email", "email", "EQUALS", "LIKE"),
                        fieldDocumentation("displayName", "text|string|character", "EQUALS", "LIKE"),
                        fieldDocumentation("role", "role|name", "EQUALS", "IN"),
                        fieldDocumentation("createdAt", "instant|date-time|timestamp",
                                "GREATER_THAN_OR_EQUAL", "LESS_THAN_OR_EQUAL"),
                        fieldDocumentation("updatedAt", "instant|date-time|timestamp",
                                "GREATER_THAN_OR_EQUAL", "LESS_THAN_OR_EQUAL")
                ),
                "/events/search", List.of(
                        fieldDocumentation("id", "uuid", "EQUALS", "IN"),
                        fieldDocumentation("title", "text|string|character", "EQUALS", "LIKE"),
                        fieldDocumentation("description", "text|string|character", "LIKE"),
                        fieldDocumentation("gamLocationId", "uuid", "EQUALS", "IN"),
                        fieldDocumentation("requiredPermissionId", "uuid", "EQUALS", "IN"),
                        fieldDocumentation("requiredPermissionCode", "code|string", "EQUALS", "IN"),
                        fieldDocumentation("type", "enum|uppercase", "EQUALS", "IN"),
                        fieldDocumentation("status", "enum|uppercase", "EQUALS", "IN"),
                        fieldDocumentation("beginDate", "instant|date-time|timestamp",
                                "GREATER_THAN_OR_EQUAL", "LESS_THAN_OR_EQUAL"),
                        fieldDocumentation("endDate", "instant|date-time|timestamp",
                                "GREATER_THAN_OR_EQUAL", "LESS_THAN_OR_EQUAL")
                ),
                "/members/search", List.of(
                        fieldDocumentation("id", "uuid", "EQUALS", "IN"),
                        fieldDocumentation("name", "full[- ]name|name|string", "LIKE"),
                        fieldDocumentation("birthDate", "date", "EQUALS",
                                "GREATER_THAN_OR_EQUAL", "LESS_THAN_OR_EQUAL"),
                        fieldDocumentation("phoneNumber", "phone|string", "EQUALS", "LIKE"),
                        fieldDocumentation("status", "enum|uppercase", "EQUALS", "IN"),
                        fieldDocumentation("accountId", "uuid", "EQUALS"),
                        fieldDocumentation("email", "email", "EQUALS", "LIKE"),
                        fieldDocumentation("role", "role|name", "EQUALS", "IN"),
                        fieldDocumentation("createdAt", "instant|date-time|timestamp",
                                "GREATER_THAN_OR_EQUAL", "LESS_THAN_OR_EQUAL"),
                        fieldDocumentation("updatedAt", "instant|date-time|timestamp",
                                "GREATER_THAN_OR_EQUAL", "LESS_THAN_OR_EQUAL")
                ),
                "/membership-solicitations/search", List.of(
                        fieldDocumentation("id", "uuid", "EQUALS", "IN"),
                        fieldDocumentation("accountId", "uuid", "EQUALS"),
                        fieldDocumentation("email", "email", "EQUALS", "LIKE"),
                        fieldDocumentation("name", "full[- ]name|name|string", "LIKE"),
                        fieldDocumentation("status", "enum|uppercase", "EQUALS", "IN"),
                        fieldDocumentation("submittedAt", "instant|date-time|timestamp",
                                "GREATER_THAN_OR_EQUAL", "LESS_THAN_OR_EQUAL"),
                        fieldDocumentation("decidedAt", "instant|date-time|timestamp",
                                "GREATER_THAN_OR_EQUAL", "LESS_THAN_OR_EQUAL"),
                        fieldDocumentation("reviewedByAccountId", "uuid", "EQUALS")
                ),
                "/oratorianos/search", List.of(
                        fieldDocumentation("id", "uuid", "EQUALS", "IN"),
                        fieldDocumentation("name", "human[- ]equivalent|full[- ]name|name|string", "EQUALS", "LIKE")
                )
        );
    }

    private SearchFieldDocumentation fieldDocumentation(
            String field,
            String valuePattern,
            String... methods
    ) {
        return new SearchFieldDocumentation(field, List.of(methods), valuePattern);
    }

    private record SearchFieldDocumentation(
            String field,
            List<String> methods,
            String valuePattern
    ) {
    }

    private void assertLength(Map<String, Object> properties, String property, int min, int max) {
        assertThat(object(properties, property))
                .containsEntry("minLength", min)
                .containsEntry("maxLength", max);
    }

    private void assertCoordinate(Map<String, Object> property, double minimum, double maximum) {
        assertThat(((Number) property.get("minimum")).doubleValue()).isEqualTo(minimum);
        assertThat(((Number) property.get("maximum")).doubleValue()).isEqualTo(maximum);
        assertThat(property).containsKey("multipleOf");
    }

    private void assertNullType(Map<String, Object> property) {
        boolean nullableType = property.get("type") instanceof List<?> types && types.contains("null");
        assertThat(nullableType)
                .as("nullable OpenAPI schema property: %s", property)
                .isTrue();
    }

    private void assertConflictCode(Map<String, Object> operation, String expectedCode) {
        Map<String, Object> response = object(object(operation, "responses"), "409");
        Map<String, Object> content = object(response, "content");
        Map<String, Object> mediaType = first(content.values());
        Map<String, Object> example = object(mediaType, "example");
        assertThat(example).containsEntry("code", expectedCode);
    }

    private void assertOrdinaryGamLocationExample(Map<String, Object> example) {
        assertThat(example)
                .containsEntry("code", null)
                .containsEntry("systemManaged", false);
    }

    private void assertValidGamLocationOwnershipPairing(
            SoftAssertions softly,
            String label,
            Map<String, Object> example
    ) {
        softly.assertThat(example)
                .as("%s ownership fields", label)
                .containsKeys("code", "systemManaged");
        Object code = example.get("code");
        Object systemManaged = example.get("systemManaged");
        softly.assertThat(systemManaged)
                .as("%s systemManaged", label)
                .isInstanceOf(Boolean.class);

        if (Boolean.TRUE.equals(systemManaged)) {
            softly.assertThat(code)
                    .as("%s system code", label)
                    .isIn(CURRENT_SYSTEM_GAM_LOCATION_CODES.toArray());
        } else {
            softly.assertThat(code)
                    .as("%s ordinary code", label)
                    .isNull();
        }
    }

    private List<Map<String, Object>> responseExampleValues(
            Map<String, Object> operation,
            String status
    ) {
        Map<String, Object> response = object(object(operation, "responses"), status);
        Map<String, Object> mediaType = first(object(response, "content").values());
        return exampleValues(mediaType);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> pagedGamLocationExamples(
            Map<String, Object> operation,
            String status
    ) {
        List<Map<String, Object>> values = new ArrayList<>();
        for (Map<String, Object> example : responseExampleValues(operation, status)) {
            Object items = example.get("items");
            assertThat(items).as("paged GamLocation example items").isInstanceOf(List.class);
            for (Object item : (List<?>) items) {
                assertThat(item).as("paged GamLocation example item").isInstanceOf(Map.class);
                values.add((Map<String, Object>) item);
            }
        }
        return values;
    }

    private Set<String> responseExampleCodes(Map<String, Object> response) {
        Map<String, Object> mediaType = first(object(response, "content").values());
        return exampleValues(mediaType).stream()
                .map(example -> example.get("code"))
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .collect(java.util.stream.Collectors.toSet());
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> exampleValues(Map<String, Object> mediaType) {
        List<Map<String, Object>> values = new ArrayList<>();
        if (mediaType.get("example") instanceof Map<?, ?> example) {
            values.add((Map<String, Object>) example);
        }
        if (mediaType.get("examples") instanceof Map<?, ?> examples) {
            for (Object wrapper : examples.values()) {
                if (wrapper instanceof Map<?, ?> exampleWrapper
                        && exampleWrapper.get("value") instanceof Map<?, ?> value) {
                    values.add((Map<String, Object>) value);
                }
            }
        }
        return values;
    }
}
