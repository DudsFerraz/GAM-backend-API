#!/usr/bin/env bash
set -Eeuo pipefail

# The job produces one complete, client-side encrypted PostgreSQL artifact.
# It never treats a local dump as a recovery point: success is reported only
# after S3 metadata, checksum, tags, and Compliance retention are verified.

: "${AWS_REGION:?AWS_REGION is required}"
: "${GAM_BACKUP_BUCKET:?GAM_BACKUP_BUCKET is required}"
: "${GAM_BACKUP_STATE_DIR:?GAM_BACKUP_STATE_DIR is required}"
: "${GAM_DEVELOPER_AGE_RECIPIENT:?developer public recipient is required}"
: "${GAM_CLIENT_AGE_RECIPIENT:?client public recipient is required}"
if [[ "$GAM_DEVELOPER_AGE_RECIPIENT" == "$GAM_CLIENT_AGE_RECIPIENT" ]]; then
    echo "developer and client age recipients must be distinct" >&2
    exit 1
fi
: "${GAM_SOURCE_COMMIT:?GAM_SOURCE_COMMIT is required for recovery provenance}"
: "${GAM_BACKEND_IMAGE_DIGEST:?GAM_BACKEND_IMAGE_DIGEST is required for recovery provenance}"
: "${GAM_FRONTEND_RELEASE:?GAM_FRONTEND_RELEASE is required for recovery provenance}"
: "${GAM_FRONTEND_ARCHIVE:?GAM_FRONTEND_ARCHIVE is required for recovery provenance}"
: "${GAM_FRONTEND_SHA256:?GAM_FRONTEND_SHA256 is required for recovery provenance}"
: "${GAM_MIGRATION_STATE:?GAM_MIGRATION_STATE is required for recovery provenance}"
: "${PGDATABASE:?PGDATABASE is required}"

readonly LOCAL_ZONE="America/Sao_Paulo"
readonly DEFAULT_BACKUP_PREFIX="production/postgresql"
GAM_BACKUP_PREFIX="${GAM_BACKUP_PREFIX:-$DEFAULT_BACKUP_PREFIX}"
readonly STATE_DIR="$GAM_BACKUP_STATE_DIR"
readonly RUNTIME_DIR="${GAM_BACKUP_RUNTIME_DIR:-$STATE_DIR/runtime}"
readonly DAILY_RETENTION_DAYS=31
readonly WEEKLY_RETENTION_DAYS=85
readonly MONTHLY_RETENTION_DAYS=370

mkdir -p "$STATE_DIR" "$RUNTIME_DIR"
umask 077

LOCK_FILE="$STATE_DIR/backup.lock"
exec 9>"$LOCK_FILE"
if ! flock -n 9; then
    echo "another backup is still running; no second recovery point is created" >&2
    exit 0
fi

STAGING_DIR=""
cleanup() {
    if [[ -n "${STAGING_DIR:-}" && -d "$STAGING_DIR" ]]; then
        # Plaintext dumps, manifests, and encrypted staging bytes are all
        # temporary and are destroyed after both success and failure.
        find "$STAGING_DIR" -type f -exec shred --remove --zero --force {} + 2>/dev/null || true
        rm -rf -- "$STAGING_DIR"
    fi
}
trap cleanup EXIT

if [[ -n "${PGPASSWORD_FILE:-}" ]]; then
    export PGPASSWORD="$(<"$PGPASSWORD_FILE")"
fi

STAGING_DIR="$(mktemp -d "$RUNTIME_DIR/artifact.XXXXXX")"
readonly LOCAL_DATE="$(TZ="$LOCAL_ZONE" date '+%Y-%m-%d')"
readonly LOCAL_MONTH="${LOCAL_DATE:0:7}"
readonly LOCAL_DAY="${LOCAL_DATE:8:2}"
readonly LOCAL_WEEKDAY="$(TZ="$LOCAL_ZONE" date '+%u')"
readonly LOCAL_WEEK="$(TZ="$LOCAL_ZONE" date '+%G-W%V')"
readonly UTC_TIMESTAMP="$(date -u '+%Y%m%dT%H%M%SZ')"

