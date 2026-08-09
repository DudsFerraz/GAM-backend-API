package br.org.gam.api.api;

import br.org.gam.api.testing.annotation.ApiTest;
import br.org.gam.api.testing.annotation.FunctionalTest;
import br.org.gam.api.testing.annotation.IntegrationTest;
import br.org.gam.api.testing.annotation.SecurityTest;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@ApiTest
@FunctionalTest
@IntegrationTest
@SecurityTest
@DisplayName("API - OpenAPI documentation contract")
class OpenApiDocumentationApiIT extends AbstractOpenApiDocumentationApiIT {

    private static final Map<String, String> MEMBER_REPLACEMENT_PATHS = Map.of(
            "/members/{memberId}", "updateMemberProfile",
            "/members/{memberId}/gam-entry-date", "updateMemberGamEntryDate",
            "/members/{memberId}/dietary-restriction", "updateMemberDietaryRestriction",
            "/members/{memberId}/experiences", "updateMemberExperiences",
            "/members/{memberId}/sacraments", "updateMemberSacraments",
            "/members/{memberId}/contribution-profile", "updateMemberContributionProfile"
    );

    @Test
    @DisplayName("REQ-MEMBER-INFO-009/011 - all Member replacements -> exact 204 success with aggregate ETag")
    void memberReplacementOperationsShouldDocumentExactSuccessAndEtag() {
        Map<String, Object> paths = object(openApiContract().body(), "paths");
        SoftAssertions softly = new SoftAssertions();

        MEMBER_REPLACEMENT_PATHS.forEach((path, operationId) -> {
            Map<String, Object> operation = object(object(paths, path), "put");
            softly.assertThat(operation).as(path + " PUT").containsEntry("operationId", operationId);
            Map<String, Object> responses = object(operation, "responses");
            softly.assertThat(responses).as(path + " responses").containsKey("204").doesNotContainKey("200");
            Map<String, Object> success = object(responses, "204");
            softly.assertThat(success).as(path + " 204 response").doesNotContainKey("content");
            softly.assertThat(object(object(success, "headers"), "ETag"))
                    .as(path + " success ETag").isNotNull();
        });
        softly.assertAll();
    }

    @Test
    @DisplayName("REQ-MEMBER-INFO-005/008 - experience and sacrament maps -> exact closed property catalogs")
    void experienceAndSacramentResponseShouldDocumentClosedCatalogMaps() {
        Map<String, Object> contract = openApiContract().body();
        Map<String, Object> operation = object(
                object(object(contract, "paths"), "/members/{memberId}/experiences-and-sacraments"), "get");
        Map<String, Object> success = object(object(operation, "responses"), "200");
        Map<String, Object> content = object(success, "content");
        Map<String, Object> media = content.containsKey("application/json")
                ? object(content, "application/json") : object(content, "*/*");
        Map<String, Object> responseSchema = resolveSchema(contract, object(media, "schema"));
        Map<String, Object> properties = object(responseSchema, "properties");
        Map<String, Object> experiences = resolveSchema(contract, object(properties, "experiences"));
        Map<String, Object> sacraments = resolveSchema(contract, object(properties, "sacraments"));
        List<String> experienceKeys = List.of(
                "JORNADA_MISSIONARIA", "CURSO_DE_LIDERANCA", "PASCOA_JUVENIL", "ACAMPABOSCO");
        List<String> sacramentKeys = List.of("BATISMO", "PRIMEIRA_COMUNHAO", "CRISMA");

        assertThat(responseSchema).containsEntry("additionalProperties", false);
        assertThat(properties).containsOnlyKeys("experiences", "sacraments");
        assertThat(strings(responseSchema, "required")).containsExactlyInAnyOrder("experiences", "sacraments");
        assertThat(object(experiences, "properties")).containsOnlyKeys(experienceKeys.toArray(String[]::new));
        assertThat(strings(experiences, "required")).containsExactlyInAnyOrderElementsOf(experienceKeys);
        assertThat(object(sacraments, "properties")).containsOnlyKeys(sacramentKeys.toArray(String[]::new));
        assertThat(strings(sacraments, "required")).containsExactlyInAnyOrderElementsOf(sacramentKeys);
        assertThat(experiences).containsEntry("additionalProperties", false);
        assertThat(sacraments).containsEntry("additionalProperties", false);
        Map<String, Object> example = object(media, "example");
        assertThat(example).containsOnlyKeys("experiences", "sacraments");
        assertThat(object(example, "experiences")).containsOnlyKeys(experienceKeys.toArray(String[]::new));
        assertThat(object(example, "sacraments")).containsOnlyKeys(sacramentKeys.toArray(String[]::new));
    }

    @Test
    @DisplayName("REQ-MEMBER-INFO-007/008 - contribution GET documents response-only shape without audit reason")
    void contributionProfileReadShouldExcludeRequestOnlyReasonFromSchemaAndExample() {
        Map<String, Object> contract = openApiContract().body();
        Map<String, Object> operation = object(
                object(object(contract, "paths"), "/members/{memberId}/contribution-profile"), "get");
        Map<String, Object> success = object(object(operation, "responses"), "200");
        Map<String, Object> content = object(success, "content");
        Map<String, Object> media = content.containsKey("application/json")
                ? object(content, "application/json") : object(content, "*/*");
        Map<String, Object> responseSchema = resolveSchema(contract, object(media, "schema"));
        Map<String, Object> properties = object(responseSchema, "properties");
        Map<String, Object> contribution = resolveSchema(contract, object(properties, "contributionProfile"));

        assertThat(responseSchema).containsEntry("additionalProperties", false);
        assertThat(properties).containsOnlyKeys("contributionProfile").doesNotContainKey("reason");
        assertThat(strings(responseSchema, "required")).containsExactly("contributionProfile");
        assertThat(contribution).containsEntry("additionalProperties", false);
        assertThat(object(contribution, "properties"))
                .containsOnlyKeys("contributionAreas", "otherContributionAreas")
                .doesNotContainKey("reason");
        assertThat(strings(contribution, "required"))
                .containsExactlyInAnyOrder("contributionAreas", "otherContributionAreas");
        Map<String, Object> example = object(media, "example");
        assertThat(example).containsOnlyKeys("contributionProfile").doesNotContainKey("reason");
        assertThat(object(example, "contributionProfile"))
                .containsOnlyKeys("contributionAreas", "otherContributionAreas")
                .doesNotContainKey("reason");
    }

