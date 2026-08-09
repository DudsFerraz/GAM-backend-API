"""Requirement-derived tests for the production backup and monitoring contracts.

These tests intentionally use the machine-readable Ansible documents, rendered
JSON policy, shell contract boundaries, and a fake AWS boundary.  They do not
provide or inspect human identities, notification subscriptions, or recovery
private keys.
"""

from __future__ import annotations

import base64
import importlib.util
import json
import os
import re
import subprocess
import sys
import tempfile
import types
import unittest
from datetime import datetime, timedelta, timezone
from pathlib import Path
from unittest.mock import patch

import yaml


ROOT = Path(__file__).resolve().parents[2]
ANSIBLE = ROOT / "operations" / "ansible"
SAO_PAULO = timezone(timedelta(hours=-3))


def read(relative_path: str) -> str:
    return (ROOT / relative_path).read_text(encoding="utf-8")


def load_yaml(relative_path: str):
    return yaml.safe_load(read(relative_path))


def task_nodes(document):
    """Yield Ansible task mappings without flattening their semantics."""

    if isinstance(document, list):
        for value in document:
            yield from task_nodes(value)
        return
    if not isinstance(document, dict):
        return

    if "name" in document and any(
        key.startswith("ansible.builtin.")
        or key in {"command", "shell", "copy", "template", "uri", "user", "group"}
        for key in document
    ):
        yield document

    for key in ("tasks", "pre_tasks", "handlers", "block", "rescue", "always"):
        if key in document:
            yield from task_nodes(document[key])


def module_payload(task: dict, *module_names: str):
    for name in module_names:
        if name in task:
            return task[name]
    return None


def command_argv(task: dict) -> list[str]:
    payload = module_payload(task, "ansible.builtin.command", "command", "ansible.builtin.shell", "shell")
    if isinstance(payload, dict) and isinstance(payload.get("argv"), list):
        return [str(value) for value in payload["argv"]]
    if isinstance(payload, str):
        return payload.split()
    return []


def argv_value(argv: list[str], option: str) -> str:
    try:
        return argv[argv.index(option) + 1]
    except (ValueError, IndexError):
        return ""


def iam_simulation_actions(task: dict) -> set[str]:
    """Extract the action names from a structured IAM simulation command."""

    argv = command_argv(task)
    if "simulate-principal-policy" not in argv or "--action-names" not in argv:
        return set()
    start = argv.index("--action-names") + 1
    values = []
    for value in argv[start:]:
        if value.startswith("--"):
            break
        values.extend(part.strip().casefold() for part in value.split(","))
    return {value for value in values if value}


def task_has_token(task: dict, token: str) -> bool:
    return any(token.casefold() == argument.casefold() for argument in command_argv(task))


def task_text(task: dict) -> str:
    return json.dumps(task, ensure_ascii=False, sort_keys=True)


def scheduler_key_values(raw_value: str) -> dict[str, str]:
    values = {}
    for component in raw_value.split(","):
        key, separator, value = component.partition("=")
        if separator:
            values[key] = value
    return values


def scheduler_target(task: dict) -> dict:
    target = argv_value(command_argv(task), "--target")
    prefix, separator, encoded_input = target.partition(",Input=")
    values = scheduler_key_values(prefix)
    if not separator:
        values["Input"] = None
        return values
    try:
        values["Input"] = json.loads(encoded_input)
    except json.JSONDecodeError:
        values["Input"] = None
    return values


def aws_api_calls(source: str, service: str) -> set[str]:
    return set(re.findall(rf"aws\s+{re.escape(service)}\s+([a-z0-9-]+)\b", source))


def render_writer_policy() -> dict:
    rendered = read("operations/ansible/templates/backup-writer-policy.json.j2")
    rendered = rendered.replace("{{ backup_bucket_name }}", "gam-test-backups-123-sa-east-1")
    rendered = rendered.replace("{{ backup_prefix }}", "production/postgresql")
    rendered = rendered.replace(
        "{{ vps_public_source_ip | to_json }}",
        json.dumps("${VPS_PUBLIC_SOURCE_IP}"),
    )
    return json.loads(rendered)


def render_monitor_policy() -> dict:
    rendered = read("operations/ansible/templates/monitor-lambda-policy.json.j2")
    replacements = {
        "{{ aws_region }}": "sa-east-1",
        "{{ aws_account_id }}": "123456789012",
        "{{ backup_monitor_lambda_function_name }}": "gam-production-backup-monitor",
        "{{ backup_bucket_name }}": "gam-test-backups-123-sa-east-1",
        "{{ backup_prefix }}": "production/postgresql",
        "{{ developer_backup_alert_topic_arn }}": "arn:aws:sns:sa-east-1:123:developer",
        "{{ client_custodian_backup_alert_topic_arn }}": "arn:aws:sns:sa-east-1:123:client",
    }
    for template_value, actual_value in replacements.items():
        rendered = rendered.replace(template_value, actual_value)
    return json.loads(rendered)