WEEKLY_PENDING="$STATE_DIR/weekly.pending"
MONTHLY_PENDING="$STATE_DIR/monthly.pending"
WEEKLY_MARKER="$STATE_DIR/weekly-${LOCAL_WEEK}.done"
MONTHLY_MARKER="$STATE_DIR/monthly-${LOCAL_MONTH}.done"
STATE_FILE="$STATE_DIR/backup.state"
LAST_ATTEMPT=""
LAST_SUCCESS=""
if [[ -f "$STATE_FILE" ]]; then
    LAST_ATTEMPT="$(awk -F= '$1 == "last_attempt" { print $2 }' "$STATE_FILE")"
    LAST_SUCCESS="$(awk -F= '$1 == "last_success" { print $2 }' "$STATE_FILE")"
fi

# LAST_ATTEMPT and LAST_SUCCESS make pending markers survive persistent-timer
# outages, including missed weekdays and calendar-month boundaries.
write_state() {
    local attempt="$1"
    local success="$2"
    local temporary_state="${STATE_FILE}.tmp"
    printf 'last_attempt=%s\nlast_success=%s\n' "$attempt" "$success" > "$temporary_state"
    mv -f -- "$temporary_state" "$STATE_FILE"
}

PREVIOUS_RUN="${LAST_ATTEMPT:-$LAST_SUCCESS}"
if [[ -n "$PREVIOUS_RUN" && "$PREVIOUS_RUN" < "$LOCAL_DATE" ]]; then
    INHERITED_DATE="$(TZ="$LOCAL_ZONE" date -d "$PREVIOUS_RUN + 1 day" '+%Y-%m-%d')"
    while [[ "$INHERITED_DATE" < "$LOCAL_DATE" ]]; do
        INHERITED_WEEKDAY="$(TZ="$LOCAL_ZONE" date -d "$INHERITED_DATE" '+%u')"
        INHERITED_DAY="$(TZ="$LOCAL_ZONE" date -d "$INHERITED_DATE" '+%d')"
        if [[ "$INHERITED_WEEKDAY" == "1" ]]; then
            : > "$WEEKLY_PENDING"
        fi
        if [[ "$INHERITED_DAY" == "01" ]]; then
            : > "$MONTHLY_PENDING"
        fi
        INHERITED_DATE="$(TZ="$LOCAL_ZONE" date -d "$INHERITED_DATE + 1 day" '+%Y-%m-%d')"
    done
fi
# Pending markers make a missed Monday or first-of-month attempt inherit its
# classification on the next successful daily run.
if [[ "$LOCAL_WEEKDAY" == "1" && ! -e "$WEEKLY_MARKER" ]]; then
    : > "$WEEKLY_PENDING"
fi
if [[ "$LOCAL_DAY" == "01" && ! -e "$MONTHLY_MARKER" ]]; then
    : > "$MONTHLY_PENDING"
fi

# One object is created when daily, weekly, and monthly classifications
# overlap; the longest applicable retention wins.
CLASSIFICATION=daily
RETENTION_DAYS=$DAILY_RETENTION_DAYS
if [[ -e "$MONTHLY_PENDING" ]]; then
    CLASSIFICATION=monthly
    RETENTION_DAYS=$MONTHLY_RETENTION_DAYS
elif [[ -e "$WEEKLY_PENDING" ]]; then
    CLASSIFICATION=weekly
    RETENTION_DAYS=$WEEKLY_RETENTION_DAYS
fi

# Persist the current attempt only after pending markers and the selected
# class are durable.  If the process dies between these operations, the next
# persistent-timer run still sees the prior state and the already-created
# markers preserve catch-up classification semantics.
LAST_ATTEMPT="$LOCAL_DATE"
write_state "$LAST_ATTEMPT" "$LAST_SUCCESS"

readonly OBJECT_KEY="$GAM_BACKUP_PREFIX/${LOCAL_DATE//-//}/${UTC_TIMESTAMP}-${CLASSIFICATION}.dump.age"
readonly DUMP_FILE="$STAGING_DIR/database.dump"
readonly ROLES_FILE="$STAGING_DIR/database-roles.sql"
readonly MANIFEST_FILE="$STAGING_DIR/manifest.json"
readonly PACKAGE_FILE="$STAGING_DIR/recovery-artifact.tar.gz"
readonly ENCRYPTED_FILE="$STAGING_DIR/${UTC_TIMESTAMP}-${CLASSIFICATION}.dump.age"

