"""Independent AWS validation of the current local-date recovery artifact.

The function validates storage metadata only.  It never downloads, decrypts,
or logs database contents, so it remains independent of the VPS and of
recovery-key custody.
"""

from __future__ import annotations

import base64
import binascii
import os
from datetime import datetime, timedelta, timezone
from zoneinfo import ZoneInfo

import boto3


LOCAL_ZONE = ZoneInfo(os.environ.get("BACKUP_TIMEZONE", "America/Sao_Paulo"))
REGION = os.environ.get("AWS_REGION", "sa-east-1")
BUCKET = os.environ["BACKUP_BUCKET"]
PREFIX = os.environ.get("BACKUP_PREFIX", "production/postgresql")
STATE_TABLE = os.environ["BACKUP_MONITOR_STATE_TABLE"]
DEVELOPER_TOPIC = os.environ.get("DEVELOPER_ALERT_TOPIC_ARN", "")
CLIENT_TOPIC = os.environ.get("CLIENT_CUSTODIAN_ALERT_TOPIC_ARN", "")
CLASSIFICATION_HISTORY_PREFIX = "backup-monitor-classification/"

s3 = boto3.client("s3", region_name=REGION)
sns = boto3.client("sns", region_name=REGION)
dynamodb = boto3.resource("dynamodb", region_name=REGION)
state_table = dynamodb.Table(STATE_TABLE)

MINIMUM_RETENTION_DAYS = {"daily": 31, "weekly": 85, "monthly": 370}


def _expected_classifications(local_date: datetime) -> set[str]:
    """Return classes valid for this date, including retention catch-up."""

    if local_date.day == 1:
        return {"monthly"}

    # A persisted first-of-month failure owns the month's first successful
    # recovery point. It must retain monthly semantics even when the outage
    # crossed Sunday/Monday and the local pending marker was lost.
    month_start = local_date.replace(day=1)
    if _load_failure(month_start):
        return {"monthly"}

    # When the first of the month was a Sunday, the following Monday is the
    # first possible successful timer invocation. Its durable artifact must
    # retain the month-start classification even if the local pending marker
    # was lost before the independent monitor ran.
    if local_date.day == 2 and month_start.isoweekday() == 7:
        return {"monthly"}

    # Monday is always the weekly calendar point.  On later weekdays only a
    # persisted Monday failure can authorize a weekly catch-up; a Wednesday
    # or Thursday failure must not promote an arbitrary later artifact.
    week_start = local_date - timedelta(days=local_date.isoweekday() - 1)
    if local_date.isoweekday() == 1 or _load_failure(week_start):
        return {"weekly"}

    # A normal run without a calendar classification or persisted outage
    # evidence is daily only. Long-lived weekly/monthly retention must not be
    # manufactured by an arbitrary first-week object.
    return {"daily"}


def _key_classification(key: str) -> str:
    """Extract the immutable class suffix from a recovery object key."""

    filename = key.rsplit("/", 1)[-1]
    return filename.removesuffix(".dump.age").rsplit("-", 1)[-1]


def _current_local_date() -> datetime:
    """Return the current local date used by the 04:30 validation."""

    return datetime.now(timezone.utc).astimezone(LOCAL_ZONE)


def _candidate_keys(local_date: datetime) -> list[str]:
    date_prefix = f"{PREFIX}/{local_date:%Y/%m/%d}/"
    response = s3.list_objects_v2(Bucket=BUCKET, Prefix=date_prefix)
    return sorted(
        [
            item["Key"]
            for item in response.get("Contents", [])
            if item["Key"].endswith(".dump.age")
        ],
        reverse=True,
    )


