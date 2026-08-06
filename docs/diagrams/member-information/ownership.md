# Member Information Ownership

This diagram supports the accepted Member Information and Member Information
Import and Account Linking Requirement Specifications. It does not supersede
their written rules.

```mermaid
flowchart LR
    Member["Member aggregate root"]
    Core["Core profile"]
    Entry["GAM entry date"]
    Diet["Dietary restriction"]
    Experience["Experiences"]
    Sacrament["Sacraments"]
    Contribution["Contribution profile"]

    Annual["Annual Member Information Response"]
    Occupation["Occupation"]
    SurveyAnswers["Protected annual answers"]

    Account["Account aggregate root"]
    Solicitation["Membership Solicitation aggregate root"]
    Presence["Presence aggregate root"]
    Batch["Member Information Import Batch"]

    Member --> Core
    Member --> Entry
    Member --> Diet
    Member --> Experience
    Member --> Sacrament
    Member --> Contribution

    Annual -->|"references exactly one"| Member
    Annual --> Occupation
    Annual --> SurveyAnswers

    Member -.->|"optional until explicit link"| Account
    Solicitation --> Account
    Presence --> Member
    Batch -->|"internal provenance"| Member
    Batch -->|"internal provenance"| Annual
```
