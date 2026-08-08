package br.org.gam.api.member.application.useCases;

import br.org.gam.api.account.application.AccountMapper;
import br.org.gam.api.member.application.MemberMapper;
import br.org.gam.api.member.domain.*;
import br.org.gam.api.member.domain.Member;
import br.org.gam.api.member.persistence.*;
import br.org.gam.api.shared.activitylog.*;
import br.org.gam.api.shared.domain.GamEmail;
import br.org.gam.api.shared.domain.GamName;
import br.org.gam.api.shared.phonenumber.GamPhoneNumber;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.transaction.Transactional;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@Profile("maintenance")
@ConditionalOnProperty(name = "maintenance.job", havingValue = "member-info-import")
public class MemberInformationImportJob implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(MemberInformationImportJob.class);
    private static final String SCHEMA = "gam-member-information-import/v1";

    private final ObjectMapper objectMapper;
    private final MemberInformationImportBatchRepository batches;
    private final MemberRepository members;
    private final AnnualMemberInformationResponseRepository responses;
    private final ActivityEvents activities;
    private final ConfigurableApplicationContext context;
    private final MemberMapper memberMapper;

    public MemberInformationImportJob(ObjectMapper objectMapper, MemberInformationImportBatchRepository batches,
            MemberRepository members, AnnualMemberInformationResponseRepository responses,
            ActivityEvents activities, ConfigurableApplicationContext context) {
        this(objectMapper, batches, members, responses, activities, context, standaloneMapper());
    }

    @Autowired
    public MemberInformationImportJob(ObjectMapper objectMapper, MemberInformationImportBatchRepository batches,
            MemberRepository members, AnnualMemberInformationResponseRepository responses,
            ActivityEvents activities, ConfigurableApplicationContext context, MemberMapper memberMapper) {
        this.objectMapper = objectMapper;
        this.batches = batches;
        this.members = members;
        this.responses = responses;
        this.activities = activities;
        this.context = context;
        this.memberMapper = memberMapper;
    }

    private static MemberMapper standaloneMapper() {
        try {
            Class<?> implementation = Class.forName("br.org.gam.api.member.application.MemberMapperImpl");
            AccountMapper accountMapper = (AccountMapper) java.lang.reflect.Proxy.newProxyInstance(
                    AccountMapper.class.getClassLoader(), new Class<?>[]{AccountMapper.class},
                    (proxy, method, arguments) -> null);
            return (MemberMapper) implementation.getConstructor(AccountMapper.class).newInstance(accountMapper);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Member mapper implementation is unavailable.", exception);
        }
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) throws Exception {
        String action = option(args, "maintenance.action");
        if (!Set.of("validate", "apply").contains(action)) throw safe("UNSUPPORTED_ACTION", -1, null);
        Path path = Path.of(option(args, "maintenance.file"));
        JsonNode document;
        try (var input = Files.newInputStream(path)) {
            document = objectMapper.readTree(input);
        } catch (Exception exception) {
            throw safe("INPUT_READ_FAILED", -1, null);
        }
        ValidatedDocument validated = validate(document);
        if ("apply".equals(action)) apply(validated, option(args, "maintenance.actor-reference"),
                ActivityReasonNormalizer.normalizeRequired(option(args, "maintenance.reason")));
        log.info("Member information import {} succeeded for batch {}, recordCount {}.",
                action, validated.batchId(), validated.records().size());
        SpringApplication.exit(context, () -> 0);
    }

    private ValidatedDocument validate(JsonNode document) throws Exception {
        if (!SCHEMA.equals(text(document, "schemaVersion"))) throw safe("UNSUPPORTED_SCHEMA", -1, "schemaVersion");
        if (!"APPROVED".equals(text(document, "documentStatus"))) throw safe("DOCUMENT_NOT_APPROVED", -1, "documentStatus");
        JsonNode batch = required(document, "batch", -1);
        UUID batchId = uuid(text(batch, "id", -1, "batch.id"), -1, "batch.id");
        requireV7(batchId, -1, "batch.id");
        int cycle = batch.path("surveyCycle").asInt(-1);
        if (cycle != 2026) throw safe("INVALID_SURVEY_CYCLE", -1, "batch.surveyCycle");
        String checksum = text(batch, "datasetChecksum", -1, "batch.datasetChecksum");
        if (!checksum.matches("sha256:[0-9a-f]{64}")) throw safe("INVALID_CHECKSUM", -1, "batch.datasetChecksum");
        JsonNode recordsNode = required(document, "records", -1);
        if (!recordsNode.isArray() || recordsNode.size() != 74) throw safe("INVALID_RECORDS", -1, "records");

        List<ImportRecord> records = new ArrayList<>();
        Set<UUID> ids = new HashSet<>();
        ids.add(batchId);
        Set<String> names = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        int index = 0;
        for (JsonNode record : recordsNode) {
            if (!"APPROVED".equals(text(record, "reviewStatus", index, "reviewStatus"))
                    || !required(record, "reviewIssues", index).isArray()
                    || !record.path("reviewIssues").isEmpty()) throw safe("RECORD_NOT_APPROVED", index, "reviewStatus");
            String source = safeSource(text(record, "sourceReference", index, "sourceReference"));
            JsonNode member = required(record, "member", index);
            JsonNode annual = required(record, "annualResponse", index);
            UUID memberId = uuid(text(member, "id", index, "member.id"), index, "member.id");
            UUID responseId = uuid(text(annual, "id", index, "annualResponse.id"), index, "annualResponse.id");
            requireV7(memberId, index, "member.id"); requireV7(responseId, index, "annualResponse.id");
            if (!ids.add(memberId) || !ids.add(responseId)) throw safe("DUPLICATE_UUID", index, "id");
            if (annual.path("surveyCycle").asInt(-1) != cycle
                    || !memberId.toString().equals(text(annual, "memberId", index, "annualResponse.memberId")))
                throw safe("RELATION_MISMATCH", index, "annualResponse.memberId");
            try {
                MemberEntity memberEntity = member(member, batchId, index);
                String canonicalName = memberEntity.getName().toString();
                if (!names.add(canonicalName)) throw safe("DUPLICATE_CANONICAL_NAME", index, "member.name");
                if (members.existsDifferentByCanonicalFullName(canonicalName, memberEntity.getId())) {
                    throw safe("EXISTING_NAME_REVIEW_COLLISION", index, "member.name");
                }
                AnnualMemberInformationResponseEntity responseEntity = annual(annual, memberEntity, batchId, index);
                records.add(new ImportRecord(index, source, memberEntity, responseEntity));
            } catch (RuntimeException exception) {
                if (exception.getMessage() != null && exception.getMessage().startsWith("member-info-import ")) {
                    throw exception;
                }
                throw safe("INVALID_RECORD", index, null);
            }
            index++;
        }
        String computed = checksum(document, records);
        if (!checksum.equals(computed)) throw safe("CHECKSUM_MISMATCH", -1, "batch.datasetChecksum");
        if (members.findById(batchId).isPresent() || responses.findById(batchId).isPresent()) {
            throw safe("BATCH_IDENTIFIER_COLLISION", -1, "batch.id");
        }
        Optional<MemberInformationImportBatchEntity> existingBatch = Optional.empty();
        boolean existingBatchLoaded = false;
        for (ImportRecord record : records) {
            if (members.findById(record.member().getId()).isPresent()
                    || responses.findById(record.response().getId()).isPresent()) {
                if (!existingBatchLoaded) {
                    existingBatch = batches.findById(batchId);
                    existingBatchLoaded = true;
                    if (existingBatch.isPresent()
                            && !checksum.equals(existingBatch.get().getDatasetChecksum())) {
                        throw safe("BATCH_IDENTIFIER_COLLISION", -1, "batch.id");
                    }
                }
                if (existingBatch.isEmpty()) throw safe("IDENTIFIER_COLLISION", record.index(), "id");
            }
        }
        return new ValidatedDocument(batchId, cycle, checksum, List.copyOf(records));
    }

    private void apply(ValidatedDocument document, String actorReference, String reason) {
        Optional<MemberInformationImportBatchEntity> existing = batches.findById(document.batchId());
        if (existing.isPresent()) {
            MemberInformationImportBatchEntity batch = existing.get();
            Set<UUID> expectedMemberIds = document.records().stream()
                    .map(record -> record.member().getId()).collect(java.util.stream.Collectors.toSet());
            Set<UUID> expectedResponseIds = document.records().stream()
                    .map(record -> record.response().getId()).collect(java.util.stream.Collectors.toSet());
            List<MemberEntity> importedMembers = members.findAll().stream()
                    .filter(member -> document.batchId().equals(member.getImportBatchId())).toList();
            List<AnnualMemberInformationResponseEntity> importedResponses = responses.findAll().stream()
                    .filter(response -> document.batchId().equals(response.getImportBatchId())).toList();
            boolean batchProjectionMatches = batch.getDatasetChecksum().equals(document.checksum())
                    && batch.getSurveyCycle() == document.surveyCycle()
                    && batch.getImportedMemberCount() == document.records().size()
                    && batch.getImportedResponseCount() == document.records().size();
            boolean memberProjectionsMatch = document.records().stream().allMatch(record ->
                    members.findById(record.member().getId())
                            .map(member -> sameMemberProjection(record.member(), member)).orElse(false));
            boolean responseIdentifiersExist = document.records().stream()
                    .allMatch(record -> responses.existsById(record.response().getId()));
            boolean responseProjectionsMatch = document.records().stream().allMatch(record ->
                    responses.findById(record.response().getId())
                            .map(response -> sameAnnualProjection(record.response(), response)).orElse(false));
            boolean memberRowsMatch = importedMembers.stream().map(MemberEntity::getId)
                    .collect(java.util.stream.Collectors.toSet()).equals(expectedMemberIds);
            boolean responseRowsMatch = importedResponses.stream().map(AnnualMemberInformationResponseEntity::getId)
                    .collect(java.util.stream.Collectors.toSet()).equals(expectedResponseIds);
            boolean activityMatches = members.countImportActivities(document.batchId()) == 1;
            boolean complete = batchProjectionMatches && memberProjectionsMatch && responseIdentifiersExist
                    && responseProjectionsMatch && memberRowsMatch && responseRowsMatch && activityMatches;
            if (!complete) throw safe("PARTIAL_OR_CORRUPTED_BATCH", -1, "batch.id");
            return;
        }
        DeveloperActorReference.useForCurrentTransaction(actorReference);
        batches.save(new MemberInformationImportBatchEntity(document.batchId(), document.surveyCycle(),
                document.checksum(), document.records().size(), document.records().size(), Instant.now(), reason));
        document.records().forEach(record -> members.save(record.member()));
        members.flush();
        document.records().forEach(record -> responses.save(record.response()));
        responses.flush();
        activities.developerMaintenance(ActivityAction.MEMBER_INFORMATION_IMPORTED, document.batchId(),
                "member_information_import_batches", reason, null,
                Map.of("surveyCycle", document.surveyCycle(), "memberCount", document.records().size(),
                        "responseCount", document.records().size()));
    }

    private MemberEntity member(JsonNode node, UUID batchId, int index) {
        UUID id = uuid(text(node, "id", index, "member.id"), index, "member.id");
        GamName name = new GamName(text(node, "firstName", index, "member.firstName"),
                text(node, "surname", index, "member.surname"));
        LocalDate birthDate = LocalDate.parse(text(node, "birthDate", index, "member.birthDate"));
        LocalDate gamEntryDate = LocalDate.parse(text(node, "gamEntryDate", index, "member.gamEntryDate"));
        if (!"ACTIVE".equals(text(node, "status", index, "member.status")))
            throw safe("INVALID_STATUS", index, "member.status");
        if (node.hasNonNull("accountId")) throw safe("ACCOUNT_LINK_NOT_ALLOWED", index, "member.accountId");
        JsonNode dietary = required(node, "dietaryRestriction", index);
        DietaryRestriction restriction = new DietaryRestriction(
                InformationStatus.valueOf(text(dietary, "status", index, "member.dietaryRestriction.status")),
                nullableText(dietary, "details", index, "member.dietaryRestriction.details"));
        Map<MemberExperienceType, InformationStatus> experiences =
                enumStatusSet(required(node, "experiences", index), MemberExperienceType.class, index);
        Map<MemberSacramentType, InformationStatus> sacraments =
                enumStatusSet(required(node, "sacraments", index), MemberSacramentType.class, index);
        Set<MemberContributionArea> contributionAreas = enumSet(
                required(node, "contributionAreas", index),
                MemberContributionArea.class,
                index,
                "member.contributionAreas"
        );
        JsonNode custom = required(node, "otherContributionAreas", index);
        if (!custom.isArray() || !custom.isEmpty()) {
            throw safe("INVALID_COLLECTION", index, "member.otherContributionAreas");
        }
        Member aggregate = Member.importApproved(id, name, birthDate,
                GamPhoneNumber.fromString(text(node, "phoneNumber", index, "member.phoneNumber")), gamEntryDate,
                normalizedCollapsedBounded(text(node, "residentialCity", index, "member.residentialCity"), 100),
                GamEmail.of(text(node, "contactEmail", index, "member.contactEmail")),
                restriction, experiences, sacraments, contributionAreas);
        return memberMapper.importedDomainToEntity(aggregate, batchId);
    }

    private AnnualMemberInformationResponseEntity annual(JsonNode node, MemberEntity member, UUID batchId, int index) {
        AnnualMemberInformationResponseEntity response = new AnnualMemberInformationResponseEntity();
        response.setId(uuid(text(node, "id", index, "annualResponse.id"), index, "annualResponse.id"));
        response.setMember(member); response.setSurveyCycle(node.path("surveyCycle").asInt());
        String submittedAt = nullableText(node, "submittedAt", index, "annualResponse.submittedAt");
        response.setSubmittedAt(submittedAt == null ? null : Instant.parse(submittedAt));
        JsonNode occupations = required(node, "occupations", index);
        response.setOccupations(enumSet(required(occupations, "values", index), MemberOccupation.class, index));
        response.setOccupationsDetails(nullableBounded(
                occupations, "details", index, "annualResponse.occupations.details"));
        if (response.getOccupations().contains(MemberOccupation.OTHER) != (response.getOccupationsDetails() != null))
            throw safe("CONDITIONAL_DETAILS", index, "annualResponse.occupations.details");
        setStatusDetails(required(node, "healthCondition", index), index, "annualResponse.healthCondition",
                response::setHealthConditionStatus, response::setHealthConditionDetails);
        response.setReligiousVocationConsidered(InformationStatus.valueOf(text(
                node, "religiousVocationConsidered", index, "annualResponse.religiousVocationConsidered")));
        response.setMassAttendanceFrequency(MemberMassAttendanceFrequency.valueOf(text(
                node, "massAttendanceFrequency", index, "annualResponse.massAttendanceFrequency")));
        setStatusDetails(required(node, "saturdayOratorioImpediment", index), index,
                "annualResponse.saturdayOratorioImpediment",
                response::setSaturdayOratorioImpedimentStatus, response::setSaturdayOratorioImpedimentDetails);
        response.setFormationAndMeetingInterests(nullableBounded(
                node, "formationAndMeetingInterests", index, "annualResponse.formationAndMeetingInterests"));
        response.setCoordinationInterest(MemberCoordinationInterest.valueOf(text(
                node, "coordinationInterest", index, "annualResponse.coordinationInterest")));
        response.setAdditionalComments(nullableBounded(
                node, "additionalComments", index, "annualResponse.additionalComments"));
        response.setOratorioActivitySuggestions(nullableBounded(
                node, "oratorioActivitySuggestions", index, "annualResponse.oratorioActivitySuggestions"));
        response.setInstagramPostSuggestions(nullableBounded(
                node, "instagramPostSuggestions", index, "annualResponse.instagramPostSuggestions"));
        response.setImportBatchId(batchId); response.setCreatedAt(Instant.now());
        return response;
    }

    private void setStatusDetails(JsonNode node, int index, String field,
                                  java.util.function.Consumer<InformationStatus> status,
                                  java.util.function.Consumer<String> details) {
        DietaryRestriction value = new DietaryRestriction(
                InformationStatus.valueOf(text(node, "status", index, field + ".status")),
                nullableBounded(node, "details", index, field + ".details"));
        status.accept(value.status()); details.accept(value.details());
    }

    private <E extends Enum<E>> Set<E> enumSet(JsonNode values, Class<E> type, int index) {
        return enumSet(values, type, index, type.getSimpleName());
    }

    private <E extends Enum<E>> Set<E> enumSet(JsonNode values, Class<E> type, int index, String field) {
        if (!values.isArray()) throw safe("INVALID_COLLECTION", index, field);
        LinkedHashSet<E> result = new LinkedHashSet<>();
        for (JsonNode value : values) {
            try {
                if (!value.isTextual()) throw safe("INVALID_TEXT", index, field);
                result.add(Enum.valueOf(type, value.textValue()));
            } catch (RuntimeException exception) {
                throw safe("UNSUPPORTED_VALUE", index, field);
            }
        }
        if (result.size() != values.size()) throw safe("DUPLICATE_VALUE", index, field);
        return result;
    }

    private <E extends Enum<E>> Map<E, InformationStatus> enumStatusSet(JsonNode values, Class<E> type, int index) {
        if (!values.isObject() || values.size() != type.getEnumConstants().length)
            throw safe("INVALID_CATALOG_MAP", index, type.getSimpleName());
        EnumMap<E, InformationStatus> result = new EnumMap<>(type);
        values.fields().forEachRemaining(entry -> {
            if (!entry.getValue().isTextual()) throw safe("INVALID_TEXT", index, type.getSimpleName());
            result.put(Enum.valueOf(type, entry.getKey()), InformationStatus.valueOf(entry.getValue().textValue()));
        });
        if (result.size() != type.getEnumConstants().length) throw safe("INVALID_CATALOG_MAP", index, type.getSimpleName());
        return result;
    }

    private String checksum(JsonNode document, List<ImportRecord> records) throws Exception {
        ObjectNode canonical = objectMapper.createObjectNode();
        canonical.put("schemaVersion", SCHEMA);
        ObjectNode batch = canonical.putObject("batch");
        batch.put("id", text(document.path("batch"), "id"));
        batch.put("surveyCycle", document.path("batch").path("surveyCycle").asInt());
        ArrayNode payloads = canonical.putArray("records");
        records.stream().sorted(Comparator.comparing(record -> record.member().getId())).forEach(record -> {
            JsonNode source = document.path("records").path(record.index());
            ObjectNode payload = payloads.addObject();
            payload.set("annualResponse", sorted(source.path("annualResponse")));
            payload.set("member", sorted(source.path("member")));
        });
        byte[] bytes = objectMapper.writeValueAsBytes(sorted(canonical));
        return "sha256:" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private JsonNode sorted(JsonNode value) {
        if (value.isObject()) {
            ObjectNode result = objectMapper.createObjectNode();
            List<String> names = new ArrayList<>(); value.fieldNames().forEachRemaining(names::add);
            names.stream().sorted().forEach(name -> result.set(name, sorted(value.get(name))));
            return result;
        }
        if (value.isArray()) {
            ArrayNode result = objectMapper.createArrayNode(); value.forEach(item -> result.add(sorted(item))); return result;
        }
        return value;
    }

    private JsonNode required(JsonNode node, String field, int index) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) throw safe("REQUIRED_FIELD", index, field);
        return value;
    }
    private String text(JsonNode node, String field) { return text(node, field, -1, field); }
    private String text(JsonNode node, String field, int index, String diagnosticField) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) throw safe("REQUIRED_FIELD", index, diagnosticField);
        if (!value.isTextual()) throw safe("INVALID_TEXT", index, diagnosticField);
        return value.textValue();
    }
    private String nullableText(JsonNode node, String field, int index, String diagnosticField) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) return null;
        if (!value.isTextual()) throw safe("INVALID_TEXT", index, diagnosticField);
        return value.textValue();
    }
    private String nullableBounded(JsonNode node, String field, int index, String diagnosticField) {
        String value = nullableText(node, field, index, diagnosticField);
        return value == null ? null : normalizedBounded(value, 2000, true);
    }
    private String normalizedBounded(String value, int max, boolean nullableBlank) {
        String normalized = java.text.Normalizer.normalize(stripUnicodeWhitespace(value), java.text.Normalizer.Form.NFC);
        if (normalized.isBlank()) { if (nullableBlank) return null; throw safe("BLANK_VALUE", -1, null); }
        if (normalized.codePointCount(0, normalized.length()) > max) throw safe("VALUE_TOO_LONG", -1, null);
        return normalized;
    }
    private String normalizedCollapsedBounded(String value, int max) {
        String stripped = stripUnicodeWhitespace(value);
        StringBuilder collapsed = new StringBuilder(stripped.length());
        boolean whitespace = false;
        for (int offset = 0; offset < stripped.length();) {
            int codePoint = stripped.codePointAt(offset);
            if (isUnicodeWhitespace(codePoint)) {
                whitespace = true;
            } else {
                if (whitespace && !collapsed.isEmpty()) collapsed.append(' ');
                collapsed.appendCodePoint(codePoint);
                whitespace = false;
            }
            offset += Character.charCount(codePoint);
        }
        return normalizedBounded(collapsed.toString(), max, false);
    }
    private String stripUnicodeWhitespace(String value) {
        int start = 0;
        int end = value.length();
        while (start < end) {
            int codePoint = value.codePointAt(start);
            if (!isUnicodeWhitespace(codePoint)) break;
            start += Character.charCount(codePoint);
        }
        while (end > start) {
            int codePoint = value.codePointBefore(end);
            if (!isUnicodeWhitespace(codePoint)) break;
            end -= Character.charCount(codePoint);
        }
        return value.substring(start, end);
    }
    private boolean isUnicodeWhitespace(int codePoint) {
        return Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint);
    }

    private boolean sameMemberProjection(MemberEntity expected, MemberEntity actual) {
        if (!expected.getImportBatchId().equals(actual.getImportBatchId())) return false;
        return java.util.Objects.equals(expected.getName(), actual.getName())
                && expected.getVersion() == actual.getVersion()
                && java.util.Objects.equals(expected.getBirthDate(), actual.getBirthDate())
                && java.util.Objects.equals(expected.getGamEntryDate(), actual.getGamEntryDate())
                && java.util.Objects.equals(expected.getResidentialCity(), actual.getResidentialCity())
                && java.util.Objects.equals(expected.getPhoneNumber(), actual.getPhoneNumber())
                && java.util.Objects.equals(expected.getContactEmail(), actual.getContactEmail())
                && java.util.Objects.equals(expected.getDietaryRestrictionStatus(), actual.getDietaryRestrictionStatus())
                && java.util.Objects.equals(expected.getDietaryRestrictionDetails(), actual.getDietaryRestrictionDetails())
                && java.util.Objects.equals(expected.getStatus(), actual.getStatus())
                && actual.getAccount() == null
                && java.util.Objects.equals(expected.getExperiences(), actual.getExperiences())
                && java.util.Objects.equals(expected.getSacraments(), actual.getSacraments())
                && java.util.Objects.equals(expected.getContributionAreas(), actual.getContributionAreas())
                && java.util.Objects.equals(expected.getOtherContributionAreas(), actual.getOtherContributionAreas());
    }

    private boolean sameAnnualProjection(AnnualMemberInformationResponseEntity expected,
                                         AnnualMemberInformationResponseEntity actual) {
        return java.util.Objects.equals(expected.getImportBatchId(), actual.getImportBatchId())
                && actual.getMember() != null
                && java.util.Objects.equals(expected.getMember().getId(), actual.getMember().getId())
                && expected.getSurveyCycle() == actual.getSurveyCycle()
                && java.util.Objects.equals(expected.getSubmittedAt(), actual.getSubmittedAt())
                && java.util.Objects.equals(expected.getOccupations(), actual.getOccupations())
                && java.util.Objects.equals(expected.getOccupationsDetails(), actual.getOccupationsDetails())
                && java.util.Objects.equals(expected.getHealthConditionStatus(), actual.getHealthConditionStatus())
                && java.util.Objects.equals(expected.getHealthConditionDetails(), actual.getHealthConditionDetails())
                && java.util.Objects.equals(expected.getReligiousVocationConsidered(), actual.getReligiousVocationConsidered())
                && java.util.Objects.equals(expected.getMassAttendanceFrequency(), actual.getMassAttendanceFrequency())
                && java.util.Objects.equals(expected.getSaturdayOratorioImpedimentStatus(), actual.getSaturdayOratorioImpedimentStatus())
                && java.util.Objects.equals(expected.getSaturdayOratorioImpedimentDetails(), actual.getSaturdayOratorioImpedimentDetails())
                && java.util.Objects.equals(expected.getFormationAndMeetingInterests(), actual.getFormationAndMeetingInterests())
                && java.util.Objects.equals(expected.getCoordinationInterest(), actual.getCoordinationInterest())
                && java.util.Objects.equals(expected.getAdditionalComments(), actual.getAdditionalComments())
                && java.util.Objects.equals(expected.getOratorioActivitySuggestions(), actual.getOratorioActivitySuggestions())
                && java.util.Objects.equals(expected.getInstagramPostSuggestions(), actual.getInstagramPostSuggestions());
    }
    private UUID uuid(String value, int index, String field) {
        try { return UUID.fromString(value); } catch (RuntimeException ex) { throw safe("INVALID_UUID", index, field); }
    }
    private void requireV7(UUID id, int index, String field) { if (id.version() != 7) throw safe("UUID_NOT_V7", index, field); }
    private String safeSource(String source) {
        return source != null && source.matches("(?:CSV_ROW|ADDITIONAL_RECORD)_[1-9][0-9]*") ? source : null;
    }
    private String option(ApplicationArguments args, String name) {
        List<String> values = args.getOptionValues(name);
        if (values == null || values.size() != 1 || values.getFirst().isBlank()) throw safe("MISSING_OPTION", -1, name);
        return values.getFirst();
    }
    private IllegalArgumentException safe(String code, int index, String field) {
        return new IllegalArgumentException("member-info-import " + code + " recordIndex=" + index
                + (field == null ? "" : " field=" + field));
    }

    private record ImportRecord(int index, String sourceReference, MemberEntity member,
                                AnnualMemberInformationResponseEntity response) {}
    private record ValidatedDocument(UUID batchId, int surveyCycle, String checksum, List<ImportRecord> records) {}
}
