# WarSampo project requirements

These contracts justify the project-specific SHACL rules. They are intentionally separate from reusable RDF and SKOS constraints. A violation means the data demonstrably fails an accepted contract below; warnings identify review conditions rather than invalid RDF.

| Requirement | Contract and evidence | Severity |
| --- | --- | --- |
| `BirthPersonRequirement` | The WarSampo schema models `Birth` with CIDOC CRM `P98_brought_into_life`; the project profile requires one IRI value. | Violation |
| `DeathPersonRequirement` | The schema models `Death` with `P100_was_death_of`; the project profile requires one IRI value. | Violation |
| `UnitJoiningRequirement` | Schema comments define `P143_joined` as the joining unit and `P144_joined_with` as the receiving unit. Each role is singular and a unit cannot join itself. | Violation |
| `MedalAwardingRequirement` | Schema comments define one assigned medal and the participating recipient for each award event. | Violation |
| `ConflictClassRequirement` | `warsampo-schema.ttl` defines a local `Conflict` while `hasConflict` ranges over the conflicts-vocabulary class. Either may be intentional. | Warning |
| `LifeEventChronologyRequirement` | A latest possible birth date later than the earliest possible death date is chronologically impossible. | Violation |
| `InternalReferenceRequirement` | Internal `http://ldf.fi/warsa/` objects should have an outgoing description in the complete selected union. | Warning |
| `EventParticipantRequirement` | Actor IRIs used through `P11_had_participant` should resolve to a typed `Person` or `MilitaryUnit`. | Warning |

The source evidence is the ignored WarSampo checkout used for the case study, principally `warsampo/Warsampo-schema/warsampo-schema.ttl`. If a contract changes, update this document, its stable requirement resource in `shapes/warsampo/requirements/project-requirements.ttl`, the corresponding shapes, and fixtures together.
