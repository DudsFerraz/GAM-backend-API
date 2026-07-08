# Software Testing Guidelines

This document defines the architecture, strategies, and heuristics for creating automated tests within the project. The goal is to ensure a robust, production-ready system by employing a clear methodological approach to quality engineering.

## 1. Philosophy and Testing Architecture (Context)

### 1.1. Adapted TDD Approach

Throughout development, we apply core Test-Driven Development (TDD) concepts. While we do not need to follow the methodology strictly "by the book," the central concept of writing tests before code is essential to ensure a robust system.

The strict TDD cycle—write a failing test (Red), write the minimum code to pass it (Green), and safely improve the structure (Refactor)—must be adapted to: **write a robust suite of failing tests, then write high-caliber code to fulfill the behaviors established by the suite.**

Given the current lack of tests in the repository, our strategy for generating tests must first focus on verifying that existing features operate correctly.

### 1.2. The Testing Pyramid

It is absolutely necessary to implement both functional and structural testing techniques, divided into unit tests and integration tests. The testing pyramid must be strictly adopted. This is a software development framework that balances testing efforts by grouping automated tests into three main layers:

* A wide base of fast, cheap unit tests.
* A middle layer of integration tests.
* A small peak of slower, end-to-end (E2E) tests.

### 1.3. Testing Heuristics

In addition to tests established by strict criteria, fundamental heuristics must be applied to choose the most relevant test cases for the application:

* Exercise all possible error outputs, including exceptions.
* Provoke buffer or array overflows.
* Test the same action multiple times.
* Test blank strings.
* **Note:** Additional test cases beyond the minimum required by the criteria can be created if there is sufficient time and resources. Business rules and the test execution context are the determining factors for choosing what, how, and until when to test.

### 1.4. Test Preservation

Agents must not delete, disable, skip, or weaken tests to obtain a green build. A failing test is evidence to investigate: fix the production code, test fixture, environment setup, or documented requirement mismatch while preserving the intended behavior under test.

Deleting or materially weakening a test is allowed only after explicit developer approval. If a test appears obsolete, duplicated, incorrect, or incompatible with the accepted requirements, report the rationale and wait for approval before changing or removing it.

---

## 2. Test Derivation Strategies (How to Think)

This section defines *how* test cases should be designed, regardless of the execution level.

### 2.1. Behavior-Focused Tests and Test Seams

Tests must verify behavior through an appropriate boundary, not through private implementation details. Functional and integration tests derive their cases from requirements and API contracts. Structural tests may derive their cases from source-code decisions, conditions, and loops, but they should still execute those paths through the narrowest meaningful public or intentionally exposed seam. A good test reads like a small executable specification: it names the behavior that must exist, uses inputs that make the scenario clear, and asserts an observable result.

A test seam is the boundary where the test interacts with the system. It is the place where behavior can be stimulated and observed without reaching inside unrelated internals. In this project, common seams include:

* A domain model or value object public method when testing domain invariants, normalization, or state transitions.
* An application use case public method when testing a workflow that coordinates domain objects, loaders, repositories, or policies.
* A repository or persistence adapter when testing persistence behavior, soft-delete visibility, database constraints, or specifications.
* An HTTP endpoint when testing API contracts, authentication, authorization, request validation, response bodies, and status codes.
* A dedicated external-infrastructure boundary when testing integration with a provider such as email, payment, storage, time, randomness, or the file system.

Choose the narrowest seam that still protects the requirement behavior. Do not test every layer for the same rule unless each layer has a distinct contract to protect. If the correct seam is unclear, derive it from the accepted requirement, ADR, OpenAPI contract, package guidelines, and risk level. Ask for clarification when those sources do not identify a clear boundary.

Good behavior-focused tests have these traits:

* They exercise behavior that a caller, API client, maintainer, or domain expert would care about.
* They use public APIs for the selected seam.
* They survive internal refactors when the behavior remains unchanged.
* Their names describe what behavior is protected, not how the implementation works.
* Their expected values come from a requirement, a worked example, a known literal, or another independent source of truth.

Structural tests have one extra nuance: their case selection is allowed to be implementation-aware, but their execution should not reach into private methods. If a private method contains enough branching complexity that direct testing feels necessary, prefer extracting that behavior into a domain policy, value object, specification, parser, validator, converter, or other focused component with its own seam. Package-private seams are acceptable only when the component is deliberately internal and the test protects a meaningful unit contract.

Avoid these test anti-patterns:

