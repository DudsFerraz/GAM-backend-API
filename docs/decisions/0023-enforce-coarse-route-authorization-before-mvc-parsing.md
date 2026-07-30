# ADR-0023: Enforce coarse route authorization before MVC parsing

## Status

Accepted

## Context

`REQ-API-ERROR-007` requires protected requests to pass authentication and
coarse route authorization before request parameters or bodies are parsed and
validated or target resources are loaded.

Controller-method `@PreAuthorize` checks cannot guarantee that precedence.
Spring MVC resolves and converts handler arguments and may parse and validate a
request body before it invokes the controller method. A method-security
interceptor can therefore be reached after a malformed or invalid request has
already produced a `400` response.

The API also has authorization decisions that cannot be made from only the HTTP
method, route pattern, authentication, and current permission authorities.
Self-view rules, resource visibility, ownership, lifecycle state, and
target-specific authority require parsed public identifiers and application
lookups. Those decisions must preserve the Accepted distinction between a
missing or hidden target, a visible target lacking authority, and a permitted
operation blocked by a business or security invariant.

ADR-0007 separately defines the layered CSRF and canonical-origin proof used by
the supported browser session. This decision must preserve that protection
while placing its failure outcome after authentication and coarse route
authorization as required by `REQ-API-ERROR-007`.

## Decision

Enforce authentication and coarse method/path authorization in the Spring
Security filter chain before Spring MVC request parsing.

The pre-MVC policy shall use request matchers or a custom
`AuthorizationManager` and shall:

- identify an operation from its HTTP method and public route pattern;
- use only authentication state and current permission authorities;
- avoid parsing request parameters or bodies;
- avoid converting path variables to application types;
- avoid loading a target resource; and
- be authoritative for protected-route `401 AUTHENTICATION_REQUIRED` and
  coarse-permission `403 ACCESS_DENIED` precedence.

The filter chain shall evaluate the common pre-MVC gates in this order:

1. valid authentication for a protected operation;
2. coarse method/path authorization; and
3. required CSRF or canonical-origin proof.

Only after the applicable pre-MVC gates pass may the request enter Spring MVC
for transport conversion, body parsing, and input validation. Public operations
skip only the gates that their Accepted contract does not require.

A coarse policy may deliberately admit more than one target-specific path to
success. For example, a direct read that supports either a permission or an
authenticated self-view shall not require the permission at the coarse layer
when doing so would make the self-view unreachable. The application-layer
policy remains responsible for the resolved target-specific alternative.

After parsing and validation, application-layer policies shall:

1. load the referenced target through the visibility rules of the owning
   Accepted Requirement Specification;
2. return the common indistinguishable `404 RESOURCE_NOT_FOUND` outcome when
   the target is absent, soft-deleted, or deliberately hidden;
3. evaluate target-specific authorization only for a visible target;
4. return `403 ACCESS_DENIED` when the visible target-specific operation is not
   authorized; and
5. evaluate business and security invariants only after target-specific
   authorization, returning the owning Accepted conflict or
   `403 FORBIDDEN_OPERATION` outcome.

Controller `@PreAuthorize` checks may be retained only as defense in depth.
They shall not be the sole enforcement of coarse route authorization, shall not
replace application-layer target policies, and shall not be stricter than or
semantically diverge from the authoritative pre-MVC policy.

Authentication-entry-point, access-denied, and request-security handlers shall
produce the common GAM error envelope, codes, safe details, headers, and
non-cacheable transport required by `REQ-API-ERROR-001`,
`REQ-API-ERROR-008`, `REQ-API-ERROR-009`, and `REQ-API-ERROR-012`.

The method/path policy and every retained defense-in-depth annotation shall be
maintained with the endpoint's documented authorization contract. Automated
coverage shall verify precedence with malformed and invalid inputs and with
targets that may be absent, hidden, visible, or forbidden.

## Alternatives considered

### Option 1: Use controller `@PreAuthorize` as the authoritative route policy

Pros:

- Keeps permission declarations next to controller methods.
- Uses the existing method-security mechanism.

Cons:

- Runs too late to guarantee authorization before MVC argument conversion,
  body parsing, and validation.
- Can disclose validation behavior to a caller that cannot invoke the
  operation.
- Cannot by itself preserve the Accepted `401` and coarse `403` precedence.

### Option 2: Use pre-MVC coarse authorization and application-layer target policies

Pros:

- Guarantees authentication and coarse authorization before parsing and
  lookup.
- Preserves hidden-resource and self-view behavior after safe parsing.
- Keeps target loading and domain relationships out of the security filter
  chain.
- Aligns each authorization layer with the information it can safely use.

Cons:

- Requires a maintained method/path authorization policy.
- Retained controller annotations can drift unless coverage checks their
  consistency.
- Requires deliberate Spring Security filter ordering for request-security
  proof.

### Option 3: Parse request data or load targets in the security filter chain

Pros:

- Could make target-specific decisions before MVC.
- Could centralize more authorization outcomes in Spring Security.

Cons:

- Duplicates request parsing, validation, and application lookup behavior.
- Risks consuming or buffering request bodies before MVC.
- Couples the filter chain to resource models and persistence.
- Conflicts with the Accepted definition of coarse route authorization.

### Option 4: Perform all authorization in the application layer

Pros:

- Keeps authorization near use cases and domain relationships.
- Avoids a separate coarse route policy.

Cons:

- Requires parsing and validation before even coarse permission rejection.
- Cannot guarantee the Accepted non-disclosure precedence.
- Mixes operation-entry authorization with target-specific policy.

## Consequences

Positive consequences:

- Unauthenticated and coarse-forbidden callers cannot probe request validation
  or target existence.
- Self-view, ownership, visibility, and lifecycle rules remain in the
  application layer where parsed identifiers and targets are available.
- `401`, `403`, and `404` responsibilities are explicit and testable.
- Controller method security remains available as an additional guard without
  defining public precedence.

Negative consequences:

- Every protected operation needs an accurate method/path coarse policy.
- Route changes must update security configuration, OpenAPI authorization
  documentation, and precedence coverage together.
- Duplicate controller annotations create a consistency obligation.
- The request-security filters must be ordered deliberately rather than relying
  on incidental framework defaults.

## Related requirements

- [API Error and Authorization Contract](../requirements/platform/api-error-and-authorization-contract.md)
- `REQ-API-ERROR-001`
- `REQ-API-ERROR-007`
- `REQ-API-ERROR-008`
- `REQ-API-ERROR-009`
- `REQ-API-ERROR-010`
- `REQ-API-ERROR-012`
- `REQ-OPENAPI-003`

## Related diagrams

- [Common API error and authorization flow](../requirements/platform/api-error-and-authorization-contract.md#diagrams)
- [Post-authorization structured-search subflow](../requirements/platform/search-and-filter-framework.md#diagrams)
- [RBAC catalog flow](../requirements/rbac/rbac-catalog.md#diagrams)

## Related videos

- None.
