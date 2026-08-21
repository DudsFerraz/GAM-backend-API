# Oratorio Module Domain

This diagram summarizes the planned module boundaries and relationships. Requirements remain authoritative for behavior and cardinality details.

```mermaid
classDiagram
    class Account
    class Member
    class OratorioCoordinatorDesignation {
        role ORATORIO_COORD
    }
    class Event {
        type ORATORIO
        begin 14:00
        end 17:00
    }
    class OratorioOccurrence {
        localDate
        lancheDescription
        gincanaDescription
        BoaTardeCriancasPlan
        BoaTardeJovensPlan
    }
    class OratorioTeamAssignment {
        teamType
    }
    class Presence {
        Member attendance
    }
    class Oratoriano {
        GamName
        birthDate?
        phoneNumber?
    }
    class OratorianoAttendance
    class AdditionalForm {
        version
        status
        origin
        signedOn
    }
    class SignedAttachment {
        private bytes
        digest
        pageOrder
    }

    Account "1" --> "0..1" Member : linked identity
    Member "1" --> "0..1" OratorioCoordinatorDesignation : active responsibility
    Event "1" *-- "1" OratorioOccurrence : shared UUID specialization
    OratorioOccurrence "1" *-- "0..*" OratorioTeamAssignment : four standard teams
    Member "1" --> "0..*" OratorioTeamAssignment : assigned
    Event "1" --> "0..*" Presence : Member attendance
    OratorioOccurrence "1" --> "0..*" OratorianoAttendance
    Oratoriano "1" --> "0..*" OratorianoAttendance
    Oratoriano "1" --> "0..*" AdditionalForm : immutable versions
    AdditionalForm "1" *-- "0..*" SignedAttachment : draft working files or completed evidence
```

## Related requirements

- [Oratorio Coordinator Designation](../requirements/oratorio/oratorio-coordinator-designation.md)
- [Oratorio Occurrences and Planning](../requirements/oratorio/oratorio-occurrences-and-planning.md)
- [Oratorio Attendance Tracker](../requirements/oratorio/oratorio-attendance-tracker.md)
- [Oratoriano Records](../requirements/oratorianos/oratoriano-records.md)
- [Oratoriano Additional Forms](../requirements/oratorianos/oratoriano-additional-forms.md)
- [Draft Signed-Attachment Collection Management](../requirements/oratorianos/incremental-signed-attachment-uploads.md)

## Related ADRs

- [ADR-0034: Treat signed attachments as transient until form completion](../decisions/0034-treat-signed-attachments-as-transient-until-form-completion.md)
