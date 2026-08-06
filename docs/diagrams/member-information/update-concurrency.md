# Member Information Update Concurrency

This diagram supports the accepted Member Information Requirement
Specification and ADR-0027. It illustrates the shared Member aggregate ETag
without replacing the written concurrency and error rules.

```mermaid
sequenceDiagram
    participant A as "Coordinator A"
    participant B as "Coordinator B"
    participant API as "Member API"
    participant Member as "Member aggregate"

    A->>API: "GET Member representation"
    API-->>A: "ETag member-41"
    B->>API: "GET Member representation"
    API-->>B: "ETag member-41"

    A->>API: "PUT with If-Match member-41"
    API->>Member: "Validate and commit change"
    Member-->>API: "Version becomes member-42"
    API-->>A: "204 with ETag member-42"

    B->>API: "PUT with If-Match member-41"
    API-->>B: "412 Precondition Failed"
```
