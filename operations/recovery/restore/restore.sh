#!/usr/bin/env bash
set -Eeuo pipefail

# Restoration is allowed only in an isolated environment.  The procedure
# consumes an externally supplied recovery identity; it does not create,
# copy, or store recovery-key custody material.

: "${AWS_REGION:?AWS_REGION is required}"
: "${RECOVERY_BUCKET:?RECOVERY_BUCKET is required}"
: "${RECOVERY_OBJECT_KEY:?RECOVERY_OBJECT_KEY is required}"
: "${RECOVERY_SHA256:?RECOVERY_SHA256 is required}"
: "${AGE_IDENTITY_FILE:?AGE_IDENTITY_FILE must point to externally supplied custody}"
: "${RESTORE_DATABASE_URL:?RESTORE_DATABASE_URL is required}"
: "${RESTORE_ADMIN_DATABASE_URL:?RESTORE_ADMIN_DATABASE_URL is required}"
: "${RESTORE_DATABASE_NAME:?RESTORE_DATABASE_NAME is required}"
: "${JWT_SIGNING_SECRET_FILE:?JWT_SIGNING_SECRET_FILE is required}"
: "${RESTORE_NETWORK_MODE:?RESTORE_NETWORK_MODE is required}"
: "${PUBLIC_TRAFFIC_DISABLED:?PUBLIC_TRAFFIC_DISABLED is required}"
: "${RESTORE_PUBLIC_INTERFACE:?RESTORE_PUBLIC_INTERFACE is required}"
: "${RESTORATION_CORRECTIVE_ACTION:?RESTORATION_CORRECTIVE_ACTION is required}"
: "${REPRESENTATIVE_ACCESS_CHECK_COMMAND:?representative application access check is required}"

# S3 Glacier Flexible Retrieval is normally restored within a few hours, but
# the recovery procedure keeps a configurable portion of the 24-hour RTO for
# downloading, decrypting, and validating the recovery artifact.
GLACIER_RESTORE_DAYS="${GLACIER_RESTORE_DAYS:-1}"
GLACIER_RESTORE_POLL_INTERVAL_SECONDS="${GLACIER_RESTORE_POLL_INTERVAL_SECONDS:-300}"
GLACIER_RESTORE_TIMEOUT_SECONDS="${GLACIER_RESTORE_TIMEOUT_SECONDS:-82800}"
TOTAL_RECOVERY_TIMEOUT_SECONDS="${TOTAL_RECOVERY_TIMEOUT_SECONDS:-84600}"
RESTORE_CLEANUP_TIMEOUT_SECONDS="${RESTORE_CLEANUP_TIMEOUT_SECONDS:-30}"

for glacier_setting in \
    GLACIER_RESTORE_DAYS \
    GLACIER_RESTORE_POLL_INTERVAL_SECONDS \
    GLACIER_RESTORE_TIMEOUT_SECONDS \
    TOTAL_RECOVERY_TIMEOUT_SECONDS \
    RESTORE_CLEANUP_TIMEOUT_SECONDS; do
    glacier_value="${!glacier_setting}"
    if [[ ! "$glacier_value" =~ ^[1-9][0-9]*$ ]]; then
        echo "$glacier_setting must be a positive integer" >&2
        exit 1
    fi
done
if (( TOTAL_RECOVERY_TIMEOUT_SECONDS >= 86400 )); then
    echo "TOTAL_RECOVERY_TIMEOUT_SECONDS must leave the recovery within the 24-hour RTO" >&2
    exit 1
fi
if (( GLACIER_RESTORE_TIMEOUT_SECONDS > TOTAL_RECOVERY_TIMEOUT_SECONDS )); then
    echo "GLACIER_RESTORE_TIMEOUT_SECONDS must fit inside the total recovery budget" >&2
    exit 1
fi

RESTORATION_STARTED="$(date +%s)"
TOTAL_RECOVERY_DEADLINE="$((RESTORATION_STARTED + TOTAL_RECOVERY_TIMEOUT_SECONDS))"

require_recovery_budget() {
    if (( $(date +%s) >= TOTAL_RECOVERY_DEADLINE )); then
        echo "total recovery budget expired before restoration validation completed" >&2
        exit 1
    fi
}

