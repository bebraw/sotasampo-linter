# ADR-001: Adopt SHACL for Linked-Data Linting and Guarded Repair

**Status:** Implemented

**Date:** 2026-08-31

## Context

The project needs to detect errors in RDF linked data and, where it is safe, describe or apply repairs. WarSampo is the first dataset, but the design must support other datasets without turning every check into WarSampo-specific code.

Linked-data quality problems occur at several levels:

1. **Serialization:** malformed Turtle, invalid literals, or unresolved prefixes.
2. **RDF and vocabulary use:** misspelled standard terms, incorrect RDF term kinds, or violations of published vocabulary integrity conditions.
3. **Graph structure:** missing required values, wrong datatypes or node kinds, cardinality errors, invalid controlled values, or inconsistent inverse relationships.
4. **Dataset integration:** unresolved internal references, duplicate entities, conflicting statements, or disagreement between modules.
5. **Domain semantics:** chronologically impossible life events, incompatible event roles, inconsistent casualty totals, or other WarSampo-specific historical rules.

The distinction matters because RDF, RDFS, and OWL do not provide closed-world data validation. For example, RDFS domain and range declarations infer types; they do not reject missing or unexpected data. OWL's open-world semantics likewise cannot be treated as a conventional schema validator. The project needs an explicit constraint language.

The current WarSampo checkout is large enough that whole-dataset, in-memory validation should not be the only execution model. It is approximately 679 MB and contains roughly 15.5 million lines of Turtle. Most checks can run per module, while a smaller group genuinely requires a union graph.

### Evidence from the initial WarSampo scan

A static scan found 15 high-confidence misspellings of standard vocabulary terms. These are valid IRIs syntactically, so an RDF parser alone will accept them:

| Observed term                    | Expected term                     | Occurrences | Example location                                         |
| -------------------------------- | --------------------------------- | ----------: | -------------------------------------------------------- |
| `rdfs:subClassof`                | `rdfs:subClassOf`                 |           2 | `warsampo/ammo-data/ammo_schema.ttl`                     |
| `rdfs:Property`                  | `rdf:Property`                    |           7 | `warsampo/ammo-data/ammo_schema.ttl`                     |
| `dct:bibliographiccitation`      | `dct:bibliographicCitation`       |           2 | `warsampo/ammo-data/sources.ttl`                         |
| `skos:preflabel`                 | `skos:prefLabel`                  |           3 | `warsampo/ammo-data/sources.ttl`                         |
| `owl:same`                       | `owl:sameAs`                      |           1 | `warsampo/warsa-actor-data/medals/medal_types.ttl`       |

The scan also found an ambiguous schema issue: `warsampo/Warsampo-schema/warsampo-schema.ttl` defines a local `:Conflict` class but declares `wacs:hasConflict` with range `wcf:Conflict`. This deserves a warning and human review, not an automatic replacement, because either namespace may be intentional.

These examples demonstrate two required rule families:

- reusable rules, such as checking terms used from standard vocabulary namespaces; and
- project rules, such as deciding which conflict class WarSampo intends to use.

## Decision