* **Implementation-coupled tests:** Tests that mock internal collaborators, test private methods, assert internal call counts or ordering, or verify behavior through an unrelated side channel. These tests often fail during harmless refactors while missing real behavior regressions.
* **Tautological tests:** Tests whose expected value is recomputed using the same logic as the production code, such as calculating the expected total with the same loop or formula being tested. Expected values must be independent enough to disagree with the implementation when the implementation is wrong.

Examples:

```java
// Good: verifies observable behavior through the selected public seam.
var total = order.totalFor(List.of(
        new OrderLine("Notebook", BigDecimal.valueOf(10)),
        new OrderLine("Pen", BigDecimal.valueOf(5))
));

assertThat(total).isEqualByComparingTo("15");
```

```java
// Bad: tautological expected value repeats the same computation style.
var lines = List.of(
        new OrderLine("Notebook", BigDecimal.valueOf(10)),
        new OrderLine("Pen", BigDecimal.valueOf(5))
);
var expected = lines.stream()
        .map(OrderLine::amount)
        .reduce(BigDecimal.ZERO, BigDecimal::add);

assertThat(order.totalFor(lines)).isEqualByComparingTo(expected);
```

```java
// Usually bad for application behavior: asserts internal collaboration
// instead of the externally observable result of the use case.
verify(paymentGateway).charge(order.total());
```

```java
// Better: assert the outcome the caller cares about.
assertThat(result.status()).isEqualTo(OrderStatus.CONFIRMED);
assertThat(result.paymentStatus()).isEqualTo(PaymentStatus.APPROVED);
```

### 2.2. Mocking and Test Doubles

Use mocks and test doubles to isolate true system boundaries, not to duplicate the application's internal structure inside tests.

Mock or fake external boundaries when using the real dependency would make the test slow, flaky, unsafe, unavailable, or focused on another system:

* External APIs and provider SDKs.
* Email, payment, storage, messaging, or notification providers.
* Time and randomness.
* File system access when the file system behavior itself is not under test.
* Databases only when the selected test seam is a pure unit seam. Prefer a real test database for persistence, API, security, and migration-sensitive behavior.

Do not mock:

* The domain model under test.
* Internal application classes merely because they are collaborators.
* Code owned by this project when a narrower public seam or a realistic fake would express the behavior more clearly.
* Repositories in tests whose purpose is to verify persistence behavior, soft-delete behavior, constraints, specifications, or transactional integration.

When an external dependency must be mocked, design the boundary intentionally:

* Inject the dependency instead of constructing provider clients directly inside business logic.
* Prefer specific provider-facing operations over a generic request function that forces conditional logic into test setup.
* Keep mock behavior simple and scenario-specific.

### 2.3. Functional Testing Approach (Black-Box)

Functional tests are based on the specification, though nothing prevents analyzing the code. The process must be iterative, not sequential. The Equivalence Partitioning and Boundary Value Analysis criteria must be used alongside JUnit, AssertJ, and Mockito.

1. **Requirements Comprehension:** Carefully read the requirements to understand how the program works, its inputs and outputs, boundary conditions, data types, etc. (If the tester does not understand how the program works, they cannot evaluate if it functions properly). If there is any doubt about the requirement the LLM model should ask the user about it, rather than make assumptions.
2. **Seam Selection:** Select the narrowest public seam that protects the requirement behavior and risk being tested. If the seam is unclear, ask for clarification before writing tests.
3. **Exploratory Analysis:** Execute the program with different input and output values to improve your understanding of what it does.
4. **Partition Identification:** Investigate inputs and outputs to map equivalence classes. Analyze each input individually, potential interactions between inputs, and all possible outputs.
5. **Boundary Analysis:** Analyze each equivalence class to identify the boundaries that lead to changes in the program's behavior.
6. **Test Case Creation:** Combine equivalence classes to create test cases. To avoid an explosion in the number of tests, combine only valid classes and test invalid classes only once. (If the number of combinations is too large, divide the tested module).
7. **Domain Contract Gate:** Before implementing or accepting a test suite, explicitly verify that the suite describes the intended domain behavior, not only the behavior already present in the code. If the specification is incomplete, ambiguous, or missing important constraints such as limits, allowed formats, normalization, error messages, or rejection rules, pause and ask for clarification. Do not proceed with tests that merely preserve an under-specified implementation.
8. **Test Automation:** Implement the test cases using a testing framework, preferring realistic values for input data even if they are not actively used in the test. Dedicate more attention to testing modules that have a higher cost of failure.
9. **Test Suite Expansion:** Revisit the created tests and add new tests if deemed necessary.