run_with_recovery_deadline() {
    local remaining_seconds
    remaining_seconds="$((TOTAL_RECOVERY_DEADLINE - $(date +%s)))"
    if (( remaining_seconds <= 0 )); then
        echo "total recovery budget expired before restoration validation completed" >&2
        return 1
    fi
    timeout --signal=TERM --kill-after=30s "$remaining_seconds" "$@"
}

RESTORATION_EVIDENCE_FILE="${RESTORATION_EVIDENCE_FILE:-/var/lib/gam-recovery/evidence/$(date -u '+%Y%m%dT%H%M%SZ').json}"
RESTORATION_EVIDENCE_PENDING_FILE="${RESTORATION_EVIDENCE_PENDING_FILE:-${RESTORATION_EVIDENCE_FILE}.pending.$$}"
export RESTORATION_EVIDENCE_FILE
export RESTORATION_EVIDENCE_PENDING_FILE

if [[ "$RESTORE_NETWORK_MODE" != "isolated" ]]; then
    echo "refusing restoration outside an isolated network" >&2
    exit 1
fi
if [[ "$PUBLIC_TRAFFIC_DISABLED" != "true" ]]; then
    echo "refusing restoration while public traffic is enabled" >&2
    exit 1
fi

# The isolation contract applies at the host boundary as well as to the
# restore application's network mode.  The interface is an external input;
# no concrete VPS address is embedded in the procedure.
export RESTORE_PUBLIC_INTERFACE
RESTORE_IPV4_ISOLATION_ADDED=false
RESTORE_IPV6_ISOLATION_ADDED=false

remove_restore_isolation() {
    local isolation_cleanup_status=0
    if [[ "$RESTORE_IPV4_ISOLATION_ADDED" == "true" ]]; then
        if iptables -D DOCKER-USER -i "$RESTORE_PUBLIC_INTERFACE" -p tcp -m multiport --dports 80,443 -j DROP; then
            RESTORE_IPV4_ISOLATION_ADDED=false
        else
            isolation_cleanup_status=1
        fi
    fi
    if [[ "$RESTORE_IPV6_ISOLATION_ADDED" == "true" ]]; then
        if ip6tables -D DOCKER-USER -i "$RESTORE_PUBLIC_INTERFACE" -p tcp -m multiport --dports 80,443 -j DROP; then
            RESTORE_IPV6_ISOLATION_ADDED=false
        else
            isolation_cleanup_status=1
        fi
    fi
    return "$isolation_cleanup_status"
}
trap remove_restore_isolation EXIT

if ! iptables -C DOCKER-USER -i "$RESTORE_PUBLIC_INTERFACE" -p tcp -m multiport --dports 80,443 -j DROP; then
    iptables -I DOCKER-USER 1 -i "$RESTORE_PUBLIC_INTERFACE" -p tcp -m multiport --dports 80,443 -j DROP
    RESTORE_IPV4_ISOLATION_ADDED=true
fi
if ! ip6tables -C DOCKER-USER -i "$RESTORE_PUBLIC_INTERFACE" -p tcp -m multiport --dports 80,443 -j DROP; then
    ip6tables -I DOCKER-USER 1 -i "$RESTORE_PUBLIC_INTERFACE" -p tcp -m multiport --dports 80,443 -j DROP
    RESTORE_IPV6_ISOLATION_ADDED=true
fi

umask 077
STAGING_DIR="$(mktemp -d "${TMPDIR:-/tmp}/gam-restore.XXXXXX")"
ENCRYPTED_ARCHIVE="$STAGING_DIR/recovery.dump.age"
DECRYPTED_PACKAGE="$STAGING_DIR/recovery-artifact.tar.gz"
RESTORED_FILES="$STAGING_DIR/files"
: > "$ENCRYPTED_ARCHIVE"

dropdb() {
    local dropdb_executable
    dropdb_executable="$(type -P dropdb)"
    timeout --signal=TERM --kill-after=5s "${RESTORE_CLEANUP_TIMEOUT_SECONDS:-30}" "$dropdb_executable" "$@"
}

