# VPS Sizing Validation Plan

This document defines how GAM will validate whether a Hostinger KVM VPS has sufficient CPU, memory, storage, and network capacity for initial production.

Hostinger KVM 2 is the accepted direct production plan. This validation therefore determines safe resource limits, production readiness, and future upgrade triggers on KVM 2; it does not compare KVM 1 against KVM 2.

The sizing decision must be based on measured production-like behavior rather than only on:

- Monthly user count
- Registered user count
- The number of open browser sessions
- Advertised VPS specifications
- Informal manual testing
- A single short benchmark

The current working assumptions are:

- Approximately 300 users per month
- Normally 1–8 concurrent users
- An occasional monthly peak around 100 concurrent users
- One VPS hosting the reverse proxy, static frontend, backend, and PostgreSQL
- Planned maintenance downtime is acceptable
- High availability and multi-host scaling are outside the initial scope
- Hostinger KVM 2 is the validation and direct initial production environment

---

## Purpose

The validation process must answer the following questions:

1. Can KVM 2 safely run the complete production-like composition?
2. Can the system handle the expected 100-user peak with acceptable response times?
3. Does the application retain sufficient capacity while backups, monitoring, and other operational work are running?
4. Which explicit JVM, PostgreSQL, container, and host memory limits preserve safe margin within KVM 2?
5. Do KVM 2's two vCPUs provide stable latency during peak and operational work?
6. Does KVM 2 storage preserve enough free space for the operating system, containers, PostgreSQL, logs, releases, and recovery work?
7. What resource reaches its limit first?
8. What thresholds should trigger a future VPS upgrade?
9. Can the complete environment recover and return to service within the accepted RTO?
10. Does the system maintain enough unused capacity to handle workload variation after production begins?

---

## Core sizing principle

User count does not directly determine VPS size.

The resource requirement depends on what users do and how frequently they do it.

For example, 100 concurrent users could represent:

- 100 authenticated users reading a page without making further requests
- 100 users making one lightweight request every 30 seconds
- 100 users continuously searching and filtering
- 100 users performing writes and triggering database transactions
- 100 users generating reports or downloading files
- 100 users repeatedly causing expensive queries

The sizing model must therefore use workload characteristics such as:

- Requests per second
- Request mix
- Think time between actions
- Query cost
- Database connection usage
- Response payload size
- File-transfer volume
- Background processing
- JVM memory behavior
- Database size and index size
- Backup and maintenance activity

---

## Decision model

KVM 2 is the accepted initial plan rather than a hypothesis competing with KVM 1.

Testing must demonstrate whether the selected resource limits and composition preserve production headroom. Evidence determines configuration changes and future upgrade triggers; it does not reopen a disposable KVM 1 phase.

---

## Validation environment

The test environment should reproduce production as closely as practical.

It should use:

- The same operating system planned for production
- The same Docker Engine and Docker Compose model
- The same reverse-proxy configuration
- The same backend OCI image intended for production
- The same PostgreSQL major version
- The same database migration state
- The same health-check configuration
- The same container resource limits
- The same log configuration
- The same backup scripts
- The same monitoring agents
- The same frontend artifact type
- HTTPS and the same-origin topology
- Production-like firewall and networking rules

The validation environment must not rely on development shortcuts that would make the result unrepresentative.

Examples of invalid shortcuts include:

- Running the database outside the VPS
- Disabling TLS
- Removing authentication
- Disabling database migrations
- Using a local developer machine for part of the stack
- Omitting monitoring or backup processes
- Testing with an empty or unrealistically small database
- Giving the backend unlimited memory during testing when production will have limits

---

## Test data

The validation database should contain production-like data volume and distribution.

The dataset should approximate:

- Expected user count
- Expected historical records
- Typical relationships between records
- Searchable text volume
- Typical index sizes
- Representative permissions and roles
- Expected file metadata
- Expected report inputs
- Enough data to expose inefficient queries