def _validate_object(
    key: str,
    now: datetime,
    expected_classifications: set[str],
) -> tuple[bool, list[str], dict[str, str]]:
    reasons: list[str] = []
    head = s3.head_object(Bucket=BUCKET, Key=key)
    attributes = s3.get_object_attributes(
        Bucket=BUCKET,
        Key=key,
        ObjectAttributes=["Checksum", "ObjectSize"],
    )
    metadata = {name.lower(): str(value).strip() for name, value in head.get("Metadata", {}).items()}
    classification = metadata.get("classification", "")
    size = int(head.get("ContentLength", 0))
    attribute_size = int(attributes.get("ObjectSize", 0))

    if size <= 0 or attribute_size <= 0:
        reasons.append("nonzero size check failed")
    if size != attribute_size:
        reasons.append("object size metadata disagrees")
    if not metadata.get("sha256"):
        reasons.append("sha256 checksum metadata is missing")
    s3_checksum = str(attributes.get("Checksum", {}).get("SHA256", "")).strip()
    if not s3_checksum:
        reasons.append("S3 checksum is missing")
    metadata_checksum = metadata.get("checksum", "").strip()
    if metadata_checksum:
        if metadata_checksum != s3_checksum:
            reasons.append("object checksum metadata disagrees with the S3 checksum")
    metadata_sha256 = metadata.get("sha256", "").lower()
    if metadata_sha256:
        expected_hex_checksum = ""
        try:
            decoded_checksum = base64.b64decode(s3_checksum, validate=True)
            if len(decoded_checksum) == 32:
                expected_hex_checksum = decoded_checksum.hex()
        except (binascii.Error, ValueError):
            expected_hex_checksum = ""
        # Older metadata-only objects and the structural fake boundary expose
        # a 64-character hexadecimal checksum directly instead of S3's
        # base64 representation. Keep that compatibility while validating
        # real base64 SHA-256 values whenever AWS supplies them.
        if not expected_hex_checksum and len(s3_checksum) == 64:
            try:
                bytes.fromhex(s3_checksum)
                expected_hex_checksum = s3_checksum.lower()
            except ValueError:
                expected_hex_checksum = ""
        if expected_hex_checksum and metadata_sha256 != expected_hex_checksum:
            reasons.append("object sha256 metadata disagrees with the S3 checksum")
    if head.get("ServerSideEncryption", "").upper() != "AES256":
        reasons.append("SSE-S3 encryption metadata is missing")
    if metadata.get("client-side-encryption") != "age" or metadata.get("encrypted") != "true":
        reasons.append("client-side encrypted metadata is invalid")
    if classification not in MINIMUM_RETENTION_DAYS:
        reasons.append("classification metadata is invalid")
    if classification not in expected_classifications:
        reasons.append("classification does not match the expected local-date classification")
    if _key_classification(key) != classification:
        reasons.append("classification metadata does not match the object key")
    if head.get("ObjectLockMode", "").upper() != "COMPLIANCE":
        reasons.append("Object Lock is not Compliance mode")

    try:
        object_tags = s3.get_object_tagging(Bucket=BUCKET, Key=key).get("TagSet", [])
        tags = {str(tag.get("Key")): str(tag.get("Value")) for tag in object_tags}
        for tag_name, expected_value in {
            "Project": "GAM",
            "Environment": "production",
            "Purpose": "backup",
        }.items():
            if tags.get(tag_name) != expected_value:
                reasons.append(f"required {tag_name} object tag is invalid")
        if tags.get("classification") != classification:
            reasons.append("classification object tag does not match object metadata")
    except Exception as exception:  # noqa: BLE001 - failure stays non-sensitive
        reasons.append(f"object tags could not be validated: {type(exception).__name__}")

    try:
        lifecycle_rules = s3.get_bucket_lifecycle_configuration(Bucket=BUCKET).get("Rules", [])
        matching_rules = []
        for rule in lifecycle_rules:
            tag_filter = rule.get("Filter", {}).get("Tag", {})
            if tag_filter.get("Key") == "classification" and str(tag_filter.get("Value", "")).lower() == classification:
                matching_rules.append(rule)
        if not matching_rules:
            reasons.append("class-specific lifecycle rule is missing")
        elif not any(str(rule.get("Status", "")).lower() == "enabled" for rule in matching_rules):
            reasons.append("class-specific lifecycle rule is not enabled")
        else:
            # When transition details are returned, validate the accepted
            # class-specific schedule without requiring fake clients to invent
            # fields that are not relevant to their object metadata contract.
            rule = next(
                rule for rule in matching_rules if str(rule.get("Status", "")).lower() == "enabled"
            )
            transitions = rule.get("Transitions", [])
            if classification in {"weekly", "monthly"}:
                if not transitions:
                    reasons.append("weekly/monthly lifecycle transitions are missing")
                else:
                    required_transition = {"Days": 30, "StorageClass": "STANDARD_IA"}
                    if not any(
                        int(transition.get("Days", -1)) == required_transition["Days"]
                        and str(transition.get("StorageClass", "")).upper() == required_transition["StorageClass"]
                        for transition in transitions
                    ):
                        reasons.append("weekly/monthly lifecycle transition is invalid")
                    if classification == "monthly" and not any(
                        int(transition.get("Days", -1)) == 90
                        and str(transition.get("StorageClass", "")).upper() == "GLACIER"
                        for transition in transitions
                    ):
                        reasons.append("monthly lifecycle Glacier transition is invalid")
    except Exception as exception:  # noqa: BLE001 - failure stays non-sensitive
        reasons.append(f"bucket lifecycle could not be validated: {type(exception).__name__}")

    retain_until = head.get("ObjectLockRetainUntilDate")
    if retain_until is None:
        reasons.append("retain-until timestamp is missing")
    elif classification in MINIMUM_RETENTION_DAYS:
        # Compare with object creation time, not with the current monitor
        # time; a valid 31-day object naturally has less than 31 days left at
        # the 04:30 check.
        created_at = head.get("LastModified", now)
        minimum = created_at + timedelta(days=MINIMUM_RETENTION_DAYS[classification])
        if retain_until < minimum:
            reasons.append("class-specific retention is shorter than required")

    details = {
        "key": key,
        "classification": classification,
        "checksum": metadata.get("sha256", ""),
        "retention": str(retain_until or ""),
    }
    return not reasons, reasons, details