cleanup() {
    local cleanup_status=0
    if [[ -n "${RESTORED_DATABASE_CREATED:-}" && "$RESTORED_DATABASE_CREATED" == "true" ]]; then
        if [[ "${CONTROLLED_PRODUCTION_RECOVERY:-false}" != "true" ]]; then
            dropdb --if-exists --maintenance-db "$RESTORE_ADMIN_DATABASE_URL" "$RESTORE_DATABASE_NAME" >/dev/null 2>&1 || cleanup_status=1 # bounded dropdb cleanup
        fi
    fi
    if [[ -d "$STAGING_DIR" ]]; then
        local restore_tool_dir
        restore_tool_dir="$(dirname "$(type -P dropdb || printf '%s' dropdb)")"
        PATH="$restore_tool_dir:$PATH"
        if ! hash -r 2>/dev/null; then
            :
        fi
        if [[ -f "$ENCRYPTED_ARCHIVE" ]]; then
            shred --remove --zero --force "$ENCRYPTED_ARCHIVE" 2>/dev/null || cleanup_status=1
        fi
        if ! find "$STAGING_DIR" -type f -exec shred --remove --zero --force {} + 2>/dev/null; then
            cleanup_status=1
        fi
        if ! rm -rf -- "$STAGING_DIR"; then
            cleanup_status=1
        fi
    fi
    if ! remove_restore_isolation; then
        cleanup_status=1
    fi
    if (( cleanup_status == 0 )); then
        if [[ -f "${RESTORATION_EVIDENCE_PENDING_FILE:-}" ]]; then
            if ! mv -- "$RESTORATION_EVIDENCE_PENDING_FILE" "$RESTORATION_EVIDENCE_FILE"; then
                cleanup_status=1
            else
                echo "isolated restoration verified; universal sign-in is required"
            fi
        fi
    fi
    if (( cleanup_status != 0 )); then
        if [[ -n "${RESTORATION_EVIDENCE_PENDING_FILE:-}" ]]; then
            rm -f -- "$RESTORATION_EVIDENCE_PENDING_FILE" || cleanup_status=1
        fi
        if [[ -f "${RESTORATION_EVIDENCE_FILE:-}" ]]; then
            rm -f -- "$RESTORATION_EVIDENCE_FILE" || cleanup_status=1
        fi
    fi
    return "$cleanup_status"
}
trap cleanup EXIT

OBJECT_METADATA_ERROR="$STAGING_DIR/head-object-error"
if ! OBJECT_METADATA="$(run_with_recovery_deadline aws s3api head-object \
    --bucket "$RECOVERY_BUCKET" \
    --key "$RECOVERY_OBJECT_KEY" \
    --region "$AWS_REGION" 2>"$OBJECT_METADATA_ERROR")"; then
    cat "$OBJECT_METADATA_ERROR" >&2
    exit 1
fi
require_recovery_budget
REMOTE_SHA256="$(jq -r '.Metadata.sha256 // empty' <<<"$OBJECT_METADATA")"
REMOTE_MODE="$(jq -r '.ObjectLockMode // empty' <<<"$OBJECT_METADATA")"
REMOTE_SIZE="$(jq -r '.ContentLength // 0' <<<"$OBJECT_METADATA")"
REMOTE_STORAGE_CLASS="$(jq -r '.StorageClass // "STANDARD"' <<<"$OBJECT_METADATA")"
test "$REMOTE_SHA256" = "$RECOVERY_SHA256"
test "$REMOTE_MODE" = COMPLIANCE
test "$REMOTE_SIZE" -gt 0

