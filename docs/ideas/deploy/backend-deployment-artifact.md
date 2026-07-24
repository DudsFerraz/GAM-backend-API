# Backend Deployment Artifact Decision: OCI Image

GAM will deploy the backend to production as an **OCI container image**.

The Java JAR remains part of the backend build process, but it is not the canonical production deployment artifact. The production deployment procedure will select and run an immutable OCI image containing the compiled backend JAR and its required Java runtime.

Direct deployment of a JAR to the production host is not part of the initial production procedure.

## Definitions

### JAR

A JAR is a Java archive containing the compiled backend application and its Java dependencies.

A JAR can normally be started with a command such as:

```bash
java -jar gam-backend.jar
```

Running the JAR directly requires the production host to have a compatible Java runtime installed and correctly configured.

### OCI image

An OCI image is a standardized container image. Docker-compatible images normally follow OCI standards and can be executed by a container runtime such as Docker Engine.

For GAM, the backend OCI image will contain:

- The compiled backend JAR
- The approved Java runtime
- Required operating-system libraries
- A defined startup command
- A non-root runtime user
- Default runtime behavior required by the application

The OCI image will not contain:

- Production secrets
- Production database data
- Environment-specific domain configuration
- Backup credentials
- Mutable application state

---

## Decision

The backend pipeline will:

1. Compile and test the Java application.
2. Produce the backend JAR.
3. Package that JAR into an OCI image.
4. Publish the OCI image to an approved container registry.
5. Record the image version and immutable digest.
6. Deploy the image by digest through the canonical production composition.

The OCI image is the **canonical production deployment artifact**.

The JAR may be retained by the CI system for diagnostics, traceability, inspection, or other engineering purposes. However, production deployment must not switch informally between direct JAR execution and container execution.

There must be one documented production deployment model.

---

## Rationale

### Reproducible runtime

The OCI image packages the backend together with its required Java runtime.

This prevents the production host from depending on an independently installed and potentially mismatched Java version.

For example, the application should not behave differently because:

- Development uses Java 21
- CI uses a different Java distribution
- The VPS accidentally has Java 17
- A host update changes the installed Java runtime

The approved Java version and runtime distribution become part of the image definition.

### Easier VPS rebuilding

The Hostinger VPS needs a relatively small host-level software set:

- The approved Linux operating system
- Docker Engine
- Docker Compose
- SSH and firewall configuration
- Backup and monitoring tooling
- Versioned operational scripts

Java does not need to be installed and managed separately on the host for the backend.

A replacement VPS can run the same backend image after the host provisioning process is completed.

### Immutable deployment

Production will deploy the backend using an immutable image digest, for example:

```text
registry.example.com/gam-backend@sha256:<digest>
```

A digest identifies the exact image contents. If the image contents change, the digest changes.

A human-readable version may also be recorded:

```text
Backend version: 2.1.0
Image digest: sha256:<digest>
```

Production must not deploy mutable references such as:

```text
gam-backend:latest
```

A mutable tag does not provide sufficient evidence of the exact artifact currently running.

### Safer rollback identification

The deployment record can preserve:

- The current backend image digest
- The previous backend image digest
- The corresponding frontend version
- The database migration state
- The verification result

For an application-only or backward-compatible release, rollback can select the previous known image digest.

Database rollback remains a separate concern. Returning to an earlier image is unsafe when a database migration made the previous backend incompatible with the current schema.

### Consistent environments

The same backend image can be exercised in:

- Development integration environments
- Production-like validation
- Load testing
- Restoration drills
- Initial production
- Recovery environments

Environment-specific values are supplied at runtime rather than embedded in the image.

### Provider portability

The image must not depend on Hostinger-specific application behavior.

The same OCI image should be runnable on another compatible Linux host with Docker or another suitable OCI runtime, subject to equivalent configuration, secrets, networking, and storage.

This preserves a practical migration path even though Hostinger is the accepted initial VPS provider.

---

## Canonical build and release flow

```text
Backend source code
        |
        v
Compile and test
        |
        v
Produce versioned JAR
        |
        v
Build OCI image containing JAR and Java runtime
        |
        v
Scan and verify image
        |
        v
Publish immutable image
        |
        v
Record version and digest
        |
        v
Deployment explicitly selects the digest
        |
        v
Hostinger VPS pulls and runs the image
```

Publishing an image must not automatically deploy it to production.

A production deployment must explicitly select one compatible frontend/backend pair.

Example:

```text
Frontend version: 1.4.0
Backend version: 2.1.0
Backend image digest: sha256:<digest>
```

---

## Image construction requirements

The production backend image should:

- Use an explicitly pinned Java runtime base image.
- Use a supported Java version.
- Run the application as a non-root user.
- Contain only the files required at runtime.
- Avoid development tools and build dependencies in the final image.
- Define a clear startup command.
- Support graceful shutdown.
- Expose only the private backend application port.
- Write application logs to standard output and standard error.
- Avoid embedding environment-specific configuration.
- Avoid embedding credentials or secrets.
- Be compatible with explicit CPU and memory limits.
- Be compatible with the production health-check strategy.
- Be identifiable by version, source commit, and digest.

