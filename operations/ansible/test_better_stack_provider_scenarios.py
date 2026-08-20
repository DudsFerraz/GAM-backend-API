"""Executable Better Stack provider scenarios for the production Ansible tasks.

These tests deliberately execute the task objects from ``site.yml``.  The fake
HTTP service models only the provider boundary: pagination, list summaries,
detail responses, mutations, and persisted provider state.  Reconciliation
logic remains owned and executed by Ansible.

The scenarios require the pinned Ansible runtime and therefore run on the
canonical Ubuntu CI runner.  Windows is not skipped or treated as a pass: an
attempt to run the file there fails if the Ansible CLI cannot start.
"""

from __future__ import annotations

import base64
import concurrent.futures
import copy
import json
import os
import re
import shutil
import subprocess
import sys
import tempfile
import threading
import unittest
from http.client import HTTPConnection
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from typing import Any
from urllib.parse import parse_qs, urlparse

import yaml


ANSIBLE_ROOT = Path(__file__).resolve().parent
SITE = ANSIBLE_ROOT / "site.yml"
GROUP_VARS = ANSIBLE_ROOT / "group_vars" / "production.yml"

COLLECTOR_NAME = "GAM production metrics"
COLLECTOR_SOURCE_ID = 9876
DASHBOARD_NAME = "GAM production"
CHARTS = (
    ("201", "GAM proxy health", "proxy", "avg"),
    ("202", "GAM backend health", "backend", "avg"),
    ("203", "GAM PostgreSQL health", "postgresql", "avg"),
    ("204", "GAM filesystem usage", "filesystem_usage_percent", "max"),
)
ALERTS = (
    ("301", "GAM proxy service unhealthy", 201, "lower_than", 1,
     "GAM proxy service is unhealthy", {"environment": "production", "service": "proxy"}),
    ("302", "GAM backend service unhealthy", 202, "lower_than", 1,
     "GAM backend service is unhealthy", {"environment": "production", "service": "backend"}),
    ("303", "GAM postgresql service unhealthy", 203, "lower_than", 1,
     "GAM PostgreSQL service is unhealthy", {"environment": "production", "service": "postgresql"}),
    ("304", "GAM filesystem usage warning", 204, "higher_than_or_equal", 80,
     "GAM filesystem usage reached the warning threshold",
     {"environment": "production", "service": "filesystem", "severity": "warning"}),
    ("305", "GAM filesystem usage critical", 204, "higher_than_or_equal", 90,
     "GAM filesystem usage reached the critical threshold",
    {"environment": "production", "service": "filesystem", "severity": "critical"}),
)
PROXY_ENDPOINT = "http://proxy.internal:2020/metrics"
CADDY_ADMIN_ENDPOINT = "http://proxy.internal:2019/metrics"
BACKEND_ENDPOINT = "http://backend.internal:8080/actuator/prometheus"
POSTGRES_USERNAME = "betterstack_metrics"
POSTGRES_PASSWORD = "generated-postgresql-metrics-secret"
AVAILABILITY_MONITOR_NAME = "GAM production availability"
TLS_MONITOR_NAME = "GAM production TLS certificate"

NO_LOG_AUDIT_CALLBACK = '''
from __future__ import annotations

import json

from ansible.plugins.callback import CallbackBase


class CallbackModule(CallbackBase):
    CALLBACK_VERSION = 2.0
    CALLBACK_TYPE = "aggregate"
    CALLBACK_NAME = "gam_no_log_audit"
    CALLBACK_NEEDS_ENABLED = True

    def _emit(self, result, status):
        task = result._task
        self._display.display(
            "GAM_NO_LOG_AUDIT " + json.dumps({
                "name": task.get_name(),
                "no_log": bool(task.no_log),
                "status": status,
            }, sort_keys=True)
        )

    def v2_runner_on_ok(self, result):
        self._emit(result, "ok")

    def v2_runner_on_failed(self, result, ignore_errors=False):
        self._emit(result, "failed")

    def v2_runner_on_skipped(self, result):
        self._emit(result, "skipped")
'''


def resource(resource_id: str, attributes: dict[str, Any]) -> dict[str, Any]:
    return {"id": resource_id, "type": "test-resource", "attributes": attributes}


def chart_resource(chart_id: str, name: str, selector: str, aggregate: str) -> dict[str, Any]:
    metric_name = "up" if selector != "filesystem_usage_percent" else "node_filesystem_usage_percent"
    if selector in {"proxy", "backend"}:
        label_predicate = f"AND label('_service') = '{selector}'"
    elif selector == "postgresql":
        label_predicate = "AND label('_host') = 'postgres.internal'"
    else:
        label_predicate = ""
    return resource(
        chart_id,
        {
            "name": name,
            "chart_type": "line_chart",
            "queries": [{
                "query_type": "sql_expression",
                "source_variable": "source",
                "sql_query": (
                    "SELECT {{time}} AS time, "
                    f"{aggregate}Merge(value_{aggregate}) AS value "
                    "FROM {{source}} "
                    "WHERE dt BETWEEN {{start_time}} AND {{end_time}} "
                    f"AND name = '{metric_name}' {label_predicate} "
                    "GROUP BY time"
                ),
            }],
        },
    )


def alert_resource(
    alert_id: str,
    name: str,
    chart_id: int,
    operator: str,
    value: int,
    incident_cause: str,
    metadata: dict[str, str],
) -> dict[str, Any]:
    return resource(
        alert_id,
        {
            "name": name,
            "dashboard_id": 100,
            "chart_id": chart_id,
            "alert_type": "threshold",
            "operator": operator,
            "value": value,
            "check_period": 300,
            "confirmation_period": 0,
            "recovery_period": 300,
            "email": True,
            "push": True,
            "incident_cause": incident_cause,
            "metadata": metadata,
        },
    )


class ProviderState:
    """Stateful, provider-shaped boundary with deliberately paginated lists."""

    def __init__(self) -> None:
        self.requests: list[tuple[str, str, dict[str, Any] | None]] = []
        self.collectors: list[dict[str, Any]] = []
        self.dashboards: list[dict[str, Any]] = []
        self.charts: list[dict[str, Any]] = []
        self.targets: list[dict[str, Any]] = []
        self.alerts: list[dict[str, Any]] = []
        self.monitors: list[dict[str, Any]] = []
        self.paginated_filtered_paths: set[str] = set()
        self.filtered_page_one: dict[str, list[dict[str, Any]]] = {}
        self.reject_postgresql_target_patch = False
        self.target_discovery_barrier: threading.Barrier | None = None
        self.target_discovery_barrier_uses = 0
        self.target_discovery_barrier_lock = threading.Lock()
        self.target_readiness_snapshots: list[list[dict[str, Any]]] = []
        self.active_target_readiness_snapshot: list[dict[str, Any]] | None = None
        self.target_readiness_snapshot_lock = threading.Lock()
        self.stale_recovery_barrier: threading.Barrier | None = None
        self.cleanup_replacement_lock_path: Path | None = None
        self.cleanup_replacement_owner_token = "successor-owner"
        self.enforced_chart_bindings = {
            "GAM proxy health",
            "GAM backend health",
            "GAM PostgreSQL health",
        }

    def seed_converged_resources(self) -> None:
        if not self.collectors:
            self.collectors = [resource("collector-1", {
                "name": COLLECTOR_NAME,
                "configuration": {"components": self.metrics_only_components()},
                "secret": "generated-provider-secret",
                "source_id": COLLECTOR_SOURCE_ID,
            })]
        self.dashboards = [resource("100", {
            "name": DASHBOARD_NAME,
            "refresh_interval": 300,
            "date_range_from": "now-3h",
            "date_range_to": "now",
            "variables": [{
                "name": "source",
                "variable_type": "source",
                "values": [str(COLLECTOR_SOURCE_ID)],
                "default_values": [str(COLLECTOR_SOURCE_ID)],
            }],
        })]
        self.charts = [chart_resource(*definition) for definition in CHARTS]
        self.targets = [
            resource("401", {"kind": "prometheus", "host": "proxy.internal", "service": "proxy", "endpoint": PROXY_ENDPOINT}),
            resource("402", {"kind": "prometheus", "host": "backend.internal", "service": "backend", "endpoint": BACKEND_ENDPOINT}),
            resource("403", {
                "kind": "postgres",
                "host": "postgres.internal",
                "port": 5432,
                "username": POSTGRES_USERNAME,
                "ssl_mode": "require",
            }),
        ]
        self.alerts = [alert_resource(*definition) for definition in ALERTS]
        self.monitors = [
            resource("501", self.availability_monitor_attributes()),
            resource("502", self.tls_monitor_attributes()),
        ]

    def seed_drifted_resources(self) -> None:
        self.seed_converged_resources()
        self.collectors[0]["attributes"]["configuration"]["components"]["logs_host"] = True
        self.dashboards[0]["attributes"]["refresh_interval"] = 60
        for chart in self.charts:
            chart["attributes"]["chart_type"] = "bar_chart"
        for target in self.targets:
            if target["attributes"]["kind"] == "prometheus":
                target["attributes"]["endpoint"] = "http://old.invalid/metrics"
            else:
                target["attributes"]["port"] = 1
        for alert in self.alerts:
            alert["attributes"]["value"] += 1
        self.monitors[0]["attributes"]["required_keyword"] = "UP"
        self.monitors[1]["attributes"]["ssl_expiration"] = 7

    @staticmethod
    def metrics_only_components() -> dict[str, bool]:
        return {
            "logs_docker": False,
            "logs_host": False,
            "logs_kubernetes": False,
            "logs_collector_internals": False,
            "ebpf_tracing_basic": False,
            "ebpf_tracing_full": False,
            "traces_opentelemetry": False,
            "ebpf_metrics": True,
            "ebpf_red_metrics": True,
            "metrics_databases": True,
        }

    @staticmethod
    def dashboard_attributes() -> dict[str, Any]:
        return {
            "name": DASHBOARD_NAME,
            "refresh_interval": 300,
            "date_range_from": "now-3h",
            "date_range_to": "now",
            "variables": [{
                "name": "source",
                "variable_type": "source",
                "values": [str(COLLECTOR_SOURCE_ID)],
                "default_values": [str(COLLECTOR_SOURCE_ID)],
            }],
        }

    @staticmethod
    def availability_monitor_attributes() -> dict[str, Any]:
        return {
            "pronounceable_name": AVAILABILITY_MONITOR_NAME,
            "url": "https://gam.example.org/api/health",
            "monitor_type": "keyword",
            "required_keyword": '{"status":"UP"}',
            "http_method": "get",
            "check_frequency": 300,
            "confirmation_period": 600,
            "email": True,
            "push": True,
        }

    @staticmethod
    def tls_monitor_attributes() -> dict[str, Any]:
        return {
            "pronounceable_name": TLS_MONITOR_NAME,
            "url": "https://gam.example.org",
            "monitor_type": "status",
            "check_frequency": 3600,
            "ssl_expiration": 30,
            "verify_ssl": True,
            "email": True,
            "push": True,
        }

    def record(self, method: str, path: str, body: dict[str, Any] | None) -> None:
        self.requests.append((method, path, body))

    def clear_requests(self) -> None:
        self.requests.clear()

    def expected_chart_binding(self, chart_name: str) -> str:
        if chart_name == "GAM proxy health":
            target = next(
                item for item in self.targets
                if item["attributes"].get("endpoint") == PROXY_ENDPOINT
            )
            return f"label('_service') = '{target['attributes']['service']}'"
        if chart_name == "GAM backend health":
            target = next(
                item for item in self.targets
                if item["attributes"].get("endpoint") == BACKEND_ENDPOINT
            )
            return f"label('_service') = '{target['attributes']['service']}'"
        if chart_name == "GAM PostgreSQL health":
            target = next(
                item for item in self.targets
                if item["attributes"].get("kind") == "postgres"
            )
            return f"label('_host') = '{target['attributes']['host']}'"
        raise AssertionError(f"no provider identity binding declared for {chart_name}")


