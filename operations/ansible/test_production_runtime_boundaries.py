"""Executable regressions for production apply and clean-host boundaries."""

from __future__ import annotations

import copy
import os
import re
import shutil
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

import yaml


ROOT = Path(__file__).resolve().parents[2]
ANSIBLE_ROOT = ROOT / "operations" / "ansible"
IDEMPOTENCY_CHECK = ANSIBLE_ROOT / "idempotency-check.sh"
SITE = ANSIBLE_ROOT / "site.yml"
DEPLOY_RELEASE = ROOT / "deploy" / "production" / "ansible" / "deploy-release.yml"
POSTGRESQL_STATE_MARKER = (
    "PostgreSQL monitoring state verified: pg_roles pg_auth_members pg_monitor "
    "has_database_privilege pg_extension pg_stat_statements shared_preload_libraries"
)


def bash_executable() -> str:
    candidates: list[Path] = []
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
    raise AssertionError("Bash is required for the production helper scenario")


def bash_path(path: Path) -> str:
    posix_path = path.resolve().as_posix()
    if len(posix_path) >= 3 and posix_path[1:3] == ":/":
        return f"/{posix_path[0].lower()}{posix_path[2:]}"
    return posix_path


def production_tasks() -> list[dict]:
    plays = yaml.safe_load(SITE.read_text(encoding="utf-8"))
    return [
        task
        for play in plays
        if play.get("hosts") == "production"
        for task in play.get("tasks", [])
    ]


def release_tasks_with_guards() -> list[tuple[dict, tuple[str, ...]]]:
    plays = yaml.safe_load(DEPLOY_RELEASE.read_text(encoding="utf-8"))
    guarded_tasks: list[tuple[dict, tuple[str, ...]]] = []

    def visit(tasks: list[dict], inherited_guards: tuple[str, ...] = ()) -> None:
        for task in tasks:
            task_when = task.get("when", [])
            if isinstance(task_when, str):
                task_guards = (task_when,)
            else:
                task_guards = tuple(str(condition) for condition in task_when)
            effective_guards = inherited_guards + task_guards
            guarded_tasks.append((task, effective_guards))
            for section in ("block", "rescue", "always"):
                nested = task.get(section, [])
                if isinstance(nested, list):
                    visit(nested, effective_guards)

    for play in plays:
        visit(play.get("tasks", []))
    return guarded_tasks


def playbook_tasks_with_ancestors(path: Path) -> list[tuple[dict, tuple[dict, ...]]]:
    plays = yaml.safe_load(path.read_text(encoding="utf-8"))
    discovered: list[tuple[dict, tuple[dict, ...]]] = []

    def visit(tasks: list[dict], ancestors: tuple[dict, ...] = ()) -> None:
        for task in tasks:
            discovered.append((task, ancestors))
            for section in ("block", "rescue", "always"):
                nested = task.get(section, [])
                if isinstance(nested, list):
                    visit(nested, ancestors + (task,))

    for play in plays:
        if isinstance(play, dict):
            visit(play.get("tasks", []))
    return discovered


