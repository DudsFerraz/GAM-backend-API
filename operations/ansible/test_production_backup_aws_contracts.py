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
import shlex
import shutil
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


def bash_executable() -> str:
    """Resolve Bash from the active runner instead of a workstation path."""

    candidates = []
    git_executable = shutil.which("git")
    if git_executable:
        candidates.append(
            Path(git_executable).parent.parent
            / "bin"
            / ("bash.exe" if os.name == "nt" else "bash")
        )

    path_bash = shutil.which("bash")
    if path_bash:
        candidates.append(Path(path_bash))

    for candidate in candidates:
        if candidate.is_file():
            return str(candidate)

    raise AssertionError(
        "Bash is required for executable recovery-script contract validation"
    )


def recovery_lifecycle_rule(classification: str, *, valid: bool = True) -> dict:
    rule = {
        "ID": f"{classification}-recovery-points",
        "Status": "Enabled",
        "Filter": {"Tag": {"Key": "classification", "Value": classification}},
        "Expiration": {"Days": {"daily": 31, "weekly": 85, "monthly": 370}[classification]},
    }
    if classification in {"weekly", "monthly"}:
        rule["Transitions"] = (
            [{"Days": 30, "StorageClass": "STANDARD_IA"}]
            if valid
            else []
        )
    if classification == "monthly" and valid:
        rule["Transitions"].append({"Days": 90, "StorageClass": "GLACIER"})
    return rule


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


def is_durable_better_stack_collector_probe(task: dict) -> bool:
    """Recognize an installed-state probe that survives the official installer's temp directory."""

    argv = command_argv(task)
    lowered = [argument.casefold() for argument in argv]
    if lowered[:2] == ["docker", "ps"]:
        filters = [
            argv[index + 1]
            for index, argument in enumerate(argv[:-1])
            if argument == "--filter"
        ]
        return "label=com.docker.compose.project=better-stack-collector" in filters

    if lowered[:2] != ["docker", "compose"] or "ps" not in lowered:
        return False
    compose_file = argv_value(argv, "--file") or argv_value(argv, "-f")
    if not compose_file or compose_file.casefold().startswith(("/tmp/", "${tmpdir", "{{ tmp")):
        return False
    file_option = "--file" if "--file" in lowered else "-f"
    return lowered.index(file_option) < lowered.index("ps")


def is_official_better_stack_installer_execution(task: dict) -> bool:
    argv = command_argv(task)
    return any("better-stack-collector-install.sh" in argument for argument in argv)


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
    try:
        values = json.loads(target)
        if isinstance(values, dict):
            encoded_input = values.get("Input")
            if isinstance(encoded_input, str):
                try:
                    values["Input"] = json.loads(encoded_input)
                except json.JSONDecodeError:
                    values["Input"] = None
            return values
    except json.JSONDecodeError:
        pass

    target_without_retry = re.sub(r",RetryPolicy=\{[^{}]+\}", "", target)
    prefix, separator, encoded_input = target_without_retry.partition(",Input=")
    values = scheduler_key_values(prefix)
    if not separator:
        values["Input"] = None
        return values
    try:
        values["Input"] = json.loads(encoded_input)
    except json.JSONDecodeError:
        values["Input"] = None
    return values