class FakeBetterStackHandler(BaseHTTPRequestHandler):
    server: "FakeBetterStackServer"

    def log_message(self, _format: str, *_args: Any) -> None:
        return

    def _body(self) -> dict[str, Any] | None:
        length = int(self.headers.get("Content-Length", "0"))
        return json.loads(self.rfile.read(length)) if length else None

    def _send(self, status: int, payload: dict[str, Any]) -> None:
        encoded = json.dumps(payload).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(encoded)))
        self.end_headers()
        self.wfile.write(encoded)

    def _schema_error(self, message: str) -> None:
        self._send(422, {"errors": message})

    @staticmethod
    def _has_only(body: dict[str, Any], allowed: set[str], required: set[str]) -> bool:
        return required <= body.keys() and body.keys() <= allowed

    @classmethod
    def _valid_collector(cls, body: dict[str, Any], *, creation: bool) -> bool:
        allowed = {"name", "platform", "configuration"} if creation else {"configuration"}
        required = allowed
        if not cls._has_only(body, allowed, required):
            return False
        components = body.get("configuration", {}).get("components", {})
        return components == ProviderState.metrics_only_components()

    @classmethod
    def _valid_dashboard(cls, body: dict[str, Any]) -> bool:
        allowed = {
            "name", "refresh_interval", "date_range_from", "date_range_to",
            "variables", "source_eligibility_sql",
        }
        required = {"name", "refresh_interval", "date_range_from", "date_range_to", "variables"}
        if not cls._has_only(body, allowed, required):
            return False
        sources = [
            variable for variable in body.get("variables", [])
            if variable.get("name") == "source" and variable.get("variable_type") == "source"
        ]
        expected_source = [str(COLLECTOR_SOURCE_ID)]
        return (
            len(sources) == 1
            and sources[0].get("values") == expected_source
            and sources[0].get("default_values") == expected_source
        )

    def _valid_chart(self, body: dict[str, Any]) -> bool:
        if not self._has_only(body, {"name", "chart_type", "queries"}, {"name", "chart_type", "queries"}):
            return False
        queries = body.get("queries", [])
        if body.get("chart_type") != "line_chart" or len(queries) != 1:
            return False
        query = queries[0]
        if not self._has_only(
            query,
            {"query_type", "source_variable", "sql_query"},
            {"query_type", "source_variable", "sql_query"},
        ):
            return False
        sql = query.get("sql_query", "")
        valid_unified_query = (
            query.get("query_type") == "sql_expression"
            and query.get("source_variable") == "source"
            and "FROM {{source}}" in sql
            and "dt BETWEEN {{start_time}} AND {{end_time}}" in sql
            and re.search(r"\bname\s*=\s*'[^']+'", sql) is not None
            and re.search(r"\b(?:avg|max|anyLast)Merge\((?:value_avg|value_max|value_last)\)", sql) is not None
            and "avg(value)" not in sql
            and re.search(r"\bservice\s*=", sql) is None
        )
        if not valid_unified_query:
            return False
        if body["name"] not in self.server.state.enforced_chart_bindings:
            return True
        return (
            self.server.state.expected_chart_binding(body["name"]) in sql
            and "label('service')" not in sql
        )

    @classmethod
    def _valid_target(cls, body: dict[str, Any], *, creation: bool) -> bool:
        kind = body.get("kind") if creation else None
        if kind == "prometheus" or (not creation and "service" in body):
            allowed = {"kind", "host", "service", "endpoint"} if creation else {"host", "service", "endpoint"}
            required = allowed
            valid_prometheus_shape = (
                cls._has_only(body, allowed, required)
                and re.match(r"^https?://[^/]+/", body.get("endpoint", "")) is not None
            )
            if not valid_prometheus_shape:
                return False
            if body.get("service") == "proxy":
                return (
                    body.get("host") == "proxy.internal"
                    and body.get("endpoint") == PROXY_ENDPOINT
                )
            return True
        if kind == "postgres" or not creation:
            allowed = (
                {"kind", "host", "port", "username", "password", "ssl_mode"}
                if creation else {"host", "port", "username", "password", "ssl_mode"}
            )
            return cls._has_only(body, allowed, allowed) and body.get("ssl_mode") == "require"
        return False

    @classmethod
    def _valid_alert(cls, body: dict[str, Any]) -> bool:
        fields = {
            "name", "alert_type", "operator", "value", "check_period",
            "confirmation_period", "recovery_period", "email", "push",
            "incident_cause", "metadata",
        }
        return cls._has_only(body, fields, fields) and body.get("alert_type") == "threshold"

    @classmethod
    def _valid_monitor(cls, body: dict[str, Any]) -> bool:
        common = {
            "pronounceable_name", "url", "monitor_type", "check_frequency",
            "email", "push",
        }
        if body.get("monitor_type") == "keyword":
            fields = common | {"required_keyword", "http_method", "confirmation_period"}
            return cls._has_only(body, fields, fields)
        if body.get("monitor_type") == "status":
            fields = common | {"ssl_expiration", "verify_ssl"}
            return cls._has_only(body, fields, fields)
        return False

    def _paged(
        self,
        values: list[dict[str, Any]],
        page: int,
        *,
        page_one: list[dict[str, Any]] | None = None,
    ) -> dict[str, Any]:
        # Page one contains a provider-owned unrelated summary. Managed objects
        # are intentionally off the first page to exercise every read boundary.
        data = (
            page_one if page_one is not None
            else [resource("unmanaged", {"name": "unmanaged"})]
        ) if page == 1 else values
        parsed = urlparse(self.path)
        per_page = parse_qs(parsed.query).get("per_page", ["100"])[0]

        def page_url(number: int) -> str:
            return f"{self.server.origin}{parsed.path}?page={number}&per_page={per_page}"

        return {
            "data": data,
            "pagination": {
                "first": page_url(1),
                "last": page_url(2),
                "prev": page_url(1) if page == 2 else None,
                "next": page_url(2) if page == 1 else None,
            },
        }

    def _list_response(
        self,
        path: str,
        values: list[dict[str, Any]],
        page: int,
    ) -> dict[str, Any]:
        if path not in self.server.state.paginated_filtered_paths:
            return {"data": values}
        return self._paged(
            values,
            page,
            page_one=self.server.state.filtered_page_one.get(path),
        )

    @staticmethod
    def _dashboard_summary(item: dict[str, Any]) -> dict[str, Any]:
        summary_fields = {
            "name", "refresh_interval", "date_range_from", "date_range_to",
        }
        return {
            "id": item["id"],
            "type": "dashboard",
            "attributes": {
                key: copy.deepcopy(value)
                for key, value in item["attributes"].items()
                if key in summary_fields
            },
        }

    def do_GET(self) -> None:  # noqa: N802 - BaseHTTPRequestHandler API
        state = self.server.state
        state.record("GET", self.path, None)
        parsed = urlparse(self.path)
        path = parsed.path
        page = int(parse_qs(parsed.query).get("page", ["1"])[0])

        if path == "/test/stale-recovery-barrier":
            barrier = state.stale_recovery_barrier
            if barrier is not None:
                try:
                    barrier.wait(timeout=5)
                except threading.BrokenBarrierError:
                    pass
            self._send(200, {"data": {"released": True}})
            return
        if path == "/test/replace-lock-owner":
            lock_path = state.cleanup_replacement_lock_path
            if lock_path is None:
                self._send(500, {"error": "cleanup replacement lock is not configured"})
                return
            lock_path.mkdir(parents=True, exist_ok=True)
            (lock_path / "owner.json").write_text(
                json.dumps({
                    "owner_token": state.cleanup_replacement_owner_token,
                    "lease_expires_epoch": 4102444800,
                }),
                encoding="utf-8",
            )
            self._send(200, {"data": {"owner_token": state.cleanup_replacement_owner_token}})
            return

        if path == "/api/v1/collectors":
            self._send(200, self._list_response(path, state.collectors, page))
            return
        if re.fullmatch(r"/api/v1/collectors/[^/]+", path):
            self._send(200, {"data": state.collectors[0]})
            return
        if re.fullmatch(r"/api/v1/collectors/[^/]+/targets", path):
            with state.target_readiness_snapshot_lock:
                if state.target_readiness_snapshots and page == 1:
                    if len(state.target_readiness_snapshots) > 1:
                        snapshot = state.target_readiness_snapshots.pop(0)
                    else:
                        snapshot = state.target_readiness_snapshots[0]
                    state.active_target_readiness_snapshot = copy.deepcopy(snapshot)
                targets = copy.deepcopy(
                    state.active_target_readiness_snapshot
                    if state.target_readiness_snapshots
                    and state.active_target_readiness_snapshot is not None
                    else state.targets
                )
            barrier = None
            with state.target_discovery_barrier_lock:
                if state.target_discovery_barrier_uses > 0:
                    barrier = state.target_discovery_barrier
                    state.target_discovery_barrier_uses -= 1
            if barrier is not None:
                try:
                    barrier.wait(timeout=1)
                except threading.BrokenBarrierError:
                    pass
            self._send(200, self._paged(
                targets,
                page,
                page_one=state.filtered_page_one.get(path),
            ))
            return
        if path == "/api/v2/dashboards":
            summaries = [self._dashboard_summary(item) for item in state.dashboards]
            if path in state.paginated_filtered_paths:
                page_one = [
                    self._dashboard_summary(item)
                    for item in state.filtered_page_one.get(path, [])
                ]
                payload = self._paged(summaries, page, page_one=page_one)
            else:
                payload = {"data": summaries}
            self._send(200, payload)
            return
        dashboard_match = re.fullmatch(r"/api/v2/dashboards/([^/]+)", path)
        if dashboard_match:
            selected = next(
                item for item in state.dashboards
                if item["id"] == dashboard_match.group(1)
            )
            self._send(200, {"data": selected})
            return
        if path == "/api/v2/dashboards/100/charts":
            self._send(200, self._paged(
                state.charts,
                page,
                page_one=state.filtered_page_one.get(path),
            ))
            return
        if path == "/api/v2/alerts":
            summaries = [
                resource(item["id"], {
                    "name": item["attributes"]["name"],
                    "dashboard_id": item["attributes"]["dashboard_id"],
                    "chart_id": item["attributes"]["chart_id"],
                })
                for item in state.alerts
            ]
            self._send(200, self._paged(summaries, page))
            return
        alert_match = re.fullmatch(r"/api/v2/alerts/([^/]+)", path)
        if alert_match:
            selected = next(item for item in state.alerts if item["id"] == alert_match.group(1))
            self._send(200, {"data": selected})
            return
        if path == "/api/v2/monitors":
            requested_name = parse_qs(parsed.query).get("pronounceable_name", [""])[0]
            selected = [
                item for item in state.monitors
                if item["attributes"]["pronounceable_name"] == requested_name
            ]
            if path in state.paginated_filtered_paths:
                page_one = [
                    item for item in state.filtered_page_one.get(path, [])
                    if item["attributes"]["pronounceable_name"] == requested_name
                ]
                self._send(200, self._paged(selected, page, page_one=page_one))
            else:
                self._send(200, {"data": selected})
            return
        self._send(404, {"error": f"unhandled GET {self.path}"})

    def do_POST(self) -> None:  # noqa: N802 - BaseHTTPRequestHandler API
        state = self.server.state
        body = self._body() or {}
        state.record("POST", self.path, body)
        path = urlparse(self.path).path
        if path == "/api/v1/collectors":
            if not self._valid_collector(body, creation=True):
                self._schema_error("invalid collector payload")
                return
            if state.collectors:
                self._send(409, {"error": "collector already exists on a later page"})
                return
            created = resource("collector-1", {
                "name": body["name"],
                "configuration": body["configuration"],
                "secret": "generated-provider-secret",
                "source_id": COLLECTOR_SOURCE_ID,
            })
            state.collectors = [created]
            self._send(201, {"data": created})
            return
        if path == "/test/installer":
            self._send(204, {})
            return
        if path == "/api/v2/dashboards":
            if not self._valid_dashboard(body):
                self._schema_error("dashboard must bind its source variable")
                return
            if state.dashboards:
                self._send(409, {"error": "dashboard already exists on a later page"})
                return
            created = resource("100", body)
            state.dashboards.append(created)
            self._send(201, {"data": created})
            return
        chart_match = re.fullmatch(r"/api/v2/dashboards/100/charts", path)
        if chart_match:
            if not self._valid_chart(body):
                self._schema_error("invalid unified-metrics chart query")
                return
            chart_id = next(item[0] for item in CHARTS if item[1] == body["name"])
            created = resource(chart_id, body)
            state.charts.append(created)
            self._send(201, {"data": created})
            return
        target_match = re.fullmatch(r"/api/v1/collectors/[^/]+/targets", path)
        if target_match:
            if not self._valid_target(body, creation=True):
                self._schema_error("invalid metric target payload")
                return
            target_id = str(401 + len(state.targets))
            attributes = {
                key: copy.deepcopy(value)
                for key, value in body.items()
                if key != "password"
            }
            created = resource(target_id, attributes)
            state.targets.append(created)
            self._send(201, {"data": created})
            return
        alert_create = re.fullmatch(r"/api/v2/dashboards/100/charts/([^/]+)/alerts", path)
        if alert_create:
            if not self._valid_alert(body):
                self._schema_error("invalid alert payload")
                return
            alert_id = str(301 + len(state.alerts))
            attributes = {"dashboard_id": 100, "chart_id": int(alert_create.group(1)), **body}
            created = resource(alert_id, attributes)
            state.alerts.append(created)
            self._send(201, {"data": created})
            return
        if path == "/api/v2/monitors":
            if not self._valid_monitor(body):
                self._schema_error("invalid monitor payload")
                return
            if any(item["attributes"]["pronounceable_name"] == body["pronounceable_name"] for item in state.monitors):
                self._send(409, {"error": "monitor already exists on a later page"})
                return
            monitor_id = str(501 + len(state.monitors))
            normalized = copy.deepcopy(body)
            if "http_method" in normalized:
                normalized["http_method"] = normalized["http_method"].lower()
            created = resource(monitor_id, normalized)
            state.monitors.append(created)
            self._send(201, {"data": created})
            return
        self._send(500, {"error": f"unexpected creation {self.path}"})

    def do_PATCH(self) -> None:  # noqa: N802 - BaseHTTPRequestHandler API
        state = self.server.state
        body = self._body() or {}
        state.record("PATCH", self.path, body)
        path = urlparse(self.path).path
        collector_match = re.fullmatch(r"/api/v1/collectors/[^/]+", path)
        if collector_match:
            if not self._valid_collector(body, creation=False):
                self._schema_error("invalid collector patch")
                return
            state.collectors[0]["attributes"].update(body)
            self._send(200, {"data": state.collectors[0]})
            return
        dashboard_match = re.fullmatch(r"/api/v2/dashboards/([^/]+)", path)
        if dashboard_match:
            if not self._valid_dashboard(body):
                self._schema_error("invalid dashboard patch")
                return
            selected = next(item for item in state.dashboards if item["id"] == dashboard_match.group(1))
            selected["attributes"].update(body)
            self._send(200, {"data": selected})
            return
        chart_match = re.fullmatch(r"/api/v2/dashboards/100/charts/([^/]+)", path)
        if chart_match:
            if not self._valid_chart(body):
                self._schema_error("invalid chart patch")
                return
            chart_candidates = state.charts + state.filtered_page_one.get(
                "/api/v2/dashboards/100/charts", []
            )
            selected = next(item for item in chart_candidates if item["id"] == chart_match.group(1))
            selected["attributes"].update(body)
            self._send(200, {"data": selected})
            return
        target_match = re.fullmatch(r"/api/v1/collectors/[^/]+/targets/([^/]+)", path)
        if target_match:
            if not self._valid_target(body, creation=False):
                self._schema_error("invalid target patch")
                return
            if "password" in body and state.reject_postgresql_target_patch:
                self._send(503, {"error": "injected PostgreSQL target update failure"})
                return
            target_candidates = state.targets + state.filtered_page_one.get(
                "/api/v1/collectors/collector-1/targets", []
            )
            selected = next(item for item in target_candidates if item["id"] == target_match.group(1))
            selected["attributes"].update({
                key: copy.deepcopy(value)
                for key, value in body.items()
                if key != "password"
            })
            self._send(200, {"data": selected})
            return
        alert_match = re.fullmatch(r"/api/v2/alerts/([^/]+)", path)
        if alert_match:
            if not self._valid_alert(body):
                self._schema_error("invalid alert patch")
                return
            selected = next(item for item in state.alerts if item["id"] == alert_match.group(1))
            selected["attributes"].update(body)
            self._send(200, {"data": selected})
            return
        if re.fullmatch(r"/api/v2/dashboards/[^/]+/charts/[^/]+/alerts/[^/]+", path):
            self._send(404, {"errors": "alert updates use /api/v2/alerts/{id}"})
            return
        monitor_match = re.fullmatch(r"/api/v2/monitors/([^/]+)", path)
        if monitor_match:
            if not self._valid_monitor(body):
                self._schema_error("invalid monitor patch")
                return
            selected = next(item for item in state.monitors if item["id"] == monitor_match.group(1))
            normalized = copy.deepcopy(body)
            if "http_method" in normalized:
                normalized["http_method"] = normalized["http_method"].lower()
            selected["attributes"].update(normalized)
            self._send(200, {"data": selected})
            return
        self._send(500, {"error": f"unexpected reconciliation {self.path}"})

    def do_DELETE(self) -> None:  # noqa: N802 - BaseHTTPRequestHandler API
        state = self.server.state
        state.record("DELETE", self.path, None)
        path = urlparse(self.path).path
        target_match = re.fullmatch(
            r"/api/v1/collectors/[^/]+/targets/([^/]+)",
            path,
        )
        if target_match:
            target_id = target_match.group(1)
            selected = next(
                (item for item in state.targets if item["id"] == target_id),
                None,
            )
            if selected is None:
                self._send(404, {"error": "metric target not found"})
                return
            state.targets.remove(selected)
            self._send(204, {})
            return
        self._send(500, {"error": f"unexpected deletion {self.path}"})


