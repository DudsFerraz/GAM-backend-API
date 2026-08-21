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
CI_WORKFLOW = ROOT / ".github" / "workflows" / "ci-testes.yml"
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


def nested_task_nodes_with_guards(
    tasks: list[dict],
    source_directory: Path,
    inherited_guards: tuple[str, ...] = (),
):
    for task in tasks:
        task_when = task.get("when", [])
        if isinstance(task_when, str):
            task_guards = (task_when,)
        else:
            task_guards = tuple(str(condition) for condition in task_when)
        effective_guards = inherited_guards + task_guards
        yield task, effective_guards

        imported_tasks = task.get("ansible.builtin.import_tasks")
        if imported_tasks is None:
            imported_tasks = task.get("ansible.builtin.include_tasks")
        if isinstance(imported_tasks, str) and "{{" not in imported_tasks:
            imported_path = (source_directory / imported_tasks).resolve()
            if imported_path.is_file():
                imported_entries = yaml.safe_load(imported_path.read_text(encoding="utf-8"))
                if isinstance(imported_entries, list):
                    yield from nested_task_nodes_with_guards(
                        imported_entries,
                        imported_path.parent,
                        effective_guards,
                    )

        included_role = task.get("ansible.builtin.include_role")
        if included_role is None:
            included_role = task.get("ansible.builtin.import_role")
        if isinstance(included_role, dict):
            role_name = str(included_role.get("name", ""))
            role_tasks = ANSIBLE_ROOT / "roles" / role_name / "tasks" / "main.yml"
            if role_tasks.is_file():
                role_entries = yaml.safe_load(role_tasks.read_text(encoding="utf-8"))
                if isinstance(role_entries, list):
                    yield from nested_task_nodes_with_guards(
                        role_entries,
                        role_tasks.parent,
                        effective_guards,
                    )

        for section in ("block", "rescue", "always"):
            nested = task.get(section, [])
            if isinstance(nested, list):
                yield from nested_task_nodes_with_guards(
                    nested,
                    source_directory,
                    effective_guards,
                )


def nested_task_nodes_with_failure_isolation(
    tasks: list[dict],
    protected_by_rescue: bool = False,
):
    for task in tasks:
        yield task, protected_by_rescue
        block = task.get("block", [])
        rescue = task.get("rescue", [])
        always = task.get("always", [])
        if isinstance(block, list):
            yield from nested_task_nodes_with_failure_isolation(
                block,
                protected_by_rescue or isinstance(rescue, list) and bool(rescue),
            )
        if isinstance(rescue, list):
            yield from nested_task_nodes_with_failure_isolation(
                rescue,
                protected_by_rescue,
            )
        if isinstance(always, list):
            yield from nested_task_nodes_with_failure_isolation(
                always,
                protected_by_rescue,
            )


def full_site_task_sequence_with_context() -> list[tuple[dict, tuple[str, ...], str]]:
    sequence: list[tuple[dict, tuple[str, ...], str]] = []

    def visit_tasks(
        tasks: list[dict],
        source_directory: Path,
        hosts: str,
        inherited_guards: tuple[str, ...] = (),
    ) -> None:
        for task, guards in nested_task_nodes_with_guards(
            tasks,
            source_directory,
            inherited_guards,
        ):
            sequence.append((task, guards, hosts))

    def visit_roles(roles: list[object], hosts: str) -> None:
        for role_entry in roles:
            if isinstance(role_entry, str):
                role_name = role_entry
                role_guards: tuple[str, ...] = ()
            elif isinstance(role_entry, dict):
                role_name = str(role_entry.get("role", ""))
                role_when = role_entry.get("when", [])
                if isinstance(role_when, str):
                    role_guards = (role_when,)
                else:
                    role_guards = tuple(str(item) for item in role_when)
            else:
                continue
            role_tasks = ANSIBLE_ROOT / "roles" / role_name / "tasks" / "main.yml"
            if role_tasks.is_file():
                entries = yaml.safe_load(role_tasks.read_text(encoding="utf-8"))
                if isinstance(entries, list):
                    visit_tasks(entries, role_tasks.parent, hosts, role_guards)

    def visit_playbook(path: Path) -> None:
        entries = yaml.safe_load(path.read_text(encoding="utf-8"))
        for entry in entries:
            if not isinstance(entry, dict):
                continue
            imported = entry.get("import_playbook")
            if imported:
                visit_playbook((path.parent / str(imported)).resolve())
                continue
            hosts = str(entry.get("hosts", ""))
            pre_tasks = entry.get("pre_tasks", [])
            if isinstance(pre_tasks, list):
                visit_tasks(pre_tasks, path.parent, hosts)
            roles = entry.get("roles", [])
            if isinstance(roles, list):
                visit_roles(roles, hosts)
            tasks = entry.get("tasks", [])
            if isinstance(tasks, list):
                visit_tasks(tasks, path.parent, hosts)
            post_tasks = entry.get("post_tasks", [])
            if isinstance(post_tasks, list):
                visit_tasks(post_tasks, path.parent, hosts)

    visit_playbook(SITE)
    return sequence


def aws_command_mutates(task: dict) -> bool:
    argv = command_argv(task)
    if len(argv) < 3 or argv[0] != "aws":
        return False
    operation = argv[2]
    return not operation.startswith(("describe-", "get-", "head-", "list-"))


def production_host_task_mutates(task: dict) -> bool:
    read_only_modules = {
        "ansible.builtin.assert",
        "ansible.builtin.debug",
        "ansible.builtin.fail",
        "ansible.builtin.find",
        "ansible.builtin.meta",
        "ansible.builtin.package_facts",
        "ansible.builtin.service_facts",
        "ansible.builtin.set_fact",
        "ansible.builtin.setup",
        "ansible.builtin.slurp",
        "ansible.builtin.stat",
        "ansible.builtin.uri",
        "ansible.builtin.wait_for_connection",
    }
    container_modules = {
        "ansible.builtin.block",
        "ansible.builtin.import_role",
        "ansible.builtin.import_tasks",
        "ansible.builtin.include_role",
        "ansible.builtin.include_tasks",
    }
    module_names = {
        str(key)
        for key in task
        if "." in str(key) and str(key) not in {"ansible.builtin.check_mode"}
    }
    actionable_modules = module_names - read_only_modules - container_modules
    if not actionable_modules:
        return False
    if actionable_modules <= {"ansible.builtin.command", "ansible.builtin.shell"}:
        return task.get("changed_when") is not False
    return True


def task_records_release_result(task: dict, result: str) -> bool:
    payload = task.get("ansible.builtin.lineinfile", {})
    if not isinstance(payload, dict) or payload.get("path") != "{{ release_record_file }}":
        return False
    line = " ".join(str(payload.get("line", "")).split())
    return re.search(rf"['\"]result['\"]\s*:\s*['\"]{re.escape(result)}['\"]", line) is not None


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


def run_local_ansible(
    plays: list[dict],
    environment_overrides: dict[str, str] | None = None,
    inventory: dict | None = None,
) -> subprocess.CompletedProcess[str]:
    with tempfile.TemporaryDirectory(prefix="gam-production-lock-boundary-") as temporary:
        playbook = Path(temporary) / "scenario.yml"
        playbook.write_text(yaml.safe_dump(plays, sort_keys=False), encoding="utf-8")
        inventory_argument = "localhost,"
        if inventory is not None:
            inventory_path = Path(temporary) / "inventory.yml"
            inventory_path.write_text(
                yaml.safe_dump(inventory, sort_keys=False),
                encoding="utf-8",
            )
            inventory_argument = str(inventory_path)
        environment = os.environ.copy()
        environment.setdefault("ANSIBLE_LOCAL_TEMP", str(Path(temporary) / "local"))
        environment.setdefault("ANSIBLE_REMOTE_TEMP", str(Path(temporary) / "remote"))
        environment.update(environment_overrides or {})
        return subprocess.run(
            [
                ansible_playbook_executable(),
                "--inventory",
                inventory_argument,
                str(playbook),
            ],
            cwd=ANSIBLE_ROOT,
            env=environment,
            text=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            timeout=90,
            check=False,
        )