When the expected first-year production data volume is uncertain, tests should use more than the expected launch volume.

A reasonable initial approach is to test at:

- Expected launch dataset
- Expected 12-month dataset
- A larger stress dataset, such as two to three times the estimated 12-month volume

The dataset must not contain real sensitive production data unless the test environment is approved to hold it. Synthetic or anonymized data should be preferred.

---

## Workload model

The load test must represent user journeys rather than isolated endpoints.

Each virtual user should perform realistic workflows with pauses between actions.

### Typical coordinator workflow

A coordinator workload may include:

1. Open the application.
2. Authenticate.
3. Load the main dashboard.
4. Browse records.
5. Search or filter records.
6. Open a record.
7. Create or update data.
8. Navigate between application sections.
9. Remain idle for a realistic period.
10. Refresh or continue working.
11. Log out.

### Typical general-user workflow

A general-user workload may include:

1. Open the application.
2. Authenticate.
3. Read relevant content.
4. Submit or update permitted information.
5. Navigate through several views.
6. Remain idle between actions.
7. Log out or allow the session to expire.

### Authentication workload

The test should exercise:

- Login
- Access-token use
- Refresh-token flow
- Logout
- CSRF protection
- Expired-session behavior
- Invalid authentication attempts at a controlled rate

Authentication testing must not weaken or bypass the accepted production security model.

### Database-heavy workflow

Where applicable, include:

- Search
- Filtering
- Sorting
- Pagination
- Aggregation
- Report generation
- Bulk reads
- Representative writes
- Transactions touching multiple tables

---

## Concurrency scenarios

The validation process should include several scenarios.

### Scenario A: Normal operation

Purpose:

- Validate the common 1–8 concurrent-user workload.
- Establish baseline resource consumption.
- Detect unnecessary background resource use.

Suggested profile:

- Start with one active user.
- Increase gradually to eight active users.
- Maintain the workload for at least 30 minutes.
- Include realistic user think time.

Expected result:

- Low and stable response times
- No errors
- Low CPU utilization
- Stable memory
- No swap pressure
- No unexpected container restarts

### Scenario B: Expected peak

Purpose:

- Validate the occasional 100-user peak.

Suggested profile:

1. Begin with normal background activity.
2. Ramp from 0 to 100 active virtual users over approximately 10 minutes.
3. Maintain 100 active users for 30–60 minutes.
4. Reduce load gradually rather than stopping all users simultaneously.

A "login storm" can be plausible in a reunião mensal.

### Scenario C: Sudden peak

Purpose:

- Measure behavior during a faster-than-expected demand increase.

Suggested profile:

- Begin with 5–8 active users.
- Increase to 100 users over 1–3 minutes.
- Maintain the peak for 10–15 minutes.
- Observe recovery after the spike.

This scenario should not necessarily determine normal sizing, but it should reveal failure behavior.

### Scenario D: Capacity margin

Purpose:

- Determine whether the system has headroom beyond the expected peak.

Suggested profile:

- Ramp to 125 or 150 active users.
- Maintain the load for 10–20 minutes.
- Stop the test before system instability risks damaging the environment.

The goal is not to claim support for 150 production users. The goal is to identify how gracefully the system approaches its limit.

### Scenario E: Extended stability

Purpose:

- Detect memory leaks, resource accumulation, and gradual degradation.

Suggested profile:

- Run a mixed 20–40-user workload for 4–8 hours.
- Include authentication refreshes and repeated workflows.
- Monitor JVM memory after garbage collection.
- Monitor PostgreSQL connections and transaction behavior.
- Monitor disk and log growth.

### Scenario F: Backup under load

Purpose:

- Validate that the daily backup does not make the application unusable.

Suggested profile:

1. Run a representative 30–50-user workload.
2. Start the production-equivalent PostgreSQL backup.
3. Continue the workload until backup completion.
4. Compare latency, CPU, disk I/O, and error rate before, during, and after backup.