def scheduler_target_retry_policy(task: dict) -> dict[str, str]:
    """Extract EventBridge Scheduler's nested Target.RetryPolicy value."""

    target = argv_value(command_argv(task), "--target")
    try:
        values = json.loads(target)
        retry_policy = values.get("RetryPolicy", {}) if isinstance(values, dict) else {}
        if isinstance(retry_policy, dict):
            return {str(key): str(value) for key, value in retry_policy.items()}
    except json.JSONDecodeError:
        pass

    match = re.search(r"(?:^|,)RetryPolicy=\{([^{}]+)\}(?:,|$)", target)
    return scheduler_key_values(match.group(1)) if match else {}


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

    def test_restore_isolation_blocks_docker_published_web_traffic_without_blocking_host_access(self):
        restore = read("operations/recovery/restore/restore.sh")
        compose = load_yaml("deploy/production/compose.yml")
        caddy_ports = compose["services"]["caddy"]["ports"]
        published_container_ports = {
            int(str(port).rsplit(":", 1)[-1].split("/", 1)[0])
            for port in caddy_ports
        }
        self.assertEqual({80, 443}, published_container_ports, "the topology seam must cover Caddy's public listeners")

        isolation_start = restore.index("export RESTORE_PUBLIC_INTERFACE")
        isolation_end = restore.index("umask 077", isolation_start)
        isolation_contract = restore[isolation_start:isolation_end]
        git_bash = bash_executable()

        with tempfile.TemporaryDirectory() as temporary_directory:
            temporary_root = Path(temporary_directory)

            def bash_path(path: Path) -> str:
                windows_posix = path.resolve().as_posix()
                if len(windows_posix) >= 3 and windows_posix[1:3] == ":/":
                    return f"/{windows_posix[0].lower()}{windows_posix[2:]}"
                return windows_posix

            fake_bin = temporary_root / "bin"
            fake_bin.mkdir()
            firewall_log = temporary_root / "firewall.log"
            for command in ("iptables", "ip6tables"):
                executable = fake_bin / command
                executable.write_text(
                    "#!/usr/bin/env bash\n"
                    'printf \'%s %s\\n\' "$(basename "$0")" "$*" >> "$FIREWALL_LOG"\n'
                    'if [[ "$1" == "-C" ]]; then exit 1; fi\n'
                    "exit 0\n",
                    encoding="utf-8",
                    newline="\n",
                )
                os.chmod(executable, 0o755)

            environment = os.environ.copy()
            environment.update(
                {
                    "FIREWALL_LOG": bash_path(firewall_log),
                    "PATH": f"{bash_path(fake_bin)}:{environment.get('PATH', '')}",
                    "RESTORE_PUBLIC_INTERFACE": "restore-public0",
                }
            )
            result = subprocess.run(
                [git_bash, "-Eeuo", "pipefail", "-c", isolation_contract],
                cwd=ROOT,
                env=environment,
                capture_output=True,
                text=True,
                timeout=30,
            )
            self.assertEqual(0, result.returncode, f"isolation contract did not execute: {result.stderr}")

            calls = [shlex.split(line) for line in firewall_log.read_text(encoding="utf-8").splitlines()]
            self.assertEqual(
                {"iptables", "ip6tables"},
                {call[0] for call in calls},
                "both IPv4 and IPv6 Docker forwarding paths must be isolated",
            )
            inserted_rules = [call for call in calls if len(call) > 2 and call[1] in {"-I", "-A"}]
            self.assertEqual(2, len(inserted_rules), "each address family must install one missing isolation rule")
            for call in inserted_rules:
                with self.subTest(command=call[0]):
                    arguments = call[1:]
                    self.assertEqual(
                        "DOCKER-USER",
                        arguments[1],
                        "Docker-published ports traverse forwarding; INPUT rules do not isolate the Caddy containers",
                    )
                    self.assertEqual("restore-public0", arguments[arguments.index("-i") + 1])
                    self.assertEqual("tcp", arguments[arguments.index("-p") + 1])
                    self.assertEqual(
                        published_container_ports,
                        {int(port) for port in arguments[arguments.index("--dports") + 1].split(",")},
                    )
                    self.assertEqual("DROP", arguments[arguments.index("-j") + 1])
                    self.assertNotIn("OUTPUT", arguments, "AWS downloads and host-originated recovery traffic must remain available")
                    self.assertNotIn("22", arguments, "operator SSH access must remain available during restoration")

    def test_restore_cleanup_continues_after_dropdb_failure_before_shredding(self):
        git_bash = bash_executable()

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
                [git_bash, "operations/recovery/restore/restore.sh"],
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

    def test_restore_success_becomes_failure_when_exit_trap_cleanup_fails(self):
        git_bash = bash_executable()

        restore = read("operations/recovery/restore/restore.sh")
        cleanup_start = restore.index("cleanup()")
        trap_line = "trap cleanup EXIT"
        cleanup_end = restore.index(trap_line, cleanup_start) + len(trap_line)
        cleanup_contract = restore[cleanup_start:cleanup_end]

        with tempfile.TemporaryDirectory() as temporary_directory:
            temporary_root = Path(temporary_directory)

            def bash_path(path: Path) -> str:
                windows_posix = path.resolve().as_posix()
                if len(windows_posix) >= 3 and windows_posix[1:3] == ":/":
                    return f"/{windows_posix[0].lower()}{windows_posix[2:]}"
                return windows_posix

            fake_bin = temporary_root / "bin"
            fake_state = temporary_root / "state"
            staging = temporary_root / "staging"
            fake_bin.mkdir()
            fake_state.mkdir()
            staging.mkdir()
            encrypted_archive = staging / "recovery.dump.age"
            encrypted_archive.write_text("encrypted", encoding="utf-8")
            evidence_file = temporary_root / "restoration-evidence.json"
            evidence_file.write_text(
                json.dumps({"plaintext_retention": {"temporary_plaintext_destroyed": True}}),
                encoding="utf-8",
            )

            dropdb = fake_bin / "dropdb"
            dropdb.write_text(
                "#!/usr/bin/env bash\n"
                'touch "$FAKE_STATE_DIR/dropdb-cleanup-failed"\n'
                "exit 42\n",
                encoding="utf-8",
                newline="\n",
            )
            os.chmod(dropdb, 0o755)
            shred = fake_bin / "shred"
            shred.write_text(
                "#!/usr/bin/env bash\n"
                'touch "$FAKE_STATE_DIR/shred-called"\n'
                "exit 0\n",
                encoding="utf-8",
                newline="\n",
            )
            os.chmod(shred, 0o755)

            harness = temporary_root / "cleanup-exit-harness.sh"
            harness.write_text(
                "#!/usr/bin/env bash\n"
                "set -Eeuo pipefail\n"
                f'STAGING_DIR="{bash_path(staging)}"\n'
                f'ENCRYPTED_ARCHIVE="{bash_path(encrypted_archive)}"\n'
                "RESTORED_DATABASE_CREATED=true\n"
                "CONTROLLED_PRODUCTION_RECOVERY=false\n"
                "RESTORE_ADMIN_DATABASE_URL=postgresql://isolated/postgres\n"
                "RESTORE_DATABASE_NAME=gam_restore\n"
                f'RESTORATION_EVIDENCE_FILE="{bash_path(evidence_file)}"\n'
                "export RESTORATION_EVIDENCE_FILE\n"
                f'FAKE_STATE_DIR="{bash_path(fake_state)}"\n'
                "export FAKE_STATE_DIR\n"
                f'PATH="{bash_path(fake_bin)}:$PATH"\n'
                "export PATH\n"
                f"{cleanup_contract}\n"
                "printf 'main-success\\n'\n",
                encoding="utf-8",
                newline="\n",
            )

            result = subprocess.run(
                [git_bash, str(harness)],
                cwd=ROOT,
                capture_output=True,
                text=True,
                timeout=30,
            )

            self.assertIn("main-success", result.stdout, "the main flow must complete successfully before cleanup")
            self.assertTrue(
                (fake_state / "dropdb-cleanup-failed").exists(),
                f"cleanup did not execute the failing dropdb seam; stdout={result.stdout!r}, stderr={result.stderr!r}",
            )
            self.assertTrue((fake_state / "shred-called").exists(), "cleanup must continue after dropdb failure")
            self.assertNotEqual(
                0,
                result.returncode,
                "a cleanup failure from the EXIT trap must override the prior successful main-flow status",
            )
            self.assertNotIn(
                "isolated restoration verified; universal sign-in is required",
                result.stdout,
                "cleanup failure must not emit the restoration success notice",
            )
            if evidence_file.exists():
                retained_evidence = json.loads(evidence_file.read_text(encoding="utf-8"))
                self.assertFalse(
                    retained_evidence.get("plaintext_retention", {}).get("temporary_plaintext_destroyed", True),
                    "failed EXIT cleanup must remove the evidence or record temporary_plaintext_destroyed=false",
                )

    def test_restore_cleanup_bounds_stalled_dropdb_then_destroys_plaintext_and_removes_isolation(self):
        git_bash = bash_executable()

        restore = read("operations/recovery/restore/restore.sh")
        cleanup_start = restore.index("cleanup()")
        trap_line = "trap cleanup EXIT"
        cleanup_end = restore.index(trap_line, cleanup_start) + len(trap_line)
        cleanup_contract = restore[cleanup_start:cleanup_end]
        cleanup_dropdb = re.search(r"(?m)^.*\bdropdb\s+--if-exists\b.*$", cleanup_contract)
        self.assertIsNotNone(cleanup_dropdb, "cleanup must remove the temporary restored database")

        function_blocks = {
            match.group("name"): match.group(0)
            for match in re.finditer(
                r"(?ms)^\s*(?P<name>[a-z_][a-z0-9_]*)\(\)\s*\{.*?^\}",
                restore,
            )
        }
        cleanup_timeout_wrappers = {
            name
            for name, body in function_blocks.items()
            if name != "run_with_recovery_deadline"
            and re.search(r"(?m)\btimeout\b", body)
            and re.search(r'(?:"\$@"|\$@)', body)
        }
        dropdb_line = cleanup_dropdb.group(0)
        dropdb_is_bounded = re.search(r"\btimeout\b", dropdb_line) or any(
            re.search(rf"\b{re.escape(wrapper)}\b.*\bdropdb\b", dropdb_line)
            for wrapper in cleanup_timeout_wrappers
        )
        self.assertTrue(
            dropdb_is_bounded,
            "cleanup dropdb must have its own interruptible timeout so stalled database cleanup cannot retain plaintext or isolation rules",
        )

        executable_contract = "\n".join(function_blocks.values()) + f"\n{trap_line}"
        with tempfile.TemporaryDirectory() as temporary_directory:
            temporary_root = Path(temporary_directory)

            def bash_path(path: Path) -> str:
                windows_posix = path.resolve().as_posix()
                if len(windows_posix) >= 3 and windows_posix[1:3] == ":/":
                    return f"/{windows_posix[0].lower()}{windows_posix[2:]}"
                return windows_posix

            fake_bin = temporary_root / "bin"
            fake_state = temporary_root / "state"
            staging = temporary_root / "staging"
            fake_bin.mkdir()
            fake_state.mkdir()
            staging.mkdir()
            encrypted_archive = staging / "recovery.dump.age"
            encrypted_archive.write_text("encrypted", encoding="utf-8")
            (staging / "database.dump").write_text("plaintext", encoding="utf-8")
            evidence_file = temporary_root / "restoration-evidence.json"
            evidence_file.write_text(
                json.dumps({"plaintext_retention": {"temporary_plaintext_destroyed": True}}),
                encoding="utf-8",
            )

            fake_commands = {
                "timeout": (
                    "#!/usr/bin/env bash\n"
                    'touch "$FAKE_STATE_DIR/cleanup-timeout-called"\n'
                    "exit 124\n"
                ),
                "dropdb": (
                    "#!/usr/bin/env bash\n"
                    'touch "$FAKE_STATE_DIR/unbounded-dropdb-called"\n'
                    "exit 99\n"
                ),
                "shred": (
                    "#!/usr/bin/env bash\n"
                    'touch "$FAKE_STATE_DIR/shred-called"\n'
                    "exit 0\n"
                ),
                "iptables": (
                    "#!/usr/bin/env bash\n"
                    'printf "%s\\n" "$*" >> "$FAKE_STATE_DIR/iptables-calls"\n'
                    "exit 0\n"
                ),
                "ip6tables": (
                    "#!/usr/bin/env bash\n"
                    'printf "%s\\n" "$*" >> "$FAKE_STATE_DIR/ip6tables-calls"\n'
                    "exit 0\n"
                ),
            }
            for command, contents in fake_commands.items():
                executable = fake_bin / command
                executable.write_text(contents, encoding="utf-8", newline="\n")
                os.chmod(executable, 0o755)

            harness = temporary_root / "stalled-cleanup-harness.sh"
            harness.write_text(
                "#!/usr/bin/env bash\n"
                "set -Eeuo pipefail\n"
                f'STAGING_DIR="{bash_path(staging)}"\n'
                f'ENCRYPTED_ARCHIVE="{bash_path(encrypted_archive)}"\n'
                "RESTORED_DATABASE_CREATED=true\n"
                "CONTROLLED_PRODUCTION_RECOVERY=false\n"
                "RESTORE_ADMIN_DATABASE_URL=postgresql://isolated/postgres\n"
                "RESTORE_DATABASE_NAME=gam_restore\n"
                "RESTORE_PUBLIC_INTERFACE=restore0\n"
                "RESTORE_IPV4_ISOLATION_ADDED=true\n"
                "RESTORE_IPV6_ISOLATION_ADDED=true\n"
                "RESTORE_CLEANUP_TIMEOUT_SECONDS=1\n"
                "CLEANUP_TIMEOUT_SECONDS=1\n"
                f'RESTORATION_EVIDENCE_FILE="{bash_path(evidence_file)}"\n'
                "export RESTORATION_EVIDENCE_FILE\n"
                f'FAKE_STATE_DIR="{bash_path(fake_state)}"\n'
                "export FAKE_STATE_DIR\n"
                f'PATH="{bash_path(fake_bin)}:$PATH"\n'
                "export PATH\n"
                f"{executable_contract}\n"
                "printf 'main-success\\n'\n",
                encoding="utf-8",
                newline="\n",
            )

            result = subprocess.run(
                [git_bash, str(harness)],
                cwd=ROOT,
                capture_output=True,
                text=True,
                timeout=10,
            )

            self.assertIn("main-success", result.stdout)
            self.assertNotEqual(0, result.returncode, "a timed-out cleanup operation must fail the run")
            self.assertTrue((fake_state / "cleanup-timeout-called").exists())
            self.assertFalse(
                (fake_state / "unbounded-dropdb-called").exists(),
                "the potentially stalled dropdb command must never execute outside the cleanup timeout",
            )
            self.assertTrue(
                (fake_state / "shred-called").exists(),
                "plaintext destruction must continue after timed-out database cleanup",
            )
            self.assertFalse(staging.exists(), "the temporary plaintext staging directory must be removed")
            for command in ("iptables", "ip6tables"):
                calls = (fake_state / f"{command}-calls").read_text(encoding="utf-8")
                self.assertIn("-D DOCKER-USER", calls, f"{command} isolation must be removed after cleanup timeout")
            self.assertFalse(
                evidence_file.exists(),
                "success evidence must not survive a timed-out cleanup that invalidates plaintext-destruction claims",
            )

    def test_restore_finalizes_success_evidence_atomically_only_after_exit_cleanup_succeeds(self):
        git_bash = bash_executable()

        restore = read("operations/recovery/restore/restore.sh")
        verification = read("operations/recovery/verify-restoration/verify-restoration.sh")
        pending_variable = "RESTORATION_EVIDENCE_PENDING_FILE"
        violations = []
        if pending_variable not in restore or pending_variable not in verification:
            violations.append("restore and verifier must share an explicit pending-evidence path")
        if not re.search(
            rf'(?m)>\s*"\${re.escape(pending_variable)}"',
            verification,
        ):
            violations.append("the verifier must write only the pending evidence artifact")
        if re.search(r'(?m)>\s*"\$RESTORATION_EVIDENCE_FILE"', verification):
            violations.append("the verifier must not publish final success evidence before EXIT cleanup")

        cleanup_start = restore.index("cleanup()")
        cleanup_end = restore.index("trap cleanup EXIT", cleanup_start)
        cleanup = restore[cleanup_start:cleanup_end]
        success_notice = "isolated restoration verified; universal sign-in is required"
        finalization = re.search(
            rf'(?is)\bmv\b.{{0,200}}"\${re.escape(pending_variable)}".{{0,200}}"\$RESTORATION_EVIDENCE_FILE"',
            cleanup,
        )
        if finalization is None:
            violations.append("successful cleanup must atomically rename pending evidence to the final evidence path")
        else:
            isolation_removal = cleanup.find("remove_restore_isolation")
            if isolation_removal < 0 or finalization.start() < isolation_removal:
                violations.append("evidence finalization must occur only after isolation removal")
            success_guard = cleanup[: finalization.start()]
            if not re.search(
                r"(?is)(?:if|elif)\s+\(\(\s*cleanup_status\s*==\s*0\s*\)\).*?then",
                success_guard[-500:],
            ):
                violations.append("evidence finalization must be guarded by complete cleanup success")
            success_notice_index = cleanup.find(success_notice)
            if success_notice_index < 0:
                violations.append("the restoration success notice must be emitted by successful EXIT cleanup")
            elif success_notice_index < finalization.end():
                violations.append("the restoration success notice must follow atomic evidence finalization")
        if not re.search(
            rf'(?is)(?:cleanup_status\s*!=\s*0|\belse\b).{{0,500}}rm\s+-f\b.{{0,200}}\${re.escape(pending_variable)}',
            cleanup,
        ):
            violations.append("failed cleanup must remove pending evidence rather than leave a success candidate")

        self.assertEqual([], violations, "restoration evidence publication violations: " + "; ".join(violations))

        function_blocks = "\n".join(
            match.group(0)
            for match in re.finditer(
                r"(?ms)^\s*(?P<name>[a-z_][a-z0-9_]*)\(\)\s*\{.*?^\}",
                restore,
            )
        )
        with tempfile.TemporaryDirectory() as temporary_directory:
            temporary_root = Path(temporary_directory)

            def bash_path(path: Path) -> str:
                windows_posix = path.resolve().as_posix()
                if len(windows_posix) >= 3 and windows_posix[1:3] == ":/":
                    return f"/{windows_posix[0].lower()}{windows_posix[2:]}"
                return windows_posix

            fake_bin = temporary_root / "bin"
            fake_state = temporary_root / "state"
            staging = temporary_root / "staging"
            fake_bin.mkdir()
            fake_state.mkdir()
            staging.mkdir()
            encrypted_archive = staging / "recovery.dump.age"
            encrypted_archive.write_text("encrypted", encoding="utf-8")
            (staging / "database.dump").write_text("plaintext", encoding="utf-8")
            pending_evidence = temporary_root / "evidence.pending.json"
            final_evidence = temporary_root / "evidence.json"
            pending_evidence.write_text(
                json.dumps({"plaintext_retention": {"temporary_plaintext_destroyed": True}}),
                encoding="utf-8",
            )

            fake_commands = {
                "dropdb": "#!/usr/bin/env bash\nexit 0\n",
                "shred": "#!/usr/bin/env bash\nexit 0\n",
                "iptables": "#!/usr/bin/env bash\nexit 0\n",
                "ip6tables": "#!/usr/bin/env bash\nexit 0\n",
            }
            for command, contents in fake_commands.items():
                executable = fake_bin / command
                executable.write_text(contents, encoding="utf-8", newline="\n")
                os.chmod(executable, 0o755)

            harness = temporary_root / "evidence-finalization-harness.sh"
            harness.write_text(
                "#!/usr/bin/env bash\n"
                "set -Eeuo pipefail\n"
                f'STAGING_DIR="{bash_path(staging)}"\n'
                f'ENCRYPTED_ARCHIVE="{bash_path(encrypted_archive)}"\n'
                "RESTORED_DATABASE_CREATED=true\n"
                "CONTROLLED_PRODUCTION_RECOVERY=false\n"
                "RESTORE_ADMIN_DATABASE_URL=postgresql://isolated/postgres\n"
                "RESTORE_DATABASE_NAME=gam_restore\n"
                "RESTORE_PUBLIC_INTERFACE=restore0\n"
                "RESTORE_IPV4_ISOLATION_ADDED=true\n"
                "RESTORE_IPV6_ISOLATION_ADDED=true\n"
                "RESTORE_CLEANUP_TIMEOUT_SECONDS=2\n"
                f'{pending_variable}="{bash_path(pending_evidence)}"\n'
                f'RESTORATION_EVIDENCE_FILE="{bash_path(final_evidence)}"\n'
                f'PATH="{bash_path(fake_bin)}:$PATH"\n'
                "export PATH\n"
                f"{function_blocks}\n"
                "trap cleanup EXIT\n"
                f'test -f "${pending_variable}"\n'
                'test ! -e "$RESTORATION_EVIDENCE_FILE"\n'
                "printf 'main-complete-with-evidence-still-pending\\n'\n",
                encoding="utf-8",
                newline="\n",
            )

            result = subprocess.run(
                [git_bash, str(harness)],
                cwd=ROOT,
                capture_output=True,
                text=True,
                timeout=15,
            )
            self.assertEqual(0, result.returncode, result.stderr)
            self.assertIn("main-complete-with-evidence-still-pending", result.stdout)
            self.assertIn(
                success_notice,
                result.stdout,
                "successful cleanup must emit the notice only after publishing final evidence",
            )
            self.assertFalse(pending_evidence.exists(), "successful EXIT cleanup must consume pending evidence")
            self.assertTrue(final_evidence.is_file(), "successful EXIT cleanup must publish final evidence")
            finalized = json.loads(final_evidence.read_text(encoding="utf-8"))
            self.assertTrue(finalized["plaintext_retention"]["temporary_plaintext_destroyed"])

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
        self.assertRegex(
            before_evidence_write,
            r"(?is)FLYWAY_COUNT=.*?SELECT\s+count\(\*\)\s+FROM\s+(?:public\.)?flyway_schema_history",
            "Flyway readiness must count applied migration rows, not merely the history table's existence",
        )
        flyway_query_start = before_evidence_write.index("FLYWAY_COUNT=")
        flyway_query_end = before_evidence_write.index("STRUCTURAL_RESULT=", flyway_query_start)
        self.assertNotIn(
            "information_schema.tables",
            before_evidence_write[flyway_query_start:flyway_query_end],
            "the Flyway count must not report one merely because flyway_schema_history exists",
        )
        flyway_guard_patterns = (
            r"(?m)^\s*test\s+[\"']?\$FLYWAY_COUNT[\"']?\s+-gt\s+0",
            r"(?m)^\s*\[\[\s*[\"']?\$FLYWAY_COUNT[\"']?\s+-gt\s+0",
            r"(?m)^\s*if\s+\(\(\s*FLYWAY_COUNT\s*(?:<=|==)\s*0",
        )
        self.assertTrue(
            any(re.search(pattern, before_evidence_write) for pattern in flyway_guard_patterns),
            "restoration verification must reject an empty Flyway history before writing success evidence",
        )

    def test_restore_validates_manifest_schema_provenance_and_package_integrity_before_pg_restore(self):
        restore = read("operations/recovery/restore/restore.sh")
        manifest_start = restore.index('test -s "$RESTORED_FILES/manifest.json"')
        pg_restore_start = restore.index("pg_restore --list", manifest_start)
        validation = restore[manifest_start:pg_restore_start]

        self.assertIn(
            "gam-recovery-manifest/v1",
            validation,
            "restore must reject recovery packages with an unknown manifest schema",
        )
        self.assertRegex(
            validation,
            r"(?m)\bjq\s+-e\b",
            "manifest validation must fail closed when its schema or required fields are invalid",
        )
        for field in (
            "schema_version",
            "created_at",
            "postgresql_version",
            "classification",
            "object_key",
            "source_commit",
            "backend_image_digest",
            "frontend_release",
            "frontend_archive",
            "frontend_sha256",
            "migration_state",
            "dump_size_bytes",
            "roles_size_bytes",
            "archive_size_bytes",
            "archive_sha256",
            "roles_sha256",
            "refresh_token_data",
            "encryption_scheme",
            "data_boundary",
        ):
            with self.subTest(manifest_field=field):
                self.assertRegex(
                    validation,
                    rf"(?m)\.({re.escape(field)})\b",
                    f"restore must validate manifest {field} before accepting the package",
                )

        for size_field in ("dump_size_bytes", "roles_size_bytes", "archive_size_bytes"):
            with self.subTest(numeric_nonnegative_manifest_size=size_field):
                self.assertRegex(
                    validation,
                    rf'(?is)(?:\.{size_field}.{{0,180}}type\s*==\s*["\']number["\']|'
                    rf'type\s*==\s*["\']number["\'].{{0,180}}\.{size_field})',
                    f"manifest {size_field} must be numeric",
                )
                self.assertRegex(
                    validation,
                    rf"(?is)\.{size_field}.{{0,180}}(?:>=\s*0|<\s*0|nonnegative|non-negative)",
                    f"manifest {size_field} must be nonnegative",
                )

        self.assertRegex(
            validation,
            r"(?is)\.object_key.{0,180}RECOVERY_OBJECT_KEY|RECOVERY_OBJECT_KEY.{0,180}\.object_key",
            "the selected S3 object key must agree with the manifest provenance",
        )
        for filename, manifest_field in (
            ("database.dump", "archive_sha256"),
            ("database-roles.sql", "roles_sha256"),
        ):
            with self.subTest(package_member=filename):
                self.assertRegex(
                    validation,
                    rf"(?is)(?:sha256sum.{{0,160}}{re.escape(filename)}.{{0,240}}\.{manifest_field}|"
                    rf"\.{manifest_field}.{{0,240}}sha256sum.{{0,160}}{re.escape(filename)})",
                    f"restore must compare {filename} bytes with manifest {manifest_field}",
                )

        for filename, manifest_field in (
            ("database.dump", "dump_size_bytes"),
            ("database-roles.sql", "roles_size_bytes"),
        ):
            with self.subTest(package_member_size=filename):
                self.assertRegex(
                    validation,
                    rf"(?is)(?:stat\b.{{0,160}}{re.escape(filename)}.{{0,300}}\.{manifest_field}|"
                    rf"\.{manifest_field}.{{0,300}}stat\b.{{0,160}}{re.escape(filename)})",
                    f"restore must compare {filename} byte length with manifest {manifest_field}",
                )
        self.assertRegex(
            validation,
            r"(?is)(?:dump_size_bytes.{0,300}\+.{0,300}roles_size_bytes.{0,300}archive_size_bytes|"
            r"archive_size_bytes.{0,300}dump_size_bytes.{0,300}\+.{0,300}roles_size_bytes)",
            "restore must reconcile archive_size_bytes with the dump-plus-roles size generated by backup.sh",
        )

        verification = read("operations/recovery/verify-restoration/verify-restoration.sh")
        migration_validation = restore[manifest_start:] + "\n" + verification
        self.assertRegex(
            migration_validation,
            r"(?is)(?:manifest[^\n]*migration_state|MANIFEST_MIGRATION_STATE|\.migration_state)"
            r".{0,1200}flyway_schema_history|flyway_schema_history.{0,1200}"
            r"(?:manifest[^\n]*migration_state|MANIFEST_MIGRATION_STATE|\.migration_state)",
            "the restored Flyway history must be reconciled with the migration state declared by the manifest",
        )

    def test_restore_verifies_manifest_postgresql_major_and_records_only_verified_evidence(self):
        restore = read("operations/recovery/restore/restore.sh")
        verification = read("operations/recovery/verify-restoration/verify-restoration.sh")
        manifest_start = restore.index('test -s "$RESTORED_FILES/manifest.json"')
        first_restore = restore.index("pg_restore --list", manifest_start)
        pre_restore_validation = restore[manifest_start:first_restore]

        for variable in (
            "MANIFEST_POSTGRESQL_VERSION",
            "TARGET_POSTGRESQL_VERSION",
            "MANIFEST_POSTGRESQL_MAJOR_VERSION",
            "TARGET_POSTGRESQL_MAJOR_VERSION",
        ):
            with self.subTest(version_signal=variable):
                self.assertIn(
                    variable,
                    pre_restore_validation,
                    f"restore must derive {variable} before accepting the archive",
                )
        self.assertRegex(
            pre_restore_validation,
            r"(?is)MANIFEST_POSTGRESQL_VERSION=.*?\.postgresql_version",
            "the manifest PostgreSQL version must come from the selected recovery package",
        )
        self.assertRegex(
            pre_restore_validation,
            r"(?is)TARGET_POSTGRESQL_VERSION=.*?psql\b.*?SHOW\s+server_version",
            "the restoration target PostgreSQL version must be queried from the isolated target",
        )
        for major_variable in ("MANIFEST_POSTGRESQL_MAJOR_VERSION", "TARGET_POSTGRESQL_MAJOR_VERSION"):
            with self.subTest(valid_numeric_major=major_variable):
                self.assertRegex(
                    pre_restore_validation,
                    rf'(?is){major_variable}.{{0,240}}\^\[0-9\]\+\$|\^\[0-9\]\+\$.{{0,240}}{major_variable}',
                    f"restore must reject an invalid {major_variable}",
                )
        self.assertRegex(
            pre_restore_validation,
            r"(?is)(?:test\s+.*\$MANIFEST_POSTGRESQL_MAJOR_VERSION.*=.*\$TARGET_POSTGRESQL_MAJOR_VERSION|"
            r"\[\[.*\$MANIFEST_POSTGRESQL_MAJOR_VERSION.*(?:==|!=).*\$TARGET_POSTGRESQL_MAJOR_VERSION)",
            "restore must reject a manifest whose PostgreSQL major version differs from the restoration target",
        )

        verifier_call = restore[restore.index("RESTORATION_DURATION_SECONDS=") :]
        for variable in (
            "MANIFEST_POSTGRESQL_VERSION",
            "TARGET_POSTGRESQL_VERSION",
            "POSTGRESQL_MAJOR_VERSION_CHECKED",
        ):
            with self.subTest(exported_verification_signal=variable):
                self.assertRegex(
                    verifier_call,
                    rf"(?m)^\s*export\s+(?:[^\n]*\s)?{variable}(?:\s|$)",
                    f"restore must pass {variable} to the evidence verifier",
                )
                self.assertRegex(
                    verification,
                    rf'(?m)^\s*:?\s*"?\$\{{{variable}:\?[^}}]+\}}"?',
                    f"the evidence verifier must require {variable}",
                )

        before_evidence = verification[: verification.index("mkdir -p")]
        self.assertRegex(
            before_evidence,
            r"(?is)(?:test\s+.*\$POSTGRESQL_MAJOR_VERSION_CHECKED.*=\s*(?:true|\"true\")|"
            r"\[\[.*\$POSTGRESQL_MAJOR_VERSION_CHECKED.*(?:==|!=)\s*(?:true|\"true\"))",
            "the evidence verifier must reject an unverified PostgreSQL major-version result",
        )
        self.assertNotRegex(
            verification,
            r"major_version_checked\s*:\s*true\b",
            "evidence must not unconditionally claim that the PostgreSQL major version was checked",
        )
        evidence_program = verification[verification.index("jq -n") :]
        self.assertRegex(
            evidence_program,
            r"(?is)major_version_checked\s*:\s*[^,}\n]*\$[a-z_]*major[a-z_]*checked",
            "the recorded major-version result must be derived from the verified input",
        )
        self.assertRegex(
            evidence_program,
            r"(?is)postgresql\s*:\s*\{[^}]*manifest[^}]*version[^}]*\$[a-z_]*manifest[a-z_]*version",
            "evidence must identify the manifest PostgreSQL version that was compared",
        )
        self.assertRegex(
            evidence_program,
            r"(?is)postgresql\s*:\s*\{[^}]*target[^}]*version[^}]*\$[a-z_]*target[a-z_]*version",
            "evidence must identify the restoration target PostgreSQL version",
        )

    def test_preproduction_restoration_reason_is_explicit_and_recordable(self):
        restore = read("operations/recovery/restore/restore.sh")
        verification = read("operations/recovery/verify-restoration/verify-restoration.sh")

        self.assertRegex(
            verification,
            r"(?s)case\s+\"\$RESTORATION_REASON\"\s+in.*\bpre-production\b",
            "the mandatory pre-production restoration must be accepted as an evidence reason",
        )
        self.assertRegex(
            restore,
            r'(?m)^\s*:\s*"\$\{RESTORATION_REASON:\?[^}]+\}"',
            "restore callers must identify the restoration trigger explicitly",
        )
        self.assertNotRegex(
            restore,
            r'RESTORATION_REASON="\$\{RESTORATION_REASON:-annual\}"',
            "restore must not misrecord an unspecified pre-production run as annual",
        )

    def test_disaster_recovery_reason_passes_restoration_evidence_reason_gate(self):
        verification = read("operations/recovery/verify-restoration/verify-restoration.sh")
        reason_gate_start = verification.index('case "$RESTORATION_REASON" in')
        reason_gate_end = verification.index("POSTGRESQL_VERSION=", reason_gate_start)
        reason_gate = verification[reason_gate_start:reason_gate_end]
        git_bash = bash_executable()

        result = subprocess.run(
            [
                git_bash,
                "-Eeuo",
                "pipefail",
                "-c",
                "RESTORATION_REASON=disaster-recovery\n" + reason_gate + "\nprintf 'reason-accepted\\n'\n",
            ],
            cwd=ROOT,
            capture_output=True,
            text=True,
            timeout=30,
        )

        self.assertEqual(
            0,
            result.returncode,
            "controlled production disaster recovery must pass the evidence reason gate before database validation: "
            + result.stderr,
        )
        self.assertIn("reason-accepted", result.stdout)

    def test_backend_health_contract_is_unauthenticated_and_returns_exact_up_body(self):
        java_sources = [
            path.read_text(encoding="utf-8")
            for path in (ROOT / "src" / "main" / "java").rglob("*.java")
        ]
        health_routes = [
            source
            for source in java_sources
            if re.search(r'@RequestMapping\(\s*["\']/health["\']', source)
            and re.search(r"@GetMapping\b", source)
        ]
        self.assertTrue(
            health_routes,
            "the backend must expose GET /health after the proxy removes exactly one public /api prefix",
        )
        self.assertFalse(
            any(re.search(r'@RequestMapping\(\s*["\']/api/health["\']', source) for source in java_sources),
            "REQ-WEB-014 forbids a duplicate backend /api/health controller alias",
        )
        normalized_health_sources = re.sub(r"\s+", "", "".join(health_routes))
        self.assertIn(
            '{"status":"UP"}',
            normalized_health_sources.replace('\\"', '"'),
            "the backend readiness route must return the exact healthy response body forwarded publicly",
        )

        security = re.sub(r"\s+", "", read("src/main/java/br/org/gam/api/security/SecurityConfig.java"))
        self.assertIn(
            '.requestMatchers(HttpMethod.GET,"/health").permitAll()',
            security,
            "backend GET /health must be explicitly unauthenticated for the public proxy route",
        )
        self.assertNotIn(
            '.requestMatchers(HttpMethod.GET,"/api/health").permitAll()',
            security,
            "security configuration must not preserve the removed backend /api/health alias",
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
        monitor.add_prior_monthly_evidence(failed_monday)
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
        boundary_monitor = MonitorHarness()
        wrongly_daily_boundary = datetime(2026, 6, 2, 4, 30, tzinfo=SAO_PAULO)
        boundary_monitor.add_object(wrongly_daily_boundary, "daily", key_classification="daily")
        boundary_monitor.module._current_local_date = lambda: wrongly_daily_boundary
        boundary_valid, boundary_reasons, _ = boundary_monitor.module._check_today()
        self.assertFalse(
            boundary_valid,
            f"the first successful artifact of the month was accepted as daily: {boundary_reasons}",
        )
        self.assertIn("classification", " ".join(boundary_reasons).casefold())

        no_history_monitor = MonitorHarness()
        prior_day = datetime(2026, 6, 10, 4, 30, tzinfo=SAO_PAULO)
        no_history_monitor.add_object(prior_day, "daily", key_classification="daily")
        no_history_monitor.module._current_local_date = lambda: prior_day
        prior_valid, prior_reasons, _ = no_history_monitor.module._check_today()
        self.assertFalse(
            prior_valid,
            f"a fresh daily artifact without monthly or weekly boundary evidence was accepted: {prior_reasons}",
        )
        self.assertIn("classification", " ".join(prior_reasons).casefold())

        history_monitor = MonitorHarness()
        monthly_boundary = datetime(2026, 6, 1, 4, 30, tzinfo=SAO_PAULO)
        history_monitor.add_object(monthly_boundary, "monthly", key_classification="monthly")
        weekly_boundary = datetime(2026, 6, 8, 4, 30, tzinfo=SAO_PAULO)
        history_monitor.add_object(weekly_boundary, "weekly", key_classification="weekly")
        history_monitor.add_object(prior_day, "daily", key_classification="daily")
        history_monitor.module._current_local_date = lambda: prior_day
        prior_valid, prior_reasons, _ = history_monitor.module._check_today()
        self.assertTrue(
            prior_valid,
            f"the daily recovery point was rejected after immutable evidence satisfied both boundaries: {prior_reasons}",
        )

        next_day = datetime(2026, 6, 11, 4, 30, tzinfo=SAO_PAULO)
        history_monitor.add_object(next_day, "monthly", key_classification="monthly")
        history_monitor.module._current_local_date = lambda: next_day
        valid, reasons, _ = history_monitor.module._check_today()
        self.assertFalse(
            valid,
            f"monthly classification after an already-successful daily was accepted without required classification history: {reasons}",
        )
        self.assertIn("classification", " ".join(reasons).casefold())

    def test_monitor_accepts_normal_midmonth_monday_weekly_artifact(self):
        monitor = MonitorHarness()
        monday = datetime(2026, 6, 8, 4, 30, tzinfo=SAO_PAULO)
        monitor.add_prior_monthly_evidence(monday)
        monitor.add_object(monday, "weekly", key_classification="weekly")
        monitor.module._current_local_date = lambda: monday

        valid, reasons, details = monitor.module._check_today()

        self.assertTrue(valid, f"a normal Monday weekly artifact was rejected: {reasons}")
        self.assertEqual("weekly", details["classification"])

    def test_monitor_rejects_weekly_and_monthly_when_earlier_immutable_evidence_satisfied_the_boundary(self):
        cases = (
            (
                "weekly boundary already satisfied",
                datetime(2026, 6, 8, 4, 30, tzinfo=SAO_PAULO),
                "weekly",
                datetime(2026, 6, 10, 4, 30, tzinfo=SAO_PAULO),
                "weekly",
            ),
            (
                "monthly boundary already satisfied",
                datetime(2026, 6, 1, 4, 30, tzinfo=SAO_PAULO),
                "monthly",
                datetime(2026, 6, 10, 4, 30, tzinfo=SAO_PAULO),
                "monthly",
            ),
        )
        for label, earlier_date, earlier_classification, current_date, current_classification in cases:
            with self.subTest(scenario=label):
                monitor = MonitorHarness()
                earlier_key = monitor.add_object(
                    earlier_date,
                    earlier_classification,
                    key_classification=earlier_classification,
                )
                current_key = monitor.add_object(
                    current_date,
                    current_classification,
                    key_classification=current_classification,
                )
                self.assertEqual(
                    {},
                    monitor.dynamo.table.items,
                    "immutable object evidence, not monitor history, must prove the duplicate classification is invalid",
                )
                self.assertEqual(
                    {earlier_key, current_key},
                    set(monitor.s3.objects),
                    "the fixture must contain exactly one earlier boundary point and the later duplicate",
                )

                monitor.module._current_local_date = lambda current_date=current_date: current_date
                result = monitor.module.lambda_handler({"phase": "daily"}, None)

                self.assertEqual(
                    "invalid",
                    result["status"],
                    f"{label} allowed a later object to duplicate the already-satisfied classification",
                )
                self.assertIn("classification", " ".join(result.get("reasons", [])).casefold())

    def test_weekly_catchup_ignores_wrongly_daily_monday_boundary_object(self):
        monitor = MonitorHarness()
        monday = datetime(2026, 6, 8, 4, 30, tzinfo=SAO_PAULO)
        monthly_key = monitor.add_prior_monthly_evidence(monday)
        monday_key = monitor.add_object(monday, "daily", key_classification="daily")
        tuesday = datetime(2026, 6, 9, 4, 30, tzinfo=SAO_PAULO)
        weekly_key = monitor.add_object(tuesday, "weekly", key_classification="weekly")
        self.assertEqual({}, monitor.dynamo.table.items)
        self.assertEqual({monthly_key, monday_key, weekly_key}, set(monitor.s3.objects))

        monitor.module._current_local_date = lambda: tuesday
        result = monitor.module.lambda_handler({"phase": "daily"}, None)

        self.assertEqual(
            "ok",
            result["status"],
            "a wrongly daily Monday object must not satisfy the weekly boundary or block Tuesday's correct catch-up",
        )
        self.assertEqual(weekly_key, result["recovery_point"])

    def test_monthly_catchup_ignores_wrongly_daily_first_day_object(self):
        monitor = MonitorHarness()
        month_start = datetime(2026, 6, 1, 4, 30, tzinfo=SAO_PAULO)
        month_start_key = monitor.add_object(month_start, "daily", key_classification="daily")
        second_day = datetime(2026, 6, 2, 4, 30, tzinfo=SAO_PAULO)
        monthly_key = monitor.add_object(second_day, "monthly", key_classification="monthly")
        self.assertEqual({}, monitor.dynamo.table.items)
        self.assertEqual({month_start_key, monthly_key}, set(monitor.s3.objects))

        monitor.module._current_local_date = lambda: second_day
        result = monitor.module.lambda_handler({"phase": "daily"}, None)

        self.assertEqual(
            "ok",
            result["status"],
            "a wrongly daily first-day object must not satisfy the monthly boundary or block day two's correct catch-up",
        )
        self.assertEqual(monthly_key, result["recovery_point"])

    def test_monitor_accepts_first_weekly_success_after_intermediate_monitor_failure(self):
        monitor = MonitorHarness()
        failed_wednesday = datetime(2026, 6, 10, 4, 30, tzinfo=SAO_PAULO)
        monthly_key = monitor.add_prior_monthly_evidence(failed_wednesday)
        monitor.module._current_local_date = lambda: failed_wednesday
        failure_result = monitor.module.lambda_handler({"phase": "daily"}, None)
        self.assertEqual("invalid", failure_result["status"])
        failure_id = "backup-monitor/2026-06-10"
        self.assertIn(failure_id, monitor.dynamo.table.items)

        thursday = datetime(2026, 6, 11, 4, 30, tzinfo=SAO_PAULO)
        recovery_key = monitor.add_object(thursday, "weekly", key_classification="weekly")
        self.assertEqual(
            {monthly_key, recovery_key},
            set(monitor.s3.objects),
            "the Thursday object must be the first successful immutable recovery point since Monday after monthly evidence",
        )
        monitor.module._current_local_date = lambda: thursday
        catchup_result = monitor.module.lambda_handler({"phase": "daily"}, None)

        self.assertEqual(
            "ok",
            catchup_result["status"],
            "the first successful artifact after an unobserved Monday and a later monitor failure must retain weekly classification",
        )
        self.assertEqual(recovery_key, catchup_result["recovery_point"])
        self.assertTrue(
            any(message.get("Subject") == "GAM backup recovery notice" for message in monitor.sns.messages),
            "the later weekly success must publish recovery for the observed Wednesday failure",
        )
        self.assertNotIn(failure_id, monitor.dynamo.table.items)

    def test_monitor_accepts_first_monthly_success_after_intermediate_monitor_failure(self):
        monitor = MonitorHarness()
        failed_second_day = datetime(2026, 6, 2, 4, 30, tzinfo=SAO_PAULO)
        monitor.module._current_local_date = lambda: failed_second_day
        failure_result = monitor.module.lambda_handler({"phase": "daily"}, None)
        self.assertEqual("invalid", failure_result["status"])
        failure_id = "backup-monitor/2026-06-02"
        self.assertIn(failure_id, monitor.dynamo.table.items)

        third_day = datetime(2026, 6, 3, 4, 30, tzinfo=SAO_PAULO)
        recovery_key = monitor.add_object(third_day, "monthly", key_classification="monthly")
        self.assertEqual(
            {recovery_key},
            set(monitor.s3.objects),
            "the day-three object must be the first successful immutable recovery point of the month",
        )
        monitor.module._current_local_date = lambda: third_day
        catchup_result = monitor.module.lambda_handler({"phase": "daily"}, None)

        self.assertEqual(
            "ok",
            catchup_result["status"],
            "the first successful artifact after an unobserved month start and a later monitor failure must retain monthly classification",
        )
        self.assertEqual(recovery_key, catchup_result["recovery_point"])
        self.assertTrue(
            any(message.get("Subject") == "GAM backup recovery notice" for message in monitor.sns.messages),
            "the later monthly success must publish recovery for the observed day-two failure",
        )
        self.assertNotIn(failure_id, monitor.dynamo.table.items)

    def test_monitor_lambda_state_transition_resolves_a_multiday_outage(self):
        monitor = MonitorHarness()
        failed_monday = datetime(2026, 6, 8, 4, 30, tzinfo=SAO_PAULO)
        monitor.add_prior_monthly_evidence(failed_monday)
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

    def test_monitor_rejects_daily_after_persisted_first_day_outage(self):
        monitor = MonitorHarness()
        outage_day = datetime(2026, 6, 1, 4, 30, tzinfo=SAO_PAULO)
        monitor.module._current_local_date = lambda: outage_day
        outage_result = monitor.module.lambda_handler({"phase": "daily"}, None)
        self.assertEqual("invalid", outage_result["status"])
        self.assertIn(
            "backup-monitor/2026-06-01",
            monitor.dynamo.table.items,
            "the rejected post-first-day daily must represent a persisted prior outage",
        )

        local_date = datetime(2026, 6, 2, 4, 30, tzinfo=SAO_PAULO)
        monitor.add_object(local_date, "daily", key_classification="daily")
        monitor.module._current_local_date = lambda: local_date
        result = monitor.module.lambda_handler({"phase": "daily"}, None)

        self.assertEqual(
            "invalid",
            result["status"],
            "daily classification must not replace the required first successful monthly recovery point",
        )
        self.assertIn("classification", " ".join(result.get("reasons", [])).casefold())

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

    def test_monthly_catchup_is_accepted_when_first_of_month_monitor_invocation_was_missed(self):
        monitor = MonitorHarness()
        catch_up_day = datetime(2026, 6, 2, 4, 30, tzinfo=SAO_PAULO)
        recovery_key = monitor.add_object(catch_up_day, "monthly", key_classification="monthly")
        self.assertEqual(
            {},
            monitor.dynamo.table.items,
            "the scenario requires an unobserved first-of-month outage with no monitor failure or success state",
        )

        monitor.module._current_local_date = lambda: catch_up_day
        result = monitor.module.lambda_handler({"phase": "daily"}, None)

        self.assertEqual(
            "ok",
            result["status"],
            "the first successful monthly artifact must be accepted even when the monitor missed the first day; "
            f"reasons: {result.get('reasons', [])}",
        )
        self.assertEqual(recovery_key, result["recovery_point"])

    def test_weekly_catchup_is_accepted_when_monday_monitor_invocation_was_missed(self):
        monitor = MonitorHarness()
        catch_up_tuesday = datetime(2026, 6, 9, 4, 30, tzinfo=SAO_PAULO)
        monitor.add_prior_monthly_evidence(catch_up_tuesday)
        recovery_key = monitor.add_object(catch_up_tuesday, "weekly", key_classification="weekly")
        self.assertEqual(
            {},
            monitor.dynamo.table.items,
            "the scenario requires an unobserved Monday outage with no monitor failure or success state",
        )

        monitor.module._current_local_date = lambda: catch_up_tuesday
        result = monitor.module.lambda_handler({"phase": "daily"}, None)

        self.assertEqual(
            "ok",
            result["status"],
            "the next successful weekly artifact must be accepted even when the monitor missed Monday; "
            f"reasons: {result.get('reasons', [])}",
        )
        self.assertEqual(recovery_key, result["recovery_point"])

    def test_monthly_catchup_rejects_daily_without_monitor_state_or_prior_boundary_evidence(self):
        monitor = MonitorHarness()
        catch_up_day = datetime(2026, 6, 2, 4, 30, tzinfo=SAO_PAULO)
        wrongly_daily_key = monitor.add_object(catch_up_day, "daily", key_classification="daily")
        self.assertEqual(
            {},
            monitor.dynamo.table.items,
            "the scenario requires a missed first-of-month monitor invocation with no persisted state",
        )
        self.assertEqual(
            {wrongly_daily_key},
            set(monitor.s3.objects),
            "no earlier correctly classified monthly artifact may satisfy the boundary",
        )

        monitor.module._current_local_date = lambda: catch_up_day
        result = monitor.module.lambda_handler({"phase": "daily"}, None)

        self.assertEqual(
            "invalid",
            result["status"],
            "the first successful artifact of the month must be monthly even when monitor state is absent",
        )
        self.assertIn("classification", " ".join(result.get("reasons", [])).casefold())

    def test_weekly_catchup_rejects_daily_without_monitor_state_or_prior_boundary_evidence(self):
        monitor = MonitorHarness()
        catch_up_tuesday = datetime(2026, 6, 9, 4, 30, tzinfo=SAO_PAULO)
        wrongly_daily_key = monitor.add_object(catch_up_tuesday, "daily", key_classification="daily")
        self.assertEqual(
            {},
            monitor.dynamo.table.items,
            "the scenario requires a missed Monday monitor invocation with no persisted state",
        )
        self.assertEqual(
            {wrongly_daily_key},
            set(monitor.s3.objects),
            "no earlier correctly classified weekly artifact may satisfy the boundary",
        )

        monitor.module._current_local_date = lambda: catch_up_tuesday
        result = monitor.module.lambda_handler({"phase": "daily"}, None)

        self.assertEqual(
            "invalid",
            result["status"],
            "the first successful artifact after Monday must be weekly even when monitor state is absent",
        )
        self.assertIn("classification", " ".join(result.get("reasons", [])).casefold())

    def test_late_monthly_catchup_rejects_daily_without_monitor_state_or_prior_boundary_evidence(self):
        monitor = MonitorHarness()
        catch_up_day = datetime(2026, 6, 3, 4, 30, tzinfo=SAO_PAULO)
        wrongly_daily_key = monitor.add_object(catch_up_day, "daily", key_classification="daily")
        self.assertEqual({}, monitor.dynamo.table.items)
        self.assertEqual(
            {wrongly_daily_key},
            set(monitor.s3.objects),
            "the third-day object must be the first immutable artifact of the unsatisfied month",
        )

        monitor.module._current_local_date = lambda: catch_up_day
        result = monitor.module.lambda_handler({"phase": "daily"}, None)

        self.assertEqual(
            "invalid",
            result["status"],
            "the first successful artifact of an unsatisfied month must remain monthly after day two",
        )
        self.assertIn("classification", " ".join(result.get("reasons", [])).casefold())

    def test_late_weekly_catchup_rejects_daily_without_monitor_state_or_prior_boundary_evidence(self):
        monitor = MonitorHarness()
        catch_up_wednesday = datetime(2026, 6, 10, 4, 30, tzinfo=SAO_PAULO)
        wrongly_daily_key = monitor.add_object(catch_up_wednesday, "daily", key_classification="daily")
        self.assertEqual({}, monitor.dynamo.table.items)
        self.assertEqual(
            {wrongly_daily_key},
            set(monitor.s3.objects),
            "the Wednesday object must be the first immutable artifact of the unsatisfied week",
        )

        monitor.module._current_local_date = lambda: catch_up_wednesday
        result = monitor.module.lambda_handler({"phase": "daily"}, None)

        self.assertEqual(
            "invalid",
            result["status"],
            "the first successful artifact of an unsatisfied week must remain weekly after Tuesday",
        )
        self.assertIn("classification", " ".join(result.get("reasons", [])).casefold())

    def test_daily_is_accepted_after_monthly_and_weekly_boundaries_have_immutable_success_evidence(self):
        monitor = MonitorHarness()
        monthly_date = datetime(2026, 6, 1, 4, 30, tzinfo=SAO_PAULO)
        monthly_key = monitor.add_object(monthly_date, "monthly", key_classification="monthly")
        weekly_date = datetime(2026, 6, 8, 4, 30, tzinfo=SAO_PAULO)
        weekly_key = monitor.add_object(weekly_date, "weekly", key_classification="weekly")
        current_date = datetime(2026, 6, 10, 4, 30, tzinfo=SAO_PAULO)
        daily_key = monitor.add_object(current_date, "daily", key_classification="daily")
        self.assertEqual({}, monitor.dynamo.table.items)
        self.assertEqual({monthly_key, weekly_key, daily_key}, set(monitor.s3.objects))

        monitor.module._current_local_date = lambda: current_date
        result = monitor.module.lambda_handler({"phase": "daily"}, None)

        self.assertEqual(
            "ok",
            result["status"],
            "daily classification must remain valid after immutable evidence satisfies both boundaries",
        )
        self.assertEqual(daily_key, result["recovery_point"])

    def test_monthly_catchup_after_multiple_unobserved_days_is_accepted(self):
        monitor = MonitorHarness()
        catch_up_day = datetime(2026, 6, 3, 4, 30, tzinfo=SAO_PAULO)
        recovery_key = monitor.add_object(catch_up_day, "monthly", key_classification="monthly")
        self.assertEqual(
            {recovery_key},
            set(monitor.s3.objects),
            "the monthly artifact must be the first successful recovery point of the month",
        )
        self.assertEqual(
            {},
            monitor.dynamo.table.items,
            "the scenario requires multiple missed monitor invocations with no persisted monitor state",
        )

        monitor.module._current_local_date = lambda: catch_up_day
        result = monitor.module.lambda_handler({"phase": "daily"}, None)

        self.assertEqual(
            "ok",
            result["status"],
            "the first successful monthly artifact remains monthly after more than one unobserved day; "
            f"reasons: {result.get('reasons', [])}",
        )
        self.assertEqual(recovery_key, result["recovery_point"])

    def test_weekly_catchup_after_multiple_unobserved_days_is_accepted(self):
        monitor = MonitorHarness()
        catch_up_wednesday = datetime(2026, 6, 10, 4, 30, tzinfo=SAO_PAULO)
        monthly_key = monitor.add_prior_monthly_evidence(catch_up_wednesday)
        recovery_key = monitor.add_object(catch_up_wednesday, "weekly", key_classification="weekly")
        self.assertEqual(
            {monthly_key, recovery_key},
            set(monitor.s3.objects),
            "the weekly artifact must be the first successful recovery point since Monday after monthly evidence",
        )
        self.assertEqual(
            {},
            monitor.dynamo.table.items,
            "the scenario requires missed Monday and Tuesday monitor invocations with no persisted monitor state",
        )

        monitor.module._current_local_date = lambda: catch_up_wednesday
        result = monitor.module.lambda_handler({"phase": "daily"}, None)

        self.assertEqual(
            "ok",
            result["status"],
            "the next successful weekly artifact remains weekly after more than one unobserved day; "
            f"reasons: {result.get('reasons', [])}",
        )
        self.assertEqual(recovery_key, result["recovery_point"])

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

        weekly_only_monitor = MonitorHarness()
        weekly_only_key = weekly_only_monitor.add_object(
            monday_after_outage,
            "weekly",
            key_classification="weekly",
        )
        self.assertEqual({}, weekly_only_monitor.dynamo.table.items)
        self.assertEqual(
            {weekly_only_key},
            set(weekly_only_monitor.s3.objects),
            "the weekly-only fixture must have no earlier monthly recovery point or monitor state",
        )
        weekly_only_monitor.module._current_local_date = lambda: monday_after_outage
        weekly_only_valid, weekly_only_reasons, _ = weekly_only_monitor.module._check_today()
        self.assertFalse(
            weekly_only_valid,
            "when monthly and weekly are simultaneously pending, weekly-only retention must be rejected",
        )
        self.assertIn("classification", " ".join(weekly_only_reasons).casefold())

    def test_monday_after_existing_first_sunday_monthly_object_is_weekly_without_monitor_history(self):
        monitor = MonitorHarness()
        first_sunday = datetime(2026, 11, 1, 4, 30, tzinfo=SAO_PAULO)
        monitor.add_object(first_sunday, "monthly", key_classification="monthly")
        self.assertNotIn(
            "backup-monitor-classification/2026-11",
            monitor.dynamo.table.items,
            "the edge case requires a valid Sunday object whose monitor-success state was never recorded",
        )

        following_monday = datetime(2026, 11, 2, 4, 30, tzinfo=SAO_PAULO)
        monitor.add_object(following_monday, "weekly", key_classification="weekly")
        monitor.module._current_local_date = lambda: following_monday
        monday_valid, monday_reasons, monday_details = monitor.module._check_today()

        self.assertTrue(
            monday_valid,
            "Monday must be weekly when the existing Sunday object already satisfied the monthly point, "
            f"even if Sunday monitor state is absent: {monday_reasons}",
        )
        self.assertEqual("weekly", monday_details["classification"])

    def test_stale_month_start_failure_does_not_override_valid_sunday_monthly_evidence(self):
        monitor = MonitorHarness()
        first_sunday = datetime(2026, 11, 1, 4, 30, tzinfo=SAO_PAULO)
        monitor.module._current_local_date = lambda: first_sunday
        failure_result = monitor.module.lambda_handler({"phase": "daily"}, None)
        self.assertEqual("invalid", failure_result["status"])
        sunday_failure_id = "backup-monitor/2026-11-01"
        self.assertIn(sunday_failure_id, monitor.dynamo.table.items)

        monitor.add_object(first_sunday, "monthly", key_classification="monthly")
        following_monday = datetime(2026, 11, 2, 4, 30, tzinfo=SAO_PAULO)
        monitor.add_object(following_monday, "weekly", key_classification="weekly")
        monitor.module._current_local_date = lambda: following_monday

        recovery_result = monitor.module.lambda_handler({"phase": "daily"}, None)

        self.assertEqual(
            "ok",
            recovery_result["status"],
            "immutable Sunday monthly evidence must take precedence over a stale monitor-failure marker",
        )
        self.assertNotIn(
            sunday_failure_id,
            monitor.dynamo.table.items,
            "Monday success must clear the stale Sunday failure after validating the prior monthly object",
        )

    def test_monthly_catchup_clears_cross_week_failures_before_later_daily_validation(self):
        monitor = MonitorHarness()
        first_sunday = datetime(2026, 11, 1, 4, 30, tzinfo=SAO_PAULO)
        following_monday = datetime(2026, 11, 2, 4, 30, tzinfo=SAO_PAULO)
        for outage_day in (first_sunday, following_monday):
            monitor.module._current_local_date = lambda outage_day=outage_day: outage_day
            failure_result = monitor.module.lambda_handler({"phase": "daily"}, None)
            self.assertEqual("invalid", failure_result["status"])

        catchup_tuesday = datetime(2026, 11, 3, 4, 30, tzinfo=SAO_PAULO)
        monitor.add_object(catchup_tuesday, "monthly", key_classification="monthly")
        monitor.module._current_local_date = lambda: catchup_tuesday
        catchup_result = monitor.module.lambda_handler({"phase": "daily"}, None)
        self.assertEqual("ok", catchup_result["status"])

        for failure_id in ("backup-monitor/2026-11-01", "backup-monitor/2026-11-02"):
            self.assertNotIn(
                failure_id,
                monitor.dynamo.table.items,
                "monthly catch-up must clear every unresolved failure that led to the first successful monthly point",
            )

        following_wednesday = datetime(2026, 11, 4, 4, 30, tzinfo=SAO_PAULO)
        monitor.add_object(following_wednesday, "daily", key_classification="daily")
        monitor.module._current_local_date = lambda: following_wednesday
        daily_result = monitor.module.lambda_handler({"phase": "daily"}, None)
        self.assertEqual(
            "ok",
            daily_result["status"],
            "a completed monthly catch-up must not cause later daily artifacts to be rejected as monthly",
        )

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
        monitor.add_prior_monthly_evidence(failed_monday)
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

    def test_same_day_retry_clears_failure_and_publishes_recovery_notice(self):
        monitor = MonitorHarness()
        failed_check = datetime(2026, 6, 3, 4, 30, tzinfo=SAO_PAULO)
        monitor.module._current_local_date = lambda: failed_check
        first_result = monitor.module.lambda_handler({"phase": "daily"}, None)
        self.assertEqual("invalid", first_result["status"])
        failure_id = "backup-monitor/2026-06-03"
        self.assertIn(failure_id, monitor.dynamo.table.items)

        successful_retry = datetime(2026, 6, 3, 5, 0, tzinfo=SAO_PAULO)
        monitor.add_object(successful_retry, "monthly", key_classification="monthly")
        monitor.module._current_local_date = lambda: successful_retry
        recovery_result = monitor.module.lambda_handler({"phase": "daily"}, None)

        self.assertEqual("ok", recovery_result["status"])
        self.assertTrue(
            any(message.get("Subject") == "GAM backup recovery notice" for message in monitor.sns.messages),
            "a valid retry between 04:30 and 12:00 must notify recovery of the same local-date failure",
        )
        self.assertNotIn(
            failure_id,
            monitor.dynamo.table.items,
            "same-day recovery must clear unresolved state so the 12:00 phase cannot escalate a recovered failure",
        )

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

    def test_monthly_lifecycle_failure_stays_unresolved_until_lifecycle_is_repaired(self):
        monitor = MonitorHarness()
        month_start = datetime(2026, 6, 1, 4, 30, tzinfo=SAO_PAULO)
        monitor.add_object(
            month_start,
            "monthly",
            key_classification="monthly",
            lifecycle=[recovery_lifecycle_rule("monthly", valid=False)],
        )
        monitor.module._current_local_date = lambda: month_start

        failed_result = monitor.module.lambda_handler({"phase": "daily"}, None)

        failure_id = "backup-monitor/2026-06-01"
        self.assertEqual("invalid", failed_result["status"])
        self.assertIn("lifecycle", " ".join(failed_result.get("reasons", [])).casefold())
        self.assertIn(failure_id, monitor.dynamo.table.items)

        next_day = datetime(2026, 6, 2, 4, 30, tzinfo=SAO_PAULO)
        monitor.add_object(
            next_day,
            "daily",
            key_classification="daily",
            lifecycle=[
                recovery_lifecycle_rule("daily"),
                recovery_lifecycle_rule("monthly", valid=False),
            ],
        )
        monitor.module._current_local_date = lambda: next_day

        premature_result = monitor.module.lambda_handler({"phase": "daily"}, None)

        self.assertEqual(
            "invalid",
            premature_result["status"],
            "a monthly artifact with invalid lifecycle must not satisfy the boundary for a later daily object",
        )
        self.assertIn(
            failure_id,
            monitor.dynamo.table.items,
            "the lifecycle-originated monthly failure must remain unresolved",
        )
        self.assertFalse(
            any(message.get("Subject") == "GAM backup recovery notice" for message in monitor.sns.messages),
            "invalid monthly lifecycle evidence must not publish a recovery notice",
        )

        monitor.s3.lifecycle = [
            recovery_lifecycle_rule("daily"),
            recovery_lifecycle_rule("monthly"),
        ]
        repaired_result = monitor.module.lambda_handler({"phase": "daily"}, None)

        self.assertEqual("ok", repaired_result["status"])
        self.assertNotIn(failure_id, monitor.dynamo.table.items)
        self.assertTrue(
            any(message.get("Subject") == "GAM backup recovery notice" for message in monitor.sns.messages),
            "repairing the monthly lifecycle must allow the valid boundary evidence to resolve the failure",
        )

    def test_weekly_lifecycle_failure_stays_unresolved_until_lifecycle_is_repaired(self):
        monitor = MonitorHarness()
        monday = datetime(2026, 6, 8, 4, 30, tzinfo=SAO_PAULO)
        monitor.add_prior_monthly_evidence(monday)
        monitor.add_object(
            monday,
            "weekly",
            key_classification="weekly",
            lifecycle=[
                recovery_lifecycle_rule("monthly"),
                recovery_lifecycle_rule("weekly", valid=False),
            ],
        )
        monitor.module._current_local_date = lambda: monday

        failed_result = monitor.module.lambda_handler({"phase": "daily"}, None)

        failure_id = "backup-monitor/2026-06-08"
        self.assertEqual("invalid", failed_result["status"])
        self.assertIn("lifecycle", " ".join(failed_result.get("reasons", [])).casefold())
        self.assertIn(failure_id, monitor.dynamo.table.items)

        tuesday = datetime(2026, 6, 9, 4, 30, tzinfo=SAO_PAULO)
        monitor.add_object(
            tuesday,
            "daily",
            key_classification="daily",
            lifecycle=[
                recovery_lifecycle_rule("daily"),
                recovery_lifecycle_rule("monthly"),
                recovery_lifecycle_rule("weekly", valid=False),
            ],
        )
        monitor.module._current_local_date = lambda: tuesday

        premature_result = monitor.module.lambda_handler({"phase": "daily"}, None)

        self.assertEqual(
            "invalid",
            premature_result["status"],
            "a weekly artifact with invalid lifecycle must not satisfy the boundary for a later daily object",
        )
        self.assertIn(
            failure_id,
            monitor.dynamo.table.items,
            "the lifecycle-originated weekly failure must remain unresolved",
        )
        self.assertFalse(
            any(message.get("Subject") == "GAM backup recovery notice" for message in monitor.sns.messages),
            "invalid weekly lifecycle evidence must not publish a recovery notice",
        )

        monitor.s3.lifecycle = [
            recovery_lifecycle_rule("daily"),
            recovery_lifecycle_rule("monthly"),
            recovery_lifecycle_rule("weekly"),
        ]
        repaired_result = monitor.module.lambda_handler({"phase": "daily"}, None)

        self.assertEqual("ok", repaired_result["status"])
        self.assertNotIn(failure_id, monitor.dynamo.table.items)
        self.assertTrue(
            any(message.get("Subject") == "GAM backup recovery notice" for message in monitor.sns.messages),
            "repairing the weekly lifecycle must allow the valid boundary evidence to resolve the failure",
        )

    def test_unobserved_monthly_lifecycle_defect_cannot_satisfy_the_boundary(self):
        monitor = MonitorHarness()
        month_start = datetime(2026, 6, 1, 4, 30, tzinfo=SAO_PAULO)
        monitor.add_object(
            month_start,
            "monthly",
            key_classification="monthly",
            lifecycle=[recovery_lifecycle_rule("monthly", valid=False)],
        )

        next_day = datetime(2026, 6, 2, 4, 30, tzinfo=SAO_PAULO)
        monitor.add_object(
            next_day,
            "daily",
            key_classification="daily",
            lifecycle=[
                recovery_lifecycle_rule("daily"),
                recovery_lifecycle_rule("monthly", valid=False),
            ],
        )
        self.assertEqual(
            {},
            monitor.dynamo.table.items,
            "the fixture requires a missed month-start monitor invocation with no persisted failure or success state",
        )
        monitor.module._current_local_date = lambda: next_day

        invalid_result = monitor.module.lambda_handler({"phase": "daily"}, None)

        self.assertEqual(
            "invalid",
            invalid_result["status"],
            "an unobserved monthly artifact with invalid lifecycle must not authorize a later daily artifact",
        )

        monitor.s3.lifecycle = [
            recovery_lifecycle_rule("daily"),
            recovery_lifecycle_rule("monthly"),
        ]
        repaired_result = monitor.module.lambda_handler({"phase": "daily"}, None)

        self.assertEqual(
            "ok",
            repaired_result["status"],
            "repairing the monthly lifecycle must make the immutable boundary evidence usable",
        )

    def test_unobserved_weekly_lifecycle_defect_cannot_satisfy_the_boundary(self):
        monitor = MonitorHarness()
        monday = datetime(2026, 6, 8, 4, 30, tzinfo=SAO_PAULO)
        monitor.add_prior_monthly_evidence(monday)
        monitor.add_object(
            monday,
            "weekly",
            key_classification="weekly",
            lifecycle=[
                recovery_lifecycle_rule("monthly"),
                recovery_lifecycle_rule("weekly", valid=False),
            ],
        )

        tuesday = datetime(2026, 6, 9, 4, 30, tzinfo=SAO_PAULO)
        monitor.add_object(
            tuesday,
            "daily",
            key_classification="daily",
            lifecycle=[
                recovery_lifecycle_rule("daily"),
                recovery_lifecycle_rule("monthly"),
                recovery_lifecycle_rule("weekly", valid=False),
            ],
        )
        self.assertEqual(
            {},
            monitor.dynamo.table.items,
            "the fixture requires a missed Monday monitor invocation with no persisted failure or success state",
        )
        monitor.module._current_local_date = lambda: tuesday

        invalid_result = monitor.module.lambda_handler({"phase": "daily"}, None)

        self.assertEqual(
            "invalid",
            invalid_result["status"],
            "an unobserved weekly artifact with invalid lifecycle must not authorize a later daily artifact",
        )

        monitor.s3.lifecycle = [
            recovery_lifecycle_rule("daily"),
            recovery_lifecycle_rule("monthly"),
            recovery_lifecycle_rule("weekly"),
        ]
        repaired_result = monitor.module.lambda_handler({"phase": "daily"}, None)

        self.assertEqual(
            "ok",
            repaired_result["status"],
            "repairing the weekly lifecycle must make the immutable boundary evidence usable",
        )

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

    def test_monitor_rejects_malformed_s3_checksum_and_non_sha256_metadata(self):
        scenarios = (
            ("malformed S3 checksum", "not-base64-or-hex", "a" * 64),
            ("arbitrary SHA-256 metadata", "still-not-a-checksum", "not-a-sha256"),
        )
        for label, s3_checksum, metadata_sha256 in scenarios:
            with self.subTest(scenario=label):
                monitor = MonitorHarness()
                local_date = datetime(2026, 6, 3, 4, 30, tzinfo=SAO_PAULO)
                key = monitor.add_object(local_date, "daily", key_classification="daily")
                monitor.s3.attribute_checksums[key] = s3_checksum
                monitor.s3.objects[key]["Metadata"]["checksum"] = s3_checksum
                monitor.s3.objects[key]["Metadata"]["sha256"] = metadata_sha256
                monitor.module._current_local_date = lambda: local_date

                valid, reasons, _ = monitor.module._check_today()

                self.assertFalse(valid, f"{label} was accepted as independently verifiable: {reasons}")
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
        schedule_by_name = {argv_value(command_argv(task), "--name"): task for task in schedules}
        self.assertEqual(
            {"gam-production-backup-monitor-0430", "gam-production-backup-monitor-1200"},
            set(schedule_by_name),
        )
        self.assertEqual(
            "cron(30 4 * * ? *)",
            argv_value(command_argv(schedule_by_name["gam-production-backup-monitor-0430"]), "--schedule-expression"),
        )
        self.assertEqual(
            "cron(0 12 * * ? *)",
            argv_value(command_argv(schedule_by_name["gam-production-backup-monitor-1200"]), "--schedule-expression"),
        )
        for task in schedule_by_name.values():
            self.assertEqual("{{ backup_monitor_lambda_arn }}", scheduler_target(task).get("Arn"))

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
            if "--retry-policy" in create_argv:
                violations.append(f"retry policy uses the invalid top-level AWS CLI option for {name}")
            retry_policy = scheduler_target_retry_policy(create_task)
            if retry_policy != {
                "MaximumEventAgeInSeconds": "86400",
                "MaximumRetryAttempts": "3",
            }:
                violations.append(f"retry policy is not nested under Target for {name}")

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
                if "--retry-policy" in update_argv:
                    violations.append(f"update-schedule uses the invalid top-level retry option for {name}")
                update_retry_policy = scheduler_target_retry_policy(update_task)
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
                    violations.append(f"update-schedule does not restore Target.RetryPolicy for {name}")

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

    def test_monitor_schedule_target_arguments_pass_aws_cli_serialization_validation(self):
        tasks = list(task_nodes(load_yaml("operations/ansible/backup-monitor.yml")))
        schedule_tasks = [
            task
            for task in tasks
            if any(operation in command_argv(task) for operation in ("create-schedule", "update-schedule"))
            and argv_value(command_argv(task), "--name")
            in {"gam-production-backup-monitor-0430", "gam-production-backup-monitor-1200"}
        ]
        self.assertEqual(4, len(schedule_tasks), "daily and unresolved create/update paths must all be validated")

        replacements = {
            "{{ backup_monitor_lambda_arn }}": "arn:aws:lambda:sa-east-1:123456789012:function:gam-monitor",
            "{{ backup_monitor_scheduler_role_arn }}": "arn:aws:iam::123456789012:role/gam-scheduler",
            "{{ aws_region }}": "sa-east-1",
        }
        failures = []
        for task in schedule_tasks:
            rendered_argv = []
            for argument in command_argv(task):
                for placeholder, value in replacements.items():
                    argument = argument.replace(placeholder, value)
                rendered_argv.append(argument)

            result = subprocess.run(
                [*rendered_argv, "--generate-cli-skeleton", "output"],
                cwd=ROOT,
                capture_output=True,
                text=True,
                timeout=30,
            )
            if result.returncode != 0:
                failures.append(
                    f"{rendered_argv[2]} {argv_value(rendered_argv, '--name')}: {result.stderr.strip()}"
                )

        self.assertEqual(
            [],
            failures,
            "EventBridge Target.Input must be serialized as the string required by the AWS CLI: "
            + "; ".join(failures),
        )

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

    def test_better_stack_collector_uses_a_durable_executable_installed_state_probe(self):
        tasks = list(task_nodes(load_yaml("operations/ansible/site.yml")))
        status_task = next(
            (task for task in tasks if task.get("register") == "better_stack_collector_status"),
            None,
        )
        self.assertIsNotNone(status_task, "the metrics-only collector must expose an installed-state probe")

        violations = []
        if not is_durable_better_stack_collector_probe(status_task):
            violations.append(
                "collector status must use the durable Compose project label or an explicitly persistent compose file"
            )

        failed_when = str(status_task.get("failed_when", "")).strip()
        if failed_when.casefold() in {"", "false"}:
            violations.append("collector status parsing or execution failures must fail closed")
        else:
            register = str(status_task.get("register", ""))
            if f"{register}.rc" not in failed_when:
                violations.append("collector status must reject a nonzero probe exit status")
            if f"{register}.stdout" not in failed_when:
                violations.append("collector status must reject an empty installed-state result")

        unusable_compose_probes = []
        for task in tasks:
            argv = command_argv(task)
            lowered = [argument.casefold() for argument in argv]
            if lowered[:2] == ["docker", "compose"] and "ps" in lowered:
                compose_file = argv_value(argv, "--file") or argv_value(argv, "-f")
                if not compose_file or compose_file.casefold().startswith(("/tmp/", "${tmpdir", "{{ tmp")):
                    unusable_compose_probes.append(task.get("name", "<unnamed>"))
        if unusable_compose_probes:
            violations.append(
                "Compose ps cannot discover the installer's deleted temporary compose file: "
                + ", ".join(unusable_compose_probes)
            )

        self.assertEqual([], violations, "Better Stack collector installed-state violations: " + "; ".join(violations))

    def test_better_stack_collector_requires_every_official_service_running_and_healthy(self):
        tasks = list(task_nodes(load_yaml("operations/ansible/site.yml")))
        preinstall_probe = next(
            (task for task in tasks if task.get("register") == "better_stack_collector_preinstall_state"),
            None,
        )
        readiness_probe = next(
            (task for task in tasks if task.get("register") == "better_stack_collector_status"),
            None,
        )
        installer = next(
            (task for task in tasks if task.get("name") == "Run the official Better Stack collector Docker Compose deployment"),
            None,
        )
        self.assertIsNotNone(preinstall_probe)
        self.assertIsNotNone(readiness_probe)
        self.assertIsNotNone(installer)

        required_services = {"collector", "ebpf"}

        def loop_values(task: dict) -> set[str]:
            loop = task.get("loop", [])
            if isinstance(loop, list):
                return {str(value) for value in loop}
            loop_variable = re.fullmatch(r"\{\{\s*([a-zA-Z_][a-zA-Z0-9_]*)\s*\}\}", str(loop))
            if loop_variable is None:
                return set()
            variables = load_yaml("operations/ansible/group_vars/production.yml")
            configured = variables.get(loop_variable.group(1), [])
            return {str(value) for value in configured} if isinstance(configured, list) else set()

        violations = []
        for label, probe in (("pre-install", preinstall_probe), ("readiness", readiness_probe)):
            argv = command_argv(probe)
            argv_text = " ".join(argv)
            if loop_values(probe) != required_services:
                violations.append(f"{label} probe must iterate exactly the official collector and ebpf services")
            for required_filter in (
                "label=com.docker.compose.project=better-stack-collector",
                "label=com.docker.compose.service={{ item }}",
                "status=running",
                "health=healthy",
            ):
                if required_filter not in argv_text:
                    violations.append(f"{label} probe must filter {required_filter}")

        install_when = str(installer.get("when", ""))
        for signal in ("better_stack_collector_preinstall_state.results", "stdout"):
            if signal not in install_when:
                violations.append(f"installer remediation must evaluate {signal} for every required service")
        if not re.search(
            r"(?is)results.*(?:selectattr|rejectattr|map).*stdout.*(?:list|length).*>\s*0",
            install_when,
        ):
            violations.append("installer remediation must run when any required-service probe is empty")
        readiness_failed_when = str(readiness_probe.get("failed_when", ""))
        for signal in ("better_stack_collector_status.results", "stdout"):
            if signal not in readiness_failed_when:
                violations.append(f"readiness must fail closed using {signal} for every required service")
        if not re.search(
            r"(?is)results.*(?:selectattr|rejectattr|map).*stdout.*(?:list|length).*>\s*0",
            readiness_failed_when,
        ):
            violations.append("readiness must fail when any required-service probe is empty")

        self.assertEqual([], violations, "Better Stack collector completeness violations: " + "; ".join(violations))

        cases = {
            "missing service": [
                {"service": "collector", "state": "running", "health": "healthy"},
            ],
            "stopped service": [
                {"service": "collector", "state": "running", "health": "healthy"},
                {"service": "ebpf", "state": "exited", "health": "healthy"},
            ],
            "unhealthy service": [
                {"service": "collector", "state": "running", "health": "healthy"},
                {"service": "ebpf", "state": "running", "health": "unhealthy"},
            ],
            "complete healthy deployment": [
                {"service": "collector", "state": "running", "health": "healthy"},
                {"service": "ebpf", "state": "running", "health": "healthy"},
            ],
        }
        for case, containers in cases.items():
            with self.subTest(deployment_state=case):
                probe_results = {
                    service: [
                        container
                        for container in containers
                        if container["service"] == service
                        and container["state"] == "running"
                        and container["health"] == "healthy"
                    ]
                    for service in required_services
                }
                deployment_ready = all(probe_results[service] for service in required_services)
                expected_ready = case == "complete healthy deployment"
                self.assertEqual(expected_ready, deployment_ready)
                self.assertEqual(
                    not expected_ready,
                    not deployment_ready,
                    "partial or unhealthy state must trigger installation remediation and fail readiness",
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

    def test_new_better_stack_collector_is_metrics_only_before_docker_startup(self):
        """Clean-state creation must disable broad telemetry before the secret starts Docker."""

        tasks = list(task_nodes(load_yaml("operations/ansible/site.yml")))
        create_index, create_task = next(
            (
                (index, task)
                for index, task in enumerate(tasks)
                if isinstance(module_payload(task, "ansible.builtin.uri", "uri"), dict)
                and str(module_payload(task, "ansible.builtin.uri", "uri").get("method", "")).upper() == "POST"
                and re.search(
                    r"/api/v1/collectors$",
                    str(module_payload(task, "ansible.builtin.uri", "uri").get("url", "")),
                )
            ),
            (None, None),
        )
        install_index, _ = next(
            (
                (index, task)
                for index, task in enumerate(tasks)
                if task.get("name") == "Run the official Better Stack collector Docker Compose deployment"
            ),
            (None, None),
        )

        self.assertIsNotNone(create_task, "clean-state provisioning must create the provider collector")
        self.assertIsNotNone(install_index, "clean-state provisioning must retain the supported Docker installer")
        self.assertLess(
            create_index,
            install_index,
            "provider collector creation and its metrics-only policy must precede Docker startup",
        )

        create_payload = module_payload(create_task, "ansible.builtin.uri", "uri")
        components = create_payload.get("body", {}).get("configuration", {}).get("components", {})
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
        violations = []
        for component in sorted(disabled_components):
            if components.get(component) is not False:
                violations.append(f"collector POST must set {component}=false before startup")
        for component in sorted(metrics_components):
            if components.get(component) is not True:
                violations.append(f"collector POST must set {component}=true before startup")

        self.assertEqual(
            [],
            violations,
            "Better Stack initial metrics-only collector violations: " + "; ".join(violations),
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
        installer_tasks = [task for task in tasks if is_official_better_stack_installer_execution(task)]
        self.assertTrue(
            installer_tasks,
            "the supported Better Stack Docker Compose collector installer must be executed",
        )

        service_binding = any(
            "/etc/gam/better-stack-monitoring-contract.yml" in task_text(task)
            for task in tasks
            if module_payload(task, "ansible.builtin.template", "template") is not None
        ) and bool(installer_tasks)
        self.assertTrue(
            service_binding,
            "the official collector deployment must be paired with the versioned metrics-only contract",
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

    def test_better_stack_collector_secret_is_gated_after_creation_and_bound_to_persistent_service(self):
        site = load_yaml("operations/ansible/site.yml")
        site_tasks = list(task_nodes(site))
        production_play = next(
            play
            for play in site
            if isinstance(play, dict)
            and play.get("name") == "Provision GAM production backup, recovery, AWS, and monitoring behavior"
        )
        global_preflight = "\n".join(task_text(task) for task in production_play.get("pre_tasks", []))
        self.assertNotIn(
            "better_stack_collector_secret",
            global_preflight,
            "clean-provider commissioning must reach collector discovery and POST before the generated secret can enter external custody",
        )

        create_index = next(
            index
            for index, task in enumerate(site_tasks)
            if task.get("register") == "better_stack_collector_created"
        )
        install_index = next(
            index
            for index, task in enumerate(site_tasks)
            if task.get("name") == "Run the official Better Stack collector Docker Compose deployment"
        )
        custody_gates = [
            task
            for task in site_tasks[create_index + 1 : install_index]
            if module_payload(task, "ansible.builtin.fail", "fail", "ansible.builtin.assert", "assert") is not None
            and "BETTER_STACK_COLLECTOR_SECRET" in task_text(task)
            and "better_stack_collector_created" in task_text(task)
        ]
        self.assertTrue(
            custody_gates,
            "new collector creation must stop before Docker startup for external secret custody and replay",
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

    def test_better_stack_provider_resource_ids_are_discovered_instead_of_supplied(self):
        """Provider-created identities must come from API responses, never operator inputs."""

        variables = load_yaml("operations/ansible/group_vars/production.yml")
        site = load_yaml("operations/ansible/site.yml")
        tasks = list(task_nodes(site))
        provider_identifiers = {
            "better_stack_collector_id": "BETTER_STACK_COLLECTOR_ID",
            "better_stack_dashboard_id": "BETTER_STACK_DASHBOARD_ID",
            "better_stack_proxy_chart_id": "BETTER_STACK_PROXY_CHART_ID",
            "better_stack_backend_chart_id": "BETTER_STACK_BACKEND_CHART_ID",
            "better_stack_postgresql_chart_id": "BETTER_STACK_POSTGRESQL_CHART_ID",
            "better_stack_filesystem_chart_id": "BETTER_STACK_FILESYSTEM_CHART_ID",
        }

        production_play = next(
            play
            for play in site
            if isinstance(play, dict)
            and play.get("name") == "Provision GAM production backup, recovery, AWS, and monitoring behavior"
        )
        preflight_text = "\n".join(task_text(task) for task in production_play.get("pre_tasks", []))
        fact_tasks = [
            task
            for task in tasks
            if isinstance(module_payload(task, "ansible.builtin.set_fact", "set_fact"), dict)
        ]

        violations = []
        for identifier, environment_name in provider_identifiers.items():
            declaration = str(variables.get(identifier, ""))
            if environment_name in declaration:
                violations.append(f"{identifier} must not be read from {environment_name}")
            if identifier in preflight_text:
                violations.append(f"{identifier} must not be a production preflight prerequisite")

            assignments = [
                module_payload(task, "ansible.builtin.set_fact", "set_fact")[identifier]
                for task in fact_tasks
                if identifier in module_payload(task, "ansible.builtin.set_fact", "set_fact")
            ]
            if not assignments:
                violations.append(f"{identifier} must be selected from Better Stack provider state")
                continue
            assignment_text = "\n".join(str(assignment) for assignment in assignments)
            if "id" not in assignment_text.casefold() or not any(
                signal in assignment_text
                for signal in (".json.data", "['data']", '["data"]')
            ):
                violations.append(f"{identifier} must derive from a provider response data.id")

        self.assertEqual([], violations, "Better Stack resource identity violations: " + "; ".join(violations))

        self.assertIn("BETTER_STACK_API_TOKEN", str(variables.get("better_stack_api_token", "")))
        self.assertIn("BETTER_STACK_COLLECTOR_SECRET", str(variables.get("better_stack_collector_secret", "")))

    def test_new_better_stack_collector_requires_external_secret_custody_before_startup(self):
        """Clean-state creation must stop for approved secret custody before Docker starts."""

        tasks = list(task_nodes(load_yaml("operations/ansible/site.yml")))
        create_index, collector_create = next(
            (
                (index, task)
                for index, task in enumerate(tasks)
                if isinstance(module_payload(task, "ansible.builtin.uri", "uri"), dict)
                and str(module_payload(task, "ansible.builtin.uri", "uri").get("method", "")).upper() == "POST"
                and re.search(
                    r"/api/v1/collectors$",
                    str(module_payload(task, "ansible.builtin.uri", "uri").get("url", "")),
                )
            ),
            (None, None),
        )
        self.assertIsNotNone(collector_create, "clean-state provisioning must create the provider collector")
        create_register = str(collector_create.get("register", "")).strip()

        install_index, install_task = next(
            (
                (index, task)
                for index, task in enumerate(tasks)
                if task.get("name") == "Run the official Better Stack collector Docker Compose deployment"
            ),
            (None, None),
        )
        self.assertIsNotNone(install_task, "the supported Better Stack collector installer must remain exercised")

        violations = []
        if not create_register:
            violations.append("collector creation must register whether a new provider resource was created")
        if collector_create.get("no_log") is not True:
            violations.append("collector creation must suppress its provider-generated secret")

        transient_secret_uses = [
            task.get("name", "<unnamed>")
            for task in tasks
            if create_register
            and create_register in task_text(task)
            and "json.data.attributes.secret" in task_text(task)
        ]
        if transient_secret_uses:
            violations.append(
                "collector creation response secret must not bypass approved external custody: "
                + ", ".join(transient_secret_uses)
            )

        custody_gates = []
        if create_register and create_index is not None and install_index is not None:
            for task in tasks[create_index + 1 : install_index]:
                gate_payload = module_payload(task, "ansible.builtin.fail", "fail", "ansible.builtin.assert", "assert")
                if gate_payload is None:
                    continue
                gate_text = task_text(task)
                if create_register in gate_text and "BETTER_STACK_COLLECTOR_SECRET" in gate_text:
                    custody_gates.append(task)
        if not custody_gates:
            violations.append(
                "a newly created collector must stop before Docker startup and require external BETTER_STACK_COLLECTOR_SECRET custody plus replay"
            )
        elif not any(
            all(signal in task_text(task).casefold() for signal in ("custody", "rerun"))
            for task in custody_gates
        ):
            violations.append("the clean-state stop must explain approved secret custody and rerun")

        install_environment = install_task.get("environment", {}) if install_task else {}
        installed_secret = str(install_environment.get("COLLECTOR_SECRET", ""))
        if "better_stack_collector_secret" not in installed_secret:
            violations.append(
                "collector startup must consume the dedicated secret restored from approved external custody"
            )
        if (
            create_register
            and create_register in installed_secret
            or "json.data.attributes.secret" in installed_secret
        ):
            violations.append("collector startup must not consume a transient provider creation response")
        if install_task and install_task.get("no_log") is not True:
            violations.append("collector installation must suppress the externally supplied collector secret")

        self.assertEqual(
            [],
            violations,
            "Better Stack collector external-custody violations: " + "; ".join(violations),
        )

    def test_better_stack_named_resource_discovery_cannot_stop_at_the_first_provider_page(self):
        """Stable-name discovery must use documented filters or consume every provider page."""

        tasks = list(task_nodes(load_yaml("operations/ansible/site.yml")))
        indexed_uri_tasks = [
            (index, task, module_payload(task, "ansible.builtin.uri", "uri"))
            for index, task in enumerate(tasks)
            if isinstance(module_payload(task, "ansible.builtin.uri", "uri"), dict)
        ]

        violations = []

        filtered_contracts = (
            (
                "collector",
                r"/api/v1/collectors(?:\?|$)",
                "name",
                ("Discover Better Stack collectors", "Read back Better Stack collectors"),
            ),
            (
                "dashboard",
                r"/api/v2/dashboards(?:\?|$)",
                "query",
                ("Discover Better Stack dashboards", "Read back Better Stack dashboards"),
            ),
            (
                "monitor",
                r"(?:/api/v2)?/monitors(?:\?|$)",
                "pronounceable_name",
                ("Read existing Better Stack monitors", "Read back Better Stack monitors"),
            ),
        )
        for resource, endpoint, filter_name, expected_names in filtered_contracts:
            requests = [
                task
                for _, task, payload in indexed_uri_tasks
                if str(payload.get("method", "")).upper() == "GET"
                and re.search(endpoint, str(payload.get("url", "")))
                and any(name in str(task.get("name", "")) for name in expected_names)
            ]
            if len(requests) != len(expected_names):
                violations.append(
                    f"{resource} discovery/readback must retain both provider list boundaries"
                )
                continue
            for request in requests:
                url = str(module_payload(request, "ansible.builtin.uri", "uri").get("url", ""))
                if re.search(rf"[?&]{filter_name}=", url) is None:
                    violations.append(
                        f"{request.get('name')} must use Better Stack's supported {filter_name} filter instead of one unbounded page"
                    )

            if resource == "monitor":
                monitor_request_text = "\n".join(task_text(task).casefold() for task in requests)
                for stable_name in ("gam production availability", "gam production tls certificate"):
                    if stable_name not in monitor_request_text:
                        violations.append(
                            f"monitor filtering must query the stable provider name {stable_name!r}"
                        )

        alert_boundaries = [
            (index, task, payload)
            for index, task, payload in indexed_uri_tasks
            if str(payload.get("method", "")).upper() == "GET"
            and re.search(r"/api/v2/alerts(?:\?|$)", str(payload.get("url", "")))
            and str(task.get("name", ""))
            in {
                "Read existing Better Stack dashboard alerts",
                "Read back Better Stack dashboard alerts",
            }
        ]
        if len(alert_boundaries) != 2:
            violations.append("alert discovery/readback must retain both provider list boundaries")

        for boundary_index, boundary_task, boundary_payload in alert_boundaries:
            register = str(boundary_task.get("register", "")).strip()
            page_followups = [
                task
                for index, task, payload in indexed_uri_tasks
                if index > boundary_index
                and str(payload.get("method", "")).upper() == "GET"
                and re.search(r"/api/v2/alerts(?:\?|$)", str(payload.get("url", "")))
                and register
                and register in task_text(task)
                and "pagination" in task_text(task)
                and any(signal in task_text(task) for signal in (".next", ".last"))
            ]
            if not page_followups:
                violations.append(
                    f"{boundary_task.get('name')} must traverse the provider pagination links beyond page one"
                )
                continue

            followup_registers = [
                str(task.get("register", "")).strip()
                for task in page_followups
                if str(task.get("register", "")).strip()
            ]
            later_text = "\n".join(task_text(task) for task in tasks[boundary_index + 1 :])
            if not any(
                followup_register in later_text
                and ".results" in later_text
                and ".json.data" in later_text
                for followup_register in followup_registers
            ):
                violations.append(
                    f"{boundary_task.get('name')} must merge subsequent-page data into discovery and verification decisions"
                )

        self.assertEqual(
            [],
            violations,
            "Better Stack provider discovery completeness violations: " + "; ".join(violations),
        )

    def test_better_stack_metric_target_discovery_consumes_every_provider_page(self):
        """Targets beyond page one must participate in creation, drift, and final verification."""

        tasks = list(task_nodes(load_yaml("operations/ansible/site.yml")))
        indexed_uri_tasks = [
            (index, task, module_payload(task, "ansible.builtin.uri", "uri"))
            for index, task in enumerate(tasks)
            if isinstance(module_payload(task, "ansible.builtin.uri", "uri"), dict)
        ]
        boundaries = [
            (index, task)
            for index, task, payload in indexed_uri_tasks
            if str(payload.get("method", "")).upper() == "GET"
            and re.search(r"/api/v1/collectors/[^/]+/targets(?:\?|$)", str(payload.get("url", "")))
            and task.get("register")
            in {
                "better_stack_existing_collector_targets",
                "better_stack_collector_targets_readback",
            }
        ]
        self.assertEqual(
            2,
            len(boundaries),
            "target discovery and final readback must retain separate provider list boundaries",
        )

        violations = []
        for boundary_index, boundary_task in boundaries:
            boundary_register = str(boundary_task.get("register", ""))
            page_followups = [
                task
                for index, task, payload in indexed_uri_tasks
                if index > boundary_index
                and str(payload.get("method", "")).upper() == "GET"
                and re.search(r"/api/v1/collectors/[^/]+/targets(?:\?|$)", str(payload.get("url", "")))
                and boundary_register in task_text(task)
                and "pagination" in task_text(task)
                and any(signal in task_text(task) for signal in (".next", ".last"))
            ]
            if not page_followups:
                violations.append(
                    f"{boundary_task.get('name')} must traverse target pages beyond the first provider response"
                )
                continue

            followup_registers = [
                str(task.get("register", "")).strip()
                for task in page_followups
                if str(task.get("register", "")).strip()
            ]
            later_text = "\n".join(task_text(task) for task in tasks[boundary_index + 1 :])
            if not any(
                followup_register in later_text
                and ".results" in later_text
                and ".json.data" in later_text
                for followup_register in followup_registers
            ):
                violations.append(
                    f"{boundary_task.get('name')} must merge subsequent target pages into provider decisions"
                )

        self.assertEqual(
            [],
            violations,
            "Better Stack metric-target pagination violations: " + "; ".join(violations),
        )

    def test_better_stack_chart_discovery_consumes_every_provider_page(self):
        """Charts beyond page one must participate in creation, selection, and final verification."""

        tasks = list(task_nodes(load_yaml("operations/ansible/site.yml")))
        indexed_uri_tasks = [
            (index, task, module_payload(task, "ansible.builtin.uri", "uri"))
            for index, task in enumerate(tasks)
            if isinstance(module_payload(task, "ansible.builtin.uri", "uri"), dict)
        ]
        chart_registers = {
            "better_stack_existing_charts",
            "better_stack_charts_readback",
            "better_stack_charts_final_readback",
        }
        boundaries = [
            (index, task)
            for index, task, payload in indexed_uri_tasks
            if str(payload.get("method", "")).upper() == "GET"
            and re.search(
                r"/api/v2/dashboards/[^/]+/charts(?:\?|$)",
                str(payload.get("url", "")),
            )
            and task.get("register") in chart_registers
        ]
        self.assertEqual(
            3,
            len(boundaries),
            "chart discovery, post-create selection, and final verification must retain distinct provider boundaries",
        )

        violations = []
        for boundary_index, boundary_task in boundaries:
            boundary_register = str(boundary_task.get("register", ""))
            page_followups = [
                task
                for index, task, payload in indexed_uri_tasks
                if index > boundary_index
                and str(payload.get("method", "")).upper() == "GET"
                and re.search(
                    r"/api/v2/dashboards/[^/]+/charts(?:\?|$)",
                    str(payload.get("url", "")),
                )
                and boundary_register in task_text(task)
                and "pagination" in task_text(task)
                and any(signal in task_text(task) for signal in (".next", ".last"))
            ]
            if not page_followups:
                violations.append(
                    f"{boundary_task.get('name')} must traverse chart pages beyond the first provider response"
                )
                continue

            followup_registers = [
                str(task.get("register", "")).strip()
                for task in page_followups
                if str(task.get("register", "")).strip()
            ]
            later_text = "\n".join(task_text(task) for task in tasks[boundary_index + 1 :])
            if not any(
                followup_register in later_text
                and ".results" in later_text
                and ".json.data" in later_text
                for followup_register in followup_registers
            ):
                violations.append(
                    f"{boundary_task.get('name')} must merge subsequent chart pages into provider decisions"
                )

        self.assertEqual(
            [],
            violations,
            "Better Stack chart pagination violations: " + "; ".join(violations),
        )

    def test_better_stack_final_alert_readback_rejects_duplicate_provider_resources(self):
        """Final acceptance must require exactly one provider alert for every declared identity."""

        tasks = list(task_nodes(load_yaml("operations/ansible/site.yml")))
        alert_assertion = next(
            (
                task
                for task in tasks
                if task.get("name")
                == "Verify Better Stack service and filesystem alerts are provider-side resources"
                and isinstance(module_payload(task, "ansible.builtin.assert", "assert"), dict)
            ),
            None,
        )
        self.assertIsNotNone(alert_assertion, "dashboard alerts must retain fail-closed provider readback")
        conditions = [
            str(condition)
            for condition in module_payload(alert_assertion, "ansible.builtin.assert", "assert").get("that", [])
        ]
        declared_names = (
            "GAM proxy service unhealthy",
            "GAM backend service unhealthy",
            "GAM postgresql service unhealthy",
            "GAM filesystem usage warning",
            "GAM filesystem usage critical",
        )
        violations = []
        for name in declared_names:
            matching_conditions = [condition for condition in conditions if name in condition]
            if len(matching_conditions) != 1:
                violations.append(f"final readback must have one unambiguous assertion for {name}")
                continue
            if re.search(r"list\s*\|\s*length\s*==\s*1", matching_conditions[0]) is None:
                violations.append(f"final readback must reject duplicate provider alerts named {name}")

        self.assertEqual(
            [],
            violations,
            "Better Stack dashboard-alert duplicate-state violations: " + "; ".join(violations),
        )

    def test_better_stack_dashboard_duplicates_fail_before_provider_id_selection(self):
        """Two exact-name dashboards must stop provisioning before either ID can be selected."""

        tasks = list(task_nodes(load_yaml("operations/ansible/site.yml")))
        readback_index = next(
            (
                index
                for index, task in enumerate(tasks)
                if task.get("register") == "better_stack_dashboards_readback"
            ),
            None,
        )
        selection_index = next(
            (
                index
                for index, task in enumerate(tasks)
                if isinstance(module_payload(task, "ansible.builtin.set_fact", "set_fact"), dict)
                and "better_stack_dashboard_id"
                in module_payload(task, "ansible.builtin.set_fact", "set_fact")
            ),
            None,
        )
        self.assertIsNotNone(readback_index, "dashboard candidates must be read from the provider")
        self.assertIsNotNone(selection_index, "one validated dashboard ID must eventually be selected")
        self.assertLess(readback_index, selection_index)

        preselection_tasks = tasks[readback_index + 1 : selection_index]
        preselection_text = "\n".join(task_text(task) for task in preselection_tasks)
        duplicate_guards = [
            task
            for task in preselection_tasks
            if isinstance(module_payload(task, "ansible.builtin.assert", "assert"), dict)
            and re.search(r"length\s*==\s*1", task_text(task))
        ]
        violations = []
        if not duplicate_guards:
            violations.append("exactly one dashboard match must be asserted before selecting data.id")
        if not (
            "better_stack_dashboards_readback" in preselection_text
            and "selectattr('attributes.name', 'equalto', better_stack_dashboard_name)" in preselection_text
        ):
            violations.append("the preselection gate must count exact-name matches from provider readback")

        self.assertEqual(
            [],
            violations,
            "Better Stack dashboard duplicate-state violations: " + "; ".join(violations),
        )

    def test_better_stack_final_readback_verifies_enabled_metrics_and_chart_contracts(self):
        """Final acceptance must prove complete collector and chart provider configuration."""

        tasks = list(task_nodes(load_yaml("operations/ansible/site.yml")))
        collector_assertion = next(
            (
                task
                for task in tasks
                if task.get("name") == "Verify the Better Stack collector remains metrics-only"
                and isinstance(module_payload(task, "ansible.builtin.assert", "assert"), dict)
            ),
            None,
        )
        chart_assertion = next(
            (
                task
                for task in tasks
                if task.get("name") == "Verify Better Stack production chart provider state"
                and isinstance(module_payload(task, "ansible.builtin.assert", "assert"), dict)
            ),
            None,
        )
        self.assertIsNotNone(collector_assertion, "collector configuration must retain fail-closed readback")
        self.assertIsNotNone(chart_assertion, "chart configuration must retain fail-closed readback")

        collector_text = task_text(collector_assertion)
        chart_text = task_text(chart_assertion)
        violations = []
        for component in ("ebpf_metrics", "ebpf_red_metrics", "metrics_databases"):
            if re.search(
                rf"components\.{component}[^\n]*is\s+sameas\s+true",
                collector_text,
            ) is None:
                violations.append(f"collector readback must verify enabled {component}=true")

        for field in (
            "attributes.chart_type",
            "attributes.queries",
            "query_type",
            "source_variable",
            "sql_query",
        ):
            if field not in chart_text:
                violations.append(f"chart readback must verify {field}")
        if not (
            len(re.findall(r"list\s*\|\s*length\s*==\s*1", chart_text)) >= 4
            or "better_stack_chart_contracts" in chart_text
        ):
            violations.append("chart readback must apply the complete contract to all four named charts")

        self.assertEqual(
            [],
            violations,
            "Better Stack final provider-readback violations: " + "; ".join(violations),
        )

    def test_better_stack_clean_provider_state_creates_every_named_resource(self):
        """A clean Better Stack team must be commissionable without manually copied IDs."""

        tasks = list(task_nodes(load_yaml("operations/ansible/site.yml")))
        uri_tasks = [
            task
            for task in tasks
            if isinstance(module_payload(task, "ansible.builtin.uri", "uri"), dict)
        ]

        def matching_requests(method: str, predicate) -> list[dict]:
            return [
                task
                for task in uri_tasks
                if str(module_payload(task, "ansible.builtin.uri", "uri").get("method", "")).upper() == method
                and predicate(str(module_payload(task, "ansible.builtin.uri", "uri").get("url", "")))
            ]

        collector_lists = matching_requests(
            "GET",
            lambda url: re.search(r"/api/v1/collectors(?:\?|$)", url) is not None,
        )
        collector_creates = matching_requests(
            "POST",
            lambda url: re.search(r"/api/v1/collectors$", url) is not None,
        )
        dashboard_lists = matching_requests(
            "GET",
            lambda url: re.search(r"/api/v2/dashboards(?:\?|$)", url) is not None,
        )
        dashboard_creates = matching_requests(
            "POST",
            lambda url: re.search(r"/api/v2/dashboards$", url) is not None,
        )
        chart_lists = matching_requests(
            "GET",
            lambda url: re.search(r"/api/v2/dashboards/[^/]+/charts(?:\?|$)", url) is not None,
        )
        chart_creates = matching_requests(
            "POST",
            lambda url: re.search(r"/api/v2/dashboards/[^/]+/charts$", url) is not None,
        )
        target_creates = matching_requests(
            "POST",
            lambda url: re.search(r"/api/v1/collectors/[^/]+/targets$", url) is not None,
        )
        alert_creates = matching_requests(
            "POST",
            lambda url: re.search(r"/api/v2/dashboards/[^/]+/charts/[^/]+/alerts$", url) is not None,
        )
        monitor_lists = matching_requests(
            "GET",
            lambda url: re.search(r"(?:/api/v2)?/monitors(?:\?|$)", url) is not None,
        )
        monitor_creates = matching_requests(
            "POST",
            lambda url: re.search(r"(?:/api/v2)?/monitors$", url) is not None,
        )

        self.assertTrue(collector_lists, "clean-state provisioning must list collectors by stable provider attributes")
        self.assertTrue(collector_creates, "clean-state provisioning must create the metrics collector when absent")
        collector_bodies = [module_payload(task, "ansible.builtin.uri", "uri").get("body", {}) for task in collector_creates]
        self.assertTrue(
            any(body.get("name") and body.get("platform") == "docker" for body in collector_bodies),
            "collector creation must use Better Stack's supported name and platform=docker fields",
        )

        self.assertTrue(dashboard_lists, "clean-state provisioning must discover dashboards before creation")
        self.assertTrue(dashboard_creates, "clean-state provisioning must create the GAM production dashboard")
        self.assertTrue(
            any(module_payload(task, "ansible.builtin.uri", "uri").get("body", {}).get("name") for task in dashboard_creates),
            "dashboard creation must provide the provider-required name",
        )

        self.assertTrue(chart_lists, "clean-state provisioning must discover charts on the selected dashboard")
        self.assertTrue(chart_creates, "clean-state provisioning must create the declared monitoring charts")
        chart_creation_text = "\n".join(task_text(task) for task in chart_creates).casefold()
        for chart in ("proxy", "backend", "postgresql", "filesystem"):
            self.assertIn(chart, chart_creation_text, f"clean-state provisioning must create the {chart} chart")
        self.assertIn("chart_type", chart_creation_text)
        self.assertIn("queries", chart_creation_text)
        self.assertIn("sql_expression", chart_creation_text)

        self.assertGreaterEqual(len(target_creates), 3, "proxy, backend, and PostgreSQL metric targets must be creatable")
        self.assertGreaterEqual(len(alert_creates), 5, "three service alerts and two filesystem alerts must be creatable")
        self.assertTrue(monitor_lists, "availability and TLS monitors must be discovered before creation")
        monitor_creation_text = "\n".join(task_text(task) for task in monitor_creates).casefold()
        self.assertIn("availability", monitor_creation_text)
        self.assertIn("tls", monitor_creation_text)

        creation_tasks = (
            collector_creates
            + dashboard_creates
            + chart_creates
            + target_creates
            + alert_creates
            + monitor_creates
        )
        unguarded = [task.get("name", "<unnamed>") for task in creation_tasks if not task.get("when")]
        self.assertEqual([], unguarded, "provider resource POSTs must be absent-state guarded: " + ", ".join(unguarded))

    def test_better_stack_managed_resources_reconcile_drift_and_verify_provider_state(self):
        """Replays must patch only observed drift and prove the resulting provider state."""

        tasks = list(task_nodes(load_yaml("operations/ansible/site.yml")))
        uri_tasks = [
            (index, task, module_payload(task, "ansible.builtin.uri", "uri"))
            for index, task in enumerate(tasks)
            if isinstance(module_payload(task, "ansible.builtin.uri", "uri"), dict)
        ]

        managed_sections = {
            "collector": lambda url: re.search(r"/api/v1/collectors/[^/]+$", url) is not None,
            "dashboard": lambda url: re.search(r"/api/v2/dashboards/[^/]+$", url) is not None,
            "charts": lambda url: re.search(r"/api/v2/dashboards/[^/]+/charts(?:/[^/]+)?$", url) is not None,
        }
        violations = []
        for resource, predicate in managed_sections.items():
            requests = [
                (index, task, payload)
                for index, task, payload in uri_tasks
                if predicate(str(payload.get("url", "")))
            ]
            patches = [item for item in requests if str(item[2].get("method", "")).upper() == "PATCH"]
            reads = [item for item in requests if str(item[2].get("method", "")).upper() == "GET"]
            if not patches:
                violations.append(f"{resource} drift must be reconciled with the official PATCH endpoint")
                continue
            if not reads:
                violations.append(f"{resource} provider state must be read for discovery and verification")
                continue
            for _, task, patch_request in patches:
                condition = str(task.get("when", ""))
                body = patch_request.get("body", {})
                mutable_fields = set(body) - {"name", "alert_type", "kind"}
                if not condition or not any(operator in condition for operator in ("!=", "not equalto", "difference")):
                    violations.append(f"{task.get('name', resource)} must PATCH only observed drift")
                if mutable_fields and not any(field in condition for field in mutable_fields):
                    violations.append(f"{task.get('name', resource)} must compare provider fields before PATCH")
            if max(index for index, _, _ in reads) < max(index for index, _, _ in patches):
                violations.append(f"{resource} must be read back after reconciliation")

        assertion_text = "\n".join(
            task_text(task)
            for task in tasks
            if isinstance(module_payload(task, "ansible.builtin.assert", "assert"), dict)
        ).casefold()
        for resource in ("collector", "dashboard", "chart"):
            if resource not in assertion_text:
                violations.append(f"{resource} provider readback must have a fail-closed assertion")

        self.assertEqual([], violations, "Better Stack provider reconciliation violations: " + "; ".join(violations))

    def test_better_stack_monitors_patch_only_drift_and_are_read_back_after_reconciliation(self):
        tasks = list(task_nodes(load_yaml("operations/ansible/site.yml")))
        monitor_requests = [
            (index, task, module_payload(task, "ansible.builtin.uri", "uri"))
            for index, task in enumerate(tasks)
            if isinstance(module_payload(task, "ansible.builtin.uri", "uri"), dict)
            and re.search(
                r"(?:/api/v2)?/monitors(?:/[^/?]+)?(?:\?|$)",
                str(module_payload(task, "ansible.builtin.uri", "uri").get("url", "")),
            )
        ]
        patches = [item for item in monitor_requests if str(item[2].get("method", "")).upper() == "PATCH"]
        reads = [item for item in monitor_requests if str(item[2].get("method", "")).upper() == "GET"]
        self.assertGreaterEqual(len(patches), 2, "availability and TLS monitor drift must be patchable")
        self.assertGreaterEqual(len(reads), 2, "monitors must be listed before and after reconciliation")
        self.assertGreater(
            max(index for index, _, _ in reads),
            max(index for index, _, _ in patches),
            "monitor provider state must be read back after create/update",
        )

        violations = []
        for _, task, request in patches:
            body = request.get("body", {})
            condition = str(task.get("when", ""))
            for field in body:
                if field == "pronounceable_name":
                    continue
                if f"attributes.{field}" not in condition:
                    violations.append(f"{task.get('name')} must compare attributes.{field} before PATCH")
            if not any(operator in condition for operator in ("!=", "not equalto", "difference")):
                violations.append(f"{task.get('name')} must skip PATCH when provider state already matches")

        final_read_index = max(index for index, _, _ in reads)
        final_assertions = "\n".join(
            task_text(task)
            for task in tasks[final_read_index + 1 :]
            if isinstance(module_payload(task, "ansible.builtin.assert", "assert"), dict)
        )
        for field in (
            "pronounceable_name",
            "url",
            "monitor_type",
            "check_frequency",
            "email",
            "push",
            "required_keyword",
            "confirmation_period",
            "ssl_expiration",
            "verify_ssl",
        ):
            if f"attributes.{field}" not in final_assertions:
                violations.append(f"monitor readback must verify attributes.{field}")
        if len(re.findall(r"list\s*\|\s*length\s*==\s*1", final_assertions)) < 2:
            violations.append("monitor readback must prove exactly one availability and one TLS monitor")

        self.assertEqual([], violations, "Better Stack monitor idempotency violations: " + "; ".join(violations))

    def test_better_stack_availability_monitor_normalizes_provider_http_method_on_replay(self):
        """Provider lowercase `get` must be accepted as converged and verified after readback."""

        tasks = list(task_nodes(load_yaml("operations/ansible/site.yml")))
        availability_patch = next(
            (
                task
                for task in tasks
                if isinstance(module_payload(task, "ansible.builtin.uri", "uri"), dict)
                and str(module_payload(task, "ansible.builtin.uri", "uri").get("method", "")).upper() == "PATCH"
                and module_payload(task, "ansible.builtin.uri", "uri").get("body", {}).get("pronounceable_name")
                == "GAM production availability"
            ),
            None,
        )
        self.assertIsNotNone(
            availability_patch,
            "the availability monitor must retain provider-state reconciliation",
        )

        patch_payload = module_payload(availability_patch, "ansible.builtin.uri", "uri")
        desired_http_method = str(patch_payload.get("body", {}).get("http_method", "")).casefold()
        self.assertEqual("get", desired_http_method, "the declared request method must normalize to the provider value")

        condition = str(availability_patch.get("when", ""))
        lower_provider_method_is_converged = bool(
            re.search(
                r"attributes\.http_method[^\n]*(?:\|\s*lower[^\n]*)?!=\s*'get'",
                condition,
                flags=re.IGNORECASE,
            )
        ) and "!= 'GET'" not in condition

        final_readback = next(
            (
                task
                for task in tasks
                if task.get("name") == "Verify Better Stack availability and TLS monitor provider state"
                and isinstance(module_payload(task, "ansible.builtin.assert", "assert"), dict)
            ),
            None,
        )
        self.assertIsNotNone(final_readback, "monitor reconciliation must retain fail-closed provider readback")
        readback_text = task_text(final_readback)
        readback_verifies_normalized_method = "attributes.http_method" in readback_text and bool(
            re.search(
                r"attributes\.http_method.*(?:\|\s*lower.*)?equalto[^\n]*'get'",
                readback_text,
                flags=re.IGNORECASE | re.DOTALL,
            )
        )

        violations = []
        if not lower_provider_method_is_converged:
            violations.append("provider http_method='get' must not trigger a replay PATCH")
        if not readback_verifies_normalized_method:
            violations.append("final provider readback must prove normalized attributes.http_method='get'")

        self.assertEqual(
            [],
            violations,
            "Better Stack availability monitor normalization violations: " + "; ".join(violations),
        )

    def test_better_stack_clean_host_provisions_supported_metrics_collector_and_service_alerts(self):
        tasks = list(task_nodes(load_yaml("operations/ansible/site.yml")))
        installer_downloaded = any(
            module_payload(task, "ansible.builtin.get_url", "get_url") is not None
            and "better-stack" in task_text(task).casefold()
            for task in tasks
        )
        installer_executed = any(is_official_better_stack_installer_execution(task) for task in tasks)
        self.assertTrue(
            installer_downloaded and installer_executed,
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

        creation_target_tasks = [
            task
            for task in target_tasks
            if str(module_payload(task, "ansible.builtin.uri", "uri").get("method", "")).upper() == "POST"
        ]
        target_bodies = [
            module_payload(task, "ansible.builtin.uri", "uri").get("body", {})
            for task in creation_target_tasks
        ]
        for service, required_kind, required_fields, forbidden_fields in (
            ("proxy", "prometheus", {"host", "service", "endpoint"}, {"port"}),
            ("backend", "prometheus", {"host", "service", "endpoint"}, {"port"}),
            (
                "postgresql",
                "postgres",
                {"host", "port", "username", "password", "ssl_mode"},
                set(),
            ),
        ):
            matching = [
                (task, body)
                for task, body in zip(creation_target_tasks, target_bodies)
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
                    and body.get("kind") == required_kind
                    and required_fields.issubset(body)
                    and forbidden_fields.isdisjoint(body)
                    for _, body in matching
                ),
                f"the {service} target must use the official {required_kind} provider fields",
            )

        postgres_bodies = [body for body in target_bodies if isinstance(body, dict) and body.get("kind") == "postgres"]
        self.assertTrue(postgres_bodies, "the PostgreSQL target must use Better Stack's supported postgres kind")
        production_variables = load_yaml("operations/ansible/group_vars/production.yml")
        postgres_port_declaration = str(production_variables.get("better_stack_postgresql_target_port", ""))
        self.assertTrue(
            all(
                body.get("ssl_mode") == "require"
                and (
                    isinstance(body.get("port"), int)
                    and not isinstance(body.get("port"), bool)
                    or (
                        "better_stack_postgresql_target_port" in str(body.get("port", ""))
                        and (
                            re.search(r"\|\s*int\b", str(body.get("port", ""))) is not None
                            or re.search(r"\|\s*int\b", postgres_port_declaration) is not None
                        )
                    )
                )
                for body in postgres_bodies
            ),
            "the PostgreSQL target must serialize an integer port and provider-supported ssl_mode=require",
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

    def test_restore_interrupts_each_blocking_step_at_the_shared_total_recovery_deadline(self):
        restore = read("operations/recovery/restore/restore.sh")
        budget_wrappers = []
        for match in re.finditer(
            r"(?ms)^\s*(?P<name>[a-z_][a-z0-9_]*)\(\)\s*\{(?P<body>.*?)^\}",
            restore,
        ):
            body = match.group("body")
            if "TOTAL_RECOVERY_DEADLINE" in body and re.search(r"(?m)\btimeout\b", body):
                budget_wrappers.append((match.group("name"), body))

        self.assertEqual(
            1,
            len(budget_wrappers),
            "restore must define one interruptible wrapper that derives each command timeout from TOTAL_RECOVERY_DEADLINE",
        )
        wrapper_name, wrapper_body = budget_wrappers[0]
        self.assertRegex(wrapper_body, r"date\s+\+%s", "the wrapper must calculate the remaining shared budget")
        self.assertRegex(
            wrapper_body,
            r"(?m)\btimeout\b.*(?:\"\$@\"|\$@)",
            "the wrapper must interrupt its supplied blocking command when the remaining budget expires",
        )

        blocking_steps = {
            "S3 restore request": r"aws\s+s3api\s+restore-object\b",
            "S3 archive download": r"aws\s+s3\s+cp\b",
            "age decryption": r"age\s+--decrypt\b",
            "archive extraction": r"tar\s+--extract\b",
            "representative application access": r"bash\s+-Eeuo\s+pipefail\s+-c\s+\"\$REPRESENTATIVE_ACCESS_CHECK_COMMAND\"",
            "final restoration verification": r"/usr/local/libexec/gam-verify-restoration\b",
        }
        for label, command_pattern in blocking_steps.items():
            with self.subTest(blocking_step=label):
                self.assertRegex(
                    restore,
                    rf"(?m)^\s*{re.escape(wrapper_name)}\s+(?:--\s+)?{command_pattern}",
                    f"{label} must execute inside the interruptible shared-deadline wrapper",
                )

        repeated_blocking_steps = {
            "S3 object metadata request": (r"aws\s+s3api\s+head-object\b", 2),
            "isolated database creation": (r"createdb\b", 2),
            "isolated SQL operation": (r"psql\b", 3),
        }
        for label, (command_pattern, minimum) in repeated_blocking_steps.items():
            with self.subTest(repeated_blocking_step=label):
                all_invocations = re.findall(command_pattern, restore)
                wrapped_invocations = re.findall(
                    rf"{re.escape(wrapper_name)}\s+(?:--\s+)?{command_pattern}",
                    restore,
                )
                self.assertGreaterEqual(
                    len(all_invocations),
                    minimum,
                    f"the restore contract must retain all required {label} operations",
                )
                self.assertEqual(
                    len(all_invocations),
                    len(wrapped_invocations),
                    f"every {label} must execute inside the interruptible shared-deadline wrapper",
                )

        wrapped_pg_restore = re.findall(
            rf"(?m)^\s*{re.escape(wrapper_name)}\s+(?:--\s+)?pg_restore\b",
            restore,
        )
        self.assertGreaterEqual(
            len(wrapped_pg_restore),
            2,
            "both pg_restore archive inspection and database restoration must be interruptible",
        )

    def test_better_stack_collector_target_payloads_are_kind_specific(self):
        """Protect provider-supported Prometheus and PostgreSQL target fields."""

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

        variables = load_yaml("operations/ansible/group_vars/production.yml")
        external_port_inputs = {
            "BETTER_STACK_POSTGRESQL_TARGET_PORT": "5432",
        }

        def render_group_variable(variable: str):
            expression = str(variables.get(variable, ""))
            lookup = re.fullmatch(
                r"\{\{\s*lookup\(\s*['\"]env['\"]\s*,\s*['\"](?P<environment>[A-Z0-9_]+)['\"]\s*\)"
                r"(?P<filters>(?:\s*\|\s*[a-zA-Z_][a-zA-Z0-9_]*)*)\s*\}\}",
                expression,
            )
            if lookup is None or lookup.group("environment") not in external_port_inputs:
                return expression
            value = external_port_inputs[lookup.group("environment")]
            if re.search(r"\|\s*int\b", lookup.group("filters")):
                value = int(value)
            return value

        def render_port_expression(expression):
            parameter = re.fullmatch(
                r"\{\{\s*(?P<variable>better_stack_postgresql_target_port)"
                r"(?P<filters>(?:\s*\|\s*[a-zA-Z_][a-zA-Z0-9_]*)*)\s*\}\}",
                str(expression),
            )
            if parameter is None:
                return None
            value = render_group_variable(parameter.group("variable"))
            if re.search(r"\|\s*int\b", parameter.group("filters")):
                value = int(value)
            return value

        violations = []
        for task in target_tasks:
            request = module_payload(task, "ansible.builtin.uri", "uri")
            body = request.get("body", {})
            method = str(request.get("method", "")).upper()
            task_name = str(task.get("name", "")).casefold()
            service = next(
                (candidate for candidate in ("proxy", "backend", "postgresql") if candidate in task_name),
                None,
            )
            if not isinstance(body, dict) or service is None:
                continue

            kind = "postgres" if service == "postgresql" else "prometheus"
            if kind == "postgres":
                mutable_fields = {"host", "port", "username", "password", "ssl_mode"}
            else:
                mutable_fields = {"host", "service", "endpoint"}
            expected_fields = mutable_fields | ({"kind"} if method == "POST" else set())
            if set(body) != expected_fields:
                violations.append(
                    f"{method} {kind} target fields must be {sorted(expected_fields)}, observed {sorted(body)}"
                )
            if method == "POST" and body.get("kind") != kind:
                violations.append(f"POST {service} target must declare immutable kind={kind}")
            if method == "PATCH" and "kind" in body:
                violations.append(f"PATCH {service} target must exclude the provider-immutable kind field")

            if kind == "prometheus":
                if "port" in body:
                    violations.append("Prometheus target payloads must not send the unsupported optional port field")
                continue

            source_port = body.get("port")
            rendered_port = render_port_expression(source_port)
            if rendered_port is None:
                violations.append(
                    "PostgreSQL target port must remain a parameterized Better Stack port expression"
                )
                continue

            provider_payload = json.loads(json.dumps({**body, "port": rendered_port}))
            if not isinstance(provider_payload["port"], int) or isinstance(provider_payload["port"], bool):
                violations.append(
                    "PostgreSQL target port must render and serialize as an integer, "
                    f"observed {provider_payload['port']!r} from {source_port!r}"
                )
            if provider_payload.get("ssl_mode") != "require":
                violations.append("PostgreSQL target must send the provider-supported ssl_mode=require field")
            if method in {"POST", "PATCH"}:
                expected_credentials = {
                    "username": "{{ better_stack_postgresql_target_username }}",
                    "password": "{{ better_stack_postgresql_target_password }}",
                }
                for field, expected in expected_credentials.items():
                    if str(body.get(field, "")) != expected:
                        violations.append(
                            f"PostgreSQL target {field} must bind to {expected}"
                        )
                if task.get("no_log") is not True:
                    violations.append(
                        f"PostgreSQL target {method} must be no_log because its password is write-only"
                    )

        self.assertEqual([], violations, "Better Stack collector target schema violations: " + "; ".join(violations))

    def test_better_stack_postgresql_password_only_rotation_patches_write_only_credentials_safely(self):
        """Require in-place PATCH when only externally custodied credentials change."""

        tasks = list(task_nodes(load_yaml("operations/ansible/site.yml")))
        target_mutations = [
            task
            for task in tasks
            if isinstance(module_payload(task, "ansible.builtin.uri", "uri"), dict)
            and "/api/v1/collectors/" in str(
                module_payload(task, "ansible.builtin.uri", "uri").get("url", "")
            )
            and "/targets" in str(
                module_payload(task, "ansible.builtin.uri", "uri").get("url", "")
            )
            and str(
                module_payload(task, "ansible.builtin.uri", "uri").get("method", "")
            ).upper() in {"POST", "PATCH", "DELETE"}
        ]
        postgresql_creations = [
            task
            for task in target_mutations
            if str(
                module_payload(task, "ansible.builtin.uri", "uri").get("method", "")
            ).upper() == "POST"
            and module_payload(task, "ansible.builtin.uri", "uri").get("body", {}).get("kind")
            == "postgres"
        ]
        postgresql_credential_patches = [
            task
            for task in target_mutations
            if str(
                module_payload(task, "ansible.builtin.uri", "uri").get("method", "")
            ).upper() == "PATCH"
            and "postgres" in task_text(task).casefold()
            and "better_stack_postgresql_target_password" in task_text(task)
        ]
        credential_rotation_deletions = [
            task
            for task in target_mutations
            if str(
                module_payload(task, "ansible.builtin.uri", "uri").get("method", "")
            ).upper() == "DELETE"
            and "credential" in task_text(task).casefold()
        ]

        self.assertTrue(
            postgresql_creations,
            "credential rotation must retain a provider-supported PostgreSQL POST payload",
        )
        self.assertTrue(
            postgresql_credential_patches,
            "password rotation must PATCH the existing PostgreSQL target through its provider item.id",
        )
        self.assertFalse(
            credential_rotation_deletions,
            "credential rotation must not delete a functioning PostgreSQL target before its replacement is known to succeed",
        )
        self.assertTrue(
            all(
                task.get("no_log") is True
                and task.get("ignore_errors") is not True
                and task.get("failed_when") is not False
                and module_payload(task, "ansible.builtin.uri", "uri").get("status_code") in ([200], 200)
                and "item.id" in str(
                    module_payload(task, "ansible.builtin.uri", "uri").get("url", "")
                )
                for task in postgresql_credential_patches
            ),
            "credential PATCH must fail closed, address provider item.id, and remain no_log",
        )

        fingerprint_directories = [
            task
            for task in tasks
            if isinstance(module_payload(task, "ansible.builtin.file", "file"), dict)
            and "better_stack_postgresql_target_credentials_fingerprint_file | dirname"
            in str(module_payload(task, "ansible.builtin.file", "file").get("path", ""))
        ]
        self.assertTrue(fingerprint_directories)
        self.assertTrue(all(
            str(module_payload(task, "ansible.builtin.file", "file").get("mode")) == "0700"
            for task in fingerprint_directories
        ), "the credential-state directory must remain restricted to its owner")

        fingerprint_derivations = [
            task
            for task in tasks
            if isinstance(
                module_payload(task, "ansible.builtin.set_fact", "set_fact"), dict
            )
            and "better_stack_postgresql_target_credentials_desired_fingerprint"
            in module_payload(task, "ansible.builtin.set_fact", "set_fact")
        ]
        self.assertTrue(fingerprint_derivations)
        self.assertTrue(all(
            task.get("no_log") is True
            and "better_stack_postgresql_target_password" in task_text(task)
            and "hash('sha256')" in task_text(task)
            for task in fingerprint_derivations
        ), "credential fingerprints must hash the external password under no_log")

        fingerprint_persistence = [
            task
            for task in tasks
            if isinstance(module_payload(task, "ansible.builtin.copy", "copy"), dict)
            and module_payload(task, "ansible.builtin.copy", "copy").get("dest")
            == "{{ better_stack_postgresql_target_credentials_fingerprint_file }}"
        ]
        self.assertTrue(fingerprint_persistence)
        self.assertTrue(all(
            any(
                tasks.index(patch_task) < tasks.index(state_task)
                for state_task in fingerprint_persistence
            )
            for patch_task in postgresql_credential_patches
        ), "protected state may be advanced only after the credential PATCH succeeds")
        persisted_contents = [
            str(module_payload(task, "ansible.builtin.copy", "copy").get("content", ""))
            for task in fingerprint_persistence
        ]
        self.assertTrue(all(
            str(module_payload(task, "ansible.builtin.copy", "copy").get("mode")) == "0600"
            and task.get("no_log") is True
            for task in fingerprint_persistence
        ), "credential state must remain mode 0600 and its writes must remain no_log")
        self.assertTrue(all(
            "better_stack_postgresql_target_credentials_desired_fingerprint" in content
            for content in persisted_contents
        ), "protected credential state must retain the one-way credential digest")
        self.assertTrue(all(
            "better_stack_postgresql_target_username" not in content
            and "better_stack_postgresql_target_password" not in content
            for content in persisted_contents
        ), "protected credential state must never persist plaintext credential expressions")

        fingerprint_reads = [
            task
            for task in tasks
            if any(
                isinstance(module_payload(task, module, module.rsplit(".", 1)[-1]), dict)
                and module_payload(task, module, module.rsplit(".", 1)[-1]).get("path")
                == "{{ better_stack_postgresql_target_credentials_fingerprint_file }}"
                or isinstance(module_payload(task, module, module.rsplit(".", 1)[-1]), dict)
                and module_payload(task, module, module.rsplit(".", 1)[-1]).get("src")
                == "{{ better_stack_postgresql_target_credentials_fingerprint_file }}"
                for module in ("ansible.builtin.stat", "ansible.builtin.slurp")
            )
        ]
        self.assertTrue(fingerprint_reads)
        self.assertTrue(all(
            task.get("no_log") is True for task in fingerprint_reads
        ), "credential-state inspection must not disclose the persisted digest")

    def test_better_stack_postgresql_credential_state_binds_the_provider_target_identity(self):
        """Reject a matching credential digest that belongs to a replaced provider target."""

        tasks = list(task_nodes(load_yaml("operations/ansible/site.yml")))
        state_writes = [
            task
            for task in tasks
            if isinstance(module_payload(task, "ansible.builtin.copy", "copy"), dict)
            and module_payload(task, "ansible.builtin.copy", "copy").get("dest")
            == "{{ better_stack_postgresql_target_credentials_fingerprint_file }}"
        ]
        currentness_decisions = [
            task
            for task in tasks
            if isinstance(
                module_payload(task, "ansible.builtin.set_fact", "set_fact"), dict
            )
            and any(
                str(name).endswith("postgresql_target_credentials_current")
                for name in module_payload(
                    task, "ansible.builtin.set_fact", "set_fact"
                )
            )
        ]

        self.assertTrue(state_writes, "the protected credential state must be persisted")
        self.assertTrue(currentness_decisions, "credential currentness must be derived before mutation")
        provider_identity_pattern = re.compile(
            r"(?:postgresql[^\n]*target[^\n]*(?:id|identity)"
            r"|(?:id|identity)[^\n]*postgresql[^\n]*target)",
            re.IGNORECASE,
        )
        persisted_state_text = "\n".join(
            str(module_payload(task, "ansible.builtin.copy", "copy").get("content", ""))
            for task in state_writes
        )
        currentness_text = "\n".join(task_text(task) for task in currentness_decisions)
        self.assertIn(
            "better_stack_postgresql_target_credentials_desired_fingerprint",
            persisted_state_text,
            "protected state must bind provider identity alongside the one-way credential digest",
        )
        self.assertNotIn(
            "better_stack_postgresql_target_username",
            persisted_state_text,
            "provider-identity state must not persist the plaintext username expression",
        )
        self.assertNotIn(
            "better_stack_postgresql_target_password",
            persisted_state_text,
            "provider-identity state must not persist the plaintext password expression",
        )
        self.assertRegex(
            persisted_state_text,
            provider_identity_pattern,
            "protected local credential state must bind the digest to the applied PostgreSQL provider target identity",
        )
        self.assertRegex(
            currentness_text,
            provider_identity_pattern,
            "credential currentness must compare the persisted provider identity with the discovered PostgreSQL target",
        )
        self.assertTrue(all(
            task.get("no_log") is True for task in state_writes + currentness_decisions
        ), "provider-identity credential state must remain redacted")

    def test_better_stack_postgresql_state_binds_exact_mutation_response_across_paginated_readback(self):
        """Bind protected state to the exact POST/PATCH result, not a same-shaped target."""

        tasks = list(task_nodes(load_yaml("operations/ansible/site.yml")))
        postgresql_mutations = [
            task for task in tasks
            if isinstance(module_payload(task, "ansible.builtin.uri", "uri"), dict)
            and "postgresql" in str(task.get("name", "")).casefold()
            and str(module_payload(task, "ansible.builtin.uri", "uri").get("method", "")).upper()
            in {"POST", "PATCH"}
        ]
        state_writes = [
            task for task in tasks
            if isinstance(module_payload(task, "ansible.builtin.copy", "copy"), dict)
            and module_payload(task, "ansible.builtin.copy", "copy").get("dest")
            == "{{ better_stack_postgresql_target_credentials_fingerprint_file }}"
        ]
        post_mutation_reads = [
            task for task in tasks
            if isinstance(module_payload(task, "ansible.builtin.uri", "uri"), dict)
            and "postgresql" in str(task.get("name", "")).casefold()
            and "read back" in str(task.get("name", "")).casefold()
        ]

        self.assertTrue(postgresql_mutations)
        self.assertTrue(all(task.get("register") for task in postgresql_mutations),
                        "every POST/PATCH must retain its exact provider response identity")
        mutation_registers = {str(task.get("register")) for task in postgresql_mutations}
        state_text = "\n".join(task_text(task) for task in state_writes)
        self.assertTrue(any(register in state_text for register in mutation_registers),
                        "protected state must bind the exact successful mutation response ID")
        self.assertTrue(post_mutation_reads)
        readback_text = "\n".join(task_text(task) for task in post_mutation_reads)
        self.assertRegex(readback_text, r"(?i)(?:next_page|total_pages|pagination|page=\{\{)",
                         "post-mutation verification must consume every provider page")
        self.assertTrue(all(task.get("no_log") is True for task in postgresql_mutations + state_writes),
                        "mutation identity and protected-state handling must remain redacted")

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

    def test_better_stack_target_readback_verifies_every_reconciled_connection_field(self):
        tasks = list(task_nodes(load_yaml("operations/ansible/site.yml")))
        readback_index = next(
            (
                index
                for index, task in enumerate(tasks)
                if task.get("register") == "better_stack_collector_targets_readback"
            ),
            None,
        )
        self.assertIsNotNone(readback_index, "collector targets must be read back after reconciliation")
        next_provider_section = next(
            (
                index
                for index, task in enumerate(tasks[readback_index + 1 :], start=readback_index + 1)
                if task.get("register") == "better_stack_existing_dashboard_alerts"
            ),
            len(tasks),
        )
        verification_text = "\n".join(
            task_text(task)
            for task in tasks[readback_index + 1 : next_provider_section]
            if isinstance(module_payload(task, "ansible.builtin.assert", "assert"), dict)
        )
        self.assertTrue(verification_text, "provider target readback must be asserted")

        expected_connections = {
            "proxy": {
                "kind": "prometheus",
                "host": "better_stack_proxy_target_host",
                "service": "better_stack_proxy_target_service",
                "endpoint": "better_stack_proxy_target_endpoint",
            },
            "backend": {
                "kind": "prometheus",
                "host": "better_stack_backend_target_host",
                "service": "better_stack_backend_target_service",
                "endpoint": "better_stack_backend_target_endpoint",
            },
            "postgresql": {
                "kind": "postgres",
                "host": "better_stack_postgresql_target_host",
                "port": "better_stack_postgresql_target_port",
                "ssl_mode": "require",
            },
        }
        violations = []
        for service, fields in expected_connections.items():
            for field, expected in fields.items():
                if not re.search(
                    rf"(?is)selectattr\(\s*['\"]attributes\.{re.escape(field)}['\"].{{0,180}}{re.escape(expected)}",
                    verification_text,
                ):
                    violations.append(
                        f"{service} readback must verify attributes.{field} against {expected}"
                    )

        self.assertEqual([], violations, "Better Stack target readback violations: " + "; ".join(violations))

    def test_better_stack_target_reconciliation_converges_drift_without_duplicates(self):
        tasks = list(task_nodes(load_yaml("operations/ansible/site.yml")))
        target_tasks = [
            task
            for task in tasks
            if isinstance(module_payload(task, "ansible.builtin.uri", "uri"), dict)
            and "/api/v1/collectors/" in str(module_payload(task, "ansible.builtin.uri", "uri").get("url", ""))
            and "/targets" in str(module_payload(task, "ansible.builtin.uri", "uri").get("url", ""))
        ]

        def target_task(service: str, method: str) -> dict:
            return next(
                task
                for task in target_tasks
                if service in str(task.get("name", "")).casefold()
                and str(module_payload(task, "ansible.builtin.uri", "uri").get("method", "")).upper() == method
            )

        def creation_identity_fields(task: dict) -> set[str]:
            return set(
                re.findall(
                    r"selectattr\(\s*['\"]attributes\.([a-z_]+)['\"]\s*,\s*['\"]equalto['\"]",
                    str(task.get("when", "")),
                )
            )

        def patch_identity_fields(task: dict) -> set[str]:
            return set(
                re.findall(
                    r"item\.attributes\.([a-z_]+)[^=\n]{0,120}==",
                    str(task.get("when", "")),
                )
            )

        scenarios = {
            "postgresql stale port": {
                "service": "postgresql",
                "desired": {
                    "kind": "postgres",
                    "host": "postgres",
                    "port": 5432,
                    "username": "betterstack_metrics",
                    "password": "rotated-postgresql-metrics-secret",
                    "ssl_mode": "require",
                },
                "existing": {
                    "id": "target-db",
                    "kind": "postgres",
                    "host": "postgres",
                    "port": 5433,
                    "username": "betterstack_metrics",
                    "ssl_mode": "require",
                },
                "provider_identity": {"kind", "host"},
            },
            "postgresql stale host": {
                "service": "postgresql",
                "desired": {
                    "kind": "postgres",
                    "host": "postgres",
                    "port": 5432,
                    "username": "betterstack_metrics",
                    "password": "rotated-postgresql-metrics-secret",
                    "ssl_mode": "require",
                },
                "existing": {
                    "id": "target-db",
                    "kind": "postgres",
                    "host": "old-postgres",
                    "port": 5432,
                    "username": "betterstack_metrics",
                    "ssl_mode": "require",
                },
                "provider_identity": {"kind"},
            },
            "proxy stale host": {
                "service": "proxy",
                "desired": {
                    "kind": "prometheus",
                    "host": "caddy:2019",
                    "service": "caddy",
                    "endpoint": "/metrics",
                },
                "existing": {
                    "id": "target-proxy",
                    "kind": "prometheus",
                    "host": "old-caddy:2019",
                    "service": "caddy",
                    "endpoint": "/metrics",
                },
                "provider_identity": {"kind", "service"},
            },
        }

        for case, scenario in scenarios.items():
            with self.subTest(provider_transition=case):
                service = scenario["service"]
                desired = scenario["desired"]
                provider_targets = [dict(scenario["existing"])]
                create_task = target_task(service, "POST")
                patch_task = target_task(service, "PATCH")
                create_fields = creation_identity_fields(create_task)
                patch_fields = patch_identity_fields(patch_task)
                self.assertTrue(create_fields, "creation must have a stable existing-target identity guard")
                self.assertTrue(patch_fields, "PATCH must select the existing logical target")

                should_create = not any(
                    all(target.get(field) == desired.get(field) for field in create_fields)
                    for target in provider_targets
                )
                duplicate_post_rejected = False
                if should_create:
                    provider_identity = scenario["provider_identity"]
                    duplicate_post_rejected = any(
                        all(target.get(field) == desired.get(field) for field in provider_identity)
                        for target in provider_targets
                    )
                    if not duplicate_post_rejected:
                        provider_targets.append({
                            "id": "unexpected-created-target",
                            **{
                                field: value
                                for field, value in desired.items()
                                if field != "password"
                            },
                        })

                if not duplicate_post_rejected:
                    patch_body = module_payload(patch_task, "ansible.builtin.uri", "uri").get("body", {})
                    if service == "postgresql":
                        self.assertEqual(
                            "{{ better_stack_postgresql_target_username }}",
                            patch_body.get("username"),
                        )
                        self.assertEqual(
                            "{{ better_stack_postgresql_target_password }}",
                            patch_body.get("password"),
                        )
                        self.assertTrue(
                            patch_task.get("no_log") is True,
                            "write-only PostgreSQL credentials must remain redacted during PATCH",
                        )
                    for target in provider_targets:
                        if all(target.get(field) == desired.get(field) for field in patch_fields):
                            for field in patch_body:
                                if field != "password":
                                    target[field] = desired[field]

                self.assertFalse(
                    duplicate_post_rejected,
                    "reconciliation must identify and PATCH a drifted logical target before attempting duplicate POST",
                )
                readable_desired = {
                    field: value
                    for field, value in desired.items()
                    if field != "password"
                }
                desired_targets = [
                    target
                    for target in provider_targets
                    if all(target.get(field) == value for field, value in readable_desired.items())
                ]
                self.assertEqual(1, len(desired_targets), "reconciliation must converge to exactly one desired target")
                self.assertEqual(1, len(provider_targets), "reconciliation must leave no stale or created duplicate target")
                self.assertTrue(
                    all("password" not in target for target in provider_targets),
                    "provider readback must never expose the write-only PostgreSQL password",
                )

        readback_index = next(
            index
            for index, task in enumerate(tasks)
            if task.get("register") == "better_stack_collector_targets_readback"
        )
        next_section = next(
            (
                index
                for index, task in enumerate(tasks[readback_index + 1 :], start=readback_index + 1)
                if task.get("register") == "better_stack_existing_dashboard_alerts"
            ),
            len(tasks),
        )
        readback_assertions = "\n".join(
            task_text(task)
            for task in tasks[readback_index + 1 : next_section]
            if isinstance(module_payload(task, "ansible.builtin.assert", "assert"), dict)
        )
        unique_assertions = re.findall(r"list\s*\|\s*length\s*==\s*1", readback_assertions)
        self.assertGreaterEqual(
            len(unique_assertions),
            3,
            "final readback must prove exactly one proxy, backend, and PostgreSQL target rather than mere existence",
        )
        readback_tasks = [
            task
            for task in tasks[readback_index + 1 : next_section]
            if isinstance(module_payload(task, "ansible.builtin.assert", "assert"), dict)
        ]
        readback_conditions = [
            str(condition)
            for task in readback_tasks
            for condition in module_payload(task, "ansible.builtin.assert", "assert").get("that", [])
        ]
        postgresql_uniqueness = [
            condition
            for condition in readback_conditions
            if re.search(
                r"(?is)selectattr\(\s*['\"]attributes\.kind['\"]\s*,\s*['\"]equalto['\"]\s*,\s*['\"]postgres['\"]\s*\)",
                condition,
            )
            and re.search(r"(?is)\|\s*list\s*\|\s*length\s*==\s*1", condition)
            and not any(
                f"attributes.{field}" in condition
                for field in ("host", "port", "ssl_mode")
            )
        ]
        self.assertTrue(
            postgresql_uniqueness,
            "final readback must prove exactly one PostgreSQL target across all hosts before validating desired connection fields",
        )

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
            for task in tasks
            if isinstance(module_payload(task, "ansible.builtin.uri", "uri"), dict)
            if str(module_payload(task, "ansible.builtin.uri", "uri").get("method", "")).upper() == "PATCH"
            and re.search(
                r"/api/v2/alerts/[^/?]+$",
                str(module_payload(task, "ansible.builtin.uri", "uri").get("url", "")),
            )
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

        def uses_installer_deployment_identity(task: dict) -> bool:
            return is_durable_better_stack_collector_probe(task)

        preinstall_probe = next(
            (task for task in tasks if task.get("register") == "better_stack_collector_preinstall_state"),
            None,
        )
        verification_probe = next(
            (task for task in tasks if task.get("register") == "better_stack_collector_status"),
            None,
        )
        if preinstall_probe is None or not uses_installer_deployment_identity(preinstall_probe):
            violations.append(
                "collector replay must inspect the installer's better-stack-collector project or a persistent compose file"
            )
        if verification_probe is None or not uses_installer_deployment_identity(verification_probe):
            violations.append(
                "collector verification must address the installer's better-stack-collector project or labeled containers"
            )

        pre_install_state = preinstall_probe is not None and uses_installer_deployment_identity(preinstall_probe)
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

    def test_machine_writer_preflight_rejects_a_skipped_registered_key_without_external_credentials(self):
        site_tasks = list(task_nodes(load_yaml("operations/ansible/site.yml")))
        preflight = next(
            (
                task
                for task in site_tasks
                if task.get("name") == "Require nonempty machine writer credentials before production activation"
            ),
            None,
        )
        self.assertIsNotNone(preflight, "production activation needs a machine writer credential preflight")
        payload = module_payload(preflight, "ansible.builtin.assert", "assert") if preflight else {}
        condition_text = "\n".join(str(condition) for condition in payload.get("that", []))

        self.assertRegex(
            condition_text,
            r"backup_writer_access_key\.(?:skipped|skip_reason)|backup_writer_access_key\s+is\s+not\s+skipped",
            "a registered result from a skipped create-access-key task must not count as usable credentials",
        )
        for field in ("AccessKeyId", "SecretAccessKey"):
            self.assertIn(
                field,
                condition_text,
                f"preflight must validate the created key's nonempty {field} or require its external credential counterpart",
            )

    def test_no_rotation_replay_guards_skipped_key_result_and_accepts_external_credentials(self):
        aws_tasks = list(task_nodes(load_yaml("operations/ansible/aws-resources.yml")))
        create_task = next(
            task
            for task in aws_tasks
            if "create-access-key" in command_argv(task)
            and task.get("register") == "backup_writer_access_key"
        )
        self.assertIn(
            "backup_writer_rotation_required",
            str(create_task.get("when", "")),
            "the no-rotation replay path must skip access-key creation",
        )

        follow_up_names = (
            "Require nonempty machine writer credentials before activation",
            "Materialize the active machine credential on the production host",
            "Retire prior machine backup-writer access keys after materialization",
        )
        for task_name in follow_up_names:
            with self.subTest(task=task_name):
                task = next(task for task in aws_tasks if task.get("name") == task_name)
                when_values = task.get("when", [])
                if isinstance(when_values, str):
                    when_values = [when_values]
                condition_text = "\n".join(str(condition) for condition in when_values)
                skipped_guard = re.search(
                    r"backup_writer_access_key\s+is\s+not\s+skipped|"
                    r"not\s+backup_writer_access_key\.skipped\s*\|\s*default\(false\)|"
                    r"backup_writer_access_key\.skipped\s*\|\s*default\(false\)"
                    r"(?:\s*\|\s*bool)?\s*==\s*false",
                    condition_text,
                )
                self.assertIsNotNone(
                    skipped_guard,
                    f"{task_name} must reject the defined-but-skipped registered result before reading rc or stdout",
                )
                rc_reference = condition_text.find("backup_writer_access_key.rc")
                if rc_reference >= 0:
                    self.assertLess(
                        skipped_guard.start(),
                        rc_reference,
                        f"{task_name} must guard skipped results before dereferencing backup_writer_access_key.rc",
                    )

        site_tasks = list(task_nodes(load_yaml("operations/ansible/site.yml")))
        activation_preflight = next(
            task
            for task in site_tasks
            if task.get("name") == "Require nonempty machine writer credentials before production activation"
        )
        preflight_text = task_text(activation_preflight)
        self.assertIn("GAM_BACKUP_ACCESS_KEY_ID", preflight_text)
        self.assertIn("GAM_BACKUP_SECRET_ACCESS_KEY", preflight_text)
        self.assertRegex(
            preflight_text,
            r"(?is)backup_writer_access_key.{0,400}\bor\b.{0,160}GAM_BACKUP_ACCESS_KEY_ID",
            "a no-rotation replay must accept a nonempty externally supplied writer access key",
        )
        self.assertRegex(
            preflight_text,
            r"(?is)backup_writer_access_key.{0,400}\bor\b.{0,160}GAM_BACKUP_SECRET_ACCESS_KEY",
            "a no-rotation replay must accept a nonempty externally supplied writer secret",
        )

    def test_recovery_shell_scripts_are_lf_normalized_and_pass_independent_bash_syntax(self):
        bash = bash_executable()

        violations = []
        attributes = read(".gitattributes")
        if re.search(r"(?m)^\*\.sh[ \t]+text[ \t]+eol=lf(?:[ \t]+#.*)?$", attributes) is None:
            violations.append(
                "Git must preserve LF shell scripts on every checkout before Ansible copies them to Linux"
            )

        for relative_path in (
            "operations/recovery/restore/restore.sh",
            "operations/recovery/verify-restoration/verify-restoration.sh",
        ):
            indexed_script = subprocess.run(
                ["git", "show", f":{relative_path}"],
                cwd=ROOT,
                capture_output=True,
                timeout=30,
            )
            if indexed_script.returncode != 0:
                diagnostic = indexed_script.stderr.decode("utf-8", errors="replace").strip().replace("\n", " | ")
                violations.append(f"{relative_path} could not be read from the Git index: {diagnostic}")
            elif b"\r" in indexed_script.stdout:
                violations.append(f"{relative_path} must be versioned with LF-only bytes")

            result = subprocess.run(
                [bash, "-n", relative_path],
                cwd=ROOT,
                capture_output=True,
                text=True,
                timeout=30,
            )
            if result.returncode != 0:
                diagnostic = (result.stderr or result.stdout).strip().replace("\n", " | ")
                violations.append(f"{relative_path} failed bash -n: {diagnostic}")

        self.assertEqual([], violations, "Recovery shell contract violations: " + "; ".join(violations))

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
                monitor.add_prior_monthly_evidence(recovery_day)
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
            any(is_official_better_stack_installer_execution(task) for task in tasks),
            "clean-host provisioning must execute the supported Docker Compose installer",
        )
        self.assertTrue(
            any(is_durable_better_stack_collector_probe(task) for task in tasks),
            "clean-host provisioning must verify collector status through durable container identity",
        )

    def test_better_stack_post_network_readiness_consumes_every_target_page(self):
        production_play = next(
            play for play in load_yaml("operations/ansible/site.yml")
            if play.get("hosts") == "production"
        )
        tasks = production_play["tasks"]
        names = [task.get("name") for task in tasks]
        connect_index = names.index(
            "Connect the Better Stack collector to the private production network"
        )
        readiness_tasks = tasks[connect_index + 1:]
        readiness_contract = json.dumps(readiness_tasks)
        page_one_index = next(
            index for index, task in enumerate(readiness_tasks)
            if task.get("name")
            == "Wait for every Better Stack service target to collect after network attachment"
        )
        remaining_pages_index = next(
            index for index, task in enumerate(readiness_tasks)
            if "remaining Better Stack post-network readiness target pages"
            in str(task.get("name", ""))
        )
        merge_index = next(
            index for index, task in enumerate(readiness_tasks)
            if "Merge every Better Stack post-network readiness target page"
            in str(task.get("name", ""))
        )
        evaluation_index = next(
            index for index, task in enumerate(readiness_tasks)
            if "collecting across all pages" in str(task.get("name", ""))
        )

        self.assertNotIn(
            "until",
            readiness_tasks[page_one_index],
            "page-one acquisition must not evaluate managed readiness before later pages are available",
        )
        self.assertLess(page_one_index, remaining_pages_index)
        self.assertLess(remaining_pages_index, merge_index)
        self.assertLess(
            merge_index,
            evaluation_index,
            "managed target health must be evaluated only after every provider page is accumulated",
        )
        self.assertIn("attributes.status", json.dumps(readiness_tasks[evaluation_index]))

        self.assertIn(
            "pagination",
            readiness_contract,
            "post-network readiness must follow Better Stack target pagination metadata",
        )
        self.assertRegex(
            readiness_contract,
            r"page=.*item",
            "post-network readiness must request target pages after page one",
        )
        self.assertIn(
            "results",
            readiness_contract,
            "readiness must combine the paginated target responses before evaluating health",
        )
        self.assertIn(
            "flatten",
            readiness_contract,
            "readiness must evaluate one flattened all-page target collection",
        )
        self.assertIn("attributes.status", readiness_contract)
        for service in ("proxy", "backend", "postgresql"):
            self.assertIn(
                f"better_stack_{service}_target_",
                readiness_contract,
                f"all-page readiness must retain the managed {service} identity",
            )

    def test_better_stack_target_reconciliation_holds_one_exclusive_failure_safe_lock(self):
        production_play = next(
            play for play in load_yaml("operations/ansible/site.yml")
            if play.get("hosts") == "production"
        )
        persisted_state_names = {
            "Persist the applied PostgreSQL target credential fingerprint before chart mutation",
            "Persist the applied PostgreSQL target credential fingerprint",
        }
        protected_sections = []
        unprotected_persistence = []

        def inspect(items, ancestors=()):
            for task in items:
                name = task.get("name")
                if name in persisted_state_names:
                    matching_section = None
                    for ancestor in reversed(ancestors):
                        block = ancestor.get("block", [])
                        always = ancestor.get("always", [])
                        if not block or not always:
                            continue
                        block_names = [str(item.get("name", "")) for item in block]
                        persistence_index = next(
                            (
                                index for index, item in enumerate(block)
                                if item.get("name") == name
                            ),
                            None,
                        )
                        acquisition_index = next(
                            (
                                index for index, item in enumerate(block)
                                if "Acquire the exclusive reconciliation lock"
                                in str(item.get("name", ""))
                            ),
                            None,
                        )
                        mutation_indices = [
                            index for index, item in enumerate(block)
                            if str(
                                (module_payload(item, "ansible.builtin.uri", "uri") or {})
                                .get("method", "")
                            ).upper() in {"POST", "PATCH"}
                            and "/targets" in str(
                                (module_payload(item, "ansible.builtin.uri", "uri") or {})
                                .get("url", "")
                            )
                        ]
                        readback_indices = [
                            index for index, item_name in enumerate(block_names)
                            if "read back" in item_name.casefold()
                            and "target" in item_name.casefold()
                        ]
                        cleanup_contract = json.dumps(always).casefold()
                        if (
                            acquisition_index is not None
                            and persistence_index is not None
                            and acquisition_index < persistence_index
                            and mutation_indices
                            and all(
                                acquisition_index < index < persistence_index
                                for index in mutation_indices
                            )
                            and readback_indices
                            and any(
                                acquisition_index < index < persistence_index
                                for index in readback_indices
                            )
                            and "lock" in cleanup_contract
                            and any(
                                cleanup in cleanup_contract
                                for cleanup in ("absent", "rmdir", "remove", "unlink")
                            )
                        ):
                            matching_section = ancestor
                            break
                    if matching_section is None:
                        unprotected_persistence.append(name)
                    else:
                        protected_sections.append(matching_section)
                for section in ("block", "rescue", "always"):
                    nested = task.get(section)
                    if isinstance(nested, list):
                        inspect(nested, ancestors + (task,))

        inspect(production_play["tasks"])

        self.assertEqual(
            [],
            unprotected_persistence,
            "each target discovery/mutation/readback/state sequence must run inside an "
            "exclusive lock with unconditional cleanup",
        )
        self.assertEqual(
            len(persisted_state_names),
            len(protected_sections),
            "both pre-chart and final target reconciliation must be concurrency-safe",
        )
        for section in protected_sections:
            block = section["block"]
            names = [str(task.get("name", "")) for task in block]
            acquisition_index = next(
                index for index, name in enumerate(names)
                if "Acquire the exclusive reconciliation lock" in name
            )
            persistence_index = next(
                index for index, name in enumerate(names)
                if name in persisted_state_names
            )
            self.assertLess(acquisition_index, persistence_index)
            self.assertTrue(any(
                acquisition_index < index < persistence_index
                and str((module_payload(task, "ansible.builtin.uri", "uri") or {}).get("method", "")).upper()
                in {"POST", "PATCH"}
                and "/targets" in str((module_payload(task, "ansible.builtin.uri", "uri") or {}).get("url", ""))
                for index, task in enumerate(block)
            ))
            self.assertTrue(any(
                acquisition_index < index < persistence_index
                and "read back" in name.casefold()
                and "target" in name.casefold()
                for index, name in enumerate(names)
            ))

    def test_better_stack_target_lock_recovers_only_an_abandoned_owner_or_expired_lease(self):
        production_play = next(
            play for play in load_yaml("operations/ansible/site.yml")
            if play.get("hosts") == "production"
        )
        lock_sections = []

        def inspect(items):
            for task in items:
                block = task.get("block")
                always = task.get("always")
                if isinstance(block, list) and isinstance(always, list):
                    block_contract = json.dumps(block).casefold()
                    cleanup_contract = json.dumps(always).casefold()
                    if (
                        "exclusive reconciliation lock" in block_contract
                        and "target" in block_contract
                        and "lock" in cleanup_contract
                    ):
                        lock_sections.append((block, always))
                for section in ("block", "rescue", "always"):
                    nested = task.get(section)
                    if isinstance(nested, list):
                        inspect(nested)

        inspect(production_play["tasks"])
        self.assertEqual(2, len(lock_sections))

        for block, always in lock_sections:
            acquisition_index = next(
                index for index, task in enumerate(block)
                if "Acquire the exclusive reconciliation lock"
                in str(task.get("name", ""))
            )
            recovery_contract = json.dumps(block[:acquisition_index]).casefold()
            cleanup_contract = json.dumps(always).casefold()
            self.assertTrue(
                any(signal in recovery_contract for signal in (
                    "owner", "pid", "kill -0", "/proc/", "lease", "mtime", "stale"
                )),
                "lock recovery must inspect owner liveness or an expiry lease before acquisition",
            )
            self.assertTrue(
                any(removal in recovery_contract for removal in (
                    '"state": "absent"', "rmdir", "remove", "unlink"
                )),
                "an abandoned owner or expired lease must be recoverable before acquisition",
            )
            self.assertTrue(
                any(guard in recovery_contract for guard in (
                    "not alive", "not running", "stale", "expired", "age", "mtime"
                )),
                "recovery must not preempt a live owner or unexpired lease",
            )
            self.assertIn(
                "owner_token",
                cleanup_contract,
                "cleanup must read and compare the persisted owner_token, not rely only on mkdir success",
            )
            self.assertTrue(
                "slurp" in cleanup_contract or "lookup" in cleanup_contract,
                "cleanup must read the current lock owner immediately before removal",
            )
            self.assertTrue(
                any(comparison in cleanup_contract for comparison in (
                    "equalto", "==", "match"
                )),
                "cleanup may remove the lock only when its current owner_token matches this apply",
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
            self.lifecycle = [
                rule
                for rule in self.lifecycle
                if str(rule.get("Filter", {}).get("Tag", {}).get("Value", "")).casefold()
                != classification.casefold()
            ]
            self.lifecycle.append(recovery_lifecycle_rule(classification))
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

    def add_prior_monthly_evidence(self, when: datetime):
        if when.day == 1:
            raise ValueError("prior monthly evidence requires a date after the first day of the month")
        month_start = when.replace(day=1)
        return self.add_object(month_start, "monthly", key_classification="monthly")

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