def full_site_task_sequence_with_guards() -> list[tuple[dict, tuple[str, ...]]]:
    sequence: list[tuple[dict, tuple[str, ...]]] = []

    def visit_tasks(
        tasks: list[dict],
        source_directory: Path,
        inherited_guards: tuple[str, ...] = (),
    ) -> None:
        for task in tasks:
            task_when = task.get("when", [])
            if isinstance(task_when, str):
                task_guards = (task_when,)
            else:
                task_guards = tuple(str(condition) for condition in task_when)
            effective_guards = inherited_guards + task_guards
            sequence.append((task, effective_guards))
            imported_tasks = task.get("ansible.builtin.import_tasks")
            if imported_tasks is None:
                imported_tasks = task.get("ansible.builtin.include_tasks")
            if isinstance(imported_tasks, str) and "{{" not in imported_tasks:
                imported_path = (source_directory / imported_tasks).resolve()
                if imported_path.is_file():
                    imported_entries = yaml.safe_load(imported_path.read_text(encoding="utf-8"))
                    if isinstance(imported_entries, list):
                        visit_tasks(imported_entries, imported_path.parent, effective_guards)
            for section in ("block", "rescue", "always"):
                nested = task.get(section, [])
                if isinstance(nested, list):
                    visit_tasks(nested, source_directory, effective_guards)

    def visit_roles(roles: list[object], inherited_guards: tuple[str, ...] = ()) -> None:
        for role_entry in roles:
            if isinstance(role_entry, str):
                role_name = role_entry
                role_guards = inherited_guards
            elif isinstance(role_entry, dict):
                role_name = str(role_entry.get("role", ""))
                role_when = role_entry.get("when", [])
                if isinstance(role_when, str):
                    role_guards = inherited_guards + (role_when,)
                else:
                    role_guards = inherited_guards + tuple(str(item) for item in role_when)
            else:
                continue
            role_tasks = ANSIBLE_ROOT / "roles" / role_name / "tasks" / "main.yml"
            if role_tasks.is_file():
                entries = yaml.safe_load(role_tasks.read_text(encoding="utf-8"))
                if isinstance(entries, list):
                    visit_tasks(entries, role_tasks.parent, role_guards)

    def visit_playbook(path: Path) -> None:
        entries = yaml.safe_load(path.read_text(encoding="utf-8"))
        for entry in entries:
            if not isinstance(entry, dict):
                continue
            imported = entry.get("import_playbook")
            if imported:
                visit_playbook((path.parent / str(imported)).resolve())
                continue
            pre_tasks = entry.get("pre_tasks", [])
            if isinstance(pre_tasks, list):
                visit_tasks(pre_tasks, path.parent)
            roles = entry.get("roles", [])
            if isinstance(roles, list):
                visit_roles(roles)
            tasks = entry.get("tasks", [])
            if isinstance(tasks, list):
                visit_tasks(tasks, path.parent)
            post_tasks = entry.get("post_tasks", [])
            if isinstance(post_tasks, list):
                visit_tasks(post_tasks, path.parent)

    visit_playbook(SITE)
    return sequence


def nested_task_nodes(tasks: list[dict]):
    for task in tasks:
        yield task
        for section in ("block", "rescue", "always"):
            nested = task.get(section, [])
            if isinstance(nested, list):
                yield from nested_task_nodes(nested)


def release_affecting_mutation(task: dict) -> bool:
    name = str(task.get("name", ""))
    copy_payload = task.get("ansible.builtin.copy", {})
    line_payload = task.get("ansible.builtin.lineinfile", {})
    touches_runtime_environment = (
        isinstance(copy_payload, dict)
        and copy_payload.get("dest") == "{{ compose_env_file }}"
    ) or (
        isinstance(line_payload, dict)
        and line_payload.get("path") == "{{ compose_env_file }}"
    )
    installs_release_inputs = name in {
        "Install versioned production Compose configuration",
        "Install recoverable production Compose environment inputs",
    }
    copy_destination = str(copy_payload.get("dest", "")) if isinstance(copy_payload, dict) else ""
    installs_external_release_input = any(
        marker in copy_destination
        for marker in (
            "secret_input_target_file",
            "postgres_tls_directory }}/server.crt",
            "postgres_tls_directory }}/server.key",
        )
    )
    argv = command_argv(task)
    mutates_runtime_services = argv[:2] == ["docker", "compose"] and "up" in argv
    return (
        touches_runtime_environment
        or installs_release_inputs
        or installs_external_release_input
        or mutates_runtime_services
    )


def ansible_playbook_executable() -> str:
    executable = shutil.which("ansible-playbook")
    sibling = Path(sys.executable).with_name("ansible-playbook")
    if executable is None and sibling.is_file():
        executable = str(sibling)
    if executable is None:
        raise AssertionError(
            "ansible-playbook is required; run this operational boundary in the pinned Linux environment"
        )
    return executable