class FakeBetterStackServer(ThreadingHTTPServer):
    def __init__(self, state: ProviderState) -> None:
        super().__init__(("127.0.0.1", 0), FakeBetterStackHandler)
        self.state = state
        self.origin = f"http://127.0.0.1:{self.server_port}"


def production_tasks() -> list[dict[str, Any]]:
    document = yaml.safe_load(SITE.read_text(encoding="utf-8"))
    monitoring_plays = [
        play
        for play in document
        if play.get("hosts") == "production"
        and any(
            task.get("name") == "Discover Better Stack collectors by stable provider name"
            for task in play.get("tasks", [])
        )
    ]
    if len(monitoring_plays) != 1:
        raise AssertionError(
            "site.yml must contain exactly one production play that owns Better Stack reconciliation"
        )
    return monitoring_plays[0]["tasks"]


def task_slice(first_name: str, last_name: str) -> list[dict[str, Any]]:
    tasks = production_tasks()
    names = [task.get("name") for task in tasks]
    if first_name not in names or last_name not in names:
        def nested_names(task: dict[str, Any]) -> set[str]:
            discovered = {str(task.get("name", ""))}
            for section in ("block", "rescue", "always"):
                nested = task.get(section)
                if isinstance(nested, list):
                    for child in nested:
                        discovered.update(nested_names(child))
            return discovered

        containing_tasks = [
            task for task in tasks
            if {first_name, last_name}.issubset(nested_names(task))
        ]
        if len(containing_tasks) == 1:
            return copy.deepcopy(containing_tasks)

        flattened: list[dict[str, Any]] = []

        def append_tasks(items: list[dict[str, Any]]) -> None:
            for item in items:
                flattened.append(item)
                for section in ("block", "rescue", "always"):
                    nested = item.get(section)
                    if isinstance(nested, list):
                        append_tasks(nested)

        append_tasks(tasks)
        tasks = flattened
        names = [task.get("name") for task in tasks]
    first = names.index(first_name)
    last = names.index(last_name)
    return copy.deepcopy(tasks[first:last + 1])


def post_network_target_readiness_tasks() -> list[dict[str, Any]]:
    tasks = production_tasks()
    names = [task.get("name") for task in tasks]
    start = names.index(
        "Connect the Better Stack collector to the private production network"
    ) + 1
    selected = copy.deepcopy(tasks[start:])
    def make_fast(items: list[dict[str, Any]]) -> None:
        for task in items:
            if "delay" in task:
                task["delay"] = 0
            for section in ("block", "rescue", "always"):
                nested = task.get(section)
                if isinstance(nested, list):
                    make_fast(nested)

    make_fast(selected)
    return selected


def concurrent_stale_recovery_tasks_with_lock(lock_path: Path) -> list[dict[str, Any]]:
    section = task_slice(
        "Hold one exclusive lock while reconciling the Better Stack PostgreSQL target",
        "Hold one exclusive lock while reconciling the Better Stack PostgreSQL target",
    )[0]
    block = section["block"]
    start = next(
        index for index, task in enumerate(block)
        if task.get("name") == "Inspect the exclusive reconciliation lock clock"
    )
    end = next(
        index for index, task in enumerate(block)
        if task.get("name") == "Record the reconciliation lock owner and lease"
    )
    selected = copy.deepcopy(block[start:end + 1])
    stale_index = next(
        index for index, task in enumerate(selected)
        if task.get("name")
        == "Determine whether the prior reconciliation lock owner is abandoned or its lease expired"
    )
    selected[stale_index + 1:stale_index + 1] = [
        {
            "name": "Synchronize concurrent stale-lock recovery contenders",
            "ansible.builtin.uri": {
                "url": "{{ better_stack_telemetry_api_url }}/test/stale-recovery-barrier",
                "method": "GET",
                "status_code": [200],
            },
        },
        {
            "name": "Apply deterministic stale-lock contender delay",
            "ansible.builtin.command": {
                "argv": [
                    sys.executable,
                    "-c",
                    "import time; time.sleep(float('{{ scenario_recovery_delay_seconds }}'))",
                ],
            },
            "changed_when": False,
        },
    ]
    acquisition = next(
        task for task in selected
        if "Acquire the exclusive reconciliation lock" in str(task.get("name", ""))
    )
    acquisition["retries"] = 10
    acquisition["delay"] = 1
    selected.extend([
        {
            "name": "Hold the acquired lock for the concurrent stale-recovery scenario",
            "ansible.builtin.command": {
                "argv": [
                    sys.executable,
                    "-c",
                    "import time; time.sleep(float('{{ scenario_lock_hold_seconds }}'))",
                ],
            },
            "changed_when": False,
        },
        {
            "name": "Read the lock owner before this contender cleans up",
            "ansible.builtin.slurp": {"src": str(lock_path / "owner.json")},
            "register": "scenario_lock_owner_before_cleanup",
        },
        {
            "name": "Verify this contender still owns the lock before cleanup",
            "ansible.builtin.assert": {
                "that": [
                    "(scenario_lock_owner_before_cleanup.content | b64decode | from_json).owner_token == inventory_hostname ~ ':' ~ better_stack_lock_clock.stdout ~ ':final'"
                ],
            },
        },
    ])
    wrapped = [{
        "name": "Exercise concurrent stale-lock recovery and token-checked cleanup",
        "block": selected,
        "always": copy.deepcopy(section["always"]),
    }]

    def adapt(value: Any) -> Any:
        if isinstance(value, str):
            return value.replace(
                "/tmp/gam-better-stack-target-reconciliation.lock",
                str(lock_path),
            )
        if isinstance(value, list):
            return [adapt(item) for item in value]
        if isinstance(value, dict):
            return {key: adapt(item) for key, item in value.items()}
        return value

    return adapt(wrapped)


def same_observation_stale_recovery_tasks_with_lock(lock_path: Path) -> list[dict[str, Any]]:
    section = task_slice(
        "Hold one exclusive lock while reconciling the Better Stack PostgreSQL target",
        "Hold one exclusive lock while reconciling the Better Stack PostgreSQL target",
    )[0]
    block = section["block"]
    start = next(
        index for index, task in enumerate(block)
        if task.get("name") == "Inspect the exclusive reconciliation lock clock"
    )
    end = next(
        index for index, task in enumerate(block)
        if task.get("name") == "Record the reconciliation lock owner and lease"
    )
    selected = copy.deepcopy(block[start:end + 1])
    confirm_index = next(
        index for index, task in enumerate(selected)
        if task.get("name") == "Confirm the same stale owner remains before removal"
    )
    selected[confirm_index + 1:confirm_index + 1] = [
        {
            "name": "Synchronize contenders after both revalidate the same stale owner",
            "ansible.builtin.uri": {
                "url": "{{ better_stack_telemetry_api_url }}/test/stale-recovery-barrier",
                "method": "GET",
                "status_code": [200],
            },
        },
        {
            "name": "Apply deterministic post-revalidation contender delay",
            "ansible.builtin.command": {
                "argv": [
                    sys.executable,
                    "-c",
                    "import time; time.sleep(float('{{ scenario_recovery_delay_seconds }}'))",
                ],
            },
            "changed_when": False,
        },
    ]
    acquisition = next(
        task for task in selected
        if "Acquire the exclusive reconciliation lock" in str(task.get("name", ""))
    )
    acquisition["retries"] = 10
    acquisition["delay"] = 1
    selected.extend([
        {
            "name": "Hold successor lock during delayed stale deletion",
            "ansible.builtin.command": {
                "argv": [sys.executable, "-c", "import time; time.sleep(3)"],
            },
            "changed_when": False,
        },
        {
            "name": "Require this successor lock to remain owned",
            "ansible.builtin.slurp": {"src": str(lock_path / "owner.json")},
            "register": "scenario_successor_owner",
        },
        {
            "name": "Verify the successor still owns the lock",
            "ansible.builtin.assert": {
                "that": [
                    "(scenario_successor_owner.content | b64decode | from_json).owner_token == inventory_hostname ~ ':' ~ better_stack_lock_clock.stdout ~ ':final'"
                ],
            },
        },
    ])

    def adapt(value: Any) -> Any:
        if isinstance(value, str):
            return value.replace(
                "/tmp/gam-better-stack-target-reconciliation.lock",
                str(lock_path),
            )
        if isinstance(value, list):
            return [adapt(item) for item in value]
        if isinstance(value, dict):
            return {key: adapt(item) for key, item in value.items()}
        return value

    return adapt([{
        "name": "Exercise same-observation stale-lock contenders",
        "block": selected,
        "always": copy.deepcopy(section["always"]),
    }])


def cleanup_compare_then_delete_tasks_with_lock(lock_path: Path) -> list[dict[str, Any]]:
    section = task_slice(
        "Hold one exclusive lock while reconciling the Better Stack PostgreSQL target",
        "Hold one exclusive lock while reconciling the Better Stack PostgreSQL target",
    )[0]
    cleanup = copy.deepcopy(section["always"])
    cleanup.insert(1, {
        "name": "Replace lock ownership after cleanup comparison but before removal",
        "ansible.builtin.uri": {
            "url": "{{ better_stack_telemetry_api_url }}/test/replace-lock-owner",
            "method": "GET",
            "status_code": [200],
        },
    })

    def adapt(value: Any) -> Any:
        if isinstance(value, str):
            return value.replace(
                "/tmp/gam-better-stack-target-reconciliation.lock",
                str(lock_path),
            )
        if isinstance(value, list):
            return [adapt(item) for item in value]
        if isinstance(value, dict):
            return {key: adapt(item) for key, item in value.items()}
        return value

    return adapt(cleanup)


def lock_helper_with_post_validation_successor(
    lock_path: Path,
    *,
    helper_name: str,
) -> dict[str, Any]:
    section = task_slice(
        "Hold one exclusive lock while reconciling the Better Stack PostgreSQL target",
        "Hold one exclusive lock while reconciling the Better Stack PostgreSQL target",
    )[0]
    candidates = section["block"] + section["always"]
    helper = copy.deepcopy(next(
        task for task in candidates if task.get("name") == helper_name
    ))
    command = helper["ansible.builtin.command"]
    script = command["argv"][2]
    rename = "os.rename(path, quarantine);"
    replacement = (
        "predecessor = path + '.validated-predecessor'; "
        "os.rename(path, predecessor); "
        "os.mkdir(path); "
        "json.dump({'owner_token': 'post-validation-successor', "
        "'lease_expires_epoch': 4102444800}, "
        "open(owner_path, 'w', encoding='utf-8')); "
        + rename
    )
    if script.count(rename) != 1:
        raise AssertionError(
            f"{helper_name} must expose exactly one ownership-checked rename"
        )
    command["argv"][2] = script.replace(rename, replacement)

    def adapt(value: Any) -> Any:
        if isinstance(value, str):
            return value.replace(
                "/tmp/gam-better-stack-target-reconciliation.lock",
                str(lock_path),
            )
        if isinstance(value, list):
            return [adapt(item) for item in value]
        if isinstance(value, dict):
            return {key: adapt(item) for key, item in value.items()}
        return value

    return adapt(helper)


def lock_helper_with_third_contender_in_restore_gap(
    lock_path: Path,
    *,
    helper_name: str,
) -> dict[str, Any]:
    helper = lock_helper_with_post_validation_successor(
        lock_path,
        helper_name=helper_name,
    )
    command = helper["ansible.builtin.command"]
    script = command["argv"][2]
    quarantine_rename = "os.rename(path, quarantine);"
    third_contender = (
        quarantine_rename
        + " os.mkdir(path); "
        "json.dump({'owner_token': 'gap-third-contender', "
        "'lease_expires_epoch': 4102444800}, "
        "open(owner_path, 'w', encoding='utf-8'));"
    )
    if script.count(quarantine_rename) != 1:
        raise AssertionError(
            f"{helper_name} must expose exactly one quarantine rename"
        )
    command["argv"][2] = script.replace(quarantine_rename, third_contender)
    return helper


def owner_token_creation_tasks_with_lock(lock_path: Path) -> list[dict[str, Any]]:
    section = task_slice(
        "Hold one exclusive lock while reconciling the Better Stack PostgreSQL target",
        "Hold one exclusive lock while reconciling the Better Stack PostgreSQL target",
    )[0]
    block = section["block"]
    start = next(
        index for index, task in enumerate(block)
        if task.get("name") == "Inspect the exclusive reconciliation lock clock"
    )
    end = next(
        index for index, task in enumerate(block)
        if task.get("name") == "Record the reconciliation lock owner and lease"
    )
    selected = copy.deepcopy(block[start:end + 1])
    selected[0] = {
        "name": "Use the shared clock observation for this concurrent invocation",
        "ansible.builtin.set_fact": {
            "better_stack_lock_clock": {"stdout": "{{ scenario_lock_clock }}"},
        },
    }

    def adapt(value: Any) -> Any:
        if isinstance(value, str):
            return value.replace(
                "/tmp/gam-better-stack-target-reconciliation.lock",
                str(lock_path),
            )
        if isinstance(value, list):
            return [adapt(item) for item in value]
        if isinstance(value, dict):
            return {key: adapt(item) for key, item in value.items()}
        return value

    return adapt(selected)