A second run should test backup activity during the 100-user peak if the first result shows adequate safety.

### Scenario G: Deployment under activity

Purpose:

- Validate the planned maintenance deployment behavior.

Suggested profile:

1. Maintain several active sessions.
2. Enable maintenance mode.
3. Stop or replace the backend.
4. Run a representative deployment.
5. Verify database migration behavior.
6. Start the new backend.
7. Verify readiness and representative API behavior.
8. Disable maintenance mode.

The test should record what users experience when requests are in progress.

### Scenario H: Recovery and restoration

Purpose:

- Validate storage needs and the accepted RTO.

Suggested profile:

1. Provision a clean isolated environment.
2. Apply the approved host configuration.
3. Restore a production-like database backup.
4. Deploy the selected frontend/backend pair.
5. Verify authentication and representative reads and writes.
6. Record the complete restoration duration.
7. Record temporary disk-space usage during restoration.

---

## Test tooling

The project may use a load-testing tool such as:

- k6
- Gatling
- Apache JMeter
- Another tool capable of scripted HTTP workflows and measurable concurrency

The selected tool must support:

- Authentication flows
- Cookies
- CSRF headers
- Variable user data
- Think time
- Gradual ramp-up
- Response validation
- Failure reporting
- Latency percentiles
- Reproducible test definitions
- Exportable results

The test scripts must be versioned in the project repository.

Manual browser testing is useful for functional verification but is not sufficient for capacity validation.

---

## Required measurements

### VPS-level metrics

Measure:

- Overall CPU utilization
- Per-process or per-container CPU utilization
- CPU load average
- CPU-steal time where available
- RAM usage
- Available memory
- Swap usage
- Out-of-memory events
- Disk utilization
- Inode utilization
- Disk I/O wait
- Read and write latency
- Read and write throughput
- Network throughput
- Network errors
- Packet loss where measurable
- Container restart count

### Backend metrics

Measure:

- Requests per second
- Response-time percentiles
- HTTP error rate
- Active requests
- Request queueing
- JVM heap usage
- JVM non-heap usage
- Garbage-collection frequency
- Garbage-collection pause time
- Thread count
- Connection-pool usage
- Application startup time
- Readiness transitions
- Unexpected exceptions

### PostgreSQL metrics

Measure:

- Active connections
- Connection-pool saturation
- Query latency
- Slow-query frequency
- Transaction rate
- Lock waits
- Deadlocks
- Cache-hit behavior where available
- Temporary-file creation
- Checkpoint behavior
- Database size
- Index size
- Table growth
- Disk usage
- Backup duration

### Reverse-proxy metrics

Measure:

- Request count
- Response-status distribution
- TLS failures
- Upstream connection failures
- Upstream timeout frequency
- 502 and 503 responses
- Response latency
- Static-asset delivery
- Log volume

### Operational metrics

Measure:

- Backup duration
- Backup size
- Backup upload duration
- Restore duration
- Temporary restore disk usage
- Deployment duration
- Backend restart duration
- Time until readiness
- Verification duration
- Log growth per hour
- Container image disk usage

---

## Proposed acceptance criteria

These thresholds are proposed initial criteria. They must be reviewed after the first representative test results and before being treated as permanent service objectives.

### Functional correctness

The test passes only if:

- Authentication works correctly.
- CSRF protections remain enabled.
- No authorization checks are bypassed.
- User actions produce correct results.
- Database writes remain consistent.
- No unexpected data corruption occurs.
- Health checks reflect actual readiness.
- Representative workflows complete successfully.

### Error rate

Recommended initial target:

- Less than 1% unexpected request failures during the expected 100-user peak.
- No sustained 5xx failure condition.
- No unexplained authentication or authorization failure increase.

Expected errors deliberately generated by negative tests must be counted separately.

### Response latency

Recommended initial targets for ordinary API operations:

- p95 below 750 ms
- p99 below 2 seconds

These targets should exclude intentionally long-running operations such as large exports unless separate objectives are defined for them.