class ProductionBackupAwsContractTest(unittest.TestCase):
    def test_manifest_contains_durable_boundary_fields_and_required_provenance(self):
        source = read("operations/recovery/backup/backup.sh")
        manifest_start = source.index("jq -n")
        manifest_end = source.index(' > "$MANIFEST_FILE"', manifest_start)
        manifest_program = source[manifest_start:manifest_end]

        fields = set(re.findall(r"^\s{6}([a-z][a-z0-9_]*)\s*:", manifest_program, re.MULTILINE))
        self.assertTrue(
            {"migration_state", "dump_size_bytes", "roles_size_bytes", "archive_size_bytes"}.issubset(fields),
            f"manifest fields were {sorted(fields)}",
        )

        provenance_inputs = (
            "GAM_SOURCE_COMMIT",
            "GAM_BACKEND_IMAGE_DIGEST",
            "GAM_FRONTEND_RELEASE",
            "GAM_FRONTEND_ARCHIVE",
            "GAM_FRONTEND_SHA256",
        )
        for variable in provenance_inputs:
            self.assertRegex(
                source,
                rf"(?m)^\s*(?::|test\s+-n)\s+.*\$\{{{variable}:?",
                f"{variable} must be required before producing a manifest",
            )
            self.assertNotIn(
                f"${{{variable}:-unrecorded}}",
                source,
                f"{variable} must not silently become a placeholder",
            )

    def test_restore_rotates_the_secret_consumed_by_the_application(self):
        restore = read("operations/recovery/restore/restore.sh")
        application = read("src/main/resources/application-dev.properties")

        self.assertIn("jwt.secret-key=${JWT_SECRET_KEY}", application)
        self.assertRegex(
            restore,
            r"(?m)^\s*export\s+JWT_SECRET_KEY\s*=",
            "restoration must export the secret using the application's real binding",
        )
        self.assertRegex(
            restore,
            r"(?is)JWT_SECRET_KEY.{0,240}(GAM_REQUIRE_SIGN_IN|refresh_tokens|truncate)",
            "secret rotation must be paired with global session invalidation",
        )

    def test_monthly_glacier_recovery_requests_restore_before_download(self):
        lifecycle = read("operations/ansible/templates/backup-lifecycle.json.j2")
        restore = read("operations/recovery/restore/restore.sh")

        self.assertRegex(
            lifecycle,
            r"(?is)monthly-recovery-points.*?StorageClass\s*[\"']?\s*:\s*[\"']GLACIER",
            "the recovery contract must identify the monthly objects that can require Glacier restoration",
        )
        restore_object = re.search(r"(?im)aws\s+s3api\s+restore-object\b", restore)
        self.assertIsNotNone(
            restore_object,
            "restoring an older monthly recovery point must invoke S3 Glacier restore-object before copying it",
        )
        restore_index = restore_object.start()
        copy_match = re.search(r"(?im)aws\s+s3\s+cp\b", restore)
        self.assertIsNotNone(copy_match, "the restore procedure must retain the verified archive download")
        copy_index = copy_match.start()
        self.assertLess(restore_index, copy_index, "Glacier restoration must be initiated before s3 cp")

        restore_window = restore[restore_index:copy_index]
        self.assertRegex(
            restore_window,
            r"(?is)(glacierjobparameters|flexible\s+retrieval|\btier\b).{0,300}(standard|bulk|expedited)",
            "restore-object must request an S3 Glacier Flexible Retrieval tier using the supported restore request",
        )
        head_object_positions = [
            match.start() for match in re.finditer(r"(?im)aws\s+s3api\s+head-object\b", restore)
        ]
        self.assertTrue(
            any(restore_index < position < copy_index for position in head_object_positions),
            "the restore procedure must poll head-object Restore status after requesting the Glacier restore",
        )
        self.assertRegex(
            restore_window,
            r"(?is)(while|until|sleep|retry|poll).{0,500}Restore",
            "Glacier Restore status must be polled until the object is available",
        )
        invalid_object_state = restore.lower().find("invalidobjectstate")
        self.assertGreaterEqual(
            invalid_object_state,
            0,
            "the procedure must handle InvalidObjectState before attempting to copy an archived object",
        )
        self.assertLess(
            invalid_object_state,
            copy_index,
            "InvalidObjectState handling must occur before s3 cp",
        )

    def test_restore_enforces_public_traffic_isolation_at_the_host_boundary(self):
        restore = read("operations/recovery/restore/restore.sh")

        self.assertRegex(
            restore,
            r"(?im)^\s*(?:iptables|ip6tables|nft|ufw|firewall-cmd)\b",
            "caller-provided isolation flags are not sufficient; the procedure must enforce the boundary",
        )

    def test_restore_isolation_fails_closed_for_ipv4_and_ipv6(self):
        restore = read("operations/recovery/restore/restore.sh")

        for command in ("iptables", "ip6tables"):
            self.assertRegex(
                restore,
                rf"(?im)^\s*{command}\s+-C\s+INPUT.*\n\s*\|\|\s*{command}\s+-I\s+INPUT",
                f"{command} must enforce a host-boundary drop rule",
            )
        self.assertNotRegex(
            restore,
            r"(?is)RESTORE_ENABLE_IPV6.*?ip6tables",
            "IPv6 isolation must not be disabled by an optional default-off flag",
        )

    def test_restore_cleanup_continues_after_dropdb_failure_before_shredding(self):
        git_bash = Path(r"C:\Program Files\Git\bin\bash.exe")
        self.assertTrue(git_bash.is_file(), "the safe Git Bash runtime is required for the restore failure-path test")

        with tempfile.TemporaryDirectory() as temporary_directory:
            temporary_root = Path(temporary_directory)
            fake_bin = temporary_root / "bin"
            fake_state = temporary_root / "state"
            fake_bin.mkdir()
            fake_state.mkdir()

            def install_fake(name: str, source: str) -> None:
                command = fake_bin / name
                command.write_text(source, encoding="utf-8")
                os.chmod(command, 0o755)

            install_fake(
                "dropdb",
                """#!/usr/bin/env bash
count_file="$FAKE_STATE_DIR/dropdb-count"
count=0
if [[ -f "$count_file" ]]; then count="$(<"$count_file")"; fi
count=$((count + 1))
printf '%s\n' "$count" > "$count_file"
if [[ "$count" -eq 2 ]]; then
    touch "$FAKE_STATE_DIR/dropdb-cleanup-failed"
    exit 42
fi
exit 0
""",
            )
            install_fake(
                "createdb",
                """#!/usr/bin/env bash
exit 0
""",
            )
            install_fake(
                "iptables",
                """#!/usr/bin/env bash
if [[ "$1" == "-C" ]]; then exit 1; fi
exit 0
""",
            )
            install_fake(
                "ip6tables",
                """#!/usr/bin/env bash
if [[ "$1" == "-C" ]]; then exit 1; fi
exit 0
""",
            )
            install_fake(
                "aws",
                """#!/usr/bin/env bash
if [[ "$1" == "s3api" && "$2" == "head-object" ]]; then
    printf '{}\n'
    exit 0
fi
if [[ "$1" == "s3" && "$2" == "cp" ]]; then
    touch "$FAKE_STATE_DIR/copy-failed"
    exit 66
fi
exit 0
""",
            )
            install_fake(
                "jq",
                """#!/usr/bin/env bash
case "$*" in
    *Metadata.sha256*) printf '%s\n' "$RECOVERY_SHA256" ;;
    *ObjectLockMode*) printf 'COMPLIANCE\n' ;;
    *ContentLength*) printf '1\n' ;;
    *) printf '\n' ;;
esac
""",
            )
            install_fake(
                "shred",
                """#!/usr/bin/env bash
touch "$FAKE_STATE_DIR/shred-called"
exit 0
""",
            )

            environment = os.environ.copy()
            environment.update(
                {
                    "AWS_REGION": "sa-east-1",
                    "RECOVERY_BUCKET": "gam-test-backups",
                    "RECOVERY_OBJECT_KEY": "production/postgresql/2026/06/08/recovery.dump.age",
                    "RECOVERY_SHA256": "a" * 64,
                    "AGE_IDENTITY_FILE": "/external/custodian/recovery.agekey",
                    "RESTORE_DATABASE_URL": "postgresql://isolated/database",
                    "RESTORE_ADMIN_DATABASE_URL": "postgresql://isolated/postgres",
                    "RESTORE_DATABASE_NAME": "gam_restore",
                    "JWT_SIGNING_SECRET_FILE": (temporary_root / "jwt.secret").as_posix(),
                    "RESTORE_NETWORK_MODE": "isolated",
                    "PUBLIC_TRAFFIC_DISABLED": "true",
                    "RESTORE_PUBLIC_INTERFACE": "restore0",
                    "RESTORATION_CORRECTIVE_ACTION": "failure-path-test",
                    "REPRESENTATIVE_ACCESS_CHECK_COMMAND": "true",
                    "RESTORATION_EVIDENCE_FILE": (temporary_root / "evidence.json").as_posix(),
                    "FAKE_STATE_DIR": fake_state.as_posix(),
                    "PATH": f"{fake_bin.as_posix()}{os.pathsep}{environment.get('PATH', '')}",
                }
            )

            result = subprocess.run(
                [str(git_bash), "operations/recovery/restore/restore.sh"],
                cwd=ROOT,
                env=environment,
                capture_output=True,
                text=True,
                timeout=30,
            )

            self.assertNotEqual(result.returncode, 0, "a failed restore must not report success")
            self.assertTrue((fake_state / "copy-failed").exists())
            self.assertTrue(
                (fake_state / "shred-called").exists(),
                "cleanup must continue to shred staging files after dropdb cleanup fails",
            )
            self.assertFalse((temporary_root / "evidence.json").exists())
            self.assertNotIn("isolated restoration verified", result.stdout)

    def test_attachment_sampling_compares_attachment_bytes_and_records_real_correction(self):
        verification = read("operations/recovery/verify-restoration/verify-restoration.sh")
        sampling_start = verification.index("ATTACHMENT_SAMPLE_CHECKSUM")
        sampling_end = verification.index("mkdir -p", sampling_start)
        sampling_sql = verification[sampling_start:sampling_end]

        self.assertRegex(
            sampling_sql,
            r"(?is)digest\s*\(\s*bytes\s*,\s*['\"]sha256['\"]\s*\)",
            "attachment evidence must digest the stored bytes, not the metadata hash strings",
        )
        self.assertRegex(
            verification,
            r"(?m)^\s*:?\s*\"?\$\{(?:RESTORATION_)?CORRECTIVE_ACTION(?::|-)",
            "corrective action must be supplied as an observable restoration input",
        )
        self.assertNotRegex(
            verification,
            r'--arg\s+corrective_action\s+"none recorded by this run"',
            "evidence must not claim that no corrective action was recorded unconditionally",
        )

    def test_restoration_evidence_requires_structural_invariant_representative_and_attachment_success(self):
        verification = read("operations/recovery/verify-restoration/verify-restoration.sh")
        before_evidence_write = verification[: verification.index("mkdir -p")]

        for variable in ("STRUCTURAL_RESULT", "INVARIANT_RESULT", "REPRESENTATIVE_RESULT"):
            self.assertRegex(
                before_evidence_write,
                rf"(?is)(?:test\s+|if\s+\[\[).{{0,100}}\${variable}.{{0,100}}(?:true|false|=|!=)",
                f"{variable} must be asserted before success evidence is written",
            )
        self.assertRegex(
            before_evidence_write,
            r"(?is)(?:test\s+|if\s+\[\[).{0,100}\$ATTACHMENT_SAMPLE_COUNT.{0,100}(?:-gt|true|=)",
            "attachment sampling count must be asserted before success evidence is written",
        )
        self.assertRegex(
            before_evidence_write,
            r"(?is)(?:test\s+|if\s+\[\[).{0,100}\$ATTACHMENT_SAMPLE_CHECKSUM.{0,100}(?:-n|empty|true|=)",
            "attachment sampling checksum must be asserted before success evidence is written",
        )

    def test_restoration_verification_fails_closed_when_flyway_history_is_empty(self):
        verification = read("operations/recovery/verify-restoration/verify-restoration.sh")
        before_evidence_write = verification[: verification.index("mkdir -p")]
        flyway_guard_patterns = (
            r"(?m)^\s*test\s+[\"']?\$FLYWAY_COUNT[\"']?\s+-gt\s+0",
            r"(?m)^\s*\[\[\s*[\"']?\$FLYWAY_COUNT[\"']?\s+-gt\s+0",
            r"(?m)^\s*if\s+\(\(\s*FLYWAY_COUNT\s*(?:<=|==)\s*0",
        )
        self.assertTrue(
            any(re.search(pattern, before_evidence_write) for pattern in flyway_guard_patterns),
            "restoration verification must reject an empty Flyway history before writing success evidence",
        )

    def test_public_health_contract_is_unauthenticated_and_returns_exact_up_body(self):
        java_sources = [
            path.read_text(encoding="utf-8")
            for path in (ROOT / "src" / "main" / "java").rglob("*.java")
        ]
        health_routes = [
            source
            for source in java_sources
            if re.search(r'@RequestMapping\(\s*["\']/api/health["\']', source)
            and re.search(r"@GetMapping\b", source)
        ]
        self.assertTrue(
            health_routes,
            "production must expose a GET /api/health route for external availability monitoring",
        )
        normalized_health_sources = re.sub(r"\s+", "", "".join(health_routes))
        self.assertIn(
            '{"status":"UP"}',
            normalized_health_sources.replace('\\"', '"'),
            "the public health route must return the exact healthy response body",
        )

        security = re.sub(r"\s+", "", read("src/main/java/br/org/gam/api/security/SecurityConfig.java"))
        self.assertIn(
            '.requestMatchers(HttpMethod.GET,"/api/health").permitAll()',
            security,
            "GET /api/health must be explicitly unauthenticated rather than falling through to authenticated requests",
        )

    def test_sampled_attachment_bytes_are_compared_with_stored_sha256_values(self):
        verification = read("operations/recovery/verify-restoration/verify-restoration.sh")
        sampling_start = verification.index("ATTACHMENT_SAMPLE_CHECKSUM")
        sampling_end = verification.index("mkdir -p", sampling_start)
        sampling_sql = verification[sampling_start:sampling_end]

        self.assertRegex(
            sampling_sql,
            r"(?is)(?:encode\s*\(\s*digest\s*\(\s*bytes\s*,\s*['\"]sha256['\"]\s*\).*?(?:=|IS\s+NOT\s+DISTINCT\s+FROM)\s*sha256|sha256\s*(?:=|IS\s+NOT\s+DISTINCT\s+FROM).*?encode\s*\(\s*digest\s*\(\s*bytes\s*,\s*['\"]sha256['\"])",
            "sampled attachment bytes must be compared to each stored sha256 value",
        )

    def test_backup_writer_verification_uses_metadata_apis_without_download_or_decrypt_path(self):
        source = read("operations/recovery/backup/backup.sh")
        s3api_calls = aws_api_calls(source, "s3api")
        high_level_s3_calls = aws_api_calls(source, "s3")
        kms_calls = aws_api_calls(source, "kms")

        self.assertTrue(
            {"head-object", "get-object-tagging"}.issubset(s3api_calls),
            f"writer verification must use HeadObject and object-tagging metadata APIs: {s3api_calls}",
        )
        self.assertTrue(
            s3api_calls.isdisjoint({"get-object", "select-object-content"}),
            "writer verification must not invoke an S3 object-content read API",
        )
        self.assertTrue(
            high_level_s3_calls.isdisjoint({"cp", "sync"}),
            "writer verification must not download or synchronize object content",
        )
        self.assertNotIn("decrypt", kms_calls, "writer verification must not invoke KMS decryption")
        self.assertNotRegex(
            source,
            r"(?im)^\s*(?:age|openssl|gpg)\b.*(?:--decrypt|\s+-d(?:\s|$)|\bdecrypt\b)",
            "writer verification must not invoke a content decryption command",
        )

    def test_writer_metadata_permissions_map_to_metadata_calls_and_not_content_downloads(self):
        source = read("operations/recovery/backup/backup.sh")
        policy_actions = {
            action
            for statement in render_writer_policy()["Statement"]
            for action in (statement["Action"] if isinstance(statement["Action"], list) else [statement["Action"]])
        }
        metadata_permissions = {
            "get-object-tagging": "s3:GetObjectTagging",
            "get-object-attributes": "s3:GetObjectAttributes",
            "get-object-retention": "s3:GetObjectRetention",
        }
        calls = aws_api_calls(source, "s3api")
        self.assertIn("head-object", calls, "backup verification must inspect object metadata")
        for api, permission in metadata_permissions.items():
            self.assertIn(api, calls, f"backup verification must call the metadata API {api}")
            self.assertIn(permission, policy_actions, f"writer policy must authorize metadata API {api}")
        self.assertIn(
            "s3:GetObject",
            policy_actions,
            "HeadObject requires s3:GetObject for metadata verification, without authorizing a content-download path",
        )

        content_read_apis = {"get-object", "select-object-content"}
        self.assertTrue(
            calls.isdisjoint(content_read_apis),
            "writer verification must not call an S3 object-content API",
        )
        self.assertNotRegex(source, r"(?m)^\s*aws\s+s3\s+(?:cp|sync)\b")
        self.assertNotRegex(source, r"(?m)^\s*aws\s+s3api\s+get-object\b")

    def test_uploaded_retention_is_checked_from_object_timestamp_after_upload(self):
        source = read("operations/recovery/backup/backup.sh")
        post_upload = source[source.index('HEAD_OBJECT="'): source.index('if [[ "$CLASSIFICATION" == monthly', source.index('HEAD_OBJECT="'))]

        self.assertRegex(
            post_upload,
            r"(?i)LastModified|last_modified|created_at",
            "post-upload retention verification must use the uploaded object's timestamp",
        )
        self.assertRegex(
            post_upload,
            r"(?i)ObjectLockRetainUntilDate|RetainUntilDate",
            "post-upload verification must inspect the remote retain-until timestamp",
        )
        self.assertRegex(
            post_upload,
            r"(?is)(?:RETENTION_DAYS|minimum.{0,80}days).{0,240}(?:date|retain|timestamp)",
            "post-upload verification must enforce class retention after upload latency",
        )

    def test_writer_provisions_machine_credentials_at_a_restricted_path(self):
        variables = load_yaml("operations/ansible/group_vars/production.yml")
        self.assertIn("backup_writer_credentials_file", variables)
        credentials_path = str(variables["backup_writer_credentials_file"])
        self.assertTrue(credentials_path.startswith("/etc/gam/"))

        site_tasks = list(task_nodes(load_yaml("operations/ansible/site.yml")))
        credential_file_tasks = []
        for task in site_tasks:
            payload = module_payload(task, "ansible.builtin.copy", "copy", "ansible.builtin.template", "template", "ansible.builtin.file", "file")
            if not isinstance(payload, dict):
                continue
            if payload.get("dest") == "{{ backup_writer_credentials_file }}" and str(payload.get("mode")) == "0600":
                credential_file_tasks.append(task)
        self.assertTrue(credential_file_tasks, "the machine writer credential needs a root-owned mode-0600 installation seam")

        aws_tasks = list(task_nodes(load_yaml("operations/ansible/aws-resources.yml")))
        self.assertTrue(
            any("create-access-key" in argument for task in aws_tasks for argument in command_argv(task)),
            "writer provisioning must create or rotate the non-human service credential rather than only logging it",
        )

    def test_clean_host_creates_the_backup_service_user_and_group(self):
        tasks = list(task_nodes(load_yaml("operations/ansible/site.yml")))
        user_tasks = [
            task
            for task in tasks
            if isinstance(module_payload(task, "ansible.builtin.user", "user"), dict)
        ]
        group_tasks = [
            task
            for task in tasks
            if isinstance(module_payload(task, "ansible.builtin.group", "group"), dict)
        ]

        self.assertTrue(
            any(
                payload.get("name") == "{{ backup_service_user }}"
                and payload.get("system") is True
                and payload.get("shell") in {"/usr/sbin/nologin", "/bin/false"}
                for task in user_tasks
                for payload in [module_payload(task, "ansible.builtin.user", "user")]
            ),
            "a clean host must receive a dedicated non-login backup service user",
        )
        self.assertTrue(
            any(
                payload.get("name") == "{{ backup_service_group }}"
                and payload.get("system") is True
                for task in group_tasks
                for payload in [module_payload(task, "ansible.builtin.group", "group")]
            ),
            "a clean host must receive the dedicated backup service group",
        )

    def test_writer_policy_allows_head_metadata_and_forbids_content_reads_with_class_specific_compliance_retention(self):
        policy = render_writer_policy()
        statements = policy["Statement"]
        actions = {
            action
            for statement in statements
            for action in statement.get("Action", [])
        }
        self.assertIn(
            "s3:GetObject",
            actions,
            "HeadObject requires s3:GetObject for metadata verification, without authorizing a content-download path",
        )
        self.assertIn("s3:GetObjectTagging", actions, "tag verification requires s3:GetObjectTagging")
        self.assertTrue(
            actions.isdisjoint(
                {
                    "s3:GetObjectVersion",
                    "s3:GetObjectVersionAcl",
                    "s3:GetObjectVersionTagging",
                    "s3:GetObjectTorrent",
                    "kms:Decrypt",
                    "kms:GenerateDataKey",
                    "kms:ReEncrypt*",
                }
            ),
            "metadata verification must not grant versioned/content-download or KMS decryption permissions",
        )

        retention_key = "s3:object-lock-remaining-retention-days"
        classification_key = "s3:RequestObjectTag/classification"
        expected_days = {"daily": 31, "weekly": 85, "monthly": 370}

        def values(condition: dict, key: str) -> set[str]:
            result: set[str] = set()
            for operator_values in condition.values():
                if isinstance(operator_values, dict) and key in operator_values:
                    value = operator_values[key]
                    result.update(str(item) for item in (value if isinstance(value, list) else [value]))
            return result

        for classification, minimum_days in expected_days.items():
            matching = []
            for statement in statements:
                condition = statement.get("Condition", {})
                class_values = values(condition, classification_key)
                numeric = condition.get("NumericGreaterThanEquals", {}).get(retention_key)
                numeric_values = [numeric] if not isinstance(numeric, list) else numeric
                if classification in class_values and any(int(value) >= minimum_days for value in numeric_values):
                    mode_values = values(condition, "s3:object-lock-mode")
                    if "COMPLIANCE" in {value.upper() for value in mode_values}:
                        matching.append(statement)
            self.assertTrue(
                matching,
                f"writer policy must bind {classification} to at least {minimum_days} Compliance days",
            )

    def test_monitor_rejects_daily_class_on_first_of_month_monday(self):
        monitor = MonitorHarness()

        monday_first = datetime(2026, 6, 1, 4, 30, tzinfo=SAO_PAULO)
        monitor.add_object(monday_first, "daily", key_classification="daily")
        monitor.module._current_local_date = lambda: monday_first
        valid, reasons, _ = monitor.module._check_today()
        self.assertFalse(valid, f"a first-of-month Monday daily object was accepted: {reasons}")
        self.assertIn("classification", " ".join(reasons).casefold())

    def test_monitor_rejects_pending_weekly_class_mismatch(self):
        monitor = MonitorHarness()
        pending_day = datetime(2026, 6, 2, 4, 30, tzinfo=SAO_PAULO)
        monitor.add_object(pending_day, "daily", key_classification="weekly")
        monitor.module._current_local_date = lambda: pending_day
        valid, reasons, _ = monitor.module._check_today()
        self.assertFalse(valid, f"a pending weekly object with a mismatched metadata class was accepted: {reasons}")
        self.assertIn("classification", " ".join(reasons).casefold())

    def test_monitor_accepts_weekly_recovery_point_after_missed_monday(self):
        monitor = MonitorHarness()
        failed_monday = datetime(2026, 6, 8, 4, 30, tzinfo=SAO_PAULO)
        monitor.module._current_local_date = lambda: failed_monday
        failure_result = monitor.module.lambda_handler({"phase": "daily"}, None)
        self.assertEqual("invalid", failure_result["status"])
        self.assertIn("backup-monitor/2026-06-08", monitor.dynamo.table.items)

        catchup_tuesday = datetime(2026, 6, 9, 4, 30, tzinfo=SAO_PAULO)
        monitor.add_object(catchup_tuesday, "weekly", key_classification="weekly")
        monitor.module._current_local_date = lambda: catchup_tuesday

        valid, reasons, details = monitor.module._check_today()
        self.assertTrue(valid, f"a valid catch-up weekly recovery point was rejected: {reasons}")
        self.assertEqual("weekly", details["classification"])

    def test_classification_history_rejects_daily_then_monthly_without_required_catchup(self):
        monitor = MonitorHarness()
        prior_day = datetime(2026, 6, 2, 4, 30, tzinfo=SAO_PAULO)
        monitor.add_object(prior_day, "daily", key_classification="daily")
        monitor.module._current_local_date = lambda: prior_day
        prior_valid, prior_reasons, _ = monitor.module._check_today()
        self.assertTrue(prior_valid, f"the valid prior daily recovery point was rejected: {prior_reasons}")

        next_day = datetime(2026, 6, 3, 4, 30, tzinfo=SAO_PAULO)
        monitor.add_object(next_day, "monthly", key_classification="monthly")
        monitor.module._current_local_date = lambda: next_day
        valid, reasons, _ = monitor.module._check_today()
        self.assertFalse(
            valid,
            f"monthly classification after an already-successful daily was accepted without required classification history: {reasons}",
        )

    def test_monitor_accepts_normal_midmonth_monday_weekly_artifact(self):
        monitor = MonitorHarness()
        monday = datetime(2026, 6, 8, 4, 30, tzinfo=SAO_PAULO)
        monitor.add_object(monday, "weekly", key_classification="weekly")
        monitor.module._current_local_date = lambda: monday

        valid, reasons, details = monitor.module._check_today()

        self.assertTrue(valid, f"a normal Monday weekly artifact was rejected: {reasons}")
        self.assertEqual("weekly", details["classification"])

    def test_expected_classifications_rejects_arbitrary_weekly_and_monthly_classes_during_ordinary_first_week(self):
        monitor = MonitorHarness()
        ordinary_first_week_day = datetime(2026, 6, 3, 4, 30, tzinfo=SAO_PAULO)

        self.assertEqual({}, monitor.dynamo.table.items, "the ordinary first-week case must have no persisted outage")
        self.assertEqual(
            {"daily"},
            monitor.module._expected_classifications(ordinary_first_week_day),
            "without a persisted outage, an ordinary first-week day must not authorize arbitrary weekly or monthly classes",
        )

    def test_monitor_rejects_weekly_catchup_after_wednesday_failure(self):
        monitor = MonitorHarness()
        failed_wednesday = datetime(2026, 6, 10, 4, 30, tzinfo=SAO_PAULO)
        monitor.module._current_local_date = lambda: failed_wednesday
        failure_result = monitor.module.lambda_handler({"phase": "daily"}, None)
        self.assertEqual("invalid", failure_result["status"])
        self.assertIn("backup-monitor/2026-06-10", monitor.dynamo.table.items)

        thursday = datetime(2026, 6, 11, 4, 30, tzinfo=SAO_PAULO)
        monitor.add_object(thursday, "weekly", key_classification="weekly")
        monitor.module._current_local_date = lambda: thursday
        catchup_result = monitor.module.lambda_handler({"phase": "daily"}, None)

        self.assertEqual(
            "invalid",
            catchup_result["status"],
            "weekly catch-up must require a failed Monday attempt, not an arbitrary same-week failure",
        )

    def test_monitor_lambda_state_transition_resolves_a_multiday_outage(self):
        monitor = MonitorHarness()
        failed_monday = datetime(2026, 6, 8, 4, 30, tzinfo=SAO_PAULO)
        monitor.module._current_local_date = lambda: failed_monday
        failure_result = monitor.module.lambda_handler({"phase": "daily"}, None)
        self.assertEqual("invalid", failure_result["status"])
        prior_failure_id = "backup-monitor/2026-06-08"
        self.assertIn(prior_failure_id, monitor.dynamo.table.items)

        recovered_wednesday = datetime(2026, 6, 10, 4, 30, tzinfo=SAO_PAULO)
        monitor.add_object(recovered_wednesday, "weekly", key_classification="weekly")
        monitor.module._current_local_date = lambda: recovered_wednesday
        recovery_result = monitor.module.lambda_handler({"phase": "daily"}, None)

        self.assertEqual("ok", recovery_result["status"])
        self.assertTrue(
            any(message.get("Subject") == "GAM backup recovery notice" for message in monitor.sns.messages),
            "lambda_handler must publish recovery after a successful artifact follows a persisted multi-day outage",
        )
        self.assertNotIn(prior_failure_id, monitor.dynamo.table.items)

    def test_monitor_rejects_midmonth_weekly_monthly_and_post_first_day_daily_classes(self):
        cases = (
            ("mid-month weekly", datetime(2026, 6, 10, 4, 30, tzinfo=SAO_PAULO), "weekly"),
            ("mid-month monthly", datetime(2026, 6, 10, 4, 30, tzinfo=SAO_PAULO), "monthly"),
            ("post-first-day daily", datetime(2026, 6, 2, 4, 30, tzinfo=SAO_PAULO), "daily"),
        )
        for label, local_date, classification in cases:
            with self.subTest(label=label):
                monitor = MonitorHarness()
                if label == "post-first-day daily":
                    outage_day = datetime(2026, 6, 1, 4, 30, tzinfo=SAO_PAULO)
                    monitor.module._current_local_date = lambda: outage_day
                    outage_result = monitor.module.lambda_handler({"phase": "daily"}, None)
                    self.assertEqual("invalid", outage_result["status"])
                    self.assertIn(
                        "backup-monitor/2026-06-01",
                        monitor.dynamo.table.items,
                        "the rejected post-first-day daily must represent a persisted prior outage",
                    )
                monitor.add_object(local_date, classification, key_classification=classification)
                monitor.module._current_local_date = lambda local_date=local_date: local_date
                valid, reasons, _ = monitor.module._check_today()
                self.assertFalse(
                    valid,
                    f"{label} classification was accepted without a valid calendar catch-up: {reasons}",
                )

    def test_monthly_pending_survives_first_of_month_outage_and_is_accepted_on_next_success(self):
        backup_source = read("operations/recovery/backup/backup.sh")
        self.assertRegex(
            backup_source,
            r'(?is)if\s+\[\[\s+-e\s+"\$MONTHLY_PENDING"\s+\]\].{0,180}CLASSIFICATION=monthly',
            "a first-of-month outage must preserve monthly classification for the next successful artifact",
        )

        monitor = MonitorHarness()
        outage_day = datetime(2026, 6, 1, 4, 30, tzinfo=SAO_PAULO)
        monitor.module._current_local_date = lambda: outage_day
        outage_result = monitor.module.lambda_handler({"phase": "daily"}, None)
        self.assertEqual("invalid", outage_result["status"])
        self.assertIn("backup-monitor/2026-06-01", monitor.dynamo.table.items)

        catch_up_day = datetime(2026, 6, 2, 4, 30, tzinfo=SAO_PAULO)
        monitor.add_object(catch_up_day, "monthly", key_classification="monthly")
        monitor.module._current_local_date = lambda: catch_up_day
        valid, reasons, details = monitor.module._check_today()
        self.assertTrue(valid, f"a valid monthly catch-up artifact was rejected: {reasons}")
        self.assertEqual("monthly", details["classification"])

    def test_monthly_catch_up_after_first_sunday_and_lost_pending_markers_is_not_weekly_only(self):
        backup_source = read("operations/recovery/backup/backup.sh")
        self.assertIn('MONTHLY_PENDING="$STATE_DIR/monthly.pending"', backup_source)

        monitor = MonitorHarness()
        first_sunday = datetime(2026, 11, 1, 4, 30, tzinfo=SAO_PAULO)
        monitor.module._current_local_date = lambda: first_sunday
        valid, reasons, _ = monitor.module._check_today()
        self.assertFalse(valid, f"an outage on the first Sunday was accepted: {reasons}")

        # A fresh monitor instance represents a host where local pending-marker
        # state was lost; the durable Monday object must still carry monthly
        # semantics rather than being rejected as weekly-only.
        monitor = MonitorHarness()
        monday_after_outage = datetime(2026, 11, 2, 4, 30, tzinfo=SAO_PAULO)
        monitor.add_object(monday_after_outage, "monthly", key_classification="monthly")
        monitor.module._current_local_date = lambda: monday_after_outage
        valid, reasons, details = monitor.module._check_today()
        self.assertTrue(valid, f"monthly catch-up after a first-Sunday outage was rejected: {reasons}")
        self.assertEqual("monthly", details["classification"])

    def test_backup_catchup_markers_inherit_across_timer_misses_and_calendar_boundaries(self):
        backup_source = read("operations/recovery/backup/backup.sh")
        timer_source = read("operations/ansible/templates/gam-backup.timer.j2")
        state_start = backup_source.index('WEEKLY_PENDING="$STATE_DIR/weekly.pending"')
        state_end = backup_source.index('readonly OBJECT_KEY', state_start)
        state_logic = backup_source[state_start:state_end]

        self.assertIn("Persistent=true", timer_source)
        self.assertRegex(
            state_logic,
            r"(?i)(?:LAST_SUCCESS|LAST_ATTEMPT|PREVIOUS_RUN|PENDING_STATE|STATE_FILE|RETRY)",
            "persistent timer catch-up must use durable prior-run state rather than only today's weekday/day",
        )
        self.assertRegex(
            state_logic,
            r"(?is)(?:WEEKLY_PENDING|MONTHLY_PENDING).{0,240}(?:LAST|PREVIOUS|PRIOR|PERSIST|ATTEMPT|SUCCESS)",
            "weekly/monthly marker inheritance must span a missed Tuesday and a first-successful Monday",
        )

    def test_monitor_failure_publishes_external_alarm_and_preserves_the_exception(self):
        monitor = MonitorHarness()
        monitor.s3.raise_on_list = True

        with self.assertRaises(RuntimeError):
            monitor.module.lambda_handler({"phase": "daily"}, None)

        self.assertTrue(
            any(message.get("Subject") == "GAM backup monitor failure" for message in monitor.sns.messages),
            "AWS monitor failures must publish an external alert before propagating the failure",
        )

    def test_next_day_catchup_resolves_prior_failure_and_publishes_recovery_notice(self):
        monitor = MonitorHarness()
        failed_monday = datetime(2026, 6, 8, 4, 30, tzinfo=SAO_PAULO)
        monitor.module._current_local_date = lambda: failed_monday
        first_result = monitor.module.lambda_handler({"phase": "daily"}, None)
        self.assertEqual("invalid", first_result["status"])
        prior_failure_id = "backup-monitor/2026-06-08"
        self.assertIn(prior_failure_id, monitor.dynamo.table.items)

        catchup_tuesday = datetime(2026, 6, 9, 4, 30, tzinfo=SAO_PAULO)
        monitor.add_object(catchup_tuesday, "weekly", key_classification="weekly")
        monitor.module._current_local_date = lambda: catchup_tuesday
        recovery_result = monitor.module.lambda_handler({"phase": "daily"}, None)

        self.assertEqual("ok", recovery_result["status"])
        self.assertTrue(
            any(message.get("Subject") == "GAM backup recovery notice" for message in monitor.sns.messages),
            "a later successful catch-up must notify recovery of the prior local-date failure",
        )
        self.assertNotIn(prior_failure_id, monitor.dynamo.table.items)

    def test_monitor_validates_object_tags_and_class_lifecycle_semantics(self):
        monitor = MonitorHarness()
        date = datetime(2026, 6, 3, 4, 30, tzinfo=SAO_PAULO)
        monitor.add_object(
            date,
            "daily",
            key_classification="daily",
            tags={"classification": "weekly"},
            lifecycle=[]
        )
        monitor.module._current_local_date = lambda: date

        valid, reasons, _ = monitor.module._check_today()
        self.assertFalse(valid, f"an object with invalid tags/lifecycle was accepted: {reasons}")
        self.assertGreater(monitor.s3.tag_calls, 0, "monitor must inspect object tags")
        self.assertGreater(monitor.s3.lifecycle_calls, 0, "monitor must inspect class lifecycle semantics")

    def test_monitor_runtime_rejects_mismatched_object_and_s3_checksum_values(self):
        monitor = MonitorHarness()
        local_date = datetime(2026, 6, 3, 4, 30, tzinfo=SAO_PAULO)
        key = monitor.add_object(local_date, "daily", key_classification="daily")
        monitor.s3.objects[key]["Metadata"]["sha256"] = "b" * 64
        monitor.module._current_local_date = lambda: local_date

        valid, reasons, _ = monitor.module._check_today()
        self.assertFalse(
            valid,
            "the monitor must reject an artifact whose stored SHA-256 disagrees with the S3 checksum metadata",
        )
        self.assertIn("checksum", " ".join(reasons).casefold())

    def test_monitor_schedules_are_enabled_and_disabled_drift_is_detected(self):
        tasks = list(task_nodes(load_yaml("operations/ansible/backup-monitor.yml")))
        schedule_tasks = [
            task
            for task in tasks
            if "scheduler" in command_argv(task) and "create-schedule" in command_argv(task)
        ]
        self.assertEqual(2, len(schedule_tasks))
        for task in schedule_tasks:
            argv = command_argv(task)
            self.assertIn("--state", argv)
            self.assertIn("ENABLED", [value.upper() for value in argv])

        drift_checks = [
            task
            for task in tasks
            if "get-schedule" in command_argv(task)
            and ("ENABLED" in task_text(task).upper() or "state" in task_text(task).casefold())
        ]
        self.assertGreaterEqual(len(drift_checks), 2, "each schedule needs an enabled-state validation path")

    def test_monitor_has_a_zero_invocation_liveness_alarm(self):
        tasks = list(task_nodes(load_yaml("operations/ansible/backup-monitor.yml")))
        alarms = [
            task
            for task in tasks
            if "cloudwatch" in command_argv(task) and "put-metric-alarm" in command_argv(task)
        ]
        invocation_alarms = []
        for task in alarms:
            argv = command_argv(task)
            if "--metric-name" not in argv:
                continue
            metric = argv[argv.index("--metric-name") + 1]
            if metric != "Invocations":
                continue
            comparator = argv[argv.index("--comparison-operator") + 1] if "--comparison-operator" in argv else ""
            threshold = argv[argv.index("--threshold") + 1] if "--threshold" in argv else ""
            if "LessThan" in comparator and threshold in {"0", "1"}:
                invocation_alarms.append(task)
        self.assertTrue(invocation_alarms, "zero successful monitor invocations must alarm independently of Lambda Errors")

    def test_monitor_schedule_cadence_and_target_fit_the_liveness_alarm_window(self):
        tasks = list(task_nodes(load_yaml("operations/ansible/backup-monitor.yml")))
        schedules = [
            task
            for task in tasks
            if "scheduler" in command_argv(task) and "create-schedule" in command_argv(task)
        ]
        schedule_by_name = {argv_value(command_argv(task), "--name"): command_argv(task) for task in schedules}
        self.assertEqual(
            {"gam-production-backup-monitor-0430", "gam-production-backup-monitor-1200"},
            set(schedule_by_name),
        )
        self.assertEqual("cron(30 4 * * ? *)", argv_value(schedule_by_name["gam-production-backup-monitor-0430"], "--schedule-expression"))
        self.assertEqual("cron(0 12 * * ? *)", argv_value(schedule_by_name["gam-production-backup-monitor-1200"], "--schedule-expression"))
        for argv in schedule_by_name.values():
            self.assertIn("Arn={{ backup_monitor_lambda_arn }}", argv_value(argv, "--target"))

        alarms = [
            task
            for task in tasks
            if "cloudwatch" in command_argv(task) and "put-metric-alarm" in command_argv(task)
            and argv_value(command_argv(task), "--metric-name") == "Invocations"
        ]
        self.assertEqual(1, len(alarms))
        alarm_argv = command_argv(alarms[0])
        # The longest normal gap is 16.5 hours (12:00 to next-day 04:30).
        self.assertGreaterEqual(
            int(argv_value(alarm_argv, "--period")),
            16 * 60 * 60 + 30 * 60,
            "the liveness period must cover the normal schedule gap",
        )
        self.assertEqual(
            "breaching",
            argv_value(alarm_argv, "--treat-missing-data").casefold(),
            "missing data must still breach after the cadence-safe liveness window",
        )

    def test_monitor_schedules_reconcile_cron_target_input_and_retry_drift(self):
        tasks = list(task_nodes(load_yaml("operations/ansible/backup-monitor.yml")))
        expected = {
            "gam-production-backup-monitor-0430": {
                "expression": "cron(30 4 * * ? *)",
                "input": {"phase": "daily"},
                "register": "daily_schedule_state",
            },
            "gam-production-backup-monitor-1200": {
                "expression": "cron(0 12 * * ? *)",
                "input": {"phase": "unresolved"},
                "register": "escalation_schedule_state",
            },
        }
        violations = []
        for name, specification in expected.items():
            create_task = next(
                (
                    task
                    for task in tasks
                    if "create-schedule" in command_argv(task)
                    and argv_value(command_argv(task), "--name") == name
                ),
                None,
            )
            if create_task is None:
                violations.append(f"missing desired schedule declaration for {name}")
                continue
            create_argv = command_argv(create_task)
            if argv_value(create_argv, "--schedule-expression") != specification["expression"]:
                violations.append(f"incorrect cron expression for {name}")
            if argv_value(create_argv, "--schedule-expression-timezone") != "America/Sao_Paulo":
                violations.append(f"incorrect timezone for {name}")
            target = scheduler_target(create_task)
            if (
                target.get("Arn") != "{{ backup_monitor_lambda_arn }}"
                or target.get("Input") != specification["input"]
            ):
                violations.append(f"incorrect Lambda target or input for {name}")
            retry_policy = scheduler_key_values(argv_value(create_argv, "--retry-policy"))
            if retry_policy != {
                "MaximumEventAgeInSeconds": "86400",
                "MaximumRetryAttempts": "3",
            }:
                violations.append(f"retry policy is not explicitly configured for {name}")

            get_tasks = [
                task
                for task in tasks
                if "get-schedule" in command_argv(task)
                and argv_value(command_argv(task), "--name") == name
            ]
            if not get_tasks:
                violations.append(f"existing {name} drift is not inspected")
            elif not any(
                argv_value(command_argv(task), "--output") == "json"
                and task.get("register") == specification["register"]
                for task in get_tasks
            ):
                violations.append(f"existing {name} drift is not inspected as JSON state")

            update_tasks = [
                task
                for task in tasks
                if "update-schedule" in command_argv(task)
                and argv_value(command_argv(task), "--name") == name
            ]
            if not update_tasks:
                violations.append(f"HTTP 409/remote drift has no update-schedule reconciliation for {name}")
            else:
                update_task = update_tasks[0]
                update_argv = command_argv(update_task)
                update_target = scheduler_target(update_task)
                update_retry_policy = scheduler_key_values(argv_value(update_argv, "--retry-policy"))
                if argv_value(update_argv, "--schedule-expression") != specification["expression"]:
                    violations.append(f"update-schedule does not restore the cron expression for {name}")
                if argv_value(update_argv, "--schedule-expression-timezone") != "America/Sao_Paulo":
                    violations.append(f"update-schedule does not restore the timezone for {name}")
                if (
                    update_target.get("Arn") != "{{ backup_monitor_lambda_arn }}"
                    or update_target.get("Input") != specification["input"]
                ):
                    violations.append(f"update-schedule does not restore the target or input for {name}")
                if update_retry_policy != {
                    "MaximumEventAgeInSeconds": "86400",
                    "MaximumRetryAttempts": "3",
                }:
                    violations.append(f"update-schedule does not restore retry policy for {name}")

                when_values = update_task.get("when", [])
                if isinstance(when_values, str):
                    when_values = [when_values]
                drift_conditions = [str(condition) for condition in when_values]
                expected_comparisons = (
                    ("ScheduleExpression", specification["expression"]),
                    ("ScheduleExpressionTimezone", "America/Sao_Paulo"),
                    ("State", "ENABLED"),
                    ("Target.Arn", "backup_monitor_lambda_arn"),
                    ("Target.Input", json.dumps(specification["input"], separators=(",", ":"))),
                    ("Target.RetryPolicy.MaximumEventAgeInSeconds", "86400"),
                    ("Target.RetryPolicy.MaximumRetryAttempts", "3"),
                )
                missing_comparisons = [
                    field
                    for field, expected_value in expected_comparisons
                    if not any(field in condition and expected_value in condition for condition in drift_conditions)
                ]
                if missing_comparisons:
                    violations.append(
                        f"update-schedule does not compare remote drift fields for {name}: "
                        + ", ".join(missing_comparisons)
                    )

        self.assertEqual([], violations, "EventBridge schedule drift violations: " + "; ".join(violations))

    def test_monitor_permission_failures_are_alarmable(self):
        tasks = list(task_nodes(load_yaml("operations/ansible/backup-monitor.yml")))
        alarm_texts = [task_text(task).casefold() for task in tasks if "cloudwatch" in command_argv(task)]
        self.assertTrue(
            any(
                any(marker in text for marker in ("failedinvocations", "targeterrorcount", "accessdenied", "permission"))
                for text in alarm_texts
            ),
            "scheduler/Lambda permission failures need a distinct monitor alarm",
        )

    def test_external_monitor_roles_are_validated_for_machine_permissions(self):
        tasks = list(task_nodes(load_yaml("operations/ansible/backup-monitor.yml")))
        for role_variable in (
            "GAM_BACKUP_MONITOR_LAMBDA_ROLE_ARN",
            "GAM_BACKUP_MONITOR_SCHEDULER_ROLE_ARN",
        ):
            role_checks = [
                task
                for task in tasks
                if any(
                    argument in {
                        "get-role-policy",
                        "list-attached-role-policies",
                        "list-role-policies",
                        "simulate-principal-policy",
                    }
                    for argument in command_argv(task)
                )
                and role_variable in command_argv(task)
            ]
            self.assertTrue(role_checks, f"{role_variable} permissions must be validated rather than only its ARN")

    def test_external_monitor_role_validation_checks_the_required_machine_actions(self):
        tasks = list(task_nodes(load_yaml("operations/ansible/backup-monitor.yml")))
        required_actions = {
            "GAM_BACKUP_MONITOR_LAMBDA_ROLE_ARN": ("logs:", "dynamodb:", "s3:", "sns:"),
            "GAM_BACKUP_MONITOR_SCHEDULER_ROLE_ARN": ("lambda:",),
        }
        for role_variable, actions in required_actions.items():
            validated_actions = set()
            for task in tasks:
                if role_variable in command_argv(task):
                    validated_actions.update(iam_simulation_actions(task))
            for action_prefix in actions:
                self.assertTrue(
                    any(action.startswith(action_prefix) for action in validated_actions),
                    f"{role_variable} validation must constrain {action_prefix} actions",
                )

    def test_monitor_machine_roles_and_policies_are_reproducibly_provisioned(self):
        tasks = list(task_nodes(load_yaml("operations/ansible/backup-monitor.yml")))
        role_creates = [
            task
            for task in tasks
            if "create-role" in command_argv(task)
        ]
        self.assertGreaterEqual(
            len(role_creates),
            2,
            "Lambda and Scheduler machine roles must be created by Ansible rather than supplied only as ARNs",
        )

        created_role_names = set()
        for task in role_creates:
            argv = command_argv(task)
            role_name = argv_value(argv, "--role-name")
            self.assertTrue(role_name, "a reproducible machine role needs an explicit role name")
            self.assertIn("--assume-role-policy-document", argv)
            created_role_names.add(role_name)

        policy_tasks = [
            task
            for task in tasks
            if any(command in command_argv(task) for command in ("put-role-policy", "attach-role-policy"))
        ]
        attached_role_names = {
            argv_value(command_argv(task), "--role-name")
            for task in policy_tasks
        }
        self.assertTrue(
            created_role_names.issubset(attached_role_names),
            "each reproducibly created monitor role must receive its machine policy",
        )

    def test_monitor_log_group_creation_permission_uses_wildcard_resource(self):
        policy = render_monitor_policy()
        create_log_group_statements = [
            statement
            for statement in policy["Statement"]
            if "logs:CreateLogGroup" in set(
                statement["Action"] if isinstance(statement["Action"], list) else [statement["Action"]]
            )
        ]
        self.assertTrue(create_log_group_statements, "the Lambda policy must explicitly authorize log-group creation")
        self.assertTrue(
            any(statement.get("Resource") == "*" for statement in create_log_group_statements),
            "AWS requires logs:CreateLogGroup to use Resource *; log-stream writes may remain resource-scoped",
        )

    def test_monitor_lambda_timeout_is_explicitly_longer_than_aws_default(self):
        tasks = list(task_nodes(load_yaml("operations/ansible/backup-monitor.yml")))
        function_configuration_tasks = [
            task
            for task in tasks
            if "update-function-configuration" in command_argv(task)
            or "create-function" in command_argv(task)
        ]
        configured_timeouts = []
        for task in function_configuration_tasks:
            value = argv_value(command_argv(task), "--timeout")
            if value:
                configured_timeouts.append(int(value))
        self.assertTrue(
            configured_timeouts,
            "the sequential S3/DynamoDB/SNS monitor Lambda must not rely on AWS's three-second default timeout",
        )
        self.assertTrue(
            all(timeout > 3 for timeout in configured_timeouts),
            f"monitor Lambda timeouts must exceed the AWS default: {configured_timeouts}",
        )

    def test_created_monitor_resources_have_cost_allocation_tags(self):
        tasks = list(task_nodes(load_yaml("operations/ansible/backup-monitor.yml")))
        created_resources = [
            task
            for task in tasks
            if any(
                command in command_argv(task)
                for command in ("create-function", "create-schedule", "create-table")
            )
        ]
        self.assertTrue(created_resources)
        for task in created_resources:
            text = task_text(task)
            self.assertIn("Project", text, f"created monitor resource is missing Project tag: {task.get('name')}")
            self.assertIn("Environment", text, f"created monitor resource is missing Environment tag: {task.get('name')}")
            self.assertIn("Purpose", text, f"created monitor resource is missing Purpose tag: {task.get('name')}")

    def test_better_stack_collector_is_deployed_and_verified_with_docker_compose(self):
        tasks = list(task_nodes(load_yaml("operations/ansible/site.yml")))
        compose_commands = [
            task
            for task in tasks
            if module_payload(task, "ansible.builtin.command", "command", "ansible.builtin.shell", "shell") is not None
            and "docker compose" in " ".join(command_argv(task)).casefold()
        ]
        self.assertTrue(compose_commands, "the metrics-only collector must use Docker Compose")
        self.assertTrue(
            any("docker compose ps" in " ".join(command_argv(task)).casefold() for task in compose_commands),
            "the Docker Compose collector deployment must be verified after startup",
        )

    def test_better_stack_collector_configures_and_verifies_the_provider_source(self):
        tasks = list(task_nodes(load_yaml("operations/ansible/site.yml")))
        collector_uri_tasks = [
            (index, task, module_payload(task, "ansible.builtin.uri", "uri"))
            for index, task in enumerate(tasks)
            if isinstance(module_payload(task, "ansible.builtin.uri", "uri"), dict)
            and "telemetry.betterstack.com/api/v1/collectors" in str(
                module_payload(task, "ansible.builtin.uri", "uri").get("url", "")
            ).casefold()
        ]
        self.assertTrue(
            collector_uri_tasks,
            "collector readiness must use Better Stack's official telemetry collectors endpoint",
        )

        disabled_components = {
            "logs_docker",
            "logs_host",
            "logs_kubernetes",
            "logs_collector_internals",
            "ebpf_tracing_basic",
            "ebpf_tracing_full",
            "traces_opentelemetry",
        }
        metrics_components = {"ebpf_metrics", "ebpf_red_metrics", "metrics_databases"}
        configuration_tasks = [
            (index, payload)
            for index, _, payload in collector_uri_tasks
            if str(payload.get("method", "")).upper() in {"POST", "PATCH"}
        ]
        self.assertTrue(
            configuration_tasks,
            "collector configuration must use the official create or update API",
        )
        self.assertTrue(
            any(
                disabled_components <= set(payload.get("body", {}).get("configuration", {}).get("components", {}))
                and all(
                    payload["body"]["configuration"]["components"].get(component) is False
                    for component in disabled_components
                )
                and any(
                    payload["body"]["configuration"]["components"].get(component) is True
                    for component in metrics_components
                )
                for _, payload in configuration_tasks
            ),
            "collector configuration must retain metrics-only collection and disable documented log/trace components",
        )

        verification_tasks = [
            (index, task, payload)
            for index, task, payload in collector_uri_tasks
            if str(payload.get("method", "")).upper() == "GET"
            and payload.get("return_content") is True
        ]
        self.assertTrue(
            verification_tasks,
            "collector deployment must read back the provider-side collector source configuration",
        )
        first_verification_index = min(index for index, _, _ in verification_tasks)
        verification_assertions = [
            task
            for index, task in enumerate(tasks)
            if index > first_verification_index
            and isinstance(module_payload(task, "ansible.builtin.assert", "assert"), dict)
        ]
        verification_text = "\n".join(task_text(task) for task in verification_assertions).casefold()
        self.assertTrue(
            "configuration" in verification_text
            and "components" in verification_text
            and all(component in verification_text for component in disabled_components)
            and all(re.search(rf"{re.escape(component)}[^\n}}]*false", verification_text) for component in disabled_components),
            "collector readback must verify the provider-side disabled component flags",
        )

        self.assertTrue(
            any(
                "COLLECTOR_SECRET" in task_text(task)
                and "better_stack_collector_secret" in task_text(task)
                for task in tasks
            ),
            "collector deployment must retain the dedicated COLLECTOR_SECRET installation",
        )

    def test_better_stack_availability_and_tls_checks_are_registered_externally(self):
        tasks = list(task_nodes(load_yaml("operations/ansible/site.yml")))
        registrations = [
            task
            for task in tasks
            if module_payload(task, "ansible.builtin.uri", "uri") is not None
            and str(module_payload(task, "ansible.builtin.uri", "uri").get("method", "")).upper() in {"POST", "PUT"}
        ]
        availability = [task for task in registrations if "availability" in task_text(task).casefold() or "health" in task_text(task).casefold()]
        tls = [task for task in registrations if "tls" in task_text(task).casefold() or "certificate" in task_text(task).casefold()]
        self.assertTrue(availability, "the external Better Stack availability check must be registered")
        self.assertTrue(tls, "the external Better Stack TLS check must be registered")
        availability_bodies = [
            module_payload(task, "ansible.builtin.uri", "uri").get("body", {})
            for task in availability
        ]
        self.assertTrue(
            any(
                body.get("required_keyword") == '{"status":"UP"}'
                for body in availability_bodies
                if isinstance(body, dict)
            ),
            "Better Stack availability registration must use the provider-supported required keyword",
        )

    def test_better_stack_realization_binds_supported_collector_configuration_and_external_contract(self):
        tasks = list(task_nodes(load_yaml("operations/ansible/site.yml")))
        compose_tasks = [
            task
            for task in tasks
            if "docker compose" in " ".join(command_argv(task)).casefold()
            or "docker-compose" in " ".join(command_argv(task)).casefold()
        ]
        self.assertTrue(
            compose_tasks,
            "the supported Better Stack collector must be installed through Docker Compose",
        )

        service_binding = any(
            "/etc/gam/better-stack-monitoring-contract.yml" in task_text(task)
            for task in tasks
            if module_payload(task, "ansible.builtin.template", "template") is not None
        ) and any("docker compose" in " ".join(command_argv(task)).casefold() for task in tasks)
        self.assertTrue(
            service_binding,
            "the Docker Compose collector must be paired with the versioned metrics-only contract",
        )

        monitoring = yaml.safe_load(read("operations/ansible/templates/better-stack-monitoring.yml.j2"))
        availability_config = monitoring["availability"]
        self.assertEqual('{"status":"UP"}', availability_config["required_keyword"])
        self.assertEqual(300, availability_config["interval_seconds"])
        self.assertEqual(600, availability_config.get("confirmation_period_seconds", availability_config.get("confirmation_period")))
        self.assertNotIn("3 consecutive", str(availability_config).casefold())
        collector = monitoring["collector"]
        self.assertEqual("metrics-only", collector["mode"])
        self.assertFalse(collector["export_request_body"])
        self.assertFalse(collector["export_application_logs"])
        self.assertFalse(collector["export_distributed_traces"])

        registrations = [
            module_payload(task, "ansible.builtin.uri", "uri")
            for task in tasks
            if module_payload(task, "ansible.builtin.uri", "uri") is not None
        ]
        availability = next(
            body for body in registrations
            if body.get("body", {}).get("pronounceable_name") == "GAM production availability"
        )
        self.assertEqual('{"status":"UP"}', availability["body"]["required_keyword"])
        self.assertEqual(600, availability["body"]["confirmation_period"])
        tls = next(body for body in registrations if body.get("body", {}).get("monitor_type") == "status")
        self.assertEqual(3600, tls["body"]["check_frequency"])

    def test_better_stack_collector_secret_is_preflighted_and_bound_to_persistent_service(self):
        site_tasks = list(task_nodes(load_yaml("operations/ansible/site.yml")))
        assert_payloads = [
            module_payload(task, "ansible.builtin.assert", "assert")
            for task in site_tasks
            if isinstance(module_payload(task, "ansible.builtin.assert", "assert"), dict)
        ]
        self.assertTrue(
            any(
                any("better_stack_collector_secret | length > 0" in str(condition) for condition in payload.get("that", []))
                for payload in assert_payloads
            ),
            "Better Stack metrics credentials must be required by deployment preflight",
        )

        self.assertTrue(
            any(
                "COLLECTOR_SECRET" in task_text(task)
                and "better_stack_collector_secret" in task_text(task)
                for task in site_tasks
            ),
            "the Docker Compose collector deployment must receive the dedicated COLLECTOR_SECRET",
        )
        self.assertNotIn(
            "BETTER_STACK_METRICS_TOKEN",
            "\n".join(task_text(task) for task in site_tasks)
            + read("operations/ansible/templates/better-stack-monitoring.yml.j2"),
            "the collector must not reuse an Uptime API or invented metrics token",
        )

    def test_better_stack_registrations_use_provider_schema_and_reconcile_conflicts(self):
        tasks = list(task_nodes(load_yaml("operations/ansible/site.yml")))
        registrations = [
            task
            for task in tasks
            if isinstance(module_payload(task, "ansible.builtin.uri", "uri"), dict)
            and str(module_payload(task, "ansible.builtin.uri", "uri").get("method", "")).upper() == "POST"
            and "/monitors" in str(module_payload(task, "ansible.builtin.uri", "uri").get("url", ""))
        ]
        availability_task = next(
            task for task in registrations if "availability" in task.get("name", "").casefold()
        )
        tls_task = next(task for task in registrations if "tls" in task.get("name", "").casefold())
        availability = module_payload(availability_task, "ansible.builtin.uri", "uri")["body"]
        tls = module_payload(tls_task, "ansible.builtin.uri", "uri")["body"]

        violations = []
        if availability.get("pronounceable_name") != "GAM production availability":
            violations.append("availability must use Better Stack's pronounceable_name field")
        if availability.get("monitor_type") != "keyword":
            violations.append("availability must use a keyword monitor for the exact healthy body")
        if availability.get("required_keyword") != '{"status":"UP"}':
            violations.append("availability must use the provider's required_keyword field")
        if availability.get("http_method", "").upper() != "GET":
            violations.append("availability must use the provider's http_method field")
        if availability.get("check_frequency") != 300:
            violations.append("availability must check every five minutes")
        if availability.get("confirmation_period") != 600:
            violations.append("availability must use a 600-second confirmation period")
        if availability.get("email") is not True or availability.get("push") is not True:
            violations.append("availability must configure hosted email and push alerts")
        if "expected_status_code" in availability:
            violations.append("unsupported expected_status_code fields must not be sent")

        if tls.get("pronounceable_name") != "GAM production TLS certificate":
            violations.append("TLS monitor must use the provider's pronounceable_name field")
        if tls.get("monitor_type") != "status":
            violations.append("TLS monitor must use a supported status monitor type")
        if tls.get("ssl_expiration") != 30 or tls.get("verify_ssl") is not True:
            violations.append("TLS monitor must verify certificates and warn at 30 days")

        conflict_acceptors = []
        for task in registrations:
            status_codes = module_payload(task, "ansible.builtin.uri", "uri").get("status_code", [])
            if 409 in status_codes:
                conflict_acceptors.append(task)
        reconciliation_methods = {
            str(module_payload(task, "ansible.builtin.uri", "uri").get("method", "")).upper()
            for task in tasks
            if isinstance(module_payload(task, "ansible.builtin.uri", "uri"), dict)
            and "/monitors" in str(module_payload(task, "ansible.builtin.uri", "uri").get("url", ""))
        }
        if conflict_acceptors and not ({"GET", "PATCH"} <= reconciliation_methods):
            violations.append("HTTP 409 must trigger list/read and PATCH reconciliation of the existing monitor")

        self.assertEqual([], violations, "Better Stack registration contract violations: " + "; ".join(violations))

        provider_monitors = [
            {
                "id": "monitor-availability-123",
                "attributes": {"pronounceable_name": "GAM production availability"},
            },
            {
                "id": "monitor-tls-456",
                "attributes": {"pronounceable_name": "GAM production TLS certificate"},
            },
        ]
        for monitor in provider_monitors:
            provider_name = monitor["attributes"]["pronounceable_name"]
            reconciliation_task = next(
                task
                for task in tasks
                if task.get("name")
                == f"Reconcile the existing Better Stack {'availability monitor' if 'availability' in provider_name else 'TLS monitor'}"
            )
            reconciliation_uri = module_payload(reconciliation_task, "ansible.builtin.uri", "uri")
            self.assertIn("item.id", reconciliation_uri["url"])
            self.assertNotIn("item.attributes.id", reconciliation_uri["url"])
            self.assertIn("item.attributes.pronounceable_name", str(reconciliation_task.get("when")))
            self.assertIn(provider_name, str(reconciliation_task.get("when")))

            duplicate_guard_task = next(
                task
                for task in tasks
                if task.get("name")
                == f"Register the Better Stack external {'availability health check' if 'availability' in provider_name else 'TLS certificate check'}"
            )
            duplicate_guard = str(duplicate_guard_task.get("when"))
            self.assertIn("selectattr('attributes.pronounceable_name'", duplicate_guard)
            self.assertNotIn("selectattr('pronounceable_name'", duplicate_guard)
            self.assertIn(provider_name, duplicate_guard)

    def test_better_stack_clean_host_provisions_supported_metrics_collector_and_service_alerts(self):
        tasks = list(task_nodes(load_yaml("operations/ansible/site.yml")))
        supported_installation = any(
            module_payload(task, "ansible.builtin.get_url", "get_url") is not None
            and "better-stack" in task_text(task).casefold()
            for task in tasks
        ) and any(
            "docker compose" in " ".join(command_argv(task)).casefold()
            and "better-stack" in task_text(task).casefold()
            for task in tasks
        )
        self.assertTrue(
            supported_installation,
            "a clean host must install and execute the supported Better Stack Docker Compose collector",
        )

        monitoring = yaml.safe_load(read("operations/ansible/templates/better-stack-monitoring.yml.j2"))
        collector = monitoring.get("collector", {})
        service_checks = collector.get("service_checks", collector.get("services", []))
        if isinstance(service_checks, dict):
            service_names = set(service_checks)
        else:
            service_names = {
                str(item.get("name", item.get("service", "")))
                for item in service_checks
                if isinstance(item, dict)
            }
        self.assertTrue(
            {"proxy", "backend", "postgresql"}.issubset({name.casefold() for name in service_names}),
            "metrics-only collector configuration must declare proxy, backend, and PostgreSQL service alerts",
        )
        filesystem = collector.get("filesystem", {})
        self.assertIsInstance(filesystem, dict)
        self.assertEqual(80, filesystem.get("warning_percent"))
        self.assertEqual(90, filesystem.get("critical_percent"))

    def test_better_stack_provider_targets_and_alerts_bind_declared_monitoring_contract(self):
        tasks = list(task_nodes(load_yaml("operations/ansible/site.yml")))
        uri_tasks = [
            task
            for task in tasks
            if isinstance(module_payload(task, "ansible.builtin.uri", "uri"), dict)
        ]
        target_tasks = [
            task
            for task in uri_tasks
            if "/api/v1/collectors/" in str(module_payload(task, "ansible.builtin.uri", "uri").get("url", ""))
            and "/targets" in str(module_payload(task, "ansible.builtin.uri", "uri").get("url", ""))
        ]
        self.assertTrue(
            target_tasks,
            "REQ-OPS-007 must be bound to Better Stack collector metric targets, not only local YAML",
        )

        target_text = "\n".join(task_text(task) for task in target_tasks)
        self.assertRegex(
            target_text,
            r"\{\{[^}]*better_stack_collector_id[^}]*\}\}",
            "collector metric target requests must use a parameterized collector ID",
        )
        self.assertNotIn(
            "BETTER_STACK_METRICS_TOKEN",
            target_text,
            "collector targets must use the existing provider credential binding, not an invented metrics token",
        )

        supported_kinds = {"postgres", "nginx", "apache", "kafka", "prometheus"}
        target_bodies = [
            module_payload(task, "ansible.builtin.uri", "uri").get("body", {})
            for task in target_tasks
        ]
        for service, required_kind in (
            ("proxy", {"nginx", "apache", "prometheus"}),
            ("backend", {"nginx", "apache", "prometheus"}),
            ("postgresql", {"postgres"}),
        ):
            matching = [
                (task, body)
                for task, body in zip(target_tasks, target_bodies)
                if service in task_text(task).casefold()
                or service in json.dumps(body, ensure_ascii=False, sort_keys=True).casefold()
            ]
            self.assertTrue(
                matching,
                f"the declared {service} service must have an official Better Stack collector target",
            )
            self.assertTrue(
                any(
                    isinstance(body, dict)
                    and body.get("kind") in supported_kinds
                    and body.get("kind") in required_kind
                    and all(field in body for field in ("host", "port"))
                    for _, body in matching
                ),
                f"the {service} target must use the official provider kind and host/port inputs",
            )

        postgres_bodies = [body for body in target_bodies if isinstance(body, dict) and body.get("kind") == "postgres"]
        self.assertTrue(postgres_bodies, "the PostgreSQL target must use Better Stack's supported postgres kind")
        self.assertTrue(
            all("ssl_mode" in body for body in postgres_bodies),
            "the PostgreSQL collector target must use the provider-supported ssl_mode field",
        )

        alert_tasks = [
            task
            for task in uri_tasks
            if "/api/v2/dashboards/" in str(module_payload(task, "ansible.builtin.uri", "uri").get("url", ""))
            and "/charts/" in str(module_payload(task, "ansible.builtin.uri", "uri").get("url", ""))
            and "/alerts" in str(module_payload(task, "ansible.builtin.uri", "uri").get("url", ""))
        ]
        self.assertTrue(
            alert_tasks,
            "REQ-OPS-007 thresholds must be bound to supported Better Stack dashboard-chart alerts",
        )
        alert_text = "\n".join(task_text(task) for task in alert_tasks)
        self.assertRegex(
            alert_text,
            r"\{\{[^}]*better_stack_dashboard_id[^}]*\}\}",
            "dashboard alert requests must use a parameterized dashboard ID",
        )
        self.assertRegex(
            alert_text,
            r"\{\{[^}]*chart[^}]*id[^}]*\}\}",
            "dashboard alert requests must use a parameterized chart ID",
        )
        alert_bodies = [
            module_payload(task, "ansible.builtin.uri", "uri").get("body", {})
            for task in alert_tasks
        ]
        self.assertTrue(
            all(
                isinstance(body, dict)
                and body.get("alert_type") == "threshold"
                and body.get("operator")
                and "check_period" in body
                and "confirmation_period" in body
                for body in alert_bodies
            ),
            "dashboard alerts must use Better Stack's supported threshold-alert fields",
        )
        alert_names = " ".join(
            json.dumps(body, ensure_ascii=False, sort_keys=True).casefold()
            for body in alert_bodies
        )
        for service in ("proxy", "backend", "postgresql"):
            self.assertIn(
                service,
                alert_names,
                f"the {service} service alert must be represented by a provider-side dashboard alert",
            )
        filesystem_alerts = [
            body
            for body in alert_bodies
            if "filesystem" in json.dumps(body, ensure_ascii=False, sort_keys=True).casefold()
            or "disk" in json.dumps(body, ensure_ascii=False, sort_keys=True).casefold()
        ]
        self.assertTrue(filesystem_alerts, "filesystem warning/critical alerts must be provider-side dashboard alerts")
        filesystem_values = {body.get("value") for body in filesystem_alerts}
        self.assertTrue({80, 90}.issubset(filesystem_values), "provider-side filesystem alerts must retain 80% and 90% thresholds")

    def test_restore_total_recovery_budget_covers_glacier_and_post_download_steps(self):
        """Protect the 24-hour RTO across the documented restore flow."""

        restore = read("operations/recovery/restore/restore.sh")
        total_budget = re.search(
            r"(?m)^\s*(?P<name>(?!GLACIER_)[A-Z][A-Z0-9_]*(?:TOTAL|RTO|RECOVERY)[A-Z0-9_]*(?:DEADLINE|BUDGET|TIMEOUT)[A-Z0-9_]*)\s*=\s*['\"]?\$\{[^:}]+:-(?P<default>[1-9][0-9]*)",
            restore,
        )
        self.assertIsNotNone(
            total_budget,
            "restore must expose a parameterized total recovery deadline or budget distinct from the Glacier polling timeout",
        )
        budget_name = total_budget.group("name")
        budget_default = int(total_budget.group("default"))
        self.assertLess(
            budget_default,
            86400,
            "the default total recovery budget must leave time for download, decryption, restore, and validation within the 24-hour RTO",
        )

        first_head_object = restore.index("aws s3api head-object")
        self.assertIn(
            budget_name,
            restore[:first_head_object],
            "the total recovery deadline must start before any Glacier polling or object inspection",
        )

        restore_object = restore.index("aws s3api restore-object")
        archive_copy = restore.index("aws s3 cp")
        self.assertIn(
            budget_name,
            restore[restore_object:archive_copy],
            "Glacier polling must consume the shared total recovery budget rather than an independent 24-hour allowance",
        )

        verification = restore.index("/usr/local/libexec/gam-verify-restoration")
        post_download_flow = restore[archive_copy:verification]
        self.assertRegex(
            post_download_flow,
            rf"(?is)(?:if|while|test|deadline|budget).{{0,300}}{re.escape(budget_name)}|{re.escape(budget_name)}.{{0,300}}(?:date \+%s|RESTORATION_STARTED|deadline|budget)",
            "the total recovery deadline must be enforced after download and before restoration validation completes",
        )

        representative_access = restore.index('bash -Eeuo pipefail -c "$REPRESENTATIVE_ACCESS_CHECK_COMMAND"')
        self.assertIn(
            budget_name,
            restore[representative_access:],
            "the total recovery deadline must remain enforced through representative application access and final validation",
        )

    def test_better_stack_collector_target_requests_serialize_integer_ports_and_postgres_ssl_mode(self):
        """Protect Better Stack's documented integer port and PostgreSQL fields."""

        tasks = list(task_nodes(load_yaml("operations/ansible/site.yml")))
        target_tasks = [
            task
            for task in tasks
            if isinstance(module_payload(task, "ansible.builtin.uri", "uri"), dict)
            and "/api/v1/collectors/" in str(module_payload(task, "ansible.builtin.uri", "uri").get("url", ""))
            and "/targets" in str(module_payload(task, "ansible.builtin.uri", "uri").get("url", ""))
            and str(module_payload(task, "ansible.builtin.uri", "uri").get("method", "")).upper() in {"POST", "PATCH"}
        ]
        self.assertTrue(target_tasks, "Better Stack collector target requests must exist")

        group_vars = read("operations/ansible/group_vars/production.yml")
        port_variables = (
            "better_stack_proxy_target_port",
            "better_stack_backend_target_port",
            "better_stack_postgresql_target_port",
        )
        violations = []
        for variable in port_variables:
            declaration = re.search(rf"(?m)^\s*{re.escape(variable)}:\s*(.+)$", group_vars)
            if declaration is None or "| int" not in declaration.group(1):
                violations.append(f"{variable} must coerce the environment input to an integer")

        target_bodies = [
            module_payload(task, "ansible.builtin.uri", "uri").get("body", {})
            for task in target_tasks
        ]
        for body in target_bodies:
            if not isinstance(body, dict) or body.get("kind") not in {"prometheus", "postgres"}:
                continue
            port = body.get("port")
            if not isinstance(port, int) or isinstance(port, bool):
                violations.append(
                    f"{body.get('kind')} target port must serialize as an integer, observed {port!r}"
                )
            if body.get("kind") == "postgres" and body.get("ssl_mode") != "require":
                violations.append("PostgreSQL target must send the provider-supported ssl_mode=require field")

        self.assertEqual([], violations, "Better Stack collector target schema violations: " + "; ".join(violations))

    def test_better_stack_collector_targets_reconcile_provider_drift_with_official_patch_endpoint(self):
        """Require readback-driven PATCH reconciliation for target connection drift."""

        tasks = list(task_nodes(load_yaml("operations/ansible/site.yml")))
        target_uri_tasks = [
            task
            for task in tasks
            if isinstance(module_payload(task, "ansible.builtin.uri", "uri"), dict)
            and "/api/v1/collectors/" in str(module_payload(task, "ansible.builtin.uri", "uri").get("url", ""))
            and "/targets" in str(module_payload(task, "ansible.builtin.uri", "uri").get("url", ""))
        ]
        existing_readback = [
            task
            for task in target_uri_tasks
            if str(module_payload(task, "ansible.builtin.uri", "uri").get("method", "")).upper() == "GET"
            and "better_stack_existing_collector_targets" in str(task)
        ]
        self.assertTrue(existing_readback, "target reconciliation must read the provider's existing targets first")

        patch_tasks = [
            task
            for task in target_uri_tasks
            if str(module_payload(task, "ansible.builtin.uri", "uri").get("method", "")).upper() == "PATCH"
        ]
        violations = []
        if not patch_tasks:
            violations.append(
                "existing collector targets with wrong port, endpoint, or ssl_mode must be updated through the official PATCH endpoint"
            )
        else:
            patch_text = "\n".join(task_text(task) for task in patch_tasks)
            patch_urls = [
                str(module_payload(task, "ansible.builtin.uri", "uri").get("url", ""))
                for task in patch_tasks
            ]
            if not any("/targets/" in url and "item.id" in url for url in patch_urls):
                violations.append("target PATCH URLs must address the provider response's item.id")
            for field in ("port", "endpoint", "ssl_mode"):
                if field not in patch_text:
                    violations.append(f"target PATCH reconciliation must carry the declared {field} drift")
                if f"attributes.{field}" not in patch_text:
                    violations.append(f"target PATCH reconciliation must compare provider attributes.{field}")
            if not any(
                "better_stack_existing_collector_targets.json.data" in task_text(task)
                or "better_stack_collector_targets_readback.json.data" in task_text(task)
                for task in patch_tasks
            ):
                violations.append("target PATCH reconciliation must iterate provider readback targets")

        self.assertEqual([], violations, "Better Stack target reconciliation violations: " + "; ".join(violations))

    def test_better_stack_dashboard_alerts_read_back_declared_values_and_patch_provider_drift(self):
        """Require provider-side alert field verification and official PATCH drift repair."""

        tasks = list(task_nodes(load_yaml("operations/ansible/site.yml")))
        alert_uri_tasks = [
            task
            for task in tasks
            if isinstance(module_payload(task, "ansible.builtin.uri", "uri"), dict)
            and "/api/v2/dashboards/" in str(module_payload(task, "ansible.builtin.uri", "uri").get("url", ""))
            and "/charts/" in str(module_payload(task, "ansible.builtin.uri", "uri").get("url", ""))
            and "/alerts" in str(module_payload(task, "ansible.builtin.uri", "uri").get("url", ""))
        ]
        self.assertTrue(alert_uri_tasks, "Better Stack dashboard alert requests must exist")

        readback_index = next(
            (
                index
                for index, task in enumerate(tasks)
                if task.get("register") == "better_stack_dashboard_alerts_readback"
            ),
            None,
        )
        self.assertIsNotNone(readback_index, "dashboard alerts must be read back from Better Stack after reconciliation")
        monitor_index = next(
            (
                index
                for index, task in enumerate(tasks)
                if task.get("name") == "Read existing Better Stack monitors before reconciliation"
            ),
            len(tasks),
        )
        verification_text = "\n".join(
            task_text(task)
            for task in tasks[readback_index + 1 : monitor_index]
            if isinstance(module_payload(task, "ansible.builtin.assert", "assert"), dict)
        )
        violations = []
        for field in (
            "alert_type",
            "operator",
            "value",
            "check_period",
            "confirmation_period",
            "recovery_period",
            "email",
            "push",
            "incident_cause",
            "metadata",
        ):
            if f"attributes.{field}" not in verification_text:
                violations.append(f"dashboard alert readback must verify attributes.{field}")

        patch_tasks = [
            task
            for task in alert_uri_tasks
            if str(module_payload(task, "ansible.builtin.uri", "uri").get("method", "")).upper() == "PATCH"
        ]
        if not patch_tasks:
            violations.append("existing dashboard alerts with declared-value drift must be updated through the official PATCH endpoint")
        else:
            patch_text = "\n".join(task_text(task) for task in patch_tasks)
            patch_urls = [
                str(module_payload(task, "ansible.builtin.uri", "uri").get("url", ""))
                for task in patch_tasks
            ]
            if not any("/alerts/" in url and "item.id" in url for url in patch_urls):
                violations.append("dashboard alert PATCH URLs must address the provider response's item.id")
            for field in (
                "value",
                "operator",
                "check_period",
                "confirmation_period",
                "recovery_period",
                "email",
                "push",
                "metadata",
            ):
                if field not in patch_text:
                    violations.append(f"dashboard alert PATCH reconciliation must send {field}")
                if f"attributes.{field}" not in patch_text:
                    violations.append(f"dashboard alert PATCH reconciliation must compare attributes.{field}")
            patch_conditions = "\n".join(str(task.get("when", "")) for task in patch_tasks)
            for identity_field in ("attributes.name", "attributes.dashboard_id", "attributes.chart_id"):
                if identity_field not in patch_conditions:
                    violations.append(f"dashboard alert PATCH reconciliation must preserve identity using {identity_field}")

        self.assertEqual([], violations, "Better Stack dashboard alert reconciliation violations: " + "; ".join(violations))

    def test_better_stack_collector_replay_does_not_report_success_as_changed(self):
        """A successful replay of the official collector installer must be idempotent."""

        tasks = list(task_nodes(load_yaml("operations/ansible/site.yml")))
        install_index, install_task = next(
            (
                (index, task)
                for index, task in enumerate(tasks)
                if task.get("name") == "Run the official Better Stack collector Docker Compose deployment"
            ),
            (None, None),
        )
        self.assertIsNotNone(install_task, "the official Better Stack collector installer task must exist")

        changed_when = str(install_task.get("changed_when", "")).strip()
        violations = []
        if changed_when.casefold() == "better_stack_collector_install.rc == 0":
            violations.append("collector installation must not mark every successful replay changed solely because rc is zero")

        pre_install_state = any(
            "docker compose" in " ".join(command_argv(task)).casefold()
            and any(token in " ".join(command_argv(task)).casefold() for token in ("ps", "inspect", "config"))
            for task in tasks[:install_index]
        )
        output_sensitive_change = bool(
            re.search(r"(?i)(stdout|changed|created|updated|installed)", changed_when)
        )
        state_guard = bool(install_task.get("when")) and pre_install_state
        if not (state_guard or output_sensitive_change):
            violations.append("collector installation needs a replay guard or an output/state-aware changed_when")

        self.assertEqual([], violations, "Better Stack collector idempotency violations: " + "; ".join(violations))

    def test_backup_writer_policy_matches_s3_metadata_apis_used_by_the_backup_job(self):
        source = read("operations/recovery/backup/backup.sh")
        actions = {
            action
            for statement in render_writer_policy()["Statement"]
            for action in statement.get("Action", [])
        }
        required_by_api = {
            "head-object": "s3:GetObject",
            "get-object-tagging": "s3:GetObjectTagging",
            "get-object-attributes": "s3:GetObjectAttributes",
            "get-object-retention": "s3:GetObjectRetention",
        }
        missing = [
            permission
            for api, permission in required_by_api.items()
            if re.search(rf"aws\s+s3api\s+{re.escape(api)}\b", source)
            and permission not in actions
        ]
        self.assertEqual(
            [],
            missing,
            "backup metadata/tagging calls must be authorized by the least-privilege writer policy",
        )

    def test_machine_writer_key_rotation_materializes_root_owned_credentials(self):
        variables = load_yaml("operations/ansible/group_vars/production.yml")
        credentials_path = str(variables["backup_writer_credentials_file"])
        self.assertTrue(credentials_path.startswith("/etc/gam/"))

        site_tasks = list(task_nodes(load_yaml("operations/ansible/site.yml")))
        credential_install = [
            task
            for task in site_tasks
            if module_payload(task, "ansible.builtin.copy", "copy", "ansible.builtin.template", "template")
            and module_payload(task, "ansible.builtin.copy", "copy", "ansible.builtin.template", "template").get("dest")
            == "{{ backup_writer_credentials_file }}"
        ]
        self.assertTrue(credential_install)
        self.assertTrue(
            any(
                module_payload(task, "ansible.builtin.copy", "copy", "ansible.builtin.template", "template").get("owner") == "root"
                and str(module_payload(task, "ansible.builtin.copy", "copy", "ansible.builtin.template", "template").get("mode")) == "0600"
                for task in credential_install
            ),
            "the machine credential path must be root-owned and mode 0600",
        )

        aws_tasks = list(task_nodes(load_yaml("operations/ansible/aws-resources.yml")))
        self.assertTrue(
            any("create-access-key" in command_argv(task) and task.get("register") == "backup_writer_access_key" for task in aws_tasks),
            "machine writer access-key creation must be registered for operational rotation",
        )
        self.assertTrue(
            any(
                any(command in command_argv(task) for command in ("delete-access-key", "update-access-key"))
                for task in aws_tasks
            ),
            "rotation must retire or deactivate the previous machine access key",
        )
        self.assertTrue(
            any(
                "{{ backup_writer_access_key" in task_text(task)
                and "{{ backup_writer_credentials_file }}" in task_text(task)
                for task in aws_tasks + site_tasks
            ),
            "the created machine key must be materialized through the configured credential path",
        )

    def test_machine_writer_rotation_is_age_driven_exactly_one_active_and_bootstraps_clean_hosts(self):
        source = read("operations/ansible/aws-resources.yml")
        rotation_start = source.index("- name: List current machine backup-writer access keys before rotation")
        rotation_end = source.index("- name: Apply GAM cost-allocation tags", rotation_start)
        rotation_block = source[rotation_start:rotation_end]

        self.assertRegex(
            rotation_block,
            r"(?is)(?:CreateDate|created|age|date).{0,260}backup_access_key_rotation_days",
            "machine-key rotation must calculate age from IAM key metadata and the configured 90-day limit",
        )
        self.assertRegex(
            rotation_block,
            r"(?is)(?:exactly|count|length).{0,180}(?:active|Status)",
            "machine-key provisioning must enforce exactly one active key before activation",
        )

        create_start = source.index("- name: Create a machine backup-writer access key")
        create_end = source.index("- name: Materialize the active machine credential", create_start)
        create_task = source[create_start:create_end]
        self.assertNotRegex(
            create_task,
            r"(?is)when:.*GAM_ROTATE_BACKUP_WRITER_KEY",
            "machine-key creation must be reproducible on a clean host, not gated only by a manual toggle",
        )
        self.assertRegex(
            rotation_block,
            r"(?is)(?:AccessKeyId|SecretAccessKey).{0,180}(?:length|nonempty|test|assert)",
            "activation must reject empty machine credentials before writing the root-owned credential file",
        )

    def test_machine_writer_credentials_are_required_nonempty_and_rotation_enforced(self):
        site_tasks = list(task_nodes(load_yaml("operations/ansible/site.yml")))
        assert_payloads = [
            module_payload(task, "ansible.builtin.assert", "assert")
            for task in site_tasks
            if isinstance(module_payload(task, "ansible.builtin.assert", "assert"), dict)
        ]
        self.assertTrue(
            any(
                "GAM_BACKUP_ACCESS_KEY_ID" in json.dumps(payload)
                and "GAM_BACKUP_SECRET_ACCESS_KEY" in json.dumps(payload)
                for payload in assert_payloads
            ),
            "machine writer access-key inputs must be required nonempty deployment inputs",
        )

        variables = load_yaml("operations/ansible/group_vars/production.yml")
        self.assertGreaterEqual(int(variables["backup_access_key_rotation_days"]), 90)
        credentials_template = read("operations/ansible/templates/backup-writer.credentials.j2")
        self.assertIn("GAM_BACKUP_ACCESS_KEY_ID", credentials_template)
        self.assertIn("GAM_BACKUP_SECRET_ACCESS_KEY", credentials_template)

    def test_backup_uses_postgresql_18_client_or_checks_major_version_compatibility(self):
        site_tasks = list(task_nodes(load_yaml("operations/ansible/site.yml")))
        package_names = []
        for task in site_tasks:
            payload = module_payload(task, "ansible.builtin.package", "package")
            if isinstance(payload, dict):
                values = payload.get("name", [])
                package_names.extend(values if isinstance(values, list) else [values])

        source = read("operations/recovery/backup/backup.sh")
        explicit_client = any(str(name).startswith("postgresql-client-18") for name in package_names)
        compatibility_check = (
            re.search(r"(?is)pg_dump\s+--version", source)
            and re.search(r"(?i)(server_version|PG_VERSION)", source)
            and re.search(r"(?is)(incompatible|major.{0,80}mismatch|version.{0,80}exit\s+1)", source)
        )
        self.assertTrue(
            explicit_client or compatibility_check,
            "the backup must use a PostgreSQL 18 client or explicitly reject a major-version mismatch",
        )

    def test_overlap_retry_reconciles_existing_classified_objects_before_upload(self):
        source = read("operations/recovery/backup/backup.sh")
        upload_position = source.index("aws s3api put-object")
        before_upload = source[:upload_position]

        self.assertRegex(
            before_upload,
            r"(?is)list-objects-v2.*(?:LOCAL_DATE|date).*(?:CLASSIFICATION|classification)",
            "a retry must reconcile an already-uploaded classified object before choosing a new timestamp key",
        )

    def test_backup_persists_pending_classification_before_current_attempt_state(self):
        source = read("operations/recovery/backup/backup.sh")
        pending_start = source.index("# Pending markers make a missed Monday")
        pending_end = source.index("# One object is created when daily", pending_start)
        first_attempt_state_write = source.index('write_state "$LAST_ATTEMPT" "$LAST_SUCCESS"')

        self.assertGreater(
            first_attempt_state_write,
            pending_end,
            "pending weekly/monthly markers must be created before persisting the current attempt so a crash cannot lose catch-up state",
        )

    def test_existing_object_retry_revalidates_checksum_encryption_tags_attributes_and_retention(self):
        source = read("operations/recovery/backup/backup.sh")
        branch_start = source.index('if [[ -n "$EXISTING_CLASSIFIED_KEY" ]]')
        branch_end = source.index("echo \"validated existing immutable recovery point", branch_start)
        existing_branch = source[branch_start:branch_end]

        for api in ("get-object-attributes", "get-object-retention"):
            self.assertIn(
                api,
                existing_branch,
                f"same-date existing-object success must revalidate S3 {api} metadata before clearing state",
            )
        for required_field in (
            "Metadata.sha256",
            "Metadata.encrypted",
            "ServerSideEncryption",
            "Checksum.SHA256",
            "ObjectSize",
            "ObjectLockRetainUntilDate",
            "Project",
            "Environment",
            "Purpose",
        ):
            self.assertIn(
                required_field,
                existing_branch,
                f"same-date existing-object validation must enforce {required_field}, not only size and one classification tag",
            )

    def test_existing_object_retry_rejects_metadata_checksum_mismatch_with_remote_checksum(self):
        source = read("operations/recovery/backup/backup.sh")
        branch_start = source.index('if [[ -n "$EXISTING_CLASSIFIED_KEY" ]]')
        branch_end = source.index('echo "validated existing immutable recovery point', branch_start)
        existing_branch = source[branch_start:branch_end]

        self.assertRegex(
            existing_branch,
            r'EXISTING_METADATA_CHECKSUM=.*Metadata\.checksum',
            "same-date retry must read the checksum stored in object metadata",
        )
        self.assertRegex(
            existing_branch,
            r'EXISTING_METADATA_CHECKSUM.*EXISTING_ATTRIBUTE_CHECKSUM|EXISTING_ATTRIBUTE_CHECKSUM.*EXISTING_METADATA_CHECKSUM',
            "same-date retry must compare uploaded checksum metadata with the remote S3 checksum",
        )

    def test_backup_rejects_identical_developer_and_client_age_recipients(self):
        source = read("operations/recovery/backup/backup.sh")
        preflight_end = source.index("mkdir -p")
        preflight = source[:preflight_end]

        self.assertRegex(
            preflight,
            r'(?is)GAM_DEVELOPER_AGE_RECIPIENT.*GAM_CLIENT_AGE_RECIPIENT.*(?:==|=).*exit\s+1',
            "developer and client encryption recipients must be distinct before any artifact is produced",
        )

    def test_existing_backup_writer_policies_are_reconciled_to_least_privilege(self):
        tasks = list(task_nodes(load_yaml("operations/ansible/aws-resources.yml")))
        commands = {argument for task in tasks for argument in command_argv(task)}

        self.assertIn(
            "list-attached-user-policies",
            commands,
            "existing writer attached policies must be enumerated before least-privilege reconciliation",
        )
        self.assertIn(
            "list-user-policies",
            commands,
            "existing writer inline policies must be enumerated before least-privilege reconciliation",
        )
        self.assertTrue(
            {"detach-user-policy", "delete-user-policy"} & commands,
            "excess attached or inline writer policies must be removed or explicitly rejected",
        )

    def test_better_stack_availability_monitor_uses_provider_keyword_and_confirmation_contract(self):
        tasks = list(task_nodes(load_yaml("operations/ansible/site.yml")))
        active_monitors = []
        for task in tasks:
            payload = module_payload(task, "ansible.builtin.uri", "uri")
            if not isinstance(payload, dict) or not isinstance(payload.get("body"), dict):
                continue
            body = payload["body"]
            if body.get("pronounceable_name") == "GAM production availability":
                active_monitors.append((task, body))

        self.assertTrue(active_monitors, "the availability monitor must have an active provider registration")
        for task, body in active_monitors:
            self.assertEqual(
                '{"status":"UP"}',
                body.get("required_keyword"),
                f"availability monitor task {task.get('name', '<unnamed>')} must use the provider keyword",
            )
            self.assertEqual(
                600,
                body.get("confirmation_period"),
                f"availability monitor task {task.get('name', '<unnamed>')} must use a ten-minute confirmation period",
            )
            self.assertNotIn("expected_body", body)
            self.assertNotIn("expected_status_code", body)

    def test_disabled_existing_monitor_schedules_reach_state_reconciliation(self):
        tasks = list(task_nodes(load_yaml("operations/ansible/backup-monitor.yml")))
        schedule_names = (
            "gam-production-backup-monitor-0430",
            "gam-production-backup-monitor-1200",
        )
        for name in schedule_names:
            with self.subTest(schedule=name):
                get_task = next(
                    task
                    for task in tasks
                    if "get-schedule" in command_argv(task)
                    and argv_value(command_argv(task), "--name") == name
                )
                failed_when = str(get_task.get("failed_when", ""))
                self.assertNotIn(
                    "ENABLED",
                    failed_when.upper(),
                    "a disabled existing schedule must reach update reconciliation instead of failing first",
                )
                update_task = next(
                    (
                        task
                        for task in tasks
                        if "update-schedule" in command_argv(task)
                        and argv_value(command_argv(task), "--name") == name
                    ),
                    None,
                )
                self.assertIsNotNone(update_task, f"{name} needs a disabled-state reconciliation path")
                when_values = update_task.get("when", []) if update_task else []
                when_text = "\n".join(when_values) if isinstance(when_values, list) else str(when_values)
                self.assertIn(".State != 'ENABLED'", when_text)

    def test_monitor_inherits_weekly_classification_after_sunday_or_multiday_outage(self):
        scenarios = (
            (
                "missed Sunday before Monday",
                [datetime(2026, 6, 7, 4, 30, tzinfo=SAO_PAULO)],
                datetime(2026, 6, 8, 4, 30, tzinfo=SAO_PAULO),
            ),
            (
                "missed Monday and Tuesday",
                [
                    datetime(2026, 6, 8, 4, 30, tzinfo=SAO_PAULO),
                    datetime(2026, 6, 9, 4, 30, tzinfo=SAO_PAULO),
                ],
                datetime(2026, 6, 10, 4, 30, tzinfo=SAO_PAULO),
            ),
        )
        for label, outage_days, recovery_day in scenarios:
            with self.subTest(scenario=label):
                monitor = MonitorHarness()
                for outage_day in outage_days:
                    monitor.module._current_local_date = lambda outage_day=outage_day: outage_day
                    result = monitor.module.lambda_handler({"phase": "daily"}, None)
                    self.assertEqual("invalid", result["status"])

                monitor.add_object(recovery_day, "weekly", key_classification="weekly")
                monitor.module._current_local_date = lambda recovery_day=recovery_day: recovery_day
                valid, reasons, details = monitor.module._check_today()
                self.assertTrue(
                    valid,
                    f"{label} must preserve weekly inheritance through the next successful artifact: {reasons}",
                )
                self.assertEqual("weekly", details["classification"])

    def test_first_successful_month_after_persisted_outage_requires_monthly_classification(self):
        monitor = MonitorHarness()
        failed_day = datetime(2026, 6, 1, 4, 30, tzinfo=SAO_PAULO)
        monitor.module._current_local_date = lambda: failed_day
        first_result = monitor.module.lambda_handler({"phase": "daily"}, None)
        self.assertEqual("invalid", first_result["status"])

        catchup_day = datetime(2026, 6, 2, 4, 30, tzinfo=SAO_PAULO)
        monitor.add_object(catchup_day, "weekly", key_classification="weekly")
        monitor.module._current_local_date = lambda: catchup_day
        valid, reasons, _ = monitor.module._check_today()
        self.assertFalse(
            valid,
            f"the first successful artifact after a persisted month-start outage accepted weekly-only retention: {reasons}",
        )

    def test_monitor_validates_hex_sha256_when_s3_checksum_metadata_is_present(self):
        monitor = MonitorHarness()
        local_date = datetime(2026, 6, 3, 4, 30, tzinfo=SAO_PAULO)
        key = monitor.add_object(local_date, "daily", key_classification="daily")
        checksum = base64.b64encode(bytes(32)).decode("ascii")
        monitor.s3.attribute_checksums[key] = checksum
        monitor.s3.objects[key]["Metadata"]["checksum"] = checksum
        monitor.s3.objects[key]["Metadata"]["sha256"] = "b" * 64
        monitor.module._current_local_date = lambda: local_date

        valid, reasons, _ = monitor.module._check_today()
        self.assertFalse(
            valid,
            "a hex SHA-256 metadata mismatch must fail even when the base64 S3 checksum agrees",
        )
        self.assertIn("checksum", " ".join(reasons).casefold())

    def test_new_upload_revalidates_remote_tags_and_age_encryption_metadata(self):
        source = read("operations/recovery/backup/backup.sh")
        upload_start = source.index("aws s3api put-object")
        upload_end = source.index('if [[ "$CLASSIFICATION" == monthly', upload_start)
        post_upload = source[upload_start:upload_end]

        self.assertIn(
            "aws s3api get-object-tagging",
            post_upload,
            "new-upload success must verify remote object tags before clearing pending state",
        )
        self.assertRegex(
            post_upload,
            r"(?is)Metadata\.client-side-encryption.{0,120}(?:=|age)",
            "new-upload success must verify the remote age-encryption metadata",
        )
        self.assertRegex(
            post_upload,
            r"(?is)(?:Project|Environment|Purpose).{0,160}(?:GAM|production|backup)",
            "new-upload success must verify the required remote object tags",
        )

    def test_monitor_rejects_enabled_weekly_or_monthly_lifecycle_without_transitions(self):
        monitor = MonitorHarness()
        monday = datetime(2026, 6, 8, 4, 30, tzinfo=SAO_PAULO)
        monitor.add_object(
            monday,
            "weekly",
            key_classification="weekly",
            lifecycle=[
                {
                    "ID": "weekly-recovery-points",
                    "Status": "Enabled",
                    "Filter": {"Tag": {"Key": "classification", "Value": "weekly"}},
                    "Transitions": [],
                }
            ],
        )
        monitor.module._current_local_date = lambda: monday

        valid, reasons, _ = monitor.module._check_today()
        self.assertFalse(
            valid,
            f"an enabled weekly lifecycle rule without required transitions was accepted: {reasons}",
        )
        self.assertIn("lifecycle", " ".join(reasons).casefold())

    def test_restore_cleanup_fails_when_dropdb_cannot_remove_restored_database(self):
        source = read("operations/recovery/restore/restore.sh")
        cleanup_start = source.index("cleanup()")
        cleanup_end = source.index("trap cleanup EXIT", cleanup_start)
        cleanup = source[cleanup_start:cleanup_end]

        self.assertRegex(cleanup, r"(?im)^\s*dropdb\b")
        self.assertNotRegex(
            cleanup,
            r"(?is)dropdb.{0,180}\|\|\s*true",
            "cleanup must not suppress dropdb failure and report a successful restoration",
        )

    def test_better_stack_clean_host_executes_and_verifies_docker_compose_collector(self):
        tasks = list(task_nodes(load_yaml("operations/ansible/site.yml")))
        downloads = [
            task
            for task in tasks
            if isinstance(module_payload(task, "ansible.builtin.get_url", "get_url"), dict)
            and "better-stack" in task_text(task).casefold()
        ]
        self.assertTrue(downloads, "clean-host provisioning must download the supported collector installer")
        installer_dest = module_payload(downloads[0], "ansible.builtin.get_url", "get_url").get("dest")
        executions = [
            task
            for task in tasks
            if installer_dest
            and installer_dest in " ".join(command_argv(task))
        ]
        self.assertTrue(executions, "the downloaded Better Stack installer must be executed on a clean host")

        self.assertTrue(
            any(
                "COLLECTOR_SECRET" in task_text(task)
                and "better_stack_collector_secret" in task_text(task)
                for task in executions
            ),
            "the collector installer must receive the dedicated COLLECTOR_SECRET",
        )
        self.assertTrue(
            any("docker compose" in " ".join(command_argv(task)).casefold() for task in tasks),
            "clean-host provisioning must execute the supported Docker Compose deployment",
        )
        self.assertTrue(
            any("docker compose ps" in " ".join(command_argv(task)).casefold() for task in tasks),
            "clean-host provisioning must verify the Docker Compose collector status",
        )

    def test_writer_key_bootstrap_does_not_require_a_preexisting_machine_credential(self):
        plays = load_yaml("operations/ansible/site.yml")
        production_index = next(index for index, play in enumerate(plays) if play.get("hosts") == "production")
        localhost_index = next(index for index, play in enumerate(plays) if play.get("hosts") == "localhost")
        self.assertLess(
            localhost_index,
            production_index,
            "machine-key creation must be reachable before the production play installs its first credential",
        )

        production_play = plays[production_index]
        preflight = json.dumps(production_play.get("pre_tasks", []))
        self.assertNotIn("GAM_BACKUP_ACCESS_KEY_ID", preflight)
        self.assertNotIn("GAM_BACKUP_SECRET_ACCESS_KEY", preflight)

    def test_existing_cloudtrail_budget_and_anomaly_resources_are_reconciled(self):
        tasks = list(task_nodes(load_yaml("operations/ansible/aws-resources.yml")))
        commands = {argument for task in tasks for argument in command_argv(task)}

        self.assertTrue(
            {"describe-trails", "update-trail"}.issubset(commands)
            or {"get-trail", "update-trail"}.issubset(commands),
            "existing CloudTrail configuration must be read and reconciled, not accepted on already-exists",
        )
        self.assertTrue(
            {"describe-budget", "update-budget"}.issubset(commands),
            "existing budget thresholds and notifications must be read and reconciled",
        )
        self.assertTrue(
            {"get-anomaly-monitors", "update-anomaly-monitor"}.issubset(commands),
            "existing anomaly monitor dimensions must be read and reconciled",
        )
        self.assertIn(
            "update-anomaly-subscription",
            commands,
            "existing anomaly notification configuration must be reconciled",
        )

    def test_backup_budget_has_actual_or_forecast_notifications_at_five_and_ten_dollars(self):
        budget = json.loads(read("operations/ansible/templates/backup-budget.json.j2"))
        notifications = [
            item["Notification"]
            for item in budget["NotificationsWithSubscribers"]
        ]

        for threshold in (5, 10):
            self.assertTrue(
                any(
                    notification.get("Threshold") == threshold
                    and notification.get("NotificationType") in {"ACTUAL", "FORECASTED"}
                    for notification in notifications
                ),
                f"backup budget must notify on actual or forecasted spend at ${threshold}",
            )