A multi-stage image build should be used where it meaningfully reduces the final image size and removes build-time dependencies.

The base image version must not rely only on a floating tag. The build process should provide a deliberate mechanism for reviewing and updating the Java runtime image.

---

## Registry requirements

The selected container registry must support:

- Private image storage
- Immutable digest-based access
- Authentication suitable for automated deployment
- Retention of current and previous production images
- Access control
- Auditability where available
- Reliable image retrieval from the production VPS

Registry credentials must:

- Be stored outside the repository
- Use the minimum permissions required
- Be revocable
- Be rotated when necessary
- Not be embedded in the OCI image
- Not appear in deployment logs

The production host should receive read-only access when the registry supports sufficiently restricted credentials.

---

## Runtime configuration

Environment-specific configuration must be provided when the container starts.

This includes:

- `GAM_PUBLIC_ORIGIN`
- Database connection information
- Authentication and signing secrets
- Backup or external-service configuration, where relevant
- Runtime resource limits
- Logging configuration

Production secrets must not be:

- Committed to Git
- Embedded in the JAR
- Embedded in the OCI image
- Written into image labels
- Printed in build logs
- Printed in deployment logs

Secrets should be mounted or injected through the approved production secret-management procedure.

---

## Docker Compose ownership

The backend repository owns the canonical production composition.

The composition must define, at minimum:

- Reverse proxy service
- Backend service
- PostgreSQL service
- Private application network
- Persistent database storage
- Required health checks
- Resource limits
- Restart behavior
- Explicit image references

Only the reverse proxy may publish public application ports.

The backend must remain reachable only through the private host or container network.

PostgreSQL port `5432` must not be published to the public Internet.

The backend’s internal port must not be publicly exposed.

---

## PostgreSQL distinction

The PostgreSQL container image and PostgreSQL data are separate concerns.

The PostgreSQL image contains the database software.

The PostgreSQL volume contains GAM’s database data.

```text
PostgreSQL image
    = database program and runtime

PostgreSQL volume
    = production data
```

Recreating or replacing the PostgreSQL container must not intentionally delete the persistent data volume.

The volume remains stored on the same VPS and is therefore still part of the accepted single point of failure. It does not replace encrypted off-host database backups.

The backend OCI image must never contain PostgreSQL production data.

---

## Frontend distinction

The React frontend is not packaged as a JAR.

The frontend pipeline produces static files through Vite, such as:

```text
index.html
assets/application-<fingerprint>.js
assets/application-<fingerprint>.css
```

The frontend may be published as a versioned archive or another immutable static artifact.

The accepted initial model is:

```text
Frontend
    -> immutable versioned static artifact

Backend
    -> immutable OCI image containing the JAR

Database
    -> PostgreSQL image plus persistent data volume
```

Node.js is required during the frontend build, but it does not need to run on the production VPS solely because the frontend uses React and Vite.

---

## Deployment requirements

A backend deployment must:

1. Select an explicit backend version.
2. Select an explicit OCI image digest.
3. Confirm compatibility with the selected frontend version.
4. Verify that the image exists and can be pulled.
5. Confirm a recent successful backup before database-changing releases.
6. Record the currently deployed release manifest.
7. Pull the selected image before stopping the current backend where practical.
8. Run required database migrations through the approved migration procedure.
9. Start the selected backend image.
10. Wait for the backend to become ready.
11. Verify proxy routing, backend health, and database connectivity.
12. Execute representative API checks.
13. Record the deployed version, digest, migration result, and verification result.
14. Roll back when verification fails and rollback remains compatible with the database state.

The deployment must not depend on manually copying an untracked JAR into the VPS.

---

## Health checks

The backend image must support the project’s health-check model.

At minimum, the deployment process must distinguish between:

- Process running
- Application alive
- Application ready to serve requests
- Database connectivity available

A container being in the `running` state is not sufficient proof that the backend is operational.

Health endpoints must not expose credentials, secrets, internal stack traces, or unnecessary infrastructure details.

---

## Resource controls

The backend container must have explicit resource configuration appropriate to the selected Hostinger plan.

This includes:

- JVM heap limits
- Container memory limits
- CPU limits or reservations where appropriate
- Bounded database connection pool
- Controlled thread pools
- Graceful shutdown timeout

The JVM heap must not be configured to consume all memory available to the container or VPS.

Memory must remain available for:

- JVM native memory
- PostgreSQL
- Operating-system services
- Filesystem cache
- Reverse proxy
- Monitoring
- Backup operations
- Deployment activities

The initial limits must be validated through production-like load testing.

---

## Logging requirements

The backend image must write operational logs to standard output and standard error so that the container runtime can collect them consistently.

Logs must not contain:

