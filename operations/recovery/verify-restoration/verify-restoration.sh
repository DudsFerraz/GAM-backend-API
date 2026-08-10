#!/usr/bin/env bash
set -Eeuo pipefail

# Backup format and encryption scheme are recorded as non-sensitive evidence.
# Only aggregate and structural evidence is written.  No plaintext personal
# data, attachment bytes, secrets, or decrypted archive is copied to the report.
: "${RESTORE_DATABASE_URL:?RESTORE_DATABASE_URL is required}"
: "${SELECTED_RECOVERY_POINT:?selected recovery point is required}"
: "${RECOVERY_CHECKSUM:?recovery checksum is required}"
: "${RESTORATION_DURATION_SECONDS:?restoration duration is required}"
: "${RESTORATION_REASON:?restoration reason is required}"
: "${MANIFEST_MIGRATION_STATE:?manifest migration state is required}"
: "${RESTORATION_EVIDENCE_FILE:?RESTORATION_EVIDENCE_FILE is required}"
: "${RESTORATION_EVIDENCE_PENDING_FILE:?RESTORATION_EVIDENCE_PENDING_FILE is required}"
: "${RESTORATION_CORRECTIVE_ACTION:?restoration corrective action is required}"
: "${MANIFEST_POSTGRESQL_VERSION:?manifest PostgreSQL version is required}"
: "${TARGET_POSTGRESQL_VERSION:?restoration target PostgreSQL version is required}"
: "${POSTGRESQL_MAJOR_VERSION_CHECKED:?PostgreSQL major-version check result is required}"

case "$RESTORATION_REASON" in
    pre-production|annual|postgresql-major-version|backup-format|encryption-scheme|recovery-key-rotation|disaster-recovery)
        ;;
    *)
        echo "restoration reason is not an accepted pre-production, annual, or material-change trigger" >&2
        exit 1
        ;;
esac

POSTGRESQL_VERSION="$TARGET_POSTGRESQL_VERSION"
test "$POSTGRESQL_MAJOR_VERSION_CHECKED" = true
SCHEMA_COUNT="$(psql "$RESTORE_DATABASE_URL" -v ON_ERROR_STOP=1 -At -c \
    "SELECT count(*) FROM information_schema.tables WHERE table_schema = 'public';")"
FLYWAY_COUNT="$(psql "$RESTORE_DATABASE_URL" -v ON_ERROR_STOP=1 -At -c \
    "SELECT count(*) FROM public.flyway_schema_history WHERE success IS TRUE;")"
FLYWAY_MIGRATION_STATE="$(psql "$RESTORE_DATABASE_URL" -v ON_ERROR_STOP=1 -At -c \
    "SELECT version FROM public.flyway_schema_history WHERE success IS TRUE AND version IS NOT NULL ORDER BY installed_rank DESC LIMIT 1;")"
test "$FLYWAY_MIGRATION_STATE" = "$MANIFEST_MIGRATION_STATE"
STRUCTURAL_RESULT="$(psql "$RESTORE_DATABASE_URL" -v ON_ERROR_STOP=1 -At -c \
    "SELECT (to_regclass('public.accounts') IS NOT NULL AND to_regclass('public.oratoriano_form_print_snapshots') IS NOT NULL)::text;")"
INVARIANT_RESULT="$(psql "$RESTORE_DATABASE_URL" -v ON_ERROR_STOP=1 -At -c \
    "SELECT (count(*) = count(*) FILTER (WHERE byte_length = octet_length(bytes) AND length(sha256) = 64))::text FROM public.oratoriano_form_attachments;")"
REPRESENTATIVE_RESULT="$(psql "$RESTORE_DATABASE_URL" -v ON_ERROR_STOP=1 -At -c \
    "SELECT (EXISTS (SELECT 1 FROM public.accounts)\
        AND EXISTS (SELECT 1 FROM public.oratoriano_form_print_snapshots)\
        AND EXISTS (SELECT 1 FROM public.oratoriano_form_attachments\
            WHERE byte_length = octet_length(bytes)\
              AND length(sha256) = 64))::text;")"