class FakeS3:
    def __init__(self):
        self.objects: dict[str, dict] = {}
        self.tags: dict[str, list[dict[str, str]]] = {}
        self.lifecycle: list[dict] = []
        self.tag_calls = 0
        self.lifecycle_calls = 0
        self.raise_on_list = False
        self.attribute_checksums: dict[str, str] = {}

    def add_object(self, key: str, created: datetime, classification: str, key_classification: str, tags=None, lifecycle=None):
        self.objects[key] = {
            "Metadata": {
                "sha256": "a" * 64,
                "classification": classification,
                "client-side-encryption": "age",
                "encrypted": "true",
            },
            "ContentLength": 128,
            "ServerSideEncryption": "AES256",
            "ObjectLockMode": "COMPLIANCE",
            "ObjectLockRetainUntilDate": created + timedelta(days={"daily": 31, "weekly": 85, "monthly": 370}[classification]),
            "LastModified": created,
        }
        self.tags[key] = [
            {"Key": name, "Value": value}
            for name, value in (tags or {
                "Project": "GAM",
                "Environment": "production",
                "Purpose": "backup",
                "classification": classification,
            }).items()
        ]
        self.attribute_checksums[key] = "a" * 64
        if lifecycle is None:
            lifecycle_rule = {
                "ID": f"{classification}-recovery-points",
                "Status": "Enabled",
                "Filter": {"Tag": {"Key": "classification", "Value": classification}},
                "Expiration": {"Days": {"daily": 31, "weekly": 85, "monthly": 370}[classification]},
            }
            if classification == "weekly":
                lifecycle_rule["Transitions"] = [{"Days": 30, "StorageClass": "STANDARD_IA"}]
            elif classification == "monthly":
                lifecycle_rule["Transitions"] = [
                    {"Days": 30, "StorageClass": "STANDARD_IA"},
                    {"Days": 90, "StorageClass": "GLACIER"},
                ]
            self.lifecycle = [lifecycle_rule]
        else:
            self.lifecycle = lifecycle

    def list_objects_v2(self, Bucket, Prefix):
        if self.raise_on_list:
            raise RuntimeError("scheduler permission denied")
        return {"Contents": [{"Key": key} for key in self.objects if key.startswith(Prefix)]}

    def head_object(self, Bucket, Key):
        return self.objects[Key]

    def get_object_attributes(self, Bucket, Key, ObjectAttributes):
        return {
            "ObjectSize": self.objects[Key]["ContentLength"],
            "Checksum": {"SHA256": self.attribute_checksums.get(Key, "a" * 64)},
        }

    def get_object_tagging(self, Bucket, Key):
        self.tag_calls += 1
        return {"TagSet": self.tags[Key]}

    def get_bucket_lifecycle_configuration(self, Bucket):
        self.lifecycle_calls += 1
        return {"Rules": self.lifecycle}