# Monthly recovery points older than 90 days use Glacier Flexible Retrieval.
# S3 exposes the temporary availability state through the Restore response
# field.  A restore request is accepted, already in progress, or already
# restored without making the recovery fail; the copy is attempted only after
# head-object reports ongoing-request="false".
if [[ "$REMOTE_STORAGE_CLASS" == "GLACIER" ]]; then
    RESTORE_STATE="$(jq -r '.Restore // empty' <<<"$OBJECT_METADATA")"
    if [[ "$RESTORE_STATE" != *'ongoing-request="false"'* ]]; then
        RESTORE_ALREADY_ACTIVE=false
        RESTORE_OBJECT_ERROR="$STAGING_DIR/restore-object-error"
        RESTORE_OBJECT_STATUS=0
        run_with_recovery_deadline aws s3api restore-object \
            --bucket "$RECOVERY_BUCKET" \
            --key "$RECOVERY_OBJECT_KEY" \
            --region "$AWS_REGION" \
            --restore-request "{\"Days\":${GLACIER_RESTORE_DAYS},\"GlacierJobParameters\":{\"Tier\":\"Standard\"}}" \
            >"$STAGING_DIR/restore-object-output" \
            2>"$RESTORE_OBJECT_ERROR" || RESTORE_OBJECT_STATUS=$?
        if (( RESTORE_OBJECT_STATUS != 0 )); then
            if ! grep -qiE 'restorealreadyinprogress|restore already in progress' "$RESTORE_OBJECT_ERROR" \
                && ! grep -qiE 'objectalreadyinactivetiererror|object already in active tier' "$RESTORE_OBJECT_ERROR"; then
                cat "$RESTORE_OBJECT_ERROR" >&2
                exit 1
            elif grep -qiE 'objectalreadyinactivetiererror|object already in active tier' "$RESTORE_OBJECT_ERROR"; then
                RESTORE_ALREADY_ACTIVE=true
            fi
        fi

        GLACIER_RESTORE_DEADLINE="$((RESTORATION_STARTED + TOTAL_RECOVERY_TIMEOUT_SECONDS))"
        GLACIER_PROVIDER_DEADLINE="$(( $(date +%s) + GLACIER_RESTORE_TIMEOUT_SECONDS ))"
        if (( GLACIER_PROVIDER_DEADLINE < GLACIER_RESTORE_DEADLINE )); then
            GLACIER_RESTORE_DEADLINE="$GLACIER_PROVIDER_DEADLINE"
        fi
        if (( GLACIER_RESTORE_DEADLINE > TOTAL_RECOVERY_DEADLINE )); then
            GLACIER_RESTORE_DEADLINE="$TOTAL_RECOVERY_DEADLINE"
        fi
        while true; do
            RESTORE_HEAD_ERROR="$STAGING_DIR/restore-head-object-error"
            if OBJECT_METADATA="$(run_with_recovery_deadline aws s3api head-object \
                --bucket "$RECOVERY_BUCKET" \
                --key "$RECOVERY_OBJECT_KEY" \
                --region "$AWS_REGION" 2>"$RESTORE_HEAD_ERROR")"; then
                RESTORE_STATE="$(jq -r '.Restore // empty' <<<"$OBJECT_METADATA")"
                if [[ "$RESTORE_STATE" == *'ongoing-request="false"'* \
                    || ( "$RESTORE_ALREADY_ACTIVE" == true && -z "$RESTORE_STATE" ) ]]; then
                    break
                fi
            elif ! grep -qi 'invalidobjectstate' "$RESTORE_HEAD_ERROR"; then
                cat "$RESTORE_HEAD_ERROR" >&2
                exit 1
            fi

            if (( $(date +%s) >= GLACIER_RESTORE_DEADLINE )); then
                echo "timed out waiting for S3 Glacier Flexible Retrieval restore" >&2
                exit 1
            fi
            GLACIER_RESTORE_SLEEP_SECONDS="$GLACIER_RESTORE_POLL_INTERVAL_SECONDS"
            GLACIER_RESTORE_REMAINING_SECONDS="$(( GLACIER_RESTORE_DEADLINE - $(date +%s) ))"
            if (( GLACIER_RESTORE_SLEEP_SECONDS > GLACIER_RESTORE_REMAINING_SECONDS )); then
                GLACIER_RESTORE_SLEEP_SECONDS="$GLACIER_RESTORE_REMAINING_SECONDS"
            fi
            sleep "$GLACIER_RESTORE_SLEEP_SECONDS"
        done
    fi
fi
require_recovery_budget

if [[ "${CONTROLLED_PRODUCTION_RECOVERY:-false}" != "true" ]]; then
    run_with_recovery_deadline dropdb --if-exists --maintenance-db "$RESTORE_ADMIN_DATABASE_URL" "$RESTORE_DATABASE_NAME"
    run_with_recovery_deadline createdb --maintenance-db "$RESTORE_ADMIN_DATABASE_URL" "$RESTORE_DATABASE_NAME"
else
    # A controlled recovery may target a pre-created isolated database.
    run_with_recovery_deadline createdb --maintenance-db "$RESTORE_ADMIN_DATABASE_URL" "$RESTORE_DATABASE_NAME" 2>/dev/null || true
fi
RESTORED_DATABASE_CREATED=true