EXPECTED_POSTGRESQL_MAJOR="${GAM_POSTGRESQL_MAJOR_VERSION:-18}"
PG_DUMP_VERSION="$(pg_dump --version)"
PG_CLIENT_MAJOR="${PG_DUMP_VERSION##* }"
PG_CLIENT_MAJOR="${PG_CLIENT_MAJOR%%.*}"
PG_VERSION="$(psql "$PGDATABASE" -At -c 'SHOW server_version;')"
PG_SERVER_MAJOR="${PG_VERSION%%.*}"
if [[ "$PG_CLIENT_MAJOR" != "$EXPECTED_POSTGRESQL_MAJOR" \
    || "$PG_SERVER_MAJOR" != "$EXPECTED_POSTGRESQL_MAJOR" ]]; then
    echo "incompatible PostgreSQL client/server major version: client=$PG_CLIENT_MAJOR server=$PG_SERVER_MAJOR expected=$EXPECTED_POSTGRESQL_MAJOR" >&2
    exit 1
fi
SOURCE_COMMIT="$GAM_SOURCE_COMMIT"
BACKEND_IMAGE_DIGEST="$GAM_BACKEND_IMAGE_DIGEST"
FRONTEND_RELEASE="$GAM_FRONTEND_RELEASE"
FRONTEND_ARCHIVE="$GAM_FRONTEND_ARCHIVE"
FRONTEND_SHA256="$GAM_FRONTEND_SHA256"
MIGRATION_STATE="$GAM_MIGRATION_STATE"

# A retry must reconcile the local-date namespace before attempting an upload.
# Object Lock prevents replacement, so a valid existing object is the success
# result and a conflicting classification is a hard failure rather than a
# second recovery point for the same local date.
EXISTING_OBJECTS="$(aws s3api list-objects-v2 \
    --bucket "$GAM_BACKUP_BUCKET" \
    --prefix "$GAM_BACKUP_PREFIX/${LOCAL_DATE//-//}/" \
    --region "$AWS_REGION")"
EXISTING_KEYS="$(jq -r '.Contents[]?.Key | select(endswith(".dump.age"))' <<<"$EXISTING_OBJECTS")"
EXISTING_CLASSIFIED_KEY="$(jq -r --arg classification "$CLASSIFICATION" \
    '[.Contents[]?.Key | select(endswith("-" + $classification + ".dump.age"))][0] // empty' \
    <<<"$EXISTING_OBJECTS")"

if [[ -n "$EXISTING_KEYS" && -z "$EXISTING_CLASSIFIED_KEY" ]]; then
    echo "a conflicting recovery classification already exists for $LOCAL_DATE" >&2
    exit 1
fi

