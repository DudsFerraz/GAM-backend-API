#!/usr/bin/env bash
set -euo pipefail

ansible_root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
cd "$ansible_root"

ansible_connection_args=()
while (($# > 0)); do
  case "$1" in
    --check|--skip-tags|--tags|--start-at-task|--list-hosts|--list-tasks)
      echo "Invalid idempotency argument: $1 is not allowed." >&2
      exit 1
      ;;
    --private-key|--ssh-common-args|--timeout)
      if (($# < 2)); then
        echo "Invalid idempotency argument: $1 requires a value." >&2
        exit 1
      fi
      ansible_connection_args+=("$1" "$2")
      shift 2
      ;;
    --private-key=*|--ssh-common-args=*|--timeout=*)
      ansible_connection_args+=("$1")
      shift
      ;;
    *)
      echo "Unsupported idempotency argument: $1. Only connection options are allowed." >&2
      exit 1
      ;;
  esac
done

apply_log="$(mktemp)"
replay_log="$(mktemp)"
firewall_state_log="$(mktemp)"
trap 'rm -f -- "$apply_log" "$replay_log" "$firewall_state_log"' EXIT
postgresql_monitoring_state_marker='PostgreSQL monitoring state verified: pg_roles pg_auth_members pg_monitor has_database_privilege pg_extension pg_stat_statements shared_preload_libraries'
operator_cidrs_json="${GAM_OPERATOR_CIDRS:?GAM_OPERATOR_CIDRS is required}"
site_play_vars="{\"gam_operator_cidrs\":${operator_cidrs_json}}"

if ! ansible-playbook \
  -i inventory/production.yml \
  site.yml \
  --extra-vars "$site_play_vars" \
  "${ansible_connection_args[@]}" \
  --diff \
  2>&1 | tee "$apply_log"; then
  echo 'Initial production-site apply failed.' >&2
  exit 1
fi

if ! test -s "$apply_log" || ! grep -Eqi 'secret input convergence verified|/etc/gam/secrets' "$apply_log"; then
  echo 'Initial production-site apply did not verify secret input convergence.' >&2
  exit 1
fi

if ! grep -Fq "$postgresql_monitoring_state_marker" "$apply_log"; then
  echo 'Initial production-site apply did not verify PostgreSQL monitoring state.' >&2
  exit 1
fi

firewall_cidr="${GAM_SSH_ALLOWED_CIDR:?GAM_SSH_ALLOWED_CIDR is required}"
firewall_port="${GAM_SSH_ALLOWED_PORT:-22}"
if ! ansible production \
  -i inventory/production.yml \
  --user gamops \
  "${ansible_connection_args[@]}" \
  --become \
  --become-method sudo \
  -m ansible.builtin.shell \
  -a 'ufw status verbose && ufw status numbered' 2>&1 | tee "$firewall_state_log"; then
  echo 'Post-apply firewall state inspection failed.' >&2
  exit 1
fi

# Expected UFW policy marker: Default: deny (incoming)
if ! test -s "$firewall_state_log" \
  || ! grep -Eqi '^Status:[[:space:]]+active[[:space:]]*$' "$firewall_state_log" \
  || ! grep -Eqi '^Default:[[:space:]]+deny[[:space:]]+\(incoming\)[[:space:]]*$' "$firewall_state_log" \
  || ! grep -Eqi '^[[:space:]]*\[[[:space:]]*[0-9]+\][[:space:]]+80/tcp( \(v6\))?[[:space:]]+ALLOW IN[[:space:]]+Anywhere( \(v6\))?[[:space:]]*$' "$firewall_state_log" \
  || ! grep -Eqi '^[[:space:]]*\[[[:space:]]*[0-9]+\][[:space:]]+443/tcp( \(v6\))?[[:space:]]+ALLOW IN[[:space:]]+Anywhere( \(v6\))?[[:space:]]*$' "$firewall_state_log" \
  || grep -Eqi '5432/tcp|8080/tcp' "$firewall_state_log" \
  || grep -Eqi '0\.0\.0\.0/0|::/0' "$firewall_state_log"; then
  echo 'Post-apply firewall state verification failed: expected exact restricted UFW state.' >&2
  exit 1
fi

if ! awk -v port="$firewall_port" -v cidr="$firewall_cidr" '
  BEGIN { valid = 1; http = 0; https = 0; ssh = 0 }
  /^[[:space:]]*\[[[:space:]]*[0-9]+\][[:space:]]+/ {
    line = $0
    sub(/^[[:space:]]*\[[[:space:]]*[0-9]+\][[:space:]]*/, "", line)
    gsub(/[[:space:]]+/, " ", line)
    sub(/^ /, "", line)
    sub(/ $/, "", line)
    if (line == "80/tcp ALLOW IN Anywhere" || line == "80/tcp (v6) ALLOW IN Anywhere (v6)") {
      if (seen[line]++) valid = 0
      http = 1
    } else if (line == "443/tcp ALLOW IN Anywhere" || line == "443/tcp (v6) ALLOW IN Anywhere (v6)") {
      if (seen[line]++) valid = 0
      https = 1
    } else if (line == port "/tcp ALLOW IN " cidr || line == port "/tcp (v6) ALLOW IN " cidr) {
      if (seen[line]++) valid = 0
      ssh = 1
    } else {
      valid = 0
    }
  }
  END { exit !(valid && http && https && ssh) }
' "$firewall_state_log"
then
  echo 'Post-apply firewall state verification failed: unexpected or missing incoming rule.' >&2
  exit 1
fi

if ! ansible-playbook \
  -i inventory/production.yml \
  site.yml \
  --extra-vars "$site_play_vars" \
  --skip-tags bootstrap \
  --user gamops \
  "${ansible_connection_args[@]}" \
  --diff \
  2>&1 | tee "$replay_log"; then
  echo 'Production-site replay failed.' >&2
  exit 1
fi

if grep -Eq 'changed=[1-9][0-9]*' "$replay_log" || ! grep -Eq 'changed=0' "$replay_log"; then
  echo 'Production-site replay failed: expected changed=0.' >&2
  exit 1
fi

if ! grep -Fq "$postgresql_monitoring_state_marker" "$replay_log"; then
  echo 'Production-site replay did not verify PostgreSQL monitoring state.' >&2
  exit 1
fi