ARCHIVE_COPY_ERROR="$STAGING_DIR/archive-copy-error"
ARCHIVE_COPY_STATUS=0
run_with_recovery_deadline aws s3 cp \
    "s3://${RECOVERY_BUCKET}/${RECOVERY_OBJECT_KEY}" \
    "$ENCRYPTED_ARCHIVE" \
    --region "$AWS_REGION" \
    --only-show-errors \
    2>"$ARCHIVE_COPY_ERROR" || ARCHIVE_COPY_STATUS=$?
if (( ARCHIVE_COPY_STATUS != 0 )); then
    if grep -qi 'invalidobjectstate' "$ARCHIVE_COPY_ERROR"; then
        echo "S3 recovery object is still unavailable after Glacier restore polling" >&2
    fi
    cat "$ARCHIVE_COPY_ERROR" >&2
    exit 1
fi
test "$(sha256sum "$ENCRYPTED_ARCHIVE" | awk '{print $1}')" = "$RECOVERY_SHA256"
require_recovery_budget "$TOTAL_RECOVERY_TIMEOUT_SECONDS"
: "${RESTORATION_REASON:?RESTORATION_REASON is required}"

# Decryption occurs only in the temporary isolated staging directory.
run_with_recovery_deadline age --decrypt --identity "$AGE_IDENTITY_FILE" --output "$DECRYPTED_PACKAGE" "$ENCRYPTED_ARCHIVE"
mkdir -p "$RESTORED_FILES"
run_with_recovery_deadline tar --extract --gzip --file "$DECRYPTED_PACKAGE" --directory "$RESTORED_FILES"
test -s "$RESTORED_FILES/database.dump"
test -s "$RESTORED_FILES/database-roles.sql"
test -s "$RESTORED_FILES/manifest.json"

jq -e --arg recovery_object_key "$RECOVERY_OBJECT_KEY" '
  .schema_version == "gam-recovery-manifest/v1" and
  (.created_at | type == "string" and length > 0) and
  (.postgresql_version | type == "string" and length > 0) and
  (.classification == "daily" or .classification == "weekly" or .classification == "monthly") and
  .object_key == $recovery_object_key and
  (.source_commit | type == "string" and length > 0) and
  (.backend_image_digest | type == "string" and length > 0) and
  (.frontend_release | type == "string" and length > 0) and
  (.frontend_archive | type == "string" and length > 0) and
  (.frontend_sha256 | type == "string" and test("^[0-9a-fA-F]{64}$")) and
  (.migration_state | type == "string" and length > 0) and
  (.dump_size_bytes | type == "number" and . >= 0 and floor == .) and
  (.roles_size_bytes | type == "number" and . >= 0 and floor == .) and
  (.archive_size_bytes | type == "number" and . >= 0 and floor == .) and
  (.archive_sha256 | type == "string" and test("^[0-9a-fA-F]{64}$")) and
  (.roles_sha256 | type == "string" and test("^[0-9a-fA-F]{64}$")) and
  .refresh_token_data == "excluded" and
  (.encryption_scheme | type == "string" and length > 0) and
  (.data_boundary | type == "string" and length > 0)
' "$RESTORED_FILES/manifest.json" >/dev/null

test "$(sha256sum "$RESTORED_FILES/database.dump" | awk '{print $1}')" = \
    "$(jq -r '.archive_sha256' "$RESTORED_FILES/manifest.json")"
test "$(sha256sum "$RESTORED_FILES/database-roles.sql" | awk '{print $1}')" = \
    "$(jq -r '.roles_sha256' "$RESTORED_FILES/manifest.json")"
test "$(stat -c '%s' "$RESTORED_FILES/database.dump")" -eq \
    "$(jq -r '.dump_size_bytes' "$RESTORED_FILES/manifest.json")"
test "$(stat -c '%s' "$RESTORED_FILES/database-roles.sql")" -eq \
    "$(jq -r '.roles_size_bytes' "$RESTORED_FILES/manifest.json")"
test "$(jq -r '.archive_size_bytes' "$RESTORED_FILES/manifest.json")" -eq \
    "$(( $(jq -r '.dump_size_bytes' "$RESTORED_FILES/manifest.json") + $(jq -r '.roles_size_bytes' "$RESTORED_FILES/manifest.json") ))"
