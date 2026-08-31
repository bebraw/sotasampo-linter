# ADRs

This directory stores Architecture Decision Records for decisions that are significant enough to shape future work in the repository.

ADRs are grouped by lifecycle status:

- `proposed/` stores draft decisions that still require review.
- `accepted/` stores approved decisions whose implementation is incomplete.
- `implemented/` stores decisions after the repository implements them.

## Proposed ADRs

No ADRs are currently proposed.

## Accepted ADRs

No accepted ADRs are currently pending implementation.

## Implemented ADRs

| ADR | Status | Summary |
| --- | --- | --- |
| [ADR-001](./implemented/ADR-001-adopt-shacl-for-linked-data-linting-and-repair.md) | Implemented | Use SHACL, Apache Jena, and guarded SPARQL Update repairs for reusable linked-data linting. |

## Lifecycle

1. Add a proposed record under `proposed/` using the next sequential ID.
2. Move it to `accepted/` after the decision has been approved.
3. Move it to `implemented/` only after the repository implements and verifies the decision.
4. Keep superseded records for historical context and link them to the replacing ADR.
5. Update this index whenever a record changes lifecycle state.