def run_idempotency_replay_fixture(
    *replay_task_names: str,
) -> subprocess.CompletedProcess[str]:
    replay_change_output = "".join(
        f"  printf '%s\\n' 'TASK [{task_name}]'\n"
        "  printf '%s\\n' 'changed: [production]'\n"
        for task_name in replay_task_names
    )
    with tempfile.TemporaryDirectory(prefix="gam-idempotency-replay-") as temporary:
        temporary_root = Path(temporary)
        fake_bin = temporary_root / "bin"
        fake_bin.mkdir()
        invocation_marker = temporary_root / "initial-apply-complete"
        fake_playbook = fake_bin / "ansible-playbook"
        fake_playbook.write_text(
            "#!/usr/bin/env bash\n"
            "set -eu\n"
            "if [ ! -e \"$INVOCATION_MARKER\" ]; then\n"
            "  : > \"$INVOCATION_MARKER\"\n"
            "  printf '%s\\n' 'secret input convergence verified'\n"
            f"  printf '%s\\n' '{POSTGRESQL_STATE_MARKER}'\n"
            "  printf '%s\\n' 'changed=1 unreachable=0 failed=0'\n"
            "else\n"
            f"{replay_change_output}"
            f"  printf '%s\\n' '{POSTGRESQL_STATE_MARKER}'\n"
            f"  printf '%s\\n' 'changed={len(replay_task_names)} unreachable=0 failed=0'\n"
            "fi\n",
            encoding="utf-8",
            newline="\n",
        )
        fake_playbook.chmod(0o755)

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
        fake_ansible.chmod(0o755)

        environment = os.environ.copy()
        environment.update(
            {
                "PATH": f"{bash_path(fake_bin)}:{environment.get('PATH', '')}",
                "INVOCATION_MARKER": bash_path(invocation_marker),
                "GAM_SSH_ALLOWED_CIDR": "203.0.113.10/32",
                "GAM_OPERATOR_CIDRS": '["203.0.113.10/32"]',
            }
        )
        return subprocess.run(
            [bash_executable(), bash_path(IDEMPOTENCY_CHECK)],
            cwd=ROOT,
            env=environment,
            capture_output=True,
            text=True,
            timeout=30,
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
            site_plays = yaml.safe_load(SITE.read_text(encoding="utf-8"))
            lock_play = next(
                play
                for play in site_plays
                if isinstance(play, dict)
                and any(
                    task.get("name") == "Acquire exclusive deployment lock"
                    for task in play.get("tasks", [])
                )
            )
            identity_violations = []
            if "--user root" not in invocations[0]:
                identity_violations.append(
                    "initial full-site apply does not explicitly select root"
                )
            if "--user gamops" not in invocations[1]:
                identity_violations.append(
                    "zero-change replay does not explicitly select gamops"
                )
            if "remote_user" in lock_play:
                identity_violations.append(
                    "canonical lock play overrides the helper-selected SSH identity with "
                    + repr(lock_play.get("remote_user"))
                )
            self.assertEqual(
                [],
                identity_violations,
                "the canonical full-site lock must honor root bootstrap and gamops replay "
                "identities: " + "; ".join(identity_violations),
            )

    def test_idempotency_helper_accepts_the_explained_same_release_audit_write(self) -> None:
        explained_audit = run_idempotency_replay_fixture(
            "Record successful same-release convergence"
        )
        self.assertEqual(
            0,
            explained_audit.returncode,
            "REQ-OPS-010 permits an explained audit-record append during the immediate "
            "same-release replay; it must not be hidden as changed=0 or confused with "
            "configuration drift:\n"
            + explained_audit.stdout
            + explained_audit.stderr,
        )

    def test_idempotency_helper_rejects_unexplained_configuration_drift(self) -> None:
        unexplained_drift = run_idempotency_replay_fixture(
            "Install versioned production Compose configuration"
        )
        self.assertNotEqual(
            0,
            unexplained_drift.returncode,
            "REQ-OPS-010 must reject a replay whose change comes from unexplained "
            "configuration drift",
        )
        self.assertIn(
            "unexplained",
            unexplained_drift.stderr.lower(),
            "the rejected drift must be classified explicitly instead of reporting every "
            "replay write as equivalent",
        )

    def test_idempotency_helper_rejects_an_audit_write_mixed_with_drift(self) -> None:
        mixed_replay = run_idempotency_replay_fixture(
            "Record successful same-release convergence",
            "Install versioned production Compose configuration",
        )
        self.assertNotEqual(
            0,
            mixed_replay.returncode,
            "an explained audit append must not conceal a second changed task",
        )
        self.assertIn(
            "unexplained",
            mixed_replay.stderr.lower(),
            "the mixed replay must classify the additional configuration change as drift",
        )

    def test_idempotency_helper_rejects_duplicate_same_release_audit_writes(self) -> None:
        duplicate_audit = run_idempotency_replay_fixture(
            "Record successful same-release convergence",
            "Record successful same-release convergence",
        )
        self.assertNotEqual(
            0,
            duplicate_audit.returncode,
            "an immediate replay permits one explained result record, not duplicate appends",
        )
        self.assertIn(
            "unexplained",
            duplicate_audit.stderr.lower(),
            "duplicate audit writes must remain observable as a non-idempotent replay",
        )

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

    def test_all_production_host_and_aws_mutations_follow_approval_and_lock(self) -> None:
        sequence = full_site_task_sequence_with_context()
        approval_indices = [
            index
            for index, (task, _, _) in enumerate(sequence)
            if task.get("name") == "Require explicit Developer approval"
        ]
        self.assertEqual(
            1,
            len(approval_indices),
            "the canonical full-site workflow needs one explicit Developer approval gate",
        )
        if len(approval_indices) != 1:
            return
        approval_index = approval_indices[0]

        violations = []
        for transaction_required, path_name in ((True, "new release"), (False, "same release")):
            active_tasks = [
                (index, task, hosts)
                for index, (task, guards, hosts) in enumerate(sequence)
                if runs_for_release_state(guards, transaction_required)
            ]
            acquisitions = [
                index
                for index, task, _ in active_tasks
                if task.get("name") == "Acquire exclusive deployment lock"
            ]
            releases = [
                index
                for index, task, _ in active_tasks
                if task.get("name") == "Release deployment lock"
            ]
            if len(acquisitions) != 1 or len(releases) != 1:
                violations.append(
                    f"{path_name} uses {len(acquisitions)} lock acquisitions and "
                    f"{len(releases)} releases instead of one full-site transaction"
                )
                continue

            acquisition_index = acquisitions[0]
            release_index = releases[0]
            if not approval_index < acquisition_index:
                violations.append(f"{path_name} acquires its lock before explicit approval")

            mutation_names = [
                str(task.get("name", ""))
                for index, task, hosts in active_tasks
                if task.get("name")
                not in {"Acquire exclusive deployment lock", "Release deployment lock"}
                if aws_command_mutates(task)
                or (hosts == "production" and production_host_task_mutates(task))
                if not (approval_index < acquisition_index < index < release_index)
            ]
            if mutation_names:
                summarized_mutations = ", ".join(mutation_names[:12])
                if len(mutation_names) > 12:
                    summarized_mutations += f", and {len(mutation_names) - 12} more"
                violations.append(
                    f"{path_name} mutates KVM 2 or AWS before approval and lock ownership: "
                    + summarized_mutations
                )

        self.assertEqual(
            [],
            violations,
            "no production-host or AWS mutation may escape explicit Developer approval "
            "and the exclusive full-site deployment lock: " + "; ".join(violations),
        )

    def test_pre_acquisition_rejections_stop_later_controller_mutations(self) -> None:
        site_plays = yaml.safe_load(SITE.read_text(encoding="utf-8"))
        authorization_play = next(
            play
            for play in site_plays
            if isinstance(play, dict)
            and any(
                task.get("name") == "Acquire exclusive deployment lock"
                for task in play.get("tasks", [])
            )
        )
        initialization_task = copy.deepcopy(next(
            task
            for task in authorization_play["tasks"]
            if task.get("name") == "Initialize deployment lock ownership"
        ))
        approval_task = copy.deepcopy(next(
            task
            for task in authorization_play["tasks"]
            if task.get("name") == "Require explicit Developer approval"
        ))
        acquisition_task = copy.deepcopy(next(
            task
            for task in authorization_play["tasks"]
            if task.get("name") == "Acquire exclusive deployment lock"
        ))
        ownership_task = copy.deepcopy(next(
            task
            for task in authorization_play["tasks"]
            if task.get("name") == "Mark deployment lock as acquired"
        ))
        inventory = {
            "all": {
                "children": {
                    "gam_production": {
                        "hosts": {
                            "production_gate": {"ansible_connection": "local"}
                        }
                    }
                }
            }
        }

        with tempfile.TemporaryDirectory(prefix="gam-pre-acquisition-boundary-") as temporary:
            temporary_root = Path(temporary)
            for rejection_kind in ("approval rejection", "lock contention"):
                with self.subTest(rejection_kind=rejection_kind):
                    scenario_root = temporary_root / rejection_kind.replace(" ", "-")
                    scenario_root.mkdir()
                    lock_directory = scenario_root / "deployment.lock"
                    controller_marker = scenario_root / "controller-mutation-ran"
                    redirected_acquisition = copy.deepcopy(acquisition_task)
                    redirected_acquisition["ansible.builtin.shell"] = str(
                        redirected_acquisition["ansible.builtin.shell"]
                    ).replace(
                        "/run/gam-production-deploy.lock",
                        str(lock_directory).replace("\\", "/"),
                    )
                    gate_tasks = [initialization_task, approval_task]
                    environment = {
                        "GAM_DEPLOYMENT_APPROVAL": "rejected",
                        "GAM_DEPLOYMENT_APPROVAL_RECORD": "REQ-OPS-008-test",
                    }
                    expected_failure = "Developer approval token"
                    if rejection_kind == "lock contention":
                        lock_directory.mkdir()
                        gate_tasks.extend([redirected_acquisition, ownership_task])
                        environment["GAM_DEPLOYMENT_APPROVAL"] = "approved"
                        expected_failure = "another production deployment"

                    scenario = [
                        {
                            "name": "Exercise the canonical pre-acquisition gate",
                            "hosts": "gam_production",
                            "become": False,
                            "gather_facts": False,
                            "tasks": copy.deepcopy(gate_tasks),
                        },
                        {
                            "name": "Represent a later controller or AWS mutation",
                            "hosts": "localhost",
                            "connection": "local",
                            "gather_facts": False,
                            "tasks": [
                                {
                                    "name": "Write the later controller mutation marker",
                                    "ansible.builtin.copy": {
                                        "content": "mutation-ran\n",
                                        "dest": str(controller_marker).replace("\\", "/"),
                                        "mode": "0600",
                                    },
                                }
                            ],
                        },
                    ]
                    result = run_local_ansible(
                        scenario,
                        environment_overrides=environment,
                        inventory=inventory,
                    )

                    self.assertNotEqual(
                        0,
                        result.returncode,
                        f"{rejection_kind} must remain observable:\n{result.stdout}",
                    )
                    self.assertIn(
                        expected_failure,
                        result.stdout,
                        f"the scenario must exercise the intended {rejection_kind} path",
                    )
                    self.assertFalse(
                        controller_marker.exists(),
                        f"{rejection_kind} before lock ownership must stop later "
                        "controller and AWS mutations:\n" + result.stdout,
                    )

    def test_steady_state_gamops_can_manage_the_canonical_run_lock(self) -> None:
        site_plays = yaml.safe_load(SITE.read_text(encoding="utf-8"))
        authorization_play = next(
            play
            for play in site_plays
            if isinstance(play, dict)
            and any(
                task.get("name") == "Acquire exclusive deployment lock"
                for task in play.get("tasks", [])
            )
        )
        acquisition_task = next(
            task
            for task in authorization_play["tasks"]
            if task.get("name") == "Acquire exclusive deployment lock"
        )
        cleanup_play = next(
            play
            for play in site_plays
            if isinstance(play, dict)
            and play.get("vars", {}).get("canonical_lock_cleanup_play") is True
        )
        steady_state_cleanup_task = copy.deepcopy(next(
            task
            for task in cleanup_play.get("tasks", [])
            if task.get("name")
            == "Release deployment lock through steady-state operations access"
        ))
        self.assertTrue(
            bool(cleanup_play.get("become", False)),
            "steady-state gamops cleanup must retain its accepted privilege path",
        )

        with tempfile.TemporaryDirectory(prefix="gam-gamops-run-lock-") as temporary:
            temporary_root = Path(temporary)
            temporary_root.chmod(0o755)
            protected_run = temporary_root / "run"
            protected_run.mkdir(mode=0o755)
            lock_directory = protected_run / "gam-production-deploy.lock"
            acquisition_command = str(acquisition_task["ansible.builtin.shell"]).replace(
                "/run",
                str(protected_run).replace("\\", "/"),
            )
            acquisition_is_privileged = bool(
                acquisition_task.get("become", authorization_play.get("become", False))
            )

            def drop_to_unprivileged_gamops_boundary() -> None:
                os.setgroups([])
                os.setgid(65534)
                os.setuid(65534)

            acquisition = subprocess.run(
                ["/bin/sh", "-c", acquisition_command],
                text=True,
                stdout=subprocess.PIPE,
                stderr=subprocess.STDOUT,
                timeout=30,
                check=False,
                preexec_fn=(
                    None
                    if acquisition_is_privileged
                    else drop_to_unprivileged_gamops_boundary
                ),
            )
            self.assertEqual(
                0,
                acquisition.returncode,
                "the gamops steady-state replay must use its accepted passwordless "
                "privilege path to create the canonical lock beneath root-owned /run:\n"
                + acquisition.stdout,
            )
            self.assertTrue(
                lock_directory.is_dir(),
                "the privileged steady-state acquisition must create the canonical lock",
            )

            steady_state_cleanup_task["ansible.builtin.file"]["path"] = str(
                lock_directory
            ).replace("\\", "/")
            cleanup = run_local_ansible(
                [
                    {
                        "name": "Release the canonical lock through steady-state access",
                        "hosts": "localhost",
                        "connection": "local",
                        # The pinned scenario process is already root; the assertion above
                        # protects the production play's gamops privilege transition.
                        "become": False,
                        "gather_facts": False,
                        "vars": {"deployment_lock_acquired": True},
                        "tasks": [steady_state_cleanup_task],
                    }
                ]
            )
            self.assertEqual(
                0,
                cleanup.returncode,
                "steady-state privileged cleanup must release the canonical lock:\n"
                + cleanup.stdout,
            )
            self.assertFalse(
                lock_directory.exists(),
                "steady-state gamops access must release the canonical /run lock",
            )

    def test_stale_terminal_cleanup_cannot_delete_a_competitor_lock(self) -> None:
        site_plays = yaml.safe_load(SITE.read_text(encoding="utf-8"))
        authorization_play = next(
            play
            for play in site_plays
            if isinstance(play, dict)
            and any(
                task.get("name") == "Acquire exclusive deployment lock"
                for task in play.get("tasks", [])
            )
        )
        release_input_play = next(
            play
            for play in site_plays
            if isinstance(play, dict)
            and any(
                task.get("name")
                == "Remove deployment lock after secret-input installation failure"
                for task in play.get("tasks", [])
            )
        )
        release_input_tasks = release_input_play["tasks"]
        early_release_index = next(
            index
            for index, task in enumerate(release_input_tasks)
            if task.get("name")
            == "Remove deployment lock after secret-input installation failure"
        )
        stop_index = next(
            index
            for index, task in enumerate(
                release_input_tasks[early_release_index + 1 :],
                early_release_index + 1,
            )
            if task.get("name")
            == "Stop after recoverable production secret-input installation failure"
        )
        early_release_tasks = copy.deepcopy(
            release_input_tasks[early_release_index:stop_index]
        )
        cleanup_play = next(
            play
            for play in site_plays
            if isinstance(play, dict)
            and play.get("vars", {}).get("canonical_lock_cleanup_play") is True
        )

        with tempfile.TemporaryDirectory(prefix="gam-lock-owner-race-") as temporary:
            lock_directory = Path(temporary) / "deployment.lock"
            escaped_lock_path = str(lock_directory).replace("\\", "/")

            def redirect_canonical_lock(value):
                if isinstance(value, dict):
                    return {
                        key: redirect_canonical_lock(item)
                        for key, item in value.items()
                    }
                if isinstance(value, list):
                    return [redirect_canonical_lock(item) for item in value]
                if isinstance(value, str):
                    redirected = value.replace(
                        "/run/gam-production-deploy.lock",
                        escaped_lock_path,
                    )
                    return redirected.replace(
                        "install -d -m 0750 /run",
                        "install -d -m 0750 "
                        + str(lock_directory.parent).replace("\\", "/"),
                    )
                return value

            authorization_tasks = redirect_canonical_lock(
                copy.deepcopy(authorization_play["tasks"])
            )
            early_release_tasks = redirect_canonical_lock(early_release_tasks)
            terminal_cleanup_tasks = redirect_canonical_lock(
                copy.deepcopy(cleanup_play.get("tasks", []))
            )
            competitor_owner = "competitor-owner-token"
            scenario = [
                {
                    "name": "Acquire the first canonical transaction lock",
                    "hosts": "localhost",
                    "connection": "local",
                    "gather_facts": False,
                    "tasks": authorization_tasks,
                },
                {
                    "name": "Release the first lock through an early failure branch",
                    "hosts": "localhost",
                    "connection": "local",
                    "gather_facts": False,
                    "vars": {"production_secret_input_install_failed": True},
                    "tasks": early_release_tasks,
                },
                {
                    "name": "Let a competing invocation acquire the released lock",
                    "hosts": "localhost",
                    "connection": "local",
                    "become": True,
                    "gather_facts": False,
                    "tasks": [
                        {
                            "name": "Create the competitor lock",
                            "ansible.builtin.file": {
                                "path": escaped_lock_path,
                                "state": "directory",
                                "mode": "0750",
                            },
                        },
                        {
                            "name": "Write the competitor ownership token",
                            "ansible.builtin.copy": {
                                "content": competitor_owner + "\n",
                                "dest": escaped_lock_path + "/owner",
                                "mode": "0600",
                            },
                        },
                    ],
                },
                {
                    "name": "Run stale terminal cleanup from the first invocation",
                    "hosts": "localhost",
                    "connection": "local",
                    "become": True,
                    "gather_facts": False,
                    "vars": {"canonical_lock_cleanup_play": True},
                    "tasks": terminal_cleanup_tasks,
                },
            ]
            result = run_local_ansible(
                scenario,
                environment_overrides={
                    "GAM_DEPLOYMENT_APPROVAL": "approved",
                    "GAM_DEPLOYMENT_APPROVAL_RECORD": "REQ-OPS-008-owner-race",
                },
            )

            self.assertEqual(
                0,
                result.returncode,
                "the owner-race scenario must reach terminal cleanup:\n" + result.stdout,
            )
            inspection = run_local_ansible(
                [
                    {
                        "name": "Inspect and remove the preserved root-owned competitor lock",
                        "hosts": "localhost",
                        "connection": "local",
                        "become": True,
                        "gather_facts": False,
                        "vars": {"expected_competitor_owner": competitor_owner},
                        "tasks": [
                            {
                                "name": "Verify the competing lock identity",
                                "block": [
                                    {
                                        "name": "Inspect the preserved competitor lock",
                                        "ansible.builtin.stat": {
                                            "path": escaped_lock_path,
                                        },
                                        "register": "preserved_competitor_lock",
                                    },
                                    {
                                        "name": "Read the preserved competitor owner token",
                                        "ansible.builtin.slurp": {
                                            "src": escaped_lock_path + "/owner",
                                        },
                                        "register": "preserved_competitor_owner",
                                    },
                                    {
                                        "name": "Assert root ownership and competitor identity",
                                        "ansible.builtin.assert": {
                                            "that": [
                                                "preserved_competitor_lock.stat.isdir",
                                                "preserved_competitor_lock.stat.uid == 0",
                                                "preserved_competitor_lock.stat.gid == 0",
                                                "(preserved_competitor_owner.content | b64decode | trim) == expected_competitor_owner",
                                            ]
                                        },
                                    },
                                ],
                                "always": [
                                    {
                                        "name": "Remove the root-owned competitor fixture",
                                        "ansible.builtin.file": {
                                            "path": escaped_lock_path,
                                            "state": "absent",
                                        },
                                    }
                                ],
                            }
                        ],
                    }
                ]
            )
            self.assertEqual(
                0,
                inspection.returncode,
                "stale terminal cleanup must preserve the root-owned competing lock and "
                "its ownership token before privileged fixture teardown:\n"
                + inspection.stdout,
            )

    def test_first_same_release_replay_reports_explained_audit_record_write(self) -> None:
        release_tasks = {
            str(task.get("name")): copy.deepcopy(task)
            for task, _ in playbook_tasks_with_ancestors(DEPLOY_RELEASE)
            if task.get("name") in {
                "Record release result",
                "Record successful same-release convergence",
            }
        }
        self.assertEqual(
            {
                "Record release result",
                "Record successful same-release convergence",
            },
            set(release_tasks),
            "the scenario requires both real release-result recording tasks",
        )

        with tempfile.TemporaryDirectory(prefix="gam-first-same-release-replay-") as temporary:
            release_record = Path(temporary) / "production-releases.jsonl"
            scenario_vars = {
                "release_record_file": str(release_record).replace("\\", "/"),
                "release_pair": "backend-v1+frontend-v1",
                "release_id": "release-v1",
                "selected_release": {
                    "backend": {
                        "image": "ghcr.io/gam/backend@sha256:" + "1" * 64,
                        "release_commit": "a" * 40,
                    },
                    "frontend": {
                        "repository": "gam/frontend",
                        "tag": "v1.0.0",
                        "artifact": "gam-frontend-v1.0.0.tar.gz",
                        "sha256": "2" * 64,
                        "release_commit": "b" * 40,
                    },
                },
                "developer_approval": "approved",
                "developer_approval_record": "REQ-OPS-010-test",
                "database_change_required": False,
            }

            def record_result(task_name: str) -> subprocess.CompletedProcess[str]:
                return run_local_ansible(
                    [
                        {
                            "name": task_name,
                            "hosts": "localhost",
                            "connection": "local",
                            "become": True,
                            "gather_facts": False,
                            "vars": scenario_vars,
                            "tasks": [release_tasks[task_name]],
                        }
                    ]
                )

            initial_apply = record_result("Record release result")
            self.assertEqual(
                0,
                initial_apply.returncode,
                "the initial release result must be recorded before replay:\n"
                + initial_apply.stdout,
            )
            first_replay = record_result("Record successful same-release convergence")
            self.assertEqual(
                0,
                first_replay.returncode,
                "the first same-release replay result recording must succeed:\n"
                + first_replay.stdout,
            )
            inspection = run_local_ansible(
                [
                    {
                        "name": "Inspect and remove the root-owned release audit fixture",
                        "hosts": "localhost",
                        "connection": "local",
                        "become": True,
                        "gather_facts": False,
                        "tasks": [
                            {
                                "name": "Verify the release audit file",
                                "block": [
                                    {
                                        "name": "Inspect the release audit metadata",
                                        "ansible.builtin.stat": {
                                            "path": str(release_record).replace("\\", "/"),
                                        },
                                        "register": "release_audit_file",
                                    },
                                    {
                                        "name": "Read the release audit contents",
                                        "ansible.builtin.slurp": {
                                            "src": str(release_record).replace("\\", "/"),
                                        },
                                        "register": "release_audit_contents",
                                    },
                                    {
                                        "name": "Assert the protected audit contract",
                                        "ansible.builtin.assert": {
                                            "that": [
                                                "release_audit_file.stat.uid == 0",
                                                "release_audit_file.stat.gid == 0",
                                                "release_audit_file.stat.mode == '0640'",
                                                "'same_release' in (release_audit_contents.content | b64decode)",
                                            ]
                                        },
                                    },
                                ],
                                "always": [
                                    {
                                        "name": "Remove the root-owned release audit fixture",
                                        "ansible.builtin.file": {
                                            "path": str(release_record).replace("\\", "/"),
                                            "state": "absent",
                                        },
                                    }
                                ],
                            }
                        ],
                    }
                ]
            )
            self.assertEqual(
                0,
                inspection.returncode,
                "the same-release audit append must preserve root:root mode 0640 "
                "ownership and remain inspectable through the privileged fixture:\n"
                + inspection.stdout,
            )
            self.assertRegex(
                first_replay.stdout,
                r"changed=1\b",
                "the first real same-release replay must report its explained audit-record "
                "append instead of masking that mutation as changed=0:\n"
                + first_replay.stdout,
            )

    def test_same_release_reverifies_retained_artifacts_before_service_restart(self) -> None:
        same_release_scope = next(
            task
            for task, _ in playbook_tasks_with_ancestors(DEPLOY_RELEASE)
            if task.get("name")
            == "Converge the already-active immutable release under the deployment lock"
        )
        block_tasks = list(
            nested_task_nodes_with_guards(
                same_release_scope.get("block", []),
                DEPLOY_RELEASE.parent,
            )
        )
        restart_index = next(
            (
                index
                for index, (task, _) in enumerate(block_tasks)
                if command_argv(task)[:2] == ["docker", "compose"]
                and "up" in command_argv(task)
            ),
            None,
        )
        self.assertIsNotNone(
            restart_index,
            "same-release convergence must identify its first runtime service restart",
        )
        if restart_index is None:
            return

        verification_text = "\n".join(
            yaml.safe_dump(task, sort_keys=False)
            for task, _ in block_tasks[:restart_index]
        )
        missing_signals = [
            description
            for description, marker in (
                ("retained release manifest", "release-manifest.yml"),
                ("retained checksum sidecar", ".sha256"),
                ("independently computed archive digest", "sha256sum"),
                ("safe archive structure", "tar -tzf"),
            )
            if marker not in verification_text
        ]
        self.assertEqual(
            [],
            missing_signals,
            "same-release recovery must repeat selected-artifact verification before "
            "restarting services; missing: " + ", ".join(missing_signals),
        )

    def test_same_release_runtime_artifacts_match_the_verified_selected_pair(self) -> None:
        same_release_scope = next(
            task
            for task, _ in playbook_tasks_with_ancestors(DEPLOY_RELEASE)
            if task.get("name")
            == "Converge the already-active immutable release under the deployment lock"
        )
        block_tasks = list(
            nested_task_nodes_with_guards(
                same_release_scope.get("block", []),
                DEPLOY_RELEASE.parent,
            )
        )
        restart_index = next(
            (
                index
                for index, (task, _) in enumerate(block_tasks)
                if command_argv(task)[:2] == ["docker", "compose"]
                and "up" in command_argv(task)
            ),
            None,
        )
        self.assertIsNotNone(
            restart_index,
            "same-release convergence must identify its first runtime service restart",
        )
        if restart_index is None:
            return

        pre_restart_tasks = [task for task, _ in block_tasks[:restart_index]]

        override_sources = {
            str(task.get("register", ""))
            for task in pre_restart_tasks
            if isinstance(task.get("ansible.builtin.slurp"), dict)
            and "compose.override.yml"
            in str(task["ansible.builtin.slurp"].get("src", ""))
            and task.get("register")
        }
        override_is_reconstructed = any(
            "compose.override.yml" in yaml.safe_dump(task, sort_keys=False)
            and "selected_release.backend.image"
            in yaml.safe_dump(task, sort_keys=False)
            and any(
                module in task
                for module in ("ansible.builtin.copy", "ansible.builtin.template")
            )
            for task in pre_restart_tasks
        )
        override_is_verified = any(
            "ansible.builtin.assert" in task
            and "selected_release.backend.image"
            in yaml.safe_dump(task, sort_keys=False)
            and any(
                register_name
                and register_name in yaml.safe_dump(task, sort_keys=False)
                for register_name in override_sources
            )
            for task in pre_restart_tasks
        )

        frontend_reset_indices = [
            index
            for index, task in enumerate(pre_restart_tasks)
            if isinstance(task.get("ansible.builtin.file"), dict)
            and task["ansible.builtin.file"].get("state") == "absent"
            and "frontend_current_link"
            in str(task["ansible.builtin.file"].get("path", ""))
            and "/frontend" in str(task["ansible.builtin.file"].get("path", ""))
        ]
        frontend_extract_indices = [
            index
            for index, task in enumerate(pre_restart_tasks)
            if isinstance(task.get("ansible.builtin.unarchive"), dict)
            and "selected_release.frontend.artifact"
            in str(task["ansible.builtin.unarchive"].get("src", ""))
            and "frontend_current_link"
            in str(task["ansible.builtin.unarchive"].get("dest", ""))
            and "/frontend" in str(task["ansible.builtin.unarchive"].get("dest", ""))
        ]
        frontend_is_reconstructed = any(
            reset_index < extract_index
            for reset_index in frontend_reset_indices
            for extract_index in frontend_extract_indices
        )
        frontend_is_verified = any(
            "frontend_current_link" in yaml.safe_dump(task, sort_keys=False)
            and "/frontend" in yaml.safe_dump(task, sort_keys=False)
            and "selected_release.frontend.artifact"
            in yaml.safe_dump(task, sort_keys=False)
            and "diff" in yaml.safe_dump(task, sort_keys=False)
            for task in pre_restart_tasks
        )

        violations = []
        if not (override_is_reconstructed or override_is_verified):
            violations.append(
                "compose.override.yml is neither rebuilt from nor checked against "
                "selected_release.backend.image"
            )
        if not (frontend_is_reconstructed or frontend_is_verified):
            violations.append(
                "current/frontend is neither rebuilt from nor compared with the "
                "verified selected frontend archive"
            )
        self.assertEqual(
            [],
            violations,
            "same-release recovery must not restart independently mutable runtime "
            "artifacts: " + "; ".join(violations),
        )

    def test_same_release_rejected_override_cannot_reach_rescue_handler(self) -> None:
        same_release_scope = next(
            task
            for task, _ in playbook_tasks_with_ancestors(DEPLOY_RELEASE)
            if task.get("name")
            == "Converge the already-active immutable release under the deployment lock"
        )
        rescue_tasks = list(nested_task_nodes(same_release_scope.get("rescue", [])))

        safe_override_selected = False
        pending_caddy_reload = False
        unsafe_flushes = []
        for task in rescue_tasks:
            facts = task.get("ansible.builtin.set_fact", {})
            selected_override = (
                facts.get("active_release_compose_override")
                if isinstance(facts, dict)
                else None
            )
            if selected_override is not None:
                safe_override_selected = (
                    "frontend_current_link" not in str(selected_override)
                )

            notifications = task.get("notify", [])
            if isinstance(notifications, str):
                notifications = [notifications]
            if any(
                notification == "Reload Caddy after maintenance transition"
                for notification in notifications
            ):
                pending_caddy_reload = True

            if (
                task.get("ansible.builtin.meta") == "flush_handlers"
                and pending_caddy_reload
            ):
                if not safe_override_selected:
                    unsafe_flushes.append(str(task.get("name", "unnamed flush")))
                pending_caddy_reload = False

        self.assertEqual(
            [],
            unsafe_flushes,
            "same-release integrity rejection must not flush a Caddy handler while "
            "active_release_compose_override still selects the rejected current "
            "override; unsafe flushes: " + ", ".join(unsafe_flushes),
        )

    def test_same_release_records_results_and_restores_previous_compatible_pair(self) -> None:
        same_release_scope = next(
            task
            for task, _ in playbook_tasks_with_ancestors(DEPLOY_RELEASE)
            if task.get("name")
            == "Converge the already-active immutable release under the deployment lock"
        )
        success_tasks = list(
            nested_task_nodes_with_guards(
                same_release_scope.get("block", []),
                DEPLOY_RELEASE.parent,
            )
        )
        recovery_tasks = list(
            nested_task_nodes_with_guards(
                same_release_scope.get("rescue", []),
                DEPLOY_RELEASE.parent,
            )
        )

        violations = []
        if not any(task_records_release_result(task, "success") for task, _ in success_tasks):
            violations.append("successful same-release recovery is not recorded")
        if not any(task_records_release_result(task, "failed") for task, _ in recovery_tasks):
            violations.append("failed same-release recovery is not recorded")

        compatible_rollback_tasks = [
            task
            for task, guards in recovery_tasks
            if any("database_rollback_compatible" in guard for guard in guards)
        ]
        restores_previous_link = any(
            isinstance(task.get("ansible.builtin.file"), dict)
            and task["ansible.builtin.file"].get("src") == "{{ frontend_previous_link }}"
            and task["ansible.builtin.file"].get("dest") == "{{ frontend_current_link }}"
            for task in compatible_rollback_tasks
        )
        restarts_previous_pair = any(
            command_argv(task)[:2] == ["docker", "compose"]
            and "up" in command_argv(task)
            and "{{ frontend_previous_link }}/compose.override.yml" in command_argv(task)
            for task in compatible_rollback_tasks
        )
        if not restores_previous_link:
            violations.append("compatible failure does not restore the previous frontend link")
        if not restarts_previous_pair:
            violations.append("compatible failure does not restart the previous backend pair")

        self.assertEqual(
            [],
            violations,
            "same-release recovery must record its outcome and restore the previous "
            "compatible pair after failure: " + "; ".join(violations),
        )

    def test_same_release_verifies_previous_pair_before_rollback_activation(self) -> None:
        same_release_scope = next(
            task
            for task, _ in playbook_tasks_with_ancestors(DEPLOY_RELEASE)
            if task.get("name")
            == "Converge the already-active immutable release under the deployment lock"
        )
        rollback_scope = next(
            task
            for task in nested_task_nodes(same_release_scope.get("rescue", []))
            if task.get("name")
            == "Restore the previous compatible pair after same-release failure"
        )
        rollback_tasks = list(nested_task_nodes(rollback_scope.get("block", [])))
        activation_index = next(
            (
                index
                for index, task in enumerate(rollback_tasks)
                if isinstance(task.get("ansible.builtin.file"), dict)
                and task["ansible.builtin.file"].get("src")
                == "{{ frontend_previous_link }}"
                and task["ansible.builtin.file"].get("dest")
                == "{{ frontend_current_link }}"
            ),
            None,
        )
        self.assertIsNotNone(
            activation_index,
            "same-release rollback must expose the previous-pair activation boundary",
        )
        if activation_index is None:
            return

        verification_text = "\n".join(
            yaml.safe_dump(task, sort_keys=False)
            for task in rollback_tasks[:activation_index]
        )
        required_signals = (
            ("previous release manifest identity", "release-manifest.yml"),
            ("parsed previous manifest", "from_yaml"),
            ("previous backend image or digest", "backend.image"),
            ("previous frontend checksum sidecar", ".sha256"),
            ("independently computed previous archive digest", "sha256sum"),
            ("previous frontend tree comparison", "diff -qr"),
        )
        missing_signals = [
            description
            for description, marker in required_signals
            if marker not in verification_text
        ]
        override_sources = {
            str(task.get("register", ""))
            for task in rollback_tasks[:activation_index]
            if isinstance(task.get("ansible.builtin.slurp"), dict)
            and "compose.override.yml"
            in str(task["ansible.builtin.slurp"].get("src", ""))
            and task.get("register")
        }
        override_is_verified = any(
            "ansible.builtin.assert" in task
            and "backend.image" in yaml.safe_dump(task, sort_keys=False)
            and any(
                source in yaml.safe_dump(task, sort_keys=False)
                for source in override_sources
            )
            for task in rollback_tasks[:activation_index]
        )
        override_is_reconstructed = any(
            any(
                module in task
                for module in ("ansible.builtin.copy", "ansible.builtin.template")
            )
            and "compose.override.yml" in yaml.safe_dump(task, sort_keys=False)
            and "backend.image" in yaml.safe_dump(task, sort_keys=False)
            for task in rollback_tasks[:activation_index]
        )
        if not (override_is_verified or override_is_reconstructed):
            missing_signals.append("previous Compose override backend binding")
        self.assertEqual(
            [],
            missing_signals,
            "same-release rollback must verify the complete retained previous pair "
            "before switching the active frontend or restarting services; missing: "
            + ", ".join(missing_signals),
        )

    def test_same_release_audit_failure_cannot_prevent_compatible_rollback(self) -> None:
        same_release_scope = next(
            task
            for task, _ in playbook_tasks_with_ancestors(DEPLOY_RELEASE)
            if task.get("name")
            == "Converge the already-active immutable release under the deployment lock"
        )
        recovery_tasks = list(
            nested_task_nodes_with_failure_isolation(
                same_release_scope.get("rescue", []),
            )
        )
        failed_record_entries = [
            (index, protected)
            for index, (task, protected) in enumerate(recovery_tasks)
            if task_records_release_result(task, "failed")
        ]
        self.assertEqual(
            1,
            len(failed_record_entries),
            "same-release recovery must have one failed-result audit write",
        )
        if len(failed_record_entries) != 1:
            return

        rollback_index = next(
            (
                index
                for index, (task, _) in enumerate(recovery_tasks)
                if isinstance(task.get("ansible.builtin.file"), dict)
                and task["ansible.builtin.file"].get("src")
                == "{{ frontend_previous_link }}"
                and task["ansible.builtin.file"].get("dest")
                == "{{ frontend_current_link }}"
            ),
            None,
        )
        self.assertIsNotNone(
            rollback_index,
            "same-release failure must expose the previous compatible restoration step",
        )
        if rollback_index is None:
            return

        record_index, record_failure_isolated = failed_record_entries[0]
        self.assertTrue(
            record_failure_isolated or rollback_index < record_index,
            "a failed audit write must be isolated or occur after compatible rollback so "
            "recording failure cannot prevent restoration",
        )

    def test_failed_production_play_cannot_strand_the_canonical_lock(self) -> None:
        site_plays = yaml.safe_load(SITE.read_text(encoding="utf-8"))
        acquisition_task = copy.deepcopy(next(
            task
            for play in site_plays
            if isinstance(play, dict)
            for task in play.get("tasks", [])
            if task.get("name") == "Acquire exclusive deployment lock"
        ))
        ownership_task = copy.deepcopy(next(
            task
            for play in site_plays
            if isinstance(play, dict)
            for task in play.get("tasks", [])
            if task.get("name") == "Mark deployment lock as acquired"
        ))

        def direct_tasks(play: dict) -> list[dict]:
            tasks: list[dict] = []
            for section in ("pre_tasks", "tasks", "post_tasks"):
                section_tasks = play.get(section, [])
                if isinstance(section_tasks, list):
                    tasks.extend(section_tasks)
            return tasks

        cleanup_plays = []
        for play in site_plays:
            if not isinstance(play, dict) or play.get("import_playbook"):
                continue
            nodes = list(nested_task_nodes(direct_tasks(play)))
            clears_failed_hosts = any(
                task.get("ansible.builtin.meta") == "clear_host_errors" for task in nodes
            )
            releases_canonical_lock = any(
                task.get("name") == "Release deployment lock"
                and isinstance(task.get("ansible.builtin.file"), dict)
                and task["ansible.builtin.file"].get("state") == "absent"
                for task in nodes
            )
            if clears_failed_hosts or releases_canonical_lock:
                cleanup_play = copy.deepcopy(play)
                cleanup_play["hosts"] = "localhost"
                cleanup_play["connection"] = "local"
                cleanup_play.pop("remote_user", None)
                cleanup_play.pop("vars_files", None)
                cleanup_plays.append(cleanup_play)

        self.assertTrue(
            cleanup_plays,
            "the canonical site must expose a post-transaction lock cleanup path",
        )

        with tempfile.TemporaryDirectory(prefix="gam-canonical-lock-failure-") as temporary:
            lock_directory = Path(temporary) / "deployment.lock"
            lock_path = str(lock_directory)

            def redirect_lock_path(value):
                if isinstance(value, dict):
                    return {key: redirect_lock_path(item) for key, item in value.items()}
                if isinstance(value, list):
                    return [redirect_lock_path(item) for item in value]
                if isinstance(value, str):
                    return value.replace(
                        "/run/gam-production-deploy.lock",
                        lock_path.replace("\\", "/"),
                    )
                return value

            acquisition_task = redirect_lock_path(acquisition_task)
            cleanup_plays = redirect_lock_path(cleanup_plays)
            scenario = [
                {
                    "name": "Acquire the canonical lock before the injected failure",
                    "hosts": "localhost",
                    "connection": "local",
                    "gather_facts": False,
                    "tasks": [acquisition_task, ownership_task],
                },
                {
                    "name": "Inject a production-play failure after lock acquisition",
                    "hosts": "localhost",
                    "connection": "local",
                    "gather_facts": False,
                    "tasks": [
                        {
                            "name": "Fail the representative production play",
                            "ansible.builtin.fail": {
                                "msg": "intentional post-lock production failure"
                            },
                        }
                    ],
                },
                *cleanup_plays,
            ]
            result = run_local_ansible(scenario)

            self.assertFalse(
                lock_directory.exists(),
                "a production play failure after approval and acquisition must not strand "
                "the exclusive canonical lock; Ansible skipped cleanup after the host failed:\n"
                + result.stdout,
            )
            self.assertNotEqual(
                0,
                result.returncode,
                "terminal lock cleanup must not convert the original production failure "
                "into a successful playbook result:\n" + result.stdout,
            )
            self.assertIn(
                "intentional post-lock production failure",
                result.stdout,
                "terminal lock cleanup must preserve the original failure evidence",
            )

    def test_terminal_cleanup_failure_cannot_report_success_with_lock_present(self) -> None:
        with tempfile.TemporaryDirectory(prefix="gam-terminal-cleanup-failure-") as temporary:
            lock_directory = Path(temporary) / "deployment.lock"
            escaped_lock_path = str(lock_directory).replace("\\", "/")
            scenario = [
                {
                    "name": "Acquire the representative canonical lock",
                    "hosts": "localhost",
                    "connection": "local",
                    "gather_facts": False,
                    "tasks": [
                        {
                            "name": "Create representative canonical lock",
                            "ansible.builtin.file": {
                                "path": escaped_lock_path,
                                "state": "directory",
                            },
                        },
                        site_lock_ownership_fact_task(),
                    ],
                },
                {
                    "name": "Fail while executing terminal canonical cleanup",
                    "hosts": "localhost",
                    "connection": "local",
                    "gather_facts": False,
                    "vars": {"canonical_lock_cleanup_play": True},
                    "tasks": [
                        {
                            "name": "Inject terminal cleanup failure",
                            "ansible.builtin.fail": {
                                "msg": "intentional terminal cleanup failure"
                            },
                        },
                        {
                            "name": "Remove representative canonical lock",
                            "ansible.builtin.file": {
                                "path": escaped_lock_path,
                                "state": "absent",
                            },
                        },
                    ],
                },
            ]
            result = run_local_ansible(scenario)

            self.assertTrue(
                lock_directory.exists(),
                "the scenario must prove cleanup failed before the lock was removed",
            )
            self.assertNotEqual(
                0,
                result.returncode,
                "a failure originating in terminal cleanup must remain observable when "
                "the canonical lock is still present:\n" + result.stdout,
            )
            self.assertIn(
                "intentional terminal cleanup failure",
                result.stdout,
                "the terminal result must retain the cleanup failure evidence",
            )

    def test_unreachable_host_stops_mutations_and_preserves_terminal_cleanup(self) -> None:
        with tempfile.TemporaryDirectory(prefix="gam-unreachable-transaction-") as temporary:
            temporary_root = Path(temporary)
            fake_bin = temporary_root / "bin"
            fake_bin.mkdir()
            fake_ssh = fake_bin / "ssh"
            fake_ssh.write_text(
                "#!/bin/sh\n"
                "echo 'intentional unreachable gamops transport' >&2\n"
                "exit 255\n",
                encoding="utf-8",
            )
            fake_ssh.chmod(0o755)
            lock_directory = temporary_root / "deployment.lock"
            escaped_lock_path = str(lock_directory).replace("\\", "/")
            mutation_marker = temporary_root / "forbidden-local-mutation"
            scenario = [
                {
                    "name": "Acquire the lock and register an unreachable production host",
                    "hosts": "localhost",
                    "connection": "local",
                    "gather_facts": False,
                    "tasks": [
                        {
                            "name": "Create representative canonical lock",
                            "ansible.builtin.file": {
                                "path": escaped_lock_path,
                                "state": "directory",
                            },
                        },
                        site_lock_ownership_fact_task(),
                        {
                            "name": "Register the representative gamops target",
                            "ansible.builtin.add_host": {
                                "name": "unreachable_production",
                                "groups": "unreachable_targets",
                                "ansible_connection": "ssh",
                                "ansible_host": "127.0.0.1",
                                "ansible_port": 1,
                                "ansible_user": "gamops",
                                "ansible_ssh_common_args": "-o ConnectTimeout=1",
                            },
                        },
                    ],
                },
                {
                    "name": "Inject the first unreachable gamops connection",
                    "hosts": "unreachable_targets",
                    "gather_facts": False,
                    "tasks": [
                        {
                            "name": "Reach the representative production host",
                            "ansible.builtin.ping": {},
                        }
                    ],
                },
                {
                    "name": "Represent forbidden post-failure AWS or controller mutation",
                    "hosts": "localhost",
                    "connection": "local",
                    "gather_facts": False,
                    "tasks": [
                        {
                            "name": "Write forbidden mutation marker",
                            "ansible.builtin.copy": {
                                "dest": str(mutation_marker).replace("\\", "/"),
                                "content": "mutation escaped transaction failure\n",
                            },
                        }
                    ],
                },
                {
                    "name": "Run terminal cleanup after the unreachable failure",
                    "hosts": "localhost",
                    "connection": "local",
                    "gather_facts": False,
                    "vars": {"canonical_lock_cleanup_play": True},
                    "tasks": [
                        {
                            "name": "Remove representative canonical lock",
                            "ansible.builtin.file": {
                                "path": escaped_lock_path,
                                "state": "absent",
                            },
                        }
                    ],
                },
            ]
            result = run_local_ansible(
                scenario,
                {
                    "PATH": str(fake_bin) + os.pathsep + os.environ.get("PATH", ""),
                },
            )

            self.assertFalse(
                mutation_marker.exists(),
                "an unreachable production host after lock acquisition must stop all "
                "later localhost and AWS mutations:\n" + result.stdout,
            )
            self.assertFalse(
                lock_directory.exists(),
                "an unreachable production host must still permit terminal lock cleanup:\n"
                + result.stdout,
            )
            self.assertNotEqual(
                0,
                result.returncode,
                "terminal cleanup must retain the original unreachable-host failure",
            )
            self.assertIn(
                "UNREACHABLE!",
                result.stdout,
                "the scenario must exercise Ansible's unreachable-host result path",
            )

    def test_pre_gamops_failures_keep_the_acquiring_identity_cleanup_path(self) -> None:
        site_plays = yaml.safe_load(SITE.read_text(encoding="utf-8"))
        baseline_plays = yaml.safe_load(
            (ANSIBLE_ROOT / "playbooks" / "production-host-baseline.yml").read_text(
                encoding="utf-8"
            )
        )
        acquisition_play = next(
            play
            for play in site_plays
            if isinstance(play, dict)
            and any(
                task.get("name") == "Acquire exclusive deployment lock"
                for task in play.get("tasks", [])
            )
        )
        acquiring_identity = acquisition_play.get("remote_user") or "root"

        bootstrap_play = next(
            play
            for play in baseline_plays
            if isinstance(play, dict)
            and play.get("name") == "Bootstrap operations access from the root connection"
        )
        steady_state_play = next(
            play
            for play in baseline_plays
            if isinstance(play, dict)
            and play.get("name")
            == "Verify operations access and configure the Ubuntu 24.04 host baseline"
        )
        self.assertTrue(
            any(
                task.get("name") == "Require real bootstrap host, network, and key inputs"
                for task in bootstrap_play.get("pre_tasks", [])
            ),
            "the lock cleanup test must cover bootstrap-input validation failure",
        )
        self.assertTrue(
            any(
                isinstance(role, dict) and role.get("role") == "operations-users"
                or role == "operations-users"
                for role in bootstrap_play.get("roles", [])
            ),
            "the lock cleanup test must cover operations-user creation failure",
        )
        self.assertTrue(
            any(
                task.get("name") == "Verify a new gamops SSH connection"
                for task in steady_state_play.get("pre_tasks", [])
            ),
            "the lock cleanup test must cover failure of the new gamops connection",
        )

        cleanup_identities = []
        for play in site_plays:
            if not isinstance(play, dict) or play.get("import_playbook"):
                continue
            for section in ("pre_tasks", "tasks", "post_tasks"):
                for task in nested_task_nodes(play.get(section, [])):
                    if (
                        task.get("name") == "Release deployment lock"
                        and isinstance(task.get("ansible.builtin.file"), dict)
                        and task["ansible.builtin.file"].get("state") == "absent"
                    ):
                        cleanup_identities.append(
                            task.get("remote_user", play.get("remote_user"))
                        )

        self.assertIn(
            acquiring_identity,
            cleanup_identities,
            "bootstrap validation, operations-user creation, or first gamops connection "
            "can fail before gamops is usable; terminal lock cleanup must retain a path "
            f"through the acquiring SSH identity ({acquiring_identity!r}), but cleanup "
            f"only exposes {cleanup_identities!r}",
        )

    def test_canonical_ci_executes_runtime_boundaries_and_full_site_syntax_check(self) -> None:
        workflow = yaml.safe_load(CI_WORKFLOW.read_text(encoding="utf-8"))
        steps = workflow["jobs"]["build-and-test"]["steps"]
        runnable_steps = [step for step in steps if isinstance(step.get("run"), str)]

        runtime_suite_steps = [
            step
            for step in runnable_steps
            if "operations/ansible/test_production_runtime_boundaries.py"
            in str(step["run"]).replace("\\", "/")
            and re.search(r"(^|\s)python(?:3(?:\.\d+)?)?(\s|$)", str(step["run"]))
        ]
        syntax_steps = []
        for step in runnable_steps:
            command = " ".join(str(step["run"]).replace("\\", "/").split())
            working_directory = str(step.get("working-directory", "")).replace("\\", "/")
            runs_syntax_check = "ansible-playbook" in command and "--syntax-check" in command
            from_ansible_directory = working_directory.rstrip("/") == "operations/ansible"
            targets_inventory = (
                "operations/ansible/inventory/production.yml" in command
                or (from_ansible_directory and "inventory/production.yml" in command)
            )
            targets_full_site = (
                "operations/ansible/site.yml" in command
                or (from_ansible_directory and re.search(r"(^|\s)site\.yml($|\s)", command))
            )
            if runs_syntax_check and targets_inventory and targets_full_site:
                syntax_steps.append(step)

        missing_ci_contracts = []
        if not runtime_suite_steps:
            missing_ci_contracts.append("production runtime-boundary suite")
        if not syntax_steps:
            missing_ci_contracts.append("full production site.yml syntax check")
        self.assertEqual(
            [],
            missing_ci_contracts,
            "canonical CI must enforce both operational verification levels; missing: "
            + ", ".join(missing_ci_contracts),
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

    def test_failed_same_release_rollback_health_reenters_maintenance(self) -> None:
        same_release_scope = next(
            task
            for task, _ in playbook_tasks_with_ancestors(DEPLOY_RELEASE)
            if task.get("name")
            == "Converge the already-active immutable release under the deployment lock"
        )
        rollback_scope = next(
            task
            for task in nested_task_nodes(same_release_scope.get("rescue", []))
            if task.get("name")
            == "Restore the previous compatible pair after same-release failure"
        )
        rollback_tasks = list(nested_task_nodes(rollback_scope.get("block", [])))
        health_index = next(
            index
            for index, task in enumerate(rollback_tasks)
            if task.get("name")
            == "Verify public health after same-release compatible rollback"
        )
        disables_maintenance_before_health = any(
            maintenance_value(task) == "false"
            for task in rollback_tasks[:health_index]
        )

        health_failure_rescue = list(
            nested_task_nodes(rollback_scope.get("rescue", []))
        )
        maintenance_reentry_indices = [
            index
            for index, task in enumerate(health_failure_rescue)
            if maintenance_value(task) == "true"
        ]
        handler_flush_indices = [
            index
            for index, task in enumerate(health_failure_rescue)
            if task.get("ansible.builtin.meta") == "flush_handlers"
        ]
        reapplies_maintenance = any(
            maintenance_index < flush_index
            for maintenance_index in maintenance_reentry_indices
            for flush_index in handler_flush_indices
        )

        self.assertTrue(
            not disables_maintenance_before_health or reapplies_maintenance,
            "if restored-pair public health fails after maintenance was disabled, the "
            "rollback failure path must re-enable and apply maintenance before exiting",
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
