# WarSampo linked-data linting: findings brief

**Snapshot:** 2026-09-01  
**Scope:** 60 strictly parseable RDF modules, 13,733,789 source triples  
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

## Important limitation found by strict parsing

`warsampo/warsa-event-data/times.ttl` contains `"1939-12-35"^^xsd:date` at line 86,020. This is invalid RDF datatype lexical content. The linter fails closed: it reports the parse error, skips union validation, and will not publish a partial SHACL report or baseline.

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

1. Confirm the intended modeling of unit-joining events with multiple joining units.
2. Confirm whether the two death events represent duplicate links or genuinely multi-person events.
3. Choose the canonical WarSampo `Conflict` class namespace.
4. Approve the 15 exact vocabulary substitutions as the first cleanup change.
5. Correct or formally replace the invalid `1939-12-35` date so all modules can participate in complete validation.

Detailed evidence is available in the [baseline record](../baselines/2026-09-01-warsampo.md), the [machine-readable baseline](../../baselines/warsampo-local.tsv), and the [project requirement catalog](../rules/warsampo-requirements.md).