if [[ -n "$EXISTING_CLASSIFIED_KEY" ]]; then
    EXISTING_OBJECT_HEAD="$(aws s3api head-object \
        --bucket "$GAM_BACKUP_BUCKET" \
        --key "$EXISTING_CLASSIFIED_KEY" \
        --region "$AWS_REGION")"
    EXISTING_TAGS="$(aws s3api get-object-tagging \
        --bucket "$GAM_BACKUP_BUCKET" \
        --key "$EXISTING_CLASSIFIED_KEY" \
        --region "$AWS_REGION")"
    EXISTING_ATTRIBUTES="$(aws s3api get-object-attributes \
        --bucket "$GAM_BACKUP_BUCKET" \
        --key "$EXISTING_CLASSIFIED_KEY" \
        --object-attributes ObjectSize Checksum \
        --region "$AWS_REGION")"
    EXISTING_RETENTION="$(aws s3api get-object-retention \
        --bucket "$GAM_BACKUP_BUCKET" \
        --key "$EXISTING_CLASSIFIED_KEY" \
        --region "$AWS_REGION")"
    EXISTING_SIZE="$(jq -r '.ContentLength // 0' <<<"$EXISTING_OBJECT_HEAD")"
    EXISTING_MODE="$(jq -r '.ObjectLockMode // empty' <<<"$EXISTING_OBJECT_HEAD")"
    EXISTING_SERVER_SIDE_ENCRYPTION="$(jq -r '.ServerSideEncryption // empty' <<<"$EXISTING_OBJECT_HEAD")"
    EXISTING_METADATA_SHA256="$(jq -r '.Metadata.sha256 // empty' <<<"$EXISTING_OBJECT_HEAD")"
    EXISTING_METADATA_ENCRYPTED="$(jq -r '.Metadata.encrypted // empty' <<<"$EXISTING_OBJECT_HEAD")"
    EXISTING_METADATA_CLIENT_SIDE_ENCRYPTION="$(jq -r '.Metadata.client-side-encryption // empty' <<<"$EXISTING_OBJECT_HEAD")"
    EXISTING_METADATA_CHECKSUM="$(jq -r '.Metadata.checksum // empty' <<<"$EXISTING_OBJECT_HEAD")"
    EXISTING_CLASSIFICATION="$(jq -r '.Metadata.classification // empty' <<<"$EXISTING_OBJECT_HEAD")"
    EXISTING_ATTRIBUTE_SIZE="$(jq -r '.ObjectSize // 0' <<<"$EXISTING_ATTRIBUTES")"
    EXISTING_ATTRIBUTE_CHECKSUM="$(jq -r '.Checksum.SHA256 // empty' <<<"$EXISTING_ATTRIBUTES")"
    EXISTING_OBJECT_TIMESTAMP="$(jq -r '.LastModified // empty' <<<"$EXISTING_OBJECT_HEAD")"
    EXISTING_OBJECT_LOCK_RETAIN_UNTIL="$(jq -r '.ObjectLockRetainUntilDate // empty' <<<"$EXISTING_OBJECT_HEAD")"
    EXISTING_RETENTION_MODE="$(jq -r '.Retention.Mode // empty' <<<"$EXISTING_RETENTION")"
    EXISTING_RETENTION_UNTIL="$(jq -r '.Retention.RetainUntilDate // empty' <<<"$EXISTING_RETENTION")"
    EXISTING_TAG_CLASSIFICATION="$(jq -r --arg key classification \
        '.TagSet[]? | select(.Key == $key) | .Value' <<<"$EXISTING_TAGS")"
    EXISTING_TAG_PROJECT="$(jq -r --arg key Project '.TagSet[]? | select(.Key == $key) | .Value' <<<"$EXISTING_TAGS")"
    EXISTING_TAG_ENVIRONMENT="$(jq -r --arg key Environment '.TagSet[]? | select(.Key == $key) | .Value' <<<"$EXISTING_TAGS")"
    EXISTING_TAG_PURPOSE="$(jq -r --arg key Purpose '.TagSet[]? | select(.Key == $key) | .Value' <<<"$EXISTING_TAGS")"
    EXISTING_OBJECT_EPOCH="$(date -u --date "$EXISTING_OBJECT_TIMESTAMP" +%s)"
    EXISTING_HEAD_RETAIN_UNTIL_EPOCH="$(date -u --date "$EXISTING_OBJECT_LOCK_RETAIN_UNTIL" +%s)"
    EXISTING_RETENTION_UNTIL_EPOCH="$(date -u --date "$EXISTING_RETENTION_UNTIL" +%s)"
    EXISTING_MINIMUM_RETAIN_UNTIL_EPOCH="$((EXISTING_OBJECT_EPOCH + RETENTION_DAYS * 86400))"
    if [[ "$EXISTING_SIZE" -gt 0 \
        && "$EXISTING_MODE" == "COMPLIANCE" \
        && "$EXISTING_SERVER_SIDE_ENCRYPTION" == "AES256" \
        && "$EXISTING_METADATA_SHA256" =~ ^[0-9a-fA-F]{64}$ \
        && "$EXISTING_METADATA_ENCRYPTED" == "true" \
        && "$EXISTING_METADATA_CLIENT_SIDE_ENCRYPTION" == "age" \
        && "$EXISTING_CLASSIFICATION" == "$CLASSIFICATION" \
        && "$EXISTING_ATTRIBUTE_SIZE" == "$EXISTING_SIZE" \
        && -n "$EXISTING_ATTRIBUTE_CHECKSUM" \
        && -n "$EXISTING_METADATA_CHECKSUM" \
        && "$EXISTING_METADATA_CHECKSUM" == "$EXISTING_ATTRIBUTE_CHECKSUM" \
        && -n "$EXISTING_OBJECT_LOCK_RETAIN_UNTIL" \
        && "$EXISTING_RETENTION_MODE" == "COMPLIANCE" \
        && -n "$EXISTING_RETENTION_UNTIL" \
        && "$EXISTING_HEAD_RETAIN_UNTIL_EPOCH" == "$EXISTING_RETENTION_UNTIL_EPOCH" \
        && "$EXISTING_RETENTION_UNTIL_EPOCH" -ge "$EXISTING_MINIMUM_RETAIN_UNTIL_EPOCH" \
        && "$EXISTING_TAG_PROJECT" == "GAM" \
        && "$EXISTING_TAG_ENVIRONMENT" == "production" \
        && "$EXISTING_TAG_PURPOSE" == "backup" \
        && "$EXISTING_TAG_CLASSIFICATION" == "$CLASSIFICATION" ]]; then
        if [[ "$CLASSIFICATION" == monthly ]]; then
            : > "$MONTHLY_MARKER"
            : > "$WEEKLY_MARKER"
            rm -f -- "$MONTHLY_PENDING" "$WEEKLY_PENDING"
        elif [[ "$CLASSIFICATION" == weekly ]]; then
            : > "$WEEKLY_MARKER"
            rm -f -- "$WEEKLY_PENDING"
        fi
        write_state "$LAST_ATTEMPT" "$LOCAL_DATE"
        echo "validated existing immutable recovery point $EXISTING_CLASSIFIED_KEY ($CLASSIFICATION)"
        exit 0
    fi
    echo "existing recovery object $EXISTING_CLASSIFIED_KEY failed immutable reconciliation" >&2
    exit 1