    @Test
    @DisplayName("REQ-MEMBER-INFO-014/015 - annual schema -> complete required nullable contract and safe example")
    void annualInformationShouldDocumentRequiredNullableShapeAndRealisticExample() {
        Map<String, Object> contract = openApiContract().body();
        Map<String, Object> operation = object(
                object(object(contract, "paths"), "/members/{memberId}/annual-information/{surveyCycle}"), "get");
        Map<String, Object> success = object(object(operation, "responses"), "200");
        Map<String, Object> content = object(success, "content");
        Map<String, Object> media = content.containsKey("application/json")
                ? object(content, "application/json") : object(content, "*/*");
        Map<String, Object> schema = resolveSchema(contract, object(media, "schema"));
        Map<String, Object> properties = object(schema, "properties");
        List<String> completeShape = List.of(
                "id", "surveyCycle", "submittedAt", "occupations", "healthCondition",
                "religiousVocationConsidered", "massAttendanceFrequency", "saturdayOratorioImpediment",
                "formationAndMeetingInterests", "coordinationInterest", "additionalComments",
                "oratorioActivitySuggestions", "instagramPostSuggestions"
        );
        assertThat(properties).containsOnlyKeys(completeShape.toArray(String[]::new));
        assertThat(strings(schema, "required")).containsExactlyInAnyOrderElementsOf(completeShape);
        assertThat(schema).containsEntry("additionalProperties", false);
        for (String nullable : List.of("submittedAt", "formationAndMeetingInterests", "additionalComments",
                "oratorioActivitySuggestions", "instagramPostSuggestions")) {
            assertThat(object(properties, nullable).get("type"))
                    .as(nullable + " nullability")
                    .isEqualTo(List.of("string", "null"));
        }
        Map<String, Object> occupations = resolveSchema(contract, object(properties, "occupations"));
        Map<String, Object> health = resolveSchema(contract, object(properties, "healthCondition"));
        Map<String, Object> impediment = resolveSchema(contract, object(properties, "saturdayOratorioImpediment"));
        assertThat(occupations).containsEntry("additionalProperties", false);
        assertThat(health).containsEntry("additionalProperties", false);
        assertThat(impediment).containsEntry("additionalProperties", false);
        assertThat(strings(occupations, "required")).containsExactlyInAnyOrder("values", "details");
        assertThat(strings(health, "required")).containsExactlyInAnyOrder("status", "details");
        assertThat(strings(impediment, "required")).containsExactlyInAnyOrder("status", "details");
        assertThat(object(object(occupations, "properties"), "details").get("type"))
                .as("occupations.details nullability").isEqualTo(List.of("string", "null"));
        assertThat(object(object(health, "properties"), "details").get("type"))
                .as("healthCondition.details nullability").isEqualTo(List.of("string", "null"));
        assertThat(object(object(impediment, "properties"), "details").get("type"))
                .as("saturdayOratorioImpediment.details nullability").isEqualTo(List.of("string", "null"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> parameters = (List<Map<String, Object>>) operation.get("parameters");
        assertThat(parameters)
                .extracting(parameter -> parameter.get("name"))
                .containsExactlyInAnyOrder("memberId", "surveyCycle");
        Map<String, Object> example = object(media, "example");
        assertThat(example).containsOnlyKeys(completeShape.toArray(String[]::new))
                .doesNotContainEntry("surveyCycle", 1).doesNotContainKey("reason");
        assertThat(object(example, "occupations")).containsOnlyKeys("values", "details");
        assertThat(object(example, "healthCondition")).containsOnlyKeys("status", "details");
        assertThat(object(example, "saturdayOratorioImpediment")).containsOnlyKeys("status", "details");
        assertThat(example.toString()).doesNotContain("experiences={}", "sacraments={}", "experiences: {}", "sacraments: {}");
    }

    @Test
    @DisplayName("REQ-MEMBER-INFO-007 - Member response -> exact required properties, nullable account, and closed nested objects")
    void memberResponseShouldDocumentExactRequiredNullableAndClosedShape() {
        Map<String, Object> contract = openApiContract().body();
        Map<String, Object> schemas = object(object(contract, "components"), "schemas");
        Map<String, Object> member = object(schemas, "MemberRDTO");
        Map<String, Object> properties = object(member, "properties");
        List<String> completeShape = List.of(
                "id", "account", "firstName", "surname", "birthDate", "gamEntryDate",
                "residentialCity", "phoneNumber", "contactEmail", "dietaryRestriction", "status"
        );

        assertThat(member).containsEntry("additionalProperties", false);
        assertThat(properties).containsOnlyKeys(completeShape.toArray(String[]::new));
        assertThat(strings(member, "required")).containsExactlyInAnyOrderElementsOf(completeShape);
        assertThat(object(properties, "account").get("type"))
                .as("Member.account nullability")
                .isEqualTo(List.of("object", "null"));

        Map<String, Object> account = resolveSchema(contract, object(properties, "account"));
        assertThat(account).containsEntry("additionalProperties", false);
        assertThat(object(account, "properties")).containsOnlyKeys("id", "email", "displayName");
        assertThat(strings(account, "required")).containsExactlyInAnyOrder("id", "email", "displayName");

        Map<String, Object> dietary = resolveSchema(contract, object(properties, "dietaryRestriction"));
        assertThat(dietary).containsEntry("additionalProperties", false);
        assertThat(object(dietary, "properties")).containsOnlyKeys("status", "details");
        assertThat(strings(dietary, "required")).containsExactlyInAnyOrder("status", "details");
        assertThat(object(object(dietary, "properties"), "details").get("type"))
                .as("Member.dietaryRestriction.details nullability")
                .isEqualTo(List.of("string", "null"));
    }

    @Test
    @DisplayName("REQ-MEMBER-INFO-004/005/006/009 - Member information requests -> exact catalogs and nullable dietary details")
    void memberInformationRequestsShouldDocumentClosedCatalogsAndFixedContributionEnum() {
        Map<String, Object> contract = openApiContract().body();
        Map<String, Object> paths = object(contract, "paths");
        Map<String, Object> experiencesRequest = requestSchema(
                contract, object(object(paths, "/members/{memberId}/experiences"), "put"));
        Map<String, Object> sacramentRequest = requestSchema(
                contract, object(object(paths, "/members/{memberId}/sacraments"), "put"));

        assertThat(strings(experiencesRequest, "required"))
                .containsExactlyInAnyOrder("experiences", "reason");
        assertThat(strings(sacramentRequest, "required"))
                .containsExactlyInAnyOrder("sacraments", "reason");
        assertClosedCatalog(
                resolveSchema(contract, object(object(experiencesRequest, "properties"), "experiences")),
                List.of("JORNADA_MISSIONARIA", "CURSO_DE_LIDERANCA", "PASCOA_JUVENIL", "ACAMPABOSCO")
        );
        assertClosedCatalog(
                resolveSchema(contract, object(object(sacramentRequest, "properties"), "sacraments")),
                List.of("BATISMO", "PRIMEIRA_COMUNHAO", "CRISMA")
        );

        Map<String, Object> contributionRequest = requestSchema(
                contract, object(object(paths, "/members/{memberId}/contribution-profile"), "put"));
        assertThat(strings(contributionRequest, "required"))
                .containsExactlyInAnyOrder("contributionAreas", "otherContributionAreas", "reason");
        Map<String, Object> contributionAreas = object(object(contributionRequest, "properties"), "contributionAreas");
        Map<String, Object> fixedItem = resolveSchema(contract, object(contributionAreas, "items"));
        assertThat(fixedItem.get("enum")).isEqualTo(List.of(
                "GAME_REFEREE", "CRAFTS", "MUSIC", "PRAYER_LEADERSHIP", "BOA_TARDE_STORYTELLING",
                "DANCE", "BALLOON_SCULPTURE", "FOOTBALL", "VOLLEYBALL", "BASKETBALL", "HANDBALL",
                "PHOTOGRAPHY_AND_VIDEO", "PUBLIC_READING", "FACE_PAINTING", "FIRST_AID",
                "GINCANA_LEADERSHIP", "TECHNOLOGY", "TERERE"
        ));

        Map<String, Object> dietaryRequest = requestSchema(
                contract, object(object(paths, "/members/{memberId}/dietary-restriction"), "put"));
        assertThat(dietaryRequest).containsEntry("additionalProperties", false);
        assertThat(object(dietaryRequest, "properties")).containsOnlyKeys("status", "details", "reason");
        assertThat(strings(dietaryRequest, "required"))
                .containsExactlyInAnyOrder("status", "details", "reason");
        assertThat(object(object(dietaryRequest, "properties"), "details").get("type"))
                .as("dietary request details nullability")
                .isEqualTo(List.of("string", "null"));
    }

    @Test
    @DisplayName("REQ-MEMBER-INFO-002/003/004/007 and REQ-OPENAPI-003/004 - normalized bounds and collection semantics are documented")
    void memberInformationSchemasShouldDocumentNormalizedBoundsAndCollectionSemantics() {
        Map<String, Object> contract = openApiContract().body();
        Map<String, Object> schemas = object(object(contract, "components"), "schemas");
        List<String> memberInformationSchemas = List.of(
                "Core", "GamEntryDate", "DietaryRestriction", "Experiences", "Sacraments",
                "ContributionProfile", "RegisterMemberDTO"
        );
        assertThat(schemas).containsKeys(
                "Core", "GamEntryDate", "DietaryRestriction", "Experiences", "Sacraments",
                "ContributionProfile", "RegisterMemberDTO", "SubmitMembershipSolicitationDTO"
        );

        assertUnicodeBoundedText(
                object(object(object(schemas, "Core"), "properties"), "residentialCity"),
                100, "Core.residentialCity"
        );
        assertUnicodeBoundedText(
                object(object(object(schemas, "RegisterMemberDTO"), "properties"), "residentialCity"),
                100, "RegisterMemberDTO.residentialCity"
        );
        assertUnicodeBoundedText(
                object(object(object(schemas, "SubmitMembershipSolicitationDTO"), "properties"), "residentialCity"),
                100, "SubmitMembershipSolicitationDTO.residentialCity"
        );

        for (String schemaName : memberInformationSchemas) {
            assertNormalizedReason(
                    object(object(object(schemas, schemaName), "properties"), "reason"),
                    schemaName + ".reason"
            );
        }
        assertTrimmedBoundedText(
                object(object(object(schemas, "SubmitMembershipSolicitationDTO"), "properties"), "justification"),
                "SubmitMembershipSolicitationDTO.justification"
        );
        assertNullableUnicodeBoundedText(
                object(object(object(schemas, "DietaryRestriction"), "properties"), "details"),
                2000, "DietaryRestriction.details"
        );

        Map<String, Object> contribution = object(schemas, "ContributionProfile");
        Map<String, Object> contributionAreas = object(object(contribution, "properties"), "contributionAreas");
        assertThat(contributionAreas)
                .as("ContributionProfile.contributionAreas")
                .containsEntry("type", "array")
                .containsEntry("uniqueItems", true)
                .containsEntry("maxItems", 18);

        Map<String, Object> customAreas = object(object(contribution, "properties"), "otherContributionAreas");
        assertThat(customAreas)
                .as("ContributionProfile.otherContributionAreas")
                .containsEntry("type", "array")
                .containsEntry("uniqueItems", true)
                .containsEntry("maxItems", 10);
        assertUnicodeBoundedText(
                resolveSchema(contract, object(customAreas, "items")),
                100, "ContributionProfile.otherContributionAreas[]"
        );
    }

    @Test
    @DisplayName("REQ-MEMBER-INFO-007/014/015 and REQ-OPENAPI-004 - response schemas -> semantic bounds and set semantics")
    void memberInformationResponseSchemasShouldDocumentSemanticBoundsAndSetSemantics() {
        Map<String, Object> contract = openApiContract().body();
        Map<String, Object> schemas = object(object(contract, "components"), "schemas");
        SoftAssertions softly = new SoftAssertions();

        Map<String, Object> annual = object(schemas, "AnnualMemberInformationRDTO");
        Map<String, Object> annualProperties = object(annual, "properties");
        for (String field : List.of(
                "formationAndMeetingInterests",
                "additionalComments",
                "oratorioActivitySuggestions",
                "instagramPostSuggestions"
        )) {
            assertSoftNullableUnicodeBoundedText(
                    softly,
                    object(annualProperties, field),
                    2000,
                    "AnnualMemberInformationRDTO." + field
            );
        }

        Map<String, Object> occupations = resolveSchema(contract, object(annualProperties, "occupations"));
        Map<String, Object> occupationProperties = object(occupations, "properties");
        Map<String, Object> occupationValues = object(occupationProperties, "values");
        softly.assertThat(occupationValues)
                .as("Occupations.values")
                .containsEntry("type", "array")
                .containsEntry("maxItems", 4)
                .containsEntry("uniqueItems", true);
        softly.assertThat(resolveSchema(contract, object(occupationValues, "items")).get("enum"))
                .as("Occupations.values catalog")
                .isEqualTo(List.of("WORK", "UNIVERSITY", "PREP_COURSE", "OTHER"));
        assertSoftNullableUnicodeBoundedText(
                softly,
                object(occupationProperties, "details"),
                2000,
                "Occupations.details"
        );

        for (String field : List.of("healthCondition", "saturdayOratorioImpediment")) {
            Map<String, Object> statusDetails = resolveSchema(contract, object(annualProperties, field));
            assertSoftNullableUnicodeBoundedText(
                    softly,
                    object(object(statusDetails, "properties"), "details"),
                    2000,
                    "AnnualMemberInformationRDTO." + field + ".details"
            );
        }

        Map<String, Object> memberProperties = object(object(schemas, "MemberRDTO"), "properties");
        assertSoftUnicodeBoundedText(
                softly,
                object(memberProperties, "residentialCity"),
                100,
                "MemberRDTO.residentialCity"
        );
        Map<String, Object> dietary = resolveSchema(contract, object(memberProperties, "dietaryRestriction"));
        assertSoftNullableUnicodeBoundedText(
                softly,
                object(object(dietary, "properties"), "details"),
                2000,
                "MemberRDTO.dietaryRestriction.details"
        );

        Map<String, Object> solicitationProperties = object(
                object(schemas, "MembershipSolicitationRDTO"), "properties");
        assertSoftUnicodeBoundedText(
                softly,
                object(solicitationProperties, "residentialCity"),
                100,
                "MembershipSolicitationRDTO.residentialCity"
        );

        Map<String, Object> requestContribution = object(schemas, "ContributionProfile");
        Map<String, Object> requestCustomAreas = object(
                object(requestContribution, "properties"), "otherContributionAreas");
        softly.assertThat(requestCustomAreas)
                .as("ContributionProfile.otherContributionAreas array")
                .containsEntry("type", "array")
                .containsEntry("maxItems", 10)
                .containsEntry("uniqueItems", true);
        softly.assertThat(resolveSchema(contract, object(requestCustomAreas, "items")))
                .as("ContributionProfile.otherContributionAreas item")
                .doesNotContainKeys("maxItems", "uniqueItems");

        Map<String, Object> readContribution = object(schemas, "MemberContributionProfileRead");
        Map<String, Object> readProperties = object(readContribution, "properties");
        Map<String, Object> readFixedAreas = object(readProperties, "contributionAreas");
        softly.assertThat(readFixedAreas)
                .as("MemberContributionProfileRead.contributionAreas array")
                .containsEntry("type", "array")
                .containsEntry("maxItems", 18)
                .containsEntry("uniqueItems", true);
        softly.assertThat(resolveSchema(contract, object(readFixedAreas, "items")).get("enum"))
                .as("MemberContributionProfileRead.contributionAreas catalog")
                .isEqualTo(List.of(
                        "GAME_REFEREE", "CRAFTS", "MUSIC", "PRAYER_LEADERSHIP", "BOA_TARDE_STORYTELLING",
                        "DANCE", "BALLOON_SCULPTURE", "FOOTBALL", "VOLLEYBALL", "BASKETBALL", "HANDBALL",
                        "PHOTOGRAPHY_AND_VIDEO", "PUBLIC_READING", "FACE_PAINTING", "FIRST_AID",
                        "GINCANA_LEADERSHIP", "TECHNOLOGY", "TERERE"
                ));

        Map<String, Object> readCustomAreas = object(readProperties, "otherContributionAreas");
        softly.assertThat(readCustomAreas)
                .as("MemberContributionProfileRead.otherContributionAreas array")
                .containsEntry("type", "array")
                .containsEntry("maxItems", 10)
                .containsEntry("uniqueItems", true);
        Map<String, Object> readCustomItem = resolveSchema(contract, object(readCustomAreas, "items"));
        softly.assertThat(readCustomItem)
                .as("MemberContributionProfileRead.otherContributionAreas item")
                .containsEntry("type", "string")
                .containsEntry("minLength", 1)
                .containsEntry("maxLength", 100)
                .doesNotContainKeys("maxItems", "uniqueItems");

        softly.assertAll();
    }

    @Test
    @DisplayName("REQ-MEMBER-SOL-006 and REQ-OPENAPI-004 - solicitation response -> complete required nullable closed shape and bounded reasons")
    void membershipSolicitationResponseShouldDocumentCompleteRequiredNullableClosedShape() {
        Map<String, Object> contract = openApiContract().body();
        Map<String, Object> schemas = object(object(contract, "components"), "schemas");
        Map<String, Object> solicitation = object(schemas, "MembershipSolicitationRDTO");
        Map<String, Object> properties = object(solicitation, "properties");
        List<String> completeShape = List.of(
                "id", "account", "firstName", "surname", "birthDate", "gamEntryDate",
                "residentialCity", "phoneNumber", "contactEmail", "justification", "status",
                "submittedAt", "reviewedBy", "decidedAt", "reviewReason", "memberId"
        );
        SoftAssertions softly = new SoftAssertions();

        softly.assertThat(solicitation)
                .as("MembershipSolicitationRDTO")
                .containsEntry("type", "object")
                .containsEntry("additionalProperties", false);
        softly.assertThat(properties)
                .as("MembershipSolicitationRDTO properties")
                .containsOnlyKeys(completeShape.toArray(String[]::new));
        softly.assertThat(strings(solicitation, "required"))
                .as("MembershipSolicitationRDTO required properties")
                .containsExactlyInAnyOrderElementsOf(completeShape);

        softly.assertThat(object(properties, "justification"))
                .as("MembershipSolicitationRDTO.justification")
                .containsEntry("type", "string")
                .containsEntry("minLength", 1)
                .containsEntry("maxLength", 2000);
        softly.assertThat(object(properties, "reviewReason"))
                .as("MembershipSolicitationRDTO.reviewReason")
                .containsEntry("type", List.of("string", "null"))
                .containsEntry("minLength", 1)
                .containsEntry("maxLength", 2000);
        softly.assertThat(object(properties, "reviewedBy"))
                .as("MembershipSolicitationRDTO.reviewedBy nullability")
                .containsEntry("type", List.of("object", "null"));
        softly.assertThat(object(properties, "decidedAt"))
                .as("MembershipSolicitationRDTO.decidedAt nullability")
                .containsEntry("type", List.of("string", "null"))
                .containsEntry("format", "date-time");
        softly.assertThat(object(properties, "memberId"))
                .as("MembershipSolicitationRDTO.memberId nullability")
                .containsEntry("type", List.of("string", "null"))
                .containsEntry("format", "uuid");

        Map<String, Object> reviewedBy = resolveSchema(contract, object(properties, "reviewedBy"));
        softly.assertThat(reviewedBy)
                .as("MembershipSolicitationRDTO.reviewedBy schema")
                .containsEntry("type", "object")
                .containsEntry("additionalProperties", false);
        softly.assertThat(object(reviewedBy, "properties"))
                .as("MembershipSolicitationRDTO.reviewedBy properties")
                .containsOnlyKeys("id", "email", "displayName");
        softly.assertThat(strings(reviewedBy, "required"))
                .as("MembershipSolicitationRDTO.reviewedBy required properties")
                .containsExactlyInAnyOrder("id", "email", "displayName");

        softly.assertAll();
    }

    @Test
    @DisplayName("REQ-EVENT-007, REQ-EVENT-012, REQ-EVENT-019 and REQ-PRESENCE-006 - Event mutation operations -> exact success statuses and Location headers")
    void eventMutationOperationsShouldDocumentExactSuccessContracts() {
        Map<String, Object> contract = openApiContract().body();
        Map<String, Object> paths = object(contract, "paths");
        Map<String, Object> createResponses = object(object(object(paths, "/events"), "post"), "responses");
        Map<String, Object> deleteResponses = object(object(object(paths, "/events/{id}"), "delete"), "responses");
        Map<String, Object> registrationResponses = object(
                object(object(paths, "/events/{eventId}/presences"), "post"), "responses"
        );
        SoftAssertions softly = new SoftAssertions();

        softly.assertThat(createResponses).as("create Event responses").containsKey("201");
        assertLocationHeader(softly, createResponses, "201", "create Event");

        softly.assertThat(deleteResponses).as("delete Event responses").containsKey("204");
        Map<String, Object> deleted = object(deleteResponses, "204");
        if (deleted != null) {
            softly.assertThat(deleted).as("delete Event 204 response").doesNotContainKey("content");
        }

        softly.assertThat(registrationResponses).as("register Presence responses").containsKey("201");
        assertLocationHeader(softly, registrationResponses, "201", "register Presence");
        softly.assertAll();
    }

    @Test
    @DisplayName("REQ-PRESENCE-006 and REQ-PRESENCE-007 - registration success schema -> complete compact Presence")
    void presenceRegistrationShouldDocumentCompleteResponseSchema() {
        Map<String, Object> contract = openApiContract().body();
        Map<String, Object> paths = object(contract, "paths");
        Map<String, Object> registration = object(
                object(paths, "/events/{eventId}/presences"), "post"
        );
        Map<String, Object> created = object(object(registration, "responses"), "201");
        Map<String, Object> content = object(created, "content");
        assertThat(content).as("Presence registration 201 content").isNotNull();
        Map<String, Object> json = content.containsKey("application/json")
                ? object(content, "application/json")
                : object(content, "*/*");
        assertThat(json).as("Presence registration 201 response schema").isNotNull();
        Map<String, Object> presenceSchema = resolveSchema(contract, object(json, "schema"));
        Map<String, Object> presenceProperties = object(presenceSchema, "properties");

        assertThat(strings(presenceSchema, "required"))
                .containsExactlyInAnyOrder("id", "member", "event", "observations", "registeredAt");
        assertThat(presenceProperties)
                .containsOnlyKeys("id", "member", "event", "observations", "registeredAt");
        Map<String, Object> memberSchema = resolveSchema(contract, object(presenceProperties, "member"));
        Map<String, Object> eventSchema = resolveSchema(contract, object(presenceProperties, "event"));
        assertThat(object(memberSchema, "properties"))
                .containsOnlyKeys("id", "firstName", "surname", "status");
        assertThat(object(eventSchema, "properties"))
                .containsOnlyKeys("id", "title", "beginDate", "endDate", "type", "status");
        assertThat(object(presenceProperties, "registeredAt"))
                .containsEntry("type", "string")
                .containsEntry("format", "date-time");
    }

    @Test
    @DisplayName("REQ-MEMBER-SOL-002 and REQ-OPENAPI-004 - solicitation submission -> prohibited accountId is absent from schema and example")
    void membershipSolicitationSubmissionShouldOmitProhibitedAccountId() {
        Map<String, Object> contract = openApiContract().body();
        Map<String, Object> operation = object(
                object(object(contract, "paths"), "/membership-solicitations"),
                "post"
        );
        Map<String, Object> requestBody = object(operation, "requestBody");
        Map<String, Object> json = object(object(requestBody, "content"), "application/json");
        Map<String, Object> schema = resolveSchema(contract, object(json, "schema"));
        Map<String, Object> properties = object(schema, "properties");
        Map<String, Object> example = object(json, "example");
        SoftAssertions softly = new SoftAssertions();

        softly.assertThat(properties)
                .as("solicitation submission request properties")
                .containsOnlyKeys(
                        "firstName", "surname", "birthDate", "gamEntryDate", "residentialCity",
                        "phoneNumber", "contactEmail", "justification"
                );
        softly.assertThat(strings(schema, "required"))
                .as("solicitation submission required properties")
                .containsExactlyInAnyOrder(
                        "firstName", "surname", "birthDate", "gamEntryDate", "residentialCity",
                        "phoneNumber", "contactEmail", "justification"
                );
        softly.assertThat(example)
                .as("solicitation submission example")
                .containsOnlyKeys(
                        "firstName", "surname", "birthDate", "gamEntryDate", "residentialCity",
                        "phoneNumber", "contactEmail", "justification"
                );
        softly.assertAll();
    }

    @Test
    @DisplayName("REQ-PRESENCE-002/004/011/013 and REQ-OPENAPI-003/004 - Presence request schemas -> exact normalized text contracts")
    void presenceRequestsShouldDocumentExactNormalizedTextContracts() {
        Map<String, Object> contract = openApiContract().body();
        Map<String, Object> paths = object(contract, "paths");
        Map<String, Object> registrationSchema = requestSchema(
                contract,
                object(object(paths, "/events/{eventId}/presences"), "post")
        );
        Map<String, Object> editSchema = requestSchema(
                contract,
                object(object(paths, "/events/{eventId}/presences/{memberId}"), "patch")
        );
        Map<String, Object> removalSchema = requestSchema(
                contract,
                object(object(paths, "/events/{eventId}/presences/{memberId}"), "delete")
        );

        assertThat(strings(registrationSchema, "required")).containsExactly("memberId");
        assertThat(object(registrationSchema, "properties"))
                .containsOnlyKeys("memberId", "observations");
        assertNormalizedObservationsSchema(
                object(object(registrationSchema, "properties"), "observations")
        );

        assertThat(strings(editSchema, "required")).containsExactly("observations");
        assertThat(object(editSchema, "properties")).containsOnlyKeys("observations");
        assertNormalizedObservationsSchema(
                object(object(editSchema, "properties"), "observations")
        );

        assertThat(strings(removalSchema, "required")).containsExactly("reason");
        assertThat(object(removalSchema, "properties")).containsOnlyKeys("reason");
        Map<String, Object> reason = object(object(removalSchema, "properties"), "reason");
        assertThat(reason)
                .containsEntry("type", "string")
                .containsEntry("minLength", 1)
                .doesNotContainKey("maxLength");
        assertThat(reason.get("description")).asString()
                .containsIgnoringCase("trim")
                .containsIgnoringCase("1")
                .containsIgnoringCase("2,000")
                .containsIgnoringCase("code point")
                .containsIgnoringCase("blank");
    }

    @Test
    @DisplayName("REQ-PRESENCE-009/010 and REQ-OPENAPI-003 - Presence collection inputs -> exact filter and deterministic ordering semantics")
    void presenceCollectionsShouldDocumentFilteringAndDeterministicOrdering() {
        Map<String, Object> contract = openApiContract().body();
        Map<String, Object> paths = object(contract, "paths");
        Map<String, Object> eventRoster = object(
                object(paths, "/events/{eventId}/presences"), "get"
        );
        Map<String, Object> memberHistory = object(
                object(paths, "/members/{memberId}/presences"), "get"
        );
        Map<String, Object> name = objects(eventRoster, "parameters").stream()
                .filter(parameter -> "name".equals(parameter.get("name")))
                .findFirst()
                .orElseThrow();
        Map<String, Object> eventSort = objects(eventRoster, "parameters").stream()
                .filter(parameter -> "sort".equals(parameter.get("name")))
                .findFirst()
                .orElseThrow();
        Map<String, Object> memberSort = objects(memberHistory, "parameters").stream()
                .filter(parameter -> "sort".equals(parameter.get("name")))
                .findFirst()
                .orElseThrow();
        SoftAssertions softly = new SoftAssertions();

        softly.assertThat(String.valueOf(name.get("description"))).as("Event roster name filter")
                .containsIgnoringCase("trim")
                .containsIgnoringCase("blank")
                .containsIgnoringCase("case-insensitive")
                .containsIgnoringCase("accent-sensitive")
                .contains("firstName", "surname")
                .containsIgnoringCase("separately");
        softly.assertThat(object(eventSort, "schema").get("default"))
                .as("Event roster default sort")
                .isEqualTo(List.of(
                        "memberFirstName,asc",
                        "memberSurname,asc"
                ));
        softly.assertThat(eventSort.get("description")).as("Event roster sort semantics")
                .asString()
                .contains("memberFirstName", "memberSurname", "registeredAt")
                .containsIgnoringCase("default")
                .containsIgnoringCase("Presence UUID ascending")
                .containsIgnoringCase("requested sort");
        softly.assertThat(object(memberSort, "schema").get("default"))
                .as("Member history default sort")
                .isEqualTo(List.of("eventBeginDate,desc"));
        softly.assertThat(memberSort.get("description")).as("Member history sort semantics")
                .asString()
                .contains("eventBeginDate", "eventTitle", "registeredAt")
                .containsIgnoringCase("default")
                .containsIgnoringCase("Event UUID descending")
                .containsIgnoringCase("Presence UUID ascending")
                .containsIgnoringCase("requested sort");
        softly.assertAll();
    }

    @Test
    @DisplayName("REQ-EVENT-010 - Event search OpenAPI contract -> effective-status sort and beginDate/id default")
    void eventSearchShouldDocumentExactSortingContract() {
        Map<String, Object> contract = openApiContract().body();
        Map<String, Object> paths = object(contract, "paths");
        Map<String, Object> searchEvents = object(object(paths, "/events/search"), "post");
        Map<String, Object> sort = objects(searchEvents, "parameters").stream()
                .filter(parameter -> "sort".equals(parameter.get("name")))
                .findFirst()
                .orElseThrow();

        assertThat(object(sort, "schema").get("default"))
                .isEqualTo(List.of("beginDate,asc", "id,asc"));
        assertThat(sort.get("description").toString())
                .contains("title", "beginDate", "endDate", "type", "status")
                .containsIgnoringCase("effective status")
                .containsIgnoringCase("default")
                .contains("beginDate", "id");
    }

    @Test
    @DisplayName("REQ-EVENT-011/012/017/019 and REQ-PRESENCE-005/017 - conflict responses -> operation-specific codes and details")
    void eventAndPresenceConflictsShouldBeDocumentedPerOperation() {
        Map<String, Object> contract = openApiContract().body();
        Map<String, Object> paths = object(contract, "paths");
        Map<String, Object> lockConflict = object(
                object(object(object(paths, "/events/{id}/lock"), "patch"), "responses"), "409"
        );
        Map<String, Object> deletionConflict = object(
                object(object(object(paths, "/events/{id}"), "delete"), "responses"), "409"
        );
        Map<String, Object> registrationConflict = object(
                object(object(object(paths, "/events/{eventId}/presences"), "post"), "responses"), "409"
        );
        SoftAssertions softly = new SoftAssertions();

        softly.assertThat(lockConflict.toString()).as("lock Event 409 documentation")
                .contains(
                        "EVENT_STATUS_TRANSITION_NOT_ALLOWED",
                        "EVENT_TYPE_NOT_MANAGEABLE",
                        "eventId",
                        "currentStatus",
                        "requestedStatus"
                );
        softly.assertThat(deletionConflict.toString()).as("delete Event 409 documentation")
                .contains(
                        "EVENT_HAS_PRESENCES",
                        "EVENT_STATUS_TRANSITION_NOT_ALLOWED",
                        "EVENT_TYPE_NOT_MANAGEABLE",
                        "eventId",
                        "activePresenceCount"
                );
        softly.assertThat(registrationConflict.toString()).as("register Presence 409 documentation")
                .contains(
                        "PRESENCE_ALREADY_REGISTERED",
                        "PRESENCE_REGISTRATION_NOT_ALLOWED",
                        "eventId",
                        "memberId",
                        "presenceId",
                        "status",
                        "evaluationInstant"
                )
                .doesNotContain("beginDate");
        softly.assertAll();
    }

    @Test
    @DisplayName("REQ-PRESENCE-017 and REQ-ORATORIO-ATT-012 - attendance operations document lifecycle eligibility without clock boundaries")
    void attendanceOperationsShouldDocumentLifecycleEligibilityWithoutClockBoundaries() {
        Map<String, Object> paths = object(openApiContract().body(), "paths");
        List<Map<String, Object>> operations = List.of(
                object(object(paths, "/events/{eventId}/presences"), "post"),
                object(object(paths, "/oratorios/{oratorioId}/attendance/members/{memberId}"), "put"),
                object(object(paths, "/oratorios/{oratorioId}/attendance/oratorianos/{oratorianoId}"), "put"),
                object(object(paths, "/oratorios/{oratorioId}/attendance/oratorianos/register-and-mark"), "post")
        );
        SoftAssertions softly = new SoftAssertions();

        operations.forEach(operation -> softly.assertThat(String.valueOf(operation.get("description")))
                .as(String.valueOf(operation.get("operationId")))
                .contains("SCHEDULED", "COMPLETED")
                .containsIgnoringCase("time boundary")
                .containsIgnoringCase("confirmed attendance"));
        softly.assertAll();
    }

    @Test
    @DisplayName("REQ-OPENAPI-002 - anonymous developer -> Swagger UI is available at the public documentation route")
    void swaggerUiShouldBeAvailableWithoutAuthentication() {
        assertHtmlEndpointAvailable("/api/docs");
    }

    @Test
    @DisplayName("REQ-OPENAPI-001 and REQ-WEB-004 - generated contract -> OpenAPI 3.1 with /api public server base")
    void generatedContractShouldDeclareOpenApi31AndThePublicApiServerBase() {
        Map<String, Object> contract = openApiContract().body();

        assertThat(contract).containsEntry("openapi", "3.1.0");
        assertThat(objects(contract, "servers"))
                .extracting(server -> server.get("url"))
                .contains("/api");
    }

    @Test
    @DisplayName("REQ-OPENAPI-002 and REQ-OPENAPI-005 - generated contract -> GAM routes, bearer default, and public authentication overrides")
    void generatedContractShouldIncludeApplicationRoutesAndTheirSecurityBoundary() {
        Map<String, Object> contract = openApiContract().body();
        Map<String, Object> paths = object(contract, "paths");
        Map<String, Object> components = object(contract, "components");
        Map<String, Object> securitySchemes = object(components, "securitySchemes");
        Map<String, Object> bearerAuth = object(securitySchemes, "bearerAuth");
        assertThat(paths).containsKeys(
                "/auth/login",
                "/auth/csrf",
                "/accounts/{accountId}",
                "/roles/{roleId}"
        );
        List<Map<String, Object>> publicAuthenticationOperations = List.of(
                object(object(paths, "/auth/register"), "post"),
                object(object(paths, "/auth/login"), "post"),
                object(object(paths, "/auth/refresh"), "post"),
                object(object(paths, "/auth/logout"), "post"),
                object(object(paths, "/auth/csrf"), "get")
        );

        assertThat(paths).doesNotContainKeys("/actuator/health", "/actuator/metrics", "/error");
        assertThat(contract.get("security"))
                .isEqualTo(List.of(Map.of("bearerAuth", List.of())));
        assertThat(bearerAuth).containsEntry("type", "http")
                .containsEntry("scheme", "bearer")
                .containsEntry("bearerFormat", "JWT");
        assertThat(publicAuthenticationOperations)
                .allSatisfy(operation -> assertThat(operation).containsEntry("security", List.of()));
    }

    @Test
    @DisplayName("REQ-OPENAPI-005 and REQ-BROWSER-AUTH-003/004 - authentication contract -> CSRF bootstrap and browser proof inputs are documented")
    @SuppressWarnings("unchecked")
    void authenticationContractShouldDocumentCsrfBootstrapAndBrowserProof() {
        Map<String, Object> contract = openApiContract().body();
        Map<String, Object> paths = object(contract, "paths");
        Map<String, Object> components = object(contract, "components");
        assertThat(paths).containsKey("/auth/csrf");

        Map<String, Object> csrf = object(object(paths, "/auth/csrf"), "get");
        Map<String, Object> csrfResponse = object(object(csrf, "responses"), "200");
        Map<String, Object> csrfJson = object(object(csrfResponse, "content"), "application/json");
        Map<String, Object> csrfSchema = resolveSchema(contract, object(csrfJson, "schema"));
        Map<String, Object> csrfProperties = object(csrfSchema, "properties");

        assertThat(csrf).containsEntry("security", List.of());
        assertThat((List<String>) csrfSchema.get("required"))
                .containsExactlyInAnyOrder("token", "headerName");
        assertThat(csrfProperties).containsKeys("token", "headerName");
        assertThat(object(csrfProperties, "headerName")).containsEntry("example", "X-XSRF-TOKEN");
        assertThat(object(csrfResponse, "headers")).containsKeys("Cache-Control", "Set-Cookie");

        assertHeaderParameter(object(object(paths, "/auth/login"), "post"), "X-XSRF-TOKEN");
        assertHeaderParameter(object(object(paths, "/auth/refresh"), "post"), "X-XSRF-TOKEN");
        assertHeaderParameter(object(object(paths, "/auth/logout"), "post"), "X-XSRF-TOKEN");
        assertCookieParameter(object(object(paths, "/auth/refresh"), "post"), "refreshToken");
        assertCookieParameter(object(object(paths, "/auth/logout"), "post"), "refreshToken");
        assertThat(components).containsKey("schemas");
    }

    @Test
    @DisplayName("REQ-OPENAPI-005 and REQ-BROWSER-AUTH-002 - authentication responses -> refresh cookie lifecycle is documented")
    void authenticationResponsesShouldDocumentRefreshCookieLifecycle() {
        Map<String, Object> contract = openApiContract().body();
        Map<String, Object> paths = object(contract, "paths");

        assertSetCookieEffect(
                contract,
                object(object(paths, "/auth/login"), "post"),
                "set", "establish", "issue"
        );
        assertSetCookieEffect(
                contract,
                object(object(paths, "/auth/refresh"), "post"),
                "set", "replace", "rotate"
        );
        assertSetCookieEffect(
                contract,
                object(object(paths, "/auth/logout"), "post"),
                "expire", "clear", "delete", "max-age=0"
        );
    }

    @Test
    @DisplayName("REQ-ACCOUNT-008 and REQ-OPENAPI-003/005 - current Account context -> API-relative protected operation and exact schema")
    void currentAccountContextShouldBeDocumentedAsAnApiRelativeBearerOperation() {
        Map<String, Object> contract = openApiContract().body();
        Map<String, Object> paths = object(contract, "paths");

        assertThat(paths)
                .containsKey("/accounts/me")
                .doesNotContainKey("/api/accounts/me");

        Map<String, Object> operation = object(object(paths, "/accounts/me"), "get");
        assertThat(operation).containsEntry("operationId", "getCurrentAccountContext");
        assertThat(operation.getOrDefault("security", contract.get("security")))
                .isEqualTo(List.of(Map.of("bearerAuth", List.of())));
        assertThat(operation.get("summary").toString())
                .containsIgnoringCase("current Account context");
        assertThat(operation.get("description").toString())
                .containsIgnoringCase("authenticated Account")
                .containsIgnoringCase("effective permission")
                .doesNotContain("Performs the documented GAM operation");
        assertThat(operation).doesNotContainKeys("parameters", "requestBody");

        Map<String, Object> responses = object(operation, "responses");
        assertThat(responses).containsOnlyKeys("200", "401");

        Map<String, Object> successResponse = object(responses, "200");
        Map<String, Object> successJson = object(object(successResponse, "content"), "application/json");
        Map<String, Object> currentContextSchema = resolveSchema(contract, object(successJson, "schema"));
        Map<String, Object> properties = object(currentContextSchema, "properties");
        Map<String, Object> example = object(successJson, "example");

        assertThat(example).containsOnlyKeys("id", "email", "displayName", "roles", "permissions");
        assertThat(example.get("email").toString())
                .endsWith("@example.test")
                .isNotEqualTo("Synthetic GAM value");
        assertThat(example.get("displayName").toString())
                .isNotBlank()
                .isNotEqualTo("Synthetic GAM value");
        assertThat(objects(example, "roles"))
                .singleElement()
                .satisfies(role -> assertThat(role)
                        .containsEntry("name", "MEMBER")
                        .containsEntry("description", "Standard authenticated member access")
                        .containsEntry("systemManaged", true));
        assertThat(strings(example, "permissions"))
                .contains("ACCOUNT_GET", "EVENT_SEARCH")
                .doesNotHaveDuplicates();

        assertThat(strings(currentContextSchema, "required"))
                .containsExactlyInAnyOrder("id", "email", "displayName", "roles", "permissions");
        assertThat(properties).containsOnlyKeys("id", "email", "displayName", "roles", "permissions");
        assertThat(object(properties, "id"))
                .containsEntry("type", "string")
                .containsEntry("format", "uuid");
        assertThat(object(properties, "email")).containsEntry("type", "string");
        assertThat(object(properties, "displayName")).containsEntry("type", "string");

        Map<String, Object> rolesSchema = object(properties, "roles");
        Map<String, Object> roleSchema = resolveSchema(contract, object(rolesSchema, "items"));
        assertThat(rolesSchema).containsEntry("type", "array");
        assertThat(strings(roleSchema, "required"))
                .containsExactlyInAnyOrder("id", "name", "description", "systemManaged");
        assertThat(object(roleSchema, "properties"))
                .containsOnlyKeys("id", "name", "description", "systemManaged");

        Map<String, Object> permissionsSchema = object(properties, "permissions");
        assertThat(permissionsSchema)
                .containsEntry("type", "array")
                .containsEntry("uniqueItems", true);
        assertThat(object(permissionsSchema, "items")).containsEntry("type", "string");

        Map<String, Object> unauthorizedResponse = object(responses, "401");
        Map<String, Object> unauthorizedJson = object(object(unauthorizedResponse, "content"), "application/json");
        Map<String, Object> errorSchema = resolveSchema(contract, object(unauthorizedJson, "schema"));
        assertThat(object(errorSchema, "properties"))
                .containsKeys("timestamp", "status", "code", "message", "details")
                .doesNotContainKey("error");
    }

    @Test
    @DisplayName("REQ-RBAC-012/013 - Role collection OpenAPI contract documents complete name-filter semantics")
    void roleCollectionShouldBeDocumented() {
        Map<String, Object> contract = openApiContract().body();
        Map<String, Object> paths = object(contract, "paths");

        assertThat(paths).containsKey("/roles");

        Map<String, Object> listRoles = object(object(paths, "/roles"), "get");
        assertThat(listRoles.get("operationId")).asString().isNotBlank();
        assertThat(objects(listRoles, "parameters"))
                .singleElement()
                .satisfies(parameter -> {
                    assertThat(parameter)
                            .containsEntry("name", "name")
                            .containsEntry("in", "query")
                            .containsEntry("required", false);
                    assertThat(object(parameter, "schema")).containsEntry("type", "string");
                    assertThat(parameter.get("description").toString())
                            .containsIgnoringCase("trim")
                            .containsIgnoringCase("case-insensitive")
                            .containsIgnoringCase("accent-sensitive")
                            .containsIgnoringCase("blank")
                            .contains("400");
                });
        assertThat(objects(listRoles, "parameters"))
                .extracting(parameter -> parameter.get("name"))
                .doesNotContain("page", "size", "sort");
        assertThat(object(listRoles, "responses")).containsOnlyKeys("200", "400", "401", "403");
    }

    @Test
    @DisplayName("REQ-MEMBER-017/018 - Coordinator lifecycle OpenAPI contract describes normalized reason validation")
    void coordinatorLifecycleShouldBeDocumented() {
        Map<String, Object> contract = openApiContract().body();
        Map<String, Object> paths = object(contract, "paths");

        assertThat(paths).containsKeys(
                "/members/{memberId}/coordinator/grant",
                "/members/{memberId}/coordinator/revoke"
        );

        for (String transition : List.of("grant", "revoke")) {
            Map<String, Object> operation = object(
                    object(paths, "/members/{memberId}/coordinator/" + transition),
                    "patch"
            );
            assertThat(operation.get("operationId").toString()).containsIgnoringCase(transition).contains("Coordinator");
            assertThat(object(operation, "responses"))
                    .containsOnlyKeys("204", "400", "401", "403", "404", "409");

            Map<String, Object> requestBody = object(operation, "requestBody");
            assertThat(requestBody).containsEntry("required", true);
            Map<String, Object> json = object(object(requestBody, "content"), "application/json");
            Map<String, Object> reasonSchema = resolveSchema(contract, object(json, "schema"));
            assertThat(strings(reasonSchema, "required")).contains("reason");
            Map<String, Object> reasonProperty = object(object(reasonSchema, "properties"), "reason");
            assertThat(reasonProperty)
                    .containsEntry("type", "string")
                    .containsEntry("minLength", 1)
                    .doesNotContainKey("maxLength");
            assertThat(reasonProperty.get("description")).asString()
                    .containsIgnoringCase("trim")
                    .containsIgnoringCase("normalized")
                    .containsIgnoringCase("2,000")
                    .containsIgnoringCase("code point");
        }
    }

    @Test
    @DisplayName("REQ-OPENAPI-002 - non-development Swagger UI configuration -> every request method is read-only")
    void nonDevelopmentSwaggerUiShouldDisableInteractiveRequestExecution() {
        Map<String, Object> configuration = swaggerUiConfiguration();

        assertThat(strings(configuration, "supportedSubmitMethods")).isEmpty();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> resolveSchema(Map<String, Object> contract, Map<String, Object> schema) {
        Object reference = schema.get("$ref");
        if (reference == null) {
            return schema;
        }
        String schemaName = reference.toString().substring(reference.toString().lastIndexOf('/') + 1);
        return object(object(contract, "components"), "schemas").get(schemaName) instanceof Map<?, ?> resolved
                ? (Map<String, Object>) resolved
                : Map.of();
    }

    @SuppressWarnings("unchecked")
    private void assertHeaderParameter(Map<String, Object> operation, String name) {
        List<Map<String, Object>> parameters = (List<Map<String, Object>>) operation.get("parameters");
        assertThat(parameters)
                .anySatisfy(parameter -> assertThat(parameter)
                        .containsEntry("name", name)
                        .containsEntry("in", "header"));
    }

    @SuppressWarnings("unchecked")
    private void assertCookieParameter(Map<String, Object> operation, String name) {
        List<Map<String, Object>> parameters = (List<Map<String, Object>>) operation.get("parameters");
        assertThat(parameters)
                .anySatisfy(parameter -> assertThat(parameter)
                        .containsEntry("name", name)
                        .containsEntry("in", "cookie"));
    }

    @SuppressWarnings("unchecked")
    private void assertSetCookieEffect(
            Map<String, Object> contract,
            Map<String, Object> operation,
            String... actionTerms
    ) {
        Map<String, Object> successResponse = object(object(operation, "responses"), "200");
        Object headersValue = successResponse.get("headers");
        assertThat(headersValue)
                .as("%s 200 response headers", operation.get("operationId"))
                .isInstanceOf(Map.class);
        Map<String, Object> headers = (Map<String, Object>) headersValue;
        assertThat(headers)
                .as("%s 200 response headers", operation.get("operationId"))
                .containsKey("Set-Cookie");
        assertThat(headers.get("Set-Cookie"))
                .as("%s Set-Cookie response header", operation.get("operationId"))
                .isInstanceOf(Map.class);
        Map<String, Object> setCookieHeader = resolveHeader(
                contract,
                (Map<String, Object>) headers.get("Set-Cookie")
        );
        String description = setCookieHeader.getOrDefault("description", "")
                .toString()
                .toLowerCase(Locale.ROOT);

        assertThat(description).contains("refreshtoken");
        assertThat(List.of(actionTerms)).anyMatch(description::contains);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> resolveHeader(Map<String, Object> contract, Map<String, Object> header) {
        Object reference = header.get("$ref");
        if (reference == null) {
            return header;
        }
        String headerName = reference.toString().substring(reference.toString().lastIndexOf('/') + 1);
        return object(object(contract, "components"), "headers").get(headerName) instanceof Map<?, ?> resolved
                ? (Map<String, Object>) resolved
                : Map.of();
    }

    private void assertLocationHeader(
            SoftAssertions softly,
            Map<String, Object> responses,
            String status,
            String operation
    ) {
        Map<String, Object> response = object(responses, status);
        if (response == null) {
            return;
        }
        Map<String, Object> headers = object(response, "headers");
        softly.assertThat(headers).as("%s %s response headers", operation, status).isNotNull();
        if (headers != null) {
            softly.assertThat(headers).as("%s %s response headers", operation, status).containsKey("Location");
        }
    }

    private Map<String, Object> requestSchema(
            Map<String, Object> contract,
            Map<String, Object> operation
    ) {
        Map<String, Object> requestBody = object(operation, "requestBody");
        assertThat(requestBody).containsEntry("required", true);
        Map<String, Object> json = object(object(requestBody, "content"), "application/json");
        return resolveSchema(contract, object(json, "schema"));
    }

    private void assertClosedCatalog(Map<String, Object> schema, List<String> keys) {
        assertThat(schema).containsEntry("type", "object").containsEntry("additionalProperties", false);
        assertThat(object(schema, "properties")).containsOnlyKeys(keys.toArray(String[]::new));
        assertThat(strings(schema, "required")).containsExactlyInAnyOrderElementsOf(keys);
    }

    private void assertNormalizedObservationsSchema(Map<String, Object> observations) {
        assertThat(observations.get("type")).isEqualTo(List.of("string", "null"));
        assertThat(observations).doesNotContainKey("maxLength");
        assertThat(observations.get("description")).asString()
                .containsIgnoringCase("trim")
                .containsIgnoringCase("2,000")
                .containsIgnoringCase("code point")
                .containsIgnoringCase("blank")
                .containsIgnoringCase("null");
    }

    private void assertNormalizedReason(Map<String, Object> reason, String field) {
        assertThat(reason)
                .as(field)
                .containsEntry("type", "string")
                .containsEntry("minLength", 1)
                .containsEntry("maxLength", 2000);
        assertThat(reason.get("description")).as(field + " description").isNotNull().asString()
                .containsIgnoringCase("Unicode White_Space")
                .containsIgnoringCase("2,000")
                .containsIgnoringCase("code point");
    }

    private void assertUnicodeBoundedText(Map<String, Object> schema, int maximum, String field) {
        assertThat(schema)
                .as(field)
                .containsEntry("type", "string")
                .containsEntry("minLength", 1)
                .containsEntry("maxLength", maximum);
        assertThat(schema.get("description")).as(field + " description").isNotNull().asString()
                .containsIgnoringCase("Unicode White_Space")
                .contains(String.valueOf(maximum))
                .containsIgnoringCase("code point");
    }

    private void assertTrimmedBoundedText(Map<String, Object> schema, String field) {
        assertThat(schema)
                .as(field)
                .containsEntry("type", "string")
                .containsEntry("minLength", 1)
                .containsEntry("maxLength", 2000);
        assertThat(schema.get("description")).as(field + " description").isNotNull().asString()
                .containsIgnoringCase("trim")
                .contains("2,000")
                .containsIgnoringCase("character");
    }

    private void assertNullableUnicodeBoundedText(Map<String, Object> schema, int maximum, String field) {
        assertThat(schema)
                .as(field)
                .containsEntry("maxLength", maximum);
        assertThat(schema.get("type"))
                .as(field + " nullability")
                .isEqualTo(List.of("string", "null"));
        assertThat(schema.get("description")).as(field + " description").isNotNull().asString()
                .containsIgnoringCase("Unicode White_Space")
                .contains(String.valueOf(maximum))
                .containsIgnoringCase("code point");
    }

    private void assertSoftUnicodeBoundedText(
            SoftAssertions softly,
            Map<String, Object> schema,
            int maximum,
            String field
    ) {
        softly.assertThat(schema)
                .as(field)
                .containsEntry("type", "string")
                .containsEntry("minLength", 1)
                .containsEntry("maxLength", maximum);
        softly.assertThat(String.valueOf(schema.get("description")))
                .as(field + " description")
                .containsIgnoringCase("Unicode White_Space")
                .contains(String.valueOf(maximum))
                .containsIgnoringCase("code point");
    }

    private void assertSoftNullableUnicodeBoundedText(
            SoftAssertions softly,
            Map<String, Object> schema,
            int maximum,
            String field
    ) {
        softly.assertThat(schema)
                .as(field)
                .containsEntry("maxLength", maximum);
        softly.assertThat(schema.get("type"))
                .as(field + " nullability")
                .isEqualTo(List.of("string", "null"));
        softly.assertThat(String.valueOf(schema.get("description")))
                .as(field + " description")
                .containsIgnoringCase("Unicode White_Space")
                .contains(String.valueOf(maximum))
                .containsIgnoringCase("code point");
    }
}