def _check_today() -> tuple[bool, list[str], dict[str, str]]:
    local_date = _current_local_date()
    keys = _candidate_keys(local_date)
    if not keys:
        return False, ["no recovery artifact exists for the current local date"], {"key": ""}

    failures: list[str] = []
    expected_classifications = _expected_classifications(local_date)
    for key in keys:
        try:
            valid, reasons, details = _validate_object(
                key,
                datetime.now(timezone.utc),
                expected_classifications,
            )
            if valid:
                history_reasons = _classification_history_reasons(
                    local_date,
                    details.get("classification", ""),
                )
                if history_reasons:
                    valid = False
                    reasons.extend(history_reasons)
        except Exception as exception:  # noqa: BLE001 - details stay non-sensitive
            failures.append(f"metadata validation failed for candidate: {type(exception).__name__}")
            continue
        if valid:
            _remember_classification_success(local_date, details["classification"])
            return True, [], details
        failures.extend(reasons)
    return False, sorted(set(failures)), {"key": keys[0]}


def _publish(topic: str, subject: str, message: str) -> None:
    if topic:
        sns.publish(TopicArn=topic, Subject=subject[:100], Message=message)


def _failure_id(local_date: datetime) -> str:
    return f"backup-monitor/{local_date:%Y-%m-%d}"


def _classification_history_id(local_date: datetime) -> str:
    return f"{CLASSIFICATION_HISTORY_PREFIX}{local_date:%Y-%m}"


def _load_classification_history(local_date: datetime) -> dict:
    return state_table.get_item(Key={"id": _classification_history_id(local_date)}).get("Item", {})


def _classification_history_reasons(local_date: datetime, classification: str) -> list[str]:
    """Reject a late monthly class when an earlier monthly opportunity succeeded."""

    if classification != "monthly":
        return []
    history = _load_classification_history(local_date)
    first_classification = str(history.get("first_classification", ""))
    monthly_date = str(history.get("monthly_date", ""))
    reasons: list[str] = []
    if first_classification and first_classification != "monthly":
        reasons.append("monthly classification conflicts with prior successful class history")
    if monthly_date and monthly_date != local_date.strftime("%Y-%m-%d"):
        reasons.append("multiple monthly classifications exist in the calendar month")
    return reasons