The project must also record:

- Median latency
- Maximum observed latency
- Latency during backup
- Latency during CPU or disk pressure

### CPU

Recommended initial target:

- Sustained CPU below approximately 70% during the expected peak.
- Short bursts above 70% may be acceptable.
- The VPS should not remain near full CPU utilization for extended periods.
- CPU-steal time should not materially destabilize latency.

A test that reaches acceptable latency only while CPU remains near 100% does not provide sufficient production headroom.

### Memory

Recommended initial target:

- Sustained memory usage below approximately 75–80%.
- No out-of-memory termination.
- No persistent swap usage.
- No continuous memory growth across the extended-stability test.
- JVM memory should return to a stable range after garbage collection.

A small configured swap area may provide emergency protection, but successful validation must not depend on regular swapping.

### Database connections

Recommended initial target:

- Connection-pool usage below approximately 70% during ordinary expected peak load.
- No sustained connection starvation.
- No uncontrolled increase in PostgreSQL connections.
- No connection leak after the load decreases.

### Storage

Recommended initial target:

- At least 20–30% disk capacity remains free after accounting for:
    - Production-like database size
    - Current and previous application releases
    - Container images
    - Logs
    - Temporary backup files
    - Restoration working space

The system must not rely on manually deleting files immediately before every deployment or backup.

### Disk I/O

The test should show:

- No persistent I/O saturation.
- No sustained high I/O wait.
- Backup activity does not make ordinary application use unacceptable.
- PostgreSQL query latency returns to baseline after operational work finishes.

### Recovery

The restoration test must:

- Complete within the accepted 24-hour RTO.
- Produce a usable application.
- Restore expected database data.
- Permit representative authentication, reads, and writes.
- Record a duration short enough to leave operational margin for diagnosis and approvals.

The operational target should be materially shorter than 24 hours where practical.

---

## Headroom requirement

A production configuration must not merely survive the expected peak.

It should retain enough capacity for:

- Workload variation
- Backup activity
- Monitoring
- Log processing
- JVM garbage collection
- PostgreSQL maintenance
- Deployment activity
- Minor growth before the next capacity review
- Shared-vCPU performance variability

The initial target is approximately 20–30% unused capacity in the limiting resource during the representative expected peak.

This is a planning margin, not a strict mathematical guarantee.

---

## KVM 2 production-readiness criteria

KVM 2 may receive production traffic only when all of the following are true:

- The full production-like composition runs reliably.
- The expected 100-user test meets the agreed latency and error criteria.
- No out-of-memory events occur.
- The system does not depend on sustained swapping.
- CPU does not remain saturated.
- Backup under load remains acceptable.
- Disk capacity preserves the required free-space margin.
- Restoration has sufficient working space.
- The extended test shows no memory or connection leaks.
- At least 20% practical headroom remains in the limiting resource.
- Future upgrade triggers are documented.

KVM 2 requires a capacity review or future upgrade when any of the following occurs:

- Memory usage approaches the safe limit.
- JVM memory must be constrained so heavily that application performance suffers.
- PostgreSQL lacks useful cache capacity.
- Swap becomes persistent.
- The OOM killer terminates a process.
- CPU remains saturated during expected load.
- Backup activity causes unacceptable latency.
- Deployment or migration activity cannot run safely.
- Disk capacity cannot preserve the required free-space margin.
- Restoration requires more temporary space than is safely available.
- Extended testing reveals unstable latency.
- The expected peak passes only without meaningful safety margin.
- Shared operational work interferes excessively with user requests.

A future larger plan should be selected only after evidence identifies the limiting resource and shows that configuration or application correction is insufficient.

---

## Diagnosing the limiting resource

### CPU-limited behavior

Likely indicators:

- High sustained CPU
- High load average
- Low available CPU during backups
- Increased request latency while memory remains stable
- PostgreSQL queries and JVM work competing for CPU
- High CPU-steal time