class FakeSNS:
    def __init__(self):
        self.messages = []

    def publish(self, **kwargs):
        self.messages.append(kwargs)


class FakeStateTable:
    def __init__(self):
        self.items = {}

    def put_item(self, Item):
        self.items[Item["id"]] = Item

    def get_item(self, Key):
        return {"Item": self.items.get(Key["id"], {})}

    def delete_item(self, Key):
        self.items.pop(Key["id"], None)


class FakeDynamo:
    def __init__(self):
        self.table = FakeStateTable()

    def Table(self, name):
        return self.table


class MonitorHarness:
    def __init__(self):
        self.s3 = FakeS3()
        self.sns = FakeSNS()
        self.dynamo = FakeDynamo()
        self.original_boto3 = sys.modules.get("boto3")
        fake_boto3 = types.ModuleType("boto3")
        fake_boto3.client = lambda service, region_name=None: self.s3 if service == "s3" else self.sns
        fake_boto3.resource = lambda service, region_name=None: self.dynamo

        self.environment = {
            "BACKUP_TIMEZONE": "America/Sao_Paulo",
            "AWS_REGION": "sa-east-1",
            "BACKUP_BUCKET": "gam-test-backups",
            "BACKUP_PREFIX": "production/postgresql",
            "BACKUP_MONITOR_STATE_TABLE": "gam-test-monitor-state",
            "DEVELOPER_ALERT_TOPIC_ARN": "arn:aws:sns:sa-east-1:123:developer",
            "CLIENT_CUSTODIAN_ALERT_TOPIC_ARN": "arn:aws:sns:sa-east-1:123:client",
        }
        self.environment_patch = patch.dict(os.environ, self.environment)
        self.environment_patch.start()
        sys.modules["boto3"] = fake_boto3

        module_path = ANSIBLE / "backup_monitor.py"
        spec = importlib.util.spec_from_file_location("gam_backup_monitor_test_subject", module_path)
        self.module = importlib.util.module_from_spec(spec)
        sys.modules[spec.name] = self.module
        import zoneinfo

        original_zoneinfo = zoneinfo.ZoneInfo
        zoneinfo.ZoneInfo = lambda key: SAO_PAULO
        try:
            spec.loader.exec_module(self.module)
        finally:
            zoneinfo.ZoneInfo = original_zoneinfo

    def add_object(self, when: datetime, classification: str, key_classification: str, tags=None, lifecycle=None):
        key = f"production/postgresql/{when:%Y/%m/%d}/20260601T031500Z-{key_classification}.dump.age"
        self.s3.add_object(key, when, classification, key_classification, tags=tags, lifecycle=lifecycle)
        return key

    def __del__(self):
        try:
            self.environment_patch.stop()
        except Exception:
            pass
        if self.original_boto3 is None:
            sys.modules.pop("boto3", None)
        else:
            sys.modules["boto3"] = self.original_boto3


if __name__ == "__main__":
    unittest.main()