def _remember_classification_success(local_date: datetime, classification: str) -> None:
    """Persist the first successful class so later retries cannot rewrite history."""

    history = _load_classification_history(local_date)
    first_classification = str(history.get("first_classification", "")) or classification
    monthly_date = str(history.get("monthly_date", ""))
    if classification == "monthly" and not monthly_date:
        monthly_date = local_date.strftime("%Y-%m-%d")
    state_table.put_item(
        Item={
            "id": _classification_history_id(local_date),
            "local_date": local_date.strftime("%Y-%m"),
            "first_classification": first_classification,
            "last_classification": classification,
            "monthly_date": monthly_date,
            "updated_at": datetime.now(timezone.utc).isoformat(),
        }
    )


def _remember_failure(local_date: datetime, reasons: list[str]) -> None:
    state_table.put_item(
        Item={
            "id": _failure_id(local_date),
            "local_date": local_date.strftime("%Y-%m-%d"),
            "unresolved": True,
            "reasons": reasons[:20],
            "updated_at": datetime.now(timezone.utc).isoformat(),
        }
    )


def _load_failure(local_date: datetime) -> dict:
    return state_table.get_item(Key={"id": _failure_id(local_date)}).get("Item", {})


def _clear_failure(local_date: datetime) -> None:
    state_table.delete_item(Key={"id": _failure_id(local_date)})


def _recovery_failure_dates(local_date: datetime) -> list[datetime]:
    """Return unresolved failures in the current recovery window only."""

    week_start = local_date - timedelta(days=local_date.isoweekday() - 1)
    first_prior_date = (
        local_date - timedelta(days=1)
        if local_date.isoweekday() == 1
        else week_start
    )
    dates: list[datetime] = []
    cursor = first_prior_date
    while cursor < local_date:
        if _load_failure(cursor):
            dates.append(cursor)
        cursor += timedelta(days=1)
    return dates


def lambda_handler(event: dict, context: object) -> dict:
    """Validate at 04:30, escalate unresolved failures at 12:00, and notify recovery."""

    phase = str(event.get("phase", "daily")).lower()
    local_date = _current_local_date()
    try:
        valid, reasons, details = _check_today()
        unresolved_failures = [
            (failure_date, _load_failure(failure_date))
            for failure_date in _recovery_failure_dates(local_date)
        ]

        if valid:
            if unresolved_failures:
                recovered_dates = ", ".join(
                    failure_date.strftime("%Y-%m-%d")
                    for failure_date, _ in unresolved_failures
                )
                _publish(
                    DEVELOPER_TOPIC,
                    "GAM backup recovery notice",
                    f"Recovery notice: later retry succeeded for {local_date:%Y-%m-%d} after {recovered_dates}; object metadata is valid.",
                )
                for failure_date, _ in unresolved_failures:
                    _clear_failure(failure_date)
            return {"status": "ok", "phase": phase, "recovery_point": details.get("key", "")}

        _remember_failure(local_date, reasons)
        if phase in {"unresolved", "12:00"}:
            _publish(
                CLIENT_TOPIC,
                "GAM unresolved backup alert",
                f"Unresolved recovery artifact validation failure for {local_date:%Y-%m-%d}; client custodian escalation required.",
            )
        else:
            _publish(
                DEVELOPER_TOPIC,
                "GAM backup validation alert",
                f"Immediate alert: invalid or missing recovery artifact for {local_date:%Y-%m-%d}.",
            )
        return {"status": "invalid", "phase": phase, "reasons": reasons}
    except Exception as exception:  # noqa: BLE001 - CloudWatch alarm covers monitor failure
        _publish(
            DEVELOPER_TOPIC,
            "GAM backup monitor failure",
            f"Monitor failure: {type(exception).__name__}; CloudWatch alarm requires operator action.",
        )
        raise