def run_local_ansible(plays: list[dict]) -> subprocess.CompletedProcess[str]:
    with tempfile.TemporaryDirectory(prefix="gam-production-lock-boundary-") as temporary:
        playbook = Path(temporary) / "scenario.yml"
        playbook.write_text(yaml.safe_dump(plays, sort_keys=False), encoding="utf-8")
        environment = os.environ.copy()
        environment.setdefault("ANSIBLE_LOCAL_TEMP", str(Path(temporary) / "local"))
        environment.setdefault("ANSIBLE_REMOTE_TEMP", str(Path(temporary) / "remote"))
        return subprocess.run(
            [ansible_playbook_executable(), "--inventory", "localhost,", str(playbook)],
            cwd=ANSIBLE_ROOT,
            env=environment,
            text=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            timeout=90,
            check=False,
        )


def site_lock_ownership_fact_task() -> dict:
    return next(
        task
        for task, _ in playbook_tasks_with_ancestors(SITE)
        if isinstance(task.get("ansible.builtin.set_fact"), dict)
        and any(
            str(fact_name).endswith("lock_acquired") and fact_value is True
            for fact_name, fact_value in task["ansible.builtin.set_fact"].items()
        )
    )


def maintenance_value(task: dict) -> str | None:
    payloads = (
        task.get("ansible.builtin.copy", {}),
        task.get("ansible.builtin.lineinfile", {}),
    )
    for payload in payloads:
        if not isinstance(payload, dict):
            continue
        text = str(payload.get("content", payload.get("line", "")))
        match = re.search(r"(?m)^GAM_MAINTENANCE_ENABLED=(true|false)$", text)
        if match:
            return match.group(1)
    return None


def mutation_holds_exclusive_deployment_lock(task: dict, ancestors: tuple[dict, ...]) -> bool:
    for index, scope in enumerate(ancestors):
        block = scope.get("block", [])
        rescue = scope.get("rescue", [])
        always = scope.get("always", [])
        if (
            not isinstance(block, list)
            or not isinstance(rescue, list)
            or not isinstance(always, list)
        ):
            continue
        acquire_index = next(
            (
                position
                for position, candidate in enumerate(block)
                if candidate.get("name") == "Acquire exclusive deployment lock"
            ),
            None,
        )
        releases_lock = any(
            candidate.get("name") == "Release deployment lock" for candidate in always
        )
        protected_branch = ancestors[index + 1] if index + 1 < len(ancestors) else task
        branch_index = next(
            (
                position
                for position, candidate in enumerate(block)
                if candidate is protected_branch
            ),
            None,
        )
        branch_is_rescue = any(candidate is protected_branch for candidate in rescue)
        if (
            acquire_index is not None
            and releases_lock
            and (
                (branch_index is not None and acquire_index < branch_index)
                or branch_is_rescue
            )
        ):
            return True
    return False


def runs_for_release_state(guards: tuple[str, ...], transaction_required: bool) -> bool:
    for guard in guards:
        normalized = " ".join(guard.split())
        if normalized == "release_transaction_required | bool" and not transaction_required:
            return False
        if normalized == "not release_transaction_required | bool" and transaction_required:
            return False
    return True


def command_argv(task: dict) -> list[str]:
    payload = task.get("ansible.builtin.command", {})
    if not isinstance(payload, dict):
        return []
    return [str(argument) for argument in payload.get("argv", [])]


def shell_command(task: dict) -> str:
    payload = task.get("ansible.builtin.shell", {})
    if isinstance(payload, str):
        return payload
    return str(payload.get("cmd", ""))


