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

for glacier_setting in \
    GLACIER_RESTORE_DAYS \
    GLACIER_RESTORE_POLL_INTERVAL_SECONDS \
    GLACIER_RESTORE_TIMEOUT_SECONDS; do
    glacier_value="${!glacier_setting}"
    if [[ ! "$glacier_value" =~ ^[1-9][0-9]*$ ]]; then
        echo "$glacier_setting must be a positive integer" >&2
        exit 1
    fi
done
if (( GLACIER_RESTORE_TIMEOUT_SECONDS > 86400 )); then
    echo "GLACIER_RESTORE_TIMEOUT_SECONDS must leave the recovery within the 24-hour RTO" >&2
    exit 1
fi

RESTORATION_EVIDENCE_FILE="${RESTORATION_EVIDENCE_FILE:-/var/lib/gam-recovery/evidence/$(date -u '+%Y%m%dT%H%M%SZ').json}"
export RESTORATION_EVIDENCE_FILE

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
iptables -C INPUT -i "$RESTORE_PUBLIC_INTERFACE" -j DROP 2>/dev/null \
    || iptables -I INPUT 1 -i "$RESTORE_PUBLIC_INTERFACE" -j DROP
ip6tables -C INPUT -i "$RESTORE_PUBLIC_INTERFACE" -j DROP 2>/dev/null \
    || ip6tables -I INPUT 1 -i "$RESTORE_PUBLIC_INTERFACE" -j DROP

umask 077
STAGING_DIR="$(mktemp -d "${TMPDIR:-/tmp}/gam-restore.XXXXXX")"
ENCRYPTED_ARCHIVE="$STAGING_DIR/recovery.dump.age"
DECRYPTED_PACKAGE="$STAGING_DIR/recovery-artifact.tar.gz"
RESTORED_FILES="$STAGING_DIR/files"
: > "$ENCRYPTED_ARCHIVE"
RESTORATION_STARTED="$(date +%s)"

cleanup() {
    local cleanup_status=0
    if [[ -n "${RESTORED_DATABASE_CREATED:-}" && "$RESTORED_DATABASE_CREATED" == "true" ]]; then
        if [[ "${CONTROLLED_PRODUCTION_RECOVERY:-false}" != "true" ]]; then
            dropdb --if-exists --maintenance-db "$RESTORE_ADMIN_DATABASE_URL" "$RESTORE_DATABASE_NAME" >/dev/null 2>&1 || cleanup_status=1
        fi
    fi
    if [[ -d "$STAGING_DIR" ]]; then
        local restore_tool_dir
        restore_tool_dir="$(dirname "$(command -v dropdb || printf '%s' dropdb)")"
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
    return "$cleanup_status"
}
trap cleanup EXIT

OBJECT_METADATA_ERROR="$STAGING_DIR/head-object-error"
if ! OBJECT_METADATA="$(aws s3api head-object \
    --bucket "$RECOVERY_BUCKET" \
    --key "$RECOVERY_OBJECT_KEY" \
    --region "$AWS_REGION" 2>"$OBJECT_METADATA_ERROR")"; then
    cat "$OBJECT_METADATA_ERROR" >&2
    exit 1
fi
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
        if ! aws s3api restore-object \
            --bucket "$RECOVERY_BUCKET" \
            --key "$RECOVERY_OBJECT_KEY" \
            --region "$AWS_REGION" \
            --restore-request "{\"Days\":${GLACIER_RESTORE_DAYS},\"GlacierJobParameters\":{\"Tier\":\"Standard\"}}" \
            >"$STAGING_DIR/restore-object-output" \
            2>"$RESTORE_OBJECT_ERROR"; then
            if ! grep -qiE 'restorealreadyinprogress|restore already in progress' "$RESTORE_OBJECT_ERROR" \
                && ! grep -qiE 'objectalreadyinactivetiererror|object already in active tier' "$RESTORE_OBJECT_ERROR"; then
                cat "$RESTORE_OBJECT_ERROR" >&2
                exit 1
            elif grep -qiE 'objectalreadyinactivetiererror|object already in active tier' "$RESTORE_OBJECT_ERROR"; then
                RESTORE_ALREADY_ACTIVE=true
            fi
        fi

        GLACIER_RESTORE_DEADLINE="$(( $(date +%s) + GLACIER_RESTORE_TIMEOUT_SECONDS ))"
        while true; do
            RESTORE_HEAD_ERROR="$STAGING_DIR/restore-head-object-error"
            if OBJECT_METADATA="$(aws s3api head-object \
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