fi

if aws s3api head-object --bucket "$GAM_BACKUP_BUCKET" --key "$OBJECT_KEY" >/dev/null 2>&1; then
    echo "refusing to overwrite existing immutable recovery object $OBJECT_KEY" >&2
    exit 1
fi

echo "creating transactionally consistent PostgreSQL custom-format archive"
pg_dump \
    --format=custom \
    --file="$DUMP_FILE" \
    --exclude-table-data=public.refresh_tokens \
    "$PGDATABASE"

echo "exporting database roles without role passwords"
pg_dumpall --roles-only --no-role-passwords > "$ROLES_FILE"

# Validate the archive before encryption and before it leaves the VPS.
pg_restore --list "$DUMP_FILE" > "$STAGING_DIR/archive.list"
ARCHIVE_SHA256="$(sha256sum "$DUMP_FILE" | awk '{print $1}')"
ROLES_SHA256="$(sha256sum "$ROLES_FILE" | awk '{print $1}')"
DUMP_SIZE_BYTES="$(stat -c '%s' "$DUMP_FILE")"
ROLES_SIZE_BYTES="$(stat -c '%s' "$ROLES_FILE")"
ARCHIVE_SIZE_BYTES="$((DUMP_SIZE_BYTES + ROLES_SIZE_BYTES))"

