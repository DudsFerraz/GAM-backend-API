# Member Information Import and Account Linking

This diagram supports the accepted Member Information Import and Account
Linking Requirement Specification and ADR-0026. It does not supersede their
written rules.

```mermaid
flowchart TD
    Source["Private reviewed dataset"] --> Approved{"Status APPROVED and checksum valid?"}
    Approved -- "No" --> Reject["Reject safely with no writes"]
    Approved -- "Yes" --> Action{"Maintenance action"}

    Action -- "validate" --> Validate["Run complete input and database checks"]
    Validate --> ExitValidation["Exit with no rows or activity"]

    Action -- "apply" --> Intent{"Trusted Developer reference and reason present?"}
    Intent -- "No" --> Reject
    Intent -- "Yes" --> Collision{"Complete batch is new, a no-op, or conflicting?"}
    Collision -- "Conflict or partial state" --> Reject
    Collision -- "Complete existing batch" --> NoOp["Successful no-op"]
    Collision -- "New and eligible" --> Transaction["One atomic transaction"]

    Transaction --> BatchRow["Persist import batch"]
    Transaction --> Members["Create Account-less Members and owned profiles"]
    Transaction --> Responses["Create immutable annual responses"]
    Transaction --> ImportActivity["Create MEMBER_INFORMATION_IMPORTED activity"]

    Link["PATCH /members/{memberId}/account/link"]
    Members --> Later["Later ordinary Account registration"]
    Later --> Pending{"Pending Membership Solicitation?"}
    Pending -- "Yes" --> HumanDecision["Human Coordinator rejects confirmed mistaken solicitation"]
    Pending -- "No" --> Link
    HumanDecision --> Link

    Link --> Eligible{"Member and Account eligible with valid lifecycle projection?"}
    Eligible -- "No" --> Conflict["409 Conflict with no repair"]
    Eligible -- "Yes" --> LinkTransaction["Atomically persist immutable link, project MEMBER or VISITOR, and audit"]
    LinkTransaction --> SeparateRole["Coordinator designation remains a separate later workflow"]
```