def target_reconciliation_tasks_with_lock(
    lock_path: Path,
) -> list[dict[str, Any]]:
    tasks = task_slice(
        "Reconcile Better Stack metric targets before chart mutation",
        "Reconcile Better Stack metric targets before chart mutation",
    ) + task_slice(
        "Read existing Better Stack collector metric targets",
        "Verify Better Stack service targets are provider-side resources",
    )

    def adapt(value: Any) -> Any:
        if isinstance(value, str):
            return value.replace(
                "/tmp/gam-better-stack-target-reconciliation.lock",
                str(lock_path),
            )
        if isinstance(value, list):
            return [adapt(item) for item in value]
        if isinstance(value, dict):
            adapted = {key: adapt(item) for key, item in value.items()}
            if "Acquire the exclusive reconciliation lock" in str(
                adapted.get("name", "")
            ):
                adapted["retries"] = 1
                adapted["delay"] = 0
            return adapted
        return value

    return adapt(tasks)


def provider_reconciliation_tasks() -> list[dict[str, Any]]:
    before_host_install = task_slice(
        "Discover Better Stack collectors by stable provider name",
        "Verify Better Stack production chart provider state",
    )
    after_host_install = task_slice(
        "Configure the Better Stack collector source through the official API",
        "Verify Better Stack availability and TLS monitor provider state",
    )
    return before_host_install + after_host_install


def collector_startup_order_tasks() -> list[dict[str, Any]]:
    selected_names = {
        "Download the official Better Stack collector Docker Compose installer",
        "Inspect the existing Better Stack collector Docker Compose deployment",
        "Run the official Better Stack collector Docker Compose deployment",
        "Verify the Better Stack collector Docker Compose deployment",
        "Configure the Better Stack collector source through the official API",
        "Read back the Better Stack collector source configuration",
        "Verify the Better Stack collector remains metrics-only",
    }
    selected = [
        copy.deepcopy(task)
        for task in production_tasks()
        if task.get("name") in selected_names
    ]
    replacements = {
        "Download the official Better Stack collector Docker Compose installer": {
            "ansible.builtin.debug": {"msg": "installer boundary prepared"},
        },
        "Inspect the existing Better Stack collector Docker Compose deployment": {
            "ansible.builtin.set_fact": {
                "better_stack_collector_preinstall_state": {
                    "results": [{"stdout": ""}],
                },
            },
        },
        "Run the official Better Stack collector Docker Compose deployment": {
            "ansible.builtin.uri": {
                "url": "{{ better_stack_telemetry_api_url }}/test/installer",
                "method": "POST",
                "body_format": "json",
                "body": {"collector_secret": "{{ better_stack_collector_secret }}"},
                "status_code": [204],
            },
            "no_log": True,
        },
        "Verify the Better Stack collector Docker Compose deployment": {
            "ansible.builtin.debug": {"msg": "installer boundary observed"},
        },
    }
    for index, task in enumerate(selected):
        replacement = replacements.get(task["name"])
        if replacement is not None:
            selected[index] = {"name": task["name"], **replacement}
    return selected


def scenario_environment(
    origin: str,
    *,
    collector_secret: str = "",
    overrides: dict[str, str] | None = None,
) -> dict[str, str]:
    environment = os.environ.copy()
    environment.update({
        "ANSIBLE_FORCE_COLOR": "0",
        "ANSIBLE_NOCOWS": "1",
        "BETTER_STACK_API_TOKEN": "scenario-token",
        "BETTER_STACK_API_URL": f"{origin}/api/v2",
        "BETTER_STACK_TELEMETRY_API_URL": origin,
        "BETTER_STACK_COLLECTOR_SECRET": collector_secret,
        "BETTER_STACK_PROXY_TARGET_HOST": "proxy.internal",
        "BETTER_STACK_PROXY_TARGET_SERVICE": "proxy",
        "BETTER_STACK_PROXY_TARGET_ENDPOINT": PROXY_ENDPOINT,
        "BETTER_STACK_BACKEND_TARGET_HOST": "backend.internal",
        "BETTER_STACK_BACKEND_TARGET_SERVICE": "backend",
        "BETTER_STACK_BACKEND_TARGET_ENDPOINT": BACKEND_ENDPOINT,
        "BETTER_STACK_POSTGRESQL_TARGET_HOST": "postgres.internal",
        "BETTER_STACK_POSTGRESQL_TARGET_PORT": "5432",
        "BETTER_STACK_POSTGRESQL_TARGET_USERNAME": POSTGRES_USERNAME,
        "BETTER_STACK_POSTGRESQL_TARGET_PASSWORD": POSTGRES_PASSWORD,
        "GAM_PUBLIC_ORIGIN": "https://gam.example.org",
        "NO_PROXY": "127.0.0.1,localhost",
        "no_proxy": "127.0.0.1,localhost",
    })
    environment.update(overrides or {})
    return environment


def run_tasks(
    tasks: list[dict[str, Any]],
    origin: str,
    *,
    collector_secret: str = "",
    variables: dict[str, Any] | None = None,
    environment_overrides: dict[str, str] | None = None,
    audit_no_log: bool = False,
) -> subprocess.CompletedProcess[str]:
    executable = shutil.which("ansible-playbook")
    sibling_executable = Path(sys.executable).with_name("ansible-playbook")
    if executable is None and sibling_executable.is_file():
        executable = str(sibling_executable)
    if executable is None:
        raise AssertionError(
            "ansible-playbook is required; install operations/ansible/requirements-test.txt"
        )
    playbook = [{
        "name": "Execute production Better Stack provider boundary",
        "hosts": "localhost",
        "connection": "local",
        "gather_facts": False,
        "vars_files": [GROUP_VARS.as_posix()],
        "vars": {
            "ansible_python_interpreter": sys.executable,
            **(variables or {}),
        },
        "tasks": tasks,
    }]
    with tempfile.TemporaryDirectory(prefix="gam-better-stack-") as temporary:
        temporary_path = Path(temporary)
        playbook_path = temporary_path / "scenario.yml"
        playbook_path.write_text(yaml.safe_dump(playbook, sort_keys=False), encoding="utf-8")
        environment = scenario_environment(
            origin,
            collector_secret=collector_secret,
            overrides=environment_overrides,
        )
        if audit_no_log:
            callback_directory = temporary_path / "callback_plugins"
            callback_directory.mkdir()
            (callback_directory / "gam_no_log_audit.py").write_text(
                NO_LOG_AUDIT_CALLBACK,
                encoding="utf-8",
            )
            environment.update({
                "ANSIBLE_CALLBACK_PLUGINS": str(callback_directory),
                "ANSIBLE_CALLBACKS_ENABLED": "gam_no_log_audit",
            })
        return subprocess.run(
            [executable, "--inventory", "localhost,", str(playbook_path)],
            cwd=ANSIBLE_ROOT,
            env=environment,
            text=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            timeout=180,
            check=False,
        )


