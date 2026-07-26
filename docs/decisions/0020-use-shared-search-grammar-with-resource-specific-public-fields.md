# ADR-0020: Use shared search grammar with resource-specific public fields

## Status

Accepted

## Context

GAM exposes structured search for resources with different domain data,
visibility rules, relationships, derived values, and normalization needs.

Clients benefit from one predictable filter request shape and comparison-method
vocabulary. A completely generic persistence-path API would reduce repetitive
backend code, but it would expose internal structure, make accidental fields
public, and couple clients to joins and entity refactors. Fully bespoke request
shapes would keep each resource independent but would duplicate grammar,
validation, OpenAPI schemas, and frontend integration.

The architecture therefore needs a stable boundary between shared transport
behavior and resource-owned product behavior.

## Decision

GAM shall use one shared structured-search grammar and closed comparison-method
catalog.

Each searchable resource shall own an explicit catalog of canonical public
filter fields. Every field definition shall declare its allowed methods, value
type, normalization, and product meaning in an accepted Requirement
Specification.

A public filter field may map to a direct property, relationship, canonical
value, derived state, full-name representation, or custom predicate. Internal
persistence paths and joins shall remain hidden and shall not be accepted as
public filter names.

Separate caller filters shall combine with logical `AND`. A resource may use
internal compound predicates, including `OR`, to implement one documented
public field without exposing general predicate-tree composition to clients.

## Alternatives considered

### Option 1: Accept arbitrary persistence paths

Pros:

- Requires little resource-specific mapping.
- Makes new persisted fields searchable with minimal code.

Cons:

- Exposes entity topology, joins, and internal names.
- Couples clients to persistence refactors.
- Makes authorization and sensitive-field allowlisting harder to review.
- Allows implementation changes to expand the public API accidentally.

### Option 2: Define a bespoke search grammar for every resource

Pros:

- Gives each resource complete local freedom.
- Avoids a shared abstraction.

Cons:

- Duplicates request shapes, comparison names, validation, errors, and
  OpenAPI schemas.
- Forces frontend clients to learn unrelated filter protocols.
- Encourages inconsistent empty-search and invalid-filter behavior.

### Option 3: Use one shared grammar and one universal field catalog

Pros:

- Maximizes apparent consistency.
- Makes generic client tooling straightforward.

Cons:

- Assumes unrelated resources share fields and normalization.
- Blurs authorization and lifecycle boundaries.
- Prevents resource Requirement Specifications from remaining authoritative
  for their own product vocabulary.

### Option 4: Use shared grammar with resource-specific public fields

Pros:

- Gives clients one stable structural protocol.
- Keeps public-field and value semantics explicit and reviewable.
- Hides persistence structure and supports derived or cross-field concepts.
- Allows resource visibility rules to remain authoritative.

Cons:

- Every searchable resource must maintain an explicit field mapping.
- Adding or changing a field requires coordinated requirement, OpenAPI, test,
  and implementation work.
- Shared and resource-specific validation boundaries must remain clearly
  separated.

## Consequences

Positive consequences:

- Search request structure and semantic errors are consistent.
- Persistence refactors need not rename public fields.
- Sensitive or internal fields remain unavailable unless explicitly accepted.
- Derived concepts such as effective Event status and human-equivalent
  Oratoriano name remain possible behind stable public names.
- Agent T and Agent D can derive behavior without treating current converters
  as requirements.

Negative consequences:

- Resource specifications require more detailed field tables.
- Generic tooling cannot assume that one public field exists everywhere.
- A shared DTO change such as correcting `comparationMethod` to
  `comparisonMethod` affects every structured-search endpoint.

## Related requirements

- [Search and Filter Framework](../requirements/platform/search-and-filter-framework.md)
- `REQ-SEARCH-001`
- `REQ-SEARCH-003`
- `REQ-SEARCH-008`

## Related videos

* None.
