# WarSampo cross-module audit: 2026-09-01

This record captures the first completed disk-backed union audit of the corrected 61-module WarSampo snapshot. It complements the [module-local baseline](./2026-09-01-warsampo.md); the new union findings have not been accepted into a regression baseline because they still require data-owner review.

## Reproducible command

```sh
./scripts/linter.sh validate \
  --data warsampo \
  --profile warsampo \
  --cross-module \
  --tdb reports/tdb-full-union-2026-09-01 \
  --report reports/warsampo-union-report-2026-09-01.ttl \
  --summary reports/warsampo-union-summary-2026-09-01.txt \
  --baseline baselines/warsampo-local.tsv
```

The command used Apache Jena 6.2.0 on Eclipse Temurin Java 21 with `-Xmx4g`, Docker/OrbStack on macOS, and the same corrected ignored corpus snapshot as the local record. The persistent TDB2 store occupied approximately 2.3 GB. Reports remain ignored build artifacts and can be regenerated with the command above.

## Result

| Metric | Result |
| --- | ---: |
| Successfully parsed modules | 61 of 61 |
| Parsed source triples | 13,873,089 |
| Distinct local and union rules | 20 |
| Elapsed time | 390,133 ms |
| Sampled peak JVM heap | 2,369.0 MiB |
| Total violations | 1,766 |
| Total warnings | 380 |
| New union violations beyond the local baseline | 1,643 |
| New union warnings | 379 |
| Parse failures | 0 |

The complete audit adds 2,022 union-only results to the 123 violations and one warning already found locally:

| Union rule | Severity | New results | Interpretation |
| --- | --- | ---: | --- |
| `SkosPreferredLabelLanguageShape` | Violation | 1,643 | The same resource receives multiple preferred labels in one language when modules are combined. |
| `InternalReferenceShape` | Warning | 334 | A referenced internal WarSampo IRI has no outgoing description in the selected union. |
| `EventParticipantTypeShape` | Warning | 45 | A participant IRI does not resolve to a `Person` or `MilitaryUnit` type. |
| Remaining seven union rules | — | 0 | No new duplicate identifiers, chronology errors, SKOS cycles, relation conflicts, reflexive links, or cross-file label-role overlaps. |

### Preferred-label conflicts

The 1,643 violations are strongly patterned:

| Focus resource family | Results |
| --- | ---: |
| Birth events | 806 |
| Death events | 806 |
| Person resources | 18 |
| Unit-naming events | 13 |

Most event results arise because actor-data and casualties-data exports describe the same event IRI with different name ordering. For example, `events:birth_p269820` is labeled `"Hokkanen, Yrjö Ensio was born"@en` in `actor_data_births.ttl` and `"Yrjö Ensio Hokkanen was born"@en` in `cas_person_births.ttl`. Likewise, `events:death_p515572` uses `"Lammi, Väinö Armas died"@en` and `"Väinö Armas Lammi died"@en`. Each module is locally valid; their union violates the SKOS rule of one preferred label per language.

This is a concrete integration-policy decision: either these exports are alternative publication artifacts that should not be unioned, or one label convention must be canonical and the other values should become alternative labels or be removed during generation.

### Reference and participant review queues

The 334 unresolved internal-reference warnings break down by referenced IRI family:

| Referenced IRI family | Results |
| --- | ---: |
| Unit categories | 183 |
| Actors | 119 |
| Places | 5 |
| Events | 5 |
| Time spans | 4 |
| Other internal resources | 18 |

The 45 untyped event participants contain 32 `actor_*` IRIs and 13 `person_*` IRIs. These are warnings because some may be aliases rather than independently typed resources. For example, `actor_3038` is used as an event participant and linked with `owl:sameAs`, but has no direct `MilitaryUnit` type in the selected graph. Data owners should decide whether inference is part of the publication contract or whether aliases need explicit types.

## Performance profile

The first instrumented chronology attempt remained CPU-bound for more than two minutes and was stopped. Rewriting it to aggregate one maximum birth bound and one minimum death bound per person both matches the documented requirement and reduces that rule to 1,742 ms.

| Union rule | Focus nodes | Elapsed |
| --- | ---: | ---: |
| `BirthDeathChronologyShape` | 1 | 1,742 ms |
| `DuplicateIdentifierShape` | 1 | 1 ms |
| `EventParticipantTypeShape` | 1 | 666 ms |
| `InternalReferenceShape` | 1 | 37,394 ms |
| `SkosBroaderCycleShape` | 1,778 | 71 ms |
| `SkosBroaderRelatedDisjointShape` | 2,105 | 42 ms |
| `SkosBroaderSelfShape` | 1,778 | 31 ms |
| `SkosLabelDisjointnessShape` | 1,716,612 | 55,939 ms |
| `SkosPreferredLabelLanguageShape` | 1,714,200 | 63,254 ms |
| `SkosRelatedSelfShape` | 327 | 5 ms |

The measured union-rule phase totals 159,145 ms. Label disjointness and preferred-label language account for approximately 75% of it because their reusable module-local target strategy visits about 1.7 million union nodes each. Internal references account for another 37,394 ms. If the audit needs to become a frequent CI gate, the next performance task is to add equivalent audit-target versions of the two global label queries and compare their result signatures before changing the execution strategy.

## Review policy

Do not baseline these 1,643 new violations until colleagues confirm that the actor and casualty exports are intended to coexist in one graph and choose a preferred-label policy. Warnings should remain visible review queues rather than baseline entries. The absence of findings from a union rule is useful evidence for this snapshot, not proof that the corresponding error class can never occur.