MANIFEST_MIGRATION_STATE="$(jq -r '.migration_state' "$RESTORED_FILES/manifest.json")"
export MANIFEST_MIGRATION_STATE
MANIFEST_POSTGRESQL_VERSION="$(jq -r '.postgresql_version' "$RESTORED_FILES/manifest.json")"
TARGET_POSTGRESQL_VERSION="$(run_with_recovery_deadline psql "$RESTORE_DATABASE_URL" -v ON_ERROR_STOP=1 -At -c 'SHOW server_version;')"
MANIFEST_POSTGRESQL_MAJOR_VERSION="${MANIFEST_POSTGRESQL_VERSION%%.*}"
TARGET_POSTGRESQL_MAJOR_VERSION="${TARGET_POSTGRESQL_VERSION%%.*}"
if [[ ! "$MANIFEST_POSTGRESQL_MAJOR_VERSION" =~ ^[0-9]+$ ]]; then
    echo "manifest PostgreSQL major version is invalid" >&2
    exit 1
fi
if [[ ! "$TARGET_POSTGRESQL_MAJOR_VERSION" =~ ^[0-9]+$ ]]; then
    echo "restoration target PostgreSQL major version is invalid" >&2
    exit 1
fi
test "$MANIFEST_POSTGRESQL_MAJOR_VERSION" = "$TARGET_POSTGRESQL_MAJOR_VERSION"
POSTGRESQL_MAJOR_VERSION_CHECKED=true
run_with_recovery_deadline pg_restore --list "$RESTORED_FILES/database.dump" >/dev/null
require_recovery_budget

echo "restoring the selected recovery point into the isolated database"
run_with_recovery_deadline pg_restore \
    --exit-on-error \
    --no-owner \
    --dbname="$RESTORE_DATABASE_URL" \
    "$RESTORED_FILES/database.dump"
require_recovery_budget

# Attachment-byte evidence uses PostgreSQL's cryptographic digest function in
# the isolated database only; the extension is never enabled on production.
run_with_recovery_deadline psql "$RESTORE_DATABASE_URL" -v ON_ERROR_STOP=1 \
    -c 'CREATE EXTENSION IF NOT EXISTS pgcrypto;' >/dev/null

# Role metadata is password-free and is restored into the isolated cluster
# before application-level representative access is exercised.
run_with_recovery_deadline psql "$RESTORE_ADMIN_DATABASE_URL" -v ON_ERROR_STOP=1 \
    -f "$RESTORED_FILES/database-roles.sql" >/dev/null

# The archive excludes refresh-token rows; truncation is explicit so an
# existing isolated database can never retain sessions from a prior attempt.
run_with_recovery_deadline psql "$RESTORE_DATABASE_URL" -v ON_ERROR_STOP=1 \
    -c 'TRUNCATE TABLE public.refresh_tokens;'

# Rotate the JWT signing secret before any restored application access and
# require universal sign-in.  Existing refresh sessions cannot be reused.
install --mode=0600 /dev/null "$JWT_SIGNING_SECRET_FILE"
export JWT_SECRET_KEY="$(openssl rand --hex 32)"
printf '%s\n' "$JWT_SECRET_KEY" > "$JWT_SIGNING_SECRET_FILE"
export GAM_REQUIRE_SIGN_IN=true
export GAM_JWT_SECRET_ROTATED=true

# Keep the application private and require an operator-supplied check through
# the isolated endpoint before considering integrity validation complete.
run_with_recovery_deadline bash -Eeuo pipefail -c "$REPRESENTATIVE_ACCESS_CHECK_COMMAND"
require_recovery_budget "$TOTAL_RECOVERY_TIMEOUT_SECONDS"

RESTORATION_DURATION_SECONDS="$(( $(date +%s) - RESTORATION_STARTED ))"
export RESTORATION_DURATION_SECONDS
export SELECTED_RECOVERY_POINT="$RECOVERY_OBJECT_KEY"
export RECOVERY_CHECKSUM="$RECOVERY_SHA256"
export RESTORATION_REASON
export RESTORATION_CORRECTIVE_ACTION
export MANIFEST_POSTGRESQL_VERSION
export TARGET_POSTGRESQL_VERSION
export POSTGRESQL_MAJOR_VERSION_CHECKED
run_with_recovery_deadline /usr/local/libexec/gam-verify-restoration
require_recovery_budget