Likely responses:

- Move beyond KVM 2 only after confirming a CPU limit rather than an application or query defect
- Optimize expensive queries
- Reduce unnecessary application work
- Review report generation
- Review JVM behavior
- Schedule heavy maintenance outside peak use

Adding RAM alone will not solve a purely CPU-limited system.

### Memory-limited behavior

Likely indicators:

- Low available memory
- Persistent swap
- OOM termination
- Long garbage-collection activity
- PostgreSQL cache pressure
- Memory limits frequently reached
- Large latency spikes without full CPU saturation

Likely responses:

- Move beyond KVM 2 only after confirming that memory tuning or leak correction cannot preserve safe margin
- Review JVM heap and native memory
- Review thread count
- Review connection-pool size
- Review PostgreSQL memory settings
- Investigate leaks
- Reduce unnecessary host services

### Storage-limited behavior

Likely indicators:

- Disk usage grows rapidly
- Docker layers consume unexpected space
- Logs grow without bounds
- Local backups consume working space
- Restore cannot coexist with existing data
- PostgreSQL temporary files become large
- Free space falls below operational thresholds

Likely responses:

- Move to a plan with more storage
- Improve log rotation
- Remove unused images
- Reduce local backup retention
- Move application media to an appropriate external store
- Investigate query temporary-file usage

### Disk-I/O-limited behavior

Likely indicators:

- High I/O wait
- Slow database queries
- Large latency increase during backup
- Slow image extraction
- Slow PostgreSQL checkpoints
- CPU remains available while requests are delayed

Likely responses:

- Optimize queries and indexes
- Adjust backup timing or compression
- Reduce unnecessary writes
- Review logging volume
- Evaluate a larger or higher-performance VPS plan
- Separate file-heavy behavior from database storage in a future architecture review

### Network-limited behavior

Likely indicators:

- High outbound transfer
- Slow large responses
- Packet loss
- High latency unrelated to server load
- Bandwidth approaching provider limits
- File transfers affecting API responsiveness

Likely responses:

- Optimize payload size
- Enable appropriate compression
- Cache fingerprinted static assets
- Move large files to object storage in a future change
- Review provider network behavior
- Review user geography

---

## Test execution process

Each formal sizing test should follow this process.

### Before the test

1. Record the VPS plan and specifications.
2. Record operating-system version.
3. Record Docker and Compose versions.
4. Record backend image version and digest.
5. Record frontend version.
6. Record PostgreSQL version.
7. Record database size and data-generation method.
8. Confirm monitoring is functioning.
9. Confirm the environment contains no unrelated workloads.
10. Confirm the test will not affect real users or production data.
11. Reset or document existing logs and metrics.
12. Confirm available disk space.
13. Record current configuration and resource limits.

### During the test

1. Record start and end times.
2. Preserve the exact workload script.
3. Monitor all required metrics.
4. Note operational events such as backups or deployments.
5. Record errors and unexpected behavior.
6. Avoid changing configuration during a run.
7. Stop the test if there is risk of data corruption or host instability.

### After the test

1. Preserve the load-test report.
2. Export relevant metrics.
3. Review application and proxy logs.
4. Review PostgreSQL logs.
5. Review OOM and kernel events.
6. Compare results with acceptance criteria.
7. Identify the limiting resource.
8. Document configuration changes proposed for the next run.
9. Repeat the test after material changes.
10. Record the sizing decision and its evidence.

---

## Avoiding invalid conclusions

The project must not conclude that a VPS is sufficient based only on:

- A successful application startup
- Low average CPU with no peak test
- One endpoint benchmark
- A test without authentication
- A test against an empty database
- A test lasting only a few minutes
- A test with no user think time
- A test with unrealistic simultaneous requests
- A test that excludes backup activity
- A test that omits database writes
- A test performed from only one unusually low-latency machine
- A test where containers have different limits from production
- A test that ignores errors while reporting only average latency