- Passwords
- Authorization headers
- Refresh tokens
- Access tokens
- Cookies
- CSRF tokens
- Database credentials
- Registry credentials
- Private signing keys

Container log rotation must be configured to prevent unbounded disk growth.

Persistent business or audit records must not depend solely on ephemeral container logs.

---

## Security requirements

The OCI deployment model does not provide security automatically.

The implementation must still ensure that:

- The backend runs as a non-root user.
- The image does not contain secrets.
- Unnecessary packages are omitted.
- Base images and dependencies are patched.
- Images are scanned before production release.
- The backend port is private.
- PostgreSQL is private.
- Filesystem permissions are minimal.
- Writable paths are limited.
- Registry credentials are restricted.
- Production does not use mutable tags.
- Image provenance remains traceable to a source commit and build.
- The host operating system and Docker runtime remain patched.

A read-only root filesystem should be used where practical, with explicit writable mounts for the limited paths that require them.

---

## Rollback implications

The previous compatible backend image digest must remain available throughout the rollback window.

An application rollback can select the previous digest when:

- The database schema remains compatible.
- The previous frontend/backend pair remains available.
- Required configuration remains compatible.

An image rollback alone is not safe when an incompatible database migration has already been applied.

Each database-changing release must classify rollback behavior as one of the following:

- Previous image remains compatible.
- A forward corrective release is required.
- Database restoration is required before using the previous image.

The deployment record must make this classification explicit.

---

## JAR retention policy

The JAR may be retained as a CI artifact when it provides useful traceability or diagnostics.

Possible uses include:

- Inspecting the compiled application
- Security analysis
- Local debugging
- Reproducing image construction
- Confirming the contents packaged into the OCI image

The existence of a published JAR does not create a second production deployment method.

The initial production runbook must describe only OCI image deployment.

Any future proposal to deploy the JAR directly must be treated as an explicit architectural change and must address:

- Java installation and patching on the host
- `systemd` service configuration
- Runtime-version consistency
- Host-level file layout
- Logging
- Health checks
- Deployment
- Rollback
- Rebuildability
- Migration from the container model

---

## Consequences

### Positive consequences

- More reproducible backend runtime
- Easier rebuilding of the Hostinger VPS
- Explicit Java runtime version
- Easier artifact identification
- Digest-based immutable deployment
- Consistent execution across validation, production, and recovery
- Simpler rollback artifact selection
- Reduced application-runtime configuration on the host
- Improved future provider portability

### Operational costs

- Docker Engine and Compose must be maintained.
- The team must understand image building and container operation.
- A container registry is required.
- Registry credentials must be managed.
- Images and unused layers must be cleaned up.
- Container logs and resource limits must be configured.
- Base images must be patched and rebuilt.
- Database persistence must be handled correctly.
- Container deployment does not eliminate the need for backup, monitoring, or migration safety.

These costs are accepted because they support GAM’s goals of reproducibility, rebuildability, and controlled deployment.

---

## Out of scope

This decision does not introduce:

- Kubernetes
- Multiple application servers
- High availability
- Managed container orchestration
- Automatic production deployment after image publication
- Blue-green deployment
- Zero-downtime deployment
- Cross-origin frontend and backend hosting
- Managed PostgreSQL
- A second production deployment path based on direct JAR execution

These may be evaluated later only when justified by production evidence or changed project requirements.

---

## Validation criteria

This decision is considered implemented when:

- CI builds the backend JAR successfully.
- CI builds an OCI image from the tested JAR.
- The image contains the approved Java runtime.
- The image runs as a non-root user.
- The image contains no production secrets.
- The image is published to the approved registry.
- The image digest is recorded.
- The Hostinger validation VPS can pull and run the image.
- Docker Compose references an explicit version and digest.
- Backend and PostgreSQL ports remain private.
- Health checks verify readiness.
- Resource limits are configured.
- Logs are rotated and do not expose secrets.
- A deployment records the frontend/backend pair.
- The previous compatible digest remains available.
- Rollback is tested for a database-compatible release.
- The complete environment can be rebuilt on a clean VPS using versioned configuration.

---

## Review triggers

This decision should be reviewed when:

- Docker or OCI operation becomes a disproportionate burden.
- Host resource constraints materially favor direct host execution.
- The project adopts a managed application platform.
- The project adopts managed container orchestration.
- Security requirements mandate different runtime isolation.
- Build or registry reliability becomes unacceptable.
- Production evidence demonstrates a clear operational advantage to another deployment model.

Until one of these conditions justifies a change, the OCI image remains the canonical backend production deployment artifact.

---

## Final decision summary

GAM’s backend will be compiled into a JAR and packaged into an OCI image containing the approved Java runtime.

Production will deploy the OCI image by immutable digest through the canonical Docker Compose configuration on the Hostinger KVM VPS.

The JAR may be retained as a build artifact, but direct JAR deployment is not part of the initial production model.

This decision provides a consistent, versioned, reproducible, and rebuildable backend deployment process while preserving GAM’s accepted single-VPS architecture.