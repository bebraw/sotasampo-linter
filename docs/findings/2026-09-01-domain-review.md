# WarSampo domain-review packet

**Prepared:** 2026-09-01  
**Purpose:** obtain explicit data-model decisions before changing non-mechanical findings

## Decision 1: may one unit-joining event contain several joining units?

The current project rule requires exactly one `crm:P143_joined` value. It produces 19 module-local results representing 11 unique events; eight events are repeated in both `unit_data.ttl` and `unit_data_joinings.ttl`.

| Event | Joining units | Present in | Review note |
| --- | ---: | --- | --- |
| `warsa:actors/joining_6198` | 4 | `unit_data_joinings.ttl` | Group composition |
| `warsa:events/joining_1080` | 2 | both unit modules | Duplicate module representation |
| `warsa:events/joining_12853` | 5 | `unit_data_joinings.ttl` | Er.P 4 composition |
| `warsa:events/joining_2236` | 6 | both unit modules | Duplicate module representation |
| `warsa:events/joining_2249` | 5 | both unit modules | Duplicate module representation |
| `warsa:events/joining_436` | 4 | both unit modules | Duplicate module representation |
| `warsa:events/joining_457` | 2 | both unit modules | Duplicate module representation |
| `warsa:events/joining_458` | 2 | both unit modules | Duplicate module representation |
| `warsa:events/joining_460` | 2 | both unit modules | Duplicate module representation |
| `warsa:events/joining_463` | 3 | both unit modules | Duplicate module representation |
| `warsa:events/joining_erp2` | 9 | `unit_data_joinings.ttl` | Existing note says the event may need splitting by time |

Requested decision:

- **Keep max-count 1:** split each group into atomic joining events and synchronize duplicated modules.
- **Allow grouped joins:** remove the max-count constraint and retain only presence, node-kind, and self-joining checks.
- **Conditional rule:** allow multiple values only when the event explicitly represents a composition and has sufficient source/time metadata.

Recommendation: treat these as a modeling-contract review, not 19 automatic data errors. The existing `joining_erp2` note is evidence that atomic events were intended in at least one part of the pipeline, while the repeated group patterns show that the current data also deliberately models compositions.

**Requested owner:** WarSampo actor/unit data model maintainer  
**Decision:** _pending_

## Decision 2: may one death event describe several people?

The current rule requires exactly one `crm:P100_was_death_of` value. Both findings appear intentional from their descriptions:

| Event | People | Description summary |
| --- | ---: | --- |
| `warsa:events/event_628` | 3 | Toini Jännes, G. Palojärvi, and F. Aflecht died in the same partisan ambush |
| `warsa:events/event_710` | 2 | Einar Vihma and Karl Gösta Palkama died in the same attack |

Requested decision:

- **Atomic life events:** split each record into one death event per person while retaining a shared military-activity event.
- **Narrative/group events:** allow multiple `P100_was_death_of` values and remove the max-count constraint.

Recommendation: suspend treating these two results as definite violations until the event-model owner confirms atomic-event semantics. CIDOC CRM permits an event to involve multiple entities; the stricter rule must come from an explicit WarSampo contract.

**Requested owner:** WarSampo event/life-event model maintainer  
**Decision:** _pending_

## Decision 3: which Conflict class is canonical?

`warsampo-schema.ttl` defines `http://ldf.fi/schema/warsa/Conflict`, while `wacs:hasConflict` ranges over `http://ldf.fi/warsa/conflicts/Conflict`. The linter reports this as one warning and does not propose a repair.

Requested decision:

- use the schema-local class;
- use the conflicts-vocabulary class; or
- retain both with an explicit equivalence/mapping relationship and documented usage rules.

**Requested owner:** WarSampo schema maintainer  
**Decision:** _pending_

## How to record the outcome

For each decision, replace `_pending_` with the chosen option and rationale. Then update the matching stable requirement in `shapes/warsampo/requirements/project-requirements.ttl`, its SHACL rule, fixtures, and the reviewed baseline in the same change. No automatic repair should be added for these findings until those decisions are recorded.