if [[ "${CONTROLLED_PRODUCTION_RECOVERY:-false}" != "true" ]]; then
    dropdb --if-exists --maintenance-db "$RESTORE_ADMIN_DATABASE_URL" "$RESTORE_DATABASE_NAME"
    createdb --maintenance-db "$RESTORE_ADMIN_DATABASE_URL" "$RESTORE_DATABASE_NAME"
else
    # A controlled recovery may target a pre-created isolated database.
    createdb --maintenance-db "$RESTORE_ADMIN_DATABASE_URL" "$RESTORE_DATABASE_NAME" 2>/dev/null || true
fi
RESTORED_DATABASE_CREATED=true

ARCHIVE_COPY_ERROR="$STAGING_DIR/archive-copy-error"
if ! aws s3 cp \
    "s3://${RECOVERY_BUCKET}/${RECOVERY_OBJECT_KEY}" \
    "$ENCRYPTED_ARCHIVE" \
    --region "$AWS_REGION" \
    --only-show-errors \
    2>"$ARCHIVE_COPY_ERROR"; then
    if grep -qi 'invalidobjectstate' "$ARCHIVE_COPY_ERROR"; then
        echo "S3 recovery object is still unavailable after Glacier restore polling" >&2
    fi
    cat "$ARCHIVE_COPY_ERROR" >&2
    exit 1
fi
test "$(sha256sum "$ENCRYPTED_ARCHIVE" | awk '{print $1}')" = "$RECOVERY_SHA256"

# Decryption occurs only in the temporary isolated staging directory.
age --decrypt --identity "$AGE_IDENTITY_FILE" --output "$DECRYPTED_PACKAGE" "$ENCRYPTED_ARCHIVE"
mkdir -p "$RESTORED_FILES"
tar --extract --gzip --file "$DECRYPTED_PACKAGE" --directory "$RESTORED_FILES"
test -s "$RESTORED_FILES/database.dump"
test -s "$RESTORED_FILES/database-roles.sql"
test -s "$RESTORED_FILES/manifest.json"
pg_restore --list "$RESTORED_FILES/database.dump" >/dev/null

echo "restoring the selected recovery point into the isolated database"
pg_restore \
    --exit-on-error \
    --no-owner \
    --dbname="$RESTORE_DATABASE_URL" \
    "$RESTORED_FILES/database.dump"

# Attachment-byte evidence uses PostgreSQL's cryptographic digest function in
# the isolated database only; the extension is never enabled on production.
psql "$RESTORE_DATABASE_URL" -v ON_ERROR_STOP=1 \
    -c 'CREATE EXTENSION IF NOT EXISTS pgcrypto;' >/dev/null

# Role metadata is password-free and is restored into the isolated cluster
# before application-level representative access is exercised.
psql "$RESTORE_ADMIN_DATABASE_URL" -v ON_ERROR_STOP=1 \
    -f "$RESTORED_FILES/database-roles.sql" >/dev/null

# The archive excludes refresh-token rows; truncation is explicit so an
# existing isolated database can never retain sessions from a prior attempt.
psql "$RESTORE_DATABASE_URL" -v ON_ERROR_STOP=1 \
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
bash -Eeuo pipefail -c "$REPRESENTATIVE_ACCESS_CHECK_COMMAND"

RESTORATION_DURATION_SECONDS="$(( $(date +%s) - RESTORATION_STARTED ))"
export RESTORATION_DURATION_SECONDS
export SELECTED_RECOVERY_POINT="$RECOVERY_OBJECT_KEY"
export RECOVERY_CHECKSUM="$RECOVERY_SHA256"
export RESTORATION_REASON="${RESTORATION_REASON:-annual}"
export RESTORATION_CORRECTIVE_ACTION
/usr/local/libexec/gam-verify-restoration

echo "isolated restoration verified; universal sign-in is required"