jq -n \
    --arg created_at "$(date -u '+%Y-%m-%dT%H:%M:%SZ')" \
    --arg postgresql_version "$PG_VERSION" \
    --arg classification "$CLASSIFICATION" \
    --arg object_key "$OBJECT_KEY" \
    --arg source_commit "$SOURCE_COMMIT" \
    --arg backend_image_digest "$BACKEND_IMAGE_DIGEST" \
    --arg frontend_release "$FRONTEND_RELEASE" \
    --arg frontend_archive "$FRONTEND_ARCHIVE" \
    --arg frontend_sha256 "$FRONTEND_SHA256" \
    --arg migration_state "$MIGRATION_STATE" \
    --argjson dump_size_bytes "$DUMP_SIZE_BYTES" \
    --argjson roles_size_bytes "$ROLES_SIZE_BYTES" \
    --argjson archive_size_bytes "$ARCHIVE_SIZE_BYTES" \
    --arg archive_sha256 "$ARCHIVE_SHA256" \
    --arg roles_sha256 "$ROLES_SHA256" \
    --arg refresh_token_data "excluded" \
    --arg encryption_scheme "age with two independent public recipients" \
    '{
      schema_version: "gam-recovery-manifest/v1",
      created_at: $created_at,
      postgresql_version: $postgresql_version,
      classification: $classification,
      object_key: $object_key,
      source_commit: $source_commit,
      backend_image_digest: $backend_image_digest,
      frontend_release: $frontend_release,
      frontend_archive: $frontend_archive,
      frontend_sha256: $frontend_sha256,
      migration_state: $migration_state,
      dump_size_bytes: $dump_size_bytes,
      roles_size_bytes: $roles_size_bytes,
      archive_size_bytes: $archive_size_bytes,
      archive_sha256: $archive_sha256,
      roles_sha256: $roles_sha256,
      refresh_token_data: $refresh_token_data,
      encryption_scheme: $encryption_scheme,
      data_boundary: "complete durable PostgreSQL state with attachment bytes"
    }' > "$MANIFEST_FILE"

tar --create --gzip --file "$PACKAGE_FILE" \
    --directory "$STAGING_DIR" \
    database.dump database-roles.sql manifest.json
tar --list --file "$PACKAGE_FILE" >/dev/null

echo "encrypting one artifact for developer and client public recipients"
age \
    --encrypt \
    --recipient "$GAM_DEVELOPER_AGE_RECIPIENT" \
    --recipient "$GAM_CLIENT_AGE_RECIPIENT" \
    --output "$ENCRYPTED_FILE" \
    "$PACKAGE_FILE"

ENCRYPTED_SHA256="$(sha256sum "$ENCRYPTED_FILE" | awk '{print $1}')"
CHECKSUM_SHA256="$(openssl dgst -sha256 -binary "$ENCRYPTED_FILE" | base64 --wrap=0)"
# Add one day before upload so the remote retain-until timestamp remains at
# least the full class retention after upload latency is accounted for.
RETENTION_UPLOAD_BUFFER_DAYS=1
RETAIN_UNTIL="$(date -u --date "+$((RETENTION_DAYS + RETENTION_UPLOAD_BUFFER_DAYS)) days" '+%Y-%m-%dT%H:%M:%SZ')"

aws s3api put-object \
    --bucket "$GAM_BACKUP_BUCKET" \
    --key "$OBJECT_KEY" \
    --body "$ENCRYPTED_FILE" \
    --region "$AWS_REGION" \
    --content-type application/octet-stream \
    --server-side-encryption AES256 \
    --checksum-algorithm SHA256 \
    --checksum-sha256 "$CHECKSUM_SHA256" \
    --tagging "Project=GAM&Environment=production&Purpose=backup&classification=${CLASSIFICATION}" \
    --metadata "sha256=${ENCRYPTED_SHA256},checksum=${CHECKSUM_SHA256},client-side-encryption=age,classification=${CLASSIFICATION},encrypted=true" \
    --object-lock-mode COMPLIANCE \
    --object-lock-retain-until-date "$RETAIN_UNTIL" \
    >/dev/null