class BetterStackProviderScenarioTest(unittest.TestCase):
    def setUp(self) -> None:
        self.state = ProviderState()
        self.server = FakeBetterStackServer(self.state)
        self.thread = threading.Thread(target=self.server.serve_forever, daemon=True)
        self.thread.start()

    def tearDown(self) -> None:
        self.server.shutdown()
        self.server.server_close()
        self.thread.join(timeout=5)

    @staticmethod
    def chart_identity_variables() -> dict[str, str]:
        return {
            "better_stack_dashboard_id": "100",
            "better_stack_proxy_chart_id": "201",
            "better_stack_backend_chart_id": "202",
            "better_stack_postgresql_chart_id": "203",
            "better_stack_filesystem_chart_id": "204",
        }

    def replace_state(self, state: ProviderState) -> None:
        self.state = state
        self.server.state = state

    def provider_request(
        self,
        method: str,
        path: str,
        body: dict[str, Any],
    ) -> tuple[int, dict[str, Any]]:
        connection = HTTPConnection("127.0.0.1", self.server.server_port, timeout=5)
        connection.request(
            method,
            path,
            body=json.dumps(body),
            headers={"Content-Type": "application/json"},
        )
        response = connection.getresponse()
        payload = json.loads(response.read().decode("utf-8") or "{}")
        connection.close()
        return response.status, payload

    def test_proxy_target_rejects_caddy_admin_and_accepts_private_metrics_listener(self) -> None:
        stale_admin_target = {
            "kind": "prometheus",
            "host": "proxy.internal",
            "service": "proxy",
            "endpoint": CADDY_ADMIN_ENDPOINT,
        }

        stale_status, stale_payload = self.provider_request(
            "POST",
            "/api/v1/collectors/collector-1/targets",
            stale_admin_target,
        )

        self.assertEqual(422, stale_status, stale_payload)
        self.assertEqual([], self.state.targets)

        supported_status, supported_payload = self.provider_request(
            "POST",
            "/api/v1/collectors/collector-1/targets",
            {**stale_admin_target, "endpoint": PROXY_ENDPOINT},
        )

        self.assertEqual(201, supported_status, supported_payload)
        self.assertEqual(PROXY_ENDPOINT, self.state.targets[0]["attributes"]["endpoint"])

    def test_two_pass_commissioning_stops_for_custody_then_converges_from_provider_pages(self) -> None:
        commissioning = task_slice(
            "Discover Better Stack collectors by stable provider name",
            "Verify one Better Stack production metrics collector was selected",
        )
        first = run_tasks(commissioning, self.server.origin)

        self.assertNotEqual(0, first.returncode, first.stdout)
        self.assertIn("approved external secret custody", first.stdout)
        self.assertEqual(
            ["GET", "POST"],
            [method for method, _path, _body in self.state.requests],
        )
        discovery = urlparse(self.state.requests[0][1])
        self.assertEqual("/api/v1/collectors", discovery.path)
        self.assertEqual([COLLECTOR_NAME], parse_qs(discovery.query)["name"])
        self.assertEqual("/api/v1/collectors", self.state.requests[1][1])
        creation = self.state.requests[-1][2]
        self.assertEqual(self.state.metrics_only_components(), creation["configuration"]["components"])

        self.state.seed_converged_resources()
        self.state.clear_requests()
        replay = run_tasks(
            provider_reconciliation_tasks(),
            self.server.origin,
            collector_secret="generated-provider-secret",
        )

        self.assertEqual(0, replay.returncode, replay.stdout)
        self.assertRegex(replay.stdout, r"changed=0\s+unreachable=0\s+failed=0")
        requests = [(method, path) for method, path, _body in self.state.requests]
        self.assertNotIn(("POST", "/api/v1/collectors"), requests)
        self.assertGreaterEqual(sum("/charts?page=2" in path for _method, path in requests), 3)
        self.assertGreaterEqual(sum("/targets?page=2" in path for _method, path in requests), 2)
        self.assertGreaterEqual(sum("/api/v2/alerts?page=2" in path for _method, path in requests), 2)
        for alert_id, *_unused in ALERTS:
            self.assertIn(("GET", f"/api/v2/alerts/{alert_id}"), requests)
        self.assertFalse(any(method in {"POST", "PATCH"} for method, _path in requests))

    def test_wrong_restored_secret_is_rejected_without_disclosing_either_secret(self) -> None:
        self.state.seed_converged_resources()
        commissioning = task_slice(
            "Discover Better Stack collectors by stable provider name",
            "Verify one Better Stack production metrics collector was selected",
        )

        result = run_tasks(
            commissioning,
            self.server.origin,
            collector_secret="wrong-restored-secret",
        )

        self.assertNotIn("wrong-restored-secret", result.stdout)
        self.assertNotIn("generated-provider-secret", result.stdout)
        self.assertNotEqual(0, result.returncode, result.stdout)

    def test_secret_bearing_collector_selection_is_no_log_at_the_ansible_runtime(self) -> None:
        self.state.seed_converged_resources()
        tasks = task_slice(
            "Read back Better Stack collectors after creation",
            "Select the Better Stack production metrics collector",
        )

        result = run_tasks(
            tasks,
            self.server.origin,
            collector_secret="generated-provider-secret",
            audit_no_log=True,
        )

        self.assertEqual(0, result.returncode, result.stdout)
        self.assertNotIn("generated-provider-secret", result.stdout)
        audit_events = [
            json.loads(line.removeprefix("GAM_NO_LOG_AUDIT "))
            for line in result.stdout.splitlines()
            if line.startswith("GAM_NO_LOG_AUDIT ")
        ]
        selection = next(
            event for event in audit_events
            if event["name"].endswith("Select the Better Stack production metrics collector")
        )
        self.assertTrue(selection["no_log"], selection)

    def test_filtered_collector_discovery_consumes_later_pages_and_rejects_duplicates(self) -> None:
        tasks = task_slice(
            "Discover Better Stack collectors by stable provider name",
            "Verify one Better Stack production metrics collector was selected",
        )
        path = "/api/v1/collectors"
        for duplicate in (False, True):
            with self.subTest(duplicate=duplicate):
                state = ProviderState()
                state.seed_converged_resources()
                state.paginated_filtered_paths.add(path)
                state.filtered_page_one[path] = (
                    [resource("collector-duplicate", copy.deepcopy(state.collectors[0]["attributes"]))]
                    if duplicate else []
                )
                self.replace_state(state)

                result = run_tasks(
                    tasks,
                    self.server.origin,
                    collector_secret="generated-provider-secret",
                )

                if duplicate:
                    self.assertNotEqual(0, result.returncode, result.stdout)
                else:
                    self.assertEqual(0, result.returncode, result.stdout)
                    self.assertFalse(any(method == "POST" for method, _path, _body in state.requests))
                self.assertTrue(any("page=2" in request_path for _method, request_path, _body in state.requests))

    def test_filtered_dashboard_discovery_consumes_later_pages_and_rejects_duplicates(self) -> None:
        tasks = task_slice(
            "Discover Better Stack dashboards by stable provider name",
            "Verify the Better Stack production dashboard provider state",
        )
        path = "/api/v2/dashboards"
        for duplicate in (False, True):
            with self.subTest(duplicate=duplicate):
                state = ProviderState()
                state.seed_converged_resources()
                state.paginated_filtered_paths.add(path)
                state.filtered_page_one[path] = (
                    [resource("101", state.dashboard_attributes())]
                    if duplicate else []
                )
                self.replace_state(state)

                result = run_tasks(tasks, self.server.origin, collector_secret="custodied")

                if duplicate:
                    self.assertNotEqual(0, result.returncode, result.stdout)
                else:
                    self.assertEqual(0, result.returncode, result.stdout)
                    self.assertFalse(any(method == "POST" for method, _path, _body in state.requests))
                self.assertTrue(any("page=2" in request_path for _method, request_path, _body in state.requests))

    def test_filtered_monitor_discovery_consumes_later_pages_and_rejects_duplicates(self) -> None:
        tasks = task_slice(
            "Read existing Better Stack monitors before reconciliation",
            "Verify Better Stack availability and TLS monitor provider state",
        )
        path = "/api/v2/monitors"
        for duplicate in (False, True):
            with self.subTest(duplicate=duplicate):
                state = ProviderState()
                state.seed_converged_resources()
                state.paginated_filtered_paths.add(path)
                state.filtered_page_one[path] = (
                    [
                        resource("591", state.availability_monitor_attributes()),
                        resource("592", state.tls_monitor_attributes()),
                    ]
                    if duplicate else []
                )
                self.replace_state(state)

                result = run_tasks(tasks, self.server.origin, collector_secret="custodied")

                if duplicate:
                    self.assertNotEqual(0, result.returncode, result.stdout)
                else:
                    self.assertEqual(0, result.returncode, result.stdout)
                    self.assertFalse(any(method == "POST" for method, _path, _body in state.requests))
                self.assertTrue(any("page=2" in request_path for _method, request_path, _body in state.requests))

    def test_dashboard_is_bound_to_the_selected_collector_source(self) -> None:
        self.state.seed_converged_resources()
        self.state.dashboards[0]["attributes"]["variables"] = []
        tasks = task_slice(
            "Discover Better Stack dashboards by stable provider name",
            "Verify the Better Stack production dashboard provider state",
        )

        result = run_tasks(
            tasks,
            self.server.origin,
            collector_secret="generated-provider-secret",
            variables={"better_stack_dashboard_id": "100"},
        )

        self.assertEqual(0, result.returncode, result.stdout)
        self.assertEqual(
            self.state.dashboard_attributes()["variables"],
            self.state.dashboards[0]["attributes"]["variables"],
        )

    def test_dashboard_binding_uses_the_collectors_distinct_source_id(self) -> None:
        self.state.seed_converged_resources()
        self.state.dashboards = []
        tasks = task_slice(
            "Discover Better Stack dashboards by stable provider name",
            "Create the Better Stack production dashboard",
        )

        result = run_tasks(
            tasks,
            self.server.origin,
            collector_secret="generated-provider-secret",
        )

        self.assertEqual(0, result.returncode, result.stdout)
        self.assertNotEqual(
            self.state.collectors[0]["id"],
            str(self.state.collectors[0]["attributes"]["source_id"]),
        )
        self.assertEqual(
            [str(COLLECTOR_SOURCE_ID)],
            self.state.dashboards[0]["attributes"]["variables"][0]["values"],
        )

    def test_dashboard_variables_are_read_from_detail_before_reconciliation(self) -> None:
        self.state.seed_converged_resources()
        tasks = task_slice(
            "Read back Better Stack dashboards after creation",
            "Verify the Better Stack production dashboard provider state",
        )

        result = run_tasks(
            tasks,
            self.server.origin,
            collector_secret="generated-provider-secret",
            variables={"better_stack_dashboard_source_id": str(COLLECTOR_SOURCE_ID)},
        )

        self.assertEqual(0, result.returncode, result.stdout)
        requests = [(method, urlparse(path).path) for method, path, _body in self.state.requests]
        self.assertIn(("GET", "/api/v2/dashboards/100"), requests)
        self.assertNotIn(("PATCH", "/api/v2/dashboards/100"), requests)

    def test_charts_use_provider_valid_unified_metrics_queries(self) -> None:
        self.state.seed_converged_resources()
        tasks = task_slice(
            "Discover Better Stack charts on the production dashboard",
            "Verify Better Stack production chart provider state",
        )

        result = run_tasks(
            tasks,
            self.server.origin,
            collector_secret="generated-provider-secret",
            variables={"better_stack_dashboard_id": "100"},
        )

        self.assertEqual(0, result.returncode, result.stdout)
        self.assertFalse(any(
            method in {"POST", "PATCH"}
            for method, _path, _body in self.state.requests
        ))

    def test_service_health_charts_bind_to_configured_provider_target_identities(self) -> None:
        cases = (
            (
                "GAM proxy health",
                "edge-router-production",
                "BETTER_STACK_PROXY_TARGET_SERVICE",
                "service",
                PROXY_ENDPOINT,
                "label('_service') = 'edge-router-production'",
            ),
            (
                "GAM backend health",
                "members-api-production",
                "BETTER_STACK_BACKEND_TARGET_SERVICE",
                "service",
                BACKEND_ENDPOINT,
                "label('_service') = 'members-api-production'",
            ),
            (
                "GAM PostgreSQL health",
                "database-primary.internal",
                "BETTER_STACK_POSTGRESQL_TARGET_HOST",
                "host",
                None,
                "label('_host') = 'database-primary.internal'",
            ),
        )
        tasks = task_slice(
            "Discover Better Stack charts on the production dashboard",
            "Create declared Better Stack proxy, backend, PostgreSQL, and filesystem charts",
        )

        for chart_name, identity, environment_name, target_field, endpoint, expected in cases:
            with self.subTest(chart=chart_name):
                state = ProviderState()
                state.seed_converged_resources()
                state.charts = []
                state.enforced_chart_bindings = {chart_name}
                target = next(
                    item for item in state.targets
                    if (
                        item["attributes"].get("endpoint") == endpoint
                        if endpoint is not None
                        else item["attributes"].get("kind") == "postgres"
                    )
                )
                target["attributes"][target_field] = identity
                self.replace_state(state)

                result = run_tasks(
                    tasks,
                    self.server.origin,
                    collector_secret="generated-provider-secret",
                    variables={"better_stack_dashboard_id": "100"},
                    environment_overrides={environment_name: identity},
                )

                self.assertEqual(0, result.returncode, result.stdout)
                chart = next(
                    item for item in state.charts
                    if item["attributes"]["name"] == chart_name
                )
                sql = chart["attributes"]["queries"][0]["sql_query"]
                self.assertIn(expected, sql)
                self.assertNotIn("label('service')", sql)

    def test_duplicate_charts_fail_before_any_provider_mutation(self) -> None:
        self.state.seed_converged_resources()
        path = "/api/v2/dashboards/100/charts"
        duplicate = copy.deepcopy(self.state.charts[0])
        duplicate["id"] = "299"
        duplicate["attributes"]["chart_type"] = "bar_chart"
        self.state.filtered_page_one[path] = [duplicate]
        tasks = task_slice(
            "Discover Better Stack charts on the production dashboard",
            "Verify Better Stack production chart provider state",
        )

        result = run_tasks(
            tasks,
            self.server.origin,
            collector_secret="generated-provider-secret",
            variables={"better_stack_dashboard_id": "100"},
        )

        self.assertNotEqual(0, result.returncode, result.stdout)
        self.assertFalse(any(
            method in {"POST", "PATCH"}
            for method, _path, _body in self.state.requests
        ))

    def test_duplicate_metric_targets_fail_before_any_provider_mutation(self) -> None:
        self.state.seed_converged_resources()
        path = "/api/v1/collectors/collector-1/targets"
        duplicate = copy.deepcopy(self.state.targets[0])
        duplicate["id"] = "499"
        duplicate["attributes"]["endpoint"] = "http://old.invalid/metrics"
        self.state.filtered_page_one[path] = [duplicate]
        tasks = task_slice(
            "Read existing Better Stack collector metric targets",
            "Verify Better Stack service targets are provider-side resources",
        )

        result = run_tasks(
            tasks,
            self.server.origin,
            collector_secret="generated-provider-secret",
            variables={"better_stack_collector_id": "collector-1"},
        )

        self.assertNotEqual(0, result.returncode, result.stdout)
        self.assertFalse(any(
            method in {"POST", "PATCH"}
            for method, _path, _body in self.state.requests
        ))

    def test_fake_provider_accepts_documented_schemas_and_rejects_unknown_fields(self) -> None:
        collector_create = {
            "name": COLLECTOR_NAME,
            "platform": "docker",
            "configuration": {"components": self.state.metrics_only_components()},
        }
        dashboard = self.state.dashboard_attributes()
        chart = chart_resource(*CHARTS[0])["attributes"]
        target_create = {
            "kind": "prometheus",
            "host": "proxy.internal",
            "service": "proxy",
            "endpoint": PROXY_ENDPOINT,
        }
        postgres_target_create = {
            "kind": "postgres",
            "host": "postgres.internal",
            "port": 5432,
            "username": POSTGRES_USERNAME,
            "password": POSTGRES_PASSWORD,
            "ssl_mode": "require",
        }
        alert = alert_resource(*ALERTS[0])["attributes"]
        alert_payload = {
            key: value for key, value in alert.items()
            if key not in {"dashboard_id", "chart_id"}
        }
        monitor = self.state.availability_monitor_attributes()
        create_cases = (
            ("/api/v1/collectors", collector_create),
            ("/api/v2/dashboards", dashboard),
            ("/api/v2/dashboards/100/charts", chart),
            ("/api/v1/collectors/collector-1/targets", target_create),
            ("/api/v1/collectors/collector-1/targets", postgres_target_create),
            ("/api/v2/dashboards/100/charts/201/alerts", alert_payload),
            ("/api/v2/monitors", monitor),
        )
        for path, valid in create_cases:
            with self.subTest(method="POST", path=path):
                state = ProviderState()
                if path == "/api/v2/dashboards/100/charts":
                    state.seed_converged_resources()
                    state.charts = []
                self.replace_state(state)
                status, payload = self.provider_request("POST", path, valid)
                self.assertEqual(201, status)
                if valid.get("kind") == "postgres":
                    attributes = payload["data"]["attributes"]
                    self.assertEqual(POSTGRES_USERNAME, attributes.get("username"))
                    self.assertNotIn("password", attributes)
                invalid = {**valid, "unsupported_provider_field": True}
                rejected, _payload = self.provider_request("POST", path, invalid)
                self.assertEqual(422, rejected)

        patch_cases = (
            ("/api/v1/collectors/collector-1", {"configuration": collector_create["configuration"]}),
            ("/api/v2/dashboards/100", dashboard),
            ("/api/v2/dashboards/100/charts/201", chart),
            ("/api/v1/collectors/collector-1/targets/401", {key: value for key, value in target_create.items() if key != "kind"}),
            ("/api/v1/collectors/collector-1/targets/403", {key: value for key, value in postgres_target_create.items() if key != "kind"}),
            ("/api/v2/alerts/301", alert_payload),
            ("/api/v2/monitors/501", monitor),
        )
        for path, valid in patch_cases:
            with self.subTest(method="PATCH", path=path):
                state = ProviderState()
                state.seed_converged_resources()
                self.replace_state(state)
                status, _payload = self.provider_request("PATCH", path, valid)
                self.assertEqual(200, status)
                invalid = {**valid, "unsupported_provider_field": True}
                rejected, _payload = self.provider_request("PATCH", path, invalid)
                self.assertEqual(422, rejected)

        state = ProviderState()
        state.seed_converged_resources()
        self.replace_state(state)
        nested_alert_status, _payload = self.provider_request(
            "PATCH",
            "/api/v2/dashboards/100/charts/201/alerts/301",
            alert_payload,
        )
        self.assertEqual(404, nested_alert_status)

    def test_absent_provider_state_creates_and_persists_every_managed_resource(self) -> None:
        commissioning = task_slice(
            "Discover Better Stack collectors by stable provider name",
            "Verify one Better Stack production metrics collector was selected",
        )
        first = run_tasks(commissioning, self.server.origin)
        self.assertNotEqual(0, first.returncode, first.stdout)
        self.state.clear_requests()

        commissioned = run_tasks(
            provider_reconciliation_tasks(),
            self.server.origin,
            collector_secret="generated-provider-secret",
        )

        self.assertEqual(0, commissioned.returncode, commissioned.stdout)
        self.assertEqual((1, 1, 4, 3, 5, 2), (
            len(self.state.collectors),
            len(self.state.dashboards),
            len(self.state.charts),
            len(self.state.targets),
            len(self.state.alerts),
            len(self.state.monitors),
        ))
        self.assertTrue(all(
            target["attributes"].get("kind") != "prometheus"
            or re.match(r"^https?://[^/]+/", target["attributes"]["endpoint"])
            for target in self.state.targets
        ))
        postgres_target = next(
            target for target in self.state.targets
            if target["attributes"].get("kind") == "postgres"
        )
        self.assertEqual(POSTGRES_USERNAME, postgres_target["attributes"].get("username"))
        self.assertNotIn("password", postgres_target["attributes"])
        postgresql_target_requests = [
            body for method, path, body in self.state.requests
            if method == "POST"
            and path.endswith("/targets")
            and isinstance(body, dict)
            and body.get("kind") == "postgres"
        ]
        self.assertEqual(1, len(postgresql_target_requests))
        self.assertEqual(POSTGRES_PASSWORD, postgresql_target_requests[0].get("password"))
        self.assertNotIn(POSTGRES_PASSWORD, commissioned.stdout)

        self.state.clear_requests()
        replay = run_tasks(
            provider_reconciliation_tasks(),
            self.server.origin,
            collector_secret="generated-provider-secret",
        )
        self.assertEqual(0, replay.returncode, replay.stdout)
        self.assertRegex(replay.stdout, r"changed=0\s+unreachable=0\s+failed=0")
        self.assertFalse(any(method in {"POST", "PATCH"} for method, _path, _body in self.state.requests))

    def test_postgresql_password_rotates_with_the_same_username_without_duplicates(self) -> None:
        previous_password = "previous-postgresql-metrics-secret"
        target_tasks = task_slice(
            "Read existing Better Stack collector metric targets",
            "Verify Better Stack service targets are provider-side resources",
        )
        credential_state = tempfile.TemporaryDirectory(
            prefix="gam-postgresql-target-credentials-"
        )
        self.addCleanup(credential_state.cleanup)
        variables = {
            "better_stack_collector_id": "collector-1",
            "better_stack_postgresql_target_credentials_fingerprint_file": str(
                Path(credential_state.name) / "fingerprint"
            ),
        }
        self.state.seed_converged_resources()
        self.state.targets = []

        provisioned = run_tasks(
            target_tasks,
            self.server.origin,
            variables=variables,
            environment_overrides={
                "BETTER_STACK_POSTGRESQL_TARGET_USERNAME": POSTGRES_USERNAME,
                "BETTER_STACK_POSTGRESQL_TARGET_PASSWORD": previous_password,
            },
        )

        self.assertEqual(0, provisioned.returncode, provisioned.stdout)
        self.assertNotIn(previous_password, provisioned.stdout)
        previous_postgres_targets = [
            target for target in self.state.targets
            if target["attributes"].get("kind") == "postgres"
        ]
        self.assertEqual(1, len(previous_postgres_targets))
        self.assertEqual(
            POSTGRES_USERNAME,
            previous_postgres_targets[0]["attributes"].get("username"),
        )
        self.assertNotIn("password", previous_postgres_targets[0]["attributes"])

        self.state.clear_requests()
        rotated = run_tasks(
            target_tasks,
            self.server.origin,
            variables=variables,
            environment_overrides={
                "BETTER_STACK_POSTGRESQL_TARGET_USERNAME": POSTGRES_USERNAME,
                "BETTER_STACK_POSTGRESQL_TARGET_PASSWORD": POSTGRES_PASSWORD,
            },
        )

        self.assertEqual(0, rotated.returncode, rotated.stdout)
        self.assertNotIn(previous_password, rotated.stdout)
        self.assertNotIn(POSTGRES_PASSWORD, rotated.stdout)
        self.assertEqual(3, len(self.state.targets))
        rotated_postgres_targets = [
            target for target in self.state.targets
            if target["attributes"].get("kind") == "postgres"
        ]
        self.assertEqual(1, len(rotated_postgres_targets))
        self.assertEqual(
            POSTGRES_USERNAME,
            rotated_postgres_targets[0]["attributes"].get("username"),
        )
        self.assertNotIn("password", rotated_postgres_targets[0]["attributes"])
        rotated_postgres_patches = [
            body for method, path, body in self.state.requests
            if method == "PATCH"
            and path == f"/api/v1/collectors/collector-1/targets/{previous_postgres_targets[0]['id']}"
            and isinstance(body, dict)
        ]
        self.assertEqual(1, len(rotated_postgres_patches))
        self.assertEqual(
            {
                "host": "postgres.internal",
                "port": 5432,
                "username": POSTGRES_USERNAME,
                "password": POSTGRES_PASSWORD,
                "ssl_mode": "require",
            },
            rotated_postgres_patches[0],
        )
        self.assertEqual(
            0,
            sum(
                method in {"POST", "DELETE"} and "/targets" in path
                for method, path, _body in self.state.requests
            ),
        )
        self.assertEqual(previous_postgres_targets[0]["id"], rotated_postgres_targets[0]["id"])

        self.state.clear_requests()
        replay = run_tasks(
            target_tasks,
            self.server.origin,
            variables=variables,
        )

        self.assertEqual(0, replay.returncode, replay.stdout)
        self.assertRegex(replay.stdout, r"changed=0\s+unreachable=0\s+failed=0")
        self.assertNotIn(POSTGRES_PASSWORD, replay.stdout)
        self.assertFalse(any(
            method in {"POST", "PATCH", "DELETE"}
            for method, _path, _body in self.state.requests
        ))

    def test_stale_credential_state_replaces_an_externally_recreated_postgresql_target(self) -> None:
        externally_managed_password = "externally-replaced-postgresql-secret"
        target_tasks = task_slice(
            "Read existing Better Stack collector metric targets",
            "Verify Better Stack service targets are provider-side resources",
        )
        credential_state = tempfile.TemporaryDirectory(
            prefix="gam-postgresql-target-identity-"
        )
        self.addCleanup(credential_state.cleanup)
        credential_state_file = Path(credential_state.name) / "fingerprint"
        variables = {
            "better_stack_collector_id": "collector-1",
            "better_stack_postgresql_target_credentials_fingerprint_file": str(
                credential_state_file
            ),
        }
        self.state.seed_converged_resources()
        self.state.targets = []

        provisioned = run_tasks(
            target_tasks,
            self.server.origin,
            variables=variables,
        )

        self.assertEqual(0, provisioned.returncode, provisioned.stdout)
        self.assertTrue(credential_state_file.is_file())
        persisted_credential_state = credential_state_file.read_text(encoding="utf-8")
        self.assertNotIn(POSTGRES_PASSWORD, persisted_credential_state)
        self.assertNotIn(POSTGRES_USERNAME, persisted_credential_state)
        provisioned_postgres = next(
            target for target in self.state.targets
            if target["attributes"].get("kind") == "postgres"
        )
        self.assertIn(
            provisioned_postgres["id"],
            persisted_credential_state,
            "protected credential state must bind its digest to the provisioned provider target",
        )
        externally_replaced_target_id = "externally-replaced-postgresql-target"
        self.state.targets = [
            target for target in self.state.targets
            if target["attributes"].get("kind") != "postgres"
        ] + [resource(
            externally_replaced_target_id,
            copy.deepcopy(provisioned_postgres["attributes"]),
        )]
        self.assertEqual(
            POSTGRES_USERNAME,
            self.state.targets[-1]["attributes"].get("username"),
        )
        self.assertNotEqual(provisioned_postgres["id"], self.state.targets[-1]["id"])
        self.assertEqual(
            persisted_credential_state,
            credential_state_file.read_text(encoding="utf-8"),
            "out-of-band provider replacement must leave the matching local credential digest stale",
        )

        self.state.clear_requests()
        reconciled = run_tasks(
            target_tasks,
            self.server.origin,
            variables=variables,
        )

        self.assertEqual(0, reconciled.returncode, reconciled.stdout)
        self.assertNotIn(POSTGRES_PASSWORD, reconciled.stdout)
        self.assertNotIn(externally_managed_password, reconciled.stdout)
        postgres_targets = [
            target for target in self.state.targets
            if target["attributes"].get("kind") == "postgres"
        ]
        self.assertEqual(1, len(postgres_targets))
        self.assertEqual(externally_replaced_target_id, postgres_targets[0]["id"])
        postgres_patches = [
            body for method, path, body in self.state.requests
            if method == "PATCH"
            and path == f"/api/v1/collectors/collector-1/targets/{externally_replaced_target_id}"
            and isinstance(body, dict)
        ]
        self.assertEqual(1, len(postgres_patches))
        self.assertEqual(POSTGRES_PASSWORD, postgres_patches[0].get("password"))
        self.assertFalse(any(
            method in {"POST", "DELETE"} and "/targets" in path
            for method, path, _body in self.state.requests
        ))
        reconciled_credential_state = credential_state_file.read_text(encoding="utf-8")
        self.assertIn(postgres_targets[0]["id"], reconciled_credential_state)
        self.assertNotIn(POSTGRES_USERNAME, reconciled_credential_state)
        self.assertNotIn(POSTGRES_PASSWORD, reconciled_credential_state)

        self.state.clear_requests()
        replay = run_tasks(
            target_tasks,
            self.server.origin,
            variables=variables,
        )

        self.assertEqual(0, replay.returncode, replay.stdout)
        self.assertRegex(replay.stdout, r"changed=0\s+unreachable=0\s+failed=0")
        self.assertNotIn(POSTGRES_PASSWORD, replay.stdout)
        self.assertFalse(any(
            method in {"POST", "PATCH", "DELETE"}
            for method, _path, _body in self.state.requests
        ))

    def test_failed_postgresql_password_patch_preserves_target_and_protected_state(self) -> None:
        previous_password = "previous-postgresql-metrics-secret"
        target_tasks = task_slice(
            "Read existing Better Stack collector metric targets",
            "Verify Better Stack service targets are provider-side resources",
        )
        credential_state = tempfile.TemporaryDirectory(
            prefix="gam-postgresql-target-patch-failure-"
        )
        self.addCleanup(credential_state.cleanup)
        credential_state_file = Path(credential_state.name) / "fingerprint"
        variables = {
            "better_stack_collector_id": "collector-1",
            "better_stack_postgresql_target_credentials_fingerprint_file": str(
                credential_state_file
            ),
        }
        self.state.seed_converged_resources()
        self.state.targets = []

        provisioned = run_tasks(
            target_tasks,
            self.server.origin,
            variables=variables,
            environment_overrides={
                "BETTER_STACK_POSTGRESQL_TARGET_USERNAME": POSTGRES_USERNAME,
                "BETTER_STACK_POSTGRESQL_TARGET_PASSWORD": previous_password,
            },
        )
        self.assertEqual(0, provisioned.returncode, provisioned.stdout)
        original_target = copy.deepcopy(next(
            target for target in self.state.targets
            if target["attributes"].get("kind") == "postgres"
        ))
        original_state = credential_state_file.read_text(encoding="utf-8")

        self.state.clear_requests()
        self.state.reject_postgresql_target_patch = True
        failed_rotation = run_tasks(
            target_tasks,
            self.server.origin,
            variables=variables,
            environment_overrides={
                "BETTER_STACK_POSTGRESQL_TARGET_USERNAME": POSTGRES_USERNAME,
                "BETTER_STACK_POSTGRESQL_TARGET_PASSWORD": POSTGRES_PASSWORD,
            },
        )

        self.assertNotEqual(0, failed_rotation.returncode, failed_rotation.stdout)
        self.assertNotIn(previous_password, failed_rotation.stdout)
        self.assertNotIn(POSTGRES_PASSWORD, failed_rotation.stdout)
        postgres_targets = [
            target for target in self.state.targets
            if target["attributes"].get("kind") == "postgres"
        ]
        self.assertEqual([original_target], postgres_targets)
        self.assertEqual(original_state, credential_state_file.read_text(encoding="utf-8"))
        self.assertEqual(1, sum(
            method == "PATCH"
            and path.endswith(f"/targets/{original_target['id']}")
            and isinstance(body, dict)
            and body.get("password") == POSTGRES_PASSWORD
            for method, path, body in self.state.requests
        ))
        self.assertFalse(any(
            method in {"POST", "DELETE"} and "/targets" in path
            for method, path, _body in self.state.requests
        ))

        self.state.reject_postgresql_target_patch = False
        self.state.clear_requests()
        recovered = run_tasks(
            target_tasks,
            self.server.origin,
            variables=variables,
            environment_overrides={
                "BETTER_STACK_POSTGRESQL_TARGET_USERNAME": POSTGRES_USERNAME,
                "BETTER_STACK_POSTGRESQL_TARGET_PASSWORD": POSTGRES_PASSWORD,
            },
        )
        self.assertEqual(0, recovered.returncode, recovered.stdout)
        self.assertEqual(1, len([
            target for target in self.state.targets
            if target["attributes"].get("kind") == "postgres"
        ]))
        self.assertNotEqual(
            original_state,
            credential_state_file.read_text(encoding="utf-8"),
            "a failed mutation must release reconciliation ownership so a later apply can converge",
        )

        self.state.clear_requests()
        replay = run_tasks(target_tasks, self.server.origin, variables=variables)
        self.assertEqual(0, replay.returncode, replay.stdout)
        self.assertRegex(replay.stdout, r"changed=0\s+unreachable=0\s+failed=0")
        self.assertFalse(any(
            method in {"POST", "PATCH", "DELETE"}
            for method, _path, _body in self.state.requests
        ))

    def test_concurrent_first_run_reconciliation_is_exclusive_and_idempotent(self) -> None:
        target_tasks = task_slice(
            "Reconcile Better Stack metric targets before chart mutation",
            "Reconcile Better Stack metric targets before chart mutation",
        ) + task_slice(
            "Read existing Better Stack collector metric targets",
            "Verify Better Stack service targets are provider-side resources",
        )
        credential_state = tempfile.TemporaryDirectory(
            prefix="gam-postgresql-target-concurrent-first-run-"
        )
        self.addCleanup(credential_state.cleanup)
        credential_state_file = Path(credential_state.name) / "fingerprint"
        variables = {
            "better_stack_collector_id": "collector-1",
            "better_stack_postgresql_target_credentials_fingerprint_file": str(
                credential_state_file
            ),
        }
        self.state.seed_converged_resources()
        self.state.targets = []
        self.state.target_discovery_barrier = threading.Barrier(2)
        self.state.target_discovery_barrier_uses = 2

        with concurrent.futures.ThreadPoolExecutor(max_workers=2) as executor:
            futures = [
                executor.submit(
                    run_tasks,
                    target_tasks,
                    self.server.origin,
                    variables=variables,
                )
                for _ in range(2)
            ]
            concurrent_results = [future.result(timeout=190) for future in futures]

        self.assertTrue(
            all(result.returncode == 0 for result in concurrent_results),
            "both concurrent applies must serialize and converge:\n"
            + "\n".join(result.stdout for result in concurrent_results),
        )
        self.assertTrue(credential_state_file.is_file())
        protected_state = credential_state_file.read_text(encoding="utf-8")
        self.assertNotIn(POSTGRES_USERNAME, protected_state)
        self.assertNotIn(POSTGRES_PASSWORD, protected_state)
        self.assertEqual(1, sum(
            target["attributes"].get("kind") == "prometheus"
            and target["attributes"].get("service") == "proxy"
            for target in self.state.targets
        ))
        self.assertEqual(1, sum(
            target["attributes"].get("kind") == "prometheus"
            and target["attributes"].get("service") == "backend"
            for target in self.state.targets
        ))
        self.assertEqual(1, sum(
            target["attributes"].get("kind") == "postgres"
            for target in self.state.targets
        ))
        self.assertEqual(3, sum(
            method == "POST" and path.endswith("/targets")
            for method, path, _body in self.state.requests
        ))
        self.assertTrue(all(
            POSTGRES_PASSWORD not in result.stdout
            for result in concurrent_results
        ))

        self.state.clear_requests()
        replay = run_tasks(target_tasks, self.server.origin, variables=variables)
        self.assertEqual(0, replay.returncode, replay.stdout)
        self.assertRegex(replay.stdout, r"changed=0\s+unreachable=0\s+failed=0")
        self.assertFalse(any(
            method in {"POST", "PATCH", "DELETE"}
            for method, _path, _body in self.state.requests
        ))

    def test_abrupt_termination_lock_recovers_without_preempting_a_live_owner(self) -> None:
        reconciliation_state = tempfile.TemporaryDirectory(
            prefix="gam-better-stack-abandoned-lock-"
        )
        self.addCleanup(reconciliation_state.cleanup)
        state_root = Path(reconciliation_state.name)
        lock_path = state_root / "target-reconciliation.lock"
        credential_state_file = state_root / "fingerprint"
        variables = {
            "better_stack_collector_id": "collector-1",
            "better_stack_postgresql_target_credentials_fingerprint_file": str(
                credential_state_file
            ),
            "better_stack_target_reconciliation_lock_path": str(lock_path),
        }
        target_tasks = target_reconciliation_tasks_with_lock(lock_path)
        self.state.seed_converged_resources()
        self.state.targets = []

        lock_path.mkdir()
        live_owner = run_tasks(
            target_tasks,
            self.server.origin,
            variables=variables,
        )
        self.assertNotEqual(
            0,
            live_owner.returncode,
            "a fresh lock lease must not be preempted by another apply",
        )
        self.assertTrue(lock_path.is_dir())
        self.assertFalse(any(
            method in {"POST", "PATCH", "DELETE"} and "/targets" in path
            for method, path, _body in self.state.requests
        ))

        os.utime(lock_path, (1, 1))
        self.state.clear_requests()
        recovered = run_tasks(
            target_tasks,
            self.server.origin,
            variables=variables,
        )

        self.assertEqual(0, recovered.returncode, recovered.stdout)
        self.assertFalse(lock_path.exists(), "the recovered apply must clean up its own lock")
        self.assertNotIn(POSTGRES_USERNAME, recovered.stdout)
        self.assertNotIn(POSTGRES_PASSWORD, recovered.stdout)
        self.assertEqual(1, sum(
            target["attributes"].get("kind") == "prometheus"
            and target["attributes"].get("service") == "proxy"
            for target in self.state.targets
        ))
        self.assertEqual(1, sum(
            target["attributes"].get("kind") == "prometheus"
            and target["attributes"].get("service") == "backend"
            for target in self.state.targets
        ))
        self.assertEqual(1, sum(
            target["attributes"].get("kind") == "postgres"
            for target in self.state.targets
        ))

        self.state.clear_requests()
        replay = run_tasks(
            target_tasks,
            self.server.origin,
            variables=variables,
        )
        self.assertEqual(0, replay.returncode, replay.stdout)
        self.assertRegex(replay.stdout, r"changed=0\s+unreachable=0\s+failed=0")
        self.assertFalse(lock_path.exists())
        self.assertFalse(any(
            method in {"POST", "PATCH", "DELETE"}
            for method, _path, _body in self.state.requests
        ))

    def test_concurrent_stale_lock_recovery_preserves_a_successor_owner(self) -> None:
        reconciliation_state = tempfile.TemporaryDirectory(
            prefix="gam-better-stack-concurrent-stale-lock-"
        )
        self.addCleanup(reconciliation_state.cleanup)
        lock_path = Path(reconciliation_state.name) / "target-reconciliation.lock"
        lock_path.mkdir()
        (lock_path / "owner.json").write_text(
            json.dumps({
                "owner_token": "abandoned-owner",
                "lease_expires_epoch": 1,
            }),
            encoding="utf-8",
        )
        self.state.stale_recovery_barrier = threading.Barrier(2)
        tasks = concurrent_stale_recovery_tasks_with_lock(lock_path)

        contender_variables = (
            {"scenario_recovery_delay_seconds": 0, "scenario_lock_hold_seconds": 2},
            {"scenario_recovery_delay_seconds": 1, "scenario_lock_hold_seconds": 4},
        )
        with concurrent.futures.ThreadPoolExecutor(max_workers=2) as executor:
            futures = [
                executor.submit(
                    run_tasks,
                    tasks,
                    self.server.origin,
                    variables=variables,
                )
                for variables in contender_variables
            ]
            results = [future.result(timeout=30) for future in futures]

        self.assertTrue(
            all(result.returncode == 0 for result in results),
            "a stale-lock contender must revalidate owner_token before removal, and "
            "cleanup must preserve a successor's newly acquired lock:\n"
            + "\n".join(result.stdout for result in results),
        )
        self.assertFalse(lock_path.exists(), "each successful owner must clean up only its own lock")

    def test_simultaneous_stale_observers_cannot_delete_a_successor_lock(self) -> None:
        reconciliation_state = tempfile.TemporaryDirectory(
            prefix="gam-better-stack-same-observation-stale-lock-"
        )
        self.addCleanup(reconciliation_state.cleanup)
        lock_path = Path(reconciliation_state.name) / "target-reconciliation.lock"
        lock_path.mkdir()
        (lock_path / "owner.json").write_text(
            json.dumps({
                "owner_token": "same-abandoned-owner",
                "lease_expires_epoch": 1,
            }),
            encoding="utf-8",
        )
        self.state.stale_recovery_barrier = threading.Barrier(2)
        tasks = same_observation_stale_recovery_tasks_with_lock(lock_path)

        with concurrent.futures.ThreadPoolExecutor(max_workers=2) as executor:
            futures = [
                executor.submit(
                    run_tasks,
                    tasks,
                    self.server.origin,
                    variables={"scenario_recovery_delay_seconds": delay},
                )
                for delay in (0, 1)
            ]
            results = [future.result(timeout=30) for future in futures]

        self.assertTrue(
            all(result.returncode == 0 for result in results),
            "once both contenders observe the same stale owner, a delayed contender "
            "must atomically revalidate before deletion and preserve the first "
            "contender's successor lock:\n"
            + "\n".join(result.stdout for result in results),
        )
        self.assertFalse(lock_path.exists(), "each successful owner must clean up only its own lock")

    def test_cleanup_cannot_delete_a_successor_acquired_after_owner_read(self) -> None:
        reconciliation_state = tempfile.TemporaryDirectory(
            prefix="gam-better-stack-cleanup-owner-swap-"
        )
        self.addCleanup(reconciliation_state.cleanup)
        lock_path = Path(reconciliation_state.name) / "target-reconciliation.lock"
        lock_path.mkdir()
        clock = "1700000000000000000"
        (lock_path / "owner.json").write_text(
            json.dumps({
                "owner_token": f"localhost:{clock}:final",
                "lease_expires_epoch": 4102444800,
            }),
            encoding="utf-8",
        )
        self.state.cleanup_replacement_lock_path = lock_path

        cleanup = run_tasks(
            cleanup_compare_then_delete_tasks_with_lock(lock_path),
            self.server.origin,
            variables={
                "better_stack_lock_clock": {"stdout": clock},
                "better_stack_target_lock": {"rc": 0},
            },
        )

        self.assertEqual(0, cleanup.returncode, cleanup.stdout)
        self.assertTrue(
            lock_path.is_dir(),
            "cleanup must compare and remove atomically so a successor acquired "
            "after the owner read cannot be deleted",
        )
        owner = json.loads((lock_path / "owner.json").read_text(encoding="utf-8"))
        self.assertEqual(self.state.cleanup_replacement_owner_token, owner["owner_token"])

    def test_stale_takeover_helper_cannot_rename_a_successor_installed_after_validation(self) -> None:
        reconciliation_state = tempfile.TemporaryDirectory(
            prefix="gam-better-stack-stale-helper-owner-swap-"
        )
        self.addCleanup(reconciliation_state.cleanup)
        lock_path = Path(reconciliation_state.name) / "target-reconciliation.lock"
        lock_path.mkdir()
        predecessor = {
            "owner_token": "validated-stale-predecessor",
            "lease_expires_epoch": 1,
        }
        (lock_path / "owner.json").write_text(
            json.dumps(predecessor),
            encoding="utf-8",
        )
        helper = lock_helper_with_post_validation_successor(
            lock_path,
            helper_name=(
                "Atomically claim and remove only the observed stale reconciliation lock"
            ),
        )

        takeover = run_tasks(
            [helper],
            self.server.origin,
            variables={
                "better_stack_lock_stale": True,
                "better_stack_lock_still_owned_by_observed_stale_owner": True,
                "better_stack_lock_owner_file": {"stat": {"exists": True}},
                "better_stack_lock_owner_content": {
                    "content": base64.b64encode(
                        json.dumps(predecessor).encode("utf-8")
                    ).decode("ascii"),
                },
                "better_stack_lock_nonce": {"stdout": "stale-helper-race"},
            },
        )

        self.assertEqual(0, takeover.returncode, takeover.stdout)
        self.assertTrue(
            lock_path.is_dir(),
            "stale takeover must not rename or delete a successor installed after "
            "the helper internally validates the predecessor owner",
        )
        owner = json.loads((lock_path / "owner.json").read_text(encoding="utf-8"))
        self.assertEqual("post-validation-successor", owner["owner_token"])

    def test_cleanup_helper_cannot_rename_a_successor_installed_after_validation(self) -> None:
        reconciliation_state = tempfile.TemporaryDirectory(
            prefix="gam-better-stack-cleanup-helper-owner-swap-"
        )
        self.addCleanup(reconciliation_state.cleanup)
        lock_path = Path(reconciliation_state.name) / "target-reconciliation.lock"
        lock_path.mkdir()
        clock = "1700000000000000000-unique-cleanup-nonce"
        predecessor_token = f"localhost:{clock}:final"
        (lock_path / "owner.json").write_text(
            json.dumps({
                "owner_token": predecessor_token,
                "lease_expires_epoch": 4102444800,
            }),
            encoding="utf-8",
        )
        helper = lock_helper_with_post_validation_successor(
            lock_path,
            helper_name=(
                "Atomically release only this apply's Better Stack target "
                "reconciliation lock"
            ),
        )

        cleanup = run_tasks(
            [helper],
            self.server.origin,
            variables={
                "better_stack_target_lock": {"rc": 0},
                "better_stack_lock_clock": {"stdout": clock},
            },
        )

        self.assertEqual(0, cleanup.returncode, cleanup.stdout)
        self.assertTrue(
            lock_path.is_dir(),
            "cleanup must not rename or delete a successor installed after the "
            "helper internally validates the predecessor owner",
        )
        owner = json.loads((lock_path / "owner.json").read_text(encoding="utf-8"))
        self.assertEqual("post-validation-successor", owner["owner_token"])

    def test_stale_takeover_preserves_exclusivity_when_a_third_owner_acquires_restore_gap(self) -> None:
        reconciliation_state = tempfile.TemporaryDirectory(
            prefix="gam-better-stack-stale-three-party-lock-"
        )
        self.addCleanup(reconciliation_state.cleanup)
        lock_path = Path(reconciliation_state.name) / "target-reconciliation.lock"
        lock_path.mkdir()
        predecessor = {
            "owner_token": "validated-stale-predecessor",
            "lease_expires_epoch": 1,
        }
        (lock_path / "owner.json").write_text(
            json.dumps(predecessor),
            encoding="utf-8",
        )
        helper = lock_helper_with_third_contender_in_restore_gap(
            lock_path,
            helper_name=(
                "Atomically claim and remove only the observed stale reconciliation lock"
            ),
        )
        takeover = run_tasks(
            [helper],
            self.server.origin,
            variables={
                "better_stack_lock_stale": True,
                "better_stack_lock_still_owned_by_observed_stale_owner": True,
                "better_stack_lock_owner_file": {"stat": {"exists": True}},
                "better_stack_lock_owner_content": {
                    "content": base64.b64encode(
                        json.dumps(predecessor).encode("utf-8")
                    ).decode("ascii"),
                },
                "better_stack_lock_nonce": {"stdout": "stale-three-party-race"},
            },
        )

        canonical_owner = json.loads(
            (lock_path / "owner.json").read_text(encoding="utf-8")
        )["owner_token"]
        displaced_owners = [
            json.loads((candidate / "owner.json").read_text(encoding="utf-8"))[
                "owner_token"
            ]
            for candidate in lock_path.parent.iterdir()
            if candidate.is_dir()
            and candidate != lock_path
            and not candidate.name.endswith(".validated-predecessor")
            and (candidate / "owner.json").is_file()
        ]
        self.assertTrue(
            takeover.returncode == 0
            and canonical_owner == "gap-third-contender"
            and displaced_owners == [],
            "stale takeover must maintain continuous exclusivity when a third "
            "contender acquires the temporary canonical gap; observed "
            f"returncode={takeover.returncode}, canonical_owner={canonical_owner!r}, "
            f"displaced_owners={displaced_owners!r}\n{takeover.stdout}",
        )

    def test_cleanup_preserves_exclusivity_when_a_third_owner_acquires_restore_gap(self) -> None:
        reconciliation_state = tempfile.TemporaryDirectory(
            prefix="gam-better-stack-cleanup-three-party-lock-"
        )
        self.addCleanup(reconciliation_state.cleanup)
        lock_path = Path(reconciliation_state.name) / "target-reconciliation.lock"
        lock_path.mkdir()
        clock = "1700000000000000000-unique-cleanup-nonce"
        predecessor_token = f"localhost:{clock}:final"
        (lock_path / "owner.json").write_text(
            json.dumps({
                "owner_token": predecessor_token,
                "lease_expires_epoch": 4102444800,
            }),
            encoding="utf-8",
        )
        helper = lock_helper_with_third_contender_in_restore_gap(
            lock_path,
            helper_name=(
                "Atomically release only this apply's Better Stack target "
                "reconciliation lock"
            ),
        )
        cleanup = run_tasks(
            [helper],
            self.server.origin,
            variables={
                "better_stack_target_lock": {"rc": 0},
                "better_stack_lock_clock": {"stdout": clock},
            },
        )

        canonical_owner = json.loads(
            (lock_path / "owner.json").read_text(encoding="utf-8")
        )["owner_token"]
        displaced_owners = [
            json.loads((candidate / "owner.json").read_text(encoding="utf-8"))[
                "owner_token"
            ]
            for candidate in lock_path.parent.iterdir()
            if candidate.is_dir()
            and candidate != lock_path
            and not candidate.name.endswith(".validated-predecessor")
            and (candidate / "owner.json").is_file()
        ]
        self.assertTrue(
            cleanup.returncode == 0
            and canonical_owner == "gap-third-contender"
            and displaced_owners == [],
            "cleanup must maintain continuous exclusivity when a third contender "
            "acquires the temporary canonical gap; observed "
            f"returncode={cleanup.returncode}, canonical_owner={canonical_owner!r}, "
            f"displaced_owners={displaced_owners!r}\n{cleanup.stdout}",
        )

    def test_same_clock_concurrent_invocations_receive_unique_owner_tokens(self) -> None:
        reconciliation_state = tempfile.TemporaryDirectory(
            prefix="gam-better-stack-owner-token-uniqueness-"
        )
        self.addCleanup(reconciliation_state.cleanup)
        root = Path(reconciliation_state.name)
        lock_paths = (root / "first.lock", root / "second.lock")
        clock = "1700000000000000000"

        with concurrent.futures.ThreadPoolExecutor(max_workers=2) as executor:
            futures = [
                executor.submit(
                    run_tasks,
                    owner_token_creation_tasks_with_lock(lock_path),
                    self.server.origin,
                    variables={"scenario_lock_clock": clock},
                )
                for lock_path in lock_paths
            ]
            results = [future.result(timeout=30) for future in futures]

        self.assertTrue(
            all(result.returncode == 0 for result in results),
            "the owner-token creation scenarios must both execute:\n"
            + "\n".join(result.stdout for result in results),
        )
        owner_tokens = [
            json.loads((lock_path / "owner.json").read_text(encoding="utf-8"))["owner_token"]
            for lock_path in lock_paths
        ]
        self.assertEqual(
            len(owner_tokens),
            len(set(owner_tokens)),
            "same-host invocations with the same clock observation require "
            "per-invocation entropy in owner_token",
        )

    def test_post_network_readiness_retries_merged_snapshots_until_all_targets_are_up(self) -> None:
        self.state.seed_converged_resources()
        transient = copy.deepcopy(self.state.targets)
        transient[0]["attributes"]["status"] = "down"
        transient[1]["attributes"]["status"] = "up"
        transient[2]["attributes"]["status"] = "pending"
        converged = copy.deepcopy(self.state.targets)
        for target in converged:
            target["attributes"]["status"] = "up"
        self.state.target_readiness_snapshots = [transient, converged]

        readiness = run_tasks(
            post_network_target_readiness_tasks(),
            self.server.origin,
            variables={
                "better_stack_collector_id": "collector-1",
                "better_stack_target_readiness_retries": 3,
                "better_stack_target_readiness_delay_seconds": 0,
            },
        )

        self.assertEqual(0, readiness.returncode, readiness.stdout)
        page_one_reads = [
            path for method, path, _body in self.state.requests
            if method == "GET"
            and "/targets" in path
            and parse_qs(urlparse(path).query).get("page", ["1"])[0] == "1"
        ]
        page_two_reads = [
            path for method, path, _body in self.state.requests
            if method == "GET"
            and "/targets" in path
            and parse_qs(urlparse(path).query).get("page", ["1"])[0] == "2"
        ]
        self.assertEqual(2, len(page_one_reads))
        self.assertEqual(2, len(page_two_reads))

    def test_post_network_readiness_fails_after_bounded_all_page_attempts(self) -> None:
        self.state.seed_converged_resources()
        never_ready = copy.deepcopy(self.state.targets)
        for target in never_ready:
            target["attributes"]["status"] = "down"
        self.state.target_readiness_snapshots = [
            copy.deepcopy(never_ready) for _ in range(3)
        ]

        readiness = run_tasks(
            post_network_target_readiness_tasks(),
            self.server.origin,
            variables={
                "better_stack_collector_id": "collector-1",
                "better_stack_target_readiness_retries": 3,
                "better_stack_target_readiness_delay_seconds": 0,
            },
        )

        self.assertNotEqual(0, readiness.returncode, readiness.stdout)
        page_one_reads = [
            path for method, path, _body in self.state.requests
            if method == "GET"
            and "/targets" in path
            and parse_qs(urlparse(path).query).get("page", ["1"])[0] == "1"
        ]
        page_two_reads = [
            path for method, path, _body in self.state.requests
            if method == "GET"
            and "/targets" in path
            and parse_qs(urlparse(path).query).get("page", ["1"])[0] == "2"
        ]
        self.assertEqual(3, len(page_one_reads))
        self.assertEqual(3, len(page_two_reads))

    def test_post_network_readiness_finds_managed_targets_after_page_one(self) -> None:
        self.state.seed_converged_resources()
        for target in self.state.targets:
            target["attributes"]["status"] = "up"

        readiness = run_tasks(
            post_network_target_readiness_tasks(),
            self.server.origin,
            variables={"better_stack_collector_id": "collector-1"},
        )

        self.assertEqual(0, readiness.returncode, readiness.stdout)
        target_reads = [
            path for method, path, _body in self.state.requests
            if method == "GET" and "/targets" in path
        ]
        self.assertTrue(any("page=2" in path for path in target_reads))
        self.assertNotIn(POSTGRES_PASSWORD, readiness.stdout)

        self.state.clear_requests()
        replay = run_tasks(
            post_network_target_readiness_tasks(),
            self.server.origin,
            variables={"better_stack_collector_id": "collector-1"},
        )
        self.assertEqual(0, replay.returncode, replay.stdout)
        self.assertRegex(replay.stdout, r"changed=0\s+unreachable=0\s+failed=0")
        self.assertTrue(any(
            method == "GET" and "page=2" in path
            for method, path, _body in self.state.requests
        ))

    def test_legacy_malformed_and_missing_target_credential_state_recovers_safely(self) -> None:
        target_tasks = task_slice(
            "Read existing Better Stack collector metric targets",
            "Verify Better Stack service targets are provider-side resources",
        )
        for state_kind in ("legacy scalar", "malformed", "missing target id"):
            with self.subTest(protected_state=state_kind):
                credential_state = tempfile.TemporaryDirectory(
                    prefix="gam-postgresql-target-state-recovery-"
                )
                self.addCleanup(credential_state.cleanup)
                credential_state_file = Path(credential_state.name) / "fingerprint"
                variables = {
                    "better_stack_collector_id": "collector-1",
                    "better_stack_postgresql_target_credentials_fingerprint_file": str(
                        credential_state_file
                    ),
                }
                self.state.seed_converged_resources()
                self.state.targets = []
                provisioned = run_tasks(target_tasks, self.server.origin, variables=variables)
                self.assertEqual(0, provisioned.returncode, provisioned.stdout)
                target_id = next(
                    target["id"] for target in self.state.targets
                    if target["attributes"].get("kind") == "postgres"
                )
                protected = json.loads(credential_state_file.read_text(encoding="utf-8"))
                replacement = {
                    "legacy scalar": protected["credential_fingerprint"],
                    "malformed": "{not-valid-protected-state",
                    "missing target id": json.dumps({
                        "credential_fingerprint": protected["credential_fingerprint"],
                        "postgresql_target_id": "missing-provider-target",
                    }),
                }[state_kind]
                credential_state_file.write_text(replacement, encoding="utf-8")

                self.state.clear_requests()
                recovered = run_tasks(target_tasks, self.server.origin, variables=variables)
                self.assertEqual(0, recovered.returncode, recovered.stdout)
                self.assertNotIn(POSTGRES_PASSWORD, recovered.stdout)
                postgres_targets = [
                    target for target in self.state.targets
                    if target["attributes"].get("kind") == "postgres"
                ]
                self.assertEqual(1, len(postgres_targets))
                self.assertEqual(target_id, postgres_targets[0]["id"])
                recovered_state = credential_state_file.read_text(encoding="utf-8")
                self.assertIn(target_id, recovered_state)
                self.assertNotIn(POSTGRES_USERNAME, recovered_state)
                self.assertNotIn(POSTGRES_PASSWORD, recovered_state)

                self.state.clear_requests()
                replay = run_tasks(target_tasks, self.server.origin, variables=variables)
                self.assertEqual(0, replay.returncode, replay.stdout)
                self.assertRegex(replay.stdout, r"changed=0\s+unreachable=0\s+failed=0")
                self.assertFalse(any(
                    method in {"POST", "PATCH", "DELETE"}
                    for method, _path, _body in self.state.requests
                ))

    def test_drifted_provider_state_is_persistently_reconciled_for_every_resource_type(self) -> None:
        self.state.seed_drifted_resources()

        reconciled = run_tasks(
            provider_reconciliation_tasks(),
            self.server.origin,
            collector_secret="generated-provider-secret",
        )

        self.assertEqual(0, reconciled.returncode, reconciled.stdout)
        patched_paths = [path for method, path, _body in self.state.requests if method == "PATCH"]
        self.assertTrue(any(re.fullmatch(r"/api/v1/collectors/[^/]+", path) for path in patched_paths))
        self.assertTrue(any(re.fullmatch(r"/api/v2/dashboards/[^/]+", path) for path in patched_paths))
        self.assertEqual(4, sum("/charts/" in path and "/alerts/" not in path for path in patched_paths))
        self.assertEqual(3, sum("/targets/" in path for path in patched_paths))
        self.assertEqual(5, sum("/alerts/" in path for path in patched_paths))
        self.assertEqual(2, sum("/monitors/" in path for path in patched_paths))

        self.state.clear_requests()
        replay = run_tasks(
            provider_reconciliation_tasks(),
            self.server.origin,
            collector_secret="generated-provider-secret",
        )
        self.assertEqual(0, replay.returncode, replay.stdout)
        self.assertRegex(replay.stdout, r"changed=0\s+unreachable=0\s+failed=0")
        self.assertFalse(any(method in {"POST", "PATCH"} for method, _path, _body in self.state.requests))

    def test_stopped_drifted_collector_is_reconciled_before_installer_invocation(self) -> None:
        self.state.seed_drifted_resources()
        variables = {
            "better_stack_collector_id": "collector-1",
            "better_stack_collector_provider_resource": copy.deepcopy(self.state.collectors[0]),
        }

        result = run_tasks(
            collector_startup_order_tasks(),
            self.server.origin,
            collector_secret="generated-provider-secret",
            variables=variables,
        )

        self.assertEqual(0, result.returncode, result.stdout)
        requests = [(method, path) for method, path, _body in self.state.requests]
        installer_index = requests.index(("POST", "/test/installer"))
        patch_index = requests.index(("PATCH", "/api/v1/collectors/collector-1"))
        readback_index = requests.index(("GET", "/api/v1/collectors/collector-1"))
        self.assertLess(patch_index, installer_index)
        self.assertLess(readback_index, installer_index)

    def test_relative_prometheus_endpoint_is_rejected_before_provider_mutation(self) -> None:
        self.state.seed_converged_resources()
        self.state.targets = []
        tasks = task_slice(
            "Read existing Better Stack collector metric targets",
            "Verify Better Stack service targets are provider-side resources",
        )

        result = run_tasks(
            tasks,
            self.server.origin,
            collector_secret="generated-provider-secret",
            variables={"better_stack_collector_id": "collector-1"},
            environment_overrides={"BETTER_STACK_PROXY_TARGET_ENDPOINT": "/metrics"},
        )

        self.assertNotEqual(0, result.returncode, result.stdout)
        self.assertFalse(any(
            method == "POST" and path.endswith("/targets")
            for method, path, _body in self.state.requests
        ))

    def test_duplicate_dashboard_is_rejected_before_identity_selection(self) -> None:
        duplicate = resource("101", self.state.dashboard_attributes())
        self.state.dashboards = [
            resource("100", self.state.dashboard_attributes()),
            duplicate,
        ]
        tasks = task_slice(
            "Discover Better Stack dashboards by stable provider name",
            "Select the Better Stack production dashboard",
        )

        result = run_tasks(tasks, self.server.origin, collector_secret="custodied")

        self.assertNotEqual(0, result.returncode, result.stdout)
        self.assertIn("Verify exactly one Better Stack production dashboard candidate", result.stdout)

    def test_duplicate_alert_summaries_are_resolved_to_details_and_rejected(self) -> None:
        self.state.seed_converged_resources()
        duplicate = copy.deepcopy(self.state.alerts[0])
        duplicate["id"] = "399"
        self.state.alerts.append(duplicate)
        tasks = task_slice(
            "Read back Better Stack dashboard alerts",
            "Verify Better Stack dashboard alert fields match the declared contract",
        )
        variables = self.chart_identity_variables()

        result = run_tasks(
            tasks,
            self.server.origin,
            collector_secret="custodied",
            variables=variables,
        )

        self.assertNotEqual(0, result.returncode, result.stdout)
        self.assertIn("Verify Better Stack service and filesystem alerts are provider-side resources", result.stdout)
        requests = [(method, path) for method, path, _body in self.state.requests]
        self.assertIn(("GET", "/api/v2/alerts/301"), requests)
        self.assertIn(("GET", "/api/v2/alerts/399"), requests)

    def test_alert_reconciliation_uses_the_top_level_provider_endpoint(self) -> None:
        self.state.seed_drifted_resources()
        tasks = task_slice(
            "Read existing Better Stack dashboard alerts",
            "Reconcile drifted Better Stack service and filesystem alerts",
        )

        result = run_tasks(
            tasks,
            self.server.origin,
            collector_secret="custodied",
            variables=self.chart_identity_variables(),
        )

        self.assertEqual(0, result.returncode, result.stdout)
        patched_paths = [
            urlparse(path).path
            for method, path, _body in self.state.requests
            if method == "PATCH"
        ]
        self.assertEqual(
            sorted(f"/api/v2/alerts/{alert_id}" for alert_id, *_unused in ALERTS),
            sorted(patched_paths),
        )

if __name__ == "__main__":
    unittest.main(verbosity=2)