Average response time alone is insufficient. Percentiles and failure behavior must be reviewed.

---

## Production monitoring after validation

Pre-production testing cannot predict every production behavior.

Better Stack is the accepted external availability, TLS, and host-monitoring provider. KVM 2 exports metrics through its metrics-only collector, while Better Stack checks public `GET /api/health` every five minutes. The independent AWS monitor remains authoritative for backup-object validation.

After launch, the project must continue monitoring:

- CPU
- CPU-steal time
- RAM
- Swap
- Disk usage
- Disk I/O
- Request latency
- Error rate
- PostgreSQL connections
- Slow queries
- Database growth
- Backup duration
- Container restarts
- Certificate health
- Network transfer

Capacity should be reviewed:

- After the first production week
- After the first production month
- Before known high-usage events
- After major feature releases
- After database growth materially changes
- After significant file-handling changes
- When any warning threshold is repeatedly reached

---

## Production upgrade triggers

The following should trigger an explicit capacity review:

- Sustained CPU above 70%
- Repeated CPU saturation
- Material CPU-steal-related latency
- Sustained RAM usage above 80%
- Persistent swap usage
- Any OOM event
- Disk utilization above 70%
- Forecast disk utilization above 80% within 90 days
- Repeated p95 or p99 latency objective violations
- Repeated connection-pool saturation
- Backup duration approaching the backup interval
- Backup activity causing unacceptable service degradation
- Database restoration approaching the accepted RTO
- Repeated container restarts
- Growth in database or media usage beyond the original estimate

Crossing one threshold does not always require an immediate plan upgrade, but it requires investigation and a documented decision.

---

## Required outputs

The sizing-validation process must produce:

- Versioned load-test scripts
- Description of production-like test data
- VPS configuration record
- Backend and frontend release identifiers
- Database version and size
- Resource-limit configuration
- Test scenario definitions
- Raw or exported metrics
- Latency and error reports
- Backup-under-load results
- Restoration results
- Identified limiting resource
- Accepted production size
- Upgrade triggers
- Reviewer and approval record

A concise final sizing record should include:

```text
Validation date:
Provider and plan:
Region:
vCPU:
RAM:
Disk:
Backend version and digest:
Frontend version:
PostgreSQL version:
Database size:
Expected-peak workload:
Peak requests per second:
p95 latency:
p99 latency:
Unexpected error rate:
Peak CPU:
Peak RAM:
Swap usage:
Peak disk I/O wait:
Peak database connections:
Backup duration:
Restore duration:
Observed limiting resource:
Remaining headroom:
Decision:
Required follow-up:
Approved by:
```

---

## Initial recommendation

The current validation sequence is:

1. Provision Hostinger KVM 2 directly with Ubuntu Server 24.04 LTS.
2. Deploy the complete production-like stack.
3. Validate normal 1–8-user behavior.
4. Validate the expected 100-user peak.
5. Run an extended stability test.
6. Run a backup during application load.
7. Rehearse deployment and rollback.
8. Restore the system in an isolated environment.
9. Evaluate CPU, memory, storage, disk I/O, and network evidence.
10. Record safe KVM 2 resource limits and production headroom.
11. Upgrade beyond KVM 2 only when evidence shows that the selected plan cannot meet the accepted criteria.

KVM 2 is the accepted direct initial production plan because its currently documented two vCPUs, 8 GB RAM, and 100 GB storage provide room for the Java backend, PostgreSQL, backups, deployments, and workload variation.

The final size must remain evidence-based.

---

## Final decision rule

GAM will launch on Hostinger KVM 2 when it:

- Meets functional requirements
- Meets the agreed latency and error objectives
- Supports backup and recovery work
- Preserves at least 20–30% practical capacity headroom
- Avoids sustained swap or CPU saturation
- Preserves sufficient disk space for operations and restoration
- Can be operated safely by the team

Cost savings do not justify selecting a plan that passes only under ideal conditions or lacks recovery and maintenance headroom.