Before considering a functional suite complete, review it with the following gate questions:

* Does each test case cover a distinct behavior, equivalence class, boundary, output, or failure mode? Remove cases that only repeat the same behavioral signal with different example data.
* Does the suite maximize behavior variance with the minimum practical number of test cases? Prefer one representative valid case per equivalence class, plus explicit boundary and invalid cases.
* Does each test use an appropriate public seam for the behavior under test?
* Would the test survive an internal refactor that preserves the behavior?
* Are boundary fixtures derived in a way that makes the boundary obvious? Use `@MethodSource` for generated values such as `"a".repeat(32)` when inline literals would be hard to count or easy to misread.
* Is test data human-readable? Prefer direct literals such as `"Á"` over Unicode escapes such as `"\u00C1"` unless the escape itself is the behavior under test.
* Are normalization tests justified by the domain contract? Only test Unicode normalization, separator equivalence, trimming, or canonicalization when normalization is explicitly required before saving or comparing values.
* Are tests named and grouped by the behavior being protected, not by incidental implementation details?

### 2.4. Structural Testing Approach (White-Box)

The structural testing technique uses the program's source code to derive test cases and acts as a complement to the functional technique. The main focus will be the **Condition/Decision** criterion.

Structural tests are implementation-aware during design, but they should remain behavior-observing during execution. They may be selected because a condition exists in the code, yet they should normally exercise that condition through the same public or intentionally exposed seam a real caller uses. Do not test private methods directly as a shortcut around awkward design.

1. **Code and Specification Comprehension:** Analyze the unit's source code, combining this reading with knowledge about the program's specification to understand the implementation details of the algorithms.
2. **Decision and Condition Mapping:** Identify all decision commands. Break down compound commands to identify individual boolean conditions.
3. **Structural Seam Selection:** Select the narrowest meaningful seam that can exercise the mapped decisions. Prefer public domain/application methods, repository/specification APIs, controller endpoints, or deliberately package-private internal components. If only a private method can exercise the decision cleanly, consider extracting the behavior before writing the test.
4. **Test Requirements Definition:** Ensure that all individual conditions are exercised at least once with each possible value (true and false), and that the final results of the decisions are also exercised.
5. **Test Case Creation:** Derive the specific input data capable of forcing the program to follow the mapped execution flows.
6. **Automation and Coverage Analysis:** Automate the test cases. Use IDE coverage reports to verify adequacy.
7. **Refinement and Loop Analysis:** Apply loop boundary adequacy: ensure there are test cases that execute each loop zero times, exactly once, and more than once.

---

## 3. Test Execution Levels (Where and With What to Apply)

This section dictates the scope and the tools to be used during automation.

### 3.1. Unit Tests and Test Doubles

Focused on component isolation. Use structured test doubles if external dependencies exist, ensuring the module is tested in isolation:

* **Dummies:** Objects that are not actually used but are necessary to invoke methods. Generally, they are applied only to fill parameter lists.
* **Fakes:** Units that have simplified implementations for testing purposes and are not used in production (e.g., in-memory databases).
* **Stubs:** Units that provide predefined responses to calls made during the test, without possessing any business logic that could be used outside the test itself.
* **Mocks:** Pre-programmed units with expectations that specify the calls they should receive. Unlike Stubs, Mocks verify behavior.

### 3.2. Integration and API Tests (REST Assured)

Integration tests evaluate how different parts of the system interact with each other and with external dependencies.

1. **Remote Specification Analysis:** Use the API documentation (such as Swagger) as the primary specification of the endpoints to be tested, understanding the routes, expected HTTP methods, parameters, and payloads.
2. **State Configuration and Dependencies (Setup/Teardown):** In tests involving real persistence (databases), ensure the system starts in a known state and returns to its initial state after each test (using annotations like `@BeforeEach` and `@AfterEach`) to avoid interference between tests.
3. **Creation of Auxiliary Classes:** Create base classes (e.g., `BaseApiIntegrationTest`) to centralize repetitive configurations (ports, base URIs) and Builders (via Java Faker) to instantiate objects, reducing boilerplate code (fixture) in the tests.
4. **Authentication and Authorization:** For protected endpoints, simulate or execute the JWT token retrieval flow beforehand and inject it into the request Header via code.
5. **Request Execution (Given-When):** Use the REST Assured BDD syntax. Configure the `given()` block with headers, query params, content-type, and the body. Trigger the request in the `when()` block.
6. **Robust Verification (Then):** Do not solely validate the status code. Verify the full content of the responses, validating the payload (using extractions like `JsonPath`) and persistence rules.
7. **Test Scope Isolation:** Clearly mark integration tests with tags (e.g., `@Tag("ApiTest")`) so they can be separated from the fast unit test suite.

