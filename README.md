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
| `warsampo` | 16 | SKOS plus WarSampo event, role, schema, and domain rules |

With `--cross-module`, local rules still run once per source module. Six reusable SKOS rules are then re-evaluated over a disk-backed union to catch facts split across files, and four explicitly integration-scoped rules run there as well. Results already found locally are de-duplicated. If any source fails to parse, union validation is skipped rather than querying an incomplete graph. Union progress identifies each rule and focus-node count as it runs; a successful summary includes the elapsed time per union rule. The full WarSampo union audit is intentionally opt-in; see [the current baseline record](./docs/baselines/2026-09-01-warsampo.md) for its performance limitation.

Only RDF triple syntaxes are accepted. TriG, N-Quads, and other dataset syntaxes are rejected explicitly until the project defines how named graphs map to source modules and union validation.

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

To review a new baseline, replace `--baseline` with `--write-baseline`. Baseline v2 signatures include the rule, SHACL constraint component, focus node, path, value, source module, and a deterministic occurrence index. Human-facing messages are deliberately excluded, so wording changes do not invalidate accepted findings. Only violations are written or compared; a baselined warning therefore cannot hide a later severity promotion.

Summary output remains available after a parse failure, but SHACL reports and baseline output are suppressed because validation was incomplete. Output paths are preflighted so they cannot collide with each other, selected RDF inputs, or a baseline being compared. Individual report, summary, and baseline files are published atomically.

Strict parsing exposed two invalid `xsd:date` values in `warsampo/warsa-event-data/times.ttl`: `1939-12-35` and `1940-02-30`. A portable [source-correction proposal](./proposals/source-corrections/2026-09-01-invalid-dates/README.md) documents the evidence and avoids inventing an unknown historical start date. With that proposal applied to the ignored local snapshot, the committed baseline covers all 61 modules. Its exact corpus scope and results are recorded in [the baseline record](./docs/baselines/2026-09-01-warsampo.md).

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

Each repair declares its minimum validation profile. The guard automatically broadens the requested profile when necessary—for example, the SKOS typo repair is checked with the SKOS rules even if the command says `--profile core`. The executed graph delta must exactly match the declared substitutions; unexpected additions or deletions and many-to-one merges are rejected.

An apply run records the repair ID, rule ID, source module, timestamp, and RDF statements actually deleted or added. It rejects updates without a corresponding validation finding, failed postconditions, or new violations, and a second run must be a no-op. Output is assembled in a sibling staging directory and atomically published only after every data copy plus mandatory `.warsampo-linter/repair.ru` and `.warsampo-linter/provenance.ttl` metadata is ready. Existing destinations and metadata files are never overwritten. Changed RDF copies are serialized by Jena, so review the generated triple patch rather than expecting a formatting-only source diff.

## Rule layout

```text
shapes/
  core/                 reusable local RDF checks
  vocabularies/skos/    reusable SKOS checks
  integration/          generic union-graph checks
  warsampo/local/       WarSampo module checks
  warsampo/cross/       WarSampo union-graph checks
  warsampo/requirements/ stable resources for accepted project contracts
repairs/core/           declarative automatic repair catalog
fixtures/               positive, negative, and repair fixtures
vocabularies/           generated, role-aware standard-term manifest
baselines/              reviewed regression signatures
```

Every executable constraint has a stable IRI, explicit severity, message, layer, source justification, and fixture coverage. WarSampo-specific source links resolve to the versioned requirement resources documented in [docs/rules/warsampo-requirements.md](./docs/rules/warsampo-requirements.md), rather than ephemeral external URLs. Prefer SHACL Core; use SHACL-SPARQL when the constraint is not clear in Core. Add rules to a reusable layer only when their meaning does not depend on WarSampo.

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

GitHub Actions runs the Java fixture/repair suite and the pySHACL compatibility check. The ignored WarSampo checkout is not required in CI. Full-corpus measurements and the reviewed finding breakdown are recorded in [docs/baselines/2026-09-01-warsampo.md](./docs/baselines/2026-09-01-warsampo.md).

## Project documentation

- [ADR index](./docs/adrs/README.md)
- [ADR-001: Adopt SHACL for Linked-Data Linting and Guarded Repair](./docs/adrs/implemented/ADR-001-adopt-shacl-for-linked-data-linting-and-repair.md)
- [WarSampo project requirements](./docs/rules/warsampo-requirements.md)
- [Current WarSampo baseline record, 2026-09-01](./docs/baselines/2026-09-01-warsampo.md)
- [Colleague-facing findings brief](./docs/findings/2026-09-01-colleague-brief.md)
- [Invalid event-date correction proposal](./proposals/source-corrections/2026-09-01-invalid-dates/README.md)