HEAD_OBJECT="$(aws s3api head-object --bucket "$GAM_BACKUP_BUCKET" --key "$OBJECT_KEY" --region "$AWS_REGION")"
test "$(jq -r '.ContentLength // 0' <<<"$HEAD_OBJECT")" -gt 0
test "$(jq -r '.ServerSideEncryption // empty' <<<"$HEAD_OBJECT")" = AES256
test "$(jq -r '.Metadata.sha256 // empty' <<<"$HEAD_OBJECT")" = "$ENCRYPTED_SHA256"
test "$(jq -r '.Metadata.classification // empty' <<<"$HEAD_OBJECT")" = "$CLASSIFICATION"
test "$(jq -r '.Metadata.encrypted // empty' <<<"$HEAD_OBJECT")" = true
# The remote Metadata.client-side-encryption field must remain age after upload.
test "$(jq -r '.Metadata[\"client-side-encryption\"] // empty' <<<"$HEAD_OBJECT")" = age
test "$(jq -r '.ObjectLockMode // empty' <<<"$HEAD_OBJECT")" = COMPLIANCE
UPLOADED_OBJECT_TIMESTAMP="$(jq -r '.LastModified // empty' <<<"$HEAD_OBJECT")"
test -n "$UPLOADED_OBJECT_TIMESTAMP"
UPLOADED_OBJECT_EPOCH="$(date -u --date "$UPLOADED_OBJECT_TIMESTAMP" +%s)"
test "$UPLOADED_OBJECT_EPOCH" -gt 0

OBJECT_TAGS="$(aws s3api get-object-tagging \
    --bucket "$GAM_BACKUP_BUCKET" \
    --key "$OBJECT_KEY" \
    --region "$AWS_REGION")"
test "$(jq -r --arg key Project '.TagSet[]? | select(.Key == $key) | .Value' <<<"$OBJECT_TAGS")" = GAM
test "$(jq -r --arg key Environment '.TagSet[]? | select(.Key == $key) | .Value' <<<"$OBJECT_TAGS")" = production
test "$(jq -r --arg key Purpose '.TagSet[]? | select(.Key == $key) | .Value' <<<"$OBJECT_TAGS")" = backup
test "$(jq -r --arg key classification '.TagSet[]? | select(.Key == $key) | .Value' <<<"$OBJECT_TAGS")" = "$CLASSIFICATION"

OBJECT_ATTRIBUTES="$(aws s3api get-object-attributes \
    --bucket "$GAM_BACKUP_BUCKET" \
    --key "$OBJECT_KEY" \
    --object-attributes ObjectSize Checksum \
    --region "$AWS_REGION")"
test "$(jq -r '.ObjectSize // 0' <<<"$OBJECT_ATTRIBUTES")" -gt 0
test "$(jq -r '.Checksum.SHA256 // empty' <<<"$OBJECT_ATTRIBUTES")" = "$CHECKSUM_SHA256"

OBJECT_RETENTION="$(aws s3api get-object-retention --bucket "$GAM_BACKUP_BUCKET" --key "$OBJECT_KEY" --region "$AWS_REGION")"
test "$(jq -r '.Retention.Mode // empty' <<<"$OBJECT_RETENTION")" = COMPLIANCE
REMOTE_RETAIN_UNTIL="$(jq -r '.Retention.RetainUntilDate // empty' <<<"$OBJECT_RETENTION")"
test -n "$REMOTE_RETAIN_UNTIL"
REMOTE_RETAIN_UNTIL_EPOCH="$(date -u --date "$REMOTE_RETAIN_UNTIL" +%s)"
MINIMUM_RETAIN_UNTIL_EPOCH="$((UPLOADED_OBJECT_EPOCH + RETENTION_DAYS * 86400))"
if [[ "$REMOTE_RETAIN_UNTIL_EPOCH" -lt "$MINIMUM_RETAIN_UNTIL_EPOCH" ]]; then
    echo "remote retain-until timestamp is shorter than ${RETENTION_DAYS}-day ${CLASSIFICATION} retention from uploaded object timestamp" >&2
    exit 1
fi

if [[ "$CLASSIFICATION" == monthly ]]; then
    : > "$MONTHLY_MARKER"
    # A monthly object is also the weekly object when both pending classes
    # overlap. Resolve both state markers so a later run cannot create a
    # duplicate weekly recovery point for the same successful artifact.
    : > "$WEEKLY_MARKER"
    rm -f -- "$MONTHLY_PENDING" "$WEEKLY_PENDING"
elif [[ "$CLASSIFICATION" == weekly ]]; then
    : > "$WEEKLY_MARKER"
    rm -f -- "$WEEKLY_PENDING"
fi

write_state "$LAST_ATTEMPT" "$LOCAL_DATE"
echo "validated encrypted recovery point $OBJECT_KEY ($CLASSIFICATION, ${RETENTION_DAYS} days)"