def inspect_connect_privilege_on_clean_host(sql: str, role_exists: bool) -> str:
    unsafe_name_lookup = re.search(
        r"\)\s+AND\s+has_database_privilege\s*\(\s*:'monitor_username'",
        sql,
        flags=re.IGNORECASE | re.DOTALL,
    )
    if not role_exists and unsafe_name_lookup:
        raise RuntimeError(
            "PostgreSQL resolves the role-name argument before returning false; "
            "has_database_privilege cannot inspect a role that does not exist"
        )
    return "connect=granted" if role_exists else "connect=missing"


class ProductionRuntimeBoundaryTest(unittest.TestCase):
    def test_imported_release_accepts_the_lock_fact_established_by_full_site(self) -> None:
        site_lock_fact = copy.deepcopy(site_lock_ownership_fact_task())
        release_play = yaml.safe_load(DEPLOY_RELEASE.read_text(encoding="utf-8"))[0]
        inherited_lock_assertion = copy.deepcopy(next(
            task
            for task in release_play.get("pre_tasks", [])
            if task.get("name")
            == "Verify inherited Developer approval and uninterrupted full-site deployment lock ownership"
        ))

        plays = [
            {
                "name": "Establish full-site lock ownership",
                "hosts": "localhost",
                "connection": "local",
                "gather_facts": False,
                "tasks": [site_lock_fact],
            },
            {
                "name": "Exercise imported release lock inheritance",
                "hosts": "localhost",
                "connection": "local",
                "gather_facts": False,
                "vars": {
                    "developer_approval": "approved",
                    "developer_approval_record": "developer-approved-test-record",
                },
                "tasks": [inherited_lock_assertion],
            },
        ]
        result = run_local_ansible(plays)

        self.assertEqual(
            0,
            result.returncode,
            "the imported release play must consume the exact lock-ownership fact set by "
            f"the full-site play through real Ansible host fact propagation:\n{result.stdout}",
        )

    def test_imported_pretask_failure_releases_the_inherited_deployment_lock(self) -> None:
        release_play = yaml.safe_load(DEPLOY_RELEASE.read_text(encoding="utf-8"))[0]
        discovered: list[tuple[dict, tuple[dict, ...], bool]] = []

        def visit_pre_tasks(
            tasks: list[dict],
            ancestors: tuple[dict, ...] = (),
            recovery_branch: bool = False,
        ) -> None:
            for task in tasks:
                discovered.append((task, ancestors, recovery_branch))
                block = task.get("block", [])
                if isinstance(block, list):
                    visit_pre_tasks(block, ancestors + (task,), recovery_branch)
                for section in ("rescue", "always"):
                    recovery = task.get(section, [])
                    if isinstance(recovery, list):
                        visit_pre_tasks(recovery, ancestors + (task,), True)

        def lock_cleanup(scope: dict) -> dict | None:
            for candidate in nested_task_nodes(scope.get("rescue", [])):
                payload = candidate.get("ansible.builtin.file", {})
                if not isinstance(payload, dict):
                    continue
                lock_path = str(payload.get("path", ""))
                if payload.get("state") == "absent" and (
                    "deployment_lock" in lock_path
                    or "gam-production-deploy.lock" in lock_path
                ):
                    return candidate
            return None

        visit_pre_tasks(release_play.get("pre_tasks", []))
        failure_prone_tasks = [
            (task, ancestors)
            for task, ancestors, recovery_branch in discovered
            if not recovery_branch and not isinstance(task.get("block"), list)
        ]
        unprotected = [
            str(task.get("name", ""))
            for task, ancestors in failure_prone_tasks
            if not any(lock_cleanup(scope) is not None for scope in ancestors)
        ]
        self.assertEqual(
            [],
            unprotected,
            "every imported pre-task must be enclosed by failure cleanup for the inherited "
            "deployment lock; unprotected: " + ", ".join(unprotected),
        )
        if unprotected:
            return

        inherited_lock_assertion = copy.deepcopy(next(
            task
            for task, _, recovery_branch in discovered
            if not recovery_branch
            if task.get("name")
            == "Verify inherited Developer approval and uninterrupted full-site deployment lock ownership"
        ))
        release_lock_cleanup = copy.deepcopy(next(
            cleanup
            for _, ancestors in failure_prone_tasks
            for scope in reversed(ancestors)
            if (cleanup := lock_cleanup(scope)) is not None
        ))
        site_lock_fact = copy.deepcopy(site_lock_ownership_fact_task())

        with tempfile.TemporaryDirectory(prefix="gam-inherited-lock-") as temporary:
            lock_directory = Path(temporary) / "deployment.lock"
            plays = [
                {
                    "name": "Establish inherited lock ownership",
                    "hosts": "localhost",
                    "connection": "local",
                    "gather_facts": False,
                    "vars": {"deployment_lock_directory": str(lock_directory)},
                    "tasks": [
                        {
                            "name": "Create isolated deployment lock",
                            "ansible.builtin.file": {
                                "path": str(lock_directory),
                                "state": "directory",
                            },
                        },
                        site_lock_fact,
                    ],
                },
                {
                    "name": "Exercise imported pre-task failure cleanup",
                    "hosts": "localhost",
                    "connection": "local",
                    "gather_facts": False,
                    "vars": {
                        "deployment_lock_directory": str(lock_directory),
                        "developer_approval": "approved",
                        "developer_approval_record": "developer-approved-test-record",
                    },
                    "tasks": [
                        {
                            "name": "Exercise the production pre-task rescue",
                            "block": [
                                inherited_lock_assertion,
                                {
                                    "name": "Inject a manifest/input pre-task failure",
                                    "ansible.builtin.fail": {
                                        "msg": "intentional imported pre-task failure"
                                    },
                                },
                            ],
                            "rescue": [release_lock_cleanup],
                        }
                    ],
                },
            ]
            result = run_local_ansible(plays)

            self.assertEqual(
                0,
                result.returncode,
                "the isolated failure must be handled by the imported pre-task rescue:\n"
                + result.stdout,
            )
            self.assertFalse(
                lock_directory.exists(),
                "an imported manifest/input pre-task failure must not leave the inherited "
                "exclusive deployment lock behind",
            )

    def test_idempotency_helper_runs_real_apply_and_real_replay_with_operator_cidrs(self) -> None:
        with tempfile.TemporaryDirectory(prefix="gam-idempotency-input-") as temporary:
            temporary_root = Path(temporary)
            fake_bin = temporary_root / "bin"
            fake_bin.mkdir()
            invocation_log = temporary_root / "ansible-playbook.log"
            fake_playbook = fake_bin / "ansible-playbook"
            fake_playbook.write_text(
                "#!/usr/bin/env bash\n"
                "set -eu\n"
                "printf '%s\\n' \"$*\" >> \"$INVOCATION_LOG\"\n"
                "case \"$*\" in\n"
                "  *gam_operator_cidrs*203.0.113.10/32*) ;;\n"
                "  *) echo 'missing gam_operator_cidrs play-variable override' >&2; exit 64 ;;\n"
                "esac\n"
                "case \" $* \" in\n"
                "  *' --check '*) echo 'idempotency replay must be a real apply' >&2; exit 65 ;;\n"
                "esac\n"
                "printf '%s\\n' 'secret input convergence verified'\n"
                f"printf '%s\\n' '{POSTGRESQL_STATE_MARKER}'\n"
                "invocation_count=\"$(wc -l < \"$INVOCATION_LOG\")\"\n"
                "if [ \"$invocation_count\" -eq 1 ]; then\n"
                "  printf '%s\\n' 'changed=1 unreachable=0 failed=0'\n"
                "else\n"
                "  printf '%s\\n' 'changed=0 unreachable=0 failed=0'\n"
                "fi\n",
                encoding="utf-8",
                newline="\n",
            )
            os.chmod(fake_playbook, 0o755)

            fake_ansible = fake_bin / "ansible"
            fake_ansible.write_text(
                "#!/usr/bin/env bash\n"
                "printf '%s\\n' 'Status: active'\n"
                "printf '%s\\n' 'Default: deny (incoming)'\n"
                "printf '%s\\n' '[ 1] 80/tcp ALLOW IN Anywhere'\n"
                "printf '%s\\n' '[ 2] 443/tcp ALLOW IN Anywhere'\n"
                "printf '%s\\n' '[ 3] 22/tcp ALLOW IN 203.0.113.10/32'\n",
                encoding="utf-8",
                newline="\n",
            )
            os.chmod(fake_ansible, 0o755)

            environment = os.environ.copy()
            environment.update(
                {
                    "PATH": f"{bash_path(fake_bin)}:{environment.get('PATH', '')}",
                    "INVOCATION_LOG": bash_path(invocation_log),
                    "GAM_SSH_ALLOWED_CIDR": "203.0.113.10/32",
                    "GAM_OPERATOR_CIDRS": '["203.0.113.10/32"]',
                }
            )
            result = subprocess.run(
                [bash_executable(), bash_path(IDEMPOTENCY_CHECK)],
                cwd=ROOT,
                env=environment,
                capture_output=True,
                text=True,
                timeout=30,
                check=False,
            )

            self.assertEqual(
                0,
                result.returncode,
                "the helper must pass the explicit operator CIDR allowlist into both "
                f"full-site invocations:\n{result.stdout}\n{result.stderr}",
            )
            invocations = invocation_log.read_text(encoding="utf-8").splitlines()
            self.assertEqual(2, len(invocations))
            self.assertTrue(all("gam_operator_cidrs" in call for call in invocations))
            self.assertTrue(all("--check" not in call for call in invocations))

    def test_full_site_replay_keeps_runtime_environment_converged_without_rewrites(self) -> None:
        site_environment_writer = next(
            task
            for task in production_tasks()
            if task.get("name") == "Install recoverable production Compose environment inputs"
        )
        release_environment_writer, release_guards = next(
            (task, guards)
            for task, guards in release_tasks_with_guards()
            if task.get("name")
            == "Enable maintenance response while preserving current commissioning state"
        )
        self.assertIn("content", release_environment_writer["ansible.builtin.copy"])

        runtime_environment = "{{ production_compose_environment_file }}"
        site_destinations = [str(destination) for destination in site_environment_writer["loop"]]
        host_files: dict[str, str] = {}

        def full_site_apply(transaction_required: bool) -> int:
            changes = 0
            if runtime_environment in site_destinations:
                if host_files.get(runtime_environment) != "recoverable-secret-input":
                    host_files[runtime_environment] = "recoverable-secret-input"
                    changes += 1
            if runs_for_release_state(release_guards, transaction_required):
                if host_files.get(runtime_environment) != "managed-compose-runtime-environment":
                    host_files[runtime_environment] = "managed-compose-runtime-environment"
                    changes += 1
            return changes

        full_site_apply(transaction_required=True)
        expected_runtime_environment = host_files[runtime_environment]
        replay_changes = full_site_apply(transaction_required=False)

        self.assertEqual(
            0,
            replay_changes,
            "a real full-site replay must not replace runtime/production.env with the "
            "recoverable secret input after the release play has rendered its managed format",
        )
        self.assertEqual(
            expected_runtime_environment,
            host_files[runtime_environment],
            "the replay must retain the converged Compose runtime environment",
        )

    def test_already_active_release_applies_configuration_drift_to_runtime_containers(self) -> None:
        deployment_file_writer = next(
            task
            for task, _ in playbook_tasks_with_ancestors(SITE)
            if task.get("name") == "Install versioned production Compose configuration"
        )
        deployment_destinations = {
            str(item["dest"]): "desired-" + Path(str(item["src"])).name
            for item in deployment_file_writer["loop"]
        }
        self.assertEqual(
            {"{{ production_compose_file }}", "{{ production_root }}/Caddyfile"},
            set(deployment_destinations),
        )

        host_configuration = {
            destination: "stale-configuration" for destination in deployment_destinations
        }
        running_configuration = dict(host_configuration)
        host_configuration.update(deployment_destinations)

        same_release_rollouts = [
            task
            for task, guards in release_tasks_with_guards()
            if command_argv(task)[:2] == ["docker", "compose"]
            and "up" in command_argv(task)
            and runs_for_release_state(guards, transaction_required=False)
        ]
        self.assertTrue(
            any("postgres" in command_argv(task) for task in same_release_rollouts),
            "same-release convergence must include PostgreSQL so changed "
            "shared_preload_libraries configuration is active before monitoring acceptance",
        )
        if same_release_rollouts:
            running_configuration = dict(host_configuration)

        self.assertEqual(
            host_configuration,
            running_configuration,
            "an already-active frontend release must still apply changed Compose/Caddy "
            "configuration to the runtime containers",
        )

    def test_release_affecting_mutations_hold_the_exclusive_deployment_lock(self) -> None:
        sequence = full_site_task_sequence_with_guards()
        approval_indices = [
            index
            for index, (task, _) in enumerate(sequence)
            if task.get("name") == "Require explicit Developer approval"
        ]
        self.assertEqual(1, len(approval_indices), "full-site mutation needs one approval gate")
        approval_index = approval_indices[0]

        violations = []
        preapproval_mutations = [
            str(task.get("name", ""))
            for index, (task, _) in enumerate(sequence)
            if index < approval_index and release_affecting_mutation(task)
        ]
        if preapproval_mutations:
            violations.append(
                "release inputs mutate before explicit approval: "
                + ", ".join(preapproval_mutations)
            )
        for transaction_required, path_name in ((True, "new release"), (False, "same release")):
            active_tasks = [
                (index, task)
                for index, (task, guards) in enumerate(sequence)
                if runs_for_release_state(guards, transaction_required)
            ]
            acquisitions = [
                index
                for index, task in active_tasks
                if task.get("name") == "Acquire exclusive deployment lock"
            ]
            releases = [
                index
                for index, task in active_tasks
                if task.get("name") == "Release deployment lock"
            ]
            mutations = [
                (index, str(task.get("name", "")))
                for index, task in active_tasks
                if release_affecting_mutation(task)
            ]

            if len(acquisitions) != 1 or len(releases) != 1:
                violations.append(
                    f"{path_name} uses {len(acquisitions)} lock acquisitions and "
                    f"{len(releases)} releases instead of one uninterrupted transaction"
                )
                continue
            acquisition_index = acquisitions[0]
            release_index = releases[0]
            if not approval_index < acquisition_index:
                violations.append(f"{path_name} acquires its lock before explicit approval")
            outside_transaction = [
                name
                for index, name in mutations
                if not acquisition_index < index < release_index
            ]
            if outside_transaction:
                violations.append(
                    f"{path_name} mutates outside the approved lock: "
                    + ", ".join(outside_transaction)
                )

        self.assertEqual(
            [],
            violations,
            "each approved deployment path must use one uninterrupted exclusive lock: "
            + "; ".join(violations),
        )

    def test_same_release_disruption_enters_maintenance_and_fails_safe(self) -> None:
        same_release_scope = next(
            task
            for task, _ in playbook_tasks_with_ancestors(DEPLOY_RELEASE)
            if task.get("name")
            == "Converge the already-active immutable release under the deployment lock"
        )
        block_tasks = list(nested_task_nodes(same_release_scope.get("block", [])))
        rescue_tasks = list(nested_task_nodes(same_release_scope.get("rescue", [])))

        disruptive_indices = [
            index
            for index, task in enumerate(block_tasks)
            if command_argv(task)[:2] == ["docker", "compose"]
            and "up" in command_argv(task)
        ]
        health_index = next(
            index
            for index, task in enumerate(block_tasks)
            if task.get("name") == "Verify the already-active immutable release during replay"
        )
        transitions = [
            (index, value)
            for index, task in enumerate(block_tasks)
            if (value := maintenance_value(task)) is not None
        ]

        violations = []
        if not disruptive_indices:
            violations.append("same-release convergence has no disruptive runtime step")
        else:
            first_disruption = min(disruptive_indices)
            if not any(index < first_disruption and value == "true" for index, value in transitions):
                violations.append("maintenance is not enabled before runtime convergence")
        if any(index < health_index and value == "false" for index, value in transitions):
            violations.append("maintenance is disabled before successful health verification")
        if not any(index > health_index and value == "false" for index, value in transitions):
            violations.append("maintenance is not disabled after successful health verification")

        rescue_retains_maintenance = any(
            maintenance_value(task) == "true" for task in rescue_tasks
        )
        rescue_restores_previous_runtime = any(
            command_argv(task)[:2] == ["docker", "compose"]
            and "up" in command_argv(task)
            and any("previous" in argument for argument in command_argv(task))
            for task in rescue_tasks
        )
        if not (rescue_retains_maintenance or rescue_restores_previous_runtime):
            violations.append(
                "failure rescue neither retains maintenance nor restores the previous runtime"
            )

        self.assertEqual(
            [],
            violations,
            "same-release disruptive convergence must fail safely: " + "; ".join(violations),
        )

    def test_same_release_public_health_requires_the_exact_response_contract(self) -> None:
        same_release_scope = next(
            task
            for task, _ in playbook_tasks_with_ancestors(DEPLOY_RELEASE)
            if task.get("name")
            == "Converge the already-active immutable release under the deployment lock"
        )
        public_health_tasks = [
            task
            for task in nested_task_nodes(same_release_scope.get("block", []))
            if isinstance(task.get("ansible.builtin.uri"), dict)
            and task["ansible.builtin.uri"].get("url")
            == "{{ gam_public_origin }}/api/health"
        ]
        self.assertEqual(
            1,
            len(public_health_tasks),
            "the distinct same-release branch must verify the canonical public health route",
        )
        if not public_health_tasks:
            return

        health_task = public_health_tasks[0]
        request = health_task["ansible.builtin.uri"]
        failure_contract = " ".join(str(health_task.get("failed_when", "")).split())
        self.assertEqual(200, request.get("status_code"))
        self.assertIs(request.get("return_content"), True)
        self.assertIs(request.get("validate_certs"), True)
        self.assertIn("active_release_public_health.status != 200", failure_contract)
        self.assertIn(
            "active_release_public_health.content != '{\"status\":\"UP\"}'",
            failure_contract,
        )
        self.assertIn("active_release_public_health.content_type", failure_contract)
        self.assertIn("!= 'application/json'", failure_contract)
        self.assertIn("active_release_public_health.cache_control", failure_contract)
        self.assertIn("!= 'no-store'", failure_contract)
        self.assertNotIn(
            "active_release_public_health.headers",
            failure_contract,
            "Ansible exposes uri response headers through flattened lowercase keys",
        )

    def test_clean_host_privilege_inspection_does_not_call_missing_monitoring_role(self) -> None:
        role_exists = False
        inspection_completed = False

        for task in production_tasks():
            command = shell_command(task)
            if "CREATE ROLE %I LOGIN" in command:
                role_exists = True
            if task.get("name") == "Inspect PostgreSQL monitoring prerequisites before reconciliation":
                try:
                    inspect_connect_privilege_on_clean_host(command, role_exists)
                except RuntimeError as failure:
                    self.fail(str(failure))
                inspection_completed = True

        self.assertTrue(inspection_completed, "the clean-host monitoring inspection task must exist")


if __name__ == "__main__":
    unittest.main(verbosity=2)