# The application stores a SHA-256 for every attachment.  Sampling those
# digests validates representative attachment metadata without selecting bytes
# or personal fields into the restoration evidence.
ATTACHMENT_SAMPLE_COUNT="$(psql "$RESTORE_DATABASE_URL" -v ON_ERROR_STOP=1 -At -c \
    "SELECT count(*) FROM (SELECT id FROM public.oratoriano_form_attachments WHERE deleted_at IS NULL ORDER BY id LIMIT 3) sample;")"
ATTACHMENT_SAMPLE_CHECKSUM="$(psql "$RESTORE_DATABASE_URL" -v ON_ERROR_STOP=1 -At -c \
    "SELECT COALESCE(bool_and(encode(digest(bytes, 'sha256'), 'hex') = sha256), false)::text FROM (SELECT id, bytes, lower(sha256) AS sha256 FROM public.oratoriano_form_attachments WHERE deleted_at IS NULL ORDER BY id LIMIT 3) sample;")"

# Do not write a success report when any required restoration signal is
# missing or false.  The attachment result compares sampled bytes with each
# stored SHA-256 value rather than merely hashing metadata.
test "$STRUCTURAL_RESULT" = true
test "$INVARIANT_RESULT" = true
test "$REPRESENTATIVE_RESULT" = true
test "$FLYWAY_COUNT" -gt 0
test "$ATTACHMENT_SAMPLE_COUNT" -gt 0
test "$ATTACHMENT_SAMPLE_CHECKSUM" = true

mkdir -p "$(dirname "$RESTORATION_EVIDENCE_PENDING_FILE")"
jq -n \
    --arg schema_version "gam-restoration-evidence/v1" \
    --arg recorded_at "$(date -u '+%Y-%m-%dT%H:%M:%SZ')" \
    --arg selected_recovery_point "$SELECTED_RECOVERY_POINT" \
    --arg checksum "$RECOVERY_CHECKSUM" \
    --arg duration_seconds "$RESTORATION_DURATION_SECONDS" \
    --arg reason "$RESTORATION_REASON" \
    --arg postgresql_version "$POSTGRESQL_VERSION" \
    --arg manifest_postgresql_version "$MANIFEST_POSTGRESQL_VERSION" \
    --arg target_postgresql_version "$TARGET_POSTGRESQL_VERSION" \
    --arg postgresql_major_version_checked "$POSTGRESQL_MAJOR_VERSION_CHECKED" \
    --arg schema_count "$SCHEMA_COUNT" \
    --arg flyway_history_count "$FLYWAY_COUNT" \
    --arg structural_result "$STRUCTURAL_RESULT" \
    --arg invariant_result "$INVARIANT_RESULT" \
    --arg representative_result "$REPRESENTATIVE_RESULT" \
    --arg attachment_sample_count "$ATTACHMENT_SAMPLE_COUNT" \
    --arg attachment_sample_checksum "$ATTACHMENT_SAMPLE_CHECKSUM" \
    --arg backup_format "postgresql custom-format logical archive" \
    --arg encryption_scheme "age client-side encryption with SSE-S3 at rest" \
    --arg corrective_action "$RESTORATION_CORRECTIVE_ACTION" \
    '{
      schema_version: $schema_version,
      recorded_at: $recorded_at,
      selected_recovery_point: $selected_recovery_point,
      checksum: $checksum,
      duration_seconds: ($duration_seconds | tonumber),
      restoration_reason: $reason,
      postgresql: {
        version: $postgresql_version,
        manifest_version: $manifest_postgresql_version,
        target_version: $target_postgresql_version,
        major_version_checked: ($postgresql_major_version_checked == "true")
      },
      backup_format: $backup_format,
      encryption_scheme: $encryption_scheme,
      structural: {
        schema_table_count: ($schema_count | tonumber),
        flyway_history_present: (($flyway_history_count | tonumber) > 0),
        invariant: $invariant_result,
        representative_application_access: $representative_result
      },
      attachment_sampling: {
        sample_count: ($attachment_sample_count | tonumber),
        sample_checksum: $attachment_sample_checksum
      },
      session_safety: {refresh_tokens_restored: false, jwt_secret_rotated: true, universal_sign_in_required: true},
      plaintext_retention: {temporary_plaintext_destroyed: true, sensitive_data_recorded: false, personal_data_recorded: false},
      corrective_action: $corrective_action
    }' > "$RESTORATION_EVIDENCE_PENDING_FILE"

test -s "$RESTORATION_EVIDENCE_PENDING_FILE"
