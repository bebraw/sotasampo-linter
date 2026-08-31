# warsampo-linter

`warsampo-linter` detects and safely repairs errors in RDF linked data. WarSampo is the first case study, while the rule library separates reusable RDF/SKOS checks from dataset-specific constraints so the same approach can be used with other datasets.

The implemented architecture is recorded in [ADR-001](./docs/adrs/implemented/ADR-001-adopt-shacl-for-linked-data-linting-and-repair.md). It uses SHACL as the rule language, Apache Jena 6.2.0 as the primary engine, pySHACL as a compatibility engine, TDB2 for union graphs, and guarded SPARQL Update for repairs.

## Quick start

Docker is the only local prerequisite. The wrapper builds the Java 21 application when needed and runs it with a 4 GiB heap cap.

```sh
./scripts/linter.sh validate \
  --data fixtures/positive/core-valid.ttl \
  --profile core
```

Validation exits with `0` when there are no new violations, `1` for new violations, and `2` for malformed input, invalid options, or execution failures. Warnings alone do not fail the run.

The profiles are cumulative:

| Profile | Local rules | Scope |
| --- | ---: | --- |
| `core` | 2 | strict parsing, standard-vocabulary term and role validation, datatype hygiene |
| `skos` | 8 | Core plus SKOS label and relation integrity |
| `warsampo` | 17 | SKOS plus WarSampo event, role, schema, and domain rules |

With `--cross-module`, local rules still run once per source module and four explicitly integration-scoped rules run over a disk-backed union. The full WarSampo union audit is intentionally opt-in; see [the baseline record](./docs/baselines/2026-08-31-warsampo.md) for its current performance limitation.

## Reports and baselines

The validator can emit a deterministic Turtle report, a text summary, and stable regression signatures:

```sh
./scripts/linter.sh validate \
  --data warsampo \
  --profile warsampo \
  --report reports/warsampo-local-report.ttl \
  --summary reports/warsampo-local-summary.txt \
  --baseline baselines/warsampo-local.tsv
```

To review a new baseline, replace `--baseline` with `--write-baseline`. A signature includes the rule, focus node, path, value, source module, and message. The committed baseline accepts existing violations but never suppresses parsing failures or validator crashes.

The current corpus contains one invalid `xsd:date` (`1939-12-35` in `warsampo/warsa-event-data/times.ttl`), so a complete corpus command exits with `2` even when every SHACL violation is baselined. This is an intentional distinction between accepted graph findings and input that cannot be parsed strictly.

## Guarded repairs

Repair is a separate command and defaults to dry run. The initial catalog contains five exact standard-term substitutions represented as DASH SPARQL Update suggestion generators:

```sh
./scripts/linter.sh repair \
  --data warsampo/ammo-data/ammo_schema.ttl \
  --data warsampo/ammo-data/sources.ttl \
  --data warsampo/warsa-actor-data/medals/medal_types.ttl \
  --profile core \
  --dry-run \
  --patch reports/standard-term-repairs.ru \
  --provenance reports/standard-term-repairs.ttl
```

The five mappings are:

| Observed | Replacement | Corpus occurrences |
| --- | --- | ---: |
| `rdfs:subClassof` | `rdfs:subClassOf` | 2 |
| `rdfs:Property` | `rdf:Property` | 7 |
| `dct:bibliographiccitation` | `dct:bibliographicCitation` | 2 |
| `skos:preflabel` | `skos:prefLabel` | 3 |
| `owl:same` | `owl:sameAs` | 1 |

Applying a repair always requires a new, empty destination. Source data is never overwritten:

```sh
./scripts/linter.sh repair \
  --data fixtures/repairs/standard-term-typos.ttl \
  --profile core \
  --apply \
  --output-dir reports/repaired
```

An apply run records the repair ID, rule ID, source module, timestamp, and exact deleted/added RDF statements. It rejects updates without a corresponding validation finding, failed postconditions, or new violations, and a second run must be a no-op. Changed RDF copies are serialized by Jena, so review the generated triple patch rather than expecting a formatting-only source diff.

## Rule layout

```text
shapes/
  core/                 reusable local RDF checks
  vocabularies/skos/    reusable SKOS checks
  integration/          generic union-graph checks
  warsampo/local/       WarSampo module checks
  warsampo/cross/       WarSampo union-graph checks
repairs/core/           declarative automatic repair catalog
fixtures/               positive, negative, and repair fixtures
vocabularies/           generated, role-aware standard-term manifest
baselines/              reviewed regression signatures
```

Every executable constraint has a stable IRI, explicit severity, message, layer, source justification, and fixture coverage. Prefer SHACL Core; use SHACL-SPARQL when the constraint is not clear in Core. Add rules to a reusable layer only when their meaning does not depend on WarSampo.

The vocabulary manifest is generated from checksum-pinned official RDF, RDFS, OWL, SKOS, and DCMI graphs:

```sh
./scripts/update-vocabularies.sh
./scripts/update-vocabularies.sh --check
```

## Verification

```sh
./scripts/mvn.sh test
./scripts/test-pyshacl.sh
docker build -t warsampo-linter .
```

GitHub Actions runs the Java fixture/repair suite and the pySHACL compatibility check. The ignored WarSampo checkout is not required in CI. Full-corpus measurements and the reviewed finding breakdown are recorded in [docs/baselines/2026-08-31-warsampo.md](./docs/baselines/2026-08-31-warsampo.md).

## Project documentation

- [ADR index](./docs/adrs/README.md)
- [ADR-001: Adopt SHACL for Linked-Data Linting and Guarded Repair](./docs/adrs/implemented/ADR-001-adopt-shacl-for-linked-data-linting-and-repair.md)
- [WarSampo baseline record, 2026-08-31](./docs/baselines/2026-08-31-warsampo.md)
