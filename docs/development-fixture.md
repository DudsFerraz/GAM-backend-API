# Local development fixture

The development fixture is a privileged, synthetic dataset for manual API
work. It creates Accounts with authorization-bearing Roles and stores a
synthetic signed-form PDF. It must never run in production, staging,
demonstration, maintenance, OpenAPI generation, or integration-test defaults.

The accepted behavior and endpoint-readiness catalog are defined by
[Development Fixture Policy and Dataset](requirements/platform/development-fixture-policy-and-dataset.md).
The callback and this document are maintenance companions to that Requirement
Specification; they do not define product behavior.

## One-time adoption

The previous random local fixture is not upgraded in place. Stop GAM
application processes and recreate only the disposable project-local database
or Compose volume before first use of this manifest. Do not import the legacy
Gmail Accounts or other old fixture rows.

## Configure a local credential

1. From an interactive terminal, run:

   ```powershell
   java scripts/NewDevelopmentFixturePasswordHash.java
   ```

2. Enter a Developer-selected password of 8 through 128 characters at the
   hidden prompt. The helper prints only a fresh delegated PBKDF2 hash. It does
   not accept the password as a command argument or persist the raw value.
3. Copy
   `src/main/resources/application-local.properties.example` to the ignored
   repository-root file `application-local.properties`.
4. Put `true` in `gam.dev-fixture.execution-enabled` and paste the generated
   hash into `gam.dev-fixture.password-hash`.
5. Start only the `dev` profile. The dev profile imports the ignored file and
   is the only committed profile that includes `classpath:db/dev-migration`.

Missing, blank, false, malformed, unsupported, or legacy configuration aborts
Flyway before fixture mutation. The committed example deliberately contains
no usable credential or fallback.

## Task-local database lifecycle

Each development task uses its own Docker Compose project and PostgreSQL named
volume. The instance identifier is selected in this order:

1. `GAM_DEV_INSTANCE_ID`, when explicitly configured;
2. `CODEX_THREAD_ID`, for a Codex task; or
3. `local`, for ordinary development outside Codex.

An explicit instance identifier must contain only lowercase letters, digits,
hyphens, and underscores. The PostgreSQL container publishes port `5432` to an
automatically allocated loopback port. Spring Boot discovers that mapped port
through its Docker Compose service connection, so concurrent tasks do not
share a fixed host port or database volume.

The first application start creates and starts the task's Compose resources.
Later application reruns issue an idempotent Compose start and reuse the same
running PostgreSQL container, network, and named database volume. Stopping the
application does not stop those task resources. To inspect the allocated host
port, use the project name derived from the resolved instance identifier:

```powershell
docker compose --project-name gam-api-<instance-id> port postgres 5432
```

When the task or worktree is finished, explicitly delete its retained database:

```powershell
java scripts/RemoveDevelopmentEnvironment.java
```

The helper resolves the instance identifier using the same precedence, prints
the exact Compose project it will delete, and requires interactive confirmation.
When running it outside the original task environment, pass the instance
identifier as its sole argument. Do not retire an environment that another
application process is using.

## Stable manifest

The callback owns fixed UUIDv7 identities and explicit projection ranges:

| Group | Stable manifest identity |
| --- | --- |
| Named Accounts | `01950000-0001-7000-8000-000000000001` through `...0013` |
| Scale Accounts | `01950000-0001-7100-8000-` plus the zero-padded ordinal `000000000001` through `000000000060` |
| Custom Roles | `01950000-0002-7000-8000-000000000001` and `...0002` |
| Named Account-Role links | `01950000-0003-7000-8000-000000000001` through `...0010` |
| Scale Account-Role links | `01950000-0003-7100-8000-` plus the scale ordinal |
| Named Members | `01950000-0004-7000-8000-000000000001` through `...0005` |
| Scale Members | `01950000-0004-7100-8000-` plus the scale ordinal |
| Ordinary GamLocations | `01950000-0005-7000-8000-000000000001` through `...0005` |
| Generic Events | `01950000-0006-7000-8000-000000000001` through `...0014` |
| Oratorio Events and occurrences | `01950000-0006-7100-8000-000000000001` through `...0009` |
| Oratorianos | explicit `01950000-0008-7000-*` workflow targets and the `01950000-0008-7100-*` scale range |
| Solicitation, attendance, form, snapshot, and attachment relationships | Explicit `01950000-0009-*` through `01950000-000f-*` UUIDv7 values in callback dependency order |

The twelve authentication personas and their accepted Role projections are:

| Email | Initial active Roles |
| --- | --- |
| `dev.sudo@example.com` | `SUDO` |
| `mariana.coord@example.com` | `MEMBER`, `COORD` |
| `rafael.coord.sandbox@example.com` | `MEMBER`, `COORD` |
| `camila.oratorio@example.com` | `MEMBER`, `ORATORIO_COORD` |
| `lucas.member@example.com` | `MEMBER` |
| `helena.inactive@example.com` | `VISITOR` |
| `beatriz.registration@example.com` | None |
| `fernanda.solicitation@example.com` | None |
| `joao.approval@example.com` | None |
| `aline.rejection@example.com` | None |
| `paulo.custom-role@example.com` | None |
| `renata.custom-role@example.com` | `EVENT_SUPPORT` |

The callback also owns deterministic Member and Oratoriano scale records,
ordinary location targets, solicitation histories, Generic Event and Oratorio
lifecycle targets, team and attendance projections, additional-form
lifecycles, print snapshots, and one visibly synthetic valid PDF.

## Reconciliation and maintenance

The callback validates all required system Roles, Permissions, and the
production-safe Oratorio GamLocation before changing fixture rows. It resolves
the current `DBSM` system catalog row by code, `system_managed`, and
`catalog_current`, never through its mutable name or a legacy fixed UUID. A
manifest UUID or canonical key collision fails the complete callback
transaction; do not change the callback to adopt a similarly named local row.

On each successful development migration:

- manifest records are created or restored under their original UUIDs;
- accepted fields, relative dates, lifecycle states, and owned relationships
  are converged;
- manually consumed fixture projections are restored;
- unrelated UUIDs, relationships, and activity entries are preserved; and
- already converged rows retain audit timestamps and binary bytes.

Keep the callback in database dependency order. When an endpoint, Role,
Permission, lifecycle, schema constraint, password encoder, manifest identity,
relative-date rule, or sacrificial workflow changes, update together:

1. the owning Accepted Requirement Specification;
2. the fixture policy and endpoint-readiness matrix;
3. this manifest documentation;
4. `db/dev-migration/afterMigrate.sql`; and
5. focused fixture verification.

Never repurpose a published fixture UUID or email. Add a new identity and
retire the old concept explicitly. Production-safe system Roles, Permissions,
Role bundles, and system GamLocations remain owned by their Java repeatable
migrations and must not be copied into this callback.
