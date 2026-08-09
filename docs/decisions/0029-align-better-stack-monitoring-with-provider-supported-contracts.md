# ADR-0029: Align Better Stack Monitoring with Provider-Supported Contracts

## Status
Accepted

## Context
ADR-0028 selected Better Stack for external availability, TLS, and host monitoring. Its external-monitoring subsection and `REQ-OPS-006` described two behaviors that Better Stack's supported standard monitor API does not provide: exact raw-response-body matching and an incident threshold expressed as a count of three consecutive failed checks.

Better Stack standard keyword monitors accept a `required_keyword` and evaluate case-insensitive containment. Better Stack incident confirmation is expressed as an elapsed `confirmation_period` after the provider first observes a failure, not as an exact number of failed checks. Encoding unsupported `expected_body` or count-based fields would make the automation appear stricter than the provider configuration actually is.

The Better Stack collector also has a distinct supported Docker deployment and credential contract. Docker installations use the official collector composition and `COLLECTOR_SECRET`; this secret is not interchangeable with the Uptime API token used to manage monitors.

The public health response itself remains deliberately exact and minimal. That application contract does not require the continuous external monitor to duplicate every release-verification assertion.

## Decision
This ADR supersedes only the external-monitoring subsection of ADR-0028. The remainder of ADR-0028 stays accepted.

Use one standard Better Stack keyword monitor for the initial public availability check. Configure it to:

- send `GET /api/health` every 300 seconds;
- use `{"status":"UP"}` as `required_keyword`;
- use a 600-second `confirmation_period`;
- notify through Better Stack-hosted email and mobile push; and
- keep the AWS EventBridge, Lambda, and SNS backup-object monitor separate.

Describe the alert threshold as a ten-minute continuously failing confirmation window. Do not describe it as a provider guarantee that exactly three consecutive checks failed. Do not send unsupported `expected_body` or count-based failure fields to the Better Stack API.

Keep the exact public health status, content type, raw body, and cache policy in `REQ-OPS-011`. Deployment and release verification shall assert that exact contract independently. The initial external monitor shall not use metered Playwright transaction monitoring solely to duplicate those assertions.

Install the metrics-only Better Stack collector through the provider's supported Docker Compose workflow. Supply the dedicated `COLLECTOR_SECRET` from approved external secret custody. Keep the Uptime API token on the automation controller, do not reuse it as the collector secret, and do not invent an apt package, repository, token name, or host-agent configuration contract. Explicitly disable broad log and distributed-trace ingestion and verify the resulting source configuration in Better Stack before accepting monitoring readiness.

## Alternatives considered

### Option 1: Use a standard keyword monitor and time-based confirmation
Pros:
- Uses the documented Better Stack API and incident model.
- Preserves an external failure detector without another runtime system.
- Avoids Playwright transaction charges and browser-probe maintenance.
- Keeps exact response verification in the deployment workflow that owns release acceptance.

Cons:
- Keyword matching is case-insensitive containment rather than exact raw-body equality.
- A time-based confirmation window does not prove that exactly three checks ran or failed.

### Option 2: Use a Better Stack Playwright monitor
Pros:
- Custom JavaScript can assert the exact status, raw body, content type, and cache header externally.
- Keeps the probe hosted outside KVM 2.

Cons:
- Playwright execution is metered separately.
- Adds script maintenance and browser-probe complexity for a small health response.
- Still requires time-based incident confirmation unless separate state is introduced.

### Option 3: Add a custom external probe
Pros:
- Could implement exact body matching and explicit failure counting.
- Could store any additional probe state required by a count-based policy.

Cons:
- Adds another deployed service, credential, billing surface, and alert path.
- Duplicates Better Stack's availability role and increases the solo developer's operational burden.
- Can reduce failure-domain independence if coupled to the existing AWS backup-monitoring path.

### Option 4: Preserve unsupported fields in Ansible
Pros:
- Would leave the earlier tests and templates unchanged.

Cons:
- The Better Stack API would reject or ignore the configuration.
- Tests would validate fictional provider behavior rather than a deployable production contract.

## Consequences
Positive consequences:
- Monitoring automation can be verified against Better Stack's documented API.
- Alert timing is described truthfully and reproducibly.
- Exact health-response guarantees remain enforced before a release is accepted.
- Collector installation and credentials match the provider-supported Docker contract.
- Initial monitoring avoids unnecessary Playwright charges and a second custom probe.

Negative consequences:
- The continuous external check can accept a response that contains the configured keyword but has extra surrounding content or case differences.
- Release verification, not Better Stack keyword monitoring, is responsible for catching exact-response regressions.
- A brief recovery before the 600-second confirmation period ends can prevent incident creation even if multiple scheduled checks failed.
- The Better Stack collector source must be reviewed after provisioning to prove logs and traces remain disabled.

## Related requirements
- `REQ-OPS-006` (superseded)
- `REQ-OPS-007`
- `REQ-OPS-008`
- `REQ-OPS-011`
- `REQ-OPS-014`

## Related ADRs
- [ADR-0028: Complete the Initial Production Commissioning and Release Contracts](0028-complete-initial-production-commissioning-and-release-contracts.md)

## References
- [Better Stack monitor API](https://betterstack.com/docs/uptime/api/create-a-new-monitor/)
- [Better Stack keyword monitoring](https://betterstack.com/docs/uptime/keyword-monitor/)
- [Better Stack confirmation and recovery period](https://betterstack.com/docs/uptime/confirmation-and-recovery-period/)
- [Better Stack Playwright API monitoring](https://betterstack.com/docs/uptime/playwright-monitor/)
- [Better Stack collector](https://betterstack.com/docs/logs/collector/)
- [Better Stack pricing](https://betterstack.com/pricing)

## Related videos
- None.