### 3.3. API/Security Integration Lessons

API and security integration tests must be self-contained. Required secrets, database configuration, ports, and fixture data must be provided by the test profile or test bootstrap, not by local developer machine state. A fixed local environment variable can be stable on one machine, but it is still outside the test boundary and can fail in CI, another developer environment, or a test runner that did not inherit the variable.

Centralize API test mechanics in a base support class. REST Assured base URI/port setup, JSON content negotiation, authentication helpers, fixture builders, and cleanup should live in shared test support so each test focuses on behavior.

When API tests write to a real database, cleanup must remove only known test-created records and must do so in dependency order. Prefer tracked fixture IDs for cleanup, and avoid teardown paths that rely on unrelated production behavior such as soft delete unless that behavior is the subject of the test.

Security tests must verify the actual authentication mechanism used by each endpoint. Bearer-token endpoints, refresh-cookie endpoints, and public endpoints have different contracts and must not be tested as though they all use the same security path.

Separate authentication failures from authorization failures. Unauthenticated requests should assert `401`, while authenticated users without the required permission should assert `403`.

API/security tests are allowed to reveal production defects. If a test exposes a runtime behavior mismatch, such as a repository delete path incompatible with the target entity, fix the production behavior instead of weakening the test.

After changing shared API test support, authentication, authorization, token behavior, or security configuration, run the full `mvn verify`. Focused API test runs are useful during iteration, but shared test infrastructure and auth changes can affect persistence, context loading, and other integration tests.

## 4. Test Organization and Architecture

### Custom Test Tags

Use custom JUnit annotations instead of raw `@Tag(...)`. Each annotation should be usable on both test classes and test methods. Required annotations are:

- `@UnitTest`
- `@FunctionalTest`
- `@StructuralTest`
- `@IntegrationTest`
- `@ApiTest`
- `@SecurityTest`
- `@PersistenceTest`

### Test Suites

1. Create one suite for each custom annotation. “Which kind of test do I want to run?”
2. Also create suites per module or functionality. “Which feature do I want to validate?”

### Display Names

Every test class should have @DisplayName. It must describe scenario and result and use prefixes when helpful. As it follows:

```java
@DisplayName("Unit - User Service")
@DisplayName("Functional - User Registration")
@DisplayName("API - Authentication Controller")
```
```java
@DisplayName("blank email -> validation error")
@DisplayName("valid credentials -> returns access token")
@DisplayName("unauthenticated request -> HTTP 401")
```
```java
@DisplayName("EP - blank name -> validation error")
@DisplayName("BVA - amount = 0.00 -> accepted")
```

### Nested Tests

Use @Nested to group scenarios inside a test class, only when they make the test easier to read. Good groups are: Valid inputs, Invalid inputs, Boundary values Authentication, Authorization, Creation, Update, Deletion, Error handling

When a test file contains more than one test type, such as functional and structural cases, keep the outer test class and filename neutral. Do not name the file `*FunctionalTest` if it also contains structural tests. Instead, create nested classes named by test type and annotate them with the matching custom annotation, such as `@FunctionalTest` or `@StructuralTest`. Only create a nested test-type class when it contains at least one test.

```java
@UnitTest
@DisplayName("Account Lookup Use Case")
class AccountLookupTest {

    @Nested
    @FunctionalTest
    @DisplayName("Functional")
    class Functional {
        // Functional tests
    }

    @Nested
    @StructuralTest
    @DisplayName("Structural")
    class Structural {
        // Structural tests
    }
}
```

### Parameterized Tests

Use parameterized tests when the same behavior must be checked with several values. Use:

- `@NullAndEmptySource` for null/empty strings
- `@ValueSource` for simple values
- `@CsvSource` for input/output tables
- `@MethodSource` for complex scenarios

```java
@ParameterizedTest
@NullAndEmptySource
@ValueSource(strings = {" ", "   ", "\t"})
@DisplayName("EP - invalid name -> validation error")
void invalidNameShouldReturnValidationError(String name) {}
```
