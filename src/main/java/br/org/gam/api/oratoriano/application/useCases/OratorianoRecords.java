package br.org.gam.api.oratoriano.application.useCases;

import br.org.gam.api.oratoriano.application.OratorianoApiModels.AttendanceHistoryItemRDTO;
import br.org.gam.api.oratoriano.application.OratorianoApiModels.AttendanceSummaryRDTO;
import br.org.gam.api.oratoriano.application.OratorianoApiModels.OratorianoRDTO;
import br.org.gam.api.oratoriano.application.OratorianoApiModels.RegisterOratorianoDTO;
import br.org.gam.api.oratoriano.application.OratorianoApiModels.ReplaceOratorianoDTO;
import br.org.gam.api.oratoriano.application.search.OratorianoSearchFilterConverter;
import br.org.gam.api.oratoriano.persistence.OratorianoEntity;
import br.org.gam.api.oratoriano.persistence.OratorianoRepository;
import br.org.gam.api.shared.activitylog.ActivityAction;
import br.org.gam.api.shared.activitylog.ActivityEvents;
import br.org.gam.api.shared.activitylog.ActivityTargetType;
import br.org.gam.api.shared.domain.GamName;
import br.org.gam.api.shared.exception.ConflictException;
import br.org.gam.api.shared.exception.InvalidCommandException;
import br.org.gam.api.shared.exception.NotFoundException;
import br.org.gam.api.shared.phonenumber.GamPhoneNumber;
import br.org.gam.api.shared.persistence.UUIDGenerator;
import br.org.gam.api.shared.specification.SearchDTO;
import br.org.gam.api.shared.validation.RequiredReason;
import br.org.gam.api.shared.web.PagedResponse;
import java.text.Normalizer;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.AuditorAware;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OratorianoRecords {
    private static final ZoneId SAO_PAULO = ZoneId.of("America/Sao_Paulo");

    private final OratorianoRepository repository;
    private final JdbcTemplate jdbcTemplate;
    private final AuditorAware<UUID> auditorAware;
    private final ActivityEvents activityEvents;
    private final Clock clock;
    private final OratorianoSearchFilterConverter searchFilterConverter;

    public OratorianoRecords(
            OratorianoRepository repository,
            JdbcTemplate jdbcTemplate,
            AuditorAware<UUID> auditorAware,
            ActivityEvents activityEvents,
            Clock clock,
            OratorianoSearchFilterConverter searchFilterConverter
    ) {
        this.repository = repository;
        this.jdbcTemplate = jdbcTemplate;
        this.auditorAware = auditorAware;
        this.activityEvents = activityEvents;
        this.clock = clock;
        this.searchFilterConverter = searchFilterConverter;
    }

    @Transactional
    public OratorianoRDTO register(RegisterOratorianoDTO dto) {
        return register(dto, true);
    }

    @Transactional
    public OratorianoRDTO registerWithoutActivity(RegisterOratorianoDTO dto) {
        return register(dto, false);
    }

    private OratorianoRDTO register(RegisterOratorianoDTO dto, boolean emitActivity) {
        GamName name = new GamName(dto.firstName(), dto.surname());
        OratorianoEntity entity = new OratorianoEntity();
        entity.setId(UUIDGenerator.generateUUIDV7());
        entity.setName(name);
        entity.setNameKey(humanEquivalentNameKey(name));
        try {
            repository.saveAndFlush(entity);
        } catch (DataIntegrityViolationException exception) {
            throw nameConflict(name);
        }
        if (emitActivity) {
            activityEvents.moduleActivity(
                    ActivityAction.ORATORIANO_REGISTERED,
                    ActivityTargetType.ORATORIANO,
                    entity.getId(),
                    null,
                    "Oratoriano registered",
                    Map.of("oratorianoId", entity.getId())
            );
        }
        return toRDTO(entity);
    }

    @Transactional(readOnly = true)
    public OratorianoRDTO get(UUID id) {
        return toRDTO(repository.findById(id)
                .orElseThrow(() -> NotFoundException.resource("Oratoriano", id)));
    }

    @Transactional
    public OratorianoRDTO replace(UUID id, ReplaceOratorianoDTO dto) {
        OratorianoEntity entity = repository.findActiveByIdForUpdate(id)
                .orElseThrow(() -> NotFoundException.resource("Oratoriano", id));
        GamName name = new GamName(dto.firstName(), dto.surname());
        LocalDate birthDate = dto.birthDate();
        if (birthDate != null && birthDate.isAfter(LocalDate.now(clock.withZone(SAO_PAULO)))) {
            throw InvalidCommandException.reason("birthDate cannot be in the future.");
        }
        GamPhoneNumber phone = dto.phoneNumber() == null ? null : GamPhoneNumber.fromString(dto.phoneNumber());

        List<String> changedFields = new ArrayList<>();
        boolean nameChanged = !Objects.equals(entity.getName(), name);
        if (nameChanged) changedFields.add("name");
        if (!Objects.equals(entity.getBirthDate(), birthDate)) changedFields.add("birthDate");
        if (!Objects.equals(entity.getPhoneNumber(), phone)) changedFields.add("phoneNumber");
        if (changedFields.isEmpty()) {
            return toRDTO(entity);
        }

        String reason = null;
        if (nameChanged) {
            reason = RequiredReason.normalize(
                    dto.reason(),
                    "Oratoriano name correction requires an audit reason."
            );
        } else if (dto.reason() != null && !dto.reason().isBlank()) {
            reason = RequiredReason.normalize(dto.reason(), "Invalid Oratoriano correction reason.");
        }

        entity.setName(name);
        entity.setNameKey(humanEquivalentNameKey(name));
        entity.setBirthDate(birthDate);
        entity.setPhoneNumber(phone);
        var correctedAt = clock.instant();
        if (nameChanged) {
            entity.setNameManualUpdatedAt(correctedAt);
            entity.setNameSourceFormId(null);
            entity.setNameSourceSignedOn(null);
        }
        if (changedFields.contains("birthDate")) {
            entity.setBirthDateManualUpdatedAt(correctedAt);
            entity.setBirthDateSourceFormId(null);
            entity.setBirthDateSourceSignedOn(null);
        }
        if (changedFields.contains("phoneNumber")) {
            entity.setPhoneManualUpdatedAt(correctedAt);
            entity.setPhoneSourceFormId(null);
            entity.setPhoneSourceSignedOn(null);
        }
        try {
            repository.saveAndFlush(entity);
        } catch (DataIntegrityViolationException exception) {
            throw nameConflict(name);
        }
        activityEvents.moduleActivity(
                ActivityAction.ORATORIANO_UPDATED,
                ActivityTargetType.ORATORIANO,
                id,
                reason,
                "Oratoriano ordinary profile corrected",
                Map.of("oratorianoId", id, "changedFields", List.copyOf(changedFields))
        );
        return toRDTO(entity);
    }

    @Transactional
    public void delete(UUID id, String rawReason) {
        String reason = RequiredReason.normalize(rawReason, "Oratoriano deletion requires an audit reason.");
        OratorianoEntity entity = repository.findActiveByIdForUpdate(id)
                .orElseThrow(() -> NotFoundException.resource("Oratoriano", id));
        Integer immutableForms = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM oratoriano_additional_forms "
                        + "WHERE oratoriano_id = ? AND deleted_at IS NULL AND status <> 'DRAFT'",
                Integer.class,
                id
        );
        if (immutableForms != null && immutableForms > 0) {
            throw ConflictException.resource(
                    "ORATORIANO_HAS_IMMUTABLE_FORMS",
                    "Oratoriano",
                    id,
                    "Completed or historical forms prevent ordinary deletion."
            );
        }
        UUID actor = auditorAware.getCurrentAuditor().orElse(null);
        Timestamp deletionTimestamp = Timestamp.from(clock.instant());
        jdbcTemplate.update(
                "UPDATE oratoriano_form_attachments SET deleted_at = ?, deleted_by = ? "
                        + "WHERE form_id IN (SELECT id FROM oratoriano_additional_forms "
                        + "WHERE oratoriano_id = ? AND status = 'DRAFT' AND deleted_at IS NULL) "
                        + "AND deleted_at IS NULL",
                deletionTimestamp,
                actor,
                id
        );
        jdbcTemplate.update(
                "UPDATE oratoriano_form_print_snapshots SET deleted_at = ?, deleted_by = ? "
                        + "WHERE form_id IN (SELECT id FROM oratoriano_additional_forms "
                        + "WHERE oratoriano_id = ? AND status = 'DRAFT' AND deleted_at IS NULL) "
                        + "AND deleted_at IS NULL",
                deletionTimestamp,
                actor,
                id
        );
        jdbcTemplate.update(
                "UPDATE oratoriano_additional_forms SET deleted_at = ?, deleted_by = ? "
                        + "WHERE oratoriano_id = ? AND status = 'DRAFT' AND deleted_at IS NULL",
                deletionTimestamp,
                actor,
                id
        );
        repository.delete(entity);
        activityEvents.moduleActivity(
                ActivityAction.ORATORIANO_DELETED,
                ActivityTargetType.ORATORIANO,
                id,
                reason,
                "Oratoriano deleted",
                Map.of("oratorianoId", id)
        );
    }

    @Transactional
    public void restore(UUID id, String rawReason) {
        String reason = RequiredReason.normalize(rawReason, "Oratoriano restoration requires an audit reason.");
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, deleted_at FROM oratorianos WHERE id = ? FOR UPDATE",
                id
        );
        if (rows.isEmpty()) {
            throw NotFoundException.resource("Oratoriano", id);
        }
        if (rows.getFirst().get("deleted_at") == null) {
            throw ConflictException.resource("Oratoriano", id, "Oratoriano is already active.");
        }
        UUID actor = auditorAware.getCurrentAuditor().orElse(null);
        jdbcTemplate.update(
                "UPDATE oratorianos SET deleted_at = NULL, deleted_by = NULL, updated_at = ?, updated_by = ? "
                        + "WHERE id = ?",
                Timestamp.from(clock.instant()),
                actor,
                id
        );
        activityEvents.moduleActivity(
                ActivityAction.ORATORIANO_RESTORED,
                ActivityTargetType.ORATORIANO,
                id,
                reason,
                "Oratoriano restored",
                Map.of("oratorianoId", id)
        );
    }

    @Transactional(readOnly = true)
    public PagedResponse<OratorianoRDTO> search(
            SearchDTO search,
            List<String> sorts,
            Integer attendanceYear,
            int page,
            int size
    ) {
        List<OratorianoEntity> matches = new ArrayList<>(
                repository.findAll(searchFilterConverter.convert(search))
        );

        List<String> submittedSorts = sorts == null
                ? List.of()
                : sorts.stream().filter(sort -> sort != null && !sort.isBlank()).toList();
        List<String> requestedSorts = submittedSorts.stream().allMatch(sort -> !sort.contains(","))
                ? pairSortTokens(submittedSorts)
                : submittedSorts;
        if (!requestedSorts.isEmpty()) {
            if (requestedSorts.stream().anyMatch(sort ->
                    !"oratorioYearAttendances,asc".equals(sort)
                            && !"oratorioYearAttendances,desc".equals(sort))) {
                throw InvalidCommandException.reason("Unsupported Oratoriano sort.");
            }
            boolean descending = requestedSorts.getFirst().endsWith(",desc");
            int selectedYear = attendanceYear == null
                    ? LocalDate.now(clock.withZone(SAO_PAULO)).getYear()
                    : attendanceYear;
            Map<UUID, Long> attendanceCounts = yearlyAttendanceCounts(selectedYear);
            Comparator<OratorianoEntity> attendanceComparator =
                    Comparator.comparingLong(item ->
                            attendanceCounts.getOrDefault(item.getId(), 0L));
            if (descending) {
                attendanceComparator = attendanceComparator.reversed();
            }
            matches = matches.stream()
                    .sorted(attendanceComparator
                            .thenComparing(OratorianoEntity::getNameKey)
                            .thenComparing(OratorianoEntity::getId))
                    .toList();
        } else {
            matches = matches.stream()
                    .sorted(Comparator
                            .comparing(OratorianoEntity::getNameKey)
                            .thenComparing(OratorianoEntity::getId))
                    .toList();
        }

        int boundedSize = Math.max(1, Math.min(size, 100));
        int boundedPage = Math.max(page, 0);
        int from = Math.min(boundedPage * boundedSize, matches.size());
        int to = Math.min(from + boundedSize, matches.size());
        List<OratorianoRDTO> items = matches.subList(from, to).stream().map(this::toRDTO).toList();
        int totalPages = matches.isEmpty() ? 0 : (int) Math.ceil((double) matches.size() / boundedSize);
        return new PagedResponse<>(
                items,
                boundedPage,
                boundedSize,
                matches.size(),
                totalPages,
                boundedPage == 0,
                totalPages == 0 || boundedPage >= totalPages - 1
        );
    }

    @Transactional(readOnly = true)
    public AttendanceSummaryRDTO attendanceSummary(UUID id, Integer year, Integer month) {
        repository.findById(id).orElseThrow(() -> NotFoundException.resource("Oratoriano", id));
        if (month != null && year == null) {
            throw InvalidCommandException.reason("Attendance month requires an attendance year.");
        }
        if (month != null && (month < 1 || month > 12)) {
            throw InvalidCommandException.reason("Attendance month must be between 1 and 12.");
        }
        List<LocalDate> dates = activeAttendanceDates(id);
        long yearAttendances = year == null
                ? 0
                : dates.stream().filter(date -> date.getYear() == year).count();
        long yearDistinctMonths = year == null
                ? 0
                : dates.stream()
                        .filter(date -> date.getYear() == year)
                        .map(LocalDate::getMonthValue)
                        .distinct()
                        .count();
        long monthAttendances = month == null
                ? 0
                : dates.stream()
                        .filter(date -> date.getYear() == year && date.getMonthValue() == month)
                        .count();
        return new AttendanceSummaryRDTO(
                dates.size(),
                dates.stream().map(date -> date.getYear() + "-" + date.getMonthValue()).distinct().count(),
                dates.stream().map(LocalDate::getYear).distinct().count(),
                year == null ? null : yearAttendances,
                year == null ? null : yearDistinctMonths,
                month == null ? null : monthAttendances
        );
    }

    @Transactional(readOnly = true)
    public PagedResponse<AttendanceHistoryItemRDTO> attendanceHistory(UUID id, int page, int size) {
        repository.findById(id).orElseThrow(() -> NotFoundException.resource("Oratoriano", id));
        int boundedSize = Math.max(1, Math.min(size, 100));
        int boundedPage = Math.max(page, 0);
        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM oratoriano_attendances a "
                        + "JOIN oratorios o ON o.id = a.oratorio_id "
                        + "WHERE a.oratoriano_id = ? AND a.deleted_at IS NULL AND o.deleted_at IS NULL",
                Long.class,
                id
        );
        List<AttendanceHistoryItemRDTO> items = jdbcTemplate.query(
                "SELECT o.id, o.local_date, e.status::text "
                        + "FROM oratoriano_attendances a "
                        + "JOIN oratorios o ON o.id = a.oratorio_id "
                        + "JOIN events e ON e.id = o.event_id "
                        + "WHERE a.oratoriano_id = ? AND a.deleted_at IS NULL AND o.deleted_at IS NULL "
                        + "ORDER BY o.local_date DESC, o.id DESC LIMIT ? OFFSET ?",
                (rs, rowNum) -> new AttendanceHistoryItemRDTO(
                        rs.getObject("id", UUID.class),
                        rs.getObject("local_date", LocalDate.class),
                        rs.getString("status")
                ),
                id,
                boundedSize,
                boundedPage * boundedSize
        );
        long totalElements = total == null ? 0 : total;
        int totalPages = totalElements == 0
                ? 0
                : (int) Math.ceil((double) totalElements / boundedSize);
        return new PagedResponse<>(
                items,
                boundedPage,
                boundedSize,
                totalElements,
                totalPages,
                boundedPage == 0,
                totalPages == 0 || boundedPage >= totalPages - 1
        );
    }

    public static String humanEquivalentNameKey(GamName name) {
        return comparisonKey(name.getFullName());
    }

    private static List<String> pairSortTokens(List<String> tokens) {
        if (tokens.size() % 2 != 0) {
            return tokens;
        }
        List<String> sorts = new ArrayList<>(tokens.size() / 2);
        for (int index = 0; index < tokens.size(); index += 2) {
            sorts.add(tokens.get(index) + "," + tokens.get(index + 1));
        }
        return List.copyOf(sorts);
    }

    public static String comparisonKey(String fullName) {
        String normalizedSeparators = Normalizer.normalize(fullName, Normalizer.Form.NFC)
                .replaceAll("[\\u2018\\u2019\\u201A\\u201B\\u2032\\u00B4\\u0060]", "'")
                .replaceAll("[\\u2010\\u2011\\u2012\\u2013\\u2014\\u2015\\u2212]", "-");
        String normalizedWhitespace = normalizedSeparators.strip()
                .replaceAll("\\p{javaWhitespace}+", " ");
        return Normalizer.normalize(normalizedWhitespace, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT);
    }

    public OratorianoRDTO toRDTO(OratorianoEntity entity) {
        return new OratorianoRDTO(
                entity.getId(),
                entity.getName().firstName(),
                entity.getName().surname(),
                entity.getBirthDate(),
                entity.getPhoneNumber() == null ? null : entity.getPhoneNumber().value()
        );
    }

    private ConflictException nameConflict(GamName name) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("nameKey", humanEquivalentNameKey(name));
        return ConflictException.resource(
                "ORATORIANO_NAME_RESERVED",
                "Oratoriano",
                humanEquivalentNameKey(name),
                "An active or deleted Oratoriano already reserves this human-equivalent name.",
                details
        );
    }

    private List<LocalDate> activeAttendanceDates(UUID oratorianoId) {
        return jdbcTemplate.queryForList(
                "SELECT o.local_date FROM oratoriano_attendances a "
                        + "JOIN oratorios o ON o.id = a.oratorio_id "
                        + "WHERE a.oratoriano_id = ? AND a.deleted_at IS NULL AND o.deleted_at IS NULL",
                LocalDate.class,
                oratorianoId
        );
    }

    private Map<UUID, Long> yearlyAttendanceCounts(int year) {
        Map<UUID, Long> counts = new HashMap<>();
        jdbcTemplate.queryForList(
                "SELECT a.oratoriano_id, COUNT(*) AS attendance_count "
                        + "FROM oratoriano_attendances a "
                        + "JOIN oratorios o ON o.id = a.oratorio_id "
                        + "WHERE a.deleted_at IS NULL AND o.deleted_at IS NULL "
                        + "AND o.local_date >= ? AND o.local_date < ? "
                        + "GROUP BY a.oratoriano_id",
                LocalDate.of(year, 1, 1),
                LocalDate.of(year + 1, 1, 1)
        ).forEach(row -> counts.put(
                (UUID) row.get("oratoriano_id"),
                ((Number) row.get("attendance_count")).longValue()
        ));
        return counts;
    }
}
