# WarSampo linked-data linting: findings brief

**Snapshot:** 2026-09-01  
**Scope:** all 61 RDF modules, 13,873,089 source triples  
**Method:** 16 cumulative RDF, SKOS, and WarSampo SHACL rules executed with Apache Jena

## What the prototype demonstrates

The linter finds valid RDF that violates vocabulary or project contracts, distinguishes definite errors from review questions, produces stable regression results, and can generate guarded repairs without rewriting source files. The reviewed baseline contains 123 violation results across 11 files plus one project-modeling warning. These are validation results, not necessarily 123 distinct entities or edits.

| Finding family | Results | Example | Proposed disposition |
| --- | ---: | --- | --- |
| SKOS label-role overlap | 80 | `ammo:maataloustyomies` uses `"agricultural worker"@en` in more than one SKOS label role | Data-owner cleanup |
| Unit-joining cardinality | 19 | `warsa:events/joining_460` has multiple `crm:P143_joined` values | Domain-owner review |
| Standard-vocabulary misuse | 16 | `owl:same` is used instead of `owl:sameAs` | 15 automatic fixes; one manual review |
| Duplicate preferred-label language | 6 | `ammo:sahkoalan-oppilas` has multiple Finnish preferred labels | Data-owner cleanup |
| Death-event cardinality | 2 | `warsa:events/event_628` identifies multiple deceased people | Domain-owner review |
| Conflict-class namespace | 1 warning | WarSampo defines a local `Conflict`, while `hasConflict` ranges over the conflicts vocabulary class | Schema-owner decision |

The results are concentrated rather than corpus-wide: 79 of 123 violations occur in `warsampo/ammo-data/ammo.ttl`. The vocabulary category contains 15 unambiguous typo occurrences that already have exact guarded replacements:

- 7 × `rdfs:Property` → `rdf:Property`
- 3 × `skos:preflabel` → `skos:prefLabel`
- 2 × `rdfs:subClassof` → `rdfs:subClassOf`
- 2 × `dct:bibliographiccitation` → `dct:bibliographicCitation`
- 1 × `owl:same` → `owl:sameAs`

One additional vocabulary result uses the DCMI namespace resource itself as a class; its intended replacement is ambiguous and is not automated.

## Source defects found by strict parsing

`warsampo/warsa-event-data/times.ttl` contained two invalid calendar dates: `1939-12-35` and `1940-02-30`. The [reviewable correction proposal](../../proposals/source-corrections/2026-09-01-invalid-dates/README.md) repoints the corroborated Parissavaara event to December 22–23 and marks the historically unresolved February start as unknown rather than guessing a date. With the proposal applied locally, all 61 modules now pass strict parsing and contribute to the baseline.

This demonstrates a separate class of error from SHACL findings: malformed datatype content prevents trustworthy graph validation. The linter fails closed and will not publish a partial report or baseline when parsing is incomplete.

## What combining all modules reveals

The completed disk-backed union audit adds 1,643 violations and 379 warnings that cannot be seen reliably one file at a time:

| Cross-module finding | Results | Main pattern |
| --- | ---: | --- |
| Multiple preferred labels per language | 1,643 violations | 806 birth and 806 death event IRIs receive different name-order labels from actor and casualty exports; 31 person or naming resources also conflict. |
| Unresolved internal references | 334 warnings | Primarily 183 unit-category and 119 actor IRIs without an outgoing description. |
| Untyped event participants | 45 warnings | 32 `actor_*` and 13 `person_*` aliases do not resolve to the required direct type. |

No new duplicate `dct:identifier` values, life-event chronology errors, SKOS cycles, hierarchy/relation conflicts, reflexive links, or cross-file label-role overlaps were found. The audit now completes in 6m30s with a 2.37 GiB sampled heap peak; its exact timings and examples are in the [cross-module audit record](../baselines/2026-09-01-warsampo-union.md).

## Suggested five-minute demonstration

```sh
# Show five real vocabulary findings in a small source file.
./scripts/linter.sh validate \
  --data warsampo/ammo-data/sources.ttl \
  --profile warsampo

# Generate the complete 15-change repair proposal without touching source data.
./scripts/linter.sh repair \
  --data warsampo/ammo-data/ammo_schema.ttl \
  --data warsampo/ammo-data/sources.ttl \
  --data warsampo/warsa-actor-data/medals/medal_types.ttl \
  --profile core \
  --dry-run \
  --patch reports/standard-term-repairs.ru \
  --provenance reports/standard-term-repairs.ttl
```

The repair engine checks the applicable validation profile, requires a corresponding finding, verifies the exact executed graph delta, rejects new violations, and records provenance. Apply mode publishes validated copies to a new directory; it never silently edits the source checkout.

## Decisions requested from colleagues

1. Confirm whether actor-data and casualties-data exports are intended to coexist in one RDF union; if so, choose the canonical preferred-label convention.
2. Confirm the intended modeling of unit-joining events with multiple joining units.
3. Confirm whether the two death events represent duplicate links or genuinely multi-person events.
4. Choose the canonical WarSampo `Conflict` class namespace.
5. Approve the 15 exact vocabulary substitutions as the first cleanup change.
6. Review and submit the two-date source correction to the owning data repository or generation pipeline.

Detailed evidence is available in the [local baseline record](../baselines/2026-09-01-warsampo.md), the [cross-module audit record](../baselines/2026-09-01-warsampo-union.md), the [machine-readable local baseline](../../baselines/warsampo-local.tsv), the [verified 15-change repair proposal](../../proposals/repairs/2026-09-01-standard-term-typos/README.md), the [domain-review packet](./2026-09-01-domain-review.md), and the [project requirement catalog](../rules/warsampo-requirements.md).