We will use [W3C SHACL 1.0](https://www.w3.org/TR/shacl/) as the canonical constraint language for linked-data linting. Rules will use SHACL Core when possible and SHACL-SPARQL only when a constraint cannot be expressed clearly in Core.

We will use [Apache Jena](https://jena.apache.org/documentation/shacl/) as the primary execution stack:

- RIOT for strict RDF parsing;
- Jena SHACL for validation;
- ARQ and SPARQL Update for queries and controlled repairs; and
- TDB2 or Fuseki when a check needs a disk-backed union graph.

[pySHACL](https://github.com/RDFLib/pyshacl) will be a secondary implementation for small fixtures, rule development, and cross-engine compatibility tests. Full-corpus validation is not required to fit into a Python in-memory graph.

Validation and repair will remain separate operations. A validation rule reports a problem without changing the source. A repair definition may generate a suggested [SPARQL 1.1 Update](https://www.w3.org/TR/sparql11-update/), but application requires the safety policy below. Where useful, repair suggestions will use the [DASH Suggestions Vocabulary](https://www.datashapes.org/suggestions.html), including `dash:SPARQLUpdateSuggestionGenerator`. DASH is an unofficial draft, so it is an internal interchange convention rather than a portability guarantee.

SHACL Advanced Features rules will not be used as the general repair mechanism. SHACL-AF rules construct inferred triples and are useful for additive enrichment, but replacement and deletion require SPARQL Update or another explicit patch mechanism.

### Rule layers

Rules will be organized by the scope of knowledge they encode:

| Layer | Scope | Examples | Expected reuse |
| ----- | ----- | -------- | -------------- |
| Parse | RDF serialization | Turtle syntax, prefix resolution, literal syntax | Universal |
| Core | RDF and common vocabulary hygiene | known-namespace term validation, datatype validity, node kind | Broadly reusable |
| Vocabulary profiles | Published vocabulary integrity rules | SKOS labels and relations, GeoSPARQL geometry constraints, CIDOC CRM usage profiles | Reusable by vocabulary |
| Structural | Dataset model contracts | required properties, cardinalities, controlled values, inverse consistency | Reusable when models align |
| Integration | Relationships across resources or modules | internal references, duplicate identifiers, cross-file type conflicts | Configurable |
| Domain | Project semantics | birth before death, event-role compatibility, WarSampo conflict modeling | Project-specific |

The distinction between general and project-specific behavior will be structural rather than encoded as conditionals in one program. Shape files will live in separate directories and be selected through profiles:

```text
shapes/
  core/
  vocabularies/
    skos/
    cidoc-crm/
    geosparql/
  integration/
  warsampo/
    local/
    cross/
    requirements/
repairs/
  core/
  warsampo/
fixtures/
  positive/
  negative/
  repairs/
baselines/
```

Each constraint must have:

- a stable IRI used as its rule identifier;
- `sh:severity` set to `sh:Violation`, `sh:Warning`, or `sh:Info`;
- a concise `sh:message` that identifies the failed expectation;
- at least one positive or negative fixture, with both for repairable rules;
- a declared layer and applicable validation profile through its file location; and
- a reference to the vocabulary specification or project requirement that justifies it.

The severity contract is:

- **Violation:** data is demonstrably invalid against an accepted contract and blocks new regressions.
- **Warning:** data is suspicious or relies on a modeling choice that needs review.
- **Info:** quality advice that is useful but not a correctness failure.

### Known-vocabulary term checking

The linter will bundle or reproducibly fetch versioned, official vocabulary graphs used by the selected profile. Validation will flag an IRI used as a predicate, class, or declared RDF term when:

1. its namespace is configured as a known vocabulary namespace; and
2. the IRI is absent from the pinned vocabulary graph in the applicable term role.

This check must be deterministic and must not depend on dereferencing arbitrary IRIs during a normal run. Unknown third-party namespaces are not errors merely because the linter has no vocabulary graph for them.

The 15 high-confidence WarSampo findings above are the initial fixtures for this rule family.

### Validation workflow

The validator will perform these stages in order:

1. Parse every selected RDF source strictly and report file and source location where the parser provides them.
2. Load the requested shape profile and its pinned vocabulary graphs.
3. Run file- or module-local shapes without creating a global graph.
4. Load a disk-backed union graph only when cross-module validation is requested, and run no union constraints unless every selected source parsed successfully.
5. Re-evaluate union-sensitive reusable rules as well as explicitly cross-module rules, de-duplicating results already found locally.
6. Emit the standard SHACL validation report as Turtle and a stable, human-readable summary;
7. derive regression signatures from rule ID, constraint component, focus node, result path, value, source module, and deterministic occurrence index; and
8. compare violation signatures with the committed baseline when running in regression mode.

The default CI policy will fail on new `sh:Violation` signatures, malformed input, validator crashes, or invalid shape graphs. Existing accepted violations may be baselined initially so that adoption does not require repairing the entire corpus in one change. Warnings are not baselined, which prevents a warning from suppressing the same result after promotion to a violation. Removing a baseline entry requires the corresponding source issue to be fixed or explicitly reclassified.

Results must be deterministic for the same data, shape profile, vocabulary versions, and configuration. Reports must not depend on blank-node labels or unstable iteration order. Messages remain report metadata but are excluded from signature identity so editorial changes do not invalidate the baseline. If strict parsing is incomplete, a diagnostic summary may be written, but no SHACL report or baseline may be published from the partial graph.

The first implementation accepts RDF triple syntaxes only. RDF dataset syntaxes such as TriG and N-Quads are rejected until named-graph ownership, source-module attribution, and union semantics are explicitly defined.

### Repair safety policy

Repairs are classified into three tiers:

| Tier | Behavior | Examples |
| ---- | -------- | -------- |
| Automatic | May be applied in an explicitly requested fix run | exact standard-term substitutions with a unique replacement |
| Suggested | Emit a patch for review but do not apply it automatically | adding an inferred type, normalizing a literal, restoring an inverse link |
| Manual | Report context only | entity merging, historical dates, identity links, ambiguous namespace choices |

An automatic repair is allowed only when it is:

- deterministic: the same input produces the same patch;
- unambiguous: exactly one replacement satisfies the rule;
- idempotent: running it again makes no change;
- local: it does not make an unbounded semantic rewrite;
- preconditioned: the update matches the precise erroneous state; and
- reversible from the emitted patch or recorded before/after values.

A fix run must:

1. default to dry-run output;
2. apply changes to a copy or generated patch, never silently rewrite source data;
3. retain the rule ID, repair ID, affected triples, and timestamp as provenance;
4. validate the repaired graph with the broader of the requested profile and the repair's declared minimum profile;
5. compare the executed graph delta with the exact expected additions and deletions, rejecting side effects and many-to-one merges;
6. reject a patch that introduces a new violation or fails its postcondition;
7. stage every copied graph and mandatory patch/provenance artifact before atomically publishing a new destination directory; and
8. present a diff for human approval before source files are replaced.

No repair will infer historical truth merely to make a graph conform. If several repairs are valid, the linter reports alternatives rather than choosing one.

### Initial implementation scope

The first implementation slice will contain 10–15 rules selected to exercise all important mechanisms without attempting exhaustive WarSampo cleanup:

- strict parsing of the selected Turtle files;
- known-vocabulary term validation for RDF, RDFS, OWL, SKOS, and DCTERMS;
- exact repairs for the five misspelling patterns already observed;
- ill-typed literal checks;
- SKOS preferred-label cardinality by language;
- configured internal-reference integrity;
- at least one cross-module consistency rule;
- the WarSampo conflict-class mismatch as a warning; and
- at least two WarSampo domain rules, such as life-event chronology and event-role compatibility.

The implementation is complete when:

- every initial rule has stable identifiers, severity, documentation, and fixtures;
- positive fixtures conform and negative fixtures produce the expected SHACL results in Apache Jena;
- Core-only fixtures are cross-checked with pySHACL;
- each automatic repair has failing-before and conforming-after tests plus an idempotence test;
- validation can run per selected module and in an explicitly requested cross-module mode;
- CI can compare current results against a reviewed baseline and rejects new violations;
- dry-run repair output includes a reviewable SPARQL Update or equivalent RDF patch and provenance; and
- a full WarSampo baseline run records elapsed time, peak memory, rule counts, and findings by severity.

### Delivery sequence

1. **Harness:** implement strict parsing, profile loading, SHACL report output, deterministic signatures, and fixture tests.
2. **Reusable rules:** implement known-vocabulary checking and the first SKOS and datatype constraints.
3. **WarSampo profile:** encode the selected structural and historical rules, then establish a reviewed baseline.
4. **Guarded repair:** implement dry-run suggestions and automatic fixes for the five known misspelling patterns.
5. **Scale validation:** add disk-backed cross-module execution and measure it on the full corpus.
6. **Extraction:** move rules proven useful outside WarSampo into versioned core or vocabulary profiles without weakening their fixtures.

Aggregate quality metrics may later be published with the [Data Quality Vocabulary](https://www.w3.org/TR/vocab-dqv/), but dashboards and dataset scoring are outside this first slice.

## Implementation

Implemented on 2026-08-31 with Apache Jena 6.2.0 and Java 21, then corrected after implementation review on 2026-09-01. The repository now contains:

- strict parsing, cumulative `core`, `skos`, and `warsampo` local profiles, plus explicitly integration-scoped union rules;
- deterministic, fail-closed SHACL reports; message-independent, multiplicity-preserving regression signatures; violation-only baseline comparison; rule counts; elapsed time; and sampled peak-heap reporting;
- 16 local rules and four explicitly cross-module rules, with the six reusable SKOS rules also evaluated over a requested complete union, covered by positive/negative fixtures;
- a checksum-pinned, role-aware standard-vocabulary manifest generated from official RDF graphs;
- pySHACL compatibility coverage for the Core profile;
- five declarative DASH/SPARQL Update repairs with minimum guard profiles, exact-delta enforcement, dry-run output, atomically published copy-only application, provenance, postconditions, revalidation, and idempotence tests; and
- GitHub Actions coverage for the Java and pySHACL fixture suites.

The corrected module-local WarSampo baseline selected the 60 strictly parseable sources and 13,733,789 triples. It completed in 119,736 ms with a sampled peak JVM heap of 2,076.9 MiB and reported 123 violations plus one warning. The remaining source is excluded from baseline generation because `1939-12-35` is not a valid `xsd:date`; selecting it makes the run incomplete and suppresses report/baseline publication. The reviewed baseline v2 signatures are committed in `baselines/warsampo-local.tsv`; details and the reproducible selection are recorded in [the corrected baseline record](../../baselines/2026-09-01-warsampo.md).

Review removed an unsupported WarSampo cardinality rule that had treated every subject of CIDOC CRM `P4_has_time-span` as an event and generated 110 false violations for legitimate war-diary periods. It also corrected SKOS reflexive-relation severities, complete-union evaluation, declared-term and `rdf:_n` vocabulary handling, named-graph rejection, report conformance fields, baseline severity handling, output collision/atomicity behavior, and actual repair-delta provenance. Project-specific shapes now cite stable requirement resources documented in the repository rather than broken external URLs.

Cross-module execution is implemented using TDB2 and is verified on fixtures. A full-corpus union audit remained CPU-bound beyond 20 minutes and was stopped without committing a partial baseline. It is therefore an explicit audit path rather than a routine CI gate, and query-specific ARQ profiling is follow-up work. This limitation does not affect completion of the required full module-local baseline.

## Trigger

The initial research pass found real vocabulary errors that ordinary RDF parsing does not detect, alongside ambiguous project-modeling questions that must not be auto-fixed. At the same time, the expected future use on other datasets makes a reusable constraint language and layered rule library more valuable than a collection of WarSampo-specific scripts.

## Consequences

**Positive:**

- Rules are expressed in a W3C standard supported by multiple implementations.
- SHACL validation reports provide a standard machine-readable result model.
- General, vocabulary-specific, and WarSampo-specific rules can evolve independently.
- Apache Jena provides one stack for parsing, validation, SPARQL, and disk-backed RDF storage.
- Repairs remain reviewable and testable rather than being hidden inside validator code.
- Baseline comparison allows incremental adoption on an existing imperfect corpus.

**Negative:**

- SHACL-SPARQL constraints can be expensive and require query-performance discipline.
- Apache Jena introduces a JVM runtime even if surrounding tooling is written in another language.
- Maintaining pinned vocabulary graphs and baseline findings adds repository overhead.
- DASH repair suggestions are not a W3C standard and may require an adapter if tools diverge.
- Cross-engine agreement cannot be assumed for every advanced feature, so compatibility tests are necessary.
- Automatic repair remains intentionally narrow; much domain cleanup still requires human judgment.

**Neutral:**

- Existing violations are recorded rather than immediately treated as CI failures.
- Some quality checks, especially duplicate detection and historical plausibility, will remain queries or review workflows rather than simple SHACL Core shapes.
- Shape inference tools may help bootstrap candidate constraints, but humans remain responsible for accepting them as contracts.

## Alternatives Considered

### Build a custom linter and repair DSL

Rejected because it would recreate constraint targeting, validation reports, severity handling, and tool integration already provided by SHACL. Project-specific orchestration is still needed, but the rules themselves should remain portable RDF where practical.

### Use ShEx as the canonical language

[ShEx](https://shex.io/shex-semantics/) is concise for describing graph structures and remains useful for interoperability experiments. It was not chosen because SHACL is a W3C Recommendation, has a standard validation-report vocabulary, integrates directly with Jena, and offers a clearer path to SPARQL-based constraints and repair suggestions. ShEx is a W3C Community Group technology rather than a W3C Standards Track Recommendation.

### Use RDFUnit as the canonical framework

[RDFUnit](https://github.com/AKSW/RDFUnit) established a valuable test-driven approach to RDF data quality and can generate tests from vocabularies. Its methodology informs this design, but SHACL is now the more interoperable representation for the repository's durable constraints.

### Use pySHACL as the only execution engine

Rejected for the initial architecture because it is convenient for development but normally operates on RDFLib in-memory graphs. It remains the secondary engine for fixtures and interoperability checks, while Jena provides the disk-backed path needed by the corpus.

### Use RDFS or OWL axioms as validation rules

Rejected because inference under open-world semantics does not enforce the required closed-world presence, cardinality, and consistency checks. Ontology axioms remain input to rule design and may support inference before validation.

### Adopt qSKOS or Luzzu as the complete solution

[qSKOS](https://github.com/cmader/qSKOS) offers useful reusable checks for SKOS vocabularies, and [Luzzu](https://arxiv.org/abs/1412.3750) focuses on linked-data quality assessment and metrics. Neither alone covers arbitrary WarSampo graph contracts plus guarded record-level repair. Relevant checks and metric concepts may be adapted into the layered SHACL library.

### Generate all shapes from existing data or ontologies

[Astrea](https://pmc.ncbi.nlm.nih.gov/articles/PMC7250618/) and [sheXer](https://www.sciencedirect.com/science/article/pii/S2352711026000865) can bootstrap shapes from ontologies or observed data. Generated shapes describe declarations or current patterns, which may include the very errors being sought. They may propose candidate rules but will not be accepted without review and fixtures.

### Use research-grade automatic SHACL repair as the production foundation

Recent work on [computing SHACL repairs](https://drops.dagstuhl.de/entities/document/10.4230/TGDK.3.3.1) can enumerate alternative repairs and is promising for ambiguous cases. The accompanying implementation is presented as a proof of concept, so it will be evaluated later as a candidate generator rather than placed on the critical production path now.

### Report problems without any repair representation

Rejected because deterministic vocabulary corrections are already visible and can be repaired safely. Keeping validation and repair separate provides safety without giving up useful automation.

## Related Literature and Standards

- W3C, [Shapes Constraint Language (SHACL)](https://www.w3.org/TR/shacl/), Recommendation.
- W3C, [RDF Schema 1.1](https://www.w3.org/TR/rdf-schema/) and [OWL 2 Primer](https://www.w3.org/TR/owl2-primer/), for the inference semantics that motivate separate validation.
- W3C, [SKOS Simple Knowledge Organization System Reference](https://www.w3.org/TR/skos-reference/), including published integrity conditions suitable for reusable checks.
- Zaveri et al., [Quality Assessment for Linked Data: A Survey](https://doi.org/10.3233/SW-150175), a taxonomy of linked-data quality dimensions and metrics.
- Kontokostas et al., [Test-driven Evaluation of Linked Data Quality](https://archives.iw3c2.org/www2014/proceedings/proceedings/p747.pdf), the RDFUnit methodology for executable quality tests.
- Debattista et al., [Luzzu—A Methodology and Framework for Linked Data Quality Assessment](https://arxiv.org/abs/1412.3750), for scalable metric-oriented assessment.
- Labra Gayo et al., [ShEx 2.1 semantics](https://shex.io/shex-semantics/), the main alternative shape language considered.
- Cimmino et al., [Astrea: Automatic Generation of SHACL Shapes from Ontologies](https://pmc.ncbi.nlm.nih.gov/articles/PMC7250618/), for constraint bootstrapping.
- Fernández-Álvarez et al., [sheXer: A Robust and Versatile Library for Automatic Extraction of RDF Shapes](https://www.sciencedirect.com/science/article/pii/S2352711026000865), for data-driven shape induction.
- Ahmetaj et al., [Computing Repairs for SHACL Constraint Violations](https://drops.dagstuhl.de/entities/document/10.4230/TGDK.3.3.1), for the state of automatic repair research.
