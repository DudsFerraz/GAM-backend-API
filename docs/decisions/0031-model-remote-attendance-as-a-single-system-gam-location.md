# ADR-0031: Model remote attendance as a single system GamLocation

## Status

Accepted

## Context

Some GAM Events, including meetings, may happen remotely without a physical
venue. The current Event model requires every Event to reference exactly one
`GamLocation`, while the current GamLocation model permits only reusable
physical places with required city, state, and country metadata.

The remote option has one shared meaning across Events. It does not identify a
provider, meeting room, URL, or independently managed online venue. Future
specialized Event workflows may allow both remote and physical locations, while
the existing Oratorio workflow remains restricted to its accepted physical
institutional locations.

## Decision

Represent remote attendance through exactly one current system-managed
`GamLocation` with stable code `REMOTE` and user-facing name `Remoto`.

The Remote GamLocation shall:

- retain one stable UUID under the system catalog lifecycle;
- contain `null` for street, city, state, postal code, country code, latitude,
  and longitude;
- contain no meeting URL or other provider-specific data;
- participate in ordinary GamLocation direct reads and listing; and
- remain protected from product update and removal operations.

Every Event shall continue to reference one required `gamLocationId`. Generic
Events may select the Remote GamLocation. Each specialized Event workflow owns
whether it permits remote, physical, or a narrower catalog of locations;
Oratorio continues to permit only `DBSM`, `DBA`, and `DBCA`.

Do not add a location-kind field. The stable `REMOTE` system code identifies
the sole non-physical GamLocation, and all other GamLocations remain physical.
User-managed create and update workflows therefore retain their physical
address requirements.

## Alternatives considered

### Option 1: Add a physical-or-remote kind to every GamLocation

Pros:

- Makes location category explicit on every record.
- Could support several independently managed remote locations later.

Cons:

- Models a variable category when the accepted domain contains exactly one
  remote option.
- Expands create, update, persistence, and API contracts for ordinary physical
  locations without a current business need.
- Permits invalid future states such as several user-managed remote locations.

### Option 2: Make Event location nullable and add a remote flag

Pros:

- Keeps GamLocation exclusively physical.
- Expresses remote attendance directly on Event.

Cons:

- Replaces the established invariant that every Event references one
  GamLocation.
- Creates separate Event shapes and filtering semantics for physical and
  remote Events.
- Requires every common and specialized Event workflow to coordinate two
  location fields.

### Option 3: Store placeholder physical address values on a Remote record

Pros:

- Avoids changing existing database nullability.
- Reuses the current representation without conditional fields.

Cons:

- Persists false city, state, and country data.
- Misrepresents remote attendance as a physical place.
- Exposes meaningless address values to clients and audit consumers.

### Option 4: Use one system-managed Remote GamLocation with absent address data

Pros:

- Preserves the required Event-to-GamLocation relationship.
- Gives clients one stable selectable UUID and catalog code.
- Avoids false address data and unnecessary meeting-provider scope.
- Allows future Event workflows to opt into remote attendance independently.

Cons:

- GamLocation address nullability becomes conditional for one protected system
  record.
- Catalog synchronization, persistence constraints, mapping, and OpenAPI must
  explicitly preserve the singleton exception.

## Consequences

Positive consequences:

- Remote Events use the same linking, embedding, filtering, and historical
  preservation mechanisms as physical Events.
- Clients discover `Remoto` through the existing GamLocation catalog.
- The domain contains no fake address and no unused type discriminator.
- Future specialized Event workflows can deliberately allow remote attendance.

Negative consequences:

- Physical address fields can no longer be universally non-null in the shared
  persistence and response representation.
- Validation must distinguish the code-owned Remote record from ordinary
  user-managed physical locations without allowing product requests to create
  other addressless locations.
- Oratorio configuration must explicitly reject `REMOTE` even though it is a
  current system GamLocation.

## Related requirements

- [GamLocation Records](../requirements/gam-locations/gam-location-records.md)
- `REQ-GAM-LOCATION-014`
- [System GamLocation Catalog](../requirements/gam-locations/system-gam-location-catalog.md)
- `REQ-GAM-LOCATION-CATALOG-001`
- `REQ-GAM-LOCATION-CATALOG-008`
- [Event Records and Generic Event Lifecycle](../requirements/events/event-records-and-generic-lifecycle.md)
- `REQ-EVENT-004`
- [Oratorio Occurrences and Planning](../requirements/oratorio/oratorio-occurrences-and-planning.md)
- `REQ-ORATORIO-002`

## Related diagrams

* None.

## Related videos

* None.
