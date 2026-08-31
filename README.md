# warsampo-linter

`warsampo-linter` explores reusable ways to detect and safely repair errors in RDF linked data. The [WarSampo datasets](https://www.ldf.fi/dataset/warsa) are the first case study, but the rule system is intended to support other datasets as well.

The project is currently in the research and specification stage. The proposed architecture is recorded in [ADR-001](./docs/adrs/proposed/ADR-001-adopt-shacl-for-linked-data-linting-and-repair.md); it is not yet implemented or accepted.

## Goals

- Detect errors ranging from malformed RDF to project-specific semantic inconsistencies.
- Express rules with established linked-data standards and tools instead of creating a custom rule language.
- Separate broadly reusable rules from vocabulary profiles and dataset-specific rules.
- Suggest or apply repairs only when their safety can be demonstrated and tested.
- Support incremental adoption on large datasets that already contain known violations.

## Proposed approach

- Use [SHACL](https://www.w3.org/TR/shacl/) as the canonical constraint language, preferring SHACL Core and using SHACL-SPARQL when necessary.
- Use [Apache Jena](https://jena.apache.org/documentation/shacl/) for strict RDF parsing, primary validation, SPARQL, and disk-backed cross-module checks.
- Use [pySHACL](https://github.com/RDFLib/pyshacl) on small fixtures as a secondary implementation and interoperability check.
- Keep validation and repair separate. Represent reviewable repairs as guarded SPARQL Updates, optionally using the [DASH Suggestions Vocabulary](https://www.datashapes.org/suggestions.html).
- Run most rules per file or module and reserve a disk-backed union graph for checks that genuinely require the complete dataset.

Rules are divided into layers:

1. RDF serialization and parsing.
2. Generic RDF and known-vocabulary hygiene.
3. Reusable profiles for vocabularies such as SKOS, CIDOC CRM, and GeoSPARQL.
4. Structural dataset contracts.
5. Cross-resource and cross-module consistency.
6. WarSampo-specific historical and domain semantics.

## Repair policy

Repairs have three safety levels:

- **Automatic:** deterministic, unambiguous, idempotent, local, and reversible corrections, such as an exact replacement for a misspelled standard vocabulary term.
- **Suggested:** a patch is generated for review but not applied automatically.
- **Manual:** ambiguous semantic changes are reported with context only.

Fix operations will default to dry-run output, operate on a copy or generated patch, preserve provenance, revalidate the result, and present a diff before source data is replaced.

## Initial WarSampo findings

The research scan found 15 high-confidence standard-vocabulary misspellings that valid RDF syntax alone cannot detect:

| Observed | Expected | Occurrences |
| -------- | -------- | ----------: |
| `rdfs:subClassof` | `rdfs:subClassOf` | 2 |
| `rdfs:Property` | `rdf:Property` | 7 |
| `dct:bibliographiccitation` | `dct:bibliographicCitation` | 2 |
| `skos:preflabel` | `skos:prefLabel` | 3 |
| `owl:same` | `owl:sameAs` | 1 |

An additional schema mismatch between the local `:Conflict` class and `wcf:Conflict` is intentionally classified as a warning requiring human review. It illustrates why some findings must not be repaired automatically.

At the time of the scan, the local WarSampo checkout was approximately 679 MB and contained about 15.5 million lines of Turtle. These figures motivate modular validation and should be treated as a snapshot rather than a permanent dataset property.

## Project documentation

- [ADR index](./docs/adrs/README.md)
- [ADR-001: Adopt SHACL for Linked-Data Linting and Guarded Repair](./docs/adrs/proposed/ADR-001-adopt-shacl-for-linked-data-linting-and-repair.md)
